package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.location.DriveCalibrationRepository
import vad.dashing.tbox.location.DriveCalibrationSession
import vad.dashing.tbox.location.DriveCalibrationStore
import vad.dashing.tbox.location.GeoCalibrationState
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxButton

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
    val mockPowerState by settingsViewModel.mockPowerState.collectAsStateWithLifecycle()
    val mockCanSpeedMode by settingsViewModel.mockCanSpeedMode.collectAsStateWithLifecycle()
    val constantAutoCalibEnabled by settingsViewModel.constantAutoCalibEnabled.collectAsStateWithLifecycle()
    val geoCalibNeeds by GeoCalibrationState.needsCalibration.collectAsStateWithLifecycle()
    val geoCalibLastAtMs by GeoCalibrationState.lastCalibratedAtEpochMs.collectAsStateWithLifecycle()
    val effectiveMockCanSpeedMode = mockPowerState.effectiveCanSpeedMode(mockCanSpeedMode)
    val hasEverDriveCalibrated =
        geoCalibLastAtMs > 0L ||
            DriveCalibrationStore.offsets.calibratedAtEpochMs > 0L
    val showGeoCalibBanner = effectiveMockCanSpeedMode.isConstantCalc && (
        (constantAutoCalibEnabled && geoCalibNeeds) ||
            !hasEverDriveCalibrated
        )

    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
        SettingsTitle(stringResource(R.string.location_calib_entry_title))
        Text(
            text = stringResource(R.string.location_calib_entry_desc),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        if (showGeoCalibBanner) {
            Text(
                text = if (constantAutoCalibEnabled && geoCalibNeeds) {
                    stringResource(R.string.settings_mock_geo_calib_needs)
                } else {
                    stringResource(R.string.settings_mock_geo_calib_never)
                },
                style = MaterialTheme.typography.tboxBody,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
            )
        }
        OutlinedButton(
            onClick = rememberWrappedOnClick { showGyroHub = true },
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
            onClick = rememberWrappedOnClick { showSteerHub = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.location_steer_calib_title),
                style = MaterialTheme.typography.tboxButton,
            )
        }
        WheelPulseCalibrationSection(settingsViewModel = settingsViewModel)
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
            TextButton(onClick = rememberWrappedOnClick(onDismiss)) {
                AppAlertDialogButtonLabel(stringResource(R.string.action_close))
            }
        },
    )
}
