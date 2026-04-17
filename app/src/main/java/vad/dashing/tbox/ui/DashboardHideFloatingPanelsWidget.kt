package vad.dashing.tbox.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min
import vad.dashing.tbox.normalizeWidgetScale

@Composable
fun DashboardHideFloatingPanelsWidgetItem(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDoubleClick: () -> Unit,
    elevation: Dp,
    shape: Dp,
    textColor: Color,
    backgroundColor: Color,
) {
    DashboardWidgetScaffold(
        onClick = onClick,
        onLongClick = onLongClick,
        onDoubleClick = onDoubleClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor
    ) { _, resolvedTextColor ->
        val textScale = normalizeWidgetScale(LocalWidgetTextScale.current)
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val pad = 4.dp.toPx()
                val maxRadius = min(size.width, size.height) / 2f - pad
                val radius = (maxRadius * textScale).coerceIn(2f, maxRadius.coerceAtLeast(2f))
                drawCircle(
                    color = resolvedTextColor,
                    radius = radius,
                    center = Offset(size.width / 2f, size.height / 2f)
                )
            }
        }
    }
}
