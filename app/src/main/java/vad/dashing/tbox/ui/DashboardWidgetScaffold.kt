package vad.dashing.tbox.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun DashboardWidgetScaffold(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onDoubleClick: (() -> Unit)? = null,
    elevation: Dp = 4.dp,
    shape: Dp = 12.dp,
    textColor: Color? = null,
    backgroundColor: Color? = null,
    content: @Composable BoxWithConstraintsScope.(availableHeight: Dp, resolvedTextColor: Color) -> Unit
) {
    val resolvedInteractionPolicy = LocalDashboardWidgetInteractionPolicy.current
    val useCardClickable = resolvedInteractionPolicy.mode == DashboardWidgetInteractionMode.STANDARD
    val playClick = rememberPlaySystemClickSound()
    val notifyPanelTileTap = LocalNotifyPanelTileTap.current
    // Stable wrappers: unstable onClick/onDoubleClick/notify lambdas must not recreate
    // combinedClickable mid-gesture (trip tiles recompose every second and used to reset
    // the double-tap detector after LocalNotifyPanelTileTap was added as a remember key).
    val onClickLatest by rememberUpdatedState(onClick)
    val onLongClickLatest by rememberUpdatedState(onLongClick)
    val onDoubleClickLatest by rememberUpdatedState(onDoubleClick)
    val notifyPanelTileTapLatest by rememberUpdatedState(notifyPanelTileTap)
    val hasDoubleClick = onDoubleClick != null
    val wrappedOnClick = remember(playClick) {
        {
            playClick()
            onClickLatest()
            notifyPanelTileTapLatest()
        }
    }
    val wrappedOnDouble = if (hasDoubleClick) {
        remember(playClick) {
            {
                playClick()
                onDoubleClickLatest?.invoke()
                notifyPanelTileTapLatest()
            }
        }
    } else {
        null
    }
    val cardInteractionSource = remember { MutableInteractionSource() }
    val cardIndication = LocalIndication.current
    Card(
        modifier = modifier
            .fillMaxSize()
            .combinedClickable(
                interactionSource = cardInteractionSource,
                indication = cardIndication,
                enabled = useCardClickable,
                onClick = wrappedOnClick,
                onLongClick = { onLongClickLatest() },
                onDoubleClick = wrappedOnDouble,
            ),
        elevation = CardDefaults.cardElevation(elevation),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor ?: MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(shape)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = Color.Transparent,
                    shape = RoundedCornerShape(shape)
                )
        ) {
            val availableHeight = maxHeight
            val resolvedTextColor = textColor ?: MaterialTheme.colorScheme.onSurface
            Box(modifier = Modifier.fillMaxSize()) {
                this@BoxWithConstraints.content(availableHeight, resolvedTextColor)
                if (!useCardClickable) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(resolvedInteractionPolicy) {
                                detectTapGestures(
                                    onTap = { offset ->
                                        if (resolvedInteractionPolicy.isActionAllowed(
                                                offset = offset,
                                                width = size.width.toFloat(),
                                                height = size.height.toFloat()
                                            )
                                        ) {
                                            playClick()
                                            onClickLatest()
                                            notifyPanelTileTapLatest()
                                        }
                                    },
                                    onLongPress = { offset ->
                                        if (resolvedInteractionPolicy.isActionAllowed(
                                                offset = offset,
                                                width = size.width.toFloat(),
                                                height = size.height.toFloat()
                                            )
                                        ) {
                                            onLongClickLatest()
                                        }
                                    }
                                )
                            }
                    )
                }
            }
        }
    }
}
