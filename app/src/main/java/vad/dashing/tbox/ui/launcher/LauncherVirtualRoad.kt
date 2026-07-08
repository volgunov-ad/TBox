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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.exp
import kotlin.math.pow

private const val DRIVE_SPEED_THRESHOLD_KMH = 3f

@Composable
fun LauncherVirtualRoad(
    speedKmh: Float,
    steerAngleDeg: Float,
    adas: LauncherAdasState = LauncherAdasState(),
    modifier: Modifier = Modifier,
    steerPreview: Boolean = false,
) {
    val driveTarget = when {
        speedKmh > DRIVE_SPEED_THRESHOLD_KMH -> 1f
        steerPreview -> 1f
        adas.frontObject.valid || adas.hasAnyAssist -> 1f
        else -> 0f
    }
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
            adas = adas,
        )
    }
}

private fun DrawScope.drawVirtualRoad(
    roadPhase: Float,
    steerDeg: Float,
    speedKmh: Float,
    adas: LauncherAdasState,
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
    drawCurvedLine(color = edgeColor, xAt = { t -> centerXAt(t) - halfWidthAt(t) }, yAt = ::yAt)
    drawCurvedLine(color = edgeColor, xAt = { t -> centerXAt(t) + halfWidthAt(t) }, yAt = ::yAt)

    drawAdasLaneAssist(
        adas = adas,
        centerXAt = ::centerXAt,
        halfWidthAt = ::halfWidthAt,
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

    adas.frontObject.displayDistanceM?.let { distanceM ->
        if (adas.frontObject.valid) {
            drawFrontObject(
                adas = adas,
                distanceM = distanceM,
                centerXAt = ::centerXAt,
                halfWidthAt = ::halfWidthAt,
                yAt = ::yAt,
            )
        }
    }
}

private fun DrawScope.drawAdasLaneAssist(
    adas: LauncherAdasState,
    centerXAt: (Float) -> Float,
    halfWidthAt: (Float) -> Float,
    yAt: (Float) -> Float,
) {
    fun laneColor(side: LauncherAdasLaneVisualization, warning: Boolean): Color = when {
        warning -> Color(0xFFEF4444).copy(alpha = 0.72f)
        side == LauncherAdasLaneVisualization.Intervention -> LauncherColors.AccentCyan.copy(alpha = 0.55f)
        side == LauncherAdasLaneVisualization.Tracking -> LauncherColors.AccentBlue.copy(alpha = 0.42f)
        else -> Color.Transparent
    }
    listOf(
        adas.leftLane to -1f,
        adas.rightLane to 1f,
    ).forEach { (lane, side) ->
        if (lane == LauncherAdasLaneVisualization.Hidden) return@forEach
        val warning = lane == LauncherAdasLaneVisualization.Warning
        val color = laneColor(lane, warning)
        if (color == Color.Transparent) return@forEach
        var prev = Offset(
            centerXAt(1f) + side * (halfWidthAt(1f) - 6f),
            yAt(1f),
        )
        for (i in 8 downTo 0) {
            val t = i / 8f
            val next = Offset(
                centerXAt(t) + side * (halfWidthAt(t) - 6f),
                yAt(t),
            )
            drawLine(color = color, start = prev, end = next, strokeWidth = if (warning) 3.5f else 2.5f)
            prev = next
        }
    }
}

private fun DrawScope.drawFrontObject(
    adas: LauncherAdasState,
    distanceM: Int,
    centerXAt: (Float) -> Float,
    halfWidthAt: (Float) -> Float,
    yAt: (Float) -> Float,
) {
    val depth = distanceToRoadDepth(distanceM)
    val cx = centerXAt(depth)
    val cy = yAt(depth)
    val roadHalf = halfWidthAt(depth)
    val alert = adas.fcwActive || adas.distanceWarning || adas.aebHint || adas.accTakeOver
    val baseColor = if (alert) Color(0xFFEF4444) else LauncherColors.AccentCyan
    val fillColor = baseColor.copy(alpha = if (alert) 0.55f else 0.42f)
    val strokeColor = baseColor.copy(alpha = 0.88f)

    val (objW, objH) = objectSizeForType(adas.frontObject.type, roadHalf)
    val topLeft = Offset(cx - objW / 2f, cy - objH)
    drawRoundRect(
        color = fillColor,
        topLeft = topLeft,
        size = Size(objW, objH),
        cornerRadius = CornerRadius(objW * 0.18f, objW * 0.18f),
    )
    drawRoundRect(
        color = strokeColor,
        topLeft = topLeft,
        size = Size(objW, objH),
        cornerRadius = CornerRadius(objW * 0.18f, objW * 0.18f),
        style = Stroke(width = 2f),
    )
    when (adas.frontObject.type) {
        LauncherAdasFrontObjectType.Pedestrian -> {
            drawCircle(
                color = strokeColor,
                radius = objW * 0.22f,
                center = Offset(cx, cy - objH * 0.72f),
            )
        }
        LauncherAdasFrontObjectType.Motorcycle, LauncherAdasFrontObjectType.Bicycle -> {
            drawCircle(color = strokeColor, radius = objW * 0.14f, center = Offset(cx - objW * 0.2f, cy - objH * 0.1f))
            drawCircle(color = strokeColor, radius = objW * 0.14f, center = Offset(cx + objW * 0.2f, cy - objH * 0.1f))
        }
        else -> Unit
    }
}

private fun objectSizeForType(type: LauncherAdasFrontObjectType, roadHalf: Float): Pair<Float, Float> {
    val base = roadHalf * 0.34f
    return when (type) {
        LauncherAdasFrontObjectType.Truck, LauncherAdasFrontObjectType.Bus ->
            base * 1.05f to base * 1.35f
        LauncherAdasFrontObjectType.Motorcycle, LauncherAdasFrontObjectType.Bicycle ->
            base * 0.55f to base * 0.85f
        LauncherAdasFrontObjectType.Pedestrian ->
            base * 0.42f to base * 0.95f
        else -> base * 0.82f to base * 1.0f
    }
}

private fun DrawScope.drawCurvedLine(
    color: Color,
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
