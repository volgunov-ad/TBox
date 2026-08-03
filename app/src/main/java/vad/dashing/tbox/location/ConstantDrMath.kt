package vad.dashing.tbox.location

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure helpers for CONSTANT mock mode: GNSS snap interval, DR↔GNSS mismatch.
 */
object ConstantDrMath {
    /** Re-take trustworthy GNSS coordinates on this interval. */
    const val GNSS_SNAP_INTERVAL_MS = 5_000L

    /** Consecutive large mismatches (× snap interval) before requesting calibration. */
    const val MISMATCH_STREAK_TO_CALIBRATE = 3

    /**
     * Horizontal distance (m) between calculated and GNSS point treated as large.
     * ~typical urban multipath / uncalibrated DR drift over a few seconds.
     */
    const val LARGE_MISMATCH_METERS = 40.0

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

    fun isLargeMismatch(distanceM: Double, thresholdM: Double = LARGE_MISMATCH_METERS): Boolean =
        distanceM.isFinite() && distanceM >= thresholdM

    /**
     * Update streak after a GNSS snap comparison.
     * @return new streak count
     */
    fun nextMismatchStreak(previousStreak: Int, largeMismatch: Boolean): Int =
        if (largeMismatch) previousStreak + 1 else 0

    fun shouldRequestCalibration(streak: Int): Boolean =
        streak >= MISMATCH_STREAK_TO_CALIBRATE

    fun shouldSnapToGnss(lastSnapElapsedMs: Long, nowElapsedMs: Long): Boolean {
        if (lastSnapElapsedMs <= 0L) return true
        return nowElapsedMs - lastSnapElapsedMs >= GNSS_SNAP_INTERVAL_MS
    }
}
