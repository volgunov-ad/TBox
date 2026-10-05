package vad.dashing.tbox

/**
 * Per-source generation + deadline so a delayed media-source release can be extended or cancelled.
 */
internal class MediaSourceSelectionHold {
    private var generation: Int = 0
    private var holdUntilElapsedMs: Long = 0L

    fun beginHold(nowElapsedMs: Long, holdMs: Long): Int {
        holdUntilElapsedMs = maxOf(holdUntilElapsedMs, nowElapsedMs + holdMs)
        generation += 1
        return generation
    }

    fun isHeld(nowElapsedMs: Long): Boolean = nowElapsedMs < holdUntilElapsedMs

    fun consumeRelease(generationSnapshot: Int): Boolean {
        if (generationSnapshot != generation) return false
        holdUntilElapsedMs = 0L
        return true
    }

    fun cancel() {
        generation += 1
        holdUntilElapsedMs = 0L
    }
}
