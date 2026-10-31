package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import vad.dashing.tbox.R
import vad.dashing.tbox.huaudio.HuUsbAudioOutcome
import vad.dashing.tbox.huaudio.HuUsbAudioPolicyClient
import vad.dashing.tbox.huaudio.HuUsbAudioPolicyStatus
import vad.dashing.tbox.huaudio.HuUsbAudioResult
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxButton

@Composable
fun HuUsbAudioSettingsCard(
    autoApply: Boolean,
    onAutoApplyChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<HuUsbAudioPolicyStatus?>(null) }
    var result by remember { mutableStateOf<HuUsbAudioResult?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            if (!busy) status = HuUsbAudioPolicyClient.status()
            delay(3_000)
        }
    }

    SettingsTitle(stringResource(R.string.settings_usb_audio_title))
    Text(
        text = stringResource(R.string.settings_usb_audio_desc),
        style = MaterialTheme.typography.tboxBody,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
    status?.let { s ->
        Text(
            text = when {
                !s.known -> stringResource(R.string.settings_usb_audio_status_unknown)
                s.appliedPermanently -> stringResource(R.string.settings_usb_audio_status_permanent)
                s.appliedTemporarily -> stringResource(R.string.settings_usb_audio_status_temporary)
                else -> stringResource(R.string.settings_usb_audio_status_off)
            },
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
    Button(
        onClick = rememberWrappedOnClick {
            if (busy) return@rememberWrappedOnClick
            scope.launch {
                busy = true
                result = HuUsbAudioPolicyClient.install(context)
                status = HuUsbAudioPolicyClient.status()
                busy = false
            }
        },
        enabled = !busy,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    ) {
        Text(
            text = stringResource(
                if (busy) R.string.settings_usb_audio_installing else R.string.settings_usb_audio_install,
            ),
            style = MaterialTheme.typography.tboxButton,
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
    }
    OutlinedButton(
        onClick = rememberWrappedOnClick {
            if (busy) return@rememberWrappedOnClick
            scope.launch {
                busy = true
                result = HuUsbAudioPolicyClient.restore(context)
                status = HuUsbAudioPolicyClient.status()
                busy = false
            }
        },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.settings_usb_audio_restore),
            style = MaterialTheme.typography.tboxButton,
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
    }
    SettingSwitch(
        autoApply,
        onAutoApplyChange,
        stringResource(R.string.settings_usb_audio_auto_title),
        stringResource(R.string.settings_usb_audio_auto_desc),
        true,
    )
    val message = result?.let { usbAudioResultText(it) }
    if (message != null) {
        val isError = result !is HuUsbAudioResult.Done ||
            (result as HuUsbAudioResult.Done).outcome is HuUsbAudioOutcome.Failed
        Text(
            text = message,
            style = MaterialTheme.typography.tboxBody,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
}

@Composable
private fun usbAudioResultText(result: HuUsbAudioResult): String = when (result) {
    is HuUsbAudioResult.AdbFailed -> stringResource(R.string.settings_usb_audio_error_adb, result.reason)
    HuUsbAudioResult.PrepareFailed -> stringResource(R.string.settings_usb_audio_error_failed, "prepare")
    is HuUsbAudioResult.BuildFailed -> when (result.code) {
        "nosource" -> stringResource(R.string.settings_usb_audio_error_nosource)
        "layout" -> stringResource(R.string.settings_usb_audio_error_layout)
        else -> stringResource(R.string.settings_usb_audio_error_failed, result.code)
    }
    is HuUsbAudioResult.Done -> when (val outcome = result.outcome) {
        HuUsbAudioOutcome.Applied -> stringResource(R.string.settings_usb_audio_done)
        HuUsbAudioOutcome.Restored -> stringResource(R.string.settings_usb_audio_restored_done)
        is HuUsbAudioOutcome.Failed -> if (outcome.code == "nosu") {
            stringResource(R.string.settings_usb_audio_error_nosu)
        } else {
            stringResource(R.string.settings_usb_audio_error_failed, outcome.code)
        }
    }
}
