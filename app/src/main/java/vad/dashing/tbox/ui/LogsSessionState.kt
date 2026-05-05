package vad.dashing.tbox.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Session-only UI state for Logs tab controls.
 * Not persisted in DataStore; lives only while app process is alive.
 */
object LogsSessionState {
    private val _messageFilter = MutableStateFlow("")
    val messageFilter: StateFlow<String> = _messageFilter.asStateFlow()

    fun setMessageFilter(value: String) {
        _messageFilter.value = value
    }
}

