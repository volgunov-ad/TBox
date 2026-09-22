package vad.dashing.tbox.speedcam

import vad.dashing.tbox.location.ConstantDrMath
import vad.dashing.tbox.location.roadmatch.RoadEdge
import vad.dashing.tbox.location.roadmatch.RoadGraph
import vad.dashing.tbox.location.roadmatch.RoadGraphStore
import vad.dashing.tbox.location.roadmatch.RoadMapMatcher
import vad.dashing.tbox.location.roadmatch.SpeedLimitLookahead
import java.util.Locale

/**
 * Picks the nearest relevant SpeedCam point ahead of travel within [radiusM].
 * Prefers along-road path when a match edge is available; otherwise bearing cone.
 */
object SpeedCamLookahead {
    /** Max heading delta to treat camera direction as matching travel. */
    const val DIR_MATCH_DEG = 60f
    /** Point must lie roughly ahead of the vehicle. */
    const val AHEAD_MAX_DEG = 90f
    /** Max lateral distance from the matched road path to accept a camera (m). */
    const val ROAD_CORRIDOR_M = 40.0

    data class RoadMatchHint(
        val graphs: List<RoadGraph>,
        val regionId: String?,
        val edgeId: Long?,
        val alongTrackM: Double?,
        val travelAgainstCoords: Boolean?,
        val allowAgainstOneway: Boolean = false,
    )

    fun findNearest(
        index: SpeedCamIndex?,
        lat: Double,
        lon: Double,
        bearingDeg: Float,
        radiusM: Int,
        vehicleSpeedKmh: Float,
        overageKmh: Int,
        roadHint: RoadMatchHint? = null,
    ): SpeedCamAlert? {
        if (index == null || index.size == 0) return null
        if (!lat.isFinite() || !lon.isFinite() || !bearingDeg.isFinite()) return null
        val radius = normalizeSpeedCamRadiusM(radiusM).toDouble()
        val alongRoad = roadHint?.let {
            findNearestAlongRoad(
                index = index,
                lat = lat,
                lon = lon,
                bearingDeg = bearingDeg,
                radiusM = radius,
                vehicleSpeedKmh = vehicleSpeedKmh,
                overageKmh = overageKmh,
                hint = it,
            )
        }
        if (alongRoad != null) return alongRoad
        return findNearestByBearing(
            index = index,
            lat = lat,
            lon = lon,
            bearingDeg = bearingDeg,
            radius = radius,
            vehicleSpeedKmh = vehicleSpeedKmh,
            overageKmh = overageKmh,
        )
    }

    private fun findNearestByBearing(
        index: SpeedCamIndex,
        lat: Double,
        lon: Double,
        bearingDeg: Float,
        radius: Double,
        vehicleSpeedKmh: Float,
        overageKmh: Int,
    ): SpeedCamAlert? {
        val candidates = index.query(lat, lon, radius)
        var best: SpeedCamPoint? = null
        var bestDist = Double.POSITIVE_INFINITY
        for (p in candidates) {
            val dist = ConstantDrMath.distanceMeters(lat, lon, p.lat, p.lon)
            if (!dist.isFinite() || dist > radius) continue
            val toCam = RoadMapMatcher.bearingBetweenDeg(lat, lon, p.lat, p.lon)
            if (RoadMapMatcher.smallestAngleDeg(bearingDeg, toCam) > AHEAD_MAX_DEG) continue
            if (!directionRelevant(bearingDeg, p)) continue
            if (dist < bestDist) {
                bestDist = dist
                best = p
            }
        }
        return best?.let { toAlert(it, bestDist, bearingDeg, vehicleSpeedKmh, overageKmh) }
    }

