package vad.dashing.tbox.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.speedcam.SpeedCamPackManager
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxButton
import java.text.DateFormat
import java.util.Date

/** Public site for manual download / browsing (SpeedCamOnline). */
const val SPEED_CAM_SITE_URL = "https://speedcamonline.ru/"

@Composable
fun SpeedCamCamerasSection(
    settingsViewModel: SettingsViewModel,
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

    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsTitle(stringResource(R.string.speed_cam_section_title))
        Text(
            text = speedCamLastLoadedLabel(snap),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        if (snap.installed) {
            Text(
                text = stringResource(R.string.speed_cam_section_points, snap.pointCount),
                style = MaterialTheme.typography.tboxBody,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        } else {
            Text(
                text = stringResource(R.string.speed_cam_status_empty),
                style = MaterialTheme.typography.tboxBody,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        Text(
            text = stringResource(R.string.speed_cam_section_source_hint),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Text(
            text = stringResource(R.string.speed_cam_section_site_link),
            style = MaterialTheme.typography.tboxBody.copy(
                textDecoration = TextDecoration.Underline,
                color = MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier
                .padding(bottom = 12.dp)
                .clickable(
                    onClick = rememberWrappedOnClick {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(SPEED_CAM_SITE_URL)),
                            )
                        }
                    },
                ),
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
}

@Composable
private fun speedCamLastLoadedLabel(snap: SpeedCamPackManager.Snapshot): String {
    if (!snap.installed || snap.installedAtEpochMs <= 0L) {
        return stringResource(R.string.speed_cam_section_last_loaded_never)
    }
    val whenText = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        .format(Date(snap.installedAtEpochMs))
    return stringResource(R.string.speed_cam_section_last_loaded, whenText)
}
