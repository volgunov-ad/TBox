package vad.dashing.tbox.mbcan

/**
 * Distinguishes comfort 3-blink / brief stalk from an intentional turn signal.
 *
 * A9 lamps: each rising edge is one flash; comfort blink is typically 3 flashes.
 * Intentional starts at flash [minFlashesForIntent] (default 4th flash).
 *
 * A10 DirectionInd: stalk stays true while held — a tap is one rising edge;
 * holding ≥ [continuousStalkMs] marks intentional without needing 4 flashes.
 *
 * Lives outside [vad.dashing.tbox.location.roadmatch.RoadMatchRuntime] so
 * Ordinary↔Rails resets do not clear intent. Thresholds are mutable so road-match
 * tuning can adjust them live.
 */
class TurnSignalIntentTracker(
    minFlashesForIntent: Int = MIN_FLASHES_FOR_INTENT,
    continuousStalkMs: Long = CONTINUOUS_STALK_MS,
) {
    data class Snapshot(
        /** Latched L/R side for this sample, or null when idle/hazard. */
        val side: TurnSignalSide? = null,
        /** True when flash count or continuous stalk says real intent (not comfort 3×). */
        val intentional: Boolean = false,
        /** Rising-edge flash count on the active side since the sequence started. */
        val flashCount: Int = 0,
    )

    @Volatile
    var minFlashesForIntent: Int = minFlashesForIntent
        set(value) {
            field = value.coerceIn(1, 12)
        }

    @Volatile
    var continuousStalkMs: Long = continuousStalkMs
        set(value) {
            field = value.coerceIn(50L, 10_000L)
        }

    private var activeSide: TurnSignalSide? = null
    private var flashCount: Int = 0
    private var prevLeft: Boolean = false
    private var prevRight: Boolean = false
    private var continuousSinceElapsedMs: Long = NEVER
    private var intentional: Boolean = false

    fun onState(state: TurnSignalsState, nowElapsedMs: Long): Snapshot {
        if (TurnSignalsLatch.isHazard(state)) {
            reset()
            return Snapshot()
        }
        val left = state.leftActive == true
        val right = state.rightActive == true
        when {
            left && !right -> onSideActive(TurnSignalSide.Left, rising = left && !prevLeft, nowElapsedMs)
            right && !left -> onSideActive(TurnSignalSide.Right, rising = right && !prevRight, nowElapsedMs)
            else -> {
                // Gap between A9 flashes / stalk released — keep count until latch
                // consumer resets via [reset] or opposite side / hazard above.
                continuousSinceElapsedMs = NEVER
            }
        }
        prevLeft = left
        prevRight = right
        if (!intentional && continuousSinceElapsedMs != NEVER &&
            nowElapsedMs - continuousSinceElapsedMs >= continuousStalkMs
        ) {
            intentional = true
        }
        if (!intentional && flashCount >= minFlashesForIntent) {
            intentional = true
        }
        return Snapshot(
            side = activeSide,
            intentional = intentional && activeSide != null,
            flashCount = flashCount,
        )
    }

    /** Call when the latched fork hint expires so the next comfort sequence starts at 0. */
    fun onLatchedIdle() {
        reset()
    }

    fun reset() {
        activeSide = null
        flashCount = 0
        prevLeft = false
        prevRight = false
        continuousSinceElapsedMs = NEVER
        intentional = false
    }

    private fun onSideActive(side: TurnSignalSide, rising: Boolean, nowElapsedMs: Long) {
        if (activeSide != null && activeSide != side) {
            // Opposite side starts a new sequence.
            flashCount = 0
            intentional = false
            continuousSinceElapsedMs = NEVER
        }
        activeSide = side
        if (rising) {
            flashCount += 1
            continuousSinceElapsedMs = nowElapsedMs
        } else if (continuousSinceElapsedMs == NEVER) {
            continuousSinceElapsedMs = nowElapsedMs
        }
    }

    companion object {
        /** Comfort blink is 3 flashes; 4th means held / intentional. */
        const val MIN_FLASHES_FOR_INTENT = 4
        /** A10 held stalk longer than a tap that only triggers comfort blink. */
        const val CONTINUOUS_STALK_MS = 2_000L
        private const val NEVER = Long.MIN_VALUE
    }
}
