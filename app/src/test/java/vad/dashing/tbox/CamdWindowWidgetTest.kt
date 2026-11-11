package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException

class CamdWindowWidgetTest {
    @Test
    fun openLineUsesScreenFractions() {
        assertEquals(
            "OPEN 4 0.0200 0.5500 0.4000 0.4000",
            camdWindowOpenLine(4, 2, 55, 40, 40),
        )
    }

    @Test
    fun openLineKeepsTheWindowOnScreen() {
        assertEquals(
            "OPEN 5 0.2000 0.0000 0.8000 0.4000",
            camdWindowOpenLine(5, 90, -10, 80, 40),
        )
    }

    @Test
    fun unknownCameraBecomesTheFrontCamera() {
        assertEquals("CLOSE 8", camdWindowCloseLine(99))
        assertEquals(8, normalizeCamdWindowCamera(2))
    }

    @Test
    fun statusReplyReadsWindowsAvmAndKeepFlag() {
        val status = parseCamdStatusReply("OK windows=4,5 avm=stopped keep_avm=1")
        assertEquals(listOf(4, 5), status?.windows)
        assertEquals("stopped", status?.avm)
        assertEquals(true, status?.keepAvm)
        assertEquals(emptyList<Int>(), parseCamdStatusReply("OK windows=none avm=running keep_avm=0")?.windows)
        assertEquals(null, parseCamdStatusReply("ERR down"))
    }

    @Test
    fun automationLinesMatchTheCameraApi() {
        assertEquals("AVM ON", camdKeepAvmCommandLine(true))
        assertEquals("AVM OFF", camdKeepAvmCommandLine(false))
        assertEquals(
            "OPEN 5 0.0200 0.5500 0.4000 0.4000",
            camdAutomationCommandLine(5, "open 2 55 40 40"),
        )
        assertEquals("CLOSE 8", camdAutomationCommandLine(8, "close"))
        assertEquals("CLOSE", camdAutomationCommandLine(4, "close_all"))
        assertEquals(null, camdAutomationCommandLine(2, "open 2 55 40 40"))
        assertEquals(null, camdAutomationCommandLine(4, "open 90 0 80 40"))
    }

    @Test
    fun statusPollDoesNotWakeTheService() {
        assertFalse(camdCommandWakesService("STATUS"))
        assertFalse(camdCommandWakesService(" status "))
        assertTrue(camdCommandWakesService("AVM ON"))
        assertTrue(camdCommandWakesService("OPEN 4 0.0200 0.5500 0.4000 0.4000"))
    }

    @Test
    fun onlyAClosedPortCountsAsTheServiceBeingDown() {
        assertTrue(isCamdPortClosed(ConnectException("Connection refused")))
        assertTrue(isCamdPortClosed(SocketTimeoutException("failed to connect to /127.0.0.1")))
        assertFalse(isCamdPortClosed(SocketTimeoutException("Read timed out")))
        assertFalse(isCamdPortClosed(IllegalStateException("ERR bad_command")))
    }
}
