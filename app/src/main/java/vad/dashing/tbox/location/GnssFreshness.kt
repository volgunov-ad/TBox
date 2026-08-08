package vad.dashing.tbox.location

/**
 * Wall-clock freshness for live GNSS ([TboxRepository.locationUpdateTime]).
 *
 * Mock retention may keep the last good fix for [MockLocationJob.FIX_RETENTION_MS],
 * but the live [vad.dashing.tbox.LocValues] stream must not stay "present" for minutes
 * after USB unplug / NMEA silence — Advanced soft-blend would pull toward a frozen fix.
 */
object GnssFreshness {
    /**
     * Clear / treat LocValues as absent after this long without an update.
     * USB/ESP periodic clear and CONSTANT [gnssPresent] gate.
     * (TBox source still uses its own 10 s watchdog in BackgroundService.)
     */
    const val STALE_CLEAR_MS = 3_000L

    fun isFresh(
        lastUpdateAtMs: Long?,
        nowMs: Long,
        staleMs: Long = STALE_CLEAR_MS,
    ): Boolean {
        if (lastUpdateAtMs == null || lastUpdateAtMs <= 0L) return false
        if (staleMs <= 0L) return true
        val age = nowMs - lastUpdateAtMs
        return age in 0L until staleMs
    }
}
