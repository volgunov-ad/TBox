package vad.dashing.tbox.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdbLogcatCaptureTest {

    @Test
    fun fileName_usesPrefixAndTimestamp() {
        val wallMs = 1_700_000_000_000L
        val expectedStamp =
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(wallMs))
        assertEquals(
            "tbox_logcat_${expectedStamp}.txt",
            AdbLogcatCapture.fileName(wallMs),
        )
        assertTrue(AdbLogcatCapture.fileName(wallMs).startsWith(AdbLogcatCapture.FILE_PREFIX))
    }

    @Test
    fun buildCommands_defaultKeepsBuffer() {
        val commands = AdbLogcatCapture.buildCommands(clearBufferFirst = false)
        assertEquals(listOf(AdbLogcatCapture.CAPTURE_COMMAND), commands)
        assertEquals("logcat -v threadtime", commands.single())
        assertFalse(commands.contains(AdbLogcatCapture.CLEAR_COMMAND))
    }

    @Test
    fun buildCommands_clearBufferFirst_prependsClear() {
        val commands = AdbLogcatCapture.buildCommands(clearBufferFirst = true)
        assertEquals(
            listOf(AdbLogcatCapture.CLEAR_COMMAND, AdbLogcatCapture.CAPTURE_COMMAND),
            commands,
        )
        assertEquals("logcat -c", commands.first())
        assertEquals("logcat -v threadtime", commands.last())
    }
}
