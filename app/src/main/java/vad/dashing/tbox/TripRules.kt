package vad.dashing.tbox

/**
 * Pure rules for trip continuation (unit-tested). Used from [TripRepository] and documented for
 * parity with [BackgroundService] RPM / split-window behaviour.
 */
object TripRules {

    /**
     * After loading stored trips or on service start: continue the last trip if it is still active,
     * or it ended recently (within [splitWindowMs]) — e.g. app/service restart during a short stop.
     */
    fun shouldResumeLastTripOnColdStart(last: TripRecord, nowMs: Long, splitWindowMs: Long): Boolean {
        if (last.isActive) return true
        val end = last.endTimeEpochMs ?: return false
        return nowMs - end < splitWindowMs
    }

    /**
     * After RPM dropped to zero and rose again: same trip continues only if the zero-RPM pause
     * was shorter than [splitWindowMs] (split trip time setting).
     */
    fun shouldContinueTripAfterEngineOffPause(pauseMs: Long, splitWindowMs: Long): Boolean =
        pauseMs < splitWindowMs
}
