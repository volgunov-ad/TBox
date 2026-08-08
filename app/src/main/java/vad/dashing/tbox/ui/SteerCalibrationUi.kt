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
    var confirmReset by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var roadPhase by remember { mutableStateOf(SteerRoadPhase.IDLE) }
    var previewSteer by remember { mutableStateOf<SteerCalibrationOffsets?>(null) }
    var previewDrive by remember { mutableStateOf<DriveCalibrationOffsets?>(null) }
    var leftCount by remember { mutableIntStateOf(0) }
    var rightCount by remember { mutableIntStateOf(0) }
    var rejectedCount by remember { mutableIntStateOf(0) }
    var speedSampleCount by remember { mutableIntStateOf(0) }
    var speedBuckets by remember { mutableIntStateOf(0) }
    var leftFill by remember { mutableFloatStateOf(0f) }
    var rightFill by remember { mutableFloatStateOf(0f) }
    var speedFill by remember { mutableFloatStateOf(0f) }
    var lagMs by remember { mutableStateOf(0L) }
    var previewLowQuality by remember { mutableStateOf(false) }
    val samples = remember { mutableStateListOf<SteerCalibrationMath.SteerSample>() }
    val speedSamples = remember { mutableStateListOf<DriveCalibrationMath.SpeedSample>() }

    val offsets by SteerCalibrationStore.offsetsFlow.collectAsStateWithLifecycle()
    val drive by DriveCalibrationStore.offsetsFlow.collectAsStateWithLifecycle()
    val steer by UniversalCanRepository.steerAngleState.collectAsStateWithLifecycle()
    val loc by TboxRepository.locValues.collectAsStateWithLifecycle()

    val okZero = stringResource(R.string.location_steer_calib_zero_ok)
    val failZero = stringResource(R.string.location_steer_calib_zero_failed)
    val resetMsg = stringResource(R.string.location_steer_calib_reset_toast)
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
        speedBuckets = 0
        leftFill = 0f
        rightFill = 0f
        speedFill = 0f
        lagMs = 0L
        previewLowQuality = false
        previewSteer = null
        previewDrive = null
        val sessionStart = SystemClock.elapsedRealtime()
        while (isActive && roadPhase == SteerRoadPhase.RUNNING) {
            val now = SystemClock.elapsedRealtime()
            if (now - sessionStart > 10 * 60_000L) {
                // Timeout → preview like gyro (even if low quality).
                previewLowQuality = previewSteer == null && previewDrive == null
                roadPhase = SteerRoadPhase.PREVIEW
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
                    val attempt = SteerCalibrationMath.attemptSteerScaleAndSign(segs)
                    // Fitted side progress is monotonic (re-fit must not shrink bars).
                    leftCount = maxOf(leftCount, attempt.fittedLeft)
                    rightCount = maxOf(rightCount, attempt.fittedRight)
                    leftFill = maxOf(leftFill, SteerCalibrationMath.sideFill(attempt.fittedLeft))
                    rightFill = maxOf(rightFill, SteerCalibrationMath.sideFill(attempt.fittedRight))
                    // Keep last good steer draft — do not wipe when a later recompute fails.
                    attempt.estimate?.let { est ->
                        previewSteer = SteerCalibrationMath.mergeWithPrevious(
                            estimate = est,
                            previous = SteerCalibrationStore.offsets,
                            nowEpochMs = System.currentTimeMillis(),
                        )
                    }
                }
                if (speedSamples.size >= 6) {
                    val speedBuf = speedSamples.toList()
                    val lag = DriveCalibrationMath.estimateLagMs(speedBuf)
                    val speedResult = DriveCalibrationMath.collectSpeedRatios(speedBuf, lag)
                    val ratios = speedResult.ratios
                    val buckets = speedResult.buckets
                    val stability = DriveCalibrationMath.lagStability(speedBuf)
                    speedSampleCount = maxOf(speedSampleCount, ratios.size)
                    speedBuckets = maxOf(speedBuckets, buckets)
                    lagMs = lag
                    // Monotonic fill — lag/window recompute must not shrink the bar.
                    speedFill = maxOf(
                        speedFill,
                        DriveCalibrationMath.speedFill(ratios.size, buckets),
                    )
                    val speedOk = ratios.size >= DriveCalibrationMath.MIN_SPEED_FOR_ESTIMATE &&
                        buckets >= DriveCalibrationMath.SPEED_BUCKETS_TARGET &&
                        stability >= DriveCalibrationMath.MIN_LAG_STABILITY_FOR_ESTIMATE
                    if (speedOk) {
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
                    } else {
                        // Keep last good draft if we already had one and fill is complete;
                        // only clear when never estimated this session.
                        if (previewDrive?.speedEstimated != true) {
                            previewDrive = null
                        }
                    }
                }
            }
            // Auto-preview once both turn sides have enough fitted arcs (steer and/or speed).
            if (leftFill >= 1f && rightFill >= 1f) {
                previewLowQuality = previewSteer == null && previewDrive == null
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
            onClick = rememberWrappedOnClick { showZero = true },
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
                    onClick = rememberWrappedOnClick { roadPhase = SteerRoadPhase.RUNNING },
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
                    leftFill = leftFill,
                    rightFill = rightFill,
                    speedFill = speedFill,
                    fittedLeft = leftCount,
                    fittedRight = rightCount,
                    rejectedCount = rejectedCount,
                    speedSampleCount = speedSampleCount,
                    speedBuckets = speedBuckets,
                    lagMs = lagMs,
                    draftSteer = previewSteer,
                    draftDrive = previewDrive,
                    liveSteerDeg = steer,
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = rememberWrappedOnClick {
                            // Always preview (like gyro) — never wipe progress to IDLE.
                            previewLowQuality = previewSteer == null && previewDrive == null
                            roadPhase = SteerRoadPhase.PREVIEW
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = stringResource(R.string.location_drive_calib_enough),
                            style = MaterialTheme.typography.tboxButton,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = rememberWrappedOnClick {
                            previewSteer = null
                            previewDrive = null
                            previewLowQuality = false
                            roadPhase = SteerRoadPhase.IDLE
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.location_drive_calib_cancel),
                            style = MaterialTheme.typography.tboxButton,
                        )
                    }
                }
            }
            SteerRoadPhase.PREVIEW -> {
                SteerRoadProgress(
                    leftFill = leftFill,
                    rightFill = rightFill,
                    speedFill = speedFill,
                    fittedLeft = leftCount,
                    fittedRight = rightCount,
                    rejectedCount = rejectedCount,
                    speedSampleCount = speedSampleCount,
                    speedBuckets = speedBuckets,
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
                if (previewLowQuality || !canSave) {
                    Text(
                        text = stringResource(R.string.location_drive_calib_hint_low_quality),
                        style = MaterialTheme.typography.tboxBody,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = rememberWrappedOnClick {
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
                            previewLowQuality = false
                            roadPhase = SteerRoadPhase.IDLE
                        },
                        enabled = canSave,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = stringResource(R.string.location_drive_calib_save),
                            style = MaterialTheme.typography.tboxButton,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = rememberWrappedOnClick {
                            previewSteer = null
                            previewDrive = null
                            previewLowQuality = false
                            roadPhase = SteerRoadPhase.IDLE
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.location_drive_calib_cancel),
                            style = MaterialTheme.typography.tboxButton,
                        )
                    }
                }
            }
        }

        OutlinedButton(
            onClick = rememberWrappedOnClick { confirmReset = true },
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
        SteerManualEditFields(
            offsets = offsets,
            drive = drive,
            onSaveSteer = { settingsViewModel.saveSteerCalibrationOffsets(it) },
            onSaveDrive = { settingsViewModel.saveDriveCalibrationOffsets(it) },
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

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { AppAlertDialogTitle(stringResource(R.string.location_steer_calib_reset)) },
            text = { AppAlertDialogText(stringResource(R.string.location_steer_calib_reset_confirm)) },
            confirmButton = {
                Button(
                    onClick = rememberWrappedOnClick {
                        val next = SteerCalibrationOffsets.DEFAULT
                        SteerCalibrationStore.update(next)
                        settingsViewModel.saveSteerCalibrationOffsets(next)
                        statusMessage = resetMsg
                        confirmReset = false
                    },
                ) {
                    AppAlertDialogButtonLabel(stringResource(R.string.location_steer_calib_reset))
                }
            },
            dismissButton = {
                TextButton(onClick = rememberWrappedOnClick { confirmReset = false }) {
                    AppAlertDialogButtonLabel(stringResource(R.string.location_drive_calib_cancel))
                }
            },
        )
    }
}

