package vad.dashing.tbox.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.adb.AdbRepository
import vad.dashing.tbox.adb.AdbShellScriptParser
import vad.dashing.tbox.adb.HuAdbControl
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
    val resources = LocalResources.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val savedHost by settingsViewModel.adbLastHost.collectAsStateWithLifecycle()
    val savedPort by settingsViewModel.adbLastPort.collectAsStateWithLifecycle()
    val savedMode by settingsViewModel.adbMode.collectAsStateWithLifecycle()
    val state by AdbRepository.state.collectAsStateWithLifecycle()
    val usbCandidates by AdbRepository.usbCandidates.collectAsStateWithLifecycle()
    val logLines by AdbRepository.consoleLog.collectAsStateWithLifecycle()
    val scriptRun by AdbRepository.scriptRun.collectAsStateWithLifecycle()
    val huAdbState by HuAdbControl.state.collectAsStateWithLifecycle()
    val huAdbError by HuAdbControl.lastError.collectAsStateWithLifecycle()
    var host by rememberSaveable(savedHost) { mutableStateOf(savedHost) }
    var port by rememberSaveable(savedPort) { mutableStateOf(savedPort.toString()) }
    var mode by rememberSaveable(savedMode) { mutableStateOf(savedMode) }
    var selectedUsbDeviceId by rememberSaveable { mutableIntStateOf(-1) }
    var command by rememberSaveable { mutableStateOf("") }
    var pendingConfirmCommands by remember { mutableStateOf<List<String>?>(null) }
    val logState = rememberLazyListState()
    val isBusy = state.phase == AdbRepository.Phase.CONNECTING
    val isConnected = state.phase == AdbRepository.Phase.CONNECTED
    val scriptActive = scriptRun.active
    val shellEnabled = isConnected && !scriptActive

    val readErrorToast = stringResource(R.string.adb_script_read_error)
    val emptyToast = stringResource(R.string.adb_script_empty)
    val doneAllToast = stringResource(R.string.adb_script_done_all)

    fun startParsedScript(commands: List<String>) {
        when (val gate = AdbShellScriptParser.evaluateExecutableCount(commands.size)) {
            is AdbShellScriptParser.CountGate.Rejected -> {
                Toast.makeText(
                    context,
                    resources.getString(
                        R.string.adb_script_too_many,
                        gate.count,
                        AdbShellScriptParser.HARD_CAP,
                    ),
                    Toast.LENGTH_LONG,
                ).show()
            }
            is AdbShellScriptParser.CountGate.NeedsConfirm -> {
                pendingConfirmCommands = commands
            }
            is AdbShellScriptParser.CountGate.Ok -> {
                if (commands.isEmpty()) {
                    Toast.makeText(context, emptyToast, Toast.LENGTH_SHORT).show()
                } else {
                    AdbRepository.startScript(commands)
                }
            }
        }
    }

    fun handlePickedScriptUri(uri: Uri) {
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader(Charsets.UTF_8).readText()
                    }
                }.getOrNull()
            }
            if (text == null) {
                Toast.makeText(context, readErrorToast, Toast.LENGTH_LONG).show()
                return@launch
            }
            val commands = AdbShellScriptParser.parseExecutableCommands(text)
            startParsedScript(commands)
        }
    }

    val scriptFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        handlePickedScriptUri(uri)
    }

    LaunchedEffect(Unit) {
        AdbRepository.initialize(context)
        AdbRepository.refreshUsbDevices()
        settingsViewModel.refreshHuAdbState()
    }

    LaunchedEffect(huAdbError) {
        val message = huAdbError ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        settingsViewModel.consumeHuAdbError()
    }

    LaunchedEffect(usbCandidates) {
        if (usbCandidates.none { it.deviceId == selectedUsbDeviceId }) {
            selectedUsbDeviceId = usbCandidates.firstOrNull()?.deviceId ?: -1
        }
    }

    LaunchedEffect(logLines.size) {
        if (logLines.isNotEmpty()) logState.animateScrollToItem(logLines.lastIndex)
    }

    LaunchedEffect(scriptRun.outcome, scriptRun.active) {
        val outcome = scriptRun.outcome ?: return@LaunchedEffect
        if (scriptRun.active) return@LaunchedEffect
        val message = when (outcome) {
            AdbRepository.ScriptOutcome.COMPLETED -> doneAllToast
            AdbRepository.ScriptOutcome.STOPPED -> resources.getString(
                R.string.adb_script_done_stopped,
                scriptRun.completedCount,
                scriptRun.failedCount,
                scriptRun.total,
            )
            AdbRepository.ScriptOutcome.ABORTED -> resources.getString(
                R.string.adb_script_done_aborted,
                scriptRun.completedCount,
                scriptRun.failedCount,
                scriptRun.total,
            )
            AdbRepository.ScriptOutcome.CANCELLED -> resources.getString(
                R.string.adb_script_done_cancelled,
                scriptRun.completedCount,
                scriptRun.failedCount,
                scriptRun.total,
            )
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        AdbRepository.acknowledgeScriptFinished()
    }

    pendingConfirmCommands?.let { commands ->
        AlertDialog(
            onDismissRequest = { pendingConfirmCommands = null },
            title = { AppAlertDialogTitle(stringResource(R.string.adb_script_confirm_title)) },
            text = {
                AppAlertDialogText(
                    stringResource(R.string.adb_script_confirm_message, commands.size),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingConfirmCommands = null
                        AdbRepository.startScript(commands)
                    },
                ) {
                    AppAlertDialogButtonLabel(stringResource(R.string.adb_script_confirm_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingConfirmCommands = null }) {
                    AppAlertDialogButtonLabel(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (scriptRun.awaitingFailureDecision) {
        AlertDialog(
            onDismissRequest = { /* require explicit continue/abort */ },
            title = { AppAlertDialogTitle(stringResource(R.string.adb_script_failure_title)) },
            text = {
                AppAlertDialogText(
                    stringResource(
                        R.string.adb_script_failure_message,
                        scriptRun.currentIndex,
                        scriptRun.total,
                        scriptRun.currentCommand,
                        scriptRun.failureDetail.orEmpty(),
                    ),
                )
            },
            confirmButton = {
                Button(onClick = AdbRepository::continueScriptAfterFailure) {
                    AppAlertDialogButtonLabel(stringResource(R.string.adb_script_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = AdbRepository::abortScriptAfterFailure) {
                    AppAlertDialogButtonLabel(stringResource(R.string.adb_script_abort))
                }
            },
        )
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
                enabled = !isConnected && !isBusy && !scriptActive,
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
                enabled = !isConnected && !isBusy && !scriptActive,
                onClick = {
                    mode = "usb"
                    settingsViewModel.saveAdbModeSetting(mode)
                    AdbRepository.refreshUsbDevices()
                },
                modifier = Modifier.weight(1f),
            )
        }

        if (mode == "tcp") {
            SettingSwitch(
                huAdbState.tcpEnabled,
                { enabled -> settingsViewModel.setHuAdbTcpEnabled(enabled) },
                stringResource(R.string.settings_adb_tcp_title),
                stringResource(R.string.settings_adb_tcp_desc),
                !huAdbState.readFailed,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text(stringResource(R.string.adb_host)) },
                    singleLine = true,
                    enabled = !isConnected && !isBusy && !scriptActive,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { value -> port = value.filter(Char::isDigit).take(5) },
                    label = { Text(stringResource(R.string.adb_port)) },
                    singleLine = true,
                    enabled = !isConnected && !isBusy && !scriptActive,
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
                                .clickable(enabled = !isConnected && !isBusy && !scriptActive) {
                                    selectedUsbDeviceId = candidate.deviceId
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = candidate.deviceId == selectedUsbDeviceId,
                                onClick = { selectedUsbDeviceId = candidate.deviceId },
                                enabled = !isConnected && !isBusy && !scriptActive,
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
                enabled = shellEnabled,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (shellEnabled && command.isNotBlank()) {
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
                enabled = shellEnabled && command.isNotBlank(),
            ) {
                Text(
                    text = stringResource(R.string.adb_execute),
                    style = MaterialTheme.typography.tboxButton,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = {
                    scriptFileLauncher.launch(
                        arrayOf("text/plain", "text/*", "*/*"),
                    )
                },
                enabled = shellEnabled,
            ) {
                Text(
                    text = stringResource(R.string.adb_run_from_file),
                    style = MaterialTheme.typography.tboxButton,
                )
            }
            if (scriptActive) {
                Text(
                    text = stringResource(
                        R.string.adb_script_progress,
                        scriptRun.currentIndex.coerceAtLeast(0),
                        scriptRun.total,
                    ),
                    style = MaterialTheme.typography.tboxBody,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = AdbRepository::stopScript,
                ) {
                    Text(
                        text = stringResource(R.string.adb_script_stop),
                        style = MaterialTheme.typography.tboxButton,
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.adb_run_from_file_hint),
                    style = MaterialTheme.typography.tboxCaption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
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
                enabled = logLines.isNotEmpty() && !scriptActive,
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
