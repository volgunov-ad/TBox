package vad.dashing.tbox.ui.launcher

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.cos
import kotlin.math.sin

data class LauncherCarTopViewState(
    val speedKmh: Float = 0f,
    val steerAngleDeg: Float = 0f,
    val wheelPressures: List<Float?> = List(4) { null },
    val doorFlOpen: Boolean = false,
    val doorFrOpen: Boolean = false,
    val doorRlOpen: Boolean = false,
    val doorRrOpen: Boolean = false,
    val tailgateOpen: Boolean = false,
    val highlightWheels: Boolean = false,
    val highlightBody: Boolean = false,
    val showTapPulse: Boolean = false,
)

private const val DRIVE_SPEED_THRESHOLD_KMH = 3f
private const val MAX_STEER_VISUAL_DEG = 45f

@Composable
fun LauncherCarTopView(
    state: LauncherCarTopViewState = LauncherCarTopViewState(),
    modifier: Modifier = Modifier,
    bodyColor: Color = Color(0xFFD1D5DB),
    strokeColor: Color = Color(0xFF6B7280),
    accentColor: Color = LauncherColors.AccentCyan,
    onClick: (() -> Unit)? = null,
) {
    val driveTarget = if (state.speedKmh > DRIVE_SPEED_THRESHOLD_KMH) 1f else 0f
    val driveBlend by animateFloatAsState(
        targetValue = driveTarget,
        animationSpec = tween(900),
        label = "driveBlend",
    )

    var roadPhase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(state.speedKmh) {
        while (true) {
            withFrameMillis {
                if (state.speedKmh > 0.5f) {
                    roadPhase += state.speedKmh * 0.035f
                }
            }
        }
    }

    val pulseTransition = rememberInfiniteTransition(label = "carTopPulse")
    val pulse by pulseTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )
    val glowAlpha by pulseTransition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseGlow",
    )

    val clickModifier = if (onClick != null) {
        Modifier.pointerInput(onClick) {
            detectTapGestures(onTap = { onClick() })
        }
    } else {
        Modifier
    }

    Box(modifier = modifier.then(clickModifier)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (driveBlend > 0.02f) {
                drawDrivingBackground(
                    blend = driveBlend,
                    roadPhase = roadPhase,
                    steerDeg = state.steerAngleDeg.coerceIn(-MAX_STEER_VISUAL_DEG, MAX_STEER_VISUAL_DEG),
                    speedKmh = state.speedKmh,
                )
            }

            if (driveBlend < 0.98f) {
                drawTopDownCar(
                    state = state,
                    blend = 1f - driveBlend,
                    bodyColor = bodyColor,
                    strokeColor = strokeColor,
                    accentColor = accentColor,
                    pulse = pulse,
                    glowAlpha = glowAlpha,
                )
            }

            if (driveBlend > 0.02f) {
                drawRearDrivingCar(
                    state = state,
                    blend = driveBlend,
                    bodyColor = bodyColor,
                    strokeColor = strokeColor,
                    accentColor = accentColor,
                    steerDeg = state.steerAngleDeg.coerceIn(-MAX_STEER_VISUAL_DEG, MAX_STEER_VISUAL_DEG),
                )
            }
        }
    }
}

