package vad.dashing.tbox.mbcan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IcmWarningLampDomainTest {
    @Test
    fun decodeWarningActive_assumedCem1Bit() {
        assertEquals(true, IcmWarningLampDomain.decodeWarningActive(1))
        assertEquals(false, IcmWarningLampDomain.decodeWarningActive(0))
        assertNull(IcmWarningLampDomain.decodeWarningActive(-1))
        assertNull(IcmWarningLampDomain.decodeWarningActive(2))
        assertNull(IcmWarningLampDomain.decodeWarningActive(3))
    }
}
