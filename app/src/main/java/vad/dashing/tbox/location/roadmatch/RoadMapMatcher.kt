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
    /**
     * Keep the sticky edge in the beam through a U-turn / mid-circle heading
     * swing (`151302` 15:17:31: 5 м от `16410`, курс 72° > 65° → `no_candidate`).
     */
    const val SAME_EDGE_HEADING_TOLERANCE_DEG = 180.0
    /**
     * Next circulating chord is often 70–100° off the current heading while the
     * car is already on it (`151302` 15:14:11 `13814` dH 77°).
     */
    const val CIRCULATING_HEADING_TOLERANCE_DEG = 110.0
    /** Fraction of cross-track error removed per successful match. */
    const val CROSS_BLEND = 0.40
    const val MAX_CROSS_STEP_M = 2.5
    /** Cap per softCorrect step; kept modest so sticky edges cannot yank heading. */
    const val MAX_BEARING_STEP_DEG = 6f
    /**
     * Faster bearing catch-up toward a matched edge. Faded by residual unless
     * [softCorrect] `catchUpHeading` is set (confirmed switch / on-edge, not leaving).
     */
    const val MAX_BEARING_STEP_EDGE_CATCHUP_DEG = 14f
    /**
     * When |heading − edgeAzimuth| exceeds this, do not blend bearing toward the edge
     * unless [softCorrect] `catchUpHeading` is set. Lateral snap still runs.
     * Stops the “old edge pulls heading through a turn” failure mode
     * (especially [RoadMatchRuntime] HOLD_EDGE). Confirmed switches catch up
     * even above this residual — gyro often undershoots the new road.
     */
    const val BEARING_INHIBIT_RESIDUAL_DEG = 28f
    /**
     * Same-edge heading is treated as “leaving this road” when residual grew by
     * more than this versus the previous tick.
     */
    const val HEADING_AWAY_EPS_DEG = 2f
    /**
     * Same-edge residual at which a heading moving away from the edge inhibits
     * bearing pull (early turn, before [RoadMatchRuntime] `dueTurn`).
     */
    const val LEAVING_EDGE_RESIDUAL_DEG = 12f
    /**
     * DR yaw (previous applied heading → this pose) vs pull-toward-edge: if both
     * exceed this and have opposite signs, do not catch up. Field 143430: gyro/steer
     * turned off the old motorway while catch-up froze mock heading on that edge.
     * Quiet gyro (124442 undershoot) stays below this and still catches up.
     */
    const val SENSOR_OPPOSE_MIN_DEG = 1.5f
    const val BEAM_WIDTH = 5
    /** Keep projecting onto the last edge while within this cross-track. */
    const val HOLD_PREVIOUS_RADIUS_M = 24.0
    /** Refuse a yard-to-yard (or onto-yard) switch this far beside the new street. */
    const val PARALLEL_YARD_XT_M = 12.0
    /**
     * Connected `*_link` that is still almost straight — early ramp on a
     * through-road (`145353` 14:49). Metres-equivalent; hard reject is separate.
     */
    const val UNHINTED_LINK_PENALTY = 8.0
    /**
     * Below this speed a connected ramp may be a real city exit from a stop
     * (`161559` 16:25). Highway false locks (`145353` 14:49) are faster.
     */
    const val UNHINTED_LINK_MIN_SPEED_KMH = 35f
    /** Stronger than the generic beam bonus: CAN travel predicts this connected edge next. */
    const val TOPOLOGY_LOOK_AHEAD_BONUS = -6.0
    /**
     * Max along-track catch-up per softCorrect on an unambiguous road.
     * Cross-track snap stays separate; this only closes odometer lag along the edge.
     */
    const val MAX_ALONG_STEP_M = 2.0
    /**
     * Rank-lag floor (m). [matchLagMeters] is 1 s of travel, clamped to
     * [[MATCH_LAG_MIN_M], [MATCH_LAG_MAX_M]]. Floor keeps the city fork case
     * (36 km/h, ~8 m past the node, heading still straight) from regressing.
     */
    const val MATCH_LAG_MIN_M = 10.0
    const val MATCH_LAG_MAX_M = 30.0
    const val MATCH_LAG_SECONDS = 1.0
    /** @see MATCH_LAG_MIN_M */
    const val MATCH_LAG_M = MATCH_LAG_MIN_M
    /** Do not lag until the trail is at least this long. */
    const val MATCH_LAG_MIN_TRAIL_M = 2.0
    /**
     * First lock: oneway with this much cross-track and along-track already
     * past the entry is “ahead of the car”, not on the ramp
     * (field `132038` edge `48261` xt 31 m / along 75 m).
     */
    const val FIRST_LOCK_AHEAD_ONEWAY_XT_M = 25.0
    const val FIRST_LOCK_AHEAD_ALONG_M = 20.0
    /** First lock: do not snap onto a courtyard this far beside the car. */
    const val FIRST_LOCK_YARD_XT_M = 15.0
    const val FIRST_LOCK_AHEAD_ONEWAY_PENALTY = 20.0
    const val FIRST_LOCK_YARD_PENALTY = 12.0

    /** Metres to rank behind the live pose. [speedKmh] from CAN / accounting. */
    fun matchLagMeters(speedKmh: Float): Double {
        if (!speedKmh.isFinite() || speedKmh <= 0f) return MATCH_LAG_MIN_M
        return (speedKmh / 3.6 * MATCH_LAG_SECONDS).toDouble()
            .coerceIn(MATCH_LAG_MIN_M, MATCH_LAG_MAX_M)
    }
    /**
     * Stalk hint at a fork: a connected candidate must differ from travel by at least
     * this many degrees in the signal direction, or the hint is ignored
     * (lane-change / early slip-road still ~straight).
     */
    const val TURN_SIGNAL_TOWARD_MIN_DEG = 25f
    /** Shallow parallel exits on highway when turn signal is intentional. */
    const val TURN_SIGNAL_HIGHWAY_INTENT_TOWARD_MIN_DEG = 12f
    /** |rel| below this is "straight through" when a real toward-candidate exists. */
    const val TURN_SIGNAL_STRAIGHT_DEG = 18f
    const val TURN_SIGNAL_TOWARD_BONUS = -5.0
    /** Strong pull onto a gentle ramp when highway profile + intentional stalk. */
    const val TURN_SIGNAL_HIGHWAY_INTENT_TOWARD_BONUS = -18.0
    const val TURN_SIGNAL_STRAIGHT_PENALTY = 8.0
    const val TURN_SIGNAL_HIGHWAY_INTENT_STRAIGHT_PENALTY = 14.0
    /**
     * On a circulating bent oneway arc every exit is geometrically "right".
     * Keep a light ranking nudge; do not use full bonus/penalty.
     */
    const val TURN_SIGNAL_ARC_WEIGHT = 0.35
    /**
     * Short oneway polyline whose heading already bends this much is a
     * roundabout / gyratory arc: every exit looks "toward" a Right stalk.
     * Dual-carriageway segments are longer or nearly straight — full hint stays on.
     */
    const val BENT_ONEWAY_ARC_MIN_BEND_DEG = 35f
    const val BENT_ONEWAY_ARC_MAX_LENGTH_M = 120.0
    /**
     * OSM splits a roundabout at every exit into short nearly-straight chords
     * (`151302` `13820` 22 м, bend 0). Those must still count as circulating.
     * Dual-carriageway pieces are usually longer — they stay on the bend rule.
     */
    const val BENT_ONEWAY_ARC_SHORT_CHORD_M = 45.0
    /**
     * Soft metres-equivalent penalty when travel is against OSM `oneway` on
     * ordinary roads (not a hard reject — OSM errors / temporary schemes /
     * reverse gear). Link ramps (`*_link`) are hard-rejected instead.
     */
    const val ONEWAY_AGAINST_PENALTY = 18.0
    /**
     * When a with-flow carriageway lies within this cross-track, against-oneway
     * candidates are dropped from the rank beam so dual-carriageway wrong-lane
     * stickiness cannot beat same-edge / connected bonuses
     * (field `095245` 10:17–10:19 — against ~16 vs parallel ~32–34).
     */
    const val PARALLEL_CORRECT_MAX_XT_M = 40.0
    /**
     * Legacy metres-equivalent demotion kept for tests/docs; ranking now
     * **filters** against-oneway when a with-flow parallel exists instead of
     * only adding this to score.
     */
    const val PARALLEL_CORRECT_AGAINST_EXTRA = 28.0
    /** Extra disconnected-jump cost when the candidate is a slip road / ramp. */
    const val DISCONNECTED_LINK_PENALTY = 20.0
    /**
     * Endpoints within this distance count as a junction even across tile graphs
     * (bundle tiles share `regionId` but adjacency is per-tile).
     * If the travel end already has a same-pack-node successor, this fallback
     * must not attach a nearby different-node exit (NN ring `20617`→`20623`).
     */
    const val JUNCTION_ENDPOINT_CONNECT_M = 12.0
    /**
     * Along-track distance to the travel-direction endpoint that still counts as
     * "at / past the end" of the polyline.
     */
    const val PAST_END_ALONG_EPS_M = 3.0
    /**
     * Cross-track (really: distance to the clamped endpoint) at which an overshoot
     * past the polyline end must not snap back toward that vertex.
     */
    const val PAST_END_XT_RELEASE_M = 8.0
    /** Additional release when endpoint-distance grows while already at the end. */
    const val PAST_END_XT_GROWTH_M = 1.5
    /**
     * Pose-from-endpoint bearing must stay within this of travel azimuth to count
     * as along-track overshoot rather than a wide lateral miss at the last vertex.
     */
    const val PAST_END_ALIGN_DEG = 55f
    private const val DISCONNECTED_PENALTY = 12.0
    private const val CONNECTED_BONUS = -2.5
    private const val SAME_EDGE_BONUS = -4.5
    private const val SWITCH_PENALTY = 1.0

    /** Left/right stalk only — hazard is not a matcher hint. */
    enum class TurnHint { Left, Right }

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
        /** True when travel matches B→A (opposite of coords A→B). */
        val travelAgainstCoords: Boolean = false,
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
        val confidence = confidenceOf(ranked, firstLock = previousEdgeId == null)
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
        turnHint: TurnHint? = null,
        turnIntent: Boolean = false,
        roadProfile: RoadMatchRoadProfile = RoadMatchRoadProfile.CITY,
        /** Widen connected-heading gate on a circulating ring hop. */
        circulatingManeuver: Boolean = false,
    ): List<Candidate> {
        val out = ArrayList<Candidate>(32)
        val minToward = turnSignalTowardMinDeg(roadProfile, turnIntent)
        for (g in graphs) {
            val near = g.edgesNear(pose.lat, pose.lon, CANDIDATE_RADIUS_M)
            for (edge in near) {
                val proj = projectOntoEdge(pose.lat, pose.lon, edge) ?: continue
                val headingDelta = smallestAngleDeg(pose.bearingDeg, proj.azimuthDeg)
                val reverseDelta = smallestAngleDeg(pose.bearingDeg, normalizeDeg(proj.azimuthDeg + 180f))
                val useReverse = reverseDelta < headingDelta
                val align = if (useReverse) reverseDelta else headingDelta
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
                if (align > headingToleranceDeg(edge, sameEdge, connected, circulatingManeuver)) {
                    continue
                }
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
                if (isLink &&
                    !sameEdge &&
                    previousHighwayClass != null &&
                    !RoadHighwayClass.isLink(previousHighwayClass) &&
                    !linkTurnEvidence(
                        headingDeltaDeg = align.toDouble(),
                        connected = connected,
                        lookAhead = isTopologyExpected,
                        travelBearingDeg = pose.bearingDeg,
                        edgeAzimuthDeg = azimuth,
                        turnHint = turnHint,
                        turnIntent = turnIntent,
                        minTowardDeg = minToward,
                    )
                ) {
                    score += UNHINTED_LINK_PENALTY
                }
                if (againstOneway && !allowAgainstOneway) {
                    score += ONEWAY_AGAINST_PENALTY
                }
                if (previousEdgeId == null) {
                    val alongFromEntry = if (useReverse) {
                        (edge.lengthM - proj.alongTrackM).coerceAtLeast(0.0)
                    } else {
                        proj.alongTrackM
                    }
                    if (edge.oneway != 0 &&
                        proj.crossTrackM >= FIRST_LOCK_AHEAD_ONEWAY_XT_M &&
                        alongFromEntry >= FIRST_LOCK_AHEAD_ALONG_M
                    ) {
                        score += FIRST_LOCK_AHEAD_ONEWAY_PENALTY
                    }
                    if (RoadHighwayClass.isCourtyardLike(edge.highwayClass) &&
                        proj.crossTrackM > FIRST_LOCK_YARD_XT_M
                    ) {
                        score += FIRST_LOCK_YARD_PENALTY
                    }
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
                        travelAgainstCoords = useReverse,
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
        val demoted = demoteAgainstWhenParallelCorrect(
            unique.values,
            allowAgainstOneway = allowAgainstOneway,
        )
        val ranked = demoted.sortedBy { it.score }
        return if (ranked.size <= limit) ranked else ranked.subList(0, limit)
    }

    /**
     * Dual carriageway / split one-ways: if travel is against OSM oneway but a
     * with-flow major road sits nearby, drop the against candidates so the
     * matcher prefers the correct carriageway instead of riding the median strip
     * on the opposite lane. Sole against-oneway (no parallel) is unchanged.
     */
    fun demoteAgainstWhenParallelCorrect(
        candidates: Collection<Candidate>,
        allowAgainstOneway: Boolean,
        maxXtM: Double = PARALLEL_CORRECT_MAX_XT_M,
        @Suppress("UNUSED_PARAMETER") extraPenalty: Double = PARALLEL_CORRECT_AGAINST_EXTRA,
    ): List<Candidate> {
        if (allowAgainstOneway || candidates.isEmpty()) return candidates.toList()
        if (!hasWithFlowParallelCorrect(candidates, maxXtM)) return candidates.toList()
        val kept = candidates.filter { !it.againstOneway }
        // Safety: never empty the beam if filtering removed everything.
        return kept.ifEmpty { candidates.toList() }
    }

    /** True when a non-against major carriageway is within [maxXtM] cross-track. */
    fun hasWithFlowParallelCorrect(
        candidates: Collection<Candidate>,
        maxXtM: Double = PARALLEL_CORRECT_MAX_XT_M,
    ): Boolean = candidates.any { cand ->
        !cand.againstOneway &&
            cand.crossTrackM <= maxXtM &&
            isParallelCorrectClass(cand.edge.highwayClass)
    }

    /**
     * Roads that count as the "correct" side of a dual carriageway / split
     * primary. Yards and service tracks are ignored so against-primary does not
     * jump into a courtyard just because it is with-flow.
     */
    fun isParallelCorrectClass(highwayClass: String): Boolean {
        val c = RoadHighwayClass.normalize(highwayClass)
        return c.startsWith("motorway") ||
            c.startsWith("trunk") ||
            c.startsWith("primary") ||
            c.startsWith("secondary") ||
            c.startsWith("tertiary")
    }

    fun isTurnSignalToward(
        travelBearingDeg: Float,
        edgeAzimuthDeg: Float,
        hint: TurnHint,
        minTowardDeg: Float = TURN_SIGNAL_TOWARD_MIN_DEG,
    ): Boolean {
        val rel = signedAngleDeg(travelBearingDeg, edgeAzimuthDeg)
        val minDeg = minTowardDeg.coerceAtLeast(1f)
        return when (hint) {
            TurnHint.Left -> rel <= -minDeg
            TurnHint.Right -> rel >= minDeg
        }
    }

    fun turnSignalTowardMinDeg(
        roadProfile: RoadMatchRoadProfile,
        turnIntent: Boolean,
    ): Float =
        if (roadProfile == RoadMatchRoadProfile.HIGHWAY && turnIntent) {
            TURN_SIGNAL_HIGHWAY_INTENT_TOWARD_MIN_DEG
        } else {
            TURN_SIGNAL_TOWARD_MIN_DEG
        }

    fun turnSignalTowardExists(
        ranked: List<Candidate>,
        travelBearingDeg: Float,
        hint: TurnHint,
        minTowardDeg: Float = TURN_SIGNAL_TOWARD_MIN_DEG,
    ): Boolean = ranked.any { cand ->
        cand.connectedFromPrevious &&
            isTurnSignalToward(travelBearingDeg, cand.edgeAzimuthDeg, hint, minTowardDeg)
    }

    /**
     * Evidence that the car is actually taking this slip road, not just
     * passing a connected ramp on a straight through-road.
     * Comfort 3-blink ([turnIntent] false) must not count as stalk evidence.
     */
    fun linkTurnEvidence(
        headingDeltaDeg: Double,
        connected: Boolean,
        lookAhead: Boolean,
        travelBearingDeg: Float,
        edgeAzimuthDeg: Float,
        turnHint: TurnHint?,
        turnIntent: Boolean = false,
        minTowardDeg: Float = TURN_SIGNAL_TOWARD_MIN_DEG,
    ): Boolean {
        if (!connected) return false
        if (headingDeltaDeg >= TURN_SIGNAL_TOWARD_MIN_DEG) return true
        if (lookAhead) return true
        if (turnIntent &&
            turnHint != null &&
            isTurnSignalToward(travelBearingDeg, edgeAzimuthDeg, turnHint, minTowardDeg)
        ) {
            return true
        }
        return false
    }

    /** First lock / already-on-link / hinted connected ramp may become sticky. */
    fun canCommitLink(
        cand: Candidate,
        previousHighwayClass: String?,
        travelBearingDeg: Float,
        turnHint: TurnHint?,
        topologyLookAheadEdgeIds: Set<Pair<String, Long>>,
        speedKmh: Float = 0f,
        turnIntent: Boolean = false,
        roadProfile: RoadMatchRoadProfile = RoadMatchRoadProfile.CITY,
    ): Boolean {
        if (!RoadHighwayClass.isLink(cand.edge.highwayClass)) return true
        if (previousHighwayClass.isNullOrBlank()) return true
        if (RoadHighwayClass.isLink(previousHighwayClass)) return true
        if (speedKmh.isFinite() && speedKmh < UNHINTED_LINK_MIN_SPEED_KMH) return true
        val headingDelta = smallestAngleDeg(travelBearingDeg, cand.edgeAzimuthDeg).toDouble()
        val minToward = turnSignalTowardMinDeg(roadProfile, turnIntent)
        return linkTurnEvidence(
            headingDeltaDeg = headingDelta,
            connected = cand.connectedFromPrevious,
            lookAhead = topologyLookAheadEdgeIds.contains(cand.regionId to cand.edge.id),
            travelBearingDeg = travelBearingDeg,
            edgeAzimuthDeg = cand.edgeAzimuthDeg,
            turnHint = turnHint,
            turnIntent = turnIntent,
            minTowardDeg = minToward,
        )
    }

    /**
     * High-xt jump onto a courtyard-like street: parallel neighbour after a
     * slow yard turn (`161651` 16:18), not a real connected handoff.
     */
    fun isParallelYardSwitch(
        cand: Candidate,
        previousHighwayClass: String?,
        travelBearingDeg: Float,
    ): Boolean {
        if (previousHighwayClass.isNullOrBlank()) return false
        if (!RoadHighwayClass.isCourtyardLike(cand.edge.highwayClass)) return false
        if (cand.crossTrackM < PARALLEL_YARD_XT_M) return false
        val aligned = smallestAngleDeg(travelBearingDeg, cand.edgeAzimuthDeg) <=
            TURN_SIGNAL_STRAIGHT_DEG
        // Only the parallel neighbour: heading already matches the other street.
        // A real yard corner has a large heading delta — do not block that.
        return aligned
    }

    /** Cumulative heading change along [edge] polyline (consecutive segment azimuths). */
    fun polylineBendDeg(edge: RoadEdge): Float {
        if (edge.pointCount < 3) return 0f
        var bend = 0f
        var prevAz: Float? = null
        for (i in 0 until edge.pointCount - 1) {
            val az = segmentAzimuthDeg(
                edge.lonAt(i), edge.latAt(i),
                edge.lonAt(i + 1), edge.latAt(i + 1),
            )
            val last = prevAz
            if (last != null) {
                bend += smallestAngleDeg(last, az)
            }
            prevAz = az
        }
        return bend
    }

    /**
     * Circulating roundabout arc: oneway and either already bent or a short
     * OSM chord (ring split at every exit). Stalk fork-hint must not run at
     * full weight here — every exit is geometrically "right".
     */
    fun isBentOnewayArc(edge: RoadEdge): Boolean {
        if (edge.oneway == 0) return false
        if (RoadHighwayClass.isLink(edge.highwayClass)) return false
        if (!(edge.lengthM.isFinite()) || edge.lengthM > BENT_ONEWAY_ARC_MAX_LENGTH_M) {
            return false
        }
        if (edge.lengthM <= BENT_ONEWAY_ARC_SHORT_CHORD_M) return true
        return polylineBendDeg(edge) >= BENT_ONEWAY_ARC_MIN_BEND_DEG
    }

    fun headingToleranceDeg(
        edge: RoadEdge,
        sameEdge: Boolean,
        connected: Boolean,
        circulatingManeuver: Boolean = false,
    ): Double {
        if (sameEdge) return SAME_EDGE_HEADING_TOLERANCE_DEG
        if (connected && (circulatingManeuver || isBentOnewayArc(edge))) {
            return CIRCULATING_HEADING_TOLERANCE_DEG
        }
        return HEADING_TOLERANCE_DEG
    }

    /**
     * If the rank winner is not an immediate successor of [previous], promote
     * a connected successor that is already in [ranked] (forbid A→C skips
     * while B is still a viable next chord).
     */
    fun preferImmediateSuccessor(
        ranked: List<Candidate>,
        graphs: List<RoadGraph>,
        previous: RoadEdge?,
        previousRegionId: String?,
        travelAgainstCoords: Boolean,
        travelBearingDeg: Float,
        allowAgainstOneway: Boolean,
    ): List<Candidate> {
        if (ranked.isEmpty() || previous == null || previousRegionId == null) return ranked
        val outgoing = outgoingAtTravelEnd(
            graphs = graphs,
            regionId = previousRegionId,
            previous = previous,
            travelAgainstCoords = travelAgainstCoords,
            targetBearingDeg = travelBearingDeg,
            allowAgainstOneway = allowAgainstOneway,
        ).map { it.first.id }.toSet()
        if (outgoing.isEmpty()) return ranked
        val best = ranked.first()
        if (best.edge.id == previous.id || best.edge.id in outgoing) return ranked
        val successor = ranked.firstOrNull { cand ->
            cand.edge.id in outgoing && cand.connectedFromPrevious
        } ?: return ranked
        return listOf(successor) + ranked.filter { it.edge.id != successor.edge.id }
    }

    fun outgoingAtTravelEnd(
        graphs: List<RoadGraph>,
        regionId: String,
        previous: RoadEdge,
        travelAgainstCoords: Boolean,
        targetBearingDeg: Float,
        allowAgainstOneway: Boolean,
        visited: Set<Long> = setOf(previous.id),
    ): List<Pair<RoadEdge, Boolean>> {
        if (previous.pointCount < 2) return emptyList()
        val endpointIndex = if (travelAgainstCoords) 0 else previous.pointCount - 1
        return connectedOutgoingEdges(
            graphs = graphs,
            regionId = regionId,
            previous = previous,
            endpointLat = previous.latAt(endpointIndex),
            endpointLon = previous.lonAt(endpointIndex),
            targetBearingDeg = targetBearingDeg,
            allowAgainstOneway = allowAgainstOneway,
            visited = visited,
        )
    }

    fun isImmediateSuccessor(
        graphs: List<RoadGraph>,
        previous: RoadEdge,
        previousRegionId: String,
        candidate: RoadEdge,
        travelAgainstCoords: Boolean,
        allowAgainstOneway: Boolean,
    ): Boolean {
        return outgoingAtTravelEnd(
            graphs = graphs,
            regionId = previousRegionId,
            previous = previous,
            travelAgainstCoords = travelAgainstCoords,
            targetBearingDeg = 0f,
            allowAgainstOneway = allowAgainstOneway,
        ).any { it.first.id == candidate.id }
    }

    /** Pose on [edge] at [alongTrackM] in the chosen travel direction. */
    fun poseOnEdge(
        regionId: String,
        edge: RoadEdge,
        alongTrackM: Double,
        travelAgainstCoords: Boolean,
    ): TopologyPrediction? {
        val length = polylineLengthM(edge)
        val along = alongTrackM.coerceIn(0.0, length)
        val point = pointAtAlong(edge, along) ?: return null
        val azimuth = if (travelAgainstCoords) {
            normalizeDeg(point.azimuthDeg + 180f)
        } else {
            point.azimuthDeg
        }
        return TopologyPrediction(
            anchor = TopologyAnchor(regionId, edge.id, along, travelAgainstCoords),
            edge = edge,
            lat = point.lat,
            lon = point.lon,
            azimuthDeg = azimuth,
        )
    }

    fun segmentAzimuthDeg(lon1: Double, lat1: Double, lon2: Double, lat2: Double): Float {
        val meanLat = Math.toRadians((lat1 + lat2) * 0.5)
        val dx = (lon2 - lon1) * 111_320.0 * cos(meanLat)
        val dy = (lat2 - lat1) * 111_320.0
        return normalizeDeg(Math.toDegrees(atan2(dx, dy)).toFloat())
    }

    /**
     * When a connected fork candidate already points the stalk way, penalize
     * straight-through successors (not the sticky edge) and bonus the turn.
     * No-op if nothing in [ranked] is a real toward-turn (lane change, early ramp).
     * Comfort 3-blink should pass [turnIntent]=false so this stays a no-op for ramps.
     */
    fun applyTurnSignalForkBias(
        ranked: List<Candidate>,
        travelBearingDeg: Float,
        hint: TurnHint,
        previousEdgeId: Long?,
        previousRegionId: String?,
        weight: Double = 1.0,
        turnIntent: Boolean = false,
        roadProfile: RoadMatchRoadProfile = RoadMatchRoadProfile.CITY,
    ): List<Candidate> {
        if (ranked.isEmpty() || weight == 0.0) return ranked
        if (!turnIntent) return ranked
        val minToward = turnSignalTowardMinDeg(roadProfile, turnIntent = true)
        if (!turnSignalTowardExists(ranked, travelBearingDeg, hint, minToward)) return ranked
        val scale = weight.coerceIn(0.0, 1.0)
        val towardBonus = if (roadProfile == RoadMatchRoadProfile.HIGHWAY) {
            TURN_SIGNAL_HIGHWAY_INTENT_TOWARD_BONUS
        } else {
            TURN_SIGNAL_TOWARD_BONUS
        }
        val straightPenalty = if (roadProfile == RoadMatchRoadProfile.HIGHWAY) {
            TURN_SIGNAL_HIGHWAY_INTENT_STRAIGHT_PENALTY
        } else {
            TURN_SIGNAL_STRAIGHT_PENALTY
        }
        return ranked.map { cand ->
            val rel = signedAngleDeg(travelBearingDeg, cand.edgeAzimuthDeg)
            val sameEdge = previousEdgeId != null &&
                cand.edge.id == previousEdgeId &&
                cand.regionId == previousRegionId
            val extra = when {
                isTurnSignalToward(travelBearingDeg, cand.edgeAzimuthDeg, hint, minToward) ->
                    towardBonus * scale
                !sameEdge && abs(rel) < TURN_SIGNAL_STRAIGHT_DEG ->
                    straightPenalty * scale
                else -> 0.0
            }
            if (extra == 0.0) cand else cand.copy(score = cand.score + extra)
        }.sortedBy { it.score }
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

    /**
     * Travel azimuth at the **end** of [edge] in the travel direction
     * (last ~2 m). Used by speed-limit lookahead, not GNSS heading.
     */
    fun travelEndAzimuthDeg(edge: RoadEdge, travelAgainstCoords: Boolean): Float? {
        val length = polylineLengthM(edge)
        if (length < 1e-6 || edge.pointCount < 2) return null
        val sampleAlong = if (travelAgainstCoords) {
            2.0.coerceAtMost(length)
        } else {
            (length - 2.0).coerceAtLeast(0.0)
        }
        val sample = pointAtAlong(edge, sampleAlong) ?: return null
        return if (travelAgainstCoords) normalizeDeg(sample.azimuthDeg + 180f) else sample.azimuthDeg
    }

    /** Travel azimuth in the first ~2 m of [edge] in the travel direction. */
    fun travelStartAzimuthDeg(edge: RoadEdge, travelAgainstCoords: Boolean): Float? {
        val length = polylineLengthM(edge)
        if (length < 1e-6 || edge.pointCount < 2) return null
        val sampleAlong = if (travelAgainstCoords) {
            (length - 2.0).coerceAtLeast(0.0)
        } else {
            2.0.coerceAtMost(length)
        }
        val sample = pointAtAlong(edge, sampleAlong) ?: return null
        return if (travelAgainstCoords) normalizeDeg(sample.azimuthDeg + 180f) else sample.azimuthDeg
    }

    data class StraightSuccessor(
        val edge: RoadEdge,
        val travelAgainstCoords: Boolean,
        val headingDeltaDeg: Float,
    )

    /**
     * Outgoing edges at the travel-direction endpoint whose heading vs the
     * current edge's travel-end azimuth is within [maxHeadingDeltaDeg].
     * Against-oneway outgoing is omitted unless [allowAgainstOneway].
     */
    fun straightSuccessors(
        graphs: List<RoadGraph>,
        regionId: String,
        edge: RoadEdge,
        travelAgainstCoords: Boolean,
        allowAgainstOneway: Boolean,
        visited: Set<Long>,
        maxHeadingDeltaDeg: Double = HEADING_TOLERANCE_DEG,
    ): List<StraightSuccessor> {
        if (edge.pointCount < 2) return emptyList()
        val endpointIndex = if (travelAgainstCoords) 0 else edge.pointCount - 1
        val travelAzimuth = travelEndAzimuthDeg(edge, travelAgainstCoords) ?: return emptyList()
        return connectedOutgoingEdges(
            graphs = graphs,
            regionId = regionId,
            previous = edge,
            endpointLat = edge.latAt(endpointIndex),
            endpointLon = edge.lonAt(endpointIndex),
            targetBearingDeg = travelAzimuth,
            allowAgainstOneway = allowAgainstOneway,
            visited = visited,
        ).mapNotNull { (next, against) ->
            if (next.id in visited) return@mapNotNull null
            val az = travelStartAzimuthDeg(next, against) ?: return@mapNotNull null
            val delta = smallestAngleDeg(travelAzimuth, az)
            if (delta > maxHeadingDeltaDeg) return@mapNotNull null
            StraightSuccessor(next, against, delta)
        }
    }

    /**
     * Forward neighbours at the travel-direction endpoint, excluding [edge] itself.
     * Count > 1 means a fork (delay successor commit until the lagged pose is
     * also past the node). Count == 1 is a simple OSM continuation.
     */
    fun forwardSuccessorCount(
        graphs: List<RoadGraph>,
        regionId: String,
        edge: RoadEdge,
        travelAgainstCoords: Boolean,
        allowAgainstOneway: Boolean,
    ): Int {
        if (edge.pointCount < 2) return 0
        val endpointIndex = if (travelAgainstCoords) 0 else edge.pointCount - 1
        return connectedOutgoingEdges(
            graphs = graphs,
            regionId = regionId,
            previous = edge,
            endpointLat = edge.latAt(endpointIndex),
            endpointLon = edge.lonAt(endpointIndex),
            targetBearingDeg = 0f,
            allowAgainstOneway = allowAgainstOneway,
            visited = setOf(edge.id),
        ).size
    }

    /** Reproject [cand]'s edge onto [pose] so ranking can use a lagged point. */
    fun candidateAtPose(pose: RoadMatchPose, cand: Candidate): Candidate {
        val proj = projectOntoEdge(pose.lat, pose.lon, cand.edge) ?: return cand
        val headingDelta = smallestAngleDeg(pose.bearingDeg, proj.azimuthDeg)
        val reverseDelta = smallestAngleDeg(pose.bearingDeg, normalizeDeg(proj.azimuthDeg + 180f))
        val useReverse = reverseDelta < headingDelta
        val azimuth = if (useReverse) normalizeDeg(proj.azimuthDeg + 180f) else proj.azimuthDeg
        return cand.copy(
            crossTrackM = proj.crossTrackM,
            alongTrackM = proj.alongTrackM,
            projLat = proj.lat,
            projLon = proj.lon,
            edgeAzimuthDeg = azimuth,
            travelAgainstCoords = useReverse,
            againstOneway = isAgainstOneway(cand.edge.oneway, useReverse),
        )
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
        val prevNode = nodeIdAtNearerEnd(previous, endpointLat, endpointLon)
        val scored = unique.values.mapNotNull { edge ->
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
            val entryNode = if (against) edge.toNode else edge.fromNode
            OutgoingChoice(
                edge, against,
                smallestAngleDeg(targetBearingDeg, azimuth) + uTurnPenalty,
                entryNode == prevNode,
            )
        }
        val preferred = if (scored.any { it.sameNode }) scored.filter { it.sameNode } else scored
        return preferred.sortedBy { it.angle }.map { it.edge to it.against }
    }

    private data class OutgoingChoice(
        val edge: RoadEdge,
        val against: Boolean,
        val angle: Float,
        val sameNode: Boolean,
    )

    private fun nodeIdAtNearerEnd(edge: RoadEdge, lat: Double, lon: Double): Int {
        if (edge.pointCount < 2) return edge.fromNode
        val start = RoadGraph.haversineM(lat, lon, edge.latAt(0), edge.lonAt(0))
        val end = RoadGraph.haversineM(
            lat, lon, edge.latAt(edge.pointCount - 1), edge.lonAt(edge.pointCount - 1),
        )
        return if (start <= end) edge.fromNode else edge.toNode
    }

    fun polylineLengthM(edge: RoadEdge): Double {
        var total = 0.0
        for (i in 0 until edge.pointCount - 1) {
            total += RoadGraph.haversineM(
                edge.latAt(i), edge.lonAt(i), edge.latAt(i + 1), edge.lonAt(i + 1),
            )
        }
        return total
    }

    internal fun pointAtAlong(edge: RoadEdge, alongTrackM: Double): Projection? {
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
        if (!endpointsNear(previous, candidate, JUNCTION_ENDPOINT_CONNECT_M)) return false
        if (sharePackNode(previous, candidate)) return true
        // 12 m seam is for broken packs / tile edges. A real same-node
        // successor already at that end must win over a nearby exit.
        return !hasPackNodeSuccessorAtNearbyEnd(graphs, previous, candidate)
    }

    private fun sharePackNode(a: RoadEdge, b: RoadEdge): Boolean =
        a.fromNode == b.fromNode || a.fromNode == b.toNode ||
            a.toNode == b.fromNode || a.toNode == b.toNode

    private fun hasPackNodeSuccessorAtNearbyEnd(
        graphs: List<RoadGraph>,
        previous: RoadEdge,
        candidate: RoadEdge,
    ): Boolean {
        if (previous.pointCount < 2 || candidate.pointCount < 2) return false
        val prevEnds = listOf(
            Triple(previous.latAt(0), previous.lonAt(0), previous.fromNode),
            Triple(
                previous.latAt(previous.pointCount - 1),
                previous.lonAt(previous.pointCount - 1),
                previous.toNode,
            ),
        )
        val candEnds = listOf(
            candidate.latAt(0) to candidate.lonAt(0),
            candidate.latAt(candidate.pointCount - 1) to candidate.lonAt(candidate.pointCount - 1),
        )
        for ((plat, plon, pnode) in prevEnds) {
            val near = candEnds.any { (clat, clon) ->
                RoadGraph.haversineM(plat, plon, clat, clon) <= JUNCTION_ENDPOINT_CONNECT_M
            }
            if (!near) continue
            for (g in graphs) {
                for (id in g.neighbors(previous.id)) {
                    if (id == candidate.id) continue
                    val other = g.edgeById[id] ?: continue
                    if (other.fromNode == pnode || other.toNode == pnode) return true
                }
            }
        }
        return false
    }

    internal fun findEdgeAcrossGraphs(
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

    /** True when [cand] projection is at the travel-direction polyline endpoint. */
    fun isAlongAtTravelEnd(cand: Candidate, epsM: Double = PAST_END_ALONG_EPS_M): Boolean {
        val length = polylineLengthM(cand.edge)
        return if (cand.travelAgainstCoords) {
            cand.alongTrackM <= epsM
        } else {
            cand.alongTrackM >= length - epsM
        }
    }

    /**
     * True when the pose has left the polyline past the travel-direction endpoint
     * (clamped projection = vertex, residual mostly along travel). Distinguishes
     * overshoot from a wide lateral miss at the last vertex.
     */
    fun isOvershootBeyondEnd(
        poseLat: Double,
        poseLon: Double,
        cand: Candidate,
        maxAlignDeg: Float = PAST_END_ALIGN_DEG,
    ): Boolean {
        if (!isAlongAtTravelEnd(cand)) return false
        if (cand.crossTrackM < 1.0) return false
        val brg = bearingBetweenDeg(cand.projLat, cand.projLon, poseLat, poseLon)
        return smallestAngleDeg(cand.edgeAzimuthDeg, brg) <= maxAlignDeg
    }

    fun alongFromOnewayEntryM(cand: Candidate): Double =
        if (cand.travelAgainstCoords) {
            (cand.edge.lengthM - cand.alongTrackM).coerceAtLeast(0.0)
        } else {
            cand.alongTrackM
        }

    /** First lock onto a oneway already far past its entry, off to the side. */
    fun isAheadOnOnewayFirstLock(cand: Candidate): Boolean {
        if (cand.edge.oneway == 0) return false
        if (cand.crossTrackM < FIRST_LOCK_AHEAD_ONEWAY_XT_M) return false
        return alongFromOnewayEntryM(cand) >= FIRST_LOCK_AHEAD_ALONG_M
    }

    fun isCourtyardSideFirstLock(cand: Candidate): Boolean =
        RoadHighwayClass.isCourtyardLike(cand.edge.highwayClass) &&
            cand.crossTrackM > FIRST_LOCK_YARD_XT_M

    fun confidenceOf(
        ranked: List<Candidate>,
        firstLock: Boolean = false,
    ): RoadMatchConfidence {
        val best = ranked.firstOrNull() ?: return RoadMatchConfidence.NONE
        if (firstLock && isAheadOnOnewayFirstLock(best)) return RoadMatchConfidence.LOW
        if (firstLock && isCourtyardSideFirstLock(best)) return RoadMatchConfidence.LOW
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
         * When true (turn trigger / steer intent), skip along-track catch-up.
         * Bearing blend is also skipped unless [catchUpHeading] is set.
         */
        turnActive: Boolean = false,
        /**
         * Optional graph-odometry target ahead of the free-DR projection. When set and
         * not mid-turn, the pose is gently pulled toward it along-track (≤ [MAX_ALONG_STEP_M]).
         */
        alongTargetLat: Double? = null,
        alongTargetLon: Double? = null,
        maxAlongStepM: Double = MAX_ALONG_STEP_M,
        /**
     * Confirmed match on the road we should follow (typically a switch, or
     * on-edge while not turning away). Pull heading toward the edge at the
     * catch-up cap even when residual exceeds [BEARING_INHIBIT_RESIDUAL_DEG]
     * or [turnActive] — gyro undershoot after a turn.
     * Runtime does not set this for a same-edge `*_link` (curving ramp chase).
     */
        catchUpHeading: Boolean = false,
        /** When false (leaving this road), do not pull lat/lon toward the edge. */
        lateralSnap: Boolean = true,
    ): RoadMatchPose {
        val residual = smallestAngleDeg(pose.bearingDeg, cand.edgeAzimuthDeg)
        val inhibitBearing = if (catchUpHeading) {
            false
        } else {
            turnActive || residual >= BEARING_INHIBIT_RESIDUAL_DEG
        }
        // Fade bearing pull as residual grows (full at 0°, none at inhibit threshold).
        // Catch-up after a confirmed match does not fade — otherwise a 25° leftover
        // after a switch only moves ~1°/tick and the pose walks sideways off the road.
        val residualFade = when {
            inhibitBearing -> 0f
            catchUpHeading -> 1f
            else -> (1f - residual / BEARING_INHIBIT_RESIDUAL_DEG).coerceIn(0f, 1f)
        }
        // Toward a matched edge catch up heading faster than steady DR.
        val maxStepCap = if (catchUpHeading || !turnActive) {
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
        var lat: Double
        var lon: Double
        val skipEndpointSnap = isOvershootBeyondEnd(pose.lat, pose.lon, cand) &&
            cross >= PAST_END_XT_RELEASE_M
        if (!lateralSnap || cross < 0.15 || skipEndpointSnap) {
            lat = pose.lat
            lon = pose.lon
        } else {
            val step = minOf(cross * CROSS_BLEND, MAX_CROSS_STEP_M)
            val t = (step / cross).coerceIn(0.0, 1.0)
            lat = pose.lat + (cand.projLat - pose.lat) * t
            lon = pose.lon + (cand.projLon - pose.lon) * t
        }
        if (!turnActive &&
            alongTargetLat != null &&
            alongTargetLon != null &&
            alongTargetLat.isFinite() &&
            alongTargetLon.isFinite() &&
            maxAlongStepM > 0.0
        ) {
            val alongDist = RoadGraph.haversineM(lat, lon, alongTargetLat, alongTargetLon)
            if (alongDist > 0.15) {
                // Only pull forward along travel — never rewind toward a target behind us.
                val targetBearing = bearingBetweenDeg(lat, lon, alongTargetLat, alongTargetLon)
                if (smallestAngleDeg(bearing, targetBearing) <= 70f) {
                    val alongStep = minOf(alongDist, maxAlongStepM)
                    val u = (alongStep / alongDist).coerceIn(0.0, 1.0)
                    lat += (alongTargetLat - lat) * u
                    lon += (alongTargetLon - lon) * u
                }
            }
        }
        return RoadMatchPose(lat = lat, lon = lon, bearingDeg = bearing)
    }

    /** Initial bearing from A to B in degrees [0, 360). */
    fun bearingBetweenDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val meanLat = Math.toRadians((lat1 + lat2) / 2.0)
        val dx = (lon2 - lon1) * 111_320.0 * cos(meanLat)
        val dy = (lat2 - lat1) * 111_320.0
        return normalizeDeg(Math.toDegrees(atan2(dx, dy)).toFloat())
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
