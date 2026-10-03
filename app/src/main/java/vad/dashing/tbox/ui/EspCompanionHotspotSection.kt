package vad.dashing.tbox.ui

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.BackgroundService
import vad.dashing.tbox.HeadUnitCanMode
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.esp.EspApStatus
import vad.dashing.tbox.esp.EspCompanionRepository
import vad.dashing.tbox.hotspot.HuSoftApCodec
import vad.dashing.tbox.hotspot.huSoftApQrImage
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
    val status by EspCompanionRepository.apStatus.collectAsStateWithLifecycle()
    val busy by EspCompanionRepository.routerBusy.collectAsStateWithLifecycle()
    val error by EspCompanionRepository.routerError.collectAsStateWithLifecycle()
    val android9 = mode == HeadUnitCanMode.Android9MbCan
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    var showPassword by remember { mutableStateOf(false) }

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
    if (status.ssid.isNotBlank()) {
        CompanionApDetails(status = status, showPassword = showPassword, onTogglePassword = {
            showPassword = !showPassword
        })
    }
}

@Composable
private fun CompanionApDetails(
    status: EspApStatus,
    showPassword: Boolean,
    onTogglePassword: () -> Unit,
) {
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = stringResource(R.string.settings_api_hotspot_ssid, status.ssid),
        style = MaterialTheme.typography.tboxBody,
        color = onSurfaceVariant,
    )
    val passwordText = when {
        status.password.isEmpty() -> stringResource(R.string.settings_api_hotspot_password_empty)
        showPassword -> stringResource(R.string.settings_api_hotspot_password, status.password)
        else -> stringResource(R.string.settings_api_hotspot_password, "••••••••")
    }
    Text(
        text = passwordText,
        style = MaterialTheme.typography.tboxBody,
        color = onSurfaceVariant,
    )
    if (status.password.isNotEmpty()) {
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
            text = stringResource(R.string.esp_hotspot_mdns),
            style = MaterialTheme.typography.tboxBody,
            color = onSurfaceVariant,
        )
    }
    val payload = remember(status.ssid, status.password) {
        HuSoftApCodec.wifiQrPayload(status.ssid, status.password, 4)
    }
    val sizePx = with(LocalDensity.current) { 220.dp.roundToPx() }
    val image = remember(payload, sizePx) { huSoftApQrImage(payload, sizePx) }
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Image(
            bitmap = image,
            contentDescription = stringResource(R.string.settings_api_hotspot_qr),
            modifier = Modifier.size(220.dp),
        )
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
