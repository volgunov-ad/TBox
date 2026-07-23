package vad.dashing.tbox.ui

import android.view.SoundEffectConstants
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role

/** When true, play [SoundEffectConstants.CLICK] for wrapped taps (see [rememberWrappedOnClick]). */
val LocalClickSoundEnabled = staticCompositionLocalOf { false }

@Composable
fun rememberPlaySystemClickSound(): () -> Unit {
    val enabled = LocalClickSoundEnabled.current
    val view = LocalView.current
    return remember(enabled, view) {
        {
            if (enabled && view.isSoundEffectsEnabled) {
                view.playSoundEffect(SoundEffectConstants.CLICK)
            }
        }
    }
}

@Composable
fun rememberWrappedOnClick(onClick: () -> Unit): () -> Unit {
    val play = rememberPlaySystemClickSound()
    return remember(onClick, play) {
        {
            play()
            onClick()
        }
    }
}

@Composable
fun rememberWrappedOnCheckedChange(onCheckedChange: (Boolean) -> Unit): (Boolean) -> Unit {
    val play = rememberPlaySystemClickSound()
    return remember(onCheckedChange, play) {
        { checked ->
            play()
            onCheckedChange(checked)
        }
    }
}

fun Modifier.clickableWithSound(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val play = rememberPlaySystemClickSound()
    val wrapped = remember(onClick, play) {
        {
            play()
            onClick()
        }
    }
    Modifier.clickable(enabled = enabled, onClickLabel = onClickLabel, role = role, onClick = wrapped)
}

fun Modifier.clickableWithSound(
    interactionSource: MutableInteractionSource,
    indication: Indication?,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val play = rememberPlaySystemClickSound()
    val wrapped = remember(onClick, play) {
        {
            play()
            onClick()
        }
    }
    Modifier.clickable(
        interactionSource = interactionSource,
        indication = indication,
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        onClick = wrapped,
    )
}

fun Modifier.combinedClickableWithSound(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val indication = LocalIndication.current
    val play = rememberPlaySystemClickSound()
    val notifyPanelTileTap = LocalNotifyPanelTileTap.current
    val onClickLatest by rememberUpdatedState(onClick)
    val onLongClickLatest by rememberUpdatedState(onLongClick)
    val onDoubleClickLatest by rememberUpdatedState(onDoubleClick)
    val notifyPanelTileTapLatest by rememberUpdatedState(notifyPanelTileTap)
    val hasDoubleClick = onDoubleClick != null
    val wrappedClick = remember(play) {
        {
            play()
            onClickLatest()
            notifyPanelTileTapLatest()
        }
    }
    val wrappedDouble = if (hasDoubleClick) {
        remember(play) {
            {
                play()
                onDoubleClickLatest?.invoke()
                notifyPanelTileTapLatest()
            }
        }
    } else {
        null
    }
    val wrappedLong = if (onLongClick != null) {
        remember {
            {
                onLongClickLatest?.invoke()
                Unit
            }
        }
    } else {
        null
    }
    Modifier.combinedClickable(
        interactionSource = interactionSource,
        indication = indication,
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        onLongClickLabel = onLongClickLabel,
        onLongClick = wrappedLong,
        onDoubleClick = wrappedDouble,
        onClick = wrappedClick,
    )
}
