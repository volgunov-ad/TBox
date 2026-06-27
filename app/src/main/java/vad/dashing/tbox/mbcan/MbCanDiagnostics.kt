package vad.dashing.tbox.mbcan

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vad.dashing.tbox.TboxRepository

/**
 * Session-only CAN diagnostics switch (mbCAN + VHAL).
 * Not persisted; should be explicitly reset when BackgroundService starts.
 */
object MbCanDiagnostics {
    private const val DEFAULT_TAG = "MBCAN_TMP"
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setEnabled(value: Boolean) {
        _enabled.value = value
    }

    fun log(level: String, message: String) {
        log(level = level, tag = DEFAULT_TAG, message = message)
    }

    fun log(level: String, tag: String, message: String) {
        if (!_enabled.value) return
        TboxRepository.addLog(level, tag, message)
    }
}