private fun DrawScope.drawDrivingBackground(
    blend: Float,
    roadPhase: Float,
    steerDeg: Float,
    speedKmh: Float,
) {
    val w = size.width
    val h = size.height
    val horizonY = h * (0.22f + 0.08f * (1f - blend))
    val steerNorm = (steerDeg / MAX_STEER_VISUAL_DEG).coerceIn(-1f, 1f)
    val bottomOffsetX = steerNorm * w * 0.18f * blend
    val topOffsetX = steerNorm * w * 0.06f * blend
    val vanishX = w / 2f + topOffsetX

    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF0B1220).copy(alpha = 0.95f * blend),
                Color(0xFF111827).copy(alpha = 0.85f * blend),
                Color(0xFF1F2937).copy(alpha = 0.7f * blend),
            ),
            startY = 0f,
            endY = horizonY,
        ),
        size = size,
    )

    val roadPath = Path().apply {
        moveTo(w / 2f - w * 0.46f + bottomOffsetX, h)
        lineTo(vanishX - w * 0.09f, horizonY)
        lineTo(vanishX + w * 0.09f, horizonY)
        lineTo(w / 2f + w * 0.46f + bottomOffsetX, h)
        close()
    }
    drawPath(roadPath, Color(0xFF374151).copy(alpha = 0.92f * blend))

    val edgeColor = Color(0xFF9CA3AF).copy(alpha = 0.55f * blend)
    drawLine(
        color = edgeColor,
        start = Offset(w / 2f - w * 0.46f + bottomOffsetX, h),
        end = Offset(vanishX - w * 0.09f, horizonY),
        strokeWidth = 2.5f,
    )
    drawLine(
        color = edgeColor,
        start = Offset(w / 2f + w * 0.46f + bottomOffsetX, h),
        end = Offset(vanishX + w * 0.09f, horizonY),
        strokeWidth = 2.5f,
    )

    val dashSpacing = (28f + speedKmh * 0.35f).coerceIn(18f, 80f)
    val phase = roadPhase % dashSpacing
    var y = h - phase
    val laneColor = Color(0xFFE5E7EB).copy(alpha = 0.75f * blend)
    while (y > horizonY) {
        val t = ((y - horizonY) / (h - horizonY)).coerceIn(0f, 1f)
        val laneHalf = (w * 0.09f) * (1f - t) + (w * 0.02f) * t
        val centerX = vanishX + bottomOffsetX * (1f - t)
        val dashLen = (12f + t * 22f).coerceAtLeast(8f)
        drawLine(
            color = laneColor,
            start = Offset(centerX - laneHalf * 0.12f, y),
            end = Offset(centerX - laneHalf * 0.12f, y - dashLen),
            strokeWidth = 2.5f,
        )
        drawLine(
            color = laneColor,
            start = Offset(centerX + laneHalf * 0.12f, y),
            end = Offset(centerX + laneHalf * 0.12f, y - dashLen),
            strokeWidth = 2.5f,
        )
        y -= dashSpacing * (0.35f + 0.65f * t)
    }
}

private fun DrawScope.drawTopDownCar(
    state: LauncherCarTopViewState,
    blend: Float,
    bodyColor: Color,
    strokeColor: Color,
    accentColor: Color,
    pulse: Float,
    glowAlpha: Float,
) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val scale = (if (state.showTapPulse) pulse else 1f) * (0.55f + 0.45f * blend)
    val bodyW = w * 0.44f * scale
    val bodyH = h * 0.78f * scale
    val left = cx - bodyW / 2f
    val top = h * 0.11f + (h * 0.78f - bodyH) / 2f

    if (state.showTapPulse && blend > 0.5f) {
        drawRoundRect(
            color = accentColor.copy(alpha = glowAlpha * blend),
            topLeft = Offset(left - 12f, top - 12f),
            size = Size(bodyW + 24f, bodyH + 24f),
            cornerRadius = CornerRadius(bodyW * 0.2f),
        )
    }

    val bodyPath = carBodyPath(left, top, bodyW, bodyH)
    drawPath(bodyPath, bodyColor.copy(alpha = blend))
    drawPath(bodyPath, strokeColor.copy(alpha = 0.85f * blend), style = Stroke(width = 2.5f))

    drawRoundRect(
        color = Color(0xFF94A3B8).copy(alpha = 0.55f * blend),
        topLeft = Offset(left + bodyW * 0.14f, top + bodyH * 0.1f),
        size = Size(bodyW * 0.72f, bodyH * 0.38f),
        cornerRadius = CornerRadius(10f),
    )
    drawRoundRect(
        color = Color(0xFF64748B).copy(alpha = 0.35f * blend),
        topLeft = Offset(left + bodyW * 0.28f, top + bodyH * 0.16f),
        size = Size(bodyW * 0.44f, bodyH * 0.2f),
        cornerRadius = CornerRadius(6f),
    )

    if (state.highlightBody) {
        drawPath(
            bodyPath,
            accentColor.copy(alpha = 0.35f * blend),
            style = Stroke(width = 5f),
        )
    }

    val wheelCenters = listOf(
        Offset(left + bodyW * 0.16f, top + bodyH * 0.2f),
        Offset(left + bodyW * 0.84f, top + bodyH * 0.2f),
        Offset(left + bodyW * 0.16f, top + bodyH * 0.8f),
        Offset(left + bodyW * 0.84f, top + bodyH * 0.8f),
    )
    wheelCenters.forEachIndexed { index, center ->
        val pressure = state.wheelPressures.getOrNull(index)
        val wheelColor = wheelPressureColor(pressure)
        val r = bodyW * 0.09f
        if (state.highlightWheels) {
            drawCircle(
                color = accentColor.copy(alpha = 0.25f * blend),
                radius = r * 1.45f,
                center = center,
            )
        }
        drawCircle(color = Color(0xFF1F2937).copy(alpha = blend), radius = r, center = center)
        drawCircle(color = wheelColor.copy(alpha = blend), radius = r * 0.42f, center = center)
    }

    drawTopDownDoors(
        state = state,
        left = left,
        top = top,
        bodyW = bodyW,
        bodyH = bodyH,
        strokeColor = strokeColor,
        accentColor = accentColor,
        alpha = blend,
    )
}

