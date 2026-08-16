package vad.dashing.tbox.location

/**
 * When to (re)send LOC CMD 0x05 subscribe while using the TBox GNSS source.
 *
 * After SUSPEND LOC the module stops pushing fixes; a stale-data watchdog must
 * not keep retrying subscribe — that only adds UDP noise.
 *
 * [SimulatedLocationSourceLoss] is the same: while the debug switch is on we
 * unsubscribe and must not resubscribe until the switch is cleared.
 */
object LocSubscribePolicy {
    /**
     * Periodic resubscribe after silence (see [BackgroundService] 1 s loop).
     *
     * @param wantTboxLoc settings say location source is TBox (`getLocData`)
     * @param autoSuspendLoc auto SUSPEND LOC setting is on
     * @param locSuspended LOC confirmed suspended (`0x82` reply)
     * @param simulatedSourceLoss debug “simulate source loss” switch is on
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
        simulatedSourceLoss: Boolean = false,
        staleThresholdMs: Long = 10_000L,
        subscribeCooldownMs: Long = 10_000L,
    ): Boolean {
        if (!wantTboxLoc || !tboxConnected) return false
        if (autoSuspendLoc || locSuspended || simulatedSourceLoss) return false
        if (locationStaleMs <= staleThresholdMs) return false
        if (sinceLastSubscribeMs <= subscribeCooldownMs) return false
        return true
    }

    /** Initial subscribe right after TBox comes online. */
    fun shouldSubscribeOnConnect(
        wantTboxLoc: Boolean,
        noTboxConnect: Boolean,
        autoSuspendLoc: Boolean,
        simulatedSourceLoss: Boolean = false,
    ): Boolean = wantTboxLoc && !noTboxConnect && !autoSuspendLoc && !simulatedSourceLoss

    /** Falling edge of the auto-suspend switch → one RESUME LOC. */
    fun shouldResumeOnAutoSuspendChange(
        wasEnabled: Boolean,
        enabled: Boolean,
    ): Boolean = wasEnabled && !enabled

    /**
     * After LOC confirms RESUME (`0x83`), re-subscribe when TBox is still the
     * active GNSS source and auto-suspend / simulated loss are off.
     */
    fun shouldSubscribeAfterLocResume(
        wantTboxLoc: Boolean,
        autoSuspendLoc: Boolean,
        simulatedSourceLoss: Boolean = false,
    ): Boolean = wantTboxLoc && !autoSuspendLoc && !simulatedSourceLoss

    /** Rising edge of simulated source loss while on TBox → unsubscribe LOC. */
    fun shouldUnsubscribeOnSimulatedLoss(
        wantTboxLoc: Boolean,
        simulatedLossEnabled: Boolean,
    ): Boolean = wantTboxLoc && simulatedLossEnabled

    /** Falling edge of simulated source loss while on TBox → subscribe LOC. */
    fun shouldSubscribeOnSimulatedLossEnd(
        wantTboxLoc: Boolean,
        simulatedLossEnabled: Boolean,
        autoSuspendLoc: Boolean,
    ): Boolean = wantTboxLoc && !simulatedLossEnabled && !autoSuspendLoc
}
