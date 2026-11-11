package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.mbcan.BodyComfortDomain
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId

class ShadeRoofStepperDomainTest {
    @Test
    fun snapPercent_roundsToTen() {
        assertEquals(0, ShadeRoofStepperDomain.snapPercent(0))
        assertEquals(10, ShadeRoofStepperDomain.snapPercent(10))
        assertEquals(20, ShadeRoofStepperDomain.snapPercent(15))
        assertEquals(20, ShadeRoofStepperDomain.snapPercent(24))
        assertEquals(100, ShadeRoofStepperDomain.snapPercent(99))
        assertEquals(100, ShadeRoofStepperDomain.snapPercent(150))
        assertEquals(0, ShadeRoofStepperDomain.snapPercent(-5))
    }

    @Test
    fun normalizePercent_fromLiveAndTilt() {
        assertEquals(0, ShadeRoofStepperDomain.normalizePercent(null, allowTilt = false))
        assertEquals(50, ShadeRoofStepperDomain.normalizePercent(50, allowTilt = false))
        assertEquals(0, ShadeRoofStepperDomain.normalizePercent(0, allowTilt = false))
        assertEquals(
            BodyComfortDomain.ROOF_TILT_PERCENT,
            ShadeRoofStepperDomain.normalizePercent(
                BodyComfortDomain.SUNROOF_STATUS_TILT,
                allowTilt = true,
            ),
        )
        assertEquals(
            BodyComfortDomain.ROOF_TILT_PERCENT,
            ShadeRoofStepperDomain.normalizePercent(
                MbCanKnownVehiclePropertyId.SUNROOF_TILT,
                allowTilt = true,
            ),
        )
        assertEquals(
            0,
            ShadeRoofStepperDomain.normalizePercent(
                BodyComfortDomain.SUNROOF_STATUS_TILT,
                allowTilt = false,
            ),
        )
    }

    @Test
    fun stepPercent_movesByTenWithinRange() {
        assertEquals(60, ShadeRoofStepperDomain.stepPercent(50, allowTilt = false, increase = true))
        assertEquals(40, ShadeRoofStepperDomain.stepPercent(50, allowTilt = false, increase = false))
        assertEquals(100, ShadeRoofStepperDomain.stepPercent(100, allowTilt = false, increase = true))
        assertEquals(0, ShadeRoofStepperDomain.stepPercent(0, allowTilt = false, increase = false))
        assertEquals(
            20,
            ShadeRoofStepperDomain.stepPercent(
                BodyComfortDomain.SUNROOF_STATUS_TILT,
                allowTilt = true,
                increase = true,
            ),
        )
        assertEquals(
            0,
            ShadeRoofStepperDomain.stepPercent(
                BodyComfortDomain.SUNROOF_STATUS_TILT,
                allowTilt = true,
                increase = false,
            ),
        )
    }

    @Test
    fun writeValue_mapsPercentToShadeCommand() {
        assertEquals(1, ShadeRoofStepperDomain.writeValue(0))
        assertEquals(2, ShadeRoofStepperDomain.writeValue(10))
        assertEquals(6, ShadeRoofStepperDomain.writeValue(50))
        assertEquals(11, ShadeRoofStepperDomain.writeValue(100))
    }

    @Test
    fun isUnknownDisplay_whenNoLivePercentIncludingTilt() {
        assertTrue(ShadeRoofStepperDomain.isUnknownDisplay(null))
        assertFalse(ShadeRoofStepperDomain.isUnknownDisplay(40))
        assertTrue(
            ShadeRoofStepperDomain.isUnknownDisplay(BodyComfortDomain.SUNROOF_STATUS_TILT),
        )
        assertTrue(
            ShadeRoofStepperDomain.isUnknownDisplay(MbCanKnownVehiclePropertyId.SUNROOF_TILT),
        )
    }
}
