package vad.dashing.tbox.location

import vad.dashing.tbox.LocValues
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * In-memory on-road steer calibration session (wheel + GNSS course + speed).
 *
 * Fits [SteerCalibrationMath] scale profile + sign vs GNSS. Does **not** touch
 * zero, deadzone, or wheelbase. Used by [ConstantDrAutoCalibJob]; the manual
 * UI keeps its own sample list.
 *
 * Public entry points are synchronized — the ticker may run off the main thread.
 */
class SteerCalibrationSession {
    enum class Phase {
        IDLE,
        RUNNING,
        PAUSED_BAD_FIX,
        PREVIEW,
    }

    data class UiState(
        val phase: Phase = Phase.IDLE,
        val fittedLeft: Int = 0,
        val fittedRight: Int = 0,
        val profileSpeedBuckets: Int = 0,
        val preview: SteerCalibrationOffsets? = null,
        val previewLowQuality: Boolean = false,
        val pause: DriveCalibrationMath.PauseKind = DriveCalibrationMath.PauseKind.NONE,
    )

    private val lock = Any()
    private val buf = ArrayList<SteerCalibrationMath.SteerSample>(512)
    private var phase: Phase = Phase.IDLE
    private var preview: SteerCalibrationOffsets? = null
    private var previewLowQuality: Boolean = false
    private var pause: DriveCalibrationMath.PauseKind = DriveCalibrationMath.PauseKind.NONE
    private var lastRecomputeAtMs: Long = 0L
    private var lastLat: Double? = null
    private var lastLon: Double? = null
    private var lastPosElapsedMs: Long = 0L
    private var startedAtElapsedMs: Long = -1L
    private var peakFittedLeft: Int = 0
    private var peakFittedRight: Int = 0
    private var peakProfileBuckets: Int = 0
    private var lastGoodEstimate: SteerCalibrationMath.SteerScaleEstimate? = null

    fun uiState(): UiState = synchronized(lock) {
        UiState(
            phase = phase,
            fittedLeft = peakFittedLeft,
            fittedRight = peakFittedRight,
            profileSpeedBuckets = peakProfileBuckets,
            preview = preview,
            previewLowQuality = previewLowQuality,
            pause = if (phase == Phase.PAUSED_BAD_FIX) pause else DriveCalibrationMath.PauseKind.NONE,
        )
    }

    fun start(startedAtElapsedMs: Long) = synchronized(lock) {
        buf.clear()
        preview = null
        previewLowQuality = false
        pause = DriveCalibrationMath.PauseKind.NONE
        lastRecomputeAtMs = 0L
        lastLat = null
        lastLon = null
        lastPosElapsedMs = 0L
        peakFittedLeft = 0
        peakFittedRight = 0
        peakProfileBuckets = 0
        lastGoodEstimate = null
        this.startedAtElapsedMs = startedAtElapsedMs
        phase = Phase.RUNNING
    }

    fun cancel() = synchronized(lock) {
        buf.clear()
        preview = null
        previewLowQuality = false
        pause = DriveCalibrationMath.PauseKind.NONE
        lastGoodEstimate = null
        startedAtElapsedMs = -1L
        peakFittedLeft = 0
        peakFittedRight = 0
        peakProfileBuckets = 0
        phase = Phase.IDLE
    }

    fun finishToPreview(
        nowEpochMs: Long,
        previous: SteerCalibrationOffsets = SteerCalibrationStore.offsets,
    ): SteerCalibrationOffsets? = synchronized(lock) {
        if (phase == Phase.IDLE) return null
        recomputeUnlocked()
        val est = lastGoodEstimate
        val off = if (est != null) {
            SteerCalibrationMath.mergeWithPrevious(est, previous, nowEpochMs)
        } else {
            previous.copy(calibratedAtEpochMs = nowEpochMs, scaleEstimated = false)
        }
        preview = off
        previewLowQuality = !off.scaleEstimated
        phase = Phase.PREVIEW
        pause = DriveCalibrationMath.PauseKind.NONE
        return off
    }

