package vad.dashing.tbox.automation

const val AUTOMATION_LOOP_GUARD_WINDOW_MS = 10_000L
const val AUTOMATION_LOOP_GUARD_MAX_RUNS = 20

enum class AutomationRunAdmission {
    LAUNCH,
    ENQUEUE,
    SKIP,
    CANCEL_ACTIVE_AND_LAUNCH,
}

/**
 * Pure overlap policy for [AutomationEngine.dispatch] / queued drain.
 *
 * Per-automation cooldown and the global loop guard are applied after this decision, inside launch.
 */
object AutomationRunAdmissionPolicy {
    fun decide(
        runMode: AutomationRunMode,
        activeCount: Int,
        queuedCount: Int,
        maxRuns: Int,
    ): AutomationRunAdmission = when (runMode) {
        AutomationRunMode.SINGLE ->
            if (activeCount > 0) AutomationRunAdmission.SKIP else AutomationRunAdmission.LAUNCH

        AutomationRunMode.RESTART -> AutomationRunAdmission.CANCEL_ACTIVE_AND_LAUNCH

        AutomationRunMode.QUEUED -> when {
            activeCount <= 0 -> AutomationRunAdmission.LAUNCH
            activeCount + queuedCount < maxRuns -> AutomationRunAdmission.ENQUEUE
            else -> AutomationRunAdmission.SKIP
        }

        AutomationRunMode.PARALLEL ->
            if (activeCount < maxRuns) AutomationRunAdmission.LAUNCH else AutomationRunAdmission.SKIP
    }

    fun shouldLaunchQueuedNext(
        runMode: AutomationRunMode,
        enabled: Boolean,
        activeCount: Int,
        queuedCount: Int,
    ): Boolean =
        enabled &&
            runMode == AutomationRunMode.QUEUED &&
            activeCount <= 0 &&
            queuedCount > 0
}

/**
 * Caps how many automations may start across the whole engine in a sliding window.
 */
class AutomationGlobalLoopGuard(
    private val windowMs: Long = AUTOMATION_LOOP_GUARD_WINDOW_MS,
    private val maxRuns: Int = AUTOMATION_LOOP_GUARD_MAX_RUNS,
) {
    private val recentDispatchElapsedMillis = ArrayDeque<Long>()

    fun tryAcquire(nowElapsedMillis: Long): Boolean {
        while (
            recentDispatchElapsedMillis.isNotEmpty() &&
            nowElapsedMillis - recentDispatchElapsedMillis.first() > windowMs
        ) {
            recentDispatchElapsedMillis.removeFirst()
        }
        if (recentDispatchElapsedMillis.size >= maxRuns) return false
        recentDispatchElapsedMillis.addLast(nowElapsedMillis)
        return true
    }

    fun clear() {
        recentDispatchElapsedMillis.clear()
    }

    internal fun sizeForTests(): Int = recentDispatchElapsedMillis.size
}
