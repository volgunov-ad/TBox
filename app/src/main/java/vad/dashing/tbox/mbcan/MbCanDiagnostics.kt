package vad.dashing.tbox.mbcan

import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vad.dashing.tbox.TboxRepository

/**
 * Session-only CAN diagnostics switch (mbCAN + VHAL).
 * Not persisted; should be explicitly reset when BackgroundService starts.
 *
 * [ERROR]/[WARN]/[INFO] always go to the in-app journal (subject to the global min log level).
 * Verbose [DEBUG] lines require [enabled] = true.
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
        if (!shouldEmit(level)) return
        TboxRepository.addLog(level, tag, message)
    }

    /** Visible for unit tests: DEBUG only when diagnostics enabled; other levels always. */
    internal fun shouldEmit(level: String, diagnosticsEnabled: Boolean = _enabled.value): Boolean {
        val normalized = level.trim().uppercase(Locale.ROOT)
        if (normalized == "DEBUG") return diagnosticsEnabled
        return true
    }
}
