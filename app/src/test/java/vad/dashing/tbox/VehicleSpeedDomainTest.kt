package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vad.dashing.tbox.mbcan.VehicleSpeedDomain

class VehicleSpeedDomainTest {

    @Test
    fun decodeVhalRaw_matchesReportedSpeeds() {
        // DisplayVehicleSpeed: км/ч = UINT16(raw) / 16
        assertEquals(41.0f, VehicleSpeedDomain.decodeVhalRaw(656)!!, 0.001f)
        assertEquals(42.0f, VehicleSpeedDomain.decodeVhalRaw(672)!!, 0.001f)
        assertEquals(48.0f, VehicleSpeedDomain.decodeVhalRaw(768)!!, 0.001f)
        assertEquals(0.0f, VehicleSpeedDomain.decodeVhalRaw(0)!!, 0.001f)
    }

    @Test
    fun decodeVhalRaw_acceptsFloatDelivery() {
        assertEquals(41.0f, VehicleSpeedDomain.decodeVhalRaw(656f)!!, 0.001f)
        assertEquals(12.5f, VehicleSpeedDomain.decodeVhalRaw(200f)!!, 0.001f)
    }

    @Test
    fun decodeVhalRaw_rejectsInvalid() {
        assertNull(VehicleSpeedDomain.decodeVhalRaw(-1))
        assertNull(VehicleSpeedDomain.decodeVhalRaw(Float.NaN))
    }
}
