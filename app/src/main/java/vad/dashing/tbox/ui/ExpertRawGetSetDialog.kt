package vad.dashing.tbox.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import vad.dashing.tbox.HeadUnitCanMode
import vad.dashing.tbox.R
import vad.dashing.tbox.mbcan.ExpertRawCanBus
import vad.dashing.tbox.mbcan.ExpertRawCanCatalog
import vad.dashing.tbox.mbcan.ExpertRawCanParam
import vad.dashing.tbox.mbcan.ExpertRawSnapshot
import vad.dashing.tbox.mbcan.HeadUnitCanModeLabel
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxCaption

/** Floor for the parameter list. Chrome scrolls with the dialog so this block can stay tall. */
private val ExpertRawParamListMinHeight = 320.dp

/** Diff lines shown on screen; the journal gets all of them. */
private const val ExpertRawSnapshotDisplayLimit = 300

@Composable
fun ExpertRawGetSetDialog(
    visible: Boolean,
    mode: HeadUnitCanMode,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    val scope = rememberCoroutineScope()
    val catalog = remember { ExpertRawCanCatalog.allParams() }
    val modeLabel = remember(mode) {
        when (mode) {
            HeadUnitCanMode.Android9MbCan -> HeadUnitCanModeLabel.Android9MbCan
            HeadUnitCanMode.Android10Vhal -> HeadUnitCanModeLabel.Android10Vhal
        }
    }
    val modeStorage = mode.storageValue

    var filterText by remember { mutableStateOf("") }
    var selected by remember {
        mutableStateOf(catalog.firstOrNull { ExpertRawCanCatalog.isListed(it, modeLabel) })
    }
    var writeEnabled by remember { mutableStateOf(false) }
    var setValueText by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var showSetConfirm by remember { mutableStateOf(false) }
    var snapshot by remember { mutableStateOf<Map<String, String>?>(null) }
    val context = LocalContext.current

    val filtered = remember(filterText, catalog, modeLabel) {
        ExpertRawCanCatalog.filterParams(
            catalog.filter { ExpertRawCanCatalog.isListed(it, modeLabel) },
            filterText,
        )
    }

    fun runGet() {
        val param = selected ?: return
        if (busy) return
        busy = true
        statusText = ""
        scope.launch {
            val result = UniversalCanRepository.getRawProperty(param.bus, param.mbCanId)
            ExpertRawCanCatalog.logGetAttempt(param, result, modeStorage)
            val decode = result.rawValue?.let {
                ExpertRawCanCatalog.optionalDecodeHint(param, it, modeLabel)
            }
            statusText = buildString {
                append(if (result.success) "GET ok" else "GET fail")
                if (result.fields == null) append("  raw=${result.rawValue ?: "—"}")
                if (decode != null) append("  ($decode)")
                append("\n")
                append(ExpertRawCanCatalog.formatIdsSummary(param, modeLabel))
                append("\n")
                append("effective=${result.effectivePropertyId ?: "—"}  ${result.message}")
                result.fields?.forEach { (field, value) -> append("\n$field = $value") }
            }
            busy = false
        }
    }

    fun runSnapshot(compare: Boolean) {
        if (busy) return
        val before = snapshot
        if (compare && before == null) return
        busy = true
        statusText = ""
        scope.launch {
            val rows = catalog.filter { ExpertRawCanCatalog.isReadable(it, modeLabel) }
            val results = UniversalCanRepository.readRawSnapshot(rows) { done, total ->
                statusText = context.getString(R.string.expert_raw_get_set_snapshot_progress, done, total)
            }
            val current = ExpertRawSnapshot.toMap(results)
            ExpertRawCanCatalog.logSnapshot(current, modeStorage)
            val okCount = ExpertRawSnapshot.okCount(current)
            statusText = if (compare && before != null) {
                val changes = ExpertRawSnapshot.diff(before, current)
                ExpertRawCanCatalog.logSnapshotDiff(changes, modeStorage)
                if (changes.isEmpty()) {
                    context.getString(R.string.expert_raw_get_set_compare_none, okCount)
                } else {
                    buildString {
                        append(context.getString(R.string.expert_raw_get_set_compare_header, changes.size))
                        changes.take(ExpertRawSnapshotDisplayLimit).forEach {
                            append("\n").append(ExpertRawSnapshot.formatChange(it))
                        }
                        if (changes.size > ExpertRawSnapshotDisplayLimit) append("\n…")
                    }
                }
            } else {
                context.getString(R.string.expert_raw_get_set_snapshot_done, rows.size, okCount)
            }
            snapshot = current
            busy = false
        }
    }

    fun runSetConfirmed() {
        val param = selected ?: return
        val value = setValueText.trim().toIntOrNull()
        if (value == null || busy) return
        busy = true
        statusText = ""
        scope.launch {
            val result = UniversalCanRepository.setRawProperty(param.bus, param.mbCanId, value)
            ExpertRawCanCatalog.logSetAttempt(param, value, result, modeStorage)
            statusText = buildString {
                append(if (result.success) "SET ok" else "SET fail")
                append("  value=$value")
                append("\n")
                append(ExpertRawCanCatalog.formatIdsSummary(param, modeLabel))
                append("\n")
                append("effective=${result.effectivePropertyId ?: "—"}  ${result.message}")
            }
            busy = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.tboxDialogSurfaceFill(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
            ) {
                // The list keeps a tall viewport. Title, filter and Get/Set sit in the
                // same scroll, so a short window can move them off and show more rows.
                val listHeight = maxOf(ExpertRawParamListMinHeight, maxHeight * 0.72f)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AppAlertDialogTitle(stringResource(R.string.expert_raw_get_set_title))
                    Text(
                        text = stringResource(R.string.expert_raw_get_set_desc),
                        style = MaterialTheme.typography.tboxBody,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.expert_raw_get_set_mode, modeStorage),
                        style = MaterialTheme.typography.tboxCaption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = filterText,
                        onValueChange = { filterText = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.expert_raw_get_set_filter)) },
                        textStyle = MaterialTheme.typography.tboxBody,
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(listHeight),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(
                            filtered,
                            key = { "${it.bus.name}-${it.mbCanId}-${it.name}" },
                        ) { param ->
                            val isSelected = param == selected
                            Text(
                                text = ExpertRawCanCatalog.displayLabel(param, modeLabel),
                                style = MaterialTheme.typography.tboxCaption,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        onClick = rememberWrappedOnClick {
                                            selected = param
                                            statusText = ""
                                        },
                                    )
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                            )
                        }
                    }
                    selected?.let { param ->
                        SelectionContainer {
                            Text(
                                text = ExpertRawCanCatalog.formatIdsSummary(param, modeLabel),
                                style = MaterialTheme.typography.tboxCaption.copy(
                                    fontFamily = FontFamily.Monospace,
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (modeLabel == HeadUnitCanModeLabel.Android10Vhal && param.readWriteDiffer) {
                            Text(
                                text = stringResource(
                                    R.string.expert_raw_get_set_rw_differ,
                                    param.vhalReadId.toString(),
                                    param.vhalWriteId.toString(),
                                ),
                                style = MaterialTheme.typography.tboxCaption,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = rememberWrappedOnClick { runGet() },
                            enabled = selected != null && !busy,
                        ) {
                            AppAlertDialogButtonLabel(stringResource(R.string.expert_raw_get_set_get))
                        }
                        OutlinedButton(
                            onClick = rememberWrappedOnClick { runSnapshot(compare = false) },
                            enabled = !busy,
                        ) {
                            AppAlertDialogButtonLabel(stringResource(R.string.expert_raw_get_set_snapshot))
                        }
                        OutlinedButton(
                            onClick = rememberWrappedOnClick { runSnapshot(compare = true) },
                            enabled = !busy && snapshot != null,
                        ) {
                            AppAlertDialogButtonLabel(stringResource(R.string.expert_raw_get_set_compare))
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Switch(
                            checked = writeEnabled,
                            onCheckedChange = rememberWrappedOnCheckedChange { writeEnabled = it },
                        )
                        Text(
                            text = stringResource(R.string.expert_raw_get_set_allow_write),
                            style = MaterialTheme.typography.tboxBody,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    OutlinedTextField(
                        value = setValueText,
                        onValueChange = { setValueText = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = writeEnabled && !busy,
                        label = { Text(stringResource(R.string.expert_raw_get_set_value)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.tboxBody.copy(fontFamily = FontFamily.Monospace),
                    )
                    Button(
                        onClick = rememberWrappedOnClick { showSetConfirm = true },
                        enabled = writeEnabled &&
                            selected != null &&
                            selected?.bus != ExpertRawCanBus.MbCanObject &&
                            !busy &&
                            setValueText.trim().toIntOrNull() != null,
                    ) {
                        AppAlertDialogButtonLabel(stringResource(R.string.expert_raw_get_set_set))
                    }
                    if (statusText.isNotEmpty()) {
                        SelectionContainer {
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.tboxCaption.copy(
                                    fontFamily = FontFamily.Monospace,
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = rememberWrappedOnClick(onDismiss),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    ) {
                        AppAlertDialogButtonLabel(stringResource(R.string.action_close))
                    }
                }
            }
        }
    }

    if (showSetConfirm) {
        val param = selected
        val value = setValueText.trim().toIntOrNull()
        AlertDialog(
            onDismissRequest = { showSetConfirm = false },
            title = { AppAlertDialogTitle(stringResource(R.string.expert_raw_get_set_confirm_title)) },
            text = {
                AppAlertDialogText(
                    stringResource(
                        R.string.expert_raw_get_set_confirm_message,
                        param?.let { ExpertRawCanCatalog.displayLabel(it, modeLabel) } ?: "?",
                        value?.toString() ?: "?",
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = rememberWrappedOnClick {
                        showSetConfirm = false
                        runSetConfirmed()
                    },
                ) {
                    AppAlertDialogButtonLabel(stringResource(R.string.expert_raw_get_set_set))
                }
            },
            dismissButton = {
                TextButton(onClick = rememberWrappedOnClick { showSetConfirm = false }) {
                    AppAlertDialogButtonLabel(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
