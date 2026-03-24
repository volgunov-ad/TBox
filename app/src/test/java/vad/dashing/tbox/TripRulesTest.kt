package vad.dashing.tbox

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
    }

    @Test
    fun coldStart_endedBeyondSplit_doesNotResume() {
        val end = 1_000_000L
        val last = TripRecord(startTimeEpochMs = 0L, endTimeEpochMs = end)
        assertFalse(TripRules.shouldResumeLastTripOnColdStart(last, nowMs = end + splitMs, splitWindowMs = splitMs))
    }

    @Test
    fun engineOffPause_withinSplit_continuesSameTrip() {
        // Short stop: RPM 0 then back on within split window → same trip (see BackgroundService)
        assertTrue(TripRules.shouldContinueTripAfterEngineOffPause(pauseMs = 60_000L, splitWindowMs = splitMs))
    }

    @Test
    fun engineOffPause_beyondSplit_startsNewTripWhenEngineRunsAgain() {
        // Long stop: pause >= split → pending cleared; next RPM>0 starts new trip
        assertFalse(TripRules.shouldContinueTripAfterEngineOffPause(pauseMs = splitMs, splitWindowMs = splitMs))
        assertFalse(TripRules.shouldContinueTripAfterEngineOffPause(pauseMs = splitMs + 1, splitWindowMs = splitMs))
    }
}
