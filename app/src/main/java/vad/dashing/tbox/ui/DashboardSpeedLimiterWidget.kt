package vad.dashing.tbox.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import vad.dashing.tbox.R
import vad.dashing.tbox.STEPPER_ADJUST_ICON_PLUS_MINUS
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.mbcan.MbCanBinaryState
import vad.dashing.tbox.mbcan.SlaSpeedLimitDomain
import vad.dashing.tbox.mbcan.UniversalCanRepository

@Composable
fun DashboardSpeedLimiterWidgetItem(
    settingsViewModel: SettingsViewModel,
    isVertical: Boolean,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    enableInnerInteractions: Boolean = true,
    elevation: Dp = 4.dp,
    shape: Dp = 12.dp,
    textColor: Color? = null,
    backgroundColor: Color? = null,
    showTitle: Boolean = true,
    titleOverride: String = "",
    stepperAdjustIconStyle: Int = STEPPER_ADJUST_ICON_PLUS_MINUS,
) {
    val scope = rememberCoroutineScope()
    val targetKmh by settingsViewModel.speedLimiterTargetKmh.collectAsStateWithLifecycle()
    val limiterState by UniversalCanRepository.speedLimiterState.collectAsStateWithLifecycle()

    val defaultTitle = stringResource(R.string.data_title_speed_limiter_widget)
    val titleText = titleOverride.trim().ifBlank { defaultTitle }
    val resolvedTextColor = textColor ?: MaterialTheme.colorScheme.onSurface
    val resolvedBackgroundColor = backgroundColor ?: MaterialTheme.colorScheme.surface
    val limiterActive = limiterState is MbCanBinaryState.On

    fun applyTargetDelta(increase: Boolean) {
        val next = SlaSpeedLimitDomain.stepLimiterTargetKmh(targetKmh, increase)
        settingsViewModel.saveSpeedLimiterTargetKmh(next)
        scope.launch {
            UniversalCanRepository.setSpeedLimiterTargetKmh(next)
        }
    }

    fun runLimiterInteraction(isDoubleTap: Boolean) {
        if (isDoubleTap) {
            scope.launch {
                UniversalCanRepository.enableSpeedLimiter(targetKmh)
            }
        } else {
            scope.launch {
                UniversalCanRepository.setSpeedLimiterEnabled(false)
            }
        }
    }

    DashboardStepperControlWidget(
        modifier = Modifier,
        isVertical = isVertical,
        centerLabel = targetKmh.toString(),
        centerDimmed = !limiterActive,
        decreaseContentDescriptionRes = R.string.widget_speed_limiter_action_decrease,
        increaseContentDescriptionRes = R.string.widget_speed_limiter_action_increase,
        adjustIconStyle = stepperAdjustIconStyle,
        showCenterIcon = false,
        enableInnerInteractions = enableInnerInteractions,
        onDecrease = { applyTargetDelta(increase = false) },
        onIncrease = { applyTargetDelta(increase = true) },
        onCenterClick = {},
        onClick = {
            if (!enableInnerInteractions) {
                onClick()
            } else {
                runLimiterInteraction(isDoubleTap = false)
            }
        },
        onDoubleClick = {
            if (enableInnerInteractions) {
                runLimiterInteraction(isDoubleTap = true)
            }
        },
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        textColor = resolvedTextColor,
        backgroundColor = resolvedBackgroundColor,
        showTitle = showTitle,
        titleText = titleText,
    )
}
