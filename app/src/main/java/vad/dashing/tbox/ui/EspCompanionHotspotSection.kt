package vad.dashing.tbox.ui

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.BackgroundService
import vad.dashing.tbox.HeadUnitCanMode
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.esp.EspApStatus
import vad.dashing.tbox.esp.EspCompanionRepository
import vad.dashing.tbox.hotspot.EspSoftApIdentity
import vad.dashing.tbox.hotspot.HuSoftApCodec
import vad.dashing.tbox.hotspot.rememberHuSoftApQrImage
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxButton
import vad.dashing.tbox.ui.theme.tboxTitle

@Composable
fun EspCompanionHotspotSection(
    settingsViewModel: SettingsViewModel,
    companionEnabled: Boolean,
    companionConnected: Boolean,
    firmwareSupportsAp: Boolean,
) {
    val context = LocalContext.current
    val mode by settingsViewModel.headUnitCanMode.collectAsStateWithLifecycle()
    val enabled by settingsViewModel.espSoftApRouterEnabled.collectAsStateWithLifecycle()
    val savedSsid by settingsViewModel.espSoftApSsid.collectAsStateWithLifecycle()
    val savedPsk by settingsViewModel.espSoftApPsk.collectAsStateWithLifecycle()
    val status by EspCompanionRepository.apStatus.collectAsStateWithLifecycle()
    val busy by EspCompanionRepository.routerBusy.collectAsStateWithLifecycle()
    val error by EspCompanionRepository.routerError.collectAsStateWithLifecycle()
    val android9 = mode == HeadUnitCanMode.Android9MbCan
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    var showPassword by remember { mutableStateOf(false) }
    var ssidDraft by remember { mutableStateOf("") }
    var pskDraft by remember { mutableStateOf("") }
    var seeded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        settingsViewModel.ensureEspSoftApIdentity()
    }
    LaunchedEffect(savedSsid, savedPsk) {
        if (!seeded && savedSsid.isNotEmpty() && savedPsk.isNotEmpty()) {
            if (ssidDraft.isEmpty() && pskDraft.isEmpty()) {
                ssidDraft = savedSsid
                pskDraft = savedPsk
            }
            seeded = true
        }
    }

    Text(
        text = stringResource(R.string.esp_hotspot_title),
        style = MaterialTheme.typography.tboxTitle,
        color = MaterialTheme.colorScheme.onSurface,
    )
    if (!android9) {
        Text(
            text = stringResource(R.string.esp_hotspot_a9_only),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.error,
        )
        return
    }
    SettingSwitch(
        isChecked = enabled,
        onCheckedChange = { want ->
            settingsViewModel.saveEspSoftApRouterEnabled(want)
            context.startService(
                Intent(context, BackgroundService::class.java).apply {
                    action = BackgroundService.ACTION_ESP_SOFTAP_ROUTER
                    putExtra(BackgroundService.EXTRA_ESP_SOFTAP_ROUTER, want)
                },
            )
        },
        text = stringResource(R.string.esp_hotspot_switch),
        description = stringResource(R.string.esp_hotspot_switch_desc),
        enabled = !busy,
    )
    if (!companionEnabled) {
        Text(
            text = stringResource(R.string.esp_hotspot_need_companion),
            style = MaterialTheme.typography.tboxBody,
            color = onSurfaceVariant,
        )
    } else if (companionConnected && !firmwareSupportsAp) {
        Text(
            text = stringResource(R.string.esp_hotspot_need_fw),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.error,
        )
    }
    if (busy) {
        Text(
            text = stringResource(R.string.esp_hotspot_busy),
            style = MaterialTheme.typography.tboxBody,
            color = onSurfaceVariant,
        )
    }
    val errorText = routerErrorText(error)
    if (errorText != null) {
        Text(
            text = errorText,
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.error,
        )
    }
    CompanionApIdentity(
        ssid = ssidDraft,
        password = pskDraft,
        showPassword = showPassword,
        onSsid = { ssidDraft = it.take(32) },
        onPassword = { pskDraft = it.take(63) },
        onTogglePassword = { showPassword = !showPassword },
        savedSsid = savedSsid,
        savedPassword = savedPsk,
        onSave = {
            val cleanSsid = ssidDraft.trim()
            ssidDraft = cleanSsid
            settingsViewModel.saveEspSoftApIdentity(cleanSsid, pskDraft) {
                context.startService(
                    Intent(context, BackgroundService::class.java).apply {
                        action = BackgroundService.ACTION_ESP_SOFTAP_IDENTITY
                    },
                )
            }
        },
    )
    if (status.on && savedSsid.isNotEmpty()) {
        CompanionApDetails(status = status, ssid = savedSsid, password = savedPsk)
    }
}

