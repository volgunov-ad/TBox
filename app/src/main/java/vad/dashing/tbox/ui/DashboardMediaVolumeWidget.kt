package vad.dashing.tbox.ui

import android.content.Context
import android.media.AudioManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import vad.dashing.tbox.DashboardWidget
import vad.dashing.tbox.R

private const val MEDIA_VOLUME_SWIPE_STEP_PX = 58f
private const val MEDIA_VOLUME_POLL_DELAY_MS = 350L

private data class MediaVolumeState(
    val current: Int = 0,
    val muted: Boolean = false
)

@Composable
fun DashboardMediaVolumeWidgetItem(
    widget: DashboardWidget,
    isVertical: Boolean,
    showTitle: Boolean = true,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    enableInnerInteractions: Boolean = true,
    elevation: Dp = 4.dp,
    shape: Dp = 12.dp,
    textColor: Color? = null,
    backgroundColor: Color? = null
) {
    val context = LocalContext.current
    val audioManager = remember(context) {
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    var volumeState by remember(widget.id, isVertical) {
        mutableStateOf(readMediaVolumeState(audioManager))
    }
    var lastNonZeroVolume by remember(widget.id, isVertical) {
        mutableIntStateOf(max(volumeState.current, 1))
    }
    var swipeAccumulator by remember(widget.id, isVertical) {
        mutableFloatStateOf(0f)
    }

    LaunchedEffect(widget.id, isVertical) {
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

    fun applyVolumeDelta(increase: Boolean) {
        if (increase && volumeState.muted) {
            unmuteGlobalVolume(audioManager)
        }
        volumeState = changeGlobalVolumeByStep(
            audioManager = audioManager,
            increase = increase
        )
        if (!volumeState.muted && volumeState.current > 0) {
            lastNonZeroVolume = volumeState.current
        }
    }

    fun toggleMute() {
        if (volumeState.muted) {
            unmuteGlobalVolume(audioManager)
        } else {
            if (volumeState.current > 0) {
                lastNonZeroVolume = volumeState.current
            }
            muteGlobalVolume(audioManager)
        }
        volumeState = readMediaVolumeState(audioManager)
        if (!volumeState.muted && volumeState.current > 0) {
            lastNonZeroVolume = volumeState.current
        }
    }

    val rootSwipeModifier = if (enableInnerInteractions) {
        Modifier.pointerInput(widget.id, isVertical) {
            detectDragGestures(
                onDrag = { change, dragAmount ->
                    change.consume()
                    val primaryDelta = if (isVertical) {
                        -dragAmount.y
                    } else {
                        dragAmount.x
                    }
                    swipeAccumulator += primaryDelta
                    while (abs(swipeAccumulator) >= MEDIA_VOLUME_SWIPE_STEP_PX) {
                        val shouldIncrease = swipeAccumulator > 0f
                        applyVolumeDelta(increase = shouldIncrease)
                        swipeAccumulator += if (shouldIncrease) {
                            -MEDIA_VOLUME_SWIPE_STEP_PX
                        } else {
                            MEDIA_VOLUME_SWIPE_STEP_PX
                        }
                    }
                },
                onDragEnd = { swipeAccumulator = 0f },
                onDragCancel = { swipeAccumulator = 0f }
            )
        }
    } else {
        Modifier
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (showTitle) {
                Text(
                    text = stringResource(R.string.widget_media_volume_title),
                    color = resolvedTextColor,
                    fontSize = calculateResponsiveFontSize(
                        containerHeight = availableHeight,
                        textType = TextType.TITLE
                    ),
                    textAlign = TextAlign.Center,
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
                    MediaVolumeActionButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_media_volume_plus),
                                contentDescription = stringResource(R.string.widget_media_volume_action_increase),
                                tint = resolvedTextColor,
                                modifier = Modifier
                                    .fillMaxHeight(0.58f)
                                    .aspectRatio(1f)
                            )
                        },
                        interactionEnabled = enableInnerInteractions,
                        onLongClick = onLongClick,
                        onClick = { applyVolumeDelta(increase = true) }
                    )
                    MediaVolumeCenterButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        muted = volumeState.muted,
                        currentVolume = volumeState.current,
                        textColor = resolvedTextColor,
                        availableHeight = availableHeight,
                        interactionEnabled = enableInnerInteractions,
                        onLongClick = onLongClick,
                        onToggleMute = { toggleMute() }
                    )
                    MediaVolumeActionButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_media_volume_minus),
                                contentDescription = stringResource(R.string.widget_media_volume_action_decrease),
                                tint = resolvedTextColor,
                                modifier = Modifier
                                    .fillMaxHeight(0.58f)
                                    .aspectRatio(1f)
                            )
                        },
                        interactionEnabled = enableInnerInteractions,
                        onLongClick = onLongClick,
                        onClick = { applyVolumeDelta(increase = false) }
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MediaVolumeActionButton(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f),
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_media_volume_minus),
                                contentDescription = stringResource(R.string.widget_media_volume_action_decrease),
                                tint = resolvedTextColor,
                                modifier = Modifier
                                    .fillMaxHeight(0.58f)
                                    .aspectRatio(1f)
                            )
                        },
                        interactionEnabled = enableInnerInteractions,
                        onLongClick = onLongClick,
                        onClick = { applyVolumeDelta(increase = false) }
                    )
                    MediaVolumeCenterButton(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f),
                        muted = volumeState.muted,
                        currentVolume = volumeState.current,
                        textColor = resolvedTextColor,
                        availableHeight = availableHeight,
                        interactionEnabled = enableInnerInteractions,
                        onLongClick = onLongClick,
                        onToggleMute = { toggleMute() }
                    )
                    MediaVolumeActionButton(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f),
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_media_volume_plus),
                                contentDescription = stringResource(R.string.widget_media_volume_action_increase),
                                tint = resolvedTextColor,
                                modifier = Modifier
                                    .fillMaxHeight(0.58f)
                                    .aspectRatio(1f)
                            )
                        },
                        interactionEnabled = enableInnerInteractions,
                        onLongClick = onLongClick,
                        onClick = { applyVolumeDelta(increase = true) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaVolumeCenterButton(
    modifier: Modifier,
    muted: Boolean,
    currentVolume: Int,
    textColor: Color,
    availableHeight: Dp,
    interactionEnabled: Boolean,
    onLongClick: () -> Unit,
    onToggleMute: () -> Unit
) {
    val iconRes = if (muted) {
        R.drawable.ic_media_volume_mute
    } else {
        R.drawable.ic_media_volume_audio
    }
    MediaVolumeActionButton(
        modifier = modifier,
        icon = {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = stringResource(R.string.widget_media_volume_action_mute),
                    tint = textColor,
                    modifier = Modifier
                        .fillMaxHeight(0.48f)
                        .aspectRatio(1f)
                )
                Text(
                    text = currentVolume.toString(),
                    color = textColor,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = calculateResponsiveFontSize(
                        containerHeight = availableHeight,
                        textType = TextType.TITLE
                    )
                )
            }
        },
        interactionEnabled = interactionEnabled,
        onLongClick = onLongClick,
        onClick = onToggleMute
    )
}