private fun DrawScope.drawTopDownDoors(
    state: LauncherCarTopViewState,
    left: Float,
    top: Float,
    bodyW: Float,
    bodyH: Float,
    strokeColor: Color,
    accentColor: Color,
    alpha: Float,
) {
    val doorStroke = if (state.highlightBody) accentColor else strokeColor.copy(alpha = 0.7f)
    val swing = bodyW * 0.14f

    fun drawDoor(
        hinge: Offset,
        open: Boolean,
        swingRight: Boolean,
    ) {
        val angle = if (open) (if (swingRight) 55f else -55f) else 0f
        val length = bodyH * 0.2f
        withTransform({
            rotate(angle, hinge)
        }) {
            drawLine(
                color = if (open) accentColor.copy(alpha = alpha) else doorStroke.copy(alpha = alpha),
                start = hinge,
                end = hinge + Offset(0f, length),
                strokeWidth = if (open) 4f else 2f,
            )
        }
        if (open) {
            val rad = Math.toRadians(angle.toDouble())
            val tip = hinge + Offset(
                (sin(rad) * swing).toFloat(),
                (cos(rad) * length).toFloat(),
            )
            drawLine(
                color = accentColor.copy(alpha = 0.5f * alpha),
                start = hinge,
                end = tip,
                strokeWidth = 1.5f,
            )
        }
    }

    drawDoor(Offset(left + bodyW * 0.06f, top + bodyH * 0.42f), state.doorFlOpen, swingRight = false)
    drawDoor(Offset(left + bodyW * 0.94f, top + bodyH * 0.42f), state.doorFrOpen, swingRight = true)
    drawDoor(Offset(left + bodyW * 0.06f, top + bodyH * 0.62f), state.doorRlOpen, swingRight = false)
    drawDoor(Offset(left + bodyW * 0.94f, top + bodyH * 0.62f), state.doorRrOpen, swingRight = true)

    val tailY = top + bodyH * 0.88f
    val tailOpenOffset = if (state.tailgateOpen) bodyH * 0.08f else 0f
    drawLine(
        color = if (state.tailgateOpen) accentColor.copy(alpha = alpha) else strokeColor.copy(alpha = 0.6f * alpha),
        start = Offset(left + bodyW * 0.25f, tailY),
        end = Offset(left + bodyW * 0.75f, tailY + tailOpenOffset),
        strokeWidth = if (state.tailgateOpen) 4f else 2f,
    )
}

