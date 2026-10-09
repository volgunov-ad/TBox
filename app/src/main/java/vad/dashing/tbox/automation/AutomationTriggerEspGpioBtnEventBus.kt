package vad.dashing.tbox.automation

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** ESP companion GPIO input edge / synthesized gesture for automations and tile bindings. */
data class AutomationEspGpioBtnEvent(
    val channel: Int,
    val status: AutomationHardKeyStatus,
)

/**
 * Process-wide stream of ESP GPIO button edges (pressed/released) and synthesized
 * single/double/long gestures. No replay: events while nobody collects are dropped.
 */
object AutomationTriggerEspGpioBtnEventBus {

    private val eventsFlow = MutableSharedFlow<AutomationEspGpioBtnEvent>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val events: SharedFlow<AutomationEspGpioBtnEvent> = eventsFlow.asSharedFlow()

    fun publish(channel: Int, status: AutomationHardKeyStatus) {
        if (channel !in 0..3) return
        eventsFlow.tryEmit(AutomationEspGpioBtnEvent(channel, status))
    }
}
