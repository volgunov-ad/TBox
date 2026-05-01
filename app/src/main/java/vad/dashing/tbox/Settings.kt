package vad.dashing.tbox

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import vad.dashing.tbox.ui.theme.DARK_THEME_BACKGROUND_COLOR_PRESET_2_INT
import vad.dashing.tbox.ui.theme.LIGHT_THEME_BACKGROUND_COLOR_PRESET_2_INT

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
    /** Package name of the app to launch (only for `appLauncherWidget`). */
    val launcherAppPackage: String = "",
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
    val valueAccuracy: Int? = null
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
    val showTboxDisconnectIndicator: Boolean = false
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
    val showTboxDisconnectIndicator: Boolean = true
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
    val autoPreventTboxRestart: Boolean,
    val getCanFrame: Boolean,
    val getCycleSignal: Boolean,
    val getLocData: Boolean,
    val widgetShowIndicator: Boolean,
    val widgetShowLocIndicator: Boolean,
    val mockLocation: Boolean,
    val floatingDashboards: List<FloatingDashboardConfig>,
    val canDataSaveCount: Int,
    val fuelTankLiters: Int,
    /** JSON калибровки топлива (пустая строка — нет данных). */
    val fuelCalibrationJson: String,
    /** Число зон бака для калибровки. */
    val fuelCalibrationZoneCount: Int,
    val fuelPriceFuelId: Int,
    val splitTripTimeMinutes: Int,
)

class SettingsManager(private val context: Context) {

