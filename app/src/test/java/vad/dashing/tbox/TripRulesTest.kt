package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.trip.TripRecord
import vad.dashing.tbox.trip.TripRules

/**
 * Documents expected trip behaviour for split time and cold-start resume.
 * [BackgroundService] implements RPM samples; these rules mirror the decisions.
 */
class TripRulesTest {

    private val splitMs = 5 * 60_000L // e.g. 5 minutes

    @Test
    fun coldStart_activeTrip_resumes() {
        val last = TripRecord(startTimeEpochMs = 1000L, endTimeEpochMs = null)
        assertTrue(TripRules.shouldResumeLastTripOnColdStart(last, nowMs = 999_999L, splitWindowMs = splitMs))
    }

    @Test
    fun coldStart_endedWithinSplit_resumesAsContinuation() {
        val end = 1_000_000L
        val last = TripRecord(startTimeEpochMs = 0L, endTimeEpochMs = end)
        assertTrue(TripRules.shouldResumeLastTripOnColdStart(last, nowMs = end + splitMs - 1, splitWindowMs = splitMs))
        assertTrue(TripRules.shouldResumeLastTripOnColdStart(last, nowMs = end + splitMs, splitWindowMs = splitMs))
    }

    @Test
    fun coldStart_endedBeyondSplit_doesNotResume() {
        val end = 1_000_000L
        val last = TripRecord(startTimeEpochMs = 0L, endTimeEpochMs = end)
        assertFalse(TripRules.shouldResumeLastTripOnColdStart(last, nowMs = end + splitMs + 1, splitWindowMs = splitMs))
    }

    @Test
    fun coldStart_endedButClockBehindHu_doesNotResume() {
        val end = 1_000_000L
        val last = TripRecord(startTimeEpochMs = 0L, endTimeEpochMs = end)
        assertFalse(TripRules.shouldResumeLastTripOnColdStart(last, nowMs = end - 1, splitWindowMs = splitMs))
        assertFalse(TripRules.shouldResumeLastTripOnColdStart(last, nowMs = end - 60_000L, splitWindowMs = splitMs))
    }

    @Test
    fun engineOffPause_withinSplit_continuesSameTrip() {
        assertTrue(TripRules.shouldContinueTripAfterEngineOffPause(pauseMs = 60_000L, splitWindowMs = splitMs))
        assertTrue(TripRules.shouldContinueTripAfterEngineOffPause(pauseMs = splitMs, splitWindowMs = splitMs))
    }

    @Test
    fun engineOffPause_beyondSplit_startsNewTripWhenEngineRunsAgain() {
        assertFalse(TripRules.shouldContinueTripAfterEngineOffPause(pauseMs = splitMs + 1, splitWindowMs = splitMs))
    }

    @Test
    fun findResumeCandidate_prefersNewestActive() {
        val a = TripRecord(id = "a", startTimeEpochMs = 100L, endTimeEpochMs = null)
        val b = TripRecord(id = "b", startTimeEpochMs = 200L, endTimeEpochMs = null)
        val c = TripRecord(id = "c", startTimeEpochMs = 50L, endTimeEpochMs = 99L)
        val picked = TripRules.findResumeCandidate(listOf(c, a, b), nowMs = 500L, splitWindowMs = splitMs)
        assertEquals("b", picked?.id)
    }

    @Test
    fun findResumeCandidate_endedPicksMostRecentEndInWindow() {
        val endOld = 1_000_000L
        val endNew = 1_000_000L + 60_000L
        val old = TripRecord(id = "o", startTimeEpochMs = 0L, endTimeEpochMs = endOld)
        val newer = TripRecord(id = "n", startTimeEpochMs = 100L, endTimeEpochMs = endNew)
        val now = endNew + 30_000L
        val picked = TripRules.findResumeCandidate(listOf(old, newer), nowMs = now, splitWindowMs = splitMs)
        assertNotNull(picked)
        assertEquals("n", picked!!.id)
    }

    @Test
    fun findResumeCandidate_clockBehindEnd_ignoresEndedTrips() {
        val end = 1_000_000L
        val ended = TripRecord(id = "e", startTimeEpochMs = 0L, endTimeEpochMs = end)
        assertEquals(
            null,
            TripRules.findResumeCandidate(listOf(ended), nowMs = end - 1, splitWindowMs = splitMs)
        )
    }
}
