package vad.dashing.tbox.location.roadmatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RoadMapMatcherTest {

    private fun horizontalEdge(): RoadGraph {
        val edge = RoadEdge(
            id = 1L,
            highwayClass = "primary",
            lengthM = 1_000.0,
            fromNode = 0,
            toNode = 1,
            // eastbound along lat=55.75 from lon 37.60 → 37.62
            coords = doubleArrayOf(37.60, 55.75, 37.62, 55.75),
        )
        return RoadGraph(
            regionId = "test",
            graphVersion = 1,
            bbox = doubleArrayOf(37.59, 55.74, 37.63, 55.76),
            edges = listOf(edge),
        )
    }

    @Test
    fun projectsAndSoftCorrectsCrossTrackWithoutRewindingAlong() {
        val graph = horizontalEdge()
        val pose = RoadMatchPose(lat = 55.7503, lon = 37.61, bearingDeg = 90f) // ~33 m north of line, heading east
        val best = RoadMapMatcher.pickBest(pose, listOf(graph), null, null)
        assertNotNull(best)
        val alongBefore = best!!.alongTrackM
        val corrected = RoadMapMatcher.softCorrect(pose, best)
        // Moved toward the line (south), not far east/west.
        assertTrue(corrected.lat < pose.lat)
        assertTrue(kotlin.math.abs(corrected.lon - pose.lon) < 0.0002)
        val after = RoadMapMatcher.projectOntoEdge(corrected.lat, corrected.lon, best.edge)!!
        // Along-track stays near the original projection (length preserved).
        assertTrue(kotlin.math.abs(after.alongTrackM - alongBefore) < 5.0)
        assertTrue(after.crossTrackM < best.crossTrackM)
    }

    @Test
    fun prefersHeadingAlignedEdge() {
        val east = RoadEdge(
            id = 1L,
            highwayClass = "primary",
            lengthM = 500.0,
            fromNode = 0,
            toNode = 1,
            coords = doubleArrayOf(37.60, 55.75, 37.62, 55.75),
        )
        val north = RoadEdge(
            id = 2L,
            highwayClass = "primary",
            lengthM = 500.0,
            fromNode = 2,
            toNode = 3,
            coords = doubleArrayOf(37.61, 55.74, 37.61, 55.76),
        )
        val graph = RoadGraph(
            regionId = "test",
            graphVersion = 1,
            bbox = doubleArrayOf(37.59, 55.73, 37.63, 55.77),
            edges = listOf(east, north),
        )
        val pose = RoadMatchPose(lat = 55.7501, lon = 37.6101, bearingDeg = 0f) // northbound
        val best = RoadMapMatcher.pickBest(pose, listOf(graph), null, null)
        assertNotNull(best)
        assertEquals(2L, best!!.edge.id)
    }

    @Test
    fun runtimeThrottlesThenMatches() {
        val edge = RoadEdge(
            id = 7L,
            highwayClass = "secondary",
            lengthM = 800.0,
            fromNode = 0,
            toNode = 1,
            coords = doubleArrayOf(37.60, 55.75, 37.62, 55.75),
        )
        val graph = RoadGraph("rt", 1, doubleArrayOf(37.59, 55.74, 37.63, 55.76), listOf(edge))
        RoadGraphStore.clear()
        val dir = createTempDir(prefix = "roads-rt-")
        installSingleTileBundle(dir, graph)

        val rt = RoadMatchRuntime(
            mapsDir = { dir },
            pathTriggerM = 1000.0, // force time-based
            timeTriggerMs = 2_000L,
            minSpeedKmh = 1.8f,
            switchConfirmCount = 1,
        )
        val pose = RoadMatchPose(55.75005, 37.61, 90f)
        // First call: lastMatchElapsed=0 → due immediately
        val first = rt.maybeCorrect(true, pose, speedKmh = 40f, nowElapsedMs = 1_000L)
        assertNotNull(first)
        assertEquals(7L, rt.debug.edgeId)
        // Immediate second call: throttled
        val second = rt.maybeCorrect(true, pose, speedKmh = 40f, nowElapsedMs = 1_100L)
        assertNull(second)
        assertEquals("throttled", rt.debug.skippedReason)
        // After time trigger
        val third = rt.maybeCorrect(true, pose, speedKmh = 40f, nowElapsedMs = 3_200L)
        assertNotNull(third)
    }

    @Test
    fun runtimeRetriesFasterAfterWeakMatch() {
        val primary = RoadEdge(
            id = 1L,
            highwayClass = "primary",
            lengthM = 800.0,
            fromNode = 0,
            toNode = 1,
            coords = doubleArrayOf(37.60, 55.75, 37.62, 55.75),
        )
        val graph = RoadGraph(
            "fast-retry",
            4,
            doubleArrayOf(37.59, 55.74, 37.63, 55.76),
            listOf(primary),
        )
        RoadGraphStore.clear()
        val dir = createTempDir(prefix = "roads-fast-retry-")
        installSingleTileBundle(dir, graph)
        val rt = RoadMatchRuntime(
            mapsDir = { dir },
            pathTriggerM = 1000.0,
            timeTriggerMs = 2_000L,
            switchConfirmCount = 1,
        )
        assertNotNull(
            rt.maybeCorrect(
                true,
                RoadMatchPose(55.75002, 37.61, 90f),
                speedKmh = 40f,
                nowElapsedMs = 1_000L,
            ),
        )
        assertEquals(1L, rt.debug.edgeId)

        // Far north + heading incompatible with the only eastbound edge → reject.
        val rejected = rt.maybeCorrect(
            true,
            RoadMatchPose(55.7520, 37.61, 0f),
            speedKmh = 40f,
            nowElapsedMs = 3_100L,
        )
        assertNull(rejected)
        assertTrue(
            rt.debug.skippedReason == "no_candidate" ||
                rt.debug.skippedReason == "low_confidence" ||
                rt.debug.skippedReason == "switch_rejected",
        )

        // With steady 2 s throttle this would still be blocked; recover mode allows ~1 s.
        val early = rt.maybeCorrect(
            true,
            RoadMatchPose(55.7520, 37.6102, 0f),
            speedKmh = 40f,
            nowElapsedMs = 4_200L,
        )
        assertTrue(
            "expected recover retry within 1.1 s, got skipped=${rt.debug.skippedReason}",
            early != null || rt.debug.skippedReason != "throttled",
        )
    }

    @Test
    fun runtimeTurnTriggerUsesDefaultEighteenDegrees() {
        val east = RoadEdge(
            id = 1L,
            highwayClass = "primary",
            lengthM = 600.0,
            fromNode = 0,
            toNode = 1,
            coords = doubleArrayOf(37.60, 55.75, 37.62, 55.75),
        )
        val north = RoadEdge(
            id = 2L,
            highwayClass = "primary",
            lengthM = 600.0,
            fromNode = 1,
            toNode = 2,
            coords = doubleArrayOf(37.61, 55.75, 37.61, 55.752),
        )
        val graph = RoadGraph(
            "turn18",
            4,
            doubleArrayOf(37.59, 55.74, 37.63, 55.76),
            listOf(east, north),
        )
        RoadGraphStore.clear()
        val dir = createTempDir(prefix = "roads-turn18-")
        installSingleTileBundle(dir, graph)
        val rt = RoadMatchRuntime(
            mapsDir = { dir },
            pathTriggerM = 1000.0,
            timeTriggerMs = 60_000L,
            // default turnTriggerDeg = 18
            switchConfirmCount = 1,
        )
        assertNotNull(
            rt.maybeCorrect(
                true,
                RoadMatchPose(55.75002, 37.605, 90f),
                speedKmh = 40f,
                nowElapsedMs = 1_000L,
            ),
        )
        // 20° heading change — below old 25°, at/above new 18° default → not throttled.
        val turned = rt.maybeCorrect(
            true,
            RoadMatchPose(55.75002, 37.606, 110f),
            speedKmh = 40f,
            nowElapsedMs = 1_200L,
        )
        assertTrue(
            "20° turn should bypass throttle with 18° trigger, skipped=${rt.debug.skippedReason}",
            turned != null || rt.debug.skippedReason != "throttled",
        )
    }

    @Test
    fun runtimeLoadsOnlyCoveringBundleTile() {
        val near = RoadGraph(
            "ru-bundle", 4, doubleArrayOf(37.0, 55.0, 37.6, 56.0),
            listOf(
                RoadEdge(
                    10L, "primary", 500.0, 0, 1,
                    doubleArrayOf(37.40, 55.50, 37.42, 55.50),
                ),
            ),
        )
        val far = RoadGraph(
            "ru-bundle", 4, doubleArrayOf(38.0, 55.0, 38.6, 56.0),
            listOf(
                RoadEdge(
                    20L, "primary", 500.0, 2, 3,
                    doubleArrayOf(38.40, 55.50, 38.42, 55.50),
                ),
            ),
        )
        val maps = createTempDir(prefix = "bundle-runtime-")
        val bundle = File(maps, "ru-bundle${RoadMapBundle.INSTALL_SUFFIX}")
        File(bundle, "tiles").mkdirs()
        File(bundle, "tiles/near.tboxroads").writeBytes(packBytesFor(near))
        File(bundle, "tiles/far.tboxroads").writeBytes(packBytesFor(far))
        File(bundle, RoadMapBundle.INDEX_FILE).writeText(
            """
            {"format":1,"regionId":"ru-bundle","graphVersion":4,
             "bbox":[37.0,55.0,38.6,56.0],
             "tiles":[
               {"id":"near","file":"tiles/near.tboxroads","bbox":[37.0,55.0,37.6,56.0],"bytes":1},
               {"id":"far","file":"tiles/far.tboxroads","bbox":[38.0,55.0,38.6,56.0],"bytes":1}
             ]}
            """.trimIndent(),
        )
        RoadGraphStore.clear()
        val runtime = RoadMatchRuntime(
            mapsDir = { maps },
            timeTriggerMs = 1L,
            switchConfirmCount = 1,
        )
        val matched = runtime.maybeCorrect(
            true,
            RoadMatchPose(55.50005, 37.41, 90f),
            speedKmh = 30f,
            nowElapsedMs = 1_000L,
        )
        assertNotNull(matched)
        assertNotNull(RoadGraphStore.peek("ru-bundle/near"))
        assertNull(RoadGraphStore.peek("ru-bundle/far"))
    }

    @Test
    fun overlappingTilesDoNotCreateDuplicateRunnerUp() {
        val edge = RoadEdge(
            42L, "primary", 500.0, 0, 1,
            doubleArrayOf(37.40, 55.50, 37.42, 55.50),
        )
        val a = RoadGraph("r", 4, doubleArrayOf(37.0, 55.0, 37.6, 56.0), listOf(edge))
        val b = RoadGraph("r", 4, doubleArrayOf(37.3, 55.0, 37.9, 56.0), listOf(edge))
        val ranked = RoadMapMatcher.rankCandidates(
            RoadMatchPose(55.50005, 37.41, 90f),
            listOf(a, b),
            previousEdgeId = null,
            previousRegionId = null,
        )
        assertEquals(1, ranked.size)
        assertEquals(42L, ranked.single().edge.id)
    }

    @Test
    fun prefersConnectedMajorRoadOverDisconnectedYard() {
        // Eastbound primary; a disconnected residential parallel 5 m north.
        val primary = RoadEdge(
            id = 1L,
            highwayClass = "primary",
            lengthM = 500.0,
            fromNode = 0,
            toNode = 1,
            coords = doubleArrayOf(37.60, 55.75, 37.62, 55.75),
        )
        val yard = RoadEdge(
            id = 2L,
            highwayClass = "residential",
            lengthM = 500.0,
            fromNode = 10,
            toNode = 11,
            // Parallel, slightly closer to pose north of primary.
            coords = doubleArrayOf(37.60, 55.75008, 37.62, 55.75008),
        )
        val graph = RoadGraph(
            regionId = "test",
            graphVersion = 1,
            bbox = doubleArrayOf(37.59, 55.74, 37.63, 55.76),
            edges = listOf(primary, yard),
        )
        // Pose between them, heading east, previously on primary.
        val pose = RoadMatchPose(lat = 55.75004, lon = 37.61, bearingDeg = 90f)
        val best = RoadMapMatcher.pickBest(
            pose, listOf(graph),
            previousEdgeId = 1L,
            previousRegionId = "test",
            previousHighwayClass = "primary",
        )
        assertNotNull(best)
        assertEquals(1L, best!!.edge.id)
    }

    @Test
    fun spatialEndpointsConnectAdjacentEdges() {
        val a = RoadEdge(
            id = 1L,
            highwayClass = "secondary",
            lengthM = 100.0,
            fromNode = 100,
            toNode = 101, // unique ids — connectivity via coordinates
            coords = doubleArrayOf(37.60, 55.75, 37.61, 55.75),
        )
        val b = RoadEdge(
            id = 2L,
            highwayClass = "secondary",
            lengthM = 100.0,
            fromNode = 200,
            toNode = 201,
            coords = doubleArrayOf(37.61, 55.75, 37.62, 55.75),
        )
        val graph = RoadGraph("c", 1, doubleArrayOf(37.59, 55.74, 37.63, 55.76), listOf(a, b))
        assertTrue(graph.isConnected(1L, 2L))
        assertTrue(graph.neighbors(1L).contains(2L))
    }

    @Test
    fun lowConfidenceDoesNotCorrectPose() {
        val east = RoadEdge(
            id = 1L,
            highwayClass = "primary",
            lengthM = 500.0,
            fromNode = 0,
            toNode = 1,
            coords = doubleArrayOf(37.60, 55.75, 37.62, 55.75),
        )
        val north = RoadEdge(
            id = 2L,
            highwayClass = "primary",
            lengthM = 500.0,
            fromNode = 0,
            toNode = 2,
            coords = doubleArrayOf(37.61, 55.75, 37.61, 55.76),
        )
        val graph = RoadGraph("amb", 1, doubleArrayOf(37.59, 55.74, 37.63, 55.77), listOf(east, north))
        RoadGraphStore.clear()
        val dir = createTempDir(prefix = "roads-amb-")
        installSingleTileBundle(dir, graph)
        val rt = RoadMatchRuntime(
            mapsDir = { dir },
            pathTriggerM = 1.0,
            timeTriggerMs = 1L,
            switchConfirmCount = 1,
        )
        // Exactly on the junction, heading NE — both candidates almost equal → low confidence.
        val pose = RoadMatchPose(55.75, 37.61, 45f)
        val out = rt.maybeCorrect(true, pose, speedKmh = 40f, nowElapsedMs = 5_000L)
        assertNull(out)
        assertEquals("low_confidence", rt.debug.skippedReason)
        assertTrue(rt.debug.candidateCount >= 2)
    }

    @Test
    fun confidenceHighOnClearWinner() {
        val ranked = listOf(
            RoadMapMatcher.Candidate(
                edge = RoadEdge(1, "primary", 100.0, 0, 1, doubleArrayOf(0.0, 0.0, 1.0, 0.0)),
                regionId = "r",
                crossTrackM = 2.0,
                alongTrackM = 10.0,
                projLat = 0.0,
                projLon = 0.0,
                edgeAzimuthDeg = 90f,
                score = 2.0,
                connectedFromPrevious = true,
            ),
            RoadMapMatcher.Candidate(
                edge = RoadEdge(2, "residential", 100.0, 2, 3, doubleArrayOf(0.0, 0.0, 1.0, 0.0)),
                regionId = "r",
                crossTrackM = 8.0,
                alongTrackM = 10.0,
                projLat = 0.0,
                projLon = 0.0,
                edgeAzimuthDeg = 90f,
                score = 12.0,
                connectedFromPrevious = false,
            ),
        )
        assertEquals(RoadMatchConfidence.HIGH, RoadMapMatcher.confidenceOf(ranked))
    }

    @Test
    fun holdsPreviousEdgeWhenNewCandidatesAreAmbiguous() {
        val east = RoadEdge(
            id = 1L,
            highwayClass = "primary",
            lengthM = 800.0,
            fromNode = 0,
            toNode = 1,
            coords = doubleArrayOf(37.60, 55.75, 37.63, 55.75),
        )
        val north = RoadEdge(
            id = 2L,
            highwayClass = "primary",
            lengthM = 500.0,
            fromNode = 1,
            toNode = 2,
            coords = doubleArrayOf(37.62, 55.75, 37.62, 55.77),
        )
        val graph = RoadGraph("hold", 1, doubleArrayOf(37.59, 55.74, 37.64, 55.78), listOf(east, north))
        RoadGraphStore.clear()
        val dir = createTempDir(prefix = "roads-hold-")
        installSingleTileBundle(dir, graph)
        val rt = RoadMatchRuntime(
            mapsDir = { dir },
            pathTriggerM = 1.0,
            timeTriggerMs = 1L,
            switchConfirmCount = 3,
        )
        // Lock onto eastbound primary.
        val first = rt.maybeCorrect(
            true,
            RoadMatchPose(55.75005, 37.61, 90f),
            speedKmh = 40f,
            nowElapsedMs = 1_000L,
        )
        assertNotNull(first)
        assertEquals(1L, rt.debug.edgeId)

        // Ambiguous NE heading near junction — should HOLD previous east edge, not freeze.
        val held = rt.maybeCorrect(
            true,
            RoadMatchPose(55.75008, 37.62, 45f),
            speedKmh = 40f,
            nowElapsedMs = 3_000L,
        )
        assertNotNull(held)
        assertEquals(1L, rt.debug.edgeId)
        assertTrue(
            rt.debug.confidence == "HOLD_EDGE" ||
                rt.debug.confidence == "MEDIUM" ||
                rt.debug.confidence == "HIGH",
        )
    }

    @Test
    fun prefersOnewayAlignedParallelStreet() {
        // Dual carriageway: north lane eastbound, south lane westbound (coords along travel).
        val eastOnly = RoadEdge(
            id = 1L,
            highwayClass = "primary",
            lengthM = 500.0,
            fromNode = 0,
            toNode = 1,
            coords = doubleArrayOf(37.60, 55.7502, 37.62, 55.7502),
            oneway = 1,
        )
        val westOnly = RoadEdge(
            id = 2L,
            highwayClass = "primary",
            lengthM = 500.0,
            fromNode = 2,
            toNode = 3,
            // Digitized in travel direction (east → west).
            coords = doubleArrayOf(37.62, 55.7499, 37.60, 55.7499),
            oneway = 1,
        )
        val graph = RoadGraph(
            regionId = "ow",
            graphVersion = 3,
            bbox = doubleArrayOf(37.59, 55.74, 37.63, 55.76),
            edges = listOf(eastOnly, westOnly),
        )
        val pose = RoadMatchPose(lat = 55.75, lon = 37.61, bearingDeg = 90f) // east
        val best = RoadMapMatcher.pickBest(pose, listOf(graph), null, null)
        assertNotNull(best)
        assertEquals(1L, best!!.edge.id)
        assertTrue(!best.againstOneway)

        val westPose = RoadMatchPose(lat = 55.75, lon = 37.61, bearingDeg = 270f)
        val westBest = RoadMapMatcher.pickBest(westPose, listOf(graph), null, null)
        assertNotNull(westBest)
        assertEquals(2L, westBest!!.edge.id)
        assertTrue(!westBest.againstOneway)
    }

    @Test
    fun onewayPenaltySkippedWhenAllowAgainst() {
        val eastOnly = RoadEdge(
            id = 1L,
            highwayClass = "primary",
            lengthM = 500.0,
            fromNode = 0,
            toNode = 1,
            coords = doubleArrayOf(37.60, 55.75, 37.62, 55.75),
            oneway = 1,
        )
        val graph = RoadGraph(
            regionId = "ow",
            graphVersion = 3,
            bbox = doubleArrayOf(37.59, 55.74, 37.63, 55.76),
            edges = listOf(eastOnly),
        )
        val westPose = RoadMatchPose(lat = 55.75005, lon = 37.61, bearingDeg = 270f)
        val penalized = RoadMapMatcher.pickBest(westPose, listOf(graph), null, null)
        assertNotNull(penalized)
        assertTrue(penalized!!.againstOneway)
        assertTrue(penalized.score >= RoadMapMatcher.ONEWAY_AGAINST_PENALTY)

        val allowed = RoadMapMatcher.pickBest(
            westPose, listOf(graph), null, null, allowAgainstOneway = true,
        )
        assertNotNull(allowed)
        assertTrue(allowed!!.againstOneway)
        assertTrue(allowed.score < RoadMapMatcher.ONEWAY_AGAINST_PENALTY)
    }

    @Test
    fun matchReturnsNullWhenDisabled() {
        val rt = RoadMatchRuntime(mapsDir = { java.io.File("/tmp/none") })
        assertNull(
            rt.maybeCorrect(
                enabled = false,
                pose = RoadMatchPose(55.75, 37.61, 90f),
                speedKmh = 40f,
                nowElapsedMs = 100L,
            ),
        )
        assertEquals("disabled", rt.debug.skippedReason)
    }

    @Test
    fun softCorrectDoesNotPullBearingWhenResidualLarge() {
        val graph = horizontalEdge()
        val edge = graph.edges.first()
        // Residual 50° (> inhibit 28°) — sticky HOLD_EDGE must not yank heading toward 90°.
        val pose = RoadMatchPose(lat = 55.7502, lon = 37.61, bearingDeg = 40f)
        val proj = RoadMapMatcher.projectOntoEdge(pose.lat, pose.lon, edge)!!
        val cand = RoadMapMatcher.Candidate(
            edge = edge,
            regionId = graph.regionId,
            crossTrackM = proj.crossTrackM,
            alongTrackM = proj.alongTrackM,
            projLat = proj.lat,
            projLon = proj.lon,
            edgeAzimuthDeg = 90f,
            score = proj.crossTrackM,
            connectedFromPrevious = true,
        )
        assertTrue(
            RoadMapMatcher.smallestAngleDeg(pose.bearingDeg, cand.edgeAzimuthDeg) >=
                RoadMapMatcher.BEARING_INHIBIT_RESIDUAL_DEG,
        )
        val corrected = RoadMapMatcher.softCorrect(pose, cand, turnActive = false)
        assertEquals(pose.bearingDeg, corrected.bearingDeg, 0.05f)
        // Large residual also freezes lateral — do not drag onto the old edge.
        assertEquals(pose.lat, corrected.lat, 1e-9)
        assertEquals(pose.lon, corrected.lon, 1e-9)
    }

    @Test
    fun softCorrectZerosLateralWhenTurnActiveEvenIfClose() {
        val graph = horizontalEdge()
        val pose = RoadMatchPose(lat = 55.75008, lon = 37.61, bearingDeg = 88f)
        val best = RoadMapMatcher.pickBest(pose, listOf(graph), null, null)
        assertNotNull(best)
        val withTurn = RoadMapMatcher.softCorrect(pose, best!!, turnActive = true)
        assertEquals(pose.lat, withTurn.lat, 1e-9)
        assertEquals(pose.lon, withTurn.lon, 1e-9)
        val withoutTurn = RoadMapMatcher.softCorrect(pose, best, turnActive = false)
        assertTrue(withoutTurn.lat < pose.lat)
    }

    @Test
    fun lateralSnapScaleFadesWhenFarOrAmbiguous() {
        assertEquals(
            1.0,
            RoadMapMatcher.lateralSnapScale(
                crossTrackM = 3.0,
                residualDeg = 5f,
                turnActive = false,
            ),
            1e-6,
        )
        assertEquals(
            0.0,
            RoadMapMatcher.lateralSnapScale(
                crossTrackM = 3.0,
                residualDeg = 10f,
                turnActive = true,
            ),
            1e-6,
        )
        val far = RoadMapMatcher.lateralSnapScale(
            crossTrackM = 16.0,
            residualDeg = 5f,
            turnActive = false,
        )
        assertTrue("far xt should weaken, got $far", far < 0.6 && far > 0.15)
        val ambiguous = RoadMapMatcher.lateralSnapScale(
            crossTrackM = 3.0,
            residualDeg = 5f,
            turnActive = false,
            candidateCount = 3,
            scoreGap = 0.4,
        )
        assertEquals(0.2, ambiguous, 1e-6)
        val clearPair = RoadMapMatcher.lateralSnapScale(
            crossTrackM = 3.0,
            residualDeg = 5f,
            turnActive = false,
            candidateCount = 2,
            scoreGap = 4.0,
        )
        assertEquals(1.0, clearPair, 1e-6)
    }

    @Test
    fun softCorrectInhibitsBearingWhenTurnActiveEvenIfAligned() {
        val graph = horizontalEdge()
        val pose = RoadMatchPose(lat = 55.7502, lon = 37.61, bearingDeg = 85f)
        val best = RoadMapMatcher.pickBest(pose, listOf(graph), null, null)
        assertNotNull(best)
        val withTurn = RoadMapMatcher.softCorrect(pose, best!!, turnActive = true)
        assertEquals(pose.bearingDeg, withTurn.bearingDeg, 0.05f)
        val withoutTurn = RoadMapMatcher.softCorrect(pose, best, turnActive = false)
        assertTrue(
            RoadMapMatcher.smallestAngleDeg(withoutTurn.bearingDeg, best.edgeAzimuthDeg) <
                RoadMapMatcher.smallestAngleDeg(pose.bearingDeg, best.edgeAzimuthDeg),
        )
    }

    @Test
    fun softCorrectPullsBearingFasterTowardEdgeWhenNotTurning() {
        val graph = horizontalEdge()
        val edge = graph.edges.first()
        // Residual ~10° — below inhibit; edge catch-up may exceed the steady 6°/tick cap.
        val pose = RoadMatchPose(lat = 55.75005, lon = 37.61, bearingDeg = 80f)
        val proj = RoadMapMatcher.projectOntoEdge(pose.lat, pose.lon, edge)!!
        val cand = RoadMapMatcher.Candidate(
            edge = edge,
            regionId = graph.regionId,
            crossTrackM = proj.crossTrackM,
            alongTrackM = proj.alongTrackM,
            projLat = proj.lat,
            projLon = proj.lon,
            edgeAzimuthDeg = 90f,
            score = proj.crossTrackM,
            connectedFromPrevious = true,
        )
        val residual = RoadMapMatcher.smallestAngleDeg(pose.bearingDeg, cand.edgeAzimuthDeg)
        assertTrue(residual < RoadMapMatcher.BEARING_INHIBIT_RESIDUAL_DEG)
        val corrected = RoadMapMatcher.softCorrect(pose, cand, turnActive = false)
        val pulled = RoadMapMatcher.smallestAngleDeg(pose.bearingDeg, corrected.bearingDeg)
        assertTrue("expected catch-up > steady cap, got $pulled", pulled > RoadMapMatcher.MAX_BEARING_STEP_DEG)
        assertTrue(pulled <= RoadMapMatcher.MAX_BEARING_STEP_EDGE_CATCHUP_DEG + 0.05f)
        assertTrue(
            RoadMapMatcher.smallestAngleDeg(corrected.bearingDeg, cand.edgeAzimuthDeg) < residual,
        )
    }

    @Test
    fun runtimeAcceptsEdgeSwitchOnFirstTurnPick() {
        val east = RoadEdge(
            id = 1L,
            highwayClass = "primary",
            lengthM = 500.0,
            fromNode = 0,
            toNode = 1,
            coords = doubleArrayOf(37.60, 55.75, 37.62, 55.75),
        )
        val north = RoadEdge(
            id = 2L,
            highwayClass = "primary",
            lengthM = 500.0,
            fromNode = 1,
            toNode = 3,
            coords = doubleArrayOf(37.61, 55.75, 37.61, 55.76),
        )
        val graph = RoadGraph(
            regionId = "turn",
            graphVersion = 1,
            bbox = doubleArrayOf(37.59, 55.74, 37.63, 55.77),
            edges = listOf(east, north),
        )
        RoadGraphStore.clear()
        val dir = createTempDir(prefix = "roads-turn-")
        installSingleTileBundle(dir, graph)

        val rt = RoadMatchRuntime(
            mapsDir = { dir },
            pathTriggerM = 1000.0,
            timeTriggerMs = 2_000L,
            turnTriggerDeg = 25f,
            minSpeedKmh = 1.8f,
            switchConfirmCount = 3, // Phase E default; turn should accept at 1
        )
        val eastPose = RoadMatchPose(55.75005, 37.6105, 90f)
        val first = rt.maybeCorrect(true, eastPose, speedKmh = 40f, nowElapsedMs = 1_000L)
        assertNotNull(first)
        assertEquals(1L, rt.debug.edgeId)

        // Large heading change → dueTurn; first pick of north edge must switch immediately.
        // Stay near the north centerline so the candidate is heading-aligned.
        val northPose = RoadMatchPose(55.75015, 37.61002, 5f)
        val switched = rt.maybeCorrect(true, northPose, speedKmh = 40f, nowElapsedMs = 1_500L)
        assertNotNull(switched)
        assertEquals(2L, rt.debug.edgeId)
        assertTrue(rt.debug.switchedEdge)
        // Bearing must not be pulled back toward the old east edge (~90°).
        assertTrue(RoadMapMatcher.smallestAngleDeg(switched!!.bearingDeg, 90f) > 60f)
    }

    @Test
    fun doesNotBounceBackToAbandonedEdgeRightAfterTurn() {
        val east = RoadEdge(
            id = 1L,
            highwayClass = "primary",
            lengthM = 500.0,
            fromNode = 0,
            toNode = 1,
            coords = doubleArrayOf(37.60, 55.75, 37.62, 55.75),
        )
        val north = RoadEdge(
            id = 2L,
            highwayClass = "primary",
            lengthM = 500.0,
            fromNode = 1,
            toNode = 3,
            coords = doubleArrayOf(37.61, 55.75, 37.61, 55.76),
        )
        val graph = RoadGraph(
            regionId = "bounce",
            graphVersion = 1,
            bbox = doubleArrayOf(37.59, 55.74, 37.63, 55.77),
            edges = listOf(east, north),
        )
        RoadGraphStore.clear()
        val dir = createTempDir(prefix = "roads-bounce-")
        installSingleTileBundle(dir, graph)

        val rt = RoadMatchRuntime(
            mapsDir = { dir },
            pathTriggerM = 1.0,
            timeTriggerMs = 1L,
            turnTriggerDeg = 25f,
            switchConfirmCount = 3,
        )
        assertNotNull(
            rt.maybeCorrect(true, RoadMatchPose(55.75005, 37.6105, 90f), 40f, 1_000L),
        )
        assertEquals(1L, rt.debug.edgeId)

        // Turn onto north (fast confirm).
        assertNotNull(
            rt.maybeCorrect(true, RoadMatchPose(55.7502, 37.61002, 5f), 40f, 1_500L),
        )
        assertEquals(2L, rt.debug.edgeId)
        assertTrue(rt.debug.switchedEdge)

        // Within return guard: ambiguous pose near the junction that would prefer east again.
        // Must stay on north (HOLD / keep), not bounce back to edge 1.
        val kept = rt.maybeCorrect(
            true,
            RoadMatchPose(55.75012, 37.6102, 80f),
            speedKmh = 40f,
            nowElapsedMs = 2_500L,
        )
        assertNotNull(kept)
        assertEquals(2L, rt.debug.edgeId)
        assertTrue(
            rt.debug.rejectReason == "return_to_prior" ||
                rt.debug.confidence == "HOLD_EDGE" ||
                rt.debug.edgeId == 2L,
        )
        assertTrue(rt.debug.edgeId != 1L)
    }

    @Test
    fun runtimeIgnoresRootLevelMonolithPack() {
        val edge = RoadEdge(
            id = 9L,
            highwayClass = "primary",
            lengthM = 800.0,
            fromNode = 0,
            toNode = 1,
            coords = doubleArrayOf(37.60, 55.75, 37.62, 55.75),
        )
        val graph = RoadGraph("mono", 3, doubleArrayOf(37.59, 55.74, 37.63, 55.76), listOf(edge))
        RoadGraphStore.clear()
        val dir = createTempDir(prefix = "roads-mono-")
        File(dir, "mono.tboxroads").writeBytes(packBytesFor(graph))
        val rt = RoadMatchRuntime(
            mapsDir = { dir },
            pathTriggerM = 1.0,
            timeTriggerMs = 1L,
            switchConfirmCount = 1,
        )
        val out = rt.maybeCorrect(
            true,
            RoadMatchPose(55.75005, 37.61, 90f),
            speedKmh = 40f,
            nowElapsedMs = 1_000L,
        )
        assertNull(out)
        assertEquals("no_graph", rt.debug.skippedReason)
        assertNull(RoadGraphStore.peek("mono"))
    }

    @Test
    fun hardRejectsAgainstOnewayLinkWhenRanking() {
        // MKAD-like: eastbound primary; one-way westbound ramp nearby (against travel).
        val primary = RoadEdge(
            id = 10L,
            highwayClass = "primary",
            lengthM = 800.0,
            fromNode = 0,
            toNode = 1,
            coords = doubleArrayOf(37.60, 55.75, 37.63, 55.75),
            oneway = 1,
        )
        val againstLink = RoadEdge(
            id = 1244L,
            highwayClass = "primary_link",
            lengthM = 200.0,
            fromNode = 5,
            toNode = 6,
            // Digitized westbound; eastbound travel is againstOneway.
            coords = doubleArrayOf(37.62, 55.75015, 37.60, 55.75015),
            oneway = 1,
        )
        val graph = RoadGraph(
            "exit",
            4,
            doubleArrayOf(37.59, 55.74, 37.64, 55.76),
            listOf(primary, againstLink),
        )
        val pose = RoadMatchPose(55.75005, 37.61, 90f)
        val ranked = RoadMapMatcher.rankCandidates(
            pose = pose,
            graphs = listOf(graph),
            previousEdgeId = 10L,
            previousRegionId = "exit",
            previousHighwayClass = "primary",
        )
        assertTrue(ranked.none { it.edge.id == 1244L })
        assertEquals(10L, ranked.first().edge.id)
    }

    @Test
    fun confidenceLowForDisconnectedSoleOrAgainstOneway() {
        val disconnectedSole = listOf(
            RoadMapMatcher.Candidate(
                edge = RoadEdge(1244, "primary_link", 100.0, 0, 1, doubleArrayOf(0.0, 0.0, 1.0, 0.0)),
                regionId = "r",
                crossTrackM = 8.0,
                alongTrackM = 10.0,
                projLat = 0.0,
                projLon = 0.0,
                edgeAzimuthDeg = 90f,
                score = 8.0,
                connectedFromPrevious = false,
            ),
        )
        assertEquals(RoadMatchConfidence.LOW, RoadMapMatcher.confidenceOf(disconnectedSole))

        val against = listOf(
            RoadMapMatcher.Candidate(
                edge = RoadEdge(
                    2, "primary", 100.0, 0, 1, doubleArrayOf(0.0, 0.0, 1.0, 0.0), oneway = 1,
                ),
                regionId = "r",
                crossTrackM = 5.0,
                alongTrackM = 10.0,
                projLat = 0.0,
                projLon = 0.0,
                edgeAzimuthDeg = 270f,
                score = 5.0,
                connectedFromPrevious = true,
                againstOneway = true,
            ),
        )
        assertEquals(RoadMatchConfidence.LOW, RoadMapMatcher.confidenceOf(against))
    }

    @Test
    fun runtimeHoldsInsteadOfJumpingOntoAgainstOnewayLink() {
        val primary = RoadEdge(
            id = 10L,
            highwayClass = "primary",
            lengthM = 800.0,
            fromNode = 0,
            toNode = 1,
            coords = doubleArrayOf(37.60, 55.75, 37.63, 55.75),
            oneway = 1,
        )
        val againstLink = RoadEdge(
            id = 1244L,
            highwayClass = "primary_link",
            lengthM = 200.0,
            fromNode = 5,
            toNode = 6,
            coords = doubleArrayOf(37.62, 55.7502, 37.60, 55.7502),
            oneway = 1,
        )
        val graph = RoadGraph(
            "rt-exit",
            4,
            doubleArrayOf(37.59, 55.74, 37.64, 55.76),
            listOf(primary, againstLink),
        )
        RoadGraphStore.clear()
        val dir = createTempDir(prefix = "roads-exit-")
        installSingleTileBundle(dir, graph)
        val rt = RoadMatchRuntime(
            mapsDir = { dir },
            pathTriggerM = 1.0,
            timeTriggerMs = 1L,
            switchConfirmCount = 1,
        )
        val first = rt.maybeCorrect(
            true,
            RoadMatchPose(55.75005, 37.61, 90f),
            speedKmh = 40f,
            nowElapsedMs = 1_000L,
        )
        assertNotNull(first)
        assertEquals(10L, rt.debug.edgeId)

        // Still on primary corridor; against-oneway link must not become current edge.
        val next = rt.maybeCorrect(
            true,
            RoadMatchPose(55.75012, 37.615, 88f),
            speedKmh = 40f,
            nowElapsedMs = 3_000L,
        )
        assertNotNull(next)
        assertEquals(10L, rt.debug.edgeId)
        assertTrue(rt.debug.edgeId != 1244L)
        assertNotNull(rt.debug.inputBearingDeg)
        assertNotNull(rt.debug.edgeBearingDeg)
        assertNotNull(rt.debug.turnActive)
    }

    @Test
    fun runtimeRejectsDisconnectedLinkSwitch() {
        // Two disconnected edges: primary then a parallel primary_link (no shared nodes).
        val primary = RoadEdge(
            id = 1L,
            highwayClass = "primary",
            lengthM = 600.0,
            fromNode = 0,
            toNode = 1,
            coords = doubleArrayOf(37.60, 55.75, 37.62, 55.75),
        )
        val orphanLink = RoadEdge(
            id = 99L,
            highwayClass = "motorway_link",
            lengthM = 400.0,
            fromNode = 10,
            toNode = 11,
            // Parallel, ~15 m north — heading-aligned but disconnected.
            coords = doubleArrayOf(37.60, 55.75015, 37.62, 55.75015),
        )
        val graph = RoadGraph(
            "disc",
            4,
            doubleArrayOf(37.59, 55.74, 37.63, 55.76),
            listOf(primary, orphanLink),
        )
        RoadGraphStore.clear()
        val dir = createTempDir(prefix = "roads-disc-")
        installSingleTileBundle(dir, graph)
        val rt = RoadMatchRuntime(
            mapsDir = { dir },
            pathTriggerM = 1.0,
            timeTriggerMs = 1L,
            switchConfirmCount = 1,
        )
        val first = rt.maybeCorrect(
            true,
            RoadMatchPose(55.75002, 37.61, 90f),
            speedKmh = 40f,
            nowElapsedMs = 1_000L,
        )
        assertNotNull(first)
        assertEquals(1L, rt.debug.edgeId)

        // Nudge toward the orphan link — must HOLD primary or reject, not switch to 99.
        val second = rt.maybeCorrect(
            true,
            RoadMatchPose(55.75012, 37.61, 90f),
            speedKmh = 40f,
            nowElapsedMs = 3_000L,
        )
        assertTrue(second == null || rt.debug.edgeId == 1L)
        assertTrue(rt.debug.edgeId != 99L)
        if (second == null) {
            assertTrue(
                rt.debug.rejectReason == "disconnected_link" ||
                    rt.debug.skippedReason == "low_confidence" ||
                    rt.debug.skippedReason == "switch_rejected",
            )
        }
    }

    @Test
    fun rematchesAfterPhantomPreviousLostHold() {
        // Far east primary (initial sticky) + disconnected secondary near the pose after a
        // jump — field interchange case: hold fails, phantom previous blocked LOW forever.
        val far = RoadEdge(
            id = 22671L,
            highwayClass = "primary",
            lengthM = 800.0,
            fromNode = 0,
            toNode = 1,
            coords = doubleArrayOf(37.60, 55.75, 37.62, 55.75),
        )
        val near = RoadEdge(
            id = 500L,
            highwayClass = "secondary",
            lengthM = 800.0,
            fromNode = 20,
            toNode = 21,
            // ~80 m north — outside hold radius, inside candidate radius after rematch seed.
            coords = doubleArrayOf(37.60, 55.75072, 37.62, 55.75072),
        )
        val graph = RoadGraph(
            "phantom",
            4,
            doubleArrayOf(37.59, 55.74, 37.63, 55.76),
            listOf(far, near),
        )
        RoadGraphStore.clear()
        val dir = createTempDir(prefix = "roads-phantom-")
        installSingleTileBundle(dir, graph)
        val rt = RoadMatchRuntime(
            mapsDir = { dir },
            pathTriggerM = 1.0,
            timeTriggerMs = 1L,
            switchConfirmCount = 1,
            holdPreviousRadiusM = 24.0,
        )
        val seed = rt.maybeCorrect(
            true,
            RoadMatchPose(55.75002, 37.61, 90f),
            speedKmh = 40f,
            nowElapsedMs = 1_000L,
        )
        assertNotNull(seed)
        assertEquals(22671L, rt.debug.edgeId)

        // Pose on the secondary: previous unholdable, rematch should clear phantom and snap.
        val rematch = rt.maybeCorrect(
            true,
            RoadMatchPose(55.75072, 37.61, 90f),
            speedKmh = 40f,
            nowElapsedMs = 3_000L,
        )
        assertNotNull(rematch)
        assertEquals(500L, rt.debug.edgeId)
        assertEquals(500L, rt.debug.candidateEdgeId)
    }

    @Test
    fun doesNotHoldAgainstOnewayWhenMovingForward() {
        val oneway = RoadEdge(
            id = 1L,
            highwayClass = "primary",
            lengthM = 800.0,
            fromNode = 0,
            toNode = 1,
            coords = doubleArrayOf(37.60, 55.75, 37.62, 55.75),
            oneway = 1, // only eastbound
        )
        val alt = RoadEdge(
            id = 2L,
            highwayClass = "primary",
            lengthM = 800.0,
            fromNode = 0,
            toNode = 2,
            // Continues east from the same west endpoint (connected), slightly south.
            coords = doubleArrayOf(37.60, 55.75, 37.62, 55.74990),
        )
        val graph = RoadGraph(
            "ow-hold",
            4,
            doubleArrayOf(37.59, 55.74, 37.63, 55.76),
            listOf(oneway, alt),
        )
        RoadGraphStore.clear()
        val dir = createTempDir(prefix = "roads-ow-hold-")
        installSingleTileBundle(dir, graph)
        val rt = RoadMatchRuntime(
            mapsDir = { dir },
            pathTriggerM = 1.0,
            timeTriggerMs = 1L,
            switchConfirmCount = 1,
        )
        val east = rt.maybeCorrect(
            true,
            RoadMatchPose(55.75002, 37.61, 90f),
            speedKmh = 40f,
            nowElapsedMs = 1_000L,
        )
        assertNotNull(east)
        assertEquals(1L, rt.debug.edgeId)

        // Travel west against oneway — must not sticky-HOLD edge 1.
        val west = rt.maybeCorrect(
            true,
            RoadMatchPose(55.75002, 37.61, 270f),
            speedKmh = 40f,
            nowElapsedMs = 3_000L,
            allowAgainstOneway = false,
        )
        assertTrue(rt.debug.confidence != "HOLD_EDGE" || rt.debug.againstOneway != true)
        if (west != null) {
            assertTrue(rt.debug.edgeId != 1L || rt.debug.againstOneway != true)
        }
    }

    @Test
    fun crossTileEndpointJunctionCountsAsConnected() {
        val a = RoadEdge(
            id = 10L,
            highwayClass = "primary",
            lengthM = 200.0,
            fromNode = 1,
            toNode = 2,
            coords = doubleArrayOf(37.60, 55.75, 37.601, 55.75),
        )
        val b = RoadEdge(
            id = 20L,
            highwayClass = "primary",
            lengthM = 200.0,
            fromNode = 99,
            toNode = 100,
            // Shares the east endpoint of A (~0 m), but lives in another tile graph.
            coords = doubleArrayOf(37.601, 55.75, 37.602, 55.75),
        )
        val tileA = RoadGraph(
            "x", 4, doubleArrayOf(37.599, 55.749, 37.6015, 55.751), listOf(a),
        )
        val tileB = RoadGraph(
            "x", 4, doubleArrayOf(37.6005, 55.749, 37.603, 55.751), listOf(b),
        )
        assertTrue(
            RoadMapMatcher.isConnectedFromPrevious(
                graphs = listOf(tileA, tileB),
                previousEdgeId = 10L,
                previousRegionId = "x",
                candidate = b,
                candidateRegionId = "x",
            ),
        )
        val ranked = RoadMapMatcher.rankCandidates(
            pose = RoadMatchPose(55.75001, 37.6012, 90f),
            graphs = listOf(tileA, tileB),
            previousEdgeId = 10L,
            previousRegionId = "x",
            previousHighwayClass = "primary",
        )
        val best = ranked.firstOrNull { it.edge.id == 20L }
        assertNotNull(best)
        assertTrue(best!!.connectedFromPrevious)
    }

    @Test
    fun softCorrectPullsGentlyAlongTrackTowardLookAheadTarget() {
        val edge = RoadEdge(
            id = 1L,
            highwayClass = "primary",
            lengthM = 1_000.0,
            fromNode = 0,
            toNode = 1,
            coords = doubleArrayOf(37.60, 55.75, 37.62, 55.75),
        )
        val pose = RoadMatchPose(lat = 55.75002, lon = 37.6100, bearingDeg = 90f)
        val proj = RoadMapMatcher.projectOntoEdge(pose.lat, pose.lon, edge)!!
        val cand = RoadMapMatcher.Candidate(
            edge = edge,
            regionId = "along",
            crossTrackM = proj.crossTrackM,
            alongTrackM = proj.alongTrackM,
            projLat = proj.lat,
            projLon = proj.lon,
            edgeAzimuthDeg = 90f,
            score = proj.crossTrackM,
            connectedFromPrevious = true,
        )
        // ~20 m further east along the same edge.
        val targetLon = pose.lon + 20.0 / (111_320.0 * kotlin.math.cos(Math.toRadians(pose.lat)))
        val corrected = RoadMapMatcher.softCorrect(
            pose = pose,
            cand = cand,
            turnActive = false,
            alongTargetLat = pose.lat,
            alongTargetLon = targetLon,
            maxAlongStepM = RoadMapMatcher.MAX_ALONG_STEP_M,
        )
        val movedEastM = (corrected.lon - pose.lon) *
            111_320.0 * kotlin.math.cos(Math.toRadians(pose.lat))
        assertTrue("expected along catch-up, got $movedEastM m", movedEastM > 1.0)
        assertTrue(movedEastM <= RoadMapMatcher.MAX_ALONG_STEP_M + 0.05)
        // Mid-turn must not advance along-track.
        val duringTurn = RoadMapMatcher.softCorrect(
            pose = pose,
            cand = cand,
            turnActive = true,
            alongTargetLat = pose.lat,
            alongTargetLon = targetLon,
        )
        val turnMove = kotlin.math.abs(duringTurn.lon - pose.lon) *
            111_320.0 * kotlin.math.cos(Math.toRadians(pose.lat))
        assertTrue(turnMove < 0.5)
    }

    @Test
    fun topologyLookAheadAdvancesOntoHeadingAlignedConnectedBranch() {
        val entry = RoadEdge(
            1L, "primary", 20.0, 1, 2,
            doubleArrayOf(37.60000, 55.75000, 37.60032, 55.75000),
        )
        val straight = RoadEdge(
            2L, "primary", 80.0, 2, 3,
            doubleArrayOf(37.60032, 55.75000, 37.60160, 55.75000),
        )
        val north = RoadEdge(
            3L, "primary", 80.0, 2, 4,
            doubleArrayOf(37.60032, 55.75000, 37.60032, 55.75100),
        )
        val graph = RoadGraph(
            "look-ahead", 4, doubleArrayOf(37.599, 55.749, 37.603, 55.753),
            listOf(entry, straight, north),
        )
        val seed = RoadMapMatcher.projectOntoEdge(55.75000, 37.60016, entry)!!
        val predicted = RoadMapMatcher.advanceAlongTopology(
            graphs = listOf(graph),
            start = RoadMapMatcher.TopologyAnchor(
                regionId = graph.regionId,
                edgeId = entry.id,
                alongTrackM = seed.alongTrackM,
                travelAgainstCoords = false,
            ),
            distanceM = 20.0,
            targetBearingDeg = 0f,
        )

        assertNotNull(predicted)
        assertEquals(3L, predicted!!.edge.id)
        assertTrue(predicted.lat > 55.75000)
        assertTrue(RoadMapMatcher.smallestAngleDeg(predicted.azimuthDeg, 0f) < 5f)
    }

    @Test
    fun runtimeUsesConnectedCorridorWhenOrdinaryCandidatesDisappear() {
        val entry = RoadEdge(
            1L, "primary", 20.0, 1, 2,
            doubleArrayOf(37.60000, 55.75000, 37.60032, 55.75000),
        )
        val north = RoadEdge(
            2L, "primary", 120.0, 2, 3,
            doubleArrayOf(37.60032, 55.75000, 37.60032, 55.75110),
        )
        val graph = RoadGraph(
            "corridor", 4, doubleArrayOf(37.599, 55.749, 37.603, 55.753),
            listOf(entry, north),
        )
        RoadGraphStore.clear()
        val dir = createTempDir(prefix = "roads-corridor-")
        installSingleTileBundle(dir, graph)
        val rt = RoadMatchRuntime(
            mapsDir = { dir },
            pathTriggerM = 1.0,
            timeTriggerMs = 1L,
            switchConfirmCount = 1,
        )
        val seed = rt.maybeCorrect(
            true,
            RoadMatchPose(55.75000, 37.60016, 90f),
            speedKmh = 36f,
            nowElapsedMs = 1_000L,
        )
        assertNotNull(seed)
        assertEquals(1L, rt.debug.edgeId)

        // About 50 m of DR travel east: >35 m from both connected roads, so normal
        // spatial candidates are empty. Topology advances the CAN distance through
        // the junction and keeps position on the north branch, within the 60 m guard.
        val recovered = rt.maybeCorrect(
            true,
            RoadMatchPose(55.75000, 37.60096, 0f),
            speedKmh = 36f,
            nowElapsedMs = 3_000L,
        )
        assertNotNull(recovered)
        assertEquals("CONNECTED_CORRIDOR", rt.debug.confidence)
        assertEquals("no_candidate_corridor", rt.debug.rejectReason)
        assertEquals(2L, rt.debug.edgeId)
        assertTrue(rt.debug.connected == true)
        assertTrue(recovered!!.lat > 55.75020)
        assertTrue(kotlin.math.abs(recovered.lon - 37.60032) < 0.00005)
    }

    private fun installSingleTileBundle(mapsDir: File, graph: RoadGraph) {
        val bundle = File(mapsDir, "${graph.regionId}${RoadMapBundle.INSTALL_SUFFIX}")
        File(bundle, "tiles").mkdirs()
        val tileRel = "tiles/0_0.tboxroads"
        File(bundle, tileRel).writeBytes(packBytesFor(graph))
        val bbox = graph.bbox.joinToString(",")
        File(bundle, RoadMapBundle.INDEX_FILE).writeText(
            """
            {"format":1,"regionId":"${graph.regionId}","graphVersion":${graph.graphVersion},
             "bbox":[$bbox],
             "tiles":[{"id":"0_0","file":"$tileRel","bbox":[$bbox],"bytes":1}]}
            """.trimIndent(),
        )
    }

    private fun packBytesFor(graph: RoadGraph): ByteArray {
        val edgesJson = graph.edges.joinToString(",") { e ->
            val coords = (0 until e.pointCount).joinToString(",") { i ->
                "[${e.lonAt(i)},${e.latAt(i)}]"
            }
            """{"id":${e.id},"class":"${e.highwayClass}","lengthM":${e.lengthM},"from":${e.fromNode},"to":${e.toNode}${if (e.oneway != 0) ""","oneway":${e.oneway}""" else ""},"coords":[$coords]}"""
        }
        val json =
            """{"format":1,"regionId":"${graph.regionId}","graphVersion":${graph.graphVersion},"bbox":[${graph.bbox.joinToString(",")}],"edges":[$edgesJson]}"""
        val gz = java.io.ByteArrayOutputStream()
        java.util.zip.GZIPOutputStream(gz).use { it.write(json.toByteArray(Charsets.UTF_8)) }
        return RoadGraph.MAGIC.toByteArray(Charsets.US_ASCII) + gz.toByteArray()
    }
}
