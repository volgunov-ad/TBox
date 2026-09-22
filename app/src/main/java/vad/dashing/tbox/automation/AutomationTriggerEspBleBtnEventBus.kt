package vad.dashing.tbox.automation

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Normalized Shelly Blu / companion BLE button event for automations. */
data class AutomationEspBleBtnEvent(
    val btn: Int,
    val act: String,
)

/**
 * Process-wide stream of ESP companion `bleBtn` events.
 * No replay: presses while the engine loop is not running are dropped.
 */
object AutomationTriggerEspBleBtnEventBus {

    private val buttons = MutableSharedFlow<AutomationEspBleBtnEvent>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val events: SharedFlow<AutomationEspBleBtnEvent> = buttons.asSharedFlow()

    fun publish(event: AutomationEspBleBtnEvent) {
        buttons.tryEmit(event)
    }
}
