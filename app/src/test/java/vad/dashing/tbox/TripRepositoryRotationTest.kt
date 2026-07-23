package vad.dashing.tbox

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.trip.TripRecord
import vad.dashing.tbox.trip.TripRepository

/**
 * Baseline coverage for list limits and repair of multiple active trips —
 * behaviour that persistent (daily) trip must not break.
 *
 * Avoids [TripRepository.setTripsFromStore] (org.json not mocked on JVM).
 */
class TripRepositoryRotationTest {

    @After
    fun reset() {
        TripRepository.resetForUnitTests()
    }

    @Test
    fun appendTrip_evictsOldestWhenOverMaxTrips() = runBlocking {
        val max = TripRepository.MAX_TRIPS
        for (i in 1..(max + 3)) {
            TripRepository.appendTrip(
                TripRecord(
                    id = "t$i",
                    startTimeEpochMs = i.toLong(),
                    endTimeEpochMs = i.toLong() + 1L,
                )
            )
        }
        val trips = TripRepository.trips.first()
        assertEquals(max, trips.size)
        assertEquals("t4", trips.first().id)
        assertEquals("t${max + 3}", trips.last().id)
        assertFalse(trips.any { it.id == "t1" })
    }

    @Test
    fun startTrip_closesOtherActiveTrips_keepsSingleActive() = runBlocking {
        TripRepository.appendTrip(
            TripRecord(id = "old", startTimeEpochMs = 10L, endTimeEpochMs = null)
        )
        TripRepository.startTrip(
            TripRecord(id = "new", startTimeEpochMs = 20L, endTimeEpochMs = null)
        )
        val trips = TripRepository.trips.first()
        val old = trips.first { it.id == "old" }
        val neu = trips.first { it.id == "new" }
        assertTrue(old.endTimeEpochMs != null)
        assertNull(neu.endTimeEpochMs)
        assertEquals("new", TripRepository.activeTrip.first()?.id)
    }

    @Test
    fun removeTrip_clearsActiveWhenRemoved() = runBlocking {
        TripRepository.appendTrip(
            TripRecord(id = "a", startTimeEpochMs = 1L, endTimeEpochMs = null)
        )
        assertEquals("a", TripRepository.activeTrip.first()?.id)
        TripRepository.removeTrip("a")
        assertTrue(TripRepository.trips.first().isEmpty())
        assertNull(TripRepository.activeTrip.first())
    }
}
