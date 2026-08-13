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

enum class RoadMatchConfidence {
    /** Clear winner — apply soft correction. */
    HIGH,
    /** Acceptable — apply soft correction. */
    MEDIUM,
    /** Ambiguous / weak — keep pure DR; only track hypotheses. */
    LOW,
    /** No usable candidate. */
    NONE,
}

data class RoadMatchResult(
    val pose: RoadMatchPose,
    val edgeId: Long,
    val regionId: String,
    val crossTrackM: Double,
    val alongTrackM: Double,
    val switchedEdge: Boolean,
    val edgeAzimuthDeg: Float,
    val confidence: RoadMatchConfidence,
    val candidateCount: Int,
    val runnerUpScore: Double?,
    val connectedFromPrevious: Boolean,
    val highwayClass: String,
)

/**
 * Offline road snap (Phase E): candidates in radius, heading gate, connectivity,
 * highway-class costs, soft lateral + bearing blend.
 * Longitudinal position along the edge is kept (project then move only cross-track).
 */
object RoadMapMatcher {
    const val CANDIDATE_RADIUS_M = 35.0
    const val HEADING_TOLERANCE_DEG = 65.0
    /** Fraction of cross-track error removed per successful match. */
    const val CROSS_BLEND = 0.40
    const val MAX_CROSS_STEP_M = 2.5
    /** Cap per softCorrect step; kept modest so sticky edges cannot yank heading. */
    const val MAX_BEARING_STEP_DEG = 6f
    /**
     * When |heading − edgeAzimuth| exceeds this, do not blend bearing toward the edge.
     * Lateral snap still runs. Stops the “old edge pulls heading through a turn” failure mode
     * (especially [RoadMatchRuntime] HOLD_EDGE).
     */
    const val BEARING_INHIBIT_RESIDUAL_DEG = 28f
    const val BEAM_WIDTH = 5
    /** Keep projecting onto the last edge while within this cross-track. */
    const val HOLD_PREVIOUS_RADIUS_M = 24.0
    /**
     * Soft metres-equivalent penalty when travel is against OSM `oneway`
     * (not a hard reject — OSM errors / temporary schemes / reverse gear).
     */
    const val ONEWAY_AGAINST_PENALTY = 18.0
    private const val DISCONNECTED_PENALTY = 12.0
    private const val CONNECTED_BONUS = -2.5
    private const val SAME_EDGE_BONUS = -4.5
    private const val SWITCH_PENALTY = 1.0

    data class Candidate(
        val edge: RoadEdge,
        val regionId: String,
        val crossTrackM: Double,
        val alongTrackM: Double,
        val projLat: Double,
        val projLon: Double,
        val edgeAzimuthDeg: Float,
        val score: Double,
        val connectedFromPrevious: Boolean,
        /** True when chosen travel direction conflicts with [RoadEdge.oneway]. */
        val againstOneway: Boolean = false,
    )

