package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import vad.dashing.tbox.cacerts.HuCaCertsClient
import vad.dashing.tbox.cacerts.HuCaCertsStatus
import vad.dashing.tbox.cacerts.HuCaInstallOutcome
import vad.dashing.tbox.cacerts.HuCaInstallResult
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxButton

@Composable
fun HuCaCertsSettingsCard(
    autoInstall: Boolean,
    onAutoInstallChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<HuCaCertsStatus?>(null) }
    var result by remember { mutableStateOf<HuCaInstallResult?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            if (!busy) status = HuCaCertsClient.status()
            delay(3_000)
        }
    }

    SettingsTitle(stringResource(R.string.settings_ca_certs_title))
    Text(
        text = stringResource(R.string.settings_ca_certs_desc),
        style = MaterialTheme.typography.tboxBody,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
    status?.let { s ->
        Text(
            text = if (s.installed == s.total) {
                stringResource(R.string.settings_ca_certs_status_installed)
            } else {
                stringResource(R.string.settings_ca_certs_status_missing, s.installed, s.total)
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
                result = HuCaCertsClient.install(context)
                status = HuCaCertsClient.status()
                busy = false
            }
        },
        enabled = !busy,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp),
    ) {
        Text(
            text = stringResource(
                if (busy) R.string.settings_ca_certs_installing else R.string.settings_ca_certs_install,
            ),
            style = MaterialTheme.typography.tboxButton,
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
    }
    val message = result?.let { caInstallResultText(it) }
    if (message != null) {
        val isError = result !is HuCaInstallResult.Done ||
            (result as HuCaInstallResult.Done).outcome is HuCaInstallOutcome.Failed
        Text(
            text = message,
            style = MaterialTheme.typography.tboxBody,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
    SettingSwitch(
        autoInstall,
        onAutoInstallChange,
        stringResource(R.string.settings_ca_certs_auto_title),
        stringResource(R.string.settings_ca_certs_auto_desc),
        true,
    )
}

@Composable
private fun caInstallResultText(result: HuCaInstallResult): String = when (result) {
    is HuCaInstallResult.AdbFailed -> stringResource(R.string.settings_ca_certs_error_adb, result.reason)
    HuCaInstallResult.PrepareFailed -> stringResource(R.string.settings_ca_certs_error_failed, "prepare")
    is HuCaInstallResult.Done -> when (val outcome = result.outcome) {
        is HuCaInstallOutcome.Installed -> stringResource(R.string.settings_ca_certs_done)
        is HuCaInstallOutcome.Failed -> if (outcome.code == "nosu") {
            stringResource(R.string.settings_ca_certs_error_nosu)
        } else {
            stringResource(R.string.settings_ca_certs_error_failed, outcome.code)
        }
    }
}
