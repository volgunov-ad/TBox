package vad.dashing.camdbench

import java.io.File
import java.io.RandomAccessFile

/** Feeds complete new lines of a growing text file into [sink]; a half-written line waits. */
class RawTail(private val file: File, private val sink: (String) -> Unit) {
    private var offset = 0L
    private val pending = StringBuilder()

    /** Returns true when new bytes arrived. */
    fun poll(): Boolean {
        val length = file.length()
        if (length <= offset) return false
        val bytes = RandomAccessFile(file, "r").use { raf ->
            raf.seek(offset)
            ByteArray((length - offset).toInt()).also { raf.readFully(it) }
        }
        offset = length
        pending.append(String(bytes, Charsets.UTF_8))
        var newline = pending.indexOf("\n")
        while (newline >= 0) {
            sink(pending.substring(0, newline))
            pending.delete(0, newline + 1)
            newline = pending.indexOf("\n")
        }
        return true
    }
}
