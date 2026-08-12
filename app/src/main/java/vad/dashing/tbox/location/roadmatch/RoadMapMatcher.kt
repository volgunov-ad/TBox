package vad.dashing.tbox.location.roadmatch

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos

/** Shadow pose before / after a soft road-match correction. */
data class RoadMatchPose(
    val lat: Double,
    val lon: Double,
    /** Travel bearing degrees [0, 360), same convention as mock travel bearing. */
    val bearingDeg: Float,
)

data class RoadMatchResult(
    val pose: RoadMatchPose,
    val edgeId: Long,
    val regionId: String,
    val crossTrackM: Double,
    val alongTrackM: Double,
    val switchedEdge: Boolean,
    val edgeAzimuthDeg: Float,
)

/**
 * Offline road snap: candidates in radius, heading gate, soft lateral + bearing blend.
 * Longitudinal position along the edge is kept (project then move only cross-track).
 */
object RoadMapMatcher {
    const val CANDIDATE_RADIUS_M = 35.0
    const val HEADING_TOLERANCE_DEG = 65.0
    /** Fraction of cross-track error removed per successful match. */
    const val CROSS_BLEND = 0.40
    const val MAX_CROSS_STEP_M = 2.5
    const val MAX_BEARING_STEP_DEG = 10f

    data class Candidate(
        val edge: RoadEdge,
        val regionId: String,
        val crossTrackM: Double,
        val alongTrackM: Double,
        val projLat: Double,
        val projLon: Double,
        val edgeAzimuthDeg: Float,
        val score: Double,
    )

    fun match(
        pose: RoadMatchPose,
        graphs: List<RoadGraph>,
        previousEdgeId: Long?,
        previousRegionId: String?,
    ): RoadMatchResult? {
        val best = pickBest(pose, graphs, previousEdgeId, previousRegionId) ?: return null
        val switched = previousEdgeId != null &&
            (best.edge.id != previousEdgeId || best.regionId != previousRegionId)
        val corrected = softCorrect(pose, best)
        return RoadMatchResult(
            pose = corrected,
            edgeId = best.edge.id,
            regionId = best.regionId,
            crossTrackM = best.crossTrackM,
            alongTrackM = best.alongTrackM,
            switchedEdge = switched,
            edgeAzimuthDeg = best.edgeAzimuthDeg,
        )
    }

    fun pickBest(
        pose: RoadMatchPose,
        graphs: List<RoadGraph>,
        previousEdgeId: Long?,
        previousRegionId: String?,
    ): Candidate? {
        var best: Candidate? = null
        for (g in graphs) {
            if (!g.contains(pose.lat, pose.lon) && g.edgesNear(pose.lat, pose.lon, CANDIDATE_RADIUS_M).isEmpty()) {
                // Still query — contains is bbox; edgesNear pads.
            }
            val near = g.edgesNear(pose.lat, pose.lon, CANDIDATE_RADIUS_M)
            for (edge in near) {
                val proj = projectOntoEdge(pose.lat, pose.lon, edge) ?: continue
                val headingDelta = smallestAngleDeg(pose.bearingDeg, proj.azimuthDeg)
                val reverseDelta = smallestAngleDeg(pose.bearingDeg, normalizeDeg(proj.azimuthDeg + 180f))
                val useReverse = reverseDelta < headingDelta
                val align = if (useReverse) reverseDelta else headingDelta
                if (align > HEADING_TOLERANCE_DEG) continue
                val azimuth = if (useReverse) normalizeDeg(proj.azimuthDeg + 180f) else proj.azimuthDeg
                var score = proj.crossTrackM + align * 0.35
                if (previousEdgeId != null &&
                    previousRegionId == g.regionId &&
                    edge.id == previousEdgeId
                ) {
                    score -= 4.0 // stickiness
                } else if (previousEdgeId != null && previousRegionId == g.regionId) {
                    // slight penalty for switch unless clearly better
                    score += 1.5
                }
                val cand = Candidate(
                    edge = edge,
                    regionId = g.regionId,
                    crossTrackM = proj.crossTrackM,
                    alongTrackM = proj.alongTrackM,
                    projLat = proj.lat,
                    projLon = proj.lon,
                    edgeAzimuthDeg = azimuth,
                    score = score,
                )
                if (best == null || cand.score < best.score) {
                    best = cand
                }
            }
        }
        return best
    }

