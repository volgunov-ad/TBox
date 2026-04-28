package vad.dashing.tbox.ui

import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.R
import vad.dashing.tbox.mbcan.MbCanBinaryState
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId
import vad.dashing.tbox.mbcan.MbCanRepository
import vad.dashing.tbox.mbcan.MbCanSeatModeState

private val SeatHeatOnColor = Color(0xFFFF9800)
private val SeatVentOnColor = Color(0xFF4FC3F7)

private const val SEAT_ACTION_LOCKOUT_MS = 500L

private enum class SeatSide { Left, Right }

@Composable
fun DashboardFrontLeftSeatHeatVentWidgetItem(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    elevation: Dp,
    shape: Dp,
    textColor: Color,
    backgroundColor: Color,
    singleLineDualMetrics: Boolean,
    enableInnerInteractions: Boolean = true,
    scale: Float = 1f
) {
    val mode by MbCanRepository.frontLeftSeatModeState.collectAsStateWithLifecycle()
    SeatHeatVentWidget(
        side = SeatSide.Left,
        mode = mode,
        propertyId = MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH,
        onClick = onClick,
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor,
        singleLineDualMetrics = singleLineDualMetrics,
        enableInnerInteractions = enableInnerInteractions,
        scale = scale
    )
}

@Composable
fun DashboardFrontRightSeatHeatVentWidgetItem(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    elevation: Dp,
    shape: Dp,
    textColor: Color,
    backgroundColor: Color,
    singleLineDualMetrics: Boolean,
    enableInnerInteractions: Boolean = true,
    scale: Float = 1f
) {
    val mode by MbCanRepository.frontRightSeatModeState.collectAsStateWithLifecycle()
    SeatHeatVentWidget(
        side = SeatSide.Right,
        mode = mode,
        propertyId = MbCanKnownVehiclePropertyId.FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH,
        onClick = onClick,
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor,
        singleLineDualMetrics = singleLineDualMetrics,
        enableInnerInteractions = enableInnerInteractions,
        scale = scale
    )
}

@Composable
private fun SeatHeatVentWidget(
    side: SeatSide,
    mode: MbCanSeatModeState,
    propertyId: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    elevation: Dp,
    shape: Dp,
    textColor: Color,
    backgroundColor: Color,
    singleLineDualMetrics: Boolean,
    enableInnerInteractions: Boolean,
    scale: Float = 1f
) {
    val context = LocalContext.current
    var seatActionBlockedUntil by remember { mutableLongStateOf(0L) }
    fun trySendSeatProperty(value: Int) {
        val now = SystemClock.uptimeMillis()
        if (now < seatActionBlockedUntil) return
        seatActionBlockedUntil = now + SEAT_ACTION_LOCKOUT_MS
        sendSetMbCanProperty(context, propertyId, value)
    }
    val iconColor = when (mode) {
        is MbCanSeatModeState.Unavailable -> {
            textColor.copy(alpha = 0.25f)
        }

        is MbCanSeatModeState.Unknown -> {
            textColor.copy(alpha = 0.25f)
        }

        else -> {
            textColor
        }
    }
    DashboardWidgetScaffold(
        onClick = if (enableInnerInteractions) {
            {}
        } else { onClick },
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor
    ) { _, _ ->
        if (singleLineDualMetrics) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SeatActionButton(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    side = side,
                    iconColor = iconColor,
                    modeType = "heat",
                    level = (mode as? MbCanSeatModeState.Heat)?.level,
                    onLongClick = onLongClick,
                    onClick = if (enableInnerInteractions) {
                        { trySendSeatProperty(nextHeatRaw(mode)) }
                    } else { onClick },
                    onDoubleClick = if (enableInnerInteractions) {
                        { trySendSeatProperty(1) }
                    } else {
                        {}
                    },
                    scale = scale
                )
                SeatActionButton(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    side = side,
                    iconColor = iconColor,
                    modeType = "vent",
                    level = (mode as? MbCanSeatModeState.Vent)?.level,
                    onLongClick = onLongClick,
                    onClick = if (enableInnerInteractions) {
                        { trySendSeatProperty(nextVentRaw(mode)) }
                    } else { onClick },
                    onDoubleClick = if (enableInnerInteractions) {
                        { trySendSeatProperty(1) }
                    } else {
                        {}
                    },
                    scale = scale
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SeatActionButton(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    side = side,
                    iconColor = iconColor,
                    modeType = "heat",
                    level = (mode as? MbCanSeatModeState.Heat)?.level,
                    onLongClick = onLongClick,
                    onClick = if (enableInnerInteractions) {
                        { trySendSeatProperty(nextHeatRaw(mode)) }
                    } else { onClick },
                    onDoubleClick = if (enableInnerInteractions) {
                        { trySendSeatProperty(1) }
                    } else {
                        {}
                    },
                    scale = scale
                )
                SeatActionButton(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    side = side,
                    iconColor = iconColor,
                    modeType = "vent",
                    level = (mode as? MbCanSeatModeState.Vent)?.level,
                    onLongClick = onLongClick,
                    onClick = if (enableInnerInteractions) {
                        { trySendSeatProperty(nextVentRaw(mode)) }
                    } else { onClick },
                    onDoubleClick = if (enableInnerInteractions) {
                        { trySendSeatProperty(1) }
                    } else {
                        {}
                    },
                    scale = scale
                )
            }
        }
    }
}