private fun DrawScope.drawRearDrivingCar(
    state: LauncherCarTopViewState,
    blend: Float,
    bodyColor: Color,
    strokeColor: Color,
    accentColor: Color,
    steerDeg: Float,
) {
    val w = size.width
    val h = size.height
    val carW = w * 0.36f * blend
    val carH = h * 0.2f * blend
    val cx = w / 2f + steerDeg * 0.35f * blend
    val cy = h * 0.78f
    val left = cx - carW / 2f
    val top = cy - carH / 2f
    val lean = (steerDeg / MAX_STEER_VISUAL_DEG).coerceIn(-1f, 1f) * 6f

    withTransform({
        rotate(lean, Offset(cx, cy))
    }) {
        val bodyPath = Path().apply {
            moveTo(left + carW * 0.12f, top)
            lineTo(left + carW * 0.88f, top)
            lineTo(left + carW, top + carH * 0.35f)
            lineTo(left + carW * 0.92f, top + carH)
            lineTo(left + carW * 0.08f, top + carH)
            lineTo(left, top + carH * 0.35f)
            close()
        }
        drawPath(bodyPath, bodyColor.copy(alpha = blend))
        drawPath(bodyPath, strokeColor.copy(alpha = 0.9f * blend), style = Stroke(width = 2f))

        drawRoundRect(
            color = Color(0xFF94A3B8).copy(alpha = 0.5f * blend),
            topLeft = Offset(left + carW * 0.18f, top + carH * 0.08f),
            size = Size(carW * 0.64f, carH * 0.42f),
            cornerRadius = CornerRadius(8f),
        )

        val tailLightY = top + carH * 0.72f
        listOf(
            Offset(left + carW * 0.18f, tailLightY),
            Offset(left + carW * 0.82f, tailLightY),
        ).forEach { center ->
            drawRoundRect(
                color = Color(0xFFEF4444).copy(alpha = 0.85f * blend),
                topLeft = Offset(center.x - carW * 0.06f, center.y - carH * 0.05f),
                size = Size(carW * 0.12f, carH * 0.1f),
                cornerRadius = CornerRadius(3f),
            )
        }

        if (state.doorFlOpen || state.doorRlOpen) {
            drawLine(
                color = accentColor.copy(alpha = 0.8f * blend),
                start = Offset(left, top + carH * 0.45f),
                end = Offset(left - carW * 0.08f, top + carH * 0.5f),
                strokeWidth = 3f,
            )
        }
        if (state.doorFrOpen || state.doorRrOpen) {
            drawLine(
                color = accentColor.copy(alpha = 0.8f * blend),
                start = Offset(left + carW, top + carH * 0.45f),
                end = Offset(left + carW + carW * 0.08f, top + carH * 0.5f),
                strokeWidth = 3f,
            )
        }
        if (state.tailgateOpen) {
            drawLine(
                color = accentColor.copy(alpha = 0.8f * blend),
                start = Offset(left + carW * 0.3f, top + carH),
                end = Offset(left + carW * 0.7f, top + carH + carH * 0.12f),
                strokeWidth = 3f,
            )
        }
    }
}

private fun carBodyPath(left: Float, top: Float, bodyW: Float, bodyH: Float): Path =
    Path().apply {
        moveTo(left + bodyW * 0.22f, top)
        lineTo(left + bodyW * 0.78f, top)
        quadraticTo(left + bodyW, top, left + bodyW, top + bodyH * 0.08f)
        lineTo(left + bodyW, top + bodyH * 0.92f)
        quadraticTo(left + bodyW, top + bodyH, left + bodyW * 0.78f, top + bodyH)
        lineTo(left + bodyW * 0.22f, top + bodyH)
        quadraticTo(left, top + bodyH, left, top + bodyH * 0.92f)
        lineTo(left, top + bodyH * 0.08f)
        quadraticTo(left, top, left + bodyW * 0.22f, top)
        close()
    }

private fun wheelPressureColor(pressure: Float?): Color = when {
    pressure == null -> Color(0xFF9CA3AF)
    pressure < 1.8f || pressure > 3.2f -> Color(0xFFEF4444)
    pressure < 2.1f || pressure > 2.9f -> Color(0xFFF59E0B)
    else -> Color(0xFF22C55E)
}
