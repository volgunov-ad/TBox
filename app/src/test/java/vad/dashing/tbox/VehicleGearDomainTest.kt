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
    fun decodeReverseGearSwitch_zeroOne() {
        assertFalse(VehicleGearDomain.decodeReverseGearSwitch(0)!!)
        assertTrue(VehicleGearDomain.decodeReverseGearSwitch(1)!!)
        assertNull(VehicleGearDomain.decodeReverseGearSwitch(2))
        assertNull(VehicleGearDomain.decodeReverseGearSwitch(-1))
    }

    @Test
    fun isReverseEngaged_orOfSwitchAndPrnd() {
        assertTrue(VehicleGearDomain.isReverseEngaged(true, "D"))
        assertTrue(VehicleGearDomain.isReverseEngaged(false, "R"))
        assertTrue(VehicleGearDomain.isReverseEngaged(null, "r"))
        assertTrue(VehicleGearDomain.isReverseEngaged(true, null))
        assertFalse(VehicleGearDomain.isReverseEngaged(false, "D"))
        assertFalse(VehicleGearDomain.isReverseEngaged(null, "P"))
        assertFalse(VehicleGearDomain.isReverseEngaged(null, null))
        assertFalse(VehicleGearDomain.isReverseEngaged(false, ""))
        // HU D must not hide TBox R when callers OR both sources.
        assertTrue(
            VehicleGearDomain.isReverseEngaged(false, "D") ||
                VehicleGearDomain.isReverseEngaged(null, "R"),
        )
    }
}
