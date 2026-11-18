package vad.dashing.camd

import java.io.File

/**
 * Read-only access to the root-side status file and the qcarcam log tail.
 * The command daemon was retired; windows are driven by [CameraWindows].
 */
class CamdEngine(filesDir: File) {

    private class FileCache {
        var stamp: Pair<Long, Long>? = null
        var text: String = ""
    }

    private val statusCache = FileCache()
    private val qcarCache = FileCache()

    fun status(): String = readIfAllowed(File(DaemonScripts.STATUS_FILE), statusCache)

    fun logTail(): String {
        val text = readIfAllowed(File(DaemonScripts.QCAR_LOG), qcarCache)
        if (text.length <= 500) return text
        return text.takeLast(500)
    }

    /**
     * Cached full read keyed by (length, mtime): the UI poll asks every second,
     * and unchanged root-side files must not be re-read from slow eMMC.
     */
    private fun readIfAllowed(file: File, cache: FileCache): String {
        return runCatching {
            if (file.canRead()) {
                val stamp = file.length() to file.lastModified()
                synchronized(cache) {
                    if (cache.stamp == stamp) {
                        cache.text
                    } else {
                        file.readText().trim().also {
                            cache.stamp = stamp
                            cache.text = it
                        }
                    }
                }
            } else {
                ""
            }
        }.getOrDefault("")
    }
}
