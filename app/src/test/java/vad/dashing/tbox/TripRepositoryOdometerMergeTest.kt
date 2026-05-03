package vad.dashing.tbox

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vad.dashing.tbox.trip.TripRecord
import vad.dashing.tbox.trip.TripRepository

class TripRepositoryOdometerMergeTest {

    @After
    fun reset() {
        TripRepository.resetForUnitTests()
    }

    @Test
    fun updateActiveTrip_backfillsOdometerStartWhenPreviouslyNull() = runBlocking {
        TripRepository.startTrip(
            TripRecord(
                id = "t1",
                startTimeEpochMs = 1L,
                endTimeEpochMs = null,
                odometerStartKm = null,
            )
        )
        val filled = 12_345u
        TripRepository.updateActiveTrip { it.copy(odometerStartKm = filled) }
        assertEquals(filled, TripRepository.activeTrip.first()?.odometerStartKm)
        assertEquals(filled, TripRepository.trips.first().first().odometerStartKm)
    }

    @Test
    fun updateActiveTrip_doesNotOverwriteExistingOdometerStart() = runBlocking {
        val original = 10_000u
        TripRepository.startTrip(
            TripRecord(
                id = "t1",
                startTimeEpochMs = 1L,
                endTimeEpochMs = null,
                odometerStartKm = original,
            )
        )
        TripRepository.updateActiveTrip { it.copy(odometerStartKm = 99_999u, distanceKm = 1f) }
        val active = TripRepository.activeTrip.first()
        assertEquals(original, active?.odometerStartKm)
        assertEquals(1f, active?.distanceKm)
    }

    @Test
    fun updateActiveTrip_keepsNullWhenTransformDoesNotSetOdometer() = runBlocking {
        TripRepository.startTrip(
            TripRecord(
                id = "t1",
                startTimeEpochMs = 1L,
                endTimeEpochMs = null,
                odometerStartKm = null,
            )
        )
        TripRepository.updateActiveTrip { it.copy(distanceKm = 5f) }
        assertNull(TripRepository.activeTrip.first()?.odometerStartKm)
    }
}
