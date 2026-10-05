package vad.dashing.tbox.automation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Runtime-only active state for automation trigger widget tiles.
 * Starts empty on every process start: a tile becomes active only after an automation
 * activates it and stays active until deactivated or the process restarts. Not persisted.
 */
object AutomationTriggerWidgetState {

    private val _activeIds = MutableStateFlow<Set<String>>(emptySet())
    val activeIds: StateFlow<Set<String>> = _activeIds.asStateFlow()

    fun setActive(triggerId: String, active: Boolean) {
        val id = triggerId.trim()
        if (id.isEmpty()) return
        _activeIds.update { current ->
            if (active) current + id else current - id
        }
    }

    fun isActive(triggerId: String): Boolean =
        triggerId.isNotBlank() && triggerId in _activeIds.value

    internal fun resetForTests() {
        _activeIds.value = emptySet()
    }
}
