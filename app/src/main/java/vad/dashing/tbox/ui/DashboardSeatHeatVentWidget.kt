package vad.dashing.tbox.ui

import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.R
import vad.dashing.tbox.SEAT_HEAT_VENT_VARIANT_HEAT
import vad.dashing.tbox.SEAT_HEAT_VENT_VARIANT_VENT
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId
import vad.dashing.tbox.mbcan.MbCanRepository
import vad.dashing.tbox.mbcan.MbCanSeatModeState

private val SeatHeatOnColor = Color(0xFFFF9800)
private val SeatVentOnColor = Color(0xFF4FC3F7)

private const val SEAT_ACTION_LOCKOUT_MS = 500L

private enum class SeatSide { FrontLeft, FrontRight, BackLeft, BackRight }

private enum class SeatHeatVentLayoutMode { Dual, Single, RearHeatOnly }

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
        side = SeatSide.FrontLeft,
        layoutMode = SeatHeatVentLayoutMode.Dual,
        selectedVariant = SEAT_HEAT_VENT_VARIANT_HEAT,
        onSelectedVariantChange = {},
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
        side = SeatSide.FrontRight,
        layoutMode = SeatHeatVentLayoutMode.Dual,
        selectedVariant = SEAT_HEAT_VENT_VARIANT_HEAT,
        onSelectedVariantChange = {},
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
fun DashboardFrontLeftSeatHeatVentSingleWidgetItem(
    selectedVariant: Int,
    onSelectedVariantChange: (Int) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    elevation: Dp,
    shape: Dp,
    textColor: Color,
    backgroundColor: Color,
    enableInnerInteractions: Boolean = true,
    scale: Float = 1f
) {
    val mode by MbCanRepository.frontLeftSeatModeState.collectAsStateWithLifecycle()
    SeatHeatVentWidget(
        side = SeatSide.FrontLeft,
        layoutMode = SeatHeatVentLayoutMode.Single,
        selectedVariant = selectedVariant,
        onSelectedVariantChange = onSelectedVariantChange,
        mode = mode,
        propertyId = MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH,
        onClick = onClick,
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor,
        singleLineDualMetrics = false,
        enableInnerInteractions = enableInnerInteractions,
        scale = scale
    )
}

@Composable
fun DashboardFrontRightSeatHeatVentSingleWidgetItem(
    selectedVariant: Int,
    onSelectedVariantChange: (Int) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    elevation: Dp,
    shape: Dp,
    textColor: Color,
    backgroundColor: Color,
    enableInnerInteractions: Boolean = true,
    scale: Float = 1f
) {
    val mode by MbCanRepository.frontRightSeatModeState.collectAsStateWithLifecycle()
    SeatHeatVentWidget(
        side = SeatSide.FrontRight,
        layoutMode = SeatHeatVentLayoutMode.Single,
        selectedVariant = selectedVariant,
        onSelectedVariantChange = onSelectedVariantChange,
        mode = mode,
        propertyId = MbCanKnownVehiclePropertyId.FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH,
        onClick = onClick,
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor,
        singleLineDualMetrics = false,
        enableInnerInteractions = enableInnerInteractions,
        scale = scale
    )
}

@Composable
fun DashboardRearLeftSeatHeatWidgetItem(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    elevation: Dp,
    shape: Dp,
    textColor: Color,
    backgroundColor: Color,
    enableInnerInteractions: Boolean = true,
    scale: Float = 1f
) {
    val mode by MbCanRepository.rearLeftSeatModeState.collectAsStateWithLifecycle()
    SeatHeatVentWidget(
        side = SeatSide.BackLeft,
        layoutMode = SeatHeatVentLayoutMode.RearHeatOnly,
        selectedVariant = SEAT_HEAT_VENT_VARIANT_HEAT,
        onSelectedVariantChange = {},
        mode = mode,
        propertyId = MbCanKnownVehiclePropertyId.REAR_LEFT_SEAT_HEAT_SWITCH,
        onClick = onClick,
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor,
        singleLineDualMetrics = false,
        enableInnerInteractions = enableInnerInteractions,
        scale = scale
    )
}

