package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripRepositoryDerivedMetricsTest {

    @Test
    fun averageSpeedTripUsesOnlyMovingAndIdleTime() {
        val trip = TripRecord(
            startTimeEpochMs = 1L,
            endTimeEpochMs = 99_999L,
            distanceKm = 100f,
            movingTimeMs = 3_000_000L, // 50 min
            idleTimeMs = 600_000L, // 10 min
            parkingTimeMs = 3_600_000L, // 60 min, must be excluded
        )
        val avg = TripRepository.averageSpeedTripKmH(trip)
        assertEquals(100f, avg!!, 0.0001f)
    }

    @Test
    fun averageSpeedTripNullWhenMovingPlusIdleIsZero() {
        val trip = TripRecord(
            startTimeEpochMs = 1L,
            endTimeEpochMs = 2L,
            distanceKm = 10f,
            movingTimeMs = 0L,
            idleTimeMs = 0L,
            parkingTimeMs = 10_000L,
        )
        assertNull(TripRepository.averageSpeedTripKmH(trip))
    }
}
