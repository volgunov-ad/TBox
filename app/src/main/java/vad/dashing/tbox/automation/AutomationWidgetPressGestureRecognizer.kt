package vad.dashing.tbox.automation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Exclusive single / double tap recognition for automation trigger tiles.
 *
 * Per-[triggerId] state machine (mirrors Compose `combinedClickable` exclusive double-tap
 * used by other dashboard widgets):
 * - first tap starts a wait of [doubleTapMillis];
 * - no second tap within the window → [AutomationWidgetPressKind.SINGLE];
 * - second tap within the window → [AutomationWidgetPressKind.DOUBLE] (pending SINGLE cancelled).
 */
internal class AutomationWidgetPressGestureRecognizer(
    private val scope: CoroutineScope,
    private val doubleTapMillis: Long = automationWidgetDoubleTapTimeoutMillis(),
    private val delayMillis: suspend (Long) -> Unit = { delay(it) },
    private val publish: (triggerId: String, kind: AutomationWidgetPressKind) -> Unit,
) {
    private enum class Phase {
        IDLE,
        WAITING_DOUBLE,
    }

    private data class IdState(
        var phase: Phase = Phase.IDLE,
        var singleJob: Job? = null,
    )

    private val ids = mutableMapOf<String, IdState>()

    fun onTap(triggerId: String) {
        val id = triggerId.trim()
        if (id.isEmpty()) return
        val state = ids.getOrPut(id) { IdState() }
        when (state.phase) {
            Phase.WAITING_DOUBLE -> {
                state.singleJob?.cancel()
                state.singleJob = null
                state.phase = Phase.IDLE
                publish(id, AutomationWidgetPressKind.DOUBLE)
            }
            Phase.IDLE -> {
                state.phase = Phase.WAITING_DOUBLE
                state.singleJob?.cancel()
                state.singleJob = scope.launch {
                    delayMillis(doubleTapMillis)
                    if (state.phase == Phase.WAITING_DOUBLE) {
                        state.phase = Phase.IDLE
                        publish(id, AutomationWidgetPressKind.SINGLE)
                    }
                }
            }
        }
    }

    fun clear() {
        ids.values.forEach { state ->
            state.singleJob?.cancel()
        }
        ids.clear()
    }
}
