package vad.dashing.tbox.adb

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pure helpers for ADB-tab logcat → Downloads capture (filename + shell commands).
 */
object AdbLogcatCapture {
    const val FILE_PREFIX = "tbox_logcat_"
    const val FILE_EXTENSION = "txt"
    const val CLEAR_COMMAND = "logcat -c"
    const val CAPTURE_COMMAND = "logcat -v threadtime"

    fun fileName(wallMs: Long): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(wallMs))
        return "$FILE_PREFIX$stamp.$FILE_EXTENSION"
    }

    /**
     * Commands to run before / as the streaming capture.
     * When [clearBufferFirst] is true, [CLEAR_COMMAND] runs first (short execute),
     * then [CAPTURE_COMMAND] is streamed until stop.
     */
    fun buildCommands(clearBufferFirst: Boolean): List<String> =
        if (clearBufferFirst) {
            listOf(CLEAR_COMMAND, CAPTURE_COMMAND)
        } else {
            listOf(CAPTURE_COMMAND)
        }
}
