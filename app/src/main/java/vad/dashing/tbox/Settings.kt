package vad.dashing.tbox

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import vad.dashing.tbox.fuel.FuelTypes
import vad.dashing.tbox.freeform.FreeformLaunchBounds
import vad.dashing.tbox.freeform.FreeformLaunchSide
import vad.dashing.tbox.mbcan.SlaSpeedLimitDomain
import vad.dashing.tbox.trip.TripWidgetTileDisplay
import vad.dashing.tbox.ui.theme.DARK_THEME_BACKGROUND_COLOR_PRESET_2_INT
import vad.dashing.tbox.ui.theme.LIGHT_THEME_BACKGROUND_COLOR_PRESET_2_INT
import vad.dashing.tbox.ui.theme.TboxFontFamily

private const val DATASTORE_NAME = "vad.dashing.tbox.settings"

// Используем extension property для DataStore
internal val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = DATASTORE_NAME)

enum class SetLauncherAppCustomIconResult {
    Success,
    InvalidPackage,
    DimensionsTooLarge,
    NotImageOrUnreadable,
    CopyFailed,
}

enum class SetTileBackgroundImageResult {
    Success,
    NotImageOrUnreadable,
    DimensionsTooLarge,
    CopyFailed,
}

data class FloatingDashboardWidgetConfig(
    val dataKey: String,
    val showTitle: Boolean = false,
    val showUnit: Boolean = true,
    /** When true, composite two-metric widgets show both values on one line (em-space separated). */
    val singleLineDualMetrics: Boolean = false,
    val scale: Float = 1.0f,
    val shape: Int = 0,
    val textColorLight: Int = DEFAULT_WIDGET_TEXT_COLOR_LIGHT,
    val textColorDark: Int = DEFAULT_WIDGET_TEXT_COLOR_DARK,
    val backgroundColorLight: Int? = null,
    val backgroundColorDark: Int? = null,
    val mediaPlayers: List<String> = emptyList(),
    val mediaSelectedPlayer: String = "",
    val mediaAutoPlayOnInit: Boolean = false,
    /** If true (and [mediaAutoPlayOnInit]), delay auto-play until engine RPM is greater than zero. */
    val mediaAutoPlayOnlyWhenEngineRunning: Boolean = false,
    /** If true (and [mediaAutoPlayOnInit]), keep player in foreground after auto-play launch. */
    val mediaKeepPlayerForeground: Boolean = false,
    /**
     * Full [MUSIC_WIDGET_DATA_KEY] only: show album art in a side column (app icon fallback).
     * Default off — layout stays single-column.
     */
    val mediaShowAlbumArt: Boolean = false,
    /**
     * Width of the album-art column as percent of the tile (full music widget when [mediaShowAlbumArt]).
     * Clamped to [MusicWidgetAlbumArtDisplay.MIN_ALBUM_ART_COLUMN_WIDTH_PERCENT]..
     * [MusicWidgetAlbumArtDisplay.MAX_ALBUM_ART_COLUMN_WIDTH_PERCENT].
     */
    val mediaAlbumArtColumnWidthPercent: Int =
        MusicWidgetAlbumArtDisplay.DEFAULT_ALBUM_ART_COLUMN_WIDTH_PERCENT,
    /**
     * Album-art column side for full music widget when [mediaShowAlbumArt]:
     * [MusicWidgetAlbumArtDisplay.ALBUM_ART_SIDE_LEFT] or [MusicWidgetAlbumArtDisplay.ALBUM_ART_SIDE_RIGHT].
     */
    val mediaAlbumArtSide: Int = MusicWidgetAlbumArtDisplay.DEFAULT_ALBUM_ART_SIDE,
    /**
     * Full music widgets: draw the player icon next to the title, or next to the artist in
     * [MUSIC_COVER_WIDGET_DATA_KEY] when [showTitle] is false. Default on.
     * Does not affect the standard widget album-art column fallback icon.
     */
    val mediaShowPlayerHeaderIcon: Boolean = true,
    /**
     * Full music widgets only: playback controls height as percent of tile height.
     * `null` — type default ([MusicWidgetControlsDisplay.DEFAULT_STANDARD_CONTROLS_HEIGHT_PERCENT]
     * or [MusicWidgetControlsDisplay.DEFAULT_COVER_CONTROLS_HEIGHT_PERCENT]).
     * Clamped to [MusicWidgetControlsDisplay.MIN_CONTROLS_HEIGHT_PERCENT]..
     * [MusicWidgetControlsDisplay.MAX_CONTROLS_HEIGHT_PERCENT].
     */
    val mediaControlsHeightPercent: Int? = null,
    /** Package name of the app to launch (only for `appLauncherWidget`). */
    val launcherAppPackage: String = "",
    /**
     * Launch path for [launcherAppPackage]: fullscreen, TBox freeform, or Adayo A10 stock window.
     * [launcherFreeformEnabled] stays in sync for legacy JSON (`true` only when mode is freeform).
     */
    val launcherLaunchMode: AppLauncherLaunchMode = AppLauncherLaunchMode.DEFAULT,
    /**
     * When true, [launcherAppPackage] launches in freeform beside TBox
     * ([launcherFreeformSide] + [launcherFreeformPercent]). Only for `appLauncherWidget`.
     * Prefer [launcherLaunchMode]; kept for backup/theme compatibility.
     */
    val launcherFreeformEnabled: Boolean = false,
    /** Edge of the display occupied by the companion app when [launcherFreeformEnabled]. */
    val launcherFreeformSide: FreeformLaunchSide = FreeformLaunchSide.DEFAULT,
    /**
     * Percent of display width (left/right) or height (top/bottom) for the companion app.
     * Clamped to [FreeformLaunchBounds.MIN_PERCENT]..[FreeformLaunchBounds.MAX_PERCENT].
     */
    val launcherFreeformPercent: Int = FreeformLaunchBounds.DEFAULT_PERCENT,
    /** YAML request config for `httpRequestWidget`. */
    val httpRequestYaml: String = DEFAULT_HTTP_REQUEST_WIDGET_YAML,
    /** When true, `httpRequestWidget` opens its URL in the browser instead of sending a request. */
    val httpOpenBrowser: Boolean = false,
    /** System app-widget id when the tile shows a third-party app widget (`externalAppWidget`). */
    val appWidgetId: Int? = null,
    /**
     * Optional tile title override. When blank, widgets use their default title strings.
     * When non-blank, shown instead of the default title where a title row is displayed.
     */
    val customTitle: String = "",
    /**
     * Decimal places for numeric values from the tile data provider on this tile.
     * `null` — built-in default per data key; `0`..`2` — fixed fraction digits where applicable.
     */
    val valueAccuracy: Int? = null,
    /** Optional SimpleDateFormat-compatible pattern for date/time tiles; blank keeps system default. */
    val dateTimeFormat: String = "",
    /** Per-tile UI variant for widgets that support multiple modes (e.g. single seat heat vs vent). */
    val selectedVariant: Int = 0,
    /** Fixed target value for [DRIVE_MODE_WIDGET_DATA_KEY] tile. */
    val selectedDriveMode: Int = DRIVE_MODE_WIDGET_DEFAULT_RAW_VALUE,
    /**
     * Selected drive-mode raw values for [DRIVE_MODE_CYCLE_WIDGET_DATA_KEY] tile.
     * Empty means default [DRIVE_MODE_CYCLE_WIDGET_DEFAULT_RAW_VALUES] after normalize.
     */
    val selectedDriveModes: List<Int> = emptyList(),
    /** If true, media volume widget controls CAN backend (mbCAN/VHAL) instead of Android AudioManager. */
    val useMbCanVhal: Boolean = false,
    /**
     * Stepper +/- control icon style for [isStepperWidgetDataKey] tiles:
     * [STEPPER_ADJUST_ICON_PLUS_MINUS] or [STEPPER_ADJUST_ICON_ARROWS].
     */
    val stepperAdjustIconStyle: Int = STEPPER_ADJUST_ICON_PLUS_MINUS,
    /**
     * Optional background image on top of the tile color (light theme).
     * Path relative to [Context.filesDir]; must stay under [TileBackgroundImageStorage.DIR_NAME].
     */
    val tileBackgroundImageRelPathLight: String? = null,
    /** Same as [tileBackgroundImageRelPathLight] for the dark theme. */
    val tileBackgroundImageRelPathDark: String? = null,
    /** Horizontal dividers between rows on trip widget tiles. */
    val tripWidgetShowRowDividers: Boolean = TripWidgetTileDisplay.DEFAULT_SHOW_ROW_DIVIDERS,
    /** First column width (percent) for trip widget row layout. */
    val tripWidgetLabelColumnWidthPercent: Int = TripWidgetTileDisplay.DEFAULT_LABEL_COLUMN_WIDTH_PERCENT,
    /**
     * Trip tile data source: [TRIP_WIDGET_SOURCE_CURRENT] (default) or [TRIP_WIDGET_SOURCE_PERSISTENT].
     * Only used when [isActiveTripWidgetDataKey] is true.
     */
    val tripWidgetSource: Int = TRIP_WIDGET_SOURCE_CURRENT,
    /**
     * Companion relay tile mode ([espRelay0]/[espRelay1]): [EspRelayWidgetMode.BUTTON] or
     * [EspRelayWidgetMode.RELAY]. Ignored for other data keys.
     */
    val espRelayMode: EspRelayWidgetMode = EspRelayWidgetMode.DEFAULT,
    /**
     * Cruise path for [ACC_CRUISE_WIDGET_DATA_KEY] / [CRUISE_STATUS_WIDGET_DATA_KEY]:
     * [CruiseControlType.AUTO] / [CruiseControlType.ACC] / [CruiseControlType.CCS].
     */
    val cruiseControlType: CruiseControlType = CruiseControlType.DEFAULT,
    /** ACC cruise setpoint km/h for [ACC_CRUISE_WIDGET_DATA_KEY] (30…150). */
    val accCruiseTargetKmh: Int = ACC_CRUISE_TARGET_KMH_DEFAULT,
    /** Delay between +1 km/h RES+ pulses (ms) for [ACC_CRUISE_WIDGET_DATA_KEY]. */
    val accCruiseIncreaseIntervalMs: Int = ACC_CRUISE_STEP_INTERVAL_MS_DEFAULT,
    /** Delay between −1 km/h SET− pulses (ms) for [ACC_CRUISE_WIDGET_DATA_KEY]. */
    val accCruiseDecreaseIntervalMs: Int = ACC_CRUISE_STEP_INTERVAL_MS_DEFAULT,
    /** Horizontal text alignment: [WIDGET_TEXT_ALIGN_CENTER], [WIDGET_TEXT_ALIGN_START], [WIDGET_TEXT_ALIGN_END]. */
    val textAlign: Int = DEFAULT_WIDGET_TEXT_ALIGN,
    /** Font weight: [WIDGET_FONT_WEIGHT_NORMAL], [WIDGET_FONT_WEIGHT_MEDIUM], [WIDGET_FONT_WEIGHT_SEMI_BOLD]. */
    val fontWeight: Int = DEFAULT_WIDGET_FONT_WEIGHT,
    /** Title row position when [showTitle]: [WIDGET_TITLE_POSITION_TOP] or [WIDGET_TITLE_POSITION_BOTTOM]. */
    val titlePosition: Int = DEFAULT_WIDGET_TITLE_POSITION,
    /** Inset from cell top edge as percent of cell height (0..[MAX_WIDGET_PADDING_PERCENT]). */
    val paddingTopPercent: Int = DEFAULT_WIDGET_PADDING_PERCENT,
    /** Inset from cell bottom edge as percent of cell height (0..[MAX_WIDGET_PADDING_PERCENT]). */
    val paddingBottomPercent: Int = DEFAULT_WIDGET_PADDING_PERCENT,
    /** Inset from cell start edge as percent of cell width (0..[MAX_WIDGET_PADDING_PERCENT]). */
    val paddingStartPercent: Int = DEFAULT_WIDGET_PADDING_PERCENT,
    /** Inset from cell end edge as percent of cell width (0..[MAX_WIDGET_PADDING_PERCENT]). */
    val paddingEndPercent: Int = DEFAULT_WIDGET_PADDING_PERCENT,
    /**
     * Control-element icon/text color when inactive (light theme).
     * `null` — widget-specific default (usually tile text color).
     */
    val controlInactiveColorLight: Int? = null,
    /** Control-element icon/text color when inactive (dark theme). */
    val controlInactiveColorDark: Int? = null,
    /**
     * Control-element icon/text color when active (light theme).
     * `null` — widget-specific default (e.g. [vad.dashing.tbox.ui.theme.WidgetActiveColors]).
     */
    val controlActiveColorLight: Int? = null,
    /** Control-element icon/text color when active (dark theme). */
    val controlActiveColorDark: Int? = null,
    /** Control-element background when inactive (light). `null` — widget default. */
    val controlInactiveBackgroundColorLight: Int? = null,
    /** Control-element background when inactive (dark). */
    val controlInactiveBackgroundColorDark: Int? = null,
    /** Control-element background when active (light). `null` — widget default. */
    val controlActiveBackgroundColorLight: Int? = null,
    /** Control-element background when active (dark). */
    val controlActiveBackgroundColorDark: Int? = null,
    /**
     * Corner radius in dp for control elements inside the tile.
     * `null` — class default (music/stepper → 10, others → 0).
     */
    val controlShape: Int? = null,
)

/** Normalized top-left of the MainScreen settings button: x,y in [0,1] vs usable width/height. */
data class MainScreenSettingsButtonPosition(
    val x: Float,
    val y: Float
) {
    companion object {
        /** Top-right area (similar to previous fixed layout). */
        val Default = MainScreenSettingsButtonPosition(0.92f, 0.04f)
    }
}

/** Normalized top-left of the MainScreen "+" add-panel control (same coordinate space as [MainScreenSettingsButtonPosition]). */
data class MainScreenAddButtonPosition(
    val x: Float,
    val y: Float
) {
    companion object {
        /** Top-right row, immediately left of [MainScreenSettingsButtonPosition.Default] (same Y). */
        val Default = MainScreenAddButtonPosition(0.84f, 0.04f)
    }
}

/** Normalized top-left of the MainScreen «previous page» control. */
data class MainScreenPagePrevButtonPosition(
    val x: Float,
    val y: Float,
) {
    companion object {
        val Default = MainScreenPagePrevButtonPosition(0.04f, 0.92f)
    }
}

/** Normalized top-left of the MainScreen «next page» control. */
data class MainScreenPageNextButtonPosition(
    val x: Float,
    val y: Float,
) {
    companion object {
        val Default = MainScreenPageNextButtonPosition(0.92f, 0.92f)
    }
}

/**
 * Pixel geometry for the main-screen window-mode overlay (beside a freeform companion app).
 * Empty / invalid stored values resolve via [defaultForDisplay] at show time.
 */
data class MainScreenWindowModeGeometry(
    val startX: Int,
    val startY: Int,
    val width: Int,
    val height: Int,
) {
    fun normalized(): MainScreenWindowModeGeometry = MainScreenWindowModeGeometry(
        startX = startX.coerceAtLeast(0),
        startY = startY.coerceAtLeast(0),
        width = width.coerceAtLeast(MIN_SIZE),
        height = height.coerceAtLeast(MIN_SIZE),
    )

    companion object {
        const val MIN_SIZE = 100

        fun defaultForDisplay(displayWidth: Int, displayHeight: Int): MainScreenWindowModeGeometry {
            val w = displayWidth.coerceAtLeast(MIN_SIZE)
            val h = displayHeight.coerceAtLeast(MIN_SIZE)
            return MainScreenWindowModeGeometry(
                startX = 0,
                startY = 0,
                width = (w / 2).coerceAtLeast(MIN_SIZE),
                height = h,
            )
        }
    }
}

/** Normalized position of the «exit window mode» corner button (only on the overlay MainScreen). */
data class MainScreenWindowModeExitButtonPosition(
    val x: Float,
    val y: Float,
) {
    companion object {
        val Default = MainScreenWindowModeExitButtonPosition(0.04f, 0.04f)
        /** Default for «restore fullscreen» (square) button — to the right of [Default]. */
        val RestoreFullscreenDefault = MainScreenWindowModeExitButtonPosition(0.12f, 0.04f)
    }
}

/**
 * Dashboard panel on the in-app MainScreen (not a system overlay).
 * Position uses the same convention as [MainScreenSettingsButtonPosition]: normalized against
 * `(containerSize - panelSize)` along each axis. [relWidth] / [relHeight] are fractions of the full container.
 */
data class MainScreenPanelConfig(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val widgetsConfig: List<FloatingDashboardWidgetConfig>,
    val rows: Int,
    val cols: Int,
    val relX: Float,
    val relY: Float,
    val relWidth: Float,
    val relHeight: Float,
    val background: Boolean,
    val clickAction: Boolean,
    val showTboxDisconnectIndicator: Boolean = false,
    /** 1-based page index on the main screen (1..[SettingsManager.MAX_MAIN_SCREEN_PAGE_COUNT]). */
    val pageNumber: Int = SettingsManager.DEFAULT_MAIN_SCREEN_PANEL_PAGE_NUMBER,
    /** Gap between tile cells in dp (0..[MAX_PANEL_GRID_SPACING_DP]). */
    val gridSpacingDp: Int = DEFAULT_PANEL_GRID_SPACING_DP,
    /**
     * Swipe-to-collapse edge ([PanelCollapseEdge.storageValue]).
     * Collapsed/expanded flag is stored separately in [PanelCollapseStates] (theme-independent).
     */
    val collapseEdge: String = PanelCollapseEdge.NONE.storageValue,
    val collapseStripThicknessDp: Int = DEFAULT_PANEL_COLLAPSE_STRIP_THICKNESS_DP,
    val collapseStripColorLight: Int = DEFAULT_PANEL_COLLAPSE_STRIP_COLOR_LIGHT,
    val collapseStripColorDark: Int = DEFAULT_PANEL_COLLAPSE_STRIP_COLOR_DARK,
    val collapseStripExpandedColorLight: Int = DEFAULT_PANEL_COLLAPSE_STRIP_EXPANDED_COLOR_LIGHT,
    val collapseStripExpandedColorDark: Int = DEFAULT_PANEL_COLLAPSE_STRIP_EXPANDED_COLOR_DARK,
    val collapseOnTileTap: Boolean = DEFAULT_PANEL_COLLAPSE_ON_TILE_TAP,
    val collapseOnTileTapDelaySec: Int = DEFAULT_PANEL_COLLAPSE_ON_TILE_TAP_DELAY_SEC,
    /** Whole-panel background fill (ARGB); null = fully transparent. */
    val panelBackgroundColorLight: Int? = null,
    val panelBackgroundColorDark: Int? = null,
    /**
     * Path relative to [android.content.Context.getFilesDir]; must stay under
     * [PanelBackgroundImageStorage.DIR_NAME].
     */
    val panelBackgroundImageRelPathLight: String? = null,
    val panelBackgroundImageRelPathDark: String? = null,
    /** Corner radius of the whole panel in dp (0..50); clips tiles. Default 0 = square. */
    val panelShape: Int = DEFAULT_PANEL_SHAPE,
)

data class FloatingDashboardConfig(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val widgetsConfig: List<FloatingDashboardWidgetConfig>,
    val rows: Int,
    val cols: Int,
    val width: Int,
    val height: Int,
    val startX: Int,
    val startY: Int,
    val background: Boolean,
    val clickAction: Boolean,
    val showTboxDisconnectIndicator: Boolean = true,
    /** Gap between tile cells in dp (0..[MAX_PANEL_GRID_SPACING_DP]). */
    val gridSpacingDp: Int = DEFAULT_PANEL_GRID_SPACING_DP,
    val collapseEdge: String = PanelCollapseEdge.NONE.storageValue,
    val collapseStripThicknessDp: Int = DEFAULT_PANEL_COLLAPSE_STRIP_THICKNESS_DP,
    val collapseStripColorLight: Int = DEFAULT_PANEL_COLLAPSE_STRIP_COLOR_LIGHT,
    val collapseStripColorDark: Int = DEFAULT_PANEL_COLLAPSE_STRIP_COLOR_DARK,
    val collapseStripExpandedColorLight: Int = DEFAULT_PANEL_COLLAPSE_STRIP_EXPANDED_COLOR_LIGHT,
    val collapseStripExpandedColorDark: Int = DEFAULT_PANEL_COLLAPSE_STRIP_EXPANDED_COLOR_DARK,
    val collapseOnTileTap: Boolean = DEFAULT_PANEL_COLLAPSE_ON_TILE_TAP,
    val collapseOnTileTapDelaySec: Int = DEFAULT_PANEL_COLLAPSE_ON_TILE_TAP_DELAY_SEC,
    /** Whole-panel background fill (ARGB); null = fully transparent. */
    val panelBackgroundColorLight: Int? = null,
    val panelBackgroundColorDark: Int? = null,
    /**
     * Path relative to [android.content.Context.getFilesDir]; must stay under
     * [PanelBackgroundImageStorage.DIR_NAME].
     */
    val panelBackgroundImageRelPathLight: String? = null,
    val panelBackgroundImageRelPathDark: String? = null,
    /** Corner radius of the whole panel in dp (0..50); clips tiles. Default 0 = square. */
    val panelShape: Int = DEFAULT_PANEL_SHAPE,
)

/**
 * One-shot read of all preferences used by [BackgroundService] `stateIn` flows, so the service
 * can subscribe with [SharingStarted.Eagerly] using persisted values without N separate DataStore reads.
 */
data class BackgroundServiceSettingsSnapshot(
    val autoModemRestart: Boolean,
    val autoTboxReboot: Boolean,
    val autoSuspendTboxApp: Boolean,
    val autoStopTboxApp: Boolean,
    val autoSuspendTboxMdc: Boolean,
    val autoStopTboxMdc: Boolean,
    val autoSuspendTboxSwd: Boolean,
    val autoSuspendTboxLoc: Boolean,
    val autoPreventTboxRestart: Boolean,
    val getCanFrame: Boolean,
    val getCycleSignal: Boolean,
    /** True when [locationSource] is [vad.dashing.tbox.esp.LocationSource.TBOX] (legacy name kept for callers). */
    val getLocData: Boolean,
    val locationSource: vad.dashing.tbox.esp.LocationSource,
    /** USB ESP32 companion session; off by default (not all users have the hardware). */
    val espCompanionEnabled: Boolean,
    /**
     * When true, do not connect to TBox / tbox-proxy (HU-only mode).
     * Default false preserves legacy connect behavior.
     */
    val noTboxConnect: Boolean,
    /** Persisted USB GNSS device id (`vid:pid` or `vid:pid:serial`). */
    val usbGnssDeviceId: String,
    /** USB GNSS serial baud (CDC / vendor UART init). */
    val usbGnssBaud: Int,
    /** After USB open, send Unicore `GPVTG` enable (default off). */
    val usbGnssRequestVtg: Boolean,
    /** After USB open, send Unicore `GPZDA` enable (default off). */
    val usbGnssRequestZda: Boolean,
    /** After USB open, send Unicore `GPGST` enable (default off). */
    val usbGnssRequestGst: Boolean,
    /** Companion UM980: after link / on toggle, send Unicore `GPVTG` (default off). */
    val espUm980RequestVtg: Boolean,
    /** Companion UM980: after link / on toggle, send Unicore `GPZDA` (default off). */
    val espUm980RequestZda: Boolean,
    /** Companion UM980: after link / on toggle, send Unicore `GPGST` (default off). */
    val espUm980RequestGst: Boolean,
    val widgetShowIndicator: Boolean,
    val widgetShowLocIndicator: Boolean,
    val mockLocation: Boolean,
    /** Period for pushing mock location into Android LocationManager (ms). */
    val mockLocationPeriodMs: Long,
    /** How mock mixes CAN vehicle speed into pushed locations. */
    val mockCanSpeedMode: vad.dashing.tbox.location.MockCanSpeedMode,
    /** Heading source for enhancement DR: gyro or steering angle. */
    val mockHeadingSource: vad.dashing.tbox.location.MockHeadingSource,
    /**
     * When true (default), mark and (in mock) reject live GNSS that fail
     * [vad.dashing.tbox.location.MockJunkFixFilter]
     * (altitude / absurd speed / poor accuracy / GPS vs CAN speed mismatch).
     */
    val mockJunkFixFilter: Boolean,
    val floatingDashboards: List<FloatingDashboardConfig>,
    /** Package names: when any of these is in foreground, listed floating panels are hidden (usage-stats poll). */
    val usageStatsHideFloatingWatchPackages: Set<String>,
    /** Floating dashboard ids to hide while a watched package is foreground. */
    val usageStatsHideFloatingPanelIds: Set<String>,
    /** Package names: when foreground is one of these (and not in hide-watch), listed panels may be force-shown. */
    val usageStatsForceShowFloatingWatchPackages: Set<String>,
    /** Panels to show while a force-show watched app is foreground, even if «Показывать плавающую панель» is off. */
    val usageStatsForceShowFloatingPanelIds: Set<String>,
    val canDataSaveCount: Int,
    val fuelTankLiters: Int,
    /** JSON калибровки топлива (пустая строка — нет данных). */
    val fuelCalibrationJson: String,
    /** Число зон бака для калибровки. */
    val fuelCalibrationZoneCount: Int,
    /**
     * Порог зрелости зоны (литры датчика, накопленные в зоне): при достижении полная уверенность в локальном K.
     */
    val fuelCalibrationMaturityThreshold: Int,
    val fuelPriceFuelId: Int,
    val splitTripTimeMinutes: Int,
    /**
     * When true, [BackgroundService] saves last non-zero wheel pressures when engine RPM drops to 0
     * and restores them from app data when the service starts (if CAN still reports null/zero).
     * Applies to both TBox (`CanDataRepository`) and HU mbCAN/VHAL (`UniversalCanRepository`) paths
     * with **separate** DataStore keys (no cross-source mix); also extends null-debounce to 5 min
     * while enabled.
     */
    val wheelPressurePersistAcrossStops: Boolean,
)

