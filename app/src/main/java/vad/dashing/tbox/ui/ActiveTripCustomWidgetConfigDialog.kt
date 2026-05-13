package vad.dashing.tbox.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.trip.ActiveTripCustomWidgetLayout

private fun swapMutableRowsAt(
    rows: MutableList<ActiveTripCustomWidgetLayout.Row>,
    i: Int,
    j: Int,
) {
    if (i !in rows.indices || j !in rows.indices) return
    val t = rows[i]
    rows[i] = rows[j]
    rows[j] = t
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActiveTripCustomWidgetConfigDialog(
    settingsViewModel: SettingsViewModel,
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    val persisted by settingsViewModel.activeTripCustomWidgetLayout.collectAsStateWithLifecycle()
    val draftRows = remember { mutableStateListOf<ActiveTripCustomWidgetLayout.Row>() }
    LaunchedEffect(visible, persisted) {
        if (visible) {
            draftRows.clear()
            draftRows.addAll(persisted.rows)
        }
    }

    val density = LocalDensity.current
    val rowStepPx = with(density) { 52.dp.toPx() }
    var dragFieldId by remember { mutableStateOf<String?>(null) }
    var dragAccumY by remember { mutableFloatStateOf(0f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { AppAlertDialogTitle(stringResource(R.string.trips_custom_widget_dialog_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.trips_custom_widget_dialog_hint),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 20.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                draftRows.forEach { row ->
                    key(row.field.id) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = row.enabled,
                                onCheckedChange = { checked ->
                                    val idx = draftRows.indexOfFirst { it.field.id == row.field.id }
                                    if (idx >= 0) {
                                        draftRows[idx] = draftRows[idx].copy(enabled = checked)
                                    }
                                }
                            )
                            Text(
                                text = stringResource(row.field.labelRes),
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 22.sp),
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = stringResource(R.string.trips_custom_widget_drag_handle_a11y),
                                modifier = Modifier
                                    .pointerInput(row.field.id, rowStepPx) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                dragFieldId = row.field.id
                                                dragAccumY = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                val fid = dragFieldId ?: return@detectDragGesturesAfterLongPress
                                                var curIdx = draftRows.indexOfFirst { it.field.id == fid }
                                                if (curIdx < 0) return@detectDragGesturesAfterLongPress
                                                dragAccumY += dragAmount.y
                                                while (dragAccumY > rowStepPx && curIdx < draftRows.lastIndex) {
                                                    swapMutableRowsAt(draftRows, curIdx, curIdx + 1)
                                                    curIdx++
                                                    dragAccumY -= rowStepPx
                                                }
                                                while (dragAccumY < -rowStepPx && curIdx > 0) {
                                                    swapMutableRowsAt(draftRows, curIdx, curIdx - 1)
                                                    curIdx--
                                                    dragAccumY += rowStepPx
                                                }
                                            },
                                            onDragEnd = {
                                                dragFieldId = null
                                                dragAccumY = 0f
                                            },
                                            onDragCancel = {
                                                dragFieldId = null
                                                dragAccumY = 0f
                                            }
                                        )
                                    }
                                    .padding(8.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = rememberWrappedOnClick {
                    settingsViewModel.saveActiveTripCustomWidgetLayout(
                        ActiveTripCustomWidgetLayout(draftRows.toList())
                    )
                    onDismiss()
                }
            ) {
                AppAlertDialogButtonLabel(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = rememberWrappedOnClick { onDismiss() }) {
                AppAlertDialogButtonLabel(stringResource(R.string.action_cancel))
            }
        }
    )
}
