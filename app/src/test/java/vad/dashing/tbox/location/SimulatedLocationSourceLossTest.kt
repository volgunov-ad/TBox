package vad.dashing.tbox.location

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.LocValues
import vad.dashing.tbox.TboxRepository
import java.util.Date

class SimulatedLocationSourceLossTest {

    @After
    fun tearDown() {
        SimulatedLocationSourceLoss.reset()
        TboxRepository.clearActiveLocation()
        TboxRepository.updateIsLocValuesTrue(false)
    }

    @Test
    fun gate_isSessionOnlyAndDefaultsToAcceptingUpdates() {
        SimulatedLocationSourceLoss.reset()

        assertFalse(SimulatedLocationSourceLoss.enabled.value)
        assertTrue(SimulatedLocationSourceLoss.acceptsLocationUpdates())

        SimulatedLocationSourceLoss.setEnabled(true)
        assertTrue(SimulatedLocationSourceLoss.enabled.value)
        assertFalse(SimulatedLocationSourceLoss.acceptsLocationUpdates())

        SimulatedLocationSourceLoss.reset()
        assertFalse(SimulatedLocationSourceLoss.enabled.value)
        assertTrue(SimulatedLocationSourceLoss.acceptsLocationUpdates())
    }

    @Test
    fun repository_discardsLocationAndTruthWhileLossIsSimulated() {
        val first = LocValues(
            rawValue = "first",
            locateStatus = true,
            latitude = 55.7,
            longitude = 37.6,
        )
        val blocked = LocValues(
            rawValue = "blocked",
            locateStatus = true,
            latitude = 55.8,
            longitude = 37.7,
        )

        TboxRepository.updateLocValues(first)
        TboxRepository.updateLocationUpdateTime()
        TboxRepository.updateIsLocValuesTrue(true)
        assertEquals(first, TboxRepository.locValues.value)
        assertTrue(TboxRepository.isLocValuesTrue.value)

        SimulatedLocationSourceLoss.setEnabled(true)
        TboxRepository.clearActiveLocation()
        TboxRepository.updateIsLocValuesTrue(false)
        TboxRepository.updateLocValues(blocked)
        TboxRepository.updateLocationUpdateTime()
        TboxRepository.updateIsLocValuesTrue(true)

        assertEquals(LocValues(), TboxRepository.locValues.value)
        assertNull(TboxRepository.locationUpdateTime.value)
        assertFalse(TboxRepository.isLocValuesTrue.value)

        SimulatedLocationSourceLoss.setEnabled(false)
        TboxRepository.updateLocValues(blocked)
        TboxRepository.updateLocationUpdateTime()
        TboxRepository.updateIsLocValuesTrue(true)

        assertEquals(blocked, TboxRepository.locValues.value)
        assertTrue(TboxRepository.locationUpdateTime.value != null)
        assertTrue(TboxRepository.isLocValuesTrue.value)
    }

    @Test
    fun staleClearDropsFixButKeepsLastClocks() {
        val changedAt = Date(1_700_000_000_000L)
        val live = LocValues(
            rawValue = "PHONE:abc#1",
            updateTime = changedAt,
            locateStatus = true,
            latitude = 56.25,
            longitude = 43.44,
        )
        TboxRepository.updateLocValues(live)
        TboxRepository.updateLocationUpdateTime()
        val packetAt = TboxRepository.locationUpdateTime.value

        TboxRepository.clearActiveLocation(preserveTimestamps = true)

        val cleared = TboxRepository.locValues.value
        assertFalse(cleared.locateStatus)
        assertEquals(0.0, cleared.latitude, 0.0)
        assertEquals(0.0, cleared.longitude, 0.0)
        assertEquals("", cleared.rawValue)
        assertEquals(changedAt, cleared.updateTime)
        assertEquals(packetAt, TboxRepository.locationUpdateTime.value)
        assertFalse(
            GnssFreshness.hasLiveFix(
                cleared.locateStatus,
                packetAt?.time,
                System.currentTimeMillis(),
            ),
        )
    }
}
