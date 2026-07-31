package vad.dashing.tbox.ui

import android.appwidget.AppWidgetHost
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import vad.dashing.tbox.R
import vad.dashing.tbox.AppDataViewModel
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.DashboardManager
import vad.dashing.tbox.DashboardWidget
import vad.dashing.tbox.FloatingDashboardWidgetConfig
import vad.dashing.tbox.TboxViewModel
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.ACC_CRUISE_WIDGET_DATA_KEY
import vad.dashing.tbox.ACTIVE_TRIP_WIDGET_CUSTOM_DATA_KEY
import vad.dashing.tbox.ACTIVE_TRIP_WIDGET_DATA_KEY
import vad.dashing.tbox.ACTIVE_TRIP_WIDGET_MINI_DATA_KEY
import vad.dashing.tbox.ACTIVE_TRIP_WIDGET_SIMPLE_DATA_KEY
import vad.dashing.tbox.TRIP_WIDGET_SOURCE_CURRENT
import vad.dashing.tbox.normalizeTripWidgetSource
import vad.dashing.tbox.trip.TripRepository
import vad.dashing.tbox.APP_LAUNCHER_WIDGET_DATA_KEY
import vad.dashing.tbox.EMPTY_TILE_WIDGET_DATA_KEY
import vad.dashing.tbox.EspRelayWidgetMode
import vad.dashing.tbox.HTTP_REQUEST_WIDGET_DATA_KEY
import vad.dashing.tbox.HttpRequestIconPaths
import vad.dashing.tbox.HIDE_FLOATING_PANELS_WIDGET_DATA_KEY
import vad.dashing.tbox.TOGGLE_FLOATING_PANELS_ENABLED_WIDGET_DATA_KEY
import vad.dashing.tbox.FRONT_LEFT_SEAT_HEAT_VENT_SINGLE_WIDGET_DATA_KEY
import vad.dashing.tbox.FRONT_RIGHT_SEAT_HEAT_VENT_SINGLE_WIDGET_DATA_KEY
import vad.dashing.tbox.REAR_LEFT_SEAT_HEAT_WIDGET_DATA_KEY
import vad.dashing.tbox.REAR_RIGHT_SEAT_HEAT_WIDGET_DATA_KEY
import vad.dashing.tbox.MEDIA_VOLUME_WIDGET_HORIZONTAL_DATA_KEY
import vad.dashing.tbox.MEDIA_VOLUME_WIDGET_VERTICAL_DATA_KEY
import vad.dashing.tbox.MUSIC_BUTTONS_WIDGET_HORIZONTAL_DATA_KEY
import vad.dashing.tbox.MUSIC_BUTTONS_WIDGET_VERTICAL_DATA_KEY
import vad.dashing.tbox.MUSIC_WIDGET_DATA_KEY
import vad.dashing.tbox.HVAC_BLOW_MODE_CYCLE_WIDGET_DATA_KEY
import vad.dashing.tbox.HVAC_BLOW_MODE_PANEL_WIDGET_HORIZONTAL_DATA_KEY
import vad.dashing.tbox.HVAC_BLOW_MODE_PANEL_WIDGET_VERTICAL_DATA_KEY
import vad.dashing.tbox.HVAC_FAN_WIDGET_HORIZONTAL_DATA_KEY
import vad.dashing.tbox.HVAC_FAN_WIDGET_VERTICAL_DATA_KEY
import vad.dashing.tbox.HVAC_SYNC_WIDGET_DATA_KEY
import vad.dashing.tbox.HVAC_TEMP_LEFT_WIDGET_HORIZONTAL_DATA_KEY
import vad.dashing.tbox.HVAC_TEMP_LEFT_WIDGET_VERTICAL_DATA_KEY
import vad.dashing.tbox.HVAC_TEMP_RIGHT_WIDGET_HORIZONTAL_DATA_KEY
import vad.dashing.tbox.HVAC_TEMP_RIGHT_WIDGET_VERTICAL_DATA_KEY
import vad.dashing.tbox.TRUNK_DOOR_WIDGET_DATA_KEY
import vad.dashing.tbox.DAY_NIGHT_THEME_WIDGET_DATA_KEY
import vad.dashing.tbox.MIRROR_ADJUST_MODE_WIDGET_DATA_KEY
import vad.dashing.tbox.MIRROR_FOLD_WIDGET_DATA_KEY
import vad.dashing.tbox.DRIVE_MODE_WIDGET_DATA_KEY
import vad.dashing.tbox.DRIVE_MODE_CYCLE_WIDGET_DATA_KEY
import vad.dashing.tbox.PARKING_RADAR_WIDGET_DATA_KEY
import vad.dashing.tbox.SLA_SPEED_LIMIT_WIDGET_DATA_KEY
import vad.dashing.tbox.SPEED_LIMITER_WIDGET_DATA_KEY
import vad.dashing.tbox.WIPER_MAINTENANCE_WIDGET_DATA_KEY
import vad.dashing.tbox.WidgetsRepository
import vad.dashing.tbox.usesDefaultControlColors

