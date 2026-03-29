package vad.dashing.tbox

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripRepositoryResumeTest {

    @After
    fun reset() {
        TripRepository.resetForUnitTests()
    }

    @Test
    fun startTrip_closesOtherActiveTrips() = runBlocking {
        TripRepository.appendTrip(
            TripRecord(id = "old", startTimeEpochMs = 1L, endTimeEpochMs = null)
        )
        TripRepository.startTrip(
            TripRecord(id = "new", startTimeEpochMs = 100L, endTimeEpochMs = null)
        )
        val trips = TripRepository.trips.first()
        val old = trips.first { it.id == "old" }
        val newT = trips.first { it.id == "new" }
        assertNotNull(old.endTimeEpochMs)
        assertNull(newT.endTimeEpochMs)
        assertEquals("new", TripRepository.activeTrip.first()?.id)
    }

    @Test
    fun tryResume_prefersCandidateNotJustListLast() = runBlocking {
        // TripRepository uses System.currentTimeMillis() for the split window; timestamps must be
        // near "now" or resume will not match unit-test expectations.
        val nowWall = System.currentTimeMillis()
        val endNew = nowWall - 30_000L
        val endOld = endNew - 60_000L
        TripRepository.appendTrip(
            TripRecord(id = "older", startTimeEpochMs = endOld - 1L, endTimeEpochMs = endOld)
        )
        TripRepository.appendTrip(
            TripRecord(id = "newer", startTimeEpochMs = endOld + 1L, endTimeEpochMs = endNew)
        )
        val splitMs = 5 * 60_000L
        assertTrue(TripRepository.tryResumeLastTripAfterServiceStart(splitMs))
        val active = TripRepository.activeTrip.first()
        assertNotNull(active)
        assertEquals("newer", active!!.id)
        assertNull(active.endTimeEpochMs)
    }

    @Test
    fun tryResume_endedWithinSplit_addsParkedTimeToIdle() = runBlocking {
        val nowWall = System.currentTimeMillis()
        val endMs = nowWall - 120_000L
        val priorIdle = 30_000L
        TripRepository.appendTrip(
            TripRecord(
                id = "parked",
                startTimeEpochMs = endMs - 3_600_000L,
                endTimeEpochMs = endMs,
                idleTimeMs = priorIdle,
            )
        )
        val splitMs = 5 * 60_000L
        assertTrue(TripRepository.tryResumeLastTripAfterServiceStart(splitMs))
        val active = TripRepository.activeTrip.first()
        assertNotNull(active)
        assertEquals("parked", active!!.id)
        assertNull(active.endTimeEpochMs)
        val addedIdle = active!!.idleTimeMs - priorIdle
        // ~2 min between persisted end and resume; allow clock skew across calls.
        assertTrue(
            "parked segment should be ~120s, was ${addedIdle}ms",
            addedIdle in 115_000L..125_000L
        )
    }
}
