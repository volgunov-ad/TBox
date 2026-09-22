package vad.dashing.tbox.location.roadmatch

/**
 * Posted OSM speed on the matched edge plus the next different limit
 * along the assumed-straight path (not the short match-corridor look-ahead).
 *
 * Horizon is [MAX_DISTANCE_M]. At a junction only outgoing edges within
 * [STRAIGHT_MAX_DEG] of the **current edge travel-end azimuth** are followed.
 * Unknown `maxspeed` is not skipped: a numeric current + null successor
 * ends the walk. Ambiguous straight forks hide [Result.nextKmh].
 */
object SpeedLimitLookahead {
    /** Default horizon; widgets may override via [compute]/[Tracker.update] maxDistanceM. */
    const val MAX_DISTANCE_M = 1000.0
    const val STRAIGHT_MAX_DEG = RoadMapMatcher.HEADING_TOLERANCE_DEG
    const val MAX_HOPS = 120
    /** Re-walk after this much travel on the same edge (or [REFRESH_MS]). */
    const val REFRESH_M = 50.0
    const val REFRESH_MS = 4_000L
    const val JUMP_M = 25.0

    data class Result(
        val currentKmh: Int? = null,
        val nextKmh: Int? = null,
        val nextDistanceM: Double? = null,
        val nextHidden: Boolean = false,
    ) {
        companion object {
            val EMPTY = Result()
        }
    }

    fun compute(
        graphs: List<RoadGraph>,
        regionId: String?,
        edgeId: Long?,
        alongTrackM: Double?,
        travelAgainstCoords: Boolean?,
        allowAgainstOneway: Boolean = false,
        maxDistanceM: Double = MAX_DISTANCE_M,
    ): Result {
        if (edgeId == null || alongTrackM == null || travelAgainstCoords == null) {
            return Result.EMPTY
        }
        if (!alongTrackM.isFinite() || alongTrackM < 0.0) return Result.EMPTY
        val horizon = maxDistanceM.takeIf { it.isFinite() && it > 0.0 } ?: MAX_DISTANCE_M
        val edge = RoadMapMatcher.findEdgeAcrossGraphs(graphs, regionId, edgeId)
            ?: return Result.EMPTY
        val resolvedRegion = regionId
            ?: graphs.firstOrNull { it.edgeById.containsKey(edge.id) }?.regionId
            ?: return Result.EMPTY
        val length = RoadMapMatcher.polylineLengthM(edge)
        val along = alongTrackM.coerceIn(0.0, length)
        val currentKmh = edge.speedLimitKmh(travelAgainstCoords)
        val remainingOnEdge = if (travelAgainstCoords) along else length - along
        if (remainingOnEdge > horizon) {
            return Result(currentKmh = currentKmh)
        }
        val walked = walkFromOutgoing(
            graphs = graphs,
            regionId = resolvedRegion,
            edge = edge,
            travelAgainstCoords = travelAgainstCoords,
            currentKmh = currentKmh,
            distanceAtEndM = remainingOnEdge,
            hopsLeft = MAX_HOPS,
            visited = linkedSetOf(edge.id),
            allowAgainstOneway = allowAgainstOneway,
            maxDistanceM = horizon,
        )
        return Result(
            currentKmh = currentKmh,
            nextKmh = if (walked.hidden) null else walked.nextKmh,
            nextDistanceM = if (walked.hidden) null else walked.nextDistanceM,
            nextHidden = walked.hidden,
        )
    }

    private data class Branch(
        val nextKmh: Int? = null,
        val nextDistanceM: Double? = null,
        val hidden: Boolean = false,
    ) {
        companion object {
            val NONE = Branch()
            val HIDDEN = Branch(hidden = true)
        }
    }

