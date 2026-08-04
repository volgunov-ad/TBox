package vad.dashing.tbox.location

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure helpers for CONSTANT mock mode: GNSS snap interval, DR↔GNSS mismatch,
 * adaptive thresholds, blend, and course-accept gates.
 */
object ConstantDrMath {
    /** Re-take trustworthy GNSS coordinates on this interval. */
    const val GNSS_SNAP_INTERVAL_MS = 5_000L

    /** Consecutive large mismatches before requesting calibration (normal). */
    const val MISMATCH_STREAK_TO_CALIBRATE = 3

    /**
     * When last drive calibration is fresher than [FRESH_CALIB_MS], require this longer
     * streak so a short-term mismatch does not start auto-calib.
     */
    const val MISMATCH_STREAK_WHEN_FRESH = 6

    /** Fresh calibration window: short mismatches are ignored for auto-calib. */
    const val FRESH_CALIB_MS = 60L * 60L * 1_000L

    /** Below this CAN/mock speed, do not count DR↔GNSS mismatch (GNSS wander). */
    const val MISMATCH_MIN_SPEED_KMH = 10f

    /** Floor for adaptive mismatch threshold (m). */
    const val MISMATCH_THRESHOLD_FLOOR_M = 25.0

    /** Max heading delta (deg) to adopt GNSS course when already holding a course. */
    const val GNSS_COURSE_MAX_DELTA_DEG = 30f

    /** Above this speed, allow larger course adopt from GNSS (still needs non-zero course). */
    const val GNSS_COURSE_HIGH_SPEED_KMH = 40f

    private const val METERS_PER_DEG_LAT = 111_320.0

    fun distanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Double {
        if (!lat1.isFinite() || !lon1.isFinite() || !lat2.isFinite() || !lon2.isFinite()) {
            return Double.POSITIVE_INFINITY
        }
        val meanLatRad = Math.toRadians((lat1 + lat2) * 0.5)
        val dLatM = (lat2 - lat1) * METERS_PER_DEG_LAT
        val metersPerDegLon = METERS_PER_DEG_LAT * cos(meanLatRad).coerceAtLeast(1e-6)
        val dLonM = (lon2 - lon1) * metersPerDegLon
        return sqrt(dLatM * dLatM + dLonM * dLonM)
    }

    /**
     * Adaptive horizontal mismatch threshold (m).
     * ~max(25, 0.5·v·Δt + 2·hAcc) with default hAcc when unknown.
     */
    fun mismatchThresholdM(
        speedKmh: Float,
        snapIntervalSec: Double = GNSS_SNAP_INTERVAL_MS / 1000.0,
        horizontalAccuracyM: Float? = null,
    ): Double {
        val v = speedKmh.takeIf { it.isFinite() && it >= 0f } ?: 0f
        val speedBased = 0.5 * (v / 3.6) * snapIntervalSec.coerceAtLeast(0.0)
        val acc = horizontalAccuracyM
            ?.takeIf { it.isFinite() && it > 0f }
            ?.toDouble()
            ?: 15.0
        return max(MISMATCH_THRESHOLD_FLOOR_M, speedBased + 2.0 * acc)
    }

    fun isLargeMismatch(distanceM: Double, thresholdM: Double): Boolean =
        distanceM.isFinite() && distanceM >= thresholdM

    /**
     * Whether this snap tick should participate in mismatch streak counting.
     * Low speed → GNSS wander dominates; skip.
     */
    fun shouldCountMismatch(speedKmh: Float): Boolean =
        speedKmh.isFinite() && speedKmh >= MISMATCH_MIN_SPEED_KMH

    fun nextMismatchStreak(previousStreak: Int, largeMismatch: Boolean): Int =
        if (largeMismatch) previousStreak + 1 else 0

    /**
     * Required streak: longer if [lastCalibratedAtEpochMs] is within [FRESH_CALIB_MS].
     */
    fun requiredMismatchStreak(
        nowEpochMs: Long,
        lastCalibratedAtEpochMs: Long,
    ): Int {
        if (lastCalibratedAtEpochMs <= 0L) return MISMATCH_STREAK_TO_CALIBRATE
        val age = nowEpochMs - lastCalibratedAtEpochMs
        return if (age in 0L until FRESH_CALIB_MS) {
            MISMATCH_STREAK_WHEN_FRESH
        } else {
            MISMATCH_STREAK_TO_CALIBRATE
        }
    }

    fun shouldRequestCalibration(streak: Int, requiredStreak: Int): Boolean =
        streak >= requiredStreak

    fun shouldSnapToGnss(lastSnapElapsedMs: Long, nowElapsedMs: Long): Boolean {
        if (lastSnapElapsedMs <= 0L) return true
        return nowElapsedMs - lastSnapElapsedMs >= GNSS_SNAP_INTERVAL_MS
    }

    /**
     * Blend weight α toward GNSS in [0, 1].
     * Small distance → hard snap (1); near threshold → softer pull.
     */
    fun blendAlphaTowardGnss(distanceM: Double, thresholdM: Double): Float {
        if (!distanceM.isFinite() || distanceM <= 0.0) return 1f
        if (!thresholdM.isFinite() || thresholdM <= 0.0) return 1f
        if (distanceM >= thresholdM) return 0f
        val half = thresholdM * 0.5
        return if (distanceM <= half) {
            1f
        } else {
            // Linear from 1 at half-threshold to ~0.35 at threshold.
            val t = ((distanceM - half) / half).coerceIn(0.0, 1.0)
            (1.0 - 0.65 * t).toFloat()
        }
    }

    fun blendLatLon(
        drLat: Double,
        drLon: Double,
        gnssLat: Double,
        gnssLon: Double,
        alpha: Float,
    ): Pair<Double, Double> {
        val a = alpha.coerceIn(0f, 1f).toDouble()
        if (a >= 1.0) return gnssLat to gnssLon
        if (a <= 0.0) return drLat to drLon
        return (drLat + (gnssLat - drLat) * a) to (drLon + (gnssLon - drLon) * a)
    }

    /**
     * Adopt GNSS course into held DR course only when safe.
     * - No held course → accept if [MockLocationJob.shouldAcceptGnssCourse]
     * - Held course → accept if delta ≤ [GNSS_COURSE_MAX_DELTA_DEG], or speed is high
     *   and delta ≤ 2× that (rough lane-change / turn catch-up)
     */
    fun shouldAdoptGnssCourse(
        speedKmh: Float,
        gnssCourseDeg: Float,
        heldCourseDeg: Float?,
    ): Boolean {
        if (!MockLocationJob.shouldAcceptGnssCourse(speedKmh, gnssCourseDeg)) return false
        val held = heldCourseDeg?.takeIf { it != 0f && it.isFinite() } ?: return true
        val delta = abs(DriveCalibrationMath.wrapDeltaDeg(held, gnssCourseDeg))
        if (delta <= GNSS_COURSE_MAX_DELTA_DEG) return true
        return speedKmh >= GNSS_COURSE_HIGH_SPEED_KMH &&
            delta <= GNSS_COURSE_MAX_DELTA_DEG * 2f
    }
}
