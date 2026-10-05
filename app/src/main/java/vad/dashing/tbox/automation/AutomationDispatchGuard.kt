package vad.dashing.tbox.automation

const val AUTOMATION_MIN_LAUNCH_INTERVAL_MS = 2_000L
const val AUTOMATION_MAX_CONSECUTIVE_FAILURES = 5

/**
 * Per-automation launch pacing and crash cutoff.
 *
 * Cooldown is measured from the previous accepted launch, not from trigger edges, so Restart and
 * Parallel cannot spin a rule faster than [minIntervalMs]. Consecutive action failures disable the
 * rule; cancellations do not count.
 */
class AutomationDispatchGuard(
    private val minIntervalMs: Long = AUTOMATION_MIN_LAUNCH_INTERVAL_MS,
    private val maxConsecutiveFailures: Int = AUTOMATION_MAX_CONSECUTIVE_FAILURES,
) {
    private data class State(
        var lastLaunchElapsedMillis: Long = Long.MIN_VALUE / 4,
        var consecutiveFailures: Int = 0,
    )

    private val states = mutableMapOf<String, State>()

    fun tryAcquire(automationId: String, nowElapsedMillis: Long): Boolean {
        val state = states.getOrPut(automationId, ::State)
        if (nowElapsedMillis - state.lastLaunchElapsedMillis < minIntervalMs) {
            return false
        }
        state.lastLaunchElapsedMillis = nowElapsedMillis
        return true
    }

    /**
     * @return true when the automation should be persisted as disabled.
     */
    fun recordOutcome(automationId: String, success: Boolean): Boolean {
        val state = states.getOrPut(automationId, ::State)
        if (success) {
            state.consecutiveFailures = 0
            return false
        }
        state.consecutiveFailures += 1
        return state.consecutiveFailures >= maxConsecutiveFailures
    }

    fun consecutiveFailures(automationId: String): Int =
        states[automationId]?.consecutiveFailures ?: 0

    fun clear(automationId: String) {
        states.remove(automationId)
    }

    fun retain(ids: Set<String>) {
        states.keys.retainAll(ids)
    }
}
