package vad.dashing.tbox.mbcan

/**
 * Retriggerable off-delay for HU turn lamps / stalk.
 *
 * A9 `TURNLIGHT` blinks (~1–2 s on, ~1–2 s off). A flash means that side is on;
 * each flash restarts [HOLD_MS]. No flash for [HOLD_MS] → off.
 * A10 `DirectionInd` is already stable: the same timer is a short tail after
 * the stalk is cancelled.
 *
 * Opposite side or hazard immediately clears the other latch.
 * Fork hint is L/R only — never hazard.
 */
class TurnSignalsLatch(
    private val holdMs: Long = HOLD_MS,
) {
    private var lastLeftElapsedMs: Long = NEVER
    private var lastRightElapsedMs: Long = NEVER

    /**
     * Ingest the latest CAN sample and return the latched fork hint at [nowElapsedMs].
     */
    fun onState(state: TurnSignalsState, nowElapsedMs: Long): TurnSignalSide? {
        if (isHazard(state)) {
            lastLeftElapsedMs = NEVER
            lastRightElapsedMs = NEVER
            return null
        }
        when {
            state.leftActive == true -> {
                lastLeftElapsedMs = nowElapsedMs
                lastRightElapsedMs = NEVER
            }
            state.rightActive == true -> {
                lastRightElapsedMs = nowElapsedMs
                lastLeftElapsedMs = NEVER
            }
        }
        return latchedForkHint(nowElapsedMs)
    }

    fun latchedForkHint(nowElapsedMs: Long): TurnSignalSide? {
        val left = held(lastLeftElapsedMs, nowElapsedMs)
        val right = held(lastRightElapsedMs, nowElapsedMs)
        return when {
            left && !right -> TurnSignalSide.Left
            right && !left -> TurnSignalSide.Right
            else -> null
        }
    }

    fun reset() {
        lastLeftElapsedMs = NEVER
        lastRightElapsedMs = NEVER
    }

    private fun held(lastElapsedMs: Long, nowElapsedMs: Long): Boolean {
        if (lastElapsedMs == NEVER) return false
        return nowElapsedMs - lastElapsedMs <= holdMs
    }

    companion object {
        const val HOLD_MS = 2_500L
        private const val NEVER = Long.MIN_VALUE

        val shared = TurnSignalsLatch()

        fun isHazard(state: TurnSignalsState): Boolean =
            state.hazardActive == true ||
                (state.leftActive == true && state.rightActive == true)
    }
}
