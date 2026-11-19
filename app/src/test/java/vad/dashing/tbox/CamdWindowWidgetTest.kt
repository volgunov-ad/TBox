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
    fun openLineAppendsCircleShapeToken() {
        assertEquals(
            "OPEN 4 0.0200 0.5500 0.4000 0.4000 shape=circle",
            camdWindowOpenLine(4, 2, 55, 40, 40, circle = true),
        )
        assertEquals(
            "OPEN 4 0.0200 0.5500 0.4000 0.4000 crop=0.1000,0.1000,0.8000,0.8000 shape=circle",
            camdWindowOpenLine(4, 2, 55, 40, 40, crop = CamdWindowCrop(10, 10, 80, 80), circle = true),
        )
    }

    @Test
    fun presetOpenLineCarriesNameAndShape() {
        assertEquals(
            "OPEN preset=front_left 0.0200 0.5500 0.4000 0.4000",
            camdWindowPresetOpenLine("front_left", 2, 55, 40, 40),
        )
        assertEquals(
            "OPEN preset=my_corner 0.0200 0.5500 0.4000 0.4000 shape=circle",
            camdWindowPresetOpenLine("my_corner", 2, 55, 40, 40, circle = true),
        )
        assertEquals(null, camdWindowPresetOpenLine("Bad Name", 2, 55, 40, 40))
        assertEquals(null, camdWindowPresetOpenLine("", 2, 55, 40, 40))
    }

    @Test
    fun presetNameRules() {
        assertEquals(null, camdPresetNameIssue("front_left"))
        assertEquals(null, camdPresetNameIssue("a"))
        assertEquals(null, camdPresetNameIssue("x9_z"))
        assertTrue(camdPresetNameIssue("") != null)
        assertTrue(camdPresetNameIssue("Bad") != null)
        assertTrue(camdPresetNameIssue("with space") != null)
        assertTrue(camdPresetNameIssue("a".repeat(33)) != null)
        assertEquals(null, camdPresetNameIssue("a".repeat(32)))
    }

    @Test
    fun frameModesNormalizeToKnownValues() {
        assertEquals(CAMD_FRAME_MODE_AS_IS, normalizeCamdWindowFrameMode(0))
        assertEquals(CAMD_FRAME_MODE_CUSTOM, normalizeCamdWindowFrameMode(1))
        assertEquals(CAMD_FRAME_MODE_PRESET, normalizeCamdWindowFrameMode(2))
        assertEquals(CAMD_FRAME_MODE_AS_IS, normalizeCamdWindowFrameMode(7))
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
        assertEquals(8, normalizeCamdWindowCamera(39))
        assertEquals(8, normalizeCamdWindowCamera(46))
    }

    @Test
    fun stockViewIdsPassThroughNormalizationAndClose() {
        assertEquals(40, normalizeCamdWindowCamera(40))
        assertEquals(42, normalizeCamdWindowCamera(42))
        assertEquals(45, normalizeCamdWindowCamera(45))
        assertEquals("CLOSE 42", camdWindowCloseLine(42))
        assertEquals(
            "OPEN 40 0.0200 0.5500 0.4000 0.4000",
            camdWindowOpenLine(40, 2, 55, 40, 40),
        )
    }

    @Test
    fun statusReplyReadsWindowsAvmAndKeepFlag() {
        val status = parseCamdStatusReply("OK windows=4,5 avm=stopped keep_avm=1")
        assertEquals(listOf(4, 5), status?.windows)
        assertEquals("stopped", status?.avm)
        assertEquals(true, status?.keepAvm)
        assertEquals(null, status?.recorderEnabled)
        assertEquals(null, status?.recordingActive)
        assertEquals(emptyList<Int>(), parseCamdStatusReply("OK windows=none avm=running keep_avm=0")?.windows)
        assertEquals(null, parseCamdStatusReply("ERR down"))
    }

    @Test
    fun statusReplyReadsSeparateRecorderEnabledAndActiveFlags() {
        val status = parseCamdStatusReply(
            "OK windows=none avm=running keep_avm=0 rec_enabled=1 rec_active=0",
        )
        assertEquals(true, status?.recorderEnabled)
        assertEquals(false, status?.recordingActive)
        val both = parseCamdStatusReply(
            "OK windows=8 avm=stopped keep_avm=1 rec_enabled=1 rec_active=1",
        )
        assertEquals(true, both?.recorderEnabled)
        assertEquals(true, both?.recordingActive)
    }

    @Test
    fun keepAvmTileLooksActiveWhenKeepOffIsOff() {
        assertEquals(true, camdKeepAvmWidgetLooksActive(keepAvm = false))
        assertEquals(false, camdKeepAvmWidgetLooksActive(keepAvm = true))
    }

    @Test
    fun recorderTilePaintPrefersRecordingRedOverEnabled() {
        assertEquals(
            CamdRecorderWidgetPaint.Unknown,
            camdRecorderWidgetPaint(null, null),
        )
        assertEquals(
            CamdRecorderWidgetPaint.Disabled,
            camdRecorderWidgetPaint(false, false),
        )
        assertEquals(
            CamdRecorderWidgetPaint.EnabledIdle,
            camdRecorderWidgetPaint(true, false),
        )
        assertEquals(
            CamdRecorderWidgetPaint.Recording,
            camdRecorderWidgetPaint(true, true),
        )
        assertEquals(
            CamdRecorderWidgetPaint.Recording,
            camdRecorderWidgetPaint(false, true),
        )
    }

    @Test
    fun automationLinesMatchTheCameraApi() {
        assertEquals("AVM ON", camdKeepAvmCommandLine(true))
        assertEquals("AVM OFF", camdKeepAvmCommandLine(false))
        assertEquals("REC ON", camdRecorderCommandLine(true))
        assertEquals("REC OFF", camdRecorderCommandLine(false))
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
        // crop tokens on close/close_all are rejected outright
        assertEquals(null, parseCamdAutomationWindowArgs("close crop=0,0,50,50"))
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
    fun automationArgsParsePresetAndShapeTokens() {
        val presetArgs = parseCamdAutomationWindowArgs("open preset=front_left 2 55 40 40")!!
        assertEquals("front_left", presetArgs.presetName)
        assertEquals(2, presetArgs.x)
        assertFalse(presetArgs.circle)
        val circleArgs = parseCamdAutomationWindowArgs("open preset=front_left 2 55 40 40 shape=circle")!!
        assertEquals("front_left", circleArgs.presetName)
        assertTrue(circleArgs.circle)
        val plainCircle = parseCamdAutomationWindowArgs("open 2 55 40 40 shape=circle")!!
        assertEquals(null, plainCircle.presetName)
        assertTrue(plainCircle.circle)
        assertEquals(false, parseCamdAutomationWindowArgs("open 2 55 40 40")!!.circle)
    }

    @Test
    fun automationArgsRejectBadPresetForms() {
        assertEquals(null, parseCamdAutomationWindowArgs("open preset=front_left 2 55 40 40 90"))
        assertEquals(null, parseCamdAutomationWindowArgs("open preset=front_left 2 55 40 40 crop=0,0,1,1"))
        assertEquals(null, parseCamdAutomationWindowArgs("open preset=Bad 2 55 40 40"))
        assertEquals(null, parseCamdAutomationWindowArgs("open preset=front_left 2 55 40 40 preset=front_right"))
        assertEquals(null, parseCamdAutomationWindowArgs("close preset=front_left"))
        assertEquals(null, parseCamdAutomationWindowArgs("close 4 shape=circle"))
        assertEquals(null, parseCamdAutomationWindowArgs("open 2 55 40 40 shape=oval"))
    }

    @Test
    fun automationCommandLineBuildsPresetLines() {
        assertEquals(
            "OPEN preset=front_left 0.0200 0.5500 0.4000 0.4000",
            camdAutomationCommandLine(8, "open preset=front_left 2 55 40 40"),
        )
        assertEquals(
            "OPEN preset=front_left 0.0200 0.5500 0.4000 0.4000 shape=circle",
            camdAutomationCommandLine(8, "open preset=front_left 2 55 40 40 shape=circle"),
        )
        assertEquals(
            "OPEN 8 0.0200 0.5500 0.4000 0.4000 shape=circle",
            camdAutomationCommandLine(8, "open 2 55 40 40 shape=circle"),
        )
        assertEquals(null, camdAutomationCommandLine(8, "open preset=front_left 90 0 80 40"))
    }

    @Test
    fun presetsReplyParsesCameraNameKind() {
        val presets = parseCamdPresetsReply("OK presets=8:front_left:builtin,4:my_corner:user")
        assertEquals(2, presets?.size)
        assertEquals(CamdPresetInfo(8, "front_left", true), presets?.get(0))
        assertEquals(CamdPresetInfo(4, "my_corner", false), presets?.get(1))
        assertEquals(emptyList<CamdPresetInfo>(), parseCamdPresetsReply("OK presets=none"))
        assertEquals(null, parseCamdPresetsReply("ERR down"))
        assertEquals(null, parseCamdPresetsReply("OK presets=8:front_left"))
        assertEquals(null, parseCamdPresetsReply("OK presets=8:Bad:builtin"))
        assertEquals(null, parseCamdPresetsReply("OK presets=8:front_left:other"))
    }

    @Test
    fun formatAndParseRoundTripWithPresetAndShape() {
        val formatted = formatCamdAutomationWindowArgs(
            CAMD_ACTION_OPEN,
            2,
            55,
            40,
            40,
            presetName = "my_corner",
            circle = true,
        )
        assertEquals("open preset=my_corner 2 55 40 40 shape=circle", formatted)
        val args = parseCamdAutomationWindowArgs(formatted)!!
        assertEquals("my_corner", args.presetName)
        assertTrue(args.circle)
        val customFormatted = formatCamdAutomationWindowArgs(
            CAMD_ACTION_OPEN,
            2,
            55,
            40,
            40,
            rotation = 90,
            flipH = true,
            crop = CamdWindowCrop(0, 0, 100, 50),
            circle = true,
        )
        assertEquals("open 2 55 40 40 90 1 crop=0,0,100,50 shape=circle", customFormatted)
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
