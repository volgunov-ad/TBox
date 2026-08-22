package vad.dashing.tbox.vehicle

/**
 * Pure helpers for wheel-pulse calibration disk throttle.
 * Live distance uses RAM; DataStore writes are sparse.
 */
object WheelPulsePersistPolicy {
    const val MIN_INTERVAL_MS = 60_000L

    fun nearlyEqual(
        a: WheelPulseCalibration?,
        b: WheelPulseCalibration?,
        metersEps: Float = 1e-5f,
        confidenceEps: Float = 0.01f,
    ): Boolean {
        if (a === b) return true
        if (a == null || b == null) return false
        return kotlin.math.abs(a.metersPerPulse - b.metersPerPulse) < metersEps &&
            kotlin.math.abs(a.confidence - b.confidence) < confidenceEps &&
            a.tripsEnabled == b.tripsEnabled &&
            a.mockDrEnabled == b.mockDrEnabled
    }

    /** Delay before next allowed write, or 0 if write may proceed now. */
    fun delayUntilNextWriteMs(nowElapsedMs: Long, lastWriteElapsedMs: Long): Long {
        val elapsed = nowElapsedMs - lastWriteElapsedMs
        return if (elapsed >= MIN_INTERVAL_MS) 0L else MIN_INTERVAL_MS - elapsed
    }

    /**
     * True when [candidate] should be queued for disk: differs from last persisted
     * and is not already the pending snapshot.
     */
    fun isDirty(
        lastPersisted: WheelPulseCalibration?,
        pending: WheelPulseCalibration?,
        candidate: WheelPulseCalibration,
    ): Boolean {
        if (nearlyEqual(lastPersisted, candidate)) return false
        if (nearlyEqual(pending, candidate)) return false
        return true
    }
}
