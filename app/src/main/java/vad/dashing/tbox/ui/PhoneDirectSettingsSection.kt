package vad.dashing.tbox.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.phoneble.PhoneDirectRepository
import vad.dashing.tbox.phoneble.PhoneDirectState
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxButton
import vad.dashing.tbox.ui.theme.tboxCaption

@Composable
fun PhoneDirectSettingsSection(settingsViewModel: SettingsViewModel) {
    val enabled by settingsViewModel.phoneDirectEnabled.collectAsStateWithLifecycle()
    val state by PhoneDirectRepository.state.collectAsStateWithLifecycle()
    val phones by PhoneDirectRepository.phones.collectAsStateWithLifecycle()
    val learnUntil by PhoneDirectRepository.learnUntil.collectAsStateWithLifecycle()
    val pending by PhoneDirectRepository.pending.collectAsStateWithLifecycle()
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(learnUntil) {
        while (learnUntil != 0L) {
            now = SystemClock.elapsedRealtime()
            delay(1_000)
        }
    }

    SettingsTitle(stringResource(R.string.phone_direct_title))
    SettingSwitch(
        enabled,
        { value -> settingsViewModel.savePhoneDirectEnabled(value) },
        stringResource(R.string.phone_direct_enable_title),
        stringResource(R.string.phone_direct_enable_desc),
        true,
    )
    if (!enabled) return

    Text(
        text = stringResource(
            when (state) {
                PhoneDirectState.OFF -> R.string.phone_direct_state_off
                PhoneDirectState.LISTENING -> R.string.phone_direct_state_listening
                PhoneDirectState.BLUETOOTH_OFF -> R.string.phone_direct_state_bt_off
                PhoneDirectState.ERROR -> R.string.phone_direct_state_error
            },
        ),
        style = MaterialTheme.typography.tboxBody,
        color = if (state == PhoneDirectState.ERROR) MaterialTheme.colorScheme.error else onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )

    val learning = learnUntil != 0L
    if (learning) {
        val secondsLeft = ((learnUntil - now) / 1000L).coerceAtLeast(0L).toInt()
        Text(
            text = stringResource(R.string.phone_direct_pairing, secondsLeft),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.phone_direct_pair_hint),
            style = MaterialTheme.typography.tboxBody,
            color = onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
    OutlinedButton(
        onClick = rememberWrappedOnClick {
            if (learning) PhoneDirectRepository.endLearn() else PhoneDirectRepository.beginLearn()
        },
        enabled = state != PhoneDirectState.OFF,
        modifier = Modifier.padding(bottom = 8.dp),
    ) {
        Text(
            text = stringResource(if (learning) R.string.phone_direct_pair_cancel else R.string.phone_direct_pair),
            style = MaterialTheme.typography.tboxButton,
        )
    }

    if (phones.isEmpty()) {
        Text(
            text = stringResource(R.string.phone_direct_empty),
            style = MaterialTheme.typography.tboxBody,
            color = onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
    phones.forEach { phone ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (phone.connected) {
                        "${phone.name} · ${stringResource(R.string.phone_direct_connected)}"
                    } else {
                        phone.name
                    },
                    style = MaterialTheme.typography.tboxBody,
                    color = if (phone.connected) MaterialTheme.colorScheme.primary else onSurface,
                )
                Text(
                    text = phone.id,
                    style = MaterialTheme.typography.tboxCaption,
                    fontFamily = FontFamily.Monospace,
                    color = onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = rememberWrappedOnClick { PhoneDirectRepository.forget(phone.id) }) {
                Text(
                    text = stringResource(R.string.phone_direct_forget),
                    style = MaterialTheme.typography.tboxButton,
                )
            }
        }
    }

    pending?.let { request ->
        AlertDialog(
            onDismissRequest = { PhoneDirectRepository.deny(request.id) },
            title = { AppAlertDialogTitle(stringResource(R.string.esp_phone_allow_title, request.name)) },
            confirmButton = {
                TextButton(onClick = rememberWrappedOnClick { PhoneDirectRepository.allow(request.id) }) {
                    Text(stringResource(R.string.esp_phone_allow_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = rememberWrappedOnClick { PhoneDirectRepository.deny(request.id) }) {
                    Text(stringResource(R.string.esp_phone_allow_no))
                }
            },
        )
    }
}
