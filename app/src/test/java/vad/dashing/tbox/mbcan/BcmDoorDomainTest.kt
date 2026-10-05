package vad.dashing.tbox.mbcan

import com.mengbo.mbCan.entity.MBCanSeatBeltWarning
import com.mengbo.mbCan.entity.MBCanVehicleDoor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BcmDoorDomainTest {
    @Test
    fun fromDoorObject_readsAllFields() {
        val door = MBCanVehicleDoor(
            /* driver */ 2,
            /* passenger */ 1,
            /* LHR */ 2,
            /* RHR */ 1,
            /* trunk */ 1,
            /* hood */ 2,
            /* srf */ 0,
            /* lock */ 1,
        )
        val snap = BcmDoorDomain.fromDoorObject(door)!!
        assertEquals(2, snap.driver)
        assertEquals(1, snap.passenger)
        assertEquals(2, snap.rearLeft)
        assertEquals(1, snap.rearRight)
        assertEquals(1, snap.trunk)
        assertEquals(2, snap.hood)
        assertEquals(1, snap.driverLock)
        assertEquals(0, snap.srfOperate)
        assertTrue(snap.journalSample().contains("FL=2"))
        assertTrue(snap.journalSample().contains("hood=2"))
        assertFalse(snap.isEmpty())
    }

    @Test
    fun fromDoorObject_nullSafe() {
        assertNull(BcmDoorDomain.fromDoorObject(null))
    }

    @Test
    fun vhalCem2AjarSeedPropertyIds_matchProductionCem2Ids() {
        val ids = BcmDoorDomain.vhalCem2AjarSeedPropertyIds()
        assertEquals(
            mapOf(
                "FL" to FirmwareVehicleJsonMapper.VHAL_CEM2_DRIVER_DOOR_STS,
                "FR" to FirmwareVehicleJsonMapper.VHAL_CEM2_PSNGR_DOOR_STS,
                "RL" to FirmwareVehicleJsonMapper.VHAL_CEM2_LHR_DOOR_STS,
                "RR" to FirmwareVehicleJsonMapper.VHAL_CEM2_RHR_DOOR_STS,
                "hood" to FirmwareVehicleJsonMapper.VHAL_CEM2_HOOD_STS,
            ),
            ids,
        )
        // Cabin doors required for automations; hood is journal/deep-only.
        assertTrue(ids.keys.containsAll(listOf("FL", "FR", "RL", "RR")))
    }

    @Test
    fun bcmTrunkSnapshot_carriesDoorsForRefreshSeed() {
        val door = MBCanVehicleDoor(
            /* driver */ 1,
            /* passenger */ 2,
            /* LHR */ 1,
            /* RHR */ 2,
            /* trunk */ 1,
            /* hood */ 1,
            /* srf */ 0,
            /* lock */ 1,
        )
        val doors = BcmDoorDomain.fromDoorObject(door)!!
        val snapshot = MbCanEngineFacade.BcmTrunkSnapshot(
            moveDir = 2,
            trunkSts = doors.trunk,
            doors = doors,
        )
        assertEquals(1, snapshot.trunkSts)
        assertEquals(1, snapshot.doors?.driver)
        assertEquals(2, snapshot.doors?.passenger)
        assertEquals(1, snapshot.doors?.rearLeft)
        assertEquals(2, snapshot.doors?.rearRight)
    }

    @Test
    fun seatBeltJournalSample_formatsPair() {
        val warning = MBCanSeatBeltWarning(1, 0)
        assertEquals(
            "driverWarn=1 passengerWarn=0",
            BcmDoorDomain.seatBeltJournalSample(
                warning.driverWarning.toInt(),
                warning.passengerWarning.toInt(),
            ),
        )
    }
}
