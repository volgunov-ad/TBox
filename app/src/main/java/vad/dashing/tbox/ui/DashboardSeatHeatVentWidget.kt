package vad.dashing.tbox.ui

import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.R
import vad.dashing.tbox.SEAT_HEAT_VENT_VARIANT_HEAT
import vad.dashing.tbox.SEAT_HEAT_VENT_VARIANT_VENT
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.mbcan.MbCanSeatModeState

import vad.dashing.tbox.ui.theme.WidgetActiveColors

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
    showTitle: Boolean = false,
    titleOverride: String = "",
    iconScale: Float = 1f
) {
    val mode by UniversalCanRepository.frontLeftSeatModeState.collectAsStateWithLifecycle()
    val defaultTitle = stringResource(R.string.data_title_front_left_seat_heat_vent_widget)
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
        showTitle = showTitle,
        titleOverride = titleOverride,
        defaultTitle = defaultTitle,
        iconScale = iconScale
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
    showTitle: Boolean = false,
    titleOverride: String = "",
    iconScale: Float = 1f
) {
    val mode by UniversalCanRepository.frontRightSeatModeState.collectAsStateWithLifecycle()
    val defaultTitle = stringResource(R.string.data_title_front_right_seat_heat_vent_widget)
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
        showTitle = showTitle,
        titleOverride = titleOverride,
        defaultTitle = defaultTitle,
        iconScale = iconScale
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
    showTitle: Boolean = false,
    titleOverride: String = "",
    iconScale: Float = 1f
) {
    val mode by UniversalCanRepository.frontLeftSeatModeState.collectAsStateWithLifecycle()
    val defaultTitle = stringResource(R.string.data_title_front_left_seat_heat_vent_single_widget)
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
        showTitle = showTitle,
        titleOverride = titleOverride,
        defaultTitle = defaultTitle,
        iconScale = iconScale
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
    showTitle: Boolean = false,
    titleOverride: String = "",
    iconScale: Float = 1f
) {
    val mode by UniversalCanRepository.frontRightSeatModeState.collectAsStateWithLifecycle()
    val defaultTitle = stringResource(R.string.data_title_front_right_seat_heat_vent_single_widget)
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
        showTitle = showTitle,
        titleOverride = titleOverride,
        defaultTitle = defaultTitle,
        iconScale = iconScale
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
    showTitle: Boolean = false,
    titleOverride: String = "",
    iconScale: Float = 1f
) {
    val mode by UniversalCanRepository.rearLeftSeatModeState.collectAsStateWithLifecycle()
    val defaultTitle = stringResource(R.string.data_title_rear_left_seat_heat_widget)
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
        showTitle = showTitle,
        titleOverride = titleOverride,
        defaultTitle = defaultTitle,
        iconScale = iconScale
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
    showTitle: Boolean = false,
    titleOverride: String = "",
    iconScale: Float = 1f
) {
    val mode by UniversalCanRepository.rearRightSeatModeState.collectAsStateWithLifecycle()
    val defaultTitle = stringResource(R.string.data_title_rear_right_seat_heat_widget)
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
        showTitle = showTitle,
        titleOverride = titleOverride,
        defaultTitle = defaultTitle,
        iconScale = iconScale
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
    showTitle: Boolean,
    titleOverride: String,
    defaultTitle: String,
    iconScale: Float = 1f
) {
    val context = LocalContext.current
    var seatActionBlockedUntil by remember { mutableLongStateOf(0L) }
    fun trySendSeatProperty(value: Int) {
        val now = SystemClock.uptimeMillis()
        if (now < seatActionBlockedUntil) return
        seatActionBlockedUntil = now + SEAT_ACTION_LOCKOUT_MS
        sendSetMbCanProperty(context, propertyId, value)
    }
    val controls = LocalWidgetControlAppearance.current
    val iconColor = when (mode) {
        is MbCanSeatModeState.Unavailable -> {
            controls.inactiveContent.copy(alpha = 0.25f)
        }

        is MbCanSeatModeState.Unknown -> {
            controls.inactiveContent.copy(alpha = 0.25f)
        }

        else -> {
            controls.inactiveContent
        }
    }

    var optimisticVariant by remember { mutableIntStateOf(selectedVariant.coerceIn(0, 1)) }
    LaunchedEffect(selectedVariant) {
        optimisticVariant = selectedVariant.coerceIn(0, 1)
    }

    val swipeThresholdPx = with(LocalDensity.current) { 40.dp.toPx() }
    val titleText = titleOverride.trim().ifBlank { defaultTitle }

    DashboardWidgetScaffold(
        onClick = if (enableInnerInteractions) {
            {}
        } else { onClick },
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor
    ) { availableHeight, resolvedTextColor ->
        DashboardWidgetContentWithOptionalTitle(
            showTitle = showTitle,
            titleText = titleText,
            availableHeight = availableHeight,
            resolvedTextColor = resolvedTextColor,
            modifier = Modifier
                .fillMaxSize()
                .widgetControlOuterPadding(controls)
                .wrapContentHeight(Alignment.CenterVertically),
        ) { contentModifier ->
            Box(
                modifier = contentModifier.fillMaxWidth()
            ) {
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
                            modifier = Modifier.fillMaxSize(),
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
                                iconScale = iconScale
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
                            modifier = Modifier.fillMaxSize(),
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
                                iconScale = iconScale
                            )
                        }
                    }

                    SeatHeatVentLayoutMode.Dual -> {
                        if (singleLineDualMetrics) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
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
                            iconScale = iconScale
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
                            iconScale = iconScale
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
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
                            iconScale = iconScale
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
                            iconScale = iconScale
                        )
                    }
                }
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
    iconScale: Float = 1f
) {
    val controls = LocalWidgetControlAppearance.current
    val useDefaults = LocalWidgetControlUsesDefaults.current
    val heatOn = if (useDefaults) WidgetActiveColors.Secondary else controls.activeContent
    val ventOn = if (useDefaults) WidgetActiveColors.Primary else controls.activeContent
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
    val levelActive = level in listOf(1, 2, 3)
    WidgetControlChrome(
        background = if (levelActive) controls.activeBackground else controls.inactiveBackground,
        shapeDp = controls.shapeDp,
        modifier = modifier
            .then(swipeModifier)
            .combinedClickableWithSound(
                onClick = onClick,
                onLongClick = onLongClick,
                onDoubleClick = onDoubleClick
            )
            .graphicsLayer { scaleX =
                if (side in listOf(SeatSide.FrontLeft, SeatSide.BackLeft)) { 1f } else { -1f } },
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_widget_seat),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .scale(iconScale),
            colorFilter = ColorFilter.tint(iconColor),
            contentScale = ContentScale.Fit,
        )
        if (side == SeatSide.BackLeft) {
            Image(
                painter = painterResource(id = R.drawable.ic_widget_seat_back_left),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(iconScale),
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
                    .scale(iconScale),
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
                    .scale(iconScale),
                colorFilter = ColorFilter.tint(if (level in listOf(1, 2, 3)) heatOn else iconColor),
                contentScale = ContentScale.Fit,
            )
            Image(
                painter = painterResource(id = R.drawable.ic_widget_seat_heat_2),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(iconScale),
                colorFilter = ColorFilter.tint(if (level in listOf(2, 3)) heatOn else iconColor),
                contentScale = ContentScale.Fit,
            )
            Image(
                painter = painterResource(id = R.drawable.ic_widget_seat_heat_3),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(iconScale),
                colorFilter = ColorFilter.tint(if (level == 3) heatOn else iconColor),
                contentScale = ContentScale.Fit,
            )
        } else if (modeType == "vent") {
            Image(
                painter = painterResource(id = R.drawable.ic_widget_seat_vent_0),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(iconScale),
                colorFilter = ColorFilter.tint(if (level in listOf(1, 2, 3)) ventOn else iconColor),
                contentScale = ContentScale.Fit,
            )
            Image(
                painter = painterResource(id = R.drawable.ic_widget_seat_vent_1),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(iconScale),
                colorFilter = ColorFilter.tint(if (level in listOf(1, 2, 3)) ventOn else iconColor),
                contentScale = ContentScale.Fit,
            )
            Image(
                painter = painterResource(id = R.drawable.ic_widget_seat_vent_2),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(iconScale),
                colorFilter = ColorFilter.tint(if (level in listOf(2, 3)) ventOn else iconColor),
                contentScale = ContentScale.Fit,
            )
            Image(
                painter = painterResource(id = R.drawable.ic_widget_seat_vent_3),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(iconScale),
                colorFilter = ColorFilter.tint(if (level == 3) ventOn else iconColor),
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