class SettingsManager(private val context: Context) {

    val themeActivationInProgressFlow: StateFlow<Boolean> =
        ThemeActivationCoordinator.themeActivationInProgressFlow

    /**
     * Invoked synchronously before [themeActivationInProgressFlow] becomes true so pending
     * main-screen page/wallpaper edits flush into the outgoing theme's runtime.json.
     */
    var preThemeActivationFlush: (suspend () -> Unit)?
        get() = ThemeActivationCoordinator.preThemeActivationFlush
        set(value) {
            ThemeActivationCoordinator.preThemeActivationFlush = value
        }

    val mainScreenWallpaperRevisionFlow: StateFlow<Long> =
        ThemeActivationCoordinator.mainScreenWallpaperRevisionFlow

    suspend fun bumpMainScreenWallpaperRevision() {
        ThemeActivationCoordinator.bumpMainScreenWallpaperRevision()
    }

    /** While true, UI should avoid drawing file-backed bitmaps (theme switch in progress). */
    suspend fun <T> runWithThemeActivation(block: suspend () -> T): T {
        return ThemeActivationCoordinator.runWithThemeActivation(this, block)
    }

    /**
     * Persists [selections] to the active theme [runtime.json], retrying once on failure.
     * @return true when the cache file was updated.
     */
    suspend fun syncActiveThemeWallpaperSelectionReliable(
        selections: MainScreenWallpaperSelectionsByPage,
    ): Boolean {
        val cacheKey = activeThemeUriFlow.first().trim()
        return syncThemeWallpaperSelectionReliable(cacheKey, selections)
    }

    /**
     * Persists [selections] to [cacheKey] theme [runtime.json], retrying once on failure.
     * Use an explicit [cacheKey] captured before async work so a drive-mode theme switch cannot
     * redirect the write to the newly active theme cache.
     */
    suspend fun syncThemeWallpaperSelectionReliable(
        cacheKey: String,
        selections: MainScreenWallpaperSelectionsByPage,
    ): Boolean {
        if (syncThemeWallpaperSelection(cacheKey, selections)) return true
        return syncThemeWallpaperSelection(cacheKey, selections)
    }

    companion object {
        /** Tab key for the home [vad.dashing.tbox.ui.MainScreen] (no left sidebar). */
        const val MAIN_SCREEN_TAB_KEY = "main_screen"

        /** Left menu tab key for the Trips section. */
        const val TRIPS_TAB_KEY = "trips"

        /** Dedicated update screen opened from the left menu button. */
        const val UPDATE_TAB_KEY = "update"

        /** Max tile rows/columns for main-screen embedded panels and floating overlay dashboards. */
        const val DASHBOARD_PANEL_MAX_GRID_DIMENSION = 10

        /** Dropdown options 1…[DASHBOARD_PANEL_MAX_GRID_DIMENSION] for panel grid settings. */
        val DASHBOARD_PANEL_GRID_OPTIONS: List<Int> =
            (1..DASHBOARD_PANEL_MAX_GRID_DIMENSION).toList()

        /** Max rows/cols for the in-app «Плитки» tab grid (not floating / main-screen panels). */
        const val MAIN_TAB_DASHBOARD_MAX_GRID_DIMENSION = 6

        val MAIN_TAB_DASHBOARD_GRID_OPTIONS: List<Int> =
            (1..MAIN_TAB_DASHBOARD_MAX_GRID_DIMENSION).toList()

        private const val KEY_PREFIX = "vad.dashing.tbox."

        // Boolean настройки
        private val AUTO_MODEM_RESTART_KEY = booleanPreferencesKey("${KEY_PREFIX}auto_modem_restart")
        private val AUTO_TBOX_REBOOT_KEY = booleanPreferencesKey("${KEY_PREFIX}auto_tbox_reboot")
        private val AUTO_SUSPEND_TBOX_APP_KEY = booleanPreferencesKey("${KEY_PREFIX}auto_suspend_tbox_app")
        private val AUTO_STOP_TBOX_APP_KEY = booleanPreferencesKey("${KEY_PREFIX}auto_stop_tbox_app")
        private val AUTO_STOP_TBOX_MDC_KEY = booleanPreferencesKey("${KEY_PREFIX}auto_stop_tbox_mdc")
        private val AUTO_SUSPEND_TBOX_MDC_KEY = booleanPreferencesKey("${KEY_PREFIX}auto_suspend_tbox_mdc")
        private val AUTO_SUSPEND_TBOX_SWD_KEY = booleanPreferencesKey("${KEY_PREFIX}auto_suspend_tbox_swd")
        private val AUTO_SUSPEND_TBOX_LOC_KEY = booleanPreferencesKey("${KEY_PREFIX}auto_suspend_tbox_loc")
        private val AUTO_PREVENT_TBOX_RESTART_KEY = booleanPreferencesKey("${KEY_PREFIX}auto_prevent_tbox_restart")
        private val GET_VOLTAGES_KEY = booleanPreferencesKey("${KEY_PREFIX}get_voltages")
        private val GET_CAN_FRAME_KEY = booleanPreferencesKey("${KEY_PREFIX}get_can_frame")
        private val NO_TBOX_CONNECT_KEY = booleanPreferencesKey("${KEY_PREFIX}no_tbox_connect")
        private val GET_CYCLE_SIGNAL_KEY = booleanPreferencesKey("${KEY_PREFIX}get_cycle_signal")
        private val GET_LOC_DATA_KEY = booleanPreferencesKey("${KEY_PREFIX}get_loc_data")
        private val LOCATION_SOURCE_KEY = stringPreferencesKey("${KEY_PREFIX}location_source")
        private val ESP_COMPANION_ENABLED_KEY = booleanPreferencesKey("${KEY_PREFIX}esp_companion_enabled")
        private val USB_GNSS_DEVICE_ID_KEY = stringPreferencesKey("${KEY_PREFIX}usb_gnss_device_id")
        private val USB_GNSS_BAUD_KEY = intPreferencesKey("${KEY_PREFIX}usb_gnss_baud")
        private val USB_GNSS_REQUEST_VTG_KEY =
            booleanPreferencesKey("${KEY_PREFIX}usb_gnss_request_vtg")
        private val USB_GNSS_REQUEST_ZDA_KEY =
            booleanPreferencesKey("${KEY_PREFIX}usb_gnss_request_zda")
        private val USB_GNSS_REQUEST_GST_KEY =
            booleanPreferencesKey("${KEY_PREFIX}usb_gnss_request_gst")
        private val ESP_UM980_REQUEST_VTG_KEY =
            booleanPreferencesKey("${KEY_PREFIX}esp_um980_request_vtg")
        private val ESP_UM980_REQUEST_ZDA_KEY =
            booleanPreferencesKey("${KEY_PREFIX}esp_um980_request_zda")
        private val ESP_UM980_REQUEST_GST_KEY =
            booleanPreferencesKey("${KEY_PREFIX}esp_um980_request_gst")
        private val USB_GNSS_MODULE_BY_DEVICE_KEY =
            stringPreferencesKey("${KEY_PREFIX}usb_gnss_module_by_device")
        private val WIDGET_SHOW_INDICATOR = booleanPreferencesKey("${KEY_PREFIX}widget_show_indicator")
        private val WIDGET_SHOW_LOC_INDICATOR = booleanPreferencesKey("${KEY_PREFIX}widget_show_loc_indicator")
        private val MOCK_LOCATION = booleanPreferencesKey("${KEY_PREFIX}mock_location")
        private val MOCK_LOCATION_PERIOD_MS = longPreferencesKey("${KEY_PREFIX}mock_location_period_ms")
        private val MOCK_CAN_SPEED_MODE_KEY = stringPreferencesKey("${KEY_PREFIX}mock_can_speed_mode")
        private val MOCK_HEADING_SOURCE_KEY =
            stringPreferencesKey("${KEY_PREFIX}mock_heading_source")
        private val MOCK_JUNK_FIX_FILTER_KEY = booleanPreferencesKey("${KEY_PREFIX}mock_junk_fix_filter")
        /** Optional background auto-calib in Advanced (CONSTANT); default off. */
        private val CONSTANT_AUTO_CALIB_ENABLED_KEY =
            booleanPreferencesKey("${KEY_PREFIX}constant_auto_calib_enabled")
        /**
         * When true, enhancement mock modes (not Direct) invert travel bearing for reverse
         * via [vad.dashing.tbox.mbcan.VehicleGearDomain.isReverseEngaged]. Default on.
         */
        private val MOCK_CONSIDER_REVERSE_KEY =
            booleanPreferencesKey("${KEY_PREFIX}mock_consider_reverse")
        private val GEO_CALIB_NEEDS_KEY =
            booleanPreferencesKey("${KEY_PREFIX}geo_calib_needs")
        private val GEO_CALIB_LAST_AT_MS_KEY =
            longPreferencesKey("${KEY_PREFIX}geo_calib_last_at_ms")
        private val MOCK_LAST_GOOD_FIX_KEY = stringPreferencesKey("${KEY_PREFIX}mock_last_good_fix")
        private val GYRO_BIAS_YAW_KEY = floatPreferencesKey("${KEY_PREFIX}gyro_bias_yaw")
        private val GYRO_BIAS_PITCH_KEY = floatPreferencesKey("${KEY_PREFIX}gyro_bias_pitch")
        private val GYRO_BIAS_ROLL_KEY = floatPreferencesKey("${KEY_PREFIX}gyro_bias_roll")
        private val GYRO_BIAS_ACCEL_X_KEY = floatPreferencesKey("${KEY_PREFIX}gyro_bias_accel_x")
        private val GYRO_BIAS_ACCEL_Y_KEY = floatPreferencesKey("${KEY_PREFIX}gyro_bias_accel_y")
        private val GYRO_BIAS_ACCEL_Z_KEY = floatPreferencesKey("${KEY_PREFIX}gyro_bias_accel_z")
        private val GYRO_BIAS_YAW_TEMP_KEY =
            floatPreferencesKey("${KEY_PREFIX}gyro_bias_yaw_temp_c")
        private val DRIVE_CALIB_SPEED_SCALE_KEY =
            floatPreferencesKey("${KEY_PREFIX}drive_calib_speed_scale")
        private val DRIVE_CALIB_YAW_SCALE_KEY =
            floatPreferencesKey("${KEY_PREFIX}drive_calib_yaw_scale")
        private val DRIVE_CALIB_YAW_SCALE_LEFT_KEY =
            floatPreferencesKey("${KEY_PREFIX}drive_calib_yaw_scale_left")
        private val DRIVE_CALIB_YAW_SCALE_RIGHT_KEY =
            floatPreferencesKey("${KEY_PREFIX}drive_calib_yaw_scale_right")
        private val DRIVE_CALIB_YAW_SIGN_KEY =
            intPreferencesKey("${KEY_PREFIX}drive_calib_yaw_sign")
        private val DRIVE_CALIB_AT_MS_KEY =
            longPreferencesKey("${KEY_PREFIX}drive_calib_at_ms")
        private val DRIVE_CALIB_LAG_MS_KEY =
            longPreferencesKey("${KEY_PREFIX}drive_calib_lag_ms")
        private val DRIVE_CALIB_SPEED_EST_KEY =
            booleanPreferencesKey("${KEY_PREFIX}drive_calib_speed_est")
        private val DRIVE_CALIB_YAW_EST_KEY =
            booleanPreferencesKey("${KEY_PREFIX}drive_calib_yaw_est")
        private val STEER_CALIB_ZERO_DEG_KEY =
            floatPreferencesKey("${KEY_PREFIX}steer_calib_zero_deg")
        private val STEER_CALIB_SCALE_KEY =
            floatPreferencesKey("${KEY_PREFIX}steer_calib_scale")
        /** Legacy dual L/R — migrated to mean [STEER_CALIB_SCALE_KEY] on load. */
        private val STEER_CALIB_SCALE_LEFT_KEY =
            floatPreferencesKey("${KEY_PREFIX}steer_calib_scale_left")
        private val STEER_CALIB_SCALE_RIGHT_KEY =
            floatPreferencesKey("${KEY_PREFIX}steer_calib_scale_right")
        private val STEER_CALIB_SIGN_KEY =
            intPreferencesKey("${KEY_PREFIX}steer_calib_sign")
        private val STEER_CALIB_AT_MS_KEY =
            longPreferencesKey("${KEY_PREFIX}steer_calib_at_ms")
        private val STEER_CALIB_SCALE_EST_KEY =
            booleanPreferencesKey("${KEY_PREFIX}steer_calib_scale_est")
        private val EXPERT_MODE = booleanPreferencesKey("${KEY_PREFIX}expert_mode")
        /** After first-run permissions dialog was closed (also set when opened from Settings and dismissed). */
        private val PERMISSIONS_INTRO_SEEN_KEY =
            booleanPreferencesKey("${KEY_PREFIX}permissions_intro_seen")
        private val LEFT_MENU_VISIBLE = booleanPreferencesKey("${KEY_PREFIX}left_menu_visible")
        private val MAIN_SCREEN_OPEN_ON_BOOT_KEY =
            booleanPreferencesKey("${KEY_PREFIX}main_screen_open_on_boot")
        private val MAIN_SCREEN_OPEN_ON_BOOT_DELAY_SECONDS_KEY =
            intPreferencesKey("${KEY_PREFIX}main_screen_open_on_boot_delay_seconds")
        /** Legacy: copied single image per theme (pre folder-based wallpapers). */
        private val MAIN_SCREEN_WALLPAPER_LIGHT_SET_LEGACY_KEY =
            booleanPreferencesKey("${KEY_PREFIX}main_screen_wallpaper_light")
        private val MAIN_SCREEN_WALLPAPER_DARK_SET_LEGACY_KEY =
            booleanPreferencesKey("${KEY_PREFIX}main_screen_wallpaper_dark")
        private val MAIN_SCREEN_WALLPAPER_LIGHT_FOLDER_URI_KEY =
            stringPreferencesKey("${KEY_PREFIX}main_screen_wallpaper_light_folder_uri")
        private val MAIN_SCREEN_WALLPAPER_DARK_FOLDER_URI_KEY =
            stringPreferencesKey("${KEY_PREFIX}main_screen_wallpaper_dark_folder_uri")
        private val MAIN_SCREEN_WALLPAPER_SELECTION_BY_PAGE_KEY =
            stringPreferencesKey("${KEY_PREFIX}main_screen_wallpaper_selection_by_page")
        private val MAIN_SCREEN_WALLPAPER_CROP_KEY =
            booleanPreferencesKey("${KEY_PREFIX}main_screen_wallpaper_crop")

        /** Bumped when launcher shortcut custom icons or wallpaper files are cleared — refreshes in-memory bitmaps. */
        private val LAUNCHER_APP_ICON_REVISION_KEY =
            intPreferencesKey("${KEY_PREFIX}launcher_app_icon_revision")

        /** Bumped when HTTP request widget custom icons change. */
        private val HTTP_REQUEST_ICON_REVISION_KEY =
            intPreferencesKey("${KEY_PREFIX}http_request_icon_revision")

        /** Bumped when per-tile background image files change (save / clear / backup import). */
        private val TILE_BACKGROUND_IMAGE_REVISION_KEY =
            intPreferencesKey("${KEY_PREFIX}tile_background_image_revision")

        /** Bumped when whole-panel background image files change (save / clear / backup import). */
        private val PANEL_BACKGROUND_IMAGE_REVISION_KEY =
            intPreferencesKey("${KEY_PREFIX}panel_background_image_revision")

        private val MAIN_SCREEN_CORNER_BUTTON_SIZE_KEY =
            intPreferencesKey("${KEY_PREFIX}main_screen_corner_button_size_dp")
        private val MAIN_SCREEN_CORNER_BTN_BG_LIGHT_KEY =
            intPreferencesKey("${KEY_PREFIX}main_screen_corner_btn_bg_light")
        private val MAIN_SCREEN_CORNER_BTN_BG_DARK_KEY =
            intPreferencesKey("${KEY_PREFIX}main_screen_corner_btn_bg_dark")
        private val MAIN_SCREEN_CORNER_BTN_ICON_LIGHT_KEY =
            intPreferencesKey("${KEY_PREFIX}main_screen_corner_btn_icon_light")
        private val MAIN_SCREEN_CORNER_BTN_ICON_DARK_KEY =
            intPreferencesKey("${KEY_PREFIX}main_screen_corner_btn_icon_dark")
        private val MAIN_SCREEN_CANVAS_BG_LIGHT_KEY =
            intPreferencesKey("${KEY_PREFIX}main_screen_canvas_bg_light")
        private val MAIN_SCREEN_CANVAS_BG_DARK_KEY =
            intPreferencesKey("${KEY_PREFIX}main_screen_canvas_bg_dark")

        const val WIDGET_COLOR_PRESET_SLOT_COUNT = 8

        /** Global user palette for [vad.dashing.tbox.ui.WidgetColorSetting] (eight ARGB slots). */
        private val WIDGET_COLOR_PRESET_KEYS = Array(WIDGET_COLOR_PRESET_SLOT_COUNT) { i ->
            intPreferencesKey("${KEY_PREFIX}widget_color_preset_$i")
        }

        private val SELECTED_TAB_KEY = stringPreferencesKey("${KEY_PREFIX}selected_tab")

        private val DASHBOARD_ROWS_KEY = intPreferencesKey("${KEY_PREFIX}dashboard_rows")
        private val DASHBOARD_COLS_KEY = intPreferencesKey("${KEY_PREFIX}dashboard_cols")
        private val DASHBOARD_CHART_KEY = booleanPreferencesKey("${KEY_PREFIX}dashboard_chart")
        private val DASHBOARD_GRID_SPACING_KEY =
            intPreferencesKey("${KEY_PREFIX}dashboard_grid_spacing_dp")
        private val FLOATING_PANELS_LAYOUT_SNAP_DP_KEY =
            intPreferencesKey("${KEY_PREFIX}floating_panels_layout_snap_dp")
        private val MAIN_SCREEN_PANELS_LAYOUT_SNAP_DP_KEY =
            intPreferencesKey("${KEY_PREFIX}main_screen_panels_layout_snap_dp")
        private val MAIN_SCREEN_PANELS_LAYOUT_SNAP_ENABLED_KEY =
            booleanPreferencesKey("${KEY_PREFIX}main_screen_panels_layout_snap_enabled")
        private val MAIN_SCREEN_SHOW_LAYOUT_GRID_KEY =
            booleanPreferencesKey("${KEY_PREFIX}main_screen_show_layout_grid")
        private val CAN_DATA_SAVE_COUNT_KEY = intPreferencesKey("${KEY_PREFIX}can_data_save_count")
        private val FUEL_TANK_LITERS_KEY = intPreferencesKey("${KEY_PREFIX}fuel_tank_liters")
        private val SPEED_LIMITER_TARGET_KMH_KEY = intPreferencesKey("${KEY_PREFIX}speed_limiter_target_kmh")
        private val FUEL_CALIBRATION_JSON_KEY = stringPreferencesKey("${KEY_PREFIX}fuel_calibration_json")
        private val FUEL_CALIBRATION_ZONE_COUNT_KEY =
            intPreferencesKey("${KEY_PREFIX}fuel_calibration_zone_count")
        private val FUEL_CALIBRATION_MATURITY_THRESHOLD_KEY =
            intPreferencesKey("${KEY_PREFIX}fuel_calibration_maturity_threshold_l")
        private val FUEL_PRICE_FUEL_ID_KEY = intPreferencesKey("${KEY_PREFIX}fuel_price_fuel_id")
        private val SPLIT_TRIP_TIME_MINUTES_KEY = intPreferencesKey("${KEY_PREFIX}split_trip_time_minutes")
        private val TRACK_REFUELS_KEY = booleanPreferencesKey("${KEY_PREFIX}track_refuels")
        private val WHEEL_PRESSURE_PERSIST_ACROSS_STOPS_KEY =
            booleanPreferencesKey("${KEY_PREFIX}wheel_pressure_persist_across_stops")
        private val UI_CLICK_SOUNDS_KEY = booleanPreferencesKey("${KEY_PREFIX}ui_click_sounds")
        private val APP_FONT_FAMILY_ID_KEY = intPreferencesKey("${KEY_PREFIX}app_font_family_id")
        private val UPDATE_CHANNEL_KEY = stringPreferencesKey("${KEY_PREFIX}update_channel")
        private val UPDATE_CHECK_ENABLED_KEY = booleanPreferencesKey("${KEY_PREFIX}update_check_enabled")
        private val HEAD_UNIT_CAN_MODE_KEY = stringPreferencesKey("${KEY_PREFIX}head_unit_can_mode")
        private val CAN_AUTO_BIND_ENABLED_KEY = booleanPreferencesKey("${KEY_PREFIX}can_auto_bind_enabled")
        private val CAN_AUTO_BIND_LOCKED_KEY = booleanPreferencesKey("${KEY_PREFIX}can_auto_bind_locked")
        private val CAN_AUTO_BIND_LAST_PRIMARY_MODE_KEY =
            stringPreferencesKey("${KEY_PREFIX}can_auto_bind_last_primary_mode")
        private val CAN_AUTO_BIND_LAST_RESULT_KEY =
            stringPreferencesKey("${KEY_PREFIX}can_auto_bind_last_result")

        // String настройки
        private val LOG_LEVEL_KEY = stringPreferencesKey("${KEY_PREFIX}log_level")
        // Значения по умолчанию
        private const val DEFAULT_LOG_LEVEL = "INFO"
        private const val DEFAULT_FLOATING_DASHBOARD_ROWS = 1
        private const val DEFAULT_FLOATING_DASHBOARD_COLS = 1
        private const val DEFAULT_FLOATING_DASHBOARD_WIDTH = 100
        private const val DEFAULT_FLOATING_DASHBOARD_HEIGHT = 100
        private const val DEFAULT_FLOATING_DASHBOARD_START_X = 50
        private const val DEFAULT_FLOATING_DASHBOARD_START_Y = 50
        private const val DEFAULT_FLOATING_DASHBOARD_ENABLED = false
        private const val DEFAULT_FLOATING_DASHBOARD_BACKGROUND = false
        private const val DEFAULT_FLOATING_DASHBOARD_CLICK_ACTION = true
        private const val DEFAULT_FLOATING_DASHBOARD_SHOW_TBOX_DISCONNECT_INDICATOR = true
        private val DEFAULT_FLOATING_DASHBOARD_WIDGETS = emptyList<FloatingDashboardWidgetConfig>()
        private const val DEFAULT_MAIN_SCREEN_PANEL_ROWS = 1
        private const val DEFAULT_MAIN_SCREEN_PANEL_COLS = 1
        private const val DEFAULT_MAIN_SCREEN_PANEL_REL_X = 0.05f
        private const val DEFAULT_MAIN_SCREEN_PANEL_REL_Y = 0.1f
        private const val DEFAULT_MAIN_SCREEN_PANEL_REL_WIDTH = 0.4f
        private const val DEFAULT_MAIN_SCREEN_PANEL_REL_HEIGHT = 0.3f
        private const val DEFAULT_MAIN_SCREEN_PANEL_ENABLED = true
        private const val DEFAULT_MAIN_SCREEN_PANEL_BACKGROUND = false
        private const val DEFAULT_MAIN_SCREEN_PANEL_CLICK_ACTION = false
        private const val DEFAULT_MAIN_SCREEN_PANEL_SHOW_TBOX_DISCONNECT = false
        private const val FLOATING_DASHBOARDS_LIST_KEY = "floating_dashboards"
        private const val USAGE_STATS_HIDE_FLOATING_WATCH_PACKAGES_KEY = "usage_stats_hide_floating_watch_packages"
        private const val USAGE_STATS_HIDE_FLOATING_PANEL_IDS_KEY = "usage_stats_hide_floating_panel_ids"
        private const val USAGE_STATS_FORCE_SHOW_WATCH_PACKAGES_KEY = "usage_stats_force_show_floating_watch_packages"
        private const val USAGE_STATS_FORCE_SHOW_PANEL_IDS_KEY = "usage_stats_force_show_floating_panel_ids"
        private const val MAIN_SCREEN_DASHBOARDS_LIST_KEY = "main_screen_dashboards"
        private const val MAIN_SCREEN_SETTINGS_BUTTON_KEY = "main_screen_settings_button"
        private const val MAIN_SCREEN_ADD_BUTTON_KEY = "main_screen_add_button"
        private const val MAIN_SCREEN_PAGE_PREV_BUTTON_KEY = "main_screen_page_prev_button"
        private const val MAIN_SCREEN_PAGE_NEXT_BUTTON_KEY = "main_screen_page_next_button"
        private const val MAIN_SCREEN_WINDOW_MODE_GEOMETRY_KEY = "main_screen_window_mode_geometry"
        private const val MAIN_SCREEN_WINDOW_MODE_EXIT_BUTTON_KEY = "main_screen_window_mode_exit_button"
        private const val MAIN_SCREEN_WINDOW_MODE_RESTORE_BUTTON_KEY =
            "main_screen_window_mode_restore_button"
        private val MAIN_SCREEN_WINDOW_MODE_AUTO_GEOMETRY_KEY =
            booleanPreferencesKey("${KEY_PREFIX}main_screen_window_mode_auto_geometry")

        const val MIN_MAIN_SCREEN_PAGE_COUNT = 1
        const val MAX_MAIN_SCREEN_PAGE_COUNT = 5
        const val DEFAULT_MAIN_SCREEN_PAGE_COUNT = 1
        const val DEFAULT_MAIN_SCREEN_CURRENT_PAGE = 1
        const val DEFAULT_MAIN_SCREEN_PANEL_PAGE_NUMBER = 1
        val MAIN_SCREEN_PAGE_COUNT_OPTIONS: List<Int> =
            (MIN_MAIN_SCREEN_PAGE_COUNT..MAX_MAIN_SCREEN_PAGE_COUNT).toList()

        private val MAIN_SCREEN_PAGE_COUNT_KEY =
            intPreferencesKey("${KEY_PREFIX}main_screen_page_count")
        private val MAIN_SCREEN_CURRENT_PAGE_KEY =
            intPreferencesKey("${KEY_PREFIX}main_screen_current_page")
        /** Last page while main-screen window (freeform companion) overlay is active. Absent = never set. */
        private val MAIN_SCREEN_WINDOW_MODE_CURRENT_PAGE_KEY =
            intPreferencesKey("${KEY_PREFIX}main_screen_window_mode_current_page")
        /** Theme-independent panelId → collapsed map ([PanelCollapseStates]). */
        private val PANEL_COLLAPSE_STATES_KEY =
            stringPreferencesKey("${KEY_PREFIX}${PanelCollapseStates.DATASTORE_KEY}")
        private val ACTIVE_THEME_URI_KEY = stringPreferencesKey("${KEY_PREFIX}active_theme_uri")
        private val ACTIVE_THEME_FINGERPRINT_KEY =
            stringPreferencesKey("${KEY_PREFIX}active_theme_fingerprint")
        private val ACTIVE_THEME_SECTIONS_KEY =
            stringPreferencesKey("${KEY_PREFIX}active_theme_sections")
        private val ACTIVE_THEME_APPLY_TARGETS_KEY =
            stringPreferencesKey("${KEY_PREFIX}active_theme_apply_targets")
        private val DRIVE_MODE_THEME_PATHS_KEY =
            stringPreferencesKey("${KEY_PREFIX}drive_mode_theme_paths")

        /** Legacy single-file copies (may be migrated to folder URIs on startup). */
        const val MAIN_SCREEN_WALLPAPER_LIGHT_FILE = "main_screen_wallpaper/light"
        const val MAIN_SCREEN_WALLPAPER_DARK_FILE = "main_screen_wallpaper/dark"
        /** One-time migration copies old per-theme files into this directory as `file://` folder URIs. */
        private const val MAIN_SCREEN_WALLPAPER_MIGRATED_DIR = "main_screen_wallpaper_migrated"
        /** Per-package custom icons for the app-launcher widget (files only; not in JSON backup). */
        const val LAUNCHER_APP_ICONS_DIR = "launcher_app_icons"
        /** Per-tile custom icons for HTTP request widgets (files only; not in JSON backup). */
        const val HTTP_REQUEST_ICONS_DIR = "http_request_icons"
        private const val MAX_LAUNCHER_APP_ICON_EDGE_PX = 512
        private const val MAX_LAUNCHER_APP_ICON_BYTES = 512 * 1024L
        private const val MAX_TILE_BACKGROUND_EDGE_PX = 4096
        private const val MAX_TILE_BACKGROUND_BYTES = 8 * 1024 * 1024L
        private const val DEFAULT_CAN_DATA_SAVE_COUNT = 5
        private const val DEFAULT_FUEL_TANK_LITERS = 57
        private const val DEFAULT_FUEL_CALIBRATION_ZONE_COUNT = 5
        private const val FUEL_CALIBRATION_ZONE_COUNT_MIN = 3
        private const val FUEL_CALIBRATION_ZONE_COUNT_MAX = 20
        /** Накопленные «литры датчика» по зоне для полной уверенности в локальном K (см. CalibrationStore). */
        private const val DEFAULT_FUEL_CALIBRATION_MATURITY_THRESHOLD = 80
        const val FUEL_CALIBRATION_MATURITY_THRESHOLD_MIN = 5
        const val FUEL_CALIBRATION_MATURITY_THRESHOLD_MAX = 500
        private const val DEFAULT_SPLIT_TRIP_TIME_MINUTES = 5
        private const val DEFAULT_TRACK_REFUELS = true
        const val MIN_MAIN_SCREEN_OPEN_ON_BOOT_DELAY_SECONDS = 0
        const val MAX_MAIN_SCREEN_OPEN_ON_BOOT_DELAY_SECONDS = 60
        const val DEFAULT_MAIN_SCREEN_OPEN_ON_BOOT_DELAY_SECONDS = 2
        private const val MIN_MAIN_SCREEN_CORNER_BUTTON_SIZE_DP = 10
        private const val DEFAULT_MAIN_SCREEN_CORNER_BUTTON_SIZE_DP = 50
        /** Fully transparent — only the icon is visible over the main-screen canvas. */
        private const val DEFAULT_MAIN_SCREEN_CORNER_BTN_BG_LIGHT = 0x00000000
        private const val DEFAULT_MAIN_SCREEN_CORNER_BTN_BG_DARK = 0x00000000
        private const val DEFAULT_MAIN_SCREEN_CORNER_BTN_ICON_LIGHT =
            DEFAULT_WIDGET_TEXT_COLOR_LIGHT
        private const val DEFAULT_MAIN_SCREEN_CORNER_BTN_ICON_DARK =
            DEFAULT_WIDGET_TEXT_COLOR_DARK

        /** Default main-screen canvas behind panels (matches app theme background). */
        private const val DEFAULT_MAIN_SCREEN_CANVAS_BG_LIGHT = LIGHT_THEME_BACKGROUND_COLOR_PRESET_2_INT
        private const val DEFAULT_MAIN_SCREEN_CANVAS_BG_DARK = DARK_THEME_BACKGROUND_COLOR_PRESET_2_INT

        /** Default ARGB for each preset slot when nothing is stored yet in DataStore. */
        val DEFAULT_WIDGET_COLOR_PRESET_SLOTS: List<Int> = listOf(
            (0xFF131C2D).toInt(),
            (0xFF292F3B).toInt(),
            (0xFF1A1C1E).toInt(),
            (0xFFE2E2E6).toInt(),
            (0xFFF8F9FA).toInt(),
            Color.WHITE,
            (0xFF2180F3).toInt(), // WidgetActiveColors.Primary
            (0xFFF3A721).toInt(), // WidgetActiveColors.Secondary
        )

        // Кэш ключей для производительности
        private val stringKeysCache = mutableMapOf<String, Preferences.Key<String>>()

        // Ключ для сохранения конфигурации виджетов
        private val DASHBOARD_WIDGETS_KEY = stringPreferencesKey("${KEY_PREFIX}dashboard_widgets")

        private val ACTIVE_TRIP_CUSTOM_WIDGET_LAYOUT_KEY =
            stringPreferencesKey("${KEY_PREFIX}active_trip_custom_widget_layout")

        private val ACTIVE_TRIP_SIMPLE_WIDGET_LAYOUT_KEY =
            stringPreferencesKey("${KEY_PREFIX}active_trip_simple_widget_layout")

        private val LEFT_MENU_LAYOUT_KEY =
            stringPreferencesKey("${KEY_PREFIX}left_menu_layout")

    }

