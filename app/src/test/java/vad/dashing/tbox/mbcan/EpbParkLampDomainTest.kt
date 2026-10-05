package vad.dashing.tbox.mbcan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpbParkLampDomainTest {
    @Test
    fun decodeOn_assumedCemSwitchScale() {
        assertEquals(true, EpbParkLampDomain.decodeOn(2))
        assertEquals(false, EpbParkLampDomain.decodeOn(1))
        assertEquals(false, EpbParkLampDomain.decodeOn(0))
        assertNull(EpbParkLampDomain.decodeOn(-1))
        assertNull(EpbParkLampDomain.decodeOn(3))
    }
}
