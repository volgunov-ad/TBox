package vad.dashing.tbox.location

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.LocValues
import vad.dashing.tbox.TboxRepository

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
}
