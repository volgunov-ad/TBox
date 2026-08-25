package vad.dashing.tbox.location.roadmatch

import java.io.File
import kotlin.math.abs

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
    /** ≤0 disables rank lag; otherwise lag is [RoadMapMatcher.matchLagMeters]. */
    private val matchLagM: Double = RoadMapMatcher.MATCH_LAG_M,
    /**
     * Force path-odometer sync on/off regardless of [RoadMatchTuningKey.PATH_ODO_SYNC_ENABLED].
     * `null` follows tuning (production default: off). Env
     * `TBOX_ROADMATCH_PATH_ODOMETER_SYNC=1|0` sets this when the caller leaves it null.
     */
    private val pathOdometerSync: Boolean? = when (
        System.getenv("TBOX_ROADMATCH_PATH_ODOMETER_SYNC")
    ) {
        "1" -> true
        "0" -> false
        else -> null
    },
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
        /** True when bearing pull is inhibited (HOLD / leaving / dueTurn / same-edge link / sensors oppose / stalk fork). */
        val turnActive: Boolean? = null,
        /**
         * Why a switch/candidate was refused when pose was not corrected, e.g.
         * `against_oneway_link`, `disconnected_link`, `low_confidence`, `lost_hold`.
         */
        val rejectReason: String? = null,
        /** Metres the rank pose sat behind the live DR pose (0 when trail is short). */
        val matchLagM: Double? = null,
        /** Stalk fork hint applied this tick (`L` / `R`); null when ignored. */
        val turnHint: String? = null,
        /** Top ranked switch candidates (≤ [RoadMapMatcher.BEAM_WIDTH]), best first. */
        val rankedCandidates: List<RankedCandidateRef> = emptyList(),
        /** Lateral leash: `stretch` / `break` / `retract`. */
        val leash: String? = null,
        /** Instrument-only particle is live at a complex junction. */
        val freeActive: Boolean = false,
        val freePromoted: Boolean = false,
        val junction: Boolean = false,
        /** Ordinary / Rails / FreeTurns. */
        val matchMode: String? = null,
        /** `CITY` / `HIGHWAY` corridor profile. */
        val roadProfile: String? = null,
        /** Intentional turn signal (not comfort 3-blink). */
        val turnIntent: Boolean? = null,
        /** Rising-edge flash count on the active stalk side. */
        val turnFlashes: Int? = null,
        /** Pose fed into this [maybeCorrect] call, before [RoadMapMatcher.softCorrect]. */
        val preMatchLat: Double? = null,
        val preMatchLon: Double? = null,
        val preMatchBearingDeg: Float? = null,
        /** True when this call returned a corrected pose (caller should apply it). */
        val matchApplied: Boolean = false,
        val freeLat: Double? = null,
        val freeLon: Double? = null,
        val freeBearingDeg: Float? = null,
        /** Instrument path since the last topology cursor (m). */
        val pathOdoM: Double? = null,
        /** Haversine from matched pose to topology prediction (m). */
        val pathOdoGapM: Double? = null,
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
        /** Default dead zone (m) before path-odometer sync pulls toward topology. */
        const val PATH_ODO_SYNC_MIN_GAP_M = 5.0
        /** Ignore absurd gaps (likely disconnected jump) and re-seed the cursor. */
        const val PATH_ODO_SYNC_MAX_GAP_M = 120.0
        /** Default max pull per matched tick toward odometer topology pose. */
        const val PATH_ODO_SYNC_MAX_STEP_M = 3.0
        const val PATH_ODO_SYNC_MAX_HEADING_DEG = 40f
        /** Predicted point must lie this close to travel heading or it is behind us. */
        const val PATH_ODO_SYNC_FORWARD_DEG = 70f
        /** After leash_break / no_candidate on a `*_link`, prefer the last non-link parent. */
        const val PARENT_PREFER_MS = 8_000L
        /** Drop graph-only corridor when travel heading opposes the predicted edge. */
        const val CORRIDOR_HEADING_ABORT_DEG = 50f
        const val MATCH_LAG_M = RoadMapMatcher.MATCH_LAG_M
        /**
         * Rails corridor leave is cross-track to the sticky edge, not Euclidean
         * free↔rail gap. Along-track chord lag is allowed.
         */
        const val RAILS_HARD_SNAP_XT_M = 10.0
        const val RAILS_SOFT_XT_M = 25.0
        const val RAILS_SOFT_BLEND = 0.45
        const val RAILS_SOFT_MAX_STEP_M = 5.0
        const val RAILS_BREAK_XT_M = 40.0
        const val RAILS_BREAK_XT_YARD_M = 22.0
        const val RAILS_RELOCK_RADIUS_M = 80.0
        const val RAILS_RELOCK_HEADING_DEG = 20f
        /** Min path (m) to advance along rails between outputs. */
        const val RAILS_MIN_ADVANCE_M = 0.4
        /**
         * Along-leash helpers (tests / leftover math). The corridor publish path
         * uses the free projection on the sticky edge instead of pulling the
         * graph pose forward.
         */
        const val RAILS_ALONG_LEASH_XT_M = 18.0
        const val RAILS_ALONG_LEASH_DEAD_M = 6.0
        const val RAILS_ALONG_LEASH_GAIN = 0.5
        const val RAILS_ALONG_LEASH_MAX_PULL_M = 8.0
        /** Lateral corridor width must not be reported as HIGH confidence. */
        const val RAILS_CONFIDENCE_MEDIUM_GAP_M = RAILS_HARD_SNAP_XT_M
        const val RAILS_CONFIDENCE_LOW_GAP_M = RAILS_SOFT_XT_M
        /** A broken rail may be reconsidered quickly after a fresh navigator lock. */
        const val RAILS_REGRAB_GUARD_MS = 1_000L
        /** Navigator target may exceed measured path only by geometry/projection tolerance. */
        const val RAILS_NAV_PATH_FACTOR = 1.25
        const val RAILS_NAV_PATH_SLACK_M = 5.0
        /** Bias travel bearing (±°) when intentional turn stalk at a fork (city). */
        const val RAILS_TURN_HINT_BIAS_DEG = 35f
        /** Stronger Rails bearing bias on highway + intentional stalk (gentle ramps). */
        const val RAILS_HIGHWAY_INTENT_BIAS_DEG = 55f
        const val FREE_TURNS_JUNCTION_SKIP = "free_turns_junction"
        const val FREE_TURNS_STALK_SKIP = "free_turns_stalk"
        /** Ordinary softCorrect skipped while experimental stalk unbind is active. */
        const val ORDINARY_STALK_SKIP = "ordinary_stalk"
    }

    @Volatile
    var debug: DebugSnapshot = DebugSnapshot()
        private set

    /** Last mode passed to [maybeCorrect]; changing modes clears sticky state. */
    private var lastMatchMode: RoadMatchMode = RoadMatchMode.ORDINARY
    private var matchFreeTurns: Boolean = false
    private var freeTurnsReleased: Boolean = false
    private var freeTurnsReleaseKind: RoadMatchFreeTurnsMath.ReleaseKind? = null
    private var freeTurnsRemainingAtReleaseM: Double = 0.0
    private var freeTurnsPathSinceReleaseM: Double = 0.0
    private var roadProfile: RoadMatchRoadProfile = RoadMatchRoadProfile.CITY
    private var pendingRoadProfile: RoadMatchRoadProfile? = null
    private var roadProfileTicks: Int = 0
    private var matchTurnIntent: Boolean = false
    private var matchTurnFlashes: Int = 0
    /** 0..1 — relax highway-class score bias when live GNSS trusts position. */
    private var matchGnssPositionTrust: Float = 0f
    /** CAN / wheel-pulse metres for this tick; null falls back to pose haversine. */
    private var matchInstrumentStepM: Double? = null
    private var tuning: RoadMatchTuning = RoadMatchTuning.DEFAULT

    private fun tv(key: RoadMatchTuningKey): Double = tuning[key]
    private fun tf(key: RoadMatchTuningKey): Float = tuning.float(key)
    private fun ti(key: RoadMatchTuningKey): Int = tuning.int(key)
    private fun pathOdoSyncEnabled(): Boolean =
        pathOdometerSync ?: tuning.bool(RoadMatchTuningKey.PATH_ODO_SYNC_ENABLED)
    private fun configuredOr(key: RoadMatchTuningKey, constructorValue: Double): Double =
        if (tuning.isDefault(key.group) && key.group == RoadMatchTuningGroup.COMMON) {
            constructorValue
        } else {
            tv(key)
        }

    fun travelAgainstCoords(): Boolean? = topologyAnchor?.travelAgainstCoords

    fun alongTrackM(): Double? = topologyAnchor?.alongTrackM

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
    /** Last topology sync for [pathOdometerSync]; independent of per-tick [topologyAnchor]. */
    private var pathOdoAnchor: RoadMapMatcher.TopologyAnchor? = null
    private var pathOdoM: Double = 0.0
    private var pathOdoLastGapM: Double? = null
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
    private data class TrailSample(val lat: Double, val lon: Double, val cumM: Double)
    /** Recent DR samples for match-lag ranking (live pose still snaps). */
    private val trail = ArrayDeque<TrailSample>()
    private var lastMatchLagM: Double = 0.0
    /** Travel azimuth of the last applied sticky edge; used to drop lag once turning off it. */
    private var lastEdgeAzimuthDeg: Float? = null
    private var turnHintActive: Boolean = false
    private var appliedTurnHint: RoadMapMatcher.TurnHint? = null
    /** Last ranked switch candidates; kept across throttle / stationary so the map does not flicker. */
    private var lastRankedCandidates: List<RankedCandidateRef> = emptyList()
    /**
     * After [reset] or cold start while parked: run one rank pass at speed 0 so the map
     * widget can show sticky edge / candidates and warm tile graphs without waiting for DR.
     */
    private var stationaryOverlaySeed = false
    /** Last pose returned to the caller (or last input when match skipped). */
    private var lastOutputPose: RoadMatchPose? = null
    /** Instrument-only particle at a complex junction. */
    private var freePose: RoadMatchPose? = null
    private var junctionPathM: Double = 0.0
    private var junctionActive: Boolean = false
    private var leavingPathM: Double = 0.0
    private var lastLeaveXt: Double? = null
    private var leashState: String? = null
    private var skipCorridor: Boolean = false
    /** Last applied ordinary (non-`*_link`) road — restore target after a ramp miss. */
    private var lastNonLinkEdgeId: Long? = null
    private var lastNonLinkRegionId: String? = null
    private var lastNonLinkHighwayClass: String? = null
    private var parentPreferUntilElapsedMs: Long = 0L
    /** Sticky is / just was a `*_link`; allow bounce back to [lastNonLinkEdgeId]. */
    private var preferParentAfterLink: Boolean = false
    private var matchTravelBearingDeg: Float = 0f
    private var matchTopologyExpected: Set<Pair<String, Long>> = emptySet()
    private var matchTurnHint: RoadMapMatcher.TurnHint? = null
    private var matchSpeedKmh: Float = 0f
    /** Bent oneway ring: hop successors immediately, no disconnected grab. */
    private var circulatingManeuver: Boolean = false
    /** True only for OSM bent/short oneway chords — not a mid-block U-turn. */
    private var circulatingArc: Boolean = false
    private var railsNavigator: RoadMatchRuntime? = null
    private var railsBreakUntilElapsedMs: Long = 0L
    private var railsBrokenEdgeId: Long? = null
    private var railsBrokenRegionId: String? = null
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
        pathOdoAnchor = null
        pathOdoM = 0.0
        pathOdoLastGapM = null
        abandonedEdgeId = null
        abandonedRegionId = null
        abandonGuardUntilElapsedMs = 0L
        preferFastRetry = false
        lastPastEndEdgeId = null
        lastPastEndRegionId = null
        lastPastEndXt = null
        exhaustedEdgeId = null
        exhaustedRegionId = null
        trail.clear()
        lastMatchLagM = 0.0
        lastEdgeAzimuthDeg = null
        turnHintActive = false
        appliedTurnHint = null
        lastRankedCandidates = emptyList()
        lastOutputPose = null
        freePose = null
        junctionPathM = 0.0
        junctionActive = false
        leavingPathM = 0.0
        lastLeaveXt = null
        leashState = null
        skipCorridor = false
        lastNonLinkEdgeId = null
        lastNonLinkRegionId = null
        lastNonLinkHighwayClass = null
        parentPreferUntilElapsedMs = 0L
        preferParentAfterLink = false
        matchTravelBearingDeg = 0f
        matchTopologyExpected = emptySet()
        matchTurnHint = null
        matchSpeedKmh = 0f
        circulatingManeuver = false
        circulatingArc = false
        railsBreakUntilElapsedMs = 0L
        railsBrokenEdgeId = null
        railsBrokenRegionId = null
        railsNavigator?.reset()
        roadProfile = RoadMatchRoadProfile.CITY
        pendingRoadProfile = null
        roadProfileTicks = 0
        matchTurnIntent = false
        matchTurnFlashes = 0
        matchFreeTurns = false
        freeTurnsReleased = false
        freeTurnsReleaseKind = null
        freeTurnsRemainingAtReleaseM = 0.0
        freeTurnsPathSinceReleaseM = 0.0
        matchGnssPositionTrust = 0f
        debug = DebugSnapshot()
        stationaryOverlaySeed = true
    }

    /** Load installed tiles around the pose into [RoadGraphStore] (overlay neighbors). */
    internal fun warmGraphsAt(lat: Double, lon: Double): List<RoadGraph> =
        loadInstalledGraphs(lat, lon)

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
        /** Left/right stalk only; hazard and unknown are null. */
        turnHint: RoadMapMatcher.TurnHint? = null,
        /**
         * Intentional stalk (not comfort 3-blink). From [TurnSignalIntentTracker]
         * outside this runtime so Ordinary↔Rails reset does not clear it.
         */
        turnIntent: Boolean = false,
        /** Rising-edge flash count for geo-debug. */
        turnFlashCount: Int = 0,
        /** Ordinary softCorrect (default) or Rails corridor. */
        mode: RoadMatchMode = RoadMatchMode.ORDINARY,
        /**
         * 0..1 from [RoadMatchGnssTrust]: when live GNSS is good, ranking leans
         * more on cross-track (nearest road) and less on highway-class bias.
         */
        gnssPositionTrust: Float = 0f,
        tuning: RoadMatchTuning = RoadMatchTuning.DEFAULT,
        /**
         * Instrument path this tick (CAN or wheel pulses). Used by path-odometer
         * sync so lateral snap cannot shorten the graph cursor. Null → haversine
         * between consecutive input poses (tests / replay without `integ.dDistM`).
         */
        instrumentStepM: Double? = null,
    ): RoadMatchPose? {
        if (mode != lastMatchMode) {
            // Switching modes must not carry sticky Ordinary state onto Rails (or vice versa).
            reset()
            lastMatchMode = mode
        }
        matchTurnIntent = turnIntent
        matchTurnFlashes = turnFlashCount
        matchGnssPositionTrust = gnssPositionTrust.coerceIn(0f, 1f)
        matchInstrumentStepM = instrumentStepM
        this.tuning = tuning
        val result = when (mode) {
            RoadMatchMode.RAILS -> maybeCorrectRails(
                enabled = enabled,
                pose = pose,
                speedKmh = speedKmh,
                nowElapsedMs = nowElapsedMs,
                allowAgainstOneway = allowAgainstOneway,
                turnHint = turnHint,
            )
            RoadMatchMode.ORDINARY,
            RoadMatchMode.FREE_TURNS -> maybeCorrectInner(
                enabled = enabled,
                pose = pose,
                speedKmh = speedKmh,
                nowElapsedMs = nowElapsedMs,
                allowAgainstOneway = allowAgainstOneway,
                turnHint = turnHint,
                freeTurns = mode == RoadMatchMode.FREE_TURNS,
            )
        }
        debug = debug.copy(
            matchMode = mode.name,
            roadProfile = roadProfile.name,
            turnIntent = matchTurnIntent,
            turnFlashes = matchTurnFlashes,
            preMatchLat = pose.lat,
            preMatchLon = pose.lon,
            preMatchBearingDeg = pose.bearingDeg,
            matchApplied = result != null,
            freeLat = freePose?.lat,
            freeLon = freePose?.lon,
            freeBearingDeg = freePose?.bearingDeg,
            pathOdoM = pathOdoM.takeIf { pathOdoSyncEnabled() },
            pathOdoGapM = pathOdoLastGapM,
        )
        return result
    }

    private fun maybeCorrectInner(
        enabled: Boolean,
        pose: RoadMatchPose,
        speedKmh: Float,
        nowElapsedMs: Long,
        allowAgainstOneway: Boolean,
        turnHint: RoadMapMatcher.TurnHint?,
        freeTurns: Boolean = false,
    ): RoadMatchPose? {
        if (!enabled) {
            reset()
            debug = DebugSnapshot(skippedReason = "disabled")
            return null
        }
        advanceFreeParticle(pose)
        if (speedKmh < configuredOr(RoadMatchTuningKey.MIN_SPEED_KMH, minSpeedKmh.toDouble())) {
            runStationaryOverlaySeedIfNeeded(
                pose = pose,
                nowElapsedMs = nowElapsedMs,
                allowAgainstOneway = allowAgainstOneway,
                turnHint = turnHint,
                freeTurns = freeTurns,
            )
            debug = debug.copy(
                skippedReason = if (debug.skippedReason == "no_graph") "no_graph" else "stationary",
                rankedCandidates = lastRankedCandidates,
            )
            lastOutputPose = pose
            return null
        }

        val stepM = if (hasLastPose) {
            RoadGraph.haversineM(
                lastPoseLat, lastPoseLon, pose.lat, pose.lon,
            )
        } else {
            0.0
        }
        if (hasLastPose) {
            pathSinceMatchM += stepM
            if (pathOdoSyncEnabled() && pathOdoAnchor != null) {
                val ledger = matchInstrumentStepM?.takeIf { it.isFinite() && it > 0.0 } ?: stepM
                if (ledger.isFinite() && ledger > 0.0) pathOdoM += ledger
            }
        }
        pushTrail(pose)
        val dtMs = if (lastMatchElapsedMs > 0L) nowElapsedMs - lastMatchElapsedMs else Long.MAX_VALUE
        val turn = if (hasLastPose) {
            RoadMapMatcher.smallestAngleDeg(lastBearingDeg, pose.bearingDeg)
        } else {
            0f
        }
        val pathLimitM = activePathTriggerM()
        val timeLimitMs = activeTimeTriggerMs()
        var duePath = pathSinceMatchM >= pathLimitM
        var dueTime = dtMs >= timeLimitMs
        val dueTurn = turn >= configuredOr(
            RoadMatchTuningKey.TURN_TRIGGER_DEG,
            turnTriggerDeg.toDouble(),
        )
        headingBeforeTickDeg = if (hasLastPose) lastBearingDeg else pose.bearingDeg
        lastPoseLat = pose.lat
        lastPoseLon = pose.lon
        lastBearingDeg = pose.bearingDeg
        hasLastPose = true

        matchFreeTurns = freeTurns
        val graphs = loadInstalledGraphs(pose.lat, pose.lon)
        val ordinaryStalkTuningOn = !freeTurns && ordinaryStalkUnbindAnyEnabled()
        // Keep the gate alive while released even if the user just flipped the
        // Ordinary stalk toggles off — otherwise softCorrect stays skipped forever.
        val runStalkOrFreeGate =
            freeTurns || ordinaryStalkTuningOn || (!freeTurns && freeTurnsReleased)
        if (runStalkOrFreeGate && graphs.isNotEmpty()) {
            val justRebound = updateFreeTurnsGate(
                pose = pose,
                graphs = graphs,
                stepM = stepM,
                allowAgainstOneway = allowAgainstOneway,
                turnHint = turnHint,
                speedKmh = speedKmh,
                freeTurns = freeTurns,
            )
            if (freeTurnsReleased) {
                val skip = when (freeTurnsReleaseKind) {
                    RoadMatchFreeTurnsMath.ReleaseKind.STALK ->
                        if (freeTurns) FREE_TURNS_STALK_SKIP else ORDINARY_STALK_SKIP
                    else -> FREE_TURNS_JUNCTION_SKIP
                }
                debug = DebugSnapshot(
                    active = false,
                    skippedReason = skip,
                    rankedCandidates = lastRankedCandidates,
                    leash = "break",
                    junction = freeTurnsReleaseKind == RoadMatchFreeTurnsMath.ReleaseKind.JUNCTION,
                    turnHint = turnHintDebugLabel(),
                    turnIntent = matchTurnIntent,
                )
                lastOutputPose = pose
                preferFastRetry = true
                return null
            }
            if (justRebound) {
                duePath = true
                dueTime = true
            }
        }

        if (lastMatchElapsedMs > 0L && !duePath && !dueTime && !dueTurn) {
            debug = DebugSnapshot(
                active = currentEdgeId != null,
                edgeId = currentEdgeId,
                regionId = currentRegionId,
                confidence = if (currentEdgeId != null) "HOLD" else null,
                highwayClass = currentHighwayClass,
                skippedReason = "throttled",
                rankedCandidates = lastRankedCandidates,
            )
            lastOutputPose = pose
            if (freeTurns) {
                pullFreeTurnsHeadingOnThrottle(pose)?.let { pulled ->
                    lastBearingDeg = pulled.bearingDeg
                    lastOutputPose = pulled
                    return pulled
                }
            }
            return null
        }

        if (graphs.isEmpty()) {
            lastRankedCandidates = emptyList()
            debug = DebugSnapshot(skippedReason = "no_graph")
            markAttempt(pose, nowElapsedMs)
            preferFastRetry = true
            lastOutputPose = pose
            return null
        }

        val matched = matchOnce(
            pose = pose,
            graphs = graphs,
            speedKmh = speedKmh,
            nowElapsedMs = nowElapsedMs,
            dueTurn = dueTurn,
            allowAgainstOneway = allowAgainstOneway,
            allowRematchAfterLostHold = true,
            turnHint = turnHint,
        )
        val corrected = maybePromoteFree(pose, matched, dueTurn)
        // Next DR tick is applied on the output heading; oppose-detect must use that.
        if (corrected != null) {
            lastBearingDeg = corrected.bearingDeg
        }
        // Successful apply clears the fast-retry latch; rejects keep it for denser attempts.
        preferFastRetry = corrected == null
        lastOutputPose = corrected ?: pose
        return corrected
    }

    /**
     * Rails corridor: Ordinary navigator chooses forks; the sticky edge follows
     * topology hop-by-hop. Published pose follows free DR with a lateral pull
     * toward that edge (hard snap ≤10 m xt, fade to free by 25 m). Along-track
     * chord lag is not a leave. Instrument retain stays free (caller).
     */
    private fun maybeCorrectRails(
        enabled: Boolean,
        pose: RoadMatchPose,
        speedKmh: Float,
        nowElapsedMs: Long,
        allowAgainstOneway: Boolean,
        turnHint: RoadMapMatcher.TurnHint?,
    ): RoadMatchPose? {
        if (!enabled) {
            reset()
            debug = DebugSnapshot(skippedReason = "disabled", matchMode = RoadMatchMode.RAILS.name)
            return null
        }
        freePose = pose
        if (speedKmh < minSpeedKmh) {
            runStationaryOverlaySeedIfNeeded(
                pose = pose,
                nowElapsedMs = nowElapsedMs,
                allowAgainstOneway = allowAgainstOneway,
                turnHint = turnHint,
                freeTurns = false,
            )
            debug = debug.copy(
                skippedReason = if (debug.skippedReason == "no_graph") "no_graph" else "stationary",
                matchMode = RoadMatchMode.RAILS.name,
                freeActive = freePose != null,
                rankedCandidates = lastRankedCandidates,
            )
            lastOutputPose = pose
            return null
        }
        if (hasLastPose) {
            pathSinceMatchM += RoadGraph.haversineM(
                lastPoseLat,
                lastPoseLon,
                pose.lat,
                pose.lon,
            )
        }
        headingBeforeTickDeg = if (hasLastPose) lastBearingDeg else pose.bearingDeg
        lastPoseLat = pose.lat
        lastPoseLon = pose.lon
        lastBearingDeg = pose.bearingDeg
        hasLastPose = true

        val graphs = loadInstalledGraphs(pose.lat, pose.lon)
        val nav = railsNav()
        nav.maybeCorrect(
            enabled = enabled,
            pose = pose,
            speedKmh = speedKmh,
            nowElapsedMs = nowElapsedMs,
            allowAgainstOneway = allowAgainstOneway,
            turnHint = turnHint,
            turnIntent = matchTurnIntent,
            turnFlashCount = matchTurnFlashes,
            mode = RoadMatchMode.ORDINARY,
            gnssPositionTrust = matchGnssPositionTrust,
            tuning = tuning,
            instrumentStepM = matchInstrumentStepM,
        )
        val navDbg = nav.debug

        if (graphs.isEmpty() || navDbg.skippedReason == "no_graph") {
            return railsBreakHoldFree(
                pose = pose,
                nowElapsedMs = nowElapsedMs,
                reason = "no_graph",
            )
        }

        val targetBearing = railsTargetBearing(pose.bearingDeg, turnHint)
        appliedTurnHint = if (matchTurnIntent) turnHint else null

        val navBroke = navDbg.leash == "break" || navDbg.rejectReason == "leash_break"
        if (navBroke) {
            val railEdge = currentMatchedEdge(graphs)
            val circulating = circulatingArc ||
                (railEdge?.let { RoadMapMatcher.isBentOnewayArc(it) } == true)
            val proj = railEdge?.let { RoadMapMatcher.projectOntoEdge(pose.lat, pose.lon, it) }
            val residual = if (lastEdgeAzimuthDeg != null) {
                RoadMapMatcher.smallestAngleDeg(pose.bearingDeg, lastEdgeAzimuthDeg!!)
            } else {
                90f
            }
            if (railEdge == null ||
                proj == null ||
                shouldRailsCorridorBreak(
                    crossTrackM = proj.crossTrackM,
                    residualDeg = residual,
                    circulating = circulating,
                    railEdge = railEdge,
                )
            ) {
                return railsBreakHoldFree(
                    pose = pose,
                    nowElapsedMs = nowElapsedMs,
                    reason = "rails_break",
                )
            }
            // Navigator lost the sticky edge at a dead-end; keep the corridor.
        }

        if (currentEdgeId == null || topologyAnchor == null) {
            val fromNav = railsLockFromNavigator(
                pose = pose,
                graphs = graphs,
                nowElapsedMs = nowElapsedMs,
                navDbg = navDbg,
            )
            if (fromNav != null) return fromNav
            return railsFirstLock(
                pose = pose,
                graphs = graphs,
                nowElapsedMs = nowElapsedMs,
                allowAgainstOneway = allowAgainstOneway,
                turnHint = turnHint,
                targetBearing = targetBearing,
            )
        }

        if (pathSinceMatchM < tv(RoadMatchTuningKey.RAILS_MIN_ADVANCE_M) &&
            lastMatchElapsedMs > 0L
        ) {
            val held = lastOutputPose ?: pose
            debug = DebugSnapshot(
                active = true,
                edgeId = currentEdgeId,
                regionId = currentRegionId,
                confidence = "HOLD",
                highwayClass = currentHighwayClass,
                skippedReason = "throttled",
                matchMode = RoadMatchMode.RAILS.name,
                freeActive = freePose != null,
                turnHint = turnHintDebugLabel(),
            )
            return held
        }

        val railStart = topologyAnchor!!
        val navBudgetM =
            pathSinceMatchM * tv(RoadMatchTuningKey.RAILS_NAV_PATH_FACTOR) +
                tv(RoadMatchTuningKey.RAILS_NAV_PATH_SLACK_M)
        val navTarget = reachableRailsNavigatorAnchor(
            graphs = graphs,
            start = railStart,
            navDbg = navDbg,
            maxDistanceM = navBudgetM,
            allowAgainstOneway = allowAgainstOneway,
        )
        val forkBearing = railsForkBearing(
            graphs = graphs,
            navDbg = navDbg,
            navTarget = navTarget,
            fallback = targetBearing,
            allowAgainstOneway = allowAgainstOneway,
        )
        val rawPredicted = RoadMapMatcher.advanceAlongTopology(
            graphs = graphs,
            start = railStart,
            distanceM = pathSinceMatchM,
            targetBearingDeg = forkBearing,
            allowAgainstOneway = allowAgainstOneway,
        )
        val predicted = inhibitSensorForkWhileNavigatorOnCurrent(
            graphs = graphs,
            start = railStart,
            predicted = rawPredicted,
            navEdgeId = navDbg.edgeId,
            navRegionId = navDbg.regionId,
        )
        val railEdge = currentMatchedEdge(graphs)
        val circulating = circulatingManeuver ||
            (railEdge?.let { RoadMapMatcher.isBentOnewayArc(it) } == true) ||
            (predicted != null && RoadMapMatcher.isBentOnewayArc(predicted.edge))
        val navigatorSynced = syncRailToNavigator(
            graphs = graphs,
            predicted = predicted,
            navTarget = navTarget,
            circulating = circulating,
            allowAgainstOneway = allowAgainstOneway,
        )
        if (navigatorSynced == null) {
            val heldEdge = railEdge
            if (heldEdge == null || currentRegionId == null || topologyAnchor == null) {
                return railsBreakHoldFree(
                    pose = pose,
                    nowElapsedMs = nowElapsedMs,
                    reason = "rails_break",
                )
            }
            val residual = RoadMapMatcher.smallestAngleDeg(
                pose.bearingDeg,
                lastEdgeAzimuthDeg ?: pose.bearingDeg,
            )
            val proj = RoadMapMatcher.projectOntoEdge(pose.lat, pose.lon, heldEdge)
            if (proj != null &&
                shouldRailsCorridorBreak(
                    crossTrackM = proj.crossTrackM,
                    residualDeg = residual,
                    circulating = circulating,
                    railEdge = heldEdge,
                )
            ) {
                return railsBreakHoldFree(
                    pose = pose,
                    nowElapsedMs = nowElapsedMs,
                    reason = "rails_break",
                )
            }
            return railsPublishOnEdge(
                pose = pose,
                edge = heldEdge,
                regionId = currentRegionId!!,
                travelAgainst = topologyAnchor!!.travelAgainstCoords,
                nowElapsedMs = nowElapsedMs,
                switched = false,
                navLeash = navDbg.leash,
                skippedReason = "rails_dead_end",
            )
        }

        return railsPublishOnEdge(
            pose = pose,
            edge = navigatorSynced.edge,
            regionId = navigatorSynced.anchor.regionId,
            travelAgainst = navigatorSynced.anchor.travelAgainstCoords,
            nowElapsedMs = nowElapsedMs,
            switched = navigatorSynced.edge.id != currentEdgeId,
            navLeash = navDbg.leash,
        )
    }

    private fun railsNav(): RoadMatchRuntime {
        val existing = railsNavigator
        if (existing != null) return existing
        val created = RoadMatchRuntime(
            mapsDir = mapsDir,
            pathTriggerM = pathTriggerM,
            timeTriggerMs = timeTriggerMs,
            turnTriggerDeg = turnTriggerDeg,
            minSpeedKmh = minSpeedKmh,
            switchConfirmCount = switchConfirmCount,
            beamWidth = beamWidth,
            beamHoldMs = beamHoldMs,
            holdPreviousRadiusM = holdPreviousRadiusM,
            matchLagM = matchLagM,
            pathOdometerSync = pathOdometerSync,
        )
        railsNavigator = created
        return created
    }

    private fun shouldRailsCorridorBreak(
        crossTrackM: Double,
        residualDeg: Float,
        circulating: Boolean,
        railEdge: RoadEdge?,
    ): Boolean {
        if (!crossTrackM.isFinite()) return false
        val yard = railEdge != null && RoadHighwayClass.isCourtyardLike(railEdge.highwayClass)
        val limit = if (yard) {
            tv(RoadMatchTuningKey.RAILS_BREAK_YARD_XT_M)
        } else {
            tv(RoadMatchTuningKey.RAILS_BREAK_XT_M)
        }
        if (circulating) {
            return crossTrackM >= limit * 1.8 && residualDeg > 50f
        }
        if (crossTrackM < limit) return false
        return residualDeg > 35f
    }

    internal data class RailsCorridorResult(
        val pose: RoadMatchPose,
        val crossTrackM: Double,
        val alongTrackM: Double,
        val azimuthDeg: Float,
    )

    /**
     * Follow free DR along the sticky edge; pull only across-track.
     * ≤ [RAILS_HARD_SNAP_XT_M]: on-graph at the free along. 10–25 m: fade.
     * > 25 m: publish free, keep the edge unless [shouldRailsCorridorBreak].
     */
    internal fun railsCorridorPose(
        free: RoadMatchPose,
        edge: RoadEdge,
        regionId: String,
        travelAgainstCoords: Boolean,
    ): RailsCorridorResult? {
        val proj = RoadMapMatcher.projectOntoEdge(free.lat, free.lon, edge) ?: return null
        val onEdge = RoadMapMatcher.poseOnEdge(
            regionId = regionId,
            edge = edge,
            alongTrackM = proj.alongTrackM,
            travelAgainstCoords = travelAgainstCoords,
        ) ?: return null
        val xt = proj.crossTrackM
        val az = onEdge.azimuthDeg
        val hardXt = tv(RoadMatchTuningKey.RAILS_HARD_SNAP_XT_M)
        val softXt = tv(RoadMatchTuningKey.RAILS_SOFT_XT_M).coerceAtLeast(hardXt + 0.1)
        val pose = when {
            xt <= hardXt -> RoadMatchPose(
                lat = onEdge.lat,
                lon = onEdge.lon,
                bearingDeg = RoadMapMatcher.blendBearing(free.bearingDeg, az, 18f),
            )
            xt <= softXt -> {
                val fade = ((softXt - xt) / (softXt - hardXt))
                    .coerceIn(0.0, 1.0)
                val step = minOf(
                    xt * tv(RoadMatchTuningKey.RAILS_SOFT_BLEND) * fade,
                    tv(RoadMatchTuningKey.RAILS_SOFT_MAX_STEP_M),
                    xt,
                )
                val u = if (xt < 1e-6) 0.0 else (step / xt).coerceIn(0.0, 1.0)
                RoadMatchPose(
                    lat = free.lat + (proj.lat - free.lat) * u,
                    lon = free.lon + (proj.lon - free.lon) * u,
                    bearingDeg = RoadMapMatcher.blendBearing(free.bearingDeg, az, (10f * fade).toFloat()),
                )
            }
            else -> RoadMatchPose(free.lat, free.lon, free.bearingDeg)
        }
        return RailsCorridorResult(
            pose = pose,
            crossTrackM = xt,
            alongTrackM = proj.alongTrackM,
            azimuthDeg = az,
        )
    }

    private fun railsPublishOnEdge(
        pose: RoadMatchPose,
        edge: RoadEdge,
        regionId: String,
        travelAgainst: Boolean,
        nowElapsedMs: Long,
        switched: Boolean,
        navLeash: String?,
        skippedReason: String? = null,
    ): RoadMatchPose {
        val corridor = railsCorridorPose(pose, edge, regionId, travelAgainst)
            ?: return railsBreakHoldFree(pose, nowElapsedMs, "rails_break")
        val residual = RoadMapMatcher.smallestAngleDeg(pose.bearingDeg, corridor.azimuthDeg)
        val circulating = circulatingArc || RoadMapMatcher.isBentOnewayArc(edge)
        if (shouldRailsCorridorBreak(corridor.crossTrackM, residual, circulating, edge)) {
            return railsBreakHoldFree(pose, nowElapsedMs, "rails_break")
        }
        railsClearBreakGuard()
        currentEdgeId = edge.id
        currentRegionId = regionId
        currentHighwayClass = edge.highwayClass
        topologyAnchor = RoadMapMatcher.TopologyAnchor(
            regionId = regionId,
            edgeId = edge.id,
            alongTrackM = corridor.alongTrackM,
            travelAgainstCoords = travelAgainst,
        )
        topologyAnchorElapsedMs = nowElapsedMs
        lastEdgeAzimuthDeg = corridor.azimuthDeg
        pathSinceMatchM = 0.0
        markAttempt(pose, nowElapsedMs)
        lastBearingDeg = corridor.pose.bearingDeg
        lastOutputPose = corridor.pose
        if (freePose == null) freePose = pose
        noteRoadProfile(edge.highwayClass, edge.speedLimitKmh(travelAgainst))
        val against = RoadMapMatcher.isAgainstOneway(edge.oneway, travelAgainst)
        debug = DebugSnapshot(
            active = true,
            edgeId = currentEdgeId,
            regionId = currentRegionId,
            crossTrackM = corridor.crossTrackM,
            alongTrackM = corridor.alongTrackM,
            switchedEdge = switched,
            skippedReason = skippedReason,
            confidence = railsConfidence(corridor.crossTrackM),
            connected = true,
            highwayClass = currentHighwayClass,
            oneway = edge.oneway,
            againstOneway = against,
            inputBearingDeg = pose.bearingDeg,
            edgeBearingDeg = corridor.azimuthDeg,
            bearingDeltaDeg = RoadMapMatcher.smallestAngleDeg(pose.bearingDeg, corridor.azimuthDeg),
            matchMode = RoadMatchMode.RAILS.name,
            freeActive = true,
            turnHint = turnHintDebugLabel(),
            leash = when {
                corridor.crossTrackM >= tv(RoadMatchTuningKey.RAILS_HARD_SNAP_XT_M) -> "stretch"
                else -> navLeash
            },
        )
        return corridor.pose
    }

    private fun railsClearBreakGuard() {
        railsBrokenEdgeId = null
        railsBrokenRegionId = null
        railsBreakUntilElapsedMs = 0L
    }

    /**
     * Positive metres by which the free instrument pose is ahead of [rail] on
     * the same edge/travel direction. Cross-track is gated so a parallel road
     * or courtyard cannot drag the rail longitudinally.
     */
    internal fun railsForwardAlongErrorM(
        pose: RoadMatchPose,
        rail: RoadMapMatcher.TopologyPrediction,
    ): Double {
        val projected = RoadMapMatcher.projectOntoEdge(pose.lat, pose.lon, rail.edge)
            ?: return 0.0
        if (projected.crossTrackM > tv(RoadMatchTuningKey.RAILS_ALONG_LEASH_XT_M)) return 0.0
        val raw = if (rail.anchor.travelAgainstCoords) {
            rail.anchor.alongTrackM - projected.alongTrackM
        } else {
            projected.alongTrackM - rail.anchor.alongTrackM
        }
        return raw.coerceAtLeast(0.0)
    }

    /** Pull only along the current edge; never choose or cross a fork here. */
    internal fun applyRailsAlongLeash(
        rail: RoadMapMatcher.TopologyPrediction,
        forwardErrorM: Double,
    ): RoadMapMatcher.TopologyPrediction {
        val deadM = tv(RoadMatchTuningKey.RAILS_ALONG_LEASH_DEAD_M)
        if (!forwardErrorM.isFinite() || forwardErrorM <= deadM) return rail
        val pullM = ((forwardErrorM - deadM) * tv(RoadMatchTuningKey.RAILS_ALONG_LEASH_GAIN))
            .coerceAtMost(tv(RoadMatchTuningKey.RAILS_ALONG_LEASH_MAX_PULL_M))
        val edgeLengthM = RoadMapMatcher.polylineLengthM(rail.edge)
        val along = if (rail.anchor.travelAgainstCoords) {
            (rail.anchor.alongTrackM - pullM).coerceAtLeast(0.0)
        } else {
            (rail.anchor.alongTrackM + pullM).coerceAtMost(edgeLengthM)
        }
        return RoadMapMatcher.poseOnEdge(
            regionId = rail.anchor.regionId,
            edge = rail.edge,
            alongTrackM = along,
            travelAgainstCoords = rail.anchor.travelAgainstCoords,
        ) ?: rail
    }

    internal fun railsConfidence(crossTrackM: Double): String {
        val xt = if (crossTrackM.isFinite()) crossTrackM else Double.POSITIVE_INFINITY
        return when {
            xt >= tv(RoadMatchTuningKey.RAILS_SOFT_XT_M) -> RoadMatchConfidence.LOW.name
            xt >= tv(RoadMatchTuningKey.RAILS_HARD_SNAP_XT_M) -> RoadMatchConfidence.MEDIUM.name
            else -> RoadMatchConfidence.HIGH.name
        }
    }

    internal fun railsRelockCandidateOk(
        cand: RoadMapMatcher.Candidate,
        pose: RoadMatchPose,
    ): Boolean {
        if (cand.againstOneway) return false
        if (!cand.crossTrackM.isFinite() ||
            cand.crossTrackM > tv(RoadMatchTuningKey.RAILS_RELOCK_RADIUS_M)
        ) {
            return false
        }
        val residual = RoadMapMatcher.smallestAngleDeg(pose.bearingDeg, cand.edgeAzimuthDeg)
        return residual <= tf(RoadMatchTuningKey.RAILS_RELOCK_HEADING_DEG)
    }

    private fun railsForkBearing(
        graphs: List<RoadGraph>,
        navDbg: DebugSnapshot,
        navTarget: RoadMapMatcher.TopologyAnchor?,
        fallback: Float,
        allowAgainstOneway: Boolean,
    ): Float {
        val target = navTarget ?: return fallback
        val navEdgeId = target.edgeId
        val navRegion = target.regionId
        if (navEdgeId == currentEdgeId) return fallback
        val navBearing = navDbg.edgeBearingDeg ?: return fallback
        val previous = currentMatchedEdge(graphs) ?: return fallback
        val navEdge = RoadMapMatcher.findEdgeAcrossGraphs(graphs, navRegion, navEdgeId)
            ?: return fallback
        val immediate = RoadMapMatcher.isImmediateSuccessor(
            graphs = graphs,
            previous = previous,
            previousRegionId = currentRegionId ?: navRegion,
            candidate = navEdge,
            travelAgainstCoords = topologyAnchor?.travelAgainstCoords == true,
            allowAgainstOneway = allowAgainstOneway,
        )
        // A farther reachable target is valid evidence only after Rails has advanced
        // to its preceding edge. Never steer a fork by a next-next chord's bearing.
        return if (immediate) navBearing else fallback
    }

    private fun reachableRailsNavigatorAnchor(
        graphs: List<RoadGraph>,
        start: RoadMapMatcher.TopologyAnchor,
        navDbg: DebugSnapshot,
        maxDistanceM: Double,
        allowAgainstOneway: Boolean,
    ): RoadMapMatcher.TopologyAnchor? {
        val edgeId = navDbg.edgeId ?: return null
        val regionId = navDbg.regionId ?: return null
        val along = navDbg.alongTrackM ?: return null
        if (!along.isFinite()) return null
        val against = railsNav().travelAgainstCoords() ?: return null
        val target = RoadMapMatcher.TopologyAnchor(regionId, edgeId, along, against)
        val reachable = RoadMapMatcher.reachableTopologyDistanceM(
            graphs = graphs,
            start = start,
            target = target,
            maxDistanceM = maxDistanceM,
            allowAgainstOneway = allowAgainstOneway,
        )
        return if (reachable != null) target else null
    }

    /**
     * Ordinary is still on the current rail edge, so a sensor-chosen successor is
     * not hop-by-hop guidance. Hold the travel-direction endpoint until the
     * navigator actually leaves (reachable successor) or the leash breaks.
     */
    internal fun inhibitSensorForkWhileNavigatorOnCurrent(
        graphs: List<RoadGraph>,
        start: RoadMapMatcher.TopologyAnchor,
        predicted: RoadMapMatcher.TopologyPrediction?,
        navEdgeId: Long?,
        navRegionId: String?,
    ): RoadMapMatcher.TopologyPrediction? {
        if (predicted == null) return null
        if (navEdgeId == null || navEdgeId != start.edgeId) return predicted
        if (navRegionId != null && navRegionId != start.regionId) return predicted
        if (predicted.edge.id == start.edgeId) return predicted
        val heldEdge = RoadMapMatcher.findEdgeAcrossGraphs(graphs, start.regionId, start.edgeId)
            ?: return predicted
        val length = RoadMapMatcher.polylineLengthM(heldEdge)
        val holdAlong = if (start.travelAgainstCoords) 0.0 else length
        return RoadMapMatcher.poseOnEdge(
            start.regionId,
            heldEdge,
            holdAlong,
            start.travelAgainstCoords,
        ) ?: predicted
    }

    private fun syncRailToNavigator(
        graphs: List<RoadGraph>,
        predicted: RoadMapMatcher.TopologyPrediction?,
        navTarget: RoadMapMatcher.TopologyAnchor?,
        circulating: Boolean,
        allowAgainstOneway: Boolean,
    ): RoadMapMatcher.TopologyPrediction? {
        if (predicted == null) return null
        val target = navTarget ?: return predicted
        val navEdgeId = target.edgeId
        val navRegion = target.regionId
        val navAlong = target.alongTrackM
        if (navEdgeId == predicted.edge.id) {
            // Navigator lag is fork evidence only. Do not copy its along-track
            // onto the visible corridor — that was the chord-lag source.
            return predicted
        }
        val navEdge = RoadMapMatcher.findEdgeAcrossGraphs(graphs, navRegion, navEdgeId)
            ?: return predicted
        val connected = RoadMapMatcher.isConnectedFromPrevious(
            graphs = graphs,
            previousEdgeId = predicted.edge.id,
            previousRegionId = predicted.anchor.regionId,
            candidate = navEdge,
            candidateRegionId = navRegion,
        )
        val immediate = RoadMapMatcher.isImmediateSuccessor(
            graphs = graphs,
            previous = predicted.edge,
            previousRegionId = predicted.anchor.regionId,
            candidate = navEdge,
            travelAgainstCoords = predicted.anchor.travelAgainstCoords,
            allowAgainstOneway = allowAgainstOneway,
        )
        if (!connected) return predicted
        if (circulating && !immediate) {
            return predicted
        }
        // Even a reachable multi-edge Ordinary target is not copied directly: the
        // visible rail advances hop-by-hop and only syncs an adjacent target.
        return RoadMapMatcher.poseOnEdge(
            navRegion,
            navEdge,
            navAlong,
            target.travelAgainstCoords,
        ) ?: predicted
    }

    private fun railsLockFromNavigator(
        pose: RoadMatchPose,
        graphs: List<RoadGraph>,
        nowElapsedMs: Long,
        navDbg: DebugSnapshot,
    ): RoadMatchPose? {
        val edgeId = navDbg.edgeId ?: return null
        val regionId = navDbg.regionId ?: return null
        val conf = navDbg.confidence
        if (conf == null ||
            conf == RoadMatchConfidence.NONE.name ||
            conf == RoadMatchConfidence.LOW.name
        ) {
            return null
        }
        if (nowElapsedMs < railsBreakUntilElapsedMs &&
            edgeId == railsBrokenEdgeId &&
            regionId == railsBrokenRegionId
        ) {
            return null
        }
        val edge = RoadMapMatcher.findEdgeAcrossGraphs(graphs, regionId, edgeId) ?: return null
        val along = navDbg.alongTrackM ?: 0.0
        val against = railsNav().travelAgainstCoords() ?: false
        val predicted = RoadMapMatcher.poseOnEdge(regionId, edge, along, against) ?: return null
        return railsPublishOnEdge(
            pose = pose,
            edge = predicted.edge,
            regionId = predicted.anchor.regionId,
            travelAgainst = predicted.anchor.travelAgainstCoords,
            nowElapsedMs = nowElapsedMs,
            switched = true,
            navLeash = navDbg.leash,
        )
    }

    private fun railsBreakHoldFree(
        pose: RoadMatchPose,
        nowElapsedMs: Long,
        reason: String,
    ): RoadMatchPose {
        railsBrokenEdgeId = currentEdgeId
        railsBrokenRegionId = currentRegionId
        railsBreakUntilElapsedMs = nowElapsedMs + RAILS_REGRAB_GUARD_MS
        // A broken Ordinary navigator otherwise keeps its old beam/sticky edge
        // and can spend many seconds returning low_confidence. Re-seed it from
        // the free pose on the next moving tick.
        railsNavigator?.reset()
        releasePhantomPrevious()
        pathSinceMatchM = 0.0
        clearFreeParticle()
        freePose = pose
        debug = DebugSnapshot(
            active = false,
            skippedReason = reason,
            rejectReason = reason,
            matchMode = RoadMatchMode.RAILS.name,
            freeActive = true,
            inputBearingDeg = pose.bearingDeg,
            leash = "break",
        )
        lastOutputPose = pose
        markAttempt(pose, nowElapsedMs)
        return pose
    }

    private fun railsTargetBearing(
        poseBearingDeg: Float,
        turnHint: RoadMapMatcher.TurnHint?,
    ): Float {
        if (turnHint == null || !matchTurnIntent) return poseBearingDeg
        val biasMag = if (roadProfile == RoadMatchRoadProfile.HIGHWAY) {
            tf(RoadMatchTuningKey.RAILS_HIGHWAY_INTENT_BIAS_DEG)
        } else {
            tf(RoadMatchTuningKey.RAILS_TURN_HINT_BIAS_DEG)
        }
        val bias = when (turnHint) {
            RoadMapMatcher.TurnHint.Left -> -biasMag
            RoadMapMatcher.TurnHint.Right -> biasMag
        }
        return RoadMapMatcher.normalizeDeg(poseBearingDeg + bias)
    }

    private fun railsFirstLock(
        pose: RoadMatchPose,
        graphs: List<RoadGraph>,
        nowElapsedMs: Long,
        allowAgainstOneway: Boolean,
        turnHint: RoadMapMatcher.TurnHint?,
        targetBearing: Float,
    ): RoadMatchPose? {
        val recovering = railsBrokenEdgeId != null
        val searchRadius = if (recovering) {
            tv(RoadMatchTuningKey.RAILS_RELOCK_RADIUS_M)
        } else {
            tv(RoadMatchTuningKey.CANDIDATE_RADIUS_M)
        }
        val rawRanked = RoadMapMatcher.rankCandidates(
            pose = pose.copy(bearingDeg = targetBearing),
            graphs = graphs,
            previousEdgeId = null,
            previousRegionId = null,
            previousHighwayClass = null,
            limit = configuredOr(
                RoadMatchTuningKey.BEAM_WIDTH,
                beamWidth.toDouble(),
            ).toInt(),
            allowAgainstOneway = allowAgainstOneway,
            turnHint = turnHint,
            turnIntent = matchTurnIntent,
            roadProfile = roadProfile,
            searchRadiusM = searchRadius,
            normalHeadingToleranceDeg = tf(RoadMatchTuningKey.HEADING_TOLERANCE_DEG),
            gnssPositionTrust = matchGnssPositionTrust,
            gnssClassPenaltyRelax = tv(RoadMatchTuningKey.GNSS_CLASS_PENALTY_RELAX),
        )
        val guarded = recovering && nowElapsedMs < railsBreakUntilElapsedMs
        val ranked = rawRanked.filterNot { cand ->
            guarded &&
                cand.edge.id == railsBrokenEdgeId &&
                cand.regionId == railsBrokenRegionId
        }
        lastRankedCandidates = rankedCandidateRefs(ranked)
        val best = ranked.firstOrNull()
        val confidence = RoadMapMatcher.confidenceOf(ranked, firstLock = true)
        val relockOk = best != null && railsRelockCandidateOk(best, pose)
        val acceptLow = recovering && relockOk
        if (best == null ||
            (
                (confidence == RoadMatchConfidence.LOW || confidence == RoadMatchConfidence.NONE) &&
                    !acceptLow
                )
        ) {
            debug = DebugSnapshot(
                active = false,
                confidence = confidence.name,
                candidateCount = ranked.size,
                skippedReason = "low_confidence",
                rejectReason = "low_confidence",
                matchMode = RoadMatchMode.RAILS.name,
                freeActive = freePose != null,
                rankedCandidates = lastRankedCandidates,
                inputBearingDeg = pose.bearingDeg,
            )
            lastOutputPose = pose
            return null
        }
        // Prefer major carriageways on first rails lock; yards need an explicit break later.
        if (RoadHighwayClass.isCourtyardLike(best.edge.highwayClass) &&
            ranked.any {
                !it.againstOneway &&
                    RoadMapMatcher.isParallelCorrectClass(it.edge.highwayClass) &&
                    it.crossTrackM <= RoadMapMatcher.PARALLEL_CORRECT_MAX_XT_M
            }
        ) {
            val major = ranked.first {
                !it.againstOneway &&
                    RoadMapMatcher.isParallelCorrectClass(it.edge.highwayClass) &&
                    it.crossTrackM <= RoadMapMatcher.PARALLEL_CORRECT_MAX_XT_M
            }
            return railsCommitCandidate(major, pose, nowElapsedMs)
        }
        return railsCommitCandidate(best, pose, nowElapsedMs)
    }

    private fun railsCommitCandidate(
        cand: RoadMapMatcher.Candidate,
        pose: RoadMatchPose,
        nowElapsedMs: Long,
    ): RoadMatchPose = railsPublishOnEdge(
        pose = pose,
        edge = cand.edge,
        regionId = cand.regionId,
        travelAgainst = cand.travelAgainstCoords,
        nowElapsedMs = nowElapsedMs,
        switched = true,
        navLeash = null,
    )

    private fun activePathTriggerM(): Double = when {
        pendingEdgeId != null -> SWITCH_PENDING_PATH_M
        preferFastRetry || currentEdgeId == null -> RECOVER_PATH_M
        else -> configuredOr(RoadMatchTuningKey.PATH_TRIGGER_M, pathTriggerM)
    }

    private fun activeTimeTriggerMs(): Long = when {
        pendingEdgeId != null -> SWITCH_PENDING_TIME_MS
        preferFastRetry || currentEdgeId == null -> RECOVER_TIME_MS
        else -> configuredOr(
            RoadMatchTuningKey.TIME_TRIGGER_MS,
            timeTriggerMs.toDouble(),
        ).toLong()
    }

    private fun effectiveSwitchConfirmCount(): Int = configuredOr(
        RoadMatchTuningKey.SWITCH_CONFIRM_COUNT,
        switchConfirmCount.toDouble(),
    ).toInt()

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
        turnHint: RoadMapMatcher.TurnHint?,
    ): RoadMatchPose? {
        val matchPose = rankingPose(
            pose = pose,
            graphs = graphs,
            dueTurn = dueTurn,
            allowAgainstOneway = allowAgainstOneway,
            speedKmh = speedKmh,
        )
        val lagM = RoadGraph.haversineM(matchPose.lat, matchPose.lon, pose.lat, pose.lon)
        lastMatchLagM = lagM
        val predicted = topologyPrediction(
            graphs = graphs,
            distanceM = (pathSinceMatchM + lookAheadDistanceM(speedKmh) - lagM).coerceAtLeast(0.0),
            bearingDeg = pose.bearingDeg,
            allowAgainstOneway = allowAgainstOneway,
        )
        val topologyExpected =
            if (lagM >= RoadMapMatcher.MATCH_LAG_MIN_TRAIL_M &&
                predicted != null &&
                currentEdgeId != null &&
                predicted.edge.id != currentEdgeId
            ) {
                emptySet()
            } else {
                predicted?.let { setOf(it.anchor.regionId to it.anchor.edgeId) }.orEmpty()
            }
        matchTravelBearingDeg = pose.bearingDeg
        matchTopologyExpected = topologyExpected
        matchTurnHint = turnHint
        matchSpeedKmh = speedKmh
        circulatingArc = currentMatchedEdge(graphs)?.let { RoadMapMatcher.isBentOnewayArc(it) } == true
        // Reverse-slide detection on ordinary two-way roads is a false positive
        // at ~90° (field 122235 @ 54447): it armed circulatingHop + clampReverseSlide
        // and froze the rail while the car left. Clamp / 1-confirm hop stay on
        // bent oneway arcs only.
        circulatingManeuver = circulatingArc
        var ranked = RoadMapMatcher.rankCandidates(
            pose = matchPose,
            graphs = graphs,
            previousEdgeId = currentEdgeId,
            previousRegionId = currentRegionId,
            previousHighwayClass = currentHighwayClass,
            hypothesisEdgeIds = activeHypotheses(nowElapsedMs),
            limit = configuredOr(
                RoadMatchTuningKey.BEAM_WIDTH,
                beamWidth.toDouble(),
            ).toInt(),
            allowAgainstOneway = allowAgainstOneway,
            topologyLookAheadEdgeIds = topologyExpected,
            turnHint = turnHint,
            turnIntent = matchTurnIntent,
            roadProfile = roadProfile,
            circulatingManeuver = circulatingManeuver,
            searchRadiusM = tv(RoadMatchTuningKey.CANDIDATE_RADIUS_M),
            normalHeadingToleranceDeg = tf(RoadMatchTuningKey.HEADING_TOLERANCE_DEG),
            gnssPositionTrust = matchGnssPositionTrust,
            gnssClassPenaltyRelax = tv(RoadMatchTuningKey.GNSS_CLASS_PENALTY_RELAX),
        )
        val circulatingArc = this.circulatingArc ||
            currentMatchedEdge(graphs)?.let { RoadMapMatcher.isBentOnewayArc(it) } == true
        val forkBiasTuning = TurnSignalForkBiasTuning.from(tuning)
        val forkBiasEnabled = tuning.bool(RoadMatchTuningKey.TS_FORK_BIAS_ENABLED)
        val effectiveTurnIntent = when {
            !forkBiasEnabled -> false
            tuning.bool(RoadMatchTuningKey.TS_INTENTIONAL_ONLY) -> matchTurnIntent
            else -> turnHint != null
        }
        val minToward = RoadMapMatcher.turnSignalTowardMinDeg(
            roadProfile,
            effectiveTurnIntent,
            forkBiasTuning,
        )
        val stickyOk = currentEdgeId != null || (
            tuning.bool(RoadMatchTuningKey.TS_BIAS_WITHOUT_STICKY) &&
                ranked.any {
                    it.crossTrackM <= tv(RoadMatchTuningKey.TS_BIAS_WITHOUT_STICKY_MAX_XT_M)
                }
            )
        val towardHint = stickyOk &&
            turnHint != null &&
            effectiveTurnIntent &&
            RoadMapMatcher.turnSignalTowardExists(ranked, pose.bearingDeg, turnHint, minToward)
        // Full hint (drop look-ahead, inhibit heading, hold past-end) only off the ring.
        // On a bent oneway arc keep a light ranking nudge so a real same-node exit
        // can still win when heading is already that way.
        turnHintActive = towardHint && !circulatingArc
        appliedTurnHint = if (towardHint) turnHint else null
        if (towardHint) {
            val hint = turnHint!!
            if (turnHintActive && topologyExpected.isNotEmpty()) {
                // Look-ahead along travel predicts the through-road; drop it once the
                // stalk has a real toward-candidate, then apply fork bias.
                ranked = RoadMapMatcher.rankCandidates(
                    pose = matchPose,
                    graphs = graphs,
                    previousEdgeId = currentEdgeId,
                    previousRegionId = currentRegionId,
                    previousHighwayClass = currentHighwayClass,
                    hypothesisEdgeIds = activeHypotheses(nowElapsedMs),
                    limit = configuredOr(
                        RoadMatchTuningKey.BEAM_WIDTH,
                        beamWidth.toDouble(),
                    ).toInt(),
                    allowAgainstOneway = allowAgainstOneway,
                    topologyLookAheadEdgeIds = emptySet(),
                    turnHint = hint,
                    turnIntent = effectiveTurnIntent,
                    roadProfile = roadProfile,
                    circulatingManeuver = circulatingManeuver,
                    searchRadiusM = tv(RoadMatchTuningKey.CANDIDATE_RADIUS_M),
                    normalHeadingToleranceDeg = tf(RoadMatchTuningKey.HEADING_TOLERANCE_DEG),
                    gnssPositionTrust = matchGnssPositionTrust,
                    gnssClassPenaltyRelax = tv(RoadMatchTuningKey.GNSS_CLASS_PENALTY_RELAX),
                )
            }
            ranked = RoadMapMatcher.applyTurnSignalForkBias(
                ranked = ranked,
                travelBearingDeg = pose.bearingDeg,
                hint = hint,
                previousEdgeId = currentEdgeId,
                previousRegionId = currentRegionId,
                weight = if (circulatingArc) forkBiasTuning.arcWeight else 1.0,
                turnIntent = effectiveTurnIntent,
                roadProfile = roadProfile,
                forkBias = forkBiasTuning,
            )
        }
        ranked = RoadMapMatcher.preferImmediateSuccessor(
            ranked = ranked,
            graphs = graphs,
            previous = currentMatchedEdge(graphs),
            previousRegionId = currentRegionId,
            travelAgainstCoords = topologyAnchor?.travelAgainstCoords == true,
            travelBearingDeg = pose.bearingDeg,
            allowAgainstOneway = allowAgainstOneway,
        )
        lastRankedCandidates = rankedCandidateRefs(ranked)
        updateJunction(pose, graphs, ranked, allowAgainstOneway, dueTurn)
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
            matchPose = matchPose,
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
                matchPose, graphs, dueTurn = dueTurn, allowAgainstOneway = allowAgainstOneway,
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
            val corridor = if (skipCorridor || leashState == "stretch") {
                null
            } else {
                connectedCorridorCorrection(
                    pose = pose,
                    graphs = graphs,
                    nowElapsedMs = nowElapsedMs,
                    allowAgainstOneway = allowAgainstOneway,
                )
            }
            if (corridor != null) return corridor
            tryRestoreParentAfterLinkLoss(pose, graphs, nowElapsedMs, dueTurn)?.let { return it }
            tryLeashBreakFromLostHold(pose, graphs, nowElapsedMs, dueTurn)?.let { return it }
            if (allowRematchAfterLostHold && releasePhantomPrevious()) {
                return matchOnce(
                    pose = pose,
                    graphs = graphs,
                    speedKmh = speedKmh,
                    nowElapsedMs = nowElapsedMs,
                    dueTurn = dueTurn,
                    allowAgainstOneway = allowAgainstOneway,
                    allowRematchAfterLostHold = false,
                    turnHint = turnHint,
                )
            }
            lastRankedCandidates = emptyList()
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
                matchLagM = lastMatchLagM,
                turnHint = turnHintDebugLabel(),
            )
            markAttempt(pose, nowElapsedMs)
            return null
        }

        val firstLock = currentEdgeId == null
        val confidence = RoadMapMatcher.confidenceOf(ranked, firstLock = firstLock)
        val rawBest = ranked.first()
        val switchReject = switchRejectReason(rawBest, allowAgainstOneway, nowElapsedMs)

        if (confidence == RoadMatchConfidence.LOW || confidence == RoadMatchConfidence.NONE) {
            // Prefer staying on the last good edge over freezing pure DR.
            // HOLD_EDGE inhibits heading only while residual is still large;
            // when course is already close to the edge, softCorrect may pull.
            val held = holdPreviousEdge(
                matchPose, graphs, dueTurn = dueTurn, allowAgainstOneway = allowAgainstOneway,
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
            // even while confidence is still LOW. Same after lost sticky: heading
            // already matches but xt is still large (`073412` 07:58).
            val residualToBest = RoadMapMatcher.smallestAngleDeg(pose.bearingDeg, rawBest.edgeAzimuthDeg)
            // Never heading-regrab onto against-oneway while a forward scheme is
            // expected — dual-carriageway wrong lane was sticky via this path.
            if ((allowAgainstOneway || !rawBest.againstOneway) &&
                RoadMatchLeashMath.shouldRegrabByHeading(
                    residualToBestDeg = residualToBest,
                    crossTrackM = rawBest.crossTrackM,
                    switchRejected = switchReject != null,
                ) &&
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
                    dueTurn = dueTurn,
                )
            }
            tryRestoreParentAfterLinkLoss(pose, graphs, nowElapsedMs, dueTurn)?.let { return it }
            tryLeashBreakFromLostHold(pose, graphs, nowElapsedMs, dueTurn)?.let { return it }
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
                    turnHint = turnHint,
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
                matchPose, graphs, dueTurn = dueTurn, allowAgainstOneway = allowAgainstOneway,
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
                    turnHint = turnHint,
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
                matchPose,
                graphs,
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
    /**
     * FreeTurns: drop sticky edge before a 3+ line junction, and optionally while
     * a turn signal is active (stalk unbind). Junction release keeps existing
     * rebind-after-node path; stalk release rebinds [FREE_STALK_REBIND_AFTER_M]
     * (or [ORDINARY_STALK_REBIND_AFTER_M] in Ordinary) after the signal goes idle.
     *
     * Ordinary: only the stalk path runs when city and/or highway stalk toggles
     * are on (experimental exit / cloverleaf test); junction unbind stays
     * FreeTurns-only.
     *
     * @param freeTurns true = FreeTurns mode (junction + FreeTurns stalk keys);
     *   false = Ordinary stalk-only using ORDINARY_STALK_* keys.
     * @return true on the tick the release window ends (caller should rematch now).
     */
    private fun updateFreeTurnsGate(
        pose: RoadMatchPose,
        graphs: List<RoadGraph>,
        stepM: Double,
        allowAgainstOneway: Boolean,
        turnHint: RoadMapMatcher.TurnHint?,
        speedKmh: Float,
        freeTurns: Boolean,
    ): Boolean {
        val highwayProfile = roadProfile == RoadMatchRoadProfile.HIGHWAY
        val stalkEnabled: Boolean
        val intentionalOnly: Boolean
        val blockHighway: Boolean
        val minSpeedKmh: Float
        val rebindAfterM: Double
        if (freeTurns) {
            stalkEnabled = tuning.bool(RoadMatchTuningKey.FREE_STALK_UNBIND_ENABLED)
            intentionalOnly = tuning.bool(RoadMatchTuningKey.FREE_STALK_UNBIND_INTENTIONAL_ONLY)
            blockHighway = tuning.bool(RoadMatchTuningKey.FREE_STALK_UNBIND_BLOCK_HIGHWAY)
            minSpeedKmh = tf(RoadMatchTuningKey.FREE_STALK_UNBIND_MIN_SPEED_KMH)
            rebindAfterM = tv(RoadMatchTuningKey.FREE_STALK_REBIND_AFTER_M)
        } else {
            stalkEnabled = ordinaryStalkUnbindEnabledForProfile(highwayProfile)
            intentionalOnly = tuning.bool(RoadMatchTuningKey.ORDINARY_STALK_UNBIND_INTENTIONAL_ONLY)
            // Profile already selected via city/highway toggles.
            blockHighway = false
            minSpeedKmh = tf(RoadMatchTuningKey.ORDINARY_STALK_UNBIND_MIN_SPEED_KMH)
            rebindAfterM = tv(RoadMatchTuningKey.ORDINARY_STALK_REBIND_AFTER_M)
        }
        val stalkQualifies = RoadMatchFreeTurnsMath.stalkUnbindQualifies(
            enabled = stalkEnabled,
            turnHintPresent = turnHint != null,
            turnIntent = matchTurnIntent,
            intentionalOnly = intentionalOnly,
            blockHighway = blockHighway,
            highwayProfile = highwayProfile,
            speedKmh = speedKmh,
            minSpeedKmh = minSpeedKmh,
        )
        if (freeTurnsReleased) {
            when (freeTurnsReleaseKind) {
                RoadMatchFreeTurnsMath.ReleaseKind.STALK -> {
                    if (stalkQualifies) {
                        // Still signalling — hold release and reset path-after-off.
                        freeTurnsPathSinceReleaseM = 0.0
                        return false
                    }
                    if (stepM.isFinite() && stepM > 0.0) {
                        freeTurnsPathSinceReleaseM += stepM
                    }
                    if (RoadMatchFreeTurnsMath.shouldRebindAfterStalkOff(
                            freeTurnsPathSinceReleaseM,
                            rebindAfterM,
                        )
                    ) {
                        freeTurnsReleased = false
                        freeTurnsReleaseKind = null
                        freeTurnsPathSinceReleaseM = 0.0
                        freeTurnsRemainingAtReleaseM = 0.0
                        return true
                    }
                    return false
                }
                RoadMatchFreeTurnsMath.ReleaseKind.JUNCTION, null -> {
                    if (!freeTurns) {
                        // Ordinary never starts a junction release; clear orphan state.
                        freeTurnsReleased = false
                        freeTurnsReleaseKind = null
                        freeTurnsPathSinceReleaseM = 0.0
                        freeTurnsRemainingAtReleaseM = 0.0
                        return true
                    }
                    if (stepM.isFinite() && stepM > 0.0) {
                        freeTurnsPathSinceReleaseM += stepM
                    }
                    if (RoadMatchFreeTurnsMath.shouldRebind(
                            freeTurnsPathSinceReleaseM,
                            freeTurnsRemainingAtReleaseM,
                            tv(RoadMatchTuningKey.FREE_REBIND_AFTER_M),
                        )
                    ) {
                        freeTurnsReleased = false
                        freeTurnsReleaseKind = null
                        freeTurnsPathSinceReleaseM = 0.0
                        freeTurnsRemainingAtReleaseM = 0.0
                        return true
                    }
                    return false
                }
            }
        }
        // Prefer stalk unbind when enabled — covers forks where junction unbind
        // would also fire, and keeps DR free until the signal is cancelled.
        if (stalkQualifies) {
            freeTurnsReleased = true
            freeTurnsReleaseKind = RoadMatchFreeTurnsMath.ReleaseKind.STALK
            freeTurnsRemainingAtReleaseM = 0.0
            freeTurnsPathSinceReleaseM = 0.0
            releasePhantomPrevious()
            hypotheses = emptySet()
            return false
        }
        if (!freeTurns) return false
        val edge = currentMatchedEdge(graphs) ?: return false
        val regionId = currentRegionId ?: return false
        val against = topologyAnchor?.travelAgainstCoords == true
        val along = RoadMapMatcher.projectOntoEdge(pose.lat, pose.lon, edge)?.alongTrackM
            ?: topologyAnchor?.alongTrackM
            ?: return false
        val remaining = RoadMapMatcher.remainingToComplexJunctionM(
            graphs = graphs,
            regionId = regionId,
            edge = edge,
            alongTrackM = along,
            travelAgainstCoords = against,
            allowAgainstOneway = allowAgainstOneway,
            maxLookM = tv(RoadMatchTuningKey.FREE_UNBIND_BEFORE_M),
            minIncidentLines = ti(RoadMatchTuningKey.FREE_MIN_INCIDENT_LINES),
        )
        if (!RoadMatchFreeTurnsMath.shouldRelease(
                remaining,
                tv(RoadMatchTuningKey.FREE_UNBIND_BEFORE_M),
            )
        ) {
            return false
        }
        freeTurnsReleased = true
        freeTurnsReleaseKind = RoadMatchFreeTurnsMath.ReleaseKind.JUNCTION
        freeTurnsRemainingAtReleaseM = remaining ?: 0.0
        freeTurnsPathSinceReleaseM = 0.0
        releasePhantomPrevious()
        hypotheses = emptySet()
        return false
    }

    private fun ordinaryStalkUnbindAnyEnabled(): Boolean =
        tuning.bool(RoadMatchTuningKey.ORDINARY_STALK_UNBIND_CITY) ||
            tuning.bool(RoadMatchTuningKey.ORDINARY_STALK_UNBIND_HIGHWAY)

    private fun ordinaryStalkUnbindEnabledForProfile(highwayProfile: Boolean): Boolean =
        if (highwayProfile) {
            tuning.bool(RoadMatchTuningKey.ORDINARY_STALK_UNBIND_HIGHWAY)
        } else {
            tuning.bool(RoadMatchTuningKey.ORDINARY_STALK_UNBIND_CITY)
        }

    /** Heading-only pull toward the selected edge between match ticks. */
    private fun pullFreeTurnsHeadingOnThrottle(pose: RoadMatchPose): RoadMatchPose? {
        val target = lastEdgeAzimuthDeg ?: return null
        val residual = RoadMapMatcher.smallestAngleDeg(pose.bearingDeg, target)
        if (residual <= 0.05f) return null
        if (residual > tf(RoadMatchTuningKey.FREE_THROTTLE_MAX_RESIDUAL_DEG)) return null
        val bearing = RoadMapMatcher.blendBearing(
            pose.bearingDeg,
            target,
            tf(RoadMatchTuningKey.FREE_THROTTLE_BEARING_DEG),
        )
        return pose.copy(bearingDeg = bearing)
    }

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
        pathOdoAnchor = null
        pathOdoM = 0.0
        pathOdoLastGapM = null
        lastPastEndEdgeId = null
        lastPastEndRegionId = null
        lastPastEndXt = null
        exhaustedEdgeId = null
        exhaustedRegionId = null
        lastEdgeAzimuthDeg = null
        return true
    }

    private fun lookAheadDistanceM(speedKmh: Float): Double {
        val minM = tv(RoadMatchTuningKey.LOOK_AHEAD_MIN_M)
        val maxM = tv(RoadMatchTuningKey.LOOK_AHEAD_MAX_M)
        return (speedKmh.coerceAtLeast(0f) / 3.6 * tv(RoadMatchTuningKey.LOOK_AHEAD_SECONDS))
            .coerceIn(minOf(minM, maxM), maxOf(minM, maxM))
    }

    private fun applyPathOdometerSync(
        matched: RoadMatchPose,
        snap: RoadMapMatcher.Candidate,
        switched: Boolean,
        dueTurn: Boolean,
        stretching: Boolean,
        graphs: List<RoadGraph>,
    ): RoadMatchPose {
        pathOdoLastGapM = null
        val anchorNow = topologyAnchor ?: return matched
        // Mid-turn / leaving: keep lateral softCorrect only; odometer pull fights the manoeuvre.
        if (dueTurn || stretching) return matched
        if (switched && snap.connectedFromPrevious != true) {
            pathOdoAnchor = anchorNow
            pathOdoM = 0.0
            return matched
        }
        if (pathOdoAnchor == null) {
            pathOdoAnchor = anchorNow
            pathOdoM = 0.0
            return matched
        }
        val predicted = RoadMapMatcher.advanceAlongTopology(
            graphs = graphs,
            start = pathOdoAnchor!!,
            distanceM = pathOdoM,
            targetBearingDeg = matched.bearingDeg,
            allowAgainstOneway = false,
        )
        if (predicted == null) {
            pathOdoAnchor = anchorNow
            pathOdoM = 0.0
            return matched
        }
        val gap = RoadGraph.haversineM(matched.lat, matched.lon, predicted.lat, predicted.lon)
        pathOdoLastGapM = gap
        if (gap > PATH_ODO_SYNC_MAX_GAP_M) {
            pathOdoAnchor = anchorNow
            pathOdoM = 0.0
            return matched
        }
        val deadM = tv(RoadMatchTuningKey.PATH_ODO_SYNC_DEAD_M)
        if (gap < deadM) return matched
        if (RoadMapMatcher.smallestAngleDeg(matched.bearingDeg, predicted.azimuthDeg) >
            PATH_ODO_SYNC_MAX_HEADING_DEG
        ) {
            return matched
        }
        val toward = RoadMapMatcher.bearingBetweenDeg(
            matched.lat,
            matched.lon,
            predicted.lat,
            predicted.lon,
        )
        if (RoadMapMatcher.smallestAngleDeg(matched.bearingDeg, toward) > PATH_ODO_SYNC_FORWARD_DEG) {
            return matched
        }
        val maxStep = tv(RoadMatchTuningKey.PATH_ODO_SYNC_MAX_STEP_M)
        val step = minOf(gap, maxStep)
        val t = (step / gap).coerceIn(0.0, 1.0)
        val out = RoadMatchPose(
            lat = matched.lat + (predicted.lat - matched.lat) * t,
            lon = matched.lon + (predicted.lon - matched.lon) * t,
            bearingDeg = matched.bearingDeg,
        )
        if (gap - step <= 0.5) {
            pathOdoAnchor = predicted.anchor
            pathOdoM = 0.0
        }
        return out
    }
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
        if (RoadMapMatcher.smallestAngleDeg(pose.bearingDeg, predicted.azimuthDeg) >
            CORRIDOR_HEADING_ABORT_DEG
        ) {
            return null
        }
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
            matchLagM = lastMatchLagM,
            turnHint = turnHintDebugLabel(),
            rankedCandidates = lastRankedCandidates,
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
        matchLagM = lastMatchLagM,
        turnHint = turnHintDebugLabel(),
        rankedCandidates = lastRankedCandidates,
    )

    private fun turnHintDebugLabel(): String? = when (appliedTurnHint) {
        RoadMapMatcher.TurnHint.Left -> "L"
        RoadMapMatcher.TurnHint.Right -> "R"
        null -> null
    }

    private fun rankedCandidateRefs(
        ranked: List<RoadMapMatcher.Candidate>,
    ): List<RankedCandidateRef> {
        if (ranked.isEmpty()) return emptyList()
        val limit = beamWidth.coerceAtMost(RoadMatchOverlayBuilder.MAX_RANKED_CANDIDATES)
        return ranked.take(limit).mapIndexed { index, cand ->
            RankedCandidateRef(
                edgeId = cand.edge.id,
                regionId = cand.regionId,
                score = cand.score,
                rank = index + 1,
            )
        }
    }

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

    private fun clampReverseSlide(
        pose: RoadMatchPose,
        snap: RoadMapMatcher.Candidate,
        switched: Boolean,
    ): RoadMapMatcher.Candidate {
        if (!circulatingManeuver || switched) return snap
        val anchor = topologyAnchor ?: return snap
        if (snap.edge.id != currentEdgeId || snap.regionId != currentRegionId) return snap
        val against = anchor.travelAgainstCoords
        if (snap.travelAgainstCoords != against) {
            val forced = RoadMapMatcher.poseOnEdge(
                snap.regionId, snap.edge, anchor.alongTrackM, against,
            ) ?: return snap
            return snap.copy(
                alongTrackM = forced.anchor.alongTrackM,
                projLat = forced.lat,
                projLon = forced.lon,
                edgeAzimuthDeg = forced.azimuthDeg,
                travelAgainstCoords = against,
                againstOneway = RoadMapMatcher.isAgainstOneway(snap.edge.oneway, against),
                crossTrackM = RoadGraph.haversineM(pose.lat, pose.lon, forced.lat, forced.lon),
            )
        }
        val last = anchor.alongTrackM
        val clamped = if (against) {
            minOf(snap.alongTrackM, last)
        } else {
            maxOf(snap.alongTrackM, last)
        }
        if (abs(clamped - snap.alongTrackM) < 0.5) return snap
        val forced = RoadMapMatcher.poseOnEdge(snap.regionId, snap.edge, clamped, against)
            ?: return snap
        return snap.copy(
            alongTrackM = clamped,
            projLat = forced.lat,
            projLon = forced.lon,
            edgeAzimuthDeg = forced.azimuthDeg,
            crossTrackM = RoadGraph.haversineM(pose.lat, pose.lon, forced.lat, forced.lon),
        )
    }

    private fun currentMatchedEdge(graphs: List<RoadGraph>): RoadEdge? {
        val edgeId = currentEdgeId ?: return null
        val regionId = currentRegionId
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
        val snap0 = RoadMapMatcher.candidateAtPose(pose, cand)
        val switched = switchedOverride ?: (
            currentEdgeId != null &&
                (snap0.edge.id != currentEdgeId || snap0.regionId != currentRegionId)
            )
        val snap = clampReverseSlide(pose, snap0, switched)
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
        val residual = RoadMapMatcher.smallestAngleDeg(pose.bearingDeg, snap.edgeAzimuthDeg)
        val holding = confidence == "HOLD_EDGE"
        val prevResidual = RoadMapMatcher.smallestAngleDeg(headingBeforeTickDeg, snap.edgeAzimuthDeg)
        val headingAway = residual > prevResidual + HEADING_AWAY_EPS_DEG
        val leavingSameEdge = !switched &&
            headingAway && residual >= LEAVING_EDGE_RESIDUAL_DEG
        // Same-edge catch-up on a slip road follows the ramp's changing azimuth.
        // Field 142148: straight motorway, early trunk_link HIGH, 14°/tick chase
        // of the cloverleaf while wheel/gyro were quiet → 2 km shadow.
        // Keep catch-up on ordinary roads (124442 tertiary undershoot) and on the
        // confirmed switch tick onto a link (real exit / first lock).
        val sameEdgeLink = !switched && RoadHighwayClass.isLink(snap.edge.highwayClass)
        val drYaw = RoadMapMatcher.signedAngleDeg(headingBeforeTickDeg, pose.bearingDeg)
        val towardEdge = RoadMapMatcher.signedAngleDeg(pose.bearingDeg, snap.edgeAzimuthDeg)
        val sensorsOpposeEdge =
            abs(drYaw) >= RoadMapMatcher.SENSOR_OPPOSE_MIN_DEG &&
                abs(towardEdge) >= RoadMapMatcher.SENSOR_OPPOSE_MIN_DEG &&
                drYaw * towardEdge < 0f
        val inhibitHeading = RoadMatchLeashMath.shouldInhibitHeadingPull(
            residualDeg = residual,
            holding = holding,
            leavingSameEdge = leavingSameEdge,
            dueTurn = dueTurn,
            switched = switched,
            sameEdgeLink = sameEdgeLink,
            sensorsOpposeEdge = sensorsOpposeEdge,
            turnHintActive = turnHintActive,
        )
        val catchUpHeading = !inhibitHeading
        val courtyardLike = RoadHighwayClass.isCourtyardLike(snap.edge.highwayClass)
        val stretching = !switched &&
            RoadMatchLeashMath.shouldStretch(
                leavingSameEdge = leavingSameEdge,
                sensorsOppose = sensorsOpposeEdge,
                drYawAbs = abs(drYaw),
                crossTrackM = snap.crossTrackM,
                dueTurn = dueTurn,
            )
        if (stretching) {
            val step = lastOutputPose?.let {
                RoadGraph.haversineM(it.lat, it.lon, pose.lat, pose.lon)
            } ?: 0.0
            leavingPathM += step
            val growing = RoadMatchLeashMath.xtGrowing(lastLeaveXt, snap.crossTrackM)
            lastLeaveXt = snap.crossTrackM
            skipCorridor = true
            val inReturnGuard = nowElapsedMs < abandonGuardUntilElapsedMs &&
                abandonedEdgeId != null
            if (!inReturnGuard &&
                RoadMatchLeashMath.shouldBreakLeash(
                    snap.crossTrackM,
                    leavingPathM,
                    growing,
                    turning = dueTurn,
                    courtyardLike = courtyardLike,
                    breakXtM = tv(RoadMatchTuningKey.LEASH_BREAK_XT_M),
                    breakYardXtM = tv(RoadMatchTuningKey.LEASH_BREAK_YARD_XT_M),
                    breakPathM = tv(RoadMatchTuningKey.LEASH_BREAK_PATH_M),
                )
            ) {
                tryRestoreParentAfterLinkLoss(
                    pose,
                    loadInstalledGraphs(pose.lat, pose.lon),
                    nowElapsedMs,
                    dueTurn,
                )?.let { return it }
                leashState = "break"
                releasePhantomPrevious()
                leavingPathM = 0.0
                lastLeaveXt = null
                freePose = null
                junctionActive = false
                junctionPathM = 0.0
                markAttempt(pose, nowElapsedMs)
                pathSinceMatchM = 0.0
                debug = DebugSnapshot(
                    active = false,
                    crossTrackM = snap.crossTrackM,
                    alongTrackM = snap.alongTrackM,
                    confidence = RoadMatchConfidence.NONE.name,
                    candidateCount = candidateCount,
                    runnerUpScore = runnerUpScore,
                    highwayClass = snap.edge.highwayClass,
                    candidateEdgeId = snap.edge.id,
                    candidateHighwayClass = snap.edge.highwayClass,
                    candidateCrossTrackM = snap.crossTrackM,
                    inputBearingDeg = pose.bearingDeg,
                    edgeBearingDeg = snap.edgeAzimuthDeg,
                    bearingDeltaDeg = 0f,
                    turnActive = true,
                    skippedReason = "leash_break",
                    rejectReason = "leash_break",
                    matchLagM = lastMatchLagM,
                    turnHint = turnHintDebugLabel(),
                    rankedCandidates = lastRankedCandidates,
                    leash = "break",
                )
                return pose
            }
            leashState = "stretch"
        } else {
            if (leashState == "stretch" || leashState == "break") leashState = "retract"
            leavingPathM = 0.0
            lastLeaveXt = null
            skipCorridor = false
        }
        val holdVertex = dueTurn && RoadMapMatcher.isAlongAtTravelEnd(snap)
        val corrected = RoadMapMatcher.softCorrect(
            pose,
            snap,
            turnActive = dueTurn || inhibitHeading,
            catchUpHeading = catchUpHeading,
            lateralSnap = !stretching && !holdVertex,
            maxAlongStepM = tv(RoadMatchTuningKey.MAX_ALONG_STEP_M),
            maxBearingStepDeg = tf(RoadMatchTuningKey.MAX_BEARING_STEP_DEG),
            maxBearingStepCatchupDeg = if (matchFreeTurns) {
                tf(RoadMatchTuningKey.FREE_BEARING_CATCHUP_DEG)
            } else {
                tf(RoadMatchTuningKey.MAX_BEARING_CATCHUP_DEG)
            },
            bearingInhibitResidualDeg = tf(RoadMatchTuningKey.BEARING_INHIBIT_DEG),
            crossBlend = tv(RoadMatchTuningKey.CROSS_BLEND),
            maxCrossStepM = tv(RoadMatchTuningKey.MAX_CROSS_STEP_M),
            pastEndReleaseM = tv(RoadMatchTuningKey.PAST_END_RELEASE_M),
        )
        currentEdgeId = snap.edge.id
        currentRegionId = snap.regionId
        currentHighwayClass = snap.edge.highwayClass
        if (!RoadHighwayClass.isLink(snap.edge.highwayClass)) {
            lastNonLinkEdgeId = snap.edge.id
            lastNonLinkRegionId = snap.regionId
            lastNonLinkHighwayClass = snap.edge.highwayClass
            preferParentAfterLink = false
        } else {
            preferParentAfterLink = true
        }
        lastEdgeAzimuthDeg = snap.edgeAzimuthDeg
        val coordsAzimuth = RoadMapMatcher.projectOntoEdge(
            snap.projLat,
            snap.projLon,
            snap.edge,
        )?.azimuthDeg ?: snap.edgeAzimuthDeg
        val travelAgainstCoords =
            RoadMapMatcher.smallestAngleDeg(
                snap.edgeAzimuthDeg,
                RoadMapMatcher.normalizeDeg(coordsAzimuth + 180f),
            ) < RoadMapMatcher.smallestAngleDeg(snap.edgeAzimuthDeg, coordsAzimuth)
        noteRoadProfile(snap.edge.highwayClass, snap.edge.speedLimitKmh(travelAgainstCoords))
        topologyAnchor = RoadMapMatcher.TopologyAnchor(
            regionId = snap.regionId,
            edgeId = snap.edge.id,
            alongTrackM = snap.alongTrackM,
            travelAgainstCoords = travelAgainstCoords,
        )
        topologyAnchorElapsedMs = nowElapsedMs
        val synced = if (pathOdoSyncEnabled()) {
            applyPathOdometerSync(
                matched = corrected,
                snap = snap,
                switched = switched,
                dueTurn = dueTurn,
                stretching = stretching,
                graphs = loadInstalledGraphs(pose.lat, pose.lon),
            )
        } else {
            corrected
        }
        val bearingDelta = RoadMapMatcher.smallestAngleDeg(pose.bearingDeg, synced.bearingDeg)
        markAttempt(synced, nowElapsedMs)
        pathSinceMatchM = 0.0
        debug = DebugSnapshot(
            active = true,
            edgeId = snap.edge.id,
            regionId = snap.regionId,
            crossTrackM = snap.crossTrackM,
            alongTrackM = snap.alongTrackM,
            switchedEdge = switched,
            confidence = confidence,
            candidateCount = candidateCount,
            runnerUpScore = runnerUpScore,
            connected = snap.connectedFromPrevious,
            highwayClass = snap.edge.highwayClass,
            oneway = snap.edge.oneway,
            againstOneway = snap.againstOneway,
            candidateEdgeId = snap.edge.id,
            candidateHighwayClass = snap.edge.highwayClass,
            candidateConnected = snap.connectedFromPrevious,
            candidateCrossTrackM = snap.crossTrackM,
            inputBearingDeg = pose.bearingDeg,
            edgeBearingDeg = snap.edgeAzimuthDeg,
            bearingDeltaDeg = bearingDelta,
            turnActive = inhibitHeading,
            skippedReason = null,
            rejectReason = null,
            matchLagM = lastMatchLagM,
            turnHint = turnHintDebugLabel(),
            rankedCandidates = lastRankedCandidates,
            leash = leashState,
            freeActive = freePose != null,
            junction = junctionActive,
            pathOdoM = pathOdoM.takeIf { pathOdoSyncEnabled() },
            pathOdoGapM = pathOdoLastGapM,
        )
        return synced
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
        if (isReturnToAbandoned(cand, nowElapsedMs) && !isPreferredLinkParent(cand)) {
            return "return_to_prior"
        }
        if (nowElapsedMs < parentPreferUntilElapsedMs &&
            RoadHighwayClass.isLink(cand.edge.highwayClass) &&
            RoadMapMatcher.smallestAngleDeg(
                matchTravelBearingDeg,
                cand.edgeAzimuthDeg,
            ) < RoadMapMatcher.turnSignalTowardMinDeg(
                roadProfile,
                matchTurnIntent,
                TurnSignalForkBiasTuning.from(tuning),
            )
        ) {
            return "early_link"
        }
        if (!RoadMapMatcher.canCommitLink(
                cand = cand,
                previousHighwayClass = currentHighwayClass,
                travelBearingDeg = matchTravelBearingDeg,
                turnHint = matchTurnHint,
                topologyLookAheadEdgeIds = matchTopologyExpected,
                speedKmh = matchSpeedKmh,
                turnIntent = matchTurnIntent,
                roadProfile = roadProfile,
            )
        ) {
            return "early_link"
        }
        if (RoadMapMatcher.isParallelYardSwitch(
                cand = cand,
                previousHighwayClass = currentHighwayClass,
                travelBearingDeg = matchTravelBearingDeg,
            )
        ) {
            return "parallel_yard"
        }
        if (circulatingArc &&
            currentEdgeId != null &&
            (cand.edge.id != currentEdgeId || cand.regionId != currentRegionId) &&
            !cand.connectedFromPrevious
        ) {
            return "disconnected_ring"
        }
        return null
    }

    private fun isPreferredLinkParent(cand: RoadMapMatcher.Candidate): Boolean {
        if (!preferParentAfterLink) return false
        if (lastNonLinkEdgeId == null || lastNonLinkRegionId == null) return false
        return cand.edge.id == lastNonLinkEdgeId && cand.regionId == lastNonLinkRegionId
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
        val circulatingHandoff = rawBest.connectedFromPrevious &&
            RoadMapMatcher.isBentOnewayArc(rawBest.edge) &&
            currentMatchedEdge(graphs)?.let { RoadMapMatcher.isBentOnewayArc(it) } == true
        val maxResidual = if (circulatingHandoff) 55f else 20f
        if (residualToBest > maxResidual) return false
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
        // Exception: bounce back from a missed `*_link` onto the ordinary parent.
        if (isReturnToAbandoned(cand, nowElapsedMs) && !isPreferredLinkParent(cand)) {
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
        val circulatingHop = circulatingManeuver &&
            cand.connectedFromPrevious &&
            !cand.againstOneway
        val needed = when {
            circulatingHop -> 1
            fastConfirm && cand.connectedFromPrevious && !cand.againstOneway -> 1
            !cand.connectedFromPrevious && isLink -> effectiveSwitchConfirmCount() + 2
            else -> effectiveSwitchConfirmCount()
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
        matchPose: RoadMatchPose,
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

        val outgoing = RoadMapMatcher.forwardSuccessorCount(
            graphs = graphs,
            regionId = currentProj.regionId,
            edge = currentProj.edge,
            travelAgainstCoords = currentProj.travelAgainstCoords,
            allowAgainstOneway = allowAgainstOneway,
        )
        if (outgoing > 1 && !circulatingArc &&
            !RoadMapMatcher.isBentOnewayArc(currentProj.edge)
        ) {
            val laggedProj = holdPreviousEdge(
                pose = matchPose,
                graphs = graphs,
                dueTurn = dueTurn,
                allowAgainstOneway = allowAgainstOneway,
                allowPastEndHold = true,
            )
            val laggedReleased = laggedProj != null && isPastEndReleased(matchPose, laggedProj)
            if (!laggedReleased) {
                // Ball overshot a fork; string end still on this edge — do not
                // commit a successor until the lagged pose is also past the node.
                return PastEndDecision.NotApplicable
            }
            // Stalk at a real fork: do not auto-lock the through-road just because
            // the live pose overshot the node. Wait until the best other successor
            // already points the stalk way (heading has started the turn).
            if (turnHintActive && !pastEndSuccessorIsTowardHint(ranked, pose, nowElapsedMs, allowAgainstOneway)) {
                return PastEndDecision.NotApplicable
            }
        }

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
                    confidence = RoadMapMatcher.confidenceOf(
                        ranked,
                        firstLock = currentEdgeId == null,
                    ).name,
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
            else RoadMapMatcher.confidenceOf(ranked, firstLock = currentEdgeId == null).name,
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

    /**
     * True when the best connected non-sticky successor already points the
     * applied stalk way. Otherwise past-end must not commit the through-road.
     */
    private fun pastEndSuccessorIsTowardHint(
        ranked: List<RoadMapMatcher.Candidate>,
        pose: RoadMatchPose,
        nowElapsedMs: Long,
        allowAgainstOneway: Boolean,
    ): Boolean {
        val hint = appliedTurnHint ?: return false
        val bestOther = ranked.firstOrNull { cand ->
            (cand.edge.id != currentEdgeId || cand.regionId != currentRegionId) &&
                cand.connectedFromPrevious &&
                (allowAgainstOneway || !cand.againstOneway) &&
                switchRejectReason(cand, allowAgainstOneway, nowElapsedMs) == null
        } ?: return false
        return RoadMapMatcher.isTurnSignalToward(
            pose.bearingDeg,
            bestOther.edgeAzimuthDeg,
            hint,
            RoadMapMatcher.turnSignalTowardMinDeg(
                roadProfile,
                matchTurnIntent,
                TurnSignalForkBiasTuning.from(tuning),
            ),
        )
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

    private fun shouldRunStationaryOverlaySeed(): Boolean =
        stationaryOverlaySeed || (currentEdgeId == null && lastMatchElapsedMs == 0L)

    private fun runStationaryOverlaySeedIfNeeded(
        pose: RoadMatchPose,
        nowElapsedMs: Long,
        allowAgainstOneway: Boolean,
        turnHint: RoadMapMatcher.TurnHint?,
        freeTurns: Boolean,
    ) {
        if (!shouldRunStationaryOverlaySeed()) return
        val graphs = loadInstalledGraphs(pose.lat, pose.lon)
        if (graphs.isEmpty()) {
            lastRankedCandidates = emptyList()
            debug = DebugSnapshot(skippedReason = "no_graph")
            preferFastRetry = true
            return
        }
        headingBeforeTickDeg = pose.bearingDeg
        lastPoseLat = pose.lat
        lastPoseLon = pose.lon
        lastBearingDeg = pose.bearingDeg
        hasLastPose = true
        matchFreeTurns = freeTurns
        val matched = matchOnce(
            pose = pose,
            graphs = graphs,
            speedKmh = 0f,
            nowElapsedMs = nowElapsedMs,
            dueTurn = false,
            allowAgainstOneway = allowAgainstOneway,
            allowRematchAfterLostHold = true,
            turnHint = turnHint,
        )
        stationaryOverlaySeed = false
        markAttempt(pose, nowElapsedMs)
        preferFastRetry = matched == null
    }

    private fun advanceFreeParticle(input: RoadMatchPose) {
        val prev = lastOutputPose ?: return
        val free = freePose ?: return
        freePose = RoadMatchLeashMath.stepFreePose(free, prev, input)
    }

    private fun tryLeashBreakFromLostHold(
        pose: RoadMatchPose,
        graphs: List<RoadGraph>,
        nowElapsedMs: Long,
        dueTurn: Boolean = false,
    ): RoadMatchPose? {
        val edge = currentMatchedEdge(graphs) ?: return null
        val proj = RoadMapMatcher.projectOntoEdge(pose.lat, pose.lon, edge) ?: return null
        val residual = minOf(
            RoadMapMatcher.smallestAngleDeg(pose.bearingDeg, proj.azimuthDeg),
            RoadMapMatcher.smallestAngleDeg(
                pose.bearingDeg,
                RoadMapMatcher.normalizeDeg(proj.azimuthDeg + 180f),
            ),
        )
        if (residual < LEAVING_EDGE_RESIDUAL_DEG) return null
        val inReturnGuard = nowElapsedMs < abandonGuardUntilElapsedMs && abandonedEdgeId != null
        val step = lastOutputPose?.let {
            RoadGraph.haversineM(it.lat, it.lon, pose.lat, pose.lon)
        } ?: 0.0
        // A single GPS/DR jump is not "leaving the road" — keep no_candidate / recover.
        if (step > RoadMatchLeashMath.MAX_LEAVE_STEP_M) return null
        leavingPathM += step
        val growing = RoadMatchLeashMath.xtGrowing(lastLeaveXt, proj.crossTrackM)
        lastLeaveXt = proj.crossTrackM
        skipCorridor = true
        leashState = "stretch"
        if (inReturnGuard) return null
        if (!RoadMatchLeashMath.shouldBreakLeash(
                proj.crossTrackM,
                leavingPathM,
                growing,
                turning = dueTurn,
                courtyardLike = RoadHighwayClass.isCourtyardLike(edge.highwayClass),
                breakXtM = tv(RoadMatchTuningKey.LEASH_BREAK_XT_M),
                breakYardXtM = tv(RoadMatchTuningKey.LEASH_BREAK_YARD_XT_M),
                breakPathM = tv(RoadMatchTuningKey.LEASH_BREAK_PATH_M),
            )
        ) {
            return null
        }
        tryRestoreParentAfterLinkLoss(pose, graphs, nowElapsedMs, dueTurn)?.let { return it }
        leashState = "break"
        releasePhantomPrevious()
        leavingPathM = 0.0
        lastLeaveXt = null
        clearFreeParticle()
        markAttempt(pose, nowElapsedMs)
        pathSinceMatchM = 0.0
        debug = DebugSnapshot(
            active = false,
            crossTrackM = proj.crossTrackM,
            alongTrackM = proj.alongTrackM,
            confidence = RoadMatchConfidence.NONE.name,
            inputBearingDeg = pose.bearingDeg,
            edgeBearingDeg = proj.azimuthDeg,
            bearingDeltaDeg = 0f,
            turnActive = true,
            skippedReason = "leash_break",
            rejectReason = "leash_break",
            matchLagM = lastMatchLagM,
            rankedCandidates = lastRankedCandidates,
            leash = "break",
        )
        return pose
    }

    /**
     * After losing a slip road, snap back to the last ordinary parent while
     * still near it instead of dropping to a free particle (`145353` 14:49).
     */
    private fun tryRestoreParentAfterLinkLoss(
        pose: RoadMatchPose,
        graphs: List<RoadGraph>,
        nowElapsedMs: Long,
        dueTurn: Boolean,
    ): RoadMatchPose? {
        val onLink = currentHighwayClass?.let { RoadHighwayClass.isLink(it) } == true
        if (!onLink) return null
        val parentId = lastNonLinkEdgeId ?: return null
        val parentRegion = lastNonLinkRegionId ?: return null
        if (parentId == currentEdgeId && parentRegion == currentRegionId) return null
        val linkId = currentEdgeId
        val linkRegion = currentRegionId
        val linkClass = currentHighwayClass
        currentEdgeId = parentId
        currentRegionId = parentRegion
        currentHighwayClass = lastNonLinkHighwayClass
        val held = holdPreviousEdge(
            pose = pose,
            graphs = graphs,
            maxCrossM = configuredOr(
                RoadMatchTuningKey.HOLD_PREVIOUS_RADIUS_M,
                holdPreviousRadiusM,
            ),
            dueTurn = dueTurn,
            allowAgainstOneway = false,
        )
        if (held == null) {
            currentEdgeId = linkId
            currentRegionId = linkRegion
            currentHighwayClass = linkClass
            return null
        }
        abandonedEdgeId = null
        abandonedRegionId = null
        abandonGuardUntilElapsedMs = 0L
        parentPreferUntilElapsedMs = nowElapsedMs + PARENT_PREFER_MS
        pendingEdgeId = null
        pendingRegionId = null
        pendingWins = 0
        leavingPathM = 0.0
        lastLeaveXt = null
        leashState = "retract"
        skipCorridor = false
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

    private fun clearFreeParticle() {
        freePose = null
        junctionPathM = 0.0
        junctionActive = false
    }

    private fun updateJunction(
        pose: RoadMatchPose,
        graphs: List<RoadGraph>,
        ranked: List<RoadMapMatcher.Candidate>,
        allowAgainstOneway: Boolean,
        dueTurn: Boolean,
    ) {
        val edge = currentMatchedEdge(graphs)
        val outgoing = if (edge != null && currentRegionId != null) {
            val against = topologyAnchor?.travelAgainstCoords == true
            RoadMapMatcher.forwardSuccessorCount(
                graphs = graphs,
                regionId = currentRegionId!!,
                edge = edge,
                travelAgainstCoords = against,
                allowAgainstOneway = allowAgainstOneway,
            )
        } else {
            0
        }
        val rankedClusters = RoadMatchLeashMath.headingClusters(
            ranked.map { it.edgeAzimuthDeg },
        )
        val nearbyClusters = if (outgoing >= 2) {
            val azimuths = ArrayList<Float>()
            for (graph in graphs) {
                for (near in graph.edgesNear(
                    pose.lat,
                    pose.lon,
                    tv(RoadMatchTuningKey.JUNCTION_RADIUS_M),
                )) {
                    RoadMapMatcher.projectOntoEdge(pose.lat, pose.lon, near)
                        ?.azimuthDeg
                        ?.let { azimuths.add(it) }
                }
            }
            RoadMatchLeashMath.headingClusters(azimuths)
        } else {
            rankedClusters
        }
        // City grid is full of 3-clusters; only arm the free particle while
        // actually turning at a 3+ fork. Straight undershoot (`124442`) must not.
        junctionActive = dueTurn &&
            (outgoing >= ti(RoadMatchTuningKey.JUNCTION_MIN_ROADS) ||
                nearbyClusters >= ti(RoadMatchTuningKey.JUNCTION_MIN_ROADS))
        if (junctionActive) {
            if (freePose == null) {
                freePose = pose
                junctionPathM = 0.0
            }
            val step = lastOutputPose?.let {
                RoadGraph.haversineM(it.lat, it.lon, pose.lat, pose.lon)
            } ?: 0.0
            junctionPathM += step
        } else if (freePose != null &&
            junctionPathM < RoadMatchLeashMath.JUNCTION_MIN_PATH_M &&
            leashState != "stretch"
        ) {
            clearFreeParticle()
        }
    }

    private fun maybePromoteFree(
        input: RoadMatchPose,
        matched: RoadMatchPose?,
        dueTurn: Boolean,
    ): RoadMatchPose? {
        val free = freePose ?: return matched
        if (leashState == "break") {
            clearFreeParticle()
            return matched
        }
        if (junctionPathM < RoadMatchLeashMath.JUNCTION_MIN_PATH_M) return matched
        val drYawAbs = abs(
            RoadMapMatcher.signedAngleDeg(headingBeforeTickDeg, input.bearingDeg),
        )
        val residual = debug.edgeBearingDeg?.let {
            RoadMapMatcher.smallestAngleDeg(input.bearingDeg, it)
        }
        if (!RoadMatchLeashMath.maneuverSettled(
                drYawAbs = drYawAbs,
                residualDeg = residual,
                dueTurn = dueTurn,
                stretching = leashState == "stretch",
            )
        ) {
            return matched
        }
        val compare = matched ?: input
        val posDist = RoadGraph.haversineM(free.lat, free.lon, compare.lat, compare.lon)
        val headingDelta = RoadMapMatcher.smallestAngleDeg(free.bearingDeg, compare.bearingDeg)
        val matchedXt = debug.crossTrackM
        // Still snapped to a road with a large heading residual = gyro undershoot,
        // not a missed courtyard. Field 124442.
        if (matched != null &&
            matchedXt != null &&
            matchedXt < tv(RoadMatchTuningKey.LEASH_BREAK_XT_M) &&
            residual != null &&
            residual >= tf(RoadMatchTuningKey.PROMOTE_HEADING_DEG)
        ) {
            return matched
        }
        if (RoadMatchLeashMath.shouldPromoteFree(
                posDist,
                headingDelta,
                promotePosM = tv(RoadMatchTuningKey.PROMOTE_POS_M),
                promotePosWithHeadingM = tv(RoadMatchTuningKey.PROMOTE_POS_HEADING_M),
                promoteHeadingDeg = tf(RoadMatchTuningKey.PROMOTE_HEADING_DEG),
            )
        ) {
            releasePhantomPrevious()
            clearFreeParticle()
            markAttempt(free, lastMatchElapsedMs)
            debug = debug.copy(
                freePromoted = true,
                freeActive = false,
                junction = false,
                rejectReason = "free_promote",
                skippedReason = "free_promote",
            )
            return free
        }
        clearFreeParticle()
        debug = debug.copy(freeActive = false, junction = false)
        return matched
    }

    private fun pushTrail(pose: RoadMatchPose) {
        if (matchLagM <= 0.0) {
            trail.clear()
            return
        }
        val prev = trail.lastOrNull()
        val step = if (prev == null) {
            0.0
        } else {
            RoadGraph.haversineM(prev.lat, prev.lon, pose.lat, pose.lon)
        }
        if (prev != null && step < 0.05) return
        val cum = (prev?.cumM ?: 0.0) + step
        trail.addLast(TrailSample(pose.lat, pose.lon, cum))
        val keepFrom = cum - tv(RoadMatchTuningKey.MATCH_LAG_MAX_M) - 8.0
        while (trail.size > 2 && trail.first().cumM < keepFrom) {
            trail.removeFirst()
        }
        while (trail.size > 80) {
            trail.removeFirst()
        }
    }

    private fun rankingPose(
        pose: RoadMatchPose,
        graphs: List<RoadGraph>,
        dueTurn: Boolean,
        allowAgainstOneway: Boolean,
        speedKmh: Float,
    ): RoadMatchPose {
        if (matchLagM <= 0.0) return pose
        val stickyAz = lastEdgeAzimuthDeg
        if (stickyAz != null &&
            RoadMapMatcher.smallestAngleDeg(pose.bearingDeg, stickyAz) >= LEAVING_EDGE_RESIDUAL_DEG
        ) {
            // Heading already leaving this road — rank at the live pose so the
            // new street can win. Lag is only for "still straight, slightly ahead".
            return pose
        }
        if (currentEdgeId != null) {
            val liveOnSticky = holdPreviousEdge(
                pose = pose,
                graphs = graphs,
                maxCrossM = configuredOr(
                    RoadMatchTuningKey.HOLD_PREVIOUS_RADIUS_M,
                    holdPreviousRadiusM,
                ),
                dueTurn = dueTurn,
                allowAgainstOneway = allowAgainstOneway,
                allowPastEndHold = true,
            )
            if (liveOnSticky == null) return pose
        }
        return laggedMatchPose(pose, speedKmh)
    }

    private fun laggedMatchPose(pose: RoadMatchPose, speedKmh: Float): RoadMatchPose {
        if (matchLagM <= 0.0 || trail.size < 2) return pose
        val newest = trail.last()
        val oldest = trail.first()
        val available = newest.cumM - oldest.cumM
        if (available < RoadMapMatcher.MATCH_LAG_MIN_TRAIL_M) return pose
        val lag = minOf(
            RoadMapMatcher.matchLagMeters(
                speedKmh,
                minM = tv(RoadMatchTuningKey.MATCH_LAG_MIN_M),
                maxM = tv(RoadMatchTuningKey.MATCH_LAG_MAX_M),
                seconds = tv(RoadMatchTuningKey.MATCH_LAG_SECONDS),
            ),
            available,
        )
        val target = newest.cumM - lag
        var prev = oldest
        for (sample in trail) {
            if (sample.cumM >= target) {
                val span = sample.cumM - prev.cumM
                val t = if (span < 1e-6) {
                    1.0
                } else {
                    ((target - prev.cumM) / span).coerceIn(0.0, 1.0)
                }
                return RoadMatchPose(
                    lat = prev.lat + (sample.lat - prev.lat) * t,
                    lon = prev.lon + (sample.lon - prev.lon) * t,
                    bearingDeg = pose.bearingDeg,
                )
            }
            prev = sample
        }
        return pose
    }

    private fun noteRoadProfile(highwayClass: String?, maxspeedKmh: Int?) {
        val classified = RoadMatchRoadProfileMath.classify(highwayClass, maxspeedKmh)
        if (classified == roadProfile) {
            pendingRoadProfile = null
            roadProfileTicks = 0
            return
        }
        if (pendingRoadProfile != classified) {
            pendingRoadProfile = classified
            roadProfileTicks = 1
            return
        }
        roadProfileTicks += 1
        if (roadProfileTicks >= RoadMatchRoadProfileMath.HYSTERESIS_TICKS) {
            roadProfile = classified
            pendingRoadProfile = null
            roadProfileTicks = 0
        }
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
