package vad.dashing.tbox.ui

import android.os.SystemClock
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import vad.dashing.tbox.TboxRepository
import vad.dashing.tbox.TripTelemetryRepository
import vad.dashing.tbox.location.DriveCalibrationMath
import vad.dashing.tbox.location.DriveCalibrationOffsets
import vad.dashing.tbox.location.DriveCalibrationStore
import vad.dashing.tbox.location.GyroCalibrationMath
import vad.dashing.tbox.location.SteerCalibrationMath
import vad.dashing.tbox.location.SteerCalibrationOffsets
import vad.dashing.tbox.location.SteerCalibrationStore
import vad.dashing.tbox.mbcan.MbCanSignal
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private const val STEER_ZERO_SOURCE_ID = "steer-calib-zero"
private const val STEER_ROAD_SOURCE_ID = "steer-calib-road"

private enum class SteerRoadPhase { IDLE, RUNNING, PREVIEW }

/**
 * Steering calibration content for the hub dialog — same layout pattern as
 * [DriveCalibrationSection]: manual actions, inline road session with fill bars,
 * reset, and saved StatusRows.
 */
@Composable
fun SteerCalibrationSection(
    settingsViewModel: SettingsViewModel,
) {
    var showZero by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var roadPhase by remember { mutableStateOf(SteerRoadPhase.IDLE) }
    var previewSteer by remember { mutableStateOf<SteerCalibrationOffsets?>(null) }
    var previewDrive by remember { mutableStateOf<DriveCalibrationOffsets?>(null) }
    var leftCount by remember { mutableIntStateOf(0) }
    var rightCount by remember { mutableIntStateOf(0) }
    var rejectedCount by remember { mutableIntStateOf(0) }
    var speedSampleCount by remember { mutableIntStateOf(0) }
    var speedFill by remember { mutableFloatStateOf(0f) }
    var lagMs by remember { mutableStateOf(0L) }
    val samples = remember { mutableStateListOf<SteerCalibrationMath.SteerSample>() }
    val speedSamples = remember { mutableStateListOf<DriveCalibrationMath.SpeedSample>() }

    val offsets by SteerCalibrationStore.offsetsFlow.collectAsStateWithLifecycle()
    val drive by DriveCalibrationStore.offsetsFlow.collectAsStateWithLifecycle()
    val steer by UniversalCanRepository.steerAngleState.collectAsStateWithLifecycle()
    val loc by TboxRepository.locValues.collectAsStateWithLifecycle()

    val okZero = stringResource(R.string.location_steer_calib_zero_ok)
    val failZero = stringResource(R.string.location_steer_calib_zero_failed)
    val resetMsg = stringResource(R.string.location_steer_calib_reset_toast)
    val steerFill = SteerCalibrationMath.steerFill(leftCount, rightCount)
    val canSave = previewSteer != null || previewDrive != null

    LaunchedEffect(Unit) {
        UniversalCanRepository.setSourceSignals(
            STEER_ROAD_SOURCE_ID,
            setOf(MbCanSignal.SteeringAngle),
        )
    }
    DisposableEffect(Unit) {
        onDispose {
            UniversalCanRepository.enqueueClearSource(STEER_ROAD_SOURCE_ID)
        }
    }

    LaunchedEffect(roadPhase) {
        if (roadPhase != SteerRoadPhase.RUNNING) return@LaunchedEffect
        samples.clear()
        speedSamples.clear()
        leftCount = 0
        rightCount = 0
        rejectedCount = 0
        speedSampleCount = 0
        speedFill = 0f
        lagMs = 0L
        previewSteer = null
        previewDrive = null
        val sessionStart = SystemClock.elapsedRealtime()
        while (isActive && roadPhase == SteerRoadPhase.RUNNING) {
            val now = SystemClock.elapsedRealtime()
            if (now - sessionStart > 10 * 60_000L) {
                roadPhase = if (previewSteer != null || previewDrive != null) {
                    SteerRoadPhase.PREVIEW
                } else {
                    SteerRoadPhase.IDLE
                }
                break
            }
            val raw = steer
            val centered = SteerCalibrationStore.applyZero(raw)
            val can = TripTelemetryRepository.accountingCarSpeed(now)
            val course = loc.trueDirection
            val speedGnss = loc.speed
            val speed = can ?: speedGnss
            if (can != null &&
                speedGnss > 0f &&
                loc.locateStatus &&
                can >= DriveCalibrationMath.MIN_SPEED_KMH * 0.5f
            ) {
                speedSamples.add(
                    DriveCalibrationMath.SpeedSample(
                        elapsedMs = now,
                        gnssKmh = speedGnss,
                        canKmh = can,
                    ),
                )
            }
            if (centered != null &&
                course != 0f &&
                course.isFinite() &&
                speed >= SteerCalibrationMath.MIN_SPEED_KMH * 0.5f &&
                loc.locateStatus
            ) {
                samples.add(
                    SteerCalibrationMath.SteerSample(
                        centeredSteerDeg = centered,
                        bearingDeg = course,
                        speedKmh = speed,
                        elapsedMs = now,
                    ),
                )
            }
            if (samples.size >= 8 || speedSamples.size >= DriveCalibrationMath.MIN_SPEED_FOR_ESTIMATE) {
                if (samples.size >= 8) {
                    val (segs, rejected) = SteerCalibrationMath.collectSteerSegments(samples.toList())
                    rejectedCount = rejected
                    val (l, r) = SteerCalibrationMath.countSides(segs)
                    leftCount = l
                    rightCount = r
                    val est = SteerCalibrationMath.estimateSteerScaleAndSign(segs)
                    if (est != null) {
                        previewSteer = SteerCalibrationMath.mergeWithPrevious(
                            estimate = est,
                            previous = SteerCalibrationStore.offsets,
                            nowEpochMs = System.currentTimeMillis(),
                        )
                    }
                }
                if (speedSamples.size >= DriveCalibrationMath.MIN_SPEED_FOR_ESTIMATE) {
                    val speedBuf = speedSamples.toList()
                    val lag = DriveCalibrationMath.estimateLagMs(speedBuf)
                    val ratios = DriveCalibrationMath.collectSpeedRatios(speedBuf, lag)
                    val stability = DriveCalibrationMath.lagStability(speedBuf)
                    val bucketSet = HashSet<Int>()
                    for (s in speedBuf) {
                        if (s.canKmh >= DriveCalibrationMath.MIN_SPEED_KMH) {
                            bucketSet.add(DriveCalibrationMath.speedBucket(s.canKmh))
                        }
                    }
                    speedSampleCount = ratios.size
                    lagMs = lag
                    speedFill = DriveCalibrationMath.speedFill(
                        ratios.size,
                        bucketSet.size,
                        stability,
                    )
                    if (ratios.size >= DriveCalibrationMath.MIN_SPEED_FOR_ESTIMATE) {
                        val scale = DriveCalibrationMath.median(ratios)?.coerceIn(0.7f, 1.4f)
                        if (scale != null) {
                            val prev = DriveCalibrationStore.offsets
                            previewDrive = prev.copy(
                                speedScale = scale,
                                lagMs = lag,
                                speedEstimated = true,
                                calibratedAtEpochMs = System.currentTimeMillis(),
                            )
                        }
                    }
                }
            }
            // Auto-open preview when steer arcs are complete (same spirit as gyro ready).
            if (previewSteer != null &&
                SteerCalibrationMath.steerFill(leftCount, rightCount) >= 1f
            ) {
                roadPhase = SteerRoadPhase.PREVIEW
                break
            }
            delay(100L)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.location_steer_calib_intro),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        Text(
            text = stringResource(R.string.location_calib_manual_title),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
        )
        OutlinedButton(
            onClick = { showZero = true },
            enabled = roadPhase == SteerRoadPhase.IDLE,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.location_steer_calib_zero_title),
                style = MaterialTheme.typography.tboxButton,
            )
        }
        statusMessage?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.tboxBody,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }

        Text(
            text = stringResource(R.string.location_steer_calib_road_section_title),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
        )
        Text(
            text = stringResource(R.string.location_steer_calib_road_message),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 6.dp),
        )

        when (roadPhase) {
            SteerRoadPhase.IDLE -> {
                OutlinedButton(
                    onClick = { roadPhase = SteerRoadPhase.RUNNING },
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
            SteerRoadPhase.RUNNING -> {
                SteerRoadProgress(
                    steerFill = steerFill,
                    speedFill = speedFill,
                    leftCount = leftCount,
                    rightCount = rightCount,
                    rejectedCount = rejectedCount,
                    speedSampleCount = speedSampleCount,
                    lagMs = lagMs,
                    draftSteer = previewSteer,
                    draftDrive = previewDrive,
                    liveSteerDeg = steer,
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            roadPhase = if (canSave) {
                                SteerRoadPhase.PREVIEW
                            } else {
                                SteerRoadPhase.IDLE
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.location_drive_calib_enough))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            previewSteer = null
                            previewDrive = null
                            roadPhase = SteerRoadPhase.IDLE
                        },
                    ) {
                        Text(stringResource(R.string.location_drive_calib_cancel))
                    }
                }
            }
            SteerRoadPhase.PREVIEW -> {
                SteerRoadProgress(
                    steerFill = steerFill,
                    speedFill = speedFill,
                    leftCount = leftCount,
                    rightCount = rightCount,
                    rejectedCount = rejectedCount,
                    speedSampleCount = speedSampleCount,
                    lagMs = lagMs,
                    draftSteer = previewSteer,
                    draftDrive = previewDrive,
                    liveSteerDeg = steer,
                )
                previewSteer?.let { p ->
                    Text(
                        text = stringResource(
                            R.string.location_steer_calib_road_preview,
                            String.format(Locale.getDefault(), "%.3f", p.scale),
                            formatSteerSign(p.sign),
                        ),
                        style = MaterialTheme.typography.tboxBody,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
                previewDrive?.let { d ->
                    Text(
                        text = stringResource(
                            R.string.location_steer_calib_road_speed_preview,
                            formatSpeedScale(d.speedScale),
                            d.lagMs,
                        ),
                        style = MaterialTheme.typography.tboxBody,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            previewSteer?.let {
                                SteerCalibrationStore.update(it)
                                settingsViewModel.saveSteerCalibrationOffsets(it)
                            }
                            previewDrive?.let {
                                DriveCalibrationStore.update(it)
                                settingsViewModel.saveDriveCalibrationOffsets(it)
                            }
                            previewSteer = null
                            previewDrive = null
                            roadPhase = SteerRoadPhase.IDLE
                        },
                        enabled = canSave,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.location_drive_calib_save))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            previewSteer = null
                            previewDrive = null
                            roadPhase = SteerRoadPhase.IDLE
                        },
                    ) {
                        Text(stringResource(R.string.location_drive_calib_cancel))
                    }
                }
            }
        }

        OutlinedButton(
            onClick = {
                val next = SteerCalibrationOffsets.DEFAULT
                SteerCalibrationStore.update(next)
                settingsViewModel.saveSteerCalibrationOffsets(next)
                statusMessage = resetMsg
            },
            enabled = !offsets.isDefault && roadPhase == SteerRoadPhase.IDLE,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.location_steer_calib_reset),
                style = MaterialTheme.typography.tboxButton,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.location_calib_values_title),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        StatusRow(
            stringResource(R.string.location_steer_calib_zero_title),
            String.format(Locale.getDefault(), "%.1f °", offsets.zeroDeg),
        )
        StatusRow(
            stringResource(R.string.location_calib_k_steer),
            String.format(Locale.getDefault(), "%.3f", offsets.scale),
        )
        StatusRow(
            stringResource(R.string.location_calib_steer_sign),
            formatSteerSign(offsets.sign),
        )
        StatusRow(
            stringResource(R.string.location_calib_k_speed),
            formatSpeedScale(drive.speedScale),
        )
        StatusRow(
            stringResource(R.string.location_calib_lag),
            if (drive.lagMs > 0L) {
                stringResource(R.string.location_calib_lag_value, drive.lagMs)
            } else {
                stringResource(R.string.location_calib_not_set)
            },
        )
        StatusRow(
            stringResource(R.string.location_calib_steer_at),
            formatSteerCalibTime(offsets),
        )
    }

    if (showZero) {
        SteerZeroDialog(
            onDismiss = { showZero = false },
            onFinished = { ok ->
                showZero = false
                statusMessage = if (ok) okZero else failZero
            },
            onSave = { settingsViewModel.saveSteerCalibrationOffsets(it) },
        )
    }
}

