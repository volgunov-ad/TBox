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
    const val MAX_DISTANCE_M = 500.0
    const val STRAIGHT_MAX_DEG = RoadMapMatcher.HEADING_TOLERANCE_DEG
    const val MAX_HOPS = 120

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
    ): Result {
        if (edgeId == null || alongTrackM == null || travelAgainstCoords == null) {
            return Result.EMPTY
        }
        if (!alongTrackM.isFinite() || alongTrackM < 0.0) return Result.EMPTY
        val edge = RoadMapMatcher.findEdgeAcrossGraphs(graphs, regionId, edgeId)
            ?: return Result.EMPTY
        val resolvedRegion = regionId
            ?: graphs.firstOrNull { it.edgeById.containsKey(edge.id) }?.regionId
            ?: return Result.EMPTY
        val length = RoadMapMatcher.polylineLengthM(edge)
        val along = alongTrackM.coerceIn(0.0, length)
        val currentKmh = edge.speedLimitKmh(travelAgainstCoords)
        val remainingOnEdge = if (travelAgainstCoords) along else length - along
        if (remainingOnEdge > MAX_DISTANCE_M) {
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
    ): Branch {
        if (distanceAtStartM > MAX_DISTANCE_M) return Branch.NONE
        val limit = next.edge.speedLimitKmh(next.travelAgainstCoords)
        if (limit != currentKmh) {
            if (limit == null) return Branch.NONE
            return Branch(nextKmh = limit, nextDistanceM = distanceAtStartM)
        }
        val length = RoadMapMatcher.polylineLengthM(next.edge)
        val distanceAtEndM = distanceAtStartM + length
        if (distanceAtEndM > MAX_DISTANCE_M) return Branch.NONE
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
        )
    }
}
