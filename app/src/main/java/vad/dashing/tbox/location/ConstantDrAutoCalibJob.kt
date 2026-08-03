package vad.dashing.tbox.location

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import vad.dashing.tbox.TripTelemetryRepository
import vad.dashing.tbox.drsensor.DrSensorRepository
import vad.dashing.tbox.esp.LocationSource

/**
 * Background calibration for [MockCanSpeedMode.CONSTANT] when [GeoCalibrationState.needsCalibration].
 *
 * - While moving: runs [DriveCalibrationRepository] session and auto-saves on success.
 * - While idle: samples yaw for [GyroCalibrationMath.CALIBRATION_DURATION_MS] and saves bias.
 * Does not interrupt a manual (UI-owned) drive calibration session.
 */
class ConstantDrAutoCalibJob(
    private val scope: CoroutineScope,
    private val mockLocation: StateFlow<Boolean>,
    private val locationSource: StateFlow<LocationSource>,
    private val canSpeedMode: StateFlow<MockCanSpeedMode>,
    private val saveDrive: suspend (DriveCalibrationOffsets) -> Unit,
    private val saveGyroBias: suspend (GyroBiasOffsets) -> Unit,
    private val markCalibrated: suspend (Long) -> Unit,
) {
    companion object {
        const val IDLE_MAX_SPEED_KMH = MockLocationJob.COURSE_HOLD_MIN_KMH
        const val IDLE_YAW_RETRY_MS = 15_000L
        const val LOOP_MS = 500L
    }

    private var job: Job? = null
    private var backgroundDriveOwned: Boolean = false
    private var lastIdleYawAttemptElapsedMs: Long = 0L

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                tick()
                delay(LOOP_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        cancelBackgroundDriveIfOwned()
    }

    private suspend fun tick() {
        val active = MockLocationJob.shouldPushMock(mockLocation.value, locationSource.value) &&
            canSpeedMode.value.isConstantCalc
        if (!active || !GeoCalibrationState.needsCalibration.value) {
            cancelBackgroundDriveIfOwned()
            return
        }

        val now = SystemClock.elapsedRealtime()
        val canKmh = TripTelemetryRepository.accountingCarSpeed(now)
        val moving = canKmh != null && canKmh >= IDLE_MAX_SPEED_KMH

        if (moving) {
            ensureBackgroundDrive()
            tryFinishBackgroundDrive()
        } else {
            cancelBackgroundDriveIfOwned()
            maybeIdleYawZero(now)
        }
    }

    private fun ensureBackgroundDrive() {
        val phase = DriveCalibrationRepository.uiState.value.phase
        if (phase != DriveCalibrationSession.Phase.IDLE) {
            // Manual or already running — do not steal.
            if (!backgroundDriveOwned) return
        }
        if (phase == DriveCalibrationSession.Phase.IDLE) {
            DriveCalibrationRepository.beginSession()
            backgroundDriveOwned = true
        }
    }

    private suspend fun tryFinishBackgroundDrive() {
        if (!backgroundDriveOwned) return
        val ui = DriveCalibrationRepository.uiState.value
        when (ui.phase) {
            DriveCalibrationSession.Phase.RUNNING,
            DriveCalibrationSession.Phase.PAUSED_BAD_FIX,
            -> {
                if (DriveCalibrationRepository.isSessionAutoReady()) {
                    DriveCalibrationRepository.finishEnough()
                }
            }
            DriveCalibrationSession.Phase.PREVIEW -> {
                val preview = ui.preview
                if (preview != null &&
                    !ui.previewLowQuality &&
                    (preview.speedEstimated || preview.yawEstimated)
                ) {
                    val off = DriveCalibrationRepository.takePreviewForSave(announce = false)
                    if (off != null) {
                        backgroundDriveOwned = false
                        saveDrive(off)
                        markCalibrated(
                            off.calibratedAtEpochMs.takeIf { it > 0L }
                                ?: System.currentTimeMillis(),
                        )
                    }
                } else if (ui.previewLowQuality) {
                    // Discard weak auto preview and keep collecting.
                    DriveCalibrationRepository.cancelSession(announce = false)
                    backgroundDriveOwned = false
                }
            }
            DriveCalibrationSession.Phase.IDLE -> {
                backgroundDriveOwned = false
            }
        }
    }

    private fun cancelBackgroundDriveIfOwned() {
        if (!backgroundDriveOwned) return
        DriveCalibrationRepository.cancelSession(announce = false)
        backgroundDriveOwned = false
    }

    private suspend fun maybeIdleYawZero(nowElapsedMs: Long) {
        if (nowElapsedMs - lastIdleYawAttemptElapsedMs < IDLE_YAW_RETRY_MS) return
        // Do not run while user is in a drive session.
        if (DriveCalibrationRepository.uiState.value.phase != DriveCalibrationSession.Phase.IDLE) {
            return
        }
        lastIdleYawAttemptElapsedMs = nowElapsedMs
        val yawSamples = ArrayList<Float>(64)
        val start = SystemClock.elapsedRealtime()
        val duration = GyroCalibrationMath.CALIBRATION_DURATION_MS
        while (SystemClock.elapsedRealtime() - start < duration) {
            val can = TripTelemetryRepository.accountingCarSpeed(SystemClock.elapsedRealtime())
            if (can != null && can >= IDLE_MAX_SPEED_KMH) {
                return // started moving
            }
            DrSensorRepository.snapshot.value.gyroYaw?.let { yawSamples.add(it) }
            delay(50)
        }
        val result = GyroCalibrationMath.averageWithRangeCheck(
            yawSamples,
            GyroCalibrationMath.MAX_STATIC_RANGE_DEG_PER_SEC,
        ) ?: return
        if (!result.accepted) return
        val next = GyroBiasStore.offsets.copy(yawDegPerSec = result.mean)
        saveGyroBias(next)
        markCalibrated(System.currentTimeMillis())
    }
}