    companion object {
        /** Tab index that shows the home [vad.dashing.tbox.ui.MainScreen] instead of [vad.dashing.tbox.ui.TboxScreen]. */
        const val MAIN_SCREEN_SELECTED_TAB_INDEX = 100

        /** Left menu tab index for the Trips section ([vad.dashing.tbox.ui.TabItems]). */
        const val TRIPS_SELECTED_TAB_INDEX = 4

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
        private val AUTO_PREVENT_TBOX_RESTART_KEY = booleanPreferencesKey("${KEY_PREFIX}auto_prevent_tbox_restart")
        private val GET_VOLTAGES_KEY = booleanPreferencesKey("${KEY_PREFIX}get_voltages")
        private val GET_CAN_FRAME_KEY = booleanPreferencesKey("${KEY_PREFIX}get_can_frame")
        private val GET_CYCLE_SIGNAL_KEY = booleanPreferencesKey("${KEY_PREFIX}get_cycle_signal")
        private val GET_LOC_DATA_KEY = booleanPreferencesKey("${KEY_PREFIX}get_loc_data")
        private val WIDGET_SHOW_INDICATOR = booleanPreferencesKey("${KEY_PREFIX}widget_show_indicator")
        private val WIDGET_SHOW_LOC_INDICATOR = booleanPreferencesKey("${KEY_PREFIX}widget_show_loc_indicator")
        private val MOCK_LOCATION = booleanPreferencesKey("${KEY_PREFIX}mock_location")
        private val EXPERT_MODE = booleanPreferencesKey("${KEY_PREFIX}expert_mode")
        private val LEFT_MENU_VISIBLE = booleanPreferencesKey("${KEY_PREFIX}left_menu_visible")
        private val MAIN_SCREEN_OPEN_ON_BOOT_KEY =
            booleanPreferencesKey("${KEY_PREFIX}main_screen_open_on_boot")
        /** Legacy: copied single image per theme (pre folder-based wallpapers). */
        private val MAIN_SCREEN_WALLPAPER_LIGHT_SET_LEGACY_KEY =
            booleanPreferencesKey("${KEY_PREFIX}main_screen_wallpaper_light")
        private val MAIN_SCREEN_WALLPAPER_DARK_SET_LEGACY_KEY =
            booleanPreferencesKey("${KEY_PREFIX}main_screen_wallpaper_dark")
        private val MAIN_SCREEN_WALLPAPER_LIGHT_FOLDER_URI_KEY =
            stringPreferencesKey("${KEY_PREFIX}main_screen_wallpaper_light_folder_uri")
        private val MAIN_SCREEN_WALLPAPER_DARK_FOLDER_URI_KEY =
            stringPreferencesKey("${KEY_PREFIX}main_screen_wallpaper_dark_folder_uri")
        private val MAIN_SCREEN_WALLPAPER_LIGHT_SELECTED_FILE_KEY =
            stringPreferencesKey("${KEY_PREFIX}main_screen_wallpaper_light_selected_file")
        private val MAIN_SCREEN_WALLPAPER_DARK_SELECTED_FILE_KEY =
            stringPreferencesKey("${KEY_PREFIX}main_screen_wallpaper_dark_selected_file")
        private val MAIN_SCREEN_WALLPAPER_CROP_KEY =
            booleanPreferencesKey("${KEY_PREFIX}main_screen_wallpaper_crop")

        /** Bumped when launcher shortcut custom icons or wallpaper files are cleared — refreshes in-memory bitmaps. */
        private val LAUNCHER_APP_ICON_REVISION_KEY =
            intPreferencesKey("${KEY_PREFIX}launcher_app_icon_revision")

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

        /** Global user palette for [vad.dashing.tbox.ui.WidgetColorSetting] (six ARGB slots). */
        private val WIDGET_COLOR_PRESET_KEYS = Array(6) { i ->
            intPreferencesKey("${KEY_PREFIX}widget_color_preset_$i")
        }

        private val SELECTED_TAB_KEY = stringPreferencesKey("${KEY_PREFIX}selected_tab")

        private val DASHBOARD_ROWS_KEY = intPreferencesKey("${KEY_PREFIX}dashboard_rows")
        private val DASHBOARD_COLS_KEY = intPreferencesKey("${KEY_PREFIX}dashboard_cols")
        private val DASHBOARD_CHART_KEY = booleanPreferencesKey("${KEY_PREFIX}dashboard_chart")
        private val CAN_DATA_SAVE_COUNT_KEY = intPreferencesKey("${KEY_PREFIX}can_data_save_count")
        private val FUEL_TANK_LITERS_KEY = intPreferencesKey("${KEY_PREFIX}fuel_tank_liters")
        private val FUEL_CALIBRATION_JSON_KEY = stringPreferencesKey("${KEY_PREFIX}fuel_calibration_json")
        private val FUEL_CALIBRATION_ZONE_COUNT_KEY =
            intPreferencesKey("${KEY_PREFIX}fuel_calibration_zone_count")
        private val FUEL_PRICE_FUEL_ID_KEY = intPreferencesKey("${KEY_PREFIX}fuel_price_fuel_id")
        private val SPLIT_TRIP_TIME_MINUTES_KEY = intPreferencesKey("${KEY_PREFIX}split_trip_time_minutes")

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
        private const val MAIN_SCREEN_DASHBOARDS_LIST_KEY = "main_screen_dashboards"
        private const val MAIN_SCREEN_SETTINGS_BUTTON_KEY = "main_screen_settings_button"
        private const val MAIN_SCREEN_ADD_BUTTON_KEY = "main_screen_add_button"

        /** Legacy single-file copies (may be migrated to folder URIs on startup). */
        const val MAIN_SCREEN_WALLPAPER_LIGHT_FILE = "main_screen_wallpaper/light"
        const val MAIN_SCREEN_WALLPAPER_DARK_FILE = "main_screen_wallpaper/dark"
        /** One-time migration copies old per-theme files into this directory as `file://` folder URIs. */
        private const val MAIN_SCREEN_WALLPAPER_MIGRATED_DIR = "main_screen_wallpaper_migrated"
        /** Per-package custom icons for the app-launcher widget (files only; not in JSON backup). */
        const val LAUNCHER_APP_ICONS_DIR = "launcher_app_icons"
        private const val MAX_LAUNCHER_APP_ICON_EDGE_PX = 512
        private const val MAX_LAUNCHER_APP_ICON_BYTES = 512 * 1024L
        private const val DEFAULT_CAN_DATA_SAVE_COUNT = 5
        private const val DEFAULT_FUEL_TANK_LITERS = 57
        private const val DEFAULT_FUEL_CALIBRATION_ZONE_COUNT = 5
        private const val FUEL_CALIBRATION_ZONE_COUNT_MIN = 3
        private const val FUEL_CALIBRATION_ZONE_COUNT_MAX = 20
        private const val DEFAULT_SPLIT_TRIP_TIME_MINUTES = 5
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

        const val WIDGET_COLOR_PRESET_SLOT_COUNT = 6

        /** Default ARGB for each preset slot when nothing is stored yet in DataStore. */
        val DEFAULT_WIDGET_COLOR_PRESET_SLOTS: List<Int> = listOf(
            (0xFF131C2D).toInt(),
            (0xFF292F3B).toInt(),
            (0xFF1A1C1E).toInt(),
            (0xFFE2E2E6).toInt(),
            (0xFFF8F9FA).toInt(),
            Color.WHITE
        )

        // Кэш ключей для производительности
        private val stringKeysCache = mutableMapOf<String, Preferences.Key<String>>()

        // Ключ для сохранения конфигурации виджетов
        private val DASHBOARD_WIDGETS_KEY = stringPreferencesKey("${KEY_PREFIX}dashboard_widgets")

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

    val mainScreenDashboardsFlow: Flow<List<MainScreenPanelConfig>> = context.settingsDataStore.data
        .map { preferences ->
            val rawJson = preferences[getStringKey(MAIN_SCREEN_DASHBOARDS_LIST_KEY)] ?: ""
            parseMainScreenDashboardsJson(rawJson)
        }
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

    val autoPreventTboxRestartFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[AUTO_PREVENT_TBOX_RESTART_KEY] ?: false }
        .distinctUntilChanged()

