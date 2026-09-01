package vad.dashing.tbox.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vad.dashing.tbox.BackgroundService
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.esp.CompanionProtocolLogRecorder
import vad.dashing.tbox.esp.EspCompanionProtocol
import vad.dashing.tbox.esp.EspCompanionRepository
import vad.dashing.tbox.esp.LocationSource
import vad.dashing.tbox.esp.Um980Commands
import vad.dashing.tbox.location.LocationIncomingBitRate
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxButton
import vad.dashing.tbox.ui.theme.tboxCaption
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EspCompanionTabContent(
    settingsViewModel: SettingsViewModel,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val yesLabel = stringResource(R.string.value_yes)
    val noLabel = stringResource(R.string.value_no)
    val companionEnabled by settingsViewModel.espCompanionEnabled.collectAsStateWithLifecycle()
    val connected by EspCompanionRepository.connected.collectAsStateWithLifecycle()
    val info by EspCompanionRepository.deviceInfo.collectAsStateWithLifecycle()
    val lastError by EspCompanionRepository.lastError.collectAsStateWithLifecycle()
    val lastMsgAt by EspCompanionRepository.lastMessageAtMs.collectAsStateWithLifecycle()
    val lastGps by EspCompanionRepository.lastGpsAtMs.collectAsStateWithLifecycle()
    val gpioMask by EspCompanionRepository.gpioMask.collectAsStateWithLifecycle()
    val relayMask by EspCompanionRepository.relayMask.collectAsStateWithLifecycle()
    val loc by EspCompanionRepository.locValues.collectAsStateWithLifecycle()
    val lastMag by EspCompanionRepository.lastMag.collectAsStateWithLifecycle()
    val lastMagAt by EspCompanionRepository.lastMagAtMs.collectAsStateWithLifecycle()
    val snapshot by EspCompanionRepository.um980ConfigSnapshot.collectAsStateWithLifecycle()
    val otaBusy by EspCompanionRepository.otaBusy.collectAsStateWithLifecycle()
    val otaProgress by EspCompanionRepository.otaProgress.collectAsStateWithLifecycle()
    val otaError by EspCompanionRepository.otaError.collectAsStateWithLifecycle()
    val um980ConfigBusy by EspCompanionRepository.um980ConfigBusy.collectAsStateWithLifecycle()

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            nowMs = System.currentTimeMillis()
        }
    }
    val um980Online = lastGps > 0L && nowMs - lastGps <= EspCompanionProtocol.UM980_ONLINE_TIMEOUT_MS
    val controlsEnabled = companionEnabled && connected && !otaBusy && !um980ConfigBusy

    // One CONFIG/MODE/MASK/VERSION read per visit while UM980 is online.
    var autoSnapshotRefreshDone by remember { mutableStateOf(false) }
    LaunchedEffect(companionEnabled, connected, um980Online, otaBusy, um980ConfigBusy) {
        if (autoSnapshotRefreshDone) return@LaunchedEffect
        if (!companionEnabled || !connected || !um980Online || otaBusy || um980ConfigBusy) {
            return@LaunchedEffect
        }
        autoSnapshotRefreshDone = true
        context.sendUm980Cmds(Um980Commands.refreshSnapshotCommands())
    }

    var showUm980Settings by remember { mutableStateOf(false) }
    var showCanConsole by remember { mutableStateOf(false) }
    var showRebootConfirm by remember { mutableStateOf(false) }
    var pendingOtaFile by remember { mutableStateOf<File?>(null) }
    var pendingOtaDisplayName by remember { mutableStateOf("") }

    val otaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val prepared = withContext(Dispatchers.IO) {
                prepareOtaCacheFile(context, uri)
            }
            prepared.fold(
                onSuccess = { (file, name) ->
                    pendingOtaFile = file
                    pendingOtaDisplayName = name
                },
                onFailure = { e ->
                    Toast.makeText(
                        context,
                        otaErrorMessage(context, e.message),
                        Toast.LENGTH_LONG,
                    ).show()
                },
            )
        }
    }

    LaunchedEffect(otaBusy, otaError, otaProgress) {
        if (!otaBusy && otaProgress >= 100 && otaError == null) {
            Toast.makeText(context, context.getString(R.string.esp_ota_ok), Toast.LENGTH_LONG).show()
        }
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val utcText = loc.utcTime?.formatDateTime().orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        SettingSwitch(
            isChecked = companionEnabled,
            onCheckedChange = { enabled ->
                settingsViewModel.saveEspCompanionEnabledSetting(enabled)
            },
            text = stringResource(R.string.esp_connect_enabled_title),
            description = stringResource(R.string.esp_connect_enabled_desc),
            enabled = true,
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SettingsTitle(stringResource(R.string.esp_status_title))
        StatusRow(
            stringResource(R.string.esp_usb_status),
            if (connected) stringResource(R.string.esp_connected) else stringResource(R.string.esp_disconnected),
        )
        StatusRow(stringResource(R.string.esp_firmware), info.firmwareVersion.ifBlank { "—" })
        StatusRow(
            stringResource(R.string.esp_um980_version),
            snapshot.um980Version?.ifBlank { "—" } ?: "—",
        )
        StatusRow(stringResource(R.string.esp_last_error), lastError?.ifBlank { "—" } ?: "—")
        StatusRow(
            stringResource(R.string.esp_last_message_at),
            if (lastMsgAt > 0L) timeFormat.format(Date(lastMsgAt)) else "—",
        )
        val companionIncomingBps = remember(nowMs) {
            LocationIncomingBitRate.formatBitsPerSec(
                LocationIncomingBitRate.bitsPerSec(LocationSource.ESP32),
            )
        }
        StatusRow(
            stringResource(R.string.location_incoming_bitrate),
            companionIncomingBps,
        )
        StatusRow(
            stringResource(R.string.esp_can_status),
            if (info.can) yesLabel else noLabel,
        )
        if (info.can) {
            StatusRow(
                stringResource(R.string.esp_can_backend),
                info.canBackend.ifBlank { "mcp2515" },
            )
        }
        val gpioBits = (if (info.gpioInCount > 0) info.gpioInCount else 4).coerceIn(1, 16)
        val relayBits = (if (info.relayCount > 0) info.relayCount else 2).coerceIn(1, 8)
        StatusRow(
            stringResource(R.string.esp_gpio_inputs),
            Integer.toBinaryString(gpioMask).padStart(gpioBits, '0'),
        )
        StatusRow(
            stringResource(R.string.esp_relays),
            Integer.toBinaryString(relayMask).padStart(relayBits, '0'),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (ch in 0 until relayBits) {
                val on = (relayMask and (1 shl ch)) != 0
                OutlinedButton(
                    onClick = rememberWrappedOnClick {
                        context.startService(
                            Intent(context, BackgroundService::class.java).apply {
                                action = BackgroundService.ACTION_ESP_RELAY_TOGGLE
                                putExtra(BackgroundService.EXTRA_ESP_RELAY_CHANNEL, ch)
                            },
                        )
                    },
                    enabled = controlsEnabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(R.string.esp_relay_n, ch) + if (on) " ●" else " ○",
                        style = MaterialTheme.typography.tboxCaption,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        StatusRow(
            stringResource(R.string.esp_um980_online),
            if (um980Online) yesLabel else noLabel,
        )
        val baudOptions = EspCompanionProtocol.UM980_BAUD_OPTIONS.map { BaudOption(it, it.toString()) }
        val selectedBaud = baudOptions.firstOrNull { it.baud == info.um980Baud }
            ?: baudOptions.first { it.baud == 115200 }
        SettingDropdownGeneric(
            selectedValue = selectedBaud,
            onValueChange = { opt ->
                context.startService(
                    Intent(context, BackgroundService::class.java).apply {
                        action = BackgroundService.ACTION_ESP_UM980_BAUD
                        putExtra(BackgroundService.EXTRA_ESP_UM980_BAUD, opt.baud)
                    },
                )
            },
            text = stringResource(R.string.esp_um980_baud),
            description = stringResource(R.string.esp_um980_baud_desc),
            enabled = controlsEnabled,
            options = baudOptions,
            selectorWidth = 300.dp,
        )
        val magChipOptions = listOf(
            MagChipOption(
                EspCompanionProtocol.MAG_CHIP_RM3100,
                stringResource(R.string.esp_mag_chip_rm3100),
            ),
            MagChipOption(
                EspCompanionProtocol.MAG_CHIP_MMC5983,
                stringResource(R.string.esp_mag_chip_mmc5983),
            ),
        )
        val selectedMagChip = magChipOptions.firstOrNull {
            it.id.equals(info.magChip, ignoreCase = true)
        } ?: magChipOptions.first()
        SettingDropdownGeneric(
            selectedValue = selectedMagChip,
            onValueChange = { opt ->
                context.startService(
                    Intent(context, BackgroundService::class.java).apply {
                        action = BackgroundService.ACTION_ESP_MAG_CHIP
                        putExtra(BackgroundService.EXTRA_ESP_MAG_CHIP, opt.id)
                    },
                )
            },
            text = stringResource(R.string.esp_mag_chip),
            description = if (info.magSupported) {
                stringResource(R.string.esp_mag_chip_desc)
            } else {
                stringResource(R.string.esp_mag_chip_need_fw)
            },
            enabled = controlsEnabled && info.magSupported,
            options = magChipOptions,
            selectorWidth = 300.dp,
        )
        StatusRow(
            stringResource(R.string.esp_mag_status),
            if (!info.magSupported) {
                stringResource(R.string.esp_mag_chip_need_fw_short)
            } else if (info.mag) {
                yesLabel
            } else {
                noLabel
            },
        )
        StatusRow(
            stringResource(R.string.esp_mag_chip_status),
            info.magChip.ifBlank { "—" },
        )
        StatusRow(
            stringResource(R.string.esp_mag_seen),
            if (info.magSeen.isEmpty()) "—" else info.magSeen.joinToString(", "),
        )
        val magFresh = lastMagAt > 0L && nowMs - lastMagAt <= 1_500L
        StatusRow(
            stringResource(R.string.esp_mag_heading),
            if (magFresh && lastMag.ok) {
                String.format(Locale.getDefault(), "%.1f°", lastMag.headingDeg)
            } else {
                "—"
            },
        )
        StatusRow(
            stringResource(R.string.esp_mag_fs),
            if (magFresh && lastMag.ok) {
                String.format(Locale.getDefault(), "%.1f µT", lastMag.fs)
            } else {
                "—"
            },
        )
        StatusRow(stringResource(R.string.location_fixation), if (loc.locateStatus) yesLabel else noLabel)
        StatusRow(stringResource(R.string.location_latitude), loc.latitude.toString())
        StatusRow(stringResource(R.string.location_longitude), loc.longitude.toString())
        StatusRow(stringResource(R.string.location_used_satellites), loc.usingSatellites.toString())
        StatusRow(stringResource(R.string.location_visible_satellites), loc.visibleSatellites.toString())
        StatusRow(
            stringResource(R.string.location_speed),
            String.format(Locale.getDefault(), "%.1f", loc.speed),
        )
        StatusRow(stringResource(R.string.location_utc), utcText)
        StatusRow(
            stringResource(R.string.location_last_change),
            loc.updateTime?.let { timeFormat.format(it) }.orEmpty(),
        )

        OutlinedButton(
            onClick = rememberWrappedOnClick { showRebootConfirm = true },
            enabled = controlsEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        ) {
            Text(stringResource(R.string.esp_reboot), style = MaterialTheme.typography.tboxButton)
        }
        Button(
            onClick = rememberWrappedOnClick {
                otaPicker.launch(arrayOf("application/octet-stream", "*/*"))
            },
            enabled = controlsEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        ) {
            Text(stringResource(R.string.esp_ota_update), style = MaterialTheme.typography.tboxButton)
        }
        if (otaBusy || otaProgress > 0) {
            Text(
                text = stringResource(R.string.esp_ota_progress, otaProgress),
                style = MaterialTheme.typography.tboxBody,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            LinearProgressIndicator(
                progress = { otaProgress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )
        }
        if (!otaBusy && !otaError.isNullOrBlank()) {
            Text(
                text = otaErrorMessage(context, otaError),
                style = MaterialTheme.typography.tboxCaption,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SettingsTitle(stringResource(R.string.esp_companion_log_title))
        val companionLog by CompanionProtocolLogRecorder.uiState.collectAsStateWithLifecycle()
        Text(
            text = stringResource(R.string.esp_companion_log_desc),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Text(
            text = if (companionLog.recording) {
                stringResource(R.string.esp_companion_log_recording, companionLog.events)
            } else {
                stringResource(R.string.esp_companion_log_idle)
            },
            style = MaterialTheme.typography.tboxBody,
            color = if (companionLog.recording) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(bottom = 8.dp),
        )
        SettingSwitch(
            isChecked = companionLog.huMarksEnabled,
            onCheckedChange = { enabled ->
                if (!enabled && CompanionProtocolLogRecorder.isRecording() &&
                    CompanionProtocolLogRecorder.isHuMarksEnabled()
                ) {
                    CompanionProtocolLogRecorder.appendMark("META", "huMarks=off")
                }
                CompanionProtocolLogRecorder.setHuMarksEnabled(enabled)
                if (enabled && CompanionProtocolLogRecorder.isRecording()) {
                    CompanionProtocolLogRecorder.appendMark("META", "huMarks=on")
                }
            },
            text = stringResource(R.string.esp_companion_log_hu_marks_title),
            description = stringResource(R.string.esp_companion_log_hu_marks_desc),
            enabled = companionEnabled,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = rememberWrappedOnClick {
                    context.startService(
                        Intent(context, BackgroundService::class.java).apply {
                            action = BackgroundService.ACTION_COMPANION_LOG_START
                        },
                    )
                },
                enabled = companionEnabled && !companionLog.recording,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    stringResource(R.string.esp_companion_log_start),
                    style = MaterialTheme.typography.tboxButton,
                    textAlign = TextAlign.Center,
                )
            }
            OutlinedButton(
                onClick = rememberWrappedOnClick {
                    context.startService(
                        Intent(context, BackgroundService::class.java).apply {
                            action = BackgroundService.ACTION_COMPANION_LOG_STOP
                        },
                    )
                },
                enabled = companionLog.recording,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    stringResource(R.string.esp_companion_log_stop),
                    style = MaterialTheme.typography.tboxButton,
                    textAlign = TextAlign.Center,
                )
            }
        }

        if (info.can) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Button(
                onClick = rememberWrappedOnClick { showCanConsole = true },
                enabled = controlsEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            ) {
                Text(
                    stringResource(R.string.esp_can_open),
                    style = MaterialTheme.typography.tboxButton,
                )
            }
            Text(
                text = stringResource(R.string.esp_can_open_desc),
                style = MaterialTheme.typography.tboxBody,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SettingsTitle(stringResource(R.string.esp_um980_settings_title))
        Button(
            onClick = rememberWrappedOnClick { showUm980Settings = true },
            enabled = controlsEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            Text(
                stringResource(R.string.esp_um980_open_settings),
                style = MaterialTheme.typography.tboxButton,
            )
        }
        Text(
            text = stringResource(R.string.esp_um980_open_settings_desc),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp),
        )
    }

    if (showUm980Settings) {
        Um980SettingsDialog(
            transport = Um980SettingsTransport.COMPANION,
            controlsEnabled = controlsEnabled,
            settingsViewModel = settingsViewModel,
            onDismiss = { showUm980Settings = false },
        )
    }

    if (showCanConsole) {
        CanCompanionDialog(
            controlsEnabled = controlsEnabled,
            onDismiss = { showCanConsole = false },
        )
    }

    if (showRebootConfirm) {
        AlertDialog(
            onDismissRequest = { showRebootConfirm = false },
            title = { AppAlertDialogTitle(stringResource(R.string.esp_reboot_confirm_title)) },
            text = {
                AppAlertDialogText(stringResource(R.string.esp_reboot_confirm_message))
            },
            confirmButton = {
                TextButton(
                    onClick = rememberWrappedOnClick {
                        showRebootConfirm = false
                        context.startService(
                            Intent(context, BackgroundService::class.java).apply {
                                action = BackgroundService.ACTION_ESP_REBOOT
                            },
                        )
                    },
                ) {
                    AppAlertDialogButtonLabel(stringResource(R.string.esp_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = rememberWrappedOnClick { showRebootConfirm = false }) {
                    AppAlertDialogButtonLabel(stringResource(R.string.action_cancel))
                }
            },
        )
    }
    pendingOtaFile?.let { file ->
        val sizeLabel = formatByteSize(file.length())
        AlertDialog(
            onDismissRequest = {
                file.delete()
                pendingOtaFile = null
            },
            title = { AppAlertDialogTitle(stringResource(R.string.esp_ota_confirm_title)) },
            text = {
                AppAlertDialogText(
                    stringResource(
                        R.string.esp_ota_confirm_message,
                        pendingOtaDisplayName.ifBlank { file.name },
                        sizeLabel,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = rememberWrappedOnClick {
                        pendingOtaFile = null
                        context.startService(
                            Intent(context, BackgroundService::class.java).apply {
                                action = BackgroundService.ACTION_ESP_OTA
                                putExtra(BackgroundService.EXTRA_ESP_OTA_PATH, file.absolutePath)
                            },
                        )
                    },
                ) {
                    AppAlertDialogButtonLabel(stringResource(R.string.esp_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = rememberWrappedOnClick {
                        file.delete()
                        pendingOtaFile = null
                    },
                ) {
                    AppAlertDialogButtonLabel(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun CanCompanionDialog(
    controlsEnabled: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val info by EspCompanionRepository.deviceInfo.collectAsStateWithLifecycle()
    val frames by EspCompanionRepository.canRecentFrames.collectAsStateWithLifecycle()
    val baudOptions = remember {
        EspCompanionProtocol.CAN_BAUD_OPTIONS.map { BaudOption(it, it.toString()) }
    }
    val selectedBaud = baudOptions.firstOrNull { it.baud == info.canBaud }
        ?: baudOptions.first { it.baud == 500_000 }
    var filterId by remember { mutableStateOf("") }
    var filterMask by remember { mutableStateOf("7FF") }
    var filterExt by remember { mutableStateOf(false) }
    var sendId by remember { mutableStateOf("") }
    var sendData by remember { mutableStateOf("") }
    var sendExt by remember { mutableStateOf(false) }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }

    DisposableEffect(Unit) {
        context.startService(
            Intent(context, BackgroundService::class.java).apply {
                action = BackgroundService.ACTION_ESP_CAN_CONSOLE_OPEN
            },
        )
        onDispose {
            context.startService(
                Intent(context, BackgroundService::class.java).apply {
                    action = BackgroundService.ACTION_ESP_CAN_CONSOLE_CLOSE
                },
            )
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 720.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                AppAlertDialogTitle(stringResource(R.string.esp_can_dialog_title))
                Column(
                    modifier = Modifier
                        .weight(1f, fill = true)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    SettingDropdownGeneric(
                        selectedValue = selectedBaud,
                        onValueChange = { opt ->
                            context.startService(
                                Intent(context, BackgroundService::class.java).apply {
                                    action = BackgroundService.ACTION_ESP_CAN_BAUD
                                    putExtra(BackgroundService.EXTRA_ESP_CAN_BAUD, opt.baud)
                                },
                            )
                        },
                        text = stringResource(R.string.esp_can_baud),
                        description = stringResource(R.string.esp_can_baud_desc),
                        enabled = controlsEnabled,
                        options = baudOptions,
                        selectorWidth = 220.dp,
                    )
                    OutlinedButton(
                        onClick = rememberWrappedOnClick {
                            context.startService(
                                Intent(context, BackgroundService::class.java).apply {
                                    action = BackgroundService.ACTION_ESP_CAN_FILTER
                                    putExtra(BackgroundService.EXTRA_ESP_CAN_FILTER_ACCEPT_ALL, true)
                                },
                            )
                        },
                        enabled = controlsEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    ) {
                        Text(
                            stringResource(R.string.esp_can_accept_all),
                            style = MaterialTheme.typography.tboxButton,
                        )
                    }
                    OutlinedTextField(
                        value = filterId,
                        onValueChange = { filterId = it },
                        label = { Text(stringResource(R.string.esp_can_filter_id)) },
                        singleLine = true,
                        enabled = controlsEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    )
                    OutlinedTextField(
                        value = filterMask,
                        onValueChange = { filterMask = it },
                        label = { Text(stringResource(R.string.esp_can_filter_mask)) },
                        singleLine = true,
                        enabled = controlsEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = filterExt,
                            onCheckedChange = { filterExt = it },
                            enabled = controlsEnabled,
                        )
                        Text(
                            stringResource(R.string.esp_can_filter_ext),
                            style = MaterialTheme.typography.tboxBody,
                        )
                    }
                    Button(
                        onClick = rememberWrappedOnClick {
                            context.startService(
                                Intent(context, BackgroundService::class.java).apply {
                                    action = BackgroundService.ACTION_ESP_CAN_FILTER
                                    putExtra(BackgroundService.EXTRA_ESP_CAN_FILTER_ID, filterId)
                                    putExtra(BackgroundService.EXTRA_ESP_CAN_FILTER_MASK, filterMask)
                                    putExtra(BackgroundService.EXTRA_ESP_CAN_FILTER_EXT, filterExt)
                                },
                            )
                        },
                        enabled = controlsEnabled && filterId.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    ) {
                        Text(
                            stringResource(R.string.esp_can_filter_apply),
                            style = MaterialTheme.typography.tboxButton,
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    OutlinedTextField(
                        value = sendId,
                        onValueChange = { sendId = it },
                        label = { Text(stringResource(R.string.esp_can_send_id)) },
                        singleLine = true,
                        enabled = controlsEnabled,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = sendData,
                        onValueChange = { sendData = it },
                        label = { Text(stringResource(R.string.esp_can_send_data)) },
                        singleLine = true,
                        enabled = controlsEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = sendExt,
                            onCheckedChange = { sendExt = it },
                            enabled = controlsEnabled,
                        )
                        Text(
                            stringResource(R.string.esp_can_send_ext),
                            style = MaterialTheme.typography.tboxBody,
                        )
                    }
                    Button(
                        onClick = rememberWrappedOnClick {
                            context.startService(
                                Intent(context, BackgroundService::class.java).apply {
                                    action = BackgroundService.ACTION_ESP_CAN_TX
                                    putExtra(BackgroundService.EXTRA_ESP_CAN_TX_ID, sendId)
                                    putExtra(BackgroundService.EXTRA_ESP_CAN_TX_DATA, sendData)
                                    putExtra(BackgroundService.EXTRA_ESP_CAN_TX_EXT, sendExt)
                                },
                            )
                        },
                        enabled = controlsEnabled && sendId.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    ) {
                        Text(
                            stringResource(R.string.esp_can_send),
                            style = MaterialTheme.typography.tboxButton,
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        text = stringResource(R.string.esp_can_recent_title),
                        style = MaterialTheme.typography.tboxBody,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    val newestFirst = remember(frames) { frames.asReversed().take(80) }
                    if (newestFirst.isEmpty()) {
                        Text(
                            text = stringResource(R.string.esp_can_recent_empty),
                            style = MaterialTheme.typography.tboxCaption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        for (entry in newestFirst) {
                            Text(
                                text = timeFormat.format(Date(entry.atMs)) + " " +
                                    EspCompanionProtocol.formatCanFrame(entry.frame),
                                style = MaterialTheme.typography.tboxCaption,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }
                    }
                }
                TextButton(
                    onClick = rememberWrappedOnClick(onDismiss),
                    modifier = Modifier.align(Alignment.End),
                ) {
                    AppAlertDialogButtonLabel(stringResource(R.string.action_close))
                }
            }
        }
    }
}

private fun prepareOtaCacheFile(context: Context, uri: Uri): Result<Pair<File, String>> {
    return runCatching {
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "esp32_companion.bin"
        val out = File(context.cacheDir, "esp_ota_${System.currentTimeMillis()}.bin")
        context.contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        } ?: error("bad_file")
        if (!out.isFile || out.length() <= 0L) {
            out.delete()
            error("empty")
        }
        val first = out.inputStream().use { it.read() }
        EspCompanionProtocol.validateFirmwareImage(out.length(), first)?.let { code ->
            out.delete()
            error(code)
        }
        out to name
    }
}

private fun otaErrorMessage(context: Context, code: String?): String {
    return when (code) {
        "no_usb" -> context.getString(R.string.esp_ota_error_no_usb)
        "bad_file" -> context.getString(R.string.esp_ota_error_bad_file)
        "empty" -> context.getString(R.string.esp_ota_error_empty)
        "too_large" -> context.getString(R.string.esp_ota_error_too_large)
        "bad_magic" -> context.getString(R.string.esp_ota_error_bad_magic)
        "timeout" -> context.getString(R.string.esp_ota_error_timeout)
        null, "" -> context.getString(R.string.esp_ota_error_bad_file)
        else -> context.getString(R.string.esp_ota_error_generic, code)
    }
}

private fun formatByteSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    return String.format(Locale.US, "%.2f MB", kb / 1024.0)
}

private fun Context.sendUm980Cmd(cmd: String) {
    startService(
        Intent(this, BackgroundService::class.java).apply {
            action = BackgroundService.ACTION_ESP_UM980_CMD
            putExtra(BackgroundService.EXTRA_ESP_UM980_CMD, cmd)
        },
    )
}

private fun Context.sendUm980Cmds(cmds: List<String>, refreshAfter: Boolean = false) {
    startService(
        Intent(this, BackgroundService::class.java).apply {
            action = BackgroundService.ACTION_ESP_UM980_CMD
            putStringArrayListExtra(
                BackgroundService.EXTRA_ESP_UM980_CMDS,
                ArrayList(cmds),
            )
            putExtra(BackgroundService.EXTRA_ESP_UM980_REFRESH_AFTER, refreshAfter)
        },
    )
}

private data class BaudOption(val baud: Int, val label: String) {
    override fun toString(): String = label
}

private data class MagChipOption(val id: String, val label: String) {
    override fun toString(): String = label
}

