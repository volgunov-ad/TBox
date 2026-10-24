package vad.dashing.tbox.ui

import vad.dashing.tbox.ui.theme.tboxCaption
import vad.dashing.tbox.ui.theme.tboxButton
import vad.dashing.tbox.ui.theme.tboxBody
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import android.appwidget.AppWidgetManager
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.Context
import vad.dashing.tbox.APP_LAUNCHER_WIDGET_DATA_KEY
import vad.dashing.tbox.AppLauncherLaunchMode
import vad.dashing.tbox.DEFAULT_HTTP_REQUEST_WIDGET_YAML
import vad.dashing.tbox.DEFAULT_WIDGET_TEXT_COLOR_DARK
import vad.dashing.tbox.freeform.FreeformLaunchBounds
import vad.dashing.tbox.freeform.FreeformLaunchSide
import vad.dashing.tbox.DEFAULT_WIDGET_TEXT_COLOR_LIGHT
import vad.dashing.tbox.DashboardManager
import vad.dashing.tbox.ACC_CRUISE_STEP_INTERVAL_MS_DEFAULT
import vad.dashing.tbox.ACC_CRUISE_TARGET_KMH_DEFAULT
import vad.dashing.tbox.CruiseControlType
import vad.dashing.tbox.isAccCruiseWidgetDataKey
import vad.dashing.tbox.isCruiseWidgetDataKey
import vad.dashing.tbox.normalizeAccCruiseStepIntervalMs
import vad.dashing.tbox.normalizeAccCruiseTargetKmh
import vad.dashing.tbox.DRIVE_MODE_WIDGET_DATA_KEY
import vad.dashing.tbox.DRIVE_MODE_WIDGET_DEFAULT_RAW_VALUE
import vad.dashing.tbox.DRIVE_MODE_CYCLE_WIDGET_DEFAULT_RAW_VALUES
import vad.dashing.tbox.normalizeDriveModeCycleSelection
import vad.dashing.tbox.isDriveModeCycleWidgetDataKey
import vad.dashing.tbox.FloatingDashboardConfig
import vad.dashing.tbox.MainScreenPanelConfig
import vad.dashing.tbox.DashboardWidget
import vad.dashing.tbox.FloatingDashboardWidgetConfig
import vad.dashing.tbox.isValidDateTimeWidgetFormat
import vad.dashing.tbox.isSeatHeatVentSingleWidgetDataKey
import vad.dashing.tbox.isActiveTripWidgetDataKey
import vad.dashing.tbox.isTripMetricWidgetDataKey
import vad.dashing.tbox.isObdMetricWidgetDataKey
import vad.dashing.tbox.obd.ObdPid
import vad.dashing.tbox.usesTripWidgetSource
import vad.dashing.tbox.normalizeTripWidgetSource
import vad.dashing.tbox.TRIP_WIDGET_SOURCE_CURRENT
import vad.dashing.tbox.isAverageFuelConsumptionWidgetDataKey
import vad.dashing.tbox.normalizeAvgFuelConsumptionSource
import vad.dashing.tbox.AVG_FUEL_CONSUMPTION_SOURCE_MBCAN_VHAL
import vad.dashing.tbox.isFuelLitersDisplayModeWidgetDataKey
import vad.dashing.tbox.normalizeFuelLitersDisplayMode
import vad.dashing.tbox.FUEL_LITERS_DISPLAY_REMAINING
import vad.dashing.tbox.isMusicWidgetDataKey
import vad.dashing.tbox.isOsmSpeedLimitWidgetDataKey
import vad.dashing.tbox.isRoadMatchMapWidgetDataKey
import vad.dashing.tbox.DEFAULT_MAPS_CAM_LOOKAHEAD_M
import vad.dashing.tbox.DEFAULT_MAPS_CAM_RADAR_HOLD_M
import vad.dashing.tbox.normalizeMapsCamLookaheadM
import vad.dashing.tbox.normalizeMapsCamRadarHoldM
import vad.dashing.tbox.speedcam.DEFAULT_SPEED_CAM_OVERAGE_KMH
import vad.dashing.tbox.speedcam.normalizeSpeedCamOverageKmh
import vad.dashing.tbox.MUSIC_COVER_WIDGET_DATA_KEY
import vad.dashing.tbox.MusicWidgetAlbumArtDisplay
import vad.dashing.tbox.MusicWidgetControlsDisplay
import vad.dashing.tbox.trip.ActiveTripCustomWidgetField
import vad.dashing.tbox.trip.TripMetricFormatter
import vad.dashing.tbox.supportsMusicAlbumArtLayoutSettings
import vad.dashing.tbox.supportsMusicAlbumArtToggle
import vad.dashing.tbox.supportsMusicControlsHeightSetting
import vad.dashing.tbox.supportsMusicPlayerHeaderIconSetting
import vad.dashing.tbox.normalizeDateTimeWidgetFormat
import vad.dashing.tbox.R
import vad.dashing.tbox.sanitizeDateTimeWidgetFormat
import vad.dashing.tbox.ExternalWidgetHostManager
import vad.dashing.tbox.AUTOMATION_TRIGGER_WIDGET_DATA_KEY
import vad.dashing.tbox.HTTP_REQUEST_WIDGET_DATA_KEY
import vad.dashing.tbox.normalizeAutomationTriggerId
import vad.dashing.tbox.normalizeWidgetButtonBinding
import vad.dashing.tbox.supportsWidgetButtonBinding
import vad.dashing.tbox.WidgetPickerActivity
import vad.dashing.tbox.FloatingWholePanelFieldsForWidgetDialogSave
import vad.dashing.tbox.MainScreenWholePanelFieldsForWidgetDialogSave
import vad.dashing.tbox.TileBackgroundImageStorage
import vad.dashing.tbox.WidgetsRepository
import vad.dashing.tbox.normalizeWidgetConfigs
import vad.dashing.tbox.loadWidgetsFromConfig
import vad.dashing.tbox.normalizeWidgetShape
import vad.dashing.tbox.normalizePanelShape
import vad.dashing.tbox.DEFAULT_PANEL_SHAPE
import vad.dashing.tbox.normalizeWidgetControlPadding
import vad.dashing.tbox.normalizeWidgetControlShape
import vad.dashing.tbox.usesDefaultControlColors
import vad.dashing.tbox.trip.TripWidgetTileDisplay
import vad.dashing.tbox.normalizeWidgetScale
import vad.dashing.tbox.normalizeDriveModeWidgetRawValue
import vad.dashing.tbox.normalizeWidgetTextAlign
import vad.dashing.tbox.normalizeWidgetFontWeight
import vad.dashing.tbox.normalizeWidgetTitlePosition
import vad.dashing.tbox.normalizeStepperAdjustIconStyle
import vad.dashing.tbox.HVAC_TEMP_WIDGET_STEP_TENTHS_DEFAULT
import vad.dashing.tbox.normalizeHvacTempWidgetStepTenths
import vad.dashing.tbox.isHvacTempWidgetDataKey
import vad.dashing.tbox.STEPPER_ADJUST_ICON_PLUS_MINUS
import vad.dashing.tbox.EspRelayWidgetMode
import vad.dashing.tbox.isEspRelayWidgetDataKey
import vad.dashing.tbox.normalizePanelGridSpacingDp
import vad.dashing.tbox.DEFAULT_PANEL_COLLAPSE_ON_STRIP_TAP
import vad.dashing.tbox.DEFAULT_PANEL_COLLAPSE_ON_STRIP_DOUBLE_TAP
import vad.dashing.tbox.DEFAULT_PANEL_COLLAPSE_ON_TILE_TAP
import vad.dashing.tbox.DEFAULT_PANEL_COLLAPSE_ON_TILE_TAP_DELAY_SEC
import vad.dashing.tbox.DEFAULT_PANEL_COLLAPSE_STRIP_COLOR_DARK
import vad.dashing.tbox.DEFAULT_PANEL_COLLAPSE_STRIP_COLOR_LIGHT
import vad.dashing.tbox.DEFAULT_PANEL_COLLAPSE_STRIP_EXPANDED_COLOR_DARK
import vad.dashing.tbox.DEFAULT_PANEL_COLLAPSE_STRIP_EXPANDED_COLOR_LIGHT
import vad.dashing.tbox.DEFAULT_PANEL_COLLAPSE_STRIP_LENGTH_PERCENT
import vad.dashing.tbox.DEFAULT_PANEL_COLLAPSE_STRIP_POSITION_PERCENT
import vad.dashing.tbox.DEFAULT_PANEL_COLLAPSE_STRIP_THICKNESS_DP
import vad.dashing.tbox.DEFAULT_PANEL_COLLAPSE_TOUCH_ZONE_MATCH_STRIP
import vad.dashing.tbox.PanelCollapseEdge
import vad.dashing.tbox.normalizePanelCollapseOnTileTapDelaySec
import vad.dashing.tbox.normalizePanelCollapseStripLengthPercent
import vad.dashing.tbox.normalizePanelCollapseStripPositionPercent
import vad.dashing.tbox.normalizePanelCollapseStripThicknessDp
import vad.dashing.tbox.normalizePanelCollapseTouchZoneThicknessDp
import vad.dashing.tbox.normalizeWidgetPaddingPercent
import vad.dashing.tbox.DEFAULT_PANEL_GRID_SPACING_DP
import vad.dashing.tbox.resolveDefaultTitlePositionForDataKey
import vad.dashing.tbox.parseHttpRequestWidgetYaml
import vad.dashing.tbox.resolveSelectedMediaPlayerForWidget

