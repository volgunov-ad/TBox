package vad.dashing.tbox.automation

import kotlin.math.min

/**
 * Wait until [isReady] is true or [waitMillis] elapses.
 * Does not abort early when [isReady] is false mid-window; keeps polling until timeout.
 * [waitMillis] `<= 0` means a single check (current skip-if-false behavior).
 */
internal suspend fun awaitAutomationConditionWindow(
    waitMillis: Long,
    isReady: () -> Boolean,
    nowElapsedMillis: () -> Long,
    delayFor: suspend (Long) -> Unit,
    pollMillis: Long = 250L,
): Boolean {
    if (isReady()) return true
    if (waitMillis <= 0L) return false
    val deadline = nowElapsedMillis() + waitMillis
    while (true) {
        val remaining = deadline - nowElapsedMillis()
        if (remaining <= 0L) return isReady()
        delayFor(min(pollMillis, remaining))
        if (isReady()) return true
    }
}
