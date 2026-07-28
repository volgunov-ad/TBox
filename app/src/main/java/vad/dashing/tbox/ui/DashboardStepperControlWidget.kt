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
    @StringRes decreaseContentDescriptionRes: Int,
    @StringRes increaseContentDescriptionRes: Int,
    adjustIconStyle: Int = STEPPER_ADJUST_ICON_PLUS_MINUS,
    /**
     * When true, all three controls (+/−/center) use active content + background;
     * otherwise inactive. Callers: climate on, unmuted volume, etc.
     */
    controlsActive: Boolean = false,
    centerIcon: @Composable (contentColor: Color) -> Unit = {},
    showCenterIcon: Boolean = true,
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
    val contentColor = if (controlsActive) controls.activeContent else controls.inactiveContent
    val chromeBackground =
        if (controlsActive) controls.activeBackground else controls.inactiveBackground
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
        DashboardWidgetContentWithOptionalTitle(
            showTitle = showTitle,
            titleText = titleText,
            availableHeight = availableHeight,
            resolvedTextColor = resolvedTextColor,
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
                        background = chromeBackground,
                        shapeDp = controls.shapeDp,
                        interactionEnabled = enableInnerInteractions,
                        onLongClick = onLongClick,
                        onClick = onIncrease,
                        content = {
                            StepperAdjustIcon(
                                increase = true,
                                isVertical = true,
                                adjustIconStyle = adjustIconStyle,
                                tint = contentColor,
                                contentDescriptionRes = increaseContentDescriptionRes,
                            )
                        },
                    )
                    StepperCenterButton(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        label = centerLabel,
                        contentColor = contentColor,
                        background = chromeBackground,
                        shapeDp = controls.shapeDp,
                        showIcon = showCenterIcon,
                        interactionEnabled = enableInnerInteractions,
                        onLongClick = onLongClick,
                        onClick = onCenterClick,
                        onDoubleClick = onCenterDoubleClick,
                        icon = centerIcon,
                        availableHeight = availableHeight,
                    )
                    StepperActionButton(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        background = chromeBackground,
                        shapeDp = controls.shapeDp,
                        interactionEnabled = enableInnerInteractions,
                        onLongClick = onLongClick,
                        onClick = onDecrease,
                        content = {
                            StepperAdjustIcon(
                                increase = false,
                                isVertical = true,
                                adjustIconStyle = adjustIconStyle,
                                tint = contentColor,
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
                        background = chromeBackground,
                        shapeDp = controls.shapeDp,
                        interactionEnabled = enableInnerInteractions,
                        onLongClick = onLongClick,
                        onClick = onDecrease,
                        content = {
                            StepperAdjustIcon(
                                increase = false,
                                isVertical = false,
                                adjustIconStyle = adjustIconStyle,
                                tint = contentColor,
                                contentDescriptionRes = decreaseContentDescriptionRes,
                            )
                        },
                    )
                    StepperCenterButton(
                        modifier = Modifier.fillMaxHeight().weight(1f),
                        label = centerLabel,
                        contentColor = contentColor,
                        background = chromeBackground,
                        shapeDp = controls.shapeDp,
                        showIcon = showCenterIcon,
                        interactionEnabled = enableInnerInteractions,
                        onLongClick = onLongClick,
                        onClick = onCenterClick,
                        onDoubleClick = onCenterDoubleClick,
                        icon = centerIcon,
                        availableHeight = availableHeight,
                    )
                    StepperActionButton(
                        modifier = Modifier.fillMaxHeight().weight(1f),
                        background = chromeBackground,
                        shapeDp = controls.shapeDp,
                        interactionEnabled = enableInnerInteractions,
                        onLongClick = onLongClick,
                        onClick = onIncrease,
                        content = {
                            StepperAdjustIcon(
                                increase = true,
                                isVertical = false,
                                adjustIconStyle = adjustIconStyle,
                                tint = contentColor,
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
    contentColor: Color,
    background: Color,
    shapeDp: Dp,
    showIcon: Boolean,
    interactionEnabled: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onDoubleClick: (() -> Unit)? = null,
    icon: @Composable (contentColor: Color) -> Unit,
    availableHeight: Dp,
) {
    StepperActionButton(
        modifier = modifier,
        background = background,
        shapeDp = shapeDp,
        interactionEnabled = interactionEnabled,
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
                        icon(contentColor)
                    }
                    Text(
                        text = label,
                        color = contentColor,
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
                        color = contentColor,
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
    background: Color,
    shapeDp: Dp,
    interactionEnabled: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onDoubleClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    WidgetControlChrome(
        background = background,
        shapeDp = shapeDp,
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
