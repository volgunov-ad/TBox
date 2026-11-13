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
    fun openLineOmitsZeroRotationForOlderCamd() {
        assertEquals(
            "OPEN 8 0.0200 0.5500 0.4000 0.4000",
            camdWindowOpenLine(8, 2, 55, 40, 40, 0),
        )
        assertEquals(
            "OPEN 8 0.0200 0.5500 0.4000 0.4000 90",
            camdWindowOpenLine(8, 2, 55, 40, 40, 90),
        )
    }

    @Test
    fun openLineOmitsFlipsWhenBothZeroAndKeepsRotationSlotWhenFlipSet() {
        assertEquals(
            "OPEN 8 0.0200 0.5500 0.4000 0.4000",
            camdWindowOpenLine(8, 2, 55, 40, 40, 0, flipH = false, flipV = false),
        )
        assertEquals(
            "OPEN 8 0.0200 0.5500 0.4000 0.4000 0 1",
            camdWindowOpenLine(8, 2, 55, 40, 40, 0, flipH = true, flipV = false),
        )
        assertEquals(
            "OPEN 8 0.0200 0.5500 0.4000 0.4000 90 1 1",
            camdWindowOpenLine(8, 2, 55, 40, 40, 90, flipH = true, flipV = true),
        )
        assertEquals(
            "OPEN 8 0.0200 0.5500 0.4000 0.4000 0 0 1",
            camdWindowOpenLine(8, 2, 55, 40, 40, 0, flipH = false, flipV = true),
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
    fun openLineAppendsCropSuffixOnlyWhenSet() {
        assertEquals(
            "OPEN 8 0.0200 0.5500 0.4000 0.4000 crop=0.1000,0.1000,0.8000,0.8000",
            camdWindowOpenLine(8, 2, 55, 40, 40, crop = CamdWindowCrop(10, 10, 80, 80)),
        )
        assertEquals(
            "OPEN 8 0.0200 0.5500 0.4000 0.4000 0 1 crop=0.0000,0.0000,1.0000,0.5000",
            camdWindowOpenLine(8, 2, 55, 40, 40, 0, flipH = true, flipV = false, crop = CamdWindowCrop(0, 0, 100, 50)),
        )
        assertEquals(
            "OPEN 8 0.0200 0.5500 0.4000 0.4000",
            camdWindowOpenLine(8, 2, 55, 40, 40, crop = null),
        )
    }

    @Test
    fun normalizeCamdWindowCropKeepsTheRectInsideTheFrame() {
        assertEquals(CamdWindowCrop(10, 0, 70, 100), normalizeCamdWindowCrop(10, 100, 70, 150))
        assertEquals(CamdWindowCrop(0, 0, 5, 100), normalizeCamdWindowCrop(-5, -5, 1, 200))
        assertEquals(CamdWindowCrop(20, 10, 80, 40), normalizeCamdWindowCrop(50, 10, 80, 40))
    }

    @Test
    fun cropIssueMirrorsGeometryBounds() {
        assertEquals(null, camdWindowCropIssue(0, 0, 100, 100))
        assertEquals(null, camdWindowCropIssue(10, 10, 80, 80))
        assertTrue(camdWindowCropIssue(0, 0, 4, 50) != null)
        assertTrue(camdWindowCropIssue(60, 0, 50, 50) != null)
        assertTrue(camdWindowCropIssue(-1, 0, 50, 50) != null)
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
            "OPEN 5 0.0200 0.5500 0.4000 0.4000 90",
            camdAutomationCommandLine(5, "open 2 55 40 40 90"),
        )
        assertEquals(
            "OPEN 5 0.0200 0.5500 0.4000 0.4000",
            camdAutomationCommandLine(5, "open 2 55 40 40"),
        )
        assertEquals(
            "OPEN 5 0.0200 0.5500 0.4000 0.4000 0 1 1",
            camdAutomationCommandLine(5, "open 2 55 40 40 0 1 1"),
        )
        assertEquals("CLOSE 8", camdAutomationCommandLine(8, "close"))
        assertEquals("CLOSE", camdAutomationCommandLine(4, "close_all"))
        assertEquals(null, camdAutomationCommandLine(2, "open 2 55 40 40"))
        assertEquals(null, camdAutomationCommandLine(4, "open 90 0 80 40"))
    }

    @Test
    fun automationArgsParseTheCropToken() {
        val args = parseCamdAutomationWindowArgs("open 2 55 40 40 90 0 1 crop=5,10,90,80")!!
        assertEquals(CamdWindowCrop(5, 10, 90, 80), args.crop)
        assertEquals(90, args.rotation)
        assertFalse(args.flipH)
        assertTrue(args.flipV)
        assertEquals(
            CamdWindowCrop(0, 0, 50, 50),
            parseCamdAutomationWindowArgs("open crop=0,0,50,50 2 55 40 40")!!.crop,
        )
        assertEquals(null, parseCamdAutomationWindowArgs("open 2 55 40 40")!!.crop)
        assertEquals(null, parseCamdAutomationWindowArgs("close crop=0,0,50,50")!!.crop)
    }

    @Test
    fun automationArgsRejectBadCropTokens() {
        assertEquals(null, parseCamdAutomationWindowArgs("open 2 55 40 40 crop=1,2,3"))
        assertEquals(null, parseCamdAutomationWindowArgs("open 2 55 40 40 crop=1,2,3,4,5"))
        assertEquals(null, parseCamdAutomationWindowArgs("open 2 55 40 40 crop=a,b,c,d"))
        assertEquals(null, parseCamdAutomationWindowArgs("open 2 55 40 40 crop=-1,0,50,50"))
        assertEquals(null, parseCamdAutomationWindowArgs("open 2 55 40 40 crop=0,0,4,50"))
        assertEquals(null, parseCamdAutomationWindowArgs("open 2 55 40 40 crop=0,0,101,50"))
        assertEquals(null, parseCamdAutomationWindowArgs("open 2 55 40 40 crop=50,0,60,50"))
        assertEquals(null, parseCamdAutomationWindowArgs("open 2 55 40 40 crop=0,0,50,50 crop=1,1,2,2"))
    }

    @Test
    fun automationCommandLineCarriesAndValidatesCrop() {
        assertEquals(
            "OPEN 8 0.0200 0.5500 0.4000 0.4000 crop=0.1000,0.1000,0.8000,0.8000",
            camdAutomationCommandLine(8, "open 2 55 40 40 crop=10,10,80,80"),
        )
        assertEquals(
            "OPEN 8 0.0200 0.5500 0.4000 0.4000 0 1 1 crop=0.0000,0.0000,1.0000,0.5000",
            camdAutomationCommandLine(8, "open 2 55 40 40 0 1 1 crop=0,0,100,50"),
        )
        assertEquals(null, camdAutomationCommandLine(8, "open 2 55 40 40 crop=50,0,60,50"))
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

    @Test
    fun failureDetailPrefersErrorToken() {
        assertEquals("overlay_permission", camdFailureDetail("OK state=error error=overlay_permission"))
        assertEquals("bad_command", camdFailureDetail("ERR bad_command"))
        assertEquals(null, camdFailureDetail(""))
    }
}
