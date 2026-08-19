package vad.dashing.tbox.location

/**
 * Gradual growth of published mock [android.location.Location.accuracy] while GNSS
 * is not driving the point (retention / junk hold / simulated loss).
 *
 * Starts from the last live horizontal accuracy and rises with retention age up to
 * [CEILING_M], so consumers (e.g. Yandex Navigator) can weight map-matching more
 * loosely as dead-reckoning ages.
 */
object MockRetentionAccuracy {
    /** Cap for horizontal accuracy while retaining (metres). */
    const val CEILING_M = 75f

    /**
     * Linear growth rate (m/s). From a typical live floor (~5 m) to [CEILING_M]
     * in ~3.5 minutes of continuous retention.
     */
    const val GROWTH_M_PER_S = (CEILING_M - 5f) / 210f

    /**
     * @param baseAccuracyM last live (or last-good) horizontal accuracy when retention began
     * @param retentionAgeMs elapsed ms since retention started; ≤0 → [baseAccuracyM] only
     */
    fun horizontalM(baseAccuracyM: Float, retentionAgeMs: Long): Float {
        val base = when {
            !baseAccuracyM.isFinite() || baseAccuracyM <= 0f -> 5f
            else -> baseAccuracyM.coerceAtMost(CEILING_M)
        }
        if (retentionAgeMs <= 0L) return base
        val ageSec = retentionAgeMs / 1000f
        return (base + GROWTH_M_PER_S * ageSec).coerceAtMost(CEILING_M)
    }

    /** Age (ms) from [startedAtElapsedMs] to [nowElapsedMs]; 0 if not started. */
    fun ageMs(startedAtElapsedMs: Long, nowElapsedMs: Long): Long {
        if (startedAtElapsedMs <= 0L) return 0L
        if (nowElapsedMs < startedAtElapsedMs) return 0L
        return nowElapsedMs - startedAtElapsedMs
    }
}
