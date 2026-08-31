package vad.dashing.tbox.esp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vad.dashing.tbox.mbcan.FirmwareVehicleJsonMapper
import vad.dashing.tbox.mbcan.MbCanCommand
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId
import vad.dashing.tbox.mbcan.MbCanSignal

class HuCanMarkLogTest {
    @Before
    fun setUp() {
        CompanionProtocolLogRecorder.setHuMarksEnabled(false)
    }

    @Test
    fun formatCommand_setPropertyIncludesNameAndId() {
        val text = HuCanMarkLog.formatCommand(
            MbCanCommand.SetProperty(MbCanKnownVehiclePropertyId.HVAC_FAN_SPEED, 4),
        )
        assertTrue(text.contains("SetProperty"))
        assertTrue(text.contains("HVAC_FAN_SPEED"))
        assertTrue(text.contains("(${MbCanKnownVehiclePropertyId.HVAC_FAN_SPEED})"))
        assertTrue(text.endsWith("=4"))
    }

    @Test
    fun formatCommand_refreshSignal() {
        val text = HuCanMarkLog.formatCommand(MbCanCommand.RefreshSignal(MbCanSignal.HvacFanSpeed))
        assertEquals("RefreshSignal HvacFanSpeed", text)
    }

    @Test
    fun shouldMarkVhalPush_skipsHighRateTelemetry() {
        assertFalse(HuCanMarkLog.shouldMarkVhalPush(FirmwareVehicleJsonMapper.VHAL_CAR_SPEED_PROPERTY_ID))
        assertFalse(HuCanMarkLog.shouldMarkVhalPush(FirmwareVehicleJsonMapper.VHAL_ENGINE_RPM_PROPERTY_ID))
        assertTrue(HuCanMarkLog.shouldMarkVhalPush(289_415_171)) // HVAC fan speed read echo
    }

    @Test
    fun uniqueConstNameMap_resolvesFanSpeed() {
        val map = HuCanMarkLog.uniqueConstNameMap(MbCanKnownVehiclePropertyId::class.java)
        assertEquals(
            "HVAC_FAN_SPEED",
            map[MbCanKnownVehiclePropertyId.HVAC_FAN_SPEED],
        )
        assertEquals(
            "HVAC_FRONT_OFF",
            map[MbCanKnownVehiclePropertyId.HVAC_FRONT_OFF],
        )
    }

    @Test
    fun appendMark_noopWhenDisabledOrNotRecording() {
        CompanionProtocolLogRecorder.setHuMarksEnabled(true)
        CompanionProtocolLogRecorder.appendMark("UI", "should-not-crash")
        CompanionProtocolLogRecorder.setHuMarksEnabled(false)
        CompanionProtocolLogRecorder.appendMark("UI", "still-noop")
    }
}
