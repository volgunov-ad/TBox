package vad.dashing.tbox.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import vad.dashing.tbox.R
import vad.dashing.tbox.STEPPER_ADJUST_ICON_PLUS_MINUS
import vad.dashing.tbox.resolveStepperAdjustIconDrawableRes

private const val STEPPER_SWIPE_STEP_PX = 58f

@Composable
fun DashboardStepperControlWidget(
    modifier: Modifier = Modifier,
    isVertical: Boolean,
    centerLabel: String,
    centerDimmed: Boolean,
    @StringRes decreaseContentDescriptionRes: Int,
    @StringRes increaseContentDescriptionRes: Int,
    adjustIconStyle: Int = STEPPER_ADJUST_ICON_PLUS_MINUS,
    centerIcon: @Composable () -> Unit = {},
    showCenterIcon: Boolean = true,
    enableInnerInteractions: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onCenterClick: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    elevation: Dp,
    shape: Dp,
    textColor: Color,
    backgroundColor: Color,
    showTitle: Boolean,
    titleText: String,
) {
    var swipeAccumulator by remember(isVertical) { mutableFloatStateOf(0f) }
    val rootSwipeModifier = if (enableInnerInteractions) {
        modifier.pointerInput(isVertical) {
            detectDragGestures(
                onDrag = { change, dragAmount ->
                    change.consume()
                    val primaryDelta = if (isVertical) -dragAmount.y else dragAmount.x
                    swipeAccumulator += primaryDelta
                    while (abs(swipeAccumulator) >= STEPPER_SWIPE_STEP_PX) {
                        val shouldIncrease = swipeAccumulator > 0f
                        if (shouldIncrease) onIncrease() else onDecrease()
                        swipeAccumulator += if (shouldIncrease) -STEPPER_SWIPE_STEP_PX else STEPPER_SWIPE_STEP_PX
                    }
                },
                onDragEnd = { swipeAccumulator = 0f },
                onDragCancel = { swipeAccumulator = 0f }
            )
        }
    } else {
        modifier
    }

    DashboardWidgetScaffold(
        modifier = rootSwipeModifier,
        onClick = onClick,
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor
    ) { availableHeight, resolvedTextColor ->
        val centerTextColor = if (centerDimmed) {
            resolvedTextColor.copy(alpha = 0.35f)
        } else {
            resolvedTextColor
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (showTitle) {
                Text(
                    text = titleText,
                    color = resolvedTextColor,
                    style = calculateResponsiveTextStyle(
                        containerHeight = availableHeight,
                        textType = TextType.TITLE
                    ),
                    textAlign = LocalWidgetTextAlign.current,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (isVertical) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StepperActionButton(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        interactionEnabled = enableInnerInteractions,
                        onLongClick = onLongClick,
                        onClick = onIncrease,
                        content = {
                            StepperAdjustIcon(
                                increase = true,
                                isVertical = true,
                                adjustIconStyle = adjustIconStyle,
                                tint = resolvedTextColor,
                                contentDescriptionRes = increaseContentDescriptionRes,
                            )
                        },
                    )
                    StepperCenterButton(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        label = centerLabel,
                        labelColor = centerTextColor,
                        showIcon = showCenterIcon,
                        interactionEnabled = enableInnerInteractions,
                        onLongClick = onLongClick,
                        onClick = onCenterClick,
                        icon = centerIcon,
                        availableHeight = availableHeight,
                    )
                    StepperActionButton(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        interactionEnabled = enableInnerInteractions,
                        onLongClick = onLongClick,
                        onClick = onDecrease,
                        content = {
                            StepperAdjustIcon(
                                increase = false,
                                isVertical = true,
                                adjustIconStyle = adjustIconStyle,
                                tint = resolvedTextColor,
                                contentDescriptionRes = decreaseContentDescriptionRes,
                            )
                        },
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StepperActionButton(
                        modifier = Modifier.fillMaxHeight().weight(1f),
                        interactionEnabled = enableInnerInteractions,
                        onLongClick = onLongClick,
                        onClick = onDecrease,
                        content = {
                            StepperAdjustIcon(
                                increase = false,
                                isVertical = false,
                                adjustIconStyle = adjustIconStyle,
                                tint = resolvedTextColor,
                                contentDescriptionRes = decreaseContentDescriptionRes,
                            )
                        },
                    )
                    StepperCenterButton(
                        modifier = Modifier.fillMaxHeight().weight(1f),
                        label = centerLabel,
                        labelColor = centerTextColor,
                        showIcon = showCenterIcon,
                        interactionEnabled = enableInnerInteractions,
                        onLongClick = onLongClick,
                        onClick = onCenterClick,
                        icon = centerIcon,
                        availableHeight = availableHeight,
                    )
                    StepperActionButton(
                        modifier = Modifier.fillMaxHeight().weight(1f),
                        interactionEnabled = enableInnerInteractions,
                        onLongClick = onLongClick,
                        onClick = onIncrease,
                        content = {
                            StepperAdjustIcon(
                                increase = true,
                                isVertical = false,
                                adjustIconStyle = adjustIconStyle,
                                tint = resolvedTextColor,
                                contentDescriptionRes = increaseContentDescriptionRes,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StepperAdjustIcon(
    increase: Boolean,
    isVertical: Boolean,
    adjustIconStyle: Int,
    tint: Color,
    @StringRes contentDescriptionRes: Int,
) {
    Icon(
        painter = painterResource(
            resolveStepperAdjustIconDrawableRes(
                increase = increase,
                isVertical = isVertical,
                style = adjustIconStyle,
            )
        ),
        contentDescription = stringResource(contentDescriptionRes),
        tint = tint,
        modifier = Modifier
            .fillMaxHeight(0.58f)
            .aspectRatio(1f),
    )
}

@Composable
private fun StepperCenterButton(
    modifier: Modifier,
    label: String,
    labelColor: Color,
    showIcon: Boolean,
    interactionEnabled: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    availableHeight: Dp,
) {
    StepperActionButton(
        modifier = modifier,
        interactionEnabled = interactionEnabled,
        onLongClick = onLongClick,
        onClick = onClick,
        content = {
            if (showIcon) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight(0.48f)
                            .aspectRatio(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        icon()
                    }
                    Text(
                        text = label,
                        color = labelColor,
                        style = calculateResponsiveTextStyle(
                            containerHeight = availableHeight,
                            textType = TextType.TITLE
                        ),
                        maxLines = 1
                    )
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = labelColor,
                        style = calculateResponsiveTextStyle(
                            containerHeight = availableHeight,
                            textType = TextType.VALUE
                        ),
                        maxLines = 1
                    )
                }
            }
        },
    )
}

@Composable
private fun StepperActionButton(
    modifier: Modifier,
    interactionEnabled: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .combinedClickableWithSound(
                enabled = interactionEnabled,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
