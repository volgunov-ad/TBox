package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import kotlinx.coroutines.launch
import vad.dashing.tbox.R
import vad.dashing.tbox.hufirewall.HuAppNetworkClient
import vad.dashing.tbox.hufirewall.HuAppNetworkOutcome
import vad.dashing.tbox.hufirewall.HuAppNetworkResult
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxButton

@Composable
fun HuAppNetworkSettingsCard(
    autoOpen: Boolean,
    onAutoOpenChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<HuAppNetworkResult?>(null) }
    var busy by remember { mutableStateOf(false) }

    SettingsTitle(stringResource(R.string.settings_hu_app_network_title))
    Text(
        text = stringResource(R.string.settings_hu_app_network_desc),
        style = MaterialTheme.typography.tboxBody,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
    Button(
        onClick = rememberWrappedOnClick {
            if (busy) return@rememberWrappedOnClick
            scope.launch {
                busy = true
                result = HuAppNetworkClient.apply(context)
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
                if (busy) R.string.settings_hu_app_network_busy else R.string.settings_hu_app_network_apply,
            ),
            style = MaterialTheme.typography.tboxButton,
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
    }
    result?.let { current ->
        val isError = current !is HuAppNetworkResult.Done ||
            current.outcome is HuAppNetworkOutcome.NoSu ||
            current.outcome is HuAppNetworkOutcome.Failed
        Text(
            text = appNetworkResultText(current),
            style = MaterialTheme.typography.tboxBody,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
    SettingSwitch(
        autoOpen,
        onAutoOpenChange,
        stringResource(R.string.settings_hu_app_network_auto_title),
        stringResource(R.string.settings_hu_app_network_auto_desc),
        true,
    )
}

@Composable
private fun appNetworkResultText(result: HuAppNetworkResult): String = when (result) {
    is HuAppNetworkResult.AdbFailed -> stringResource(R.string.settings_ca_certs_error_adb, result.reason)
    is HuAppNetworkResult.Done -> when (val outcome = result.outcome) {
        is HuAppNetworkOutcome.Open -> stringResource(R.string.settings_hu_app_network_done)
        HuAppNetworkOutcome.NoFirewall -> stringResource(R.string.settings_hu_app_network_no_firewall)
        HuAppNetworkOutcome.NoSu -> stringResource(R.string.settings_ca_certs_error_nosu)
        is HuAppNetworkOutcome.Failed -> stringResource(R.string.settings_hu_app_network_error, outcome.detail)
    }
}
