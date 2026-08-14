package vad.dashing.tbox.location.roadmatch

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Read-only match snapshot for the OSM speed-limit widget (and later lookahead).
 * Independent of whether the DR shadow is being corrected.
 */
data class RoadMatchAnchorState(
    val matchNeeded: Boolean = false,
    val correctPose: Boolean = false,
    val edgeId: Long? = null,
    val regionId: String? = null,
    val alongTrackM: Double? = null,
    val travelAgainstCoords: Boolean? = null,
    val highwayClass: String? = null,
    val confidence: String? = null,
    val edgeBearingDeg: Float? = null,
    val skippedReason: String? = null,
) {
    companion object {
        val EMPTY = RoadMatchAnchorState()

        fun from(
            demand: RoadMatchDemand,
            debug: RoadMatchRuntime.DebugSnapshot,
            travelAgainstCoords: Boolean?,
        ): RoadMatchAnchorState = RoadMatchAnchorState(
            matchNeeded = demand.matchNeeded,
            correctPose = demand.correctPose,
            edgeId = debug.edgeId,
            regionId = debug.regionId,
            alongTrackM = debug.alongTrackM,
            travelAgainstCoords = travelAgainstCoords,
            highwayClass = debug.highwayClass,
            confidence = debug.confidence,
            edgeBearingDeg = debug.edgeBearingDeg,
            skippedReason = debug.skippedReason,
        )
    }
}

object RoadMatchAnchorRepository {
    private val _state = MutableStateFlow(RoadMatchAnchorState.EMPTY)
    val state: StateFlow<RoadMatchAnchorState> = _state.asStateFlow()

    fun publish(state: RoadMatchAnchorState) {
        if (_state.value != state) {
            _state.value = state
        }
    }

    fun clear() {
        _state.value = RoadMatchAnchorState.EMPTY
    }
}
