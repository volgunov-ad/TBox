package vad.dashing.tbox.mbcan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HighBeamDomainTest {
    @Test
    fun decodeOn_oemEnumScale() {
        assertEquals(true, HighBeamDomain.decodeOn(2))
        assertEquals(false, HighBeamDomain.decodeOn(1))
        assertEquals(false, HighBeamDomain.decodeOn(0))
        assertNull(HighBeamDomain.decodeOn(-1))
        assertNull(HighBeamDomain.decodeOn(3))
    }
}