@Composable
private fun SteerManualEditFields(
    offsets: SteerCalibrationOffsets,
    drive: DriveCalibrationOffsets,
    onSaveSteer: (SteerCalibrationOffsets) -> Unit,
    onSaveDrive: (DriveCalibrationOffsets) -> Unit,
) {
    var zeroDraft by remember { mutableStateOf(formatCalibFloat(offsets.zeroDeg, 1)) }
    var scaleDraft by remember { mutableStateOf(formatCalibFloat(offsets.scale, 3)) }
    var signDraft by remember { mutableStateOf(if (offsets.sign < 0) "-1" else "1") }
    var deadzoneDraft by remember { mutableStateOf(formatCalibFloat(offsets.deadzoneDeg, 1)) }
    var speedDraft by remember { mutableStateOf(formatCalibFloat(drive.speedScale, 3)) }
    LaunchedEffect(offsets) {
        zeroDraft = formatCalibFloat(offsets.zeroDeg, 1)
        scaleDraft = formatCalibFloat(offsets.scale, 3)
        signDraft = if (offsets.sign < 0) "-1" else "1"
        deadzoneDraft = formatCalibFloat(offsets.deadzoneDeg, 1)
    }
    LaunchedEffect(drive.speedScale) {
        speedDraft = formatCalibFloat(drive.speedScale, 3)
    }

    CalibrationFloatCommitField(
        title = stringResource(R.string.location_steer_calib_zero_title),
        description = stringResource(R.string.location_calib_edit_steer_zero_hint),
        draft = zeroDraft,
        onDraftChange = { zeroDraft = it },
        savedValue = offsets.zeroDeg,
        minValue = SteerCalibrationOffsets.ZERO_EDIT_MIN,
        maxValue = SteerCalibrationOffsets.ZERO_EDIT_MAX,
        decimals = 1,
        onCommit = {
            val next = offsets.copy(zeroDeg = it)
            SteerCalibrationStore.update(next)
            onSaveSteer(next)
        },
    )
    CalibrationFloatCommitField(
        title = stringResource(R.string.location_calib_k_steer),
        description = stringResource(R.string.location_calib_edit_steer_scale_hint),
        draft = scaleDraft,
        onDraftChange = { scaleDraft = it },
        savedValue = offsets.scale,
        minValue = SteerCalibrationOffsets.SCALE_EDIT_MIN,
        maxValue = SteerCalibrationOffsets.SCALE_EDIT_MAX,
        decimals = 3,
        onCommit = {
            val next = offsets.copy(
                scale = it,
                calibratedAtEpochMs = System.currentTimeMillis(),
                scaleEstimated = true,
            )
            SteerCalibrationStore.update(next)
            onSaveSteer(next)
        },
    )
    CalibrationSignCommitField(
        title = stringResource(R.string.location_calib_steer_sign),
        description = stringResource(R.string.location_calib_edit_sign_hint),
        draft = signDraft,
        onDraftChange = { signDraft = it },
        savedSign = offsets.sign,
        onCommit = {
            val next = offsets.copy(
                sign = it,
                calibratedAtEpochMs = System.currentTimeMillis(),
            )
            SteerCalibrationStore.update(next)
            onSaveSteer(next)
        },
    )
    CalibrationFloatCommitField(
        title = stringResource(R.string.location_calib_steer_deadzone),
        description = stringResource(R.string.location_calib_edit_deadzone_hint),
        draft = deadzoneDraft,
        onDraftChange = { deadzoneDraft = it },
        savedValue = offsets.deadzoneDeg,
        minValue = SteerCalibrationOffsets.DEADZONE_MIN_DEG,
        maxValue = SteerCalibrationOffsets.DEADZONE_MAX_DEG,
        decimals = 1,
        onCommit = {
            val next = offsets.copy(deadzoneDeg = it)
            SteerCalibrationStore.update(next)
            onSaveSteer(next)
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
}

@Composable
private fun SteerRoadProgress(
    leftFill: Float,
    rightFill: Float,
    speedFill: Float,
    fittedLeft: Int,
    fittedRight: Int,
    rejectedCount: Int,
    speedSampleCount: Int,
    speedBuckets: Int,
    lagMs: Long,
    draftSteer: SteerCalibrationOffsets?,
    draftDrive: DriveCalibrationOffsets?,
    liveSteerDeg: Float?,
) {
    val angleSuffix = stringResource(
        R.string.location_calib_road_live_steer_angle,
        liveSteerDeg?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: "—",
    )
    Text(
        text = stringResource(
            R.string.location_calib_road_live,
            speedSampleCount,
            speedBuckets,
            fittedLeft,
            fittedRight,
            rejectedCount,
        ) + angleSuffix,
        style = MaterialTheme.typography.tboxBody,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp),
    )
    CalibrationSpeedTurnProgressBars(
        speedFill = speedFill,
        leftFill = leftFill,
        rightFill = rightFill,
    )
    Text(
        text = stringResource(
            R.string.location_steer_calib_live_draft,
            draftDrive?.let { formatSpeedScale(it.speedScale) } ?: "—",
            draftSteer?.let { String.format(Locale.getDefault(), "%.3f", it.scale) } ?: "—",
            draftSteer?.let { formatSteerSign(it.sign) } ?: "—",
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
                onClick = rememberWrappedOnClick { running = true },
                enabled = !running,
            ) {
                AppAlertDialogButtonLabel(stringResource(R.string.location_gyro_calib_start))
            }
        },
        dismissButton = {
            TextButton(
                onClick = rememberWrappedOnClick(onDismiss),
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
