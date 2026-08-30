package vad.dashing.tbox.mbcan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BodyComfortDomainTest {
    @Test
    fun shadeRoof_closedOpenTilt() {
        assertEquals(ShadeRoofPosition.Closed, BodyComfortDomain.decodeShadeRoof(0, allowTilt = true))
        assertEquals(ShadeRoofPosition.Closed, BodyComfortDomain.decodeShadeRoof(1, allowTilt = true))
        assertEquals(ShadeRoofPosition.Open, BodyComfortDomain.decodeShadeRoof(6, allowTilt = true))
        assertEquals(ShadeRoofPosition.Open, BodyComfortDomain.decodeShadeRoof(11, allowTilt = true))
        assertEquals(ShadeRoofPosition.Open, BodyComfortDomain.decodeShadeRoof(70, allowTilt = true))
        assertEquals(ShadeRoofPosition.Open, BodyComfortDomain.decodeShadeRoof(100, allowTilt = true))
        assertEquals(ShadeRoofPosition.Tilt, BodyComfortDomain.decodeShadeRoof(12, allowTilt = true))
        assertEquals(ShadeRoofPosition.Tilt, BodyComfortDomain.decodeShadeRoof(102, allowTilt = true))
        assertNull(BodyComfortDomain.decodeShadeRoof(12, allowTilt = false))
        assertNull(BodyComfortDomain.decodeShadeRoof(102, allowTilt = false))
        assertNull(BodyComfortDomain.decodeShadeRoof(-1, allowTilt = true))
        assertNull(BodyComfortDomain.decodeShadeRoof(255, allowTilt = true))
        assertEquals("70", BodyComfortRawRead().format(70))
        assertEquals("—", BodyComfortRawRead().format(-1))
    }

    @Test
    fun window_percentAndVentBand() {
        assertEquals(WindowPanePosition.Closed, BodyComfortDomain.decodeWindow(0))
        assertEquals(WindowPanePosition.Vent, BodyComfortDomain.decodeWindow(1))
        assertEquals(WindowPanePosition.Vent, BodyComfortDomain.decodeWindow(20))
        assertEquals(WindowPanePosition.Vent, BodyComfortDomain.decodeWindow(30))
        assertEquals(WindowPanePosition.Open, BodyComfortDomain.decodeWindow(31))
        assertEquals(WindowPanePosition.Open, BodyComfortDomain.decodeWindow(100))
        assertNull(BodyComfortDomain.decodeWindow(-1))
        assertNull(BodyComfortDomain.decodeWindow(101))
    }

    @Test
    fun automationKeys() {
        assertEquals("closed", BodyComfortDomain.toAutomationState(ShadeRoofPosition.Closed))
        assertEquals("open", BodyComfortDomain.toAutomationState(ShadeRoofPosition.Open))
        assertEquals("tilt", BodyComfortDomain.toAutomationState(ShadeRoofPosition.Tilt))
        assertEquals("vent", BodyComfortDomain.toAutomationState(WindowPanePosition.Vent))
        assertEquals(listOf("closed", "open"), BodyComfortDomain.SHADE_STATE_OPTIONS)
        assertEquals(listOf("closed", "open", "tilt"), BodyComfortDomain.ROOF_STATE_OPTIONS)
        assertEquals(listOf("closed", "open", "vent"), BodyComfortDomain.WINDOW_STATE_OPTIONS)
    }

    @Test
    fun selectedShadeRoofWriteValue_mapsLiveAndKeepsIntermediate() {
        assertEquals(1, BodyComfortDomain.selectedShadeRoofWriteValue(ShadeRoofPosition.Closed, 6, allowTilt = true))
        assertEquals(11, BodyComfortDomain.selectedShadeRoofWriteValue(ShadeRoofPosition.Open, null, allowTilt = true))
        assertEquals(6, BodyComfortDomain.selectedShadeRoofWriteValue(ShadeRoofPosition.Open, 6, allowTilt = true))
        assertEquals(
            MbCanKnownVehiclePropertyId.SUNROOF_TILT,
            BodyComfortDomain.selectedShadeRoofWriteValue(ShadeRoofPosition.Tilt, 6, allowTilt = true),
        )
        assertEquals(6, BodyComfortDomain.selectedShadeRoofWriteValue(ShadeRoofPosition.Tilt, 6, allowTilt = false))
        assertEquals(4, BodyComfortDomain.selectedShadeRoofWriteValue(null, 4, allowTilt = true))
        assertNull(BodyComfortDomain.selectedShadeRoofWriteValue(null, null, allowTilt = true))
    }

    @Test
    fun selectedWindowWriteValue_a9AndA10() {
        assertEquals(0, BodyComfortDomain.selectedWindowWriteValue(WindowPanePosition.Closed, 50, android10 = false))
        assertEquals(20, BodyComfortDomain.selectedWindowWriteValue(WindowPanePosition.Vent, null, android10 = false))
        assertEquals(15, BodyComfortDomain.selectedWindowWriteValue(WindowPanePosition.Vent, 15, android10 = false))
        assertEquals(100, BodyComfortDomain.selectedWindowWriteValue(WindowPanePosition.Open, null, android10 = false))
        assertEquals(80, BodyComfortDomain.selectedWindowWriteValue(WindowPanePosition.Open, 80, android10 = false))
        assertEquals(
            MbCanKnownVehiclePropertyId.WINDOW_A10_CLOSE,
            BodyComfortDomain.selectedWindowWriteValue(WindowPanePosition.Closed, null, android10 = true),
        )
        assertEquals(
            MbCanKnownVehiclePropertyId.WINDOW_A10_OPEN,
            BodyComfortDomain.selectedWindowWriteValue(WindowPanePosition.Open, null, android10 = true),
        )
        assertEquals(
            MbCanKnownVehiclePropertyId.WINDOW_A10_VENT,
            BodyComfortDomain.selectedWindowWriteValue(WindowPanePosition.Vent, null, android10 = true),
        )
        assertEquals(2, BodyComfortDomain.selectedWindowWriteValue(null, 2, android10 = true))
    }
}
