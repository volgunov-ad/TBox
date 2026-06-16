package vad.dashing.tbox.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel

@Composable
fun LeftMenuTabField.menuIcon(): ImageVector = when (this) {
    LeftMenuTabField.MODEM -> ImageVector.vectorResource(R.drawable.menu_icon_modem)
    LeftMenuTabField.AT_COMMANDS -> ImageVector.vectorResource(R.drawable.menu_icon_at)
    LeftMenuTabField.GEOPOSITION -> Icons.Filled.Place
    LeftMenuTabField.CAR_DATA -> Icons.Filled.Build
    LeftMenuTabField.TRIPS -> Icons.AutoMirrored.Filled.List
    LeftMenuTabField.REFUELS -> ImageVector.vectorResource(R.drawable.ic_menu_refuels)
    LeftMenuTabField.SETTINGS -> Icons.Filled.Settings
    LeftMenuTabField.FLOATING_PANELS_SETTINGS ->
        ImageVector.vectorResource(R.drawable.ic_tab_floating_panels_settings)
    LeftMenuTabField.THEMES -> ImageVector.vectorResource(R.drawable.ic_menu_themes)
    LeftMenuTabField.LOGS -> ImageVector.vectorResource(R.drawable.menu_icon_log)
    LeftMenuTabField.INFO -> Icons.Filled.Info
    LeftMenuTabField.CAN -> ImageVector.vectorResource(R.drawable.menu_icon_data)
    LeftMenuTabField.WIDGETS -> ImageVector.vectorResource(R.drawable.menu_icon_widgets)
    LeftMenuTabField.MAIN_SCREEN_SETTINGS ->
        ImageVector.vectorResource(R.drawable.ic_tab_main_screen_settings)
    LeftMenuTabField.CAR_SETTINGS ->
        ImageVector.vectorResource(R.drawable.ic_tab_car_settings)
}

private fun swapMutableMenuRowsAt(
    rows: MutableList<LeftMenuLayout.Row>,
    i: Int,
    j: Int,
) {
    if (i !in rows.indices || j !in rows.indices) return
    val t = rows[i]
    rows[i] = rows[j]
    rows[j] = t
}

@Composable
fun LeftMenuConfigDialog(
    settingsViewModel: SettingsViewModel,
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    val persisted by settingsViewModel.leftMenuLayout.collectAsStateWithLifecycle()
    val draftRows = remember { mutableStateListOf<LeftMenuLayout.Row>() }
    LaunchedEffect(visible, persisted) {
        if (visible) {
            draftRows.clear()
            draftRows.addAll(persisted.rows)
        }
    }

    val density = LocalDensity.current
    val rowStepPx = with(density) { 52.dp.toPx() }
    var activeReorderFieldId by remember { mutableStateOf<String?>(null) }
    var dragAccumY by remember { mutableFloatStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .heightIn(max = 800.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
            ) {
                AppAlertDialogTitle(stringResource(R.string.left_menu_config_dialog_title))
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.left_menu_config_dialog_hint),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 20.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    userScrollEnabled = activeReorderFieldId == null,
                ) {
                    items(
                        count = draftRows.size,
                        key = { index -> draftRows[index].field.id },
                    ) { index ->
                        val row = draftRows[index]
                        Row(
                            modifier = Modifier
                                .animateItem(
                                    placementSpec = tween(
                                        durationMillis = 220,
                                        easing = FastOutSlowInEasing,
                                    ),
                                )
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Checkbox(
                                checked = row.enabled,
                                enabled = !row.field.locked,
                                onCheckedChange = { checked ->
                                    if (row.field.locked) return@Checkbox
                                    val idx =
                                        draftRows.indexOfFirst { it.field.id == row.field.id }
                                    if (idx >= 0) {
                                        draftRows[idx] = draftRows[idx].copy(enabled = checked)
                                    }
                                },
                            )
                            Text(
                                text = stringResource(row.field.labelRes),
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 22.sp),
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(48.dp)
                                    .pointerInput(row.field.id, rowStepPx) {
                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            activeReorderFieldId = row.field.id
                                            dragAccumY = 0f
                                            try {
                                                var lastY = down.position.y
                                                verticalDrag(down.id) { change ->
                                                    change.consume()
                                                    val y = change.position.y
                                                    val dy = y - lastY
                                                    lastY = y
                                                    val fid = row.field.id
                                                    var curIdx =
                                                        draftRows.indexOfFirst { it.field.id == fid }
                                                    if (curIdx < 0) return@verticalDrag
                                                    dragAccumY += dy
                                                    while (
                                                        dragAccumY > rowStepPx &&
                                                        curIdx < draftRows.lastIndex
                                                    ) {
                                                        swapMutableMenuRowsAt(
                                                            draftRows,
                                                            curIdx,
                                                            curIdx + 1,
                                                        )
                                                        curIdx++
                                                        dragAccumY -= rowStepPx
                                                    }
                                                    while (dragAccumY < -rowStepPx && curIdx > 0) {
                                                        swapMutableMenuRowsAt(
                                                            draftRows,
                                                            curIdx,
                                                            curIdx - 1,
                                                        )
                                                        curIdx--
                                                        dragAccumY += rowStepPx
                                                    }
                                                }
                                            } finally {
                                                activeReorderFieldId = null
                                                dragAccumY = 0f
                                            }
                                        }
                                    },
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Menu,
                                    contentDescription = stringResource(
                                        R.string.left_menu_config_drag_handle_a11y,
                                    ),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = rememberWrappedOnClick { onDismiss() }) {
                        AppAlertDialogButtonLabel(stringResource(R.string.action_cancel))
                    }
                    Button(
                        onClick = rememberWrappedOnClick {
                            settingsViewModel.saveLeftMenuLayout(
                                LeftMenuLayout(LeftMenuLayout.enforceLocked(draftRows.toList())),
                            )
                            onDismiss()
                        }
                    ) {
                        AppAlertDialogButtonLabel(stringResource(R.string.action_save))
                    }
                }
            }
        }
    }
}
