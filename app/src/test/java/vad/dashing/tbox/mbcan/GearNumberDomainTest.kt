package vad.dashing.tbox.mbcan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GearNumberDomainTest {
    @Test
    fun decode_passThroughNonNegative() {
        assertEquals(0, GearNumberDomain.decode(0))
        assertEquals(1, GearNumberDomain.decode(1))
        assertEquals(8, GearNumberDomain.decode(8))
        assertEquals(15, GearNumberDomain.decode(15))
        assertNull(GearNumberDomain.decode(null))
        assertNull(GearNumberDomain.decode(-1))
    }
}
