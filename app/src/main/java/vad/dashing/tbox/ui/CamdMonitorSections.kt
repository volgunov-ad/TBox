package vad.dashing.tbox.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vad.dashing.tbox.CAMD_WINDOW_CAMERA_IDS
import vad.dashing.tbox.CamdServiceStatus
import vad.dashing.tbox.CamdWindowClient
import vad.dashing.tbox.R
import vad.dashing.tbox.camdKeepAvmCommandLine
import vad.dashing.tbox.isCamdPackageInstalled
import vad.dashing.tbox.ui.theme.tboxBody

/**
 * Info-tab block for «Камеры ГУ». Hidden when the package is not installed.
 * The service starts itself at boot; this screen only reads `STATUS`.
 */
@Composable
fun CamdStatusInfoSection() {
    val context = LocalContext.current
    val installed = remember(context) {
        isCamdPackageInstalled(context.packageManager)
    }
    if (!installed) return

    var status by remember { mutableStateOf<CamdServiceStatus?>(null) }
    LaunchedEffect(Unit) {
        while (isActive) {
            status = withContext(Dispatchers.IO) { CamdWindowClient.fetchStatus() }
            delay(2_000)
        }
    }

    val yes = stringResource(R.string.value_yes)
    val no = stringResource(R.string.value_no)
    val okColor = MaterialTheme.colorScheme.primary
    val failColor = MaterialTheme.colorScheme.error
    val neutralColor = MaterialTheme.colorScheme.onSurface
    val current = status

    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SettingsTitle(stringResource(R.string.info_camd_section))
        if (current == null) {
            StatusRow(
                label = stringResource(R.string.info_camd_service),
                value = no,
                color = failColor,
            )
            TextLine(stringResource(R.string.info_camd_service_down))
        } else {
            StatusRow(
                label = stringResource(R.string.info_camd_service),
                value = yes,
                color = okColor,
            )
            StatusRow(
                label = stringResource(R.string.info_camd_keep_avm),
                value = if (current.keepAvm) yes else no,
                color = if (current.keepAvm) okColor else neutralColor,
            )
            StatusRow(
                label = stringResource(R.string.info_camd_avm),
                value = avmLabel(current.avm),
                color = if (current.avm == "running") okColor else neutralColor,
            )
            StatusRow(
                label = stringResource(R.string.info_camd_windows),
                value = windowsLabel(current.windows),
                valueMaxLines = 3,
            )
        }
    }
}

/**
 * Settings → System switch. Talks to the camera service; does not store a second copy
 * of the flag and does not start the service.
 */
@Composable
fun CamdKeepAvmSystemSwitch() {
    val context = LocalContext.current
    val installed = remember(context) {
        isCamdPackageInstalled(context.packageManager)
    }
    if (!installed) return

    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<CamdServiceStatus?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (isActive) {
            if (!busy) {
                status = withContext(Dispatchers.IO) { CamdWindowClient.fetchStatus() }
            }
            delay(2_000)
        }
    }

    SettingSwitch(
        isChecked = status?.keepAvm == true,
        onCheckedChange = { keepStopped ->
            if (busy) return@SettingSwitch
            busy = true
            val app = context.applicationContext
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    CamdWindowClient.command(
                        camdKeepAvmCommandLine(keepStopped),
                        context = app,
                    )
                }
                status = withContext(Dispatchers.IO) { CamdWindowClient.fetchStatus() }
                busy = false
                if (result.isFailure) {
                    Toast.makeText(
                        app,
                        app.getString(R.string.camd_window_unreachable),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        },
        text = stringResource(R.string.settings_camd_keep_avm),
        description = if (status == null) {
            stringResource(R.string.settings_camd_keep_avm_down)
        } else {
            stringResource(R.string.settings_camd_keep_avm_desc)
        },
        enabled = !busy,
    )
}

@Composable
private fun avmLabel(avm: String): String = when (avm) {
    "running" -> stringResource(R.string.info_camd_avm_running)
    "stopped" -> stringResource(R.string.info_camd_avm_stopped)
    else -> avm
}

@Composable
private fun windowsLabel(ids: List<Int>): String {
    if (ids.isEmpty()) return stringResource(R.string.info_camd_windows_none)
    val names = CAMD_WINDOW_CAMERA_IDS.associateWith { id ->
        stringResource(camdWindowCameraTitle(id))
    }
    return ids.joinToString(", ") { id ->
        val name = names[id]
        if (name == null) id.toString() else "$name ($id)"
    }
}

@Composable
private fun TextLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.tboxBody,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
    )
}
