package vad.dashing.tbox.location.roadmatch

/**
 * Align travel bearing to the nearest road when a coordinate seed arrives from
 * clipboard paste / share link (Yandex, 2GIS, …) — not from the F3 heading ring.
 *
 * Rules:
 * - nearest edge by cross-track distance;
 * - if farther than [MAX_SNAP_DISTANCE_M] (or no graph) → keep current course;
 * - oneway restricts allowed travel direction; two-way picks the smaller turn.
 */
object RoadMatchSeedBearing {
    /** Do not change course when the nearest edge is farther than this. */
    const val MAX_SNAP_DISTANCE_M = 30.0

    /**
     * @return aligned travel bearing, or `null` when no edge is within [maxDistanceM].
     */
    fun snapTravelBearingDeg(
        lat: Double,
        lon: Double,
        currentBearingDeg: Float,
        graphs: List<RoadGraph>,
        maxDistanceM: Double = MAX_SNAP_DISTANCE_M,
    ): Float? {
        if (!lat.isFinite() || !lon.isFinite()) return null
        if (!currentBearingDeg.isFinite()) return null
        if (!maxDistanceM.isFinite() || maxDistanceM < 0.0) return null
        if (graphs.isEmpty()) return null

        var bestCross = Double.POSITIVE_INFINITY
        var bestTurn = Float.POSITIVE_INFINITY
        var bestBearing: Float? = null

        for (graph in graphs) {
            for (edge in graph.edgesNear(lat, lon, maxDistanceM)) {
                val proj = RoadMapMatcher.projectOntoEdge(lat, lon, edge) ?: continue
                if (proj.crossTrackM > maxDistanceM) continue
                val aligned = alignToEdgeAzimuth(
                    currentBearingDeg = currentBearingDeg,
                    edgeAzimuthAlongCoordsDeg = proj.azimuthDeg,
                    oneway = edge.oneway,
                )
                val turn = RoadMapMatcher.smallestAngleDeg(currentBearingDeg, aligned)
                val closer = proj.crossTrackM < bestCross - 1e-6
                val sameDistBetterTurn =
                    kotlin.math.abs(proj.crossTrackM - bestCross) <= 1e-6 && turn < bestTurn
                if (closer || sameDistBetterTurn) {
                    bestCross = proj.crossTrackM
                    bestTurn = turn
                    bestBearing = aligned
                }
            }
        }
        return bestBearing
    }

    /**
     * Pick the legal travel direction on an edge closest to [currentBearingDeg].
     * [edgeAzimuthAlongCoordsDeg] is the along-coords segment azimuth at the
     * projection (same as [RoadMapMatcher.Projection.azimuthDeg]).
     */
    fun alignToEdgeAzimuth(
        currentBearingDeg: Float,
        edgeAzimuthAlongCoordsDeg: Float,
        oneway: Int,
    ): Float {
        val forward = RoadMapMatcher.normalizeDeg(edgeAzimuthAlongCoordsDeg)
        val reverse = RoadMapMatcher.normalizeDeg(forward + 180f)
        val allowed = ArrayList<Float>(2)
        if (!RoadMapMatcher.isAgainstOneway(oneway, travelAgainstCoords = false)) {
            allowed.add(forward)
        }
        if (!RoadMapMatcher.isAgainstOneway(oneway, travelAgainstCoords = true)) {
            allowed.add(reverse)
        }
        if (allowed.isEmpty()) {
            // Degenerate oneway value — fall back to both ways.
            allowed.add(forward)
            allowed.add(reverse)
        }
        return allowed.minBy { RoadMapMatcher.smallestAngleDeg(currentBearingDeg, it) }
    }

    /** Snap when a near edge exists; otherwise return [currentBearingDeg]. */
    fun snapOrKeep(
        lat: Double,
        lon: Double,
        currentBearingDeg: Float,
        graphs: List<RoadGraph> = RoadGraphStore.cachedGraphs(),
        maxDistanceM: Double = MAX_SNAP_DISTANCE_M,
    ): Float {
        val current = if (currentBearingDeg.isFinite()) {
            RoadMapMatcher.normalizeDeg(currentBearingDeg)
        } else {
            0f
        }
        return snapTravelBearingDeg(lat, lon, current, graphs, maxDistanceM) ?: current
    }
}