    private fun walkFromOutgoing(
        graphs: List<RoadGraph>,
        regionId: String,
        edge: RoadEdge,
        travelAgainstCoords: Boolean,
        currentKmh: Int?,
        distanceAtEndM: Double,
        hopsLeft: Int,
        visited: Set<Long>,
        allowAgainstOneway: Boolean,
        maxDistanceM: Double,
    ): Branch {
        if (hopsLeft <= 0) return Branch.NONE
        val straight = RoadMapMatcher.straightSuccessors(
            graphs = graphs,
            regionId = regionId,
            edge = edge,
            travelAgainstCoords = travelAgainstCoords,
            allowAgainstOneway = allowAgainstOneway,
            visited = visited,
            maxHeadingDeltaDeg = STRAIGHT_MAX_DEG,
        )
        if (straight.isEmpty()) return Branch.NONE
        if (straight.size == 1) {
            return walkOnto(
                graphs = graphs,
                regionId = regionId,
                next = straight[0],
                currentKmh = currentKmh,
                distanceAtStartM = distanceAtEndM,
                hopsLeft = hopsLeft,
                visited = visited,
                allowAgainstOneway = allowAgainstOneway,
                maxDistanceM = maxDistanceM,
            )
        }
        val outcomes = straight.map { successor ->
            walkOnto(
                graphs = graphs,
                regionId = regionId,
                next = successor,
                currentKmh = currentKmh,
                distanceAtStartM = distanceAtEndM,
                hopsLeft = hopsLeft,
                visited = visited,
                allowAgainstOneway = allowAgainstOneway,
                maxDistanceM = maxDistanceM,
            )
        }
        if (outcomes.any { it.hidden }) return Branch.HIDDEN
        val nextValues = outcomes.map { it.nextKmh }.toSet()
        if (nextValues.size > 1) return Branch.HIDDEN
        val agreed = nextValues.single() ?: return Branch.NONE
        val distance = outcomes.mapNotNull { it.nextDistanceM }.minOrNull() ?: return Branch.NONE
        return Branch(nextKmh = agreed, nextDistanceM = distance)
    }

    private fun walkOnto(
        graphs: List<RoadGraph>,
        regionId: String,
        next: RoadMapMatcher.StraightSuccessor,
        currentKmh: Int?,
        distanceAtStartM: Double,
        hopsLeft: Int,
        visited: Set<Long>,
        allowAgainstOneway: Boolean,
        maxDistanceM: Double,
    ): Branch {
        if (distanceAtStartM > maxDistanceM) return Branch.NONE
        val limit = next.edge.speedLimitKmh(next.travelAgainstCoords)
        if (limit != currentKmh) {
            if (limit == null) return Branch.NONE
            return Branch(nextKmh = limit, nextDistanceM = distanceAtStartM)
        }
        val length = RoadMapMatcher.polylineLengthM(next.edge)
        val distanceAtEndM = distanceAtStartM + length
        if (distanceAtEndM > maxDistanceM) return Branch.NONE
        return walkFromOutgoing(
            graphs = graphs,
            regionId = regionId,
            edge = next.edge,
            travelAgainstCoords = next.travelAgainstCoords,
            currentKmh = currentKmh,
            distanceAtEndM = distanceAtEndM,
            hopsLeft = hopsLeft - 1,
            visited = visited + next.edge.id,
            allowAgainstOneway = allowAgainstOneway,
            maxDistanceM = maxDistanceM,
        )
    }

    /**
     * Full graph walk is not needed on every matcher tick. [currentKmh] stays
     * O(1); [nextDistanceM] counts down from the last walk while the edge holds.
     * Re-walk on identity change, along jump, horizon entry, countdown expiry,
     * or every [REFRESH_M] / [REFRESH_MS].
     */
    class Tracker {
        private var cache: Cache? = null

        /** Times [compute] actually walked outgoing edges (remaining ≤ horizon). */
        var walkCount: Int = 0
            private set

        fun reset() {
            cache = null
            walkCount = 0
        }