@Composable
private fun MediaVolumeActionButton(
    modifier: Modifier,
    icon: @Composable () -> Unit,
    interactionEnabled: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
                shape = RoundedCornerShape(10.dp)
            )
            .combinedClickable(
                enabled = interactionEnabled,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}

private fun readMediaVolumeState(audioManager: AudioManager): MediaVolumeState {
    // Keep widget value aligned with what user observes from hardware/system volume UI.
    val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(0)
    val muted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        audioManager.isStreamMute(AudioManager.STREAM_MUSIC) || currentVolume == 0
    } else {
        currentVolume == 0
    }
    return MediaVolumeState(
        current = currentVolume,
        muted = muted
    )
}

private fun changeGlobalVolumeByStep(
    audioManager: AudioManager,
    increase: Boolean
): MediaVolumeState {
    val direction = if (increase) {
        AudioManager.ADJUST_RAISE
    } else {
        AudioManager.ADJUST_LOWER
    }

    val before = readMediaVolumeState(audioManager)
    runCatching {
        // Closer to hardware volume keys behavior than direct stream change.
        audioManager.adjustSuggestedStreamVolume(direction, AudioManager.USE_DEFAULT_STREAM_TYPE, 0)
    }
    var after = readMediaVolumeState(audioManager)
    if (after.current == before.current && after.muted == before.muted) {
        runCatching {
            audioManager.adjustVolume(direction, 0)
        }
        after = readMediaVolumeState(audioManager)
    }
    return after
}

private fun muteGlobalVolume(audioManager: AudioManager) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        runCatching {
            audioManager.adjustSuggestedStreamVolume(
                AudioManager.ADJUST_MUTE,
                AudioManager.USE_DEFAULT_STREAM_TYPE,
                0
            )
        }
    } else {
        runCatching {
            audioManager.adjustSuggestedStreamVolume(
                AudioManager.ADJUST_LOWER,
                AudioManager.USE_DEFAULT_STREAM_TYPE,
                0
            )
        }
    }
    runCatching {
        audioManager.adjustVolume(AudioManager.ADJUST_MUTE, 0)
    }
}

private fun unmuteGlobalVolume(audioManager: AudioManager) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        runCatching {
            audioManager.adjustSuggestedStreamVolume(
                AudioManager.ADJUST_UNMUTE,
                AudioManager.USE_DEFAULT_STREAM_TYPE,
                0
            )
        }
    }
    runCatching {
        audioManager.adjustVolume(AudioManager.ADJUST_UNMUTE, 0)
    }
    if (readMediaVolumeState(audioManager).current <= 0) {
        runCatching {
            audioManager.adjustSuggestedStreamVolume(
                AudioManager.ADJUST_RAISE,
                AudioManager.USE_DEFAULT_STREAM_TYPE,
                0
            )
        }
    }
}
