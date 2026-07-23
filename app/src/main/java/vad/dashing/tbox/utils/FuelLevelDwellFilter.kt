package vad.dashing.tbox.utils

import android.os.SystemClock

/**
 * Accepts a fuel % as "stable filtered" only after it has been observed continuously
 * for [dwellMs] (default 15 s). Replaces sample-count [FuelLevelBuffer] so HU poll
 * (≈30 s) and frequent TBox frames share the same time semantics.
 */
class FuelLevelDwellFilter(
    private val dwellMs: Long = DEFAULT_DWELL_MS,
) {
    private var candidate: UInt? = null
    private var candidateSinceElapsedMs: Long = 0L
    private var lastAccepted: UInt? = null

    /**
     * @return the stable % to publish as filtered, or null if still dwelling / unchanged.
     */
    fun onSample(percent: UInt, nowElapsedMs: Long = SystemClock.elapsedRealtime()): UInt? {
        if (candidate != percent) {
            candidate = percent
            candidateSinceElapsedMs = nowElapsedMs
            return null
        }
        if (nowElapsedMs - candidateSinceElapsedMs < dwellMs) {
            return null
        }
        if (lastAccepted == percent) {
            return null
        }
        lastAccepted = percent
        return percent
    }

    fun reset() {
        candidate = null
        candidateSinceElapsedMs = 0L
        lastAccepted = null
    }

    companion object {
        const val DEFAULT_DWELL_MS: Long = 15_000L
    }
}
