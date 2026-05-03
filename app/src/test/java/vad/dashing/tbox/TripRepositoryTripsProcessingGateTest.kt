package vad.dashing.tbox

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.trip.TripRepository

class TripRepositoryTripsProcessingGateTest {

    @After
    fun tearDown() {
        TripRepository.resetForUnitTests()
    }

    @Test
    fun tripsProcessingEnabled_defaultsTrue() {
        TripRepository.resetForUnitTests()
        assertTrue(TripRepository.isTripsProcessingEnabled())
    }

    @Test
    fun tripsProcessingEnabled_canBeToggled() {
        TripRepository.resetForUnitTests()
        TripRepository.setTripsProcessingEnabled(false)
        assertFalse(TripRepository.isTripsProcessingEnabled())
        TripRepository.setTripsProcessingEnabled(true)
        assertTrue(TripRepository.isTripsProcessingEnabled())
    }
}
