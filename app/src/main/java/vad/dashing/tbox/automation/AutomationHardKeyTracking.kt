package vad.dashing.tbox.automation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import vad.dashing.tbox.HeadUnitCanMode
import vad.dashing.tbox.WidgetButtonBindingRegistry
import vad.dashing.tbox.mbcan.MbCanRepository
import vad.dashing.tbox.mbcan.UniversalCanRepository

/**
 * Enables hardkey delivery while at least one runnable automation or a visible tile
 * binding needs it. Android 9 uses the mbCAN listener. Android 10 uses steering
 * `InputKey` broadcasts and door-panel VHAL properties.
 */
object AutomationHardKeyTracking {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val startLock = Any()
    private var started = false

    private val _interestRequired = MutableStateFlow(false)
    val interestRequired: StateFlow<Boolean> = _interestRequired.asStateFlow()

    fun setInterestRequired(required: Boolean) {
        ensureStarted()
        _interestRequired.value = required
    }

    fun ensureStarted() {
        synchronized(startLock) {
            if (started) return
            started = true
        }
        scope.launch {
            combine(
                UniversalCanRepository.mode,
                _interestRequired,
                WidgetButtonBindingRegistry.hardKeyVisible,
            ) { mode, automation, widget ->
                mode to (automation || widget)
            }.collect { (mode, required) ->
                MbCanRepository.setAutomationHardKeyTrackingEnabled(
                    mode == HeadUnitCanMode.Android9MbCan && required,
                )
                Android10HardKeySource.setEnabled(
                    mode == HeadUnitCanMode.Android10Vhal && required,
                )
            }
        }
    }
}
