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
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
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
    val accelXSamples = remember { mutableStateListOf<Float>() }
    val accelYSamples = remember { mutableStateListOf<Float>() }
    val accelZSamples = remember { mutableStateListOf<Float>() }
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
        accelXSamples.clear()
        accelYSamples.clear()
        accelZSamples.clear()
        val start = System.currentTimeMillis()
        val duration = GyroCalibrationMath.CALIBRATION_DURATION_MS
        while (isActive) {
            val elapsed = System.currentTimeMillis() - start
            progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
            snap.gyroYaw?.let { yawSamples.add(it) }
            snap.gyroPitch?.let { pitchSamples.add(it) }
            snap.gyroRoll?.let { rollSamples.add(it) }
            snap.accelX?.let { accelXSamples.add(it) }
            snap.accelY?.let { accelYSamples.add(it) }
            snap.accelZ?.let { accelZSamples.add(it) }
            if (elapsed >= duration) break
            delay(50)
        }
        val maxGyroRange = GyroCalibrationMath.MAX_STATIC_RANGE_DEG_PER_SEC
        val maxAccelRange = GyroCalibrationMath.MAX_STATIC_RANGE_ACCEL
        val current = GyroBiasStore.offsets
        val accepted: Boolean
        val next: GyroBiasOffsets
        when (kind) {
            GyroCalibKind.TILT -> {
                val pitch = GyroCalibrationMath.averageWithRangeCheck(pitchSamples, maxGyroRange)
                val roll = GyroCalibrationMath.averageWithRangeCheck(rollSamples, maxGyroRange)
                val ax = GyroCalibrationMath.averageWithRangeCheck(accelXSamples, maxAccelRange)
                val ay = GyroCalibrationMath.averageWithRangeCheck(accelYSamples, maxAccelRange)
                val az = GyroCalibrationMath.averageWithRangeCheck(accelZSamples, maxAccelRange)
                val gyroOk = pitch?.accepted == true && roll?.accepted == true
                val hasAccel = accelXSamples.isNotEmpty() ||
                    accelYSamples.isNotEmpty() ||
                    accelZSamples.isNotEmpty()
                val accelOk = ax?.accepted == true && ay?.accepted == true && az?.accepted == true
                // Accel optional if sensor absent; require gyro pitch/roll always.
                accepted = gyroOk && (!hasAccel || accelOk)
                next = if (accepted) {
                    current.copy(
                        pitchDegPerSec = pitch!!.mean,
                        rollDegPerSec = roll!!.mean,
                        accelX = if (accelOk) ax!!.mean else current.accelX,
                        accelY = if (accelOk) ay!!.mean else current.accelY,
                        accelZ = if (accelOk) az!!.mean else current.accelZ,
                    )
                } else {
                    current
                }
            }
            GyroCalibKind.ZERO -> {
                val yaw = GyroCalibrationMath.averageWithRangeCheck(yawSamples, maxGyroRange)
                accepted = yaw?.accepted == true
                next = if (accepted) {
                    current.copy(
                        yawDegPerSec = yaw!!.mean,
                        yawCalibTempC = snap.gyroTemp?.takeIf { it.isFinite() },
                    )
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
        title = { AppAlertDialogTitle(title) },
        text = {
            Column {
                AppAlertDialogText(message)
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
                        style = androidx.compose.material3.MaterialTheme.typography.tboxBody,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
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
                AppAlertDialogButtonLabel(stringResource(R.string.location_gyro_calib_start))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !running,
            ) {
                AppAlertDialogButtonLabel(stringResource(R.string.location_gyro_calib_cancel))
            }
        },
    )
}
