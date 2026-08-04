package vad.dashing.tbox.location

import vad.dashing.tbox.LocValues
import kotlin.math.abs
import kotlin.math.max

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
 * Altitude / absurd speed / accuracy reject immediately.
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
     * at/above ù relative [SPEED_RELATIVE_TOLERANCE].
     */
    const val SPEED_COMPARE_FLOOR_KMH = 15f

    /** HWGPS-style relative mismatch (fraction of max(gps, car)). */
    const val SPEED_RELATIVE_TOLERANCE = 0.10f

    /** Absolute mismatch at low speed (same ballpark as [LocationTruthEvaluator]). */
    const val LOW_SPEED_ABS_TOLERANCE_KMH = 10f

    enum class RejectReason {
        ALTITUDE,
        GNSS_SPEED,
        ACCURACY,
        SPEED_MISMATCH,
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
    fun evaluate(loc: LocValues, carSpeedKmh: Float?, nowElapsedMs: Long): Result {
        val instant = evaluateInstantExceptSpeed(loc)
        if (!instant.accepted) return instant
        val rawMismatch = isSpeedMismatch(loc.speed, carSpeedKmh)
        val latched = JunkSpeedMismatchDebouncer.update(
            nowElapsedMs = nowElapsedMs,
            rawMismatch = rawMismatch,
            carSpeedKnown = carSpeedKmh != null,
        )
        if (latched) {
            return Result(false, RejectReason.SPEED_MISMATCH)
        }
        return Result.OK
    }

    fun isAcceptable(loc: LocValues, carSpeedKmh: Float?, nowElapsedMs: Long): Boolean =
        evaluate(loc, carSpeedKmh, nowElapsedMs).accepted

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
