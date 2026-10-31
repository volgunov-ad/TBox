package vad.dashing.tbox

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
 *
 * Tile taps run on [dispatchContext] (main by default): the tile click
 * lambdas may touch main-thread-only UI APIs (Toast, dialogs).
 */
class WidgetButtonBindingCoordinator(
    private val scope: CoroutineScope,
    private val dispatchContext: CoroutineContext = Dispatchers.Main.immediate,
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
                    dispatch(
                        binding = WidgetButtonBinding.HardKey(event.keyCode),
                        tap = tap,
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
                    dispatch(
                        binding = WidgetButtonBinding.EspBle(mac = event.mac, btn = event.btn),
                        tap = tap,
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
                    dispatch(
                        binding = WidgetButtonBinding.EspGpio(event.channel),
                        tap = tap,
                    )
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun dispatch(binding: WidgetButtonBinding, tap: WidgetButtonBindingTap) {
        withContext(dispatchContext) {
            WidgetButtonBindingRegistry.dispatch(binding, tap)
        }
    }
}
