package vad.dashing.tbox.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.obd.ObdInterestAggregator
import vad.dashing.tbox.obd.ObdRepository
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxCaption
import vad.dashing.tbox.ui.theme.tboxTitle
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
    val enabled by settingsViewModel.elm327Enabled.collectAsStateWithLifecycle()
    val selectedAddress by settingsViewModel.elm327DeviceAddress.collectAsStateWithLifecycle()
    val connected by ObdRepository.connected.collectAsStateWithLifecycle()
    val status by ObdRepository.statusText.collectAsStateWithLifecycle()
    val lastError by ObdRepository.lastError.collectAsStateWithLifecycle()
    val adapterVoltage by ObdRepository.adapterVoltage.collectAsStateWithLifecycle()
    val dtcCodes by ObdRepository.dtcCodes.collectAsStateWithLifecycle()
    val dtcReading by ObdRepository.dtcReading.collectAsStateWithLifecycle()
    val dtcLastReadAtMs by ObdRepository.dtcLastReadAtMs.collectAsStateWithLifecycle()
    val dtcError by ObdRepository.dtcError.collectAsStateWithLifecycle()
    val dtcReadEverSucceeded by ObdRepository.dtcReadEverSucceeded.collectAsStateWithLifecycle()

    var bondedRefreshToken by remember { mutableIntStateOf(0) }
    val bondedDevices = remember(bondedRefreshToken) {
        loadBondedBluetoothDevices(context)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        bondedRefreshToken++
    }

    fun ensureBtPermissionAndRefresh() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                return
            }
        }
        bondedRefreshToken++
    }

    val statusLabel = when {
        !enabled -> stringResource(R.string.elm327_status_stopped)
        selectedAddress.isBlank() -> stringResource(R.string.elm327_status_no_device)
        connected -> stringResource(R.string.elm327_status_connected)
        status == "connecting" || status == "starting" ->
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
            adapterVoltage?.let { String.format(Locale.US, "%.1f V", it) } ?: "—",
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
        Button(onClick = { ensureBtPermissionAndRefresh() }) {
            Text(stringResource(R.string.elm327_device_refresh))
        }
        if (bondedDevices.isEmpty()) {
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
            bondedDevices.forEach { device ->
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

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Text(
            text = stringResource(R.string.elm327_dtc_section_title),
            style = MaterialTheme.typography.tboxTitle,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Button(
            onClick = { ObdInterestAggregator.requestStoredDtcs() },
            enabled = enabled && connected && !dtcReading,
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
        if (dtcError != null) {
            Text(
                text = stringResource(R.string.elm327_dtc_error, dtcError!!),
                style = MaterialTheme.typography.tboxBody,
                color = MaterialTheme.colorScheme.error,
            )
        }
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
    }
}

private data class BondedBtDevice(
    val name: String,
    val address: String,
)

@SuppressLint("MissingPermission")
private fun loadBondedBluetoothDevices(context: android.content.Context): List<BondedBtDevice> {
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
            BondedBtDevice(
                name = runCatching { device.name }.getOrNull().orEmpty(),
                address = device.address.orEmpty(),
            )
        }
        .filter { it.address.isNotBlank() }
        .sortedBy { it.name.ifBlank { it.address }.lowercase(Locale.US) }
}
