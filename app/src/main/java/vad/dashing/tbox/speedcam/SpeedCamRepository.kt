package vad.dashing.tbox.speedcam

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Published SpeedCam lookahead for the dashboard widget and optional map markers.
 */
object SpeedCamRepository {
    private val _state = MutableStateFlow(SpeedCamUiState.EMPTY)
    val state: StateFlow<SpeedCamUiState> = _state.asStateFlow()

    fun publish(state: SpeedCamUiState) {
        if (_state.value != state) {
            _state.value = state
        }
    }

    fun clear() {
        _state.value = SpeedCamUiState.EMPTY
    }

    /**
     * Recompute alert from [index] + pose. [showMapMarkers] controls [SpeedCamUiState.nearbyForMap].
     */
    fun updateFromPose(
        index: SpeedCamIndex?,
        installedMeta: SpeedCamPackManager.Snapshot,
        lat: Double,
        lon: Double,
        bearingDeg: Float?,
        vehicleSpeedKmh: Float,
        radiusM: Int,
        overageKmh: Int,
        showMapMarkers: Boolean,
    ) {
        if (index == null || !installedMeta.installed) {
            publish(
                SpeedCamUiState(
                    installed = false,
                    pointCount = 0,
                    installedAtEpochMs = installedMeta.installedAtEpochMs,
                    sourceLabel = installedMeta.source,
                ),
            )
            return
        }
        val bearing = bearingDeg?.takeIf { it.isFinite() }
        val alert = if (bearing != null) {
            SpeedCamLookahead.findNearest(
                index = index,
                lat = lat,
                lon = lon,
                bearingDeg = bearing,
                radiusM = radiusM,
                vehicleSpeedKmh = vehicleSpeedKmh,
                overageKmh = overageKmh,
            )
        } else {
            null
        }
        val nearby = if (showMapMarkers && bearing != null) {
            SpeedCamLookahead.nearbyMarkers(
                index = index,
                lat = lat,
                lon = lon,
                radiusM = radiusM,
                alertId = alert?.point?.id,
            )
        } else {
            emptyList()
        }
        publish(
            SpeedCamUiState(
                installed = true,
                pointCount = index.size,
                installedAtEpochMs = installedMeta.installedAtEpochMs,
                sourceLabel = installedMeta.source,
                alert = alert,
                nearbyForMap = nearby,
            ),
        )
    }
}
