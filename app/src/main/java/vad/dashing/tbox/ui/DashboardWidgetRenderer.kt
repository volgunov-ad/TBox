package vad.dashing.tbox.ui

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
    widgetTextColor: Color,
    widgetBackgroundColor: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMusicSelectedPlayerChange: (String) -> Unit,
    onRestartRequested: () -> Unit,
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
                shape = shape,
                units = widgetConfig.showUnit,
                textColor = widgetTextColor,
                backgroundColor = widgetBackgroundColor
            )
        }

        "musicWidget" -> {
            DashboardMusicWidgetItem(
                widget = widget,
                widgetConfig = widgetConfig,
                title = widgetConfig.showTitle,
                onClick = onClick,
                onLongClick = onLongClick,
                onSelectedPlayerChange = onMusicSelectedPlayerChange,
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
                shape = shape,
                units = widgetConfig.showUnit,
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
                shape = shape,
                title = widgetConfig.showTitle,
                units = widgetConfig.showUnit,
                backgroundColor = widgetBackgroundColor,
                textColor = widgetTextColor
            )
        }
    }
}
