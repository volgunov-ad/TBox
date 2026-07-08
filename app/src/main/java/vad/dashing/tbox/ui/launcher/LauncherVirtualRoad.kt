package vad.dashing.tbox.ui.launcher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.exp
import kotlin.math.pow

private const val DRIVE_SPEED_THRESHOLD_KMH = 3f

@Composable
fun LauncherVirtualRoad(
    speedKmh: Float,
    steerAngleDeg: Float,
    modifier: Modifier = Modifier,
    steerPreview: Boolean = false,
) {
    val driveTarget = if (speedKmh > DRIVE_SPEED_THRESHOLD_KMH) 1f else if (steerPreview) 1f else 0f
    val driveBlend by animateFloatAsState(
        targetValue = driveTarget,
        animationSpec = tween(280),
        label = "roadDriveBlend",
    )

    val steerTargetRef = rememberUpdatedState(LauncherSteerVisual.visualSteerDeg(steerAngleDeg))
    var visualSteer by remember { mutableFloatStateOf(0f) }
    var lastFrameMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { frameMs ->
                val dt = if (lastFrameMs == 0L) {
                    0.016f
                } else {
                    ((frameMs - lastFrameMs).coerceAtMost(50L)) / 1000f
                }
                lastFrameMs = frameMs
                val target = steerTargetRef.value
                val alpha = 1f - exp(-dt * LauncherSteerVisual.STEER_SMOOTH_RATE)
                visualSteer += (target - visualSteer) * alpha
            }
        }
    }

    var roadPhase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(speedKmh, driveBlend) {
        while (true) {
            withFrameMillis {
                if (speedKmh > 0.5f && driveBlend > 0.02f) {
                    roadPhase += speedKmh * 0.03f
                }
            }
        }
    }

    if (driveBlend <= 0.01f) return

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = driveBlend },
    ) {
        drawVirtualRoad(
            roadPhase = roadPhase,
            steerDeg = visualSteer,
            speedKmh = speedKmh,
        )
    }
}

private fun DrawScope.drawVirtualRoad(
    roadPhase: Float,
    steerDeg: Float,
    speedKmh: Float,
) {
    val w = size.width
    val h = size.height
    val horizonY = h * 0.24f
    val steerNorm = (steerDeg / LauncherSteerVisual.MAX_VISUAL_DEG).coerceIn(-1f, 1f)
    val vanishX = w / 2f
    fun halfWidthAt(t: Float): Float =
        (w * 0.075f) * (1f - t).pow(1.25f) + (w * 0.42f) * t.pow(1.05f)
    fun laneOffsetAt(t: Float): Float = halfWidthAt(t) * 0.34f
    fun yAt(t: Float): Float = horizonY + (h - horizonY) * t
    fun centerXAt(t: Float): Float =
        vanishX + steerNorm * w * 0.20f * (1f - t).pow(1.55f)

    val roadPath = Path().apply {
        moveTo(centerXAt(1f) - halfWidthAt(1f), yAt(1f))
        for (i in 9 downTo 0) {
            val t = i / 9f
            lineTo(centerXAt(t) - halfWidthAt(t), yAt(t))
        }
        for (i in 0..9) {
            val t = i / 9f
            lineTo(centerXAt(t) + halfWidthAt(t), yAt(t))
        }
        lineTo(centerXAt(1f) + halfWidthAt(1f), yAt(1f))
        close()
    }
    drawPath(
        roadPath,
        brush = Brush.verticalGradient(
            colors = listOf(
                LauncherColors.AccentBlue.copy(alpha = 0.10f),
                LauncherColors.AccentCyan.copy(alpha = 0.08f),
                LauncherColors.CardDark.copy(alpha = 0.48f),
            ),
            startY = horizonY,
            endY = h,
        ),
    )

    val edgeColor = LauncherColors.AccentBlue.copy(alpha = 0.32f)
    drawCurvedLine(
        color = edgeColor,
        xAt = { t -> centerXAt(t) - halfWidthAt(t) },
        yAt = ::yAt,
    )
    drawCurvedLine(
        color = edgeColor,
        xAt = { t -> centerXAt(t) + halfWidthAt(t) },
        yAt = ::yAt,
    )

    val dashSpacing = (32f + speedKmh * 0.28f).coerceIn(20f, 72f)
    val phase = roadPhase % dashSpacing
    var y = horizonY + phase
    val laneColor = LauncherColors.TextSecondary.copy(alpha = 0.42f)
    while (y < h) {
        val t = ((y - horizonY) / (h - horizonY)).coerceIn(0f, 1f)
        val dashLen = (10f + t * 18f).coerceAtLeast(7f)
        val nextT = (((y + dashLen) - horizonY) / (h - horizonY)).coerceIn(0f, 1f)
        listOf(-1f, 1f).forEach { side ->
            drawLine(
                color = laneColor,
                start = Offset(centerXAt(t) + side * laneOffsetAt(t), y),
                end = Offset(centerXAt(nextT) + side * laneOffsetAt(nextT), y + dashLen),
                strokeWidth = 2f,
            )
        }
        y += dashSpacing * (0.4f + 0.6f * t)
    }
}

private fun DrawScope.drawCurvedLine(
    color: androidx.compose.ui.graphics.Color,
    xAt: (Float) -> Float,
    yAt: (Float) -> Float,
) {
    var prev = Offset(xAt(1f), yAt(1f))
    for (i in 8 downTo 0) {
        val t = i / 8f
        val next = Offset(xAt(t), yAt(t))
        drawLine(color = color, start = prev, end = next, strokeWidth = 2f)
        prev = next
    }
}