    val getVoltagesFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[GET_VOLTAGES_KEY] ?: false }
        .distinctUntilChanged()

    val getCanFrameFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[GET_CAN_FRAME_KEY] ?: true }
        .distinctUntilChanged()

    val getCycleSignalFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[GET_CYCLE_SIGNAL_KEY] ?: false }
        .distinctUntilChanged()

    val getLocDataFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[GET_LOC_DATA_KEY] ?: true }
        .distinctUntilChanged()

    val expertModeFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[EXPERT_MODE] ?: false }
        .distinctUntilChanged()

    val leftMenuVisibleFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[LEFT_MENU_VISIBLE] ?: true }
        .distinctUntilChanged()

    val selectedTabFlow: Flow<Int> = context.settingsDataStore.data
        .map { preferences ->
            preferences[SELECTED_TAB_KEY]?.toIntOrNull()
                ?: MAIN_SCREEN_SELECTED_TAB_INDEX
        }
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

    /** After device boot, open [MainActivity] on the main home screen (tab 100) when enabled. */
    val mainScreenOpenOnBootFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[MAIN_SCREEN_OPEN_ON_BOOT_KEY] ?: false }
        .distinctUntilChanged()

    val mainScreenWallpaperLightFolderUriFlow: Flow<String> = context.settingsDataStore.data
        .map { preferences -> preferences[MAIN_SCREEN_WALLPAPER_LIGHT_FOLDER_URI_KEY] ?: "" }
        .distinctUntilChanged()

    val mainScreenWallpaperDarkFolderUriFlow: Flow<String> = context.settingsDataStore.data
        .map { preferences -> preferences[MAIN_SCREEN_WALLPAPER_DARK_FOLDER_URI_KEY] ?: "" }
        .distinctUntilChanged()

    val mainScreenWallpaperLightSelectedFileFlow: Flow<String> = context.settingsDataStore.data
        .map { preferences -> preferences[MAIN_SCREEN_WALLPAPER_LIGHT_SELECTED_FILE_KEY] ?: "" }
        .distinctUntilChanged()

    val mainScreenWallpaperDarkSelectedFileFlow: Flow<String> = context.settingsDataStore.data
        .map { preferences -> preferences[MAIN_SCREEN_WALLPAPER_DARK_SELECTED_FILE_KEY] ?: "" }
        .distinctUntilChanged()

    /** `true`: fill screen with Crop; `false`: Fit (whole image, possible side bars). */
    val mainScreenWallpaperCropFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences -> preferences[MAIN_SCREEN_WALLPAPER_CROP_KEY] ?: false }
        .distinctUntilChanged()

    val launcherAppIconRevisionFlow: Flow<Int> = context.settingsDataStore.data
        .map { preferences -> preferences[LAUNCHER_APP_ICON_REVISION_KEY] ?: 0 }
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

    /** Six global ARGB colors for quick pick in color editors; missing keys use [DEFAULT_WIDGET_COLOR_PRESET_SLOTS]. */
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

    val canDataSaveCountFlow: Flow<Int> = context.settingsDataStore.data
        .map { preferences -> preferences[CAN_DATA_SAVE_COUNT_KEY] ?: DEFAULT_CAN_DATA_SAVE_COUNT }
        .distinctUntilChanged()

    val fuelTankLitersFlow: Flow<Int> = context.settingsDataStore.data
        .map { preferences -> preferences[FUEL_TANK_LITERS_KEY] ?: DEFAULT_FUEL_TANK_LITERS }
        .distinctUntilChanged()

    val fuelCalibrationJsonFlow: Flow<String> = context.settingsDataStore.data
        .map { preferences -> preferences[FUEL_CALIBRATION_JSON_KEY].orEmpty() }
        .distinctUntilChanged()

    val fuelCalibrationZoneCountFlow: Flow<Int> = context.settingsDataStore.data
        .map { preferences ->
            preferences[FUEL_CALIBRATION_ZONE_COUNT_KEY] ?: DEFAULT_FUEL_CALIBRATION_ZONE_COUNT
        }
        .distinctUntilChanged()

    val fuelPriceFuelIdFlow: Flow<Int> = context.settingsDataStore.data
        .map { preferences -> preferences[FUEL_PRICE_FUEL_ID_KEY] ?: FuelTypes.DEFAULT_FUEL_ID }
        .distinctUntilChanged()

    val splitTripTimeMinutesFlow: Flow<Int> = context.settingsDataStore.data
        .map { preferences -> preferences[SPLIT_TRIP_TIME_MINUTES_KEY] ?: DEFAULT_SPLIT_TRIP_TIME_MINUTES }
        .distinctUntilChanged()

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
            autoPreventTboxRestart = preferences[AUTO_PREVENT_TBOX_RESTART_KEY] ?: false,
            getCanFrame = preferences[GET_CAN_FRAME_KEY] ?: true,
            getCycleSignal = preferences[GET_CYCLE_SIGNAL_KEY] ?: false,
            getLocData = preferences[GET_LOC_DATA_KEY] ?: true,
            widgetShowIndicator = preferences[WIDGET_SHOW_INDICATOR] ?: false,
            widgetShowLocIndicator = preferences[WIDGET_SHOW_LOC_INDICATOR] ?: false,
            mockLocation = preferences[MOCK_LOCATION] ?: false,
            floatingDashboards = parseFloatingDashboardsJson(floatingRaw),
            canDataSaveCount = preferences[CAN_DATA_SAVE_COUNT_KEY] ?: DEFAULT_CAN_DATA_SAVE_COUNT,
            fuelTankLiters = preferences[FUEL_TANK_LITERS_KEY] ?: DEFAULT_FUEL_TANK_LITERS,
            fuelCalibrationJson = preferences[FUEL_CALIBRATION_JSON_KEY].orEmpty(),
            fuelCalibrationZoneCount = preferences[FUEL_CALIBRATION_ZONE_COUNT_KEY]
                ?: DEFAULT_FUEL_CALIBRATION_ZONE_COUNT,
            fuelPriceFuelId = preferences[FUEL_PRICE_FUEL_ID_KEY] ?: FuelTypes.DEFAULT_FUEL_ID,
            splitTripTimeMinutes = preferences[SPLIT_TRIP_TIME_MINUTES_KEY]
                ?: DEFAULT_SPLIT_TRIP_TIME_MINUTES,
        )
    }

    // Suspend функции для сохранения настроек

    // Сохранение конфигурации виджетов
    suspend fun saveDashboardWidgets(config: List<FloatingDashboardWidgetConfig>) {
        context.settingsDataStore.edit { preferences ->
            preferences[DASHBOARD_WIDGETS_KEY] = serializeWidgetConfigs(config)
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

    suspend fun saveGetCycleSignalSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[GET_CYCLE_SIGNAL_KEY] = enabled
        }
    }

    suspend fun saveGetLocDataSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[GET_LOC_DATA_KEY] = enabled
        }
    }

    suspend fun saveExpertModeSetting(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[EXPERT_MODE] = enabled
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

    suspend fun saveSelectedTab(tabIndex: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[SELECTED_TAB_KEY] = tabIndex.toString()
        }
    }

    /**
     * One-time compatibility for tab insertions: shift stored indices after Trips so the same
     * screen stays selected (Main screen index 100 is unchanged).
     */
    suspend fun migrateSelectedTabIndexIfNeeded() {
        val raw = context.settingsDataStore.data.first()[SELECTED_TAB_KEY]?.toIntOrNull()
            ?: return
        if (raw == MAIN_SCREEN_SELECTED_TAB_INDEX) return
        if (raw in 5..10) {
            saveSelectedTab(raw + 1)
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
                selectedFileKey: Preferences.Key<String>,
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
                    e[selectedFileKey] = dest.name
                    e[legacyBooleanKey] = false
                }
                return true
            }
            val migrated = tryMigrate(
                MAIN_SCREEN_WALLPAPER_LIGHT_SET_LEGACY_KEY,
                File(context.filesDir, MAIN_SCREEN_WALLPAPER_LIGHT_FILE),
                "$MAIN_SCREEN_WALLPAPER_MIGRATED_DIR/light",
                MAIN_SCREEN_WALLPAPER_LIGHT_FOLDER_URI_KEY,
                MAIN_SCREEN_WALLPAPER_LIGHT_SELECTED_FILE_KEY,
            ) || tryMigrate(
                MAIN_SCREEN_WALLPAPER_DARK_SET_LEGACY_KEY,
                File(context.filesDir, MAIN_SCREEN_WALLPAPER_DARK_FILE),
                "$MAIN_SCREEN_WALLPAPER_MIGRATED_DIR/dark",
                MAIN_SCREEN_WALLPAPER_DARK_FOLDER_URI_KEY,
                MAIN_SCREEN_WALLPAPER_DARK_SELECTED_FILE_KEY,
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

    suspend fun saveMainScreenWallpaperLightFolderUri(uriString: String?) {
        context.settingsDataStore.edit { preferences ->
            if (uriString.isNullOrBlank()) {
                preferences.remove(MAIN_SCREEN_WALLPAPER_LIGHT_FOLDER_URI_KEY)
                preferences.remove(MAIN_SCREEN_WALLPAPER_LIGHT_SELECTED_FILE_KEY)
            } else {
                preferences[MAIN_SCREEN_WALLPAPER_LIGHT_FOLDER_URI_KEY] = uriString
            }
        }
    }

    suspend fun saveMainScreenWallpaperDarkFolderUri(uriString: String?) {
        context.settingsDataStore.edit { preferences ->
            if (uriString.isNullOrBlank()) {
                preferences.remove(MAIN_SCREEN_WALLPAPER_DARK_FOLDER_URI_KEY)
                preferences.remove(MAIN_SCREEN_WALLPAPER_DARK_SELECTED_FILE_KEY)
            } else {
                preferences[MAIN_SCREEN_WALLPAPER_DARK_FOLDER_URI_KEY] = uriString
            }
        }
    }

    suspend fun saveMainScreenWallpaperLightSelectedFileName(fileName: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[MAIN_SCREEN_WALLPAPER_LIGHT_SELECTED_FILE_KEY] = fileName
        }
    }

    suspend fun saveMainScreenWallpaperDarkSelectedFileName(fileName: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[MAIN_SCREEN_WALLPAPER_DARK_SELECTED_FILE_KEY] = fileName
        }
    }

    /** Single DataStore write when picking wallpaper (folder URI + selected file name). */
    suspend fun saveMainScreenWallpaperLightFolderAndSelection(folderUriString: String, selectedFileName: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[MAIN_SCREEN_WALLPAPER_LIGHT_FOLDER_URI_KEY] = folderUriString
            preferences[MAIN_SCREEN_WALLPAPER_LIGHT_SELECTED_FILE_KEY] = selectedFileName
        }
    }

    suspend fun saveMainScreenWallpaperDarkFolderAndSelection(folderUriString: String, selectedFileName: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[MAIN_SCREEN_WALLPAPER_DARK_FOLDER_URI_KEY] = folderUriString
            preferences[MAIN_SCREEN_WALLPAPER_DARK_SELECTED_FILE_KEY] = selectedFileName
        }
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

    suspend fun hasCustomLauncherAppIcon(packageName: String): Boolean =
        withContext(Dispatchers.IO) {
            if (packageName.isBlank()) return@withContext false
            val f = launcherAppIconFile(packageName)
            f.isFile && f.length() > 0L
        }

    suspend fun clearCustomLauncherAppIcon(packageName: String) {
        withContext(Dispatchers.IO) {
            if (packageName.isBlank()) return@withContext
            launcherAppIconFile(packageName).takeIf { it.exists() }?.delete()
            bumpLauncherAppIconRevision()
        }
    }

    /**
     * Removes on-disk assets that are not part of the JSON backup (same idea as main-screen wallpapers).
     * Call after a successful full settings import.
     */
    suspend fun clearNonExportedLocalAssetsAfterBackupImport() {
        withContext(Dispatchers.IO) {
            File(context.filesDir, LAUNCHER_APP_ICONS_DIR).takeIf { it.exists() }?.deleteRecursively()
            listOf(MAIN_SCREEN_WALLPAPER_LIGHT_FILE, MAIN_SCREEN_WALLPAPER_DARK_FILE).forEach { rel ->
                File(context.filesDir, rel).takeIf { it.exists() }?.delete()
            }
            File(context.filesDir, MAIN_SCREEN_WALLPAPER_MIGRATED_DIR).takeIf { it.exists() }?.deleteRecursively()
            context.settingsDataStore.edit { preferences ->
                preferences[MAIN_SCREEN_WALLPAPER_LIGHT_SET_LEGACY_KEY] = false
                preferences[MAIN_SCREEN_WALLPAPER_DARK_SET_LEGACY_KEY] = false
                val cur = preferences[LAUNCHER_APP_ICON_REVISION_KEY] ?: 0
                preferences[LAUNCHER_APP_ICON_REVISION_KEY] = cur + 1
            }
        }
    }

    private suspend fun bumpLauncherAppIconRevision() {
        context.settingsDataStore.edit { preferences ->
            val cur = preferences[LAUNCHER_APP_ICON_REVISION_KEY] ?: 0
            preferences[LAUNCHER_APP_ICON_REVISION_KEY] = cur + 1
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
                    relWidth = it.relWidth.coerceIn(0.08f, 1f),
                    relHeight = it.relHeight.coerceIn(0.08f, 1f)
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

    private fun parseMainScreenDashboardsJson(json: String): List<MainScreenPanelConfig> {
        if (json.isBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            val configs = mutableListOf<MainScreenPanelConfig>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val config = parseMainScreenPanelConfig(obj) ?: continue
                configs.add(config)
            }
            configs
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseMainScreenPanelConfig(obj: JSONObject): MainScreenPanelConfig? {
        val id = obj.optString("id").trim()
        if (id.isEmpty()) return null
        val name = obj.optString("name").ifBlank { id }
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
                .toFloat().coerceIn(0.08f, 1f),
            relHeight = obj.optDouble("relHeight", DEFAULT_MAIN_SCREEN_PANEL_REL_HEIGHT.toDouble())
                .toFloat().coerceIn(0.08f, 1f),
            background = obj.optBoolean("background", DEFAULT_MAIN_SCREEN_PANEL_BACKGROUND),
            clickAction = obj.optBoolean("clickAction", DEFAULT_MAIN_SCREEN_PANEL_CLICK_ACTION),
            showTboxDisconnectIndicator = obj.optBoolean(
                "showTboxDisconnectIndicator",
                DEFAULT_MAIN_SCREEN_PANEL_SHOW_TBOX_DISCONNECT
            )
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
            )
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
            array.put(obj)
        }
        return array.toString()
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
            sanitizeExternalAppWidgetsAfterBackupImport()
        }
        return result
    }

    private suspend fun sanitizeExternalAppWidgetsAfterBackupImport() {
        context.settingsDataStore.edit { prefs ->
            val dashKey = DASHBOARD_WIDGETS_KEY
            val oldDash = parseWidgetConfigsFromString(prefs[dashKey] ?: "")
            val newDash = clearExternalAppWidgetsAfterBackupImport(oldDash)
            if (newDash != oldDash) {
                prefs[dashKey] = serializeWidgetConfigs(newDash)
            }

            val floatKey = getStringKey(FLOATING_DASHBOARDS_LIST_KEY)
            val floatRaw = prefs[floatKey] ?: ""
            val floatList = parseFloatingDashboardsJson(floatRaw)
            val newFloat = floatList.map { panel ->
                val w = clearExternalAppWidgetsAfterBackupImport(panel.widgetsConfig)
                if (w == panel.widgetsConfig) panel else panel.copy(widgetsConfig = w)
            }
            if (newFloat != floatList) {
                prefs[floatKey] = serializeFloatingDashboards(newFloat)
            }

            val mainKey = getStringKey(MAIN_SCREEN_DASHBOARDS_LIST_KEY)
            val mainRaw = prefs[mainKey] ?: ""
            val mainList = parseMainScreenDashboardsJson(mainRaw)
            val newMain = mainList.map { panel ->
                val w = clearExternalAppWidgetsAfterBackupImport(panel.widgetsConfig)
                if (w == panel.widgetsConfig) panel else panel.copy(widgetsConfig = w)
            }
            if (newMain != mainList) {
                prefs[mainKey] = serializeMainScreenDashboards(newMain)
            }
        }
    }

}