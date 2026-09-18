package vad.dashing.tbox.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxButton
import vad.dashing.tbox.ui.theme.tboxTitle
import java.text.DateFormat
import java.util.Date

@Composable
fun SpeedCamEntryButton(
    settingsViewModel: SettingsViewModel,
    enabled: Boolean = true,
) {
    var showHub by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = rememberWrappedOnClick { showHub = true },
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = stringResource(R.string.speed_cam_open_button),
            style = MaterialTheme.typography.tboxButton,
        )
    }
    if (showHub) {
        SpeedCamHubDialog(
            settingsViewModel = settingsViewModel,
            onDismiss = { showHub = false },
        )
    }
}

@Composable
fun SpeedCamHubDialog(
    settingsViewModel: SettingsViewModel,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val manager = remember(settingsViewModel) {
        settingsViewModel.speedCamPackManager(context)
    }
    val scope = rememberCoroutineScope()
    LaunchedEffect(manager) {
        manager.ensureLoaded()
    }
    val snap by manager.snapshot.collectAsStateWithLifecycle()
    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        scope.launch {
            manager.installFromUri(uri)
        }
    }

    AlertDialog(
        onDismissRequest = { if (!snap.busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Text(
                text = stringResource(R.string.speed_cam_hub_title),
                style = MaterialTheme.typography.tboxTitle,
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                val status = if (!snap.installed) {
                    stringResource(R.string.speed_cam_status_empty)
                } else {
                    val whenText = if (snap.installedAtEpochMs > 0L) {
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                            .format(Date(snap.installedAtEpochMs))
                    } else {
                        snap.source.ifBlank { "—" }
                    }
                    stringResource(
                        R.string.speed_cam_status_installed,
                        snap.pointCount,
                        whenText,
                    )
                }
                Text(
                    text = status,
                    style = MaterialTheme.typography.tboxBody,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                snap.lastError?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.tboxBody,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                if (snap.busy) {
                    LinearProgressIndicator(
                        progress = { snap.progressFraction ?: 0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    )
                    Text(
                        text = snap.statusMessage,
                        style = MaterialTheme.typography.tboxBody,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                OutlinedButton(
                    onClick = rememberWrappedOnClick {
                        scope.launch { manager.downloadFromSite() }
                    },
                    enabled = !snap.busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.speed_cam_download),
                        style = MaterialTheme.typography.tboxButton,
                    )
                }
                OutlinedButton(
                    onClick = rememberWrappedOnClick {
                        pickLauncher.launch(arrayOf("text/*", "text/plain", "*/*"))
                    },
                    enabled = !snap.busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.speed_cam_install_usb),
                        style = MaterialTheme.typography.tboxButton,
                    )
                }
                if (snap.installed) {
                    OutlinedButton(
                        onClick = rememberWrappedOnClick {
                            scope.launch { manager.deleteInstalled() }
                        },
                        enabled = !snap.busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.speed_cam_delete),
                            style = MaterialTheme.typography.tboxButton,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = rememberWrappedOnClick(onDismiss),
                enabled = !snap.busy,
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
    )
}
