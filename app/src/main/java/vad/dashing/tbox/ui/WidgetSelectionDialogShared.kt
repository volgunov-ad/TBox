package vad.dashing.tbox.ui

import vad.dashing.tbox.ui.theme.tboxTitle
import vad.dashing.tbox.ui.theme.tboxTabLabel
import vad.dashing.tbox.ui.theme.tboxHeadline
import vad.dashing.tbox.ui.theme.tboxCaption
import vad.dashing.tbox.ui.theme.tboxButton
import vad.dashing.tbox.ui.theme.tboxBody
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import android.appwidget.AppWidgetManager
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Context
import vad.dashing.tbox.APP_LAUNCHER_WIDGET_DATA_KEY
import vad.dashing.tbox.DEFAULT_HTTP_REQUEST_WIDGET_YAML
import vad.dashing.tbox.DEFAULT_WIDGET_TEXT_COLOR_DARK
import vad.dashing.tbox.freeform.FreeformLaunchBounds
import vad.dashing.tbox.freeform.FreeformLaunchSide
import vad.dashing.tbox.DEFAULT_WIDGET_TEXT_COLOR_LIGHT
import vad.dashing.tbox.DashboardManager
import vad.dashing.tbox.ACC_CRUISE_STEP_INTERVAL_MS_DEFAULT
import vad.dashing.tbox.ACC_CRUISE_STEP_INTERVAL_MS_MAX
import vad.dashing.tbox.ACC_CRUISE_STEP_INTERVAL_MS_MIN
import vad.dashing.tbox.ACC_CRUISE_TARGET_KMH_DEFAULT
import vad.dashing.tbox.ACC_CRUISE_TARGET_KMH_MAX
import vad.dashing.tbox.ACC_CRUISE_TARGET_KMH_MIN
import vad.dashing.tbox.CruiseControlType
import vad.dashing.tbox.isAccCruiseWidgetDataKey
import vad.dashing.tbox.isCruiseWidgetDataKey
import vad.dashing.tbox.normalizeAccCruiseStepIntervalMs
import vad.dashing.tbox.normalizeAccCruiseTargetKmh
import vad.dashing.tbox.DRIVE_MODE_WIDGET_DATA_KEY
import vad.dashing.tbox.DRIVE_MODE_WIDGET_DEFAULT_RAW_VALUE
import vad.dashing.tbox.DRIVE_MODE_WIDGET_OPTIONS
import vad.dashing.tbox.DRIVE_MODE_CYCLE_WIDGET_DATA_KEY
import vad.dashing.tbox.DRIVE_MODE_CYCLE_WIDGET_DEFAULT_RAW_VALUES
import vad.dashing.tbox.normalizeDriveModeCycleSelection
import vad.dashing.tbox.toggleDriveModeCycleSelection
import vad.dashing.tbox.isDriveModeCycleWidgetDataKey
import vad.dashing.tbox.FloatingDashboardConfig
import vad.dashing.tbox.MainScreenPanelConfig
import vad.dashing.tbox.DashboardWidget
import vad.dashing.tbox.FloatingDashboardWidgetConfig
import vad.dashing.tbox.isValidDateTimeWidgetFormat
import vad.dashing.tbox.isSeatHeatVentSingleWidgetDataKey
import vad.dashing.tbox.isActiveTripWidgetDataKey
import vad.dashing.tbox.normalizeTripWidgetSource
import vad.dashing.tbox.TRIP_WIDGET_SOURCE_CURRENT
import vad.dashing.tbox.TRIP_WIDGET_SOURCE_PERSISTENT
import vad.dashing.tbox.isMusicWidgetDataKey
import vad.dashing.tbox.normalizeDateTimeWidgetFormat
import vad.dashing.tbox.previewDateTimeWidgetFormat
import vad.dashing.tbox.R
import vad.dashing.tbox.sanitizeDateTimeWidgetFormat
import vad.dashing.tbox.SettingsManager
import vad.dashing.tbox.ExternalWidgetHostManager
import vad.dashing.tbox.HTTP_REQUEST_WIDGET_DATA_KEY
import vad.dashing.tbox.WidgetPickerActivity
import vad.dashing.tbox.FloatingWholePanelFieldsForWidgetDialogSave
import vad.dashing.tbox.MainScreenWholePanelFieldsForWidgetDialogSave
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.TileBackgroundImageStorage
import vad.dashing.tbox.WidgetsRepository
import vad.dashing.tbox.normalizeWidgetConfigs
import vad.dashing.tbox.loadWidgetsFromConfig
import vad.dashing.tbox.normalizeWidgetShape
import vad.dashing.tbox.normalizePanelShape
import vad.dashing.tbox.DEFAULT_PANEL_SHAPE
import vad.dashing.tbox.normalizeWidgetControlShape
import vad.dashing.tbox.usesDefaultControlColors
import vad.dashing.tbox.trip.TripWidgetTileDisplay
import vad.dashing.tbox.normalizeWidgetScale
import androidx.compose.ui.graphics.toArgb
import vad.dashing.tbox.normalizeDriveModeWidgetRawValue
import vad.dashing.tbox.normalizeWidgetTextAlign
import vad.dashing.tbox.normalizeWidgetFontWeight
import vad.dashing.tbox.normalizeWidgetTitlePosition
import vad.dashing.tbox.normalizeStepperAdjustIconStyle
import vad.dashing.tbox.STEPPER_ADJUST_ICON_ARROWS
import vad.dashing.tbox.STEPPER_ADJUST_ICON_PLUS_MINUS
import vad.dashing.tbox.EspRelayWidgetMode
import vad.dashing.tbox.isEspRelayWidgetDataKey
import vad.dashing.tbox.normalizePanelGridSpacingDp
import vad.dashing.tbox.DEFAULT_PANEL_COLLAPSE_ON_TILE_TAP
import vad.dashing.tbox.DEFAULT_PANEL_COLLAPSE_ON_TILE_TAP_DELAY_SEC
import vad.dashing.tbox.DEFAULT_PANEL_COLLAPSE_STRIP_COLOR_DARK
import vad.dashing.tbox.DEFAULT_PANEL_COLLAPSE_STRIP_COLOR_LIGHT
import vad.dashing.tbox.DEFAULT_PANEL_COLLAPSE_STRIP_EXPANDED_COLOR_DARK
import vad.dashing.tbox.DEFAULT_PANEL_COLLAPSE_STRIP_EXPANDED_COLOR_LIGHT
import vad.dashing.tbox.DEFAULT_PANEL_COLLAPSE_STRIP_THICKNESS_DP
import vad.dashing.tbox.MAX_PANEL_COLLAPSE_ON_TILE_TAP_DELAY_SEC
import vad.dashing.tbox.MAX_PANEL_COLLAPSE_STRIP_THICKNESS_DP
import vad.dashing.tbox.MIN_PANEL_COLLAPSE_ON_TILE_TAP_DELAY_SEC
import vad.dashing.tbox.MIN_PANEL_COLLAPSE_STRIP_THICKNESS_DP
import vad.dashing.tbox.PanelCollapseEdge
import vad.dashing.tbox.normalizePanelCollapseOnTileTapDelaySec
import vad.dashing.tbox.normalizePanelCollapseStripThicknessDp
import vad.dashing.tbox.normalizeWidgetPaddingPercent
import vad.dashing.tbox.WIDGET_TEXT_ALIGN_CENTER
import vad.dashing.tbox.WIDGET_TEXT_ALIGN_START
import vad.dashing.tbox.WIDGET_TEXT_ALIGN_END
import vad.dashing.tbox.WIDGET_FONT_WEIGHT_NORMAL
import vad.dashing.tbox.WIDGET_FONT_WEIGHT_MEDIUM
import vad.dashing.tbox.WIDGET_FONT_WEIGHT_SEMI_BOLD
import vad.dashing.tbox.WIDGET_TITLE_POSITION_TOP
import vad.dashing.tbox.WIDGET_TITLE_POSITION_BOTTOM
import vad.dashing.tbox.DEFAULT_PANEL_GRID_SPACING_DP
import vad.dashing.tbox.MAX_PANEL_GRID_SPACING_DP
import vad.dashing.tbox.MAX_WIDGET_PADDING_PERCENT
import vad.dashing.tbox.MIN_PANEL_GRID_SPACING_DP
import vad.dashing.tbox.MIN_WIDGET_PADDING_PERCENT
import vad.dashing.tbox.resolveDefaultTitlePositionForDataKey
import vad.dashing.tbox.parseHttpRequestWidgetYaml
import vad.dashing.tbox.resolveSelectedMediaPlayerForWidget

/** Width of value dropdowns in the tile / panel settings dialog. */
val WidgetDialogDropdownSelectorWidth = 300.dp

/** Label + stored value for the per-tile numeric accuracy dropdown ([SettingDropdownGeneric] uses [toString]). */
internal data class ValueAccuracyDropdownEntry(
    private val display: String,
    val stored: Int?
) {
    override fun toString(): String = display
}

internal data class WidgetTextAlignDropdownEntry(
    private val display: String,
    val stored: Int,
) {
    override fun toString(): String = display
}

internal data class WidgetFontWeightDropdownEntry(
    private val display: String,
    val stored: Int,
) {
    override fun toString(): String = display
}

internal data class WidgetTitlePositionDropdownEntry(
    private val display: String,
    val stored: Int,
) {
    override fun toString(): String = display
}

internal data class StepperAdjustIconStyleDropdownEntry(
    private val display: String,
    val stored: Int,
) {
    override fun toString(): String = display
}

internal data class TripWidgetSourceDropdownEntry(
    val source: Int,
    val display: String,
) {
    override fun toString(): String = display
}

internal data class EspRelayModeDropdownEntry(
    val mode: EspRelayWidgetMode,
    val display: String,
) {
    override fun toString(): String = display
}

internal data class CruiseControlTypeDropdownEntry(
    val type: CruiseControlType,
    val display: String,
) {
    override fun toString(): String = display
}