    // Flow для конфигурации виджетов
    val dashboardWidgetsFlow: Flow<List<FloatingDashboardWidgetConfig>> = context.settingsDataStore.data
        .map { preferences ->
            parseWidgetConfigsFromString(preferences[DASHBOARD_WIDGETS_KEY] ?: "")
        }
        .distinctUntilChanged()

    val floatingDashboardsFlow: Flow<List<FloatingDashboardConfig>> = context.settingsDataStore.data
        .map { preferences ->
            val rawJson = preferences[getStringKey(FLOATING_DASHBOARDS_LIST_KEY)] ?: ""
            parseFloatingDashboardsJson(rawJson)
        }
        .distinctUntilChanged()

    val usageStatsHideFloatingWatchPackagesFlow: Flow<Set<String>> = context.settingsDataStore.data
        .map { preferences ->
            stringSetFromJsonArray(
                preferences[getStringKey(USAGE_STATS_HIDE_FLOATING_WATCH_PACKAGES_KEY)] ?: "[]"
            )
        }
        .distinctUntilChanged()

    val usageStatsHideFloatingPanelIdsFlow: Flow<Set<String>> = context.settingsDataStore.data
        .map { preferences ->
            stringSetFromJsonArray(
                preferences[getStringKey(USAGE_STATS_HIDE_FLOATING_PANEL_IDS_KEY)] ?: "[]"
            )
        }
        .distinctUntilChanged()

    val usageStatsForceShowFloatingWatchPackagesFlow: Flow<Set<String>> = context.settingsDataStore.data
        .map { preferences ->
            stringSetFromJsonArray(
                preferences[getStringKey(USAGE_STATS_FORCE_SHOW_WATCH_PACKAGES_KEY)] ?: "[]"
            )
        }
        .distinctUntilChanged()

    val usageStatsForceShowFloatingPanelIdsFlow: Flow<Set<String>> = context.settingsDataStore.data
        .map { preferences ->
            stringSetFromJsonArray(
                preferences[getStringKey(USAGE_STATS_FORCE_SHOW_PANEL_IDS_KEY)] ?: "[]"
            )
        }
        .distinctUntilChanged()

    val mainScreenDashboardsFlow: Flow<List<MainScreenPanelConfig>> = context.settingsDataStore.data
        .map { preferences ->
            val rawJson = preferences[getStringKey(MAIN_SCREEN_DASHBOARDS_LIST_KEY)] ?: ""
            val pageCount = preferences[MAIN_SCREEN_PAGE_COUNT_KEY] ?: DEFAULT_MAIN_SCREEN_PAGE_COUNT
            parseMainScreenDashboardsJson(rawJson, pageCount)
        }
        .distinctUntilChanged()

    /** JSON for [vad.dashing.tbox.trip.ActiveTripCustomWidgetLayout]; empty string means defaults. */
    val activeTripCustomWidgetLayoutJsonFlow: Flow<String> = context.settingsDataStore.data
        .map { preferences -> preferences[ACTIVE_TRIP_CUSTOM_WIDGET_LAYOUT_KEY].orEmpty() }
        .distinctUntilChanged()

    /** JSON for simplified trip tile layout; empty string means [ActiveTripCustomWidgetLayout.defaultSimplified]. */
    val activeTripSimpleWidgetLayoutJsonFlow: Flow<String> = context.settingsDataStore.data
        .map { preferences -> preferences[ACTIVE_TRIP_SIMPLE_WIDGET_LAYOUT_KEY].orEmpty() }
        .distinctUntilChanged()

