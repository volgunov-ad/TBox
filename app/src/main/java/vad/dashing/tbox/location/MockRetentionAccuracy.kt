package vad.dashing.tbox.location

/**
 * Gradual growth of published mock [android.location.Location.accuracy] while GNSS
 * is not driving the point (retention / junk hold / simulated loss).
 *
 * Starts from the last live horizontal accuracy and rises with retention age up to
 * the configured ceiling (default [DEFAULT_CEILING_M]), so consumers (e.g. Yandex Navigator)
 * can weight map-matching more loosely as dead-reckoning ages.
 */
object MockRetentionAccuracy {
    /** Default cap for horizontal accuracy while retaining (metres). */
    const val DEFAULT_CEILING_M = 75f

    /** User-adjustable minimum ceiling (metres). */
    const val MIN_CEILING_M = 10f

    /** User-adjustable maximum ceiling (metres). */
    const val MAX_CEILING_M = 100f

    /** @deprecated Use [DEFAULT_CEILING_M]. */
    @Deprecated("Use DEFAULT_CEILING_M", ReplaceWith("DEFAULT_CEILING_M"))
    const val CEILING_M = DEFAULT_CEILING_M

    /** Time to grow from a typical live floor (~5 m) to the ceiling. */
    private const val GROWTH_DURATION_SEC = 210f

    private const val BASE_FLOOR_M = 5f

    fun normalizeCeilingM(raw: Float): Float =
        raw.coerceIn(MIN_CEILING_M, MAX_CEILING_M)

    fun normalizeCeilingM(raw: Int): Int =
        raw.coerceIn(MIN_CEILING_M.toInt(), MAX_CEILING_M.toInt())

    fun growthMPerS(ceilingM: Float): Float {
        val ceiling = normalizeCeilingM(ceilingM)
        return (ceiling - BASE_FLOOR_M) / GROWTH_DURATION_SEC
    }

    /**
     * @param baseAccuracyM last live (or last-good) horizontal accuracy when retention began
     * @param retentionAgeMs elapsed ms since retention started; ≤0 → [baseAccuracyM] only
     * @param ceilingM user cap while retaining; coerced to [MIN_CEILING_M]…[MAX_CEILING_M]
     */
    fun horizontalM(
        baseAccuracyM: Float,
        retentionAgeMs: Long,
        ceilingM: Float = DEFAULT_CEILING_M,
    ): Float {
        val ceiling = normalizeCeilingM(ceilingM)
        val base = when {
            !baseAccuracyM.isFinite() || baseAccuracyM <= 0f -> BASE_FLOOR_M
            else -> baseAccuracyM.coerceAtMost(ceiling)
        }
        if (retentionAgeMs <= 0L) return base
        val ageSec = retentionAgeMs / 1000f
        return (base + growthMPerS(ceiling) * ageSec).coerceAtMost(ceiling)
    }

    /** Age (ms) from [startedAtElapsedMs] to [nowElapsedMs]; 0 if not started. */
    fun ageMs(startedAtElapsedMs: Long, nowElapsedMs: Long): Long {
        if (startedAtElapsedMs <= 0L) return 0L
        if (nowElapsedMs < startedAtElapsedMs) return 0L
        return nowElapsedMs - startedAtElapsedMs
    }
}