/** Width of value dropdowns in the tile / panel settings dialog. */
val WidgetDialogDropdownSelectorWidth = 300.dp
/** Wider selector for long OBD PID labels (+ discovery hints) in Additional. */
val WidgetDialogObdPidDropdownSelectorWidth = 480.dp

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

internal data class HvacTempStepDropdownEntry(
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

internal data class ObdPidDropdownEntry(
    val pidId: String,
    val display: String,
) {
    override fun toString(): String = display
}

data class TripMetricFieldDropdownEntry(
    val fieldId: String,
    val display: String,
) {
    override fun toString(): String = display
}

internal data class AvgFuelConsumptionSourceDropdownEntry(
    val source: Int,
    val display: String,
) {
    override fun toString(): String = display
}

internal data class FuelLitersDisplayModeDropdownEntry(
    val mode: Int,
    val display: String,
) {
    override fun toString(): String = display
}

internal data class MusicAlbumArtSideDropdownEntry(
    val side: Int,
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

internal data class RoadMatchBasemapTransparencyDropdownEntry(
    val percent: Int,
    val label: String,
) {
    override fun toString(): String = label
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
    val panelDefaultBackgroundLight: Int,
    val panelDefaultBackgroundDark: Int,
    initialColorThemeSegment: Int = 0,
    initialEditingIndex: Int = 0,
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
    var titleScale by mutableFloatStateOf(normalizeWidgetScale(initialConfig.titleScale))
    var iconScale by mutableFloatStateOf(normalizeWidgetScale(initialConfig.iconScale))
    var textScale by mutableFloatStateOf(normalizeWidgetScale(initialConfig.textScale))
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
    var mediaFollowPlayback by mutableStateOf(
        isMusicWidgetDataKey(initialConfig.dataKey) && initialConfig.mediaFollowPlayback
    )
    var roadMatchHeadingUp by mutableStateOf(
        isRoadMatchMapWidgetDataKey(initialConfig.dataKey) && initialConfig.roadMatchHeadingUp
    )
    var roadMatchMapKitBasemap by mutableStateOf(
        isRoadMatchMapWidgetDataKey(initialConfig.dataKey) && initialConfig.roadMatchMapKitBasemap
    )
    var roadMatchBasemapTransparencyPercent by mutableIntStateOf(
        if (isRoadMatchMapWidgetDataKey(initialConfig.dataKey)) {
            vad.dashing.tbox.location.roadmatch.RoadMatchBasemapOpacity.normalize(
                initialConfig.roadMatchBasemapTransparencyPercent,
            )
        } else {
            0
        },
    )
    var speedCamOverageKmh by mutableIntStateOf(
        if (isOsmSpeedLimitWidgetDataKey(initialConfig.dataKey)) {
            normalizeSpeedCamOverageKmh(initialConfig.speedCamOverageKmh)
        } else {
            DEFAULT_SPEED_CAM_OVERAGE_KMH
        },
    )
    var mapsCamLookaheadDistanceM by mutableIntStateOf(
        if (isOsmSpeedLimitWidgetDataKey(initialConfig.dataKey)) {
            normalizeMapsCamLookaheadM(initialConfig.mapsCamLookaheadDistanceM)
        } else {
            DEFAULT_MAPS_CAM_LOOKAHEAD_M
        },
    )
    var mapsCamRadarHoldDistanceM by mutableIntStateOf(
        if (isOsmSpeedLimitWidgetDataKey(initialConfig.dataKey)) {
            normalizeMapsCamRadarHoldM(initialConfig.mapsCamRadarHoldDistanceM)
        } else {
            DEFAULT_MAPS_CAM_RADAR_HOLD_M
        },
    )
    var speedCamShowOnMap by mutableStateOf(
        isRoadMatchMapWidgetDataKey(initialConfig.dataKey) && initialConfig.speedCamShowOnMap,
    )
    var mapsCamShowCameras by mutableStateOf(
        !isOsmSpeedLimitWidgetDataKey(initialConfig.dataKey) || initialConfig.mapsCamShowCameras,
    )
    var mapsCamShowCurrentLimit by mutableStateOf(
        !isOsmSpeedLimitWidgetDataKey(initialConfig.dataKey) || initialConfig.mapsCamShowCurrentLimit,
    )
    var mapsCamShowAheadLimit by mutableStateOf(
        !isOsmSpeedLimitWidgetDataKey(initialConfig.dataKey) || initialConfig.mapsCamShowAheadLimit,
    )
    var mediaShowLikeButton by mutableStateOf(
        isMusicWidgetDataKey(initialConfig.dataKey) && initialConfig.mediaShowLikeButton
    )
    var mediaShowAlbumArt by mutableStateOf(
        supportsMusicAlbumArtToggle(initialConfig.dataKey) && initialConfig.mediaShowAlbumArt
    )
    var mediaAlbumArtColumnWidthPercent by mutableIntStateOf(
        MusicWidgetAlbumArtDisplay.normalizeAlbumArtColumnWidthPercent(
            initialConfig.mediaAlbumArtColumnWidthPercent,
        )
    )
    var mediaAlbumArtSide by mutableIntStateOf(
        MusicWidgetAlbumArtDisplay.normalizeAlbumArtSide(initialConfig.mediaAlbumArtSide)
    )
    var mediaShowPlayerHeaderIcon by mutableStateOf(
        if (supportsMusicPlayerHeaderIconSetting(initialConfig.dataKey)) {
            initialConfig.mediaShowPlayerHeaderIcon
        } else {
            true
        }
    )
    var mediaShowTrackInfo by mutableStateOf(
        initialConfig.dataKey != MUSIC_COVER_WIDGET_DATA_KEY || initialConfig.mediaShowTrackInfo
    )
    var mediaControlsHeightPercent by mutableIntStateOf(
        MusicWidgetControlsDisplay.resolveControlsHeightPercent(
            initialConfig.dataKey,
            initialConfig.mediaControlsHeightPercent,
        )
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
    var hvacTempStepTenths by mutableIntStateOf(
        if (isHvacTempWidgetDataKey(initialConfig.dataKey)) {
            normalizeHvacTempWidgetStepTenths(initialConfig.hvacTempStepTenths)
        } else {
            HVAC_TEMP_WIDGET_STEP_TENTHS_DEFAULT
        }
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

    /** Grid position of the tile currently loaded into this state. */
    var editingIndex by mutableIntStateOf(initialEditingIndex.coerceAtLeast(0))
        private set
    var level by mutableStateOf(WidgetEditorLevel.TILE)
    var tileTab by mutableStateOf(TileEditorTab.TYPE)
    var panelTab by mutableStateOf(PanelEditorTab.GENERAL)
    /** Theme shown in the preview and preselected in the color table. */
    var previewDark by mutableStateOf(initialColorThemeSegment == 1)
    /** Tiles selected on the «Сетка» tab for «Применить вид». */
    var gridSelection by mutableStateOf<Set<Int>>(emptySet())
    /** True after another tile was edited, moved or restyled in this session. */
    var otherTilesDirty by mutableStateOf(false)
        private set

    val showWholePanelSettings: Boolean
        get() = level == WidgetEditorLevel.PANEL
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
    var wholePanelCollapseTouchZoneThicknessDp by mutableIntStateOf(DEFAULT_PANEL_COLLAPSE_STRIP_THICKNESS_DP)
    var wholePanelCollapseStripLengthPercentCollapsed by mutableIntStateOf(
        DEFAULT_PANEL_COLLAPSE_STRIP_LENGTH_PERCENT,
    )
    var wholePanelCollapseStripLengthPercentExpanded by mutableIntStateOf(
        DEFAULT_PANEL_COLLAPSE_STRIP_LENGTH_PERCENT,
    )
    var wholePanelCollapseStripPositionPercentCollapsed by mutableIntStateOf(
        DEFAULT_PANEL_COLLAPSE_STRIP_POSITION_PERCENT,
    )
    var wholePanelCollapseStripPositionPercentExpanded by mutableIntStateOf(
        DEFAULT_PANEL_COLLAPSE_STRIP_POSITION_PERCENT,
    )
    var wholePanelCollapseTouchZoneMatchStrip by mutableStateOf(
        DEFAULT_PANEL_COLLAPSE_TOUCH_ZONE_MATCH_STRIP,
    )
    var wholePanelCollapseStripColorLight by mutableIntStateOf(DEFAULT_PANEL_COLLAPSE_STRIP_COLOR_LIGHT)
    var wholePanelCollapseStripColorDark by mutableIntStateOf(DEFAULT_PANEL_COLLAPSE_STRIP_COLOR_DARK)
    var wholePanelCollapseStripExpandedColorLight by mutableIntStateOf(
        DEFAULT_PANEL_COLLAPSE_STRIP_EXPANDED_COLOR_LIGHT,
    )
    var wholePanelCollapseStripExpandedColorDark by mutableIntStateOf(
        DEFAULT_PANEL_COLLAPSE_STRIP_EXPANDED_COLOR_DARK,
    )
    var wholePanelCollapseOnStripTap by mutableStateOf(DEFAULT_PANEL_COLLAPSE_ON_STRIP_TAP)
    var wholePanelCollapseOnStripDoubleTap by mutableStateOf(DEFAULT_PANEL_COLLAPSE_ON_STRIP_DOUBLE_TAP)
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
        wholePanelCollapseTouchZoneThicknessDp = cfg.collapseTouchZoneThicknessDp
        wholePanelCollapseStripLengthPercentCollapsed = cfg.collapseStripLengthPercentCollapsed
        wholePanelCollapseStripLengthPercentExpanded = cfg.collapseStripLengthPercentExpanded
        wholePanelCollapseStripPositionPercentCollapsed = cfg.collapseStripPositionPercentCollapsed
        wholePanelCollapseStripPositionPercentExpanded = cfg.collapseStripPositionPercentExpanded
        wholePanelCollapseTouchZoneMatchStrip = cfg.collapseTouchZoneMatchStrip
        wholePanelCollapseStripColorLight = cfg.collapseStripColorLight
        wholePanelCollapseStripColorDark = cfg.collapseStripColorDark
        wholePanelCollapseStripExpandedColorLight = cfg.collapseStripExpandedColorLight
        wholePanelCollapseStripExpandedColorDark = cfg.collapseStripExpandedColorDark
        wholePanelCollapseOnStripTap = cfg.collapseOnStripTap
        wholePanelCollapseOnStripDoubleTap = cfg.collapseOnStripDoubleTap
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
        wholePanelCollapseTouchZoneThicknessDp = cfg.collapseTouchZoneThicknessDp
        wholePanelCollapseStripLengthPercentCollapsed = cfg.collapseStripLengthPercentCollapsed
        wholePanelCollapseStripLengthPercentExpanded = cfg.collapseStripLengthPercentExpanded
        wholePanelCollapseStripPositionPercentCollapsed = cfg.collapseStripPositionPercentCollapsed
        wholePanelCollapseStripPositionPercentExpanded = cfg.collapseStripPositionPercentExpanded
        wholePanelCollapseTouchZoneMatchStrip = cfg.collapseTouchZoneMatchStrip
        wholePanelCollapseStripColorLight = cfg.collapseStripColorLight
        wholePanelCollapseStripColorDark = cfg.collapseStripColorDark
        wholePanelCollapseStripExpandedColorLight = cfg.collapseStripExpandedColorLight
        wholePanelCollapseStripExpandedColorDark = cfg.collapseStripExpandedColorDark
        wholePanelCollapseOnStripTap = cfg.collapseOnStripTap
        wholePanelCollapseOnStripDoubleTap = cfg.collapseOnStripDoubleTap
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
    var launcherLaunchMode by mutableStateOf(
        if (initialConfig.dataKey == APP_LAUNCHER_WIDGET_DATA_KEY) {
            AppLauncherLaunchMode.fromStored(
                initialConfig.launcherLaunchMode.storageKey,
                initialConfig.launcherFreeformEnabled,
            )
        } else {
            AppLauncherLaunchMode.DEFAULT
        }
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
    var launcherFreeformOverlayPage by mutableStateOf(
        if (initialConfig.dataKey == APP_LAUNCHER_WIDGET_DATA_KEY) {
            initialConfig.launcherFreeformOverlayPage
        } else {
            null
        }
    )
    var launcherFreeformOverlayCrop by mutableStateOf(
        initialConfig.dataKey == APP_LAUNCHER_WIDGET_DATA_KEY &&
            initialConfig.launcherFreeformOverlayCrop,
    )
    var launcherVirtualDisplayId by mutableStateOf(
        if (initialConfig.dataKey == APP_LAUNCHER_WIDGET_DATA_KEY) {
            initialConfig.launcherVirtualDisplayId
        } else {
            null
        },
    )
    var launcherVirtualDisplayWidthPx by mutableStateOf(
        if (initialConfig.dataKey == APP_LAUNCHER_WIDGET_DATA_KEY) {
            initialConfig.launcherVirtualDisplayWidthPx
        } else {
            null
        },
    )
    var launcherVirtualDisplayHeightPx by mutableStateOf(
        if (initialConfig.dataKey == APP_LAUNCHER_WIDGET_DATA_KEY) {
            initialConfig.launcherVirtualDisplayHeightPx
        } else {
            null
        },
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
    var automationTriggerId by mutableStateOf(
        if (initialConfig.dataKey == AUTOMATION_TRIGGER_WIDGET_DATA_KEY) {
            normalizeAutomationTriggerId(initialConfig.automationTriggerId)
        } else {
            ""
        }
    )
    var buttonBinding by mutableStateOf(
        if (supportsWidgetButtonBinding(initialConfig.dataKey)) {
            normalizeWidgetButtonBinding(initialConfig.buttonBinding)
        } else {
            null
        },
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
    var tripMetricFieldId by mutableStateOf(
        TripMetricFormatter.normalizeFieldId(initialConfig.tripMetricFieldId),
    )
    var obdPidId by mutableStateOf(
        ObdPid.normalizeId(initialConfig.obdPidId),
    )
    var avgFuelConsumptionSource by mutableIntStateOf(
        if (isAverageFuelConsumptionWidgetDataKey(initialConfig.dataKey)) {
            normalizeAvgFuelConsumptionSource(initialConfig.avgFuelConsumptionSource)
        } else {
            AVG_FUEL_CONSUMPTION_SOURCE_MBCAN_VHAL
        },
    )
    var fuelLitersDisplayMode by mutableIntStateOf(
        if (isFuelLitersDisplayModeWidgetDataKey(initialConfig.dataKey)) {
            normalizeFuelLitersDisplayMode(initialConfig.fuelLitersDisplayMode)
        } else {
            FUEL_LITERS_DISPLAY_REMAINING
        },
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
    /** `null` = class default outer padding; otherwise explicit 0..50. */
    var controlPadding by mutableStateOf(initialConfig.controlPadding)

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

    fun controlColor(active: Boolean, background: Boolean, dark: Boolean): Int = when {
        !background && !active && !dark -> controlInactiveColorLight
        !background && !active && dark -> controlInactiveColorDark
        !background && active && !dark -> controlActiveColorLight
        !background && active && dark -> controlActiveColorDark
        background && !active && !dark -> controlInactiveBackgroundColorLight
        background && !active && dark -> controlInactiveBackgroundColorDark
        background && active && !dark -> controlActiveBackgroundColorLight
        else -> controlActiveBackgroundColorDark
    }

    fun setControlColor(active: Boolean, background: Boolean, dark: Boolean, color: Int) {
        controlColorsUseDefaults = false
        when {
            !background && !active && !dark -> controlInactiveColorLight = color
            !background && !active && dark -> controlInactiveColorDark = color
            !background && active && !dark -> controlActiveColorLight = color
            !background && active && dark -> controlActiveColorDark = color
            background && !active && !dark -> controlInactiveBackgroundColorLight = color
            background && !active && dark -> controlInactiveBackgroundColorDark = color
            background && active && !dark -> controlActiveBackgroundColorLight = color
            else -> controlActiveBackgroundColorDark = color
        }
    }

    /** Copies the day (`toDark = true`) or night text/background colors to the other theme. */
    fun copyTileColorsBetweenThemes(toDark: Boolean) {
        if (toDark) {
            textColorDark = textColorLight
            backgroundColorDark = backgroundColorLight
        } else {
            textColorLight = textColorDark
            backgroundColorLight = backgroundColorDark
        }
    }

    fun applySelectedDataKey(key: String) {
        val previousKey = selectedDataKey
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
        if (!WidgetsRepository.supportsHvacTempStep(key)) {
            hvacTempStepTenths = HVAC_TEMP_WIDGET_STEP_TENTHS_DEFAULT
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
        if (!isAverageFuelConsumptionWidgetDataKey(key)) {
            avgFuelConsumptionSource = AVG_FUEL_CONSUMPTION_SOURCE_MBCAN_VHAL
        }
        if (!isFuelLitersDisplayModeWidgetDataKey(key)) {
            fuelLitersDisplayMode = FUEL_LITERS_DISPLAY_REMAINING
        }
        if (!isTripMetricWidgetDataKey(key)) {
            tripMetricFieldId = ActiveTripCustomWidgetField.DISTANCE.id
        }
        if (!isObdMetricWidgetDataKey(key)) {
            obdPidId = ObdPid.RPM.id
        }
        if (!usesTripWidgetSource(key)) {
            tripWidgetSource = TRIP_WIDGET_SOURCE_CURRENT
        }
        if (!WidgetsRepository.supportsDateTimeFormat(key)) {
            dateTimeFormat = ""
        }
        if (!supportsWidgetButtonBinding(key)) {
            buttonBinding = null
        }
        if (key != DRIVE_MODE_WIDGET_DATA_KEY) {
            selectedDriveMode = DRIVE_MODE_WIDGET_DEFAULT_RAW_VALUE
        }
        if (!isDriveModeCycleWidgetDataKey(key)) {
            selectedDriveModes = DRIVE_MODE_CYCLE_WIDGET_DEFAULT_RAW_VALUES
        }
        if (!isRoadMatchMapWidgetDataKey(key)) {
            roadMatchHeadingUp = false
            roadMatchMapKitBasemap = false
            roadMatchBasemapTransparencyPercent = 0
            speedCamShowOnMap = false
        }
        if (!isOsmSpeedLimitWidgetDataKey(key)) {
            speedCamOverageKmh = DEFAULT_SPEED_CAM_OVERAGE_KMH
            mapsCamLookaheadDistanceM = DEFAULT_MAPS_CAM_LOOKAHEAD_M
            mapsCamRadarHoldDistanceM = DEFAULT_MAPS_CAM_RADAR_HOLD_M
            mapsCamShowCameras = true
            mapsCamShowCurrentLimit = true
            mapsCamShowAheadLimit = true
        }
        if (supportsMusicControlsHeightSetting(key)) {
            val previousDefault = if (supportsMusicControlsHeightSetting(previousKey)) {
                MusicWidgetControlsDisplay.defaultControlsHeightPercent(previousKey)
            } else {
                null
            }
            if (previousDefault == null || mediaControlsHeightPercent == previousDefault) {
                mediaControlsHeightPercent =
                    MusicWidgetControlsDisplay.defaultControlsHeightPercent(key)
            }
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

    val isAutomationTriggerWidgetSelected: Boolean
        get() = selectedDataKey == AUTOMATION_TRIGGER_WIDGET_DATA_KEY

    val isExternalAppWidgetSelected: Boolean
        get() = selectedDataKey == WidgetsRepository.EXTERNAL_WIDGET_DATA_KEY

    val togglesEnabled: Boolean
        get() = selectedDataKey.isNotEmpty()

    fun toDraftWidgetConfig(): FloatingDashboardWidgetConfig {
        val draft = buildDraftWidgetConfig()
        titleScale = draft.titleScale
        iconScale = draft.iconScale
        textScale = draft.textScale
        shape = draft.shape
        return draft
    }

    /** Same as [toDraftWidgetConfig] without writing normalized values back (safe in composition). */
    fun buildDraftWidgetConfig(): FloatingDashboardWidgetConfig {
        val normalizedTitleScale = normalizeWidgetScale(titleScale)
        val normalizedIconScale = normalizeWidgetScale(iconScale)
        val normalizedTextScale = normalizeWidgetScale(textScale)
        val normalizedShape = normalizeWidgetShape(shape)
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
            titleScale = normalizedTitleScale,
            iconScale = normalizedIconScale,
            textScale = normalizedTextScale,
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
            mediaFollowPlayback = if (isMusicWidgetDataKey(selectedDataKey)) {
                mediaFollowPlayback
            } else {
                false
            },
            mediaShowLikeButton = if (isMusicWidgetDataKey(selectedDataKey)) {
                mediaShowLikeButton
            } else {
                false
            },
            mediaShowAlbumArt = supportsMusicAlbumArtToggle(selectedDataKey) && mediaShowAlbumArt,
            mediaAlbumArtColumnWidthPercent = if (supportsMusicAlbumArtLayoutSettings(selectedDataKey)) {
                MusicWidgetAlbumArtDisplay.normalizeAlbumArtColumnWidthPercent(
                    mediaAlbumArtColumnWidthPercent,
                )
            } else {
                MusicWidgetAlbumArtDisplay.DEFAULT_ALBUM_ART_COLUMN_WIDTH_PERCENT
            },
            mediaAlbumArtSide = if (supportsMusicAlbumArtLayoutSettings(selectedDataKey)) {
                MusicWidgetAlbumArtDisplay.normalizeAlbumArtSide(mediaAlbumArtSide)
            } else {
                MusicWidgetAlbumArtDisplay.DEFAULT_ALBUM_ART_SIDE
            },
            mediaShowPlayerHeaderIcon = if (supportsMusicPlayerHeaderIconSetting(selectedDataKey)) {
                mediaShowPlayerHeaderIcon
            } else {
                true
            },
            mediaShowTrackInfo = if (selectedDataKey == MUSIC_COVER_WIDGET_DATA_KEY) {
                mediaShowTrackInfo
            } else {
                true
            },
            mediaControlsHeightPercent = if (supportsMusicControlsHeightSetting(selectedDataKey)) {
                MusicWidgetControlsDisplay.normalizeControlsHeightPercent(
                    mediaControlsHeightPercent,
                )
            } else {
                null
            },
            launcherAppPackage = if (selectedDataKey == APP_LAUNCHER_WIDGET_DATA_KEY) {
                launcherAppPackage.trim()
            } else {
                ""
            },
            launcherLaunchMode = if (selectedDataKey == APP_LAUNCHER_WIDGET_DATA_KEY) {
                launcherLaunchMode
            } else {
                AppLauncherLaunchMode.DEFAULT
            },
            launcherFreeformEnabled = selectedDataKey == APP_LAUNCHER_WIDGET_DATA_KEY &&
                launcherLaunchMode == AppLauncherLaunchMode.FREEFORM,
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
            launcherFreeformOverlayPage =
                if (selectedDataKey == APP_LAUNCHER_WIDGET_DATA_KEY &&
                    launcherLaunchMode == AppLauncherLaunchMode.FREEFORM
                ) {
                    launcherFreeformOverlayPage
                } else {
                    null
                },
            launcherFreeformOverlayCrop =
                selectedDataKey == APP_LAUNCHER_WIDGET_DATA_KEY &&
                    launcherLaunchMode == AppLauncherLaunchMode.FREEFORM &&
                    launcherFreeformOverlayCrop,
            launcherVirtualDisplayId =
                if (selectedDataKey == APP_LAUNCHER_WIDGET_DATA_KEY &&
                    launcherLaunchMode == AppLauncherLaunchMode.VIRTUAL_DISPLAY
                ) {
                    launcherVirtualDisplayId?.takeIf { it > 0 }
                } else {
                    null
                },
            launcherVirtualDisplayWidthPx =
                if (selectedDataKey == APP_LAUNCHER_WIDGET_DATA_KEY &&
                    launcherLaunchMode == AppLauncherLaunchMode.VIRTUAL_DISPLAY
                ) {
                    launcherVirtualDisplayWidthPx?.takeIf { it > 0 }
                } else {
                    null
                },
            launcherVirtualDisplayHeightPx =
                if (selectedDataKey == APP_LAUNCHER_WIDGET_DATA_KEY &&
                    launcherLaunchMode == AppLauncherLaunchMode.VIRTUAL_DISPLAY
                ) {
                    launcherVirtualDisplayHeightPx?.takeIf { it > 0 }
                } else {
                    null
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
            automationTriggerId = if (selectedDataKey == AUTOMATION_TRIGGER_WIDGET_DATA_KEY) {
                normalizeAutomationTriggerId(automationTriggerId)
            } else {
                ""
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
            hvacTempStepTenths = if (WidgetsRepository.supportsHvacTempStep(selectedDataKey)) {
                normalizeHvacTempWidgetStepTenths(hvacTempStepTenths)
            } else {
                HVAC_TEMP_WIDGET_STEP_TENTHS_DEFAULT
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
            tripWidgetSource = if (usesTripWidgetSource(selectedDataKey)) {
                normalizeTripWidgetSource(tripWidgetSource)
            } else {
                TRIP_WIDGET_SOURCE_CURRENT
            },
            tripMetricFieldId = if (isTripMetricWidgetDataKey(selectedDataKey)) {
                TripMetricFormatter.normalizeFieldId(tripMetricFieldId)
            } else {
                ActiveTripCustomWidgetField.DISTANCE.id
            },
            obdPidId = if (isObdMetricWidgetDataKey(selectedDataKey)) {
                ObdPid.normalizeId(obdPidId)
            } else {
                ObdPid.RPM.id
            },
            avgFuelConsumptionSource = if (isAverageFuelConsumptionWidgetDataKey(selectedDataKey)) {
                normalizeAvgFuelConsumptionSource(avgFuelConsumptionSource)
            } else {
                AVG_FUEL_CONSUMPTION_SOURCE_MBCAN_VHAL
            },
            fuelLitersDisplayMode = if (isFuelLitersDisplayModeWidgetDataKey(selectedDataKey)) {
                normalizeFuelLitersDisplayMode(fuelLitersDisplayMode)
            } else {
                FUEL_LITERS_DISPLAY_REMAINING
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
            controlPadding = controlPadding?.let { normalizeWidgetControlPadding(it) },
            roadMatchHeadingUp = isRoadMatchMapWidgetDataKey(selectedDataKey) &&
                roadMatchHeadingUp,
            roadMatchMapKitBasemap = isRoadMatchMapWidgetDataKey(selectedDataKey) &&
                roadMatchMapKitBasemap,
            roadMatchBasemapTransparencyPercent = if (isRoadMatchMapWidgetDataKey(selectedDataKey)) {
                vad.dashing.tbox.location.roadmatch.RoadMatchBasemapOpacity.normalize(
                    roadMatchBasemapTransparencyPercent,
                )
            } else {
                0
            },
            speedCamOverageKmh = if (isOsmSpeedLimitWidgetDataKey(selectedDataKey)) {
                normalizeSpeedCamOverageKmh(speedCamOverageKmh)
            } else {
                DEFAULT_SPEED_CAM_OVERAGE_KMH
            },
            speedCamRadiusM = if (isOsmSpeedLimitWidgetDataKey(selectedDataKey)) {
                normalizeMapsCamLookaheadM(mapsCamLookaheadDistanceM)
            } else {
                DEFAULT_MAPS_CAM_LOOKAHEAD_M
            },
            speedCamShowOnMap = isRoadMatchMapWidgetDataKey(selectedDataKey) && speedCamShowOnMap,
            mapsCamShowCameras = !isOsmSpeedLimitWidgetDataKey(selectedDataKey) || mapsCamShowCameras,
            mapsCamShowCurrentLimit = !isOsmSpeedLimitWidgetDataKey(selectedDataKey) ||
                mapsCamShowCurrentLimit,
            mapsCamShowAheadLimit = !isOsmSpeedLimitWidgetDataKey(selectedDataKey) ||
                mapsCamShowAheadLimit,
            mapsCamLookaheadDistanceM = if (isOsmSpeedLimitWidgetDataKey(selectedDataKey)) {
                normalizeMapsCamLookaheadM(mapsCamLookaheadDistanceM)
            } else {
                DEFAULT_MAPS_CAM_LOOKAHEAD_M
            },
            mapsCamRadarHoldDistanceM = if (isOsmSpeedLimitWidgetDataKey(selectedDataKey)) {
                normalizeMapsCamRadarHoldM(mapsCamRadarHoldDistanceM)
            } else {
                DEFAULT_MAPS_CAM_RADAR_HOLD_M
            },
            buttonBinding = if (supportsWidgetButtonBinding(selectedDataKey)) {
                normalizeWidgetButtonBinding(buttonBinding)
            } else {
                null
            },
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
                controlPadding = controlPadding?.let { normalizeWidgetControlPadding(it) },
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
        titleScale = normalizeWidgetScale(cfg.titleScale)
        iconScale = normalizeWidgetScale(cfg.iconScale)
        textScale = normalizeWidgetScale(cfg.textScale)
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
        mediaFollowPlayback = if (isMusicWidgetDataKey(selectedDataKey)) {
            cfg.mediaFollowPlayback
        } else {
            false
        }
        mediaShowLikeButton = if (isMusicWidgetDataKey(selectedDataKey)) {
            cfg.mediaShowLikeButton
        } else {
            false
        }
        mediaShowAlbumArt = supportsMusicAlbumArtToggle(selectedDataKey) && cfg.mediaShowAlbumArt
        mediaAlbumArtColumnWidthPercent = if (supportsMusicAlbumArtLayoutSettings(selectedDataKey)) {
            MusicWidgetAlbumArtDisplay.normalizeAlbumArtColumnWidthPercent(
                cfg.mediaAlbumArtColumnWidthPercent,
            )
        } else {
            MusicWidgetAlbumArtDisplay.DEFAULT_ALBUM_ART_COLUMN_WIDTH_PERCENT
        }
        mediaAlbumArtSide = if (supportsMusicAlbumArtLayoutSettings(selectedDataKey)) {
            MusicWidgetAlbumArtDisplay.normalizeAlbumArtSide(cfg.mediaAlbumArtSide)
        } else {
            MusicWidgetAlbumArtDisplay.DEFAULT_ALBUM_ART_SIDE
        }
        mediaShowPlayerHeaderIcon = if (supportsMusicPlayerHeaderIconSetting(selectedDataKey)) {
            cfg.mediaShowPlayerHeaderIcon
        } else {
            true
        }
        mediaShowTrackInfo = if (selectedDataKey == MUSIC_COVER_WIDGET_DATA_KEY) {
            cfg.mediaShowTrackInfo
        } else {
            true
        }
        mediaControlsHeightPercent = MusicWidgetControlsDisplay.resolveControlsHeightPercent(
            selectedDataKey,
            if (supportsMusicControlsHeightSetting(selectedDataKey)) {
                cfg.mediaControlsHeightPercent
            } else {
                null
            },
        )
        useMbCanVhal = WidgetsRepository.supportsUseMbCanVhal(selectedDataKey) &&
            (cfg.useMbCanVhal || preferUseMbCanVhalDefault)
        stepperAdjustIconStyle = if (WidgetsRepository.supportsStepperAdjustIconStyle(selectedDataKey)) {
            normalizeStepperAdjustIconStyle(cfg.stepperAdjustIconStyle)
        } else {
            STEPPER_ADJUST_ICON_PLUS_MINUS
        }
        hvacTempStepTenths = if (WidgetsRepository.supportsHvacTempStep(selectedDataKey)) {
            normalizeHvacTempWidgetStepTenths(cfg.hvacTempStepTenths)
        } else {
            HVAC_TEMP_WIDGET_STEP_TENTHS_DEFAULT
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
        launcherLaunchMode = if (selectedDataKey == APP_LAUNCHER_WIDGET_DATA_KEY) {
            AppLauncherLaunchMode.fromStored(
                cfg.launcherLaunchMode.storageKey,
                cfg.launcherFreeformEnabled,
            )
        } else {
            AppLauncherLaunchMode.DEFAULT
        }
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
        launcherFreeformOverlayPage = if (selectedDataKey == APP_LAUNCHER_WIDGET_DATA_KEY) {
            cfg.launcherFreeformOverlayPage
        } else {
            null
        }
        launcherFreeformOverlayCrop =
            selectedDataKey == APP_LAUNCHER_WIDGET_DATA_KEY && cfg.launcherFreeformOverlayCrop
        launcherVirtualDisplayId = if (selectedDataKey == APP_LAUNCHER_WIDGET_DATA_KEY) {
            cfg.launcherVirtualDisplayId
        } else {
            null
        }
        launcherVirtualDisplayWidthPx = if (selectedDataKey == APP_LAUNCHER_WIDGET_DATA_KEY) {
            cfg.launcherVirtualDisplayWidthPx
        } else {
            null
        }
        launcherVirtualDisplayHeightPx = if (selectedDataKey == APP_LAUNCHER_WIDGET_DATA_KEY) {
            cfg.launcherVirtualDisplayHeightPx
        } else {
            null
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
        automationTriggerId = if (selectedDataKey == AUTOMATION_TRIGGER_WIDGET_DATA_KEY) {
            normalizeAutomationTriggerId(cfg.automationTriggerId)
        } else {
            ""
        }
        buttonBinding = if (supportsWidgetButtonBinding(selectedDataKey)) {
            normalizeWidgetButtonBinding(cfg.buttonBinding)
        } else {
            null
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
        tripMetricFieldId = if (isTripMetricWidgetDataKey(selectedDataKey)) {
            TripMetricFormatter.normalizeFieldId(cfg.tripMetricFieldId)
        } else {
            ActiveTripCustomWidgetField.DISTANCE.id
        }
        obdPidId = if (isObdMetricWidgetDataKey(selectedDataKey)) {
            ObdPid.normalizeId(cfg.obdPidId)
        } else {
            ObdPid.RPM.id
        }
        avgFuelConsumptionSource = if (isAverageFuelConsumptionWidgetDataKey(selectedDataKey)) {
            normalizeAvgFuelConsumptionSource(cfg.avgFuelConsumptionSource)
        } else {
            AVG_FUEL_CONSUMPTION_SOURCE_MBCAN_VHAL
        }
        fuelLitersDisplayMode = if (isFuelLitersDisplayModeWidgetDataKey(selectedDataKey)) {
            normalizeFuelLitersDisplayMode(cfg.fuelLitersDisplayMode)
        } else {
            FUEL_LITERS_DISPLAY_REMAINING
        }
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
        controlPadding = cfg.controlPadding
        roadMatchHeadingUp = isRoadMatchMapWidgetDataKey(selectedDataKey) &&
            cfg.roadMatchHeadingUp
        roadMatchMapKitBasemap = isRoadMatchMapWidgetDataKey(selectedDataKey) &&
            cfg.roadMatchMapKitBasemap
        roadMatchBasemapTransparencyPercent = if (isRoadMatchMapWidgetDataKey(selectedDataKey)) {
            vad.dashing.tbox.location.roadmatch.RoadMatchBasemapOpacity.normalize(
                cfg.roadMatchBasemapTransparencyPercent,
            )
        } else {
            0
        }
        speedCamOverageKmh = if (isOsmSpeedLimitWidgetDataKey(selectedDataKey)) {
            normalizeSpeedCamOverageKmh(cfg.speedCamOverageKmh)
        } else {
            DEFAULT_SPEED_CAM_OVERAGE_KMH
        }
        mapsCamLookaheadDistanceM = if (isOsmSpeedLimitWidgetDataKey(selectedDataKey)) {
            normalizeMapsCamLookaheadM(cfg.mapsCamLookaheadDistanceM)
        } else {
            DEFAULT_MAPS_CAM_LOOKAHEAD_M
        }
        mapsCamRadarHoldDistanceM = if (isOsmSpeedLimitWidgetDataKey(selectedDataKey)) {
            normalizeMapsCamRadarHoldM(cfg.mapsCamRadarHoldDistanceM)
        } else {
            DEFAULT_MAPS_CAM_RADAR_HOLD_M
        }
        speedCamShowOnMap = isRoadMatchMapWidgetDataKey(selectedDataKey) && cfg.speedCamShowOnMap
        mapsCamShowCameras = !isOsmSpeedLimitWidgetDataKey(selectedDataKey) || cfg.mapsCamShowCameras
        mapsCamShowCurrentLimit =
            !isOsmSpeedLimitWidgetDataKey(selectedDataKey) || cfg.mapsCamShowCurrentLimit
        mapsCamShowAheadLimit =
            !isOsmSpeedLimitWidgetDataKey(selectedDataKey) || cfg.mapsCamShowAheadLimit
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
            collapseTouchZoneThicknessDp = wholePanelCollapseTouchZoneThicknessDp,
            collapseStripLengthPercentCollapsed = wholePanelCollapseStripLengthPercentCollapsed,
            collapseStripLengthPercentExpanded = wholePanelCollapseStripLengthPercentExpanded,
            collapseStripPositionPercentCollapsed = wholePanelCollapseStripPositionPercentCollapsed,
            collapseStripPositionPercentExpanded = wholePanelCollapseStripPositionPercentExpanded,
            collapseTouchZoneMatchStrip = wholePanelCollapseTouchZoneMatchStrip,
            collapseStripColorLight = wholePanelCollapseStripColorLight,
            collapseStripColorDark = wholePanelCollapseStripColorDark,
            collapseStripExpandedColorLight = wholePanelCollapseStripExpandedColorLight,
            collapseStripExpandedColorDark = wholePanelCollapseStripExpandedColorDark,
            collapseOnStripTap = wholePanelCollapseOnStripTap,
            collapseOnStripDoubleTap = wholePanelCollapseOnStripDoubleTap,
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
        wholePanelCollapseTouchZoneThicknessDp = snapshot.collapseTouchZoneThicknessDp
        wholePanelCollapseStripLengthPercentCollapsed = snapshot.collapseStripLengthPercentCollapsed
        wholePanelCollapseStripLengthPercentExpanded = snapshot.collapseStripLengthPercentExpanded
        wholePanelCollapseStripPositionPercentCollapsed =
            snapshot.collapseStripPositionPercentCollapsed
        wholePanelCollapseStripPositionPercentExpanded =
            snapshot.collapseStripPositionPercentExpanded
        wholePanelCollapseTouchZoneMatchStrip = snapshot.collapseTouchZoneMatchStrip
        wholePanelCollapseStripColorLight = snapshot.collapseStripColorLight
        wholePanelCollapseStripColorDark = snapshot.collapseStripColorDark
        wholePanelCollapseStripExpandedColorLight = snapshot.collapseStripExpandedColorLight
        wholePanelCollapseStripExpandedColorDark = snapshot.collapseStripExpandedColorDark
        wholePanelCollapseOnStripTap = snapshot.collapseOnStripTap
        wholePanelCollapseOnStripDoubleTap = snapshot.collapseOnStripDoubleTap
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
        otherTilesDirty = true
        val tileCfg = widgets.getOrNull(widgetIndex)
            ?: FloatingDashboardWidgetConfig(dataKey = "")
        applyDraftWidgetConfig(tileCfg, preserveDataKey = false)
    }

    /** Call right after [syncWholePanelDraftFromMainScreen] / [syncWholePanelDraftFromFloating]. */
    fun markWholePanelDraftSeeded() {
        wholePanelDraftSeeded = true
        panelBaseline = panelFieldsFingerprint()
    }

    /** Config written for the edited tile on Save (empty slot when no type is chosen). */
    fun editingTileConfigForSave(): FloatingDashboardWidgetConfig =
        if (selectedDataKey.isNotEmpty()) {
            buildDraftWidgetConfig()
        } else {
            FloatingDashboardWidgetConfig(dataKey = "", customTitle = "")
        }

    /** All tiles of the panel as they would be saved now, padded to [tileCount]. */
    fun workingTileConfigs(
        currentWidgetConfigs: List<FloatingDashboardWidgetConfig>,
        tileCount: Int,
    ): List<FloatingDashboardWidgetConfig> {
        val list = padWidgetConfigs(
            wholePanelWidgetsDraft ?: currentWidgetConfigs,
            maxOf(tileCount, editingIndex + 1),
        ).toMutableList()
        list[editingIndex] = editingTileConfigForSave()
        return list
    }

    val isEditingTileDirty: Boolean
        get() = buildDraftWidgetConfig() != tileBaseline

    val isPanelDirty: Boolean
        get() = wholePanelDraftSeeded &&
            (panelBaseline == null || panelFieldsFingerprint() != panelBaseline)

    val hasUnsavedChanges: Boolean
        get() = otherTilesDirty || isEditingTileDirty || isPanelDirty

    /** Tile type when the current tile was loaded into the editor (for «Было:»). */
    val loadedTileDataKey: String
        get() = tileBaseline.dataKey

    /** Writes pending edits of the current tile into [wholePanelWidgetsDraft] and returns that list. */
    private fun commitEditingTile(
        currentWidgetConfigs: List<FloatingDashboardWidgetConfig>,
        tileCount: Int,
    ): MutableList<FloatingDashboardWidgetConfig> {
        val list = padWidgetConfigs(
            wholePanelWidgetsDraft ?: currentWidgetConfigs,
            maxOf(tileCount, editingIndex + 1),
        ).toMutableList()
        if (isEditingTileDirty) {
            list[editingIndex] = editingTileConfigForSave()
            tileBaseline = buildDraftWidgetConfig()
            otherTilesDirty = true
        }
        wholePanelWidgetsDraft = list.toList()
        return list
    }

    /** Keeps edits of the current tile in the draft and loads tile [target] into the editor. */
    fun goToTile(
        target: Int,
        currentWidgetConfigs: List<FloatingDashboardWidgetConfig>,
        tileCount: Int,
    ) {
        if (target == editingIndex || target < 0 || target >= maxOf(tileCount, 1)) return
        val list = commitEditingTile(currentWidgetConfigs, tileCount)
        val cfg = list.getOrNull(target) ?: FloatingDashboardWidgetConfig(dataKey = "")
        val preferMbCan = preferUseMbCanVhalDefault
        preferUseMbCanVhalDefault = false
        applyDraftWidgetConfig(cfg, preserveDataKey = false)
        preferUseMbCanVhalDefault = preferMbCan
        editingIndex = target
        tileBaseline = buildDraftWidgetConfig()
    }

    /** Swaps two tiles; the editor keeps showing the same tile at its new position. */
    fun swapTiles(
        from: Int,
        to: Int,
        currentWidgetConfigs: List<FloatingDashboardWidgetConfig>,
        tileCount: Int,
    ): Boolean {
        val list = commitEditingTile(currentWidgetConfigs, tileCount)
        if (!canSwapTiles(list, from, to)) return false
        wholePanelWidgetsDraft = swapTileConfigs(list, from, to)
        otherTilesDirty = true
        editingIndex = when (editingIndex) {
            from -> to
            to -> from
            else -> editingIndex
        }
        gridSelection = emptySet()
        return true
    }

    /** Copies the current tile's appearance to the tiles in [gridSelection]; returns how many changed. */
    fun applyAppearanceToSelection(
        currentWidgetConfigs: List<FloatingDashboardWidgetConfig>,
        tileCount: Int,
    ): Int {
        val source = editingTileConfigForSave()
        if (source.dataKey.isEmpty()) return 0
        val list = commitEditingTile(currentWidgetConfigs, tileCount)
        var changed = 0
        gridSelection.filter { it != editingIndex && it in list.indices }.forEach { index ->
            val updated = applyTileAppearance(list[index], source)
            if (updated != list[index]) {
                list[index] = updated
                changed++
            }
        }
        if (changed > 0) {
            wholePanelWidgetsDraft = list.toList()
            otherTilesDirty = true
        }
        return changed
    }

    private fun panelFieldsFingerprint(): List<Any?> = listOf(
        wholePanelNameDraft,
        wholePanelShowTboxDisconnect,
        wholePanelRows,
        wholePanelCols,
        wholePanelGridSpacingDp,
        wholePanelPageNumber,
        wholePanelClickAction,
        wholePanelCollapseEdge,
        wholePanelCollapseStripThicknessDp,
        wholePanelCollapseTouchZoneThicknessDp,
        wholePanelCollapseStripLengthPercentCollapsed,
        wholePanelCollapseStripLengthPercentExpanded,
        wholePanelCollapseStripPositionPercentCollapsed,
        wholePanelCollapseStripPositionPercentExpanded,
        wholePanelCollapseTouchZoneMatchStrip,
        wholePanelCollapseStripColorLight,
        wholePanelCollapseStripColorDark,
        wholePanelCollapseStripExpandedColorLight,
        wholePanelCollapseStripExpandedColorDark,
        wholePanelCollapseOnStripTap,
        wholePanelCollapseOnStripDoubleTap,
        wholePanelCollapseOnTileTap,
        wholePanelCollapseOnTileTapDelaySec,
        wholePanelBackgroundColorLight,
        wholePanelBackgroundColorDark,
        wholePanelBackgroundImageRelPathLight,
        wholePanelBackgroundImageRelPathDark,
        wholePanelShape,
    )

    private var panelBaseline by mutableStateOf<List<Any?>?>(null)
    // Declared after every tile field: the initializer reads all of them.
    private var tileBaseline by mutableStateOf(buildDraftWidgetConfig())

    val canSaveSelection: Boolean
        get() = when {
            selectedDataKey.isEmpty() -> true
            isMusicWidgetSelected -> selectedMediaPlayers.isNotEmpty()
            isDriveModeCycleWidgetSelected ->
                normalizeDriveModeCycleSelection(selectedDriveModes).isNotEmpty()
            isAppLauncherWidgetSelected -> {
                val displayId = launcherVirtualDisplayId
                launcherAppPackage.isNotBlank() &&
                    (
                        launcherLaunchMode != AppLauncherLaunchMode.VIRTUAL_DISPLAY ||
                            (displayId != null && displayId > 0)
                        )
            }
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
    // currentTheme is read only at creation: a day/night switch must not drop unsaved edits.
    return remember(
        widgetIndex,
        currentWidgets,
        currentWidgetConfigs,
        defaultBackgroundLight,
        defaultBackgroundDark,
    ) {
        WidgetSelectionDialogState(
            initialDataKey = initialDataKey,
            initialConfig = initialConfig,
            panelDefaultBackgroundLight = defaultBackgroundLight,
            panelDefaultBackgroundDark = defaultBackgroundDark,
            initialColorThemeSegment = colorThemeSegmentFor(currentTheme),
            initialEditingIndex = widgetIndex,
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

internal fun applyWidgetSelectionChanges(
    context: Context,
    dashboardManager: DashboardManager,
    currentWidgets: List<DashboardWidget>,
    currentWidgetConfigs: List<FloatingDashboardWidgetConfig>,
    widgetIndex: Int,
    state: WidgetSelectionDialogState,
    saveConfigs: (List<FloatingDashboardWidgetConfig>) -> Unit,
    externalAppWidgetId: Int? = null,
    /** False keeps the stored config of [widgetIndex] (its new widget is still being picked). */
    writeEditingTile: Boolean = true,
) {
    if (state.selectedDataKey == WidgetsRepository.EXTERNAL_WIDGET_DATA_KEY) {
        state.draftAppWidgetId = externalAppWidgetId
    }
    val draftConfig = state.editingTileConfigForSave()
    val targetCount = if (state.wholePanelDraftSeeded) {
        (state.wholePanelRows * state.wholePanelCols).coerceAtLeast(1)
    } else {
        currentWidgets.size.coerceAtLeast(1)
    }
    val baseConfigs = state.wholePanelWidgetsDraft ?: currentWidgetConfigs
    val normalizedConfigs = normalizeWidgetConfigs(baseConfigs, targetCount).toMutableList()
    val prevAppWidgetIds = normalizedConfigs.mapNotNull { it.appWidgetId }.toSet() +
        currentWidgetConfigs.mapNotNull { it.appWidgetId }
    if (writeEditingTile && widgetIndex in normalizedConfigs.indices) {
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
    updatedWidgets.forEachIndexed { index, widget ->
        if (index == widgetIndex || normalizedConfigs[index] != currentWidgetConfigs.getOrNull(index)) {
            dashboardManager.clearWidgetHistory(widget.id)
        }
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
        collapseTouchZoneThicknessDp = normalizePanelCollapseTouchZoneThicknessDp(
            state.wholePanelCollapseTouchZoneThicknessDp,
            normalizePanelCollapseStripThicknessDp(state.wholePanelCollapseStripThicknessDp),
        ),
        collapseStripLengthPercentCollapsed = normalizePanelCollapseStripLengthPercent(
            state.wholePanelCollapseStripLengthPercentCollapsed,
        ),
        collapseStripLengthPercentExpanded = normalizePanelCollapseStripLengthPercent(
            state.wholePanelCollapseStripLengthPercentExpanded,
        ),
        collapseStripPositionPercentCollapsed = normalizePanelCollapseStripPositionPercent(
            state.wholePanelCollapseStripPositionPercentCollapsed,
            state.wholePanelCollapseStripLengthPercentCollapsed,
        ),
        collapseStripPositionPercentExpanded = normalizePanelCollapseStripPositionPercent(
            state.wholePanelCollapseStripPositionPercentExpanded,
            state.wholePanelCollapseStripLengthPercentExpanded,
        ),
        collapseTouchZoneMatchStrip = state.wholePanelCollapseTouchZoneMatchStrip,
        collapseStripColorLight = state.wholePanelCollapseStripColorLight,
        collapseStripColorDark = state.wholePanelCollapseStripColorDark,
        collapseStripExpandedColorLight = state.wholePanelCollapseStripExpandedColorLight,
        collapseStripExpandedColorDark = state.wholePanelCollapseStripExpandedColorDark,
        collapseOnStripTap = state.wholePanelCollapseOnStripTap,
        collapseOnStripDoubleTap = state.wholePanelCollapseOnStripDoubleTap,
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
        collapseTouchZoneThicknessDp = normalizePanelCollapseTouchZoneThicknessDp(
            state.wholePanelCollapseTouchZoneThicknessDp,
            normalizePanelCollapseStripThicknessDp(state.wholePanelCollapseStripThicknessDp),
        ),
        collapseStripLengthPercentCollapsed = normalizePanelCollapseStripLengthPercent(
            state.wholePanelCollapseStripLengthPercentCollapsed,
        ),
        collapseStripLengthPercentExpanded = normalizePanelCollapseStripLengthPercent(
            state.wholePanelCollapseStripLengthPercentExpanded,
        ),
        collapseStripPositionPercentCollapsed = normalizePanelCollapseStripPositionPercent(
            state.wholePanelCollapseStripPositionPercentCollapsed,
            state.wholePanelCollapseStripLengthPercentCollapsed,
        ),
        collapseStripPositionPercentExpanded = normalizePanelCollapseStripPositionPercent(
            state.wholePanelCollapseStripPositionPercentExpanded,
            state.wholePanelCollapseStripLengthPercentExpanded,
        ),
        collapseTouchZoneMatchStrip = state.wholePanelCollapseTouchZoneMatchStrip,
        collapseStripColorLight = state.wholePanelCollapseStripColorLight,
        collapseStripColorDark = state.wholePanelCollapseStripColorDark,
        collapseStripExpandedColorLight = state.wholePanelCollapseStripExpandedColorLight,
        collapseStripExpandedColorDark = state.wholePanelCollapseStripExpandedColorDark,
        collapseOnStripTap = state.wholePanelCollapseOnStripTap,
        collapseOnStripDoubleTap = state.wholePanelCollapseOnStripDoubleTap,
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
        ?: (state.wholePanelWidgetsDraft ?: currentWidgetConfigs).getOrNull(widgetIndex)
            ?.takeIf { it.dataKey == WidgetsRepository.EXTERNAL_WIDGET_DATA_KEY }
            ?.appWidgetId
}

/**
 * Opens the system widget picker when an external widget tile has no widget yet.
 * Other pending edits are saved first via [persistOtherChanges]: the picker activity
 * rewrites the stored panel config and would otherwise drop them.
 */
internal fun tryLaunchExternalWidgetPicker(
    context: Context,
    saveTarget: Int,
    panelId: String,
    widgetIndex: Int,
    state: WidgetSelectionDialogState,
    currentWidgetConfigs: List<FloatingDashboardWidgetConfig>,
    onDismiss: () -> Unit,
    persistOtherChanges: () -> Unit = {},
): Boolean {
    if (state.selectedDataKey != WidgetsRepository.EXTERNAL_WIDGET_DATA_KEY) return false
    val id = externalAppWidgetIdForApply(state, currentWidgetConfigs, widgetIndex)
    if (id != null) return false
    if (state.otherTilesDirty || state.isPanelDirty) {
        persistOtherChanges()
    }
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
