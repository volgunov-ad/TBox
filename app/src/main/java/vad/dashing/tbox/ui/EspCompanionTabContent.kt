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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vad.dashing.tbox.BackgroundService
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.esp.EspCompanionProtocol
import vad.dashing.tbox.esp.EspCompanionRepository
import vad.dashing.tbox.esp.Um980Commands
import vad.dashing.tbox.esp.Um980LogDirection
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
    val snapshot by EspCompanionRepository.um980ConfigSnapshot.collectAsStateWithLifecycle()
    val otaBusy by EspCompanionRepository.otaBusy.collectAsStateWithLifecycle()
    val otaProgress by EspCompanionRepository.otaProgress.collectAsStateWithLifecycle()
    val otaError by EspCompanionRepository.otaError.collectAsStateWithLifecycle()
    val um980ConfigBusy by EspCompanionRepository.um980ConfigBusy.collectAsStateWithLifecycle()
    val um980Log by EspCompanionRepository.um980TrafficLog.collectAsStateWithLifecycle()

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

    var showFresetConfirm by remember { mutableStateOf(false) }
    var showRebootConfirm by remember { mutableStateOf(false) }
    var pendingSignalGroup by remember { mutableStateOf<SignalGroupOption?>(null) }
    var pendingOtaFile by remember { mutableStateOf<File?>(null) }
    var pendingOtaDisplayName by remember { mutableStateOf("") }
    var refreshConfigCooldownUntilMs by remember { mutableLongStateOf(0L) }
    val refreshConfigOnCooldown = nowMs < refreshConfigCooldownUntilMs

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

    val nmeaRateOptions = listOf(
        NmeaRateOption(0.0, stringResource(R.string.esp_um980_rate_off)),
        NmeaRateOption(0.5, stringResource(R.string.esp_um980_rate_0_5s)),
        NmeaRateOption(1.0, stringResource(R.string.esp_um980_rate_1s)),
        NmeaRateOption(2.0, stringResource(R.string.esp_um980_rate_2s)),
    )
    var coordPeriod by remember { mutableStateOf(0.5) }
    var gsaPeriod by remember { mutableStateOf(1.0) }
    var gsvPeriod by remember { mutableStateOf(1.0) }
    var zdaPeriod by remember { mutableStateOf(2.0) }
    var vtgPeriod by remember { mutableStateOf(2.0) }

    val modeOptions = listOf(
        ModeOption("AUTOMOTIVE", stringResource(R.string.esp_um980_mode_automotive)),
        ModeOption("UAV", stringResource(R.string.esp_um980_mode_uav)),
        ModeOption("ROVER", stringResource(R.string.esp_um980_mode_rover)),
    )
    val selectedMode = modeOptions.firstOrNull { it.id == (snapshot.mode ?: "AUTOMOTIVE") }
        ?: modeOptions.first()

    val signalGroupOptions = listOf(
        SignalGroupOption(1, "1"),
        SignalGroupOption(2, "2"),
        SignalGroupOption(8, stringResource(R.string.esp_um980_signalgroup_8)),
    )
    val selectedSignalGroup = signalGroupOptions.firstOrNull { it.id == (snapshot.signalGroup ?: 2) }
        ?: signalGroupOptions[1]

    val antijamOptions = listOf(
        AntijamOption("FORCE", stringResource(R.string.esp_um980_antijam_force)),
        AntijamOption("AUTO", stringResource(R.string.esp_um980_antijam_auto)),
        AntijamOption("DISABLE", stringResource(R.string.esp_um980_antijam_disable)),
    )
    val selectedAntijam = antijamOptions.firstOrNull {
        it.id == (snapshot.antijamMode ?: "AUTO")
    } ?: antijamOptions[1]

    val pvtAlgOptions = listOf(
        PvtAlgOption("MULTI", "MULTI"),
        PvtAlgOption("AUTO", "AUTO"),
        PvtAlgOption("SINGLE", "SINGLE"),
    )
    val selectedPvtAlg = pvtAlgOptions.firstOrNull {
        it.id == (snapshot.pvtAlg ?: "AUTO")
    } ?: pvtAlgOptions[1]

    val sbasOptions = listOf(
        SbasOption("DISABLE", stringResource(R.string.esp_um980_sbas_disable)),
        SbasOption("AUTO", "AUTO"),
        SbasOption("SDCM", "SDCM"),
        SbasOption("EGNOS", "EGNOS"),
        SbasOption("WAAS", "WAAS"),
    )
    val selectedSbas = sbasOptions.firstOrNull {
        it.id == (snapshot.sbasMode ?: "DISABLE")
    } ?: sbasOptions.first()

    val maskOptions = listOf(0, 5, 10, 15, 20).map { MaskOption(it, "$it°") }
    val selectedMask = maskOptions.firstOrNull {
        it.deg == (snapshot.maskElevation ?: 5)
    } ?: maskOptions[1]

    val rtkReliabilityOptions = listOf(1, 2, 3, 4).map { RtkReliabilityOption(it, it.toString()) }
    val selectedRtkReliability = rtkReliabilityOptions.firstOrNull {
        it.level == (snapshot.rtkReliability ?: 3)
    } ?: rtkReliabilityOptions[2]

    val smoothHeightOptions = listOf(0, 5, 10, 20, 50).map { SmoothHeightOption(it, it.toString()) }
    val selectedSmoothHeight = smoothHeightOptions.firstOrNull {
        it.epochs == (snapshot.smoothRtkHeight ?: 0)
    } ?: smoothHeightOptions.first()

    val dgpsOptions = listOf(
        DgpsOption(60, "60"),
        DgpsOption(300, "300"),
        DgpsOption(600, "600"),
    )
    val selectedDgps = dgpsOptions.firstOrNull { it.sec == (snapshot.dgpsTimeout ?: 600) }
        ?: dgpsOptions.last()

    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val logTimeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
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
        StatusRow(
            stringResource(R.string.esp_gpio_inputs),
            Integer.toBinaryString(gpioMask).padStart(8, '0'),
        )
        StatusRow(
            stringResource(R.string.esp_relays),
            Integer.toBinaryString(relayMask).padStart(4, '0'),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (ch in 0 until 4) {
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
        SettingsTitle(stringResource(R.string.esp_um980_resets_title))
        Text(
            text = stringResource(R.string.esp_um980_resets_desc),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = rememberWrappedOnClick { context.sendUm980Cmd("RESET") },
                enabled = controlsEnabled,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.esp_um980_reset_hot), style = MaterialTheme.typography.tboxCaption)
            }
            OutlinedButton(
                onClick = rememberWrappedOnClick { context.sendUm980Cmd("RESET EPHEM") },
                enabled = controlsEnabled,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.esp_um980_reset_warm), style = MaterialTheme.typography.tboxCaption)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = rememberWrappedOnClick {
                    context.sendUm980Cmd("RESET EPHEM ALMANAC IONUTC POSITION")
                },
                enabled = controlsEnabled,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.esp_um980_reset_cold), style = MaterialTheme.typography.tboxCaption)
            }
            OutlinedButton(
                onClick = rememberWrappedOnClick { showFresetConfirm = true },
                enabled = controlsEnabled,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.esp_um980_freset), style = MaterialTheme.typography.tboxCaption)
            }
        }
        Button(
            onClick = rememberWrappedOnClick {
                refreshConfigCooldownUntilMs = System.currentTimeMillis() + 5_000L
                context.sendUm980Cmds(Um980Commands.refreshSnapshotCommands())
            },
            enabled = controlsEnabled && !refreshConfigOnCooldown,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text(stringResource(R.string.esp_um980_refresh_config), style = MaterialTheme.typography.tboxButton)
        }
        Text(
            text = stringResource(R.string.esp_um980_refresh_config_desc),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SettingsTitle(stringResource(R.string.esp_um980_geo_period_title))
        SettingDropdownGeneric(
            selectedValue = nmeaRateOptions.first { it.periodSec == coordPeriod },
            onValueChange = { opt ->
                coordPeriod = opt.periodSec
                context.sendUm980Cmds(Um980Commands.ggaRmcCommands(opt.periodSec))
            },
            text = stringResource(R.string.esp_um980_coord_period),
            description = stringResource(R.string.esp_um980_coord_period_desc),
            enabled = controlsEnabled,
            options = nmeaRateOptions,
            selectorWidth = 300.dp,
        )
        SettingDropdownGeneric(
            selectedValue = nmeaRateOptions.first { it.periodSec == gsaPeriod },
            onValueChange = { opt ->
                gsaPeriod = opt.periodSec
                context.sendUm980Cmd("GPGSA ${Um980Commands.periodSecondsToNmeaRate(opt.periodSec)}")
            },
            text = stringResource(R.string.esp_um980_gsa),
            description = stringResource(R.string.esp_um980_gsa_desc),
            enabled = controlsEnabled,
            options = nmeaRateOptions,
            selectorWidth = 300.dp,
        )
        SettingDropdownGeneric(
            selectedValue = nmeaRateOptions.first { it.periodSec == gsvPeriod },
            onValueChange = { opt ->
                gsvPeriod = opt.periodSec
                context.sendUm980Cmd("GPGSV ${Um980Commands.periodSecondsToNmeaRate(opt.periodSec)}")
            },
            text = stringResource(R.string.esp_um980_gsv),
            description = stringResource(R.string.esp_um980_gsv_desc),
            enabled = controlsEnabled,
            options = nmeaRateOptions,
            selectorWidth = 300.dp,
        )
        SettingDropdownGeneric(
            selectedValue = nmeaRateOptions.first { it.periodSec == zdaPeriod },
            onValueChange = { opt ->
                zdaPeriod = opt.periodSec
                context.sendUm980Cmd("GPZDA ${Um980Commands.periodSecondsToNmeaRate(opt.periodSec)}")
            },
            text = stringResource(R.string.esp_um980_zda),
            description = stringResource(R.string.esp_um980_zda_desc),
            enabled = controlsEnabled,
            options = nmeaRateOptions,
            selectorWidth = 300.dp,
        )
        SettingDropdownGeneric(
            selectedValue = nmeaRateOptions.first { it.periodSec == vtgPeriod },
            onValueChange = { opt ->
                vtgPeriod = opt.periodSec
                context.sendUm980Cmd("GPVTG ${Um980Commands.periodSecondsToNmeaRate(opt.periodSec)}")
            },
            text = stringResource(R.string.esp_um980_vtg),
            description = stringResource(R.string.esp_um980_vtg_desc),
            enabled = controlsEnabled,
            options = nmeaRateOptions,
            selectorWidth = 300.dp,
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SettingsTitle(stringResource(R.string.esp_um980_settings_title))
        SettingDropdownGeneric(
            selectedValue = selectedDgps,
            onValueChange = { opt ->
                context.sendUm980Cmd("CONFIG DGPS TIMEOUT ${opt.sec}")
            },
            text = stringResource(R.string.esp_um980_dgps_timeout),
            description = stringResource(R.string.esp_um980_dgps_timeout_desc),
            enabled = controlsEnabled,
            options = dgpsOptions,
            selectorWidth = 300.dp,
        )
        SettingSwitch(
            isChecked = snapshot.rtkOff != true,
            onCheckedChange = { enabled ->
                if (enabled) {
                    context.sendUm980Cmd("CONFIG RTK TIMEOUT 600")
                } else {
                    context.sendUm980Cmd("CONFIG RTK TIMEOUT 0")
                }
            },
            text = stringResource(R.string.esp_um980_rtk),
            description = stringResource(R.string.esp_um980_rtk_desc),
            enabled = controlsEnabled,
        )
        SettingDropdownGeneric(
            selectedValue = selectedRtkReliability,
            onValueChange = { opt ->
                context.sendUm980Cmd("CONFIG RTK RELIABILITY ${opt.level}")
            },
            text = stringResource(R.string.esp_um980_rtk_reliability),
            description = stringResource(R.string.esp_um980_rtk_reliability_desc),
            enabled = controlsEnabled,
            options = rtkReliabilityOptions,
            selectorWidth = 300.dp,
        )
        SettingSwitch(
            isChecked = snapshot.standalone == true,
            onCheckedChange = { enabled ->
                context.sendUm980Cmd(
                    if (enabled) "CONFIG STANDALONE ENABLE" else "CONFIG STANDALONE DISABLE",
                )
            },
            text = stringResource(R.string.esp_um980_standalone),
            description = stringResource(R.string.esp_um980_standalone_desc),
            enabled = controlsEnabled,
        )
        Button(
            onClick = rememberWrappedOnClick { context.sendUm980Cmd("CONFIG ALGRESET RTK1") },
            enabled = controlsEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            Text(stringResource(R.string.esp_um980_algreset_rtk), style = MaterialTheme.typography.tboxButton)
        }
        Text(
            text = stringResource(R.string.esp_um980_algreset_rtk_desc),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Button(
            onClick = rememberWrappedOnClick { context.sendUm980Cmd("CONFIG ALGRESET ADR") },
            enabled = controlsEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            Text(stringResource(R.string.esp_um980_algreset_adr), style = MaterialTheme.typography.tboxButton)
        }
        Text(
            text = stringResource(R.string.esp_um980_algreset_adr_desc),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        SettingDropdownGeneric(
            selectedValue = selectedMode,
            onValueChange = { opt ->
                val cmd = when (opt.id) {
                    "AUTOMOTIVE" -> "MODE ROVER AUTOMOTIVE"
                    "UAV" -> "MODE ROVER UAV"
                    else -> "MODE ROVER"
                }
                context.sendUm980Cmd(cmd)
            },
            text = stringResource(R.string.esp_um980_mode),
            description = stringResource(R.string.esp_um980_mode_desc),
            enabled = controlsEnabled,
            options = modeOptions,
            selectorWidth = 300.dp,
        )
        SettingDropdownGeneric(
            selectedValue = selectedMask,
            onValueChange = { opt ->
                context.sendUm980Cmd("MASK ${opt.deg}")
            },
            text = stringResource(R.string.esp_um980_mask),
            description = stringResource(R.string.esp_um980_mask_desc),
            enabled = controlsEnabled,
            options = maskOptions,
            selectorWidth = 300.dp,
        )
        SettingDropdownGeneric(
            selectedValue = selectedSbas,
            onValueChange = { opt ->
                val cmd = when (opt.id) {
                    "DISABLE" -> "CONFIG SBAS DISABLE"
                    else -> "CONFIG SBAS ENABLE ${opt.id}"
                }
                context.sendUm980Cmd(cmd)
            },
            text = stringResource(R.string.esp_um980_sbas),
            description = stringResource(R.string.esp_um980_sbas_desc),
            enabled = controlsEnabled,
            options = sbasOptions,
            selectorWidth = 300.dp,
        )
        SettingSwitch(
            isChecked = snapshot.mmp == true,
            onCheckedChange = { enabled ->
                context.sendUm980Cmd(if (enabled) "CONFIG MMP ENABLE" else "CONFIG MMP DISABLE")
            },
            text = stringResource(R.string.esp_um980_mmp),
            description = stringResource(R.string.esp_um980_mmp_desc),
            enabled = controlsEnabled,
        )
        SettingSwitch(
            isChecked = snapshot.agnss == true,
            onCheckedChange = { enabled ->
                context.sendUm980Cmd(if (enabled) "CONFIG AGNSS ENABLE" else "CONFIG AGNSS DISABLE")
            },
            text = stringResource(R.string.esp_um980_agnss),
            description = stringResource(R.string.esp_um980_agnss_desc),
            enabled = controlsEnabled,
        )
        SettingDropdownGeneric(
            selectedValue = selectedAntijam,
            onValueChange = { opt ->
                context.sendUm980Cmd("CONFIG ANTIJAM ${opt.id}")
            },
            text = stringResource(R.string.esp_um980_antijam),
            description = stringResource(R.string.esp_um980_antijam_desc),
            enabled = controlsEnabled,
            options = antijamOptions,
            selectorWidth = 300.dp,
        )
        SettingDropdownGeneric(
            selectedValue = selectedSignalGroup,
            onValueChange = { opt ->
                if (opt.id == (snapshot.signalGroup ?: 2)) return@SettingDropdownGeneric
                pendingSignalGroup = opt
            },
            text = stringResource(R.string.esp_um980_signalgroup),
            description = stringResource(R.string.esp_um980_signalgroup_desc),
            enabled = controlsEnabled,
            options = signalGroupOptions,
            selectorWidth = 300.dp,
        )
        SettingDropdownGeneric(
            selectedValue = selectedPvtAlg,
            onValueChange = { opt ->
                context.sendUm980Cmd("CONFIG PVTALG ${opt.id}")
            },
            text = stringResource(R.string.esp_um980_pvtalg),
            description = stringResource(R.string.esp_um980_pvtalg_desc),
            enabled = controlsEnabled,
            options = pvtAlgOptions,
            selectorWidth = 300.dp,
        )
        SettingSwitch(
            isChecked = snapshot.smoothPsrVel == true,
            onCheckedChange = { enabled ->
                context.sendUm980Cmd(
                    if (enabled) "CONFIG SMOOTH PSRVEL ENABLE" else "CONFIG SMOOTH PSRVEL DISABLE",
                )
            },
            text = stringResource(R.string.esp_um980_smooth_psrvel),
            description = stringResource(R.string.esp_um980_smooth_psrvel_desc),
            enabled = controlsEnabled,
        )
        SettingDropdownGeneric(
            selectedValue = selectedSmoothHeight,
            onValueChange = { opt ->
                context.sendUm980Cmd("CONFIG SMOOTH RTKHEIGHT ${opt.epochs}")
            },
            text = stringResource(R.string.esp_um980_smooth_rtkheight),
            description = stringResource(R.string.esp_um980_smooth_rtkheight_desc),
            enabled = controlsEnabled,
            options = smoothHeightOptions,
            selectorWidth = 300.dp,
        )
        SettingSwitch(
            isChecked = snapshot.psrVelDrPos == true,
            onCheckedChange = { enabled ->
                context.sendUm980Cmd(
                    if (enabled) "CONFIG PSRVELDRPOS ENABLE" else "CONFIG PSRVELDRPOS DISABLE",
                )
            },
            text = stringResource(R.string.esp_um980_psrveldrpos),
            description = stringResource(R.string.esp_um980_psrveldrpos_desc),
            enabled = controlsEnabled,
        )

        Button(
            onClick = rememberWrappedOnClick {
                context.sendUm980Cmds(
                    Um980Commands.gpsGuideProfileCommands(),
                    refreshAfter = true,
                )
            },
            enabled = controlsEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text(stringResource(R.string.esp_um980_guide_profile), style = MaterialTheme.typography.tboxButton)
        }
        Text(
            text = stringResource(R.string.esp_um980_guide_profile_desc),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        OutlinedButton(
            onClick = rememberWrappedOnClick {
                context.sendUm980Cmds(listOf("SAVECONFIG"), refreshAfter = true)
            },
            enabled = controlsEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        ) {
            Text(stringResource(R.string.esp_um980_saveconfig), style = MaterialTheme.typography.tboxButton)
        }
        Text(
            text = stringResource(R.string.esp_um980_saveconfig_desc),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                top = 4.dp,
                bottom = if (um980ConfigBusy) 0.dp else 16.dp,
            ),
        )
        if (um980ConfigBusy) {
            Text(
                text = stringResource(R.string.esp_um980_config_busy),
                style = MaterialTheme.typography.tboxCaption,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SettingsTitle(stringResource(R.string.esp_um980_traffic_log_title))
        Text(
            text = stringResource(R.string.esp_um980_traffic_log_desc),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        if (um980Log.isEmpty()) {
            Text(
                text = "—",
                style = MaterialTheme.typography.tboxCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        } else {
            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                for (entry in um980Log) {
                    val prefix = when (entry.direction) {
                        Um980LogDirection.TX -> "→"
                        Um980LogDirection.RX -> "←"
                    }
                    val isErr = entry.text.contains("PARSING FAILD", ignoreCase = true) ||
                        entry.text.contains("GRAMMAR ERROR", ignoreCase = true) ||
                        entry.text.contains(" FAIL:", ignoreCase = false) ||
                        entry.text.contains(" ERR:", ignoreCase = false)
                    Text(
                        text = "${logTimeFormat.format(Date(entry.atMs))} $prefix ${entry.text}",
                        style = MaterialTheme.typography.tboxCaption,
                        fontFamily = FontFamily.Monospace,
                        color = if (isErr) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                    )
                }
            }
        }
    }

    pendingSignalGroup?.let { opt ->
        AlertDialog(
            onDismissRequest = { pendingSignalGroup = null },
            title = { AppAlertDialogTitle(stringResource(R.string.esp_um980_signalgroup_confirm_title)) },
            text = {
                Text(
                    stringResource(R.string.esp_um980_signalgroup_confirm_message, opt.id),
                    style = MaterialTheme.typography.tboxBody,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = rememberWrappedOnClick {
                        pendingSignalGroup = null
                        context.sendUm980Cmd("CONFIG SIGNALGROUP ${opt.id}")
                    },
                ) {
                    Text(stringResource(R.string.esp_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = rememberWrappedOnClick { pendingSignalGroup = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
    if (showFresetConfirm) {
        AlertDialog(
            onDismissRequest = { showFresetConfirm = false },
            title = { AppAlertDialogTitle(stringResource(R.string.esp_um980_freset_confirm_title)) },
            text = {
                Text(
                    stringResource(R.string.esp_um980_freset_confirm_message),
                    style = MaterialTheme.typography.tboxBody,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = rememberWrappedOnClick {
                        showFresetConfirm = false
                        context.sendUm980Cmd("FRESET")
                    },
                ) {
                    Text(stringResource(R.string.esp_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = rememberWrappedOnClick { showFresetConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
    if (showRebootConfirm) {
        AlertDialog(
            onDismissRequest = { showRebootConfirm = false },
            title = { AppAlertDialogTitle(stringResource(R.string.esp_reboot_confirm_title)) },
            text = {
                Text(
                    stringResource(R.string.esp_reboot_confirm_message),
                    style = MaterialTheme.typography.tboxBody,
                )
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
                    Text(stringResource(R.string.esp_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = rememberWrappedOnClick { showRebootConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
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
                Text(
                    stringResource(
                        R.string.esp_ota_confirm_message,
                        pendingOtaDisplayName.ifBlank { file.name },
                        sizeLabel,
                    ),
                    style = MaterialTheme.typography.tboxBody,
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
                    Text(stringResource(R.string.esp_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = rememberWrappedOnClick {
                        file.delete()
                        pendingOtaFile = null
                    },
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
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

private data class NmeaRateOption(val periodSec: Double, val label: String) {
    override fun toString(): String = label
}

private data class BaudOption(val baud: Int, val label: String) {
    override fun toString(): String = label
}

private data class ModeOption(val id: String, val label: String) {
    override fun toString(): String = label
}

private data class SignalGroupOption(val id: Int, val label: String) {
    override fun toString(): String = label
}

private data class DgpsOption(val sec: Int, val label: String) {
    override fun toString(): String = label
}

private data class AntijamOption(val id: String, val label: String) {
    override fun toString(): String = label
}

private data class PvtAlgOption(val id: String, val label: String) {
    override fun toString(): String = label
}

private data class SbasOption(val id: String, val label: String) {
    override fun toString(): String = label
}

private data class MaskOption(val deg: Int, val label: String) {
    override fun toString(): String = label
}

private data class RtkReliabilityOption(val level: Int, val label: String) {
    override fun toString(): String = label
}

private data class SmoothHeightOption(val epochs: Int, val label: String) {
    override fun toString(): String = label
}