@Composable
private fun CompanionApIdentity(
    ssid: String,
    password: String,
    showPassword: Boolean,
    savedSsid: String,
    savedPassword: String,
    onSsid: (String) -> Unit,
    onPassword: (String) -> Unit,
    onTogglePassword: () -> Unit,
    onSave: () -> Unit,
) {
    OutlinedTextField(
        value = ssid,
        onValueChange = onSsid,
        label = { Text(stringResource(R.string.esp_hotspot_ssid)) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    )
    OutlinedTextField(
        value = password,
        onValueChange = onPassword,
        label = { Text(stringResource(R.string.esp_hotspot_password)) },
        singleLine = true,
        visualTransformation = if (showPassword) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    )
    if (password.isNotEmpty()) {
        TextButton(onClick = rememberWrappedOnClick(onTogglePassword)) {
            Text(
                text = stringResource(
                    if (showPassword) {
                        R.string.settings_api_hotspot_hide_password
                    } else {
                        R.string.settings_api_hotspot_show_password
                    },
                ),
                style = MaterialTheme.typography.tboxButton,
            )
        }
    }
    val cleanSsid = ssid.trim()
    val valid = EspSoftApIdentity.isValidSsid(cleanSsid) && EspSoftApIdentity.isValidPsk(password)
    val dirty = cleanSsid != savedSsid || password != savedPassword
    if (ssid.isNotEmpty() && password.isNotEmpty() && !valid) {
        Text(
            text = stringResource(R.string.esp_hotspot_invalid),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.error,
        )
    }
    Button(
        onClick = rememberWrappedOnClick(onSave),
        enabled = valid && dirty,
        modifier = Modifier.padding(top = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.esp_hotspot_save),
            style = MaterialTheme.typography.tboxButton,
        )
    }
}

@Composable
private fun CompanionApDetails(
    status: EspApStatus,
    ssid: String,
    password: String,
) {
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val frequency = if (status.freqMhz > 0) {
        stringResource(R.string.settings_api_hotspot_frequency, status.freqMhz, status.channel)
    } else {
        stringResource(R.string.settings_api_hotspot_frequency_unknown)
    }
    Text(
        text = frequency,
        style = MaterialTheme.typography.tboxBody,
        color = onSurfaceVariant,
    )
    Text(
        text = stringResource(
            R.string.esp_hotspot_sta,
            stringResource(
                if (status.sta) R.string.esp_hotspot_sta_on else R.string.esp_hotspot_sta_off,
            ),
        ),
        style = MaterialTheme.typography.tboxBody,
        color = onSurfaceVariant,
    )
    if (status.ip.isNotBlank()) {
        Text(
            text = stringResource(R.string.esp_hotspot_panel, status.ip, status.panelPort),
            style = MaterialTheme.typography.tboxBody,
            color = onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.esp_hotspot_mdns, status.panelPort),
            style = MaterialTheme.typography.tboxBody,
            color = onSurfaceVariant,
        )
    }
    val payload = remember(ssid, password) {
        HuSoftApCodec.wifiQrPayload(ssid, password, 4)
    }
    val sizePx = with(LocalDensity.current) { 220.dp.roundToPx() }
    val image = rememberHuSoftApQrImage(payload, sizePx).value
    Column(modifier = Modifier.padding(top = 8.dp)) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = stringResource(R.string.settings_api_hotspot_qr),
                modifier = Modifier.size(220.dp),
            )
        } else {
            Spacer(modifier = Modifier.size(220.dp))
        }
    }
}

@Composable
private fun routerErrorText(code: String?): String? {
    if (code.isNullOrBlank()) return null
    return when (code) {
        "a9" -> stringResource(R.string.esp_hotspot_a9_only)
        "band" -> stringResource(R.string.esp_hotspot_band)
        "denied" -> stringResource(R.string.settings_api_hotspot_error_denied)
        "TcpEnableFailed", "TcpNotReady", "AdbConnectFailed" ->
            stringResource(R.string.settings_api_hotspot_error_adb)
        else -> stringResource(R.string.settings_api_hotspot_error_failed)
    }
}
