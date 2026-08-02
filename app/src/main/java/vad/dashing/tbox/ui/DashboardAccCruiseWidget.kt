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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.CruiseControlType
import vad.dashing.tbox.R
import vad.dashing.tbox.TripTelemetryRepository
import vad.dashing.tbox.mbcan.AccCruiseController
import vad.dashing.tbox.mbcan.AccCruiseDomain
import vad.dashing.tbox.mbcan.CruiseLogicalState
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.normalizeAccCruiseTargetKmh
import vad.dashing.tbox.ui.theme.WidgetActiveColors

/** Half-period of active↔inactive pulse (full cycle ≈ 2× this). Was 550; 2× faster → 275. */
private const val ACC_CRUISE_PULSE_DURATION_MS = 275

@Composable
private fun rememberAccCruisePulseColor(from: Color, to: Color): Color {
    val transition = rememberInfiniteTransition(label = "accCruisePulse")
    val fraction by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = ACC_CRUISE_PULSE_DURATION_MS,
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "accCruisePulseFraction",
    )
    return lerp(from, to, fraction)
}

@Composable
fun DashboardAccCruiseWidgetItem(
    targetKmh: Int,
    increaseIntervalMs: Int,
    decreaseIntervalMs: Int,
    cruiseControlType: CruiseControlType = CruiseControlType.AUTO,
    widgetKey: String = "",
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
    scale: Float = 1f,
) {
    val accMode by UniversalCanRepository.accCruiseMode.collectAsStateWithLifecycle()
    val vSetDis by UniversalCanRepository.accCruiseVSetDisKmh.collectAsStateWithLifecycle()
    val ccsStatus by UniversalCanRepository.ccsCruiseStatus.collectAsStateWithLifecycle()
    val frmFeedback by UniversalCanRepository.accFrmFeedbackAvailable.collectAsStateWithLifecycle()
    val vehicleSpeed by TripTelemetryRepository.carSpeed.collectAsStateWithLifecycle()
    val adjustingWidgetKey by AccCruiseController.adjustingWidgetKey.collectAsStateWithLifecycle()
    val adjustingAny = adjustingWidgetKey != null
    val adjustingThis = adjustingAny &&
        widgetKey.isNotEmpty() &&
        adjustingWidgetKey == widgetKey
    val target = normalizeAccCruiseTargetKmh(targetKmh)
    val useAcc = AccCruiseDomain.shouldUseAccPath(frmFeedback, cruiseControlType)
    val logical = AccCruiseDomain.cruiseLogicalState(useAcc, accMode, ccsStatus)
    val activeAtTarget = AccCruiseDomain.isAtWidgetTarget(
        useAccPath = useAcc,
        accMode = accMode,
        vSetDisKmh = vSetDis,
        ccsStatus = ccsStatus,
        vehicleSpeedKmh = vehicleSpeed,
        targetKmh = target,
    )
    val known = if (useAcc) {
        accMode != null
    } else {
        ccsStatus != null || adjustingAny
    }
    val controls = LocalWidgetControlAppearance.current
    val steadyIconColor = when {
        !known -> controls.inactiveContent.copy(alpha = 0.25f)
        logical == CruiseLogicalState.Fault -> WidgetActiveColors.Secondary
        activeAtTarget -> controls.activeContent
        else -> controls.inactiveContent
    }
    val iconColor = if (adjustingThis && logical != CruiseLogicalState.Fault) {
        rememberAccCruisePulseColor(
            from = controls.activeContent,
            to = controls.inactiveContent,
        )
    } else {
        steadyIconColor
    }
    val defaultTitle = stringResource(R.string.data_title_acc_cruise_widget)
    val titleText = titleOverride.trim().ifBlank { defaultTitle }

    DashboardWidgetScaffold(
        onClick = {
            if (enableInnerInteractions) {
                AccCruiseController.launchEngageToTarget(
                    targetKmh = target,
                    increaseIntervalMs = increaseIntervalMs,
                    decreaseIntervalMs = decreaseIntervalMs,
                    cruiseControlType = cruiseControlType,
                    widgetKey = widgetKey,
                )
            } else {
                onClick()
            }
        },
        onLongClick = onLongClick,
        onDoubleClick = {
            if (enableInnerInteractions) {
                AccCruiseController.launchFullOff(cruiseControlType)
            }
            onDoubleClick()
        },
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor,
    ) { availableHeight, resolvedTextColor ->
        DashboardWidgetContentWithOptionalTitle(
            showTitle = showTitle,
            titleText = titleText,
            availableHeight = availableHeight,
            resolvedTextColor = resolvedTextColor,
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
                .wrapContentHeight(Alignment.CenterVertically),
        ) { contentModifier ->
            WidgetControlChrome(
                background = if (activeAtTarget && !adjustingThis && logical != CruiseLogicalState.Fault) {
                    controls.activeBackground
                } else {
                    controls.inactiveBackground
                },
                shapeDp = controls.shapeDp,
                modifier = contentModifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_widget_acc_cruise),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().scale(scale),
                        colorFilter = ColorFilter.tint(iconColor),
                    )
                    Text(
                        text = target.toString(),
                        color = iconColor,
                        textAlign = TextAlign.Center,
                        style = calculateResponsiveTextStyle(
                            containerHeight = availableHeight,
                            textType = TextType.VALUE,
                        ).let { style ->
                            style.copy(fontSize = style.fontSize * 1.25f)
                        },
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
