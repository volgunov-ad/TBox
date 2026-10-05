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
import vad.dashing.tbox.mbcan.MbCanRepository
import vad.dashing.tbox.mbcan.UniversalCanRepository

/**
 * Enables the OEM mbCAN hardkey subscription only while the Android 9 mbCAN backend is
 * active and at least one runnable automation uses an [AutomationTrigger.HardKey] trigger.
 * Same lifecycle pattern as the CCS stalk tracking in `CcsRememberedSetpoint`.
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
            combine(UniversalCanRepository.mode, _interestRequired) { mode, required ->
                mode == HeadUnitCanMode.Android9MbCan && required
            }.collect { enable ->
                MbCanRepository.setAutomationHardKeyTrackingEnabled(enable)
            }
        }
    }
}
