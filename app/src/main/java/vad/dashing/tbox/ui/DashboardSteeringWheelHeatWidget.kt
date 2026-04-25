package vad.dashing.tbox.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.R
import vad.dashing.tbox.mbcan.MbCanBinaryState
import vad.dashing.tbox.mbcan.MbCanRepository

private val SteeringHeatOnColor = Color(0xFFFF9800)

@Composable
fun DashboardSteeringWheelHeatWidgetItem(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    elevation: Dp,
    shape: Dp,
    textColor: Color,
    backgroundColor: Color,
) {
    val state by MbCanRepository.steeringWheelHeatState.collectAsStateWithLifecycle()
    val iconColor = if (state is MbCanBinaryState.On) SteeringHeatOnColor else textColor
    DashboardWidgetScaffold(
        onClick = onClick,
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor
    ) { _, _ ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_widget_steering_wheel_heat),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp),
                colorFilter = ColorFilter.tint(iconColor)
            )
        }
    }
}

