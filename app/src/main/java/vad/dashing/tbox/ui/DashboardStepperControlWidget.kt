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
    /** When true, center button uses active control background (e.g. HVAC fan while climate on). */
    centerUseActiveBackground: Boolean = false,
    enableInnerInteractions: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onCenterClick: () -> Unit,
    onCenterDoubleClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDoubleClick: (() -> Unit)? = null,
    elevation: Dp,
    shape: Dp,
    textColor: Color,
    backgroundColor: Color,
    showTitle: Boolean,
    titleText: String,
) {
    var swipeAccumulator by remember(isVertical) { mutableFloatStateOf(0f) }
    val controls = LocalWidgetControlAppearance.current
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
        onDoubleClick = onDoubleClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor
    ) { availableHeight, resolvedTextColor ->
        val centerTextColor = if (centerDimmed) {
            controls.inactiveContent.copy(alpha = 0.35f)
        } else {
            controls.inactiveContent
        }
        DashboardWidgetContentWithOptionalTitle(
            showTitle = showTitle,
            titleText = titleText,
            availableHeight = availableHeight,
            resolvedTextColor = resolvedTextColor,
            titleWeight = 1f,
            contentWeight = 1f,
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) { contentModifier ->
            if (isVertical) {
                Column(
                    modifier = contentModifier.fillMaxWidth(),
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
                                tint = controls.inactiveContent,
                                contentDescriptionRes = increaseContentDescriptionRes,
                            )
                        },
                    )
                    StepperCenterButton(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        label = centerLabel,
                        labelColor = centerTextColor,
                        showIcon = showCenterIcon,
                        useActiveBackground = centerUseActiveBackground,
                        interactionEnabled = enableInnerInteractions,
                        onLongClick = onLongClick,
                        onClick = onCenterClick,
                        onDoubleClick = onCenterDoubleClick,
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
                                tint = controls.inactiveContent,
                                contentDescriptionRes = decreaseContentDescriptionRes,
                            )
                        },
                    )
                }
            } else {
                Row(
                    modifier = contentModifier.fillMaxWidth(),
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
                                tint = controls.inactiveContent,
                                contentDescriptionRes = decreaseContentDescriptionRes,
                            )
                        },
                    )
                    StepperCenterButton(
                        modifier = Modifier.fillMaxHeight().weight(1f),
                        label = centerLabel,
                        labelColor = centerTextColor,
                        showIcon = showCenterIcon,
                        useActiveBackground = centerUseActiveBackground,
                        interactionEnabled = enableInnerInteractions,
                        onLongClick = onLongClick,
                        onClick = onCenterClick,
                        onDoubleClick = onCenterDoubleClick,
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
                                tint = controls.inactiveContent,
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
    useActiveBackground: Boolean,
    interactionEnabled: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onDoubleClick: (() -> Unit)? = null,
    icon: @Composable () -> Unit,
    availableHeight: Dp,
) {
    StepperActionButton(
        modifier = modifier,
        interactionEnabled = interactionEnabled,
        useActiveBackground = useActiveBackground,
        onLongClick = onLongClick,
        onClick = onClick,
        onDoubleClick = onDoubleClick,
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
    useActiveBackground: Boolean = false,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onDoubleClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val controls = LocalWidgetControlAppearance.current
    WidgetControlChrome(
        background = if (useActiveBackground) {
            controls.activeBackground
        } else {
            controls.inactiveBackground
        },
        shapeDp = controls.shapeDp,
        modifier = modifier
            .combinedClickableWithSound(
                enabled = interactionEnabled,
                onClick = onClick,
                onLongClick = onLongClick,
                onDoubleClick = onDoubleClick,
            ),
    ) {
        content()
    }
}
