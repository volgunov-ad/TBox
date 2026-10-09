package vad.dashing.tbox

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import vad.dashing.tbox.automation.AutomationEspBleBtnAction
import vad.dashing.tbox.automation.AutomationHardKeyStatus
import vad.dashing.tbox.automation.AutomationTriggerEspBleBtnEventBus
import vad.dashing.tbox.automation.AutomationTriggerEspGpioBtnEventBus
import vad.dashing.tbox.automation.AutomationTriggerHardKeyEventBus

/**
 * Routes physical button gestures to on-screen tile bindings.
 *
 * Mapping:
 * - Hard key / ESP GPIO: [SINGLE]→single, [DOUBLE]→double
 * - Shelly BLE: [PRESS]→single, [DOUBLE]→double
 */
class WidgetButtonBindingCoordinator(
    private val scope: CoroutineScope,
) {
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch {
            launch {
                AutomationTriggerHardKeyEventBus.events.collect { event ->
                    val tap = when (event.keyStatus) {
                        AutomationHardKeyStatus.SINGLE -> WidgetButtonBindingTap.SINGLE
                        AutomationHardKeyStatus.DOUBLE -> WidgetButtonBindingTap.DOUBLE
                        else -> return@collect
                    }
                    WidgetButtonBindingRegistry.dispatch(
                        WidgetButtonBinding.HardKey(event.keyCode),
                        tap,
                    )
                }
            }
            launch {
                AutomationTriggerEspBleBtnEventBus.events.collect { event ->
                    val act = AutomationEspBleBtnAction.fromStorageKey(event.act) ?: return@collect
                    val tap = when (act) {
                        AutomationEspBleBtnAction.PRESS -> WidgetButtonBindingTap.SINGLE
                        AutomationEspBleBtnAction.DOUBLE -> WidgetButtonBindingTap.DOUBLE
                        else -> return@collect
                    }
                    WidgetButtonBindingRegistry.dispatch(
                        WidgetButtonBinding.EspBle(mac = event.mac, btn = event.btn),
                        tap,
                    )
                }
            }
            launch {
                AutomationTriggerEspGpioBtnEventBus.events.collect { event ->
                    val tap = when (event.status) {
                        AutomationHardKeyStatus.SINGLE -> WidgetButtonBindingTap.SINGLE
                        AutomationHardKeyStatus.DOUBLE -> WidgetButtonBindingTap.DOUBLE
                        else -> return@collect
                    }
                    WidgetButtonBindingRegistry.dispatch(
                        WidgetButtonBinding.EspGpio(event.channel),
                        tap,
                    )
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
