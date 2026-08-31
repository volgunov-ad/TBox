package vad.dashing.tbox.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.DashboardManager
import vad.dashing.tbox.DashboardWidget
import vad.dashing.tbox.mbcan.UniversalCanRepository

@Composable
fun DashboardGasBrakeWidgetItem(
    widget: DashboardWidget,
    dataProvider: DataProvider,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onDoubleClick: () -> Unit = {},
    dashboardManager: DashboardManager,
    dashboardChart: Boolean,
    elevation: Dp = 4.dp,
    shape: Dp = 12.dp,
    title: Boolean = true,
    titleOverride: String = "",
    units: Boolean = true,
    textColor: Color? = null,
    backgroundColor: Color? = null,
) {
    val brakePressed by UniversalCanRepository.brakePedalPressedState.collectAsStateWithLifecycle()
    DashboardWidgetItem(
        widget = widget,
        dataProvider = dataProvider,
        onClick = onClick,
        onLongClick = onLongClick,
        onDoubleClick = onDoubleClick,
        dashboardManager = dashboardManager,
        dashboardChart = dashboardChart,
        elevation = elevation,
        shape = shape,
        title = title,
        titleOverride = titleOverride,
        units = units,
        backgroundColor = backgroundColor,
        textColor = if (brakePressed == true) Color.Red else textColor,
    )
}
