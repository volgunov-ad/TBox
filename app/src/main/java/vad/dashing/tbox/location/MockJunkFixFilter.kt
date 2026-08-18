package vad.dashing.tbox.location

import vad.dashing.tbox.LocValues
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Asymmetric debounce for GNSS?CAN speed mismatch only.
 * Latch junk after [TO_JUNK_DEBOUNCE_MS] of continuous raw mismatch;
 * clear latch after [TO_OK_DEBOUNCE_MS] of continuous match.
 * Shared by truth / mock so both see the same state.
 */
object JunkSpeedMismatchDebouncer {
    /** Continuous mismatch before treating speed as junk. */
    const val TO_JUNK_DEBOUNCE_MS = 3_000L

    /** Continuous match before clearing speed-junk latch. */
    const val TO_OK_DEBOUNCE_MS = 2_000L

    @Volatile
    private var latchedJunk: Boolean = false
    private var mismatchSinceElapsedMs: Long? = null
    private var matchSinceElapsedMs: Long? = null

    fun isLatched(): Boolean = latchedJunk

    fun reset() {
        latchedJunk = false
        mismatchSinceElapsedMs = null
        matchSinceElapsedMs = null
    }

    /**
     * @param rawMismatch instantaneous [MockJunkFixFilter.isSpeedMismatch]
     * @param carSpeedKnown false ? skip (clear timers, unlatch); GNSS lag check needs CAN
     * @return whether speed mismatch is currently latched as junk
     */
    @Synchronized
    fun update(nowElapsedMs: Long, rawMismatch: Boolean, carSpeedKnown: Boolean): Boolean {
        if (!carSpeedKnown) {
            mismatchSinceElapsedMs = null
            matchSinceElapsedMs = null
            latchedJunk = false
            return false
        }
        if (rawMismatch) {
            matchSinceElapsedMs = null
            if (mismatchSinceElapsedMs == null) {
                mismatchSinceElapsedMs = nowElapsedMs
            }
            if (!latchedJunk &&
                nowElapsedMs - mismatchSinceElapsedMs!! >= TO_JUNK_DEBOUNCE_MS
            ) {
                latchedJunk = true
            }
        } else {
            mismatchSinceElapsedMs = null
            if (matchSinceElapsedMs == null) {
                matchSinceElapsedMs = nowElapsedMs
            }
            if (latchedJunk &&
                nowElapsedMs - matchSinceElapsedMs!! >= TO_OK_DEBOUNCE_MS
            ) {
                latchedJunk = false
            }
        }
        return latchedJunk
    }
}

/**
 * Optional sanity gate for live GNSS fixes (truth + mock rejection when enabled).
 * Altitude / absurd speed / accuracy / coord-or-altitude jump reject immediately.
 * GNSS?CAN speed mismatch uses [JunkSpeedMismatchDebouncer] (3 s ? junk, 2 s ? ok).
 */
object MockJunkFixFilter {
    /** Reject when horizontal accuracy (GST / HDOP) exceeds this. */
    const val MAX_HORIZONTAL_ACCURACY_M = 100f

    /** Reject absurd GNSS speed (km/h). */
    const val MAX_GNSS_SPEED_KMH = 230f

    const val MIN_ALTITUDE_M = -200.0
    const val MAX_ALTITUDE_M = 5_000.0

    /**
     * Below this max(gps, car) speed, compare with absolute [LOW_SPEED_ABS_TOLERANCE_KMH];
     * at/above — relative [SPEED_RELATIVE_TOLERANCE].
     */
    const val SPEED_COMPARE_FLOOR_KMH = 15f

    /** HWGPS-style relative mismatch (fraction of max(gps, car)). */
    const val SPEED_RELATIVE_TOLERANCE = 0.10f

    /** Absolute mismatch at low speed (same ballpark as [LocationTruthEvaluator]). */
    const val LOW_SPEED_ABS_TOLERANCE_KMH = 10f

    /** Instant reject when altitude jumps more than this vs the last accepted fix. */
    const val MAX_ALTITUDE_JUMP_M = 400.0

    /** Same window as [DriveCalibrationSession] coord-jump: skip if the gap is longer. */
    const val JUMP_MAX_DT_SEC = 2.0

    const val JUMP_MIN_DT_SEC = 0.05

    /** Extra metres on top of CAN/GNSS speed × time (same ballpark as drive-calib). */
    const val JUMP_SLACK_M = 25.0

    const val JUMP_SPEED_MARGIN = 3.5

    enum class RejectReason {
        ALTITUDE,
        GNSS_SPEED,
        ACCURACY,
        SPEED_MISMATCH,
        COORD_JUMP,
        ALTITUDE_JUMP,
    }

    data class Result(
        val accepted: Boolean,
        val reason: RejectReason? = null,
    ) {
        companion object {
            val OK = Result(accepted = true)
        }
    }

    /**
     * Instant checks only (no speed-mismatch debounce). Used by tests and as the
     * first stage of [evaluate].
     */
    fun evaluateInstantExceptSpeed(loc: LocValues): Result {
        if (loc.altitude < MIN_ALTITUDE_M || loc.altitude > MAX_ALTITUDE_M) {
            return Result(false, RejectReason.ALTITUDE)
        }
        if (!loc.speed.isFinite() || loc.speed < 0f || loc.speed > MAX_GNSS_SPEED_KMH) {
            return Result(false, RejectReason.GNSS_SPEED)
        }
        val accuracyM = LocationMockManager.horizontalAccuracyMeters(
            hdop = loc.hdop,
            retainingFix = false,
            hrms = loc.hrms,
        )
        val hasAccuracySignal = (loc.hrms != null && loc.hrms > 0f && loc.hrms.isFinite()) ||
            (loc.hdop != null && loc.hdop > 0f && loc.hdop.isFinite())
        if (hasAccuracySignal && accuracyM > MAX_HORIZONTAL_ACCURACY_M) {
            return Result(false, RejectReason.ACCURACY)
        }
        return Result.OK
    }

