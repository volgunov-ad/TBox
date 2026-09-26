package vad.dashing.tbox

/**
 * Quiet period before floating panels may first appear after background-service startup.
 * Arms once per startup pipeline; gates **all** cold-start open paths (Usage Stats sync and
 * [FloatingOverlayController.ensureFloatingDashboards]). Force-show still has its own settle
 * after [ServiceLifecyclePhase.Running] — it only applies once this gate is open.
 */
internal object FloatingPanelsShowOnServiceStartDelay {
    const val DEFAULT_SECONDS = 3
    const val MIN_SECONDS = 0
    const val MAX_SECONDS = 60

    /** Until [FloatingOverlayController.armFirstShowGate] runs, opens stay blocked. */
    const val UNARMED_ALLOWED_AFTER_ELAPSED_MS: Long = Long.MAX_VALUE

    fun coerceSeconds(raw: Int): Int = raw.coerceIn(MIN_SECONDS, MAX_SECONDS)

    fun delayMs(seconds: Int): Long = coerceSeconds(seconds).toLong() * 1000L

    fun allowedAfterElapsedRealtimeMs(
        delaySeconds: Int,
        nowElapsedRealtimeMs: Long,
    ): Long = nowElapsedRealtimeMs + delayMs(delaySeconds)

    fun isAllowed(
        nowElapsedRealtimeMs: Long,
        allowedAfterElapsedRealtimeMs: Long,
    ): Boolean = nowElapsedRealtimeMs >= allowedAfterElapsedRealtimeMs

    fun remainingDelayMs(
        nowElapsedRealtimeMs: Long,
        allowedAfterElapsedRealtimeMs: Long,
    ): Long = (allowedAfterElapsedRealtimeMs - nowElapsedRealtimeMs).coerceAtLeast(0L)
}
