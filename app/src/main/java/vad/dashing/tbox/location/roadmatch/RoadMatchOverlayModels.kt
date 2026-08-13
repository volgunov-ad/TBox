package vad.dashing.tbox.location.roadmatch

/**
 * Phase F1 — map-agnostic overlay model for the road-match map widget.
 * F2 (MapKit) renders this state; F1 does not depend on Yandex SDK.
 */
data class OverlayLatLon(
    val lat: Double,
    val lon: Double,
)

/** Placemark with optional travel / nose bearing (°). */
data class OverlayPoseMarker(
    val lat: Double,
    val lon: Double,
    val bearingDeg: Float? = null,
    val visible: Boolean = false,
)

/** Polyline for one road edge (WGS84 points in pack order). */
data class OverlayEdgePolyline(
    val edgeId: Long,
    val regionId: String,
    val highwayClass: String,
    val points: List<OverlayLatLon>,
)

/**
 * Camera follow hint for F2: center on shadow; keep GNSS in frame when present.
 */
data class OverlayCameraHint(
    val centerLat: Double,
    val centerLon: Double,
    /** Include GNSS point in bbox when [gnssVisible]. */
    val includeGnss: Boolean = false,
    val paddingM: Double = 40.0,
    val minZoom: Float = 14f,
    val maxZoom: Float = 18f,
)

/**
 * Snapshot consumed by [RoadMatchMapRenderer]. Pure data — no MapKit types.
 */
data class RoadMatchOverlayState(
    val active: Boolean = false,
    val shadow: OverlayPoseMarker = OverlayPoseMarker(0.0, 0.0),
    val gnss: OverlayPoseMarker = OverlayPoseMarker(0.0, 0.0),
    val matchedEdge: OverlayEdgePolyline? = null,
    /** Nearby edges for context; already capped by [RoadMatchOverlayBuilder]. */
    val neighborEdges: List<OverlayEdgePolyline> = emptyList(),
    val camera: OverlayCameraHint? = null,
    /**
     * Why overlays are empty / partial: `disabled`, `no_graph`, `no_edge`,
     * `no_pose`, or null when healthy.
     */
    val fallbackReason: String? = null,
    val matchConfidence: String? = null,
    val matchConnected: Boolean? = null,
) {
    companion object {
        val EMPTY = RoadMatchOverlayState(fallbackReason = "disabled")
    }
}

/**
 * Host-agnostic renderer contract. F2 implements with MapKit MapObjectCollection;
 * tests can use a recording stub.
 */
interface RoadMatchMapRenderer {
    fun render(state: RoadMatchOverlayState)
    fun clear()
}
