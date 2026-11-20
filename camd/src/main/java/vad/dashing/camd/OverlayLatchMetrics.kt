package vad.dashing.camd

import java.util.concurrent.atomic.AtomicLong

/**
 * Counters for overlay main-thread latch timeouts (P1-4).
 * Pure JVM — unit-testable without WindowManager / Handler.
 */
object OverlayLatchMetrics {
    private val showTimeouts = AtomicLong(0)
    private val hideTimeouts = AtomicLong(0)

    fun onShowTimeout(): Long = showTimeouts.incrementAndGet()

    fun onHideTimeout(): Long = hideTimeouts.incrementAndGet()

    fun showTimeoutCount(): Long = showTimeouts.get()

    fun hideTimeoutCount(): Long = hideTimeouts.get()

    /** Test-only reset. */
    fun resetForTest() {
        showTimeouts.set(0)
        hideTimeouts.set(0)
    }
}
