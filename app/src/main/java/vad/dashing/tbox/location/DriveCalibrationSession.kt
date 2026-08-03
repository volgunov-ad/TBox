package vad.dashing.tbox.location

import vad.dashing.tbox.LocValues
import vad.dashing.tbox.location.DriveCalibrationMath.Estimates
import vad.dashing.tbox.location.DriveCalibrationMath.Hint
import vad.dashing.tbox.location.DriveCalibrationMath.PauseKind
import vad.dashing.tbox.location.DriveCalibrationMath.SpeedSample
import vad.dashing.tbox.location.DriveCalibrationMath.YawSample
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * In-memory drive calibration session: raw GNSS + CAN + debiased yaw only.
 */
class DriveCalibrationSession {
    enum class Phase {
        IDLE,
        RUNNING,
        PAUSED_BAD_FIX,
        PREVIEW,
    }

    data class UiState(
        val phase: Phase = Phase.IDLE,
        val estimates: Estimates = Estimates(),
        val hint: Hint = Hint.INTRO,
        val pause: PauseKind = PauseKind.NONE,
        val preview: DriveCalibrationOffsets? = null,
        /** True when preview has no usable new estimates (Save should stay off). */
        val previewLowQuality: Boolean = false,
    )

    private val speedBuf = ArrayList<SpeedSample>(512)
    private val yawBuf = ArrayList<YawSample>(512)
    private var phase: Phase = Phase.IDLE
    private var estimates: Estimates = Estimates()
    private var preview: DriveCalibrationOffsets? = null
    private var previewLowQuality: Boolean = false
    private var pause: PauseKind = PauseKind.NONE
    private var lastRecomputeAtMs: Long = 0L
    private var lastYawSample: YawSample? = null
    private var lastLat: Double? = null
    private var lastLon: Double? = null
    private var lastPosElapsedMs: Long = 0L

    fun uiState(): UiState {
        val active = phase != Phase.IDLE
        val paused = phase == Phase.PAUSED_BAD_FIX
        return UiState(
            phase = phase,
            estimates = estimates,
            hint = DriveCalibrationMath.hint(
                estimates = estimates,
                pause = if (paused) pause else PauseKind.NONE,
                hasSession = active,
                previewLowQuality = phase == Phase.PREVIEW && previewLowQuality,
            ),
            pause = if (paused) pause else PauseKind.NONE,
            preview = preview,
            previewLowQuality = previewLowQuality,
        )
    }

    fun start() {
        speedBuf.clear()
        yawBuf.clear()
        estimates = Estimates()
        preview = null
        previewLowQuality = false
        pause = PauseKind.NONE
        lastRecomputeAtMs = 0L
        lastYawSample = null
        lastLat = null
        lastLon = null
        lastPosElapsedMs = 0L
        phase = Phase.RUNNING
    }

    fun cancel() {
        speedBuf.clear()
        yawBuf.clear()
        estimates = Estimates()
        preview = null
        previewLowQuality = false
        pause = PauseKind.NONE
        lastYawSample = null
        phase = Phase.IDLE
    }

    /**
     * Manual or auto finish → preview without persisting.
     * Merges with [previous] so unevaluated channels keep prior scales.
     */
    fun finishToPreview(
        nowEpochMs: Long,
        previous: DriveCalibrationOffsets = DriveCalibrationStore.offsets,
    ): DriveCalibrationOffsets? {
        if (phase == Phase.IDLE) return null
        recompute()
        val off = DriveCalibrationMath.mergeWithPrevious(estimates, previous, nowEpochMs)
        preview = off
        previewLowQuality = !off.speedEstimated && !off.yawEstimated
        phase = Phase.PREVIEW
        pause = PauseKind.NONE
        return off
    }

