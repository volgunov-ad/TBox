package vad.dashing.tbox.location

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Session-only debug gate that makes the active location source appear unavailable.
 *
 * The physical transport remains connected so reception can resume immediately. Producers may
 * continue parsing diagnostics, but [vad.dashing.tbox.TboxRepository] rejects active location
 * updates while this gate is enabled.
 */
object SimulatedLocationSourceLoss {
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        _enabled.value = enabled
    }

    fun acceptsLocationUpdates(): Boolean = !_enabled.value

    /** Not persisted: every background-service session starts with normal reception. */
    fun reset() {
        _enabled.value = false
    }
}
