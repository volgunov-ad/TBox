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
import androidx.compose.ui.graphics.Color
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

    LaunchedEffect(flash) {
        val msg = flash ?: return@LaunchedEffect
        DriveCalibrationRepository.consumeFlashMessage()
        val text = when (msg) {
            DriveCalibrationRepository.Message.SAVED -> toastSaved
            DriveCalibrationRepository.Message.RESET -> toastReset
            DriveCalibrationRepository.Message.CANCELLED -> toastCancelled
            DriveCalibrationRepository.Message.NOTHING_TO_SAVE -> toastNothing
        }
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.location_drive_calib_intro),
            style = MaterialTheme.typography.tboxBody,
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
                            formatYawScale(preview.yawScale),
                            formatYawSign(preview.yawSign),
                            formatEstFlags(preview),
                        ),
                        style = MaterialTheme.typography.tboxBody,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                    if (ui.previewLowQuality) {
                        Text(
                            text = stringResource(R.string.location_drive_calib_hint_low_quality),
                            style = MaterialTheme.typography.tboxBody,
                            color = Color(0xFFF9A825),
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
            text = stringResource(R.string.location_calib_values_title),
            style = MaterialTheme.typography.tboxBody,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        StatusRow(
            stringResource(R.string.location_calib_bias_yaw),
            String.format(Locale.getDefault(), "%.2f °/s", bias.yawDegPerSec),
        )
        StatusRow(
            stringResource(R.string.location_calib_bias_pitch),
            String.format(Locale.getDefault(), "%.2f °/s", bias.pitchDegPerSec),
        )
        StatusRow(
            stringResource(R.string.location_calib_bias_roll),
            String.format(Locale.getDefault(), "%.2f °/s", bias.rollDegPerSec),
        )
        StatusRow(
            stringResource(R.string.location_calib_bias_accel),
            String.format(
                Locale.getDefault(),
                "%.2f / %.2f / %.2f",
                bias.accelX,
                bias.accelY,
                bias.accelZ,
            ),
        )
        StatusRow(
            stringResource(R.string.location_calib_k_speed),
            formatSpeedScale(saved.speedScale),
        )
        StatusRow(
            stringResource(R.string.location_calib_k_yaw),
            "${formatYawScale(saved.yawScale)} (${formatYawSign(saved.yawSign)})",
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
            title = { Text(stringResource(R.string.location_drive_calib_reset)) },
            text = { Text(stringResource(R.string.location_drive_calib_reset_confirm)) },
            confirmButton = {
                Button(onClick = {
                    settingsViewModel.resetDriveCalibrationOffsets()
                    DriveCalibrationRepository.announceReset()
                    confirmReset = false
                }) {
                    Text(stringResource(R.string.location_drive_calib_reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text(stringResource(R.string.location_drive_calib_cancel))
                }
            },
        )
    }
}

@Composable
private fun DriveCalibProgress(ui: DriveCalibrationSession.UiState) {
    val hintColor = if (ui.phase == DriveCalibrationSession.Phase.PAUSED_BAD_FIX ||
        ui.previewLowQuality
    ) {
        Color(0xFFF9A825)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = hintText(ui.hint),
        style = MaterialTheme.typography.tboxBody,
        color = hintColor,
        modifier = Modifier.padding(vertical = 4.dp),
    )
    Text(
        text = stringResource(R.string.location_drive_calib_speed_fill),
        style = MaterialTheme.typography.tboxBody,
    )
    LinearProgressIndicator(
        progress = { ui.estimates.speedFill },
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
    )
    Text(
        text = stringResource(R.string.location_drive_calib_yaw_fill),
        style = MaterialTheme.typography.tboxBody,
    )
    LinearProgressIndicator(
        progress = { ui.estimates.yawFill },
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
            formatSpeedScale(e.speedScale),
            formatYawScale(e.yawScale),
            formatYawSign(if (e.yawSign < 0) -1 else 1),
            e.speedSampleCount,
            e.yawSegmentCount,
            e.yawRejectedCount,
        ),
        style = MaterialTheme.typography.tboxBody,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun hintText(hint: DriveCalibrationMath.Hint): String = when (hint) {
    DriveCalibrationMath.Hint.INTRO -> stringResource(R.string.location_drive_calib_hint_intro)
    DriveCalibrationMath.Hint.WAIT_FIX -> stringResource(R.string.location_drive_calib_hint_wait_fix)
    DriveCalibrationMath.Hint.NO_CAN -> stringResource(R.string.location_drive_calib_hint_no_can)
    DriveCalibrationMath.Hint.NO_GYRO -> stringResource(R.string.location_drive_calib_hint_no_gyro)
    DriveCalibrationMath.Hint.COURSE_JUMP ->
        stringResource(R.string.location_drive_calib_hint_course_jump)
    DriveCalibrationMath.Hint.SPEED_UP -> stringResource(R.string.location_drive_calib_hint_speed_up)
    DriveCalibrationMath.Hint.HOLD_STEADY ->
        stringResource(R.string.location_drive_calib_hint_hold_steady)
    DriveCalibrationMath.Hint.TURN -> stringResource(R.string.location_drive_calib_hint_turn)
    DriveCalibrationMath.Hint.SPEED_DONE_NEED_TURN ->
        stringResource(R.string.location_drive_calib_hint_need_turn)
    DriveCalibrationMath.Hint.TURN_DONE_NEED_SPEED ->
        stringResource(R.string.location_drive_calib_hint_need_speed)
    DriveCalibrationMath.Hint.READY -> stringResource(R.string.location_drive_calib_hint_ready)
    DriveCalibrationMath.Hint.LOW_QUALITY ->
        stringResource(R.string.location_drive_calib_hint_low_quality)
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
