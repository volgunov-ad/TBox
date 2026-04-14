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
        assertTrue(TripRepository.tryResumeLastTripAfterServiceStart(splitMs).resumed)
        val active = TripRepository.activeTrip.first()
        assertNotNull(active)
        assertEquals("newer", active!!.id)
        assertNull(active.endTimeEpochMs)
    }

    @Test
    fun tryResume_endedWithinSplit_reopensWithoutChangingIdleYet() = runBlocking {
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
        assertTrue(resumeResult.resumed)
        assertNotNull(resumeResult.reopenedEndedTrip)
        assertEquals("parked", resumeResult.reopenedEndedTrip!!.tripId)
        assertEquals(endMs, resumeResult.reopenedEndedTrip!!.previousEndTimeEpochMs)
        assertEquals(priorIdle, resumeResult.reopenedEndedTrip!!.previousIdleTimeMs)
        assertTrue(
            resumeResult.reopenedEndedTrip!!.parkedMsAddedToIdle in 115_000L..125_000L
        )
        val active = TripRepository.activeTrip.first()
        assertNotNull(active)
        assertEquals("parked", active!!.id)
        assertNull(active.endTimeEpochMs)
        assertEquals(
            "cold resume should only reopen trip; parked idle is applied at first RPM>0 sample",
            priorIdle,
            active.idleTimeMs
        )
    }

    @Test
    fun tryResume_alreadyActive_reopenedEndedTripIsNull() = runBlocking {
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
        assertNull(r.reopenedEndedTrip)
        assertEquals("running", TripRepository.activeTrip.first()?.id)
    }
}
