package vad.dashing.tbox.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import vad.dashing.tbox.BackgroundService
import vad.dashing.tbox.R
import vad.dashing.tbox.esp.EspCompanionProtocol
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxButton
import vad.dashing.tbox.ui.theme.tboxCaption

/** Key for the remote that is about to be learned. Blank means a plain remote. */
@Composable
internal fun EspBleLearnKeyField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
) {
    val valid = EspCompanionProtocol.normalizeBleKey(value) != null
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.esp_ble_learn_key)) },
        supportingText = {
            Text(
                stringResource(if (valid) R.string.esp_ble_key_hint else R.string.esp_ble_key_invalid),
                style = MaterialTheme.typography.tboxCaption,
            )
        },
        isError = !valid,
        singleLine = true,
        enabled = enabled,
        textStyle = MaterialTheme.typography.tboxBody.copy(fontFamily = FontFamily.Monospace),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Set or remove the BTHome key of a learned remote. The key is sent to the companion and not kept here. */
@Composable
internal fun EspBleRemoteKeyEditor(
    mac: String,
    keyed: Boolean,
    enabled: Boolean,
) {
    val context = LocalContext.current
    var draft by remember(mac, keyed) { mutableStateOf("") }
    val normalized = EspCompanionProtocol.normalizeBleKey(draft)
    fun send(key: String) {
        context.startService(
            Intent(context, BackgroundService::class.java).apply {
                action = BackgroundService.ACTION_ESP_BLE_KEY_SET
                putExtra(BackgroundService.EXTRA_ESP_BLE_MAC, mac)
                putExtra(BackgroundService.EXTRA_ESP_BLE_KEY, key)
            },
        )
        draft = ""
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            label = {
                Text(stringResource(if (keyed) R.string.esp_ble_key_replace else R.string.esp_ble_key))
            },
            isError = normalized == null,
            supportingText = if (normalized == null) {
                {
                    Text(
                        stringResource(R.string.esp_ble_key_invalid),
                        style = MaterialTheme.typography.tboxCaption,
                    )
                }
            } else {
                null
            },
            singleLine = true,
            enabled = enabled,
            textStyle = MaterialTheme.typography.tboxBody.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(if (keyed) R.string.esp_ble_encrypted_on else R.string.esp_ble_encrypted_off),
                style = MaterialTheme.typography.tboxCaption,
                color = if (keyed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = rememberWrappedOnClick { normalized?.let(::send) },
                enabled = enabled && !normalized.isNullOrEmpty(),
            ) {
                Text(stringResource(R.string.esp_ble_key_save), style = MaterialTheme.typography.tboxButton)
            }
            if (keyed) {
                TextButton(
                    onClick = rememberWrappedOnClick { send("") },
                    enabled = enabled,
                ) {
                    Text(stringResource(R.string.esp_ble_key_remove), style = MaterialTheme.typography.tboxButton)
                }
            }
        }
    }
}
