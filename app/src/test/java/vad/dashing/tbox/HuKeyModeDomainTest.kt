package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vad.dashing.tbox.mbcan.HuSystemModeDomain
import vad.dashing.tbox.mbcan.IgnitionKeyDomain

class HuKeyModeDomainTest {

    @Test
    fun ignitionKey_decodesCemScale() {
        assertEquals(IgnitionKeyDomain.OFF, IgnitionKeyDomain.decode(0))
        assertEquals(IgnitionKeyDomain.ACC, IgnitionKeyDomain.decode(1))
        assertEquals(IgnitionKeyDomain.ON, IgnitionKeyDomain.decode(2))
        assertEquals(IgnitionKeyDomain.CRANK, IgnitionKeyDomain.decode(3))
        assertNull(IgnitionKeyDomain.decode(-1))
        assertNull(IgnitionKeyDomain.decode(4))
    }

    @Test
    fun systemMode_decodesNamedModesOnly() {
        assertEquals(HuSystemModeDomain.STANDBY, HuSystemModeDomain.decode(1))
        assertEquals(HuSystemModeDomain.ACTIVE, HuSystemModeDomain.decode(2))
        assertEquals(HuSystemModeDomain.HOLD_15M, HuSystemModeDomain.decode(6))
        assertEquals(HuSystemModeDomain.SLEEP, HuSystemModeDomain.decode(7))
        listOf(0, 3, 4, 5, 8).forEach { raw ->
            assertNull("raw=$raw", HuSystemModeDomain.decode(raw))
        }
    }
}