    /**
     * Walk the assumed-straight road path (same cone as [SpeedLimitLookahead]) and pick the
     * nearest camera within [ROAD_CORRIDOR_M] of that path, measured along-road.
     */
    private fun findNearestAlongRoad(
        index: SpeedCamIndex,
        lat: Double,
        lon: Double,
        bearingDeg: Float,
        radiusM: Double,
        vehicleSpeedKmh: Float,
        overageKmh: Int,
        hint: RoadMatchHint,
    ): SpeedCamAlert? {
        val edgeId = hint.edgeId ?: return null
        val along = hint.alongTrackM ?: return null
        val against = hint.travelAgainstCoords ?: return null
        if (!along.isFinite() || along < 0.0) return null
        val graphs = hint.graphs.ifEmpty { RoadGraphStore.cachedGraphs() }
        if (graphs.isEmpty()) return null
        val edge = RoadMapMatcher.findEdgeAcrossGraphs(graphs, hint.regionId, edgeId) ?: return null
        val regionId = hint.regionId
            ?: graphs.firstOrNull { it.edgeById.containsKey(edge.id) }?.regionId
            ?: return null
        val segments = collectPathSegments(
            graphs = graphs,
            regionId = regionId,
            edge = edge,
            alongTrackM = along,
            travelAgainstCoords = against,
            allowAgainstOneway = hint.allowAgainstOneway,
            maxDistanceM = radiusM,
        )
        if (segments.isEmpty()) return null
        val candidates = index.query(lat, lon, radiusM)
        var best: SpeedCamPoint? = null
        var bestAlong = Double.POSITIVE_INFINITY
        for (p in candidates) {
            if (!directionRelevant(bearingDeg, p)) continue
            val alongDist = alongPathDistanceM(p.lat, p.lon, segments) ?: continue
            if (alongDist < 0.0 || alongDist > radiusM) continue
            if (alongDist < bestAlong) {
                bestAlong = alongDist
                best = p
            }
        }
        return best?.let { toAlert(it, bestAlong, bearingDeg, vehicleSpeedKmh, overageKmh) }
    }

    private data class PathSegment(
        val edge: RoadEdge,
        /**
         * Along-path distance from the vehicle to the travel-direction start of this edge.
         * `0` for the current edge.
         */
        val pathToTravelStartM: Double,
        val travelAgainstCoords: Boolean,
        /** Vehicle along-track on the current edge; `null` for successor edges. */
        val vehicleAlongOnEdge: Double?,
    )

    private fun collectPathSegments(
        graphs: List<RoadGraph>,
        regionId: String,
        edge: RoadEdge,
        alongTrackM: Double,
        travelAgainstCoords: Boolean,
        allowAgainstOneway: Boolean,
        maxDistanceM: Double,
    ): List<PathSegment> {
        val length = RoadMapMatcher.polylineLengthM(edge)
        val along = alongTrackM.coerceIn(0.0, length)
        val remainingOnEdge = if (travelAgainstCoords) along else length - along
        val out = ArrayList<PathSegment>(8)
        out.add(
            PathSegment(
                edge = edge,
                pathToTravelStartM = 0.0,
                travelAgainstCoords = travelAgainstCoords,
                vehicleAlongOnEdge = along,
            ),
        )
        if (remainingOnEdge >= maxDistanceM) return out
        var distanceAtEnd = remainingOnEdge
        var current = edge
        var against = travelAgainstCoords
        val visited = linkedSetOf(edge.id)
        var hops = SpeedLimitLookahead.MAX_HOPS
        while (hops-- > 0 && distanceAtEnd < maxDistanceM) {
            val straight = RoadMapMatcher.straightSuccessors(
                graphs = graphs,
                regionId = regionId,
                edge = current,
                travelAgainstCoords = against,
                allowAgainstOneway = allowAgainstOneway,
                visited = visited,
                maxHeadingDeltaDeg = SpeedLimitLookahead.STRAIGHT_MAX_DEG,
            )
            if (straight.size != 1) break
            val next = straight[0]
            out.add(
                PathSegment(
                    edge = next.edge,
                    pathToTravelStartM = distanceAtEnd,
                    travelAgainstCoords = next.travelAgainstCoords,
                    vehicleAlongOnEdge = null,
                ),
            )
            visited.add(next.edge.id)
            distanceAtEnd += RoadMapMatcher.polylineLengthM(next.edge)
            current = next.edge
            against = next.travelAgainstCoords
        }
        return out
    }

