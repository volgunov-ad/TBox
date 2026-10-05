package vad.dashing.tbox.automation

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Normalized OEM hardkey event delivered to automations. */
data class AutomationHardKeyEvent(
    val keyCode: Int,
    val keyStatus: AutomationHardKeyStatus,
)

/**
 * Process-wide stream of A9 mbCAN OEM hardkey events (steering wheel keys, door buttons).
 * No replay on purpose: keys pressed while the engine loop is not running are dropped.
 */
object AutomationTriggerHardKeyEventBus {

    private val hardKeys = MutableSharedFlow<AutomationHardKeyEvent>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val events: SharedFlow<AutomationHardKeyEvent> = hardKeys.asSharedFlow()

    fun publish(event: AutomationHardKeyEvent) {
        hardKeys.tryEmit(event)
    }
}
