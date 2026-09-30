package vad.dashing.tbox.ui

import android.content.ClipData
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.externalapi.ExternalApiConstants
import vad.dashing.tbox.externalapi.ExternalApiControllerHolder
import vad.dashing.tbox.externalapi.ExternalApiManualToken
import vad.dashing.tbox.externalapi.ExternalApiPairRequest
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxButton
import vad.dashing.tbox.ui.theme.tboxCaption

@Composable
fun ExternalApiSettingsSection(
    settingsViewModel: SettingsViewModel,
) {
    val externalApiEnabled by settingsViewModel.externalApiEnabled.collectAsStateWithLifecycle()
    val externalApiPort by settingsViewModel.externalApiPort.collectAsStateWithLifecycle()
    val externalApiDangerousEnabled by settingsViewModel.externalApiDangerousEnabled.collectAsStateWithLifecycle()
    val controller by ExternalApiControllerHolder.instance.collectAsStateWithLifecycle()
    val idleStatusFlow = remember {
        kotlinx.coroutines.flow.MutableStateFlow(vad.dashing.tbox.externalapi.ExternalApiStatus())
    }
    val idlePendingFlow = remember {
        kotlinx.coroutines.flow.MutableStateFlow(emptyList<ExternalApiPairRequest>())
    }
    val status by (controller?.status ?: idleStatusFlow).collectAsStateWithLifecycle()
    val pendingRequests by (controller?.pendingPairRequests ?: idlePendingFlow)
        .collectAsStateWithLifecycle()

    var portDraft by remember { mutableStateOf(externalApiPort.toString()) }
    LaunchedEffect(externalApiPort) {
        portDraft = externalApiPort.toString()
    }

    var pairingTick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(status.pairingActive, status.pairingExpiresAtElapsed) {
        while (status.pairingActive) {
            pairingTick = System.currentTimeMillis()
            delay(1_000)
        }
    }

    val pairingSecondsLeft = remember(status.pairingActive, status.pairingExpiresAtElapsed, pairingTick) {
        val expiresAt = status.pairingExpiresAtElapsed ?: return@remember 0L
        ((expiresAt - SystemClock.elapsedRealtime()) / 1000L).coerceAtLeast(0L)
    }

    var approveRequest by remember { mutableStateOf<ExternalApiPairRequest?>(null) }
    val defaultManualName = stringResource(R.string.settings_api_manual_token_name_default)
    var manualClientName by remember { mutableStateOf(defaultManualName) }
    var createdManualToken by remember { mutableStateOf<ExternalApiManualToken?>(null) }

    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.settings_api_manual_token_copied)

    SettingsTitle(stringResource(R.string.settings_api_section_title))
    SettingSwitch(
        externalApiEnabled,
        { enabled -> settingsViewModel.saveExternalApiEnabled(enabled) },
        stringResource(R.string.settings_api_enable_title),
        stringResource(R.string.settings_api_enable_desc),
        true,
    )

    CalibrationIntCommitField(
        title = stringResource(R.string.settings_api_port_title),
        description = "",
        draft = portDraft,
        onDraftChange = { portDraft = it.filter { ch -> ch.isDigit() }.take(5) },
        savedValue = externalApiPort,
        minValue = ExternalApiConstants.MIN_PORT,
        maxValue = ExternalApiConstants.MAX_PORT,
        onCommit = { value -> settingsViewModel.saveExternalApiPort(value) },
    )

    val statusText = when {
        !externalApiEnabled -> stringResource(R.string.settings_api_status_disabled)
        status.lastError != null -> stringResource(R.string.settings_api_status_error, status.lastError ?: "")
        status.running -> stringResource(R.string.settings_api_status_running, status.boundPort ?: externalApiPort)
        else -> stringResource(R.string.settings_api_status_starting)
    }
    val statusColor = when {
        status.lastError != null && externalApiEnabled -> MaterialTheme.colorScheme.error
        else -> onSurfaceVariant
    }
    Text(
        text = statusText,
        style = MaterialTheme.typography.tboxBody,
        color = statusColor,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    Text(
        text = stringResource(
            R.string.settings_api_versions,
            status.apiVersion,
            status.catalogVersion,
        ),
        style = MaterialTheme.typography.tboxBody,
        color = onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )

    val effectivePort = status.boundPort ?: externalApiPort
    Text(
        text = stringResource(R.string.settings_api_url_local, effectivePort),
        style = MaterialTheme.typography.tboxBody,
        color = onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
    controller?.lanAddresses()?.forEach { address ->
        Text(
            text = stringResource(R.string.settings_api_url_lan, address, effectivePort),
            style = MaterialTheme.typography.tboxBody,
            color = onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    SettingsTitle(stringResource(R.string.settings_api_pairing_title))
    if (status.pairingActive) {
        Text(
            text = stringResource(R.string.settings_api_pairing_active, pairingSecondsLeft),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        OutlinedButton(
            onClick = rememberWrappedOnClick { controller?.stopPairing() },
            modifier = Modifier.padding(bottom = 8.dp),
        ) {
            Text(
                stringResource(R.string.settings_api_pairing_stop),
                style = MaterialTheme.typography.tboxButton,
            )
        }
    } else {
        Button(
            onClick = rememberWrappedOnClick { controller?.startPairing() },
            enabled = controller != null,
            modifier = Modifier.padding(bottom = 8.dp),
        ) {
            Text(
                stringResource(R.string.settings_api_pairing_start),
                style = MaterialTheme.typography.tboxButton,
            )
        }
    }

    pendingRequests.forEach { request ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_api_pairing_pending, request.clientName),
                style = MaterialTheme.typography.tboxBody,
                color = onSurface,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = rememberWrappedOnClick { approveRequest = request }) {
                Text(
                    stringResource(R.string.settings_api_pairing_approve),
                    style = MaterialTheme.typography.tboxButton,
                )
            }
            OutlinedButton(onClick = rememberWrappedOnClick { controller?.denyPair(request.requestId) }) {
                Text(
                    stringResource(R.string.settings_api_pairing_deny),
                    style = MaterialTheme.typography.tboxButton,
                )
            }
        }
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    SettingsTitle(stringResource(R.string.settings_api_manual_token_title))
    Text(
        text = stringResource(R.string.settings_api_manual_token_desc),
        style = MaterialTheme.typography.tboxBody,
        color = onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    OutlinedTextField(
        value = manualClientName,
        onValueChange = { manualClientName = it.take(64) },
        label = { Text(stringResource(R.string.settings_api_manual_token_name_hint)) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    )
    Button(
        onClick = rememberWrappedOnClick {
            val created = controller?.createManualToken(manualClientName) ?: return@rememberWrappedOnClick
            createdManualToken = created
        },
        enabled = controller != null,
        modifier = Modifier.padding(bottom = 8.dp),
    ) {
        Text(
            stringResource(R.string.settings_api_manual_token_create),
            style = MaterialTheme.typography.tboxButton,
        )
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    SettingsTitle(stringResource(R.string.settings_api_clients_title))
    if (status.clients.isEmpty()) {
        Text(
            text = stringResource(R.string.settings_api_clients_empty),
            style = MaterialTheme.typography.tboxBody,
            color = onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    } else {
        status.clients.forEach { client ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = client.clientName,
                        style = MaterialTheme.typography.tboxBody,
                        color = onSurface,
                    )
                    Text(
                        text = client.clientId,
                        style = MaterialTheme.typography.tboxCaption,
                        color = onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = rememberWrappedOnClick { controller?.revokeClient(client.clientId) }) {
                    Text(
                        stringResource(R.string.settings_api_client_revoke),
                        style = MaterialTheme.typography.tboxButton,
                    )
                }
            }
        }
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    SettingSwitch(
        externalApiDangerousEnabled,
        { enabled -> settingsViewModel.saveExternalApiDangerousEnabled(enabled) },
        stringResource(R.string.settings_api_dangerous_title),
        stringResource(R.string.settings_api_dangerous_desc),
        true,
    )

    Text(
        text = stringResource(R.string.settings_api_help),
        style = MaterialTheme.typography.tboxBody,
        color = onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
    )

    approveRequest?.let { request ->
        AlertDialog(
            onDismissRequest = { approveRequest = null },
            title = { AppAlertDialogTitle(stringResource(R.string.settings_api_pairing_dialog_title)) },
            text = {
                AppAlertDialogText(
                    stringResource(R.string.settings_api_pairing_dialog_message, request.clientName),
                )
            },
            confirmButton = {
                Button(
                    onClick = rememberWrappedOnClick {
                        controller?.approvePair(request.requestId)
                        approveRequest = null
                    },
                ) {
                    AppAlertDialogButtonLabel(stringResource(R.string.settings_api_pairing_approve))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = rememberWrappedOnClick { approveRequest = null }) {
                    AppAlertDialogButtonLabel(stringResource(R.string.settings_api_pairing_deny))
                }
            },
        )
    }

    createdManualToken?.let { token ->
        AlertDialog(
            onDismissRequest = { createdManualToken = null },
            title = { AppAlertDialogTitle(stringResource(R.string.settings_api_manual_token_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppAlertDialogText(
                        stringResource(
                            R.string.settings_api_manual_token_dialog_message,
                            token.clientName,
                        ),
                    )
                    Text(
                        text = token.accessToken,
                        style = MaterialTheme.typography.tboxCaption,
                        color = onSurface,
                    )
                    Text(
                        text = token.clientId,
                        style = MaterialTheme.typography.tboxCaption,
                        color = onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = rememberWrappedOnClick {
                            scope.launch {
                                clipboard.setClipEntry(
                                    ClipEntry(ClipData.newPlainText("tbox-api-token", token.accessToken)),
                                )
                            }
                            Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                        },
                    ) {
                        AppAlertDialogButtonLabel(stringResource(R.string.settings_api_manual_token_copy))
                    }
                    OutlinedButton(
                        onClick = rememberWrappedOnClick {
                            val header = "Bearer ${token.accessToken}"
                            scope.launch {
                                clipboard.setClipEntry(
                                    ClipEntry(ClipData.newPlainText("tbox-api-bearer", header)),
                                )
                            }
                            Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                        },
                    ) {
                        AppAlertDialogButtonLabel(stringResource(R.string.settings_api_manual_token_copy_header))
                    }
                    OutlinedButton(onClick = rememberWrappedOnClick { createdManualToken = null }) {
                        AppAlertDialogButtonLabel(stringResource(R.string.settings_api_manual_token_close))
                    }
                }
            },
            dismissButton = {},
        )
    }
}
