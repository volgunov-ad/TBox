package vad.dashing.tbox.ui.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke

/** Simple top-down car silhouette (Tesla reference style). */
@Composable
fun LauncherCarSilhouette(
    modifier: Modifier = Modifier,
    bodyColor: Color = Color(0xFFE5E7EB),
    strokeColor: Color = Color(0xFF9CA3AF),
    accentColor: Color = LauncherColors.AccentBlue,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val bodyW = w * 0.42f
        val bodyH = h * 0.72f
        val left = cx - bodyW / 2f
        val top = h * 0.14f

        drawRoundRect(
            color = bodyColor,
            topLeft = Offset(left, top),
            size = Size(bodyW, bodyH),
            cornerRadius = CornerRadius(bodyW * 0.18f, bodyW * 0.18f),
        )
        drawRoundRect(
            color = strokeColor,
            topLeft = Offset(left, top),
            size = Size(bodyW, bodyH),
            cornerRadius = CornerRadius(bodyW * 0.18f, bodyW * 0.18f),
            style = Stroke(width = 3f),
        )
        // Windshield
        drawRoundRect(
            color = Color(0xFFCBD5E1),
            topLeft = Offset(left + bodyW * 0.12f, top + bodyH * 0.08f),
            size = Size(bodyW * 0.76f, bodyH * 0.22f),
            cornerRadius = CornerRadius(12f, 12f),
        )
        // Wheels
        val wheelR = bodyW * 0.11f
        listOf(0.18f, 0.82f).forEach { fx ->
            listOf(0.12f, 0.88f).forEach { fy ->
                drawCircle(
                    color = Color(0xFF374151),
                    radius = wheelR,
                    center = Offset(left + bodyW * fx, top + bodyH * fy),
                )
            }
        }
        // Proximity arc (reference orange arc)
        drawArc(
            color = accentColor.copy(alpha = 0.35f),
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(cx - w * 0.35f, top - h * 0.05f),
            size = Size(w * 0.7f, h * 0.45f),
            style = Stroke(width = 6f),
        )
    }
}