@Composable
fun DashboardWidgetRenderer(
    widget: DashboardWidget,
    widgetConfig: FloatingDashboardWidgetConfig,
    settingsViewModel: SettingsViewModel,
    tboxViewModel: TboxViewModel,
    canViewModel: CanDataViewModel,
    appDataViewModel: AppDataViewModel,
    dataProvider: DataProvider,
    dashboardManager: DashboardManager,
    dashboardChart: Boolean,
    tboxConnected: Boolean,
    restartEnabled: Boolean,
    onTripFinishAndStart: () -> Unit,
    widgetTextColor: Color,
    widgetBackgroundColor: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMusicSelectedPlayerChange: (String) -> Unit,
    onSeatHeatVentSelectedVariantChange: (Int) -> Unit = {},
    onHideFloatingPanelsDoubleClick: () -> Unit = {},
    onToggleFloatingPanelsEnabledDoubleClick: () -> Unit = {},
    onRestartRequested: () -> Unit,
    externalWidgetHost: AppWidgetHost? = null,
    isEditMode: Boolean = false,
    elevation: Dp = 4.dp,
    shape: Dp = 12.dp,
    enableInnerInteractions: Boolean = true,
    panelStorageId: String = "",
) {
    val launcherAppIconRevision by settingsViewModel.launcherAppIconRevision.collectAsStateWithLifecycle()
    val httpRequestIconRevision by settingsViewModel.httpRequestIconRevision.collectAsStateWithLifecycle()
    val themeActivating by settingsViewModel.themeActivationInProgress.collectAsStateWithLifecycle()
    val iconLookup = rememberLauncherAppIconLookup(settingsViewModel)
    val activeTripCustomLayout by settingsViewModel.activeTripCustomWidgetLayout.collectAsStateWithLifecycle()
    val activeTripSimpleLayout by settingsViewModel.activeTripSimpleWidgetLayout.collectAsStateWithLifecycle()
    val titleOverride = widgetConfig.customTitle
    val valueAccuracy = widgetConfig.valueAccuracy
    val currentTheme by tboxViewModel.currentTheme.collectAsStateWithLifecycle()
    val controlAppearance = rememberResolvedControlAppearance(
        config = widgetConfig,
        currentTheme = currentTheme,
        tileTextColor = widgetTextColor,
        dataKey = widget.dataKey,
    )
    CompositionLocalProvider(
        LocalWidgetControlAppearance provides controlAppearance,
        LocalWidgetControlUsesDefaults provides widgetConfig.usesDefaultControlColors(),
    ) {
    when (widget.dataKey) {
        "netWidget" -> {
            DashboardNetWidgetItem(
                widget = widget,
                onClick = onClick,
                onLongClick = onLongClick,
                viewModel = tboxViewModel,
                elevation = elevation,
                shape = shape,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride,
                scale = widgetConfig.scale
            )
        }

        "netWidgetNew" -> {
            DashboardNetNewWidgetItem(
                widget = widget,
                onClick = onClick,
                onLongClick = onLongClick,
                viewModel = tboxViewModel,
                color = widgetTextColor,
                textColor = widgetTextColor,
                elevation = elevation,
                shape = shape,
                backgroundColor = widgetBackgroundColor,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride,
                scale = widgetConfig.scale
            )
        }

        "netWidgetColored" -> {
            DashboardNetNewWidgetItem(
                widget = widget,
                onClick = onClick,
                onLongClick = onLongClick,
                viewModel = tboxViewModel,
                elevation = elevation,
                shape = shape,
                backgroundColor = widgetBackgroundColor,
                textColor = widgetTextColor,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride,
                defaultTitleRes = R.string.data_title_net_widget_colored,
                scale = widgetConfig.scale
            )
        }

        "locWidget" -> {
            DashboardLocWidgetItem(
                widget = widget,
                onClick = onClick,
                onLongClick = onLongClick,
                viewModel = tboxViewModel,
                valueAccuracy = valueAccuracy,
                elevation = elevation,
                shape = shape,
                backgroundColor = widgetBackgroundColor,
                textColor = widgetTextColor,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride,
                scale = widgetConfig.scale
            )
        }

        "voltage+engineTemperatureWidget" -> {
            DashboardVoltEngTempWidgetItem(
                widget = widget,
                onClick = onClick,
                onLongClick = onLongClick,
                dataProvider = dataProvider,
                valueAccuracy = valueAccuracy,
                elevation = elevation,
                shape = shape,
                units = widgetConfig.showUnit,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride,
                singleLineDualMetrics = widgetConfig.singleLineDualMetrics,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor
            )
        }

        "gearBoxWidget" -> {
            DashboardGearBoxWidgetItem(
                widget = widget,
                onClick = onClick,
                onLongClick = onLongClick,
                dataProvider = dataProvider,
                valueAccuracy = valueAccuracy,
                elevation = elevation,
                shape = shape,
                units = widgetConfig.showUnit,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride,
                singleLineDualMetrics = widgetConfig.singleLineDualMetrics,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor
            )
        }

        DRIVE_MODE_WIDGET_DATA_KEY -> {
            DashboardDriveModeWidgetItem(
                selectedDriveModeRawValue = widgetConfig.selectedDriveMode,
                onClick = onClick,
                onLongClick = onLongClick,
                elevation = elevation,
                shape = shape,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride
            )
        }

        DRIVE_MODE_CYCLE_WIDGET_DATA_KEY -> {
            DashboardDriveModeCycleWidgetItem(
                onClick = onClick,
                onLongClick = onLongClick,
                elevation = elevation,
                shape = shape,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride
            )
        }

        "wheelsPressureWidget" -> {
            DashboardWheelsPressureWidgetItem(
                widget = widget,
                onClick = onClick,
                onLongClick = onLongClick,
                canViewModel = canViewModel,
                dataProvider = dataProvider,
                valueAccuracy = valueAccuracy,
                elevation = elevation,
                shape = shape,
                units = widgetConfig.showUnit,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor,
                useMbCan = widgetConfig.useMbCanVhal,
            )
        }

        "wheelsPressureTemperatureWidget" -> {
            DashboardWheelsPressureTemperatureWidgetItem(
                widget = widget,
                onClick = onClick,
                onLongClick = onLongClick,
                canViewModel = canViewModel,
                dataProvider = dataProvider,
                valueAccuracy = valueAccuracy,
                elevation = elevation,
                shape = shape,
                units = widgetConfig.showUnit,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor,
                useMbCan = widgetConfig.useMbCanVhal,
            )
        }

        "tempInOutWidget" -> {
            DashboardTempInOutWidgetItem(
                widget = widget,
                onClick = onClick,
                onLongClick = onLongClick,
                dataProvider = dataProvider,
                valueAccuracy = valueAccuracy,
                elevation = elevation,
                shape = shape,
                units = widgetConfig.showUnit,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride,
                singleLineDualMetrics = widgetConfig.singleLineDualMetrics,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor
            )
        }

        "fuelLevelWidget" -> {
            DashboardFuelLevelWidgetItem(
                widget = widget,
                onClick = onClick,
                onLongClick = onLongClick,
                dataProvider = dataProvider,
                valueAccuracy = valueAccuracy,
                elevation = elevation,
                shape = shape,
                units = widgetConfig.showUnit,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride,
                singleLineDualMetrics = widgetConfig.singleLineDualMetrics,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor
            )
        }

        "airQualityWidget" -> {
            DashboardAirQualityWidgetItem(
                widget = widget,
                onClick = onClick,
                onLongClick = onLongClick,
                dataProvider = dataProvider,
                valueAccuracy = valueAccuracy,
                elevation = elevation,
                shape = shape,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride,
                singleLineDualMetrics = widgetConfig.singleLineDualMetrics,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor,
                useMbCan = widgetConfig.useMbCanVhal,
            )
        }

        "steeringWheelHeatWidget" -> {
            DashboardSteeringWheelHeatWidgetItem(
                onClick = onClick,
                onLongClick = onLongClick,
                elevation = elevation,
                shape = shape,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride,
                scale = widgetConfig.scale
            )
        }

        WIPER_MAINTENANCE_WIDGET_DATA_KEY -> {
            DashboardWiperMaintenanceWidgetItem(
                onClick = onClick,
                onLongClick = onLongClick,
                elevation = elevation,
                shape = shape,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride,
                scale = widgetConfig.scale
            )
        }

        PARKING_RADAR_WIDGET_DATA_KEY -> {
            DashboardParkingRadarWidgetItem(
                onClick = onClick,
                onLongClick = onLongClick,
                elevation = elevation,
                shape = shape,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride,
                scale = widgetConfig.scale
            )
        }

        SLA_SPEED_LIMIT_WIDGET_DATA_KEY -> {
            DashboardSlaSpeedLimitWidgetItem(
                onClick = onClick,
                onLongClick = onLongClick,
                elevation = elevation,
                shape = shape,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride,
            )
        }

        SPEED_LIMITER_WIDGET_DATA_KEY -> {
            DashboardSpeedLimiterWidgetItem(
                settingsViewModel = settingsViewModel,
                isVertical = true,
                onClick = onClick,
                onLongClick = onLongClick,
                enableInnerInteractions = enableInnerInteractions,
                elevation = elevation,
                shape = shape,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride,
                stepperAdjustIconStyle = widgetConfig.stepperAdjustIconStyle,
            )
        }

        "frontWindscreenHeatWidget" -> {
            DashboardFrontWindscreenHeatWidgetItem(
                onClick = onClick,
                onLongClick = onLongClick,
                elevation = elevation,
                shape = shape,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride,
                scale = widgetConfig.scale
            )
        }

        "rearWindowMirrorsDefrostWidget" -> {
            DashboardRearWindowMirrorsDefrostWidgetItem(
                onClick = onClick,
                onLongClick = onLongClick,
                elevation = elevation,
                shape = shape,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride,
                scale = widgetConfig.scale
            )
        }

        "hvacAirRecirculationWidget" -> {
            DashboardHvacAirRecirculationWidgetItem(
                onClick = onClick,
                onLongClick = onLongClick,
                elevation = elevation,
                shape = shape,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride,
                scale = widgetConfig.scale
            )
        }

        "hvacAcWidget" -> {
            DashboardHvacAcWidgetItem(
                onClick = onClick,
                onLongClick = onLongClick,
                elevation = elevation,
                shape = shape,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride,
                scale = widgetConfig.scale
            )
        }

        "hvacAcCleanWhenLockedWidget" -> {
            DashboardHvacAcCleanWhenLockedWidgetItem(
                onClick = onClick,
                onLongClick = onLongClick,
                elevation = elevation,
                shape = shape,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride,
                scale = widgetConfig.scale
            )
        }

        "hvacAutoWidget" -> {
            DashboardHvacAutoWidgetItem(
                onClick = onClick,
                onLongClick = onLongClick,
                elevation = elevation,
                shape = shape,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride,
                scale = widgetConfig.scale
            )
        }

        "hvacDefrosterFrontWidget" -> {
            DashboardHvacDefrosterFrontWidgetItem(
                onClick = onClick,
                onLongClick = onLongClick,
                elevation = elevation,
                shape = shape,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride,
                scale = widgetConfig.scale
            )
        }

        HVAC_SYNC_WIDGET_DATA_KEY -> {
            DashboardHvacSyncWidgetItem(
                onClick = onClick,
                onLongClick = onLongClick,
                elevation = elevation,
                shape = shape,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride,
                scale = widgetConfig.scale
            )
        }

        HVAC_FAN_WIDGET_HORIZONTAL_DATA_KEY -> {
            DashboardHvacFanWidgetItem(
                isVertical = false,
                onClick = onClick,
                onLongClick = onLongClick,
                enableInnerInteractions = enableInnerInteractions,
                elevation = elevation,
                shape = shape,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride,
                stepperAdjustIconStyle = widgetConfig.stepperAdjustIconStyle,
            )
        }

        HVAC_FAN_WIDGET_VERTICAL_DATA_KEY -> {
            DashboardHvacFanWidgetItem(
                isVertical = true,
                onClick = onClick,
                onLongClick = onLongClick,
                enableInnerInteractions = enableInnerInteractions,
                elevation = elevation,
                shape = shape,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride,
                stepperAdjustIconStyle = widgetConfig.stepperAdjustIconStyle,
            )
        }

        HVAC_TEMP_LEFT_WIDGET_HORIZONTAL_DATA_KEY -> {
            DashboardHvacTempLeftWidgetItem(
                isVertical = false,
                onClick = onClick,
                onLongClick = onLongClick,
                enableInnerInteractions = enableInnerInteractions,
                elevation = elevation,
                shape = shape,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride,
                stepperAdjustIconStyle = widgetConfig.stepperAdjustIconStyle,
            )
        }

        HVAC_TEMP_LEFT_WIDGET_VERTICAL_DATA_KEY -> {
            DashboardHvacTempLeftWidgetItem(
                isVertical = true,
                onClick = onClick,
                onLongClick = onLongClick,
                enableInnerInteractions = enableInnerInteractions,
                elevation = elevation,
                shape = shape,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride,
                stepperAdjustIconStyle = widgetConfig.stepperAdjustIconStyle,
            )
        }

        HVAC_TEMP_RIGHT_WIDGET_HORIZONTAL_DATA_KEY -> {
            DashboardHvacTempRightWidgetItem(
                isVertical = false,
                onClick = onClick,
                onLongClick = onLongClick,
                enableInnerInteractions = enableInnerInteractions,
                elevation = elevation,
                shape = shape,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride,
                stepperAdjustIconStyle = widgetConfig.stepperAdjustIconStyle,
            )
        }

        HVAC_TEMP_RIGHT_WIDGET_VERTICAL_DATA_KEY -> {
            DashboardHvacTempRightWidgetItem(
                isVertical = true,
                onClick = onClick,
                onLongClick = onLongClick,
                enableInnerInteractions = enableInnerInteractions,
                elevation = elevation,
                shape = shape,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride,
                stepperAdjustIconStyle = widgetConfig.stepperAdjustIconStyle,
            )
        }

        HVAC_BLOW_MODE_CYCLE_WIDGET_DATA_KEY -> {
            DashboardHvacBlowModeCycleWidgetItem(
                onClick = onClick,
                onLongClick = onLongClick,
                onDoubleClick = {},
                enableInnerInteractions = enableInnerInteractions,
                elevation = elevation,
                shape = shape,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride,
                scale = widgetConfig.scale
            )
        }

        HVAC_BLOW_MODE_PANEL_WIDGET_HORIZONTAL_DATA_KEY -> {
            DashboardHvacBlowModePanelWidgetItem(
                isVertical = false,
                onClick = onClick,
                onLongClick = onLongClick,
                enableInnerInteractions = enableInnerInteractions,
                elevation = elevation,
                shape = shape,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride
            )
        }

        HVAC_BLOW_MODE_PANEL_WIDGET_VERTICAL_DATA_KEY -> {
            DashboardHvacBlowModePanelWidgetItem(
                isVertical = true,
                onClick = onClick,
                onLongClick = onLongClick,
                enableInnerInteractions = enableInnerInteractions,
                elevation = elevation,
                shape = shape,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride
            )
        }

        TRUNK_DOOR_WIDGET_DATA_KEY -> {
            DashboardTrunkDoorWidgetItem(
                onClick = onClick,
                onLongClick = onLongClick,
                onDoubleClick = {},
                enableInnerInteractions = enableInnerInteractions,
                elevation = elevation,
                shape = shape,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride,
                scale = widgetConfig.scale
            )
        }

        DAY_NIGHT_THEME_WIDGET_DATA_KEY -> {
            DashboardDayNightThemeWidgetItem(
                onClick = onClick,
                onLongClick = onLongClick,
                onDoubleClick = {},
                enableInnerInteractions = enableInnerInteractions,
                elevation = elevation,
                shape = shape,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride,
                scale = widgetConfig.scale
            )
        }

        MIRROR_ADJUST_MODE_WIDGET_DATA_KEY -> {
            DashboardMirrorAdjustModeWidgetItem(
                onClick = onClick,
                onLongClick = onLongClick,
                elevation = elevation,
                shape = shape,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride,
                scale = widgetConfig.scale
            )
        }

        MIRROR_FOLD_WIDGET_DATA_KEY -> {
            DashboardMirrorFoldWidgetItem(
                onClick = onClick,
                onLongClick = onLongClick,
                onDoubleClick = {},
                enableInnerInteractions = enableInnerInteractions,
                elevation = elevation,
                shape = shape,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride,
                scale = widgetConfig.scale
            )
        }

        ACC_CRUISE_WIDGET_DATA_KEY -> {
            DashboardAccCruiseWidgetItem(
                targetKmh = widgetConfig.accCruiseTargetKmh,
                increaseIntervalMs = widgetConfig.accCruiseIncreaseIntervalMs,
                decreaseIntervalMs = widgetConfig.accCruiseDecreaseIntervalMs,
                onClick = onClick,
                onLongClick = onLongClick,
                onDoubleClick = {},
                enableInnerInteractions = enableInnerInteractions,
                elevation = elevation,
                shape = shape,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride,
                scale = widgetConfig.scale,
            )
        }

        "frontLeftSeatHeatVentWidget" -> {
            DashboardFrontLeftSeatHeatVentWidgetItem(
                onClick = onClick,
                onLongClick = onLongClick,
                elevation = elevation,
                shape = shape,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor,
                singleLineDualMetrics = widgetConfig.singleLineDualMetrics,
                enableInnerInteractions = enableInnerInteractions,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride,
                scale = widgetConfig.scale
            )
        }

        FRONT_LEFT_SEAT_HEAT_VENT_SINGLE_WIDGET_DATA_KEY -> {
            DashboardFrontLeftSeatHeatVentSingleWidgetItem(
                selectedVariant = widgetConfig.selectedVariant,
                onSelectedVariantChange = onSeatHeatVentSelectedVariantChange,
                onClick = onClick,
                onLongClick = onLongClick,
                elevation = elevation,
                shape = shape,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor,
                enableInnerInteractions = enableInnerInteractions,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride,
                scale = widgetConfig.scale
            )
        }

        "frontRightSeatHeatVentWidget" -> {
            DashboardFrontRightSeatHeatVentWidgetItem(
                onClick = onClick,
                onLongClick = onLongClick,
                elevation = elevation,
                shape = shape,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor,
                singleLineDualMetrics = widgetConfig.singleLineDualMetrics,
                enableInnerInteractions = enableInnerInteractions,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride,
                scale = widgetConfig.scale
            )
        }

        FRONT_RIGHT_SEAT_HEAT_VENT_SINGLE_WIDGET_DATA_KEY -> {
            DashboardFrontRightSeatHeatVentSingleWidgetItem(
                selectedVariant = widgetConfig.selectedVariant,
                onSelectedVariantChange = onSeatHeatVentSelectedVariantChange,
                onClick = onClick,
                onLongClick = onLongClick,
                elevation = elevation,
                shape = shape,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor,
                enableInnerInteractions = enableInnerInteractions,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride,
                scale = widgetConfig.scale
            )
        }

        REAR_LEFT_SEAT_HEAT_WIDGET_DATA_KEY -> {
            DashboardRearLeftSeatHeatWidgetItem(
                onClick = onClick,
                onLongClick = onLongClick,
                elevation = elevation,
                shape = shape,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor,
                enableInnerInteractions = enableInnerInteractions,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride,
                scale = widgetConfig.scale
            )
        }

        REAR_RIGHT_SEAT_HEAT_WIDGET_DATA_KEY -> {
            DashboardRearRightSeatHeatWidgetItem(
                onClick = onClick,
                onLongClick = onLongClick,
                elevation = elevation,
                shape = shape,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor,
                enableInnerInteractions = enableInnerInteractions,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride,
                scale = widgetConfig.scale
            )
        }

        WidgetsRepository.EXTERNAL_WIDGET_DATA_KEY -> {
            ExternalAppWidgetItem(
                widgetConfig = widgetConfig,
                appWidgetHost = externalWidgetHost,
                isEditMode = isEditMode,
                handleClick = false,
                onClick = onClick,
                onLongClick = onLongClick,
                elevation = elevation,
                shape = shape,
                backgroundColor = widgetBackgroundColor,
                textColor = widgetTextColor,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride,
                defaultTitle = stringResource(R.string.data_title_external_app_widget)
            )
        }

        APP_LAUNCHER_WIDGET_DATA_KEY -> {
            DashboardAppLauncherWidgetItem(
                widget = widget,
                packageName = widgetConfig.launcherAppPackage,
                customIconRevision = launcherAppIconRevision,
                iconLookup = iconLookup,
                suppressCustomIcon = themeActivating,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride,
                onClick = onClick,
                onLongClick = onLongClick,
                elevation = elevation,
                shape = shape,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor
            )
        }

        HTTP_REQUEST_WIDGET_DATA_KEY -> {
            DashboardHttpRequestWidgetItem(
                widget = widget,
                iconKey = HttpRequestIconPaths.iconKey(panelStorageId, widget.id),
                requestYaml = widgetConfig.httpRequestYaml,
                openBrowser = widgetConfig.httpOpenBrowser,
                customIconRevision = httpRequestIconRevision,
                iconLookup = iconLookup,
                suppressCustomIcon = themeActivating,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride,
                isEditMode = isEditMode,
                onEditClick = onClick,
                onLongClick = onLongClick,
                elevation = elevation,
                shape = shape,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor
            )
        }

        EMPTY_TILE_WIDGET_DATA_KEY -> {
            DashboardEmptyTileWidgetItem(
                onClick = onClick,
                onLongClick = onLongClick,
                elevation = elevation,
                shape = shape,
                backgroundColor = widgetBackgroundColor,
                textColor = widgetTextColor,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride,
                defaultTitle = stringResource(R.string.data_title_empty_tile_widget)
            )
        }

        MUSIC_WIDGET_DATA_KEY -> {
            DashboardMusicWidgetItem(
                widget = widget,
                widgetConfig = widgetConfig,
                settingsViewModel = settingsViewModel,
                canViewModel = canViewModel,
                title = widgetConfig.showTitle,
                titleOverride = titleOverride,
                onClick = onClick,
                onLongClick = onLongClick,
                onSelectedPlayerChange = onMusicSelectedPlayerChange,
                elevation = elevation,
                shape = shape,
                enableInnerInteractions = enableInnerInteractions,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor
            )
        }

        MUSIC_BUTTONS_WIDGET_HORIZONTAL_DATA_KEY -> {
            DashboardMusicButtonsWidgetItem(
                widget = widget,
                widgetConfig = widgetConfig,
                settingsViewModel = settingsViewModel,
                canViewModel = canViewModel,
                isVertical = false,
                title = widgetConfig.showTitle,
                titleOverride = titleOverride,
                onClick = onClick,
                onLongClick = onLongClick,
                onSelectedPlayerChange = onMusicSelectedPlayerChange,
                elevation = elevation,
                shape = shape,
                enableInnerInteractions = enableInnerInteractions,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor
            )
        }

        MUSIC_BUTTONS_WIDGET_VERTICAL_DATA_KEY -> {
            DashboardMusicButtonsWidgetItem(
                widget = widget,
                widgetConfig = widgetConfig,
                settingsViewModel = settingsViewModel,
                canViewModel = canViewModel,
                isVertical = true,
                title = widgetConfig.showTitle,
                titleOverride = titleOverride,
                onClick = onClick,
                onLongClick = onLongClick,
                onSelectedPlayerChange = onMusicSelectedPlayerChange,
                elevation = elevation,
                shape = shape,
                enableInnerInteractions = enableInnerInteractions,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor
            )
        }

        MEDIA_VOLUME_WIDGET_HORIZONTAL_DATA_KEY -> {
            DashboardMediaVolumeWidgetItem(
                widget = widget,
                isVertical = false,
                useMbCan = widgetConfig.useMbCanVhal,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride,
                onClick = onClick,
                onLongClick = onLongClick,
                enableInnerInteractions = enableInnerInteractions,
                elevation = elevation,
                shape = shape,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor,
                stepperAdjustIconStyle = widgetConfig.stepperAdjustIconStyle,
            )
        }

        MEDIA_VOLUME_WIDGET_VERTICAL_DATA_KEY -> {
            DashboardMediaVolumeWidgetItem(
                widget = widget,
                isVertical = true,
                useMbCan = widgetConfig.useMbCanVhal,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride,
                onClick = onClick,
                onLongClick = onLongClick,
                enableInnerInteractions = enableInnerInteractions,
                elevation = elevation,
                shape = shape,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor,
                stepperAdjustIconStyle = widgetConfig.stepperAdjustIconStyle,
            )
        }

        "motorHoursWidget" -> {
            DashboardMotorHoursWidgetItem(
                widget = widget,
                dataProvider = dataProvider,
                onClick = onClick,
                onLongClick = onLongClick,
                onDoubleClick = {
                    appDataViewModel.setMotorHours(0f)
                },
                elevation = elevation,
                shape = shape,
                units = widgetConfig.showUnit,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride,
                singleLineDualMetrics = widgetConfig.singleLineDualMetrics,
                valueAccuracy = widgetConfig.valueAccuracy,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor
            )
        }

        ACTIVE_TRIP_WIDGET_CUSTOM_DATA_KEY -> {
            DashboardActiveTripWidgetItem(
                widget = widget,
                appDataViewModel = appDataViewModel,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride,
                customTripLayout = activeTripCustomLayout,
                simpleTripLayout = activeTripSimpleLayout,
                showRowDividers = widgetConfig.tripWidgetShowRowDividers,
                labelColumnWidthPercent = widgetConfig.tripWidgetLabelColumnWidthPercent,
                tripWidgetSource = widgetConfig.tripWidgetSource,
                onClick = onClick,
                onLongClick = onLongClick,
                onDoubleClick = {
                    // Read TripRepository directly: AppDataViewModel.activeTrip is stateIn
                    // (WhileSubscribed) and can lag behind the live active trip on overlays.
                    if (normalizeTripWidgetSource(widgetConfig.tripWidgetSource) ==
                        TRIP_WIDGET_SOURCE_CURRENT &&
                        TripRepository.activeTrip.value?.isCurrentActive == true
                    ) {
                        onTripFinishAndStart()
                    }
                },
                elevation = elevation,
                shape = shape,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor
            )
        }

        ACTIVE_TRIP_WIDGET_DATA_KEY, ACTIVE_TRIP_WIDGET_SIMPLE_DATA_KEY, ACTIVE_TRIP_WIDGET_MINI_DATA_KEY -> {
            DashboardActiveTripWidgetItem(
                widget = widget,
                appDataViewModel = appDataViewModel,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride,
                simpleTripLayout = activeTripSimpleLayout,
                showRowDividers = widgetConfig.tripWidgetShowRowDividers,
                labelColumnWidthPercent = widgetConfig.tripWidgetLabelColumnWidthPercent,
                tripWidgetSource = widgetConfig.tripWidgetSource,
                onClick = onClick,
                onLongClick = onLongClick,
                onDoubleClick = {
                    if (normalizeTripWidgetSource(widgetConfig.tripWidgetSource) ==
                        TRIP_WIDGET_SOURCE_CURRENT &&
                        TripRepository.activeTrip.value?.isCurrentActive == true
                    ) {
                        onTripFinishAndStart()
                    }
                },
                elevation = elevation,
                shape = shape,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor
            )
        }

        "restartTbox" -> {
            DashboardWidgetItem(
                widget = widget,
                dataProvider = dataProvider,
                onClick = onClick,
                onLongClick = onLongClick,
                onDoubleClick = onRestartRequested,
                dashboardManager = dashboardManager,
                dashboardChart = false,
                elevation = elevation,
                shape = shape,
                title = widgetConfig.showTitle,
                titleOverride = titleOverride,
                units = widgetConfig.showUnit,
                backgroundColor = widgetBackgroundColor,
                textColor = if (restartEnabled) {
                    if (tboxConnected) {
                        Color(0xD900A400)
                    } else {
                        Color(0xD9FF0000)
                    }
                } else {
                    Color(0xD97E4C4C)
                }
            )
        }

        HIDE_FLOATING_PANELS_WIDGET_DATA_KEY -> {
            DashboardHideFloatingPanelsWidgetItem(
                onClick = onClick,
                onLongClick = onLongClick,
                onDoubleClick = onHideFloatingPanelsDoubleClick,
                elevation = elevation,
                shape = shape,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride,
                defaultTitle = stringResource(R.string.data_title_hide_floating_panels_widget)
            )
        }

        TOGGLE_FLOATING_PANELS_ENABLED_WIDGET_DATA_KEY -> {
            DashboardHideFloatingPanelsWidgetItem(
                onClick = onClick,
                onLongClick = onLongClick,
                onDoubleClick = onToggleFloatingPanelsEnabledDoubleClick,
                elevation = elevation,
                shape = shape,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor,
                showTitle = widgetConfig.showTitle,
                titleOverride = titleOverride,
                defaultTitle = stringResource(R.string.data_title_toggle_floating_panels_enabled_widget)
            )
        }

        "engineRPM" -> {
            DashboardWidgetItem(
                widget = if (widgetConfig.useMbCanVhal) {
                    widget.copy(dataKey = ENGINE_RPM_CAN_FLOW_KEY)
                } else {
                    widget
                },
                dataProvider = dataProvider,
                onClick = onClick,
                onLongClick = onLongClick,
                dashboardManager = dashboardManager,
                dashboardChart = dashboardChart,
                elevation = elevation,
                shape = shape,
                title = widgetConfig.showTitle,
                titleOverride = titleOverride,
                units = widgetConfig.showUnit,
                backgroundColor = widgetBackgroundColor,
                textColor = widgetTextColor
            )
        }

        "engineTemperature" -> {
            DashboardWidgetItem(
                widget = if (widgetConfig.useMbCanVhal) {
                    widget.copy(dataKey = ENGINE_TEMPERATURE_CAN_FLOW_KEY)
                } else {
                    widget
                },
                dataProvider = dataProvider,
                onClick = onClick,
                onLongClick = onLongClick,
                dashboardManager = dashboardManager,
                dashboardChart = dashboardChart,
                elevation = elevation,
                shape = shape,
                title = widgetConfig.showTitle,
                titleOverride = titleOverride,
                units = widgetConfig.showUnit,
                backgroundColor = widgetBackgroundColor,
                textColor = widgetTextColor
            )
        }

        "carSpeed" -> {
            DashboardWidgetItem(
                widget = if (widgetConfig.useMbCanVhal) {
                    widget.copy(dataKey = CAR_SPEED_CAN_FLOW_KEY)
                } else {
                    widget
                },
                dataProvider = dataProvider,
                onClick = onClick,
                onLongClick = onLongClick,
                dashboardManager = dashboardManager,
                dashboardChart = dashboardChart,
                elevation = elevation,
                shape = shape,
                title = widgetConfig.showTitle,
                titleOverride = titleOverride,
                units = widgetConfig.showUnit,
                backgroundColor = widgetBackgroundColor,
                textColor = widgetTextColor
            )
        }

        "odometer" -> {
            DashboardWidgetItem(
                widget = if (widgetConfig.useMbCanVhal) {
                    widget.copy(dataKey = ODOMETER_CAN_FLOW_KEY)
                } else {
                    widget
                },
                dataProvider = dataProvider,
                onClick = onClick,
                onLongClick = onLongClick,
                dashboardManager = dashboardManager,
                dashboardChart = dashboardChart,
                elevation = elevation,
                shape = shape,
                title = widgetConfig.showTitle,
                titleOverride = titleOverride,
                units = widgetConfig.showUnit,
                backgroundColor = widgetBackgroundColor,
                textColor = widgetTextColor
            )
        }

        "fuelLevelPercentage" -> {
            DashboardWidgetItem(
                widget = if (widgetConfig.useMbCanVhal) {
                    widget.copy(dataKey = FUEL_LEVEL_PERCENTAGE_CAN_FLOW_KEY)
                } else {
                    widget
                },
                dataProvider = dataProvider,
                onClick = onClick,
                onLongClick = onLongClick,
                dashboardManager = dashboardManager,
                dashboardChart = dashboardChart,
                elevation = elevation,
                shape = shape,
                title = widgetConfig.showTitle,
                titleOverride = titleOverride,
                units = widgetConfig.showUnit,
                backgroundColor = widgetBackgroundColor,
                textColor = widgetTextColor
            )
        }

        "outsideTemperature" -> {
            DashboardWidgetItem(
                widget = if (widgetConfig.useMbCanVhal) {
                    widget.copy(dataKey = OUTSIDE_TEMPERATURE_CAN_FLOW_KEY)
                } else {
                    widget
                },
                dataProvider = dataProvider,
                onClick = onClick,
                onLongClick = onLongClick,
                dashboardManager = dashboardManager,
                dashboardChart = dashboardChart,
                elevation = elevation,
                shape = shape,
                title = widgetConfig.showTitle,
                titleOverride = titleOverride,
                units = widgetConfig.showUnit,
                backgroundColor = widgetBackgroundColor,
                textColor = widgetTextColor
            )
        }

        "currentFuelConsumption" -> {
            DashboardWidgetItem(
                widget = if (widgetConfig.useMbCanVhal) {
                    widget.copy(dataKey = CURRENT_FUEL_CONSUMPTION_CAN_FLOW_KEY)
                } else {
                    widget
                },
                dataProvider = dataProvider,
                onClick = onClick,
                onLongClick = onLongClick,
                dashboardManager = dashboardManager,
                dashboardChart = dashboardChart,
                elevation = elevation,
                shape = shape,
                title = widgetConfig.showTitle,
                titleOverride = titleOverride,
                units = widgetConfig.showUnit,
                backgroundColor = widgetBackgroundColor,
                textColor = widgetTextColor
            )
        }

        "distanceToNextMaintenance" -> {
            DashboardWidgetItem(
                widget = if (widgetConfig.useMbCanVhal) {
                    widget.copy(dataKey = DISTANCE_TO_NEXT_MAINTENANCE_CAN_FLOW_KEY)
                } else {
                    widget
                },
                dataProvider = dataProvider,
                onClick = onClick,
                onLongClick = onLongClick,
                dashboardManager = dashboardManager,
                dashboardChart = dashboardChart,
                elevation = elevation,
                shape = shape,
                title = widgetConfig.showTitle,
                titleOverride = titleOverride,
                units = widgetConfig.showUnit,
                backgroundColor = widgetBackgroundColor,
                textColor = widgetTextColor
            )
        }

        "distanceToFuelEmpty" -> {
            DashboardWidgetItem(
                widget = if (widgetConfig.useMbCanVhal) {
                    widget.copy(dataKey = DISTANCE_TO_FUEL_EMPTY_CAN_FLOW_KEY)
                } else {
                    widget
                },
                dataProvider = dataProvider,
                onClick = onClick,
                onLongClick = onLongClick,
                dashboardManager = dashboardManager,
                dashboardChart = dashboardChart,
                elevation = elevation,
                shape = shape,
                title = widgetConfig.showTitle,
                titleOverride = titleOverride,
                units = widgetConfig.showUnit,
                backgroundColor = widgetBackgroundColor,
                textColor = widgetTextColor
            )
        }

        "insideAirQuality" -> {
            DashboardWidgetItem(
                widget = if (widgetConfig.useMbCanVhal) {
                    widget.copy(dataKey = INSIDE_AIR_QUALITY_CAN_FLOW_KEY)
                } else {
                    widget
                },
                dataProvider = dataProvider,
                onClick = onClick,
                onLongClick = onLongClick,
                dashboardManager = dashboardManager,
                dashboardChart = dashboardChart,
                elevation = elevation,
                shape = shape,
                title = widgetConfig.showTitle,
                titleOverride = titleOverride,
                units = widgetConfig.showUnit,
                backgroundColor = widgetBackgroundColor,
                textColor = widgetTextColor
            )
        }

        "outsideAirQuality" -> {
            DashboardWidgetItem(
                widget = if (widgetConfig.useMbCanVhal) {
                    widget.copy(dataKey = OUTSIDE_AIR_QUALITY_CAN_FLOW_KEY)
                } else {
                    widget
                },
                dataProvider = dataProvider,
                onClick = onClick,
                onLongClick = onLongClick,
                dashboardManager = dashboardManager,
                dashboardChart = dashboardChart,
                elevation = elevation,
                shape = shape,
                title = widgetConfig.showTitle,
                titleOverride = titleOverride,
                units = widgetConfig.showUnit,
                backgroundColor = widgetBackgroundColor,
                textColor = widgetTextColor
            )
        }

        "steerAngle" -> {
            DashboardWidgetItem(
                widget = if (widgetConfig.useMbCanVhal) {
                    widget.copy(dataKey = STEER_ANGLE_CAN_FLOW_KEY)
                } else {
                    widget
                },
                dataProvider = dataProvider,
                onClick = onClick,
                onLongClick = onLongClick,
                dashboardManager = dashboardManager,
                dashboardChart = dashboardChart,
                elevation = elevation,
                shape = shape,
                title = widgetConfig.showTitle,
                titleOverride = titleOverride,
                units = widgetConfig.showUnit,
                backgroundColor = widgetBackgroundColor,
                textColor = widgetTextColor
            )
        }

        "steerSpeed" -> {
            DashboardWidgetItem(
                widget = if (widgetConfig.useMbCanVhal) {
                    widget.copy(dataKey = STEER_SPEED_CAN_FLOW_KEY)
                } else {
                    widget
                },
                dataProvider = dataProvider,
                onClick = onClick,
                onLongClick = onLongClick,
                dashboardManager = dashboardManager,
                dashboardChart = dashboardChart,
                elevation = elevation,
                shape = shape,
                title = widgetConfig.showTitle,
                titleOverride = titleOverride,
                units = widgetConfig.showUnit,
                backgroundColor = widgetBackgroundColor,
                textColor = widgetTextColor
            )
        }

        "espRelay0", "espRelay1" -> {
            val context = androidx.compose.ui.platform.LocalContext.current
            val channel = if (widget.dataKey == "espRelay0") 0 else 1
            val relayMode = widgetConfig.espRelayMode
            fun startRelayToggle() {
                context.startService(
                    android.content.Intent(context, vad.dashing.tbox.BackgroundService::class.java).apply {
                        action = vad.dashing.tbox.BackgroundService.ACTION_ESP_RELAY_TOGGLE
                        putExtra(vad.dashing.tbox.BackgroundService.EXTRA_ESP_RELAY_CHANNEL, channel)
                    }
                )
            }
            fun startRelayPulse() {
                context.startService(
                    android.content.Intent(context, vad.dashing.tbox.BackgroundService::class.java).apply {
                        action = vad.dashing.tbox.BackgroundService.ACTION_ESP_RELAY_PULSE
                        putExtra(vad.dashing.tbox.BackgroundService.EXTRA_ESP_RELAY_CHANNEL, channel)
                        putExtra(
                            vad.dashing.tbox.BackgroundService.EXTRA_ESP_RELAY_DURATION_MS,
                            EspRelayWidgetMode.BUTTON_PULSE_MS,
                        )
                    }
                )
            }
            DashboardWidgetItem(
                widget = widget,
                dataProvider = dataProvider,
                onClick = {
                    if (enableInnerInteractions && !isEditMode) {
                        when (relayMode) {
                            EspRelayWidgetMode.BUTTON -> startRelayPulse()
                            EspRelayWidgetMode.RELAY -> startRelayToggle()
                        }
                    } else {
                        onClick()
                    }
                },
                onDoubleClick = if (enableInnerInteractions && !isEditMode) {
                    { startRelayToggle() }
                } else {
                    {}
                },
                onLongClick = onLongClick,
                dashboardManager = dashboardManager,
                dashboardChart = false,
                elevation = elevation,
                shape = shape,
                title = widgetConfig.showTitle,
                titleOverride = titleOverride,
                units = widgetConfig.showUnit,
                backgroundColor = widgetBackgroundColor,
                textColor = widgetTextColor
            )
        }

        else -> {
            val remappedWidget = if (widgetConfig.useMbCanVhal) {
                when (widget.dataKey) {
                    "wheel1Pressure" -> widget.copy(dataKey = WHEEL1_PRESSURE_CAN_FLOW_KEY)
                    "wheel2Pressure" -> widget.copy(dataKey = WHEEL2_PRESSURE_CAN_FLOW_KEY)
                    "wheel3Pressure" -> widget.copy(dataKey = WHEEL3_PRESSURE_CAN_FLOW_KEY)
                    "wheel4Pressure" -> widget.copy(dataKey = WHEEL4_PRESSURE_CAN_FLOW_KEY)
                    "wheel1Temperature" -> widget.copy(dataKey = WHEEL1_TEMPERATURE_CAN_FLOW_KEY)
                    "wheel2Temperature" -> widget.copy(dataKey = WHEEL2_TEMPERATURE_CAN_FLOW_KEY)
                    "wheel3Temperature" -> widget.copy(dataKey = WHEEL3_TEMPERATURE_CAN_FLOW_KEY)
                    "wheel4Temperature" -> widget.copy(dataKey = WHEEL4_TEMPERATURE_CAN_FLOW_KEY)
                    else -> widget
                }
            } else {
                widget
            }
            DashboardWidgetItem(
                widget = remappedWidget,
                dataProvider = dataProvider,
                onClick = onClick,
                onLongClick = onLongClick,
                onDoubleClick = {
                    if (widget.dataKey == "motorHours") {
                        appDataViewModel.setMotorHours(0f)
                    }
                },
                dashboardManager = dashboardManager,
                dashboardChart = dashboardChart,
                elevation = elevation,
                shape = shape,
                title = widgetConfig.showTitle,
                titleOverride = titleOverride,
                units = widgetConfig.showUnit,
                dateTimeFormat = widgetConfig.dateTimeFormat,
                backgroundColor = widgetBackgroundColor,
                textColor = widgetTextColor
            )
        }
    }
    }
}
