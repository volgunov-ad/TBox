package vad.dashing.tbox.mbcan

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vad.dashing.tbox.TboxRepository

/**
 * Session-only mbCAN diagnostics switch.
 * Not persisted; should be explicitly reset when BackgroundService starts.
 */
object MbCanDiagnostics {
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setEnabled(value: Boolean) {
        _enabled.value = value
    }

    fun log(level: String, message: String) {
        if (!_enabled.value) return
        TboxRepository.addLog(level, "MBCAN_TMP", message)
    }
}

