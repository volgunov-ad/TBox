package vad.dashing.tbox.ui

import android.appwidget.AppWidgetHost
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import vad.dashing.tbox.AppDataViewModel
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.DashboardManager
import vad.dashing.tbox.DashboardWidget
import vad.dashing.tbox.FloatingDashboardWidgetConfig
import vad.dashing.tbox.TboxViewModel
import vad.dashing.tbox.APP_LAUNCHER_WIDGET_DATA_KEY
import vad.dashing.tbox.WidgetsRepository

@Composable
fun DashboardWidgetRenderer(
    widget: DashboardWidget,
    widgetConfig: FloatingDashboardWidgetConfig,
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
    onRestartRequested: () -> Unit,
    externalWidgetHost: AppWidgetHost? = null,
    isEditMode: Boolean = false,
    elevation: Dp = 4.dp,
    shape: Dp = 12.dp,
    enableMusicInnerInteractions: Boolean = true
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
                backgroundColor = widgetBackgroundColor,
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
                elevation = elevation,
                shape = shape,
                backgroundColor = widgetBackgroundColor,
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
                scale = widgetConfig.scale
            )
        }

        "locWidget" -> {
            DashboardLocWidgetItem(
                widget = widget,
                onClick = onClick,
                onLongClick = onLongClick,
                viewModel = tboxViewModel,
                elevation = elevation,
                shape = shape,
                backgroundColor = widgetBackgroundColor,
                textColor = widgetTextColor,
                scale = widgetConfig.scale
            )
        }

        "voltage+engineTemperatureWidget" -> {
            DashboardVoltEngTempWidgetItem(
                widget = widget,
                onClick = onClick,
                onLongClick = onLongClick,
                canViewModel = canViewModel,
                elevation = elevation,
                shape = shape,
                units = widgetConfig.showUnit,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor
            )
        }

        "gearBoxWidget" -> {
            DashboardGearBoxWidgetItem(
                widget = widget,
                onClick = onClick,
                onLongClick = onLongClick,
                canViewModel = canViewModel,
                elevation = elevation,
                shape = shape,
                units = widgetConfig.showUnit,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor
            )
        }

        "wheelsPressureWidget" -> {
            DashboardWheelsPressureWidgetItem(
                widget = widget,
                onClick = onClick,
                onLongClick = onLongClick,
                canViewModel = canViewModel,
                elevation = elevation,
                shape = shape,
                units = widgetConfig.showUnit,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor
            )
        }

        "wheelsPressureTemperatureWidget" -> {
            DashboardWheelsPressureTemperatureWidgetItem(
                widget = widget,
                onClick = onClick,
                onLongClick = onLongClick,
                canViewModel = canViewModel,
                elevation = elevation,
                shape = shape,
                units = widgetConfig.showUnit,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor
            )
        }

        "tempInOutWidget" -> {
            DashboardTempInOutWidgetItem(
                widget = widget,
                onClick = onClick,
                onLongClick = onLongClick,
                canViewModel = canViewModel,
                elevation = elevation,
                shape = shape,
                units = widgetConfig.showUnit,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor
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
                backgroundColor = widgetBackgroundColor
            )
        }

        APP_LAUNCHER_WIDGET_DATA_KEY -> {
            DashboardAppLauncherWidgetItem(
                widget = widget,
                packageName = widgetConfig.launcherAppPackage,
                showTitle = widgetConfig.showTitle,
                onClick = onClick,
                onLongClick = onLongClick,
                elevation = elevation,
                shape = shape,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor
            )
        }

        "musicWidget" -> {
            DashboardMusicWidgetItem(
                widget = widget,
                widgetConfig = widgetConfig,
                canViewModel = canViewModel,
                title = widgetConfig.showTitle,
                onClick = onClick,
                onLongClick = onLongClick,
                onSelectedPlayerChange = onMusicSelectedPlayerChange,
                elevation = elevation,
                shape = shape,
                enableInnerInteractions = enableMusicInnerInteractions,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor
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
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor
            )
        }

        "activeTripWidget", "activeTripWidgetSimple" -> {
            DashboardActiveTripWidgetItem(
                widget = widget,
                appDataViewModel = appDataViewModel,
                showTitle = widgetConfig.showTitle,
                onClick = onClick,
                onLongClick = onLongClick,
                onDoubleClick = {
                    if (appDataViewModel.activeTrip.value?.isActive == true) {
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

        else -> {
            DashboardWidgetItem(
                widget = widget,
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
                units = widgetConfig.showUnit,
                backgroundColor = widgetBackgroundColor,
                textColor = widgetTextColor
            )
        }
    }
}
