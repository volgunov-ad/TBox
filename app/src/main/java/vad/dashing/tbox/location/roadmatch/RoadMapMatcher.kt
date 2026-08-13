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
     * Faster bearing catch-up toward a matched edge when not in a turn.
     * Still faded by residual and fully inhibited at [BEARING_INHIBIT_RESIDUAL_DEG]
     * / [turnActive] — does not fight the kinematic standstill lock (match only
     * runs while moving).
     */
    const val MAX_BEARING_STEP_EDGE_CATCHUP_DEG = 14f
    /**
     * When |heading − edgeAzimuth| exceeds this, do not blend bearing toward the edge.
     * Lateral snap still runs. Stops the “old edge pulls heading through a turn” failure mode
     * (especially [RoadMatchRuntime] HOLD_EDGE).
     */
    const val BEARING_INHIBIT_RESIDUAL_DEG = 28f
    const val BEAM_WIDTH = 5
    /** Keep projecting onto the last edge while within this cross-track. */
    const val HOLD_PREVIOUS_RADIUS_M = 24.0
    /** Stronger than the generic beam bonus: CAN travel predicts this connected edge next. */
    const val TOPOLOGY_LOOK_AHEAD_BONUS = -6.0
    /**
     * Soft metres-equivalent penalty when travel is against OSM `oneway` on
     * ordinary roads (not a hard reject — OSM errors / temporary schemes /
     * reverse gear). Link ramps (`*_link`) are hard-rejected instead.
     */
    const val ONEWAY_AGAINST_PENALTY = 18.0
    /** Extra disconnected-jump cost when the candidate is a slip road / ramp. */
    const val DISCONNECTED_LINK_PENALTY = 20.0
    /**
     * Endpoints within this distance count as a junction even across tile graphs
     * (bundle tiles share `regionId` but adjacency is per-tile).
     */
    const val JUNCTION_ENDPOINT_CONNECT_M = 12.0
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

    data class TopologyAnchor(
        val regionId: String,
        val edgeId: Long,
        val alongTrackM: Double,
        val travelAgainstCoords: Boolean,
    )

    data class TopologyPrediction(
        val anchor: TopologyAnchor,
        val edge: RoadEdge,
        val lat: Double,
        val lon: Double,
        val azimuthDeg: Float,
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
        topologyLookAheadEdgeIds: Set<Pair<String, Long>> = emptySet(),
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
                val isLink = RoadHighwayClass.isLink(edge.highwayClass)
                // Forward travel onto a one-way link against its direction is almost
                // never a valid exit handoff (field: MKAD ramp accepted againstOneway).
                if (againstOneway && isLink && !allowAgainstOneway) continue

                val sameEdge = previousEdgeId != null &&
                    previousRegionId == g.regionId &&
                    edge.id == previousEdgeId
                val connected = isConnectedFromPrevious(
                    graphs = graphs,
                    previousEdgeId = previousEdgeId,
                    previousRegionId = previousRegionId,
                    candidate = edge,
                    candidateRegionId = g.regionId,
                )
                val inBeam = hypothesisEdgeIds.contains(g.regionId to edge.id)
                val isTopologyExpected = topologyLookAheadEdgeIds.contains(g.regionId to edge.id)

                var score = proj.crossTrackM + align * 0.35
                score += RoadHighwayClass.scorePenalty(edge.highwayClass)
                score += RoadHighwayClass.transitionPenalty(previousHighwayClass, edge.highwayClass)
                when {
                    sameEdge -> score += SAME_EDGE_BONUS
                    connected -> score += CONNECTED_BONUS
                    previousEdgeId != null -> {
                        score += DISCONNECTED_PENALTY
                        if (isLink) score += DISCONNECTED_LINK_PENALTY
                    }
                }
                if (previousEdgeId != null && !sameEdge && previousRegionId == g.regionId) {
                    score += SWITCH_PENALTY
                }
                if (inBeam && !sameEdge) {
                    score -= 1.0
                }
                if (isTopologyExpected && !sameEdge) {
                    score += TOPOLOGY_LOOK_AHEAD_BONUS
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

    /**
     * Advances a matched position by CAN/DR path length, crossing only connected endpoints.
     * At junctions the gyro/steer-derived [targetBearingDeg] selects the outgoing branch.
     */
    fun advanceAlongTopology(
        graphs: List<RoadGraph>,
        start: TopologyAnchor,
        distanceM: Double,
        targetBearingDeg: Float,
        allowAgainstOneway: Boolean = false,
        maxHops: Int = 8,
    ): TopologyPrediction? {
        if (!distanceM.isFinite() || distanceM < 0.0) return null
        var edge = findEdgeAcrossGraphs(graphs, start.regionId, start.edgeId) ?: return null
        var along = start.alongTrackM.coerceIn(0.0, polylineLengthM(edge))
        var against = start.travelAgainstCoords
        var remaining = distanceM
        val visited = linkedSetOf(edge.id)

        repeat(maxHops + 1) {
            val length = polylineLengthM(edge)
            val available = if (against) along else length - along
            if (remaining <= available + 0.05) {
                val targetAlong = if (against) along - remaining else along + remaining
                val point = pointAtAlong(edge, targetAlong.coerceIn(0.0, length)) ?: return null
                val azimuth = if (against) normalizeDeg(point.azimuthDeg + 180f) else point.azimuthDeg
                return TopologyPrediction(
                    anchor = TopologyAnchor(start.regionId, edge.id, targetAlong, against),
                    edge = edge,
                    lat = point.lat,
                    lon = point.lon,
                    azimuthDeg = azimuth,
                )
            }

            remaining -= available.coerceAtLeast(0.0)
            val endpointIndex = if (against) 0 else edge.pointCount - 1
            if (endpointIndex < 0) return null
            val endpointLat = edge.latAt(endpointIndex)
            val endpointLon = edge.lonAt(endpointIndex)
            val next = connectedOutgoingEdges(
                graphs = graphs,
                regionId = start.regionId,
                previous = edge,
                endpointLat = endpointLat,
                endpointLon = endpointLon,
                targetBearingDeg = targetBearingDeg,
                allowAgainstOneway = allowAgainstOneway,
                visited = visited,
            ).firstOrNull() ?: return null
            edge = next.first
            against = next.second
            along = if (against) polylineLengthM(edge) else 0.0
            visited.add(edge.id)
        }
        return null
    }

    private fun connectedOutgoingEdges(
        graphs: List<RoadGraph>,
        regionId: String,
        previous: RoadEdge,
        endpointLat: Double,
        endpointLon: Double,
        targetBearingDeg: Float,
        allowAgainstOneway: Boolean,
        visited: Set<Long>,
    ): List<Pair<RoadEdge, Boolean>> {
        val unique = linkedMapOf<Long, RoadEdge>()
        for (g in graphs) {
            if (g.regionId != regionId) continue
            for (id in g.neighbors(previous.id)) {
                g.edgeById[id]?.let { unique.putIfAbsent(id, it) }
            }
            for (edge in g.edgesNear(endpointLat, endpointLon, JUNCTION_ENDPOINT_CONNECT_M)) {
                if (edge.id != previous.id && endpointsNear(previous, edge, JUNCTION_ENDPOINT_CONNECT_M)) {
                    unique.putIfAbsent(edge.id, edge)
                }
            }
        }
        return unique.values.mapNotNull { edge ->
            if (edge.pointCount < 2) return@mapNotNull null
            val last = edge.pointCount - 1
            val startDist = RoadGraph.haversineM(endpointLat, endpointLon, edge.latAt(0), edge.lonAt(0))
            val endDist = RoadGraph.haversineM(endpointLat, endpointLon, edge.latAt(last), edge.lonAt(last))
            val against = endDist < startDist
            if (minOf(startDist, endDist) > JUNCTION_ENDPOINT_CONNECT_M) return@mapNotNull null
            if (!allowAgainstOneway && isAgainstOneway(edge.oneway, against)) return@mapNotNull null
            val length = polylineLengthM(edge)
            val sampleAlong = if (against) (length - 2.0).coerceAtLeast(0.0) else 2.0.coerceAtMost(length)
            val sample = pointAtAlong(edge, sampleAlong) ?: return@mapNotNull null
            val azimuth = if (against) normalizeDeg(sample.azimuthDeg + 180f) else sample.azimuthDeg
            val uTurnPenalty = if (edge.id in visited) 180f else 0f
            Triple(edge, against, smallestAngleDeg(targetBearingDeg, azimuth) + uTurnPenalty)
        }.sortedBy { it.third }.map { it.first to it.second }
    }

    private fun polylineLengthM(edge: RoadEdge): Double {
        var total = 0.0
        for (i in 0 until edge.pointCount - 1) {
            total += RoadGraph.haversineM(
                edge.latAt(i), edge.lonAt(i), edge.latAt(i + 1), edge.lonAt(i + 1),
            )
        }
        return total
    }

    private fun pointAtAlong(edge: RoadEdge, alongTrackM: Double): Projection? {
        if (edge.pointCount < 2) return null
        val target = alongTrackM.coerceAtLeast(0.0)
        var before = 0.0
        for (i in 0 until edge.pointCount - 1) {
            val lat1 = edge.latAt(i)
            val lon1 = edge.lonAt(i)
            val lat2 = edge.latAt(i + 1)
            val lon2 = edge.lonAt(i + 1)
            val segment = RoadGraph.haversineM(lat1, lon1, lat2, lon2)
            if (target <= before + segment || i == edge.pointCount - 2) {
                val t = if (segment < 1e-6) 0.0 else ((target - before) / segment).coerceIn(0.0, 1.0)
                val meanLat = Math.toRadians((lat1 + lat2) / 2.0)
                val dx = (lon2 - lon1) * 111_320.0 * cos(meanLat)
                val dy = (lat2 - lat1) * 111_320.0
                return Projection(
                    lat = lat1 + (lat2 - lat1) * t,
                    lon = lon1 + (lon2 - lon1) * t,
                    crossTrackM = 0.0,
                    alongTrackM = before + segment * t,
                    azimuthDeg = normalizeDeg(Math.toDegrees(atan2(dx, dy)).toFloat()),
                )
            }
            before += segment
        }
        return null
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
     * Connectivity for scoring: same edge, pack adjacency inside any loaded tile that
     * holds both ids, or spatial endpoint junction across tiles / pack seams.
     */
    fun isConnectedFromPrevious(
        graphs: List<RoadGraph>,
        previousEdgeId: Long?,
        previousRegionId: String?,
        candidate: RoadEdge,
        candidateRegionId: String,
    ): Boolean {
        if (previousEdgeId == null) return true
        if (previousRegionId == candidateRegionId && previousEdgeId == candidate.id) return true

        for (g in graphs) {
            if (!g.edgeById.containsKey(previousEdgeId)) continue
            if (!g.edgeById.containsKey(candidate.id)) continue
            // Prefer same-region tiles; still allow if both edges live in one graph.
            if (previousRegionId != null &&
                g.regionId != previousRegionId &&
                g.regionId != candidateRegionId
            ) {
                continue
            }
            if (g.isConnected(previousEdgeId, candidate.id)) return true
        }

        val previous = findEdgeAcrossGraphs(graphs, previousRegionId, previousEdgeId)
            ?: return false
        return endpointsNear(previous, candidate, JUNCTION_ENDPOINT_CONNECT_M)
    }

    private fun findEdgeAcrossGraphs(
        graphs: List<RoadGraph>,
        regionId: String?,
        edgeId: Long,
    ): RoadEdge? {
        if (regionId != null) {
            for (g in graphs) {
                if (g.regionId != regionId) continue
                g.edgeById[edgeId]?.let { return it }
            }
        }
        for (g in graphs) {
            g.edgeById[edgeId]?.let { return it }
        }
        return null
    }

    private fun endpointsNear(a: RoadEdge, b: RoadEdge, maxM: Double): Boolean {
        if (a.pointCount < 2 || b.pointCount < 2) return false
        val aEnds = listOf(
            a.latAt(0) to a.lonAt(0),
            a.latAt(a.pointCount - 1) to a.lonAt(a.pointCount - 1),
        )
        val bEnds = listOf(
            b.latAt(0) to b.lonAt(0),
            b.latAt(b.pointCount - 1) to b.lonAt(b.pointCount - 1),
        )
        for ((alat, alon) in aEnds) {
            for ((blat, blon) in bEnds) {
                if (RoadGraph.haversineM(alat, alon, blat, blon) <= maxM) return true
            }
        }
        return false
    }

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
        // Against-oneway (non-link soft survivors) never get apply-grade confidence
        // while moving forward — treat as ambiguous DR.
        if (best.againstOneway) return RoadMatchConfidence.LOW
        val gap = if (ranked.size >= 2) ranked[1].score - best.score else 50.0
        val connectedOk = best.connectedFromPrevious
        return when {
            // Sole plausible candidate — trust it out to ~30 m when connected.
            ranked.size == 1 && best.crossTrackM <= 30.0 && connectedOk -> {
                if (best.crossTrackM <= 15.0) RoadMatchConfidence.HIGH else RoadMatchConfidence.MEDIUM
            }
            best.crossTrackM <= 12.0 && gap >= 2.5 && connectedOk -> RoadMatchConfidence.HIGH
            best.crossTrackM <= 20.0 && gap >= 2.0 && connectedOk -> RoadMatchConfidence.MEDIUM
            // Sticky: already on a connected edge, even if runner-up is close.
            best.crossTrackM <= 22.0 && connectedOk && gap >= 0.8 -> RoadMatchConfidence.MEDIUM
            // Disconnected sole/clear winners stay LOW — field MKAD exit jumped onto
            // an unrelated primary_link because this used to return MEDIUM.
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
        // Toward a matched edge (not mid-turn) catch up heading faster than steady DR.
        val maxStepCap = if (!turnActive) {
            MAX_BEARING_STEP_EDGE_CATCHUP_DEG
        } else {
            MAX_BEARING_STEP_DEG
        }
        val maxBearingStep = maxStepCap * residualFade
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