    fun match(
        pose: RoadMatchPose,
        graphs: List<RoadGraph>,
        previousEdgeId: Long?,
        previousRegionId: String?,
        previousHighwayClass: String? = null,
        hypothesisEdgeIds: Set<Pair<String, Long>> = emptySet(),
        allowAgainstOneway: Boolean = false,
    ): RoadMatchResult? {
        val ranked = rankCandidates(
            pose, graphs, previousEdgeId, previousRegionId, previousHighwayClass,
            hypothesisEdgeIds, allowAgainstOneway = allowAgainstOneway,
        )
        val best = ranked.firstOrNull() ?: return null
        val confidence = confidenceOf(ranked)
        if (confidence == RoadMatchConfidence.NONE || confidence == RoadMatchConfidence.LOW) {
            return null
        }
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
            confidence = confidence,
            candidateCount = ranked.size,
            runnerUpScore = ranked.getOrNull(1)?.score,
            connectedFromPrevious = best.connectedFromPrevious,
            highwayClass = best.edge.highwayClass,
        )
    }

    fun rankCandidates(
        pose: RoadMatchPose,
        graphs: List<RoadGraph>,
        previousEdgeId: Long?,
        previousRegionId: String?,
        previousHighwayClass: String? = null,
        hypothesisEdgeIds: Set<Pair<String, Long>> = emptySet(),
        limit: Int = BEAM_WIDTH,
        allowAgainstOneway: Boolean = false,
    ): List<Candidate> {
        val out = ArrayList<Candidate>(32)
        for (g in graphs) {
            val near = g.edgesNear(pose.lat, pose.lon, CANDIDATE_RADIUS_M)
            for (edge in near) {
                val proj = projectOntoEdge(pose.lat, pose.lon, edge) ?: continue
                val headingDelta = smallestAngleDeg(pose.bearingDeg, proj.azimuthDeg)
                val reverseDelta = smallestAngleDeg(pose.bearingDeg, normalizeDeg(proj.azimuthDeg + 180f))
                val useReverse = reverseDelta < headingDelta
                val align = if (useReverse) reverseDelta else headingDelta
                if (align > HEADING_TOLERANCE_DEG) continue
                val azimuth = if (useReverse) normalizeDeg(proj.azimuthDeg + 180f) else proj.azimuthDeg
                val againstOneway = isAgainstOneway(edge.oneway, travelAgainstCoords = useReverse)

                val sameEdge = previousEdgeId != null &&
                    previousRegionId == g.regionId &&
                    edge.id == previousEdgeId
                val connected = when {
                    previousEdgeId == null -> true
                    previousRegionId != g.regionId -> false
                    sameEdge -> true
                    else -> g.isConnected(previousEdgeId, edge.id)
                }
                val inBeam = hypothesisEdgeIds.contains(g.regionId to edge.id)

                var score = proj.crossTrackM + align * 0.35
                score += RoadHighwayClass.scorePenalty(edge.highwayClass)
                score += RoadHighwayClass.transitionPenalty(previousHighwayClass, edge.highwayClass)
                when {
                    sameEdge -> score += SAME_EDGE_BONUS
                    connected -> score += CONNECTED_BONUS
                    previousEdgeId != null -> score += DISCONNECTED_PENALTY
                }
                if (previousEdgeId != null && !sameEdge && previousRegionId == g.regionId) {
                    score += SWITCH_PENALTY
                }
                if (inBeam && !sameEdge) {
                    score -= 1.0
                }
                if (againstOneway && !allowAgainstOneway) {
                    score += ONEWAY_AGAINST_PENALTY
                }

                out.add(
                    Candidate(
                        edge = edge,
                        regionId = g.regionId,
                        crossTrackM = proj.crossTrackM,
                        alongTrackM = proj.alongTrackM,
                        projLat = proj.lat,
                        projLon = proj.lon,
                        edgeAzimuthDeg = azimuth,
                        score = score,
                        connectedFromPrevious = connected || previousEdgeId == null,
                        againstOneway = againstOneway,
                    ),
                )
            }
        }
        // Bundle tiles overlap by design, so one OSM edge may appear in 2–4 loaded
        // graphs. Keep one candidate per region/edge or the duplicate would look like
        // an equal-score runner-up and incorrectly lower confidence.
        val unique = LinkedHashMap<Pair<String, Long>, Candidate>(out.size)
        for (candidate in out) {
            val key = candidate.regionId to candidate.edge.id
            val previous = unique[key]
            if (previous == null || candidate.score < previous.score) {
                unique[key] = candidate
            }
        }
        val ranked = unique.values.sortedBy { it.score }
        return if (ranked.size <= limit) ranked else ranked.subList(0, limit)
    }

    fun pickBest(
        pose: RoadMatchPose,
        graphs: List<RoadGraph>,
        previousEdgeId: Long?,
        previousRegionId: String?,
        previousHighwayClass: String? = null,
        hypothesisEdgeIds: Set<Pair<String, Long>> = emptySet(),
        allowAgainstOneway: Boolean = false,
    ): Candidate? = rankCandidates(
        pose, graphs, previousEdgeId, previousRegionId, previousHighwayClass, hypothesisEdgeIds,
        allowAgainstOneway = allowAgainstOneway,
    ).firstOrNull()

    /**
     * @param travelAgainstCoords true when vehicle travel matches B→A (opposite of coords A→B).
     */
    fun isAgainstOneway(oneway: Int, travelAgainstCoords: Boolean): Boolean {
        return when (oneway) {
            1 -> travelAgainstCoords
            -1 -> !travelAgainstCoords
            else -> false
        }
    }

    fun confidenceOf(ranked: List<Candidate>): RoadMatchConfidence {
        val best = ranked.firstOrNull() ?: return RoadMatchConfidence.NONE
        if (best.crossTrackM > 32.0) return RoadMatchConfidence.LOW
        val gap = if (ranked.size >= 2) ranked[1].score - best.score else 50.0
        val connectedOk = best.connectedFromPrevious
        return when {
            // Sole plausible candidate — trust it out to ~30 m.
            ranked.size == 1 && best.crossTrackM <= 30.0 && connectedOk -> {
                if (best.crossTrackM <= 15.0) RoadMatchConfidence.HIGH else RoadMatchConfidence.MEDIUM
            }
            best.crossTrackM <= 12.0 && gap >= 2.5 && connectedOk -> RoadMatchConfidence.HIGH
            best.crossTrackM <= 20.0 && gap >= 2.0 && connectedOk -> RoadMatchConfidence.MEDIUM
            // Sticky: already on a connected edge, even if runner-up is close.
            best.crossTrackM <= 22.0 && connectedOk && gap >= 0.8 -> RoadMatchConfidence.MEDIUM
            best.crossTrackM <= 12.0 && gap >= 4.5 -> RoadMatchConfidence.MEDIUM
            else -> RoadMatchConfidence.LOW
        }
    }

    fun softCorrect(
        pose: RoadMatchPose,
        cand: Candidate,
        /**
         * When true (turn trigger / steer intent), skip bearing blend entirely.
         * Lateral snap still applies.
         */
        turnActive: Boolean = false,
    ): RoadMatchPose {
        val residual = smallestAngleDeg(pose.bearingDeg, cand.edgeAzimuthDeg)
        val inhibitBearing = turnActive || residual >= BEARING_INHIBIT_RESIDUAL_DEG
        // Fade bearing pull as residual grows (full at 0°, none at inhibit threshold).
        val residualFade = if (inhibitBearing) {
            0f
        } else {
            (1f - residual / BEARING_INHIBIT_RESIDUAL_DEG).coerceIn(0f, 1f)
        }
        val maxBearingStep = MAX_BEARING_STEP_DEG * residualFade
        val bearing = if (maxBearingStep <= 0.01f) {
            pose.bearingDeg
        } else {
            blendBearing(pose.bearingDeg, cand.edgeAzimuthDeg, maxBearingStep)
        }
        val cross = cand.crossTrackM
        if (cross < 0.15) {
            return pose.copy(bearingDeg = bearing)
        }
        val step = minOf(cross * CROSS_BLEND, MAX_CROSS_STEP_M)
        val t = (step / cross).coerceIn(0.0, 1.0)
        val lat = pose.lat + (cand.projLat - pose.lat) * t
        val lon = pose.lon + (cand.projLon - pose.lon) * t
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
                )
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
