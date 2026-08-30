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
        assertEquals(ShadeRoofPosition.Open, BodyComfortDomain.decodeShadeRoof(100, allowTilt = true))
        assertEquals(ShadeRoofPosition.Tilt, BodyComfortDomain.decodeShadeRoof(12, allowTilt = true))
        assertNull(BodyComfortDomain.decodeShadeRoof(12, allowTilt = false))
        assertNull(BodyComfortDomain.decodeShadeRoof(-1, allowTilt = true))
        assertNull(BodyComfortDomain.decodeShadeRoof(255, allowTilt = true))
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
}
