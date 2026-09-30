package vad.dashing.tbox.ui

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxButton
import vad.dashing.tbox.ui.theme.tboxCaption
import vad.dashing.tbox.externalapi.ExternalApiConstants
import vad.dashing.tbox.externalapi.ExternalApiControllerHolder
import vad.dashing.tbox.externalapi.ExternalApiPairRequest

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
        ((expiresAt - android.os.SystemClock.elapsedRealtime()) / 1000L).coerceAtLeast(0L)
    }

    var approveRequest by remember { mutableStateOf<ExternalApiPairRequest?>(null) }

    SettingsTitle(stringResource(R.string.settings_api_section_title))
    SettingSwitch(
        externalApiEnabled,
        { enabled -> settingsViewModel.saveExternalApiEnabled(enabled) },
        stringResource(R.string.settings_api_enable_title),
        stringResource(R.string.settings_api_enable_desc),
        true,
    )

    OutlinedTextField(
        value = portDraft,
        onValueChange = { portDraft = it.filter { ch -> ch.isDigit() }.take(5) },
        label = { Text(stringResource(R.string.settings_api_port_title)) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        singleLine = true,
    )
    Button(
        onClick = rememberWrappedOnClick {
            val port = portDraft.toIntOrNull() ?: ExternalApiConstants.DEFAULT_PORT
            settingsViewModel.saveExternalApiPort(port)
        },
        modifier = Modifier.padding(bottom = 8.dp),
    ) {
        Text(stringResource(R.string.settings_api_port_save), style = MaterialTheme.typography.tboxButton)
    }

    val statusText = when {
        !externalApiEnabled -> stringResource(R.string.settings_api_status_disabled)
        status.lastError != null -> stringResource(R.string.settings_api_status_error, status.lastError ?: "")
        status.running -> stringResource(R.string.settings_api_status_running, status.boundPort ?: externalApiPort)
        else -> stringResource(R.string.settings_api_status_starting)
    }
    Text(
        text = statusText,
        style = MaterialTheme.typography.tboxBody,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    Text(
        text = stringResource(
            R.string.settings_api_versions,
            status.apiVersion,
            status.catalogVersion,
        ),
        style = MaterialTheme.typography.tboxBody,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )

    val effectivePort = status.boundPort ?: externalApiPort
    Text(
        text = stringResource(R.string.settings_api_url_local, effectivePort),
        style = MaterialTheme.typography.tboxBody,
        modifier = Modifier.padding(bottom = 4.dp),
    )
    controller?.lanAddresses()?.forEach { address ->
        Text(
            text = stringResource(R.string.settings_api_url_lan, address, effectivePort),
            style = MaterialTheme.typography.tboxBody,
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
            Text(stringResource(R.string.settings_api_pairing_stop))
        }
    } else {
        Button(
            onClick = rememberWrappedOnClick { controller?.startPairing() },
            enabled = controller != null,
            modifier = Modifier.padding(bottom = 8.dp),
        ) {
            Text(stringResource(R.string.settings_api_pairing_start))
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
                modifier = Modifier.weight(1f),
            )
            Button(onClick = rememberWrappedOnClick { approveRequest = request }) {
                Text(stringResource(R.string.settings_api_pairing_approve))
            }
            OutlinedButton(onClick = rememberWrappedOnClick { controller?.denyPair(request.requestId) }) {
                Text(stringResource(R.string.settings_api_pairing_deny))
            }
        }
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    SettingsTitle(stringResource(R.string.settings_api_clients_title))
    if (status.clients.isEmpty()) {
        Text(
            text = stringResource(R.string.settings_api_clients_empty),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    Text(text = client.clientName, style = MaterialTheme.typography.tboxBody)
                    Text(
                        text = client.clientId,
                        style = MaterialTheme.typography.tboxCaption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = rememberWrappedOnClick { controller?.revokeClient(client.clientId) }) {
                    Text(stringResource(R.string.settings_api_client_revoke))
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
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
}
