package vad.dashing.tbox.ui.launcher

import android.graphics.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.registerLauncherDropTarget(
    key: String,
    kind: LauncherDropZoneKind,
    slotIndex: Int,
    enabled: Boolean = true,
): Modifier {
    if (!enabled) return this
    return this.onGloballyPositioned { coordinates ->
        val rect = coordinates.boundsInWindow()
        LauncherDropTargetState.register(
            key = key,
            zone = LauncherDropZone(
                kind = kind,
                slotIndex = slotIndex,
                bounds = Rect(
                    rect.left.toInt(),
                    rect.top.toInt(),
                    rect.right.toInt(),
                    rect.bottom.toInt(),
                ),
            ),
        )
    }
}

@Composable
fun LauncherDropTargetLifecycle(key: String) {
    DisposableEffect(key) {
        onDispose { LauncherDropTargetState.unregister(key) }
    }
}

@Composable
fun LauncherDropHighlight(
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!active) return
    Box(
        modifier = modifier
            .fillMaxSize()
            .border(
                width = 2.dp,
                color = LauncherColors.AccentCyan,
                shape = RoundedCornerShape(12.dp),
            )
            .background(LauncherColors.AccentCyan.copy(alpha = 0.12f)),
    )
}