@Composable
private fun SeatActionButton(
    modifier: Modifier,
    side: SeatSide,
    iconColor: Color,
    modeType: String,
    level: Int?,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
    scale: Float = 1f
) {
    Box(
        modifier = modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onDoubleClick = onDoubleClick
            )
            .graphicsLayer { scaleX = if (side == SeatSide.Left) { 1f } else { -1f } },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_widget_seat),
            contentDescription = null,
            modifier = Modifier
                .matchParentSize()
                .scale(scale),
            colorFilter = ColorFilter.tint(iconColor),
            contentScale = ContentScale.Fit,
        )
        if (modeType == "heat") {
            Image(
                painter = painterResource(id = R.drawable.ic_widget_seat_heat_1),
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize()
                    .scale(scale),
                colorFilter = ColorFilter.tint(if (level in listOf(1, 2, 3)) SeatHeatOnColor else iconColor),
                contentScale = ContentScale.Fit,
            )
            Image(
                painter = painterResource(id = R.drawable.ic_widget_seat_heat_2),
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize()
                    .scale(scale),
                colorFilter = ColorFilter.tint(if (level in listOf(2, 3)) SeatHeatOnColor else iconColor),
                contentScale = ContentScale.Fit,
            )
            Image(
                painter = painterResource(id = R.drawable.ic_widget_seat_heat_3),
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize()
                    .scale(scale),
                colorFilter = ColorFilter.tint(if (level == 3) SeatHeatOnColor else iconColor),
                contentScale = ContentScale.Fit,
            )
        } else if (modeType == "vent") {
            Image(
                painter = painterResource(id = R.drawable.ic_widget_seat_vent_0),
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize()
                    .scale(scale),
                colorFilter = ColorFilter.tint(if (level in listOf(1, 2, 3)) SeatVentOnColor else iconColor),
                contentScale = ContentScale.Fit,
            )
            Image(
                painter = painterResource(id = R.drawable.ic_widget_seat_vent_1),
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize()
                    .scale(scale),
                colorFilter = ColorFilter.tint(if (level in listOf(1, 2, 3)) SeatVentOnColor else iconColor),
                contentScale = ContentScale.Fit,
            )
            Image(
                painter = painterResource(id = R.drawable.ic_widget_seat_vent_2),
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize()
                    .scale(scale),
                colorFilter = ColorFilter.tint(if (level in listOf(2, 3)) SeatVentOnColor else iconColor),
                contentScale = ContentScale.Fit,
            )
            Image(
                painter = painterResource(id = R.drawable.ic_widget_seat_vent_3),
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize()
                    .scale(scale),
                colorFilter = ColorFilter.tint(if (level == 3) SeatVentOnColor else iconColor),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

private fun nextHeatRaw(mode: MbCanSeatModeState): Int {
    return when (mode) {
        is MbCanSeatModeState.Heat -> when (mode.level) {
            3 -> 3 // heat 2
            2 -> 2 // heat 1
            1 -> 1 // off
            else -> 4
        }
        else -> 4
    }
}

private fun nextVentRaw(mode: MbCanSeatModeState): Int {
    return when (mode) {
        is MbCanSeatModeState.Vent -> when (mode.level) {
            3 -> 6 // vent 2
            2 -> 5 // vent 1
            1 -> 1 // off
            else -> 7
        }
        else -> 7
    }
}
