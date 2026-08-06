package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vad.dashing.tbox.mbcan.SteeringAngleDomain

class SteeringAngleDomainTest {

    @Test
    fun decodeMcuReplyDeg_matchesStraightAndTurn() {
        assertEquals(0.0f, SteeringAngleDomain.decodeMcuReplyDeg(0)!!, 0.001f)
        assertEquals(-45.0f, SteeringAngleDomain.decodeMcuReplyDeg(-45)!!, 0.001f)
        assertEquals(180.0f, SteeringAngleDomain.decodeMcuReplyDeg(180)!!, 0.001f)
    }

    @Test
    fun decodeMcuReplyDeg_acceptsFloatDelivery() {
        assertEquals(12.5f, SteeringAngleDomain.decodeMcuReplyDeg(12.5f)!!, 0.001f)
    }

    @Test
    fun decodeMcuReplyDeg_rejectsInvalid() {
        assertNull(SteeringAngleDomain.decodeMcuReplyDeg(Float.NaN))
        assertNull(SteeringAngleDomain.decodeMcuReplyDeg(2500))
        assertNull(SteeringAngleDomain.decodeMcuReplyDeg(-2500))
    }
}
