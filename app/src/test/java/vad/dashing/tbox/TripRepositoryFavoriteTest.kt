package vad.dashing.tbox

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.trip.TripRecord
import vad.dashing.tbox.trip.TripRepository

class TripRepositoryFavoriteTest {

    @After
    fun reset() {
        TripRepository.resetForUnitTests()
    }

    @Test
    fun canFavoriteActiveTrip() = runBlocking {
        val id = "t1"
        TripRepository.startTrip(
            TripRecord(id = id, startTimeEpochMs = 1L, endTimeEpochMs = null)
        )
        TripRepository.setFavorite(id, favorite = true)
        assertTrue(TripRepository.favoriteIds.first().contains(id))
    }

    @Test
    fun canFavoriteCompletedTrip() = runBlocking {
        TripRepository.resetForUnitTests()
        val id = "t2"
        TripRepository.appendTrip(
            TripRecord(id = id, startTimeEpochMs = 1L, endTimeEpochMs = 100L)
        )
        TripRepository.setFavorite(id, favorite = true)
        assertTrue(TripRepository.favoriteIds.first().contains(id))
    }
}