    /**
     * @return along-path distance from the vehicle if the point is within [ROAD_CORRIDOR_M]
     * of some segment ahead; null otherwise.
     */
    private fun alongPathDistanceM(
        lat: Double,
        lon: Double,
        segments: List<PathSegment>,
    ): Double? {
        var bestCross = Double.POSITIVE_INFINITY
        var bestAlong: Double? = null
        for (seg in segments) {
            val proj = RoadMapMatcher.projectOntoEdge(lat, lon, seg.edge) ?: continue
            if (proj.crossTrackM > ROAD_CORRIDOR_M) continue
            val edgeLen = RoadMapMatcher.polylineLengthM(seg.edge)
            val alongOnEdge = proj.alongTrackM.coerceIn(0.0, edgeLen)
            val pathAlong = when {
                seg.vehicleAlongOnEdge != null && !seg.travelAgainstCoords ->
                    alongOnEdge - seg.vehicleAlongOnEdge
                seg.vehicleAlongOnEdge != null && seg.travelAgainstCoords ->
                    seg.vehicleAlongOnEdge - alongOnEdge
                !seg.travelAgainstCoords ->
                    seg.pathToTravelStartM + alongOnEdge
                else ->
                    seg.pathToTravelStartM + (edgeLen - alongOnEdge)
            }
            if (pathAlong < -1.0) continue
            if (proj.crossTrackM < bestCross ||
                (proj.crossTrackM == bestCross && (bestAlong == null || pathAlong < bestAlong))
            ) {
                bestCross = proj.crossTrackM
                bestAlong = pathAlong
            }
        }
        val along = bestAlong ?: return null
        return along.coerceAtLeast(0.0)
    }

    private fun toAlert(
        point: SpeedCamPoint,
        distanceM: Double,
        bearingDeg: Float,
        vehicleSpeedKmh: Float,
        overageKmh: Int,
    ): SpeedCamAlert {
        val relative = relativeDirection(bearingDeg, point.directionDeg)
        val overLimit = point.hasSpeedLimit &&
            vehicleSpeedKmh.isFinite() &&
            vehicleSpeedKmh > point.speedKmh + normalizeSpeedCamOverageKmh(overageKmh)
        return SpeedCamAlert(
            point = point,
            distanceM = distanceM,
            relative = relative,
            overLimit = overLimit,
        )
    }

    fun nearbyMarkers(
        index: SpeedCamIndex?,
        lat: Double,
        lon: Double,
        radiusM: Int,
        alertId: Int?,
    ): List<SpeedCamMapMarker> {
        if (index == null || index.size == 0) return emptyList()
        if (!lat.isFinite() || !lon.isFinite()) return emptyList()
        val radius = normalizeSpeedCamRadiusM(radiusM).toDouble()
        return index.query(lat, lon, radius).mapNotNull { p ->
            val dist = ConstantDrMath.distanceMeters(lat, lon, p.lat, p.lon)
            if (!dist.isFinite() || dist > radius) return@mapNotNull null
            SpeedCamMapMarker(
                lat = p.lat,
                lon = p.lon,
                category = p.category,
                speedKmh = p.speedKmh,
                isAlertTarget = alertId != null && p.id == alertId,
                dirType = p.dirType,
                directionDeg = p.directionDeg,
            )
        }
    }

    fun directionRelevant(travelBearingDeg: Float, point: SpeedCamPoint): Boolean {
        return when (point.dirType) {
            0 -> true
            1 -> RoadMapMatcher.smallestAngleDeg(
                travelBearingDeg,
                point.directionDeg.toFloat(),
            ) <= DIR_MATCH_DEG
            2 -> {
                val a = RoadMapMatcher.smallestAngleDeg(
                    travelBearingDeg,
                    point.directionDeg.toFloat(),
                )
                val b = RoadMapMatcher.smallestAngleDeg(
                    travelBearingDeg,
                    RoadMapMatcher.normalizeDeg(point.directionDeg + 180f),
                )
                a <= DIR_MATCH_DEG || b <= DIR_MATCH_DEG
            }
            else -> true
        }
    }

    fun relativeDirection(travelBearingDeg: Float, cameraDirectionDeg: Int): SpeedCamRelativeDirection {
        val delta = RoadMapMatcher.smallestAngleDeg(
            travelBearingDeg,
            cameraDirectionDeg.toFloat(),
        )
        return if (delta <= 90f) SpeedCamRelativeDirection.SAME else SpeedCamRelativeDirection.ONCOMING
    }

    fun formatDistanceM(distanceM: Double): String {
        if (!distanceM.isFinite() || distanceM < 0.0) return "—"
        return if (distanceM >= 1000.0) {
            String.format(Locale.US, "%.1f км", distanceM / 1000.0)
        } else {
            "${distanceM.toInt()} м"
        }
    }

    fun formatDistanceMEn(distanceM: Double): String {
        if (!distanceM.isFinite() || distanceM < 0.0) return "—"
        return if (distanceM >= 1000.0) {
            String.format(Locale.US, "%.1f km", distanceM / 1000.0)
        } else {
            "${distanceM.toInt()} m"
        }
    }
}
