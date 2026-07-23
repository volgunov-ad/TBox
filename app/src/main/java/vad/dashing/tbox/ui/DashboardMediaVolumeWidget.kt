package vad.dashing.tbox.ui

import android.content.Context
import android.media.AudioManager
import android.os.Build
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import vad.dashing.tbox.DashboardWidget
import vad.dashing.tbox.R
import vad.dashing.tbox.STEPPER_ADJUST_ICON_PLUS_MINUS
import vad.dashing.tbox.mbcan.UniversalCanRepository

private const val MEDIA_VOLUME_POLL_DELAY_MS = 350L

private data class MediaVolumeState(
    val current: Int = 0,
    val muted: Boolean = false
)

@Composable
fun DashboardMediaVolumeWidgetItem(
    widget: DashboardWidget,
    isVertical: Boolean,
    useMbCan: Boolean = false,
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
    val scope = rememberCoroutineScope()
    val audioManager = remember(context) {
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val mbCanVolume by UniversalCanRepository.audioVolumeState.collectAsStateWithLifecycle()

    var volumeState by remember(widget.id, isVertical, useMbCan) {
        mutableStateOf(readMediaVolumeState(audioManager))
    }
    var lastNonZeroVolume by remember(widget.id, isVertical, useMbCan) {
        mutableIntStateOf(kotlin.math.max(volumeState.current, 1))
    }

    val defaultVolumeTitle = stringResource(R.string.widget_media_volume_title)
    val volumeTitleText = titleOverride.trim().ifBlank { defaultVolumeTitle }
    val resolvedTextColor = textColor ?: MaterialTheme.colorScheme.onSurface
    val resolvedBackgroundColor = backgroundColor ?: MaterialTheme.colorScheme.surface
    val centerIconRes = if (volumeState.muted) {
        R.drawable.ic_media_volume_mute
    } else {
        R.drawable.ic_media_volume_audio
    }

    LaunchedEffect(widget.id, isVertical, useMbCan) {
        if (useMbCan) {
            return@LaunchedEffect
        }
        while (true) {
            val updated = readMediaVolumeState(audioManager)
            if (updated != volumeState) {
                volumeState = updated
            }
            if (!updated.muted && updated.current > 0) {
                lastNonZeroVolume = updated.current
            }
            delay(MEDIA_VOLUME_POLL_DELAY_MS)
        }
    }

    LaunchedEffect(useMbCan, mbCanVolume) {
        if (!useMbCan) {
            return@LaunchedEffect
        }
        val current = mbCanVolume ?: 0
        volumeState = MediaVolumeState(
            current = current,
            muted = current == 0
        )
        if (current > 0) {
            UniversalCanRepository.rememberAudioVolumeLastNonZeroInSession(current)
        }
    }

    fun applyMbCanVolume(target: Int) {
        scope.launch {
            UniversalCanRepository.setAudioVolume(target)
        }
    }

    fun applyVolumeDelta(increase: Boolean) {
        if (useMbCan) {
            val direction = if (increase) 1 else -1
            val next = (volumeState.current + direction).coerceAtLeast(0)
            applyMbCanVolume(next)
            return
        }
        if (increase && volumeState.muted) {
            unmuteMediaStream(audioManager, lastNonZeroVolume)
        }
        volumeState = changeMediaVolumeByStep(
            audioManager = audioManager,
            increase = increase
        )
        if (!volumeState.muted && volumeState.current > 0) {
            lastNonZeroVolume = volumeState.current
        }
    }

    fun toggleMute() {
        if (useMbCan) {
            if (volumeState.current > 0) {
                UniversalCanRepository.rememberAudioVolumeLastNonZeroInSession(volumeState.current)
                applyMbCanVolume(0)
            } else {
                applyMbCanVolume(UniversalCanRepository.audioVolumeRestoreCandidate())
            }
            return
        }
        if (volumeState.muted) {
            unmuteMediaStream(audioManager, lastNonZeroVolume)
        } else {
            if (volumeState.current > 0) {
                lastNonZeroVolume = volumeState.current
            }
            muteMediaStream(audioManager)
        }
        volumeState = readMediaVolumeState(audioManager)
        if (!volumeState.muted && volumeState.current > 0) {
            lastNonZeroVolume = volumeState.current
        }
    }

    DashboardStepperControlWidget(
        modifier = Modifier,
        isVertical = isVertical,
        centerLabel = volumeState.current.toString(),
        decreaseContentDescriptionRes = R.string.widget_media_volume_action_decrease,
        increaseContentDescriptionRes = R.string.widget_media_volume_action_increase,
        adjustIconStyle = stepperAdjustIconStyle,
        controlsActive = !volumeState.muted,
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

private fun readMediaVolumeState(audioManager: AudioManager): MediaVolumeState {
    val currentVolume = runCatching {
        audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(0)
    }.getOrDefault(0)
    val mutedBySystem = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        runCatching { audioManager.isStreamMute(AudioManager.STREAM_MUSIC) }.getOrDefault(currentVolume == 0)
    } else {
        false
    }
    return MediaVolumeState(
        current = currentVolume,
        muted = mutedBySystem || currentVolume == 0
    )
}

private fun changeMediaVolumeByStep(
    audioManager: AudioManager,
    increase: Boolean
): MediaVolumeState {
    val direction = if (increase) {
        AudioManager.ADJUST_RAISE
    } else {
        AudioManager.ADJUST_LOWER
    }

    val beforeState = readMediaVolumeState(audioManager)
    runCatching {
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
    }
    var afterState = readMediaVolumeState(audioManager)
    if (afterState.current == beforeState.current && afterState.muted == beforeState.muted) {
        runCatching {
            audioManager.adjustSuggestedStreamVolume(
                direction,
                AudioManager.STREAM_MUSIC,
                0
            )
        }
        afterState = readMediaVolumeState(audioManager)
    }
    return afterState
}

private fun muteMediaStream(audioManager: AudioManager) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        runCatching {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_MUTE,
                0
            )
        }
    } else {
        runCatching {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_LOWER,
                0
            )
        }
    }
    runCatching {
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
    }
}

private fun unmuteMediaStream(audioManager: AudioManager, fallbackVolume: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        runCatching {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_UNMUTE,
                0
            )
        }
    }
    val maxVolume = runCatching {
        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    }.getOrDefault(1).coerceAtLeast(1)
    val restoreVolume = fallbackVolume.coerceIn(1, maxVolume)
    if (runCatching { audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(0) <= 0) {
        runCatching {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, restoreVolume, 0)
        }
    }
}