    /**
     * @return true if sample accepted into buffers
     */
    fun onTick(
        elapsedMs: Long,
        liveUsable: Boolean,
        live: LocValues,
        canKmh: Float?,
        yawDebiasedDegPerSec: Float?,
        horizontalAccuracyM: Float?,
        gyroAvailable: Boolean,
    ): Boolean {
        if (phase != Phase.RUNNING && phase != Phase.PAUSED_BAD_FIX) return false

        val can = canKmh?.takeIf { it.isFinite() && it >= 0f }
        if (can == null) {
            pause(PauseKind.NO_CAN)
            return false
        }
        if (!gyroAvailable) {
            pause(PauseKind.NO_GYRO)
            return false
        }

        val accuracyOk = horizontalAccuracyM == null ||
            (horizontalAccuracyM.isFinite() &&
                horizontalAccuracyM > 0f &&
                horizontalAccuracyM <= DriveCalibrationMath.MAX_HORIZONTAL_ACCURACY_M)
        val gnssSpeed = live.speed.takeIf { it.isFinite() && it >= 0f }
        val okFix = liveUsable && live.locateStatus && accuracyOk && gnssSpeed != null
        if (!okFix) {
            pause(PauseKind.BAD_FIX)
            return false
        }

        if (isCoordJump(live, elapsedMs)) {
            pause(PauseKind.COURSE_JUMP)
            return false
        }

        val bearing = live.trueDirection.takeIf {
            it.isFinite() && it != 0f && gnssSpeed!! >= DriveCalibrationMath.MIN_SPEED_KMH
        }
        val yaw = yawDebiasedDegPerSec?.takeIf { it.isFinite() }
        if (bearing != null && yaw != null) {
            val ys = YawSample(
                elapsedMs = elapsedMs,
                yawRateDegPerSec = yaw,
                bearingDeg = bearing,
                speedKmh = gnssSpeed!!,
            )
            if (DriveCalibrationMath.isCourseJump(lastYawSample, ys)) {
                pause(PauseKind.COURSE_JUMP)
                lastYawSample = ys
                return false
            }
            lastYawSample = ys
            yawBuf.add(ys)
        }

        phase = Phase.RUNNING
        pause = PauseKind.NONE
        speedBuf.add(SpeedSample(elapsedMs, gnssSpeed!!, can))
        rememberPos(live, elapsedMs)
        trim(elapsedMs)
        if (elapsedMs - lastRecomputeAtMs >= RECOMPUTE_EVERY_MS || speedBuf.size % 10 == 0) {
            lastRecomputeAtMs = elapsedMs
            recompute()
        }
        return true
    }

    fun isAutoReady(): Boolean =
        estimates.ready && (phase == Phase.RUNNING || phase == Phase.PAUSED_BAD_FIX)

    private fun pause(kind: PauseKind) {
        phase = Phase.PAUSED_BAD_FIX
        pause = kind
    }

    private fun recompute() {
        estimates = DriveCalibrationMath.buildEstimates(speedBuf, yawBuf)
    }

    private fun rememberPos(live: LocValues, elapsedMs: Long) {
        if (live.latitude != 0.0 || live.longitude != 0.0) {
            lastLat = live.latitude
            lastLon = live.longitude
            lastPosElapsedMs = elapsedMs
        }
    }

    /** Unrealistic position jump vs CAN speed → pause (same class as course junk). */
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
        while (speedBuf.isNotEmpty() && speedBuf.first().elapsedMs < minKeep && speedBuf.size > MAX_SAMPLES) {
            speedBuf.removeAt(0)
        }
        while (yawBuf.isNotEmpty() && yawBuf.first().elapsedMs < minKeep && yawBuf.size > MAX_SAMPLES) {
            yawBuf.removeAt(0)
        }
        while (speedBuf.size > MAX_SAMPLES) speedBuf.removeAt(0)
        while (yawBuf.size > MAX_SAMPLES) yawBuf.removeAt(0)
    }

    companion object {
        private const val KEEP_MS = 8 * 60_000L
        private const val MAX_SAMPLES = 6_000
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
