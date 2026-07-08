package vad.dashing.tbox.ui.launcher

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.ui.LaunchableAppEntry
import vad.dashing.tbox.ui.rememberLaunchableAppEntries
import vad.dashing.tbox.ui.theme.tboxCaption

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherAppDrawer(
    visible: Boolean,
    settingsViewModel: SettingsViewModel,
    onDismiss: () -> Unit,
    editMode: Boolean,
    onEditModeChange: (Boolean) -> Unit,
    configRevision: Int,
    onConfigChanged: () -> Unit,
    bottomInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val iconRevision by settingsViewModel.launcherAppIconRevision.collectAsStateWithLifecycle()
    val rawApps = rememberLaunchableAppEntries(settingsViewModel, iconRevision)
    val priority = remember(context) { LauncherOemAppSort.loadPriorityPackages(context) }
    val hidden = remember(context, configRevision) { LauncherAppConfigStore.hiddenPackages(context) }
    val visibleApps = remember(rawApps, hidden) { LauncherAppConfigStore.filterVisible(rawApps, hidden) }
    val apps = remember(visibleApps, priority) { LauncherOemAppSort.sortEntries(visibleApps, priority) }
    val hiddenApps = remember(rawApps, hidden) { rawApps.filter { it.packageName in hidden } }

    var dragWindowPos by remember { mutableStateOf<Offset?>(null) }
    var dragApp by remember { mutableStateOf<LaunchableAppEntry?>(null) }

    fun finishDrag() {
        val pos = dragWindowPos
        val pkg = dragApp?.packageName
        if (pos != null && pkg != null) {
            val zone = LauncherDropTargetState.findDropAt(pos.x.roundToInt(), pos.y.roundToInt())
            if (zone != null) {
                when (zone.kind) {
                    LauncherDropZoneKind.Grid ->
                        LauncherAppConfigStore.setGridSlot(context, zone.slotIndex, pkg)
                    LauncherDropZoneKind.Dock ->
                        LauncherAppConfigStore.setDockSlot(context, zone.slotIndex, pkg)
                }
                onConfigChanged()
            }
        }
        onEditModeChange(false)
        dragApp = null
        dragWindowPos = null
        LauncherDropTargetState.clearDrag()
    }

    fun pinToFirstGridSlot(packageName: String) {
        val slots = LauncherAppConfigStore.gridPackages(context)
        val target = slots.indexOfFirst { it.isBlank() }.takeIf { it >= 0 } ?: 0
        LauncherAppConfigStore.setGridSlot(context, target, packageName)
        onConfigChanged()
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LauncherColors.DrawerScrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (editMode) onEditModeChange(false) else onDismiss()
                    },
                ),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(LauncherColors.DrawerSurface)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .padding(top = 12.dp, bottom = 20.dp + bottomInset),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (editMode) {
                            stringResource(R.string.launcher_app_drawer_edit_title)
                        } else {
                            stringResource(R.string.launcher_app_drawer_title)
                        },
                        style = MaterialTheme.typography.tboxCaption,
                        color = LauncherColors.TextPrimary,
                        fontSize = 18.sp,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (editMode) {
                            TextButton(onClick = { onEditModeChange(false) }) {
                                Text(
                                    text = stringResource(R.string.launcher_app_drawer_done),
                                    color = LauncherColors.AccentCyan,
                                )
                            }
                        }
                        IconButton(onClick = {
                            if (editMode) onEditModeChange(false) else onDismiss()
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.action_close),
                                tint = LauncherColors.AccentCyan,
                            )
                        }
                    }
                }

                if (editMode) {
                    Text(
                        text = stringResource(R.string.launcher_app_drawer_edit_hint),
                        color = LauncherColors.TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                } else {
                    Text(
                        text = stringResource(R.string.launcher_app_drawer_drag_hint),
                        color = LauncherColors.TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    apps.forEach { app ->
                        LauncherAppDrawerItem(
                            app = app,
                            editMode = editMode,
                            onClick = {
                                if (editMode) {
                                    pinToFirstGridSlot(app.packageName)
                                } else {
                                    launchLauncherApp(context, app.packageName, app.activityName)
                                    onDismiss()
                                }
                            },
                            onLongClick = { onEditModeChange(true) },
                            onHide = {
                                LauncherAppConfigStore.hidePackage(context, app.packageName)
                                onConfigChanged()
                            },
                            onEnterEditMode = { onEditModeChange(true) },
                            onDragStart = {
                                dragApp = app
                                LauncherDropTargetState.draggingPackage = app.packageName
                            },
                            onDragWindow = { windowPos -> dragWindowPos = windowPos },
                            onDragEnd = { finishDrag() },
                        )
                    }
                }

                if (editMode && hiddenApps.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.launcher_app_drawer_hidden_title),
                        color = LauncherColors.TextMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        hiddenApps.forEach { app ->
                            LauncherAppDrawerItem(
                                app = app,
                                editMode = true,
                                hidden = true,
                                onClick = {},
                                onLongClick = {},
                                onHide = {},
                                onRestore = {
                                    LauncherAppConfigStore.showPackage(context, app.packageName)
                                    onConfigChanged()
                                },
                                onDragStart = {},
                                onDragWindow = {},
                                onDragEnd = {},
                            )
                        }
                    }
                }
            }

            dragApp?.let { app ->
                val pos = dragWindowPos ?: return@let
                val density = LocalDensity.current
                val half = with(density) { 36.dp.roundToPx() }
                Box(
                    modifier = Modifier
                        .zIndex(200f)
                        .offset {
                            IntOffset(
                                (pos.x - half).roundToInt(),
                                (pos.y - half).roundToInt(),
                            )
                        }
                        .size(72.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(LauncherColors.CardDarkElevated),
                    contentAlignment = Alignment.Center,
                ) {
                    if (app.icon != null) {
                        Image(
                            bitmap = app.icon,
                            contentDescription = app.label,
                            modifier = Modifier.size(56.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LauncherAppDrawerItem(
    app: LaunchableAppEntry,
    editMode: Boolean,
    hidden: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onHide: () -> Unit,
    onRestore: (() -> Unit)? = null,
    onEnterEditMode: () -> Unit = {},
    onDragStart: () -> Unit,
    onDragWindow: (Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    var itemWindowOrigin by remember { mutableStateOf(Offset.Zero) }

    Column(
        modifier = Modifier
            .size(width = 108.dp, height = 120.dp)
            .onGloballyPositioned { coordinates ->
                val origin = coordinates.localToWindow(Offset.Zero)
                itemWindowOrigin = Offset(origin.x, origin.y)
            }
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (!hidden) {
                    Modifier.pointerInput(app.packageName) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                onEnterEditMode()
                                onDragStart()
                            },
                            onDrag = { change, _ ->
                                onDragWindow(itemWindowOrigin + change.position)
                                change.consume()
                            },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() },
                        )
                    }
                } else {
                    Modifier
                },
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(LauncherColors.CardDark),
                contentAlignment = Alignment.Center,
            ) {
                if (app.icon != null) {
                    Image(
                        bitmap = app.icon,
                        contentDescription = app.label,
                        modifier = Modifier.size(56.dp),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Text(
                        text = app.label.take(1).uppercase(),
                        color = LauncherColors.AccentCyan,
                        fontSize = 24.sp,
                    )
                }
            }
            if (editMode) {
                val actionModifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(LauncherColors.CardDarkElevated)
                    .clickable {
                        if (hidden) onRestore?.invoke() else onHide()
                    }
                if (hidden) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.launcher_app_show_cd),
                        tint = LauncherColors.AccentCyan,
                        modifier = actionModifier.padding(4.dp),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.launcher_app_hide_cd),
                        tint = Color(0xFFFF8A80),
                        modifier = actionModifier.padding(4.dp),
                    )
                }
            }
        }
        Text(
            text = app.label,
            style = MaterialTheme.typography.tboxCaption,
            color = LauncherColors.TextPrimary,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
