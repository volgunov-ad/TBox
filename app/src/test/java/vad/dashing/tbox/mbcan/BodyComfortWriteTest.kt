package vad.dashing.tbox.mbcan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.HeadUnitCanMode

class BodyComfortWriteTest {
    @Test
    fun a9WindowBytes_leaveOtherPanesUnchanged() {
        val leave = BodyComfortWrite.WINDOW_UNCHANGED
        assertEquals(
            BodyComfortWrite.A9WindowBytes(40, 40, 40, 40),
            BodyComfortWrite.a9WindowBytes(MbCanKnownVehiclePropertyId.WINDOW_POS, 40),
        )
        assertEquals(
            BodyComfortWrite.A9WindowBytes(leave, 75, leave, leave),
            BodyComfortWrite.a9WindowBytes(MbCanKnownVehiclePropertyId.WINDOW_FL_POS, 75),
        )
        assertEquals(
            BodyComfortWrite.A9WindowBytes(10, leave, leave, leave),
            BodyComfortWrite.a9WindowBytes(MbCanKnownVehiclePropertyId.WINDOW_FR_POS, 10),
        )
        assertEquals(
            BodyComfortWrite.A9WindowBytes(leave, leave, leave, 0),
            BodyComfortWrite.a9WindowBytes(MbCanKnownVehiclePropertyId.WINDOW_RL_POS, 0),
        )
        assertEquals(
            BodyComfortWrite.A9WindowBytes(leave, leave, 100, leave),
            BodyComfortWrite.a9WindowBytes(MbCanKnownVehiclePropertyId.WINDOW_RR_POS, 100),
        )
    }

    @Test
    fun windowValues_dependOnHeadUnit() {
        assertEquals(
            listOf(0, 20, 80, 100),
            BodyComfortWrite.windowValues(HeadUnitCanMode.Android9MbCan),
        )
        assertEquals(
            listOf(1, 2, 3),
            BodyComfortWrite.windowValues(HeadUnitCanMode.Android10Vhal),
        )
        assertTrue(BodyComfortWrite.isAllowedWindowValue(0, android10 = false))
        assertTrue(BodyComfortWrite.isAllowedWindowValue(20, android10 = false))
        assertTrue(BodyComfortWrite.isAllowedWindowValue(80, android10 = false))
        assertTrue(BodyComfortWrite.isAllowedWindowValue(100, android10 = false))
        assertFalse(BodyComfortWrite.isAllowedWindowValue(5, android10 = false))
        assertFalse(BodyComfortWrite.isAllowedWindowValue(50, android10 = false))
        assertFalse(BodyComfortWrite.isAllowedWindowValue(47, android10 = false))
        assertFalse(BodyComfortWrite.isAllowedWindowValue(50, android10 = true))
        assertTrue(BodyComfortWrite.isAllowedWindowValue(2, android10 = true))
    }
}
