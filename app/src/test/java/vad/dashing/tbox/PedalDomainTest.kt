package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.mbcan.PedalDomain

class PedalDomainTest {
    @Test
    fun decodeGasPedalPercent_validRange() {
        assertEquals(0f, PedalDomain.decodeGasPedalPercent(0f, 0))
        assertEquals(0.5f, PedalDomain.decodeGasPedalPercent(0.5f, 0))
        assertEquals(50f, PedalDomain.decodeGasPedalPercent(50f, 0))
        assertEquals(100f, PedalDomain.decodeGasPedalPercent(100f, 0))
    }

    @Test
    fun decodeGasPedalPercent_missingInvalidTreatedAsValid() {
        assertEquals(12f, PedalDomain.decodeGasPedalPercent(12f, null))
    }

    @Test
    fun decodeGasPedalPercent_rejectsInvalidFlag() {
        assertNull(PedalDomain.decodeGasPedalPercent(40f, 1))
        assertNull(PedalDomain.decodeGasPedalPercent(40f, 2))
        assertNull(PedalDomain.decodeGasPedalPercent(0f, 1))
    }

    @Test
    fun decodeGasPedalPercent_rejectsMissingOrOutOfRange() {
        assertNull(PedalDomain.decodeGasPedalPercent(null, 0))
        assertNull(PedalDomain.decodeGasPedalPercent(Float.NaN, 0))
        assertNull(PedalDomain.decodeGasPedalPercent(-0.1f, 0))
        assertNull(PedalDomain.decodeGasPedalPercent(100.1f, 0))
    }

    @Test
    fun decodeVhalGasPedalPercent_scalesRaw0to255() {
        assertEquals(0f, PedalDomain.decodeVhalGasPedalPercent(0f, 0))
        assertEquals(100f, PedalDomain.decodeVhalGasPedalPercent(255f, 0))
        assertEquals(50f, PedalDomain.decodeVhalGasPedalPercent(127.5f, 0)!!, 0.01f)
        // Field log: raw 253 while revving → ~99.2%, not rejected as >100.
        assertEquals(253f * 100f / 255f, PedalDomain.decodeVhalGasPedalPercent(253f, 0)!!, 0.01f)
        assertEquals(140f * 100f / 255f, PedalDomain.decodeVhalGasPedalPercent(140f, 0)!!, 0.01f)
    }

    @Test
    fun decodeVhalGasPedalPercent_rejectsInvalidOrOutOfRange() {
        assertNull(PedalDomain.decodeVhalGasPedalPercent(null, 0))
        assertNull(PedalDomain.decodeVhalGasPedalPercent(40f, 1))
        assertNull(PedalDomain.decodeVhalGasPedalPercent(-1f, 0))
        assertNull(PedalDomain.decodeVhalGasPedalPercent(256f, 0))
        assertNull(PedalDomain.decodeVhalGasPedalPercent(Float.NaN, 0))
    }

    @Test
    fun decodeBrakePressed_twoPressedOneReleased() {
        assertTrue(PedalDomain.decodeBrakePressed(2)!!)
        assertFalse(PedalDomain.decodeBrakePressed(1)!!)
        assertNull(PedalDomain.decodeBrakePressed(0))
        assertNull(PedalDomain.decodeBrakePressed(-1))
        assertNull(PedalDomain.decodeBrakePressed(3))
    }

    @Test
    fun decodeVhalBrakePressed_onePressedZeroReleased() {
        assertTrue(PedalDomain.decodeVhalBrakePressed(1)!!)
        assertFalse(PedalDomain.decodeVhalBrakePressed(0)!!)
        assertNull(PedalDomain.decodeVhalBrakePressed(2))
        assertNull(PedalDomain.decodeVhalBrakePressed(-1))
        assertNull(PedalDomain.decodeVhalBrakePressed(3))
    }
}
