package vad.dashing.tbox.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.R
import vad.dashing.tbox.mbcan.TrunkDoorDomain
import vad.dashing.tbox.mbcan.TrunkDoorRepository
import vad.dashing.tbox.mbcan.TrunkIconTint
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.mbcan.launchTrunkCommand
import vad.dashing.tbox.mbcan.trunkPulseFromStopped
import vad.dashing.tbox.mbcan.trunkPulseStop
import vad.dashing.tbox.ui.theme.WidgetActiveColors

private const val TRUNK_PULSE_DURATION_MS = 550

@Composable
private fun rememberTrunkPulseColor(from: Color, to: Color): Color {
    val transition = rememberInfiniteTransition(label = "trunkPulse")
    val fraction by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = TRUNK_PULSE_DURATION_MS,
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "trunkPulseFraction",
    )
    return lerp(from, to, fraction)
}

@Composable
private fun resolveTrunkIconColor(tint: TrunkIconTint): Color =
    when (tint) {
        is TrunkIconTint.Solid -> tint.color
        is TrunkIconTint.Pulsing -> rememberTrunkPulseColor(
            from = tint.from,
            to = tint.to,
        )
    }

@Composable
fun DashboardTrunkDoorWidgetItem(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDoubleClick: () -> Unit,
    enableInnerInteractions: Boolean,
    elevation: Dp,
    shape: Dp,
    textColor: Color,
    backgroundColor: Color,
    showTitle: Boolean = false,
    titleOverride: String = "",
    iconScale: Float = 1f,
) {
    val scope = rememberCoroutineScope()
    val trunkState by TrunkDoorRepository.displayState.collectAsStateWithLifecycle()
    val controls = LocalWidgetControlAppearance.current
    val defaultTitle = stringResource(R.string.data_title_trunk_door_widget)
    val titleText = titleOverride.trim().ifBlank { defaultTitle }

    fun runTrunkInteraction(isDoubleTap: Boolean) {
        if (TrunkDoorDomain.shouldPulseStop(trunkState)) {
            UniversalCanRepository.launchTrunkCommand(scope) { trunkPulseStop() }
            return
        }
        if (!isDoubleTap) return
        UniversalCanRepository.launchTrunkCommand(scope) { trunkPulseFromStopped(trunkState) }
    }

    DashboardWidgetScaffold(
        onClick = {
            if (!enableInnerInteractions) {
                onClick()
            } else {
                runTrunkInteraction(isDoubleTap = false)
            }
        },
        onLongClick = onLongClick,
        onDoubleClick = {
            if (enableInnerInteractions) {
                runTrunkInteraction(isDoubleTap = true)
            }
            onDoubleClick()
        },
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor
    ) { availableHeight, resolvedTextColor ->
        val iconTint = TrunkDoorDomain.resolveIconTint(
            state = trunkState,
            idleColor = controls.inactiveContent,
            openColor = controls.activeContent,
            openingAccentColor = WidgetActiveColors.Secondary,
        )
        val iconColor = resolveTrunkIconColor(iconTint)

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
                modifier = contentModifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                // Open or moving → active control chrome; closed/unknown → inactive.
                val useActiveBackground = trunkState.isOpen == true || trunkState.isMoving
                WidgetControlChrome(
                    background = if (useActiveBackground) {
                        controls.activeBackground
                    } else {
                        controls.inactiveBackground
                    },
                    shapeDp = controls.shapeDp,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_widget_trunk),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().scale(iconScale),
                        colorFilter = ColorFilter.tint(iconColor)
                    )
                }
            }
        }
    }
}
