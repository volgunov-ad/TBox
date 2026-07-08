package vad.dashing.tbox.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import vad.dashing.tbox.R

/**
 * Full-screen drop overlay while dragging an app from the drawer.
 * Grid/dock slots sit above the drawer so drops are not blocked by the scrim.
 */
@Composable
fun LauncherAppDragOverlay(
    dragging: Boolean,
    sidebarWidth: Dp,
    modifier: Modifier = Modifier,
) {
    if (!dragging) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(150f)
            .background(LauncherColors.DrawerScrim.copy(alpha = 0.55f)),
    ) {
        Text(
            text = stringResource(R.string.launcher_drag_overlay_hint),
            color = LauncherColors.TextPrimary,
            fontSize = 14.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 56.dp, bottom = 88.dp, start = 12.dp, end = 12.dp),
        ) {
            Box(modifier = Modifier.width(sidebarWidth))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.launcher_drag_grid_title),
                    color = LauncherColors.AccentCyan,
                    fontSize = 13.sp,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    repeat(3) { row ->
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            repeat(3) { col ->
                                val index = row * 3 + col
                                val key = "overlay-grid-$index"
                                LauncherDropTargetLifecycle(key)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .registerLauncherDropTarget(
                                            key = key,
                                            kind = LauncherDropZoneKind.Grid,
                                            slotIndex = index,
                                            enabled = true,
                                        )
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(LauncherColors.CardDark.copy(alpha = 0.75f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        color = LauncherColors.TextMuted,
                                        fontSize = 18.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 120.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        ) {
            Text(
                text = stringResource(R.string.launcher_drag_dock_title),
                color = LauncherColors.AccentCyan,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .padding(end = 4.dp),
            )
            repeat(4) { index ->
                val key = "overlay-dock-$index"
                LauncherDropTargetLifecycle(key)
                Box(
                    modifier = Modifier
                        .registerLauncherDropTarget(
                            key = key,
                            kind = LauncherDropZoneKind.Dock,
                            slotIndex = index,
                            enabled = true,
                        )
                        .clip(RoundedCornerShape(12.dp))
                        .background(LauncherColors.CardDark.copy(alpha = 0.85f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "D${index + 1}",
                        color = LauncherColors.TextMuted,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}