        fun update(
            graphs: List<RoadGraph>,
            regionId: String?,
            edgeId: Long?,
            alongTrackM: Double?,
            travelAgainstCoords: Boolean?,
            allowAgainstOneway: Boolean = false,
            nowElapsedMs: Long,
            pose: RoadMatchPose? = null,
            maxDistanceM: Double = MAX_DISTANCE_M,
        ): Result {
            val horizon = maxDistanceM.takeIf { it.isFinite() && it > 0.0 } ?: MAX_DISTANCE_M
            if (edgeId == null || travelAgainstCoords == null) {
                cache = null
                return Result.EMPTY
            }
            val edge = RoadMapMatcher.findEdgeAcrossGraphs(graphs, regionId, edgeId)
                ?: run {
                    cache = null
                    return Result.EMPTY
                }
            val along = resolveAlong(edge, alongTrackM, pose) ?: run {
                val held = cache
                if (held != null && held.edgeId == edgeId && held.against == travelAgainstCoords &&
                    held.maxDistanceM == horizon
                ) {
                    return Result(
                        currentKmh = edge.speedLimitKmh(travelAgainstCoords),
                        nextKmh = held.result.nextKmh,
                        nextDistanceM = held.result.nextDistanceM,
                        nextHidden = held.result.nextHidden,
                    )
                }
                cache = null
                return Result.EMPTY
            }
            val currentKmh = edge.speedLimitKmh(travelAgainstCoords)
            val length = RoadMapMatcher.polylineLengthM(edge)
            val remaining = if (travelAgainstCoords) along else length - along
            val prev = cache
            val sameIdentity = prev != null &&
                prev.edgeId == edgeId &&
                prev.regionId == regionId &&
                prev.against == travelAgainstCoords &&
                prev.allowAgainstOneway == allowAgainstOneway &&
                prev.maxDistanceM == horizon
            val progress = if (sameIdentity) {
                travelProgress(prev.walkAlongM, along, travelAgainstCoords)
            } else {
                0.0
            }
            val alongJump = sameIdentity && kotlin.math.abs(along - prev.lastAlongM) > JUMP_M
            val countdown = prev?.result?.nextDistanceM?.let { it - progress }
            val horizonOpened = remaining <= horizon &&
                (prev == null || prev.remainingOnEdge > horizon)
            val periodicDue = sameIdentity &&
                remaining <= horizon &&
                (nowElapsedMs - prev.walkedAtMs >= REFRESH_MS ||
                    kotlin.math.abs(progress) >= REFRESH_M)
            val countdownExpired = countdown != null && countdown <= 0.0
            if (remaining > horizon) {
                val cheap = Result(currentKmh = currentKmh)
                cache = Cache(
                    regionId = regionId,
                    edgeId = edgeId,
                    against = travelAgainstCoords,
                    allowAgainstOneway = allowAgainstOneway,
                    maxDistanceM = horizon,
                    walkAlongM = along,
                    lastAlongM = along,
                    remainingOnEdge = remaining,
                    walkedAtMs = nowElapsedMs,
                    result = cheap,
                )
                return cheap
            }

            val mustWalk = !sameIdentity || alongJump || horizonOpened ||
                countdownExpired || periodicDue

            if (!mustWalk) {
                cache = prev.copy(lastAlongM = along, remainingOnEdge = remaining)
                return Result(
                    currentKmh = currentKmh,
                    nextKmh = prev.result.nextKmh,
                    nextDistanceM = countdown?.coerceAtLeast(0.0) ?: prev.result.nextDistanceM,
                    nextHidden = prev.result.nextHidden,
                )
            }

            val walked = compute(
                graphs = graphs,
                regionId = regionId,
                edgeId = edgeId,
                alongTrackM = along,
                travelAgainstCoords = travelAgainstCoords,
                allowAgainstOneway = allowAgainstOneway,
                maxDistanceM = horizon,
            )
            if (remaining <= horizon) walkCount++
            cache = Cache(
                regionId = regionId,
                edgeId = edgeId,
                against = travelAgainstCoords,
                allowAgainstOneway = allowAgainstOneway,
                maxDistanceM = horizon,
                walkAlongM = along,
                lastAlongM = along,
                remainingOnEdge = remaining,
                walkedAtMs = nowElapsedMs,
                result = walked,
            )
            return walked
        }

        private fun resolveAlong(
            edge: RoadEdge,
            alongTrackM: Double?,
            pose: RoadMatchPose?,
        ): Double? {
            if (alongTrackM != null && alongTrackM.isFinite() && alongTrackM >= 0.0) {
                return alongTrackM
            }
            if (pose != null) {
                return RoadMapMatcher.projectOntoEdge(pose.lat, pose.lon, edge)?.alongTrackM
            }
            return cache?.lastAlongM
        }

        private fun travelProgress(
            previousAlongM: Double,
            alongTrackM: Double,
            travelAgainstCoords: Boolean,
        ): Double = if (travelAgainstCoords) {
            previousAlongM - alongTrackM
        } else {
            alongTrackM - previousAlongM
        }

        private data class Cache(
            val regionId: String?,
            val edgeId: Long,
            val against: Boolean,
            val allowAgainstOneway: Boolean,
            val maxDistanceM: Double,
            val walkAlongM: Double,
            val lastAlongM: Double,
            val remainingOnEdge: Double,
            val walkedAtMs: Long,
            val result: Result,
        )
    }
}
