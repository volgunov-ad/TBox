package vad.dashing.tbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.adb.AdbRepository
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxButton
import vad.dashing.tbox.ui.theme.tboxCaption
import vad.dashing.tbox.ui.theme.tboxHeadline
import vad.dashing.tbox.ui.theme.tboxTitle

@Composable
fun AdbTabContent(
    settingsViewModel: SettingsViewModel,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val savedHost by settingsViewModel.adbLastHost.collectAsStateWithLifecycle()
    val savedPort by settingsViewModel.adbLastPort.collectAsStateWithLifecycle()
    val savedMode by settingsViewModel.adbMode.collectAsStateWithLifecycle()
    val state by AdbRepository.state.collectAsStateWithLifecycle()
    val usbCandidates by AdbRepository.usbCandidates.collectAsStateWithLifecycle()
    val logLines by AdbRepository.consoleLog.collectAsStateWithLifecycle()
    var host by rememberSaveable(savedHost) { mutableStateOf(savedHost) }
    var port by rememberSaveable(savedPort) { mutableStateOf(savedPort.toString()) }
    var mode by rememberSaveable(savedMode) { mutableStateOf(savedMode) }
    var selectedUsbDeviceId by rememberSaveable { mutableIntStateOf(-1) }
    var command by rememberSaveable { mutableStateOf("") }
    val logState = rememberLazyListState()
    val isBusy = state.phase == AdbRepository.Phase.CONNECTING
    val isConnected = state.phase == AdbRepository.Phase.CONNECTED

    LaunchedEffect(Unit) {
        AdbRepository.initialize(context)
        AdbRepository.refreshUsbDevices()
    }

    LaunchedEffect(usbCandidates) {
        if (usbCandidates.none { it.deviceId == selectedUsbDeviceId }) {
            selectedUsbDeviceId = usbCandidates.firstOrNull()?.deviceId ?: -1
        }
    }

    LaunchedEffect(logLines.size) {
        if (logLines.isNotEmpty()) logState.animateScrollToItem(logLines.lastIndex)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
            .padding(top = 18.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.tab_adb),
            style = MaterialTheme.typography.tboxHeadline,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            ModeButton(
                text = stringResource(R.string.adb_mode_tcp),
                isSelected = mode == "tcp",
                enabled = !isConnected && !isBusy,
                onClick = {
                    mode = "tcp"
                    settingsViewModel.saveAdbModeSetting(mode)
                },
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            ModeButton(
                text = stringResource(R.string.adb_mode_usb),
                isSelected = mode == "usb",
                enabled = !isConnected && !isBusy,
                onClick = {
                    mode = "usb"
                    settingsViewModel.saveAdbModeSetting(mode)
                    AdbRepository.refreshUsbDevices()
                },
                modifier = Modifier.weight(1f),
            )
        }

        if (mode == "tcp") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text(stringResource(R.string.adb_host)) },
                    singleLine = true,
                    enabled = !isConnected && !isBusy,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { value -> port = value.filter(Char::isDigit).take(5) },
                    label = { Text(stringResource(R.string.adb_port)) },
                    singleLine = true,
                    enabled = !isConnected && !isBusy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(0.4f),
                )
            }
        } else {
            Text(
                text = stringResource(R.string.adb_usb_device),
                style = MaterialTheme.typography.tboxTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (usbCandidates.isEmpty()) {
                Text(
                    text = stringResource(R.string.adb_no_usb_devices),
                    style = MaterialTheme.typography.tboxBody,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 140.dp),
                ) {
                    usbCandidates.forEach { candidate ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isConnected && !isBusy) {
                                    selectedUsbDeviceId = candidate.deviceId
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = candidate.deviceId == selectedUsbDeviceId,
                                onClick = { selectedUsbDeviceId = candidate.deviceId },
                                enabled = !isConnected && !isBusy,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${candidate.name} (${hexId(candidate.vendorId)}:${hexId(candidate.productId)})",
                                    style = MaterialTheme.typography.tboxBody,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (candidate.sharesNetworkWithHost) {
                                    Text(
                                        text = stringResource(R.string.adb_usb_network_device_label),
                                        style = MaterialTheme.typography.tboxCaption,
                                        color = MaterialTheme.colorScheme.error,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            val selectedSharesNetwork = usbCandidates
                .firstOrNull { it.deviceId == selectedUsbDeviceId }
                ?.sharesNetworkWithHost == true
            if (selectedSharesNetwork) {
                Text(
                    text = stringResource(R.string.adb_usb_tbox_rndis_warning),
                    style = MaterialTheme.typography.tboxCaption,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                text = stringResource(R.string.adb_authorization_hint),
                style = MaterialTheme.typography.tboxCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = {
                    if (isConnected || isBusy) {
                        AdbRepository.disconnect()
                    } else if (mode == "tcp") {
                        val portNumber = port.toIntOrNull() ?: return@Button
                        settingsViewModel.saveAdbLastHostSetting(host)
                        settingsViewModel.saveAdbLastPortSetting(portNumber)
                        settingsViewModel.saveAdbModeSetting("tcp")
                        AdbRepository.connectTcp(host.trim(), portNumber)
                    } else {
                        settingsViewModel.saveAdbModeSetting("usb")
                        AdbRepository.requestUsbPermission(selectedUsbDeviceId)
                    }
                },
                enabled = isConnected || isBusy ||
                    (mode == "tcp" && host.isNotBlank() && port.toIntOrNull() in 1..65535) ||
                    (mode == "usb" && selectedUsbDeviceId >= 0),
            ) {
                Text(
                    text = if (isConnected || isBusy) {
                        stringResource(R.string.adb_disconnect)
                    } else {
                        stringResource(R.string.adb_connect)
                    },
                    style = MaterialTheme.typography.tboxButton,
                )
            }
            Text(
                text = when (state.phase) {
                    AdbRepository.Phase.DISCONNECTED -> stringResource(R.string.adb_status_disconnected)
                    AdbRepository.Phase.CONNECTING -> stringResource(R.string.adb_connecting)
                    AdbRepository.Phase.CONNECTED -> stringResource(
                        R.string.adb_status_connected,
                        state.endpoint,
                    )
                    AdbRepository.Phase.ERROR -> stringResource(
                        R.string.adb_status_error,
                        state.error.orEmpty(),
                    )
                },
                style = MaterialTheme.typography.tboxBody,
                color = if (state.phase == AdbRepository.Phase.ERROR) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                label = { Text(stringResource(R.string.adb_command)) },
                singleLine = true,
                enabled = isConnected,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (command.isNotBlank()) {
                            AdbRepository.execute(command)
                            command = ""
                            focusManager.clearFocus()
                        }
                    },
                ),
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    AdbRepository.execute(command)
                    command = ""
                    focusManager.clearFocus()
                },
                enabled = isConnected && command.isNotBlank(),
            ) {
                Text(
                    text = stringResource(R.string.adb_execute),
                    style = MaterialTheme.typography.tboxButton,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.adb_console),
                style = MaterialTheme.typography.tboxTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            TextButton(
                onClick = AdbRepository::clearLog,
                enabled = logLines.isNotEmpty(),
            ) {
                Text(
                    text = stringResource(R.string.adb_clear_log),
                    style = MaterialTheme.typography.tboxButton,
                )
            }
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            elevation = CardDefaults.cardElevation(4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            LazyColumn(
                state = logState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(logLines) { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.tboxBody,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

private fun hexId(value: Int): String = "%04X".format(value and 0xFFFF)
