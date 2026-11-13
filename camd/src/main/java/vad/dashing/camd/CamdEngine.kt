package vad.dashing.camd

import java.io.File

/**
 * Read-only access to the root-side status file and the qcarcam log tail.
 * The command daemon was retired; windows are driven by [CameraWindows].
 */
class CamdEngine(filesDir: File) {

    fun status(): String = readIfAllowed(File(DaemonScripts.STATUS_FILE))

    fun logTail(): String {
        val text = readIfAllowed(File(DaemonScripts.QCAR_LOG))
        if (text.length <= 500) return text
        return text.takeLast(500)
    }

    private fun readIfAllowed(file: File): String {
        return runCatching { if (file.canRead()) file.readText().trim() else "" }.getOrDefault("")
    }
}
