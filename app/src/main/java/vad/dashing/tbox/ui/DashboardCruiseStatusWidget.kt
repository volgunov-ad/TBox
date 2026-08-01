package vad.dashing.tbox.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.CruiseControlType
import vad.dashing.tbox.R
import vad.dashing.tbox.mbcan.AccCruiseController
import vad.dashing.tbox.mbcan.AccCruiseDomain
import vad.dashing.tbox.mbcan.CruiseLogicalState
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.ui.theme.WidgetActiveColors

private val CRUISE_STATUS_SWIPE_THRESHOLD_DP = 40.dp

/**
 * Cruise status tile: live ACC VSetDis (or CCS on/off).
 * Single tap: Off/Standby → activate at current; Active → pause (212); Fault → no-op.
 * Double tap: full off (210) when Standby/Active.
 * Standby: swipe down → SET−, swipe up → RES+.
 * Active: swipe up → RES+ (+1), swipe down → SET− (−1).
 */
@Composable
fun DashboardCruiseStatusWidgetItem(
    cruiseControlType: CruiseControlType = CruiseControlType.AUTO,
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
    val useAcc = AccCruiseDomain.shouldUseAccPath(frmFeedback, cruiseControlType)
    val logical = AccCruiseDomain.cruiseLogicalState(useAcc, accMode, ccsStatus)
    val logicalState = rememberUpdatedState(logical)
    val typeState = rememberUpdatedState(cruiseControlType)

    val known = if (useAcc) {
        accMode != null
    } else {
        ccsStatus != null
    }
    val showSetpoint = useAcc && AccCruiseDomain.shouldShowAccSetpoint(accMode)
    val setpointText = if (showSetpoint) {
        vSetDis?.toString().orEmpty()
    } else {
        ""
    }

    val controls = LocalWidgetControlAppearance.current
    val iconColor = when {
        !known -> controls.inactiveContent.copy(alpha = 0.25f)
        logical == CruiseLogicalState.Fault -> WidgetActiveColors.Secondary
        logical == CruiseLogicalState.Active -> controls.activeContent
        logical == CruiseLogicalState.Standby -> controls.inactiveContent
        else -> controls.inactiveContent.copy(alpha = 0.45f)
    }
    val defaultTitle = stringResource(R.string.data_title_cruise_status_widget)
    val titleText = titleOverride.trim().ifBlank { defaultTitle }
    val swipeThresholdPx = with(LocalDensity.current) { CRUISE_STATUS_SWIPE_THRESHOLD_DP.toPx() }
    val swipeModifier = if (enableInnerInteractions) {
        Modifier.pointerInput(swipeThresholdPx, cruiseControlType) {
            var dragAccum = 0f
            detectVerticalDragGestures(
                onVerticalDrag = { _, amount ->
                    dragAccum += amount
                },
                onDragCancel = { dragAccum = 0f },
                onDragEnd = {
                    when {
                        dragAccum <= -swipeThresholdPx -> {
                            // Finger moved up → RES+ (Standby resume / Active +1).
                            when (logicalState.value) {
                                CruiseLogicalState.Standby,
                                CruiseLogicalState.Active,
                                -> AccCruiseController.launchStatusSwipeUp(typeState.value)
                                else -> Unit
                            }
                        }
                        dragAccum >= swipeThresholdPx -> {
                            // Finger moved down → SET− (Standby activate / Active −1).
                            when (logicalState.value) {
                                CruiseLogicalState.Standby,
                                CruiseLogicalState.Active,
                                -> AccCruiseController.launchStatusSwipeDown(typeState.value)
                                else -> Unit
                            }
                        }
                    }
                    dragAccum = 0f
                },
            )
        }
    } else {
        Modifier
    }

    DashboardWidgetScaffold(
        modifier = swipeModifier,
        onClick = {
            if (enableInnerInteractions) {
                AccCruiseController.launchStatusSingleTap(cruiseControlType)
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
                background = if (logical == CruiseLogicalState.Active) {
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
                    if (setpointText.isNotEmpty()) {
                        Text(
                            text = setpointText,
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
}
