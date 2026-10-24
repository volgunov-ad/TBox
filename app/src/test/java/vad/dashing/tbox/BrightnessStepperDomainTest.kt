package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrightnessStepperDomainTest {
    @Test
    fun normalizeLevel_clampsAndDefaults() {
        assertEquals(5, BrightnessStepperDomain.normalizeLevel(null))
        assertEquals(1, BrightnessStepperDomain.normalizeLevel(0))
        assertEquals(10, BrightnessStepperDomain.normalizeLevel(99))
        assertEquals(7, BrightnessStepperDomain.normalizeLevel(7))
    }

    @Test
    fun stepLevel_movesByOneWithinRange() {
        assertEquals(6, BrightnessStepperDomain.stepLevel(5, increase = true))
        assertEquals(4, BrightnessStepperDomain.stepLevel(5, increase = false))
        assertEquals(10, BrightnessStepperDomain.stepLevel(10, increase = true))
        assertEquals(1, BrightnessStepperDomain.stepLevel(1, increase = false))
        assertEquals(6, BrightnessStepperDomain.stepLevel(null, increase = true))
    }

    @Test
    fun stepWithAutoOff_flagsDisableWhenAutoWasOn() {
        val fromAuto = BrightnessStepperDomain.stepWithAutoOff(7, autoOn = true, increase = true)
        assertEquals(8, fromAuto.level)
        assertTrue(fromAuto.turnAutoOff)

        val fromManual = BrightnessStepperDomain.stepWithAutoOff(7, autoOn = false, increase = false)
        assertEquals(6, fromManual.level)
        assertFalse(fromManual.turnAutoOff)
    }

    @Test
    fun icmMode_mapping() {
        assertTrue(BrightnessStepperDomain.isIcmAuto(0))
        assertFalse(BrightnessStepperDomain.isIcmAuto(1))
        assertFalse(BrightnessStepperDomain.isIcmAuto(null))
    }
}
