package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxButton
import vad.dashing.tbox.vehicle.WheelPulseCalibrationStore
import vad.dashing.tbox.vehicle.WheelPulseOdometer
import vad.dashing.tbox.valueToString
import java.util.Locale
import kotlin.math.roundToInt

/** Live status for wheel-pulse odometer on Geolocation → calibrations. */
@Composable
fun WheelPulseCalibrationSection(
    settingsViewModel: SettingsViewModel,
) {
    val calib by WheelPulseCalibrationStore.calibration.collectAsStateWithLifecycle()
    val counters by UniversalCanRepository.wheelPulseState.collectAsStateWithLifecycle()
    var snap by remember { mutableStateOf(WheelPulseOdometer.peekCalibration()) }
    var confirmReset by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            snap = WheelPulseOdometer.peekCalibration()
            delay(1_000L)
        }
    }

    val usable = WheelPulseCalibrationStore.isUsableForDistance()
    val tripsUsePulse = WheelPulseCalibrationStore.isTripsPulseEnabled()
    val drUsesPulse = WheelPulseCalibrationStore.isMockDrPulseEnabled()

    val statusColor = when {
        usable -> Color(0xFF2E7D32)
        snap.metersPerPulse > 0f -> MaterialTheme.colorScheme.tertiary
        counters != null -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.error
    }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
        SettingsTitle(stringResource(R.string.location_wheel_pulse_title))
        Text(
            text = stringResource(R.string.location_wheel_pulse_desc),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Text(
            text = stringResource(R.string.location_wheel_pulse_calib_hint),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Text(
            text = when {
                usable -> stringResource(R.string.location_wheel_pulse_status_ready)
                snap.metersPerPulse > 0f ->
                    stringResource(
                        R.string.location_wheel_pulse_status_calibrating,
                        (snap.confidence * 100f).roundToInt(),
                        (WheelPulseCalibrationStore.CONFIDENCE_USE_THRESHOLD * 100f).roundToInt(),
                    )
                counters != null -> stringResource(R.string.location_wheel_pulse_status_collecting)
                else -> stringResource(R.string.location_wheel_pulse_status_uncalibrated)
            },
            style = MaterialTheme.typography.tboxBody,
            color = statusColor,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        WheelPulseRow(
            label = stringResource(R.string.location_wheel_pulse_k),
            value = if (snap.metersPerPulse > 0f) {
                String.format(Locale.getDefault(), "%.4f", snap.metersPerPulse)
            } else {
                stringResource(R.string.location_calib_not_set)
            },
        )
        WheelPulseRow(
            label = stringResource(R.string.location_wheel_pulse_confidence),
            value = "${(snap.confidence * 100f).roundToInt()} %",
        )
        WheelPulseRow(
            label = stringResource(R.string.location_wheel_pulse_trips_source),
            value = if (tripsUsePulse) {
                stringResource(R.string.location_wheel_pulse_source_hybrid)
            } else {
                stringResource(R.string.location_wheel_pulse_source_odo)
            },
            highlight = tripsUsePulse,
        )
        WheelPulseRow(
            label = stringResource(R.string.location_wheel_pulse_dr_source),
            value = if (drUsesPulse) {
                stringResource(R.string.location_wheel_pulse_source_pulse)
            } else {
                stringResource(R.string.location_wheel_pulse_source_can_speed)
            },
            highlight = drUsesPulse,
        )
        val c = counters
        if (c != null) {
            WheelPulseRow(
                label = stringResource(R.string.location_wheel_pulse_counters),
                value = "${c.lhf} / ${c.rhf} / ${c.lhr} / ${c.rhr}",
            )
        } else {
            WheelPulseRow(
                label = stringResource(R.string.location_wheel_pulse_counters),
                value = stringResource(R.string.location_wheel_pulse_counters_none),
            )
        }
        if (snap.lastAsymmetryRatio > 0f) {
            WheelPulseRow(
                label = stringResource(R.string.location_wheel_pulse_asym),
                value = valueToString(snap.lastAsymmetryRatio * 100f, 1),
            )
        }
        SettingSwitch(
            isChecked = calib.tripsEnabled,
            onCheckedChange = { enabled ->
                settingsViewModel.setWheelPulseTripsEnabled(enabled)
            },
            text = stringResource(R.string.location_wheel_pulse_trips_flag),
            description = stringResource(R.string.location_wheel_pulse_trips_flag_desc),
            enabled = usable,
        )
        SettingSwitch(
            isChecked = calib.mockDrEnabled,
            onCheckedChange = { enabled ->
                settingsViewModel.setWheelPulseMockDrEnabled(enabled)
            },
            text = stringResource(R.string.location_wheel_pulse_dr_flag),
            description = stringResource(R.string.location_wheel_pulse_dr_flag_desc),
            enabled = usable,
        )
        OutlinedButton(
            onClick = rememberWrappedOnClick { confirmReset = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            enabled = snap.metersPerPulse > 0f || snap.confidence > 0f,
        ) {
            Text(
                text = stringResource(R.string.location_wheel_pulse_reset),
                style = MaterialTheme.typography.tboxButton,
            )
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { AppAlertDialogTitle(stringResource(R.string.location_wheel_pulse_reset)) },
            text = {
                Text(
                    text = stringResource(R.string.location_wheel_pulse_reset_message),
                    style = MaterialTheme.typography.tboxBody,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = rememberWrappedOnClick {
                        confirmReset = false
                        settingsViewModel.resetWheelPulseCalibration()
                    },
                ) {
                    AppAlertDialogButtonLabel(stringResource(R.string.location_wheel_pulse_reset_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = rememberWrappedOnClick { confirmReset = false }) {
                    AppAlertDialogButtonLabel(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun WheelPulseRow(
    label: String,
    value: String,
    highlight: Boolean = false,
) {
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.tboxBody,
        color = if (highlight) {
            Color(0xFF2E7D32)
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = Modifier.padding(vertical = 2.dp),
    )
}
