package vad.dashing.tbox

import android.os.SystemClock

private data class UiTimingMark(val label: String, val elapsedMs: Long)

private class ElapsedTimingBuffer {
    private val marks = mutableListOf<UiTimingMark>()
    private val lock = Any()

    fun reset() {
        synchronized(lock) { marks.clear() }
    }

    fun mark(label: String) {
        synchronized(lock) {
            marks.add(UiTimingMark(label, SystemClock.elapsedRealtime()))
        }
    }

    fun log(tag: String) {
        synchronized(lock) {
            if (marks.isEmpty()) return
            val sb = StringBuilder()
            var prev = marks[0].elapsedMs
            val base = prev
            for (m in marks) {
                val delta = m.elapsedMs - prev
                val cum = m.elapsedMs - base
                if (sb.isNotEmpty()) sb.append(" | ")
                sb.append(m.label).append(" +").append(delta).append("ms (Σ ").append(cum).append("ms)")
                prev = m.elapsedMs
            }
            TboxRepository.addLog("DEBUG", tag, sb.toString())
            marks.clear()
        }
    }
}

/** Cold path for [MainActivity] (onCreate → first layout). */
object MainActivityLoadTimings {
    private val buffer = ElapsedTimingBuffer()

    fun reset() = buffer.reset()

    fun mark(label: String) = buffer.mark(label)

    fun log(tag: String) = buffer.log(tag)
}

/** [FloatingOverlayController] ensure/sync and per-panel window attach. */
object FloatingOverlayLoadTimings {
    private val buffer = ElapsedTimingBuffer()

    fun reset() = buffer.reset()

    fun mark(label: String) = buffer.mark(label)

    fun log(tag: String) = buffer.log(tag)
}
