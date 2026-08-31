package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vad.dashing.tbox.mbcan.AccStatusDomain

class AccStatusDomainTest {

    @Test
    fun decodeMbCan_accOnAndIgnition() {
        assertEquals(AccStatusDomain.STATE_ACC, AccStatusDomain.decodeMbCan(4))
        assertEquals(AccStatusDomain.STATE_IGN, AccStatusDomain.decodeMbCan(5))
    }

    @Test
    fun decodeMbCan_offRange() {
        (0..3).forEach { raw ->
            assertEquals("raw=$raw", AccStatusDomain.STATE_OFF, AccStatusDomain.decodeMbCan(raw))
        }
    }

    @Test
    fun decodeMbCan_rejectsUnknown() {
        assertNull(AccStatusDomain.decodeMbCan(-1))
        assertNull(AccStatusDomain.decodeMbCan(6))
        assertNull(AccStatusDomain.decodeMbCan(255))
    }

    @Test
    fun decodeMcuReply_a10Scale() {
        assertEquals(AccStatusDomain.STATE_OFF, AccStatusDomain.decodeMcuReply(0))
        assertEquals(AccStatusDomain.STATE_ACC, AccStatusDomain.decodeMcuReply(1))
        assertEquals(AccStatusDomain.STATE_ACC, AccStatusDomain.decodeMcuReply(2))
        assertEquals(AccStatusDomain.STATE_OFF, AccStatusDomain.decodeMcuReply(3))
        assertNull(AccStatusDomain.decodeMcuReply(4))
        assertNull(AccStatusDomain.decodeMcuReply(5))
        assertNull(AccStatusDomain.decodeMcuReply(-1))
    }
}
