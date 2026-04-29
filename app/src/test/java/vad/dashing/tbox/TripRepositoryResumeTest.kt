package vad.dashing.tbox

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun tryResume_endedCandidateDoesNotBecomeActiveAtServiceStart() = runBlocking {
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
        assertFalse(TripRepository.tryResumeLastTripAfterServiceStart(splitMs).resumed)
        assertNull(TripRepository.activeTrip.first())
        assertEquals(endNew, TripRepository.trips.first().first { it.id == "newer" }.endTimeEpochMs)
    }

    @Test
    fun tryResume_endedWithinSplit_remainsClosedUntilEngineStarts() = runBlocking {
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
        val resumeResult = TripRepository.tryResumeLastTripAfterServiceStart(splitMs)
        assertFalse(resumeResult.resumed)
        assertNull(TripRepository.activeTrip.first())
        val stored = TripRepository.trips.first().single()
        assertEquals("parked", stored.id)
        assertEquals(endMs, stored.endTimeEpochMs)
        assertEquals(priorIdle, stored.idleTimeMs)
    }

    @Test
    fun tryResume_alreadyActive_resumesImmediately() = runBlocking {
        val nowWall = System.currentTimeMillis()
        TripRepository.appendTrip(
            TripRecord(
                id = "running",
                startTimeEpochMs = nowWall - 3_600_000L,
                endTimeEpochMs = null,
            )
        )
        val splitMs = 5 * 60_000L
        val r = TripRepository.tryResumeLastTripAfterServiceStart(splitMs)
        assertTrue(r.resumed)
        assertEquals("running", TripRepository.activeTrip.first()?.id)
    }
}
