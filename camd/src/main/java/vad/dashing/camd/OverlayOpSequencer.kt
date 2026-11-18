package vad.dashing.camd

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Per-input generation gate for overlay show/hide work posted to the main looper.
 *
 * Each [begin] bumps the generation for an input id. A late Runnable that still holds
 * an older token must no-op via [isCurrent] / [runIfCurrent], so a latch timeout or a
 * CLOSE→OPEN reorder cannot add or remove the wrong window.
 *
 * Pure JVM — unit-testable without [android.os.Handler] or WindowManager.
 */
class OverlayOpSequencer {
    private val generations = ConcurrentHashMap<Int, AtomicLong>()

    /** Start a show/hide op for [inputId]; the posted Runnable must hold the returned token. */
    fun begin(inputId: Int): Long = counter(inputId).incrementAndGet()

    /** True iff [token] is still the latest op for [inputId]. */
    fun isCurrent(inputId: Int, token: Long): Boolean = counter(inputId).get() == token

    /**
     * Invalidate any in-flight op (e.g. after a latch timeout) so a late Runnable no-ops.
     * Returns the new generation.
     */
    fun cancel(inputId: Int): Long = begin(inputId)

    /** Run [block] only while [token] is still current. Returns true if the block ran. */
    fun runIfCurrent(inputId: Int, token: Long, block: () -> Unit): Boolean {
        if (!isCurrent(inputId, token)) return false
        block()
        return true
    }

    /** Latest generation for [inputId], or 0 if none began yet. */
    fun current(inputId: Int): Long = generations[inputId]?.get() ?: 0L

    private fun counter(inputId: Int): AtomicLong =
        generations.getOrPut(inputId) { AtomicLong(0L) }
}
