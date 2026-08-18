package vad.dashing.tbox.mbcan

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
 *
 * [lastIntentSnapshot] distinguishes comfort 3-blink from intentional
 * (≥4 flashes / held stalk) for road-match ramp boost.
 */
class TurnSignalsLatch(
    private val holdMs: Long = HOLD_MS,
) {
    private var lastLeftElapsedMs: Long = NEVER
    private var lastRightElapsedMs: Long = NEVER
    private val intentTracker = TurnSignalIntentTracker()
    private var lastIntent: TurnSignalIntentTracker.Snapshot =
        TurnSignalIntentTracker.Snapshot()

    /**
     * Ingest the latest CAN sample and return the latched fork hint at [nowElapsedMs].
     */
    fun onState(state: TurnSignalsState, nowElapsedMs: Long): TurnSignalSide? {
        if (isHazard(state)) {
            lastLeftElapsedMs = NEVER
            lastRightElapsedMs = NEVER
            intentTracker.reset()
            lastIntent = TurnSignalIntentTracker.Snapshot()
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
        val side = latchedForkHint(nowElapsedMs)
        if (side == null) {
            intentTracker.onLatchedIdle()
            lastIntent = TurnSignalIntentTracker.Snapshot()
        } else {
            val snap = intentTracker.onState(state, nowElapsedMs)
            lastIntent = snap.copy(
                side = side,
                intentional = snap.intentional,
            )
        }
        return side
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

    fun lastIntentSnapshot(): TurnSignalIntentTracker.Snapshot = lastIntent

    fun reset() {
        lastLeftElapsedMs = NEVER
        lastRightElapsedMs = NEVER
        intentTracker.reset()
        lastIntent = TurnSignalIntentTracker.Snapshot()
    }

    private fun held(lastElapsedMs: Long, nowElapsedMs: Long): Boolean {
        if (lastElapsedMs == NEVER) return false
        return nowElapsedMs - lastElapsedMs <= holdMs
    }

    companion object {
        const val HOLD_MS = 2_500L
        private const val NEVER = Long.MIN_VALUE

        fun isHazard(state: TurnSignalsState): Boolean =
            state.hazardActive == true ||
                (state.leftActive == true && state.rightActive == true)
    }
}

/**
 * Owns one [TurnSignalsLatch] and last raw sample so [UniversalCanRepository]
 * can publish a shared latched L/R for every consumer.
 *
 * [ingest] on each CAN update; [poll] on a short ticker so A10 (stable true)
 * retriggers and the hold expires without waiting for the next frame.
 */
class TurnSignalsLatchRuntime(
    private val elapsedRealtimeMs: () -> Long,
    holdMs: Long = TurnSignalsLatch.HOLD_MS,
) {
    private val latch = TurnSignalsLatch(holdMs)
    private val _side = MutableStateFlow<TurnSignalSide?>(null)
    val side: StateFlow<TurnSignalSide?> = _side.asStateFlow()
    private val _intent = MutableStateFlow(TurnSignalIntentTracker.Snapshot())
    val intent: StateFlow<TurnSignalIntentTracker.Snapshot> = _intent.asStateFlow()
    private var lastState = TurnSignalsState()

    fun ingest(state: TurnSignalsState) {
        lastState = state
        publish()
    }

    fun poll() {
        val rawOn = lastState.leftActive == true ||
            lastState.rightActive == true ||
            lastState.hazardActive == true
        if (!rawOn && _side.value == null) return
        publish()
    }

    fun peek(): TurnSignalSide? = latch.latchedForkHint(elapsedRealtimeMs())

    fun peekIntent(): TurnSignalIntentTracker.Snapshot = latch.lastIntentSnapshot()

    fun reset() {
        latch.reset()
        lastState = TurnSignalsState()
        _side.value = null
        _intent.value = TurnSignalIntentTracker.Snapshot()
    }

    private fun publish() {
        val now = elapsedRealtimeMs()
        _side.value = latch.onState(lastState, now)
        _intent.value = latch.lastIntentSnapshot()
    }

    companion object {
        const val POLL_MS = 100L
    }
}
