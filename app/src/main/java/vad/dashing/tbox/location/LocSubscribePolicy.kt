package vad.dashing.tbox.location

/**
 * When to (re)send LOC CMD 0x05 subscribe while using the TBox GNSS source.
 *
 * After SUSPEND LOC the module stops pushing fixes; a stale-data watchdog must
 * not keep retrying subscribe — that only adds UDP noise.
 */
object LocSubscribePolicy {
    /**
     * Periodic resubscribe after silence (see [BackgroundService] 1 s loop).
     *
     * @param wantTboxLoc settings say location source is TBox (`getLocData`)
     * @param autoSuspendLoc auto SUSPEND LOC setting is on
     * @param locSuspended LOC confirmed suspended (`0x82` reply)
     * @param tboxConnected UDP session up
     * @param locationStaleMs age of last fix (or of epoch 0 if never received)
     * @param sinceLastSubscribeMs time since last subscribe attempt
     * @param staleThresholdMs silence before treating as stale (app uses 10_000)
     * @param subscribeCooldownMs min gap between subscribe attempts (app uses 10_000)
     */
    fun shouldPeriodicResubscribe(
        wantTboxLoc: Boolean,
        autoSuspendLoc: Boolean,
        locSuspended: Boolean,
        tboxConnected: Boolean,
        locationStaleMs: Long,
        sinceLastSubscribeMs: Long,
        staleThresholdMs: Long = 10_000L,
        subscribeCooldownMs: Long = 10_000L,
    ): Boolean {
        if (!wantTboxLoc || !tboxConnected) return false
        if (autoSuspendLoc || locSuspended) return false
        if (locationStaleMs <= staleThresholdMs) return false
        if (sinceLastSubscribeMs <= subscribeCooldownMs) return false
        return true
    }

    /** Initial subscribe right after TBox comes online. */
    fun shouldSubscribeOnConnect(
        wantTboxLoc: Boolean,
        noTboxConnect: Boolean,
        autoSuspendLoc: Boolean,
    ): Boolean = wantTboxLoc && !noTboxConnect && !autoSuspendLoc
}
