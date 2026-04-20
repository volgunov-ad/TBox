package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripRepositoryFuelConsumptionTest {

    @Test
    fun averageFuelConsumption100km_nullWhenDistanceZero() {
        val trip = TripRecord(
            startTimeEpochMs = 1L,
            endTimeEpochMs = 2L,
            distanceKm = 0f,
            fuelConsumedLiters = 5f,
        )
        assertNull(TripRepository.averageFuelConsumptionLitersPer100Km(trip))
    }

    @Test
    fun averageFuelConsumption100km_zeroWhenFuelZero() {
        val trip = TripRecord(
            startTimeEpochMs = 1L,
            endTimeEpochMs = 2L,
            distanceKm = 100f,
            fuelConsumedLiters = 0f,
        )
        assertEquals(0f, TripRepository.averageFuelConsumptionLitersPer100Km(trip)!!, 0.0001f)
    }

    @Test
    fun averageFuelConsumption100km_calculatesExpectedValue() {
        val trip = TripRecord(
            startTimeEpochMs = 1L,
            endTimeEpochMs = 2L,
            distanceKm = 250f,
            fuelConsumedLiters = 20f,
        )
        assertEquals(8f, TripRepository.averageFuelConsumptionLitersPer100Km(trip)!!, 0.0001f)
    }
}
