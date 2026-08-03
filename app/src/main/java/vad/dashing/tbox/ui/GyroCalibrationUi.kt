package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.drsensor.DrSensorRepository
import vad.dashing.tbox.location.GyroBiasOffsets
import vad.dashing.tbox.location.GyroBiasStore
import vad.dashing.tbox.location.GyroCalibrationMath
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxButton

private enum class GyroCalibKind { TILT, ZERO }

@Composable
fun GyroCalibrationButtons(
    settingsViewModel: SettingsViewModel,
) {
    var dialogKind by remember { mutableStateOf<GyroCalibKind?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    val okMsg = stringResource(R.string.location_gyro_calib_ok)
    val failMsg = stringResource(R.string.location_gyro_calib_failed)

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { dialogKind = GyroCalibKind.TILT },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.location_gyro_calib_tilt_title),
                style = androidx.compose.material3.MaterialTheme.typography.tboxButton,
            )
        }
        OutlinedButton(
            onClick = { dialogKind = GyroCalibKind.ZERO },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.location_gyro_calib_zero_title),
                style = androidx.compose.material3.MaterialTheme.typography.tboxButton,
            )
        }
        statusMessage?.let {
            Text(
                text = it,
                style = androidx.compose.material3.MaterialTheme.typography.tboxBody,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
    }

    dialogKind?.let { kind ->
        GyroCalibrationDialog(
            kind = kind,
            onDismiss = { dialogKind = null },
            onFinished = { ok ->
                dialogKind = null
                statusMessage = if (ok) okMsg else failMsg
            },
            onSave = { offsets ->
                settingsViewModel.saveGyroBiasOffsets(offsets)
            },
        )
    }
}

@Composable
private fun GyroCalibrationDialog(
    kind: GyroCalibKind,
    onDismiss: () -> Unit,
    onFinished: (Boolean) -> Unit,
    onSave: (GyroBiasOffsets) -> Unit,
) {
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    val yawSamples = remember { mutableStateListOf<Float>() }
    val pitchSamples = remember { mutableStateListOf<Float>() }
    val rollSamples = remember { mutableStateListOf<Float>() }
    val snap by DrSensorRepository.snapshot.collectAsStateWithLifecycle()

    val title = when (kind) {
        GyroCalibKind.TILT -> stringResource(R.string.location_gyro_calib_tilt_title)
        GyroCalibKind.ZERO -> stringResource(R.string.location_gyro_calib_zero_title)
    }
    val message = when (kind) {
        GyroCalibKind.TILT -> stringResource(R.string.location_gyro_calib_tilt_message)
        GyroCalibKind.ZERO -> stringResource(R.string.location_gyro_calib_zero_message)
    }

    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        yawSamples.clear()
        pitchSamples.clear()
        rollSamples.clear()
        val start = System.currentTimeMillis()
        val duration = GyroCalibrationMath.CALIBRATION_DURATION_MS
        while (isActive) {
            val elapsed = System.currentTimeMillis() - start
            progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
            snap.gyroYaw?.let { yawSamples.add(it) }
            snap.gyroPitch?.let { pitchSamples.add(it) }
            snap.gyroRoll?.let { rollSamples.add(it) }
            if (elapsed >= duration) break
            delay(50)
        }
        val maxRange = GyroCalibrationMath.MAX_STATIC_RANGE_DEG_PER_SEC
        val current = GyroBiasStore.offsets
        val accepted: Boolean
        val next: GyroBiasOffsets
        when (kind) {
            GyroCalibKind.TILT -> {
                val pitch = GyroCalibrationMath.averageWithRangeCheck(pitchSamples, maxRange)
                val roll = GyroCalibrationMath.averageWithRangeCheck(rollSamples, maxRange)
                accepted = pitch?.accepted == true && roll?.accepted == true
                next = if (accepted) {
                    current.copy(
                        pitchDegPerSec = pitch!!.mean,
                        rollDegPerSec = roll!!.mean,
                    )
                } else {
                    current
                }
            }
            GyroCalibKind.ZERO -> {
                val yaw = GyroCalibrationMath.averageWithRangeCheck(yawSamples, maxRange)
                accepted = yaw?.accepted == true
                next = if (accepted) {
                    current.copy(yawDegPerSec = yaw!!.mean)
                } else {
                    current
                }
            }
        }
        if (accepted) onSave(next)
        running = false
        onFinished(accepted)
    }

    AlertDialog(
        onDismissRequest = { if (!running) onDismiss() },
        title = { Text(title) },
        text = {
            Column {
                Text(message)
                if (running) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    )
                    Text(
                        text = stringResource(
                            R.string.location_gyro_calib_progress,
                            (progress * 100).toInt(),
                        ),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { running = true },
                enabled = !running,
            ) {
                Text(stringResource(R.string.location_gyro_calib_start))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !running,
            ) {
                Text(stringResource(R.string.location_gyro_calib_cancel))
            }
        },
    )
}