    /**
     * @return true if a steer sample was accepted into the buffer
     */
    fun onTick(
        elapsedMs: Long,
        liveUsable: Boolean,
        live: LocValues,
        canKmh: Float?,
        centeredSteerDeg: Float?,
        horizontalAccuracyM: Float?,
        reverseEngaged: Boolean = false,
    ): Boolean = synchronized(lock) {
        if (phase != Phase.RUNNING && phase != Phase.PAUSED_BAD_FIX) return false

        if (!DriveCalibrationMath.shouldCollectRoadSample(reverseEngaged)) {
            buf.clear()
            lastLat = null
            lastLon = null
            lastPosElapsedMs = 0L
            pause(DriveCalibrationMath.PauseKind.REVERSE)
            return false
        }

        val can = canKmh?.takeIf { it.isFinite() && it >= 0f }
        if (can == null) {
            pause(DriveCalibrationMath.PauseKind.NO_CAN)
            return false
        }

        val accuracyOk = horizontalAccuracyM == null ||
            (horizontalAccuracyM.isFinite() &&
                horizontalAccuracyM > 0f &&
                horizontalAccuracyM <= DriveCalibrationMath.MAX_HORIZONTAL_ACCURACY_M)
        val gnssSpeed = live.speed.takeIf { it.isFinite() && it >= 0f }
        when {
            !live.locateStatus || !MockLocationJob.hasValidCoordinates(live) -> {
                pause(DriveCalibrationMath.PauseKind.BAD_FIX)
                return false
            }
            !liveUsable -> {
                pause(DriveCalibrationMath.PauseKind.BAD_FIX_JUNK)
                return false
            }
            !accuracyOk -> {
                pause(DriveCalibrationMath.PauseKind.BAD_FIX_ACCURACY)
                return false
            }
            gnssSpeed == null -> {
                pause(DriveCalibrationMath.PauseKind.BAD_FIX_NO_SPEED)
                return false
            }
        }

        if (isCoordJump(live, elapsedMs)) {
            pause(DriveCalibrationMath.PauseKind.COURSE_JUMP)
            return false
        }

        phase = Phase.RUNNING
        pause = DriveCalibrationMath.PauseKind.NONE
        rememberPos(live, elapsedMs)

        val course = live.trueDirection.takeIf { it.isFinite() && it != 0f }
        val centered = centeredSteerDeg?.takeIf { it.isFinite() }
        val speed = can.takeIf { it >= SteerCalibrationMath.MIN_SPEED_KMH * 0.5f }
            ?: gnssSpeed.takeIf { it >= SteerCalibrationMath.MIN_SPEED_KMH * 0.5f }
        val accepted = if (centered != null && course != null && speed != null) {
            buf.add(
                SteerCalibrationMath.SteerSample(
                    centeredSteerDeg = centered,
                    bearingDeg = course,
                    speedKmh = speed,
                    elapsedMs = elapsedMs,
                ),
            )
            true
        } else {
            false
        }
        trim(elapsedMs)
        if (elapsedMs - lastRecomputeAtMs >= RECOMPUTE_EVERY_MS) {
            lastRecomputeAtMs = elapsedMs
            recomputeUnlocked()
        }
        return accepted
    }

    fun isAutoReady(): Boolean = synchronized(lock) {
        if (phase != Phase.RUNNING && phase != Phase.PAUSED_BAD_FIX) return false
        lastGoodEstimate != null &&
            peakFittedLeft >= SteerCalibrationMath.MIN_SEGMENTS_PER_SIDE &&
            peakFittedRight >= SteerCalibrationMath.MIN_SEGMENTS_PER_SIDE &&
            peakProfileBuckets >= SteerCalibrationMath.MIN_PROFILE_SPEED_BUCKETS
    }

    fun isTimedOut(nowElapsedMs: Long): Boolean = synchronized(lock) {
        if (phase != Phase.RUNNING && phase != Phase.PAUSED_BAD_FIX) return false
        if (startedAtElapsedMs < 0L) return false
        nowElapsedMs - startedAtElapsedMs >= SESSION_TIMEOUT_MS
    }

    private fun pause(kind: DriveCalibrationMath.PauseKind) {
        phase = Phase.PAUSED_BAD_FIX
        pause = kind
    }

    private fun recomputeUnlocked() {
        if (buf.size < 8) return
        val (segs, _) = SteerCalibrationMath.collectSteerSegments(buf)
        val attempt = SteerCalibrationMath.attemptSteerScaleAndSign(segs)
        peakFittedLeft = maxOf(peakFittedLeft, attempt.fittedLeft)
        peakFittedRight = maxOf(peakFittedRight, attempt.fittedRight)
        peakProfileBuckets = maxOf(peakProfileBuckets, attempt.profileSpeedBuckets)
        attempt.estimate?.let { lastGoodEstimate = it }
    }

    private fun rememberPos(live: LocValues, elapsedMs: Long) {
        if (live.latitude != 0.0 || live.longitude != 0.0) {
            lastLat = live.latitude
            lastLon = live.longitude
            lastPosElapsedMs = elapsedMs
        }
    }

    private fun isCoordJump(live: LocValues, elapsedMs: Long): Boolean {
        val prevLat = lastLat ?: return false
        val prevLon = lastLon ?: return false
        if (live.latitude == 0.0 && live.longitude == 0.0) return false
        val dtSec = (elapsedMs - lastPosElapsedMs) / 1000.0
        if (dtSec <= 0.05 || dtSec > 2.0) return false
        val distM = haversineM(prevLat, prevLon, live.latitude, live.longitude)
        val maxM = (live.speed.coerceAtLeast(5f) / 3.6) * dtSec * 3.5 + 25.0
        return distM > maxM
    }

    private fun trim(nowElapsedMs: Long) {
        val minKeep = nowElapsedMs - KEEP_MS
        while (buf.isNotEmpty() && buf.first().elapsedMs < minKeep) {
            buf.removeAt(0)
        }
        while (buf.size > MAX_SAMPLES) buf.removeAt(0)
    }

    companion object {
        const val SESSION_TIMEOUT_MS = DriveCalibrationSession.SESSION_TIMEOUT_MS
        private const val KEEP_MS = 4 * 60_000L
        private const val MAX_SAMPLES = 2_400
        private const val RECOMPUTE_EVERY_MS = 1_000L

        private fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6_371_000.0
            val p1 = Math.toRadians(lat1)
            val p2 = Math.toRadians(lat2)
            val dPhi = Math.toRadians(lat2 - lat1)
            val dLam = Math.toRadians(lon2 - lon1)
            val a = sin(dPhi / 2) * sin(dPhi / 2) +
                cos(p1) * cos(p2) * sin(dLam / 2) * sin(dLam / 2)
            return 2 * r * asin(sqrt(a.coerceIn(0.0, 1.0)))
        }
    }
}