    fun softCorrect(pose: RoadMatchPose, cand: Candidate): RoadMatchPose {
        val cross = cand.crossTrackM
        if (cross < 0.15) {
            // Already on line — only nudge bearing.
            val bearing = blendBearing(pose.bearingDeg, cand.edgeAzimuthDeg, MAX_BEARING_STEP_DEG)
            return pose.copy(bearingDeg = bearing)
        }
        val step = minOf(cross * CROSS_BLEND, MAX_CROSS_STEP_M)
        val t = (step / cross).coerceIn(0.0, 1.0)
        val lat = pose.lat + (cand.projLat - pose.lat) * t
        val lon = pose.lon + (cand.projLon - pose.lon) * t
        val bearing = blendBearing(pose.bearingDeg, cand.edgeAzimuthDeg, MAX_BEARING_STEP_DEG)
        return RoadMatchPose(lat = lat, lon = lon, bearingDeg = bearing)
    }

    data class Projection(
        val lat: Double,
        val lon: Double,
        val crossTrackM: Double,
        val alongTrackM: Double,
        val azimuthDeg: Float,
    )

    fun projectOntoEdge(lat: Double, lon: Double, edge: RoadEdge): Projection? {
        val n = edge.pointCount
        if (n < 2) return null
        var bestDist = Double.POSITIVE_INFINITY
        var bestLat = lat
        var bestLon = lon
        var bestAlong = 0.0
        var bestAz = 0f
        var alongBefore = 0.0
        for (i in 0 until n - 1) {
            val lat1 = edge.latAt(i)
            val lon1 = edge.lonAt(i)
            val lat2 = edge.latAt(i + 1)
            val lon2 = edge.lonAt(i + 1)
            val segLen = RoadGraph.haversineM(lat1, lon1, lat2, lon2)
            val meanLat = Math.toRadians((lat + lat1 + lat2) / 3.0)
            val mPerDegLat = 111_320.0
            val mPerDegLon = 111_320.0 * cos(meanLat)
            val x = (lon - lon1) * mPerDegLon
            val y = (lat - lat1) * mPerDegLat
            val dx = (lon2 - lon1) * mPerDegLon
            val dy = (lat2 - lat1) * mPerDegLat
            val len2 = dx * dx + dy * dy
            val t = if (len2 < 1e-6) 0.0 else ((x * dx + y * dy) / len2).coerceIn(0.0, 1.0)
            val px = lon1 + (lon2 - lon1) * t
            val py = lat1 + (lat2 - lat1) * t
            val dist = RoadGraph.distanceToSegmentM(lat, lon, lat1, lon1, lat2, lon2)
            if (dist < bestDist) {
                bestDist = dist
                bestLon = px
                bestLat = py
                bestAlong = alongBefore + segLen * t
                bestAz = normalizeDeg(
                    Math.toDegrees(atan2(dx, dy)).toFloat(),
                ) // atan2(east, north) → bearing from north
            }
            alongBefore += segLen
        }
        return Projection(
            lat = bestLat,
            lon = bestLon,
            crossTrackM = bestDist,
            alongTrackM = bestAlong,
            azimuthDeg = bestAz,
        )
    }

    fun blendBearing(fromDeg: Float, toDeg: Float, maxStepDeg: Float): Float {
        val delta = signedAngleDeg(fromDeg, toDeg)
        val step = delta.coerceIn(-maxStepDeg, maxStepDeg)
        return normalizeDeg(fromDeg + step)
    }

    fun normalizeDeg(deg: Float): Float {
        var d = deg % 360f
        if (d < 0f) d += 360f
        return d
    }

    fun smallestAngleDeg(a: Float, b: Float): Float = abs(signedAngleDeg(a, b))

    fun signedAngleDeg(from: Float, to: Float): Float {
        var d = (to - from) % 360f
        if (d > 180f) d -= 360f
        if (d < -180f) d += 360f
        return d
    }
}
