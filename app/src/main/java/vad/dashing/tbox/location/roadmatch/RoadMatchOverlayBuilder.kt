package vad.dashing.tbox.location.roadmatch

/**
 * Builds [RoadMatchOverlayState] from DR shadow, optional GNSS, and match debug.
 * Map-host independent (Phase F1).
 */
object RoadMatchOverlayBuilder {
    /** Wider than the matcher search so the Canvas shows road context around the shadow. */
    const val DEFAULT_NEIGHBOR_RADIUS_M = 180.0
    const val DEFAULT_MAX_NEIGHBORS = 48
    const val DEFAULT_CAMERA_PADDING_M = 40.0
    /** Hide frozen GNSS when it sits farther than this from the green shadow. */
    const val GNSS_MAX_GAP_FROM_SHADOW_M = 1_000.0

    fun edgeToPolyline(edge: RoadEdge, regionId: String): OverlayEdgePolyline {
        val pts = ArrayList<OverlayLatLon>(edge.pointCount)
        for (i in 0 until edge.pointCount) {
            pts.add(OverlayLatLon(lat = edge.latAt(i), lon = edge.lonAt(i)))
        }
        return OverlayEdgePolyline(
            edgeId = edge.id,
            regionId = regionId,
            highwayClass = edge.highwayClass,
            points = pts,
        )
    }

    /**
     * @param matchEnabled road-match toggle
     * @param shadowLatLonBearing DR / matched pose pushed to mock
     * @param gnss optional live GNSS (may be null / invalid)
     * @param debug last [RoadMatchRuntime.DebugSnapshot]
     * @param graphs currently loaded tile graphs (may be empty)
     */
    fun build(
        matchEnabled: Boolean,
        shadowLat: Double,
        shadowLon: Double,
        shadowBearingDeg: Float?,
        gnssLat: Double? = null,
        gnssLon: Double? = null,
        gnssBearingDeg: Float? = null,
        gnssVisible: Boolean = false,
        debug: RoadMatchRuntime.DebugSnapshot = RoadMatchRuntime.DebugSnapshot(),
        graphs: List<RoadGraph> = emptyList(),
        neighborRadiusM: Double = DEFAULT_NEIGHBOR_RADIUS_M,
        maxNeighbors: Int = DEFAULT_MAX_NEIGHBORS,
        gnssMaxGapFromShadowM: Double = GNSS_MAX_GAP_FROM_SHADOW_M,
    ): RoadMatchOverlayState {
        if (!matchEnabled) {
            return RoadMatchOverlayState.EMPTY
        }
        if (!shadowLat.isFinite() || !shadowLon.isFinite() ||
            shadowLat !in -90.0..90.0 || shadowLon !in -180.0..180.0
        ) {
            return RoadMatchOverlayState(active = false, fallbackReason = "no_pose")
        }

        val shadow = OverlayPoseMarker(
            lat = shadowLat,
            lon = shadowLon,
            bearingDeg = shadowBearingDeg,
            visible = true,
        )
        val gnssLatOk = gnssLat
        val gnssLonOk = gnssLon
        val gnssCoordsOk = gnssVisible &&
            gnssLatOk != null && gnssLonOk != null &&
            gnssLatOk.isFinite() && gnssLonOk.isFinite() &&
            gnssLatOk in -90.0..90.0 && gnssLonOk in -180.0..180.0 &&
            (gnssLatOk != 0.0 || gnssLonOk != 0.0)
        val gnssGapM = if (gnssCoordsOk) {
            haversineM(shadowLat, shadowLon, gnssLatOk!!, gnssLonOk!!)
        } else {
            Double.POSITIVE_INFINITY
        }
        val gnssOk = gnssCoordsOk && gnssGapM <= gnssMaxGapFromShadowM
        val gnss = if (gnssOk) {
            OverlayPoseMarker(
                lat = gnssLatOk!!,
                lon = gnssLonOk!!,
                bearingDeg = gnssBearingDeg,
                visible = true,
            )
        } else {
            OverlayPoseMarker(lat = 0.0, lon = 0.0, visible = false)
        }

        val edgeId = debug.edgeId
        val regionId = debug.regionId
        var matched: OverlayEdgePolyline? = null
        var fallback: String? = null
        if (edgeId != null && regionId != null) {
            val edge = findEdgeNear(
                graphs = graphs,
                regionId = regionId,
                edgeId = edgeId,
                nearLat = shadowLat,
                nearLon = shadowLon,
            ) ?: RoadGraphStore.findEdge(
                regionId = regionId,
                edgeId = edgeId,
                nearLat = shadowLat,
                nearLon = shadowLon,
            )
            if (edge != null) {
                matched = edgeToPolyline(edge, regionId)
            } else {
                fallback = "no_edge"
            }
        } else if (graphs.isEmpty() && debug.skippedReason == "no_graph") {
            fallback = "no_graph"
        }

        val neighbors = if (graphs.isEmpty() || maxNeighbors <= 0) {
            emptyList()
        } else {
            collectNeighbors(
                graphs = graphs,
                lat = shadowLat,
                lon = shadowLon,
                excludeEdgeId = edgeId,
                excludeRegionId = regionId,
                radiusM = neighborRadiusM,
                limit = maxNeighbors,
            )
        }

        return RoadMatchOverlayState(
            active = matched != null || debug.active || debug.confidence != null,
            shadow = shadow,
            gnss = gnss,
            matchedEdge = matched,
            neighborEdges = neighbors,
            camera = OverlayCameraHint(
                centerLat = shadowLat,
                centerLon = shadowLon,
                includeGnss = gnssOk,
                paddingM = DEFAULT_CAMERA_PADDING_M,
            ),
            fallbackReason = fallback,
            matchConfidence = debug.confidence,
            matchConnected = debug.connected,
        )
    }

