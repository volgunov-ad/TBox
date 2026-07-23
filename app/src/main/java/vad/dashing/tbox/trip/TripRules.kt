package vad.dashing.tbox.trip

/**
 * Pure rules for trip continuation (unit-tested). Used from [TripRepository] and documented for
 * parity with [vad.dashing.tbox.BackgroundService] periodic RPM snapshot / split-window behaviour.
 */
object TripRules {

    /** Trips that must never reopen as the current driving trip (live/archive daily). */
    fun isExcludedFromCurrentTripResume(trip: TripRecord): Boolean =
        trip.isPersistent || trip.originPersistent

    /**
     * After loading stored trips or on service start: continue the last trip if it is still active,
     * or it ended recently (within [splitWindowMs]) — e.g. app/service restart during a short stop.
     */
    fun shouldResumeLastTripOnColdStart(last: TripRecord, nowMs: Long, splitWindowMs: Long): Boolean {
        if (isExcludedFromCurrentTripResume(last)) return false
        if (last.isCurrentActive) return true
        val end = last.endTimeEpochMs ?: return false
        // HU clock behind real time (e.g. battery disconnect): do not treat as "recent end".
        if (nowMs < end) return false
        return nowMs - end <= splitWindowMs
    }

    /**
     * After RPM dropped to zero and rose again: same trip continues only if the zero-RPM pause
     * was shorter than [splitWindowMs] (split trip time setting).
     */
    fun shouldContinueTripAfterEngineOffPause(pauseMs: Long, splitWindowMs: Long): Boolean =
        pauseMs <= splitWindowMs

    /**
     * Picks which stored trip to continue after cold start: prefer the newest current active trip;
     * else the most recently ended non-daily trip still within the split window.
     */
    fun findResumeCandidate(trips: List<TripRecord>, nowMs: Long, splitWindowMs: Long): TripRecord? {
        if (trips.isEmpty()) return null
        val eligible = trips.filterNot { isExcludedFromCurrentTripResume(it) }
        val activeOnes = eligible.filter { it.isCurrentActive }
        if (activeOnes.isNotEmpty()) {
            return activeOnes.maxByOrNull { it.startTimeEpochMs }
        }
        val inWindow = eligible.mapNotNull { t ->
            val end = t.endTimeEpochMs ?: return@mapNotNull null
            if (nowMs >= end && nowMs - end <= splitWindowMs) Pair(t, end) else null
        }
        return inWindow.maxByOrNull { it.second }?.first
    }
}
