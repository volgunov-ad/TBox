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
import vad.dashing.tbox.mbcan.UniversalCanRepository

/**
 * Optional background calibration for [MockCanSpeedMode.CONSTANT].
 *
 * Uses **private** sessions so the manual UI in [DriveCalibrationRepository] /
 * [SteerCalibrationUi] is never blocked or stolen.
 *
 * - While [constantAutoCalibEnabled] and [GeoCalibrationState.needsCalibration]:
 *   moving → drive session (speed; yaw scale when heading uses gyro).
 *   If heading uses the wheel → parallel steer session (scale profile + sign vs GNSS).
 * - Steer is independent of gyro: both fit GNSS, never each other.
 * - Steer may keep collecting after drive saved / the need flag cleared.
 * - While [idleYawBiasCalibEnabled] and parked: yaw-zero (bias + temperature stamp).
 *   Frequent while needs-calib + road auto; otherwise maintenance interval.
 * - Idle yaw-zero never clears the need flag.
 */
class ConstantDrAutoCalibJob(
    private val scope: CoroutineScope,
    private val mockPower: StateFlow<MockPowerState>,
    private val locationSource: StateFlow<LocationSource>,
    private val canSpeedMode: StateFlow<MockCanSpeedMode>,
    private val constantAutoCalibEnabled: StateFlow<Boolean>,
    private val idleYawBiasCalibEnabled: StateFlow<Boolean> =
        kotlinx.coroutines.flow.MutableStateFlow(false),
    private val headingSource: StateFlow<MockHeadingSource> =
        kotlinx.coroutines.flow.MutableStateFlow(MockHeadingSource.GYRO),
    private val junkFilterOn: () -> Boolean = { true },
    private val saveDrive: suspend (DriveCalibrationOffsets) -> Unit,
    private val saveGyroBias: suspend (GyroBiasOffsets) -> Unit,
    private val saveSteer: suspend (SteerCalibrationOffsets) -> Unit = {},
    private val markDriveCalibrated: suspend (Long) -> Unit,
    private val noteYawActivity: suspend (Long) -> Unit,
) {
    companion object {
        const val IDLE_MAX_SPEED_KMH = MockLocationJob.COURSE_HOLD_MIN_KMH
        /** When needs-calib + road auto: retry idle yaw-zero this often. */
        const val IDLE_YAW_RETRY_MS = 15_000L
        /**
         * Disk persist for idle yaw bias — RAM updates stay on [IDLE_YAW_RETRY_MS];
         * DataStore writes are sparse so a stuck needs-calib flag cannot thrash flash.
         */
        const val IDLE_YAW_DISK_MIN_INTERVAL_MS = 60_000L
        /**
         * Maintenance idle yaw-zero (idle-bias toggle on, no urgent need-calib).
         * Tracks gyro bias vs temperature while parked.
         */
        const val IDLE_YAW_MAINTENANCE_MS = 180_000L
        const val LOOP_MS = 500L
        const val DRIVE_TICK_MS = 100L
        /** After a timed-out drive attempt, wait before starting another. */
        const val DRIVE_ABORT_COOLDOWN_MS = 90_000L
    }

    private var job: Job? = null
    private var collectJob: Job? = null
    private var session: DriveCalibrationSession? = null
    private var steerSession: SteerCalibrationSession? = null
    private var lastIdleYawAttemptElapsedMs: Long = 0L
    private var lastIdleYawDiskPersistElapsedMs: Long = 0L
    private var lastDriveAbortElapsedMs: Long = 0L
    private var lastSteerAbortElapsedMs: Long = 0L

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
        cancelBackgroundSteer()
    }

    private suspend fun tick() {
        val power = mockPower.value
        val effectiveMode = power.effectiveCanSpeedMode(canSpeedMode.value)
        val mockPush = MockLocationJob.shouldPushMock(power, locationSource.value) &&
            effectiveMode.isConstantCalc
        if (!mockPush) {
            cancelBackgroundDrive()
            cancelBackgroundSteer()
            return
        }

        val heading = headingSource.value
        if (!ConstantDrAutoCalibPolicy.shouldCalibrateSteer(heading)) {
            cancelBackgroundSteer()
        }

        val needsAndAuto = constantAutoCalibEnabled.value &&
            GeoCalibrationState.needsCalibration.value
        val idleBiasOn = idleYawBiasCalibEnabled.value
        val now = SystemClock.elapsedRealtime()
        val canKmh = TripTelemetryRepository.accountingCarSpeed(now)
        val moving = canKmh != null && canKmh >= IDLE_MAX_SPEED_KMH
        val keepSteer = moving &&
            steerSession != null &&
            ConstantDrAutoCalibPolicy.keepSteerAfterNeedCleared(heading)

        if (needsAndAuto && moving) {
            ensureBackgroundDrive(now)
            if (ConstantDrAutoCalibPolicy.shouldCalibrateSteer(heading)) {
                ensureBackgroundSteer(now)
            }
            tryFinishBackgroundDrive(now, heading)
            tryFinishBackgroundSteer(now, heading)
        } else if (keepSteer) {
            cancelBackgroundDrive()
            tryFinishBackgroundSteer(now, heading)
        } else {
            cancelBackgroundDrive()
            cancelBackgroundSteer()
            if (!moving && idleBiasOn) {
                val interval = if (needsAndAuto) IDLE_YAW_RETRY_MS else IDLE_YAW_MAINTENANCE_MS
                maybeIdleYawZero(now, minIntervalMs = interval)
            }
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
        ensureCollectLoop()
    }

    private fun ensureBackgroundSteer(nowElapsedMs: Long) {
        if (steerSession != null) return
        if (nowElapsedMs - lastSteerAbortElapsedMs < DRIVE_ABORT_COOLDOWN_MS &&
            lastSteerAbortElapsedMs > 0L
        ) {
            return
        }
        val s = SteerCalibrationSession()
        s.start(nowElapsedMs)
        steerSession = s
        ensureCollectLoop()
    }

    private fun ensureCollectLoop() {
        if (collectJob?.isActive == true) return
        collectJob = scope.launch {
            while (isActive) {
                val drive = session
                val steer = steerSession
                if (drive == null && steer == null) break
                val headingNow = headingSource.value
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
                val reverse = MockLocationJob.isReverseEngagedNow()
                if (drive != null) {
                    val phase = drive.uiState().phase
                    if (phase != DriveCalibrationSession.Phase.IDLE &&
                        phase != DriveCalibrationSession.Phase.PREVIEW
                    ) {
                        val snap = DrSensorRepository.snapshot.value
                        val gyroAvailable = snap.gyroYaw != null && snap.gyroYaw.isFinite()
                        val yawDebiased = GyroBiasStore.applyYaw(snap.gyroYaw)
                        drive.onTick(
                            elapsedMs = now,
                            liveUsable = liveUsable,
                            live = live,
                            canKmh = can,
                            yawDebiasedDegPerSec = yawDebiased,
                            horizontalAccuracyM = accuracyM,
                            gyroAvailable = gyroAvailable,
                            reverseEngaged = reverse,
                            requireGyro = ConstantDrAutoCalibPolicy.driveRequiresGyro(headingNow),
                        )
                    }
                }
                if (steer != null) {
                    val phase = steer.uiState().phase
                    if (phase != SteerCalibrationSession.Phase.IDLE &&
                        phase != SteerCalibrationSession.Phase.PREVIEW
                    ) {
                        val rawWheel = UniversalCanRepository.steerAngleState.value
                        val centered = SteerCalibrationStore.applyZero(rawWheel)
                        steer.onTick(
                            elapsedMs = now,
                            liveUsable = liveUsable,
                            live = live,
                            canKmh = can,
                            centeredSteerDeg = centered,
                            horizontalAccuracyM = accuracyM,
                            reverseEngaged = reverse,
                        )
                    }
                }
                delay(DRIVE_TICK_MS)
            }
        }
    }

    private suspend fun tryFinishBackgroundDrive(
        nowElapsedMs: Long,
        heading: MockHeadingSource,
    ) {
        val s = session ?: return
        val requireYaw = ConstantDrAutoCalibPolicy.driveRequiresYaw(heading)
        if (s.isTimedOut(nowElapsedMs) && !s.isAutoReady(requireYaw)) {
            lastDriveAbortElapsedMs = nowElapsedMs
            cancelBackgroundDrive()
            return
        }
        if (!s.isAutoReady(requireYaw)) return
        val off = s.finishToPreview(
            System.currentTimeMillis(),
            DriveCalibrationStore.offsets,
        ) ?: return
        val ui = s.uiState()
        val usable = if (requireYaw) {
            !ui.previewLowQuality && (off.speedEstimated || off.yawEstimated)
        } else {
            off.speedEstimated
        }
        if (!usable) {
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

    private suspend fun tryFinishBackgroundSteer(
        nowElapsedMs: Long,
        heading: MockHeadingSource,
    ) {
        val s = steerSession ?: return
        if (s.isTimedOut(nowElapsedMs) && !s.isAutoReady()) {
            lastSteerAbortElapsedMs = nowElapsedMs
            cancelBackgroundSteer()
            return
        }
        if (!s.isAutoReady()) return
        val off = s.finishToPreview(
            System.currentTimeMillis(),
            SteerCalibrationStore.offsets,
        ) ?: return
        if (!off.scaleEstimated) {
            lastSteerAbortElapsedMs = nowElapsedMs
            cancelBackgroundSteer()
            return
        }
        cancelBackgroundSteer()
        lastSteerAbortElapsedMs = 0L
        saveSteer(off)
        if (ConstantDrAutoCalibPolicy.markSuccessOnSteerFinish(heading) &&
            GeoCalibrationState.needsCalibration.value
        ) {
            markDriveCalibrated(
                off.calibratedAtEpochMs.takeIf { it > 0L } ?: System.currentTimeMillis(),
            )
        }
    }

    private fun cancelBackgroundDrive() {
        session?.cancel()
        session = null
        if (steerSession == null) {
            collectJob?.cancel()
            collectJob = null
        }
    }

    private fun cancelBackgroundSteer() {
        steerSession?.cancel()
        steerSession = null
        if (session == null) {
            collectJob?.cancel()
            collectJob = null
        }
    }

    private suspend fun maybeIdleYawZero(nowElapsedMs: Long, minIntervalMs: Long) {
        if (nowElapsedMs - lastIdleYawAttemptElapsedMs < minIntervalMs) return
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
        val temp = DrSensorRepository.snapshot.value.gyroTemp
        val next = GyroBiasStore.offsets.copy(
            yawDegPerSec = result.mean,
            yawCalibTempC = temp?.takeIf { it.isFinite() },
        )
        GyroBiasStore.update(next)
        // Always refresh RAM; DataStore at most every IDLE_YAW_DISK_MIN_INTERVAL_MS.
        val now = SystemClock.elapsedRealtime()
        if (now - lastIdleYawDiskPersistElapsedMs >= IDLE_YAW_DISK_MIN_INTERVAL_MS) {
            lastIdleYawDiskPersistElapsedMs = now
            saveGyroBias(next)
            noteYawActivity(System.currentTimeMillis())
        }
    }
}
