package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Test
import vad.dashing.tbox.utils.formatGearBoxModeWithCurrentGear

class GearBoxDisplayFormatTest {

    @Test
    fun driveMode_showsGearNumber() {
        assertEquals("D3", formatGearBoxModeWithCurrentGear("D", 3))
    }

    @Test
    fun driveMode_zeroGear_showsLetterOnly() {
        assertEquals("D", formatGearBoxModeWithCurrentGear("D", 0))
    }

    @Test
    fun driveMode_nullGear_showsLetterOnly() {
        assertEquals("D", formatGearBoxModeWithCurrentGear("D", null))
    }

    @Test
    fun parkReverseNeutral_neverShowGearDigit() {
        assertEquals("P", formatGearBoxModeWithCurrentGear("P", 0))
        assertEquals("R", formatGearBoxModeWithCurrentGear("R", 1))
        assertEquals("N", formatGearBoxModeWithCurrentGear("N", 5))
    }

    @Test
    fun unknownMode_showsModeOnly() {
        assertEquals("N/A", formatGearBoxModeWithCurrentGear("N/A", 2))
    }

    @Test
    fun blankMode_returnsEmpty() {
        assertEquals("", formatGearBoxModeWithCurrentGear("", 1))
    }
}
