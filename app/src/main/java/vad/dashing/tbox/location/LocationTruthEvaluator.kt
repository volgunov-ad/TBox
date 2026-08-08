package vad.dashing.tbox.location

import kotlin.math.abs

/**
 * Debounced location-truth flag: GNSS fix + nav speed vs vehicle speed (˜tolerance).
 * Same rules for TBox / ESP32 / Android; start and [reset] yield false.
 */
class LocationTruthEvaluator {
    companion object {
        const val SPEED_TOLERANCE_KMH = 10f
        const val DEBOUNCE_MS = 5_000L

        fun hasFix(locateStatus: Boolean, latitude: Double, longitude: Double): Boolean =
            locateStatus && !(latitude == 0.0 && longitude == 0.0)

        fun isMatching(
            hasFix: Boolean,
            navSpeedKmH: Float,
            carSpeedKmH: Float?,
        ): Boolean {
            if (!hasFix) return false
            val car = carSpeedKmH ?: return false
            return abs(navSpeedKmH - car) <= SPEED_TOLERANCE_KMH
        }
    }

    private var truth: Boolean = false
    private var matchSinceElapsedMs: Long? = null
    private var mismatchSinceElapsedMs: Long? = null

    fun currentTruth(): Boolean = truth

    fun reset() {
        truth = false
        matchSinceElapsedMs = null
        mismatchSinceElapsedMs = null
    }

    /**
     * @param nowElapsedMs [android.os.SystemClock.elapsedRealtime] (or test clock)
     * @return updated truth flag
     */
    fun onTick(
        nowElapsedMs: Long,
        hasFix: Boolean,
        navSpeedKmH: Float,
        carSpeedKmH: Float?,
    ): Boolean {
        if (isMatching(hasFix, navSpeedKmH, carSpeedKmH)) {
            mismatchSinceElapsedMs = null
            if (matchSinceElapsedMs == null) {
                matchSinceElapsedMs = nowElapsedMs
            }
            if (nowElapsedMs - matchSinceElapsedMs!! >= DEBOUNCE_MS) {
                truth = true
            }
        } else {
            matchSinceElapsedMs = null
            if (mismatchSinceElapsedMs == null) {
                mismatchSinceElapsedMs = nowElapsedMs
            }
            if (nowElapsedMs - mismatchSinceElapsedMs!! >= DEBOUNCE_MS) {
                truth = false
            }
        }
        return truth
    }
}
