package vad.dashing.tbox.location.roadmatch

import java.io.File

/**
 * Throttled road-match corrections for the DR shadow (Phase E).
 * Keeps a small beam of edge hypotheses; applies soft correction only when
 * confidence is MEDIUM/HIGH. Low confidence → pure DR (no pose nudge).
 */
class RoadMatchRuntime(
    private val mapsDir: () -> File,
    private val pathTriggerM: Double = 12.0,
    private val timeTriggerMs: Long = 2_000L,
    private val turnTriggerDeg: Float = DEFAULT_TURN_TRIGGER_DEG,
    private val minSpeedKmh: Float = 1.8f,
    private val switchConfirmCount: Int = 3,
    private val beamWidth: Int = RoadMapMatcher.BEAM_WIDTH,
    private val beamHoldMs: Long = 8_000L,
    private val holdPreviousRadiusM: Double = RoadMapMatcher.HOLD_PREVIOUS_RADIUS_M,
) {
    data class DebugSnapshot(
        val active: Boolean = false,
        /** Sticky / applied edge id (null when previous was released). */
        val edgeId: Long? = null,
        val regionId: String? = null,
        val crossTrackM: Double? = null,
        val alongTrackM: Double? = null,
        val switchedEdge: Boolean = false,
        val skippedReason: String? = null,
        val confidence: String? = null,
        val candidateCount: Int = 0,
        val runnerUpScore: Double? = null,
        val connected: Boolean? = null,
        /** Highway class of the sticky/applied edge. */
        val highwayClass: String? = null,
        val oneway: Int? = null,
        val againstOneway: Boolean? = null,
        /** Best-ranked candidate when different from [edgeId] (reject / pending paths). */
        val candidateEdgeId: Long? = null,
        val candidateHighwayClass: String? = null,
        val candidateConnected: Boolean? = null,
        val candidateCrossTrackM: Double? = null,
        /** Pose travel bearing fed into this match attempt (°). */
        val inputBearingDeg: Float? = null,
        /** Chosen / held edge travel azimuth (°). */
        val edgeBearingDeg: Float? = null,
        /** Applied softCorrect bearing delta (°); 0 when inhibited. */
        val bearingDeltaDeg: Float? = null,
        /** True when bearing pull is inhibited (HOLD / leaving / dueTurn / same-edge link). */
        val turnActive: Boolean? = null,
        /**
         * Why a switch/candidate was refused when pose was not corrected, e.g.
         * `against_oneway_link`, `disconnected_link`, `low_confidence`, `lost_hold`.
         */
        val rejectReason: String? = null,
    )

    companion object {
        /**
         * After a switch, block return to the previous edge for this long.
         * Short enough for a real U-turn to recover; long enough to ride out a junction.
         */
        const val RETURN_GUARD_MS = 5_000L
        /** Nearby rawBest within this cross-track can trigger a fresh rematch after lost hold. */
        const val REMATCH_NEAR_CROSS_M = 20.0
        /** Steady cruise path trigger (m) — constructor default. */
        const val STEADY_PATH_M = 12.0
        /** Steady cruise time trigger (ms) — constructor default. */
        const val STEADY_TIME_MS = 2_000L
        /** Faster path retry while recovering / no sticky edge. */
        const val RECOVER_PATH_M = 6.0
        /** Faster time retry while recovering / no sticky edge. */
        const val RECOVER_TIME_MS = 500L
        /** Path retry while waiting for switch confirmation. */
        const val SWITCH_PENDING_PATH_M = 5.0
        /** Time retry while waiting for switch confirmation. */
        const val SWITCH_PENDING_TIME_MS = 500L
        /** Default turn trigger (°); lower than old 25° so exits get an early match. */
        const val DEFAULT_TURN_TRIGGER_DEG = 18f
        /** Same-edge residual growth treated as leaving this road (heading inhibit). */
        const val HEADING_AWAY_EPS_DEG = RoadMapMatcher.HEADING_AWAY_EPS_DEG
        const val LEAVING_EDGE_RESIDUAL_DEG = RoadMapMatcher.LEAVING_EDGE_RESIDUAL_DEG
        const val LOOK_AHEAD_MIN_M = 10.0
        const val LOOK_AHEAD_MAX_M = 20.0
        const val LOOK_AHEAD_SECONDS = 1.5
        /** Short graph-only recovery; arbitrary nearby roads remain excluded. */
        const val CONNECTED_CORRIDOR_HOLD_MS = 5_000L
        const val CONNECTED_CORRIDOR_MAX_M = 60.0
    }

    @Volatile
    var debug: DebugSnapshot = DebugSnapshot()
        private set

    private var lastMatchElapsedMs: Long = 0L
    private var lastPoseLat: Double = 0.0
    private var lastPoseLon: Double = 0.0
    private var lastBearingDeg: Float = 0f
    private var hasLastPose: Boolean = false
    /** Heading at the previous tick, captured before this pose overwrites [lastBearingDeg]. */
    private var headingBeforeTickDeg: Float = 0f
    private var pathSinceMatchM: Double = 0.0
    private var currentEdgeId: Long? = null
    private var currentRegionId: String? = null
    private var currentHighwayClass: String? = null
    private var pendingEdgeId: Long? = null
    private var pendingRegionId: String? = null
    private var pendingWins: Int = 0
    /** Beam of (regionId, edgeId) hypotheses from the last ranking. */
    private var hypotheses: Set<Pair<String, Long>> = emptySet()
    private var hypothesesUntilElapsedMs: Long = 0L
    /** Last applied graph position; CAN path advances this anchor through connected edges. */
    private var topologyAnchor: RoadMapMatcher.TopologyAnchor? = null
    private var topologyAnchorElapsedMs: Long = 0L
    /**
     * Edge left by the last accepted switch. For a short dwell, refuse jumping
     * straight back (field: turn locks the exit, then oscillates onto the old road).
     */
    private var abandonedEdgeId: Long? = null
    private var abandonedRegionId: String? = null
    private var abandonGuardUntilElapsedMs: Long = 0L
    /**
     * After a weak / rejected attempt, prefer faster retries until the next
     * successful correction (recovering from phantom / LOW / pending switch).
     */
    private var preferFastRetry: Boolean = false
    /**
     * Last projection of the sticky edge while it was already at the travel end.
     * Used so a growing endpoint-distance can release before [PAST_END_XT_RELEASE_M].
     */
    private var lastPastEndEdgeId: Long? = null
    private var lastPastEndRegionId: String? = null
    private var lastPastEndXt: Double? = null
    /**
     * Sticky edge that already overshot its polyline end. Keep refusing HOLD/snap
     * until we switch away or the pose is clearly back on-edge.
     */
    private var exhaustedEdgeId: Long? = null
    private var exhaustedRegionId: String? = null
    private data class CachedBundleIndex(
        val lastModified: Long,
        val index: RoadMapBundleIndex,
    )
    private val bundleIndexes = mutableMapOf<String, CachedBundleIndex>()

    fun reset() {
        lastMatchElapsedMs = 0L
        hasLastPose = false
        pathSinceMatchM = 0.0
        currentEdgeId = null
        currentRegionId = null
        currentHighwayClass = null
        pendingEdgeId = null
        pendingRegionId = null
        pendingWins = 0
        hypotheses = emptySet()
        hypothesesUntilElapsedMs = 0L
        topologyAnchor = null
        topologyAnchorElapsedMs = 0L
        abandonedEdgeId = null
        abandonedRegionId = null
        abandonGuardUntilElapsedMs = 0L
        preferFastRetry = false
        lastPastEndEdgeId = null
        lastPastEndRegionId = null
        lastPastEndXt = null
        exhaustedEdgeId = null
        exhaustedRegionId = null
        debug = DebugSnapshot()
    }

    /**
     * @return corrected pose, or null if skipped / low confidence / no coverage
     * (caller keeps previous pose).
     */
    fun maybeCorrect(
        enabled: Boolean,
        pose: RoadMatchPose,
        speedKmh: Float,
        nowElapsedMs: Long,
        /** When true (e.g. reverse gear), do not penalize travel against OSM oneway. */
        allowAgainstOneway: Boolean = false,
    ): RoadMatchPose? {
        if (!enabled) {
            reset()
            debug = DebugSnapshot(skippedReason = "disabled")
            return null
        }
        if (speedKmh < minSpeedKmh) {
            debug = DebugSnapshot(
                active = currentEdgeId != null,
                edgeId = currentEdgeId,
                regionId = currentRegionId,
                confidence = if (currentEdgeId != null) "HOLD" else null,
                highwayClass = currentHighwayClass,
                skippedReason = "stationary",
            )
            return null
        }

        if (hasLastPose) {
            pathSinceMatchM += RoadGraph.haversineM(
                lastPoseLat, lastPoseLon, pose.lat, pose.lon,
            )
        }
        val dtMs = if (lastMatchElapsedMs > 0L) nowElapsedMs - lastMatchElapsedMs else Long.MAX_VALUE
        val turn = if (hasLastPose) {
            RoadMapMatcher.smallestAngleDeg(lastBearingDeg, pose.bearingDeg)
        } else {
            0f
        }
        val pathLimitM = activePathTriggerM()
        val timeLimitMs = activeTimeTriggerMs()
        val duePath = pathSinceMatchM >= pathLimitM
        val dueTime = dtMs >= timeLimitMs
        val dueTurn = turn >= turnTriggerDeg
        headingBeforeTickDeg = if (hasLastPose) lastBearingDeg else pose.bearingDeg
        lastPoseLat = pose.lat
        lastPoseLon = pose.lon
        lastBearingDeg = pose.bearingDeg
        hasLastPose = true
        if (lastMatchElapsedMs > 0L && !duePath && !dueTime && !dueTurn) {
            debug = DebugSnapshot(
                active = currentEdgeId != null,
                edgeId = currentEdgeId,
                regionId = currentRegionId,
                confidence = if (currentEdgeId != null) "HOLD" else null,
                highwayClass = currentHighwayClass,
                skippedReason = "throttled",
            )
            return null
        }

        val graphs = loadInstalledGraphs(pose.lat, pose.lon)
        if (graphs.isEmpty()) {
            debug = DebugSnapshot(skippedReason = "no_graph")
            markAttempt(pose, nowElapsedMs)
            preferFastRetry = true
            return null
        }

        val corrected = matchOnce(
            pose = pose,
            graphs = graphs,
            speedKmh = speedKmh,
            nowElapsedMs = nowElapsedMs,
            dueTurn = dueTurn,
            allowAgainstOneway = allowAgainstOneway,
            allowRematchAfterLostHold = true,
        )
        // Successful apply clears the fast-retry latch; rejects keep it for denser attempts.
        preferFastRetry = corrected == null
        return corrected
    }

    private fun activePathTriggerM(): Double = when {
        pendingEdgeId != null -> SWITCH_PENDING_PATH_M
        preferFastRetry || currentEdgeId == null -> RECOVER_PATH_M
        else -> pathTriggerM
    }

    private fun activeTimeTriggerMs(): Long = when {
        pendingEdgeId != null -> SWITCH_PENDING_TIME_MS
        preferFastRetry || currentEdgeId == null -> RECOVER_TIME_MS
        else -> timeTriggerMs
    }

    /**
     * One ranking + apply/hold/reject pass. When [allowRematchAfterLostHold] is true and the
     * sticky previous edge can no longer be held, clears it and retries once without the
     * disconnected penalty that was blocking nearby roads on interchanges.
     */
    private fun matchOnce(
        pose: RoadMatchPose,
        graphs: List<RoadGraph>,
        speedKmh: Float,
        nowElapsedMs: Long,
        dueTurn: Boolean,
        allowAgainstOneway: Boolean,
        allowRematchAfterLostHold: Boolean,
    ): RoadMatchPose? {
        val topologyExpected = topologyPrediction(
            graphs = graphs,
            distanceM = pathSinceMatchM + lookAheadDistanceM(speedKmh),
            bearingDeg = pose.bearingDeg,
            allowAgainstOneway = allowAgainstOneway,
        )?.let { setOf(it.anchor.regionId to it.anchor.edgeId) }.orEmpty()
        val ranked = RoadMapMatcher.rankCandidates(
            pose = pose,
            graphs = graphs,
            previousEdgeId = currentEdgeId,
            previousRegionId = currentRegionId,
            previousHighwayClass = currentHighwayClass,
            hypothesisEdgeIds = activeHypotheses(nowElapsedMs),
            limit = beamWidth,
            allowAgainstOneway = allowAgainstOneway,
            topologyLookAheadEdgeIds = topologyExpected,
        )
        if (ranked.isNotEmpty()) {
            hypotheses = ranked.map { it.regionId to it.edge.id }.toSet()
            hypotheses = hypotheses + topologyExpected
            // Keep current edge in the beam so stickiness survives brief gaps / weaving.
            if (currentEdgeId != null && currentRegionId != null) {
                hypotheses = hypotheses + (currentRegionId!! to currentEdgeId!!)
            }
            // During a turn, hold the beam longer so a short opposite weave does not
            // drop the connected exit hypothesis.
            val holdMs = if (dueTurn) beamHoldMs * 2L else beamHoldMs
            hypothesesUntilElapsedMs = nowElapsedMs + holdMs
        } else if (nowElapsedMs > hypothesesUntilElapsedMs) {
            hypotheses = emptySet()
        }

        val pastEndDecision = handlePastEndRelease(
            pose = pose,
            graphs = graphs,
            ranked = ranked,
            nowElapsedMs = nowElapsedMs,
            dueTurn = dueTurn,
            allowAgainstOneway = allowAgainstOneway,
        )
        if (pastEndDecision is PastEndDecision.Done) {
            return pastEndDecision.pose
        }

        if (ranked.isEmpty()) {
            val held = holdPreviousEdge(
                pose, graphs, dueTurn = dueTurn, allowAgainstOneway = allowAgainstOneway,
            )
            if (held != null) {
                return applyCandidate(
                    pose = pose,
                    cand = held,
                    confidence = "HOLD_EDGE",
                    candidateCount = 0,
                    runnerUpScore = null,
                    nowElapsedMs = nowElapsedMs,
                    switchedOverride = false,
                    dueTurn = dueTurn,
                )
            }
            val corridor = connectedCorridorCorrection(
                pose = pose,
                graphs = graphs,
                nowElapsedMs = nowElapsedMs,
                allowAgainstOneway = allowAgainstOneway,
            )
            if (corridor != null) return corridor
            if (allowRematchAfterLostHold && releasePhantomPrevious()) {
                return matchOnce(
                    pose = pose,
                    graphs = graphs,
                    speedKmh = speedKmh,
                    nowElapsedMs = nowElapsedMs,
                    dueTurn = dueTurn,
                    allowAgainstOneway = allowAgainstOneway,
                    allowRematchAfterLostHold = false,
                )
            }
            debug = DebugSnapshot(
                active = currentEdgeId != null,
                edgeId = currentEdgeId,
                regionId = currentRegionId,
                confidence = RoadMatchConfidence.NONE.name,
                candidateCount = 0,
                highwayClass = currentHighwayClass,
                inputBearingDeg = pose.bearingDeg,
                turnActive = dueTurn,
                skippedReason = "no_candidate",
                rejectReason = "no_candidate",
            )
            markAttempt(pose, nowElapsedMs)
            return null
        }

        val confidence = RoadMapMatcher.confidenceOf(ranked)
        val rawBest = ranked.first()
        val switchReject = switchRejectReason(rawBest, allowAgainstOneway, nowElapsedMs)

        if (confidence == RoadMatchConfidence.LOW || confidence == RoadMatchConfidence.NONE) {
            // Prefer staying on the last good edge over freezing pure DR.
            // HOLD_EDGE still inhibits heading when dueTurn / residual is large.
            val held = holdPreviousEdge(
                pose, graphs, dueTurn = dueTurn, allowAgainstOneway = allowAgainstOneway,
            )
            if (held != null) {
                return applyCandidate(
                    pose = pose,
                    cand = held,
                    confidence = "HOLD_EDGE",
                    candidateCount = ranked.size,
                    runnerUpScore = ranked.getOrNull(1)?.score,
                    nowElapsedMs = nowElapsedMs,
                    switchedOverride = false,
                    dueTurn = dueTurn,
                )
            }
            // Held edge lost heading compatibility (typical mid-corner). If the best
            // candidate is clearly aligned and not a forbidden ramp jump, hand off
            // even while confidence is still LOW.
            val residualToBest = RoadMapMatcher.smallestAngleDeg(pose.bearingDeg, rawBest.edgeAzimuthDeg)
            if (dueTurn &&
                residualToBest <= 20f &&
                switchReject == null &&
                acceptEdge(rawBest, fastConfirm = true, nowElapsedMs = nowElapsedMs)
            ) {
                return applyCandidate(
                    pose = pose,
                    cand = rawBest,
                    confidence = confidence.name,
                    candidateCount = ranked.size,
                    runnerUpScore = ranked.getOrNull(1)?.score,
                    nowElapsedMs = nowElapsedMs,
                    switchedOverride = null,
                    dueTurn = true,
                )
            }
            // Interchange field case: sticky previous is orphaned, nearby road has tiny
            // cross-track but stays LOW only because of disconnected-from-phantom.
            if (allowRematchAfterLostHold &&
                shouldRematchAfterLostHold(rawBest, allowAgainstOneway) &&
                releasePhantomPrevious()
            ) {
                return matchOnce(
                    pose = pose,
                    graphs = graphs,
                    speedKmh = speedKmh,
                    nowElapsedMs = nowElapsedMs,
                    dueTurn = dueTurn,
                    allowAgainstOneway = allowAgainstOneway,
                    allowRematchAfterLostHold = false,
                )
            }
            debug = rejectDebug(
                pose = pose,
                rawBest = rawBest,
                confidence = confidence.name,
                candidateCount = ranked.size,
                runnerUpScore = ranked.getOrNull(1)?.score,
                dueTurn = dueTurn,
                skippedReason = "low_confidence",
                rejectReason = switchReject ?: "low_confidence",
            )
            markAttempt(pose, nowElapsedMs)
            pathSinceMatchM = 0.0
            return null
        }

        if (switchReject != null) {
            val held = holdPreviousEdge(
                pose, graphs, dueTurn = dueTurn, allowAgainstOneway = allowAgainstOneway,
            )
            if (held != null) {
                return applyCandidate(
                    pose = pose,
                    cand = held,
                    confidence = "HOLD_EDGE",
                    candidateCount = ranked.size,
                    runnerUpScore = ranked.getOrNull(1)?.score,
                    nowElapsedMs = nowElapsedMs,
                    switchedOverride = false,
                    dueTurn = dueTurn,
                )
            }
            if (allowRematchAfterLostHold && releasePhantomPrevious()) {
                return matchOnce(
                    pose = pose,
                    graphs = graphs,
                    speedKmh = speedKmh,
                    nowElapsedMs = nowElapsedMs,
                    dueTurn = dueTurn,
                    allowAgainstOneway = allowAgainstOneway,
                    allowRematchAfterLostHold = false,
                )
            }
            debug = rejectDebug(
                pose = pose,
                rawBest = rawBest,
                confidence = confidence.name,
                candidateCount = ranked.size,
                runnerUpScore = ranked.getOrNull(1)?.score,
                dueTurn = dueTurn,
                skippedReason = "switch_rejected",
                rejectReason = switchReject,
            )
            markAttempt(pose, nowElapsedMs)
            pathSinceMatchM = 0.0
            return null
        }

        val fastConfirm = shouldFastConfirmTurn(dueTurn, pose, rawBest, graphs, nowElapsedMs)
        val accepted = acceptEdge(rawBest, fastConfirm = fastConfirm, nowElapsedMs = nowElapsedMs)
        val cand = if (accepted) {
            rawBest
        } else {
            holdPreviousEdge(
                pose,
                graphs,
                maxCrossM = RoadMapMatcher.HOLD_PREVIOUS_RADIUS_M,
                dueTurn = dueTurn,
                allowAgainstOneway = allowAgainstOneway,
            )
        }

        if (cand == null) {
            debug = rejectDebug(
                pose = pose,
                rawBest = rawBest,
                confidence = confidence.name,
                candidateCount = ranked.size,
                runnerUpScore = ranked.getOrNull(1)?.score,
                dueTurn = dueTurn,
                skippedReason = "switch_pending",
                rejectReason = "switch_pending",
            ).copy(
                // Keep sticky id visible while waiting for confirm.
                active = currentEdgeId != null,
                edgeId = currentEdgeId,
                regionId = currentRegionId,
                highwayClass = currentHighwayClass,
            )
            markAttempt(pose, nowElapsedMs)
            pathSinceMatchM = 0.0
            return null
        }

        return applyCandidate(
            pose = pose,
            cand = cand,
            confidence = confidence.name,
            candidateCount = ranked.size,
            runnerUpScore = ranked.getOrNull(1)?.score,
            nowElapsedMs = nowElapsedMs,
            switchedOverride = null,
            dueTurn = dueTurn,
        )
    }

    private fun shouldRematchAfterLostHold(
        rawBest: RoadMapMatcher.Candidate,
        allowAgainstOneway: Boolean,
    ): Boolean {
        if (currentEdgeId == null) return false
        if (rawBest.crossTrackM > REMATCH_NEAR_CROSS_M) return false
        if (!allowAgainstOneway && rawBest.againstOneway) return false
        return true
    }

    /** Drops orphaned sticky previous so the next rank is a fresh seed. */
    private fun releasePhantomPrevious(): Boolean {
        if (currentEdgeId == null) return false
        currentEdgeId = null
        currentRegionId = null
        currentHighwayClass = null
        pendingEdgeId = null
        pendingRegionId = null
        pendingWins = 0
        topologyAnchor = null
        topologyAnchorElapsedMs = 0L
        lastPastEndEdgeId = null
        lastPastEndRegionId = null
        lastPastEndXt = null
        exhaustedEdgeId = null
        exhaustedRegionId = null
        return true
    }

    private fun lookAheadDistanceM(speedKmh: Float): Double =
        (speedKmh.coerceAtLeast(0f) / 3.6 * LOOK_AHEAD_SECONDS)
            .coerceIn(LOOK_AHEAD_MIN_M, LOOK_AHEAD_MAX_M)

    private fun topologyPrediction(
        graphs: List<RoadGraph>,
        distanceM: Double,
        bearingDeg: Float,
        allowAgainstOneway: Boolean,
    ): RoadMapMatcher.TopologyPrediction? {
        val anchor = topologyAnchor ?: return null
        return RoadMapMatcher.advanceAlongTopology(
            graphs = graphs,
            start = anchor,
            distanceM = distanceM,
            targetBearingDeg = bearingDeg,
            allowAgainstOneway = allowAgainstOneway,
        )
    }

    /**
     * When the ordinary radius/heading search has no candidate, keep the vehicle on the
     * connected graph corridor for a few seconds. Position comes from the last matched graph
     * anchor plus travelled CAN path; unrelated roads are never considered.
     */
    private fun connectedCorridorCorrection(
        pose: RoadMatchPose,
        graphs: List<RoadGraph>,
        nowElapsedMs: Long,
        allowAgainstOneway: Boolean,
    ): RoadMatchPose? {
        if (topologyAnchor == null || topologyAnchorElapsedMs <= 0L) return null
        if (nowElapsedMs - topologyAnchorElapsedMs > CONNECTED_CORRIDOR_HOLD_MS) return null
        if (pathSinceMatchM !in 0.0..CONNECTED_CORRIDOR_MAX_M) return null
        val predicted = topologyPrediction(
            graphs = graphs,
            distanceM = pathSinceMatchM,
            bearingDeg = pose.bearingDeg,
            allowAgainstOneway = allowAgainstOneway,
        ) ?: return null
        val driftM = RoadGraph.haversineM(pose.lat, pose.lon, predicted.lat, predicted.lon)
        if (driftM > CONNECTED_CORRIDOR_MAX_M) return null
        if (exhaustedEdgeId != null &&
            predicted.edge.id == exhaustedEdgeId &&
            predicted.anchor.regionId == exhaustedRegionId
        ) {
            // Topology did not hop off the overshot polyline — do not yank to its endpoint.
            return null
        }

        currentEdgeId = predicted.edge.id
        currentRegionId = predicted.anchor.regionId
        currentHighwayClass = predicted.edge.highwayClass
        exhaustedEdgeId = null
        exhaustedRegionId = null
        lastPastEndEdgeId = null
        lastPastEndRegionId = null
        lastPastEndXt = null
        hypotheses = hypotheses + (predicted.anchor.regionId to predicted.edge.id)
        hypothesesUntilElapsedMs = maxOf(
            hypothesesUntilElapsedMs,
            nowElapsedMs + CONNECTED_CORRIDOR_HOLD_MS,
        )
        pendingEdgeId = null
        pendingRegionId = null
        pendingWins = 0
        val corrected = RoadMatchPose(
            lat = predicted.lat,
            lon = predicted.lon,
            // Sensor fusion remains authoritative for heading; topology constrains position.
            bearingDeg = pose.bearingDeg,
        )
        markAttempt(corrected, nowElapsedMs)
        debug = DebugSnapshot(
            active = true,
            edgeId = predicted.edge.id,
            regionId = predicted.anchor.regionId,
            crossTrackM = driftM,
            alongTrackM = predicted.anchor.alongTrackM,
            confidence = "CONNECTED_CORRIDOR",
            candidateCount = 0,
            connected = true,
            highwayClass = predicted.edge.highwayClass,
            oneway = predicted.edge.oneway,
            againstOneway = RoadMapMatcher.isAgainstOneway(
                predicted.edge.oneway,
                predicted.anchor.travelAgainstCoords,
            ),
            candidateEdgeId = predicted.edge.id,
            candidateHighwayClass = predicted.edge.highwayClass,
            candidateConnected = true,
            candidateCrossTrackM = driftM,
            inputBearingDeg = pose.bearingDeg,
            edgeBearingDeg = predicted.azimuthDeg,
            bearingDeltaDeg = 0f,
            turnActive = true,
            skippedReason = null,
            rejectReason = "no_candidate_corridor",
        )
        return corrected
    }

    private fun rejectDebug(
        pose: RoadMatchPose,
        rawBest: RoadMapMatcher.Candidate,
        confidence: String,
        candidateCount: Int,
        runnerUpScore: Double?,
        dueTurn: Boolean,
        skippedReason: String,
        rejectReason: String,
    ): DebugSnapshot = DebugSnapshot(
        active = currentEdgeId != null,
        edgeId = currentEdgeId,
        regionId = currentRegionId,
        highwayClass = currentHighwayClass,
        confidence = confidence,
        candidateCount = candidateCount,
        runnerUpScore = runnerUpScore,
        candidateEdgeId = rawBest.edge.id,
        candidateHighwayClass = rawBest.edge.highwayClass,
        candidateConnected = rawBest.connectedFromPrevious,
        candidateCrossTrackM = rawBest.crossTrackM,
        crossTrackM = rawBest.crossTrackM,
        alongTrackM = rawBest.alongTrackM,
        connected = rawBest.connectedFromPrevious,
        oneway = rawBest.edge.oneway,
        againstOneway = rawBest.againstOneway,
        inputBearingDeg = pose.bearingDeg,
        edgeBearingDeg = rawBest.edgeAzimuthDeg,
        turnActive = dueTurn,
        skippedReason = skippedReason,
        rejectReason = rejectReason,
    )

    private fun activeHypotheses(nowElapsedMs: Long): Set<Pair<String, Long>> {
        if (hypotheses.isEmpty()) return emptySet()
        if (nowElapsedMs > hypothesesUntilElapsedMs) return emptySet()
        return hypotheses
    }

    private fun holdPreviousEdge(
        pose: RoadMatchPose,
        graphs: List<RoadGraph>,
        maxCrossM: Double = holdPreviousRadiusM,
        dueTurn: Boolean = false,
        allowAgainstOneway: Boolean = false,
        allowPastEndHold: Boolean = false,
    ): RoadMapMatcher.Candidate? {
        val edgeId = currentEdgeId ?: return null
        val regionId = currentRegionId ?: return null
        // Prefer the tile copy nearest to the pose — never the first graph that happens
        // to share a sequential edge id after cache churn.
        val edge = resolveEdgeNear(graphs, regionId, edgeId, pose.lat, pose.lon, maxCrossM)
            ?: return null
        val proj = RoadMapMatcher.projectOntoEdge(pose.lat, pose.lon, edge) ?: return null
        if (proj.crossTrackM > maxCrossM) return null
        val d = RoadMapMatcher.smallestAngleDeg(pose.bearingDeg, proj.azimuthDeg)
        val r = RoadMapMatcher.smallestAngleDeg(
            pose.bearingDeg,
            RoadMapMatcher.normalizeDeg(proj.azimuthDeg + 180f),
        )
        val useReverse = r < d
        val az = if (useReverse) {
            RoadMapMatcher.normalizeDeg(proj.azimuthDeg + 180f)
        } else {
            proj.azimuthDeg
        }
        // Heading still roughly compatible with the held edge.
        // During turns allow a wider residual so a short opposite weave keeps the exit.
        val headingLimit = if (dueTurn) {
            RoadMapMatcher.HEADING_TOLERANCE_DEG + 25f
        } else {
            RoadMapMatcher.HEADING_TOLERANCE_DEG
        }
        if (minOf(d, r) > headingLimit) return null
        val against = RoadMapMatcher.isAgainstOneway(
            edge.oneway,
            travelAgainstCoords = useReverse,
        )
        // Do not sticky-hold against OSM oneway while moving forward (field logs).
        if (against && !allowAgainstOneway) return null
        val cand = RoadMapMatcher.Candidate(
            edge = edge,
            regionId = regionId,
            crossTrackM = proj.crossTrackM,
            alongTrackM = proj.alongTrackM,
            projLat = proj.lat,
            projLon = proj.lon,
            edgeAzimuthDeg = az,
            score = proj.crossTrackM,
            connectedFromPrevious = true,
            againstOneway = against,
            travelAgainstCoords = useReverse,
        )
        if (!allowPastEndHold && isPastEndReleased(pose, cand)) return null
        return cand
    }

    private fun resolveEdgeNear(
        graphs: List<RoadGraph>,
        regionId: String,
        edgeId: Long,
        lat: Double,
        lon: Double,
        maxCrossM: Double,
    ): RoadEdge? {
        var best: RoadEdge? = null
        var bestCross = Double.POSITIVE_INFINITY
        for (g in graphs) {
            if (g.regionId != regionId) continue
            val edge = g.edgeById[edgeId] ?: continue
            val proj = RoadMapMatcher.projectOntoEdge(lat, lon, edge) ?: continue
            if (proj.crossTrackM < bestCross) {
                bestCross = proj.crossTrackM
                best = edge
            }
        }
        if (best != null && bestCross <= maxCrossM) return best
        return null
    }

    private fun applyCandidate(
        pose: RoadMatchPose,
        cand: RoadMapMatcher.Candidate,
        confidence: String,
        candidateCount: Int,
        runnerUpScore: Double?,
        nowElapsedMs: Long,
        switchedOverride: Boolean?,
        dueTurn: Boolean = false,
    ): RoadMatchPose {
        val switched = switchedOverride ?: (
            currentEdgeId != null &&
                (cand.edge.id != currentEdgeId || cand.regionId != currentRegionId)
            )
        if (switched && currentEdgeId != null && currentRegionId != null) {
            abandonedEdgeId = currentEdgeId
            abandonedRegionId = currentRegionId
            abandonGuardUntilElapsedMs = nowElapsedMs + RETURN_GUARD_MS
            exhaustedEdgeId = null
            exhaustedRegionId = null
            lastPastEndEdgeId = null
            lastPastEndRegionId = null
            lastPastEndXt = null
            // Drop pending toward the abandoned edge so it cannot "finish" after the guard.
            if (pendingEdgeId == abandonedEdgeId && pendingRegionId == abandonedRegionId) {
                pendingEdgeId = null
                pendingRegionId = null
                pendingWins = 0
            }
        }
        val residual = RoadMapMatcher.smallestAngleDeg(pose.bearingDeg, cand.edgeAzimuthDeg)
        val holding = confidence == "HOLD_EDGE"
        val prevResidual = RoadMapMatcher.smallestAngleDeg(headingBeforeTickDeg, cand.edgeAzimuthDeg)
        val headingAway = residual > prevResidual + HEADING_AWAY_EPS_DEG
        val leavingSameEdge = !switched && !holding &&
            headingAway && residual >= LEAVING_EDGE_RESIDUAL_DEG
        // Same-edge catch-up on a slip road follows the ramp's changing azimuth.
        // Field 142148: straight motorway, early trunk_link HIGH, 14°/tick chase
        // of the cloverleaf while wheel/gyro were quiet → 2 km shadow.
        // Keep catch-up on ordinary roads (124442 tertiary undershoot) and on the
        // confirmed switch tick onto a link (real exit / first lock).
        val sameEdgeLink = !switched && RoadHighwayClass.isLink(cand.edge.highwayClass)
        val inhibitHeading = when {
            holding -> true
            leavingSameEdge -> true
            dueTurn && !switched -> true
            sameEdgeLink -> true
            else -> false
        }
        val catchUpHeading = !holding && !inhibitHeading
        val corrected = RoadMapMatcher.softCorrect(
            pose,
            cand,
            turnActive = dueTurn || inhibitHeading,
            catchUpHeading = catchUpHeading,
        )
        val bearingDelta = RoadMapMatcher.smallestAngleDeg(pose.bearingDeg, corrected.bearingDeg)
        currentEdgeId = cand.edge.id
        currentRegionId = cand.regionId
        currentHighwayClass = cand.edge.highwayClass
        val coordsAzimuth = RoadMapMatcher.projectOntoEdge(
            cand.projLat,
            cand.projLon,
            cand.edge,
        )?.azimuthDeg ?: cand.edgeAzimuthDeg
        val travelAgainstCoords =
            RoadMapMatcher.smallestAngleDeg(
                cand.edgeAzimuthDeg,
                RoadMapMatcher.normalizeDeg(coordsAzimuth + 180f),
            ) < RoadMapMatcher.smallestAngleDeg(cand.edgeAzimuthDeg, coordsAzimuth)
        topologyAnchor = RoadMapMatcher.TopologyAnchor(
            regionId = cand.regionId,
            edgeId = cand.edge.id,
            alongTrackM = cand.alongTrackM,
            travelAgainstCoords = travelAgainstCoords,
        )
        topologyAnchorElapsedMs = nowElapsedMs
        markAttempt(corrected, nowElapsedMs)
        pathSinceMatchM = 0.0
        debug = DebugSnapshot(
            active = true,
            edgeId = cand.edge.id,
            regionId = cand.regionId,
            crossTrackM = cand.crossTrackM,
            alongTrackM = cand.alongTrackM,
            switchedEdge = switched,
            confidence = confidence,
            candidateCount = candidateCount,
            runnerUpScore = runnerUpScore,
            connected = cand.connectedFromPrevious,
            highwayClass = cand.edge.highwayClass,
            oneway = cand.edge.oneway,
            againstOneway = cand.againstOneway,
            candidateEdgeId = cand.edge.id,
            candidateHighwayClass = cand.edge.highwayClass,
            candidateConnected = cand.connectedFromPrevious,
            candidateCrossTrackM = cand.crossTrackM,
            inputBearingDeg = pose.bearingDeg,
            edgeBearingDeg = cand.edgeAzimuthDeg,
            bearingDeltaDeg = bearingDelta,
            turnActive = inhibitHeading,
            skippedReason = null,
            rejectReason = null,
        )
        return corrected
    }

    /**
     * Hard-reject switches onto forbidden ramps / disconnected links even when the
     * scorer still ranked them first (legacy sole-candidate MEDIUM path).
     * Also refuse an immediate bounce back onto the edge we just left.
     */
    private fun switchRejectReason(
        cand: RoadMapMatcher.Candidate,
        allowAgainstOneway: Boolean,
        nowElapsedMs: Long,
    ): String? {
        val isLink = RoadHighwayClass.isLink(cand.edge.highwayClass)
        if (!allowAgainstOneway && cand.againstOneway && isLink) {
            return "against_oneway_link"
        }
        if (!allowAgainstOneway &&
            !cand.connectedFromPrevious &&
            isLink &&
            currentEdgeId != null
        ) {
            return "disconnected_link"
        }
        if (isReturnToAbandoned(cand, nowElapsedMs)) {
            return "return_to_prior"
        }
        return null
    }

    private fun isReturnToAbandoned(
        cand: RoadMapMatcher.Candidate,
        nowElapsedMs: Long,
    ): Boolean {
        if (abandonedEdgeId == null || abandonedRegionId == null) return false
        if (nowElapsedMs >= abandonGuardUntilElapsedMs) return false
        return cand.edge.id == abandonedEdgeId && cand.regionId == abandonedRegionId
    }

    /**
     * Fast edge handoff only when the turn is clear: new best is well aligned with
     * travel bearing and the held edge is not. Avoids jumping on ambiguous 45° NE
     * headings near junctions (still uses full [switchConfirmCount] there).
     * Never fast-confirms against-oneway, disconnected link jumps, or return-to-prior.
     */
    private fun shouldFastConfirmTurn(
        dueTurn: Boolean,
        pose: RoadMatchPose,
        rawBest: RoadMapMatcher.Candidate,
        graphs: List<RoadGraph>,
        nowElapsedMs: Long,
    ): Boolean {
        if (!dueTurn) return false
        if (currentEdgeId == null || currentRegionId == null) return false
        if (rawBest.edge.id == currentEdgeId && rawBest.regionId == currentRegionId) return false
        if (switchRejectReason(rawBest, allowAgainstOneway = false, nowElapsedMs) != null) {
            return false
        }
        val residualToBest = RoadMapMatcher.smallestAngleDeg(pose.bearingDeg, rawBest.edgeAzimuthDeg)
        if (residualToBest > 20f) return false
        val held = holdPreviousEdge(pose, graphs, dueTurn = true) ?: return true
        val residualToHeld = RoadMapMatcher.smallestAngleDeg(pose.bearingDeg, held.edgeAzimuthDeg)
        return residualToHeld >= RoadMapMatcher.BEARING_INHIBIT_RESIDUAL_DEG
    }

    private fun acceptEdge(
        cand: RoadMapMatcher.Candidate,
        fastConfirm: Boolean,
        nowElapsedMs: Long,
    ): Boolean {
        val edgeId = cand.edge.id
        val regionId = cand.regionId
        if (currentEdgeId == null) {
            pendingEdgeId = null
            pendingWins = 0
            return true
        }
        if (edgeId == currentEdgeId && regionId == currentRegionId) {
            pendingEdgeId = null
            pendingWins = 0
            return true
        }
        // Do not let pending wins accumulate toward the abandoned edge during the guard.
        if (isReturnToAbandoned(cand, nowElapsedMs)) {
            return false
        }
        if (pendingEdgeId == edgeId && pendingRegionId == regionId) {
            pendingWins++
        } else {
            pendingEdgeId = edgeId
            pendingRegionId = regionId
            pendingWins = 1
        }
        // Disconnected non-link jumps still need full confirmation; never fast-confirm.
        val isLink = RoadHighwayClass.isLink(cand.edge.highwayClass)
        val needed = when {
            fastConfirm && cand.connectedFromPrevious && !cand.againstOneway -> 1
            !cand.connectedFromPrevious && isLink -> switchConfirmCount + 2
            else -> switchConfirmCount
        }
        return pendingWins >= needed
    }

    private sealed class PastEndDecision {
        data object NotApplicable : PastEndDecision()
        class Done(val pose: RoadMatchPose?) : PastEndDecision()
    }

    /**
     * When the sticky edge has been overshot past its travel-direction endpoint,
     * do not snap/HOLD toward that vertex (that pull is backward). Prefer a
     * connected successor immediately; otherwise leave the pose on pure DR.
     */
    private fun handlePastEndRelease(
        pose: RoadMatchPose,
        graphs: List<RoadGraph>,
        ranked: List<RoadMapMatcher.Candidate>,
        nowElapsedMs: Long,
        dueTurn: Boolean,
        allowAgainstOneway: Boolean,
    ): PastEndDecision {
        val currentProj = holdPreviousEdge(
            pose = pose,
            graphs = graphs,
            dueTurn = dueTurn,
            allowAgainstOneway = allowAgainstOneway,
            allowPastEndHold = true,
        ) ?: return PastEndDecision.NotApplicable
        val released = isPastEndReleased(pose, currentProj)
        notePastEndObservation(currentProj)
        if (!released) return PastEndDecision.NotApplicable

        exhaustedEdgeId = currentProj.edge.id
        exhaustedRegionId = currentProj.regionId

        val successor = ranked.firstOrNull { cand ->
            (cand.edge.id != currentEdgeId || cand.regionId != currentRegionId) &&
                cand.connectedFromPrevious &&
                (allowAgainstOneway || !cand.againstOneway) &&
                switchRejectReason(cand, allowAgainstOneway, nowElapsedMs) == null &&
                !isPastEndReleased(pose, cand)
        }
        if (successor != null &&
            acceptEdge(successor, fastConfirm = true, nowElapsedMs = nowElapsedMs)
        ) {
            return PastEndDecision.Done(
                applyCandidate(
                    pose = pose,
                    cand = successor,
                    confidence = RoadMapMatcher.confidenceOf(ranked).name,
                    candidateCount = ranked.size,
                    runnerUpScore = ranked.getOrNull(1)?.score,
                    nowElapsedMs = nowElapsedMs,
                    switchedOverride = null,
                    dueTurn = dueTurn,
                ),
            )
        }

        val corridor = connectedCorridorCorrection(
            pose = pose,
            graphs = graphs,
            nowElapsedMs = nowElapsedMs,
            allowAgainstOneway = allowAgainstOneway,
        )
        if (corridor != null &&
            (debug.edgeId != currentProj.edge.id || debug.regionId != currentProj.regionId)
        ) {
            return PastEndDecision.Done(corridor)
        }

        debug = rejectDebug(
            pose = pose,
            rawBest = ranked.firstOrNull() ?: currentProj,
            confidence = if (ranked.isEmpty()) RoadMatchConfidence.NONE.name
            else RoadMapMatcher.confidenceOf(ranked).name,
            candidateCount = ranked.size,
            runnerUpScore = ranked.getOrNull(1)?.score,
            dueTurn = dueTurn,
            skippedReason = "past_end",
            rejectReason = "past_end",
        ).copy(
            active = true,
            edgeId = currentProj.edge.id,
            regionId = currentProj.regionId,
            highwayClass = currentProj.edge.highwayClass,
            crossTrackM = currentProj.crossTrackM,
            alongTrackM = currentProj.alongTrackM,
            connected = true,
            oneway = currentProj.edge.oneway,
            againstOneway = currentProj.againstOneway,
            edgeBearingDeg = currentProj.edgeAzimuthDeg,
        )
        markAttempt(pose, nowElapsedMs)
        pathSinceMatchM = 0.0
        return PastEndDecision.Done(null)
    }

    private fun isPastEndReleased(
        pose: RoadMatchPose,
        cand: RoadMapMatcher.Candidate,
    ): Boolean {
        if (exhaustedEdgeId == cand.edge.id && exhaustedRegionId == cand.regionId) {
            val backOnEdge = cand.crossTrackM < 4.0 &&
                !RoadMapMatcher.isAlongAtTravelEnd(cand)
            if (backOnEdge) {
                exhaustedEdgeId = null
                exhaustedRegionId = null
                lastPastEndEdgeId = null
                lastPastEndRegionId = null
                lastPastEndXt = null
                return false
            }
            return true
        }
        if (!RoadMapMatcher.isOvershootBeyondEnd(pose.lat, pose.lon, cand)) return false
        if (cand.crossTrackM >= RoadMapMatcher.PAST_END_XT_RELEASE_M) return true
        if (lastPastEndEdgeId == cand.edge.id &&
            lastPastEndRegionId == cand.regionId &&
            lastPastEndXt != null &&
            cand.crossTrackM >= lastPastEndXt!! + RoadMapMatcher.PAST_END_XT_GROWTH_M
        ) {
            return true
        }
        return false
    }

    private fun notePastEndObservation(cand: RoadMapMatcher.Candidate) {
        if (RoadMapMatcher.isAlongAtTravelEnd(cand)) {
            lastPastEndEdgeId = cand.edge.id
            lastPastEndRegionId = cand.regionId
            lastPastEndXt = cand.crossTrackM
        } else if (lastPastEndEdgeId == cand.edge.id && lastPastEndRegionId == cand.regionId) {
            lastPastEndEdgeId = null
            lastPastEndRegionId = null
            lastPastEndXt = null
        }
    }

    private fun markAttempt(pose: RoadMatchPose, nowElapsedMs: Long) {
        lastMatchElapsedMs = nowElapsedMs
        lastPoseLat = pose.lat
        lastPoseLon = pose.lon
        lastBearingDeg = pose.bearingDeg
        hasLastPose = true
    }

    private fun loadInstalledGraphs(lat: Double, lon: Double): List<RoadGraph> {
        val dir = mapsDir()
        if (!dir.isDirectory) return emptyList()
        val entries = dir.listFiles() ?: return emptyList()
        val out = ArrayList<RoadGraph>()
        val activeCacheKeys = linkedSetOf<String>()

        // New format: one user-visible region, locally extracted into independently
        // compressed tiles. Only overlapping tiles enter the heap.
        for (bundleDir in entries.filter {
            it.isDirectory && it.name.endsWith(RoadMapBundle.INSTALL_SUFFIX)
        }) {
            val indexFile = File(bundleDir, RoadMapBundle.INDEX_FILE)
            val cacheId = bundleDir.absolutePath
            val cached = bundleIndexes[cacheId]
            val index = if (cached != null && cached.lastModified == indexFile.lastModified()) {
                cached.index
            } else {
                runCatching { RoadMapBundle.loadIndex(bundleDir) }.getOrNull()?.also {
                    bundleIndexes[cacheId] = CachedBundleIndex(indexFile.lastModified(), it)
                }
            } ?: continue
            if (!index.contains(lat, lon)) continue
            for (tile in index.covering(lat, lon)) {
                val tileFile = File(bundleDir, tile.file)
                if (!tileFile.isFile) continue
                val key = "${index.regionId}/${tile.id}"
                activeCacheKeys.add(key)
                val graph = try {
                    RoadGraphStore.loadOrGet(key, tileFile)
                } catch (_: OutOfMemoryError) {
                    debug = DebugSnapshot(skippedReason = "oom_load")
                    continue
                } catch (_: Throwable) {
                    continue
                }
                if (graph.edges.isNotEmpty() && graph.contains(lat, lon)) out.add(graph)
            }
        }

        // Root-level monolithic *.tboxroads are intentionally ignored (v4 = bundles only).
        // Leftovers are deleted by RoadMapDownloadManager without parsing into RAM.
        RoadGraphStore.retainOnly(activeCacheKeys)
        return out
    }
}

/** Process-wide debug mirror for geo-debug log. */
object RoadMatchRuntimeDebug {
    @Volatile
    var snapshot: RoadMatchRuntime.DebugSnapshot = RoadMatchRuntime.DebugSnapshot()
        private set

    fun publish(snapshot: RoadMatchRuntime.DebugSnapshot) {
        this.snapshot = snapshot
    }

    fun clear() {
        snapshot = RoadMatchRuntime.DebugSnapshot()
    }
}
