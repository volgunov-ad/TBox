package vad.dashing.tbox.automation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Derives [AutomationHardKeyStatus.SINGLE], [DOUBLE], and [LONG] from OEM
 * press/release edges. Does not re-emit [PRESSED]/[RELEASED] — those already
 * flow through [AutomationTriggerHardKeyEventBus] from the forwarder.
 *
 * Per-[keyCode] state machine:
 * - hold ≥ [longPressMillis] while down → LONG (release then ignored for gestures);
 * - short press+release, no second press within [doubleTapMillis] → SINGLE;
 * - second press+release within the window → DOUBLE (cancels pending SINGLE).
 */
internal class AutomationHardKeyGestureRecognizer(
    private val scope: CoroutineScope,
    private val longPressMillis: Long = AUTOMATION_HARD_KEY_LONG_PRESS_MS,
    private val doubleTapMillis: Long = AUTOMATION_HARD_KEY_DOUBLE_TAP_MS,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val delayMillis: suspend (Long) -> Unit = { delay(it) },
    private val publish: (keyCode: Int, status: AutomationHardKeyStatus) -> Unit,
) {
    private enum class Phase {
        IDLE,
        DOWN,
        UP_WAITING_DOUBLE,
        DOWN_SECOND,
        LONG_FIRED,
    }

    private data class KeyState(
        var phase: Phase = Phase.IDLE,
        var longJob: Job? = null,
        var singleJob: Job? = null,
    )

    private val keys = mutableMapOf<Int, KeyState>()

    /** Feed only OEM [PRESSED]/[RELEASED] edges (after debounce). */
    fun onRaw(keyCode: Int, status: AutomationHardKeyStatus) {
        when (status) {
            AutomationHardKeyStatus.PRESSED -> onPressed(keyCode)
            AutomationHardKeyStatus.RELEASED -> onReleased(keyCode)
            AutomationHardKeyStatus.SINGLE,
            AutomationHardKeyStatus.DOUBLE,
            AutomationHardKeyStatus.LONG,
            -> Unit
        }
    }

    fun clear() {
        keys.values.forEach { state ->
            state.longJob?.cancel()
            state.singleJob?.cancel()
        }
        keys.clear()
    }

    private fun stateFor(keyCode: Int): KeyState =
        keys.getOrPut(keyCode) { KeyState() }

    private fun onPressed(keyCode: Int) {
        val state = stateFor(keyCode)
        state.singleJob?.cancel()
        state.singleJob = null
        when (state.phase) {
            Phase.UP_WAITING_DOUBLE -> {
                // Second press of a double: no long-press path (short taps only).
                state.phase = Phase.DOWN_SECOND
            }
            Phase.IDLE, Phase.LONG_FIRED, Phase.DOWN, Phase.DOWN_SECOND -> {
                state.longJob?.cancel()
                state.phase = Phase.DOWN
                val startedAt = nowMillis()
                state.longJob = scope.launch {
                    delayMillis(longPressMillis)
                    if (state.phase == Phase.DOWN && nowMillis() - startedAt >= longPressMillis) {
                        state.phase = Phase.LONG_FIRED
                        publish(keyCode, AutomationHardKeyStatus.LONG)
                    }
                }
            }
        }
    }

    private fun onReleased(keyCode: Int) {
        val state = stateFor(keyCode)
        state.longJob?.cancel()
        state.longJob = null
        when (state.phase) {
            Phase.LONG_FIRED -> {
                state.phase = Phase.IDLE
            }
            Phase.DOWN_SECOND -> {
                state.phase = Phase.IDLE
                publish(keyCode, AutomationHardKeyStatus.DOUBLE)
            }
            Phase.DOWN -> {
                state.phase = Phase.UP_WAITING_DOUBLE
                state.singleJob = scope.launch {
                    delayMillis(doubleTapMillis)
                    if (state.phase == Phase.UP_WAITING_DOUBLE) {
                        state.phase = Phase.IDLE
                        publish(keyCode, AutomationHardKeyStatus.SINGLE)
                    }
                }
            }
            Phase.IDLE, Phase.UP_WAITING_DOUBLE -> Unit
        }
    }
}
