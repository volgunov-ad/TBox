package vad.dashing.tbox.mbcan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun shadeRoofPercent_tiltIsNotAPercent() {
        assertEquals(0, BodyComfortDomain.shadeRoofPercent(0))
        assertEquals(40, BodyComfortDomain.shadeRoofPercent(40))
        assertEquals(100, BodyComfortDomain.shadeRoofPercent(100))
        assertNull(BodyComfortDomain.shadeRoofPercent(12))
        assertNull(BodyComfortDomain.shadeRoofPercent(102))
        assertNull(BodyComfortDomain.shadeRoofPercent(null))
        assertNull(BodyComfortDomain.shadeRoofPercent(-1))
        assertTrue(BodyComfortDomain.shadeRoofTilted(12))
        assertTrue(BodyComfortDomain.shadeRoofTilted(102))
        assertFalse(BodyComfortDomain.shadeRoofTilted(10))
        assertEquals(1, BodyComfortDomain.percentToWrite(0))
        assertEquals(2, BodyComfortDomain.percentToWrite(10))
        assertEquals(6, BodyComfortDomain.percentToWrite(50))
        assertEquals(11, BodyComfortDomain.percentToWrite(100))
    }

    @Test
    fun automationKeys() {
        assertEquals(
            listOf("0%", "10%", "20%", "30%", "40%", "50%", "60%", "70%", "80%", "90%", "100%"),
            BodyComfortDomain.SHADE_STATE_OPTIONS,
        )
        assertEquals(
            BodyComfortDomain.SHADE_STATE_OPTIONS + "tilt",
            BodyComfortDomain.ROOF_STATE_OPTIONS,
        )
        assertEquals(listOf("0%", "20%", "80%", "100%"), BodyComfortDomain.WINDOW_STATE_OPTIONS)
    }

    @Test
    fun selectedShadeRoofWriteValues_mapsLivePercent() {
        assertEquals(setOf(1), BodyComfortDomain.selectedShadeRoofWriteValues(0, 6, allowTilt = true))
        assertEquals(setOf(2), BodyComfortDomain.selectedShadeRoofWriteValues(10, null, allowTilt = false))
        assertEquals(setOf(6), BodyComfortDomain.selectedShadeRoofWriteValues(50, null, allowTilt = true))
        assertEquals(setOf(11), BodyComfortDomain.selectedShadeRoofWriteValues(100, null, allowTilt = true))
        assertEquals(setOf(4), BodyComfortDomain.selectedShadeRoofWriteValues(null, 4, allowTilt = true))
        assertEquals(emptySet<Int>(), BodyComfortDomain.selectedShadeRoofWriteValues(null, null, allowTilt = true))
    }

    @Test
    fun selectedShadeRoofWriteValues_roofTiltLightsBothButtons() {
        assertEquals(
            setOf(2, MbCanKnownVehiclePropertyId.SUNROOF_TILT),
            BodyComfortDomain.selectedShadeRoofWriteValues(102, null, allowTilt = true),
        )
        assertEquals(
            setOf(2, MbCanKnownVehiclePropertyId.SUNROOF_TILT),
            BodyComfortDomain.selectedShadeRoofWriteValues(10, null, allowTilt = true),
        )
        assertEquals(
            setOf(2),
            BodyComfortDomain.selectedShadeRoofWriteValues(10, null, allowTilt = false),
        )
    }

    @Test
    fun selectedWindowWriteValues_a9SnapsToNearestStop() {
        assertEquals(setOf(0), BodyComfortDomain.selectedWindowWriteValues(0, 50, android10 = false))
        assertEquals(setOf(20), BodyComfortDomain.selectedWindowWriteValues(20, null, android10 = false))
        assertEquals(setOf(20), BodyComfortDomain.selectedWindowWriteValues(25, null, android10 = false))
        assertEquals(setOf(80), BodyComfortDomain.selectedWindowWriteValues(55, null, android10 = false))
        assertEquals(setOf(100), BodyComfortDomain.selectedWindowWriteValues(100, null, android10 = false))
        assertEquals(setOf(100), BodyComfortDomain.selectedWindowWriteValues(95, null, android10 = false))
        assertEquals(setOf(80), BodyComfortDomain.selectedWindowWriteValues(null, 80, android10 = false))
        assertEquals(emptySet<Int>(), BodyComfortDomain.selectedWindowWriteValues(null, null, android10 = false))
    }

    @Test
    fun selectedWindowWriteValues_a10Commands() {
        assertEquals(
            setOf(MbCanKnownVehiclePropertyId.WINDOW_A10_CLOSE),
            BodyComfortDomain.selectedWindowWriteValues(0, null, android10 = true),
        )
        assertEquals(
            setOf(MbCanKnownVehiclePropertyId.WINDOW_A10_VENT),
            BodyComfortDomain.selectedWindowWriteValues(20, null, android10 = true),
        )
        assertEquals(
            setOf(MbCanKnownVehiclePropertyId.WINDOW_A10_OPEN),
            BodyComfortDomain.selectedWindowWriteValues(80, null, android10 = true),
        )
        assertEquals(
            setOf(2),
            BodyComfortDomain.selectedWindowWriteValues(null, 2, android10 = true),
        )
    }
}