internal class WidgetSelectionDialogState(
    initialDataKey: String,
    initialConfig: FloatingDashboardWidgetConfig,
    private val panelDefaultBackgroundLight: Int,
    private val panelDefaultBackgroundDark: Int,
    initialColorThemeSegment: Int = 0,
) {
    var selectedDataKey by mutableStateOf(initialDataKey)
    var showTitle by mutableStateOf(initialConfig.showTitle)
    var showUnit by mutableStateOf(initialConfig.showUnit)
    var textAlign by mutableIntStateOf(normalizeWidgetTextAlign(initialConfig.textAlign))
    var fontWeight by mutableIntStateOf(normalizeWidgetFontWeight(initialConfig.fontWeight))
    var titlePosition by mutableIntStateOf(normalizeWidgetTitlePosition(initialConfig.titlePosition))
    var customTitle by mutableStateOf(initialConfig.customTitle)
    var singleLineDualMetrics by mutableStateOf(
        initialConfig.singleLineDualMetrics &&
            WidgetsRepository.supportsSingleLineDualMetrics(initialConfig.dataKey)
    )
    var scale by mutableFloatStateOf(normalizeWidgetScale(initialConfig.scale))
    var shape by mutableIntStateOf(normalizeWidgetShape(initialConfig.shape))
    var paddingTopPercent by mutableIntStateOf(
        normalizeWidgetPaddingPercent(initialConfig.paddingTopPercent)
    )
    var paddingBottomPercent by mutableIntStateOf(
        normalizeWidgetPaddingPercent(initialConfig.paddingBottomPercent)
    )
    var paddingStartPercent by mutableIntStateOf(
        normalizeWidgetPaddingPercent(initialConfig.paddingStartPercent)
    )
    var paddingEndPercent by mutableIntStateOf(
        normalizeWidgetPaddingPercent(initialConfig.paddingEndPercent)
    )
    var textColorLight by mutableIntStateOf(initialConfig.textColorLight)
    var textColorDark by mutableIntStateOf(initialConfig.textColorDark)
    var backgroundColorLight by mutableIntStateOf(
        initialConfig.backgroundColorLight ?: panelDefaultBackgroundLight
    )
    var backgroundColorDark by mutableIntStateOf(
        initialConfig.backgroundColorDark ?: panelDefaultBackgroundDark
    )
    var selectedMediaPlayers by mutableStateOf(
        if (isMusicWidgetDataKey(initialConfig.dataKey)) {
            normalizeMediaPlayersSelection(initialConfig.mediaPlayers)
        } else {
            emptySet()
        }
    )
    var selectedMediaPlayer by mutableStateOf(resolveSelectedMediaPlayerForWidget(initialConfig))
    var mediaAutoPlayOnInit by mutableStateOf(initialConfig.mediaAutoPlayOnInit)
    var mediaAutoPlayOnlyWhenEngineRunning by mutableStateOf(
        initialConfig.mediaAutoPlayOnlyWhenEngineRunning
    )

    // anymani: новое свойство для опции "Оставить плеер на переднем плане"
    var mediaKeepPlayerForeground by mutableStateOf(
        initialConfig.mediaKeepPlayerForeground
    )
    var useMbCanVhal by mutableStateOf(initialConfig.useMbCanVhal)
    /**
     * When true (no-TBox mode), newly selected keys that support [useMbCanVhal] default to on.
     * Set from the dialog form via settings; does not force-change an already chosen key.
     */
    var preferUseMbCanVhalDefault by mutableStateOf(false)
    var stepperAdjustIconStyle by mutableIntStateOf(
        normalizeStepperAdjustIconStyle(initialConfig.stepperAdjustIconStyle)
    )
    var selectedDriveMode by mutableIntStateOf(
        if (initialConfig.dataKey == DRIVE_MODE_WIDGET_DATA_KEY) {
            normalizeDriveModeWidgetRawValue(initialConfig.selectedDriveMode)
        } else {
            DRIVE_MODE_WIDGET_DEFAULT_RAW_VALUE
        }
    )
    var selectedDriveModes by mutableStateOf(
        if (isDriveModeCycleWidgetDataKey(initialConfig.dataKey)) {
            normalizeDriveModeCycleSelection(initialConfig.selectedDriveModes)
        } else {
            DRIVE_MODE_CYCLE_WIDGET_DEFAULT_RAW_VALUES
        }
    )

    var showAdvancedSettings by mutableStateOf(false)
    var showWholePanelSettings by mutableStateOf(false)
    /** 0 = light theme colors, 1 = dark theme colors (advanced settings only). */
    var advancedColorThemeSegment by mutableIntStateOf(initialColorThemeSegment)
    /** Draft for «Вся панель»; persisted only on dialog Save. */
    var wholePanelNameDraft by mutableStateOf("")
    var wholePanelShowTboxDisconnect by mutableStateOf(false)
    var wholePanelRows by mutableIntStateOf(2)
    var wholePanelCols by mutableIntStateOf(3)
    var wholePanelGridSpacingDp by mutableIntStateOf(DEFAULT_PANEL_GRID_SPACING_DP)
    var wholePanelPageNumber by mutableIntStateOf(1)
    /** Main-screen and floating whole-panel draft for clickAction. */
    var wholePanelClickAction by mutableStateOf(false)
    var wholePanelCollapseEdge by mutableStateOf(PanelCollapseEdge.NONE.storageValue)
    var wholePanelCollapseStripThicknessDp by mutableIntStateOf(DEFAULT_PANEL_COLLAPSE_STRIP_THICKNESS_DP)
    var wholePanelCollapseStripColorLight by mutableIntStateOf(DEFAULT_PANEL_COLLAPSE_STRIP_COLOR_LIGHT)
    var wholePanelCollapseStripColorDark by mutableIntStateOf(DEFAULT_PANEL_COLLAPSE_STRIP_COLOR_DARK)
    var wholePanelCollapseStripExpandedColorLight by mutableIntStateOf(
        DEFAULT_PANEL_COLLAPSE_STRIP_EXPANDED_COLOR_LIGHT,
    )
    var wholePanelCollapseStripExpandedColorDark by mutableIntStateOf(
        DEFAULT_PANEL_COLLAPSE_STRIP_EXPANDED_COLOR_DARK,
    )
    var wholePanelCollapseOnTileTap by mutableStateOf(DEFAULT_PANEL_COLLAPSE_ON_TILE_TAP)
    var wholePanelCollapseOnTileTapDelaySec by mutableIntStateOf(
        DEFAULT_PANEL_COLLAPSE_ON_TILE_TAP_DELAY_SEC,
    )
    var wholePanelCollapseColorThemeSegment by mutableIntStateOf(initialColorThemeSegment)
    var wholePanelBackgroundColorLight by mutableStateOf<Int?>(null)
    var wholePanelBackgroundColorDark by mutableStateOf<Int?>(null)
    var wholePanelBackgroundImageRelPathLight by mutableStateOf<String?>(null)
    var wholePanelBackgroundImageRelPathDark by mutableStateOf<String?>(null)
    var wholePanelShape by mutableIntStateOf(DEFAULT_PANEL_SHAPE)
    var wholePanelBackgroundColorThemeSegment by mutableIntStateOf(initialColorThemeSegment)
    /**
     * True after draft was loaded from persisted config when user opened «Вся панель» in this dialog.
     * Not cleared when switching back to Advanced / tile list — Save must still persist whole-panel edits.
     * Reset only when this dialog state is recreated (new dialog session).
     */
    var wholePanelDraftSeeded by mutableStateOf(false)
    /**
     * When set (after whole-panel paste), Save replaces the panel's full tile list with this draft
     * (normalized to [wholePanelRows]×[wholePanelCols]), instead of only updating the edited tile.
     */
    var wholePanelWidgetsDraft by mutableStateOf<List<FloatingDashboardWidgetConfig>?>(null)

    fun syncWholePanelDraftFromMainScreen(cfg: MainScreenPanelConfig) {
        wholePanelNameDraft = cfg.name
        wholePanelShowTboxDisconnect = cfg.showTboxDisconnectIndicator
        wholePanelRows = cfg.rows
        wholePanelCols = cfg.cols
        wholePanelGridSpacingDp = cfg.gridSpacingDp
        wholePanelClickAction = cfg.clickAction
        wholePanelPageNumber = cfg.pageNumber
        wholePanelCollapseEdge = cfg.collapseEdge
        wholePanelCollapseStripThicknessDp = cfg.collapseStripThicknessDp
        wholePanelCollapseStripColorLight = cfg.collapseStripColorLight
        wholePanelCollapseStripColorDark = cfg.collapseStripColorDark
        wholePanelCollapseStripExpandedColorLight = cfg.collapseStripExpandedColorLight
        wholePanelCollapseStripExpandedColorDark = cfg.collapseStripExpandedColorDark
        wholePanelCollapseOnTileTap = cfg.collapseOnTileTap
        wholePanelCollapseOnTileTapDelaySec = cfg.collapseOnTileTapDelaySec
        wholePanelBackgroundColorLight = cfg.panelBackgroundColorLight
        wholePanelBackgroundColorDark = cfg.panelBackgroundColorDark
        wholePanelBackgroundImageRelPathLight = cfg.panelBackgroundImageRelPathLight
        wholePanelBackgroundImageRelPathDark = cfg.panelBackgroundImageRelPathDark
        wholePanelShape = normalizePanelShape(cfg.panelShape)
    }

    fun syncWholePanelDraftFromFloating(cfg: FloatingDashboardConfig) {
        wholePanelNameDraft = cfg.name
        wholePanelShowTboxDisconnect = cfg.showTboxDisconnectIndicator
        wholePanelRows = cfg.rows
        wholePanelCols = cfg.cols
        wholePanelGridSpacingDp = cfg.gridSpacingDp
        wholePanelClickAction = cfg.clickAction
        wholePanelCollapseEdge = cfg.collapseEdge
        wholePanelCollapseStripThicknessDp = cfg.collapseStripThicknessDp
        wholePanelCollapseStripColorLight = cfg.collapseStripColorLight
        wholePanelCollapseStripColorDark = cfg.collapseStripColorDark
        wholePanelCollapseStripExpandedColorLight = cfg.collapseStripExpandedColorLight
        wholePanelCollapseStripExpandedColorDark = cfg.collapseStripExpandedColorDark
        wholePanelCollapseOnTileTap = cfg.collapseOnTileTap
        wholePanelCollapseOnTileTapDelaySec = cfg.collapseOnTileTapDelaySec
        wholePanelBackgroundColorLight = cfg.panelBackgroundColorLight
        wholePanelBackgroundColorDark = cfg.panelBackgroundColorDark
        wholePanelBackgroundImageRelPathLight = cfg.panelBackgroundImageRelPathLight
        wholePanelBackgroundImageRelPathDark = cfg.panelBackgroundImageRelPathDark
        wholePanelShape = normalizePanelShape(cfg.panelShape)
    }

    /** Same defaults as a fresh [FloatingDashboardWidgetConfig] for this panel (main / floating). */
    fun resetTileTextAndBackgroundColors() {
        textColorLight = DEFAULT_WIDGET_TEXT_COLOR_LIGHT
        textColorDark = DEFAULT_WIDGET_TEXT_COLOR_DARK
        backgroundColorLight = panelDefaultBackgroundLight
        backgroundColorDark = panelDefaultBackgroundDark
    }
    var launcherAppPackage by mutableStateOf(
        if (initialConfig.dataKey == APP_LAUNCHER_WIDGET_DATA_KEY) {
            initialConfig.launcherAppPackage
        } else {
            ""
        }
    )
    var launcherFreeformEnabled by mutableStateOf(
        initialConfig.dataKey == APP_LAUNCHER_WIDGET_DATA_KEY && initialConfig.launcherFreeformEnabled
    )
    var launcherFreeformSide by mutableStateOf(
        if (initialConfig.dataKey == APP_LAUNCHER_WIDGET_DATA_KEY) {
            initialConfig.launcherFreeformSide
        } else {
            FreeformLaunchSide.DEFAULT
        }
    )
    var launcherFreeformPercent by mutableIntStateOf(
        if (initialConfig.dataKey == APP_LAUNCHER_WIDGET_DATA_KEY) {
            FreeformLaunchBounds.normalizePercent(initialConfig.launcherFreeformPercent)
        } else {
            FreeformLaunchBounds.DEFAULT_PERCENT
        }
    )
    var httpRequestYaml by mutableStateOf(
        if (initialConfig.dataKey == HTTP_REQUEST_WIDGET_DATA_KEY) {
            initialConfig.httpRequestYaml.ifBlank { DEFAULT_HTTP_REQUEST_WIDGET_YAML }
        } else {
            DEFAULT_HTTP_REQUEST_WIDGET_YAML
        }
    )
    var httpOpenBrowser by mutableStateOf(
        if (initialConfig.dataKey == HTTP_REQUEST_WIDGET_DATA_KEY) {
            initialConfig.httpOpenBrowser
        } else {
            false
        }
    )

    var tileBackgroundImageRelPathLight by mutableStateOf(
        initialConfig.tileBackgroundImageRelPathLight?.takeIf {
            TileBackgroundImageStorage.isAllowedStoredRelPath(it)
        }
    )
    var tileBackgroundImageRelPathDark by mutableStateOf(
        initialConfig.tileBackgroundImageRelPathDark?.takeIf {
            TileBackgroundImageStorage.isAllowedStoredRelPath(it)
        }
    )

    /** `null` = default decimals per data key in provider; otherwise 0..2 fractional digits. */
    var valueAccuracy by mutableStateOf(initialConfig.valueAccuracy?.takeIf { it in 0..2 })
    var dateTimeFormat by mutableStateOf(
        normalizeDateTimeWidgetFormat(initialConfig.dataKey, initialConfig.dateTimeFormat)
    )

    var tripWidgetShowRowDividers by mutableStateOf(initialConfig.tripWidgetShowRowDividers)
    var tripWidgetLabelColumnWidthPercent by mutableIntStateOf(
        TripWidgetTileDisplay.normalizeLabelColumnWidthPercent(
            initialConfig.tripWidgetLabelColumnWidthPercent,
        ),
    )
    var tripWidgetSource by mutableIntStateOf(
        normalizeTripWidgetSource(initialConfig.tripWidgetSource),
    )
    var espRelayMode by mutableStateOf(
        if (isEspRelayWidgetDataKey(initialConfig.dataKey)) {
            initialConfig.espRelayMode
        } else {
            EspRelayWidgetMode.DEFAULT
        },
    )
    var cruiseControlType by mutableStateOf(
        if (isCruiseWidgetDataKey(initialConfig.dataKey)) {
            initialConfig.cruiseControlType
        } else {
            CruiseControlType.DEFAULT
        },
    )
    var accCruiseTargetKmh by mutableIntStateOf(
        if (isAccCruiseWidgetDataKey(initialConfig.dataKey)) {
            normalizeAccCruiseTargetKmh(initialConfig.accCruiseTargetKmh)
        } else {
            ACC_CRUISE_TARGET_KMH_DEFAULT
        },
    )
    var accCruiseIncreaseIntervalMs by mutableIntStateOf(
        if (isAccCruiseWidgetDataKey(initialConfig.dataKey)) {
            normalizeAccCruiseStepIntervalMs(initialConfig.accCruiseIncreaseIntervalMs)
        } else {
            ACC_CRUISE_STEP_INTERVAL_MS_DEFAULT
        },
    )
    var accCruiseDecreaseIntervalMs by mutableIntStateOf(
        if (isAccCruiseWidgetDataKey(initialConfig.dataKey)) {
            normalizeAccCruiseStepIntervalMs(initialConfig.accCruiseDecreaseIntervalMs)
        } else {
            ACC_CRUISE_STEP_INTERVAL_MS_DEFAULT
        },
    )

    /** 0 = inactive control colors, 1 = active (paired with [advancedColorThemeSegment]). */
    var controlStateSegment by mutableIntStateOf(0)
    var controlColorsUseDefaults by mutableStateOf(initialConfig.usesDefaultControlColors())
    var controlInactiveColorLight by mutableIntStateOf(
        initialConfig.controlInactiveColorLight ?: DEFAULT_WIDGET_TEXT_COLOR_LIGHT
    )
    var controlInactiveColorDark by mutableIntStateOf(
        initialConfig.controlInactiveColorDark ?: DEFAULT_WIDGET_TEXT_COLOR_DARK
    )
    var controlActiveColorLight by mutableIntStateOf(
        initialConfig.controlActiveColorLight ?: 0xFF2180F3.toInt()
    )
    var controlActiveColorDark by mutableIntStateOf(
        initialConfig.controlActiveColorDark ?: 0xFF2180F3.toInt()
    )
    var controlInactiveBackgroundColorLight by mutableIntStateOf(
        initialConfig.controlInactiveBackgroundColorLight ?: 0x00000000
    )
    var controlInactiveBackgroundColorDark by mutableIntStateOf(
        initialConfig.controlInactiveBackgroundColorDark ?: 0x00000000
    )
    var controlActiveBackgroundColorLight by mutableIntStateOf(
        initialConfig.controlActiveBackgroundColorLight ?: 0x00000000
    )
    var controlActiveBackgroundColorDark by mutableIntStateOf(
        initialConfig.controlActiveBackgroundColorDark ?: 0x00000000
    )
    /** `null` = class default shape; otherwise explicit 0..50. */
    var controlShape by mutableStateOf(initialConfig.controlShape)

    /** Draft system app-widget id for [WidgetsRepository.EXTERNAL_WIDGET_DATA_KEY]. */
    var draftAppWidgetId by mutableStateOf(
        if (initialConfig.dataKey == WidgetsRepository.EXTERNAL_WIDGET_DATA_KEY) {
            initialConfig.appWidgetId
        } else {
            null
        }
    )
    /** Draft UI variant for seat heat/vent single tiles. */
    var draftSelectedVariant by mutableIntStateOf(
        if (isSeatHeatVentSingleWidgetDataKey(initialConfig.dataKey)) {
            initialConfig.selectedVariant.coerceIn(0, 1)
        } else {
            0
        }
    )

    fun clearControlColorsToDefaults() {
        controlColorsUseDefaults = true
        controlInactiveColorLight = DEFAULT_WIDGET_TEXT_COLOR_LIGHT
        controlInactiveColorDark = DEFAULT_WIDGET_TEXT_COLOR_DARK
        controlActiveColorLight = 0xFF2180F3.toInt()
        controlActiveColorDark = 0xFF2180F3.toInt()
        controlInactiveBackgroundColorLight = 0x00000000
        controlInactiveBackgroundColorDark = 0x00000000
        controlActiveBackgroundColorLight = 0x00000000
        controlActiveBackgroundColorDark = 0x00000000
    }

    fun applyControlColorSeed(seed: ControlColorSeed) {
        controlColorsUseDefaults = false
        controlInactiveColorLight = seed.inactiveColorLight
        controlInactiveColorDark = seed.inactiveColorDark
        controlActiveColorLight = seed.activeColorLight
        controlActiveColorDark = seed.activeColorDark
        controlInactiveBackgroundColorLight = seed.inactiveBackgroundLight
        controlInactiveBackgroundColorDark = seed.inactiveBackgroundDark
        controlActiveBackgroundColorLight = seed.activeBackgroundLight
        controlActiveBackgroundColorDark = seed.activeBackgroundDark
    }

    fun controlContentColorForEditor(): Int {
        val light = advancedColorThemeSegment == 0
        val inactive = controlStateSegment == 0
        return when {
            light && inactive -> controlInactiveColorLight
            light && !inactive -> controlActiveColorLight
            !light && inactive -> controlInactiveColorDark
            else -> controlActiveColorDark
        }
    }

    fun setControlContentColorForEditor(color: Int) {
        controlColorsUseDefaults = false
        val light = advancedColorThemeSegment == 0
        val inactive = controlStateSegment == 0
        when {
            light && inactive -> controlInactiveColorLight = color
            light && !inactive -> controlActiveColorLight = color
            !light && inactive -> controlInactiveColorDark = color
            else -> controlActiveColorDark = color
        }
    }

    fun controlBackgroundColorForEditor(): Int {
        val light = advancedColorThemeSegment == 0
        val inactive = controlStateSegment == 0
        return when {
            light && inactive -> controlInactiveBackgroundColorLight
            light && !inactive -> controlActiveBackgroundColorLight
            !light && inactive -> controlInactiveBackgroundColorDark
            else -> controlActiveBackgroundColorDark
        }
    }

    fun setControlBackgroundColorForEditor(color: Int) {
        controlColorsUseDefaults = false
        val light = advancedColorThemeSegment == 0
        val inactive = controlStateSegment == 0
        when {
            light && inactive -> controlInactiveBackgroundColorLight = color
            light && !inactive -> controlActiveBackgroundColorLight = color
            !light && inactive -> controlInactiveBackgroundColorDark = color
            else -> controlActiveBackgroundColorDark = color
        }
    }

    fun applySelectedDataKey(key: String) {
        selectedDataKey = key
        if (!WidgetsRepository.supportsSingleLineDualMetrics(key)) {
            singleLineDualMetrics = false
        }
        if (!WidgetsRepository.supportsUseMbCanVhal(key)) {
            useMbCanVhal = false
        } else if (preferUseMbCanVhalDefault) {
            useMbCanVhal = true
        }
        if (!WidgetsRepository.supportsStepperAdjustIconStyle(key)) {
            stepperAdjustIconStyle = STEPPER_ADJUST_ICON_PLUS_MINUS
        }
        if (!WidgetsRepository.supportsEspRelayMode(key)) {
            espRelayMode = EspRelayWidgetMode.DEFAULT
        }
        if (!isCruiseWidgetDataKey(key)) {
            cruiseControlType = CruiseControlType.DEFAULT
        }
        if (!isAccCruiseWidgetDataKey(key)) {
            accCruiseTargetKmh = ACC_CRUISE_TARGET_KMH_DEFAULT
            accCruiseIncreaseIntervalMs = ACC_CRUISE_STEP_INTERVAL_MS_DEFAULT
            accCruiseDecreaseIntervalMs = ACC_CRUISE_STEP_INTERVAL_MS_DEFAULT
        }
        if (!WidgetsRepository.supportsDateTimeFormat(key)) {
            dateTimeFormat = ""
        }
        if (key != DRIVE_MODE_WIDGET_DATA_KEY) {
            selectedDriveMode = DRIVE_MODE_WIDGET_DEFAULT_RAW_VALUE
        }
        if (!isDriveModeCycleWidgetDataKey(key)) {
            selectedDriveModes = DRIVE_MODE_CYCLE_WIDGET_DEFAULT_RAW_VALUES
        }
        titlePosition = resolveDefaultTitlePositionForDataKey(key)
    }

    val isMusicWidgetSelected: Boolean
        get() = isMusicWidgetDataKey(selectedDataKey)

    val isDriveModeCycleWidgetSelected: Boolean
        get() = isDriveModeCycleWidgetDataKey(selectedDataKey)

    val isAppLauncherWidgetSelected: Boolean
        get() = selectedDataKey == APP_LAUNCHER_WIDGET_DATA_KEY

    val isHttpRequestWidgetSelected: Boolean
        get() = selectedDataKey == HTTP_REQUEST_WIDGET_DATA_KEY

    val isExternalAppWidgetSelected: Boolean
        get() = selectedDataKey == WidgetsRepository.EXTERNAL_WIDGET_DATA_KEY

    val togglesEnabled: Boolean
        get() = selectedDataKey.isNotEmpty()

    fun toDraftWidgetConfig(): FloatingDashboardWidgetConfig {
        val normalizedScale = normalizeWidgetScale(scale)
        val normalizedShape = normalizeWidgetShape(shape)
        scale = normalizedScale
        shape = normalizedShape
        val storedValueAccuracy = if (WidgetsRepository.supportsValueAccuracy(selectedDataKey)) {
            valueAccuracy?.takeIf { it in 0..2 }
        } else {
            null
        }
        return FloatingDashboardWidgetConfig(
            dataKey = selectedDataKey,
            showTitle = showTitle,
            showUnit = showUnit,
            customTitle = customTitle.trim(),
            singleLineDualMetrics = if (WidgetsRepository.supportsSingleLineDualMetrics(selectedDataKey)) {
                singleLineDualMetrics
            } else {
                false
            },
            scale = normalizedScale,
            shape = normalizedShape,
            textColorLight = textColorLight,
            textColorDark = textColorDark,
            backgroundColorLight = backgroundColorLight,
            backgroundColorDark = backgroundColorDark,
            mediaPlayers = if (isMusicWidgetDataKey(selectedDataKey)) {
                orderedMediaPlayersForStorage(selectedMediaPlayers)
            } else {
                emptyList()
            },
            mediaSelectedPlayer = if (isMusicWidgetDataKey(selectedDataKey)) {
                resolveStoredMediaSelectedPlayer(
                    selectedPlayers = selectedMediaPlayers,
                    currentSelectedPlayer = selectedMediaPlayer
                )
            } else {
                ""
            },
            mediaAutoPlayOnInit = if (isMusicWidgetDataKey(selectedDataKey)) {
                mediaAutoPlayOnInit
            } else {
                false
            },
            mediaAutoPlayOnlyWhenEngineRunning = if (isMusicWidgetDataKey(selectedDataKey)) {
                mediaAutoPlayOnlyWhenEngineRunning && mediaAutoPlayOnInit
            } else {
                false
            },
            mediaKeepPlayerForeground = if (isMusicWidgetDataKey(selectedDataKey)) {
                mediaKeepPlayerForeground
            } else {
                false
            },
            launcherAppPackage = if (selectedDataKey == APP_LAUNCHER_WIDGET_DATA_KEY) {
                launcherAppPackage.trim()
            } else {
                ""
            },
            launcherFreeformEnabled = selectedDataKey == APP_LAUNCHER_WIDGET_DATA_KEY &&
                launcherFreeformEnabled,
            launcherFreeformSide = if (selectedDataKey == APP_LAUNCHER_WIDGET_DATA_KEY) {
                launcherFreeformSide
            } else {
                FreeformLaunchSide.DEFAULT
            },
            launcherFreeformPercent = if (selectedDataKey == APP_LAUNCHER_WIDGET_DATA_KEY) {
                FreeformLaunchBounds.normalizePercent(launcherFreeformPercent)
            } else {
                FreeformLaunchBounds.DEFAULT_PERCENT
            },
            httpRequestYaml = if (selectedDataKey == HTTP_REQUEST_WIDGET_DATA_KEY) {
                httpRequestYaml.ifBlank { DEFAULT_HTTP_REQUEST_WIDGET_YAML }
            } else {
                DEFAULT_HTTP_REQUEST_WIDGET_YAML
            },
            httpOpenBrowser = if (selectedDataKey == HTTP_REQUEST_WIDGET_DATA_KEY) {
                httpOpenBrowser
            } else {
                false
            },
            appWidgetId = if (selectedDataKey == WidgetsRepository.EXTERNAL_WIDGET_DATA_KEY) {
                draftAppWidgetId
            } else {
                null
            },
            valueAccuracy = storedValueAccuracy,
            dateTimeFormat = if (WidgetsRepository.supportsDateTimeFormat(selectedDataKey)) {
                sanitizeDateTimeWidgetFormat(selectedDataKey, dateTimeFormat)
            } else {
                ""
            },
            selectedVariant = if (isSeatHeatVentSingleWidgetDataKey(selectedDataKey)) {
                draftSelectedVariant.coerceIn(0, 1)
            } else {
                0
            },
            selectedDriveMode = if (selectedDataKey == DRIVE_MODE_WIDGET_DATA_KEY) {
                normalizeDriveModeWidgetRawValue(selectedDriveMode)
            } else {
                DRIVE_MODE_WIDGET_DEFAULT_RAW_VALUE
            },
            selectedDriveModes = if (isDriveModeCycleWidgetDataKey(selectedDataKey)) {
                normalizeDriveModeCycleSelection(selectedDriveModes)
            } else {
                emptyList()
            },
            useMbCanVhal = WidgetsRepository.supportsUseMbCanVhal(selectedDataKey) && useMbCanVhal,
            stepperAdjustIconStyle = if (WidgetsRepository.supportsStepperAdjustIconStyle(selectedDataKey)) {
                normalizeStepperAdjustIconStyle(stepperAdjustIconStyle)
            } else {
                STEPPER_ADJUST_ICON_PLUS_MINUS
            },
            tileBackgroundImageRelPathLight = tileBackgroundImageRelPathLight?.takeIf {
                TileBackgroundImageStorage.isAllowedStoredRelPath(it)
            },
            tileBackgroundImageRelPathDark = tileBackgroundImageRelPathDark?.takeIf {
                TileBackgroundImageStorage.isAllowedStoredRelPath(it)
            },
            tripWidgetShowRowDividers = if (isActiveTripWidgetDataKey(selectedDataKey)) {
                tripWidgetShowRowDividers
            } else {
                TripWidgetTileDisplay.DEFAULT_SHOW_ROW_DIVIDERS
            },
            tripWidgetLabelColumnWidthPercent = if (isActiveTripWidgetDataKey(selectedDataKey)) {
                TripWidgetTileDisplay.normalizeLabelColumnWidthPercent(
                    tripWidgetLabelColumnWidthPercent,
                )
            } else {
                TripWidgetTileDisplay.DEFAULT_LABEL_COLUMN_WIDTH_PERCENT
            },
            tripWidgetSource = if (isActiveTripWidgetDataKey(selectedDataKey)) {
                normalizeTripWidgetSource(tripWidgetSource)
            } else {
                TRIP_WIDGET_SOURCE_CURRENT
            },
            espRelayMode = if (WidgetsRepository.supportsEspRelayMode(selectedDataKey)) {
                espRelayMode
            } else {
                EspRelayWidgetMode.DEFAULT
            },
            cruiseControlType = if (isCruiseWidgetDataKey(selectedDataKey)) {
                cruiseControlType
            } else {
                CruiseControlType.DEFAULT
            },
            accCruiseTargetKmh = if (isAccCruiseWidgetDataKey(selectedDataKey)) {
                normalizeAccCruiseTargetKmh(accCruiseTargetKmh)
            } else {
                ACC_CRUISE_TARGET_KMH_DEFAULT
            },
            accCruiseIncreaseIntervalMs = if (isAccCruiseWidgetDataKey(selectedDataKey)) {
                normalizeAccCruiseStepIntervalMs(accCruiseIncreaseIntervalMs)
            } else {
                ACC_CRUISE_STEP_INTERVAL_MS_DEFAULT
            },
            accCruiseDecreaseIntervalMs = if (isAccCruiseWidgetDataKey(selectedDataKey)) {
                normalizeAccCruiseStepIntervalMs(accCruiseDecreaseIntervalMs)
            } else {
                ACC_CRUISE_STEP_INTERVAL_MS_DEFAULT
            },
            textAlign = normalizeWidgetTextAlign(textAlign),
            fontWeight = normalizeWidgetFontWeight(fontWeight),
            titlePosition = normalizeWidgetTitlePosition(titlePosition),
            paddingTopPercent = normalizeWidgetPaddingPercent(paddingTopPercent),
            paddingBottomPercent = normalizeWidgetPaddingPercent(paddingBottomPercent),
            paddingStartPercent = normalizeWidgetPaddingPercent(paddingStartPercent),
            paddingEndPercent = normalizeWidgetPaddingPercent(paddingEndPercent),
            controlInactiveColorLight = if (controlColorsUseDefaults) null else controlInactiveColorLight,
            controlInactiveColorDark = if (controlColorsUseDefaults) null else controlInactiveColorDark,
            controlActiveColorLight = if (controlColorsUseDefaults) null else controlActiveColorLight,
            controlActiveColorDark = if (controlColorsUseDefaults) null else controlActiveColorDark,
            controlInactiveBackgroundColorLight = if (controlColorsUseDefaults) {
                null
            } else {
                controlInactiveBackgroundColorLight
            },
            controlInactiveBackgroundColorDark = if (controlColorsUseDefaults) {
                null
            } else {
                controlInactiveBackgroundColorDark
            },
            controlActiveBackgroundColorLight = if (controlColorsUseDefaults) {
                null
            } else {
                controlActiveBackgroundColorLight
            },
            controlActiveBackgroundColorDark = if (controlColorsUseDefaults) {
                null
            } else {
                controlActiveBackgroundColorDark
            },
            controlShape = controlShape?.let { normalizeWidgetControlShape(it) },
        )
    }

    /** Clipboard snapshot with raw control color ints + explicit defaults flag. */
    fun toTileClipboardSnapshot(): TileClipboardSnapshot {
        val base = toDraftWidgetConfig()
        return TileClipboardSnapshot(
            config = base.copy(
                controlInactiveColorLight = controlInactiveColorLight,
                controlInactiveColorDark = controlInactiveColorDark,
                controlActiveColorLight = controlActiveColorLight,
                controlActiveColorDark = controlActiveColorDark,
                controlInactiveBackgroundColorLight = controlInactiveBackgroundColorLight,
                controlInactiveBackgroundColorDark = controlInactiveBackgroundColorDark,
                controlActiveBackgroundColorLight = controlActiveBackgroundColorLight,
                controlActiveBackgroundColorDark = controlActiveBackgroundColorDark,
                controlShape = controlShape?.let { normalizeWidgetControlShape(it) },
            ),
            controlColorsUseDefaults = controlColorsUseDefaults,
        )
    }

    fun applyDraftWidgetConfig(cfg: FloatingDashboardWidgetConfig, preserveDataKey: Boolean) {
        applyTileClipboardSnapshot(
            TileClipboardSnapshot(
                config = cfg,
                controlColorsUseDefaults = cfg.usesDefaultControlColors(),
            ),
            preserveDataKey = preserveDataKey,
        )
    }

    fun applyTileClipboardSnapshot(snapshot: TileClipboardSnapshot, preserveDataKey: Boolean) {
        val cfg = snapshot.config
        if (preserveDataKey && selectedDataKey.isEmpty()) return
        if (!preserveDataKey) {
            applySelectedDataKey(cfg.dataKey)
        }
        showTitle = cfg.showTitle
        showUnit = cfg.showUnit
        textAlign = normalizeWidgetTextAlign(cfg.textAlign)
        fontWeight = normalizeWidgetFontWeight(cfg.fontWeight)
        titlePosition = normalizeWidgetTitlePosition(cfg.titlePosition)
        customTitle = cfg.customTitle
        singleLineDualMetrics = cfg.singleLineDualMetrics &&
            WidgetsRepository.supportsSingleLineDualMetrics(selectedDataKey)
        scale = normalizeWidgetScale(cfg.scale)
        shape = normalizeWidgetShape(cfg.shape)
        paddingTopPercent = normalizeWidgetPaddingPercent(cfg.paddingTopPercent)
        paddingBottomPercent = normalizeWidgetPaddingPercent(cfg.paddingBottomPercent)
        paddingStartPercent = normalizeWidgetPaddingPercent(cfg.paddingStartPercent)
        paddingEndPercent = normalizeWidgetPaddingPercent(cfg.paddingEndPercent)
        textColorLight = cfg.textColorLight
        textColorDark = cfg.textColorDark
        backgroundColorLight = cfg.backgroundColorLight ?: panelDefaultBackgroundLight
        backgroundColorDark = cfg.backgroundColorDark ?: panelDefaultBackgroundDark
        selectedMediaPlayers = if (isMusicWidgetDataKey(selectedDataKey)) {
            normalizeMediaPlayersSelection(cfg.mediaPlayers)
        } else {
            emptySet()
        }
        selectedMediaPlayer = if (isMusicWidgetDataKey(selectedDataKey)) {
            resolveSelectedMediaPlayerForWidget(cfg.copy(dataKey = selectedDataKey))
        } else {
            ""
        }
        mediaAutoPlayOnInit = if (isMusicWidgetDataKey(selectedDataKey)) {
            cfg.mediaAutoPlayOnInit
        } else {
            false
        }
        mediaAutoPlayOnlyWhenEngineRunning = if (isMusicWidgetDataKey(selectedDataKey)) {
            cfg.mediaAutoPlayOnlyWhenEngineRunning
        } else {
            false
        }
        mediaKeepPlayerForeground = if (isMusicWidgetDataKey(selectedDataKey)) {
            cfg.mediaKeepPlayerForeground
        } else {
            false
        }
        useMbCanVhal = WidgetsRepository.supportsUseMbCanVhal(selectedDataKey) &&
            (cfg.useMbCanVhal || preferUseMbCanVhalDefault)
        stepperAdjustIconStyle = if (WidgetsRepository.supportsStepperAdjustIconStyle(selectedDataKey)) {
            normalizeStepperAdjustIconStyle(cfg.stepperAdjustIconStyle)
        } else {
            STEPPER_ADJUST_ICON_PLUS_MINUS
        }
        selectedDriveMode = if (selectedDataKey == DRIVE_MODE_WIDGET_DATA_KEY) {
            normalizeDriveModeWidgetRawValue(cfg.selectedDriveMode)
        } else {
            DRIVE_MODE_WIDGET_DEFAULT_RAW_VALUE
        }
        selectedDriveModes = if (isDriveModeCycleWidgetDataKey(selectedDataKey)) {
            normalizeDriveModeCycleSelection(cfg.selectedDriveModes)
        } else {
            DRIVE_MODE_CYCLE_WIDGET_DEFAULT_RAW_VALUES
        }
        launcherAppPackage = if (selectedDataKey == APP_LAUNCHER_WIDGET_DATA_KEY) {
            cfg.launcherAppPackage
        } else {
            ""
        }
        launcherFreeformEnabled =
            selectedDataKey == APP_LAUNCHER_WIDGET_DATA_KEY && cfg.launcherFreeformEnabled
        launcherFreeformSide = if (selectedDataKey == APP_LAUNCHER_WIDGET_DATA_KEY) {
            cfg.launcherFreeformSide
        } else {
            FreeformLaunchSide.DEFAULT
        }
        launcherFreeformPercent = if (selectedDataKey == APP_LAUNCHER_WIDGET_DATA_KEY) {
            FreeformLaunchBounds.normalizePercent(cfg.launcherFreeformPercent)
        } else {
            FreeformLaunchBounds.DEFAULT_PERCENT
        }
        httpRequestYaml = if (selectedDataKey == HTTP_REQUEST_WIDGET_DATA_KEY) {
            cfg.httpRequestYaml.ifBlank { DEFAULT_HTTP_REQUEST_WIDGET_YAML }
        } else {
            DEFAULT_HTTP_REQUEST_WIDGET_YAML
        }
        httpOpenBrowser = if (selectedDataKey == HTTP_REQUEST_WIDGET_DATA_KEY) {
            cfg.httpOpenBrowser
        } else {
            false
        }
        tileBackgroundImageRelPathLight = cfg.tileBackgroundImageRelPathLight?.takeIf {
            TileBackgroundImageStorage.isAllowedStoredRelPath(it)
        }
        tileBackgroundImageRelPathDark = cfg.tileBackgroundImageRelPathDark?.takeIf {
            TileBackgroundImageStorage.isAllowedStoredRelPath(it)
        }
        valueAccuracy = if (WidgetsRepository.supportsValueAccuracy(selectedDataKey)) {
            cfg.valueAccuracy?.takeIf { it in 0..2 }
        } else {
            null
        }
        dateTimeFormat = normalizeDateTimeWidgetFormat(selectedDataKey, cfg.dateTimeFormat)
        tripWidgetShowRowDividers = cfg.tripWidgetShowRowDividers
        tripWidgetLabelColumnWidthPercent =
            TripWidgetTileDisplay.normalizeLabelColumnWidthPercent(
                cfg.tripWidgetLabelColumnWidthPercent,
            )
        tripWidgetSource = normalizeTripWidgetSource(cfg.tripWidgetSource)
        espRelayMode = if (WidgetsRepository.supportsEspRelayMode(selectedDataKey)) {
            cfg.espRelayMode
        } else {
            EspRelayWidgetMode.DEFAULT
        }
        if (isCruiseWidgetDataKey(selectedDataKey)) {
            cruiseControlType = cfg.cruiseControlType
        } else {
            cruiseControlType = CruiseControlType.DEFAULT
        }
        if (isAccCruiseWidgetDataKey(selectedDataKey)) {
            accCruiseTargetKmh = normalizeAccCruiseTargetKmh(cfg.accCruiseTargetKmh)
            accCruiseIncreaseIntervalMs =
                normalizeAccCruiseStepIntervalMs(cfg.accCruiseIncreaseIntervalMs)
            accCruiseDecreaseIntervalMs =
                normalizeAccCruiseStepIntervalMs(cfg.accCruiseDecreaseIntervalMs)
        } else {
            accCruiseTargetKmh = ACC_CRUISE_TARGET_KMH_DEFAULT
            accCruiseIncreaseIntervalMs = ACC_CRUISE_STEP_INTERVAL_MS_DEFAULT
            accCruiseDecreaseIntervalMs = ACC_CRUISE_STEP_INTERVAL_MS_DEFAULT
        }
        draftAppWidgetId = if (selectedDataKey == WidgetsRepository.EXTERNAL_WIDGET_DATA_KEY) {
            cfg.appWidgetId
        } else {
            null
        }
        draftSelectedVariant = if (isSeatHeatVentSingleWidgetDataKey(selectedDataKey)) {
            cfg.selectedVariant.coerceIn(0, 1)
        } else {
            0
        }
        if (snapshot.controlColorsUseDefaults) {
            clearControlColorsToDefaults()
        } else {
            controlColorsUseDefaults = false
            controlInactiveColorLight =
                cfg.controlInactiveColorLight ?: DEFAULT_WIDGET_TEXT_COLOR_LIGHT
            controlInactiveColorDark =
                cfg.controlInactiveColorDark ?: DEFAULT_WIDGET_TEXT_COLOR_DARK
            controlActiveColorLight = cfg.controlActiveColorLight ?: 0xFF2180F3.toInt()
            controlActiveColorDark = cfg.controlActiveColorDark ?: 0xFF2180F3.toInt()
            controlInactiveBackgroundColorLight =
                cfg.controlInactiveBackgroundColorLight ?: 0x00000000
            controlInactiveBackgroundColorDark =
                cfg.controlInactiveBackgroundColorDark ?: 0x00000000
            controlActiveBackgroundColorLight =
                cfg.controlActiveBackgroundColorLight ?: 0x00000000
            controlActiveBackgroundColorDark =
                cfg.controlActiveBackgroundColorDark ?: 0x00000000
        }
        controlShape = cfg.controlShape
        controlAppearanceEpoch++
    }

    /** Bumped on clipboard paste so control color editors remount with new values. */
    var controlAppearanceEpoch by mutableIntStateOf(0)

    fun toWholePanelClipboardSnapshot(
        currentWidgetConfigs: List<FloatingDashboardWidgetConfig>,
        widgetIndex: Int,
    ): WholePanelClipboardSnapshot {
        val targetCount = (wholePanelRows * wholePanelCols).coerceAtLeast(1)
        val base = normalizeWidgetConfigs(
            wholePanelWidgetsDraft ?: currentWidgetConfigs,
            maxOf(targetCount, widgetIndex + 1),
        ).toMutableList()
        if (widgetIndex in base.indices) {
            base[widgetIndex] = if (selectedDataKey.isNotEmpty()) {
                toDraftWidgetConfig()
            } else {
                FloatingDashboardWidgetConfig(dataKey = "", customTitle = "")
            }
        }
        return WholePanelClipboardSnapshot(
            name = wholePanelNameDraft,
            showTboxDisconnect = wholePanelShowTboxDisconnect,
            rows = wholePanelRows,
            cols = wholePanelCols,
            gridSpacingDp = wholePanelGridSpacingDp,
            pageNumber = wholePanelPageNumber,
            clickAction = wholePanelClickAction,
            collapseEdge = wholePanelCollapseEdge,
            collapseStripThicknessDp = wholePanelCollapseStripThicknessDp,
            collapseStripColorLight = wholePanelCollapseStripColorLight,
            collapseStripColorDark = wholePanelCollapseStripColorDark,
            collapseStripExpandedColorLight = wholePanelCollapseStripExpandedColorLight,
            collapseStripExpandedColorDark = wholePanelCollapseStripExpandedColorDark,
            collapseOnTileTap = wholePanelCollapseOnTileTap,
            collapseOnTileTapDelaySec = wholePanelCollapseOnTileTapDelaySec,
            panelBackgroundColorLight = wholePanelBackgroundColorLight,
            panelBackgroundColorDark = wholePanelBackgroundColorDark,
            panelBackgroundImageRelPathLight = wholePanelBackgroundImageRelPathLight,
            panelBackgroundImageRelPathDark = wholePanelBackgroundImageRelPathDark,
            panelShape = wholePanelShape,
            widgetsConfig = normalizeWidgetConfigs(base, targetCount),
        )
    }

    fun applyWholePanelFromClipboard(
        snapshot: WholePanelClipboardSnapshot,
        widgetIndex: Int,
    ) {
        wholePanelNameDraft = snapshot.name
        wholePanelShowTboxDisconnect = snapshot.showTboxDisconnect
        wholePanelRows = snapshot.rows
        wholePanelCols = snapshot.cols
        wholePanelGridSpacingDp = snapshot.gridSpacingDp
        wholePanelPageNumber = snapshot.pageNumber
        wholePanelClickAction = snapshot.clickAction
        wholePanelCollapseEdge = snapshot.collapseEdge
        wholePanelCollapseStripThicknessDp = snapshot.collapseStripThicknessDp
        wholePanelCollapseStripColorLight = snapshot.collapseStripColorLight
        wholePanelCollapseStripColorDark = snapshot.collapseStripColorDark
        wholePanelCollapseStripExpandedColorLight = snapshot.collapseStripExpandedColorLight
        wholePanelCollapseStripExpandedColorDark = snapshot.collapseStripExpandedColorDark
        wholePanelCollapseOnTileTap = snapshot.collapseOnTileTap
        wholePanelCollapseOnTileTapDelaySec = snapshot.collapseOnTileTapDelaySec
        wholePanelBackgroundColorLight = snapshot.panelBackgroundColorLight
        wholePanelBackgroundColorDark = snapshot.panelBackgroundColorDark
        wholePanelBackgroundImageRelPathLight = snapshot.panelBackgroundImageRelPathLight
        wholePanelBackgroundImageRelPathDark = snapshot.panelBackgroundImageRelPathDark
        wholePanelShape = normalizePanelShape(snapshot.panelShape)
        wholePanelDraftSeeded = true
        val targetCount = (snapshot.rows * snapshot.cols).coerceAtLeast(1)
        val widgets = normalizeWidgetConfigs(snapshot.widgetsConfig, targetCount)
        wholePanelWidgetsDraft = widgets
        val tileCfg = widgets.getOrNull(widgetIndex)
            ?: FloatingDashboardWidgetConfig(dataKey = "")
        applyDraftWidgetConfig(tileCfg, preserveDataKey = false)
    }

    val canSaveSelection: Boolean
        get() = when {
            selectedDataKey.isEmpty() -> true
            isMusicWidgetSelected -> selectedMediaPlayers.isNotEmpty()
            isDriveModeCycleWidgetSelected ->
                normalizeDriveModeCycleSelection(selectedDriveModes).isNotEmpty()
            isAppLauncherWidgetSelected -> launcherAppPackage.isNotBlank()
            isHttpRequestWidgetSelected -> parseHttpRequestWidgetYaml(httpRequestYaml).isSuccess
            WidgetsRepository.supportsDateTimeFormat(selectedDataKey) ->
                isValidDateTimeWidgetFormat(selectedDataKey, dateTimeFormat)
            else -> true
        }
}

