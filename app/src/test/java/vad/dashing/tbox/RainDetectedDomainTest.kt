package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.mbcan.RainDetectedDomain

class RainDetectedDomainTest {
    @Test
    fun decode_cemOneIsRain() {
        assertFalse(RainDetectedDomain.decodeDetected(0)!!)
        assertTrue(RainDetectedDomain.decodeDetected(1)!!)
    }

    @Test
    fun decode_unknownOutsideCemBit() {
        assertNull(RainDetectedDomain.decodeDetected(-1))
        assertNull(RainDetectedDomain.decodeDetected(2))
        assertNull(RainDetectedDomain.decodeDetected(255))
    }

    @Test
    fun decode_matchesElectricalManualSRain() {
        assertEquals(true, RainDetectedDomain.decodeDetected(0x1))
        assertEquals(false, RainDetectedDomain.decodeDetected(0x0))
    }
}
