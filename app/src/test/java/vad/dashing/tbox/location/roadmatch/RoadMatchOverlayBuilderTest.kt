package vad.dashing.tbox.location.roadmatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RoadMatchOverlayBuilderTest {

    @Before
    fun clearStore() {
        RoadGraphStore.clear()
        RoadMatchOverlayRepository.clear()
    }

    private fun sampleGraph(): RoadGraph {
        val edge = RoadEdge(
            id = 42L,
            highwayClass = "primary",
            lengthM = 500.0,
            fromNode = 0,
            toNode = 1,
            coords = doubleArrayOf(37.60, 55.75, 37.62, 55.75),
        )
        val side = RoadEdge(
            id = 99L,
            highwayClass = "residential",
            lengthM = 200.0,
            fromNode = 2,
            toNode = 3,
            coords = doubleArrayOf(37.61, 55.7502, 37.61, 55.7510),
        )
        return RoadGraph(
            regionId = "test",
            graphVersion = 4,
            bbox = doubleArrayOf(37.59, 55.74, 37.63, 55.76),
            edges = listOf(edge, side),
        )
    }

    @Test
    fun disabledReturnsEmpty() {
        val s = RoadMatchOverlayBuilder.build(
            matchEnabled = false,
            shadowLat = 55.75,
            shadowLon = 37.61,
            shadowBearingDeg = 90f,
        )
        assertEquals(RoadMatchOverlayState.EMPTY, s)
        assertEquals("disabled", s.fallbackReason)
    }

    @Test
    fun buildsMatchedPolylineAndNeighbors() {
        val g = sampleGraph()
        RoadGraphStore.put("test/0_0", g)
        val debug = RoadMatchRuntime.DebugSnapshot(
            active = true,
            edgeId = 42L,
            regionId = "test",
            confidence = "HIGH",
            connected = true,
            highwayClass = "primary",
        )
        val s = RoadMatchOverlayBuilder.build(
            matchEnabled = true,
            shadowLat = 55.75005,
            shadowLon = 37.61,
            shadowBearingDeg = 90f,
            gnssLat = 55.7502,
            gnssLon = 37.6105,
            gnssBearingDeg = 88f,
            gnssVisible = true,
            debug = debug,
            graphs = listOf(g),
            maxNeighbors = 8,
        )
        assertTrue(s.active)
        assertTrue(s.shadow.visible)
        assertTrue(s.gnss.visible)
        assertNotNull(s.matchedEdge)
        assertEquals(42L, s.matchedEdge!!.edgeId)
        assertEquals(2, s.matchedEdge!!.points.size)
        assertEquals(55.75, s.matchedEdge!!.points.first().lat, 1e-9)
        assertTrue(s.neighborEdges.none { it.edgeId == 42L })
        assertTrue(s.neighborEdges.any { it.edgeId == 99L })
        assertNotNull(s.camera)
        assertTrue(s.camera!!.includeGnss)
        assertEquals("HIGH", s.matchConfidence)
        assertNull(s.fallbackReason)
    }

    @Test
    fun missingEdgeSetsFallbackButKeepsPoses() {
        val debug = RoadMatchRuntime.DebugSnapshot(
            active = true,
            edgeId = 777L,
            regionId = "test",
            confidence = "MEDIUM",
        )
        val s = RoadMatchOverlayBuilder.build(
            matchEnabled = true,
            shadowLat = 55.75,
            shadowLon = 37.61,
            shadowBearingDeg = 10f,
            debug = debug,
            graphs = emptyList(),
        )
        assertNull(s.matchedEdge)
        assertEquals("no_edge", s.fallbackReason)
        assertTrue(s.shadow.visible)
    }

    @Test
    fun storeFindEdgeAcrossTileKey() {
        val g = sampleGraph()
        RoadGraphStore.put("test/tile_a", g)
        assertNotNull(RoadGraphStore.findEdge("test", 42L))
        assertNull(RoadGraphStore.findEdge("other", 42L))
        assertEquals(1, RoadGraphStore.cachedGraphs().size)
    }

    @Test
    fun repositoryPublishesAndClears() {
        val recording = object : RoadMatchMapRenderer {
            var last: RoadMatchOverlayState? = null
            override fun render(state: RoadMatchOverlayState) {
                last = state
            }
            override fun clear() {
                last = RoadMatchOverlayState.EMPTY
            }
        }
        val state = RoadMatchOverlayBuilder.build(
            matchEnabled = true,
            shadowLat = 55.75,
            shadowLon = 37.61,
            shadowBearingDeg = 0f,
            debug = RoadMatchRuntime.DebugSnapshot(active = true, confidence = "HOLD"),
        )
        RoadMatchOverlayRepository.publish(state)
        recording.render(RoadMatchOverlayRepository.state.value)
        assertTrue(recording.last!!.shadow.visible)
        RoadMatchOverlayRepository.clear()
        assertFalse(RoadMatchOverlayRepository.state.value.shadow.visible)
        assertEquals("disabled", RoadMatchOverlayRepository.state.value.fallbackReason)
    }

    @Test
    fun canvasViewportCentersShadowAndIncludesNearbyGnss() {
        val state = RoadMatchOverlayState(
            active = true,
            shadow = OverlayPoseMarker(55.75, 37.61, 90f, visible = true),
            gnss = OverlayPoseMarker(55.7503, 37.6105, 80f, visible = true),
        )
        val viewport = RoadMatchCanvasProjection.viewport(state, aspectRatio = 2f)
        assertNotNull(viewport)
        val shadow = viewport!!.project(state.shadow.lat, state.shadow.lon)
        val gnss = viewport.project(state.gnss.lat, state.gnss.lon)
        assertEquals(0.5f, shadow.x, 0.001f)
        assertEquals(0.5f, shadow.y, 0.001f)
        assertTrue(gnss.x in 0f..1f)
        assertTrue(gnss.y in 0f..1f)
    }

    @Test
    fun canvasViewportCapsBogusFarGnssZoom() {
        val state = RoadMatchOverlayState(
            active = true,
            shadow = OverlayPoseMarker(55.75, 37.61, visible = true),
            gnss = OverlayPoseMarker(56.75, 38.61, visible = true),
        )
        val viewport = RoadMatchCanvasProjection.viewport(state, aspectRatio = 1f)!!
        assertEquals(280.0, viewport.halfHeightM, 0.01)
    }

    @Test
    fun canvasViewportIgnoresDistantMatchedEdgeEndpoints() {
        val state = RoadMatchOverlayState(
            active = true,
            shadow = OverlayPoseMarker(55.75, 37.61, visible = true),
            matchedEdge = OverlayEdgePolyline(
                edgeId = 1L,
                regionId = "t",
                highwayClass = "primary",
                points = listOf(
                    OverlayLatLon(55.75, 37.61),
                    OverlayLatLon(55.80, 37.70), // far — must not force max zoom-out alone
                ),
            ),
        )
        val viewport = RoadMatchCanvasProjection.viewport(state, aspectRatio = 1f)!!
        assertTrue(viewport.halfHeightM <= 280.0)
        assertTrue(viewport.halfHeightM < 400.0)
    }

    @Test
    fun gnssHiddenWhenNotVisibleEvenIfCoordsPassed() {
        val s = RoadMatchOverlayBuilder.build(
            matchEnabled = true,
            shadowLat = 55.75,
            shadowLon = 37.61,
            shadowBearingDeg = 10f,
            gnssLat = 55.7502,
            gnssLon = 37.6105,
            gnssVisible = false,
            debug = RoadMatchRuntime.DebugSnapshot(active = true, confidence = "HIGH"),
        )
        assertTrue(s.shadow.visible)
        assertFalse(s.gnss.visible)
    }

    @Test
    fun gnssHiddenAtZeroZeroMirror() {
        val s = RoadMatchOverlayBuilder.build(
            matchEnabled = true,
            shadowLat = 55.75,
            shadowLon = 37.61,
            shadowBearingDeg = 10f,
            gnssLat = 0.0,
            gnssLon = 0.0,
            gnssVisible = true,
            debug = RoadMatchRuntime.DebugSnapshot(active = true, confidence = "HIGH"),
        )
        assertFalse(s.gnss.visible)
    }

    @Test
    fun gnssShownWhenFrozenWithin1km() {
        val s = RoadMatchOverlayBuilder.build(
            matchEnabled = true,
            shadowLat = 55.75,
            shadowLon = 37.61,
            shadowBearingDeg = 10f,
            // ~500 m east
            gnssLat = 55.75,
            gnssLon = 37.6175,
            gnssVisible = true,
            debug = RoadMatchRuntime.DebugSnapshot(active = true, confidence = "HOLD"),
        )
        assertTrue(s.gnss.visible)
    }

    @Test
    fun gnssHiddenWhenFartherThan1km() {
        val s = RoadMatchOverlayBuilder.build(
            matchEnabled = true,
            shadowLat = 55.75,
            shadowLon = 37.61,
            shadowBearingDeg = 10f,
            // ~5 km east
            gnssLat = 55.75,
            gnssLon = 37.68,
            gnssVisible = true,
            debug = RoadMatchRuntime.DebugSnapshot(active = true, confidence = "HOLD"),
        )
        assertFalse(s.gnss.visible)
    }

    @Test
    fun matchedEdgeIgnoresFarSameIdFromOtherRegionFallback() {
        // Same sequential id 42 on a far road must not paint blue away from the shadow.
        val near = sampleGraph()
        val far = RoadGraph(
            regionId = "other",
            graphVersion = 4,
            bbox = doubleArrayOf(38.0, 56.0, 38.1, 56.1),
            edges = listOf(
                RoadEdge(
                    id = 42L,
                    highwayClass = "primary",
                    lengthM = 500.0,
                    fromNode = 0,
                    toNode = 1,
                    coords = doubleArrayOf(38.05, 56.05, 38.06, 56.05),
                ),
            ),
        )
        RoadGraphStore.put("other/0_0", far)
        val s = RoadMatchOverlayBuilder.build(
            matchEnabled = true,
            shadowLat = 55.75,
            shadowLon = 37.61,
            shadowBearingDeg = 90f,
            debug = RoadMatchRuntime.DebugSnapshot(
                active = true,
                edgeId = 42L,
                regionId = "test",
                confidence = "HIGH",
            ),
            graphs = listOf(near, far),
        )
        assertNotNull(s.matchedEdge)
        assertEquals(42L, s.matchedEdge!!.edgeId)
        assertEquals(55.75, s.matchedEdge!!.points.first().lat, 1e-6)
    }

    @Test
    fun defaultNeighborBudgetIs250mAnd72() {
        assertEquals(250.0, RoadMatchOverlayBuilder.DEFAULT_NEIGHBOR_RADIUS_M, 0.0)
        assertEquals(72, RoadMatchOverlayBuilder.DEFAULT_MAX_NEIGHBORS)
        assertEquals(220.0, RoadMatchCanvasProjection.EDGE_FIT_RADIUS_M, 0.0)
    }

    @Test
    fun neighborsCollectedAroundQueryCenterNotShadow() {
        val nearShadow = RoadEdge(
            id = 1L,
            highwayClass = "residential",
            lengthM = 80.0,
            fromNode = 0,
            toNode = 1,
            coords = doubleArrayOf(37.6100, 55.7500, 37.6104, 55.7500),
        )
        // ~400 m north of the shadow — inside 250 m only when querying the draft center.
        val nearDraft = RoadEdge(
            id = 2L,
            highwayClass = "residential",
            lengthM = 80.0,
            fromNode = 2,
            toNode = 3,
            coords = doubleArrayOf(37.6100, 55.7536, 37.6104, 55.7536),
        )
        val g = RoadGraph(
            regionId = "test",
            graphVersion = 4,
            bbox = doubleArrayOf(37.60, 55.74, 37.62, 55.76),
            edges = listOf(nearShadow, nearDraft),
        )
        val aroundShadow = RoadMatchOverlayBuilder.build(
            matchEnabled = true,
            shadowLat = 55.7500,
            shadowLon = 37.6102,
            shadowBearingDeg = 90f,
            debug = RoadMatchRuntime.DebugSnapshot(active = true, confidence = "HIGH"),
            graphs = listOf(g),
        )
        assertTrue(aroundShadow.neighborEdges.any { it.edgeId == 1L })
        assertTrue(aroundShadow.neighborEdges.none { it.edgeId == 2L })

        val aroundDraft = RoadMatchOverlayBuilder.build(
            matchEnabled = true,
            shadowLat = 55.7500,
            shadowLon = 37.6102,
            shadowBearingDeg = 90f,
            debug = RoadMatchRuntime.DebugSnapshot(active = true, confidence = "HIGH"),
            graphs = listOf(g),
            neighborLat = 55.7536,
            neighborLon = 37.6102,
        )
        assertTrue(aroundDraft.neighborEdges.any { it.edgeId == 2L })
        assertTrue(aroundDraft.neighborEdges.none { it.edgeId == 1L })
    }

    @Test
    fun canvasViewportAtPinsRequestedCenter() {
        val vp = RoadMatchCanvasProjection.viewportAt(55.75, 37.61, 120.0, aspectRatio = 1f)
        val p = vp.project(55.75, 37.61)
        assertEquals(0.5f, p.x, 0.001f)
        assertEquals(0.5f, p.y, 0.001f)
        assertEquals(120.0, vp.halfHeightM, 0.01)
    }

    @Test
    fun matchedEdgeRejectedWhenOnlyFarGeometryExists() {
        val far = RoadGraph(
            regionId = "test",
            graphVersion = 4,
            bbox = doubleArrayOf(38.0, 56.0, 38.1, 56.1),
            edges = listOf(
                RoadEdge(
                    id = 42L,
                    highwayClass = "primary",
                    lengthM = 500.0,
                    fromNode = 0,
                    toNode = 1,
                    coords = doubleArrayOf(38.05, 56.05, 38.06, 56.05),
                ),
            ),
        )
        val s = RoadMatchOverlayBuilder.build(
            matchEnabled = true,
            shadowLat = 55.75,
            shadowLon = 37.61,
            shadowBearingDeg = 90f,
            debug = RoadMatchRuntime.DebugSnapshot(
                active = true,
                edgeId = 42L,
                regionId = "test",
                confidence = "HIGH",
            ),
            graphs = listOf(far),
        )
        assertNull(s.matchedEdge)
        assertEquals("no_edge", s.fallbackReason)
    }
}
