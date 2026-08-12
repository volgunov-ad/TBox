package vad.dashing.tbox.location.roadmatch

import java.io.File

/**
 * Throttled road-match corrections for the DR shadow.
 * Call [maybeCorrect] after CONSTANT / enhancement DR step, before setMockLocation.
 */
class RoadMatchRuntime(
    private val mapsDir: () -> File,
    private val pathTriggerM: Double = 12.0,
    private val timeTriggerMs: Long = 2_000L,
    private val turnTriggerDeg: Float = 25f,
    private val minSpeedKmh: Float = 1.8f,
    private val switchConfirmCount: Int = 2,
) {
    data class DebugSnapshot(
        val active: Boolean = false,
        val edgeId: Long? = null,
        val regionId: String? = null,
        val crossTrackM: Double? = null,
        val alongTrackM: Double? = null,
        val switchedEdge: Boolean = false,
        val skippedReason: String? = null,
    )

    @Volatile
    var debug: DebugSnapshot = DebugSnapshot()
        private set

    private var lastMatchElapsedMs: Long = 0L
    private var lastPoseLat: Double = 0.0
    private var lastPoseLon: Double = 0.0
    private var lastBearingDeg: Float = 0f
    private var hasLastPose: Boolean = false
    private var pathSinceMatchM: Double = 0.0
    private var currentEdgeId: Long? = null
    private var currentRegionId: String? = null
    private var pendingEdgeId: Long? = null
    private var pendingRegionId: String? = null
    private var pendingWins: Int = 0

    fun reset() {
        lastMatchElapsedMs = 0L
        hasLastPose = false
        pathSinceMatchM = 0.0
        currentEdgeId = null
        currentRegionId = null
        pendingEdgeId = null
        pendingRegionId = null
        pendingWins = 0
        debug = DebugSnapshot()
    }

    /**
     * @return corrected pose, or null if skipped / no coverage (caller keeps previous pose).
     */
    fun maybeCorrect(
        enabled: Boolean,
        pose: RoadMatchPose,
        speedKmh: Float,
        nowElapsedMs: Long,
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
        val duePath = pathSinceMatchM >= pathTriggerM
        val dueTime = dtMs >= timeTriggerMs
        val dueTurn = turn >= turnTriggerDeg
        lastPoseLat = pose.lat
        lastPoseLon = pose.lon
        lastBearingDeg = pose.bearingDeg
        hasLastPose = true
        if (lastMatchElapsedMs > 0L && !duePath && !dueTime && !dueTurn) {
            debug = DebugSnapshot(
                active = currentEdgeId != null,
                edgeId = currentEdgeId,
                regionId = currentRegionId,
                skippedReason = "throttled",
            )
            return null
        }

        val graphs = loadInstalledGraphs()
        if (graphs.isEmpty()) {
            debug = DebugSnapshot(skippedReason = "no_graph")
            markAttempt(pose, nowElapsedMs)
            return null
        }

        val rawBest = RoadMapMatcher.pickBest(
            pose = pose,
            graphs = graphs,
            previousEdgeId = currentEdgeId,
            previousRegionId = currentRegionId,
        )
        if (rawBest == null) {
            debug = DebugSnapshot(
                active = currentEdgeId != null,
                edgeId = currentEdgeId,
                regionId = currentRegionId,
                skippedReason = "no_candidate",
            )
            markAttempt(pose, nowElapsedMs)
            return null
        }

        val accepted = acceptEdge(rawBest.edge.id, rawBest.regionId)
        val cand = if (accepted) {
            rawBest
        } else if (currentEdgeId != null) {
            // Keep previous edge if still near; else soft-hold without switch.
            graphs.firstOrNull { it.regionId == currentRegionId }
                ?.edges
                ?.firstOrNull { it.id == currentEdgeId }
                ?.let { edge ->
                    val proj = RoadMapMatcher.projectOntoEdge(pose.lat, pose.lon, edge)
                        ?: return@let null
                    if (proj.crossTrackM > RoadMapMatcher.CANDIDATE_RADIUS_M * 1.5) return@let null
                    RoadMapMatcher.Candidate(
                        edge = edge,
                        regionId = currentRegionId!!,
                        crossTrackM = proj.crossTrackM,
                        alongTrackM = proj.alongTrackM,
                        projLat = proj.lat,
                        projLon = proj.lon,
                        edgeAzimuthDeg = run {
                            val d = RoadMapMatcher.smallestAngleDeg(pose.bearingDeg, proj.azimuthDeg)
                            val r = RoadMapMatcher.smallestAngleDeg(
                                pose.bearingDeg,
                                RoadMapMatcher.normalizeDeg(proj.azimuthDeg + 180f),
                            )
                            if (r < d) RoadMapMatcher.normalizeDeg(proj.azimuthDeg + 180f) else proj.azimuthDeg
                        },
                        score = proj.crossTrackM,
                    )
                }
        } else {
            null
        }

        if (cand == null) {
            debug = DebugSnapshot(skippedReason = "switch_pending")
            markAttempt(pose, nowElapsedMs)
            return null
        }

        val switched = currentEdgeId != null &&
            (cand.edge.id != currentEdgeId || cand.regionId != currentRegionId)
        val corrected = RoadMapMatcher.softCorrect(pose, cand)
        currentEdgeId = cand.edge.id
        currentRegionId = cand.regionId
        markAttempt(corrected, nowElapsedMs)
        pathSinceMatchM = 0.0
        debug = DebugSnapshot(
            active = true,
            edgeId = cand.edge.id,
            regionId = cand.regionId,
            crossTrackM = cand.crossTrackM,
            alongTrackM = cand.alongTrackM,
            switchedEdge = switched,
            skippedReason = null,
        )
        return corrected
    }

    private fun acceptEdge(edgeId: Long, regionId: String): Boolean {
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
        if (pendingEdgeId == edgeId && pendingRegionId == regionId) {
            pendingWins++
        } else {
            pendingEdgeId = edgeId
            pendingRegionId = regionId
            pendingWins = 1
        }
        return pendingWins >= switchConfirmCount
    }

    private fun markAttempt(pose: RoadMatchPose, nowElapsedMs: Long) {
        lastMatchElapsedMs = nowElapsedMs
        lastPoseLat = pose.lat
        lastPoseLon = pose.lon
        lastBearingDeg = pose.bearingDeg
        hasLastPose = true
    }

    private fun loadInstalledGraphs(): List<RoadGraph> {
        val dir = mapsDir()
        if (!dir.isDirectory) return emptyList()
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".tboxroads") } ?: return emptyList()
        val out = ArrayList<RoadGraph>(files.size)
        for (f in files) {
            val id = f.name.removeSuffix(".tboxroads")
            val g = runCatching { RoadGraphStore.loadOrGet(id, f) }.getOrNull() ?: continue
            if (g.edges.isNotEmpty()) out.add(g)
        }
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