    /**
     * Resolve [edgeId] inside [regionId] using the copy nearest to the shadow.
     * Never falls back to another region's sequential id.
     */
    fun findEdgeNear(
        graphs: List<RoadGraph>,
        regionId: String,
        edgeId: Long,
        nearLat: Double,
        nearLon: Double,
        maxCrossTrackM: Double = RoadGraphStore.MATCHED_EDGE_MAX_CROSS_M,
    ): RoadEdge? {
        var best: RoadEdge? = null
        var bestCross = Double.POSITIVE_INFINITY
        for (g in graphs) {
            if (g.regionId != regionId) continue
            val edge = g.edgeById[edgeId] ?: continue
            val proj = RoadMapMatcher.projectOntoEdge(nearLat, nearLon, edge) ?: continue
            if (proj.crossTrackM < bestCross) {
                bestCross = proj.crossTrackM
                best = edge
            }
        }
        if (best != null && bestCross <= maxCrossTrackM) return best
        return null
    }

    @Deprecated("Use findEdgeNear with pose", ReplaceWith("findEdgeNear(graphs, regionId, edgeId, nearLat, nearLon)"))
    fun findEdge(graphs: List<RoadGraph>, regionId: String, edgeId: Long): RoadEdge? {
        for (g in graphs) {
            if (g.regionId != regionId) continue
            g.edgeById[edgeId]?.let { return it }
        }
        return null
    }

    private fun collectNeighbors(
        graphs: List<RoadGraph>,
        lat: Double,
        lon: Double,
        excludeEdgeId: Long?,
        excludeRegionId: String?,
        radiusM: Double,
        limit: Int,
    ): List<OverlayEdgePolyline> {
        data class Ranked(val cross: Double, val poly: OverlayEdgePolyline)
        val ranked = ArrayList<Ranked>(limit * 2)
        for (g in graphs) {
            for (edge in g.edgesNear(lat, lon, radiusM)) {
                if (excludeEdgeId != null && edge.id == excludeEdgeId &&
                    (excludeRegionId == null || g.regionId == excludeRegionId)
                ) {
                    continue
                }
                val proj = RoadMapMatcher.projectOntoEdge(lat, lon, edge) ?: continue
                ranked.add(Ranked(proj.crossTrackM, edgeToPolyline(edge, g.regionId)))
            }
        }
        ranked.sortBy { it.cross }
        val out = ArrayList<OverlayEdgePolyline>(limit)
        val seen = HashSet<Pair<String, Long>>(limit * 2)
        for (r in ranked) {
            val key = r.poly.regionId to r.poly.edgeId
            if (!seen.add(key)) continue
            out.add(r.poly)
            if (out.size >= limit) break
        }
        return out
    }

    private fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = Math.toRadians(lat2 - lat1)
        val dl = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dp / 2) * kotlin.math.sin(dp / 2) +
            kotlin.math.cos(p1) * kotlin.math.cos(p2) *
            kotlin.math.sin(dl / 2) * kotlin.math.sin(dl / 2)
        return 2.0 * r * kotlin.math.asin(kotlin.math.sqrt(a))
    }
}
