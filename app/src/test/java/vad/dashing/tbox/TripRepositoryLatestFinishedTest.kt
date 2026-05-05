package vad.dashing.tbox

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vad.dashing.tbox.trip.TripRecord
import vad.dashing.tbox.trip.TripRepository

class TripRepositoryLatestFinishedTest {

    @After
    fun reset() {
        TripRepository.resetForUnitTests()
    }

    @Test
    fun latestFinishedTrip_empty_returnsNull() {
        assertNull(TripRepository.latestFinishedTrip(emptyList()))
    }

    @Test
    fun latestFinishedTrip_onlyActive_returnsNull() {
        val list = listOf(
            TripRecord(id = "a", startTimeEpochMs = 100L, endTimeEpochMs = null)
        )
        assertNull(TripRepository.latestFinishedTrip(list))
    }

    @Test
    fun latestFinishedTrip_picksLatestByEndTime() {
        val older = TripRecord(id = "o", startTimeEpochMs = 10L, endTimeEpochMs = 50L)
        val newer = TripRecord(id = "n", startTimeEpochMs = 20L, endTimeEpochMs = 200L)
        assertEquals(
            "n",
            TripRepository.latestFinishedTrip(listOf(older, newer))?.id
        )
    }

    @Test
    fun latestFinishedTrip_sameEndTime_picksLaterStart() {
        val a = TripRecord(id = "a", startTimeEpochMs = 10L, endTimeEpochMs = 100L)
        val b = TripRecord(id = "b", startTimeEpochMs = 50L, endTimeEpochMs = 100L)
        assertEquals(
            "b",
            TripRepository.latestFinishedTrip(listOf(a, b))?.id
        )
    }
}
