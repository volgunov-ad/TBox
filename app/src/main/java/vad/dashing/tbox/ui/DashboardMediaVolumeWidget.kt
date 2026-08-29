package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.DashboardWidget
import vad.dashing.tbox.PlatformAudioDomain
import vad.dashing.tbox.PlatformAudioRepository
import vad.dashing.tbox.R
import vad.dashing.tbox.STEPPER_ADJUST_ICON_PLUS_MINUS

@Composable
fun DashboardMediaVolumeWidgetItem(
    widget: DashboardWidget,
    isVertical: Boolean,
    showTitle: Boolean = true,
    titleOverride: String = "",
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    enableInnerInteractions: Boolean = true,
    elevation: Dp = 4.dp,
    shape: Dp = 12.dp,
    textColor: Color? = null,
    backgroundColor: Color? = null,
    stepperAdjustIconStyle: Int = STEPPER_ADJUST_ICON_PLUS_MINUS,
) {
    val context = LocalContext.current
    DisposableEffect(widget.id, context) {
        PlatformAudioRepository.startObserving(context)
        onDispose { PlatformAudioRepository.stopObserving() }
    }
    val volume by PlatformAudioRepository.mediaVolume.collectAsStateWithLifecycle()
    // Unknown (null) is not mute — otherwise the tile shows a crossed speaker
    // before the first mixer poll and after a failed read.
    val muted = volume == 0
    val defaultVolumeTitle = stringResource(R.string.widget_media_volume_title)
    val volumeTitleText = titleOverride.trim().ifBlank { defaultVolumeTitle }
    val resolvedTextColor = textColor ?: MaterialTheme.colorScheme.onSurface
    val resolvedBackgroundColor = backgroundColor ?: MaterialTheme.colorScheme.surface
    val centerIconRes = if (muted) {
        R.drawable.ic_media_volume_mute
    } else {
        R.drawable.ic_media_volume_audio
    }

    fun applyVolumeDelta(increase: Boolean) {
        PlatformAudioRepository.adjustVolume(PlatformAudioDomain.VolumeChannel.Media, increase)
    }

    fun toggleMute() {
        when (volume) {
            null, 0 -> PlatformAudioRepository.setVolume(
                PlatformAudioDomain.VolumeChannel.Media,
                PlatformAudioRepository.mediaVolumeRestoreCandidate(),
            )
            else -> PlatformAudioRepository.setVolume(PlatformAudioDomain.VolumeChannel.Media, 0)
        }
    }

    DashboardStepperControlWidget(
        modifier = Modifier,
        isVertical = isVertical,
        centerLabel = volume?.toString() ?: "—",
        decreaseContentDescriptionRes = R.string.widget_media_volume_action_decrease,
        increaseContentDescriptionRes = R.string.widget_media_volume_action_increase,
        adjustIconStyle = stepperAdjustIconStyle,
        controlsActive = !muted,
        centerIcon = { contentColor ->
            Icon(
                painter = painterResource(id = centerIconRes),
                contentDescription = stringResource(R.string.widget_media_volume_action_mute),
                tint = contentColor,
                modifier = Modifier.fillMaxSize(),
            )
        },
        enableInnerInteractions = enableInnerInteractions,
        onDecrease = { applyVolumeDelta(increase = false) },
        onIncrease = { applyVolumeDelta(increase = true) },
        onCenterClick = { toggleMute() },
        onClick = onClick,
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        textColor = resolvedTextColor,
        backgroundColor = resolvedBackgroundColor,
        showTitle = showTitle,
        titleText = volumeTitleText,
    )
}