@Composable
private fun SteerRoadProgress(
    steerFill: Float,
    speedFill: Float,
    leftCount: Int,
    rightCount: Int,
    rejectedCount: Int,
    speedSampleCount: Int,
    lagMs: Long,
    draftSteer: SteerCalibrationOffsets?,
    draftDrive: DriveCalibrationOffsets?,
    liveSteerDeg: Float?,
) {
    Text(
        text = stringResource(
            R.string.location_steer_calib_road_live,
            leftCount + rightCount,
            leftCount,
            rightCount,
            rejectedCount,
            speedSampleCount,
            liveSteerDeg?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: "—",
        ),
        style = MaterialTheme.typography.tboxBody,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp),
    )
    Text(
        text = stringResource(R.string.location_steer_calib_steer_fill),
        style = MaterialTheme.typography.tboxBody,
        color = MaterialTheme.colorScheme.onSurface,
    )
    LinearProgressIndicator(
        progress = { steerFill },
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
    )
    Text(
        text = stringResource(R.string.location_drive_calib_speed_fill),
        style = MaterialTheme.typography.tboxBody,
        color = MaterialTheme.colorScheme.onSurface,
    )
    LinearProgressIndicator(
        progress = { speedFill },
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
    )
    Text(
        text = stringResource(
            R.string.location_steer_calib_live_draft,
            draftSteer?.let { String.format(Locale.getDefault(), "%.3f", it.scale) } ?: "—",
            draftSteer?.let { formatSteerSign(it.sign) } ?: "—",
            draftDrive?.let { formatSpeedScale(it.speedScale) } ?: "—",
            lagMs,
        ),
        style = MaterialTheme.typography.tboxBody,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun SteerZeroDialog(
    onDismiss: () -> Unit,
    onFinished: (Boolean) -> Unit,
    onSave: (SteerCalibrationOffsets) -> Unit,
) {
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    val samples = remember { mutableStateListOf<Float>() }
    val steer by UniversalCanRepository.steerAngleState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        UniversalCanRepository.setSourceSignals(
            STEER_ZERO_SOURCE_ID,
            setOf(MbCanSignal.SteeringAngle),
        )
    }
    DisposableEffect(Unit) {
        onDispose {
            UniversalCanRepository.enqueueClearSource(STEER_ZERO_SOURCE_ID)
        }
    }

    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        samples.clear()
        val duration = GyroCalibrationMath.CALIBRATION_DURATION_MS
        val start = SystemClock.elapsedRealtime()
        while (isActive) {
            val elapsed = SystemClock.elapsedRealtime() - start
            progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
            steer?.takeIf { it.isFinite() }?.let { samples.add(it) }
            if (elapsed >= duration) break
            delay(50L)
        }
        val avg = GyroCalibrationMath.averageWithRangeCheck(samples, maxRange = 8f)
        if (avg != null && avg.accepted) {
            val next = SteerCalibrationStore.offsets.copy(zeroDeg = avg.mean)
            SteerCalibrationStore.update(next)
            onSave(next)
            onFinished(true)
        } else {
            onFinished(false)
        }
    }

    AlertDialog(
        onDismissRequest = { if (!running) onDismiss() },
        title = { AppAlertDialogTitle(stringResource(R.string.location_steer_calib_zero_title)) },
        text = {
            Column {
                AppAlertDialogText(stringResource(R.string.location_steer_calib_zero_message))
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
                        style = MaterialTheme.typography.tboxBody,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        text = stringResource(
                            R.string.location_steer_calib_angle_live,
                            steer?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: "—",
                        ),
                        style = MaterialTheme.typography.tboxBody,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
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

@Composable
private fun formatSteerSign(sign: Int): String =
    if (sign < 0) {
        stringResource(R.string.location_drive_calib_sign_inverted)
    } else {
        stringResource(R.string.location_drive_calib_sign_normal)
    }

private fun formatSpeedScale(k: Float): String {
    val pct = ((k - 1f) * 100f).roundToInt()
    val pctStr = if (pct >= 0) "+$pct%" else "$pct%"
    return String.format(Locale.getDefault(), "%.3f (%s)", k, pctStr)
}

@Composable
private fun formatSteerCalibTime(off: SteerCalibrationOffsets): String {
    if (off.calibratedAtEpochMs <= 0L) {
        return stringResource(R.string.location_calib_not_set)
    }
    val fmt = remember {
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    }
    return fmt.format(Date(off.calibratedAtEpochMs))
}