@Composable
internal fun rememberWidgetSelectionDialogState(
    widgetIndex: Int,
    currentWidgets: List<DashboardWidget>,
    currentWidgetConfigs: List<FloatingDashboardWidgetConfig>,
    defaultBackgroundLight: Int,
    defaultBackgroundDark: Int,
    currentTheme: Int,
): WidgetSelectionDialogState {
    val initialConfig = currentWidgetConfigs.getOrNull(widgetIndex)
        ?: FloatingDashboardWidgetConfig(dataKey = "")
    val initialDataKey = currentWidgets.getOrNull(widgetIndex)?.dataKey ?: ""
    return remember(
        widgetIndex,
        currentWidgets,
        currentWidgetConfigs,
        defaultBackgroundLight,
        defaultBackgroundDark,
        currentTheme,
    ) {
        WidgetSelectionDialogState(
            initialDataKey = initialDataKey,
            initialConfig = initialConfig,
            panelDefaultBackgroundLight = defaultBackgroundLight,
            panelDefaultBackgroundDark = defaultBackgroundDark,
            initialColorThemeSegment = colorThemeSegmentFor(currentTheme),
        )
    }
}

@Composable
internal fun ExternalAppWidgetPickerSection(
    appWidgetId: Int?,
    onPickClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appWidgetManager = remember { AppWidgetManager.getInstance(context) }
    val selectedWidgetLabel = remember(appWidgetId) {
        appWidgetId?.let { id ->
            appWidgetManager.getAppWidgetInfo(id)?.loadLabel(context.packageManager)?.toString()
        }.orEmpty()
    }
    val label = if (selectedWidgetLabel.isNotBlank()) {
        selectedWidgetLabel
    } else {
        stringResource(R.string.widget_external_app_not_selected)
    }
    Column(modifier = modifier.padding(top = 8.dp)) {
        SettingsTitle(stringResource(R.string.widget_external_app_title))
        Text(
            text = stringResource(R.string.widget_external_app_selected, label),
            style = MaterialTheme.typography.tboxBody,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        OutlinedButton(onClick = rememberWrappedOnClick(onPickClick)) {
            Text(text = stringResource(R.string.widget_external_app_pick), style = MaterialTheme.typography.tboxButton)
        }
    }
}

@Composable
internal fun WidgetColorThemeSegmentRow(
    selectedSegment: Int,
    onSegmentSelected: (Int) -> Unit,
    enabled: Boolean,
) {
    WidgetTwoSegmentRow(
        selectedSegment = selectedSegment,
        onSegmentSelected = onSegmentSelected,
        enabled = enabled,
        label0 = stringResource(R.string.widget_color_theme_segment_light),
        label1 = stringResource(R.string.widget_color_theme_segment_dark),
    )
}

@Composable
internal fun WidgetControlStateSegmentRow(
    selectedSegment: Int,
    onSegmentSelected: (Int) -> Unit,
    enabled: Boolean,
) {
    WidgetTwoSegmentRow(
        selectedSegment = selectedSegment,
        onSegmentSelected = onSegmentSelected,
        enabled = enabled,
        label0 = stringResource(R.string.widget_control_state_segment_inactive),
        label1 = stringResource(R.string.widget_control_state_segment_active),
    )
}

@Composable
private fun WidgetTwoSegmentRow(
    selectedSegment: Int,
    onSegmentSelected: (Int) -> Unit,
    enabled: Boolean,
    label0: String,
    label1: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = rememberWrappedOnClick { onSegmentSelected(0) },
            enabled = enabled,
            modifier = Modifier.weight(1f),
            border = BorderStroke(
                1.dp,
                if (selectedSegment == 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                }
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (selectedSegment == 0) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                } else {
                    Color.Transparent
                },
                contentColor = if (selectedSegment == 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        ) {
            Text(
                text = label0,
                style = MaterialTheme.typography.tboxCaption,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        OutlinedButton(
            onClick = rememberWrappedOnClick { onSegmentSelected(1) },
            enabled = enabled,
            modifier = Modifier.weight(1f),
            border = BorderStroke(
                1.dp,
                if (selectedSegment == 1) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                }
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (selectedSegment == 1) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                } else {
                    Color.Transparent
                },
                contentColor = if (selectedSegment == 1) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        ) {
            Text(
                text = label1,
                style = MaterialTheme.typography.tboxCaption,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PanelCollapseWholeSettingsSection(
    state: WidgetSelectionDialogState,
    enabled: Boolean,
    currentThemeSegment: Int,
    settingsViewModel: SettingsViewModel,
    presetSlots: List<Int>,
) {
    val edgeOptions = listOf(
        PanelCollapseEdgeDropdownOption(PanelCollapseEdge.NONE, stringResource(R.string.settings_panel_collapse_edge_none)),
        PanelCollapseEdgeDropdownOption(PanelCollapseEdge.LEFT, stringResource(R.string.settings_panel_collapse_edge_left)),
        PanelCollapseEdgeDropdownOption(PanelCollapseEdge.RIGHT, stringResource(R.string.settings_panel_collapse_edge_right)),
        PanelCollapseEdgeDropdownOption(PanelCollapseEdge.TOP, stringResource(R.string.settings_panel_collapse_edge_top)),
        PanelCollapseEdgeDropdownOption(PanelCollapseEdge.BOTTOM, stringResource(R.string.settings_panel_collapse_edge_bottom)),
    )
    val selectedEdge = edgeOptions.firstOrNull { it.edge.storageValue == state.wholePanelCollapseEdge }
        ?: edgeOptions.first()
    SettingDropdownGeneric(
        selectedValue = selectedEdge,
        onValueChange = { state.wholePanelCollapseEdge = it.edge.storageValue },
        text = stringResource(R.string.settings_panel_collapse_edge_title),
        description = stringResource(R.string.settings_panel_collapse_edge_desc),
        enabled = enabled,
        options = edgeOptions,
        selectorWidth = WidgetDialogDropdownSelectorWidth,
    )
    SettingSliderInt(
        value = state.wholePanelCollapseStripThicknessDp,
        onValueChange = {
            state.wholePanelCollapseStripThicknessDp = normalizePanelCollapseStripThicknessDp(it)
        },
        text = stringResource(
            R.string.settings_panel_collapse_thickness_title,
            state.wholePanelCollapseStripThicknessDp,
        ),
        description = stringResource(R.string.settings_panel_collapse_thickness_desc),
        minValue = MIN_PANEL_COLLAPSE_STRIP_THICKNESS_DP,
        maxValue = MAX_PANEL_COLLAPSE_STRIP_THICKNESS_DP,
        enabled = enabled,
    )
    WidgetColorThemeSegmentRow(
        selectedSegment = state.wholePanelCollapseColorThemeSegment,
        onSegmentSelected = { state.wholePanelCollapseColorThemeSegment = it },
        enabled = enabled,
    )
    val editingLight = state.wholePanelCollapseColorThemeSegment == 0
    WidgetColorSetting(
        title = stringResource(R.string.settings_panel_collapse_strip_color_collapsed_title),
        colorValue = if (editingLight) {
            state.wholePanelCollapseStripColorLight
        } else {
            state.wholePanelCollapseStripColorDark
        },
        enabled = enabled,
        onColorChange = {
            if (editingLight) state.wholePanelCollapseStripColorLight = it
            else state.wholePanelCollapseStripColorDark = it
        },
        presetSlots = presetSlots,
        onPresetSlotColorSave = settingsViewModel::saveWidgetColorPresetSlot,
        valueTextStyle = MaterialTheme.typography.tboxTitle,
        valueLabelStyle = MaterialTheme.typography.tboxBody,
    )
    Text(
        text = stringResource(R.string.settings_panel_collapse_strip_color_collapsed_desc),
        style = MaterialTheme.typography.tboxCaption,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    WidgetColorSetting(
        title = stringResource(R.string.settings_panel_collapse_strip_color_expanded_title),
        colorValue = if (editingLight) {
            state.wholePanelCollapseStripExpandedColorLight
        } else {
            state.wholePanelCollapseStripExpandedColorDark
        },
        enabled = enabled,
        onColorChange = {
            if (editingLight) state.wholePanelCollapseStripExpandedColorLight = it
            else state.wholePanelCollapseStripExpandedColorDark = it
        },
        presetSlots = presetSlots,
        onPresetSlotColorSave = settingsViewModel::saveWidgetColorPresetSlot,
        valueTextStyle = MaterialTheme.typography.tboxTitle,
        valueLabelStyle = MaterialTheme.typography.tboxBody,
    )
    Text(
        text = stringResource(R.string.settings_panel_collapse_strip_color_expanded_desc),
        style = MaterialTheme.typography.tboxCaption,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    val collapseEdgeSelected =
        PanelCollapseEdge.fromStorage(state.wholePanelCollapseEdge) != PanelCollapseEdge.NONE
    val autoCollapseEnabled = enabled && collapseEdgeSelected
    SettingSwitch(
        state.wholePanelCollapseOnTileTap,
        { state.wholePanelCollapseOnTileTap = it },
        stringResource(R.string.settings_panel_collapse_on_tile_tap_title),
        stringResource(R.string.settings_panel_collapse_on_tile_tap_desc),
        autoCollapseEnabled,
    )
    SettingSliderInt(
        value = state.wholePanelCollapseOnTileTapDelaySec,
        onValueChange = {
            state.wholePanelCollapseOnTileTapDelaySec = normalizePanelCollapseOnTileTapDelaySec(it)
        },
        text = stringResource(
            R.string.settings_panel_collapse_on_tile_tap_delay_title,
            state.wholePanelCollapseOnTileTapDelaySec,
        ),
        description = stringResource(R.string.settings_panel_collapse_on_tile_tap_delay_desc),
        minValue = MIN_PANEL_COLLAPSE_ON_TILE_TAP_DELAY_SEC,
        maxValue = MAX_PANEL_COLLAPSE_ON_TILE_TAP_DELAY_SEC,
        enabled = autoCollapseEnabled && state.wholePanelCollapseOnTileTap,
    )
}

@Composable
private fun MainScreenPanelWholeSettingsSection(
    state: WidgetSelectionDialogState,
    pageCount: Int,
    enabled: Boolean,
    settingsViewModel: SettingsViewModel,
    presetSlots: List<Int>,
    panelStorageId: String,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = state.wholePanelNameDraft,
            onValueChange = { state.wholePanelNameDraft = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            enabled = enabled,
            singleLine = true,
            label = {
                Text(
                    text = stringResource(R.string.floating_panel_name_label),
                    style = MaterialTheme.typography.tboxBody
                )
            },
            textStyle = MaterialTheme.typography.tboxTitle
        )
        SettingSwitch(
            state.wholePanelClickAction,
            { state.wholePanelClickAction = it },
            stringResource(R.string.settings_open_app_on_main_screen_panel_click_title),
            "",
            enabled
        )
        SettingSwitch(
            state.wholePanelShowTboxDisconnect,
            { state.wholePanelShowTboxDisconnect = it },
            stringResource(R.string.settings_floating_tbox_disconnect_indicator_title),
            "",
            enabled
        )
        SettingDropdownGeneric(
            state.wholePanelRows,
            { state.wholePanelRows = it },
            stringResource(R.string.settings_main_screen_panel_rows_title),
            "",
            enabled,
            SettingsManager.DASHBOARD_PANEL_GRID_OPTIONS
        )
        SettingDropdownGeneric(
            state.wholePanelCols,
            { state.wholePanelCols = it },
            stringResource(R.string.settings_main_screen_panel_cols_title),
            "",
            enabled,
            SettingsManager.DASHBOARD_PANEL_GRID_OPTIONS
        )
        SettingSliderInt(
            value = state.wholePanelGridSpacingDp,
            onValueChange = { state.wholePanelGridSpacingDp = normalizePanelGridSpacingDp(it) },
            text = stringResource(
                R.string.settings_panel_grid_spacing_title,
                state.wholePanelGridSpacingDp,
            ),
            description = stringResource(R.string.settings_panel_grid_spacing_desc),
            minValue = MIN_PANEL_GRID_SPACING_DP,
            maxValue = MAX_PANEL_GRID_SPACING_DP,
            enabled = enabled,
        )
        SettingDropdownGeneric(
            state.wholePanelPageNumber,
            { state.wholePanelPageNumber = it },
            stringResource(R.string.settings_main_screen_panel_page_title),
            stringResource(R.string.settings_main_screen_panel_page_desc),
            enabled,
            (1..pageCount.coerceAtLeast(1)).toList(),
        )
        PanelCollapseWholeSettingsSection(
            state = state,
            enabled = enabled,
            currentThemeSegment = state.wholePanelCollapseColorThemeSegment,
            settingsViewModel = settingsViewModel,
            presetSlots = presetSlots,
        )
        PanelBackgroundAppearanceSettingsSection(
            panelStorageId = panelStorageId,
            enabled = enabled,
            colorThemeSegment = state.wholePanelBackgroundColorThemeSegment,
            onColorThemeSegmentChange = { state.wholePanelBackgroundColorThemeSegment = it },
            backgroundColorLight = state.wholePanelBackgroundColorLight,
            backgroundColorDark = state.wholePanelBackgroundColorDark,
            onBackgroundColorLightChange = { state.wholePanelBackgroundColorLight = it },
            onBackgroundColorDarkChange = { state.wholePanelBackgroundColorDark = it },
            backgroundImageRelPathLight = state.wholePanelBackgroundImageRelPathLight,
            backgroundImageRelPathDark = state.wholePanelBackgroundImageRelPathDark,
            onBackgroundImageRelPathLightChange = { state.wholePanelBackgroundImageRelPathLight = it },
            onBackgroundImageRelPathDarkChange = { state.wholePanelBackgroundImageRelPathDark = it },
            panelShape = state.wholePanelShape,
            onPanelShapeChange = { state.wholePanelShape = it },
            settingsViewModel = settingsViewModel,
            presetSlots = presetSlots,
        )
    }
}

@Composable
private fun FloatingDashboardWholeSettingsSection(
    state: WidgetSelectionDialogState,
    enabled: Boolean,
    settingsViewModel: SettingsViewModel,
    presetSlots: List<Int>,
    panelStorageId: String,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = state.wholePanelNameDraft,
            onValueChange = { state.wholePanelNameDraft = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            enabled = enabled,
            singleLine = true,
            label = {
                Text(
                    text = stringResource(R.string.floating_panel_name_label),
                    style = MaterialTheme.typography.tboxBody
                )
            },
            textStyle = MaterialTheme.typography.tboxTitle
        )
        SettingSwitch(
            state.wholePanelClickAction,
            { state.wholePanelClickAction = it },
            stringResource(R.string.settings_open_app_on_panel_click_title),
            "",
            enabled
        )
        SettingSwitch(
            state.wholePanelShowTboxDisconnect,
            { state.wholePanelShowTboxDisconnect = it },
            stringResource(R.string.settings_floating_tbox_disconnect_indicator_title),
            "",
            enabled
        )
        SettingDropdownGeneric(
            state.wholePanelRows,
            { state.wholePanelRows = it },
            stringResource(R.string.settings_floating_rows_title),
            "",
            enabled,
            SettingsManager.DASHBOARD_PANEL_GRID_OPTIONS
        )
        SettingDropdownGeneric(
            state.wholePanelCols,
            { state.wholePanelCols = it },
            stringResource(R.string.settings_floating_cols_title),
            "",
            enabled,
            SettingsManager.DASHBOARD_PANEL_GRID_OPTIONS
        )
        SettingSliderInt(
            value = state.wholePanelGridSpacingDp,
            onValueChange = { state.wholePanelGridSpacingDp = normalizePanelGridSpacingDp(it) },
            text = stringResource(
                R.string.settings_panel_grid_spacing_title,
                state.wholePanelGridSpacingDp,
            ),
            description = stringResource(R.string.settings_panel_grid_spacing_desc),
            minValue = MIN_PANEL_GRID_SPACING_DP,
            maxValue = MAX_PANEL_GRID_SPACING_DP,
            enabled = enabled,
        )
        PanelCollapseWholeSettingsSection(
            state = state,
            enabled = enabled,
            currentThemeSegment = state.wholePanelCollapseColorThemeSegment,
            settingsViewModel = settingsViewModel,
            presetSlots = presetSlots,
        )
        PanelBackgroundAppearanceSettingsSection(
            panelStorageId = panelStorageId,
            enabled = enabled,
            colorThemeSegment = state.wholePanelBackgroundColorThemeSegment,
            onColorThemeSegmentChange = { state.wholePanelBackgroundColorThemeSegment = it },
            backgroundColorLight = state.wholePanelBackgroundColorLight,
            backgroundColorDark = state.wholePanelBackgroundColorDark,
            onBackgroundColorLightChange = { state.wholePanelBackgroundColorLight = it },
            onBackgroundColorDarkChange = { state.wholePanelBackgroundColorDark = it },
            backgroundImageRelPathLight = state.wholePanelBackgroundImageRelPathLight,
            backgroundImageRelPathDark = state.wholePanelBackgroundImageRelPathDark,
            onBackgroundImageRelPathLightChange = { state.wholePanelBackgroundImageRelPathLight = it },
            onBackgroundImageRelPathDarkChange = { state.wholePanelBackgroundImageRelPathDark = it },
            panelShape = state.wholePanelShape,
            onPanelShapeChange = { state.wholePanelShape = it },
            settingsViewModel = settingsViewModel,
            presetSlots = presetSlots,
        )
    }
}

internal data class WidgetSelectionDescriptionResources(
    val descriptionRes: Int,
    val actionsRes: Int?,
)

internal fun resolveWidgetSelectionDescriptionResources(
    dataKey: String,
    selectedDataKey: String,
): WidgetSelectionDescriptionResources? {
    if (dataKey != selectedDataKey) return null
    val descriptionRes = WidgetsRepository.getDescriptionResForDataKey(dataKey) ?: return null
    return WidgetSelectionDescriptionResources(
        descriptionRes = descriptionRes,
        actionsRes = WidgetsRepository.getActionsDescriptionResForDataKey(dataKey),
    )
}

@Composable
internal fun WidgetSelectionDialogForm(
    titleText: String,
    settingsViewModel: SettingsViewModel,
    state: WidgetSelectionDialogState,
    modifier: Modifier = Modifier,
    dataKeyFilter: (String) -> Boolean = { true },
    bottomContent: (@Composable () -> Unit)? = null,
    mainScreenPanelId: String = "",
    floatingDashboardPanelId: String = "",
    widgetIndex: Int = 0,
    currentWidgetConfigs: List<FloatingDashboardWidgetConfig> = emptyList(),
    tileBackgroundPanelStorageId: String = TileBackgroundImageStorage.MAIN_TAB_DASHBOARD_STORAGE_ID,
) {
    val context = LocalContext.current
    val widgetColorPresetSlots by settingsViewModel.widgetColorPresetSlots.collectAsStateWithLifecycle()
    val mainScreenPageCount by settingsViewModel.mainScreenPageCount.collectAsStateWithLifecycle()
    val noTboxConnect by settingsViewModel.noTboxConnect.collectAsStateWithLifecycle()
    LaunchedEffect(noTboxConnect) {
        state.preferUseMbCanVhalDefault = noTboxConnect
    }
    val notSelectedLabel = stringResource(R.string.widget_option_not_selected)
    val widgetPairs = WidgetsRepository.getAvailableDataKeysWidgets(noTboxConnect)
        .filter { it.isNotEmpty() && dataKeyFilter(it) }
        .map { key ->
            key to WidgetsRepository.getTitleUnitForDataKey(context, key)
        }
    val selectedKey = state.selectedDataKey

    Column(
        modifier = modifier
    ) {
        SettingsTitle(titleText)
        WidgetDialogClipboardActionsRow(
            state = state,
            currentWidgetConfigs = currentWidgetConfigs,
            widgetIndex = widgetIndex,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when {
                state.showWholePanelSettings && mainScreenPanelId.isNotBlank() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(androidx.compose.foundation.rememberScrollState())
                            .padding(12.dp)
                    ) {
                        MainScreenPanelWholeSettingsSection(
                            state = state,
                            pageCount = mainScreenPageCount,
                            enabled = true,
                            settingsViewModel = settingsViewModel,
                            presetSlots = widgetColorPresetSlots,
                            panelStorageId = mainScreenPanelId,
                        )
                    }
                }
                state.showWholePanelSettings && floatingDashboardPanelId.isNotBlank() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(androidx.compose.foundation.rememberScrollState())
                            .padding(12.dp)
                    ) {
                        FloatingDashboardWholeSettingsSection(
                            state = state,
                            enabled = true,
                            settingsViewModel = settingsViewModel,
                            presetSlots = widgetColorPresetSlots,
                            panelStorageId = floatingDashboardPanelId,
                        )
                    }
                }
                state.showAdvancedSettings -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
                        .padding(12.dp)
                ) {
                    if (state.isMusicWidgetSelected) {
                        MediaPlayersInlineSelection(
                            settingsViewModel = settingsViewModel,
                            selectedPlayers = state.selectedMediaPlayers,
                            onSelectionChange = { state.selectedMediaPlayers = it },
                            enabled = state.togglesEnabled,
                        )
                        if (state.selectedMediaPlayers.isEmpty()) {
                            Text(
                                text = stringResource(R.string.widget_music_players_required),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.tboxBody
                            )
                        }
                        SettingSwitch(
                            state.mediaAutoPlayOnInit,
                            {
                                state.mediaAutoPlayOnInit = it
                                if (!it) {
                                    state.mediaAutoPlayOnlyWhenEngineRunning = false
                                }
                            },
                            stringResource(R.string.widget_music_auto_play_on_init),
                            "",
                            state.togglesEnabled
                        )
                        SettingSwitch(
                            state.mediaAutoPlayOnlyWhenEngineRunning,
                            { state.mediaAutoPlayOnlyWhenEngineRunning = it },
                            stringResource(R.string.widget_music_auto_play_only_engine),
                            "",
                            state.togglesEnabled && state.mediaAutoPlayOnInit
                        )
                        // anymani: новая опция для контроля возврата лаунчера
                        SettingSwitch(
                            state.mediaKeepPlayerForeground,
                            { state.mediaKeepPlayerForeground = it },
                            stringResource(R.string.widget_music_keep_player_foreground),
                            stringResource(R.string.widget_music_keep_player_foreground_desc),
                            state.togglesEnabled
                        )
                    }
                    AppLauncherWidgetSettingsSection(
                        state = state,
                        settingsViewModel = settingsViewModel,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )
                    HttpRequestWidgetSettingsSection(
                        state = state,
                        settingsViewModel = settingsViewModel,
                        panelStorageId = tileBackgroundPanelStorageId,
                        widgetIndex = widgetIndex,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )
                    SettingSwitch(
                        state.showTitle,
                        { state.showTitle = it },
                        stringResource(R.string.widget_show_title),
                        "",
                        state.togglesEnabled
                    )
                    if (state.showTitle) {
                        val titlePositionEntries = listOf(
                            WidgetTitlePositionDropdownEntry(
                                stringResource(R.string.widget_title_position_top),
                                WIDGET_TITLE_POSITION_TOP,
                            ),
                            WidgetTitlePositionDropdownEntry(
                                stringResource(R.string.widget_title_position_bottom),
                                WIDGET_TITLE_POSITION_BOTTOM,
                            ),
                        )
                        val selectedTitlePosition = titlePositionEntries.firstOrNull {
                            it.stored == normalizeWidgetTitlePosition(state.titlePosition)
                        } ?: titlePositionEntries.first()
                        SettingDropdownGeneric(
                            selectedValue = selectedTitlePosition,
                            onValueChange = { state.titlePosition = it.stored },
                            text = stringResource(R.string.widget_title_position_title),
                            description = "",
                            enabled = state.togglesEnabled,
                            options = titlePositionEntries,
                            selectorWidth = WidgetDialogDropdownSelectorWidth,
                        )
                    }
                    OutlinedTextField(
                        value = state.customTitle,
                        onValueChange = { state.customTitle = it },
                        enabled = state.togglesEnabled,
                        textStyle = MaterialTheme.typography.tboxTitle,
                        label = {
                            Text(
                                stringResource(R.string.widget_custom_title_label),
                                style = MaterialTheme.typography.tboxBody
                            )
                        },
                        placeholder = {
                            Text(
                                stringResource(R.string.widget_custom_title_hint),
                                style = MaterialTheme.typography.tboxBody,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        singleLine = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp)
                    )
                    if (WidgetsRepository.supportsShowUnit(state.selectedDataKey)) {
                        SettingSwitch(
                            state.showUnit,
                            { state.showUnit = it },
                            stringResource(R.string.widget_show_unit),
                            "",
                            state.togglesEnabled
                        )
                    }
                    if (WidgetsRepository.supportsSingleLineDualMetrics(state.selectedDataKey)) {
                        SettingSwitch(
                            state.singleLineDualMetrics,
                            { state.singleLineDualMetrics = it },
                            stringResource(R.string.widget_single_line_dual_metrics),
                            "",
                            state.togglesEnabled
                        )
                    }
                    if (WidgetsRepository.supportsValueAccuracy(state.selectedDataKey)) {
                        val accuracyEntries = listOf(
                            ValueAccuracyDropdownEntry(
                                stringResource(R.string.widget_value_accuracy_default),
                                null
                            ),
                            ValueAccuracyDropdownEntry(
                                stringResource(R.string.widget_value_accuracy_0),
                                0
                            ),
                            ValueAccuracyDropdownEntry(
                                stringResource(R.string.widget_value_accuracy_1),
                                1
                            ),
                            ValueAccuracyDropdownEntry(
                                stringResource(R.string.widget_value_accuracy_2),
                                2
                            ),
                        )
                        val selectedAccuracyEntry = accuracyEntries.find { it.stored == state.valueAccuracy }
                            ?: accuracyEntries.first()
                        SettingDropdownGeneric(
                            selectedValue = selectedAccuracyEntry,
                            onValueChange = { state.valueAccuracy = it.stored },
                            text = stringResource(R.string.widget_value_accuracy_title),
                            description = stringResource(R.string.widget_value_accuracy_desc),
                            enabled = state.togglesEnabled,
                            options = accuracyEntries,
                            selectorWidth = WidgetDialogDropdownSelectorWidth
                        )
                    }
                    if (WidgetsRepository.supportsDateTimeFormat(state.selectedDataKey)) {
                        val dateTimeFormatError = !isValidDateTimeWidgetFormat(
                            state.selectedDataKey,
                            state.dateTimeFormat,
                        )
                        val dateTimeFormatPreview = previewDateTimeWidgetFormat(
                            state.selectedDataKey,
                            state.dateTimeFormat,
                        ).orEmpty()
                        OutlinedTextField(
                            value = state.dateTimeFormat,
                            onValueChange = { state.dateTimeFormat = it },
                            enabled = state.togglesEnabled,
                            isError = dateTimeFormatError,
                            textStyle = MaterialTheme.typography.tboxTitle,
                            label = {
                                Text(
                                    stringResource(R.string.widget_datetime_format_label),
                                    style = MaterialTheme.typography.tboxBody,
                                )
                            },
                            placeholder = {
                                Text(
                                    stringResource(R.string.widget_datetime_format_hint),
                                    style = MaterialTheme.typography.tboxBody,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            supportingText = {
                                Text(
                                    text = if (dateTimeFormatError) {
                                        stringResource(R.string.widget_datetime_format_error)
                                    } else {
                                        stringResource(
                                            R.string.widget_datetime_format_preview,
                                            dateTimeFormatPreview,
                                        )
                                    },
                                    style = MaterialTheme.typography.tboxCaption,
                                )
                            },
                            singleLine = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 4.dp),
                        )
                    }
                    if (WidgetsRepository.supportsUseMbCanVhal(state.selectedDataKey)) {
                        SettingSwitch(
                            state.useMbCanVhal,
                            { state.useMbCanVhal = it },
                            stringResource(R.string.widget_media_volume_use_mbcan_vhal),
                            "",
                            state.togglesEnabled
                        )
                    }
                    if (WidgetsRepository.supportsStepperAdjustIconStyle(state.selectedDataKey)) {
                        val stepperIconEntries = listOf(
                            StepperAdjustIconStyleDropdownEntry(
                                stringResource(R.string.widget_stepper_adjust_icon_plus_minus),
                                STEPPER_ADJUST_ICON_PLUS_MINUS,
                            ),
                            StepperAdjustIconStyleDropdownEntry(
                                stringResource(R.string.widget_stepper_adjust_icon_arrows),
                                STEPPER_ADJUST_ICON_ARROWS,
                            ),
                        )
                        val selectedStepperIconEntry = stepperIconEntries.find {
                            it.stored == state.stepperAdjustIconStyle
                        } ?: stepperIconEntries.first()
                        SettingDropdownGeneric(
                            selectedValue = selectedStepperIconEntry,
                            onValueChange = { state.stepperAdjustIconStyle = it.stored },
                            text = stringResource(R.string.widget_stepper_adjust_icon_style_title),
                            description = stringResource(R.string.widget_stepper_adjust_icon_style_desc),
                            enabled = state.togglesEnabled,
                            options = stepperIconEntries,
                            selectorWidth = WidgetDialogDropdownSelectorWidth,
                        )
                    }
                    if (WidgetsRepository.supportsEspRelayMode(state.selectedDataKey)) {
                        val relayModeEntries = listOf(
                            EspRelayModeDropdownEntry(
                                EspRelayWidgetMode.BUTTON,
                                stringResource(R.string.widget_esp_relay_mode_button),
                            ),
                            EspRelayModeDropdownEntry(
                                EspRelayWidgetMode.RELAY,
                                stringResource(R.string.widget_esp_relay_mode_relay),
                            ),
                        )
                        val selectedRelayMode = relayModeEntries.find {
                            it.mode == state.espRelayMode
                        } ?: relayModeEntries.first { it.mode == EspRelayWidgetMode.DEFAULT }
                        SettingDropdownGeneric(
                            selectedValue = selectedRelayMode,
                            onValueChange = { state.espRelayMode = it.mode },
                            text = stringResource(R.string.widget_esp_relay_mode_title),
                            description = stringResource(R.string.widget_esp_relay_mode_desc),
                            enabled = state.togglesEnabled,
                            options = relayModeEntries,
                            selectorWidth = WidgetDialogDropdownSelectorWidth,
                        )
                    }
                    if (isCruiseWidgetDataKey(state.selectedDataKey)) {
                        val cruiseTypeEntries = listOf(
                            CruiseControlTypeDropdownEntry(
                                CruiseControlType.AUTO,
                                stringResource(R.string.widget_cruise_control_type_auto),
                            ),
                            CruiseControlTypeDropdownEntry(
                                CruiseControlType.ACC,
                                stringResource(R.string.widget_cruise_control_type_acc),
                            ),
                            CruiseControlTypeDropdownEntry(
                                CruiseControlType.CCS,
                                stringResource(R.string.widget_cruise_control_type_ccs),
                            ),
                        )
                        val selectedCruiseType = cruiseTypeEntries.find {
                            it.type == state.cruiseControlType
                        } ?: cruiseTypeEntries.first { it.type == CruiseControlType.DEFAULT }
                        SettingDropdownGeneric(
                            selectedValue = selectedCruiseType,
                            onValueChange = { state.cruiseControlType = it.type },
                            text = stringResource(R.string.widget_cruise_control_type_title),
                            description = stringResource(R.string.widget_cruise_control_type_desc),
                            enabled = state.togglesEnabled,
                            options = cruiseTypeEntries,
                            selectorWidth = WidgetDialogDropdownSelectorWidth,
                        )
                    }
                    if (isAccCruiseWidgetDataKey(state.selectedDataKey)) {
                        SettingInt(
                            value = state.accCruiseTargetKmh,
                            onValueChange = {
                                state.accCruiseTargetKmh = normalizeAccCruiseTargetKmh(it)
                            },
                            text = stringResource(R.string.widget_acc_cruise_target_title),
                            description = stringResource(R.string.widget_acc_cruise_target_desc),
                            minValue = ACC_CRUISE_TARGET_KMH_MIN,
                            maxValue = ACC_CRUISE_TARGET_KMH_MAX,
                        )
                        SettingInt(
                            value = state.accCruiseIncreaseIntervalMs,
                            onValueChange = {
                                state.accCruiseIncreaseIntervalMs =
                                    normalizeAccCruiseStepIntervalMs(it)
                            },
                            text = stringResource(R.string.widget_acc_cruise_increase_interval_title),
                            description = stringResource(R.string.widget_acc_cruise_increase_interval_desc),
                            minValue = ACC_CRUISE_STEP_INTERVAL_MS_MIN,
                            maxValue = ACC_CRUISE_STEP_INTERVAL_MS_MAX,
                        )
                        SettingInt(
                            value = state.accCruiseDecreaseIntervalMs,
                            onValueChange = {
                                state.accCruiseDecreaseIntervalMs =
                                    normalizeAccCruiseStepIntervalMs(it)
                            },
                            text = stringResource(R.string.widget_acc_cruise_decrease_interval_title),
                            description = stringResource(R.string.widget_acc_cruise_decrease_interval_desc),
                            minValue = ACC_CRUISE_STEP_INTERVAL_MS_MIN,
                            maxValue = ACC_CRUISE_STEP_INTERVAL_MS_MAX,
                        )
                    }
                    if (state.selectedDataKey == DRIVE_MODE_WIDGET_DATA_KEY) {
                        val selectedOption = DRIVE_MODE_WIDGET_OPTIONS.firstOrNull {
                            it.rawValue == normalizeDriveModeWidgetRawValue(state.selectedDriveMode)
                        } ?: DRIVE_MODE_WIDGET_OPTIONS.first()
                        SettingDropdownGeneric(
                            selectedValue = selectedOption,
                            onValueChange = { state.selectedDriveMode = it.rawValue },
                            text = stringResource(R.string.widget_drive_mode_target_title),
                            description = stringResource(R.string.widget_drive_mode_target_desc),
                            enabled = state.togglesEnabled,
                            options = DRIVE_MODE_WIDGET_OPTIONS,
                            selectorWidth = WidgetDialogDropdownSelectorWidth
                        )
                    }
                    if (state.isDriveModeCycleWidgetSelected) {
                        Text(
                            text = stringResource(R.string.widget_drive_mode_cycle_modes_title),
                            style = MaterialTheme.typography.tboxBody,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Text(
                            text = stringResource(R.string.widget_drive_mode_cycle_modes_desc),
                            style = MaterialTheme.typography.tboxCaption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        val selectedSet = state.selectedDriveModes.toSet()
                        DRIVE_MODE_WIDGET_OPTIONS.forEach { option ->
                            val checked = option.rawValue in selectedSet
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = {
                                        state.selectedDriveModes = toggleDriveModeCycleSelection(
                                            state.selectedDriveModes,
                                            option.rawValue,
                                        )
                                    },
                                    enabled = state.togglesEnabled,
                                )
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.tboxBody,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                    if (isActiveTripWidgetDataKey(state.selectedDataKey)) {
                        val sourceOptions = listOf(
                            TripWidgetSourceDropdownEntry(
                                TRIP_WIDGET_SOURCE_CURRENT,
                                stringResource(R.string.trips_widget_source_current),
                            ),
                            TripWidgetSourceDropdownEntry(
                                TRIP_WIDGET_SOURCE_PERSISTENT,
                                stringResource(R.string.trips_widget_source_persistent),
                            ),
                        )
                        val selectedSource = sourceOptions.firstOrNull {
                            it.source == normalizeTripWidgetSource(state.tripWidgetSource)
                        } ?: sourceOptions.first()
                        SettingDropdownGeneric(
                            selectedValue = selectedSource,
                            onValueChange = { state.tripWidgetSource = it.source },
                            text = stringResource(R.string.trips_widget_source_title),
                            description = "",
                            enabled = state.togglesEnabled,
                            options = sourceOptions,
                            selectorWidth = WidgetDialogDropdownSelectorWidth,
                        )
                        SettingSwitch(
                            state.tripWidgetShowRowDividers,
                            { state.tripWidgetShowRowDividers = it },
                            stringResource(R.string.trips_widget_show_row_dividers_title),
                            "",
                            state.togglesEnabled,
                        )
                        SettingInt(
                            value = state.tripWidgetLabelColumnWidthPercent,
                            onValueChange = { state.tripWidgetLabelColumnWidthPercent = it },
                            text = stringResource(R.string.trips_widget_label_column_width_title),
                            description = stringResource(R.string.trips_widget_label_column_width_desc),
                            minValue = TripWidgetTileDisplay.MIN_LABEL_COLUMN_WIDTH_PERCENT,
                            maxValue = TripWidgetTileDisplay.MAX_LABEL_COLUMN_WIDTH_PERCENT,
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.widget_scale, state.scale),
                            style = MaterialTheme.typography.tboxTitle,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.widget_scale_hint),
                            style = MaterialTheme.typography.tboxBody,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = state.scale,
                            onValueChange = { newValue ->
                                state.scale = normalizeWidgetScale(newValue)
                            },
                            valueRange = 0.1f..2.0f,
                            steps = 18,
                            enabled = state.togglesEnabled,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    SettingSliderInt(
                        value = state.paddingTopPercent,
                        onValueChange = {
                            state.paddingTopPercent = normalizeWidgetPaddingPercent(it)
                        },
                        text = stringResource(R.string.widget_padding_top, state.paddingTopPercent),
                        description = stringResource(R.string.widget_padding_hint),
                        minValue = MIN_WIDGET_PADDING_PERCENT,
                        maxValue = MAX_WIDGET_PADDING_PERCENT,
                        enabled = state.togglesEnabled,
                    )
                    SettingSliderInt(
                        value = state.paddingBottomPercent,
                        onValueChange = {
                            state.paddingBottomPercent = normalizeWidgetPaddingPercent(it)
                        },
                        text = stringResource(
                            R.string.widget_padding_bottom,
                            state.paddingBottomPercent,
                        ),
                        description = stringResource(R.string.widget_padding_hint),
                        minValue = MIN_WIDGET_PADDING_PERCENT,
                        maxValue = MAX_WIDGET_PADDING_PERCENT,
                        enabled = state.togglesEnabled,
                    )
                    SettingSliderInt(
                        value = state.paddingStartPercent,
                        onValueChange = {
                            state.paddingStartPercent = normalizeWidgetPaddingPercent(it)
                        },
                        text = stringResource(
                            R.string.widget_padding_start,
                            state.paddingStartPercent,
                        ),
                        description = stringResource(R.string.widget_padding_hint),
                        minValue = MIN_WIDGET_PADDING_PERCENT,
                        maxValue = MAX_WIDGET_PADDING_PERCENT,
                        enabled = state.togglesEnabled,
                    )
                    SettingSliderInt(
                        value = state.paddingEndPercent,
                        onValueChange = {
                            state.paddingEndPercent = normalizeWidgetPaddingPercent(it)
                        },
                        text = stringResource(R.string.widget_padding_end, state.paddingEndPercent),
                        description = stringResource(R.string.widget_padding_hint),
                        minValue = MIN_WIDGET_PADDING_PERCENT,
                        maxValue = MAX_WIDGET_PADDING_PERCENT,
                        enabled = state.togglesEnabled,
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.widget_shape, state.shape),
                            style = MaterialTheme.typography.tboxTitle,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.widget_shape_hint),
                            style = MaterialTheme.typography.tboxBody,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = state.shape.toFloat(),
                            onValueChange = { newValue ->
                                state.shape = normalizeWidgetShape(newValue.toInt())
                            },
                            valueRange = 0f..50f,
                            steps = 49,
                            enabled = state.togglesEnabled,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    val textAlignEntries = listOf(
                        WidgetTextAlignDropdownEntry(
                            stringResource(R.string.widget_text_align_center),
                            WIDGET_TEXT_ALIGN_CENTER,
                        ),
                        WidgetTextAlignDropdownEntry(
                            stringResource(R.string.widget_text_align_start),
                            WIDGET_TEXT_ALIGN_START,
                        ),
                        WidgetTextAlignDropdownEntry(
                            stringResource(R.string.widget_text_align_end),
                            WIDGET_TEXT_ALIGN_END,
                        ),
                    )
                    val selectedTextAlign = textAlignEntries.firstOrNull {
                        it.stored == normalizeWidgetTextAlign(state.textAlign)
                    } ?: textAlignEntries.first()
                    SettingDropdownGeneric(
                        selectedValue = selectedTextAlign,
                        onValueChange = { state.textAlign = it.stored },
                        text = stringResource(R.string.widget_text_align_title),
                        description = "",
                        enabled = state.togglesEnabled,
                        options = textAlignEntries,
                        selectorWidth = WidgetDialogDropdownSelectorWidth,
                    )
                    val fontWeightEntries = listOf(
                        WidgetFontWeightDropdownEntry(
                            stringResource(R.string.widget_font_weight_normal),
                            WIDGET_FONT_WEIGHT_NORMAL,
                        ),
                        WidgetFontWeightDropdownEntry(
                            stringResource(R.string.widget_font_weight_medium),
                            WIDGET_FONT_WEIGHT_MEDIUM,
                        ),
                        WidgetFontWeightDropdownEntry(
                            stringResource(R.string.widget_font_weight_semi_bold),
                            WIDGET_FONT_WEIGHT_SEMI_BOLD,
                        ),
                    )
                    val selectedFontWeight = fontWeightEntries.firstOrNull {
                        it.stored == normalizeWidgetFontWeight(state.fontWeight)
                    } ?: fontWeightEntries[1]
                    SettingDropdownGeneric(
                        selectedValue = selectedFontWeight,
                        onValueChange = { state.fontWeight = it.stored },
                        text = stringResource(R.string.widget_font_weight_title),
                        description = "",
                        enabled = state.togglesEnabled,
                        options = fontWeightEntries,
                        selectorWidth = WidgetDialogDropdownSelectorWidth,
                    )
                    WidgetColorThemeSegmentRow(
                        selectedSegment = state.advancedColorThemeSegment,
                        onSegmentSelected = { state.advancedColorThemeSegment = it },
                        enabled = state.togglesEnabled
                    )
                    if (state.advancedColorThemeSegment == 0) {
                        WidgetColorSetting(
                            title = stringResource(R.string.widget_text_color_light),
                            colorValue = state.textColorLight,
                            enabled = state.togglesEnabled,
                            onColorChange = { state.textColorLight = it },
                            presetSlots = widgetColorPresetSlots,
                            onPresetSlotColorSave = settingsViewModel::saveWidgetColorPresetSlot,
                            valueTextStyle = MaterialTheme.typography.tboxTitle,
                            valueLabelStyle = MaterialTheme.typography.tboxBody,
                        )
                        WidgetColorSetting(
                            title = stringResource(R.string.widget_background_color_light),
                            colorValue = state.backgroundColorLight,
                            enabled = state.togglesEnabled,
                            onColorChange = { state.backgroundColorLight = it },
                            presetSlots = widgetColorPresetSlots,
                            onPresetSlotColorSave = settingsViewModel::saveWidgetColorPresetSlot,
                            valueTextStyle = MaterialTheme.typography.tboxTitle,
                            valueLabelStyle = MaterialTheme.typography.tboxBody,
                        )
                    } else {
                        WidgetColorSetting(
                            title = stringResource(R.string.widget_text_color_dark),
                            colorValue = state.textColorDark,
                            enabled = state.togglesEnabled,
                            onColorChange = { state.textColorDark = it },
                            presetSlots = widgetColorPresetSlots,
                            onPresetSlotColorSave = settingsViewModel::saveWidgetColorPresetSlot,
                            valueTextStyle = MaterialTheme.typography.tboxTitle,
                            valueLabelStyle = MaterialTheme.typography.tboxBody,
                        )
                        WidgetColorSetting(
                            title = stringResource(R.string.widget_background_color_dark),
                            colorValue = state.backgroundColorDark,
                            enabled = state.togglesEnabled,
                            onColorChange = { state.backgroundColorDark = it },
                            presetSlots = widgetColorPresetSlots,
                            onPresetSlotColorSave = settingsViewModel::saveWidgetColorPresetSlot,
                            valueTextStyle = MaterialTheme.typography.tboxTitle,
                            valueLabelStyle = MaterialTheme.typography.tboxBody,
                        )
                    }
                    TileBackgroundImageSettingsSection(
                        state = state,
                        settingsViewModel = settingsViewModel,
                        panelStorageId = tileBackgroundPanelStorageId,
                        widgetIndex = widgetIndex,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                    OutlinedButton(
                        onClick = rememberWrappedOnClick { state.resetTileTextAndBackgroundColors() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 8.dp),
                        enabled = state.togglesEnabled,
                    ) {
                        Text(
                            stringResource(R.string.widget_reset_text_background_colors),
                            style = MaterialTheme.typography.tboxBody
                        )
                    }

                    Text(
                        text = stringResource(R.string.widget_control_colors_section),
                        style = MaterialTheme.typography.tboxTitle,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                    val musicStepperBgArgb = MaterialTheme.colorScheme.surfaceVariant
                        .copy(alpha = DEFAULT_MUSIC_STEPPER_CONTROL_BG_ALPHA)
                        .toArgb()
                    SettingSwitch(
                        state.controlColorsUseDefaults,
                        { enabled ->
                            if (enabled) {
                                state.clearControlColorsToDefaults()
                            } else {
                                val kind = controlAppearanceKindForDataKey(state.selectedDataKey)
                                state.applyControlColorSeed(
                                    seedControlColorsFromDefaults(
                                        kind = kind,
                                        tileTextColorLight = state.textColorLight,
                                        tileTextColorDark = state.textColorDark,
                                        musicStepperBgArgb = musicStepperBgArgb,
                                        dataKey = state.selectedDataKey,
                                    )
                                )
                            }
                        },
                        stringResource(R.string.widget_control_colors_use_defaults),
                        "",
                        state.togglesEnabled,
                    )
                    key(state.controlAppearanceEpoch) {
                        val controlEditorsEnabled =
                            state.togglesEnabled && !state.controlColorsUseDefaults
                        WidgetControlStateSegmentRow(
                            selectedSegment = state.controlStateSegment,
                            onSegmentSelected = { state.controlStateSegment = it },
                            enabled = controlEditorsEnabled,
                        )
                        WidgetColorSetting(
                            title = stringResource(R.string.widget_control_content_color),
                            colorValue = state.controlContentColorForEditor(),
                            enabled = controlEditorsEnabled,
                            onColorChange = { state.setControlContentColorForEditor(it) },
                            presetSlots = widgetColorPresetSlots,
                            onPresetSlotColorSave = settingsViewModel::saveWidgetColorPresetSlot,
                            valueTextStyle = MaterialTheme.typography.tboxTitle,
                            valueLabelStyle = MaterialTheme.typography.tboxBody,
                        )
                        WidgetColorSetting(
                            title = stringResource(R.string.widget_control_background_color),
                            colorValue = state.controlBackgroundColorForEditor(),
                            enabled = controlEditorsEnabled,
                            onColorChange = { state.setControlBackgroundColorForEditor(it) },
                            presetSlots = widgetColorPresetSlots,
                            onPresetSlotColorSave = settingsViewModel::saveWidgetColorPresetSlot,
                            valueTextStyle = MaterialTheme.typography.tboxTitle,
                            valueLabelStyle = MaterialTheme.typography.tboxBody,
                        )
                    }
                    val controlShapeDisplay = state.controlShape
                        ?: defaultControlShapeDpForKind(
                            controlAppearanceKindForDataKey(state.selectedDataKey)
                        )
                    Text(
                        text = stringResource(R.string.widget_control_shape, controlShapeDisplay),
                        style = MaterialTheme.typography.tboxTitle,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        text = stringResource(R.string.widget_control_shape_hint),
                        style = MaterialTheme.typography.tboxCaption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = controlShapeDisplay.toFloat(),
                        onValueChange = { newValue ->
                            state.controlShape = normalizeWidgetControlShape(newValue.toInt())
                        },
                        valueRange = 0f..50f,
                        steps = 49,
                        enabled = state.togglesEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    )
                }
                }

                else -> {
                    var dataKeyFilterText by rememberSaveable { mutableStateOf("") }
                    val initialListSelectedKey = rememberSaveable {
                        state.selectedDataKey
                    }
                    val needle = dataKeyFilterText.trim().lowercase()
                    fun optionMatches(pair: Pair<String, String>): Boolean {
                        if (needle.isEmpty()) return true
                        val description = WidgetsRepository
                            .getDescriptionResForDataKey(pair.first)
                            ?.let(context::getString)
                            .orEmpty()
                        val actions = WidgetsRepository
                            .getActionsDescriptionResForDataKey(pair.first)
                            ?.let(context::getString)
                            .orEmpty()
                        return pair.second.lowercase().contains(needle) ||
                            pair.first.lowercase().contains(needle) ||
                            description.lowercase().contains(needle) ||
                            actions.lowercase().contains(needle)
                    }
                    val selectedPair = widgetPairs.find { it.first == initialListSelectedKey }
                    val filteredTileOptions = buildList {
                        add("" to notSelectedLabel)
                        if (initialListSelectedKey.isNotEmpty() && selectedPair != null) {
                            add(selectedPair)
                        }
                        addAll(
                            widgetPairs
                                .filter { it.first != initialListSelectedKey }
                                .filter { optionMatches(it) }
                                .sortedBy { it.second }
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(androidx.compose.foundation.rememberScrollState())
                            .padding(12.dp)
                    ) {
                    OutlinedTextField(
                        value = dataKeyFilterText,
                        onValueChange = { dataKeyFilterText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        textStyle = MaterialTheme.typography.tboxTitle,
                        label = {
                            Text(
                                text = stringResource(R.string.widget_app_launcher_search),
                                style = MaterialTheme.typography.tboxBody
                            )
                        },
                        singleLine = true,
                    )
                    filteredTileOptions.forEach { (key, displayName) ->
                        key(key) {
                            val selectKey = rememberWrappedOnClick { state.applySelectedDataKey(key) }
                            val selected = state.selectedDataKey == key
                            val descriptionResources = resolveWidgetSelectionDescriptionResources(
                                dataKey = key,
                                selectedDataKey = state.selectedDataKey,
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickableWithSound {
                                        state.applySelectedDataKey(key)
                                    }
                                    .padding(vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selected,
                                        onClick = selectKey
                                    )
                                    Text(
                                        text = displayName,
                                        style = MaterialTheme.typography.tboxTitle,
                                        modifier = Modifier
                                            .padding(start = 8.dp)
                                            .weight(1f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (descriptionResources != null) {
                                    Text(
                                        text = stringResource(descriptionResources.descriptionRes),
                                        style = MaterialTheme.typography.tboxBody,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 56.dp, end = 8.dp)
                                    )
                                    val actionsRes = descriptionResources.actionsRes
                                    if (actionsRes != null) {
                                        Text(
                                            text = stringResource(
                                                R.string.widget_actions_template,
                                                stringResource(actionsRes)
                                            ),
                                            style = MaterialTheme.typography.tboxCaption,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(
                                                start = 56.dp,
                                                top = 4.dp,
                                                end = 8.dp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                    }
                }
            }
        }
        bottomContent?.invoke()
    }
}

@Composable
internal fun WidgetDialogClipboardActionsRow(
    state: WidgetSelectionDialogState,
    currentWidgetConfigs: List<FloatingDashboardWidgetConfig>,
    widgetIndex: Int,
    modifier: Modifier = Modifier,
) {
    val isWholePanelMode = state.showWholePanelSettings
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isWholePanelMode) {
            OutlinedButton(
                onClick = rememberWrappedOnClick {
                    WidgetDialogClipboard.copyPanel(
                        state.toWholePanelClipboardSnapshot(
                            currentWidgetConfigs = currentWidgetConfigs,
                            widgetIndex = widgetIndex,
                        )
                    )
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(R.string.action_copy),
                    style = MaterialTheme.typography.tboxButton,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OutlinedButton(
                onClick = rememberWrappedOnClick {
                    WidgetDialogClipboard.panelSnapshot?.let {
                        state.applyWholePanelFromClipboard(it, widgetIndex = widgetIndex)
                    }
                },
                enabled = WidgetDialogClipboard.hasPanel,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(R.string.action_paste),
                    style = MaterialTheme.typography.tboxButton,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            OutlinedButton(
                onClick = rememberWrappedOnClick {
                    WidgetDialogClipboard.copyTile(state.toTileClipboardSnapshot())
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(R.string.action_copy),
                    style = MaterialTheme.typography.tboxButton,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OutlinedButton(
                onClick = rememberWrappedOnClick {
                    WidgetDialogClipboard.tileSnapshot?.let {
                        state.applyTileClipboardSnapshot(it, preserveDataKey = false)
                    }
                },
                enabled = WidgetDialogClipboard.hasTile,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(R.string.action_paste),
                    style = MaterialTheme.typography.tboxButton,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OutlinedButton(
                onClick = rememberWrappedOnClick {
                    WidgetDialogClipboard.tileSnapshot?.let {
                        state.applyTileClipboardSnapshot(it, preserveDataKey = true)
                    }
                },
                enabled = WidgetDialogClipboard.hasTile && state.selectedDataKey.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(R.string.widget_paste_without_type),
                    style = MaterialTheme.typography.tboxButton,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun WidgetSelectionDialogActions(
    state: WidgetSelectionDialogState,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    showWholePanelButton: Boolean = false,
    deleteAfterWholePanel: (@Composable RowScope.() -> Unit)? = null,
    /** Load whole-panel draft from persisted config once when user opens «Вся панель». */
    onWholePanelSectionOpened: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = rememberWrappedOnClick {
                    val next = !state.showAdvancedSettings
                    state.showAdvancedSettings = next
                    if (next) {
                        state.showWholePanelSettings = false
                    }
                },
                modifier = Modifier.weight(1f),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (state.showAdvancedSettings) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (state.showAdvancedSettings) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    } else {
                        Color.Transparent
                    },
                    contentColor = if (state.showAdvancedSettings) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            ) {
                Text(
                    text = stringResource(R.string.widget_toggle_advanced),
                    style = MaterialTheme.typography.tboxButton,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (showWholePanelButton) {
                OutlinedButton(
                    onClick = rememberWrappedOnClick {
                        val next = !state.showWholePanelSettings
                        state.showWholePanelSettings = next
                        if (next) {
                            state.showAdvancedSettings = false
                            if (!state.wholePanelDraftSeeded) {
                                onWholePanelSectionOpened?.invoke()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (state.showWholePanelSettings) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        }
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (state.showWholePanelSettings) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        } else {
                            Color.Transparent
                        },
                        contentColor = if (state.showWholePanelSettings) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                ) {
                    Text(
                        text = stringResource(R.string.widget_toggle_whole_panel),
                        style = MaterialTheme.typography.tboxButton,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            deleteAfterWholePanel?.invoke(this)
        }
        OutlinedButton(
            onClick = rememberWrappedOnClick(onDismiss),
            modifier = Modifier.padding(end = 12.dp)
        ) {
            Text(
                text = stringResource(R.string.action_cancel),
                style = MaterialTheme.typography.tboxButton,
            )
        }
        Button(
            enabled = state.canSaveSelection,
            onClick = rememberWrappedOnClick(onSave)
        ) {
            Text(
                text = stringResource(R.string.action_save),
                style = MaterialTheme.typography.tboxButton,
            )
        }
    }
}

internal fun applyWidgetSelectionChanges(
    context: Context,
    dashboardManager: DashboardManager,
    currentWidgets: List<DashboardWidget>,
    currentWidgetConfigs: List<FloatingDashboardWidgetConfig>,
    widgetIndex: Int,
    state: WidgetSelectionDialogState,
    saveConfigs: (List<FloatingDashboardWidgetConfig>) -> Unit,
    externalAppWidgetId: Int? = null
) {
    if (state.selectedDataKey == WidgetsRepository.EXTERNAL_WIDGET_DATA_KEY) {
        state.draftAppWidgetId = externalAppWidgetId
    }
    val draftConfig = if (state.selectedDataKey.isNotEmpty()) {
        state.toDraftWidgetConfig()
    } else {
        FloatingDashboardWidgetConfig(dataKey = "", customTitle = "")
    }
    val targetCount = if (state.wholePanelDraftSeeded) {
        (state.wholePanelRows * state.wholePanelCols).coerceAtLeast(1)
    } else {
        currentWidgets.size.coerceAtLeast(1)
    }
    val baseConfigs = state.wholePanelWidgetsDraft ?: currentWidgetConfigs
    val normalizedConfigs = normalizeWidgetConfigs(baseConfigs, targetCount).toMutableList()
    val prevAppWidgetIds = normalizedConfigs.mapNotNull { it.appWidgetId }.toSet() +
        currentWidgetConfigs.mapNotNull { it.appWidgetId }
    if (widgetIndex in normalizedConfigs.indices) {
        normalizedConfigs[widgetIndex] = draftConfig
    }

    val updatedWidgets = loadWidgetsFromConfig(
        configs = normalizedConfigs,
        widgetCount = targetCount,
        context = context,
    )
    dashboardManager.updateWidgets(updatedWidgets)

    val newAppWidgetIds = normalizedConfigs.mapNotNull { cfg ->
        cfg.appWidgetId.takeIf { cfg.dataKey == WidgetsRepository.EXTERNAL_WIDGET_DATA_KEY }
    }.toSet()
    for (prevId in prevAppWidgetIds) {
        if (prevId !in newAppWidgetIds) {
            ExternalWidgetHostManager.deleteAppWidgetId(context, prevId)
        }
    }
    saveConfigs(normalizedConfigs)
    if (widgetIndex in updatedWidgets.indices) {
        dashboardManager.clearWidgetHistory(updatedWidgets[widgetIndex].id)
    }
}

internal fun mainScreenWholePanelSavePayloadIfSeeded(
    state: WidgetSelectionDialogState
): MainScreenWholePanelFieldsForWidgetDialogSave? {
    if (!state.wholePanelDraftSeeded) return null
    return MainScreenWholePanelFieldsForWidgetDialogSave(
        name = state.wholePanelNameDraft.trim(),
        rows = state.wholePanelRows,
        cols = state.wholePanelCols,
        showTboxDisconnectIndicator = state.wholePanelShowTboxDisconnect,
        clickAction = state.wholePanelClickAction,
        pageNumber = state.wholePanelPageNumber,
        gridSpacingDp = normalizePanelGridSpacingDp(state.wholePanelGridSpacingDp),
        collapseEdge = PanelCollapseEdge.fromStorage(state.wholePanelCollapseEdge).storageValue,
        collapseStripThicknessDp = normalizePanelCollapseStripThicknessDp(
            state.wholePanelCollapseStripThicknessDp,
        ),
        collapseStripColorLight = state.wholePanelCollapseStripColorLight,
        collapseStripColorDark = state.wholePanelCollapseStripColorDark,
        collapseStripExpandedColorLight = state.wholePanelCollapseStripExpandedColorLight,
        collapseStripExpandedColorDark = state.wholePanelCollapseStripExpandedColorDark,
        collapseOnTileTap = state.wholePanelCollapseOnTileTap,
        collapseOnTileTapDelaySec = normalizePanelCollapseOnTileTapDelaySec(
            state.wholePanelCollapseOnTileTapDelaySec,
        ),
        panelBackgroundColorLight = state.wholePanelBackgroundColorLight,
        panelBackgroundColorDark = state.wholePanelBackgroundColorDark,
        panelBackgroundImageRelPathLight = state.wholePanelBackgroundImageRelPathLight,
        panelBackgroundImageRelPathDark = state.wholePanelBackgroundImageRelPathDark,
        panelShape = normalizePanelShape(state.wholePanelShape),
    )
}

internal fun floatingWholePanelSavePayloadIfSeeded(
    state: WidgetSelectionDialogState
): FloatingWholePanelFieldsForWidgetDialogSave? {
    if (!state.wholePanelDraftSeeded) return null
    return FloatingWholePanelFieldsForWidgetDialogSave(
        name = state.wholePanelNameDraft.trim(),
        rows = state.wholePanelRows,
        cols = state.wholePanelCols,
        showTboxDisconnectIndicator = state.wholePanelShowTboxDisconnect,
        clickAction = state.wholePanelClickAction,
        gridSpacingDp = normalizePanelGridSpacingDp(state.wholePanelGridSpacingDp),
        collapseEdge = PanelCollapseEdge.fromStorage(state.wholePanelCollapseEdge).storageValue,
        collapseStripThicknessDp = normalizePanelCollapseStripThicknessDp(
            state.wholePanelCollapseStripThicknessDp,
        ),
        collapseStripColorLight = state.wholePanelCollapseStripColorLight,
        collapseStripColorDark = state.wholePanelCollapseStripColorDark,
        collapseStripExpandedColorLight = state.wholePanelCollapseStripExpandedColorLight,
        collapseStripExpandedColorDark = state.wholePanelCollapseStripExpandedColorDark,
        collapseOnTileTap = state.wholePanelCollapseOnTileTap,
        collapseOnTileTapDelaySec = normalizePanelCollapseOnTileTapDelaySec(
            state.wholePanelCollapseOnTileTapDelaySec,
        ),
        panelBackgroundColorLight = state.wholePanelBackgroundColorLight,
        panelBackgroundColorDark = state.wholePanelBackgroundColorDark,
        panelBackgroundImageRelPathLight = state.wholePanelBackgroundImageRelPathLight,
        panelBackgroundImageRelPathDark = state.wholePanelBackgroundImageRelPathDark,
        panelShape = normalizePanelShape(state.wholePanelShape),
    )
}

internal fun externalAppWidgetIdForApply(
    state: WidgetSelectionDialogState,
    currentWidgetConfigs: List<FloatingDashboardWidgetConfig>,
    widgetIndex: Int
): Int? {
    if (state.selectedDataKey != WidgetsRepository.EXTERNAL_WIDGET_DATA_KEY) return null
    return state.draftAppWidgetId
        ?: currentWidgetConfigs.getOrNull(widgetIndex)
            ?.takeIf { it.dataKey == WidgetsRepository.EXTERNAL_WIDGET_DATA_KEY }
            ?.appWidgetId
}

internal fun tryLaunchExternalWidgetPicker(
    context: Context,
    saveTarget: Int,
    panelId: String,
    widgetIndex: Int,
    state: WidgetSelectionDialogState,
    currentWidgetConfigs: List<FloatingDashboardWidgetConfig>,
    onDismiss: () -> Unit
): Boolean {
    if (state.selectedDataKey != WidgetsRepository.EXTERNAL_WIDGET_DATA_KEY) return false
    val id = externalAppWidgetIdForApply(state, currentWidgetConfigs, widgetIndex)
    if (id != null) return false
    WidgetPickerActivity.start(
        context = context,
        saveTarget = saveTarget,
        panelId = panelId,
        widgetIndex = widgetIndex,
        showTitle = state.showTitle,
        showUnit = state.showUnit
    )
    onDismiss()
    return true
}

internal fun resolveStoredMediaSelectedPlayer(
    selectedPlayers: Set<String>,
    currentSelectedPlayer: String
): String {
    val orderedPlayers = orderedMediaPlayersForStorage(selectedPlayers)
    if (orderedPlayers.isEmpty()) return ""
    return if (currentSelectedPlayer in orderedPlayers) {
        currentSelectedPlayer
    } else {
        orderedPlayers.first()
    }
}