@Composable
fun DashboardRearRightSeatHeatWidgetItem(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    elevation: Dp,
    shape: Dp,
    textColor: Color,
    backgroundColor: Color,
    enableInnerInteractions: Boolean = true,
    scale: Float = 1f
) {
    val mode by MbCanRepository.rearRightSeatModeState.collectAsStateWithLifecycle()
    SeatHeatVentWidget(
        side = SeatSide.BackRight,
        layoutMode = SeatHeatVentLayoutMode.RearHeatOnly,
        selectedVariant = SEAT_HEAT_VENT_VARIANT_HEAT,
        onSelectedVariantChange = {},
        mode = mode,
        propertyId = MbCanKnownVehiclePropertyId.REAR_RIGHT_SEAT_HEAT_SWITCH,
        onClick = onClick,
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor,
        singleLineDualMetrics = false,
        enableInnerInteractions = enableInnerInteractions,
        scale = scale
    )
}

@Composable
private fun SeatHeatVentWidget(
    side: SeatSide,
    layoutMode: SeatHeatVentLayoutMode,
    selectedVariant: Int,
    onSelectedVariantChange: (Int) -> Unit,
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

    var optimisticVariant by remember { mutableIntStateOf(selectedVariant.coerceIn(0, 1)) }
    LaunchedEffect(selectedVariant) {
        optimisticVariant = selectedVariant.coerceIn(0, 1)
    }

    val swipeThresholdPx = with(LocalDensity.current) { 40.dp.toPx() }

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
        when (layoutMode) {
            SeatHeatVentLayoutMode.RearHeatOnly -> {
                val heatLevel = (mode as? MbCanSeatModeState.Heat)?.level
                val onHeatClick = if (enableInnerInteractions) {
                    { trySendSeatProperty(nextHeatRaw(mode)) }
                } else {
                    onClick
                }
                val onDouble = if (enableInnerInteractions) {
                    { trySendSeatProperty(1) }
                } else {
                    {}
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    SeatActionButton(
                        modifier = Modifier.fillMaxSize(),
                        side = side,
                        iconColor = iconColor,
                        modeType = "heat",
                        level = heatLevel,
                        onLongClick = onLongClick,
                        onClick = onHeatClick,
                        onDoubleClick = onDouble,
                        scale = scale
                    )
                }
            }

            SeatHeatVentLayoutMode.Single -> {
                val showHeat = optimisticVariant == SEAT_HEAT_VENT_VARIANT_HEAT
                val heatLevel = (mode as? MbCanSeatModeState.Heat)?.level
                val ventLevel = (mode as? MbCanSeatModeState.Vent)?.level
                val modeType = if (showHeat) "heat" else "vent"
                val level = if (showHeat) heatLevel else ventLevel
                val onSingleClick = if (enableInnerInteractions) {
                    {
                        if (showHeat) {
                            trySendSeatProperty(nextHeatRaw(mode))
                        } else {
                            trySendSeatProperty(nextVentRaw(mode))
                        }
                    }
                } else {
                    onClick
                }
                val onDouble = if (enableInnerInteractions) {
                    { trySendSeatProperty(1) }
                } else {
                    {}
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    SeatActionButton(
                        modifier = Modifier.fillMaxSize(),
                        side = side,
                        iconColor = iconColor,
                        modeType = modeType,
                        level = level,
                        onLongClick = onLongClick,
                        onClick = onSingleClick,
                        onDoubleClick = onDouble,
                        horizontalSwipePointerKey = optimisticVariant,
                        horizontalSwipeThresholdPx = if (enableInnerInteractions) {
                            swipeThresholdPx
                        } else {
                            null
                        },
                        onHorizontalSwipeConfirmed = {
                            val next = if (optimisticVariant == SEAT_HEAT_VENT_VARIANT_HEAT) {
                                SEAT_HEAT_VENT_VARIANT_VENT
                            } else {
                                SEAT_HEAT_VENT_VARIANT_HEAT
                            }
                            optimisticVariant = next
                            onSelectedVariantChange(next)
                        },
                        scale = scale
                    )
                }
            }

            SeatHeatVentLayoutMode.Dual -> {
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
    horizontalSwipePointerKey: Any? = null,
    horizontalSwipeThresholdPx: Float? = null,
    onHorizontalSwipeConfirmed: () -> Unit = {},
    scale: Float = 1f
) {
    val swipeModifier = if (horizontalSwipeThresholdPx != null) {
        Modifier.pointerInput(horizontalSwipeThresholdPx, horizontalSwipePointerKey) {
            var dragAccum = 0f
            detectHorizontalDragGestures(
                onHorizontalDrag = { _, amount ->
                    dragAccum += amount
                },
                onDragCancel = { dragAccum = 0f },
                onDragEnd = {
                    if (kotlin.math.abs(dragAccum) >= horizontalSwipeThresholdPx) {
                        onHorizontalSwipeConfirmed()
                    }
                    dragAccum = 0f
                }
            )
        }
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .then(swipeModifier)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onDoubleClick = onDoubleClick
            )
            .graphicsLayer { scaleX =
                if (side in listOf(SeatSide.FrontLeft, SeatSide.BackLeft)) { 1f } else { -1f } },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_widget_seat),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .scale(scale),
            colorFilter = ColorFilter.tint(iconColor),
            contentScale = ContentScale.Fit,
        )
        if (side == SeatSide.BackLeft) {
            Image(
                painter = painterResource(id = R.drawable.ic_widget_seat_back_left),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(scale),
                colorFilter = ColorFilter.tint(iconColor),
                contentScale = ContentScale.Fit,
            )
        }
        else if (side == SeatSide.BackRight) {
            Image(
                painter = painterResource(id = R.drawable.ic_widget_seat_back_right),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(scale),
                colorFilter = ColorFilter.tint(iconColor),
                contentScale = ContentScale.Fit,
            )
        }

        if (modeType == "heat") {
            Image(
                painter = painterResource(id = R.drawable.ic_widget_seat_heat_1),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(scale),
                colorFilter = ColorFilter.tint(if (level in listOf(1, 2, 3)) SeatHeatOnColor else iconColor),
                contentScale = ContentScale.Fit,
            )
            Image(
                painter = painterResource(id = R.drawable.ic_widget_seat_heat_2),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(scale),
                colorFilter = ColorFilter.tint(if (level in listOf(2, 3)) SeatHeatOnColor else iconColor),
                contentScale = ContentScale.Fit,
            )
            Image(
                painter = painterResource(id = R.drawable.ic_widget_seat_heat_3),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(scale),
                colorFilter = ColorFilter.tint(if (level == 3) SeatHeatOnColor else iconColor),
                contentScale = ContentScale.Fit,
            )
        } else if (modeType == "vent") {
            Image(
                painter = painterResource(id = R.drawable.ic_widget_seat_vent_0),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(scale),
                colorFilter = ColorFilter.tint(if (level in listOf(1, 2, 3)) SeatVentOnColor else iconColor),
                contentScale = ContentScale.Fit,
            )
            Image(
                painter = painterResource(id = R.drawable.ic_widget_seat_vent_1),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(scale),
                colorFilter = ColorFilter.tint(if (level in listOf(1, 2, 3)) SeatVentOnColor else iconColor),
                contentScale = ContentScale.Fit,
            )
            Image(
                painter = painterResource(id = R.drawable.ic_widget_seat_vent_2),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(scale),
                colorFilter = ColorFilter.tint(if (level in listOf(2, 3)) SeatVentOnColor else iconColor),
                contentScale = ContentScale.Fit,
            )
            Image(
                painter = painterResource(id = R.drawable.ic_widget_seat_vent_3),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
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
