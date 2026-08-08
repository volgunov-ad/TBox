package vad.dashing.tbox.location

import java.util.ArrayDeque

/**
 * Ring of recent NMEA sentences for geo debug logging (decoded path still uses [LocValues]).
 */
object GeoDebugNmeaBuffer {
    private const val MAX_LINES = 80
    private val lock = Any()
    private val lines = ArrayDeque<String>(MAX_LINES)

    fun noteSentence(sentence: String) {
        val s = sentence.trim()
        if (s.isEmpty()) return
        synchronized(lock) {
            if (lines.size >= MAX_LINES) {
                lines.removeFirst()
            }
            lines.addLast(s.take(240))
        }
    }

    /** Snapshot of lines noted since last [drainSinceLastTick] (clears the tick window). */
    fun drainSinceLastTick(): List<String> {
        synchronized(lock) {
            if (lines.isEmpty()) return emptyList()
            val out = lines.toList()
            lines.clear()
            return out
        }
    }

    /** Peek without clearing (tests). */
    fun snapshot(): List<String> = synchronized(lock) { lines.toList() }

    fun clear() = synchronized(lock) { lines.clear() }
}
