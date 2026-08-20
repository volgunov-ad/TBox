package vad.dashing.tbox.location.roadmatch

/**
 * Experimental Ordinary-like snap: stronger heading pull toward the selected
 * edge, and a full unbind around junctions where more than 2 lines meet
 * (any fork / T / cross / highway exit). Optional stalk-driven unbind keeps
 * pose free while an intentional turn signal is active.
 */
object RoadMatchFreeTurnsMath {
    /** Unbind this far before a complex junction (remaining along-track). */
    const val UNBIND_BEFORE_M = 35.0
    /** Rebind after travelling this far past the junction node. */
    const val REBIND_AFTER_M = 10.0
    /** "More than 2 lines" → 3+ incident edges at the travel-end node. */
    const val MIN_INCIDENT_LINES = 3
    /** Per-match heading catch-up toward the selected (blue) edge. */
    const val MAX_BEARING_STEP_CATCHUP_DEG = 26f
    /** Heading-only pull on throttled ticks (DR cycle ~0.5 s). */
    const val THROTTLE_BEARING_STEP_DEG = 18f
    /** Do not yank heading on a throttled tick when residual is a U-turn. */
    const val THROTTLE_BEARING_MAX_RESIDUAL_DEG = 60f
    /** Default path after turn-signal off before stalk rebind. */
    const val STALK_REBIND_AFTER_M = 10.0

    enum class ReleaseKind {
        JUNCTION,
        STALK,
    }

    fun remainingAlongM(
        alongTrackM: Double,
        lengthM: Double,
        travelAgainstCoords: Boolean,
    ): Double {
        if (!alongTrackM.isFinite() || !lengthM.isFinite() || lengthM <= 0.0) {
            return Double.POSITIVE_INFINITY
        }
        val along = alongTrackM.coerceIn(0.0, lengthM)
        return if (travelAgainstCoords) along else (lengthM - along).coerceAtLeast(0.0)
    }

    fun shouldRelease(
        remainingToComplexM: Double?,
        unbindBeforeM: Double = UNBIND_BEFORE_M,
    ): Boolean {
        if (remainingToComplexM == null || !remainingToComplexM.isFinite()) return false
        return remainingToComplexM <= unbindBeforeM
    }

    fun shouldRebind(
        pathSinceReleaseM: Double,
        remainingAtReleaseM: Double,
        rebindAfterM: Double = REBIND_AFTER_M,
    ): Boolean {
        if (!pathSinceReleaseM.isFinite() || !remainingAtReleaseM.isFinite()) return false
        val remaining = remainingAtReleaseM.coerceAtLeast(0.0)
        return pathSinceReleaseM >= remaining + rebindAfterM
    }

    fun shouldRebindAfterStalkOff(
        pathSinceStalkOffM: Double,
        rebindAfterM: Double = STALK_REBIND_AFTER_M,
    ): Boolean {
        if (!pathSinceStalkOffM.isFinite()) return false
        return pathSinceStalkOffM >= rebindAfterM.coerceAtLeast(0.0)
    }

    fun stalkUnbindQualifies(
        enabled: Boolean,
        turnHintPresent: Boolean,
        turnIntent: Boolean,
        intentionalOnly: Boolean,
        blockHighway: Boolean,
        highwayProfile: Boolean,
        speedKmh: Float,
        minSpeedKmh: Float,
    ): Boolean {
        if (!enabled) return false
        if (!turnHintPresent) return false
        if (intentionalOnly && !turnIntent) return false
        if (blockHighway && highwayProfile) return false
        if (!speedKmh.isFinite()) return false
        return speedKmh >= minSpeedKmh
    }
}
