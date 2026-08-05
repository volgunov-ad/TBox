package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.mbcan.VehicleGearDomain

class VehicleGearDomainTest {

    @Test
    fun decodePrndBitmask_stockAaosValues() {
        assertEquals("N", VehicleGearDomain.decodePrndBitmask(1))
        assertEquals("R", VehicleGearDomain.decodePrndBitmask(2))
        assertEquals("P", VehicleGearDomain.decodePrndBitmask(4))
        assertEquals("D", VehicleGearDomain.decodePrndBitmask(8))
    }

    @Test
    fun decodePrndBitmask_rejectsUnknown() {
        assertNull(VehicleGearDomain.decodePrndBitmask(0))
        assertNull(VehicleGearDomain.decodePrndBitmask(3))
        assertNull(VehicleGearDomain.decodePrndBitmask(16))
        assertNull(VehicleGearDomain.decodePrndBitmask(-1))
    }

    @Test
    fun decodeReverseGearSwitch_dashingInvertedPolarity() {
        // Dashing CEM: 0 = reverse engaged, 1 = not reverse (inverted vs stock docs).
        assertTrue(VehicleGearDomain.decodeReverseGearSwitch(0)!!)
        assertFalse(VehicleGearDomain.decodeReverseGearSwitch(1)!!)
        assertNull(VehicleGearDomain.decodeReverseGearSwitch(2))
        assertNull(VehicleGearDomain.decodeReverseGearSwitch(-1))
    }

    @Test
    fun isReverseEngaged_huPrndFirst() {
        // Known HU R wins even if switch says false.
        assertTrue(VehicleGearDomain.isReverseEngaged(false, "R"))
        assertTrue(VehicleGearDomain.isReverseEngaged(null, "r"))
        // Known non-R HU ignores switch (and TBox R).
        assertFalse(VehicleGearDomain.isReverseEngaged(true, "D"))
        assertFalse(VehicleGearDomain.isReverseEngaged(true, "P", "R"))
        assertFalse(VehicleGearDomain.isReverseEngaged(false, "D"))
        assertFalse(VehicleGearDomain.isReverseEngaged(null, "P"))
        assertFalse(VehicleGearDomain.isReverseEngaged(null, "N", "R"))
    }

    @Test
    fun isReverseEngaged_switchWhenHuPrndAbsent() {
        // MT / no HU PRND: switch decides.
        assertTrue(VehicleGearDomain.isReverseEngaged(true, null))
        assertTrue(VehicleGearDomain.isReverseEngaged(true, ""))
        assertFalse(VehicleGearDomain.isReverseEngaged(false, null))
        assertFalse(VehicleGearDomain.isReverseEngaged(false, ""))
        assertFalse(VehicleGearDomain.isReverseEngaged(null, null))
    }

    @Test
    fun isReverseEngaged_tboxPrndFallback() {
        // TBox R only when HU PRND unknown and switch not engaged.
        assertTrue(VehicleGearDomain.isReverseEngaged(null, null, "R"))
        assertTrue(VehicleGearDomain.isReverseEngaged(false, null, "r"))
        assertFalse(VehicleGearDomain.isReverseEngaged(null, null, "D"))
        // HU D still blocks TBox R.
        assertFalse(VehicleGearDomain.isReverseEngaged(null, "D", "R"))
    }
}
