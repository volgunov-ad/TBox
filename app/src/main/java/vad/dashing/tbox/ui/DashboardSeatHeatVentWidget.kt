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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.R
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId
import vad.dashing.tbox.mbcan.MbCanRepository
import vad.dashing.tbox.mbcan.MbCanSeatModeState

private val SeatHeatOnColor = Color(0xFFFF9800)
private val SeatVentOnColor = Color(0xFF4FC3F7)

private const val SEAT_ACTION_LOCKOUT_MS = 500L

private enum class SeatSide { Left, Right }

@Composable
fun DashboardFrontLeftSeatHeatVentWidgetItem(
    onLongClick: () -> Unit,
    elevation: Dp,
    shape: Dp,
    textColor: Color,
    backgroundColor: Color,
    singleLineDualMetrics: Boolean,
    enableInnerInteractions: Boolean = true,
) {
    val mode by MbCanRepository.frontLeftSeatModeState.collectAsStateWithLifecycle()
    SeatHeatVentWidget(
        side = SeatSide.Left,
        mode = mode,
        propertyId = MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH,
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor,
        singleLineDualMetrics = singleLineDualMetrics,
        enableInnerInteractions = enableInnerInteractions
    )
}

@Composable
fun DashboardFrontRightSeatHeatVentWidgetItem(
    onLongClick: () -> Unit,
    elevation: Dp,
    shape: Dp,
    textColor: Color,
    backgroundColor: Color,
    singleLineDualMetrics: Boolean,
    enableInnerInteractions: Boolean = true,
) {
    val mode by MbCanRepository.frontRightSeatModeState.collectAsStateWithLifecycle()
    SeatHeatVentWidget(
        side = SeatSide.Right,
        mode = mode,
        propertyId = MbCanKnownVehiclePropertyId.FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH,
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor,
        singleLineDualMetrics = singleLineDualMetrics,
        enableInnerInteractions = enableInnerInteractions
    )
}

@Composable
private fun SeatHeatVentWidget(
    side: SeatSide,
    mode: MbCanSeatModeState,
    propertyId: Int,
    onLongClick: () -> Unit,
    elevation: Dp,
    shape: Dp,
    textColor: Color,
    backgroundColor: Color,
    singleLineDualMetrics: Boolean,
    enableInnerInteractions: Boolean,
) {
    val context = LocalContext.current
    var seatActionBlockedUntil by remember { mutableLongStateOf(0L) }
    fun trySendSeatProperty(value: Int) {
        val now = SystemClock.uptimeMillis()
        if (now < seatActionBlockedUntil) return
        seatActionBlockedUntil = now + SEAT_ACTION_LOCKOUT_MS
        sendSetMbCanProperty(context, propertyId, value)
    }
    DashboardWidgetScaffold(
        onClick = {},
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
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SeatActionButton(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    side = side,
                    textColor = textColor,
                    activeColor = SeatHeatOnColor,
                    iconRes = if (side == SeatSide.Left) {
                        R.drawable.ic_widget_seat_heat_left
                    } else {
                        R.drawable.ic_widget_seat_heat_right
                    },
                    level = (mode as? MbCanSeatModeState.Heat)?.level,
                    enableInnerInteractions = enableInnerInteractions,
                    onLongClick = onLongClick,
                    onClick = { trySendSeatProperty(nextHeatRaw(mode)) },
                    onDoubleClick = {
                        trySendSeatProperty(1)
                    }
                )
                SeatActionButton(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    side = side,
                    textColor = textColor,
                    activeColor = SeatVentOnColor,
                    iconRes = if (side == SeatSide.Left) {
                        R.drawable.ic_widget_seat_vent_left
                    } else {
                        R.drawable.ic_widget_seat_vent_right
                    },
                    level = (mode as? MbCanSeatModeState.Vent)?.level,
                    enableInnerInteractions = enableInnerInteractions,
                    onLongClick = onLongClick,
                    onClick = { trySendSeatProperty(nextVentRaw(mode)) },
                    onDoubleClick = {
                        trySendSeatProperty(1)
                    }
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SeatActionButton(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    side = side,
                    textColor = textColor,
                    activeColor = SeatHeatOnColor,
                    iconRes = if (side == SeatSide.Left) {
                        R.drawable.ic_widget_seat_heat_left
                    } else {
                        R.drawable.ic_widget_seat_heat_right
                    },
                    level = (mode as? MbCanSeatModeState.Heat)?.level,
                    enableInnerInteractions = enableInnerInteractions,
                    onLongClick = onLongClick,
                    onClick = { trySendSeatProperty(nextHeatRaw(mode)) },
                    onDoubleClick = {
                        trySendSeatProperty(1)
                    }
                )
                SeatActionButton(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    side = side,
                    textColor = textColor,
                    activeColor = SeatVentOnColor,
                    iconRes = if (side == SeatSide.Left) {
                        R.drawable.ic_widget_seat_vent_left
                    } else {
                        R.drawable.ic_widget_seat_vent_right
                    },
                    level = (mode as? MbCanSeatModeState.Vent)?.level,
                    enableInnerInteractions = enableInnerInteractions,
                    onLongClick = onLongClick,
                    onClick = { trySendSeatProperty(nextVentRaw(mode)) },
                    onDoubleClick = {
                        trySendSeatProperty(1)
                    }
                )
            }
        }
    }
}

@Composable
private fun SeatActionButton(
    modifier: Modifier,
    side: SeatSide,
    textColor: Color,
    activeColor: Color,
    iconRes: Int,
    level: Int?,
    enableInnerInteractions: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit
) {
    val iconColor = if (level != null) activeColor else textColor
    val shapeMod = Modifier.clip(RoundedCornerShape(8.dp))
    val clickMod = if (enableInnerInteractions) {
        shapeMod.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick,
            onDoubleClick = onDoubleClick
        )
    } else {
        shapeMod
    }
    Box(
        modifier = modifier.then(clickMod),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            colorFilter = ColorFilter.tint(iconColor)
        )
        if (level != null) {
            androidx.compose.material3.Text(
                text = level.toString(),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = iconColor,
                modifier = Modifier.align(
                    if (side == SeatSide.Left) Alignment.BottomEnd else Alignment.BottomStart
                )
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
