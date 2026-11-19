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
     * Invalidate [token] for [inputId] only if it is still current.
     * No-op when a newer [begin] already advanced the generation (e.g. a later show),
     * so a late latch timeout cannot cancel the newer op.
     * Returns true when this call invalidated [token].
     */
    fun cancelIfCurrent(inputId: Int, token: Long): Boolean {
        val c = counter(inputId)
        while (true) {
            val cur = c.get()
            if (cur != token) return false
            if (c.compareAndSet(token, token + 1L)) return true
        }
    }

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

    companion object {
        /** API / ShowResult error when a posted overlay op lost the generation race. */
        const val ERROR_SUPERSEDED = "overlay_superseded"

        /** API / ShowResult error when the main-thread latch timed out. */
        const val ERROR_TIMEOUT = "overlay_timeout"
    }
}

/**
 * Non-blank reason for `state=error error=…` after a failed overlay show.
 * Never returns null or blank — stale / missing messages become [OverlayOpSequencer.ERROR_SUPERSEDED].
 */
fun overlayFailureReason(error: String?): String =
    error?.takeIf { it.isNotBlank() } ?: OverlayOpSequencer.ERROR_SUPERSEDED

