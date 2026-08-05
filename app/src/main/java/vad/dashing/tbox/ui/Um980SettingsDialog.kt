package vad.dashing.tbox.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import vad.dashing.tbox.BackgroundService
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.esp.EspCompanionRepository
import vad.dashing.tbox.esp.Um980Commands
import vad.dashing.tbox.esp.Um980ConfigSnapshot
import vad.dashing.tbox.esp.Um980ConfigUiStore
import vad.dashing.tbox.usbgnss.UsbGnssNmeaEnableCommands
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxButton
import vad.dashing.tbox.ui.theme.tboxCaption

enum class Um980SettingsTransport {
    COMPANION,
    USB,
}

@Composable
fun Um980SettingsDialog(
    transport: Um980SettingsTransport,
    controlsEnabled: Boolean,
    settingsViewModel: SettingsViewModel,
    onDismiss: () -> Unit,
) {
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
            Column(
                modifier = Modifier.padding(24.dp),
            ) {
                AppAlertDialogTitle(stringResource(R.string.esp_um980_settings_dialog_title))
                Um980SettingsContent(
                    transport = transport,
                    controlsEnabled = controlsEnabled,
                    settingsViewModel = settingsViewModel,
                    modifier = Modifier
                        .weight(1f, fill = true)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                )
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

@Composable
fun Um980SettingsContent(
    transport: Um980SettingsTransport,
    controlsEnabled: Boolean,
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val companionSnapshot by EspCompanionRepository.um980ConfigSnapshot.collectAsStateWithLifecycle()
    val companionBusy by EspCompanionRepository.um980ConfigBusy.collectAsStateWithLifecycle()
    val usbSnapshot by Um980ConfigUiStore.snapshot.collectAsStateWithLifecycle()
    val usbBusy by Um980ConfigUiStore.busy.collectAsStateWithLifecycle()

    val usbRequestVtg by settingsViewModel.usbGnssRequestVtg.collectAsStateWithLifecycle()
    val usbRequestZda by settingsViewModel.usbGnssRequestZda.collectAsStateWithLifecycle()
    val usbRequestGst by settingsViewModel.usbGnssRequestGst.collectAsStateWithLifecycle()
    val espRequestVtg by settingsViewModel.espUm980RequestVtg.collectAsStateWithLifecycle()
    val espRequestZda by settingsViewModel.espUm980RequestZda.collectAsStateWithLifecycle()
    val espRequestGst by settingsViewModel.espUm980RequestGst.collectAsStateWithLifecycle()

    val snapshot: Um980ConfigSnapshot =
        if (transport == Um980SettingsTransport.COMPANION) companionSnapshot else usbSnapshot
    val um980ConfigBusy =
        if (transport == Um980SettingsTransport.COMPANION) companionBusy else usbBusy

    val requestVtg =
        if (transport == Um980SettingsTransport.COMPANION) espRequestVtg else usbRequestVtg
    val requestZda =
        if (transport == Um980SettingsTransport.COMPANION) espRequestZda else usbRequestZda
    val requestGst =
        if (transport == Um980SettingsTransport.COMPANION) espRequestGst else usbRequestGst

    val enabled = controlsEnabled && !um980ConfigBusy

    fun sendCmd(cmd: String) {
        context.sendUm980TransportCmd(transport, cmd)
    }
    fun sendCmds(
        cmds: List<String>,
        refreshAfter: Boolean = false,
        ensureSignalGroup: Int = 0,
    ) {
        context.sendUm980TransportCmds(transport, cmds, refreshAfter, ensureSignalGroup)
    }

    fun saveRequestVtg(enabled: Boolean) {
        if (transport == Um980SettingsTransport.COMPANION) {
            settingsViewModel.saveEspUm980RequestVtgSetting(enabled)
        } else {
            settingsViewModel.saveUsbGnssRequestVtgSetting(enabled)
        }
        sendCmd(
            if (enabled) UsbGnssNmeaEnableCommands.unicoreEnableVtg()
            else "GPVTG 0",
        )
    }
    fun saveRequestZda(enabled: Boolean) {
        if (transport == Um980SettingsTransport.COMPANION) {
            settingsViewModel.saveEspUm980RequestZdaSetting(enabled)
        } else {
            settingsViewModel.saveUsbGnssRequestZdaSetting(enabled)
        }
        sendCmd(
            if (enabled) UsbGnssNmeaEnableCommands.unicoreEnableZda()
            else "GPZDA 0",
        )
    }
    fun saveRequestGst(enabled: Boolean) {
        if (transport == Um980SettingsTransport.COMPANION) {
            settingsViewModel.saveEspUm980RequestGstSetting(enabled)
        } else {
            settingsViewModel.saveUsbGnssRequestGstSetting(enabled)
        }
        sendCmd(
            if (enabled) UsbGnssNmeaEnableCommands.unicoreEnableGst()
            else "GPGST 0",
        )
    }

    LaunchedEffect(transport) {
        sendCmds(Um980Commands.refreshSnapshotCommands())
    }

    var showFresetConfirm by remember { mutableStateOf(false) }
    var pendingSignalGroup by remember { mutableStateOf<Um980SignalGroupOption?>(null) }
    var precisionCmdsExpanded by remember { mutableStateOf(false) }
    var antispoofCmdsExpanded by remember { mutableStateOf(false) }
    var refreshConfigCooldownUntilMs by remember { mutableLongStateOf(0L) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            nowMs = System.currentTimeMillis()
        }
    }
    val refreshConfigOnCooldown = nowMs < refreshConfigCooldownUntilMs

    val nmeaRateOptions = listOf(
        Um980NmeaRateOption(0.0, stringResource(R.string.esp_um980_rate_off)),
        Um980NmeaRateOption(0.5, stringResource(R.string.esp_um980_rate_0_5s)),
        Um980NmeaRateOption(1.0, stringResource(R.string.esp_um980_rate_1s)),
        Um980NmeaRateOption(2.0, stringResource(R.string.esp_um980_rate_2s)),
    )
    var coordPeriod by remember { mutableStateOf(0.5) }
    var gsaPeriod by remember { mutableStateOf(1.0) }
    var gsvPeriod by remember { mutableStateOf(1.0) }
    var zdaPeriod by remember { mutableStateOf(2.0) }
    var vtgPeriod by remember { mutableStateOf(2.0) }

    val modeOptions = listOf(
        Um980ModeOption("AUTOMOTIVE", stringResource(R.string.esp_um980_mode_automotive)),
        Um980ModeOption("UAV", stringResource(R.string.esp_um980_mode_uav)),
        Um980ModeOption("ROVER", stringResource(R.string.esp_um980_mode_rover)),
    )
    val selectedMode = modeOptions.firstOrNull { it.id == (snapshot.mode ?: "AUTOMOTIVE") }
        ?: modeOptions.first()

    val signalGroupOptions = listOf(
        Um980SignalGroupOption(1, "1"),
        Um980SignalGroupOption(2, "2"),
        Um980SignalGroupOption(8, stringResource(R.string.esp_um980_signalgroup_8)),
    )
    val selectedSignalGroup = signalGroupOptions.firstOrNull { it.id == (snapshot.signalGroup ?: 2) }
        ?: signalGroupOptions[1]

    val antijamOptions = listOf(
        Um980AntijamOption("FORCE", stringResource(R.string.esp_um980_antijam_force)),
        Um980AntijamOption("AUTO", stringResource(R.string.esp_um980_antijam_auto)),
        Um980AntijamOption("DISABLE", stringResource(R.string.esp_um980_antijam_disable)),
    )
    val selectedAntijam = antijamOptions.firstOrNull {
        it.id == (snapshot.antijamMode ?: "AUTO")
    } ?: antijamOptions[1]

    val pvtAlgOptions = listOf(
        Um980PvtAlgOption("MULTI", "MULTI"),
        Um980PvtAlgOption("AUTO", "AUTO"),
        Um980PvtAlgOption("SINGLE", "SINGLE"),
    )
    val selectedPvtAlg = pvtAlgOptions.firstOrNull {
        it.id == (snapshot.pvtAlg ?: "AUTO")
    } ?: pvtAlgOptions[1]

    val sbasOptions = listOf(
        Um980SbasOption("DISABLE", stringResource(R.string.esp_um980_sbas_disable)),
        Um980SbasOption("AUTO", "AUTO"),
        Um980SbasOption("SDCM", "SDCM"),
        Um980SbasOption("EGNOS", "EGNOS"),
        Um980SbasOption("WAAS", "WAAS"),
    )
    val selectedSbas = sbasOptions.firstOrNull {
        it.id == (snapshot.sbasMode ?: "DISABLE")
    } ?: sbasOptions.first()

    val maskOptions = listOf(0, 5, 10, 15, 20).map { Um980MaskOption(it, "$it°") }
    val selectedMask = maskOptions.firstOrNull {
        it.deg == (snapshot.maskElevation ?: 5)
    } ?: maskOptions[1]

    val rtkReliabilityOptions = listOf(1, 2, 3, 4).map { Um980RtkReliabilityOption(it, it.toString()) }
    val selectedRtkReliability = rtkReliabilityOptions.firstOrNull {
        it.level == (snapshot.rtkReliability ?: 3)
    } ?: rtkReliabilityOptions[2]
    val selectedRtkAdrReliability = rtkReliabilityOptions.firstOrNull {
        it.level == (snapshot.rtkAdrReliability ?: snapshot.rtkReliability ?: 3)
    } ?: rtkReliabilityOptions[2]

    val rtkMmplOptions = listOf(
        Um980RtkMmplOption(0, stringResource(R.string.esp_um980_rtk_mmpl_normal)),
        Um980RtkMmplOption(1, stringResource(R.string.esp_um980_rtk_mmpl_stringent)),
    )
    val selectedRtkMmpl = rtkMmplOptions.firstOrNull {
        it.level == (snapshot.rtkMmpl ?: 0)
    } ?: rtkMmplOptions.first()

    val standaloneWaitOptions = listOf(3, 10, 30, 100).map {
        Um980StandaloneWaitOption(it, stringResource(R.string.esp_um980_standalone_wait_sec, it))
    }
    val selectedStandaloneWait = standaloneWaitOptions.firstOrNull {
        it.sec == (snapshot.standaloneWaitSec ?: 100)
    } ?: standaloneWaitOptions.last()

    val smoothHeightOptions = listOf(0, 5, 10, 20, 50).map { Um980SmoothHeightOption(it, it.toString()) }
    val selectedSmoothHeight = smoothHeightOptions.firstOrNull {
        it.epochs == (snapshot.smoothRtkHeight ?: 0)
    } ?: smoothHeightOptions.first()

    val smoothHeadingOptions = listOf(0, 5, 10, 20).map { Um980SmoothHeadingOption(it, it.toString()) }
    val selectedSmoothHeading = smoothHeadingOptions.firstOrNull {
        it.epochs == (snapshot.smoothHeading ?: 0)
    } ?: smoothHeadingOptions.first()

    val precisionPresetCmds = remember { Um980Commands.maxPrecisionPresetCommands() }
    val antispoofPresetCmds = remember { Um980Commands.maxAntispoofPresetCommands() }
    val precisionPreview = remember(precisionPresetCmds) {
        Um980Commands.presetPreviewLines(precisionPresetCmds)
    }
    val antispoofPreview = remember(antispoofPresetCmds) {
        Um980Commands.presetPreviewLines(antispoofPresetCmds)
    }
    val dgpsOptions = listOf(
        Um980DgpsOption(60, "60"),
        Um980DgpsOption(300, "300"),
        Um980DgpsOption(600, "600"),
    )
    val selectedDgps = dgpsOptions.firstOrNull { it.sec == (snapshot.dgpsTimeout ?: 600) }
        ?: dgpsOptions.last()

    val pppModeOptions = listOf(
        Um980PppModeOption("DISABLE", stringResource(R.string.esp_um980_ppp_disable)),
        Um980PppModeOption("AUTO", stringResource(R.string.esp_um980_ppp_auto)),
        Um980PppModeOption("B2b-PPP", "B2b-PPP"),
        Um980PppModeOption("E6-HAS", "E6-HAS"),
        Um980PppModeOption("L6MDCPPP", "L6MDCPPP"),
    )
    val selectedPppMode = pppModeOptions.firstOrNull { opt ->
        val mode = snapshot.pppMode ?: "DISABLE"
        opt.id.equals(mode, ignoreCase = true)
    } ?: pppModeOptions.first()
    val pppEnabled = selectedPppMode.id != "DISABLE"

    val pppTimeoutOptions = listOf(90, 120, 180).map { Um980PppTimeoutOption(it, it.toString()) }
    val selectedPppTimeout = pppTimeoutOptions.firstOrNull {
        it.sec == (snapshot.pppTimeout ?: 120)
    } ?: pppTimeoutOptions[1]

    val pppDatumOptions = listOf(
        Um980PppDatumOption("PPPORIGINAL", stringResource(R.string.esp_um980_ppp_datum_original)),
        Um980PppDatumOption("WGS84", "WGS84"),
    )
    val selectedPppDatum = pppDatumOptions.firstOrNull {
        it.id == (snapshot.pppDatum ?: "PPPORIGINAL")
    } ?: pppDatumOptions.first()

    Column(modifier = modifier.padding(horizontal = 8.dp)) {
        StatusRow(
            stringResource(R.string.esp_um980_version),
            snapshot.um980Version?.ifBlank { "—" } ?: "—",
        )
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
                onClick = rememberWrappedOnClick { sendCmd("RESET") },
                enabled = enabled,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.esp_um980_reset_hot), style = MaterialTheme.typography.tboxCaption)
            }
            OutlinedButton(
                onClick = rememberWrappedOnClick { sendCmd("RESET EPHEM") },
                enabled = enabled,
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
                    sendCmd("RESET EPHEM ALMANAC IONUTC POSITION")
                },
                enabled = enabled,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.esp_um980_reset_cold), style = MaterialTheme.typography.tboxCaption)
            }
            OutlinedButton(
                onClick = rememberWrappedOnClick { showFresetConfirm = true },
                enabled = enabled,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.esp_um980_freset), style = MaterialTheme.typography.tboxCaption)
            }
        }
        Button(
            onClick = rememberWrappedOnClick {
                refreshConfigCooldownUntilMs = System.currentTimeMillis() + 5_000L
                sendCmds(Um980Commands.refreshSnapshotCommands())
            },
            enabled = enabled && !refreshConfigOnCooldown,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text(stringResource(R.string.esp_um980_refresh_config), style = MaterialTheme.typography.tboxButton)
        }
        Um980ConfigWaitHint(visible = um980ConfigBusy)
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
                sendCmds(Um980Commands.ggaRmcCommands(opt.periodSec))
            },
            text = stringResource(R.string.esp_um980_coord_period),
            description = stringResource(R.string.esp_um980_coord_period_desc),
            enabled = enabled,
            options = nmeaRateOptions,
            selectorWidth = 300.dp,
        )
        SettingDropdownGeneric(
            selectedValue = nmeaRateOptions.first { it.periodSec == gsaPeriod },
            onValueChange = { opt ->
                gsaPeriod = opt.periodSec
                sendCmd("GPGSA ${Um980Commands.periodSecondsToNmeaRate(opt.periodSec)}")
            },
            text = stringResource(R.string.esp_um980_gsa),
            description = stringResource(R.string.esp_um980_gsa_desc),
            enabled = enabled,
            options = nmeaRateOptions,
            selectorWidth = 300.dp,
        )
        SettingDropdownGeneric(
            selectedValue = nmeaRateOptions.first { it.periodSec == gsvPeriod },
            onValueChange = { opt ->
                gsvPeriod = opt.periodSec
                sendCmd("GPGSV ${Um980Commands.periodSecondsToNmeaRate(opt.periodSec)}")
            },
            text = stringResource(R.string.esp_um980_gsv),
            description = stringResource(R.string.esp_um980_gsv_desc),
            enabled = enabled,
            options = nmeaRateOptions,
            selectorWidth = 300.dp,
        )
        SettingDropdownGeneric(
            selectedValue = nmeaRateOptions.first { it.periodSec == zdaPeriod },
            onValueChange = { opt ->
                zdaPeriod = opt.periodSec
                sendCmd("GPZDA ${Um980Commands.periodSecondsToNmeaRate(opt.periodSec)}")
            },
            text = stringResource(R.string.esp_um980_zda),
            description = stringResource(R.string.esp_um980_zda_desc),
            enabled = enabled,
            options = nmeaRateOptions,
            selectorWidth = 300.dp,
        )
        SettingDropdownGeneric(
            selectedValue = nmeaRateOptions.first { it.periodSec == vtgPeriod },
            onValueChange = { opt ->
                vtgPeriod = opt.periodSec
                sendCmd("GPVTG ${Um980Commands.periodSecondsToNmeaRate(opt.periodSec)}")
            },
            text = stringResource(R.string.esp_um980_vtg),
            description = stringResource(R.string.esp_um980_vtg_desc),
            enabled = enabled,
            options = nmeaRateOptions,
            selectorWidth = 300.dp,
        )
        SettingSwitch(
            isChecked = requestVtg,
            onCheckedChange = { saveRequestVtg(it) },
            text = stringResource(R.string.esp_um980_request_vtg_title),
            description = stringResource(R.string.esp_um980_request_vtg_desc),
            enabled = enabled,
        )
        SettingSwitch(
            isChecked = requestZda,
            onCheckedChange = { saveRequestZda(it) },
            text = stringResource(R.string.esp_um980_request_zda_title),
            description = stringResource(R.string.esp_um980_request_zda_desc),
            enabled = enabled,
        )
        SettingSwitch(
            isChecked = requestGst,
            onCheckedChange = { saveRequestGst(it) },
            text = stringResource(R.string.esp_um980_request_gst_title),
            description = stringResource(R.string.esp_um980_request_gst_desc),
            enabled = enabled,
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SettingsTitle(stringResource(R.string.esp_um980_settings_title))
        SettingDropdownGeneric(
            selectedValue = selectedDgps,
            onValueChange = { opt ->
                sendCmd("CONFIG DGPS TIMEOUT ${opt.sec}")
            },
            text = stringResource(R.string.esp_um980_dgps_timeout),
            description = stringResource(R.string.esp_um980_dgps_timeout_desc),
            enabled = enabled,
            options = dgpsOptions,
            selectorWidth = 300.dp,
        )
        SettingSwitch(
            isChecked = snapshot.rtkOff != true,
            onCheckedChange = { enabled ->
                if (enabled) {
                    sendCmd("CONFIG RTK TIMEOUT 600")
                } else {
                    sendCmd("CONFIG RTK TIMEOUT 0")
                }
            },
            text = stringResource(R.string.esp_um980_rtk),
            description = stringResource(R.string.esp_um980_rtk_desc),
            enabled = enabled,
        )
        SettingDropdownGeneric(
            selectedValue = selectedRtkReliability,
            onValueChange = { opt ->
                sendCmd(
                    "CONFIG RTK RELIABILITY ${opt.level} ${selectedRtkAdrReliability.level}",
                )
            },
            text = stringResource(R.string.esp_um980_rtk_reliability),
            description = stringResource(R.string.esp_um980_rtk_reliability_desc),
            enabled = enabled,
            options = rtkReliabilityOptions,
            selectorWidth = 300.dp,
        )
        SettingDropdownGeneric(
            selectedValue = selectedRtkAdrReliability,
            onValueChange = { opt ->
                sendCmd(
                    "CONFIG RTK RELIABILITY ${selectedRtkReliability.level} ${opt.level}",
                )
            },
            text = stringResource(R.string.esp_um980_rtk_adr_reliability),
            description = stringResource(R.string.esp_um980_rtk_adr_reliability_desc),
            enabled = enabled,
            options = rtkReliabilityOptions,
            selectorWidth = 300.dp,
        )
        SettingDropdownGeneric(
            selectedValue = selectedRtkMmpl,
            onValueChange = { opt ->
                sendCmd("CONFIG RTK MMPL ${opt.level}")
            },
            text = stringResource(R.string.esp_um980_rtk_mmpl),
            description = stringResource(R.string.esp_um980_rtk_mmpl_desc),
            enabled = enabled,
            options = rtkMmplOptions,
            selectorWidth = 300.dp,
        )
        SettingSwitch(
            isChecked = snapshot.standalone == true,
            onCheckedChange = { on ->
                sendCmd(
                    if (on) {
                        "CONFIG STANDALONE ENABLE ${selectedStandaloneWait.sec}"
                    } else {
                        "CONFIG STANDALONE DISABLE"
                    },
                )
            },
            text = stringResource(R.string.esp_um980_standalone),
            description = stringResource(R.string.esp_um980_standalone_desc),
            enabled = enabled,
        )
        SettingDropdownGeneric(
            selectedValue = selectedStandaloneWait,
            onValueChange = { opt ->
                if (snapshot.standalone == true) {
                    sendCmd("CONFIG STANDALONE ENABLE ${opt.sec}")
                }
            },
            text = stringResource(R.string.esp_um980_standalone_wait),
            description = stringResource(R.string.esp_um980_standalone_wait_desc),
            enabled = enabled && snapshot.standalone == true,
            options = standaloneWaitOptions,
            selectorWidth = 300.dp,
        )
        Button(
            onClick = rememberWrappedOnClick { sendCmd("CONFIG ALGRESET RTK1") },
            enabled = enabled,
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
            onClick = rememberWrappedOnClick { sendCmd("CONFIG ALGRESET ADR") },
            enabled = enabled,
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
                sendCmd(cmd)
            },
            text = stringResource(R.string.esp_um980_mode),
            description = stringResource(R.string.esp_um980_mode_desc),
            enabled = enabled,
            options = modeOptions,
            selectorWidth = 300.dp,
        )
        SettingDropdownGeneric(
            selectedValue = selectedMask,
            onValueChange = { opt ->
                sendCmd("MASK ${opt.deg}")
            },
            text = stringResource(R.string.esp_um980_mask),
            description = stringResource(R.string.esp_um980_mask_desc),
            enabled = enabled,
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
                sendCmd(cmd)
            },
            text = stringResource(R.string.esp_um980_sbas),
            description = stringResource(R.string.esp_um980_sbas_desc),
            enabled = enabled,
            options = sbasOptions,
            selectorWidth = 300.dp,
        )
        SettingDropdownGeneric(
            selectedValue = selectedPppMode,
            onValueChange = { opt ->
                sendCmd(
                    when (opt.id) {
                        "DISABLE" -> "CONFIG PPP DISABLE"
                        "AUTO" -> "CONFIG PPP ENABLE AUTO"
                        else -> "CONFIG PPP ENABLE ${opt.id}"
                    },
                )
            },
            text = stringResource(R.string.esp_um980_ppp),
            description = stringResource(R.string.esp_um980_ppp_desc),
            enabled = enabled,
            options = pppModeOptions,
            selectorWidth = 300.dp,
        )
        SettingDropdownGeneric(
            selectedValue = selectedPppTimeout,
            onValueChange = { opt ->
                sendCmd("CONFIG PPP TIMEOUT ${opt.sec}")
            },
            text = stringResource(R.string.esp_um980_ppp_timeout),
            description = stringResource(R.string.esp_um980_ppp_timeout_desc),
            enabled = enabled && pppEnabled,
            options = pppTimeoutOptions,
            selectorWidth = 300.dp,
        )
        SettingDropdownGeneric(
            selectedValue = selectedPppDatum,
            onValueChange = { opt ->
                sendCmd("CONFIG PPP DATUM ${opt.id}")
            },
            text = stringResource(R.string.esp_um980_ppp_datum),
            description = stringResource(R.string.esp_um980_ppp_datum_desc),
            enabled = enabled && pppEnabled,
            options = pppDatumOptions,
            selectorWidth = 300.dp,
        )
        Button(
            onClick = rememberWrappedOnClick { sendCmd("CONFIG ALGRESET PPP") },
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            Text(stringResource(R.string.esp_um980_algreset_ppp), style = MaterialTheme.typography.tboxButton)
        }
        Text(
            text = stringResource(R.string.esp_um980_algreset_ppp_desc),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        SettingSwitch(
            isChecked = snapshot.mmp == true,
            onCheckedChange = { enabled ->
                sendCmd(if (enabled) "CONFIG MMP ENABLE" else "CONFIG MMP DISABLE")
            },
            text = stringResource(R.string.esp_um980_mmp),
            description = stringResource(R.string.esp_um980_mmp_desc),
            enabled = enabled,
        )
        SettingSwitch(
            isChecked = snapshot.agnss == true,
            onCheckedChange = { enabled ->
                sendCmd(if (enabled) "CONFIG AGNSS ENABLE" else "CONFIG AGNSS DISABLE")
            },
            text = stringResource(R.string.esp_um980_agnss),
            description = stringResource(R.string.esp_um980_agnss_desc),
            enabled = enabled,
        )
        SettingDropdownGeneric(
            selectedValue = selectedAntijam,
            onValueChange = { opt ->
                sendCmd("CONFIG ANTIJAM ${opt.id}")
            },
            text = stringResource(R.string.esp_um980_antijam),
            description = stringResource(R.string.esp_um980_antijam_desc),
            enabled = enabled,
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
            enabled = enabled,
            options = signalGroupOptions,
            selectorWidth = 300.dp,
        )
        SettingDropdownGeneric(
            selectedValue = selectedPvtAlg,
            onValueChange = { opt ->
                sendCmd("CONFIG PVTALG ${opt.id}")
            },
            text = stringResource(R.string.esp_um980_pvtalg),
            description = stringResource(R.string.esp_um980_pvtalg_desc),
            enabled = enabled,
            options = pvtAlgOptions,
            selectorWidth = 300.dp,
        )
        SettingSwitch(
            isChecked = snapshot.smoothPsrVel == true,
            onCheckedChange = { enabled ->
                sendCmd(
                    if (enabled) "CONFIG SMOOTH PSRVEL ENABLE" else "CONFIG SMOOTH PSRVEL DISABLE",
                )
            },
            text = stringResource(R.string.esp_um980_smooth_psrvel),
            description = stringResource(R.string.esp_um980_smooth_psrvel_desc),
            enabled = enabled,
        )
        SettingDropdownGeneric(
            selectedValue = selectedSmoothHeading,
            onValueChange = { opt ->
                sendCmd(
                    if (opt.epochs <= 0) "CONFIG SMOOTH HEADING DISABLE"
                    else "CONFIG SMOOTH HEADING ${opt.epochs}",
                )
            },
            text = stringResource(R.string.esp_um980_smooth_heading),
            description = stringResource(R.string.esp_um980_smooth_heading_desc),
            enabled = enabled,
            options = smoothHeadingOptions,
            selectorWidth = 300.dp,
        )
        SettingDropdownGeneric(
            selectedValue = selectedSmoothHeight,
            onValueChange = { opt ->
                sendCmd("CONFIG SMOOTH RTKHEIGHT ${opt.epochs}")
            },
            text = stringResource(R.string.esp_um980_smooth_rtkheight),
            description = stringResource(R.string.esp_um980_smooth_rtkheight_desc),
            enabled = enabled,
            options = smoothHeightOptions,
            selectorWidth = 300.dp,
        )
        SettingSwitch(
            isChecked = snapshot.psrVelDrPos == true,
            onCheckedChange = { enabled ->
                sendCmd(
                    if (enabled) "CONFIG PSRVELDRPOS ENABLE" else "CONFIG PSRVELDRPOS DISABLE",
                )
            },
            text = stringResource(R.string.esp_um980_psrveldrpos),
            description = stringResource(R.string.esp_um980_psrveldrpos_desc),
            enabled = enabled,
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SettingsTitle(stringResource(R.string.esp_um980_presets_title))
        Text(
            text = stringResource(R.string.esp_um980_presets_desc),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Button(
            onClick = rememberWrappedOnClick {
                if (transport == Um980SettingsTransport.COMPANION) {
                    settingsViewModel.saveEspUm980RequestGstSetting(false)
                } else {
                    settingsViewModel.saveUsbGnssRequestGstSetting(false)
                }
                sendCmds(
                    precisionPresetCmds,
                    refreshAfter = true,
                    ensureSignalGroup = Um980Commands.PRESET_SIGNALGROUP,
                )
            },
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text(
                stringResource(R.string.esp_um980_preset_precision),
                style = MaterialTheme.typography.tboxButton,
            )
        }
        Um980ConfigWaitHint(visible = um980ConfigBusy)
        Text(
            text = stringResource(R.string.esp_um980_preset_precision_desc),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Um980PresetCommandsPreview(
            expanded = precisionCmdsExpanded,
            onToggle = { precisionCmdsExpanded = !precisionCmdsExpanded },
            lines = precisionPreview,
        )
        Button(
            onClick = rememberWrappedOnClick {
                if (transport == Um980SettingsTransport.COMPANION) {
                    settingsViewModel.saveEspUm980RequestGstSetting(false)
                } else {
                    settingsViewModel.saveUsbGnssRequestGstSetting(false)
                }
                sendCmds(
                    antispoofPresetCmds,
                    refreshAfter = true,
                    ensureSignalGroup = Um980Commands.PRESET_SIGNALGROUP,
                )
            },
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        ) {
            Text(
                stringResource(R.string.esp_um980_preset_antispoof),
                style = MaterialTheme.typography.tboxButton,
            )
        }
        Um980ConfigWaitHint(visible = um980ConfigBusy)
        Text(
            text = stringResource(R.string.esp_um980_preset_antispoof_desc),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Um980PresetCommandsPreview(
            expanded = antispoofCmdsExpanded,
            onToggle = { antispoofCmdsExpanded = !antispoofCmdsExpanded },
            lines = antispoofPreview,
        )
        OutlinedButton(
            onClick = rememberWrappedOnClick {
                sendCmds(listOf("SAVECONFIG"), refreshAfter = true)
            },
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        ) {
            Text(stringResource(R.string.esp_um980_saveconfig), style = MaterialTheme.typography.tboxButton)
        }
        Um980ConfigWaitHint(visible = um980ConfigBusy)
        Text(
            text = stringResource(R.string.esp_um980_saveconfig_desc),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )
    }

    pendingSignalGroup?.let { opt ->
        AlertDialog(
            onDismissRequest = { pendingSignalGroup = null },
            title = { AppAlertDialogTitle(stringResource(R.string.esp_um980_signalgroup_confirm_title)) },
            text = {
                AppAlertDialogText(
                    stringResource(R.string.esp_um980_signalgroup_confirm_message, opt.id),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = rememberWrappedOnClick {
                        pendingSignalGroup = null
                        sendCmd("CONFIG SIGNALGROUP ${opt.id}")
                    },
                ) {
                    AppAlertDialogButtonLabel(stringResource(R.string.esp_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = rememberWrappedOnClick { pendingSignalGroup = null }) {
                    AppAlertDialogButtonLabel(stringResource(R.string.action_cancel))
                }
            },
        )
    }
    if (showFresetConfirm) {
        AlertDialog(
            onDismissRequest = { showFresetConfirm = false },
            title = { AppAlertDialogTitle(stringResource(R.string.esp_um980_freset_confirm_title)) },
            text = {
                AppAlertDialogText(stringResource(R.string.esp_um980_freset_confirm_message))
            },
            confirmButton = {
                TextButton(
                    onClick = rememberWrappedOnClick {
                        showFresetConfirm = false
                        sendCmd("FRESET")
                    },
                ) {
                    AppAlertDialogButtonLabel(stringResource(R.string.esp_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = rememberWrappedOnClick { showFresetConfirm = false }) {
                    AppAlertDialogButtonLabel(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

internal fun Context.sendUm980TransportCmd(transport: Um980SettingsTransport, cmd: String) {
    when (transport) {
        Um980SettingsTransport.COMPANION -> startService(
            Intent(this, BackgroundService::class.java).apply {
                action = BackgroundService.ACTION_ESP_UM980_CMD
                putExtra(BackgroundService.EXTRA_ESP_UM980_CMD, cmd)
            },
        )
        Um980SettingsTransport.USB -> startService(
            Intent(this, BackgroundService::class.java).apply {
                action = BackgroundService.ACTION_USB_GNSS_UM980_CMD
                putExtra(BackgroundService.EXTRA_USB_GNSS_UM980_CMD, cmd)
            },
        )
    }
}

internal fun Context.sendUm980TransportCmds(
    transport: Um980SettingsTransport,
    cmds: List<String>,
    refreshAfter: Boolean = false,
    ensureSignalGroup: Int = 0,
) {
    when (transport) {
        Um980SettingsTransport.COMPANION -> startService(
            Intent(this, BackgroundService::class.java).apply {
                action = BackgroundService.ACTION_ESP_UM980_CMD
                putStringArrayListExtra(BackgroundService.EXTRA_ESP_UM980_CMDS, ArrayList(cmds))
                putExtra(BackgroundService.EXTRA_ESP_UM980_REFRESH_AFTER, refreshAfter)
                putExtra(BackgroundService.EXTRA_ESP_UM980_ENSURE_SIGNALGROUP, ensureSignalGroup)
            },
        )
        Um980SettingsTransport.USB -> startService(
            Intent(this, BackgroundService::class.java).apply {
                action = BackgroundService.ACTION_USB_GNSS_UM980_CMD
                putStringArrayListExtra(BackgroundService.EXTRA_USB_GNSS_UM980_CMDS, ArrayList(cmds))
                putExtra(BackgroundService.EXTRA_USB_GNSS_UM980_REFRESH_AFTER, refreshAfter)
                putExtra(BackgroundService.EXTRA_USB_GNSS_UM980_ENSURE_SIGNALGROUP, ensureSignalGroup)
            },
        )
    }
}

@Composable
private fun Um980PresetCommandsPreview(
    expanded: Boolean,
    onToggle: () -> Unit,
    lines: List<String>,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    if (expanded) R.string.esp_um980_preset_commands_hide
                    else R.string.esp_um980_preset_commands_show,
                ),
                style = MaterialTheme.typography.tboxCaption,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
        if (expanded) {
            Text(
                text = lines.joinToString("\n"),
                style = MaterialTheme.typography.tboxCaption.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun Um980ConfigWaitHint(visible: Boolean) {
    if (!visible) return
    Text(
        text = stringResource(R.string.esp_um980_config_busy),
        style = MaterialTheme.typography.tboxBody,
        color = MaterialTheme.colorScheme.error,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp),
    )
}

private data class Um980NmeaRateOption(val periodSec: Double, val label: String) {
    override fun toString(): String = label
}
private data class Um980ModeOption(val id: String, val label: String) {
    override fun toString(): String = label
}
private data class Um980SignalGroupOption(val id: Int, val label: String) {
    override fun toString(): String = label
}
private data class Um980DgpsOption(val sec: Int, val label: String) {
    override fun toString(): String = label
}
private data class Um980AntijamOption(val id: String, val label: String) {
    override fun toString(): String = label
}
private data class Um980PvtAlgOption(val id: String, val label: String) {
    override fun toString(): String = label
}
private data class Um980SbasOption(val id: String, val label: String) {
    override fun toString(): String = label
}
private data class Um980PppModeOption(val id: String, val label: String) {
    override fun toString(): String = label
}
private data class Um980PppTimeoutOption(val sec: Int, val label: String) {
    override fun toString(): String = label
}
private data class Um980PppDatumOption(val id: String, val label: String) {
    override fun toString(): String = label
}
private data class Um980MaskOption(val deg: Int, val label: String) {
    override fun toString(): String = label
}
private data class Um980RtkReliabilityOption(val level: Int, val label: String) {
    override fun toString(): String = label
}
private data class Um980RtkMmplOption(val level: Int, val label: String) {
    override fun toString(): String = label
}
private data class Um980StandaloneWaitOption(val sec: Int, val label: String) {
    override fun toString(): String = label
}
private data class Um980SmoothHeightOption(val epochs: Int, val label: String) {
    override fun toString(): String = label
}
private data class Um980SmoothHeadingOption(val epochs: Int, val label: String) {
    override fun toString(): String = label
}
