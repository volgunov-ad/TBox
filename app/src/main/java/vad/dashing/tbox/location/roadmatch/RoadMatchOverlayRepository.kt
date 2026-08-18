package vad.dashing.tbox.location.roadmatch

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide overlay snapshot for the Phase F map widget (F1 data plane).
 */
object RoadMatchOverlayRepository {
    private val _state = MutableStateFlow(RoadMatchOverlayState.EMPTY)
    val state: StateFlow<RoadMatchOverlayState> = _state.asStateFlow()

    fun publish(state: RoadMatchOverlayState) {
        if (_state.value != state) {
            _state.value = state
        }
    }

    fun clear() {
        _state.value = RoadMatchOverlayState.EMPTY
    }
}
