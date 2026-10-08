package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.BackgroundService
import vad.dashing.tbox.R
import vad.dashing.tbox.uda.UdaDtcEntry
import vad.dashing.tbox.uda.UdaDtcSession
import vad.dashing.tbox.uda.UdaEcuCatalog
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxCaption

@Composable
fun UdaDtcDialog(
    visible: Boolean,
    tboxConnected: Boolean,
    onServiceCommand: (String, String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    val entries by UdaDtcSession.entries.collectAsStateWithLifecycle()
    val pendingEcuId by UdaDtcSession.pendingEcuId.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val buttonsEnabled = tboxConnected && pendingEcuId == null

    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) {
            listState.animateScrollToItem(entries.lastIndex)
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
            Column(modifier = Modifier.padding(24.dp)) {
                AppAlertDialogTitle(stringResource(R.string.uda_dtc_dialog_title))
                Text(
                    text = stringResource(R.string.uda_dtc_dialog_hint),
                    style = MaterialTheme.typography.tboxBody,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    UdaEcuCatalog.all.chunked(4).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            row.forEach { ecu ->
                                OutlinedButton(
                                    onClick = rememberWrappedOnClick {
                                        onServiceCommand(
                                            BackgroundService.ACTION_UDA_READ_DTC,
                                            BackgroundService.EXTRA_UDA_ECU_ID,
                                            ecu.id.toString(),
                                        )
                                    },
                                    enabled = buttonsEnabled,
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                                ) {
                                    Text(
                                        text = ecu.name,
                                        style = MaterialTheme.typography.tboxCaption,
                                        maxLines = 1,
                                    )
                                }
                            }
                            repeat(4 - row.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
                if (pendingEcuId != null) {
                    Text(
                        text = stringResource(
                            R.string.uda_dtc_reading,
                            UdaEcuCatalog.nameOf(pendingEcuId!!),
                        ),
                        style = MaterialTheme.typography.tboxCaption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (entries.isEmpty()) {
                    Text(
                        text = stringResource(R.string.uda_dtc_empty),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        style = MaterialTheme.typography.tboxBody,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(entries) { entry ->
                            Text(
                                text = udaDtcEntryText(entry),
                                style = MaterialTheme.typography.tboxBody,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = rememberWrappedOnClick { UdaDtcSession.clear() },
                        enabled = entries.isNotEmpty(),
                    ) {
                        AppAlertDialogButtonLabel(stringResource(R.string.uda_dtc_clear))
                    }
                    Button(
                        onClick = rememberWrappedOnClick(onDismiss),
                        modifier = Modifier.weight(1f),
                    ) {
                        AppAlertDialogButtonLabel(stringResource(R.string.action_close))
                    }
                }
            }
        }
    }
}

@Composable
private fun udaDtcEntryText(entry: UdaDtcEntry): String = when (entry) {
    is UdaDtcEntry.None -> stringResource(R.string.uda_dtc_none, entry.ecuName)
    is UdaDtcEntry.Code -> stringResource(R.string.uda_dtc_code, entry.ecuName, entry.code)
    is UdaDtcEntry.Failure -> stringResource(R.string.uda_dtc_failure, entry.ecuName, entry.detail)
    is UdaDtcEntry.Timeout -> stringResource(R.string.uda_dtc_timeout, entry.ecuName)
}
