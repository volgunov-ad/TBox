package vad.dashing.tbox.automation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Raises shared road-match demand ([vad.dashing.tbox.location.roadmatch.RoadMatchDemand])
 * only while at least one runnable automation uses road APP signals
 * ([AutomationSignalId.ROAD_HIGHWAY_CLASS], [AutomationSignalId.ROAD_SPEED_LIMIT_KMH]).
 *
 * Same lifecycle pattern as [AutomationHardKeyTracking]: interest is set from
 * [AutomationEngine] when definitions change; [correctPose] stays off for this source.
 * Does not start SpeedCam.
 */
object AutomationRoadMatchTracking {
    private val _interestRequired = MutableStateFlow(false)
    val interestRequired: StateFlow<Boolean> = _interestRequired.asStateFlow()

    fun setInterestRequired(required: Boolean) {
        _interestRequired.value = required
    }
}
