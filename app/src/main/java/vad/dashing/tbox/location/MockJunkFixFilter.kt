package vad.dashing.tbox.location

import vad.dashing.tbox.LocValues
import kotlin.math.abs
import kotlin.math.max

/**
 * Optional sanity gate for live GNSS fixes before they enter mock / retention.
 * When enabled, junk samples are ignored so the last good point (or DR) stays in use.
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
     * @param carSpeedKmh vehicle speed when known; if null, speed-mismatch check is skipped
     */
    fun evaluate(loc: LocValues, carSpeedKmh: Float?): Result {
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
        // Only reject when DOP/GST actually informed accuracy (not the bare default).
        val hasAccuracySignal = (loc.hrms != null && loc.hrms > 0f && loc.hrms.isFinite()) ||
            (loc.hdop != null && loc.hdop > 0f && loc.hdop.isFinite())
        if (hasAccuracySignal && accuracyM > MAX_HORIZONTAL_ACCURACY_M) {
            return Result(false, RejectReason.ACCURACY)
        }
        if (isSpeedMismatch(loc.speed, carSpeedKmh)) {
            return Result(false, RejectReason.SPEED_MISMATCH)
        }
        return Result.OK
    }

    fun isAcceptable(loc: LocValues, carSpeedKmh: Float?): Boolean =
        evaluate(loc, carSpeedKmh).accepted

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
