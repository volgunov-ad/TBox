package vad.dashing.tbox.automation

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Raw tap from an automation trigger tile (before exclusive single/double recognition).
 * No replay on purpose: presses raised while the engine loop is not running are dropped.
 */
data class AutomationTriggerWidgetRawTap(
    val triggerId: String,
)

/**
 * Process-wide stream of raw automation trigger widget taps.
 * [AutomationEngine] runs [AutomationWidgetPressGestureRecognizer] to emit exclusive
 * [AutomationWidgetPressKind.SINGLE] / [DOUBLE] into the evaluator.
 */
object AutomationTriggerWidgetPressEventBus {

    private val presses = MutableSharedFlow<AutomationTriggerWidgetRawTap>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val events: SharedFlow<AutomationTriggerWidgetRawTap> = presses.asSharedFlow()

    fun publish(triggerId: String) {
        val id = triggerId.trim()
        if (id.isEmpty()) return
        presses.tryEmit(AutomationTriggerWidgetRawTap(id))
    }
}
