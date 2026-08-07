package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vad.dashing.tbox.mbcan.VehicleSpeedDomain

class VehicleSpeedDomainTest {

    @Test
    fun decodeMcuReplyKmh_matchesReportedSpeeds() {
        assertEquals(0.0f, VehicleSpeedDomain.decodeMcuReplyKmh(0)!!, 0.001f)
        assertEquals(16.0f, VehicleSpeedDomain.decodeMcuReplyKmh(16)!!, 0.001f)
        assertEquals(38.0f, VehicleSpeedDomain.decodeMcuReplyKmh(38)!!, 0.001f)
        assertEquals(80.0f, VehicleSpeedDomain.decodeMcuReplyKmh(80)!!, 0.001f)
    }

    @Test
    fun decodeMcuReplyKmh_acceptsFloatDelivery() {
        assertEquals(41.0f, VehicleSpeedDomain.decodeMcuReplyKmh(41f)!!, 0.001f)
    }

    @Test
    fun decodeMcuReplyKmh_rejectsInvalid() {
        assertNull(VehicleSpeedDomain.decodeMcuReplyKmh(-1))
        assertNull(VehicleSpeedDomain.decodeMcuReplyKmh(Float.NaN))
    }
}
