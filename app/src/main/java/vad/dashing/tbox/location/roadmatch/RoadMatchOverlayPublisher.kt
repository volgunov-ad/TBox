package vad.dashing.tbox.location.roadmatch

/**
 * Builds and publishes the Phase F map overlay from the shared road-match runtime.
 */
object RoadMatchOverlayPublisher {

    fun publish(
        controller: RoadMatchController,
        matchEnabled: Boolean,
        shadowLat: Double,
        shadowLon: Double,
        shadowBearingDeg: Float?,
        gnssLat: Double? = null,
        gnssLon: Double? = null,
        gnssBearingDeg: Float? = null,
        gnssVisible: Boolean = false,
    ) {
        if (!matchEnabled) {
            RoadMatchOverlayRepository.clear()
            return
        }
        if (!shadowLat.isFinite() || !shadowLon.isFinite() ||
            shadowLat !in -90.0..90.0 || shadowLon !in -180.0..180.0 ||
            (shadowLat == 0.0 && shadowLon == 0.0)
        ) {
            RoadMatchOverlayRepository.clear()
            return
        }
        controller.warmGraphsAt(shadowLat, shadowLon)
        val state = RoadMatchOverlayBuilder.build(
            matchEnabled = true,
            shadowLat = shadowLat,
            shadowLon = shadowLon,
            shadowBearingDeg = shadowBearingDeg,
            gnssLat = gnssLat,
            gnssLon = gnssLon,
            gnssBearingDeg = gnssBearingDeg,
            gnssVisible = gnssVisible,
            debug = controller.runtime.debug,
            graphs = RoadGraphStore.cachedGraphs(),
        )
        RoadMatchOverlayRepository.publish(state)
    }
}
