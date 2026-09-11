package vad.dashing.tbox.automation

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Process-wide stream of automation trigger widget taps (published trigger ids).
 * No replay on purpose: presses raised while the engine loop is not running are dropped.
 */
object AutomationTriggerWidgetPressEventBus {

    private val presses = MutableSharedFlow<String>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val events: SharedFlow<String> = presses.asSharedFlow()

    fun publish(triggerId: String) {
        val id = triggerId.trim()
        if (id.isEmpty()) return
        presses.tryEmit(id)
    }
}
