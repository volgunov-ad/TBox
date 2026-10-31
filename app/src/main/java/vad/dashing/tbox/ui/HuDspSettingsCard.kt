package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import vad.dashing.tbox.HuDspClient
import vad.dashing.tbox.HuDspMode
import vad.dashing.tbox.HuDspResult
import vad.dashing.tbox.R
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxButton

@Composable
fun HuDspSettingsCard(
    mode: Int,
    onModeChange: (Int) -> Unit,
    autoApply: Boolean,
    onAutoApplyChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var result by remember(mode) { mutableStateOf<HuDspResult?>(null) }

    SettingsTitle(stringResource(R.string.settings_hu_dsp_title))
    Text(
        text = stringResource(R.string.settings_hu_dsp_desc),
        style = MaterialTheme.typography.tboxBody,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = rememberWrappedOnClick { expanded = true },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = dspModeLabel(mode),
                style = MaterialTheme.typography.tboxButton,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = stringResource(
                    if (expanded) R.string.dropdown_collapse else R.string.dropdown_expand,
                ),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            HuDspMode.MODES.forEach { candidate ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = dspModeLabel(candidate),
                            style = MaterialTheme.typography.tboxBody,
                            color = if (candidate == mode) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    },
                    onClick = rememberWrappedOnClick {
                        expanded = false
                        onModeChange(candidate)
                    },
                )
            }
        }
    }
    Button(
        onClick = rememberWrappedOnClick {
            if (busy) return@rememberWrappedOnClick
            scope.launch {
                busy = true
                try {
                    result = HuDspClient.apply(context, mode)
                } finally {
                    busy = false
                }
            }
        },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
    ) {
        Text(
            text = stringResource(
                if (busy) R.string.settings_hu_time_zone_busy else R.string.settings_hu_dsp_apply,
            ),
            style = MaterialTheme.typography.tboxButton,
            textAlign = TextAlign.Center,
        )
    }
    result?.let { current ->
        Text(
            text = when (current) {
                is HuDspResult.Accepted -> stringResource(
                    R.string.settings_hu_dsp_accepted,
                    dspModeLabel(current.mode),
                )
                is HuDspResult.AdbFailed -> stringResource(R.string.settings_ca_certs_error_adb, current.reason)
                is HuDspResult.ServiceFailed -> stringResource(
                    R.string.settings_hu_dsp_error,
                    current.status?.toString() ?: "—",
                )
            },
            style = MaterialTheme.typography.tboxBody,
            color = if (current is HuDspResult.Accepted) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            },
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
    SettingSwitch(
        autoApply,
        onAutoApplyChange,
        stringResource(R.string.settings_hu_dsp_auto_title),
        stringResource(R.string.settings_hu_dsp_auto_desc),
        true,
    )
}

@Composable
private fun dspModeLabel(mode: Int): String = when (mode) {
    1 -> stringResource(R.string.settings_hu_dsp_adayo)
    2 -> stringResource(R.string.settings_hu_dsp_sony)
    250 -> stringResource(R.string.settings_hu_dsp_default)
    else -> stringResource(R.string.settings_hu_dsp_unknown, mode)
}