    /**
     * Full gate including debounced speed mismatch.
     * @param nowElapsedMs [android.os.SystemClock.elapsedRealtime] (or test clock)
     */
    @Volatile
    private var anchorLat: Double? = null
    @Volatile
    private var anchorLon: Double? = null
    @Volatile
    private var anchorAlt: Double? = null
    @Volatile
    private var anchorElapsedMs: Long = 0L

    fun resetSession() {
        JunkSpeedMismatchDebouncer.reset()
        synchronized(this) {
            anchorLat = null
            anchorLon = null
            anchorAlt = null
            anchorElapsedMs = 0L
        }
    }

    fun evaluate(loc: LocValues, carSpeedKmh: Float?, nowElapsedMs: Long): Result {
        val instant = evaluateInstantExceptSpeed(loc)
        if (!instant.accepted) return instant
        val jump = evaluateJump(loc, carSpeedKmh, nowElapsedMs)
        if (!jump.accepted) return jump
        val rawMismatch = isSpeedMismatch(loc.speed, carSpeedKmh)
        val latched = JunkSpeedMismatchDebouncer.update(
            nowElapsedMs = nowElapsedMs,
            rawMismatch = rawMismatch,
            carSpeedKnown = carSpeedKmh != null,
        )
        if (latched) {
            return Result(false, RejectReason.SPEED_MISMATCH)
        }
        rememberAccepted(loc, nowElapsedMs)
        return Result.OK
    }

    fun isAcceptable(loc: LocValues, carSpeedKmh: Float?, nowElapsedMs: Long): Boolean =
        evaluate(loc, carSpeedKmh, nowElapsedMs).accepted

    fun isCoordJump(
        loc: LocValues,
        prevLat: Double,
        prevLon: Double,
        prevElapsedMs: Long,
        nowElapsedMs: Long,
        carSpeedKmh: Float?,
    ): Boolean {
        if (loc.latitude == 0.0 && loc.longitude == 0.0) return false
        val dtSec = (nowElapsedMs - prevElapsedMs) / 1000.0
        if (dtSec <= JUMP_MIN_DT_SEC || dtSec > JUMP_MAX_DT_SEC) return false
        val distM = haversineM(prevLat, prevLon, loc.latitude, loc.longitude)
        val speedKmh = (
            carSpeedKmh?.takeIf { it.isFinite() && it >= 0f }
                ?: loc.speed.coerceAtLeast(0f)
            ).coerceAtLeast(5f)
        val maxM = (speedKmh / 3.6) * dtSec * JUMP_SPEED_MARGIN + JUMP_SLACK_M
        return distM > maxM
    }

    fun isAltitudeJump(
        loc: LocValues,
        prevAlt: Double,
        prevElapsedMs: Long,
        nowElapsedMs: Long,
    ): Boolean {
        if (!loc.altitude.isFinite() || !prevAlt.isFinite()) return false
        val dtSec = (nowElapsedMs - prevElapsedMs) / 1000.0
        if (dtSec <= JUMP_MIN_DT_SEC || dtSec > JUMP_MAX_DT_SEC) return false
        return abs(loc.altitude - prevAlt) > MAX_ALTITUDE_JUMP_M
    }

    private fun evaluateJump(
        loc: LocValues,
        carSpeedKmh: Float?,
        nowElapsedMs: Long,
    ): Result {
        val prevLat: Double
        val prevLon: Double
        val prevAlt: Double?
        val prevElapsed: Long
        synchronized(this) {
            prevLat = anchorLat ?: return Result.OK
            prevLon = anchorLon ?: return Result.OK
            prevAlt = anchorAlt
            prevElapsed = anchorElapsedMs
        }
        if (isCoordJump(loc, prevLat, prevLon, prevElapsed, nowElapsedMs, carSpeedKmh)) {
            return Result(false, RejectReason.COORD_JUMP)
        }
        if (prevAlt != null && isAltitudeJump(loc, prevAlt, prevElapsed, nowElapsedMs)) {
            return Result(false, RejectReason.ALTITUDE_JUMP)
        }
        return Result.OK
    }

    private fun rememberAccepted(loc: LocValues, nowElapsedMs: Long) {
        synchronized(this) {
            anchorLat = loc.latitude
            anchorLon = loc.longitude
            anchorAlt = loc.altitude.takeIf { it.isFinite() }
            anchorElapsedMs = nowElapsedMs
        }
    }

    private fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dPhi = Math.toRadians(lat2 - lat1)
        val dLam = Math.toRadians(lon2 - lon1)
        val a = sin(dPhi / 2) * sin(dPhi / 2) +
            cos(p1) * cos(p2) * sin(dLam / 2) * sin(dLam / 2)
        return 2 * r * asin(min(1.0, sqrt(a)))
    }

    fun isSpeedMismatch(gnssSpeedKmh: Float, carSpeedKmh: Float?): Boolean {
        val car = carSpeedKmh ?: return false
        if (!gnssSpeedKmh.isFinite() || !car.isFinite() || car < 0f) return false
        val ceiling = max(gnssSpeedKmh, car)
        if (ceiling < SPEED_COMPARE_FLOOR_KMH) {
            return abs(gnssSpeedKmh - car) > LOW_SPEED_ABS_TOLERANCE_KMH
        }
        return abs(gnssSpeedKmh - car) > ceiling * SPEED_RELATIVE_TOLERANCE
    }
}
