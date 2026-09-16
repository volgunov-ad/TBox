package vad.dashing.tbox.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.obd.Elm327Protocol
import vad.dashing.tbox.obd.ObdDtcExport
import vad.dashing.tbox.obd.ObdInterestAggregator
import vad.dashing.tbox.obd.ObdPid
import vad.dashing.tbox.obd.ObdRepository
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxButton
import vad.dashing.tbox.ui.theme.tboxCaption
import vad.dashing.tbox.ui.theme.tboxTitle
import vad.dashing.tbox.valueToString
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun Elm327Tab(
    settingsViewModel: SettingsViewModel,
) {
    Elm327TabContent(settingsViewModel = settingsViewModel)
}

@Composable
fun Elm327TabContent(
    settingsViewModel: SettingsViewModel,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val enabled by settingsViewModel.elm327Enabled.collectAsStateWithLifecycle()
    val selectedAddress by settingsViewModel.elm327DeviceAddress.collectAsStateWithLifecycle()
    val savedPin by settingsViewModel.elm327PairingPin.collectAsStateWithLifecycle()
    val supportedPidsRaw by settingsViewModel.elm327SupportedPids.collectAsStateWithLifecycle()
    val discoveryAtMs by settingsViewModel.elm327DiscoveryAtMs.collectAsStateWithLifecycle()
    val connected by ObdRepository.connected.collectAsStateWithLifecycle()
    val status by ObdRepository.statusText.collectAsStateWithLifecycle()
    val lastError by ObdRepository.lastError.collectAsStateWithLifecycle()
    val adapterVoltage by ObdRepository.adapterVoltage.collectAsStateWithLifecycle()
    val adapterVersion by ObdRepository.adapterVersion.collectAsStateWithLifecycle()
    val protocolDescription by ObdRepository.protocolDescription.collectAsStateWithLifecycle()
    val protocolNumber by ObdRepository.protocolNumber.collectAsStateWithLifecycle()
    val lastResponseMs by ObdRepository.lastResponseMs.collectAsStateWithLifecycle()
    val busErrors by ObdRepository.busErrors.collectAsStateWithLifecycle()
    val dtcCodes by ObdRepository.dtcCodes.collectAsStateWithLifecycle()
    val pendingDtcCodes by ObdRepository.pendingDtcCodes.collectAsStateWithLifecycle()
    val dtcReading by ObdRepository.dtcReading.collectAsStateWithLifecycle()
    val dtcClearing by ObdRepository.dtcClearing.collectAsStateWithLifecycle()
    val dtcLastReadAtMs by ObdRepository.dtcLastReadAtMs.collectAsStateWithLifecycle()
    val pendingDtcLastReadAtMs by ObdRepository.pendingDtcLastReadAtMs.collectAsStateWithLifecycle()
    val dtcError by ObdRepository.dtcError.collectAsStateWithLifecycle()
    val dtcReadEverSucceeded by ObdRepository.dtcReadEverSucceeded.collectAsStateWithLifecycle()
    val pendingDtcReadEverSucceeded by ObdRepository.pendingDtcReadEverSucceeded.collectAsStateWithLifecycle()
    val discoveryRunning by ObdRepository.discoveryRunning.collectAsStateWithLifecycle()
    val discoveryError by ObdRepository.discoveryError.collectAsStateWithLifecycle()
    val freezeFrameReading by ObdRepository.freezeFrameReading.collectAsStateWithLifecycle()
    val freezeFrameError by ObdRepository.freezeFrameError.collectAsStateWithLifecycle()
    val freezeFrameDtc by ObdRepository.freezeFrameDtc.collectAsStateWithLifecycle()
    val freezeFrameValues by ObdRepository.freezeFrameValues.collectAsStateWithLifecycle()
    val freezeFrameLastReadAtMs by ObdRepository.freezeFrameLastReadAtMs.collectAsStateWithLifecycle()
    val freezeFrameReadEverSucceeded by ObdRepository.freezeFrameReadEverSucceeded.collectAsStateWithLifecycle()

    val supportedMode01Pids = remember(supportedPidsRaw) {
        Elm327Protocol.decodeSupportedPids(supportedPidsRaw)
    }
    val discoveryDone = discoveryAtMs > 0L
    var showClearDtcConfirm by remember { mutableStateOf(false) }
    var bondedRefreshToken by remember { mutableIntStateOf(0) }
    val bondedDevices = remember(bondedRefreshToken) {
        loadBondedBluetoothDevices(context)
    }
    val foundDevices = remember { mutableStateListOf<BtDeviceEntry>() }
    var scanning by remember { mutableStateOf(false) }
    var manualMac by remember { mutableStateOf("") }
    var pinInput by remember(savedPin) { mutableStateOf(savedPin) }

    val discoveryReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothAdapter.ACTION_DISCOVERY_STARTED -> scanning = true
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> scanning = false
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = intent.getBluetoothDeviceExtra() ?: return
                        val address = device.address.orEmpty()
                        if (address.isBlank()) return
                        val name = runCatching { device.name }.getOrNull().orEmpty()
                        if (foundDevices.none { it.address.equals(address, ignoreCase = true) }) {
                            foundDevices += BtDeviceEntry(name = name, address = address)
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_FOUND)
        }
        ContextCompat.registerReceiver(
            context,
            discoveryReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(discoveryReceiver) }
    }

    DisposableEffect(Unit) {
        ObdInterestAggregator.setSourcePids("elm327_tab", setOf(ObdPid.ADAPTER_VOLTAGE.id))
        onDispose { ObdInterestAggregator.clearSource("elm327_tab") }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        bondedRefreshToken++
    }

    fun missingPermissions(permissions: Array<String>): Array<String> =
        permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

    fun ensureBtPermissionAndRefresh() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val missing = missingPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
            if (missing.isNotEmpty()) {
                permissionLauncher.launch(missing)
                return
            }
        }
        bondedRefreshToken++
    }

    fun startDiscovery() {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        if (!adapter.isEnabled) return
        foundDevices.clear()
        scanning = adapter.startDiscovery()
    }

    fun ensureScanPermissionsAndScan() {
        val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        val missing = missingPermissions(needed)
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing)
            return
        }
        startDiscovery()
    }

    val statusLabel = when {
        !enabled -> stringResource(R.string.elm327_status_stopped)
        selectedAddress.isBlank() -> stringResource(R.string.elm327_status_no_device)
        connected -> stringResource(R.string.elm327_status_connected)
        status == "connecting" || status == "starting" || status == "pairing" ->
            stringResource(R.string.elm327_status_connecting)
        status == "reconnecting" -> stringResource(R.string.elm327_status_reconnecting)
        else -> stringResource(R.string.elm327_status_disconnected)
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SettingSwitch(
            isChecked = enabled,
            onCheckedChange = { settingsViewModel.saveElm327EnabledSetting(it) },
            text = stringResource(R.string.elm327_enabled_title),
            description = stringResource(R.string.elm327_enabled_desc),
            enabled = true,
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Text(
            text = stringResource(R.string.elm327_status_title),
            style = MaterialTheme.typography.tboxTitle,
            color = MaterialTheme.colorScheme.onSurface,
        )
        StatusRow(stringResource(R.string.elm327_status_title), statusLabel)
        StatusRow(
            stringResource(R.string.elm327_adapter_voltage),
            adapterVoltage?.let { "${valueToString(it, accuracy = 1)} V" } ?: "—",
        )
        if (!adapterVersion.isNullOrBlank()) {
            StatusRow(stringResource(R.string.elm327_adapter_version), adapterVersion!!)
        }
        StatusRow(
            stringResource(R.string.elm327_protocol_desc),
            protocolDescription?.ifBlank { "—" } ?: "—",
        )
        StatusRow(
            stringResource(R.string.elm327_protocol_num),
            protocolNumber?.ifBlank { "—" } ?: "—",
        )
        StatusRow(
            stringResource(R.string.elm327_last_response_ms),
            lastResponseMs?.let { stringResource(R.string.elm327_ms_fmt, it) } ?: "—",
        )
        StatusRow(
            stringResource(R.string.elm327_bus_errors),
            busErrors.toString(),
        )
        StatusRow(
            stringResource(R.string.elm327_last_error),
            lastError?.ifBlank { "—" } ?: "—",
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Text(
            text = stringResource(R.string.elm327_device_title),
            style = MaterialTheme.typography.tboxTitle,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { ensureBtPermissionAndRefresh() }) {
                Text(stringResource(R.string.elm327_device_refresh))
            }
            Button(
                onClick = { ensureScanPermissionsAndScan() },
                enabled = !scanning,
            ) {
                Text(
                    stringResource(
                        if (scanning) {
                            R.string.elm327_device_scanning
                        } else {
                            R.string.elm327_device_scan
                        },
                    ),
                )
            }
        }
        val knownDevices = (bondedDevices + foundDevices)
            .distinctBy { it.address.uppercase(Locale.US) }
            .sortedBy { it.name.ifBlank { it.address }.lowercase(Locale.US) }
        if (knownDevices.isEmpty()) {
            Text(
                text = stringResource(R.string.elm327_device_none),
                style = MaterialTheme.typography.tboxBody,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.elm327_bt_permission_needed),
                style = MaterialTheme.typography.tboxCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            knownDevices.forEach { device ->
                val selected = device.address.equals(selectedAddress, ignoreCase = true)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            settingsViewModel.saveElm327DeviceAddressSetting(device.address)
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = {
                            settingsViewModel.saveElm327DeviceAddressSetting(device.address)
                        },
                    )
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(
                            text = device.name.ifBlank { device.address },
                            style = MaterialTheme.typography.tboxBody,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = device.address,
                            style = MaterialTheme.typography.tboxCaption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        val macRegex = remember { Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$") }
        val manualMacValid = macRegex.matches(manualMac)
        OutlinedTextField(
            value = manualMac,
            onValueChange = { value ->
                manualMac = value.filter { it.isLetterOrDigit() || it == ':' }.take(17)
            },
            singleLine = true,
            label = { Text(stringResource(R.string.elm327_device_manual_mac)) },
            isError = manualMac.isNotEmpty() && !manualMacValid,
            modifier = Modifier.fillMaxWidth(),
        )
        if (manualMac.isNotEmpty() && !manualMacValid) {
            Text(
                text = stringResource(R.string.elm327_device_manual_hint),
                style = MaterialTheme.typography.tboxCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(
            onClick = {
                settingsViewModel.saveElm327DeviceAddressSetting(manualMac.uppercase(Locale.US))
            },
            enabled = manualMacValid,
        ) {
            Text(stringResource(R.string.elm327_device_use))
        }

        OutlinedTextField(
            value = pinInput,
            onValueChange = { value -> pinInput = value.filter { it.isDigit() }.take(16) },
            singleLine = true,
            label = { Text(stringResource(R.string.elm327_pairing_pin_label)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.elm327_pairing_pin_hint),
            style = MaterialTheme.typography.tboxCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = { settingsViewModel.saveElm327PairingPinSetting(pinInput) },
            enabled = pinInput.trim() != savedPin,
        ) {
            Text(stringResource(R.string.elm327_pairing_pin_save))
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Text(
            text = stringResource(R.string.elm327_discovery_section_title),
            style = MaterialTheme.typography.tboxTitle,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.elm327_discovery_section_desc),
            style = MaterialTheme.typography.tboxCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { ObdInterestAggregator.requestPidDiscovery() },
                enabled = enabled && connected && !discoveryRunning && !dtcReading && !dtcClearing && !freezeFrameReading,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    if (discoveryRunning) {
                        stringResource(R.string.elm327_discovery_running)
                    } else {
                        stringResource(R.string.elm327_discovery_button)
                    },
                )
            }
            Button(
                onClick = {
                    ObdRepository.setDiscoveryError(null)
                    settingsViewModel.clearElm327PidDiscoveryResult()
                },
                enabled = discoveryDone || supportedMode01Pids.isNotEmpty() || discoveryError != null,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.elm327_discovery_reset))
            }
        }
        if (discoveryError != null) {
            Text(
                text = stringResource(R.string.elm327_discovery_error, discoveryError!!),
                style = MaterialTheme.typography.tboxBody,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (discoveryDone) {
            Text(
                text = stringResource(
                    R.string.elm327_discovery_last,
                    timeFormat.format(Date(discoveryAtMs)),
                    supportedMode01Pids.size,
                ),
                style = MaterialTheme.typography.tboxCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = stringResource(R.string.elm327_discovery_not_run),
                style = MaterialTheme.typography.tboxCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ObdPid.entries.forEach { pid ->
            val modePid = pid.mode01Pid
            val statusText = when {
                modePid == null -> stringResource(R.string.elm327_discovery_status_adapter)
                !discoveryDone -> stringResource(R.string.elm327_discovery_status_unknown)
                modePid in supportedMode01Pids ->
                    stringResource(R.string.elm327_discovery_status_supported)
                else -> stringResource(R.string.elm327_discovery_status_unsupported)
            }
            val label = stringResource(pid.labelRes)
            val pidHex = modePid?.let { "%02X".format(it) } ?: "ATRV"
            StatusRow("$label ($pidHex)", statusText)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Text(
            text = stringResource(R.string.elm327_dtc_section_title),
            style = MaterialTheme.typography.tboxTitle,
            color = MaterialTheme.colorScheme.onSurface,
        )
        val dtcBusy = dtcReading || dtcClearing || discoveryRunning || freezeFrameReading
        Button(
            onClick = { ObdInterestAggregator.requestStoredDtcs() },
            enabled = enabled && connected && !dtcBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (dtcReading) {
                    stringResource(R.string.elm327_dtc_reading)
                } else {
                    stringResource(R.string.elm327_dtc_read_button)
                },
            )
        }
        Button(
            onClick = { ObdInterestAggregator.requestPendingDtcs() },
            enabled = enabled && connected && !dtcBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.elm327_dtc_read_pending_button))
        }
        Button(
            onClick = { ObdInterestAggregator.requestFreezeFrame() },
            enabled = enabled && connected && !dtcBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (freezeFrameReading) {
                    stringResource(R.string.elm327_ff_reading)
                } else {
                    stringResource(R.string.elm327_ff_read_button)
                },
            )
        }
        Button(
            onClick = { showClearDtcConfirm = true },
            enabled = enabled && connected && !dtcBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (dtcClearing) {
                    stringResource(R.string.elm327_dtc_clearing)
                } else {
                    stringResource(R.string.elm327_dtc_clear_button)
                },
            )
        }
        val canExportDtc =
            dtcReadEverSucceeded || pendingDtcReadEverSucceeded || freezeFrameReadEverSucceeded
        Button(
            onClick = {
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { ObdDtcExport.writeToDownloads() }
                    }
                    if (result.isSuccess) {
                        Toast.makeText(
                            context,
                            context.getString(
                                R.string.toast_saved_to,
                                result.getOrNull()?.absolutePath.orEmpty(),
                            ),
                            Toast.LENGTH_LONG,
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(
                                R.string.elm327_dtc_export_error,
                                result.exceptionOrNull()?.message.orEmpty(),
                            ),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            },
            enabled = canExportDtc && !dtcBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.elm327_dtc_export_button))
        }
        if (dtcError != null) {
            Text(
                text = stringResource(R.string.elm327_dtc_error, dtcError!!),
                style = MaterialTheme.typography.tboxBody,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text(
            text = stringResource(R.string.elm327_dtc_stored_title),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (dtcLastReadAtMs > 0L) {
            Text(
                text = stringResource(
                    R.string.elm327_dtc_last_read,
                    timeFormat.format(Date(dtcLastReadAtMs)),
                ),
                style = MaterialTheme.typography.tboxCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            dtcReading -> {
                Text(
                    text = stringResource(R.string.elm327_dtc_reading),
                    style = MaterialTheme.typography.tboxBody,
                )
            }
            !dtcReadEverSucceeded && dtcCodes.isEmpty() -> {
                Text(
                    text = stringResource(R.string.elm327_dtc_not_read_yet),
                    style = MaterialTheme.typography.tboxBody,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            dtcReadEverSucceeded && dtcCodes.isEmpty() -> {
                Text(
                    text = stringResource(R.string.elm327_dtc_none),
                    style = MaterialTheme.typography.tboxBody,
                )
            }
            else -> {
                dtcCodes.forEach { dtc ->
                    Text(
                        text = dtc.code,
                        style = MaterialTheme.typography.tboxTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.elm327_dtc_pending_title),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (pendingDtcLastReadAtMs > 0L) {
            Text(
                text = stringResource(
                    R.string.elm327_dtc_last_read,
                    timeFormat.format(Date(pendingDtcLastReadAtMs)),
                ),
                style = MaterialTheme.typography.tboxCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            !pendingDtcReadEverSucceeded && pendingDtcCodes.isEmpty() -> {
                Text(
                    text = stringResource(R.string.elm327_dtc_pending_not_read_yet),
                    style = MaterialTheme.typography.tboxBody,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            pendingDtcReadEverSucceeded && pendingDtcCodes.isEmpty() -> {
                Text(
                    text = stringResource(R.string.elm327_dtc_pending_none),
                    style = MaterialTheme.typography.tboxBody,
                )
            }
            else -> {
                pendingDtcCodes.forEach { dtc ->
                    Text(
                        text = dtc.code,
                        style = MaterialTheme.typography.tboxTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }

        Text(
            text = stringResource(R.string.elm327_ff_section_title),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = stringResource(R.string.elm327_ff_section_desc),
            style = MaterialTheme.typography.tboxCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (freezeFrameError != null) {
            Text(
                text = stringResource(R.string.elm327_ff_error, freezeFrameError!!),
                style = MaterialTheme.typography.tboxBody,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (freezeFrameLastReadAtMs > 0L) {
            Text(
                text = stringResource(
                    R.string.elm327_dtc_last_read,
                    timeFormat.format(Date(freezeFrameLastReadAtMs)),
                ),
                style = MaterialTheme.typography.tboxCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            freezeFrameReading -> {
                Text(
                    text = stringResource(R.string.elm327_ff_reading),
                    style = MaterialTheme.typography.tboxBody,
                )
            }
            !freezeFrameReadEverSucceeded -> {
                Text(
                    text = stringResource(R.string.elm327_ff_not_read_yet),
                    style = MaterialTheme.typography.tboxBody,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            freezeFrameDtc == null && freezeFrameValues.isEmpty() -> {
                Text(
                    text = stringResource(R.string.elm327_ff_empty),
                    style = MaterialTheme.typography.tboxBody,
                )
            }
            else -> {
                StatusRow(
                    stringResource(R.string.elm327_ff_dtc_label),
                    freezeFrameDtc?.code ?: "—",
                )
                ObdPid.entries.forEach { pid ->
                    val raw = freezeFrameValues[pid.id] ?: return@forEach
                    val unit = pid.unitRes?.let { " " + stringResource(it) }.orEmpty()
                    StatusRow(
                        stringResource(pid.labelRes),
                        valueToString(raw, accuracy = pid.defaultAccuracy) + unit,
                    )
                }
            }
        }
    }

    if (showClearDtcConfirm) {
        AlertDialog(
            onDismissRequest = { showClearDtcConfirm = false },
            title = { AppAlertDialogTitle(stringResource(R.string.elm327_dtc_clear_confirm_title)) },
            text = { AppAlertDialogText(stringResource(R.string.elm327_dtc_clear_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDtcConfirm = false
                        ObdInterestAggregator.requestClearDtcs()
                    },
                ) {
                    AppAlertDialogButtonLabel(stringResource(R.string.elm327_dtc_clear_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDtcConfirm = false }) {
                    AppAlertDialogButtonLabel(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

private data class BtDeviceEntry(
    val name: String,
    val address: String,
)

@Suppress("DEPRECATION")
private fun Intent.getBluetoothDeviceExtra(): BluetoothDevice? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
    } else {
        getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
    }

@SuppressLint("MissingPermission")
private fun loadBondedBluetoothDevices(context: Context): List<BtDeviceEntry> {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return emptyList()
    }
    val adapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
    val bonded: Set<BluetoothDevice> = runCatching { adapter.bondedDevices }.getOrNull()
        ?: return emptyList()
    return bonded
        .map { device ->
            BtDeviceEntry(
                name = runCatching { device.name }.getOrNull().orEmpty(),
                address = device.address.orEmpty(),
            )
        }
        .filter { it.address.isNotBlank() }
        .sortedBy { it.name.ifBlank { it.address }.lowercase(Locale.US) }
}