    val autoModemRestartFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[AUTO_MODEM_RESTART_KEY] ?: false }
        .distinctUntilChanged()

    val widgetShowIndicatorFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[WIDGET_SHOW_INDICATOR] ?: false }
        .distinctUntilChanged()

    val widgetShowLocIndicatorFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[WIDGET_SHOW_LOC_INDICATOR] ?: false }
        .distinctUntilChanged()

    val mockLocationFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[MOCK_LOCATION] ?: false }
        .distinctUntilChanged()

    val mockLocationPeriodMsFlow: Flow<Long> = context.settingsDataStore.data
        .map { preferences ->
            (preferences[MOCK_LOCATION_PERIOD_MS] ?: 1000L).coerceIn(200L, 60_000L)
        }
        .distinctUntilChanged()

    val mockCanSpeedModeFlow: Flow<vad.dashing.tbox.location.MockCanSpeedMode> =
        context.settingsDataStore.data
            .map { preferences ->
                vad.dashing.tbox.location.MockCanSpeedMode.fromStorage(
                    preferences[MOCK_CAN_SPEED_MODE_KEY],
                )
            }
            .distinctUntilChanged()

    val mockHeadingSourceFlow: Flow<vad.dashing.tbox.location.MockHeadingSource> =
        context.settingsDataStore.data
            .map { preferences ->
                vad.dashing.tbox.location.MockHeadingSource.fromStorage(
                    preferences[MOCK_HEADING_SOURCE_KEY],
                )
            }
            .distinctUntilChanged()

    val mockJunkFixFilterFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[MOCK_JUNK_FIX_FILTER_KEY] ?: true }
        .distinctUntilChanged()

    val constantAutoCalibEnabledFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[CONSTANT_AUTO_CALIB_ENABLED_KEY] ?: false }
        .distinctUntilChanged()

    val mockConsiderReverseFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[MOCK_CONSIDER_REVERSE_KEY] ?: true }
        .distinctUntilChanged()

    val geoCalibNeedsFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[GEO_CALIB_NEEDS_KEY] ?: false }
        .distinctUntilChanged()

    val geoCalibLastAtMsFlow: Flow<Long> = context.settingsDataStore.data
        .map { preferences -> preferences[GEO_CALIB_LAST_AT_MS_KEY] ?: 0L }
        .distinctUntilChanged()

    val autoTboxRebootFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[AUTO_TBOX_REBOOT_KEY] ?: false }
        .distinctUntilChanged()

    val autoSuspendTboxAppFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[AUTO_SUSPEND_TBOX_APP_KEY] ?: false }
        .distinctUntilChanged()

    val autoStopTboxAppFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[AUTO_STOP_TBOX_APP_KEY] ?: false }
        .distinctUntilChanged()

    val autoSuspendTboxMdcFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[AUTO_SUSPEND_TBOX_MDC_KEY] ?: false }
        .distinctUntilChanged()

    val autoStopTboxMdcFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[AUTO_STOP_TBOX_MDC_KEY] ?: false }
        .distinctUntilChanged()

    val autoSuspendTboxSwdFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[AUTO_SUSPEND_TBOX_SWD_KEY] ?: false }
        .distinctUntilChanged()

    val autoSuspendTboxLocFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[AUTO_SUSPEND_TBOX_LOC_KEY] ?: false }
        .distinctUntilChanged()

    val autoPreventTboxRestartFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[AUTO_PREVENT_TBOX_RESTART_KEY] ?: false }
        .distinctUntilChanged()

    val getVoltagesFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[GET_VOLTAGES_KEY] ?: false }
        .distinctUntilChanged()

    val getCanFrameFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[GET_CAN_FRAME_KEY] ?: true }
        .distinctUntilChanged()

    /** When true, skip TBox UDP / tbox-proxy connection (default false). */
    val noTboxConnectFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[NO_TBOX_CONNECT_KEY] ?: false }
        .distinctUntilChanged()

    val getCycleSignalFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[GET_CYCLE_SIGNAL_KEY] ?: false }
        .distinctUntilChanged()

    val locationSourceFlow: Flow<vad.dashing.tbox.esp.LocationSource> = context.settingsDataStore.data
        .map { preferences -> resolveLocationSource(preferences) }
        .distinctUntilChanged()

    /** Legacy: true when location source is TBox (subscribe to LOC). */
    val getLocDataFlow: Flow<Boolean> = locationSourceFlow
        .map { it == vad.dashing.tbox.esp.LocationSource.TBOX }
        .distinctUntilChanged()

    val espCompanionEnabledFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[ESP_COMPANION_ENABLED_KEY] ?: false }
        .distinctUntilChanged()

    val usbGnssDeviceIdFlow: Flow<String> = context.settingsDataStore.data
        .map { preferences -> preferences[USB_GNSS_DEVICE_ID_KEY].orEmpty() }
        .distinctUntilChanged()

    val usbGnssBaudFlow: Flow<Int> = context.settingsDataStore.data
        .map { preferences ->
            val raw = preferences[USB_GNSS_BAUD_KEY]
                ?: vad.dashing.tbox.usbgnss.UsbGnssDeviceIds.DEFAULT_BAUD
            if (raw in vad.dashing.tbox.usbgnss.UsbGnssDeviceIds.BAUD_OPTIONS) {
                raw
            } else {
                vad.dashing.tbox.usbgnss.UsbGnssDeviceIds.DEFAULT_BAUD
            }
        }
        .distinctUntilChanged()

    /** Default false — many modules already emit VTG; avoid surprise CONFIG. */
    val usbGnssRequestVtgFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[USB_GNSS_REQUEST_VTG_KEY] ?: false }
        .distinctUntilChanged()

    /** Default false — many modules already emit ZDA; avoid surprise CONFIG. */
    val usbGnssRequestZdaFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[USB_GNSS_REQUEST_ZDA_KEY] ?: false }
        .distinctUntilChanged()

    /** Default false — GST often off until explicitly enabled on Unicore. */
    val usbGnssRequestGstFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[USB_GNSS_REQUEST_GST_KEY] ?: false }
        .distinctUntilChanged()

    /** Companion-only; independent from [usbGnssRequestVtgFlow]. Default false. */
    val espUm980RequestVtgFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[ESP_UM980_REQUEST_VTG_KEY] ?: false }
        .distinctUntilChanged()

    /** Companion-only; independent from [usbGnssRequestZdaFlow]. Default false. */
    val espUm980RequestZdaFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[ESP_UM980_REQUEST_ZDA_KEY] ?: false }
        .distinctUntilChanged()

    /** Companion-only; independent from [usbGnssRequestGstFlow]. Default false. */
    val espUm980RequestGstFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[ESP_UM980_REQUEST_GST_KEY] ?: false }
        .distinctUntilChanged()

    /** USB GNSS module identity map keyed by stable device id (`vid:pid[:serial]`). */
    val usbGnssModuleByDeviceFlow: Flow<Map<String, vad.dashing.tbox.usbgnss.GnssModuleIdentity>> =
        context.settingsDataStore.data
            .map { preferences ->
                vad.dashing.tbox.usbgnss.GnssModuleIdentityCodec.decodeMap(
                    preferences[USB_GNSS_MODULE_BY_DEVICE_KEY],
                )
            }
            .distinctUntilChanged()

    val expertModeFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[EXPERT_MODE] ?: false }
        .distinctUntilChanged()

    val permissionsIntroSeenFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[PERMISSIONS_INTRO_SEEN_KEY] ?: false }
        .distinctUntilChanged()

    val leftMenuVisibleFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[LEFT_MENU_VISIBLE] ?: true }
        .distinctUntilChanged()

    val selectedTabFlow: Flow<String> = context.settingsDataStore.data
        .map { preferences ->
            vad.dashing.tbox.ui.LeftMenuLayout.parseSelectedTabKey(
                preferences[SELECTED_TAB_KEY],
            )
        }
        .distinctUntilChanged()

    val leftMenuLayoutJsonFlow: Flow<String> = context.settingsDataStore.data
        .map { preferences -> preferences[LEFT_MENU_LAYOUT_KEY].orEmpty() }
        .distinctUntilChanged()

    val mainScreenSettingsButtonFlow: Flow<MainScreenSettingsButtonPosition> =
        context.settingsDataStore.data
            .map { preferences ->
                parseMainScreenSettingsButtonJson(
                    preferences[getStringKey(MAIN_SCREEN_SETTINGS_BUTTON_KEY)] ?: ""
                )
            }
            .distinctUntilChanged()

    val mainScreenAddButtonFlow: Flow<MainScreenAddButtonPosition> =
        context.settingsDataStore.data
            .map { preferences ->
                parseMainScreenAddButtonJson(
                    preferences[getStringKey(MAIN_SCREEN_ADD_BUTTON_KEY)] ?: ""
                )
            }
            .distinctUntilChanged()

    val mainScreenPageCountFlow: Flow<Int> = context.settingsDataStore.data
        .map { preferences ->
            PagingStateNormalizer.normalizePageCount(
                preferences[MAIN_SCREEN_PAGE_COUNT_KEY] ?: DEFAULT_MAIN_SCREEN_PAGE_COUNT
            )
        }
        .distinctUntilChanged()

    val mainScreenCurrentPageFlow: Flow<Int> = context.settingsDataStore.data
        .map { preferences ->
            val pageCount = PagingStateNormalizer.normalizePageCount(
                preferences[MAIN_SCREEN_PAGE_COUNT_KEY] ?: DEFAULT_MAIN_SCREEN_PAGE_COUNT
            )
            PagingStateNormalizer.normalizeCurrentPage(
                preferences[MAIN_SCREEN_CURRENT_PAGE_KEY] ?: DEFAULT_MAIN_SCREEN_CURRENT_PAGE,
                pageCount,
            )
        }
        .distinctUntilChanged()

    /**
     * Window-mode page when the user has set one; `null` means “never set” —
     * on first enter, keep the current (normal) page.
     */
    val mainScreenWindowModeCurrentPageFlow: Flow<Int?> = context.settingsDataStore.data
        .map { preferences ->
            if (!preferences.contains(MAIN_SCREEN_WINDOW_MODE_CURRENT_PAGE_KEY)) {
                null
            } else {
                val pageCount = PagingStateNormalizer.normalizePageCount(
                    preferences[MAIN_SCREEN_PAGE_COUNT_KEY] ?: DEFAULT_MAIN_SCREEN_PAGE_COUNT
                )
                PagingStateNormalizer.normalizeCurrentPage(
                    preferences[MAIN_SCREEN_WINDOW_MODE_CURRENT_PAGE_KEY]
                        ?: DEFAULT_MAIN_SCREEN_CURRENT_PAGE,
                    pageCount,
                )
            }
        }
        .distinctUntilChanged()

    /** Theme-independent collapsed flags keyed by panel id. */
    val panelCollapseStatesFlow: Flow<Map<String, Boolean>> = context.settingsDataStore.data
        .map { preferences ->
            PanelCollapseStates.parse(preferences[PANEL_COLLAPSE_STATES_KEY])
        }
        .distinctUntilChanged()

    val mainScreenPagePrevButtonFlow: Flow<MainScreenPagePrevButtonPosition> =
        context.settingsDataStore.data
            .map { preferences ->
                parseMainScreenPagePrevButtonJson(
                    preferences[getStringKey(MAIN_SCREEN_PAGE_PREV_BUTTON_KEY)] ?: ""
                )
            }
            .distinctUntilChanged()

    val mainScreenPageNextButtonFlow: Flow<MainScreenPageNextButtonPosition> =
        context.settingsDataStore.data
            .map { preferences ->
                parseMainScreenPageNextButtonJson(
                    preferences[getStringKey(MAIN_SCREEN_PAGE_NEXT_BUTTON_KEY)] ?: ""
                )
            }
            .distinctUntilChanged()

    val mainScreenWindowModeGeometryFlow: Flow<MainScreenWindowModeGeometry?> =
        context.settingsDataStore.data
            .map { preferences ->
                parseMainScreenWindowModeGeometryJson(
                    preferences[getStringKey(MAIN_SCREEN_WINDOW_MODE_GEOMETRY_KEY)] ?: ""
                )
            }
            .distinctUntilChanged()

    val mainScreenWindowModeExitButtonFlow: Flow<MainScreenWindowModeExitButtonPosition> =
        context.settingsDataStore.data
            .map { preferences ->
                parseMainScreenWindowModeExitButtonJson(
                    preferences[getStringKey(MAIN_SCREEN_WINDOW_MODE_EXIT_BUTTON_KEY)] ?: "",
                    default = MainScreenWindowModeExitButtonPosition.Default,
                )
            }
            .distinctUntilChanged()

    val mainScreenWindowModeRestoreButtonFlow: Flow<MainScreenWindowModeExitButtonPosition> =
        context.settingsDataStore.data
            .map { preferences ->
                parseMainScreenWindowModeExitButtonJson(
                    preferences[getStringKey(MAIN_SCREEN_WINDOW_MODE_RESTORE_BUTTON_KEY)] ?: "",
                    default = MainScreenWindowModeExitButtonPosition.RestoreFullscreenDefault,
                )
            }
            .distinctUntilChanged()

    /**
     * When true (default), main-screen window overlay fills the complementary area beside the
     * freeform companion (mapped from activity/virtual display into overlay/full-screen coords).
     * When false, uses [mainScreenWindowModeGeometryFlow].
     */
    val mainScreenWindowModeAutoGeometryFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[MAIN_SCREEN_WINDOW_MODE_AUTO_GEOMETRY_KEY] ?: true }
        .distinctUntilChanged()

    val activeThemeUriFlow: Flow<String> = context.settingsDataStore.data
        .map { preferences -> preferences[ACTIVE_THEME_URI_KEY] ?: "" }
        .distinctUntilChanged()

    val activeThemeFingerprintFlow: Flow<String> = context.settingsDataStore.data
        .map { preferences -> preferences[ACTIVE_THEME_FINGERPRINT_KEY] ?: "" }
        .distinctUntilChanged()

    val activeThemeSectionsFlow: Flow<Set<ThemeSection>> = context.settingsDataStore.data
        .map { preferences ->
            ThemeSection.parseJsonArray(
                runCatching { JSONArray(preferences[ACTIVE_THEME_SECTIONS_KEY].orEmpty()) }.getOrNull()
            )
        }
        .distinctUntilChanged()

    val activeThemeApplyTargetsFlow: Flow<Set<ThemeApplyTarget>> = context.settingsDataStore.data
        .map { preferences ->
            val targets = ThemeApplyTarget.parseJsonArray(
                runCatching { JSONArray(preferences[ACTIVE_THEME_APPLY_TARGETS_KEY].orEmpty()) }.getOrNull()
            )
            val sections = ThemeSection.parseJsonArray(
                runCatching { JSONArray(preferences[ACTIVE_THEME_SECTIONS_KEY].orEmpty()) }.getOrNull()
            )
            ThemeApplyTarget.resolveActive(targets, sections)
        }
        .distinctUntilChanged()

    val driveModeThemePathsFlow: Flow<Map<Int, String>> = context.settingsDataStore.data
        .map { preferences -> parseDriveModeThemePathsJson(preferences[DRIVE_MODE_THEME_PATHS_KEY].orEmpty()) }
        .distinctUntilChanged()

    /** After device boot, open [MainActivity] on the main home screen (tab 100) when enabled. */
    val mainScreenOpenOnBootFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[MAIN_SCREEN_OPEN_ON_BOOT_KEY] ?: false }
        .distinctUntilChanged()

    /** Delay before opening [MainActivity] after boot auto-start, in seconds. */
    val mainScreenOpenOnBootDelaySecondsFlow: Flow<Int> = context.settingsDataStore.data
        .map { preferences ->
            preferences[MAIN_SCREEN_OPEN_ON_BOOT_DELAY_SECONDS_KEY]
                ?: DEFAULT_MAIN_SCREEN_OPEN_ON_BOOT_DELAY_SECONDS
        }
        .map {
            it.coerceIn(
                MIN_MAIN_SCREEN_OPEN_ON_BOOT_DELAY_SECONDS,
                MAX_MAIN_SCREEN_OPEN_ON_BOOT_DELAY_SECONDS
            )
        }
        .distinctUntilChanged()

    val mainScreenWallpaperLightFolderUriFlow: Flow<String> = context.settingsDataStore.data
        .map { preferences -> preferences[MAIN_SCREEN_WALLPAPER_LIGHT_FOLDER_URI_KEY] ?: "" }
        .distinctUntilChanged()

    val mainScreenWallpaperDarkFolderUriFlow: Flow<String> = context.settingsDataStore.data
        .map { preferences -> preferences[MAIN_SCREEN_WALLPAPER_DARK_FOLDER_URI_KEY] ?: "" }
        .distinctUntilChanged()

    val mainScreenWallpaperSelectionByPageFlow: Flow<MainScreenWallpaperSelectionsByPage> =
        context.settingsDataStore.data
            .map { preferences ->
                MainScreenWallpaperSelectionsByPage.fromDataStoreJson(
                    preferences[MAIN_SCREEN_WALLPAPER_SELECTION_BY_PAGE_KEY],
                )
            }
            .distinctUntilChanged()

    /** `true`: fill screen with Crop; `false`: Fit (whole image, possible side bars). */
    val mainScreenWallpaperCropFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[MAIN_SCREEN_WALLPAPER_CROP_KEY] ?: false }
        .distinctUntilChanged()

    val launcherAppIconRevisionFlow: Flow<Int> = context.settingsDataStore.data
        .map { preferences -> preferences[LAUNCHER_APP_ICON_REVISION_KEY] ?: 0 }
        .distinctUntilChanged()

    val httpRequestIconRevisionFlow: Flow<Int> = context.settingsDataStore.data
        .map { preferences -> preferences[HTTP_REQUEST_ICON_REVISION_KEY] ?: 0 }
        .distinctUntilChanged()

    val tileBackgroundImageRevisionFlow: Flow<Int> = context.settingsDataStore.data
        .map { preferences -> preferences[TILE_BACKGROUND_IMAGE_REVISION_KEY] ?: 0 }
        .distinctUntilChanged()

    val panelBackgroundImageRevisionFlow: Flow<Int> = context.settingsDataStore.data
        .map { preferences -> preferences[PANEL_BACKGROUND_IMAGE_REVISION_KEY] ?: 0 }
        .distinctUntilChanged()

    val mainScreenCornerButtonSizeDpFlow: Flow<Int> = context.settingsDataStore.data
        .map { preferences ->
            preferences[MAIN_SCREEN_CORNER_BUTTON_SIZE_KEY]
                ?: DEFAULT_MAIN_SCREEN_CORNER_BUTTON_SIZE_DP
        }
        .map { it.coerceIn(MIN_MAIN_SCREEN_CORNER_BUTTON_SIZE_DP, 100) }
        .distinctUntilChanged()

    val mainScreenCornerButtonBackgroundLightFlow: Flow<Int> = context.settingsDataStore.data
        .map { preferences ->
            preferences[MAIN_SCREEN_CORNER_BTN_BG_LIGHT_KEY]
                ?: DEFAULT_MAIN_SCREEN_CORNER_BTN_BG_LIGHT
        }
        .distinctUntilChanged()

    val mainScreenCornerButtonBackgroundDarkFlow: Flow<Int> = context.settingsDataStore.data
        .map { preferences ->
            preferences[MAIN_SCREEN_CORNER_BTN_BG_DARK_KEY]
                ?: DEFAULT_MAIN_SCREEN_CORNER_BTN_BG_DARK
        }
        .distinctUntilChanged()

    val mainScreenCornerButtonIconLightFlow: Flow<Int> = context.settingsDataStore.data
        .map { preferences ->
            preferences[MAIN_SCREEN_CORNER_BTN_ICON_LIGHT_KEY]
                ?: DEFAULT_MAIN_SCREEN_CORNER_BTN_ICON_LIGHT
        }
        .distinctUntilChanged()

    val mainScreenCornerButtonIconDarkFlow: Flow<Int> = context.settingsDataStore.data
        .map { preferences ->
            preferences[MAIN_SCREEN_CORNER_BTN_ICON_DARK_KEY]
                ?: DEFAULT_MAIN_SCREEN_CORNER_BTN_ICON_DARK
        }
        .distinctUntilChanged()

    val mainScreenCanvasBackgroundLightFlow: Flow<Int> = context.settingsDataStore.data
        .map { preferences ->
            preferences[MAIN_SCREEN_CANVAS_BG_LIGHT_KEY] ?: DEFAULT_MAIN_SCREEN_CANVAS_BG_LIGHT
        }
        .distinctUntilChanged()

    val mainScreenCanvasBackgroundDarkFlow: Flow<Int> = context.settingsDataStore.data
        .map { preferences ->
            preferences[MAIN_SCREEN_CANVAS_BG_DARK_KEY] ?: DEFAULT_MAIN_SCREEN_CANVAS_BG_DARK
        }
        .distinctUntilChanged()

    /** Eight global ARGB colors for quick pick in color editors; missing keys use [DEFAULT_WIDGET_COLOR_PRESET_SLOTS]. */
    val widgetColorPresetSlotsFlow: Flow<List<Int>> = context.settingsDataStore.data
        .map { preferences ->
            List(WIDGET_COLOR_PRESET_SLOT_COUNT) { i ->
                preferences[WIDGET_COLOR_PRESET_KEYS[i]] ?: DEFAULT_WIDGET_COLOR_PRESET_SLOTS[i]
            }
        }
        .distinctUntilChanged()

    // String flows
    val logLevelFlow: Flow<String> = context.settingsDataStore.data
        .map { preferences -> preferences[LOG_LEVEL_KEY] ?: DEFAULT_LOG_LEVEL }
        .distinctUntilChanged()

    val dashboardRowsFlow: Flow<Int> = context.settingsDataStore.data
        .map { preferences -> preferences[DASHBOARD_ROWS_KEY] ?: 3 }
        .distinctUntilChanged()

    val dashboardColsFlow: Flow<Int> = context.settingsDataStore.data
        .map { preferences -> preferences[DASHBOARD_COLS_KEY] ?: 4 }
        .distinctUntilChanged()

    val dashboardChartFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[DASHBOARD_CHART_KEY] ?: false }
        .distinctUntilChanged()

    val dashboardGridSpacingDpFlow: Flow<Int> = context.settingsDataStore.data
        .map { preferences ->
            normalizePanelGridSpacingDp(
                preferences[DASHBOARD_GRID_SPACING_KEY] ?: DEFAULT_MAIN_TAB_DASHBOARD_GRID_SPACING_DP
            )
        }
        .distinctUntilChanged()

    val floatingPanelsLayoutSnapDpFlow: Flow<Int> = context.settingsDataStore.data
        .map { preferences ->
            normalizePanelLayoutSnapDp(
                preferences[FLOATING_PANELS_LAYOUT_SNAP_DP_KEY] ?: DEFAULT_PANEL_LAYOUT_SNAP_DP
            )
        }
        .distinctUntilChanged()

    val mainScreenPanelsLayoutSnapDpFlow: Flow<Int> = context.settingsDataStore.data
        .map { preferences ->
            normalizePanelLayoutSnapDp(
                preferences[MAIN_SCREEN_PANELS_LAYOUT_SNAP_DP_KEY] ?: DEFAULT_PANEL_LAYOUT_SNAP_DP
            )
        }
        .distinctUntilChanged()

    val mainScreenPanelsLayoutSnapEnabledFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[MAIN_SCREEN_PANELS_LAYOUT_SNAP_ENABLED_KEY] ?: false }
        .distinctUntilChanged()

    val mainScreenShowLayoutGridFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[MAIN_SCREEN_SHOW_LAYOUT_GRID_KEY] ?: false }
        .distinctUntilChanged()

    val canDataSaveCountFlow: Flow<Int> = context.settingsDataStore.data
        .map { preferences -> preferences[CAN_DATA_SAVE_COUNT_KEY] ?: DEFAULT_CAN_DATA_SAVE_COUNT }
        .distinctUntilChanged()

    val fuelTankLitersFlow: Flow<Int> = context.settingsDataStore.data
        .map { preferences -> preferences[FUEL_TANK_LITERS_KEY] ?: DEFAULT_FUEL_TANK_LITERS }

    val speedLimiterTargetKmhFlow: Flow<Int> = context.settingsDataStore.data
        .map { preferences ->
            SlaSpeedLimitDomain.clampLimiterTargetKmh(
                preferences[SPEED_LIMITER_TARGET_KMH_KEY] ?: SlaSpeedLimitDomain.SPEED_LIMITER_KMH_DEFAULT
            )
        }
        .distinctUntilChanged()

    val fuelCalibrationJsonFlow: Flow<String> = context.settingsDataStore.data
        .map { preferences -> preferences[FUEL_CALIBRATION_JSON_KEY].orEmpty() }
        .distinctUntilChanged()

    val fuelCalibrationZoneCountFlow: Flow<Int> = context.settingsDataStore.data
        .map { preferences ->
            preferences[FUEL_CALIBRATION_ZONE_COUNT_KEY] ?: DEFAULT_FUEL_CALIBRATION_ZONE_COUNT
        }
        .distinctUntilChanged()

    val fuelCalibrationMaturityThresholdFlow: Flow<Int> = context.settingsDataStore.data
        .map { preferences ->
            preferences[FUEL_CALIBRATION_MATURITY_THRESHOLD_KEY]
                ?: DEFAULT_FUEL_CALIBRATION_MATURITY_THRESHOLD
        }
        .distinctUntilChanged()

    val fuelPriceFuelIdFlow: Flow<Int> = context.settingsDataStore.data
        .map { preferences -> preferences[FUEL_PRICE_FUEL_ID_KEY] ?: FuelTypes.DEFAULT_FUEL_ID }
        .distinctUntilChanged()

    val splitTripTimeMinutesFlow: Flow<Int> = context.settingsDataStore.data
        .map { preferences -> preferences[SPLIT_TRIP_TIME_MINUTES_KEY] ?: DEFAULT_SPLIT_TRIP_TIME_MINUTES }
        .distinctUntilChanged()

    val trackRefuelsFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[TRACK_REFUELS_KEY] ?: DEFAULT_TRACK_REFUELS }
        .distinctUntilChanged()

    val wheelPressurePersistAcrossStopsFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[WHEEL_PRESSURE_PERSIST_ACROSS_STOPS_KEY] ?: false }
        .distinctUntilChanged()

    val uiClickSoundsFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[UI_CLICK_SOUNDS_KEY] ?: false }
        .distinctUntilChanged()

    val appFontFamilyIdFlow: Flow<Int> = context.settingsDataStore.data
        .map { preferences ->
            TboxFontFamily.fromId(preferences[APP_FONT_FAMILY_ID_KEY] ?: TboxFontFamily.Default.id).id
        }
        .distinctUntilChanged()

    val updateChannelFlow: Flow<vad.dashing.tbox.update.UpdateChannel> = context.settingsDataStore.data
        .map { preferences ->
            vad.dashing.tbox.update.UpdateChannel.fromStorageValue(preferences[UPDATE_CHANNEL_KEY])
        }
        .distinctUntilChanged()

    val updateCheckEnabledFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[UPDATE_CHECK_ENABLED_KEY] ?: true }
        .distinctUntilChanged()

    val headUnitCanModeFlow: Flow<HeadUnitCanMode> = context.settingsDataStore.data
        .map { preferences ->
            HeadUnitCanMode.fromStorageValue(preferences[HEAD_UNIT_CAN_MODE_KEY])
        }
        .distinctUntilChanged()

    val canAutoBindEnabledFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[CAN_AUTO_BIND_ENABLED_KEY] ?: true }
        .distinctUntilChanged()

    val canAutoBindLockedFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[CAN_AUTO_BIND_LOCKED_KEY] ?: false }
        .distinctUntilChanged()

    val canAutoBindLastPrimaryModeFlow: Flow<HeadUnitCanMode?> = context.settingsDataStore.data
        .map { preferences ->
            preferences[CAN_AUTO_BIND_LAST_PRIMARY_MODE_KEY]?.let(HeadUnitCanMode::fromStorageValue)
        }
        .distinctUntilChanged()

    val canAutoBindLastResultFlow: Flow<String> = context.settingsDataStore.data
        .map { preferences -> preferences[CAN_AUTO_BIND_LAST_RESULT_KEY].orEmpty() }
        .distinctUntilChanged()

    private fun stringSetFromJsonArray(raw: String): Set<String> {
        if (raw.isBlank()) return emptySet()
        return try {
            val a = JSONArray(raw)
            buildSet {
                for (i in 0 until a.length()) {
                    val s = a.optString(i).trim()
                    if (s.isNotEmpty()) add(s)
                }
            }
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun stringSetToJsonArray(values: Set<String>): String {
        val a = JSONArray()
        values.sorted().forEach { a.put(it) }
        return a.toString()
    }

    /**
     * Single DataStore read for all keys backing [BackgroundService] setting [kotlinx.coroutines.flow.StateFlow]s.
     */
    suspend fun readBackgroundServiceSettingsSnapshot(): BackgroundServiceSettingsSnapshot =
        context.settingsDataStore.data.first().let { backgroundSnapshotFromPreferences(it) }

    /** Exposed for unit tests mapping empty/custom [Preferences] without a DataStore. */
    internal fun backgroundSnapshotFromPreferences(preferences: Preferences): BackgroundServiceSettingsSnapshot {
        val floatingRaw = preferences[getStringKey(FLOATING_DASHBOARDS_LIST_KEY)] ?: ""
        return BackgroundServiceSettingsSnapshot(
            autoModemRestart = preferences[AUTO_MODEM_RESTART_KEY] ?: false,
            autoTboxReboot = preferences[AUTO_TBOX_REBOOT_KEY] ?: false,
            autoSuspendTboxApp = preferences[AUTO_SUSPEND_TBOX_APP_KEY] ?: false,
            autoStopTboxApp = preferences[AUTO_STOP_TBOX_APP_KEY] ?: false,
            autoSuspendTboxMdc = preferences[AUTO_SUSPEND_TBOX_MDC_KEY] ?: false,
            autoStopTboxMdc = preferences[AUTO_STOP_TBOX_MDC_KEY] ?: false,
            autoSuspendTboxSwd = preferences[AUTO_SUSPEND_TBOX_SWD_KEY] ?: false,
            autoSuspendTboxLoc = preferences[AUTO_SUSPEND_TBOX_LOC_KEY] ?: false,
            autoPreventTboxRestart = preferences[AUTO_PREVENT_TBOX_RESTART_KEY] ?: false,
            getCanFrame = preferences[GET_CAN_FRAME_KEY] ?: true,
            getCycleSignal = preferences[GET_CYCLE_SIGNAL_KEY] ?: false,
            locationSource = resolveLocationSource(preferences),
            getLocData = resolveLocationSource(preferences) == vad.dashing.tbox.esp.LocationSource.TBOX,
            espCompanionEnabled = preferences[ESP_COMPANION_ENABLED_KEY] ?: false,
            noTboxConnect = preferences[NO_TBOX_CONNECT_KEY] ?: false,
            usbGnssDeviceId = preferences[USB_GNSS_DEVICE_ID_KEY].orEmpty(),
            usbGnssBaud = run {
                val raw = preferences[USB_GNSS_BAUD_KEY]
                    ?: vad.dashing.tbox.usbgnss.UsbGnssDeviceIds.DEFAULT_BAUD
                if (raw in vad.dashing.tbox.usbgnss.UsbGnssDeviceIds.BAUD_OPTIONS) {
                    raw
                } else {
                    vad.dashing.tbox.usbgnss.UsbGnssDeviceIds.DEFAULT_BAUD
                }
            },
            usbGnssRequestVtg = preferences[USB_GNSS_REQUEST_VTG_KEY] ?: false,
            usbGnssRequestZda = preferences[USB_GNSS_REQUEST_ZDA_KEY] ?: false,
            usbGnssRequestGst = preferences[USB_GNSS_REQUEST_GST_KEY] ?: false,
            espUm980RequestVtg = preferences[ESP_UM980_REQUEST_VTG_KEY] ?: false,
            espUm980RequestZda = preferences[ESP_UM980_REQUEST_ZDA_KEY] ?: false,
            espUm980RequestGst = preferences[ESP_UM980_REQUEST_GST_KEY] ?: false,
            widgetShowIndicator = preferences[WIDGET_SHOW_INDICATOR] ?: false,
            widgetShowLocIndicator = preferences[WIDGET_SHOW_LOC_INDICATOR] ?: false,
            mockLocation = preferences[MOCK_LOCATION] ?: false,
            mockLocationPeriodMs = (preferences[MOCK_LOCATION_PERIOD_MS] ?: 1000L).coerceIn(200L, 60_000L),
            mockCanSpeedMode = vad.dashing.tbox.location.MockCanSpeedMode.fromStorage(
                preferences[MOCK_CAN_SPEED_MODE_KEY],
            ),
            mockHeadingSource = vad.dashing.tbox.location.MockHeadingSource.fromStorage(
                preferences[MOCK_HEADING_SOURCE_KEY],
            ),
            mockJunkFixFilter = preferences[MOCK_JUNK_FIX_FILTER_KEY] ?: true,
            floatingDashboards = parseFloatingDashboardsJson(floatingRaw),
            usageStatsHideFloatingWatchPackages = stringSetFromJsonArray(
                preferences[getStringKey(USAGE_STATS_HIDE_FLOATING_WATCH_PACKAGES_KEY)] ?: "[]"
            ),
            usageStatsHideFloatingPanelIds = stringSetFromJsonArray(
                preferences[getStringKey(USAGE_STATS_HIDE_FLOATING_PANEL_IDS_KEY)] ?: "[]"
            ),
            usageStatsForceShowFloatingWatchPackages = stringSetFromJsonArray(
                preferences[getStringKey(USAGE_STATS_FORCE_SHOW_WATCH_PACKAGES_KEY)] ?: "[]"
            ),
            usageStatsForceShowFloatingPanelIds = stringSetFromJsonArray(
                preferences[getStringKey(USAGE_STATS_FORCE_SHOW_PANEL_IDS_KEY)] ?: "[]"
            ),
            canDataSaveCount = preferences[CAN_DATA_SAVE_COUNT_KEY] ?: DEFAULT_CAN_DATA_SAVE_COUNT,
            fuelTankLiters = preferences[FUEL_TANK_LITERS_KEY] ?: DEFAULT_FUEL_TANK_LITERS,
            fuelCalibrationJson = preferences[FUEL_CALIBRATION_JSON_KEY].orEmpty(),
            fuelCalibrationZoneCount = preferences[FUEL_CALIBRATION_ZONE_COUNT_KEY]
                ?: DEFAULT_FUEL_CALIBRATION_ZONE_COUNT,
            fuelCalibrationMaturityThreshold = preferences[FUEL_CALIBRATION_MATURITY_THRESHOLD_KEY]
                ?: DEFAULT_FUEL_CALIBRATION_MATURITY_THRESHOLD,
            fuelPriceFuelId = preferences[FUEL_PRICE_FUEL_ID_KEY] ?: FuelTypes.DEFAULT_FUEL_ID,
            splitTripTimeMinutes = preferences[SPLIT_TRIP_TIME_MINUTES_KEY]
                ?: DEFAULT_SPLIT_TRIP_TIME_MINUTES,
            wheelPressurePersistAcrossStops = preferences[WHEEL_PRESSURE_PERSIST_ACROSS_STOPS_KEY] ?: false,
        )
    }

    // Suspend функции для сохранения настроек

    // Сохранение конфигурации виджетов
    suspend fun saveDashboardWidgets(config: List<FloatingDashboardWidgetConfig>) {
        context.settingsDataStore.edit { preferences ->
            preferences[DASHBOARD_WIDGETS_KEY] = serializeWidgetConfigs(config)
        }
    }

    suspend fun saveActiveTripCustomWidgetLayoutJson(json: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[ACTIVE_TRIP_CUSTOM_WIDGET_LAYOUT_KEY] = json
        }
    }

    suspend fun saveActiveTripSimpleWidgetLayoutJson(json: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[ACTIVE_TRIP_SIMPLE_WIDGET_LAYOUT_KEY] = json
        }
    }

    suspend fun saveAutoModemRestartSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[AUTO_MODEM_RESTART_KEY] = enabled
        }
    }

    suspend fun saveAutoTboxRebootSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[AUTO_TBOX_REBOOT_KEY] = enabled
        }
    }

    suspend fun saveWidgetShowIndicatorSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[WIDGET_SHOW_INDICATOR] = enabled
        }
    }

    suspend fun saveWidgetShowLocIndicatorSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[WIDGET_SHOW_LOC_INDICATOR] = enabled
        }
    }

    suspend fun saveMockLocationSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[MOCK_LOCATION] = enabled
        }
    }

    suspend fun saveMockLocationPeriodMs(periodMs: Long) {
        context.settingsDataStore.edit { preferences ->
            preferences[MOCK_LOCATION_PERIOD_MS] = periodMs.coerceIn(200L, 60_000L)
        }
    }

    suspend fun saveMockCanSpeedModeSetting(mode: vad.dashing.tbox.location.MockCanSpeedMode) {
        context.settingsDataStore.edit { preferences ->
            preferences[MOCK_CAN_SPEED_MODE_KEY] = mode.name
        }
    }

    suspend fun saveMockHeadingSourceSetting(source: vad.dashing.tbox.location.MockHeadingSource) {
        context.settingsDataStore.edit { preferences ->
            preferences[MOCK_HEADING_SOURCE_KEY] = source.name
        }
    }

    suspend fun saveMockJunkFixFilterSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[MOCK_JUNK_FIX_FILTER_KEY] = enabled
        }
    }

    suspend fun saveConstantAutoCalibEnabledSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[CONSTANT_AUTO_CALIB_ENABLED_KEY] = enabled
        }
    }

    suspend fun saveMockConsiderReverseSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[MOCK_CONSIDER_REVERSE_KEY] = enabled
        }
    }

    suspend fun loadGeoCalibrationState() {
        val prefs = context.settingsDataStore.data.first()
        var lastAt = prefs[GEO_CALIB_LAST_AT_MS_KEY] ?: 0L
        if (lastAt <= 0L) {
            lastAt = prefs[DRIVE_CALIB_AT_MS_KEY] ?: 0L
        }
        vad.dashing.tbox.location.GeoCalibrationState.load(
            needs = prefs[GEO_CALIB_NEEDS_KEY] ?: false,
            lastAtEpochMs = lastAt,
        )
    }

    /**
     * Persist need-calib flag. When [needs] is true, pass [onlyIfSuccessSerial] from
     * [vad.dashing.tbox.location.GeoCalibrationState.currentSuccessSerial] taken
     * **before** requesting — stale writers after a drive Save are ignored.
     */
    suspend fun saveGeoCalibNeeds(
        needs: Boolean,
        onlyIfSuccessSerial: Long? = null,
    ) {
        if (needs) {
            val serial = onlyIfSuccessSerial
                ?: vad.dashing.tbox.location.GeoCalibrationState.currentSuccessSerial()
            if (vad.dashing.tbox.location.GeoCalibrationState.currentSuccessSerial() != serial) {
                return
            }
            context.settingsDataStore.edit { preferences ->
                if (vad.dashing.tbox.location.GeoCalibrationState.currentSuccessSerial() != serial) {
                    return@edit
                }
                preferences[GEO_CALIB_NEEDS_KEY] = true
            }
            vad.dashing.tbox.location.GeoCalibrationState.applyNeedsIfSerialUnchanged(serial)
        } else {
            context.settingsDataStore.edit { preferences ->
                preferences[GEO_CALIB_NEEDS_KEY] = false
            }
            vad.dashing.tbox.location.GeoCalibrationState.setNeedsCalibration(false)
        }
    }

    suspend fun markGeoCalibrationSuccess(atEpochMs: Long = System.currentTimeMillis()) {
        context.settingsDataStore.edit { preferences ->
            preferences[GEO_CALIB_NEEDS_KEY] = false
            preferences[GEO_CALIB_LAST_AT_MS_KEY] = atEpochMs
        }
        vad.dashing.tbox.location.GeoCalibrationState.markCalibrated(atEpochMs)
    }

    suspend fun noteGeoCalibrationActivity(atEpochMs: Long = System.currentTimeMillis()) {
        context.settingsDataStore.edit { preferences ->
            preferences[GEO_CALIB_LAST_AT_MS_KEY] = atEpochMs
        }
        vad.dashing.tbox.location.GeoCalibrationState.noteCalibrationActivity(atEpochMs)
    }

    suspend fun loadMockLastGoodFix(): vad.dashing.tbox.location.MockLastGoodFix? {
        val raw = context.settingsDataStore.data.first()[MOCK_LAST_GOOD_FIX_KEY]
        return vad.dashing.tbox.location.MockLastGoodFix.fromJson(raw)
    }

    suspend fun saveMockLastGoodFix(fix: vad.dashing.tbox.location.MockLastGoodFix) {
        context.settingsDataStore.edit { preferences ->
            preferences[MOCK_LAST_GOOD_FIX_KEY] = fix.toJson()
        }
    }

    suspend fun loadGyroBiasOffsets(): vad.dashing.tbox.location.GyroBiasOffsets {
        val prefs = context.settingsDataStore.data.first()
        val tempRaw = prefs[GYRO_BIAS_YAW_TEMP_KEY]
        val temp = tempRaw?.takeIf { it.isFinite() }
        return vad.dashing.tbox.location.GyroBiasOffsets(
            yawDegPerSec = prefs[GYRO_BIAS_YAW_KEY] ?: 0f,
            pitchDegPerSec = prefs[GYRO_BIAS_PITCH_KEY] ?: 0f,
            rollDegPerSec = prefs[GYRO_BIAS_ROLL_KEY] ?: 0f,
            accelX = prefs[GYRO_BIAS_ACCEL_X_KEY] ?: 0f,
            accelY = prefs[GYRO_BIAS_ACCEL_Y_KEY] ?: 0f,
            accelZ = prefs[GYRO_BIAS_ACCEL_Z_KEY] ?: 0f,
            yawCalibTempC = temp,
        )
    }

    suspend fun saveGyroBiasOffsets(
        offsets: vad.dashing.tbox.location.GyroBiasOffsets,
        noteGeoCalibration: Boolean = false,
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[GYRO_BIAS_YAW_KEY] = offsets.yawDegPerSec
            preferences[GYRO_BIAS_PITCH_KEY] = offsets.pitchDegPerSec
            preferences[GYRO_BIAS_ROLL_KEY] = offsets.rollDegPerSec
            preferences[GYRO_BIAS_ACCEL_X_KEY] = offsets.accelX
            preferences[GYRO_BIAS_ACCEL_Y_KEY] = offsets.accelY
            preferences[GYRO_BIAS_ACCEL_Z_KEY] = offsets.accelZ
            val t = offsets.yawCalibTempC
            if (t != null && t.isFinite()) {
                preferences[GYRO_BIAS_YAW_TEMP_KEY] = t
            } else {
                preferences.remove(GYRO_BIAS_YAW_TEMP_KEY)
            }
        }
        vad.dashing.tbox.location.GyroBiasStore.update(offsets)
        if (noteGeoCalibration) {
            // Yaw-zero: timestamp only — does not clear CONSTANT need-calib flag.
            noteGeoCalibrationActivity()
        }
    }

    suspend fun loadDriveCalibrationOffsets(): vad.dashing.tbox.location.DriveCalibrationOffsets {
        val prefs = context.settingsDataStore.data.first()
        val sign = prefs[DRIVE_CALIB_YAW_SIGN_KEY] ?: 1
        val legacy = prefs[DRIVE_CALIB_YAW_SCALE_KEY] ?: 1f
        val left = prefs[DRIVE_CALIB_YAW_SCALE_LEFT_KEY] ?: legacy
        val right = prefs[DRIVE_CALIB_YAW_SCALE_RIGHT_KEY] ?: legacy
        return vad.dashing.tbox.location.DriveCalibrationOffsets(
            speedScale = prefs[DRIVE_CALIB_SPEED_SCALE_KEY] ?: 1f,
            yawScaleLeft = left,
            yawScaleRight = right,
            yawSign = if (sign < 0) -1 else 1,
            lagMs = prefs[DRIVE_CALIB_LAG_MS_KEY] ?: 0L,
            calibratedAtEpochMs = prefs[DRIVE_CALIB_AT_MS_KEY] ?: 0L,
            speedEstimated = prefs[DRIVE_CALIB_SPEED_EST_KEY] ?: false,
            yawEstimated = prefs[DRIVE_CALIB_YAW_EST_KEY] ?: false,
        )
    }

    suspend fun saveDriveCalibrationOffsets(
        offsets: vad.dashing.tbox.location.DriveCalibrationOffsets,
        noteGeoCalibration: Boolean = true,
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[DRIVE_CALIB_SPEED_SCALE_KEY] = offsets.speedScale
            preferences[DRIVE_CALIB_YAW_SCALE_LEFT_KEY] = offsets.yawScaleLeft
            preferences[DRIVE_CALIB_YAW_SCALE_RIGHT_KEY] = offsets.yawScaleRight
            // Legacy single field = mean (older builds / tools).
            preferences[DRIVE_CALIB_YAW_SCALE_KEY] = offsets.yawScale
            preferences[DRIVE_CALIB_YAW_SIGN_KEY] = if (offsets.yawSign < 0) -1 else 1
            preferences[DRIVE_CALIB_LAG_MS_KEY] = offsets.lagMs
            preferences[DRIVE_CALIB_AT_MS_KEY] = offsets.calibratedAtEpochMs
            preferences[DRIVE_CALIB_SPEED_EST_KEY] = offsets.speedEstimated
            preferences[DRIVE_CALIB_YAW_EST_KEY] = offsets.yawEstimated
        }
        vad.dashing.tbox.location.DriveCalibrationStore.update(offsets)
        if (noteGeoCalibration &&
            (offsets.speedEstimated || offsets.yawEstimated || offsets.calibratedAtEpochMs > 0L)
        ) {
            val at = offsets.calibratedAtEpochMs.takeIf { it > 0L }
                ?: System.currentTimeMillis()
            markGeoCalibrationSuccess(at)
        }
    }

    suspend fun resetDriveCalibrationOffsets() {
        saveDriveCalibrationOffsets(
            vad.dashing.tbox.location.DriveCalibrationOffsets.DEFAULT,
            noteGeoCalibration = false,
        )
    }

    suspend fun loadSteerCalibrationOffsets(): vad.dashing.tbox.location.SteerCalibrationOffsets {
        val prefs = context.settingsDataStore.data.first()
        val sign = prefs[STEER_CALIB_SIGN_KEY] ?: 1
        val single = prefs[STEER_CALIB_SCALE_KEY]
        val left = prefs[STEER_CALIB_SCALE_LEFT_KEY]
        val right = prefs[STEER_CALIB_SCALE_RIGHT_KEY]
        val scale = vad.dashing.tbox.location.SteerCalibrationMath.migrateScale(
            when {
                single != null && single.isFinite() && single > 0f -> single
                left != null || right != null -> {
                    val l = left?.takeIf { it.isFinite() && it > 0f }
                        ?: vad.dashing.tbox.location.SteerHeadingIntegrator.DEFAULT_SCALE
                    val r = right?.takeIf { it.isFinite() && it > 0f }
                        ?: vad.dashing.tbox.location.SteerHeadingIntegrator.DEFAULT_SCALE
                    (l + r) * 0.5f
                }
                else -> vad.dashing.tbox.location.SteerHeadingIntegrator.DEFAULT_SCALE
            },
        )
        return vad.dashing.tbox.location.SteerCalibrationOffsets(
            zeroDeg = prefs[STEER_CALIB_ZERO_DEG_KEY] ?: 0f,
            scale = scale,
            sign = if (sign < 0) -1 else 1,
            calibratedAtEpochMs = prefs[STEER_CALIB_AT_MS_KEY] ?: 0L,
            scaleEstimated = prefs[STEER_CALIB_SCALE_EST_KEY] ?: false,
        )
    }

    suspend fun saveSteerCalibrationOffsets(
        offsets: vad.dashing.tbox.location.SteerCalibrationOffsets,
        noteGeoCalibration: Boolean = true,
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[STEER_CALIB_ZERO_DEG_KEY] = offsets.zeroDeg
            preferences[STEER_CALIB_SCALE_KEY] = offsets.scale
            // Keep legacy keys in sync (both = single scale) for older builds.
            preferences[STEER_CALIB_SCALE_LEFT_KEY] = offsets.scale
            preferences[STEER_CALIB_SCALE_RIGHT_KEY] = offsets.scale
            preferences[STEER_CALIB_SIGN_KEY] = if (offsets.sign < 0) -1 else 1
            preferences[STEER_CALIB_AT_MS_KEY] = offsets.calibratedAtEpochMs
            preferences[STEER_CALIB_SCALE_EST_KEY] = offsets.scaleEstimated
        }
        vad.dashing.tbox.location.SteerCalibrationStore.update(offsets)
        if (noteGeoCalibration) {
            noteGeoCalibrationActivity(
                offsets.calibratedAtEpochMs.takeIf { it > 0L }
                    ?: System.currentTimeMillis(),
            )
        }
    }

    suspend fun saveLogLevel(level: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[LOG_LEVEL_KEY] = level
        }
    }

    suspend fun saveAutoSuspendTboxAppSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[AUTO_SUSPEND_TBOX_APP_KEY] = enabled
        }
    }

    suspend fun saveAutoStopTboxAppSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[AUTO_STOP_TBOX_APP_KEY] = enabled
        }
    }

    suspend fun saveAutoSuspendTboxMdcSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[AUTO_SUSPEND_TBOX_MDC_KEY] = enabled
        }
    }

    suspend fun saveAutoStopTboxMdcSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[AUTO_STOP_TBOX_MDC_KEY] = enabled
        }
    }

    suspend fun saveAutoSuspendTboxSwdSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[AUTO_SUSPEND_TBOX_SWD_KEY] = enabled
        }
    }

    suspend fun saveAutoSuspendTboxLocSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[AUTO_SUSPEND_TBOX_LOC_KEY] = enabled
        }
    }

    suspend fun saveAutoPreventTboxRestartSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[AUTO_PREVENT_TBOX_RESTART_KEY] = enabled
        }
    }

    suspend fun saveGetVoltagesSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[GET_VOLTAGES_KEY] = enabled
        }
    }

    suspend fun saveGetCanFrameSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[GET_CAN_FRAME_KEY] = enabled
        }
    }

    suspend fun saveNoTboxConnectSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[NO_TBOX_CONNECT_KEY] = enabled
        }
    }

    suspend fun saveGetCycleSignalSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[GET_CYCLE_SIGNAL_KEY] = enabled
        }
    }

    suspend fun saveGetLocDataSetting(enabled: Boolean) {
        saveLocationSourceSetting(
            if (enabled) {
                vad.dashing.tbox.esp.LocationSource.TBOX
            } else {
                vad.dashing.tbox.esp.LocationSource.ANDROID
            }
        )
    }

    suspend fun saveLocationSourceSetting(source: vad.dashing.tbox.esp.LocationSource) {
        var sendResumeLoc = false
        context.settingsDataStore.edit { preferences ->
            val previous = resolveLocationSource(preferences)
            var effective = source
            if (effective == vad.dashing.tbox.esp.LocationSource.TBOX &&
                (preferences[NO_TBOX_CONNECT_KEY] ?: false)
            ) {
                effective = vad.dashing.tbox.esp.LocationSource.ANDROID
            }
            if (effective == vad.dashing.tbox.esp.LocationSource.ESP32 &&
                !(preferences[ESP_COMPANION_ENABLED_KEY] ?: false)
            ) {
                effective = if (preferences[NO_TBOX_CONNECT_KEY] ?: false) {
                    vad.dashing.tbox.esp.LocationSource.ANDROID
                } else {
                    vad.dashing.tbox.esp.LocationSource.TBOX
                }
            }
            preferences[LOCATION_SOURCE_KEY] = effective.name
            preferences[GET_LOC_DATA_KEY] = effective == vad.dashing.tbox.esp.LocationSource.TBOX
            // Do not auto-enable companion USB here — that registers USB Host listeners and can
            // briefly drop TBox RNDIS on this HU even when no Espressif device is present.
            if (effective == vad.dashing.tbox.esp.LocationSource.ESP32) {
                // Stale mock while on Android must not resume when switching to companion.
                if (previous == vad.dashing.tbox.esp.LocationSource.ANDROID) {
                    preferences[MOCK_LOCATION] = false
                }
            }
            // USB GNSS does not require / enable the ESP companion session.
            if (effective == vad.dashing.tbox.esp.LocationSource.USB) {
                if (previous == vad.dashing.tbox.esp.LocationSource.ANDROID) {
                    preferences[MOCK_LOCATION] = false
                }
            }
            // Mock while on Android would loop; clear so switching back does not
            // suddenly resume mock without an explicit user toggle.
            if (effective == vad.dashing.tbox.esp.LocationSource.ANDROID) {
                preferences[MOCK_LOCATION] = false
            }
            // Auto SUSPEND LOC follows external GNSS sources; TBOX needs LOC running.
            if (effective != previous) {
                when (effective) {
                    vad.dashing.tbox.esp.LocationSource.TBOX -> {
                        preferences[AUTO_SUSPEND_TBOX_LOC_KEY] = false
                        sendResumeLoc = true
                    }
                    vad.dashing.tbox.esp.LocationSource.USB,
                    vad.dashing.tbox.esp.LocationSource.ESP32,
                    -> {
                        preferences[AUTO_SUSPEND_TBOX_LOC_KEY] = true
                    }
                    vad.dashing.tbox.esp.LocationSource.ANDROID -> Unit
                }
            }
        }
        if (sendResumeLoc) {
            runCatching {
                context.startService(
                    android.content.Intent(context, BackgroundService::class.java).apply {
                        action = BackgroundService.ACTION_TBOX_APP_RESUME
                        putExtra(BackgroundService.EXTRA_APP_NAME, "LOC")
                    },
                )
            }
        }
    }

    suspend fun saveUsbGnssDeviceIdSetting(deviceId: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[USB_GNSS_DEVICE_ID_KEY] = deviceId.trim()
        }
    }

    suspend fun saveUsbGnssBaudSetting(baud: Int) {
        val safe = if (baud in vad.dashing.tbox.usbgnss.UsbGnssDeviceIds.BAUD_OPTIONS) {
            baud
        } else {
            vad.dashing.tbox.usbgnss.UsbGnssDeviceIds.DEFAULT_BAUD
        }
        context.settingsDataStore.edit { preferences ->
            preferences[USB_GNSS_BAUD_KEY] = safe
        }
    }

    suspend fun saveUsbGnssRequestVtgSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[USB_GNSS_REQUEST_VTG_KEY] = enabled
        }
    }

    suspend fun saveUsbGnssRequestZdaSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[USB_GNSS_REQUEST_ZDA_KEY] = enabled
        }
    }

    suspend fun saveUsbGnssRequestGstSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[USB_GNSS_REQUEST_GST_KEY] = enabled
        }
    }

    suspend fun saveEspUm980RequestVtgSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[ESP_UM980_REQUEST_VTG_KEY] = enabled
        }
    }

    suspend fun saveEspUm980RequestZdaSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[ESP_UM980_REQUEST_ZDA_KEY] = enabled
        }
    }

    suspend fun saveEspUm980RequestGstSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[ESP_UM980_REQUEST_GST_KEY] = enabled
        }
    }

    suspend fun saveUsbGnssModuleIdentity(stableId: String, identity: vad.dashing.tbox.usbgnss.GnssModuleIdentity) {
        val id = stableId.trim()
        if (id.isEmpty()) return
        context.settingsDataStore.edit { preferences ->
            val map = vad.dashing.tbox.usbgnss.GnssModuleIdentityCodec.decodeMap(
                preferences[USB_GNSS_MODULE_BY_DEVICE_KEY],
            ).toMutableMap()
            map[id] = identity
            preferences[USB_GNSS_MODULE_BY_DEVICE_KEY] =
                vad.dashing.tbox.usbgnss.GnssModuleIdentityCodec.encodeMap(map)
        }
    }

    suspend fun migrateUsbGnssModuleIdentityStableId(fromId: String, toId: String) {
        val from = fromId.trim()
        val to = toId.trim()
        if (from.isEmpty() || to.isEmpty() || from == to) return
        context.settingsDataStore.edit { preferences ->
            val map = vad.dashing.tbox.usbgnss.GnssModuleIdentityCodec.decodeMap(
                preferences[USB_GNSS_MODULE_BY_DEVICE_KEY],
            )
            val migrated = vad.dashing.tbox.usbgnss.GnssModuleIdentityCodec.migrateStableId(map, from, to)
            if (migrated != map) {
                preferences[USB_GNSS_MODULE_BY_DEVICE_KEY] =
                    vad.dashing.tbox.usbgnss.GnssModuleIdentityCodec.encodeMap(migrated)
            }
        }
    }

    suspend fun saveEspCompanionEnabledSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[ESP_COMPANION_ENABLED_KEY] = enabled
            if (!enabled) {
                val src = resolveLocationSource(preferences)
                if (src == vad.dashing.tbox.esp.LocationSource.ESP32) {
                    preferences[LOCATION_SOURCE_KEY] =
                        vad.dashing.tbox.esp.LocationSource.TBOX.name
                    preferences[GET_LOC_DATA_KEY] = true
                }
            }
        }
    }

    private fun resolveLocationSource(preferences: Preferences): vad.dashing.tbox.esp.LocationSource {
        val raw = preferences[LOCATION_SOURCE_KEY]
        val source = if (!raw.isNullOrBlank()) {
            vad.dashing.tbox.esp.LocationSource.fromStorage(raw)
        } else {
            vad.dashing.tbox.esp.LocationSource.fromLegacyGetLocData(
                preferences[GET_LOC_DATA_KEY]
            )
        }
        if (source == vad.dashing.tbox.esp.LocationSource.ESP32 &&
            !(preferences[ESP_COMPANION_ENABLED_KEY] ?: false)
        ) {
            return vad.dashing.tbox.esp.LocationSource.TBOX
        }
        return source
    }

    suspend fun saveExpertModeSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[EXPERT_MODE] = enabled
        }
    }

    suspend fun savePermissionsIntroSeen(seen: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[PERMISSIONS_INTRO_SEEN_KEY] = seen
        }
    }

    suspend fun saveLeftMenuVisibleSetting(visible: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[LEFT_MENU_VISIBLE] = visible
        }
    }

    suspend fun saveFloatingDashboards(configs: List<FloatingDashboardConfig>) {
        val normalized = configs
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }
            .map {
                it.copy(
                    rows = it.rows.coerceIn(1, DASHBOARD_PANEL_MAX_GRID_DIMENSION),
                    cols = it.cols.coerceIn(1, DASHBOARD_PANEL_MAX_GRID_DIMENSION)
                )
            }
        saveCustomString(FLOATING_DASHBOARDS_LIST_KEY, serializeFloatingDashboards(normalized))
    }

    suspend fun saveUsageStatsFloatingOverlayRules(
        hideWatchPackages: Set<String>,
        hidePanelIds: Set<String>,
        showWatchPackages: Set<String>,
        showPanelIds: Set<String>,
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[getStringKey(USAGE_STATS_HIDE_FLOATING_WATCH_PACKAGES_KEY)] =
                stringSetToJsonArray(hideWatchPackages)
            preferences[getStringKey(USAGE_STATS_HIDE_FLOATING_PANEL_IDS_KEY)] =
                stringSetToJsonArray(hidePanelIds)
            preferences[getStringKey(USAGE_STATS_FORCE_SHOW_WATCH_PACKAGES_KEY)] =
                stringSetToJsonArray(showWatchPackages)
            preferences[getStringKey(USAGE_STATS_FORCE_SHOW_PANEL_IDS_KEY)] =
                stringSetToJsonArray(showPanelIds)
        }
    }

    suspend fun saveSelectedTab(tabKey: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[SELECTED_TAB_KEY] = tabKey
        }
    }

    suspend fun saveLeftMenuLayoutJson(json: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[LEFT_MENU_LAYOUT_KEY] = json
        }
    }

    /**
     * If the persisted sidebar tab is disabled in the menu layout, switch to the first visible tab.
     * [main_screen] is left unchanged.
     */
    suspend fun reconcileSelectedTabWithMenuLayoutIfNeeded() {
        val prefs = context.settingsDataStore.data.first()
        val rawKey = prefs[SELECTED_TAB_KEY].orEmpty()
        val layout = vad.dashing.tbox.ui.LeftMenuLayout.parse(
            prefs[LEFT_MENU_LAYOUT_KEY].orEmpty(),
        )
        val resolved = vad.dashing.tbox.ui.LeftMenuLayout.resolveSelectedTab(rawKey, layout)
        if (resolved != vad.dashing.tbox.ui.LeftMenuLayout.parseSelectedTabKey(rawKey)) {
            saveSelectedTab(resolved)
        }
    }

    /**
     * Migrates pre-0.14.x single copied wallpaper files into per-theme `file://` folders so the
     * new folder-based picker keeps working without re-selecting images.
     */
    suspend fun migrateMainScreenWallpaperFilesToFolderUrisIfNeeded() {
        withContext(Dispatchers.IO) {
            suspend fun tryMigrate(
                legacyBooleanKey: Preferences.Key<Boolean>,
                legacyFile: File,
                dirRel: String,
                folderUriKey: Preferences.Key<String>,
                forLightTheme: Boolean,
            ): Boolean {
                val snapshot = context.settingsDataStore.data.first()
                if (snapshot[folderUriKey].orEmpty().isNotEmpty()) return false
                val hadLegacyFlag = snapshot[legacyBooleanKey] == true
                if (!hadLegacyFlag && !legacyFile.isFile) return false
                val bmp = runCatching {
                    BitmapFactory.decodeFile(legacyFile.absolutePath)
                }.getOrNull()
                if (bmp == null) {
                    if (legacyFile.exists()) legacyFile.delete()
                    if (hadLegacyFlag) {
                        context.settingsDataStore.edit { it[legacyBooleanKey] = false }
                    }
                    return false
                }
                val dir = File(context.filesDir, dirRel)
                dir.mkdirs()
                val dest = File(dir, "migrated_wallpaper.jpg")
                val ok = runCatching {
                    dest.outputStream().use { out ->
                        bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
                    }
                    dest.length() > 0L
                }.getOrDefault(false)
                bmp.recycle()
                if (!ok) {
                    dest.delete()
                    return false
                }
                legacyFile.delete()
                val folderUri = Uri.fromFile(dir).toString()
                context.settingsDataStore.edit { e ->
                    e[folderUriKey] = folderUri
                    val existing = MainScreenWallpaperSelectionsByPage.fromDataStoreJson(
                        e[MAIN_SCREEN_WALLPAPER_SELECTION_BY_PAGE_KEY],
                    )
                    val updated = existing.withFileName(
                        page = DEFAULT_MAIN_SCREEN_CURRENT_PAGE,
                        forLightTheme = forLightTheme,
                        fileName = dest.name,
                    )
                    e[MAIN_SCREEN_WALLPAPER_SELECTION_BY_PAGE_KEY] = updated.toJson().toString()
                    e[legacyBooleanKey] = false
                }
                return true
            }
            val migrated = tryMigrate(
                MAIN_SCREEN_WALLPAPER_LIGHT_SET_LEGACY_KEY,
                File(context.filesDir, MAIN_SCREEN_WALLPAPER_LIGHT_FILE),
                "$MAIN_SCREEN_WALLPAPER_MIGRATED_DIR/light",
                MAIN_SCREEN_WALLPAPER_LIGHT_FOLDER_URI_KEY,
                forLightTheme = true,
            ) || tryMigrate(
                MAIN_SCREEN_WALLPAPER_DARK_SET_LEGACY_KEY,
                File(context.filesDir, MAIN_SCREEN_WALLPAPER_DARK_FILE),
                "$MAIN_SCREEN_WALLPAPER_MIGRATED_DIR/dark",
                MAIN_SCREEN_WALLPAPER_DARK_FOLDER_URI_KEY,
                forLightTheme = false,
            )
            if (migrated) {
                bumpLauncherAppIconRevision()
            }
        }
    }

    suspend fun saveMainScreenSettingsButton(position: MainScreenSettingsButtonPosition) {
        val obj = JSONObject()
        obj.put("x", position.x.coerceIn(0f, 1f).toDouble())
        obj.put("y", position.y.coerceIn(0f, 1f).toDouble())
        saveCustomString(MAIN_SCREEN_SETTINGS_BUTTON_KEY, obj.toString())
    }

    suspend fun saveMainScreenAddButton(position: MainScreenAddButtonPosition) {
        val obj = JSONObject()
        obj.put("x", position.x.coerceIn(0f, 1f).toDouble())
        obj.put("y", position.y.coerceIn(0f, 1f).toDouble())
        saveCustomString(MAIN_SCREEN_ADD_BUTTON_KEY, obj.toString())
    }

    suspend fun saveMainScreenPageCount(pageCount: Int) {
        val normalized = PagingStateNormalizer.normalizePageCount(pageCount)
        val prefs = context.settingsDataStore.data.first()
        val oldPageCount = PagingStateNormalizer.normalizePageCount(
            prefs[MAIN_SCREEN_PAGE_COUNT_KEY] ?: DEFAULT_MAIN_SCREEN_PAGE_COUNT,
        )
        val rawJson = prefs[getStringKey(MAIN_SCREEN_DASHBOARDS_LIST_KEY)] ?: ""
        val parsePageCount = if (oldPageCount == 1 && normalized > 1) 1 else normalized
        val panels = parseMainScreenDashboardsJson(rawJson, parsePageCount)
        val adjusted = PagingStateNormalizer.adjustPanelsForPageCountChange(
            panels = panels,
            oldPageCount = oldPageCount,
            newPageCount = normalized,
        )
        context.settingsDataStore.edit { preferences ->
            preferences[MAIN_SCREEN_PAGE_COUNT_KEY] = normalized
            val current = preferences[MAIN_SCREEN_CURRENT_PAGE_KEY] ?: DEFAULT_MAIN_SCREEN_CURRENT_PAGE
            preferences[MAIN_SCREEN_CURRENT_PAGE_KEY] =
                PagingStateNormalizer.normalizeCurrentPage(current, normalized)
            if (preferences.contains(MAIN_SCREEN_WINDOW_MODE_CURRENT_PAGE_KEY)) {
                val windowPage = preferences[MAIN_SCREEN_WINDOW_MODE_CURRENT_PAGE_KEY]
                    ?: DEFAULT_MAIN_SCREEN_CURRENT_PAGE
                preferences[MAIN_SCREEN_WINDOW_MODE_CURRENT_PAGE_KEY] =
                    PagingStateNormalizer.normalizeCurrentPage(windowPage, normalized)
            }
        }
        saveMainScreenDashboards(adjusted)
    }

    suspend fun saveMainScreenCurrentPage(page: Int) {
        val pageCount = PagingStateNormalizer.normalizePageCount(
            context.settingsDataStore.data.first()[MAIN_SCREEN_PAGE_COUNT_KEY]
                ?: DEFAULT_MAIN_SCREEN_PAGE_COUNT
        )
        val normalized = PagingStateNormalizer.normalizeCurrentPage(page, pageCount)
        context.settingsDataStore.edit { preferences ->
            preferences[MAIN_SCREEN_CURRENT_PAGE_KEY] = normalized
        }
    }

    suspend fun saveMainScreenWindowModeCurrentPage(page: Int) {
        val pageCount = PagingStateNormalizer.normalizePageCount(
            context.settingsDataStore.data.first()[MAIN_SCREEN_PAGE_COUNT_KEY]
                ?: DEFAULT_MAIN_SCREEN_PAGE_COUNT
        )
        val normalized = PagingStateNormalizer.normalizeCurrentPage(page, pageCount)
        context.settingsDataStore.edit { preferences ->
            preferences[MAIN_SCREEN_WINDOW_MODE_CURRENT_PAGE_KEY] = normalized
        }
    }

    suspend fun savePanelCollapseStates(states: Map<String, Boolean>) {
        val json = PanelCollapseStates.serialize(states)
        context.settingsDataStore.edit { preferences ->
            if (json.isBlank()) {
                preferences.remove(PANEL_COLLAPSE_STATES_KEY)
            } else {
                preferences[PANEL_COLLAPSE_STATES_KEY] = json
            }
        }
    }

    suspend fun setPanelCollapsed(panelId: String, collapsed: Boolean) {
        if (panelId.isBlank()) return
        context.settingsDataStore.edit { preferences ->
            val current = PanelCollapseStates.parse(preferences[PANEL_COLLAPSE_STATES_KEY])
            val updated = PanelCollapseStates.withCollapsed(current, panelId, collapsed)
            val json = PanelCollapseStates.serialize(updated)
            if (json.isBlank()) {
                preferences.remove(PANEL_COLLAPSE_STATES_KEY)
            } else {
                preferences[PANEL_COLLAPSE_STATES_KEY] = json
            }
        }
    }

    suspend fun saveMainScreenPagePrevButton(position: MainScreenPagePrevButtonPosition) {
        val obj = JSONObject()
        obj.put("x", position.x.coerceIn(0f, 1f).toDouble())
        obj.put("y", position.y.coerceIn(0f, 1f).toDouble())
        saveCustomString(MAIN_SCREEN_PAGE_PREV_BUTTON_KEY, obj.toString())
    }

    suspend fun saveMainScreenPageNextButton(position: MainScreenPageNextButtonPosition) {
        val obj = JSONObject()
        obj.put("x", position.x.coerceIn(0f, 1f).toDouble())
        obj.put("y", position.y.coerceIn(0f, 1f).toDouble())
        saveCustomString(MAIN_SCREEN_PAGE_NEXT_BUTTON_KEY, obj.toString())
    }

    suspend fun saveMainScreenWindowModeGeometry(geometry: MainScreenWindowModeGeometry) {
        val g = geometry.normalized()
        val obj = JSONObject()
        obj.put("startX", g.startX)
        obj.put("startY", g.startY)
        obj.put("width", g.width)
        obj.put("height", g.height)
        saveCustomString(MAIN_SCREEN_WINDOW_MODE_GEOMETRY_KEY, obj.toString())
    }

    suspend fun saveMainScreenWindowModeExitButton(position: MainScreenWindowModeExitButtonPosition) {
        val obj = JSONObject()
        obj.put("x", position.x.coerceIn(0f, 1f).toDouble())
        obj.put("y", position.y.coerceIn(0f, 1f).toDouble())
        saveCustomString(MAIN_SCREEN_WINDOW_MODE_EXIT_BUTTON_KEY, obj.toString())
    }

    suspend fun saveMainScreenWindowModeRestoreButton(position: MainScreenWindowModeExitButtonPosition) {
        val obj = JSONObject()
        obj.put("x", position.x.coerceIn(0f, 1f).toDouble())
        obj.put("y", position.y.coerceIn(0f, 1f).toDouble())
        saveCustomString(MAIN_SCREEN_WINDOW_MODE_RESTORE_BUTTON_KEY, obj.toString())
    }

    suspend fun saveMainScreenWindowModeAutoGeometry(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[MAIN_SCREEN_WINDOW_MODE_AUTO_GEOMETRY_KEY] = enabled
        }
    }

    suspend fun saveActiveTheme(
        uri: String,
        fingerprint: String,
        sections: Set<ThemeSection>,
        applyTargets: Set<ThemeApplyTarget> = emptySet(),
    ) {
        context.settingsDataStore.edit { preferences ->
            if (uri.isBlank()) {
                preferences.remove(ACTIVE_THEME_URI_KEY)
            } else {
                preferences[ACTIVE_THEME_URI_KEY] = uri
            }
            if (fingerprint.isBlank()) {
                preferences.remove(ACTIVE_THEME_FINGERPRINT_KEY)
            } else {
                preferences[ACTIVE_THEME_FINGERPRINT_KEY] = fingerprint
            }
            if (sections.isEmpty()) {
                preferences.remove(ACTIVE_THEME_SECTIONS_KEY)
            } else {
                preferences[ACTIVE_THEME_SECTIONS_KEY] = ThemeSection.toJsonArray(sections).toString()
            }
            if (applyTargets.isEmpty()) {
                preferences.remove(ACTIVE_THEME_APPLY_TARGETS_KEY)
            } else {
                preferences[ACTIVE_THEME_APPLY_TARGETS_KEY] =
                    ThemeApplyTarget.toJsonArray(applyTargets).toString()
            }
        }
    }

    suspend fun clearActiveTheme() {
        saveActiveTheme(uri = "", fingerprint = "", sections = emptySet(), applyTargets = emptySet())
    }

    suspend fun saveDriveModeThemePaths(paths: Map<Int, String>) {
        context.settingsDataStore.edit { preferences ->
            if (paths.isEmpty()) {
                preferences.remove(DRIVE_MODE_THEME_PATHS_KEY)
            } else {
                preferences[DRIVE_MODE_THEME_PATHS_KEY] = serializeDriveModeThemePaths(paths)
            }
        }
    }

    suspend fun saveDriveModeThemePath(rawValue: Int, uri: String) {
        val current = driveModeThemePathsFlow.first().toMutableMap()
        if (uri.isBlank()) {
            current.remove(rawValue)
        } else {
            current[rawValue] = uri
        }
        saveDriveModeThemePaths(current)
    }

    suspend fun clearDriveModeThemePaths() {
        saveDriveModeThemePaths(emptyMap())
    }

    suspend fun saveMainScreenCornerButtonSizeDp(sizeDp: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[MAIN_SCREEN_CORNER_BUTTON_SIZE_KEY] =
                sizeDp.coerceIn(MIN_MAIN_SCREEN_CORNER_BUTTON_SIZE_DP, 100)
        }
    }

    suspend fun saveMainScreenCornerButtonBackgroundLight(color: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[MAIN_SCREEN_CORNER_BTN_BG_LIGHT_KEY] = color
        }
    }

    suspend fun saveMainScreenCornerButtonBackgroundDark(color: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[MAIN_SCREEN_CORNER_BTN_BG_DARK_KEY] = color
        }
    }

    suspend fun saveMainScreenCornerButtonIconLight(color: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[MAIN_SCREEN_CORNER_BTN_ICON_LIGHT_KEY] = color
        }
    }

    suspend fun saveMainScreenCornerButtonIconDark(color: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[MAIN_SCREEN_CORNER_BTN_ICON_DARK_KEY] = color
        }
    }

    suspend fun saveMainScreenCanvasBackgroundLight(color: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[MAIN_SCREEN_CANVAS_BG_LIGHT_KEY] = color
        }
    }

    suspend fun saveMainScreenCanvasBackgroundDark(color: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[MAIN_SCREEN_CANVAS_BG_DARK_KEY] = color
        }
    }

    suspend fun saveWidgetColorPresetSlot(slotIndex: Int, color: Int) {
        require(slotIndex in 0 until WIDGET_COLOR_PRESET_SLOT_COUNT)
        context.settingsDataStore.edit { preferences ->
            preferences[WIDGET_COLOR_PRESET_KEYS[slotIndex]] = color
        }
    }

    suspend fun saveMainScreenOpenOnBoot(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[MAIN_SCREEN_OPEN_ON_BOOT_KEY] = enabled
        }
    }

    suspend fun saveMainScreenOpenOnBootDelaySeconds(delaySeconds: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[MAIN_SCREEN_OPEN_ON_BOOT_DELAY_SECONDS_KEY] =
                delaySeconds.coerceIn(
                    MIN_MAIN_SCREEN_OPEN_ON_BOOT_DELAY_SECONDS,
                    MAX_MAIN_SCREEN_OPEN_ON_BOOT_DELAY_SECONDS
                )
        }
    }

    suspend fun saveMainScreenWallpaperLightFolderUri(uriString: String?) {
        context.settingsDataStore.edit { preferences ->
            if (uriString.isNullOrBlank()) {
                preferences.remove(MAIN_SCREEN_WALLPAPER_LIGHT_FOLDER_URI_KEY)
                val cleared = MainScreenWallpaperSelectionsByPage.fromDataStoreJson(
                    preferences[MAIN_SCREEN_WALLPAPER_SELECTION_BY_PAGE_KEY],
                ).clearedForTheme(forLightTheme = true)
                if (cleared.isEmpty()) {
                    preferences.remove(MAIN_SCREEN_WALLPAPER_SELECTION_BY_PAGE_KEY)
                } else {
                    preferences[MAIN_SCREEN_WALLPAPER_SELECTION_BY_PAGE_KEY] = cleared.toJson().toString()
                }
            } else {
                preferences[MAIN_SCREEN_WALLPAPER_LIGHT_FOLDER_URI_KEY] = uriString
            }
        }
    }

    suspend fun saveMainScreenWallpaperDarkFolderUri(uriString: String?) {
        context.settingsDataStore.edit { preferences ->
            if (uriString.isNullOrBlank()) {
                preferences.remove(MAIN_SCREEN_WALLPAPER_DARK_FOLDER_URI_KEY)
                val cleared = MainScreenWallpaperSelectionsByPage.fromDataStoreJson(
                    preferences[MAIN_SCREEN_WALLPAPER_SELECTION_BY_PAGE_KEY],
                ).clearedForTheme(forLightTheme = false)
                if (cleared.isEmpty()) {
                    preferences.remove(MAIN_SCREEN_WALLPAPER_SELECTION_BY_PAGE_KEY)
                } else {
                    preferences[MAIN_SCREEN_WALLPAPER_SELECTION_BY_PAGE_KEY] = cleared.toJson().toString()
                }
            } else {
                preferences[MAIN_SCREEN_WALLPAPER_DARK_FOLDER_URI_KEY] = uriString
            }
        }
    }

    suspend fun saveMainScreenWallpaperSelectionsByPage(selections: MainScreenWallpaperSelectionsByPage) {
        context.settingsDataStore.edit { preferences ->
            if (selections.isEmpty()) {
                preferences.remove(MAIN_SCREEN_WALLPAPER_SELECTION_BY_PAGE_KEY)
            } else {
                preferences[MAIN_SCREEN_WALLPAPER_SELECTION_BY_PAGE_KEY] = selections.toJson().toString()
            }
        }
    }

    suspend fun saveMainScreenWallpaperSelectionForPage(
        page: Int,
        forLightTheme: Boolean,
        fileName: String,
    ) {
        val pageCount = PagingStateNormalizer.normalizePageCount(
            context.settingsDataStore.data.first()[MAIN_SCREEN_PAGE_COUNT_KEY]
                ?: DEFAULT_MAIN_SCREEN_PAGE_COUNT,
        )
        val normalizedPage = PagingStateNormalizer.normalizeCurrentPage(page, pageCount)
        val current = mainScreenWallpaperSelectionByPageFlow.first()
        saveMainScreenWallpaperSelectionsByPage(
            current.withFileName(normalizedPage, forLightTheme, fileName),
        )
    }

    /** Single DataStore write when picking wallpaper (folder URI + selected file name for [page]). */
    suspend fun saveMainScreenWallpaperLightFolderAndSelection(
        folderUriString: String,
        selectedFileName: String,
        page: Int,
    ) {
        val pageCount = PagingStateNormalizer.normalizePageCount(
            context.settingsDataStore.data.first()[MAIN_SCREEN_PAGE_COUNT_KEY]
                ?: DEFAULT_MAIN_SCREEN_PAGE_COUNT,
        )
        val normalizedPage = PagingStateNormalizer.normalizeCurrentPage(page, pageCount)
        val selections = MainScreenWallpaperSelectionsByPage.fromDataStoreJson(
            context.settingsDataStore.data.first()[MAIN_SCREEN_WALLPAPER_SELECTION_BY_PAGE_KEY],
        ).withFileName(normalizedPage, forLightTheme = true, selectedFileName)
        context.settingsDataStore.edit { preferences ->
            preferences[MAIN_SCREEN_WALLPAPER_LIGHT_FOLDER_URI_KEY] = folderUriString
            if (selections.isEmpty()) {
                preferences.remove(MAIN_SCREEN_WALLPAPER_SELECTION_BY_PAGE_KEY)
            } else {
                preferences[MAIN_SCREEN_WALLPAPER_SELECTION_BY_PAGE_KEY] = selections.toJson().toString()
            }
        }
    }

    suspend fun saveMainScreenWallpaperDarkFolderAndSelection(
        folderUriString: String,
        selectedFileName: String,
        page: Int,
    ) {
        val pageCount = PagingStateNormalizer.normalizePageCount(
            context.settingsDataStore.data.first()[MAIN_SCREEN_PAGE_COUNT_KEY]
                ?: DEFAULT_MAIN_SCREEN_PAGE_COUNT,
        )
        val normalizedPage = PagingStateNormalizer.normalizeCurrentPage(page, pageCount)
        val selections = MainScreenWallpaperSelectionsByPage.fromDataStoreJson(
            context.settingsDataStore.data.first()[MAIN_SCREEN_WALLPAPER_SELECTION_BY_PAGE_KEY],
        ).withFileName(normalizedPage, forLightTheme = false, selectedFileName)
        context.settingsDataStore.edit { preferences ->
            preferences[MAIN_SCREEN_WALLPAPER_DARK_FOLDER_URI_KEY] = folderUriString
            if (selections.isEmpty()) {
                preferences.remove(MAIN_SCREEN_WALLPAPER_SELECTION_BY_PAGE_KEY)
            } else {
                preferences[MAIN_SCREEN_WALLPAPER_SELECTION_BY_PAGE_KEY] = selections.toJson().toString()
            }
        }
    }

    suspend fun mainScreenWallpaperSelectionsSnapshot(): MainScreenWallpaperSelectionsByPage {
        return MainScreenWallpaperSelectionsByPage.fromDataStoreJson(
            context.settingsDataStore.data.first()[MAIN_SCREEN_WALLPAPER_SELECTION_BY_PAGE_KEY],
        )
    }

    suspend fun syncActiveThemeWallpaperSelection(
        selections: MainScreenWallpaperSelectionsByPage? = null,
    ): Boolean {
        val cacheKey = activeThemeUriFlow.first().trim()
        return syncThemeWallpaperSelection(cacheKey, selections)
    }

    suspend fun syncThemeWallpaperSelection(
        cacheKey: String,
        selections: MainScreenWallpaperSelectionsByPage? = null,
    ): Boolean {
        if (selections == null) return false
        return ThemeMaterialization.syncRuntimeStateToThemeCache(
            context = context,
            cacheKey = cacheKey,
            wallpaperSelections = selections,
        )
    }

    /** Writes current main-screen page/wallpaper choices into [cacheKey] runtime.json. */
    suspend fun snapshotMainScreenRuntimeToThemeCache(cacheKey: String) {
        val selections = mainScreenWallpaperSelectionByPageFlow.first()
        val page = mainScreenCurrentPageFlow.first()
        val windowModePage = mainScreenWindowModeCurrentPageFlow.first()
        ThemeMaterialization.syncRuntimeStateToThemeCache(
            context = context,
            cacheKey = cacheKey,
            wallpaperSelections = selections,
            currentPage = page,
            currentPageWindowMode = windowModePage,
        )
    }

    /** @deprecated Use [snapshotMainScreenRuntimeToThemeCache] with explicit outgoing cache key. */
    suspend fun snapshotMainScreenRuntimeToActiveThemeCache() {
        snapshotMainScreenRuntimeToThemeCache(activeThemeUriFlow.first())
    }

    suspend fun syncActiveThemeCurrentPage(currentPage: Int): Boolean {
        val cacheKey = activeThemeUriFlow.first().trim()
        return syncThemeCurrentPage(cacheKey, currentPage)
    }

    suspend fun syncActiveThemeWindowModeCurrentPage(currentPage: Int): Boolean {
        val cacheKey = activeThemeUriFlow.first().trim()
        return syncThemeWindowModeCurrentPage(cacheKey, currentPage)
    }

    suspend fun syncThemeCurrentPage(cacheKey: String, currentPage: Int): Boolean {
        return ThemeMaterialization.syncRuntimeStateToThemeCache(
            context = context,
            cacheKey = cacheKey,
            currentPage = currentPage,
        )
    }

    suspend fun syncThemeWindowModeCurrentPage(cacheKey: String, currentPage: Int): Boolean {
        return ThemeMaterialization.syncRuntimeStateToThemeCache(
            context = context,
            cacheKey = cacheKey,
            currentPageWindowMode = currentPage,
        )
    }

    suspend fun saveMainScreenWallpaperCrop(crop: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[MAIN_SCREEN_WALLPAPER_CROP_KEY] = crop
        }
    }

    private fun launcherAppIconFile(packageName: String): File {
        val dir = File(context.filesDir, LAUNCHER_APP_ICONS_DIR)
        return File(dir, packageName)
    }

    private fun httpRequestIconFile(iconKey: String): File {
        val dir = File(context.filesDir, HTTP_REQUEST_ICONS_DIR)
        return File(dir, iconKey)
    }

    suspend fun hasCustomLauncherAppIcon(packageName: String): Boolean =
        withContext(Dispatchers.IO) {
            if (packageName.isBlank()) return@withContext false
            val lookup = launcherAppIconLookup()
            LauncherAppIconPaths.hasResolvableIcon(context.filesDir, packageName, lookup)
        }

    suspend fun clearSharedLauncherAppIconsFolder() {
        withContext(Dispatchers.IO) {
            val dir = LauncherAppIconPaths.sharedIconsDir(context.filesDir)
            if (dir.isDirectory) {
                dir.listFiles()?.forEach { file ->
                    if (file.isFile) file.delete()
                }
            }
            bumpLauncherAppIconRevision()
        }
    }

    suspend fun clearSharedHttpRequestIconsFolder() {
        withContext(Dispatchers.IO) {
            val dir = HttpRequestIconPaths.sharedIconsDir(context.filesDir)
            if (dir.isDirectory) {
                dir.listFiles()?.forEach { file ->
                    if (file.isFile) file.delete()
                }
            }
            bumpHttpRequestIconRevision()
        }
    }

    suspend fun launcherAppIconLookup(): LauncherAppIconPaths.Lookup =
        LauncherAppIconPaths.Lookup(
            activeThemeCacheKey = activeThemeUriFlow.first().trim(),
            activeThemeApplyTargets = activeThemeApplyTargetsFlow.first(),
        )

    suspend fun clearCustomLauncherAppIcon(packageName: String) {
        withContext(Dispatchers.IO) {
            if (packageName.isBlank()) return@withContext
            val lookup = launcherAppIconLookup()
            if (LauncherAppIconPaths.deleteThemeCacheIcon(context.filesDir, packageName, lookup)) {
                bumpLauncherAppIconRevision()
                return@withContext
            }
            if (LauncherAppIconPaths.deleteSharedIcon(context.filesDir, packageName)) {
                bumpLauncherAppIconRevision()
            }
        }
    }

    suspend fun hasCustomHttpRequestIcon(iconKey: String): Boolean =
        withContext(Dispatchers.IO) {
            if (iconKey.isBlank()) return@withContext false
            val lookup = launcherAppIconLookup()
            HttpRequestIconPaths.hasResolvableIcon(context.filesDir, iconKey, lookup)
        }

    suspend fun clearCustomHttpRequestIcon(iconKey: String) {
        withContext(Dispatchers.IO) {
            if (iconKey.isBlank()) return@withContext
            val lookup = launcherAppIconLookup()
            if (HttpRequestIconPaths.deleteThemeCacheIcon(context.filesDir, iconKey, lookup)) {
                bumpHttpRequestIconRevision()
                return@withContext
            }
            if (HttpRequestIconPaths.deleteSharedIcon(context.filesDir, iconKey)) {
                bumpHttpRequestIconRevision()
            }
        }
    }

    suspend fun clearSharedTileBackgroundsFolder() {
        withContext(Dispatchers.IO) {
            TileBackgroundImageStorage.clearSharedDir(context.filesDir)
            bumpTileBackgroundImageRevision()
        }
    }

    /**
     * Removes on-disk assets that are not part of the JSON backup (main-screen wallpaper copies).
     * Custom launcher icons and per-tile background files under `filesDir` are **not** deleted so
     * existing files keep matching paths from the imported JSON without embedding binaries in the backup.
     * Call after a successful full settings import.
     */
    suspend fun clearNonExportedLocalAssetsAfterBackupImport() {
        withContext(Dispatchers.IO) {
            listOf(MAIN_SCREEN_WALLPAPER_LIGHT_FILE, MAIN_SCREEN_WALLPAPER_DARK_FILE).forEach { rel ->
                File(context.filesDir, rel).takeIf { it.exists() }?.delete()
            }
            File(context.filesDir, MAIN_SCREEN_WALLPAPER_MIGRATED_DIR).takeIf { it.exists() }?.deleteRecursively()
            context.settingsDataStore.edit { preferences ->
                preferences[MAIN_SCREEN_WALLPAPER_LIGHT_SET_LEGACY_KEY] = false
                preferences[MAIN_SCREEN_WALLPAPER_DARK_SET_LEGACY_KEY] = false
                val cur = preferences[LAUNCHER_APP_ICON_REVISION_KEY] ?: 0
                preferences[LAUNCHER_APP_ICON_REVISION_KEY] = cur + 1
                val curHttp = preferences[HTTP_REQUEST_ICON_REVISION_KEY] ?: 0
                preferences[HTTP_REQUEST_ICON_REVISION_KEY] = curHttp + 1
                val curTile = preferences[TILE_BACKGROUND_IMAGE_REVISION_KEY] ?: 0
                preferences[TILE_BACKGROUND_IMAGE_REVISION_KEY] = curTile + 1
                val curPanel = preferences[PANEL_BACKGROUND_IMAGE_REVISION_KEY] ?: 0
                preferences[PANEL_BACKGROUND_IMAGE_REVISION_KEY] = curPanel + 1
            }
        }
    }

    suspend fun bumpLauncherAppIconRevision() {
        context.settingsDataStore.edit { preferences ->
            val cur = preferences[LAUNCHER_APP_ICON_REVISION_KEY] ?: 0
            preferences[LAUNCHER_APP_ICON_REVISION_KEY] = cur + 1
        }
    }

    suspend fun bumpHttpRequestIconRevision() {
        context.settingsDataStore.edit { preferences ->
            val cur = preferences[HTTP_REQUEST_ICON_REVISION_KEY] ?: 0
            preferences[HTTP_REQUEST_ICON_REVISION_KEY] = cur + 1
        }
    }

    suspend fun bumpTileBackgroundImageRevision() {
        context.settingsDataStore.edit { preferences ->
            val cur = preferences[TILE_BACKGROUND_IMAGE_REVISION_KEY] ?: 0
            preferences[TILE_BACKGROUND_IMAGE_REVISION_KEY] = cur + 1
        }
    }

    suspend fun bumpPanelBackgroundImageRevision() {
        context.settingsDataStore.edit { preferences ->
            val cur = preferences[PANEL_BACKGROUND_IMAGE_REVISION_KEY] ?: 0
            preferences[PANEL_BACKGROUND_IMAGE_REVISION_KEY] = cur + 1
        }
    }

    /**
     * Copies an image into [PanelBackgroundImageStorage.DIR_NAME] for the given panel and theme.
     * [sourceUri] `null` removes the file for that panel/theme. Returned path is suitable for
     * [MainScreenPanelConfig.panelBackgroundImageRelPathLight] / Dark (and floating equivalents).
     */
    suspend fun setPanelBackgroundImageFromUri(
        panelStorageId: String,
        darkTheme: Boolean,
        sourceUri: Uri?,
    ): Pair<SetTileBackgroundImageResult, String?> {
        return withContext(Dispatchers.IO) {
            val rel = PanelBackgroundImageStorage.relativePathFor(panelStorageId, darkTheme)
            val dest = File(context.filesDir, rel.replace('/', File.separatorChar))
            dest.parentFile?.mkdirs()
            if (sourceUri == null) {
                val lookup = launcherAppIconLookup()
                if (PanelBackgroundImageStorage.themeTargetsIncludePanelBackgrounds(lookup) &&
                    PanelBackgroundImageStorage.deleteThemeCacheFile(
                        context.filesDir,
                        rel,
                        lookup.activeThemeCacheKey,
                    )
                ) {
                    bumpPanelBackgroundImageRevision()
                    val stillVisible = PanelBackgroundImageStorage.hasResolvableFile(
                        context.filesDir,
                        rel,
                        lookup,
                    )
                    return@withContext Pair(
                        SetTileBackgroundImageResult.Success,
                        if (stillVisible) rel else null,
                    )
                }
                if (PanelBackgroundImageStorage.deleteSharedFile(context.filesDir, rel)) {
                    bumpPanelBackgroundImageRevision()
                }
                return@withContext Pair(SetTileBackgroundImageResult.Success, null)
            }
            val bounds = runCatching {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    BitmapFactory.decodeStream(input, null, opts)
                }
                opts
            }.getOrNull() ?: return@withContext Pair(SetTileBackgroundImageResult.NotImageOrUnreadable, null)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return@withContext Pair(SetTileBackgroundImageResult.NotImageOrUnreadable, null)
            }
            if (bounds.outWidth > MAX_TILE_BACKGROUND_EDGE_PX ||
                bounds.outHeight > MAX_TILE_BACKGROUND_EDGE_PX
            ) {
                return@withContext Pair(SetTileBackgroundImageResult.DimensionsTooLarge, null)
            }
            val copiedOk = runCatching {
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                dest.exists() && dest.length() > 0L && dest.length() <= MAX_TILE_BACKGROUND_BYTES
            }.getOrElse {
                if (dest.exists()) dest.delete()
                false
            }
            if (!copiedOk) {
                if (dest.exists()) dest.delete()
                return@withContext Pair(SetTileBackgroundImageResult.CopyFailed, null)
            }
            val decoded = BitmapFactory.decodeFile(dest.absolutePath)
            if (decoded == null) {
                dest.delete()
                return@withContext Pair(SetTileBackgroundImageResult.NotImageOrUnreadable, null)
            }
            decoded.recycle()
            bumpPanelBackgroundImageRevision()
            Pair(SetTileBackgroundImageResult.Success, rel)
        }
    }

    /**
     * Copies an image into [TileBackgroundImageStorage.DIR_NAME] for the given panel slot and theme.
     * [sourceUri] `null` removes the file for that slot/theme. Returned path is suitable for
     * [FloatingDashboardWidgetConfig.tileBackgroundImageRelPathLight] / Dark.
     */
    suspend fun setTileBackgroundImageFromUri(
        panelStorageId: String,
        widgetIndex: Int,
        darkTheme: Boolean,
        sourceUri: Uri?,
    ): Pair<SetTileBackgroundImageResult, String?> {
        return withContext(Dispatchers.IO) {
            val rel = TileBackgroundImageStorage.relativePathFor(panelStorageId, widgetIndex, darkTheme)
            val dest = File(context.filesDir, rel.replace('/', File.separatorChar))
            dest.parentFile?.mkdirs()
            if (sourceUri == null) {
                val lookup = launcherAppIconLookup()
                if (TileBackgroundImageStorage.themeTargetsIncludeTileBackgrounds(lookup) &&
                    TileBackgroundImageStorage.deleteThemeCacheFile(
                        context.filesDir,
                        rel,
                        lookup.activeThemeCacheKey,
                    )
                ) {
                    bumpTileBackgroundImageRevision()
                    val stillVisible = TileBackgroundImageStorage.hasResolvableFile(
                        context.filesDir,
                        rel,
                        lookup,
                    )
                    return@withContext Pair(
                        SetTileBackgroundImageResult.Success,
                        if (stillVisible) rel else null,
                    )
                }
                if (TileBackgroundImageStorage.deleteSharedFile(context.filesDir, rel)) {
                    bumpTileBackgroundImageRevision()
                }
                return@withContext Pair(SetTileBackgroundImageResult.Success, null)
            }
            val bounds = runCatching {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    BitmapFactory.decodeStream(input, null, opts)
                }
                opts
            }.getOrNull() ?: return@withContext Pair(SetTileBackgroundImageResult.NotImageOrUnreadable, null)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return@withContext Pair(SetTileBackgroundImageResult.NotImageOrUnreadable, null)
            }
            if (bounds.outWidth > MAX_TILE_BACKGROUND_EDGE_PX ||
                bounds.outHeight > MAX_TILE_BACKGROUND_EDGE_PX
            ) {
                return@withContext Pair(SetTileBackgroundImageResult.DimensionsTooLarge, null)
            }
            val copiedOk = runCatching {
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                dest.exists() && dest.length() > 0L && dest.length() <= MAX_TILE_BACKGROUND_BYTES
            }.getOrElse {
                if (dest.exists()) dest.delete()
                false
            }
            if (!copiedOk) {
                if (dest.exists()) dest.delete()
                return@withContext Pair(SetTileBackgroundImageResult.CopyFailed, null)
            }
            val decoded = BitmapFactory.decodeFile(dest.absolutePath)
            if (decoded == null) {
                dest.delete()
                return@withContext Pair(SetTileBackgroundImageResult.NotImageOrUnreadable, null)
            }
            decoded.recycle()
            bumpTileBackgroundImageRevision()
            Pair(SetTileBackgroundImageResult.Success, rel)
        }
    }

    suspend fun setCustomLauncherAppIconFromUri(
        packageName: String,
        sourceUri: Uri?,
    ): SetLauncherAppCustomIconResult {
        if (packageName.isBlank()) return SetLauncherAppCustomIconResult.InvalidPackage
        return withContext(Dispatchers.IO) {
            val dest = launcherAppIconFile(packageName)
            dest.parentFile?.mkdirs()
            if (sourceUri == null) {
                if (dest.exists()) dest.delete()
                return@withContext SetLauncherAppCustomIconResult.Success
            }
            val bounds = runCatching {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    BitmapFactory.decodeStream(input, null, opts)
                }
                opts
            }.getOrNull() ?: return@withContext SetLauncherAppCustomIconResult.NotImageOrUnreadable
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return@withContext SetLauncherAppCustomIconResult.NotImageOrUnreadable
            }
            if (bounds.outWidth > MAX_LAUNCHER_APP_ICON_EDGE_PX ||
                bounds.outHeight > MAX_LAUNCHER_APP_ICON_EDGE_PX
            ) {
                return@withContext SetLauncherAppCustomIconResult.DimensionsTooLarge
            }
            val copiedOk = runCatching {
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                dest.exists() && dest.length() > 0L && dest.length() <= MAX_LAUNCHER_APP_ICON_BYTES
            }.getOrElse {
                if (dest.exists()) dest.delete()
                false
            }
            if (!copiedOk) {
                if (dest.exists()) dest.delete()
                return@withContext SetLauncherAppCustomIconResult.CopyFailed
            }
            val decoded = BitmapFactory.decodeFile(dest.absolutePath)
            if (decoded == null) {
                dest.delete()
                return@withContext SetLauncherAppCustomIconResult.NotImageOrUnreadable
            }
            decoded.recycle()
            bumpLauncherAppIconRevision()
            SetLauncherAppCustomIconResult.Success
        }
    }

    suspend fun setCustomHttpRequestIconFromUri(
        iconKey: String,
        sourceUri: Uri?,
    ): SetLauncherAppCustomIconResult {
        if (iconKey.isBlank()) return SetLauncherAppCustomIconResult.InvalidPackage
        return withContext(Dispatchers.IO) {
            val dest = httpRequestIconFile(iconKey)
            dest.parentFile?.mkdirs()
            if (sourceUri == null) {
                if (dest.exists()) dest.delete()
                bumpHttpRequestIconRevision()
                return@withContext SetLauncherAppCustomIconResult.Success
            }
            val bounds = runCatching {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    BitmapFactory.decodeStream(input, null, opts)
                }
                opts
            }.getOrNull() ?: return@withContext SetLauncherAppCustomIconResult.NotImageOrUnreadable
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return@withContext SetLauncherAppCustomIconResult.NotImageOrUnreadable
            }
            if (bounds.outWidth > MAX_LAUNCHER_APP_ICON_EDGE_PX ||
                bounds.outHeight > MAX_LAUNCHER_APP_ICON_EDGE_PX
            ) {
                return@withContext SetLauncherAppCustomIconResult.DimensionsTooLarge
            }
            val copiedOk = runCatching {
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                dest.exists() && dest.length() > 0L && dest.length() <= MAX_LAUNCHER_APP_ICON_BYTES
            }.getOrElse {
                if (dest.exists()) dest.delete()
                false
            }
            if (!copiedOk) {
                if (dest.exists()) dest.delete()
                return@withContext SetLauncherAppCustomIconResult.CopyFailed
            }
            val decoded = BitmapFactory.decodeFile(dest.absolutePath)
            if (decoded == null) {
                dest.delete()
                return@withContext SetLauncherAppCustomIconResult.NotImageOrUnreadable
            }
            decoded.recycle()
            bumpHttpRequestIconRevision()
            SetLauncherAppCustomIconResult.Success
        }
    }

    suspend fun saveMainScreenDashboards(configs: List<MainScreenPanelConfig>) {
        val normalized = configs
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }
            .map {
                it.copy(
                    rows = it.rows.coerceIn(1, DASHBOARD_PANEL_MAX_GRID_DIMENSION),
                    cols = it.cols.coerceIn(1, DASHBOARD_PANEL_MAX_GRID_DIMENSION),
                    relX = it.relX.coerceIn(0f, 1f),
                    relY = it.relY.coerceIn(0f, 1f),
                    relWidth = it.relWidth.coerceIn(MIN_MAIN_SCREEN_PANEL_REL_FRACTION, 1f),
                    relHeight = it.relHeight.coerceIn(MIN_MAIN_SCREEN_PANEL_REL_FRACTION, 1f)
                )
            }
        saveCustomString(MAIN_SCREEN_DASHBOARDS_LIST_KEY, serializeMainScreenDashboards(normalized))
    }

    suspend fun ensureDefaultFloatingDashboards() {
        // Historical API: empty floating panel list is valid; no default injection.
    }

    // Улучшенные методы для кастомных строк
    suspend fun saveCustomString(key: String, value: String) {
        val preferencesKey = getStringKey(key)
        context.settingsDataStore.edit { preferences ->
            preferences[preferencesKey] = value
        }
    }

    fun getStringFlow(key: String, defaultValue: String = ""): Flow<String> {
        val preferencesKey = getStringKey(key)
        return context.settingsDataStore.data
            .map { preferences -> preferences[preferencesKey] ?: defaultValue }
            .distinctUntilChanged()
    }

    private fun getStringKey(key: String): Preferences.Key<String> {
        return stringKeysCache.getOrPut(key) {
            stringPreferencesKey("${KEY_PREFIX}$key")
        }
    }

    suspend fun saveDashboardRows(config: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[DASHBOARD_ROWS_KEY] = config
        }
    }

    suspend fun saveDashboardCols(config: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[DASHBOARD_COLS_KEY] = config
        }
    }

    suspend fun saveDashboardChart(config: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[DASHBOARD_CHART_KEY] = config
        }
    }

    suspend fun saveDashboardGridSpacingDp(config: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[DASHBOARD_GRID_SPACING_KEY] = normalizePanelGridSpacingDp(config)
        }
    }

    suspend fun saveFloatingPanelsLayoutSnapDp(config: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[FLOATING_PANELS_LAYOUT_SNAP_DP_KEY] = normalizePanelLayoutSnapDp(config)
        }
    }

    suspend fun saveMainScreenPanelsLayoutSnapDp(config: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[MAIN_SCREEN_PANELS_LAYOUT_SNAP_DP_KEY] = normalizePanelLayoutSnapDp(config)
        }
    }

    suspend fun saveMainScreenPanelsLayoutSnapEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[MAIN_SCREEN_PANELS_LAYOUT_SNAP_ENABLED_KEY] = enabled
        }
    }

    suspend fun saveMainScreenShowLayoutGrid(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[MAIN_SCREEN_SHOW_LAYOUT_GRID_KEY] = enabled
        }
    }

    suspend fun saveCanDataSaveCount(config: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[CAN_DATA_SAVE_COUNT_KEY] = config
        }
    }

    suspend fun saveFuelTankLiters(liters: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[FUEL_TANK_LITERS_KEY] = liters.coerceIn(1, 500)
        }
    }

    suspend fun saveSpeedLimiterTargetKmh(kmh: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[SPEED_LIMITER_TARGET_KMH_KEY] =
                SlaSpeedLimitDomain.clampLimiterTargetKmh(kmh)
        }
    }

    /** Объём бака и сброс JSON калибровки в одной транзакции DataStore. */
    suspend fun saveFuelTankLitersAndClearFuelCalibration(liters: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[FUEL_TANK_LITERS_KEY] = liters.coerceIn(1, 500)
            preferences[FUEL_CALIBRATION_JSON_KEY] = ""
        }
    }

    /** Число зон и сброс JSON калибровки. */
    suspend fun saveFuelCalibrationZoneCountAndClearCalibration(zoneCount: Int) {
        val z = zoneCount.coerceIn(FUEL_CALIBRATION_ZONE_COUNT_MIN, FUEL_CALIBRATION_ZONE_COUNT_MAX)
        context.settingsDataStore.edit { preferences ->
            preferences[FUEL_CALIBRATION_ZONE_COUNT_KEY] = z
            preferences[FUEL_CALIBRATION_JSON_KEY] = ""
        }
    }

    suspend fun saveFuelCalibrationJson(json: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[FUEL_CALIBRATION_JSON_KEY] = json
        }
    }

    suspend fun saveFuelCalibrationZoneCount(zoneCount: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[FUEL_CALIBRATION_ZONE_COUNT_KEY] =
                zoneCount.coerceIn(FUEL_CALIBRATION_ZONE_COUNT_MIN, FUEL_CALIBRATION_ZONE_COUNT_MAX)
        }
    }

    /** Порог зрелости зон (л датчика); JSON калибровки не сбрасывается. */
    suspend fun saveFuelCalibrationMaturityThreshold(thresholdLiters: Int) {
        val t = thresholdLiters.coerceIn(
            FUEL_CALIBRATION_MATURITY_THRESHOLD_MIN,
            FUEL_CALIBRATION_MATURITY_THRESHOLD_MAX,
        )
        context.settingsDataStore.edit { preferences ->
            preferences[FUEL_CALIBRATION_MATURITY_THRESHOLD_KEY] = t
        }
    }

    suspend fun clearFuelCalibrationJson() {
        context.settingsDataStore.edit { preferences ->
            preferences[FUEL_CALIBRATION_JSON_KEY] = ""
        }
    }

    suspend fun saveFuelPriceFuelId(fuelId: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[FUEL_PRICE_FUEL_ID_KEY] = FuelTypes.optionFor(fuelId).id
        }
    }

    suspend fun saveSplitTripTimeMinutes(minutes: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[SPLIT_TRIP_TIME_MINUTES_KEY] = minutes.coerceIn(1, 100000)
        }
    }

    suspend fun saveTrackRefuelsSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[TRACK_REFUELS_KEY] = enabled
        }
    }

    suspend fun saveWheelPressurePersistAcrossStopsSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[WHEEL_PRESSURE_PERSIST_ACROSS_STOPS_KEY] = enabled
        }
    }

    suspend fun saveUiClickSoundsSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[UI_CLICK_SOUNDS_KEY] = enabled
        }
    }

    suspend fun saveAppFontFamilyId(fontFamilyId: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[APP_FONT_FAMILY_ID_KEY] = TboxFontFamily.fromId(fontFamilyId).id
        }
    }

    suspend fun saveUpdateChannel(channel: vad.dashing.tbox.update.UpdateChannel) {
        context.settingsDataStore.edit { preferences ->
            preferences[UPDATE_CHANNEL_KEY] = channel.storageValue
        }
    }

    suspend fun saveUpdateCheckEnabledSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[UPDATE_CHECK_ENABLED_KEY] = enabled
        }
    }

    suspend fun saveHeadUnitCanMode(mode: HeadUnitCanMode) {
        context.settingsDataStore.edit { preferences ->
            preferences[HEAD_UNIT_CAN_MODE_KEY] = mode.storageValue
        }
    }

    suspend fun saveHeadUnitCanModeByUser(mode: HeadUnitCanMode) {
        context.settingsDataStore.edit { preferences ->
            preferences[HEAD_UNIT_CAN_MODE_KEY] = mode.storageValue
            preferences[CAN_AUTO_BIND_LOCKED_KEY] = false
            preferences.remove(CAN_AUTO_BIND_LAST_RESULT_KEY)
        }
    }

    suspend fun saveCanAutoBindEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[CAN_AUTO_BIND_ENABLED_KEY] = enabled
        }
    }

    suspend fun saveCanAutoBindLocked(locked: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[CAN_AUTO_BIND_LOCKED_KEY] = locked
        }
    }

    suspend fun saveCanAutoBindLastPrimaryMode(mode: HeadUnitCanMode?) {
        context.settingsDataStore.edit { preferences ->
            if (mode == null) {
                preferences.remove(CAN_AUTO_BIND_LAST_PRIMARY_MODE_KEY)
            } else {
                preferences[CAN_AUTO_BIND_LAST_PRIMARY_MODE_KEY] = mode.storageValue
            }
        }
    }

    suspend fun saveCanAutoBindLastResult(result: String) {
        context.settingsDataStore.edit { preferences ->
            if (result.isBlank()) {
                preferences.remove(CAN_AUTO_BIND_LAST_RESULT_KEY)
            } else {
                preferences[CAN_AUTO_BIND_LAST_RESULT_KEY] = result
            }
        }
    }

    private fun parseMainScreenSettingsButtonJson(raw: String): MainScreenSettingsButtonPosition {
        if (raw.isBlank()) return MainScreenSettingsButtonPosition.Default
        return try {
            val o = JSONObject(raw)
            MainScreenSettingsButtonPosition(
                x = o.optDouble("x", MainScreenSettingsButtonPosition.Default.x.toDouble())
                    .toFloat()
                    .coerceIn(0f, 1f),
                y = o.optDouble("y", MainScreenSettingsButtonPosition.Default.y.toDouble())
                    .toFloat()
                    .coerceIn(0f, 1f)
            )
        } catch (_: Exception) {
            MainScreenSettingsButtonPosition.Default
        }
    }

    private fun parseMainScreenAddButtonJson(raw: String): MainScreenAddButtonPosition {
        if (raw.isBlank()) return MainScreenAddButtonPosition.Default
        return try {
            val o = JSONObject(raw)
            MainScreenAddButtonPosition(
                x = o.optDouble("x", MainScreenAddButtonPosition.Default.x.toDouble())
                    .toFloat()
                    .coerceIn(0f, 1f),
                y = o.optDouble("y", MainScreenAddButtonPosition.Default.y.toDouble())
                    .toFloat()
                    .coerceIn(0f, 1f)
            )
        } catch (_: Exception) {
            MainScreenAddButtonPosition.Default
        }
    }

    private fun parseMainScreenPagePrevButtonJson(raw: String): MainScreenPagePrevButtonPosition {
        if (raw.isBlank()) return MainScreenPagePrevButtonPosition.Default
        return try {
            val o = JSONObject(raw)
            MainScreenPagePrevButtonPosition(
                x = o.optDouble("x", MainScreenPagePrevButtonPosition.Default.x.toDouble())
                    .toFloat()
                    .coerceIn(0f, 1f),
                y = o.optDouble("y", MainScreenPagePrevButtonPosition.Default.y.toDouble())
                    .toFloat()
                    .coerceIn(0f, 1f),
            )
        } catch (_: Exception) {
            MainScreenPagePrevButtonPosition.Default
        }
    }

    private fun parseMainScreenPageNextButtonJson(raw: String): MainScreenPageNextButtonPosition {
        if (raw.isBlank()) return MainScreenPageNextButtonPosition.Default
        return try {
            val o = JSONObject(raw)
            MainScreenPageNextButtonPosition(
                x = o.optDouble("x", MainScreenPageNextButtonPosition.Default.x.toDouble())
                    .toFloat()
                    .coerceIn(0f, 1f),
                y = o.optDouble("y", MainScreenPageNextButtonPosition.Default.y.toDouble())
                    .toFloat()
                    .coerceIn(0f, 1f),
            )
        } catch (_: Exception) {
            MainScreenPageNextButtonPosition.Default
        }
    }

    /** Null when never configured — caller should use [MainScreenWindowModeGeometry.defaultForDisplay]. */
    private fun parseMainScreenWindowModeGeometryJson(raw: String): MainScreenWindowModeGeometry? {
        if (raw.isBlank()) return null
        return try {
            val o = JSONObject(raw)
            MainScreenWindowModeGeometry(
                startX = o.optInt("startX", 0),
                startY = o.optInt("startY", 0),
                width = o.optInt("width", MainScreenWindowModeGeometry.MIN_SIZE),
                height = o.optInt("height", MainScreenWindowModeGeometry.MIN_SIZE),
            ).normalized()
        } catch (_: Exception) {
            null
        }
    }

    private fun parseMainScreenWindowModeExitButtonJson(
        raw: String,
        default: MainScreenWindowModeExitButtonPosition,
    ): MainScreenWindowModeExitButtonPosition {
        if (raw.isBlank()) return default
        return try {
            val o = JSONObject(raw)
            MainScreenWindowModeExitButtonPosition(
                x = o.optDouble("x", default.x.toDouble())
                    .toFloat()
                    .coerceIn(0f, 1f),
                y = o.optDouble("y", default.y.toDouble())
                    .toFloat()
                    .coerceIn(0f, 1f),
            )
        } catch (_: Exception) {
            default
        }
    }

    private fun parseDriveModeThemePathsJson(raw: String): Map<Int, String> {
        if (raw.isBlank()) return emptyMap()
        return try {
            val root = JSONObject(raw)
            val out = linkedMapOf<Int, String>()
            root.keys().forEach { key ->
                val rawValue = key.toIntOrNull() ?: return@forEach
                val path = root.optString(key).trim()
                if (path.isNotEmpty()) {
                    out[rawValue] = path
                }
            }
            out
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun serializeDriveModeThemePaths(paths: Map<Int, String>): String {
        val root = JSONObject()
        paths.toSortedMap().forEach { (rawValue, uri) ->
            if (uri.isNotBlank()) {
                root.put(rawValue.toString(), uri)
            }
        }
        return root.toString()
    }

    private fun parseMainScreenDashboardsJson(
        json: String,
        pageCount: Int = DEFAULT_MAIN_SCREEN_PAGE_COUNT,
    ): List<MainScreenPanelConfig> {
        if (json.isBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            val configs = mutableListOf<MainScreenPanelConfig>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val config = parseMainScreenPanelConfig(obj, pageCount) ?: continue
                configs.add(config)
            }
            configs
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseMainScreenPanelConfig(
        obj: JSONObject,
        pageCount: Int = DEFAULT_MAIN_SCREEN_PAGE_COUNT,
    ): MainScreenPanelConfig? {
        val id = obj.optString("id").trim()
        if (id.isEmpty()) return null
        val name = obj.optString("name").ifBlank { id }
        val style = parsePanelBackgroundStyleFieldsDataStore(obj)
        return MainScreenPanelConfig(
            id = id,
            name = name,
            enabled = obj.optBoolean("enabled", DEFAULT_MAIN_SCREEN_PANEL_ENABLED),
            widgetsConfig = parseWidgetConfigsFromAny(obj.opt("widgetsConfig")),
            rows = obj.optInt("rows", DEFAULT_MAIN_SCREEN_PANEL_ROWS)
                .coerceIn(1, DASHBOARD_PANEL_MAX_GRID_DIMENSION),
            cols = obj.optInt("cols", DEFAULT_MAIN_SCREEN_PANEL_COLS)
                .coerceIn(1, DASHBOARD_PANEL_MAX_GRID_DIMENSION),
            relX = obj.optDouble("relX", DEFAULT_MAIN_SCREEN_PANEL_REL_X.toDouble()).toFloat()
                .coerceIn(0f, 1f),
            relY = obj.optDouble("relY", DEFAULT_MAIN_SCREEN_PANEL_REL_Y.toDouble()).toFloat()
                .coerceIn(0f, 1f),
            relWidth = obj.optDouble("relWidth", DEFAULT_MAIN_SCREEN_PANEL_REL_WIDTH.toDouble())
                .toFloat().coerceIn(MIN_MAIN_SCREEN_PANEL_REL_FRACTION, 1f),
            relHeight = obj.optDouble("relHeight", DEFAULT_MAIN_SCREEN_PANEL_REL_HEIGHT.toDouble())
                .toFloat().coerceIn(MIN_MAIN_SCREEN_PANEL_REL_FRACTION, 1f),
            background = obj.optBoolean("background", DEFAULT_MAIN_SCREEN_PANEL_BACKGROUND),
            clickAction = obj.optBoolean("clickAction", DEFAULT_MAIN_SCREEN_PANEL_CLICK_ACTION),
            showTboxDisconnectIndicator = obj.optBoolean(
                "showTboxDisconnectIndicator",
                DEFAULT_MAIN_SCREEN_PANEL_SHOW_TBOX_DISCONNECT
            ),
            pageNumber = PagingStateNormalizer.normalizePanelPageNumber(
                obj.optInt("pageNumber", DEFAULT_MAIN_SCREEN_PANEL_PAGE_NUMBER),
                pageCount,
            ),
            gridSpacingDp = normalizePanelGridSpacingDp(
                obj.optInt("gridSpacingDp", DEFAULT_PANEL_GRID_SPACING_DP)
            ),
            collapseEdge = PanelCollapseEdge.fromStorage(obj.optString("collapseEdge")).storageValue,
            collapseStripThicknessDp = normalizePanelCollapseStripThicknessDp(
                obj.optInt("collapseStripThicknessDp", DEFAULT_PANEL_COLLAPSE_STRIP_THICKNESS_DP)
            ),
            collapseStripColorLight = obj.optInt(
                "collapseStripColorLight",
                DEFAULT_PANEL_COLLAPSE_STRIP_COLOR_LIGHT,
            ),
            collapseStripColorDark = obj.optInt(
                "collapseStripColorDark",
                DEFAULT_PANEL_COLLAPSE_STRIP_COLOR_DARK,
            ),
            collapseStripExpandedColorLight = obj.optInt(
                "collapseStripExpandedColorLight",
                DEFAULT_PANEL_COLLAPSE_STRIP_EXPANDED_COLOR_LIGHT,
            ),
            collapseStripExpandedColorDark = obj.optInt(
                "collapseStripExpandedColorDark",
                DEFAULT_PANEL_COLLAPSE_STRIP_EXPANDED_COLOR_DARK,
            ),
            collapseOnTileTap = obj.optBoolean(
                "collapseOnTileTap",
                DEFAULT_PANEL_COLLAPSE_ON_TILE_TAP,
            ),
            collapseOnTileTapDelaySec = normalizePanelCollapseOnTileTapDelaySec(
                obj.optInt(
                    "collapseOnTileTapDelaySec",
                    DEFAULT_PANEL_COLLAPSE_ON_TILE_TAP_DELAY_SEC,
                ),
            ),
            panelBackgroundColorLight = style.backgroundColorLight,
            panelBackgroundColorDark = style.backgroundColorDark,
            panelBackgroundImageRelPathLight = style.backgroundImageRelPathLight,
            panelBackgroundImageRelPathDark = style.backgroundImageRelPathDark,
            panelShape = style.panelShape,
        )
    }

    private fun serializeMainScreenDashboards(configs: List<MainScreenPanelConfig>): String {
        val array = JSONArray()
        configs.forEach { config ->
            val o = JSONObject()
            o.put("id", config.id)
            o.put("name", config.name)
            o.put("enabled", config.enabled)
            o.put("widgetsConfig", serializeWidgetConfigsToJsonArray(config.widgetsConfig))
            o.put("rows", config.rows)
            o.put("cols", config.cols)
            o.put("relX", config.relX.toDouble())
            o.put("relY", config.relY.toDouble())
            o.put("relWidth", config.relWidth.toDouble())
            o.put("relHeight", config.relHeight.toDouble())
            o.put("background", config.background)
            o.put("clickAction", config.clickAction)
            o.put("showTboxDisconnectIndicator", config.showTboxDisconnectIndicator)
            o.put("pageNumber", config.pageNumber)
            if (config.gridSpacingDp != DEFAULT_PANEL_GRID_SPACING_DP) {
                o.put("gridSpacingDp", config.gridSpacingDp)
            }
            putPanelCollapseFields(o, config)
            putPanelBackgroundStyleFieldsDataStore(
                o = o,
                backgroundColorLight = config.panelBackgroundColorLight,
                backgroundColorDark = config.panelBackgroundColorDark,
                backgroundImageRelPathLight = config.panelBackgroundImageRelPathLight,
                backgroundImageRelPathDark = config.panelBackgroundImageRelPathDark,
                panelShape = config.panelShape,
            )
            array.put(o)
        }
        return array.toString()
    }

    private fun parseFloatingDashboardsJson(json: String): List<FloatingDashboardConfig> {
        if (json.isBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            val configs = mutableListOf<FloatingDashboardConfig>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val config = parseFloatingDashboardConfig(obj) ?: continue
                configs.add(config)
            }
            configs
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseFloatingDashboardConfig(obj: JSONObject): FloatingDashboardConfig? {
        val id = obj.optString("id").trim()
        if (id.isEmpty()) return null
        val name = obj.optString("name").ifBlank { id }
        val style = parsePanelBackgroundStyleFieldsDataStore(obj)
        return FloatingDashboardConfig(
            id = id,
            name = name,
            enabled = obj.optBoolean("enabled", DEFAULT_FLOATING_DASHBOARD_ENABLED),
            widgetsConfig = parseWidgetConfigsFromAny(obj.opt("widgetsConfig")),
            rows = obj.optInt("rows", DEFAULT_FLOATING_DASHBOARD_ROWS)
                .coerceIn(1, DASHBOARD_PANEL_MAX_GRID_DIMENSION),
            cols = obj.optInt("cols", DEFAULT_FLOATING_DASHBOARD_COLS)
                .coerceIn(1, DASHBOARD_PANEL_MAX_GRID_DIMENSION),
            width = obj.optInt("width", DEFAULT_FLOATING_DASHBOARD_WIDTH),
            height = obj.optInt("height", DEFAULT_FLOATING_DASHBOARD_HEIGHT),
            startX = obj.optInt("startX", DEFAULT_FLOATING_DASHBOARD_START_X),
            startY = obj.optInt("startY", DEFAULT_FLOATING_DASHBOARD_START_Y),
            background = obj.optBoolean("background", DEFAULT_FLOATING_DASHBOARD_BACKGROUND),
            clickAction = obj.optBoolean("clickAction", DEFAULT_FLOATING_DASHBOARD_CLICK_ACTION),
            showTboxDisconnectIndicator = obj.optBoolean(
                "showTboxDisconnectIndicator",
                DEFAULT_FLOATING_DASHBOARD_SHOW_TBOX_DISCONNECT_INDICATOR
            ),
            gridSpacingDp = normalizePanelGridSpacingDp(
                obj.optInt("gridSpacingDp", DEFAULT_PANEL_GRID_SPACING_DP)
            ),
            collapseEdge = PanelCollapseEdge.fromStorage(obj.optString("collapseEdge")).storageValue,
            collapseStripThicknessDp = normalizePanelCollapseStripThicknessDp(
                obj.optInt("collapseStripThicknessDp", DEFAULT_PANEL_COLLAPSE_STRIP_THICKNESS_DP)
            ),
            collapseStripColorLight = obj.optInt(
                "collapseStripColorLight",
                DEFAULT_PANEL_COLLAPSE_STRIP_COLOR_LIGHT,
            ),
            collapseStripColorDark = obj.optInt(
                "collapseStripColorDark",
                DEFAULT_PANEL_COLLAPSE_STRIP_COLOR_DARK,
            ),
            collapseStripExpandedColorLight = obj.optInt(
                "collapseStripExpandedColorLight",
                DEFAULT_PANEL_COLLAPSE_STRIP_EXPANDED_COLOR_LIGHT,
            ),
            collapseStripExpandedColorDark = obj.optInt(
                "collapseStripExpandedColorDark",
                DEFAULT_PANEL_COLLAPSE_STRIP_EXPANDED_COLOR_DARK,
            ),
            collapseOnTileTap = obj.optBoolean(
                "collapseOnTileTap",
                DEFAULT_PANEL_COLLAPSE_ON_TILE_TAP,
            ),
            collapseOnTileTapDelaySec = normalizePanelCollapseOnTileTapDelaySec(
                obj.optInt(
                    "collapseOnTileTapDelaySec",
                    DEFAULT_PANEL_COLLAPSE_ON_TILE_TAP_DELAY_SEC,
                ),
            ),
            panelBackgroundColorLight = style.backgroundColorLight,
            panelBackgroundColorDark = style.backgroundColorDark,
            panelBackgroundImageRelPathLight = style.backgroundImageRelPathLight,
            panelBackgroundImageRelPathDark = style.backgroundImageRelPathDark,
            panelShape = style.panelShape,
        )
    }

    private fun serializeFloatingDashboards(configs: List<FloatingDashboardConfig>): String {
        val array = JSONArray()
        configs.forEach { config ->
            val obj = JSONObject()
            obj.put("id", config.id)
            obj.put("name", config.name)
            obj.put("enabled", config.enabled)
            obj.put("widgetsConfig", serializeWidgetConfigsToJsonArray(config.widgetsConfig))
            obj.put("rows", config.rows)
            obj.put("cols", config.cols)
            obj.put("width", config.width)
            obj.put("height", config.height)
            obj.put("startX", config.startX)
            obj.put("startY", config.startY)
            obj.put("background", config.background)
            obj.put("clickAction", config.clickAction)
            obj.put("showTboxDisconnectIndicator", config.showTboxDisconnectIndicator)
            if (config.gridSpacingDp != DEFAULT_PANEL_GRID_SPACING_DP) {
                obj.put("gridSpacingDp", config.gridSpacingDp)
            }
            putPanelCollapseFields(obj, config)
            putPanelBackgroundStyleFieldsDataStore(
                o = obj,
                backgroundColorLight = config.panelBackgroundColorLight,
                backgroundColorDark = config.panelBackgroundColorDark,
                backgroundImageRelPathLight = config.panelBackgroundImageRelPathLight,
                backgroundImageRelPathDark = config.panelBackgroundImageRelPathDark,
                panelShape = config.panelShape,
            )
            array.put(obj)
        }
        return array.toString()
    }

    private fun putPanelCollapseFields(o: JSONObject, config: MainScreenPanelConfig) {
        putPanelCollapseFields(
            o = o,
            collapseEdge = config.collapseEdge,
            collapseStripThicknessDp = config.collapseStripThicknessDp,
            collapseStripColorLight = config.collapseStripColorLight,
            collapseStripColorDark = config.collapseStripColorDark,
            collapseStripExpandedColorLight = config.collapseStripExpandedColorLight,
            collapseStripExpandedColorDark = config.collapseStripExpandedColorDark,
            collapseOnTileTap = config.collapseOnTileTap,
            collapseOnTileTapDelaySec = config.collapseOnTileTapDelaySec,
        )
    }

    private fun putPanelCollapseFields(o: JSONObject, config: FloatingDashboardConfig) {
        putPanelCollapseFields(
            o = o,
            collapseEdge = config.collapseEdge,
            collapseStripThicknessDp = config.collapseStripThicknessDp,
            collapseStripColorLight = config.collapseStripColorLight,
            collapseStripColorDark = config.collapseStripColorDark,
            collapseStripExpandedColorLight = config.collapseStripExpandedColorLight,
            collapseStripExpandedColorDark = config.collapseStripExpandedColorDark,
            collapseOnTileTap = config.collapseOnTileTap,
            collapseOnTileTapDelaySec = config.collapseOnTileTapDelaySec,
        )
    }

    private fun putPanelCollapseFields(
        o: JSONObject,
        collapseEdge: String,
        collapseStripThicknessDp: Int,
        collapseStripColorLight: Int,
        collapseStripColorDark: Int,
        collapseStripExpandedColorLight: Int,
        collapseStripExpandedColorDark: Int,
        collapseOnTileTap: Boolean,
        collapseOnTileTapDelaySec: Int,
    ) {
        val edge = PanelCollapseEdge.fromStorage(collapseEdge)
        if (edge != PanelCollapseEdge.NONE) {
            o.put("collapseEdge", edge.storageValue)
        }
        if (collapseStripThicknessDp != DEFAULT_PANEL_COLLAPSE_STRIP_THICKNESS_DP) {
            o.put("collapseStripThicknessDp", collapseStripThicknessDp)
        }
        if (collapseStripColorLight != DEFAULT_PANEL_COLLAPSE_STRIP_COLOR_LIGHT) {
            o.put("collapseStripColorLight", collapseStripColorLight)
        }
        if (collapseStripColorDark != DEFAULT_PANEL_COLLAPSE_STRIP_COLOR_DARK) {
            o.put("collapseStripColorDark", collapseStripColorDark)
        }
        if (collapseStripExpandedColorLight != DEFAULT_PANEL_COLLAPSE_STRIP_EXPANDED_COLOR_LIGHT) {
            o.put("collapseStripExpandedColorLight", collapseStripExpandedColorLight)
        }
        if (collapseStripExpandedColorDark != DEFAULT_PANEL_COLLAPSE_STRIP_EXPANDED_COLOR_DARK) {
            o.put("collapseStripExpandedColorDark", collapseStripExpandedColorDark)
        }
        if (collapseOnTileTap != DEFAULT_PANEL_COLLAPSE_ON_TILE_TAP) {
            o.put("collapseOnTileTap", collapseOnTileTap)
        }
        if (collapseOnTileTapDelaySec != DEFAULT_PANEL_COLLAPSE_ON_TILE_TAP_DELAY_SEC) {
            o.put(
                "collapseOnTileTapDelaySec",
                normalizePanelCollapseOnTileTapDelaySec(collapseOnTileTapDelaySec),
            )
        }
    }

    suspend fun exportFullBackupJson(
        appDataManager: AppDataManager,
        excludeTripsAndRefuels: Boolean = false,
    ): String =
        SettingsBackupCoordinator.exportFullJson(
            context.packageName,
            context.settingsDataStore,
            appDataManager.preferencesDataStore,
            excludeTripAndRefuelLists = excludeTripsAndRefuels,
        )

    suspend fun importFullBackupJson(appDataManager: AppDataManager, json: String): Result<Unit> {
        val result = SettingsBackupCoordinator.importFullJson(
            appDataManager,
            context.settingsDataStore,
            appDataManager.preferencesDataStore,
            json,
        )
        if (result.isSuccess) {
            clearNonExportedLocalAssetsAfterBackupImport()
            ThemeSettingsValidator.validateOnStartup(context, this)
        }
        return result
    }

}
