package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.location.DriveCalibrationRepository
import vad.dashing.tbox.location.DriveCalibrationSession
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxButton
import vad.dashing.tbox.ui.theme.tboxTitle

/**
 * Compact Geolocation-tab entry: two buttons open hub dialogs with start/process
 * UI and all saved calibration values (keeps the tab uncluttered).
 */
@Composable
fun LocationCalibrationEntryButtons(
    settingsViewModel: SettingsViewModel,
) {
    var showGyroHub by remember { mutableStateOf(false) }
    var showSteerHub by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(
            text = stringResource(R.string.location_calib_entry_title),
            style = MaterialTheme.typography.tboxTitle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Text(
            text = stringResource(R.string.location_calib_entry_desc),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        OutlinedButton(
            onClick = { showGyroHub = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.location_gyro_calib_section_title),
                style = MaterialTheme.typography.tboxButton,
            )
        }
        OutlinedButton(
            onClick = { showSteerHub = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.location_steer_calib_title),
                style = MaterialTheme.typography.tboxButton,
            )
        }
    }

    if (showGyroHub) {
        GyroCalibrationHubDialog(
            settingsViewModel = settingsViewModel,
            onDismiss = { showGyroHub = false },
        )
    }
    if (showSteerHub) {
        SteerCalibrationHubDialog(
            settingsViewModel = settingsViewModel,
            onDismiss = { showSteerHub = false },
        )
    }
}

@Composable
private fun GyroCalibrationHubDialog(
    settingsViewModel: SettingsViewModel,
    onDismiss: () -> Unit,
) {
    DisposableEffect(Unit) {
        onDispose {
            val phase = DriveCalibrationRepository.uiState.value.phase
            if (phase != DriveCalibrationSession.Phase.IDLE) {
                DriveCalibrationRepository.cancelSession()
            }
        }
    }

    CalibrationHubDialog(
        title = stringResource(R.string.location_gyro_calib_section_title),
        onDismiss = onDismiss,
    ) {
        Text(
            text = stringResource(R.string.location_gyro_calib_section_desc),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        GyroCalibrationButtons(settingsViewModel = settingsViewModel)
        DriveCalibrationSection(settingsViewModel = settingsViewModel)
    }
}

@Composable
private fun SteerCalibrationHubDialog(
    settingsViewModel: SettingsViewModel,
    onDismiss: () -> Unit,
) {
    CalibrationHubDialog(
        title = stringResource(R.string.location_steer_calib_title),
        onDismiss = onDismiss,
    ) {
        SteerCalibrationSection(settingsViewModel = settingsViewModel)
    }
}

@Composable
private fun CalibrationHubDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.96f)
            .fillMaxHeight(0.92f),
        title = { AppAlertDialogTitle(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                content()
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                AppAlertDialogButtonLabel(stringResource(R.string.action_close))
            }
        },
    )
}
