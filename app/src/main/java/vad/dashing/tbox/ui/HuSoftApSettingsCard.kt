package vad.dashing.tbox.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import vad.dashing.tbox.R
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxButton
import vad.dashing.tbox.hotspot.HuSoftApClient
import vad.dashing.tbox.hotspot.HuSoftApCodec
import vad.dashing.tbox.hotspot.HuSoftApRead
import vad.dashing.tbox.hotspot.HuSoftApSnapshot
import vad.dashing.tbox.hotspot.rememberHuSoftApQrImage

@Composable
fun HuSoftApSettingsCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<HuSoftApSnapshot?>(null) }
    var radio by remember { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    LaunchedEffect(Unit) {
        snapshot = HuSoftApClient.refresh(context)
        radio = snapshot?.radioEnabled
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2_000)
            if (busy) continue
            val now = HuSoftApClient.radioEnabled(context)
            val changed = now != null && now != radio
            if (!changed) continue
            val next = HuSoftApClient.refresh(context)
            if (busy) continue
            snapshot = next
            radio = next.radioEnabled ?: now
        }
    }

    SettingsTitle(stringResource(R.string.settings_api_hotspot_title))
    val checked = radio ?: snapshot?.read?.enabled ?: false
    SettingSwitch(
        isChecked = checked,
        onCheckedChange = { want ->
            if (busy) return@SettingSwitch
            scope.launch {
                busy = true
                radio = want
                val next = HuSoftApClient.setEnabled(context, want)
                snapshot = next
                radio = next.radioEnabled ?: HuSoftApClient.radioEnabled(context) ?: want
                busy = false
            }
        },
        text = stringResource(R.string.settings_api_hotspot_switch),
        description = stringResource(R.string.settings_api_hotspot_switch_desc),
        enabled = !busy && (radio != null || snapshot?.read != null),
    )

    val read = snapshot?.read
    if (snapshot == null) {
        Text(
            text = stringResource(R.string.settings_api_hotspot_loading),
            style = MaterialTheme.typography.tboxBody,
            color = onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
    if (read != null) {
        Text(
            text = stringResource(R.string.settings_api_hotspot_ssid, read.ssid.ifBlank { "—" }),
            style = MaterialTheme.typography.tboxBody,
            color = onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        val passwordText = when {
            read.password.isEmpty() -> stringResource(R.string.settings_api_hotspot_password_empty)
            showPassword -> stringResource(R.string.settings_api_hotspot_password, read.password)
            else -> stringResource(R.string.settings_api_hotspot_password, "••••••••")
        }
        Text(
            text = passwordText,
            style = MaterialTheme.typography.tboxBody,
            color = onSurfaceVariant,
        )
        if (read.password.isNotEmpty()) {
            TextButton(
                onClick = rememberWrappedOnClick { showPassword = !showPassword },
            ) {
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
        Text(
            text = frequencyText(read),
            style = MaterialTheme.typography.tboxBody,
            color = onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        val ipv4 = snapshot?.ipv4
        Text(
            text = if (ipv4.isNullOrBlank()) {
                stringResource(R.string.settings_api_hotspot_ip_missing)
            } else {
                stringResource(R.string.settings_api_hotspot_ip, ipv4)
            },
            style = MaterialTheme.typography.tboxBody,
            color = onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        if (read.ssid.isNotBlank()) {
            val payload = remember(read.ssid, read.password, read.authType) {
                HuSoftApCodec.wifiQrPayload(read.ssid, read.password, read.authType)
            }
            val sizePx = with(LocalDensity.current) { 220.dp.roundToPx() }
            val image = rememberHuSoftApQrImage(payload, sizePx).value
            Column(modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)) {
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
    }
    val error = hotspotErrorText(snapshot)
    if (error != null) {
        Text(
            text = error,
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
}

@Composable
private fun frequencyText(read: HuSoftApRead): String {
    val mhz = read.liveMhz.takeIf { it > 0 } ?: read.frequencyMhz
    val channel = if (read.liveMhz > 0) {
        HuSoftApCodec.channelOfMhz(read.liveMhz) ?: read.channel
    } else {
        read.channel
    }
    if (mhz != null && channel > 0) {
        return stringResource(R.string.settings_api_hotspot_frequency, mhz, channel)
    }
    val band = when (read.band) {
        0 -> stringResource(R.string.settings_api_hotspot_band_24)
        1 -> stringResource(R.string.settings_api_hotspot_band_5)
        else -> null
    }
    return if (band != null) {
        stringResource(R.string.settings_api_hotspot_frequency_band, band)
    } else {
        stringResource(R.string.settings_api_hotspot_frequency_unknown)
    }
}

@Composable
private fun hotspotErrorText(snapshot: HuSoftApSnapshot?): String? {
    if (snapshot == null || snapshot.errorCode == null) return null
    return when {
        snapshot.adbFailed -> stringResource(R.string.settings_api_hotspot_error_adb)
        snapshot.errorCode == "denied" -> stringResource(R.string.settings_api_hotspot_error_denied)
        else -> stringResource(R.string.settings_api_hotspot_error_failed)
    }
}
