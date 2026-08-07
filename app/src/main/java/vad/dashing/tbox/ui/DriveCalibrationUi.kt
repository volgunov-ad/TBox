package vad.dashing.tbox.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.location.DriveCalibrationMath
import vad.dashing.tbox.location.DriveCalibrationOffsets
import vad.dashing.tbox.location.DriveCalibrationRepository
import vad.dashing.tbox.location.DriveCalibrationSession
import vad.dashing.tbox.location.DriveCalibrationStore
import vad.dashing.tbox.location.GyroBiasOffsets
import vad.dashing.tbox.location.GyroBiasStore
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun DriveCalibrationSection(
    settingsViewModel: SettingsViewModel,
) {
    val ui by DriveCalibrationRepository.uiState.collectAsStateWithLifecycle()
    val saved by DriveCalibrationStore.offsetsFlow.collectAsStateWithLifecycle()
    val bias by GyroBiasStore.offsetsFlow.collectAsStateWithLifecycle()
    val flash by DriveCalibrationRepository.flashMessage.collectAsStateWithLifecycle()
    var confirmReset by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val toastSaved = stringResource(R.string.location_drive_calib_toast_saved)
    val toastReset = stringResource(R.string.location_drive_calib_toast_reset)
    val toastCancelled = stringResource(R.string.location_drive_calib_toast_cancelled)
    val toastNothing = stringResource(R.string.location_drive_calib_toast_nothing)
    val toastTimedOut = stringResource(R.string.location_drive_calib_toast_timed_out)

    LaunchedEffect(flash) {
        val msg = flash ?: return@LaunchedEffect
        DriveCalibrationRepository.consumeFlashMessage()
        val text = when (msg) {
            DriveCalibrationRepository.Message.SAVED -> toastSaved
            DriveCalibrationRepository.Message.RESET -> toastReset
            DriveCalibrationRepository.Message.CANCELLED -> toastCancelled
            DriveCalibrationRepository.Message.NOTHING_TO_SAVE -> toastNothing
            DriveCalibrationRepository.Message.TIMED_OUT -> toastTimedOut
        }
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.location_gyro_calib_road_title),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
        )
        Text(
            text = stringResource(R.string.location_drive_calib_intro),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 6.dp),
        )

        when (ui.phase) {
            DriveCalibrationSession.Phase.IDLE -> {
                OutlinedButton(
                    onClick = { DriveCalibrationRepository.beginSession() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.location_drive_calib_start),
                        style = MaterialTheme.typography.tboxButton,
                    )
                }
            }
            DriveCalibrationSession.Phase.RUNNING,
            DriveCalibrationSession.Phase.PAUSED_BAD_FIX,
            -> {
                DriveCalibProgress(ui)
                DriveCalibLiveDraft(ui)
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { DriveCalibrationRepository.finishEnough() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.location_drive_calib_enough))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { DriveCalibrationRepository.cancelSession() }) {
                        Text(stringResource(R.string.location_drive_calib_cancel))
                    }
                }
            }
            DriveCalibrationSession.Phase.PREVIEW -> {
                DriveCalibProgress(ui)
                DriveCalibLiveDraft(ui)
                val preview = ui.preview
                if (preview != null) {
                    Text(
                        text = stringResource(
                            R.string.location_drive_calib_preview,
                            formatSpeedScale(preview.speedScale),
                            formatYawScale(preview.yawScaleLeft),
                            formatYawScale(preview.yawScaleRight),
                            formatYawSign(preview.yawSign),
                            formatEstFlags(preview),
                        ),
                        style = MaterialTheme.typography.tboxBody,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                    if (ui.previewLowQuality) {
                        Text(
                            text = stringResource(R.string.location_drive_calib_hint_low_quality),
                            style = MaterialTheme.typography.tboxBody,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                }
                val canSave = preview != null &&
                    (preview.speedEstimated || preview.yawEstimated)
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            val off = DriveCalibrationRepository.takePreviewForSave()
                            if (off != null) {
                                settingsViewModel.saveDriveCalibrationOffsets(off)
                            }
                        },
                        enabled = canSave,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.location_drive_calib_save))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { DriveCalibrationRepository.cancelSession() }) {
                        Text(stringResource(R.string.location_drive_calib_cancel))
                    }
                }
            }
        }

        OutlinedButton(
            onClick = { confirmReset = true },
            enabled = !saved.isDefault,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.location_drive_calib_reset),
                style = MaterialTheme.typography.tboxButton,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.location_calib_manual_edit_title),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Text(
            text = stringResource(R.string.location_calib_manual_edit_desc),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Text(
            text = stringResource(R.string.location_calib_online_note),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        DriveManualEditFields(
            bias = bias,
            drive = saved,
            onSaveBias = { settingsViewModel.saveGyroBiasOffsets(it) },
            onSaveDrive = { settingsViewModel.saveDriveCalibrationOffsets(it) },
        )
        StatusRow(
            stringResource(R.string.location_calib_bias_temp),
            bias.yawCalibTempC?.let {
                String.format(Locale.getDefault(), "%.1f °C", it)
            } ?: stringResource(R.string.location_calib_not_set),
        )
        StatusRow(
            stringResource(R.string.location_calib_lag),
            if (saved.lagMs > 0L) {
                stringResource(R.string.location_calib_lag_value, saved.lagMs)
            } else {
                stringResource(R.string.location_calib_not_set)
            },
        )
        StatusRow(
            stringResource(R.string.location_calib_drive_at),
            formatCalibTime(saved),
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { AppAlertDialogTitle(stringResource(R.string.location_drive_calib_reset)) },
            text = { AppAlertDialogText(stringResource(R.string.location_drive_calib_reset_confirm)) },
            confirmButton = {
                Button(onClick = {
                    settingsViewModel.resetDriveCalibrationOffsets()
                    DriveCalibrationRepository.announceReset()
                    confirmReset = false
                }) {
                    AppAlertDialogButtonLabel(stringResource(R.string.location_drive_calib_reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    AppAlertDialogButtonLabel(stringResource(R.string.location_drive_calib_cancel))
                }
            },
        )
    }
}

@Composable
private fun DriveManualEditFields(
    bias: GyroBiasOffsets,
    drive: DriveCalibrationOffsets,
    onSaveBias: (GyroBiasOffsets) -> Unit,
    onSaveDrive: (DriveCalibrationOffsets) -> Unit,
) {
    var yawDraft by remember { mutableStateOf(formatCalibFloat(bias.yawDegPerSec, 2)) }
    var pitchDraft by remember { mutableStateOf(formatCalibFloat(bias.pitchDegPerSec, 2)) }
    var rollDraft by remember { mutableStateOf(formatCalibFloat(bias.rollDegPerSec, 2)) }
    var axDraft by remember { mutableStateOf(formatCalibFloat(bias.accelX, 2)) }
    var ayDraft by remember { mutableStateOf(formatCalibFloat(bias.accelY, 2)) }
    var azDraft by remember { mutableStateOf(formatCalibFloat(bias.accelZ, 2)) }
    var speedDraft by remember { mutableStateOf(formatCalibFloat(drive.speedScale, 3)) }
    var yawLDraft by remember { mutableStateOf(formatCalibFloat(drive.yawScaleLeft, 3)) }
    var yawRDraft by remember { mutableStateOf(formatCalibFloat(drive.yawScaleRight, 3)) }
    var signDraft by remember {
        mutableStateOf(if (drive.yawSign < 0) "-1" else "1")
    }
    LaunchedEffect(bias) {
        yawDraft = formatCalibFloat(bias.yawDegPerSec, 2)
        pitchDraft = formatCalibFloat(bias.pitchDegPerSec, 2)
        rollDraft = formatCalibFloat(bias.rollDegPerSec, 2)
        axDraft = formatCalibFloat(bias.accelX, 2)
        ayDraft = formatCalibFloat(bias.accelY, 2)
        azDraft = formatCalibFloat(bias.accelZ, 2)
    }
    LaunchedEffect(drive.speedScale, drive.yawScaleLeft, drive.yawScaleRight, drive.yawSign) {
        speedDraft = formatCalibFloat(drive.speedScale, 3)
        yawLDraft = formatCalibFloat(drive.yawScaleLeft, 3)
        yawRDraft = formatCalibFloat(drive.yawScaleRight, 3)
        signDraft = if (drive.yawSign < 0) "-1" else "1"
    }

    CalibrationFloatCommitField(
        title = stringResource(R.string.location_calib_bias_yaw),
        description = stringResource(R.string.location_calib_edit_bias_rate_hint),
        draft = yawDraft,
        onDraftChange = { yawDraft = it },
        savedValue = bias.yawDegPerSec,
        minValue = GyroBiasOffsets.BIAS_RATE_EDIT_MIN,
        maxValue = GyroBiasOffsets.BIAS_RATE_EDIT_MAX,
        decimals = 2,
        onCommit = {
            val next = bias.copy(yawDegPerSec = it)
            GyroBiasStore.update(next)
            onSaveBias(next)
        },
    )
    CalibrationFloatCommitField(
        title = stringResource(R.string.location_calib_bias_pitch),
        description = "",
        draft = pitchDraft,
        onDraftChange = { pitchDraft = it },
        savedValue = bias.pitchDegPerSec,
        minValue = GyroBiasOffsets.BIAS_RATE_EDIT_MIN,
        maxValue = GyroBiasOffsets.BIAS_RATE_EDIT_MAX,
        decimals = 2,
        onCommit = {
            val next = bias.copy(pitchDegPerSec = it)
            GyroBiasStore.update(next)
            onSaveBias(next)
        },
    )
    CalibrationFloatCommitField(
        title = stringResource(R.string.location_calib_bias_roll),
        description = "",
        draft = rollDraft,
        onDraftChange = { rollDraft = it },
        savedValue = bias.rollDegPerSec,
        minValue = GyroBiasOffsets.BIAS_RATE_EDIT_MIN,
        maxValue = GyroBiasOffsets.BIAS_RATE_EDIT_MAX,
        decimals = 2,
        onCommit = {
            val next = bias.copy(rollDegPerSec = it)
            GyroBiasStore.update(next)
            onSaveBias(next)
        },
    )
    CalibrationFloatCommitField(
        title = stringResource(R.string.location_calib_bias_accel_x),
        description = stringResource(R.string.location_calib_edit_accel_hint),
        draft = axDraft,
        onDraftChange = { axDraft = it },
        savedValue = bias.accelX,
        minValue = GyroBiasOffsets.ACCEL_EDIT_MIN,
        maxValue = GyroBiasOffsets.ACCEL_EDIT_MAX,
        decimals = 2,
        onCommit = {
            val next = bias.copy(accelX = it)
            GyroBiasStore.update(next)
            onSaveBias(next)
        },
    )
    CalibrationFloatCommitField(
        title = stringResource(R.string.location_calib_bias_accel_y),
        description = "",
        draft = ayDraft,
        onDraftChange = { ayDraft = it },
        savedValue = bias.accelY,
        minValue = GyroBiasOffsets.ACCEL_EDIT_MIN,
        maxValue = GyroBiasOffsets.ACCEL_EDIT_MAX,
        decimals = 2,
        onCommit = {
            val next = bias.copy(accelY = it)
            GyroBiasStore.update(next)
            onSaveBias(next)
        },
    )
    CalibrationFloatCommitField(
        title = stringResource(R.string.location_calib_bias_accel_z),
        description = "",
        draft = azDraft,
        onDraftChange = { azDraft = it },
        savedValue = bias.accelZ,
        minValue = GyroBiasOffsets.ACCEL_EDIT_MIN,
        maxValue = GyroBiasOffsets.ACCEL_EDIT_MAX,
        decimals = 2,
        onCommit = {
            val next = bias.copy(accelZ = it)
            GyroBiasStore.update(next)
            onSaveBias(next)
        },
    )
    CalibrationFloatCommitField(
        title = stringResource(R.string.location_calib_k_speed),
        description = stringResource(R.string.location_calib_edit_speed_hint),
        draft = speedDraft,
        onDraftChange = { speedDraft = it },
        savedValue = drive.speedScale,
        minValue = DriveCalibrationOffsets.SPEED_SCALE_EDIT_MIN,
        maxValue = DriveCalibrationOffsets.SPEED_SCALE_EDIT_MAX,
        decimals = 3,
        onCommit = {
            val next = drive.copy(
                speedScale = it,
                calibratedAtEpochMs = System.currentTimeMillis(),
            )
            DriveCalibrationStore.update(next)
            onSaveDrive(next)
        },
    )
    CalibrationFloatCommitField(
        title = stringResource(R.string.location_calib_k_yaw_left),
        description = stringResource(R.string.location_calib_edit_yaw_scale_hint),
        draft = yawLDraft,
        onDraftChange = { yawLDraft = it },
        savedValue = drive.yawScaleLeft,
        minValue = DriveCalibrationOffsets.YAW_SCALE_EDIT_MIN,
        maxValue = DriveCalibrationOffsets.YAW_SCALE_EDIT_MAX,
        decimals = 3,
        onCommit = {
            val next = drive.copy(
                yawScaleLeft = it,
                calibratedAtEpochMs = System.currentTimeMillis(),
            )
            DriveCalibrationStore.update(next)
            onSaveDrive(next)
        },
    )
    CalibrationFloatCommitField(
        title = stringResource(R.string.location_calib_k_yaw_right),
        description = "",
        draft = yawRDraft,
        onDraftChange = { yawRDraft = it },
        savedValue = drive.yawScaleRight,
        minValue = DriveCalibrationOffsets.YAW_SCALE_EDIT_MIN,
        maxValue = DriveCalibrationOffsets.YAW_SCALE_EDIT_MAX,
        decimals = 3,
        onCommit = {
            val next = drive.copy(
                yawScaleRight = it,
                calibratedAtEpochMs = System.currentTimeMillis(),
            )
            DriveCalibrationStore.update(next)
            onSaveDrive(next)
        },
    )
    CalibrationSignCommitField(
        title = stringResource(R.string.location_calib_yaw_sign),
        description = stringResource(R.string.location_calib_edit_sign_hint),
        draft = signDraft,
        onDraftChange = { signDraft = it },
        savedSign = drive.yawSign,
        onCommit = {
            val next = drive.copy(
                yawSign = it,
                calibratedAtEpochMs = System.currentTimeMillis(),
            )
            DriveCalibrationStore.update(next)
            onSaveDrive(next)
        },
    )
}

@Composable
private fun DriveCalibProgress(ui: DriveCalibrationSession.UiState) {
    val e = ui.estimates
    Text(
        text = stringResource(R.string.location_drive_calib_speed_fill),
        style = MaterialTheme.typography.tboxBody,
        color = MaterialTheme.colorScheme.onSurface,
    )
    LinearProgressIndicator(
        progress = { e.speedFill },
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
    )
    Text(
        text = stringResource(R.string.location_calib_turns_left),
        style = MaterialTheme.typography.tboxBody,
        color = MaterialTheme.colorScheme.onSurface,
    )
    LinearProgressIndicator(
        progress = { DriveCalibrationMath.sideFill(e.yawLeftCount) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
    )
    Text(
        text = stringResource(R.string.location_calib_turns_right),
        style = MaterialTheme.typography.tboxBody,
        color = MaterialTheme.colorScheme.onSurface,
    )
    LinearProgressIndicator(
        progress = { DriveCalibrationMath.sideFill(e.yawRightCount) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
    )
}

@Composable
private fun DriveCalibLiveDraft(ui: DriveCalibrationSession.UiState) {
    val e = ui.estimates
    Text(
        text = stringResource(
            R.string.location_drive_calib_live_draft,
            e.lagMs,
            if (e.speedEstimated) formatSpeedScale(e.speedScale) else "—",
            if (e.yawLeftEstimated) formatYawScale(e.yawScaleLeft) else "—",
            if (e.yawRightEstimated) formatYawScale(e.yawScaleRight) else "—",
            if (e.yawEstimated) formatYawSign(if (e.yawSign < 0) -1 else 1) else "—",
            e.speedSampleCount,
            e.speedBuckets,
            e.yawLeftCount,
            e.yawRightCount,
            e.yawRejectedCount,
        ),
        style = MaterialTheme.typography.tboxBody,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

private fun formatSpeedScale(k: Float): String {
    val pct = ((k - 1f) * 100f).roundToInt()
    val pctStr = if (pct >= 0) "+$pct%" else "$pct%"
    return String.format(Locale.getDefault(), "%.3f (%s)", k, pctStr)
}

private fun formatYawScale(k: Float): String =
    String.format(Locale.getDefault(), "%.3f", k)

@Composable
private fun formatYawSign(sign: Int): String =
    if (sign < 0) {
        stringResource(R.string.location_drive_calib_sign_inverted)
    } else {
        stringResource(R.string.location_drive_calib_sign_normal)
    }

@Composable
private fun formatEstFlags(off: DriveCalibrationOffsets): String {
    val speed = if (off.speedEstimated) {
        stringResource(R.string.location_drive_calib_flag_speed_ok)
    } else {
        stringResource(R.string.location_drive_calib_flag_speed_keep)
    }
    val yaw = if (off.yawEstimated) {
        stringResource(R.string.location_drive_calib_flag_yaw_ok)
    } else {
        stringResource(R.string.location_drive_calib_flag_yaw_keep)
    }
    return "$speed · $yaw"
}

@Composable
private fun formatCalibTime(off: DriveCalibrationOffsets): String {
    if (off.calibratedAtEpochMs <= 0L) {
        return stringResource(R.string.location_calib_not_set)
    }
    val fmt = remember {
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    }
    return fmt.format(Date(off.calibratedAtEpochMs))
}
