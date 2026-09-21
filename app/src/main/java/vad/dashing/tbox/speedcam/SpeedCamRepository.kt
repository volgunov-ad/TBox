package vad.dashing.tbox.speedcam

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vad.dashing.tbox.LastRadarLimit
import vad.dashing.tbox.MapsCamerasRadarsLogic
import vad.dashing.tbox.DEFAULT_MAPS_CAM_RADAR_HOLD_M
import vad.dashing.tbox.normalizeMapsCamRadarHoldM

/**
 * Published SpeedCam lookahead for the unified maps/cameras widget and optional map markers.
 */
object SpeedCamRepository {
    private val _state = MutableStateFlow(SpeedCamUiState.EMPTY)
    val state: StateFlow<SpeedCamUiState> = _state.asStateFlow()

    @Volatile
    private var lastRadar: LastRadarLimit? = null

    fun publish(state: SpeedCamUiState) {
        if (_state.value != state) {
            _state.value = state
        }
    }

    fun clear() {
        lastRadar = null
        _state.value = SpeedCamUiState.EMPTY
    }

    fun lastRadarLimit(): LastRadarLimit? = lastRadar

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
        roadHint: SpeedCamLookahead.RoadMatchHint? = null,
        radarHoldDistanceM: Int = DEFAULT_MAPS_CAM_RADAR_HOLD_M,
    ) {
        if (index == null || !installedMeta.installed) {
            lastRadar = MapsCamerasRadarsLogic.updateLastRadar(
                previous = lastRadar,
                alertLat = null,
                alertLon = null,
                alertSpeedKmh = null,
                alertDistanceM = null,
                vehicleLat = lat,
                vehicleLon = lon,
                holdDistanceM = normalizeMapsCamRadarHoldM(radarHoldDistanceM),
            )
            publish(
                SpeedCamUiState(
                    installed = false,
                    pointCount = 0,
                    installedAtEpochMs = installedMeta.installedAtEpochMs,
                    sourceLabel = installedMeta.source,
                    lastRadarLimitKmh = lastRadar?.speedKmh,
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
                roadHint = roadHint,
            )
        } else {
            null
        }
        lastRadar = MapsCamerasRadarsLogic.updateLastRadar(
            previous = lastRadar,
            alertLat = alert?.point?.lat,
            alertLon = alert?.point?.lon,
            alertSpeedKmh = alert?.point?.speedKmh?.takeIf { alert.point.hasSpeedLimit },
            alertDistanceM = alert?.distanceM,
            vehicleLat = lat,
            vehicleLon = lon,
            holdDistanceM = normalizeMapsCamRadarHoldM(radarHoldDistanceM),
        )
        val nearby = if (showMapMarkers) {
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
                lastRadarLimitKmh = lastRadar?.speedKmh,
            ),
        )
    }
}
