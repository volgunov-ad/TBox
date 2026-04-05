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
import vad.dashing.tbox.DashboardWidget
import vad.dashing.tbox.R

private const val MEDIA_VOLUME_SWIPE_STEP_PX = 58f
private const val MEDIA_VOLUME_POLL_DELAY_MS = 350L
private val GLOBAL_VOLUME_STREAMS = intArrayOf(
    AudioManager.STREAM_MUSIC,
    AudioManager.STREAM_RING,
    AudioManager.STREAM_NOTIFICATION,
    AudioManager.STREAM_ALARM,
    AudioManager.STREAM_SYSTEM,
    AudioManager.STREAM_VOICE_CALL
)

private data class MediaVolumeState(
    val current: Int = 0,
    val muted: Boolean = false,
    val streamType: Int = AudioManager.STREAM_MUSIC
)

private data class GlobalVolumeResult(
    val state: MediaVolumeState,
    val snapshot: IntArray
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
    var trackedStreamType by remember(widget.id, isVertical) {
        mutableIntStateOf(volumeState.streamType)
    }
    var trackedSnapshot by remember(widget.id, isVertical) {
        mutableStateOf(takeGlobalVolumeSnapshot(audioManager))
    }
    var swipeAccumulator by remember(widget.id, isVertical) {
        mutableFloatStateOf(0f)
    }

    LaunchedEffect(widget.id, isVertical) {
        while (true) {
            val snapshot = takeGlobalVolumeSnapshot(audioManager)
            val changedStream = resolveChangedStreamType(
                before = trackedSnapshot,
                after = snapshot,
                preferredStream = trackedStreamType
            )
            trackedStreamType = changedStream
            trackedSnapshot = snapshot
            val updated = readMediaVolumeState(audioManager, trackedStreamType)
            if (updated != volumeState) {
                volumeState = updated
            }
            delay(MEDIA_VOLUME_POLL_DELAY_MS)
        }
    }

    fun applyVolumeDelta(increase: Boolean) {
        if (increase && volumeState.muted) {
            val unmuteResult = unmuteGlobalVolume(
                audioManager = audioManager,
                preferredStreamType = trackedStreamType
            )
            trackedStreamType = unmuteResult.state.streamType
            trackedSnapshot = unmuteResult.snapshot
            volumeState = unmuteResult.state
        }
        val changeResult = changeGlobalVolumeByStep(
            audioManager = audioManager,
            increase = increase,
            preferredStreamType = trackedStreamType
        )
        trackedStreamType = changeResult.state.streamType
        trackedSnapshot = changeResult.snapshot
        volumeState = changeResult.state
    }

    fun toggleMute() {
        if (volumeState.muted) {
            val unmuteResult = unmuteGlobalVolume(
                audioManager = audioManager,
                preferredStreamType = trackedStreamType
            )
            trackedStreamType = unmuteResult.state.streamType
            trackedSnapshot = unmuteResult.snapshot
            volumeState = unmuteResult.state
        } else {
            val muteResult = muteGlobalVolume(
                audioManager = audioManager,
                preferredStreamType = trackedStreamType
            )
            trackedStreamType = muteResult.state.streamType
            trackedSnapshot = muteResult.snapshot
            volumeState = muteResult.state
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

private fun readMediaVolumeState(
    audioManager: AudioManager,
    preferredStreamType: Int = AudioManager.STREAM_MUSIC
): MediaVolumeState {
    val streamType = resolveActiveSystemStream(audioManager, preferredStreamType)
    val currentVolume = runCatching {
        audioManager.getStreamVolume(streamType).coerceAtLeast(0)
    }.getOrDefault(0)
    val mutedBySystem = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        runCatching { audioManager.isStreamMute(streamType) }.getOrDefault(currentVolume == 0)
    } else {
        false
    }
    return MediaVolumeState(
        current = currentVolume,
        muted = mutedBySystem || currentVolume == 0,
        streamType = streamType
    )
}

private fun resolveActiveSystemStream(
    audioManager: AudioManager,
    preferredStreamType: Int
): Int {
    if (GLOBAL_VOLUME_STREAMS.contains(preferredStreamType)) {
        return preferredStreamType
    }
    return GLOBAL_VOLUME_STREAMS.firstOrNull { stream ->
        runCatching { audioManager.getStreamVolume(stream) > 0 }.getOrDefault(false)
    } ?: AudioManager.STREAM_MUSIC
}

private fun takeGlobalVolumeSnapshot(audioManager: AudioManager): IntArray {
    return IntArray(GLOBAL_VOLUME_STREAMS.size) { index ->
        runCatching {
            audioManager.getStreamVolume(GLOBAL_VOLUME_STREAMS[index]).coerceAtLeast(0)
        }.getOrDefault(0)
    }
}

private fun resolveChangedStreamType(
    before: IntArray,
    after: IntArray,
    preferredStream: Int
): Int {
    var changedStream = preferredStream
    var bestDelta = 0
    for (index in GLOBAL_VOLUME_STREAMS.indices) {
        val delta = abs(after[index] - before[index])
        if (delta > bestDelta) {
            bestDelta = delta
            changedStream = GLOBAL_VOLUME_STREAMS[index]
        }
    }
    return if (bestDelta > 0) {
        changedStream
    } else if (GLOBAL_VOLUME_STREAMS.contains(preferredStream)) {
        preferredStream
    } else {
        AudioManager.STREAM_MUSIC
    }
}

private fun changeGlobalVolumeByStep(
    audioManager: AudioManager,
    increase: Boolean,
    preferredStreamType: Int
): GlobalVolumeResult {
    val direction = if (increase) {
        AudioManager.ADJUST_RAISE
    } else {
        AudioManager.ADJUST_LOWER
    }

    val beforeSnapshot = takeGlobalVolumeSnapshot(audioManager)
    val beforeState = readMediaVolumeState(audioManager, preferredStreamType)

    runCatching {
        audioManager.adjustSuggestedStreamVolume(
            direction,
            AudioManager.USE_DEFAULT_STREAM_TYPE,
            0
        )
    }

    var afterSnapshot = takeGlobalVolumeSnapshot(audioManager)
    var resolvedStream = resolveChangedStreamType(
        before = beforeSnapshot,
        after = afterSnapshot,
        preferredStream = beforeState.streamType
    )
    var afterState = readMediaVolumeState(audioManager, resolvedStream)

    if (afterState.current == beforeState.current && afterState.muted == beforeState.muted) {
        runCatching {
            audioManager.adjustVolume(direction, 0)
        }
        afterSnapshot = takeGlobalVolumeSnapshot(audioManager)
        resolvedStream = resolveChangedStreamType(
            before = beforeSnapshot,
            after = afterSnapshot,
            preferredStream = resolvedStream
        )
        afterState = readMediaVolumeState(audioManager, resolvedStream)
    }

    return GlobalVolumeResult(
        state = afterState,
        snapshot = afterSnapshot
    )
}

private fun muteGlobalVolume(
    audioManager: AudioManager,
    preferredStreamType: Int
): GlobalVolumeResult {
    val beforeSnapshot = takeGlobalVolumeSnapshot(audioManager)
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
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        runCatching {
            audioManager.adjustVolume(AudioManager.ADJUST_MUTE, 0)
        }
    }
    val afterSnapshot = takeGlobalVolumeSnapshot(audioManager)
    val resolvedStream = resolveChangedStreamType(
        before = beforeSnapshot,
        after = afterSnapshot,
        preferredStream = preferredStreamType
    )
    return GlobalVolumeResult(
        state = readMediaVolumeState(audioManager, resolvedStream),
        snapshot = afterSnapshot
    )
}

private fun unmuteGlobalVolume(
    audioManager: AudioManager,
    preferredStreamType: Int
): GlobalVolumeResult {
    val beforeSnapshot = takeGlobalVolumeSnapshot(audioManager)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        runCatching {
            audioManager.adjustSuggestedStreamVolume(
                AudioManager.ADJUST_UNMUTE,
                AudioManager.USE_DEFAULT_STREAM_TYPE,
                0
            )
        }
        runCatching {
            audioManager.adjustVolume(AudioManager.ADJUST_UNMUTE, 0)
        }
    }
    // Ensure unmute visibly restores at least one step on systems that keep zero after unmute.
    runCatching {
        audioManager.adjustSuggestedStreamVolume(
            AudioManager.ADJUST_RAISE,
            AudioManager.USE_DEFAULT_STREAM_TYPE,
            0
        )
    }
    val afterSnapshot = takeGlobalVolumeSnapshot(audioManager)
    val resolvedStream = resolveChangedStreamType(
        before = beforeSnapshot,
        after = afterSnapshot,
        preferredStream = preferredStreamType
    )
    return GlobalVolumeResult(
        state = readMediaVolumeState(audioManager, resolvedStream),
        snapshot = afterSnapshot
    )
}
