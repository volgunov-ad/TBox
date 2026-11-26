package vad.dashing.tbox.ui

import android.widget.Toast
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vad.dashing.tbox.HeadUnitCanMode
import vad.dashing.tbox.R
import vad.dashing.tbox.rootsvc.RootSvcMonitor
import vad.dashing.tbox.rootsvc.RootSvcMonitorStatus
import vad.dashing.tbox.store.TboxStoreLauncher
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxButton

/**
 * Settings → System card for the shared A9 root service (`vad.dashing.rootsvc`).
 * Hidden on A10 (service is A9-only).
 */
@Composable
fun RootSvcSettingsCard(headUnitCanMode: HeadUnitCanMode) {
    if (headUnitCanMode != HeadUnitCanMode.Android9MbCan) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<RootSvcMonitorStatus?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (isActive) {
            if (!busy) {
                status = withContext(Dispatchers.IO) {
                    RootSvcMonitor.fetchStatus(context.applicationContext)
                }
            }
            delay(2_000)
        }
    }

    val yes = stringResource(R.string.value_yes)
    val no = stringResource(R.string.value_no)
    val dash = stringResource(R.string.value_no_data)
    val okColor = MaterialTheme.colorScheme.primary
    val failColor = MaterialTheme.colorScheme.error
    val neutralColor = MaterialTheme.colorScheme.onSurface
    val current = status

    SettingsTitle(stringResource(R.string.settings_rootsvc_title))
    Text(
        text = stringResource(R.string.settings_rootsvc_desc),
        style = MaterialTheme.typography.tboxBody,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )

    if (current == null) {
        Text(
            text = stringResource(R.string.settings_rootsvc_status_checking),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    } else {
        StatusRow(
            label = stringResource(R.string.settings_rootsvc_package),
            value = if (current.packageInstalled) {
                current.packageVersion ?: yes
            } else {
                no
            },
            color = if (current.packageInstalled) okColor else failColor,
        )
        StatusRow(
            label = stringResource(R.string.settings_rootsvc_service),
            value = if (current.up) yes else no,
            color = if (current.up) okColor else failColor,
        )
        if (current.packageInstalled) {
            StatusRow(
                label = stringResource(R.string.settings_rootsvc_server),
                value = current.serverVersion ?: dash,
                color = if (current.serverVersion != null) okColor else neutralColor,
            )
            StatusRow(
                label = stringResource(R.string.settings_rootsvc_uptime),
                value = formatRootSvcUptime(current.uptimeMs),
            )
            StatusRow(
                label = stringResource(R.string.settings_rootsvc_state),
                value = when {
                    current.crashloop -> stringResource(R.string.settings_rootsvc_state_crashloop)
                    !current.state.isNullOrBlank() -> current.state!!
                    current.up -> stringResource(R.string.settings_rootsvc_state_ok)
                    else -> dash
                },
                color = when {
                    current.crashloop -> failColor
                    current.up -> okColor
                    else -> neutralColor
                },
            )
        } else {
            Text(
                text = stringResource(R.string.settings_rootsvc_not_installed_hint),
                style = MaterialTheme.typography.tboxBody,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
    }

    if (current?.packageInstalled == true) {
        Button(
            onClick = rememberWrappedOnClick {
                if (busy) return@rememberWrappedOnClick
                busy = true
                val app = context.applicationContext
                scope.launch {
                    withContext(Dispatchers.IO) {
                        RootSvcMonitor.ensure(app)
                        delay(1_500)
                    }
                    status = withContext(Dispatchers.IO) { RootSvcMonitor.fetchStatus(app) }
                    busy = false
                }
            },
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_rootsvc_ensure),
                style = MaterialTheme.typography.tboxButton,
                maxLines = 2,
                textAlign = TextAlign.Center,
            )
        }
        OutlinedButton(
            onClick = rememberWrappedOnClick {
                if (busy) return@rememberWrappedOnClick
                busy = true
                val app = context.applicationContext
                scope.launch {
                    withContext(Dispatchers.IO) {
                        RootSvcMonitor.ensureOrRestart(app)
                        delay(2_000)
                    }
                    status = withContext(Dispatchers.IO) { RootSvcMonitor.fetchStatus(app) }
                    busy = false
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.settings_rootsvc_restart),
                style = MaterialTheme.typography.tboxButton,
                maxLines = 2,
                textAlign = TextAlign.Center,
            )
        }
        OutlinedButton(
            onClick = rememberWrappedOnClick {
                if (!RootSvcMonitor.openPackage(context)) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.settings_rootsvc_open_failed),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.settings_rootsvc_open),
                style = MaterialTheme.typography.tboxButton,
                maxLines = 2,
                textAlign = TextAlign.Center,
            )
        }
    } else {
        val storeInstalled = remember {
            TboxStoreLauncher.isInstalled(context.packageManager)
        }
        if (storeInstalled) {
            Button(
                onClick = rememberWrappedOnClick {
                    if (!TboxStoreLauncher.open(context)) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.settings_rootsvc_store_open_failed),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_rootsvc_install_store),
                    style = MaterialTheme.typography.tboxButton,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun formatRootSvcUptime(uptimeMs: Long?): String {
    if (uptimeMs == null || uptimeMs < 0L) {
        return stringResource(R.string.value_no_data)
    }
    val totalSec = uptimeMs / 1_000L
    val h = totalSec / 3_600L
    val m = (totalSec % 3_600L) / 60L
    val s = totalSec % 60L
    return if (h > 0L) {
        stringResource(R.string.settings_rootsvc_uptime_hms, h, m, s)
    } else {
        stringResource(R.string.settings_rootsvc_uptime_ms, m, s)
    }
}
