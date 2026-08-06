package vad.dashing.tbox.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import vad.dashing.tbox.location.GyroCalibrationMath
import vad.dashing.tbox.location.SteerCalibrationMath
import vad.dashing.tbox.location.SteerCalibrationOffsets
import vad.dashing.tbox.location.SteerCalibrationStore
import vad.dashing.tbox.mbcan.MbCanSignal
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxButton
import java.util.Locale

private const val STEER_ZERO_SOURCE_ID = "steer-calib-zero"
private const val STEER_ROAD_SOURCE_ID = "steer-calib-road"

@Composable
fun SteerCalibrationSection(
    settingsViewModel: SettingsViewModel,
) {
    var showZero by remember { mutableStateOf(false) }
    var showRoad by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    val offsets by SteerCalibrationStore.offsetsFlow.collectAsStateWithLifecycle()
    val okZero = stringResource(R.string.location_steer_calib_zero_ok)
    val failZero = stringResource(R.string.location_steer_calib_zero_failed)
    val okRoad = stringResource(R.string.location_steer_calib_road_ok)
    val failRoad = stringResource(R.string.location_steer_calib_road_failed)
    val resetMsg = stringResource(R.string.location_steer_calib_reset_toast)

    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            text = stringResource(R.string.location_steer_calib_title),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Text(
            text = stringResource(R.string.location_steer_calib_intro),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Text(
            text = stringResource(
                R.string.location_steer_calib_saved,
                String.format(Locale.US, "%.1f", offsets.zeroDeg),
                String.format(Locale.US, "%.3f", offsets.scaleLeft),
                String.format(Locale.US, "%.3f", offsets.scaleRight),
                if (offsets.sign < 0) {
                    stringResource(R.string.location_drive_calib_sign_inverted)
                } else {
                    stringResource(R.string.location_drive_calib_sign_normal)
                },
            ),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        OutlinedButton(
            onClick = { showZero = true },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.location_steer_calib_zero_title),
                style = MaterialTheme.typography.tboxButton,
            )
        }
        OutlinedButton(
            onClick = { showRoad = true },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.location_steer_calib_road_title),
                style = MaterialTheme.typography.tboxButton,
            )
        }
        OutlinedButton(
            onClick = {
                val next = SteerCalibrationOffsets.DEFAULT
                SteerCalibrationStore.update(next)
                settingsViewModel.saveSteerCalibrationOffsets(next)
                statusMessage = resetMsg
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.location_steer_calib_reset),
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
    if (showRoad) {
        SteerRoadDialog(
            onDismiss = { showRoad = false },
            onFinished = { ok ->
                showRoad = false
                statusMessage = if (ok) okRoad else failRoad
            },
            onSave = { settingsViewModel.saveSteerCalibrationOffsets(it) },
        )
    }
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
        onDismissRequest = {
            if (!running) onDismiss()
        },
        title = { Text(stringResource(R.string.location_steer_calib_zero_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.location_steer_calib_zero_message),
                    style = MaterialTheme.typography.tboxBody,
                )
                if (running) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    )
                    Text(
                        text = stringResource(
                            R.string.location_steer_calib_angle_live,
                            steer?.let { String.format(Locale.US, "%.1f", it) } ?: "—",
                        ),
                        style = MaterialTheme.typography.tboxBody,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            if (!running) {
                Button(onClick = { running = true }) {
                    Text(stringResource(R.string.location_gyro_calib_start))
                }
            }
        },
        dismissButton = {
            if (!running) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.location_drive_calib_cancel))
                }
            }
        },
    )
}

@Composable
private fun SteerRoadDialog(
    onDismiss: () -> Unit,
    onFinished: (Boolean) -> Unit,
    onSave: (SteerCalibrationOffsets) -> Unit,
) {
    var running by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<SteerCalibrationOffsets?>(null) }
    var segmentCount by remember { mutableStateOf(0) }
    var rejectedCount by remember { mutableStateOf(0) }
    val samples = remember { mutableStateListOf<SteerCalibrationMath.SteerSample>() }
    val steer by UniversalCanRepository.steerAngleState.collectAsStateWithLifecycle()
    val loc by TboxRepository.locValues.collectAsStateWithLifecycle()

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

    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        samples.clear()
        segmentCount = 0
        rejectedCount = 0
        preview = null
        val sessionStart = SystemClock.elapsedRealtime()
        while (isActive && running) {
            val now = SystemClock.elapsedRealtime()
            if (now - sessionStart > 10 * 60_000L) break
            val raw = steer
            val centered = SteerCalibrationStore.applyZero(raw)
            val can = TripTelemetryRepository.accountingCarSpeed(now)
            val course = loc.trueDirection
            val speedGnss = loc.speed
            val speed = can ?: speedGnss
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
                if (samples.size >= 8) {
                    val (segs, rejected) = SteerCalibrationMath.collectSteerSegments(samples.toList())
                    segmentCount = segs.size
                    rejectedCount = rejected
                    val est = SteerCalibrationMath.estimateSteerScalesAndSign(segs)
                    if (est != null && est.hasAny) {
                        preview = SteerCalibrationMath.mergeWithPrevious(
                            estimate = est,
                            previous = SteerCalibrationStore.offsets,
                            nowEpochMs = System.currentTimeMillis(),
                        )
                    }
                }
            }
            delay(100L)
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!running || preview != null) {
                running = false
                onDismiss()
            }
        },
        title = { Text(stringResource(R.string.location_steer_calib_road_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.location_steer_calib_road_message),
                    style = MaterialTheme.typography.tboxBody,
                )
                Text(
                    text = stringResource(
                        R.string.location_steer_calib_road_live,
                        samples.size,
                        segmentCount,
                        rejectedCount,
                        steer?.let { String.format(Locale.US, "%.1f", it) } ?: "—",
                    ),
                    style = MaterialTheme.typography.tboxBody,
                    modifier = Modifier.padding(top = 8.dp),
                )
                preview?.let { p ->
                    Text(
                        text = stringResource(
                            R.string.location_steer_calib_road_preview,
                            String.format(Locale.US, "%.3f", p.scaleLeft),
                            String.format(Locale.US, "%.3f", p.scaleRight),
                            if (p.sign < 0) {
                                stringResource(R.string.location_drive_calib_sign_inverted)
                            } else {
                                stringResource(R.string.location_drive_calib_sign_normal)
                            },
                        ),
                        style = MaterialTheme.typography.tboxBody,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            when {
                !running -> {
                    Button(onClick = { running = true }) {
                        Text(stringResource(R.string.location_steer_calib_road_start))
                    }
                }
                preview != null -> {
                    Button(
                        onClick = {
                            val p = preview!!
                            SteerCalibrationStore.update(p)
                            onSave(p)
                            running = false
                            onFinished(true)
                        },
                    ) {
                        Text(stringResource(R.string.location_drive_calib_save))
                    }
                }
                else -> {
                    Button(
                        onClick = {
                            running = false
                            onFinished(false)
                        },
                    ) {
                        Text(stringResource(R.string.location_drive_calib_enough))
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    running = false
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.location_drive_calib_cancel))
            }
        },
    )
}
