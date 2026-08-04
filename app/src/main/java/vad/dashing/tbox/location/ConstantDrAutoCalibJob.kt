package vad.dashing.tbox.location

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import vad.dashing.tbox.TboxRepository
import vad.dashing.tbox.TripTelemetryRepository
import vad.dashing.tbox.drsensor.DrSensorRepository
import vad.dashing.tbox.esp.LocationSource

/**
 * Background calibration for [MockCanSpeedMode.CONSTANT] when [GeoCalibrationState.needsCalibration].
 *
 * Uses a **private** [DriveCalibrationSession] so the manual UI session in
 * [DriveCalibrationRepository] is never blocked or stolen.
 *
 * - While moving: collect drive samples; auto-save on ready (clears need flag).
 * - Wall-clock session timeout without ready → abort attempt (need flag stays); cooldown before retry.
 * - While idle: sample yaw zero and save bias (timestamp only — does **not** clear need flag).
 */
class ConstantDrAutoCalibJob(
    private val scope: CoroutineScope,
    private val mockLocation: StateFlow<Boolean>,
    private val locationSource: StateFlow<LocationSource>,
    private val canSpeedMode: StateFlow<MockCanSpeedMode>,
    private val junkFilterOn: () -> Boolean = { true },
    private val saveDrive: suspend (DriveCalibrationOffsets) -> Unit,
    private val saveGyroBias: suspend (GyroBiasOffsets) -> Unit,
    private val markDriveCalibrated: suspend (Long) -> Unit,
    private val noteYawActivity: suspend (Long) -> Unit,
) {
    companion object {
        const val IDLE_MAX_SPEED_KMH = MockLocationJob.COURSE_HOLD_MIN_KMH
        const val IDLE_YAW_RETRY_MS = 15_000L
        const val LOOP_MS = 500L
        const val DRIVE_TICK_MS = 100L
        /** After a timed-out drive attempt, wait before starting another. */
        const val DRIVE_ABORT_COOLDOWN_MS = 90_000L
    }

    private var job: Job? = null
    private var driveJob: Job? = null
    private var session: DriveCalibrationSession? = null
    private var lastIdleYawAttemptElapsedMs: Long = 0L
    private var lastDriveAbortElapsedMs: Long = 0L

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
        cancelBackgroundDrive()
    }

    private suspend fun tick() {
        val active = MockLocationJob.shouldPushMock(mockLocation.value, locationSource.value) &&
            canSpeedMode.value.isConstantCalc
        if (!active || !GeoCalibrationState.needsCalibration.value) {
            cancelBackgroundDrive()
            return
        }

        // Fresh drive calib + only short mismatch should not have set the flag;
        // if flag is set, proceed. (Gate is in MockLocationJob streak length.)

        val now = SystemClock.elapsedRealtime()
        val canKmh = TripTelemetryRepository.accountingCarSpeed(now)
        val moving = canKmh != null && canKmh >= IDLE_MAX_SPEED_KMH

        if (moving) {
            ensureBackgroundDrive(now)
            tryFinishBackgroundDrive(now)
        } else {
            cancelBackgroundDrive()
            maybeIdleYawZero(now)
        }
    }

    private fun ensureBackgroundDrive(nowElapsedMs: Long) {
        if (session != null) return
        if (nowElapsedMs - lastDriveAbortElapsedMs < DRIVE_ABORT_COOLDOWN_MS &&
            lastDriveAbortElapsedMs > 0L
        ) {
            return
        }
        val s = DriveCalibrationSession()
        s.start(nowElapsedMs)
        session = s
        if (driveJob?.isActive == true) return
        driveJob = scope.launch {
            while (isActive) {
                val sess = session ?: break
                val phase = sess.uiState().phase
                if (phase == DriveCalibrationSession.Phase.IDLE ||
                    phase == DriveCalibrationSession.Phase.PREVIEW
                ) {
                    delay(DRIVE_TICK_MS)
                    continue
                }
                val now = SystemClock.elapsedRealtime()
                val live = TboxRepository.locValues.value
                val can = TripTelemetryRepository.accountingCarSpeed(now)
                val liveUsable = MockLocationJob.isLiveUsable(
                    live,
                    junkFilterOn(),
                    can,
                    now,
                )
                val accuracyM = LocationMockManager.horizontalAccuracyMeters(
                    hdop = live.hdop,
                    retainingFix = false,
                    hrms = live.hrms,
                )
                val snap = DrSensorRepository.snapshot.value
                val gyroAvailable = snap.gyroYaw != null && snap.gyroYaw.isFinite()
                val yawDebiased = GyroBiasStore.applyYaw(snap.gyroYaw)
                sess.onTick(
                    elapsedMs = now,
                    liveUsable = liveUsable,
                    live = live,
                    canKmh = can,
                    yawDebiasedDegPerSec = yawDebiased,
                    horizontalAccuracyM = accuracyM,
                    gyroAvailable = gyroAvailable,
                )
                delay(DRIVE_TICK_MS)
            }
        }
    }

    private suspend fun tryFinishBackgroundDrive(nowElapsedMs: Long) {
        val s = session ?: return
        if (s.isTimedOut(nowElapsedMs) && !s.isAutoReady()) {
            // Failed attempt — keep needsCalibration; cooldown before retry.
            lastDriveAbortElapsedMs = nowElapsedMs
            cancelBackgroundDrive()
            return
        }
        if (!s.isAutoReady()) return
        val off = s.finishToPreview(
            System.currentTimeMillis(),
            DriveCalibrationStore.offsets,
        ) ?: return
        val ui = s.uiState()
        if (ui.previewLowQuality || (!off.speedEstimated && !off.yawEstimated)) {
            // Restart collection quietly.
            lastDriveAbortElapsedMs = nowElapsedMs
            cancelBackgroundDrive()
            return
        }
        cancelBackgroundDrive()
        lastDriveAbortElapsedMs = 0L
        saveDrive(off)
        markDriveCalibrated(
            off.calibratedAtEpochMs.takeIf { it > 0L } ?: System.currentTimeMillis(),
        )
    }

    private fun cancelBackgroundDrive() {
        driveJob?.cancel()
        driveJob = null
        session?.cancel()
        session = null
    }

    private suspend fun maybeIdleYawZero(nowElapsedMs: Long) {
        if (nowElapsedMs - lastIdleYawAttemptElapsedMs < IDLE_YAW_RETRY_MS) return
        lastIdleYawAttemptElapsedMs = nowElapsedMs
        val yawSamples = ArrayList<Float>(64)
        val start = SystemClock.elapsedRealtime()
        val duration = GyroCalibrationMath.CALIBRATION_DURATION_MS
        while (SystemClock.elapsedRealtime() - start < duration) {
            val can = TripTelemetryRepository.accountingCarSpeed(SystemClock.elapsedRealtime())
            if (can != null && can >= IDLE_MAX_SPEED_KMH) {
                return
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
        // Timestamp only — need flag stays until drive calib succeeds.
        noteYawActivity(System.currentTimeMillis())
    }
}
