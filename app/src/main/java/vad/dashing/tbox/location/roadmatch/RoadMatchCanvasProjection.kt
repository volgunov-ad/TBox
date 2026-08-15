package vad.dashing.tbox.location.roadmatch

import kotlin.math.cos

/** Map-agnostic projection used by the F2a Canvas widget and unit tests. */
data class OverlayCanvasPoint(val x: Float, val y: Float)

data class RoadMatchCanvasViewport(
    val centerLat: Double,
    val centerLon: Double,
    /** Half-width in local east metres. */
    val halfWidthM: Double,
    /** Half-height in local north metres. */
    val halfHeightM: Double,
) {
    fun project(lat: Double, lon: Double): OverlayCanvasPoint {
        val eastM = (lon - centerLon) * METRES_PER_DEG *
            cos(Math.toRadians(centerLat))
        val northM = (lat - centerLat) * METRES_PER_DEG
        return OverlayCanvasPoint(
            x = ((eastM / halfWidthM + 1.0) * 0.5).toFloat(),
            y = ((1.0 - northM / halfHeightM) * 0.5).toFloat(),
        )
    }

    companion object {
        const val METRES_PER_DEG = 111_320.0
    }
}

object RoadMatchCanvasProjection {
    const val MIN_HALF_SPAN_M = 70.0
    const val MAX_HALF_SPAN_M = 280.0
    private const val FIT_PADDING = 1.25
    /** Fit nearby road geometry so the map shows context around the green shadow. */
    const val EDGE_FIT_RADIUS_M = 220.0

    /**
     * Keeps the shadow at viewport center and zooms for nearby GNSS / road context.
     * Distant bogus GNSS is capped; matched/neighbor edges only contribute within
     * [EDGE_FIT_RADIUS_M] so a long wrong polyline cannot collapse the camera.
     */
    fun viewport(state: RoadMatchOverlayState, aspectRatio: Float): RoadMatchCanvasViewport? {
        if (!state.shadow.visible) return null
        val centerLat = state.shadow.lat
        val centerLon = state.shadow.lon
        val cosLat = cos(Math.toRadians(centerLat)).coerceAtLeast(0.1)
        var maxEast = 0.0
        var maxNorth = 0.0

        fun include(lat: Double, lon: Double) {
            if (!lat.isFinite() || !lon.isFinite()) return
            val east = kotlin.math.abs((lon - centerLon) * RoadMatchCanvasViewport.METRES_PER_DEG * cosLat)
            val north = kotlin.math.abs((lat - centerLat) * RoadMatchCanvasViewport.METRES_PER_DEG)
            maxEast = maxOf(maxEast, east)
            maxNorth = maxOf(maxNorth, north)
        }

        fun includeIfNear(lat: Double, lon: Double, radiusM: Double) {
            if (!lat.isFinite() || !lon.isFinite()) return
            val east = kotlin.math.abs((lon - centerLon) * RoadMatchCanvasViewport.METRES_PER_DEG * cosLat)
            val north = kotlin.math.abs((lat - centerLat) * RoadMatchCanvasViewport.METRES_PER_DEG)
            if (east > radiusM || north > radiusM) return
            maxEast = maxOf(maxEast, east)
            maxNorth = maxOf(maxNorth, north)
        }

        if (state.gnss.visible) include(state.gnss.lat, state.gnss.lon)
        state.matchedEdge?.points?.forEach { includeIfNear(it.lat, it.lon, EDGE_FIT_RADIUS_M) }
        state.neighborEdges.forEach { edge ->
            edge.points.forEach { includeIfNear(it.lat, it.lon, EDGE_FIT_RADIUS_M) }
        }

        val safeAspect = aspectRatio.takeIf { it.isFinite() && it > 0.1f } ?: 1f
        val halfHeight = maxOf(
            MIN_HALF_SPAN_M,
            maxNorth * FIT_PADDING,
            maxEast * FIT_PADDING / safeAspect,
        ).coerceAtMost(MAX_HALF_SPAN_M)
        return viewportAt(centerLat, centerLon, halfHeight, safeAspect)
    }

    /** Fixed-span viewport for F3 set-mode (user pan / pinch-zoom). */
    fun viewportAt(
        centerLat: Double,
        centerLon: Double,
        halfHeightM: Double,
        aspectRatio: Float,
    ): RoadMatchCanvasViewport {
        val safeAspect = aspectRatio.takeIf { it.isFinite() && it > 0.1f } ?: 1f
        val halfHeight = halfHeightM.takeIf { it.isFinite() && it > 0.0 } ?: MIN_HALF_SPAN_M
        return RoadMatchCanvasViewport(
            centerLat = centerLat,
            centerLon = centerLon,
            halfWidthM = (halfHeight * safeAspect).coerceAtLeast(MIN_HALF_SPAN_M),
            halfHeightM = halfHeight,
        )
    }
}
