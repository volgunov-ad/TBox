package vad.dashing.tbox.location

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Published geoposition for UI widgets and loc indicator (aligned with mock when active).
 */
object GeoDisplayRepository {
    private val _state = MutableStateFlow(GeoDisplayState.EMPTY)
    val state: StateFlow<GeoDisplayState> = _state.asStateFlow()

    fun publish(state: GeoDisplayState) {
        if (_state.value != state) {
            _state.value = state
        }
    }

    fun reset() {
        _state.value = GeoDisplayState.EMPTY
    }
}
