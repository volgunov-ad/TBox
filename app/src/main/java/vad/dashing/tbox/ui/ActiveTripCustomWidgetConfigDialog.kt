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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.trip.ActiveTripCustomWidgetLayout

enum class ActiveTripWidgetLayoutEditorTarget {
    Custom,
    Simple,
}

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

/**
 * Custom [Dialog] instead of [androidx.compose.material3.AlertDialog]: M3 AlertDialog wraps the
 * `text` slot in a scroll container that steals vertical drags from the reorder handle.
 */
@Composable
fun ActiveTripCustomWidgetConfigDialog(
    settingsViewModel: SettingsViewModel,
    visible: Boolean,
    target: ActiveTripWidgetLayoutEditorTarget = ActiveTripWidgetLayoutEditorTarget.Custom,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    val customLayout by settingsViewModel.activeTripCustomWidgetLayout.collectAsStateWithLifecycle()
    val simpleLayout by settingsViewModel.activeTripSimpleWidgetLayout.collectAsStateWithLifecycle()
    val persisted = when (target) {
        ActiveTripWidgetLayoutEditorTarget.Custom -> customLayout
        ActiveTripWidgetLayoutEditorTarget.Simple -> simpleLayout
    }
    val titleRes = when (target) {
        ActiveTripWidgetLayoutEditorTarget.Custom -> R.string.trips_custom_widget_dialog_title
        ActiveTripWidgetLayoutEditorTarget.Simple -> R.string.trips_simple_widget_dialog_title
    }
    val hintRes = when (target) {
        ActiveTripWidgetLayoutEditorTarget.Custom -> R.string.trips_custom_widget_dialog_hint
        ActiveTripWidgetLayoutEditorTarget.Simple -> R.string.trips_simple_widget_dialog_hint
    }
    val draftRows = remember { mutableStateListOf<ActiveTripCustomWidgetLayout.Row>() }
    LaunchedEffect(visible, persisted, target) {
        if (visible) {
            draftRows.clear()
            draftRows.addAll(persisted.rows)
        }
    }

    val density = LocalDensity.current
    val rowStepPx = with(density) { 52.dp.toPx() }
    /** Set on finger-down on the handle so list scroll is off before touch slop (fixes scroll stealing). */
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
                AppAlertDialogTitle(stringResource(titleRes))
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(hintRes),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 20.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
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
                                onCheckedChange = { checked ->
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
                                                        swapMutableRowsAt(
                                                            draftRows,
                                                            curIdx,
                                                            curIdx + 1,
                                                        )
                                                        curIdx++
                                                        dragAccumY -= rowStepPx
                                                    }
                                                    while (dragAccumY < -rowStepPx && curIdx > 0) {
                                                        swapMutableRowsAt(
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
                                        R.string.trips_custom_widget_drag_handle_a11y,
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
                            val layout = ActiveTripCustomWidgetLayout(draftRows.toList())
                            when (target) {
                                ActiveTripWidgetLayoutEditorTarget.Custom ->
                                    settingsViewModel.saveActiveTripCustomWidgetLayout(layout)
                                ActiveTripWidgetLayoutEditorTarget.Simple ->
                                    settingsViewModel.saveActiveTripSimpleWidgetLayout(layout)
                            }
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
