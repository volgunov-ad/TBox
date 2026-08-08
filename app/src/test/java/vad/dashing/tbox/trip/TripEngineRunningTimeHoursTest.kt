package vad.dashing.tbox.trip

import org.junit.Assert.assertEquals
import org.junit.Test

class TripEngineRunningTimeHoursTest {

    @Test
    fun engineRunningTimeHours_isMovingPlusIdleAsDecimalHours() {
        val trip = TripRecord(
            startTimeEpochMs = 1_000L,
            movingTimeMs = 3_600_000L, // 1.0 h
            idleTimeMs = 1_800_000L, // 0.5 h
            parkingTimeMs = 7_200_000L, // excluded
        )
        assertEquals(1.5f, trip.engineRunningTimeHours(), 0.0001f)
    }

    @Test
    fun engineRunningTimeHours_zeroWhenNoEngineTime() {
        val trip = TripRecord(
            startTimeEpochMs = 1_000L,
            movingTimeMs = 0L,
            idleTimeMs = 0L,
            parkingTimeMs = 60_000L,
        )
        assertEquals(0f, trip.engineRunningTimeHours(), 0.0001f)
    }
}
