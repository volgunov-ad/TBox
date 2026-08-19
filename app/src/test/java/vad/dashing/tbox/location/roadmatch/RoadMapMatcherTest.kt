package vad.dashing.tbox.location.roadmatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun softCorrectSkipsLateralSnapWhenLeaving() {
        val graph = horizontalEdge()
        val pose = RoadMatchPose(lat = 55.7503, lon = 37.61, bearingDeg = 90f)
        val best = RoadMapMatcher.pickBest(pose, listOf(graph), null, null)
        assertNotNull(best)
        val held = RoadMapMatcher.softCorrect(pose, best!!, lateralSnap = false)
        assertEquals(pose.lat, held.lat, 1e-9)
        assertEquals(pose.lon, held.lon, 1e-9)
    }

    @Test
    fun matchLagMetersIsOneSecondClamped10to30() {
        assertEquals(10.0, RoadMapMatcher.matchLagMeters(20f), 1e-6)
        assertEquals(10.0, RoadMapMatcher.matchLagMeters(36f), 1e-6)
        assertEquals(50.0 / 3.6, RoadMapMatcher.matchLagMeters(50f), 1e-6)
        assertEquals(25.0, RoadMapMatcher.matchLagMeters(90f), 1e-6)
        assertEquals(30.0, RoadMapMatcher.matchLagMeters(108f), 1e-6)
        assertEquals(30.0, RoadMapMatcher.matchLagMeters(130f), 1e-6)
        assertEquals(10.0, RoadMapMatcher.matchLagMeters(0f), 1e-6)
        assertEquals(10.0, RoadMapMatcher.matchLagMeters(Float.NaN), 1e-6)
    }

    @Test
    fun softCorrectDoesNotPullTowardEndpointWhenPastEnd() {
        val graph = horizontalEdge()
        val edge = graph.edges.first()
        val mPerDegLon = 111_320.0 * kotlin.math.cos(Math.toRadians(55.75))
        val pose = RoadMatchPose(
            lat = 55.75,
            lon = 37.62 + 15.0 / mPerDegLon,
            bearingDeg = 90f,
        )
        val proj = RoadMapMatcher.projectOntoEdge(pose.lat, pose.lon, edge)!!
        val cand = RoadMapMatcher.Candidate(
            edge = edge,
            regionId = "test",
            crossTrackM = proj.crossTrackM,
            alongTrackM = proj.alongTrackM,
            projLat = proj.lat,
            projLon = proj.lon,
            edgeAzimuthDeg = 90f,
            score = proj.crossTrackM,
            connectedFromPrevious = true,
            travelAgainstCoords = false,
        )
        assertTrue(RoadMapMatcher.isOvershootBeyondEnd(pose.lat, pose.lon, cand))
        assertTrue(proj.crossTrackM >= RoadMapMatcher.PAST_END_XT_RELEASE_M)
        val corrected = RoadMapMatcher.softCorrect(pose, cand)
        val movedWestM = (pose.lon - corrected.lon) * mPerDegLon
        assertTrue("must not snap back toward endpoint, movedWest=$movedWestM", movedWestM < 0.5)
    }

    @Test
    fun runtimeReleasesPastEndOntoConnectedNextEdge() {
        val a = RoadEdge(
            id = 1L,
            highwayClass = "primary",
            lengthM = 200.0,
            fromNode = 0,
            toNode = 1,
            coords = doubleArrayOf(37.60, 55.75, 37.62, 55.75),
        )
        val b = RoadEdge(
            id = 2L,
            highwayClass = "primary",
            lengthM = 200.0,
            fromNode = 1,
            toNode = 2,
            coords = doubleArrayOf(37.62, 55.75, 37.64, 55.75),
        )
        val graph = RoadGraph(
            "past-end",
            4,
            doubleArrayOf(37.59, 55.74, 37.65, 55.76),
            listOf(a, b),
        )
        RoadGraphStore.clear()
        val dir = createTempDir(prefix = "roads-past-end-")
        installSingleTileBundle(dir, graph)
        val rt = RoadMatchRuntime(
            mapsDir = { dir },
            pathTriggerM = 1.0,
            timeTriggerMs = 1L,
            switchConfirmCount = 3,
        )
        val seed = rt.maybeCorrect(
            true,
            RoadMatchPose(55.75002, 37.61, 90f),
            speedKmh = 40f,
            nowElapsedMs = 1_000L,
        )
        assertNotNull(seed)
        assertEquals(1L, rt.debug.edgeId)

        val mPerDegLon = 111_320.0 * kotlin.math.cos(Math.toRadians(55.75))
        val pastLon = 37.62 + 12.0 / mPerDegLon
        val next = rt.maybeCorrect(
            true,
            RoadMatchPose(55.75, pastLon, 90f),
            speedKmh = 40f,
            nowElapsedMs = 3_000L,
        )
        assertNotNull(next)
        assertEquals(2L, rt.debug.edgeId)
        val movedWestM = (pastLon - next!!.lon) * mPerDegLon
        assertTrue("must not rewind toward old endpoint, movedWest=$movedWestM", movedWestM < 1.0)
    }

    @Test
    fun runtimeDoesNotSnapBackwardWhenPastEndHasNoSuccessor() {
        val graph = horizontalEdge()
        RoadGraphStore.clear()
        val dir = createTempDir(prefix = "roads-past-end-none-")
        installSingleTileBundle(dir, graph)
        val rt = RoadMatchRuntime(
            mapsDir = { dir },
            pathTriggerM = 1.0,
            timeTriggerMs = 1L,
            switchConfirmCount = 3,
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

        val mPerDegLon = 111_320.0 * kotlin.math.cos(Math.toRadians(55.75))
        val pastLon = 37.62 + 15.0 / mPerDegLon
        val corrected = rt.maybeCorrect(
            true,
            RoadMatchPose(55.75, pastLon, 90f),
            speedKmh = 40f,
            nowElapsedMs = 3_000L,
        )
        assertNull(corrected)
        assertEquals("past_end", rt.debug.skippedReason)
        assertEquals(1L, rt.debug.edgeId)
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

        // With steady 2 s throttle this would still be blocked; recover mode allows 0.5 s.
        val early = rt.maybeCorrect(
            true,
            RoadMatchPose(55.7520, 37.6102, 0f),
            speedKmh = 40f,
            nowElapsedMs = 3_650L,
        )
        assertTrue(
            "expected recover retry within 0.55 s, got skipped=${rt.debug.skippedReason}",
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
        assertTrue(rt.debug.rankedCandidates.size >= 2)
        assertEquals(1, rt.debug.rankedCandidates.first().rank)
        assertTrue(
            rt.debug.rankedCandidates.map { it.edgeId }.toSet().containsAll(setOf(1L, 2L)),
        )
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
    fun firstLockRejectsAheadOnewayAndCourtyardSide() {
        val aheadRamp = RoadMapMatcher.Candidate(
            edge = RoadEdge(
                48261, "secondary", 120.0, 0, 1,
                doubleArrayOf(0.0, 0.0, 1.0, 0.0), oneway = 1,
            ),
            regionId = "r",
            crossTrackM = 26.0,
            alongTrackM = 75.0,
            projLat = 0.0,
            projLon = 0.0,
            edgeAzimuthDeg = 90f,
            score = 31.0,
            connectedFromPrevious = true,
        )
        assertTrue(RoadMapMatcher.isAheadOnOnewayFirstLock(aheadRamp))
        assertEquals(
            RoadMatchConfidence.LOW,
            RoadMapMatcher.confidenceOf(listOf(aheadRamp), firstLock = true),
        )
        assertEquals(
            RoadMatchConfidence.MEDIUM,
            RoadMapMatcher.confidenceOf(listOf(aheadRamp), firstLock = false),
        )

        val courtyard = RoadMapMatcher.Candidate(
            edge = RoadEdge(11610, "unclassified", 200.0, 0, 1, doubleArrayOf(0.0, 0.0, 1.0, 0.0)),
            regionId = "r",
            crossTrackM = 22.0,
            alongTrackM = 10.0,
            projLat = 0.0,
            projLon = 0.0,
            edgeAzimuthDeg = 180f,
            score = 22.0,
            connectedFromPrevious = true,
        )
        assertTrue(RoadMapMatcher.isCourtyardSideFirstLock(courtyard))
        assertEquals(
            RoadMatchConfidence.LOW,
            RoadMapMatcher.confidenceOf(listOf(courtyard), firstLock = true),
        )
        assertEquals(
            RoadMatchConfidence.MEDIUM,
            RoadMapMatcher.confidenceOf(listOf(courtyard), firstLock = false),
        )
        assertTrue(RoadHighwayClass.isCourtyardLike("unclassified"))
        assertFalse(RoadHighwayClass.isCourtyardLike("secondary"))
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
    fun stickyAgainstOnewayLosesToParallelWithFlow() {
        // Field 10:17–10:19: already on westbound oneway while heading east;
        // correct eastbound carriageway ~25 m north must win despite same-edge bonus.
        val westOnly = RoadEdge(
            id = 10L,
            highwayClass = "primary",
            lengthM = 800.0,
            fromNode = 0,
            toNode = 1,
            coords = doubleArrayOf(37.62, 55.75, 37.60, 55.75),
            oneway = 1,
        )
        val eastOnly = RoadEdge(
            id = 20L,
            highwayClass = "primary",
            lengthM = 800.0,
            fromNode = 2,
            toNode = 3,
            // ~25 m north (≈0.000225° lat).
            coords = doubleArrayOf(37.60, 55.750225, 37.62, 55.750225),
            oneway = 1,
        )
        val graph = RoadGraph(
            regionId = "ow-par",
            graphVersion = 4,
            bbox = doubleArrayOf(37.59, 55.74, 37.63, 55.76),
            edges = listOf(westOnly, eastOnly),
        )
        val eastPose = RoadMatchPose(lat = 55.75002, lon = 37.61, bearingDeg = 90f)
        val sticky = RoadMapMatcher.pickBest(
            eastPose, listOf(graph), previousEdgeId = 10L, previousRegionId = "ow-par",
            previousHighwayClass = "primary",
        )
        assertNotNull(sticky)
        assertEquals(20L, sticky!!.edge.id)
        assertTrue(!sticky.againstOneway)
    }

    @Test
    fun soleAgainstOnewayStillAllowedWhenNoParallel() {
        val westOnly = RoadEdge(
            id = 10L,
            highwayClass = "primary",
            lengthM = 800.0,
            fromNode = 0,
            toNode = 1,
            coords = doubleArrayOf(37.62, 55.75, 37.60, 55.75),
            oneway = 1,
        )
        val graph = RoadGraph(
            regionId = "ow-sole",
            graphVersion = 4,
            bbox = doubleArrayOf(37.59, 55.74, 37.63, 55.76),
            edges = listOf(westOnly),
        )
        val eastPose = RoadMatchPose(lat = 55.75002, lon = 37.61, bearingDeg = 90f)
        val best = RoadMapMatcher.pickBest(
            eastPose, listOf(graph), previousEdgeId = 10L, previousRegionId = "ow-sole",
            previousHighwayClass = "primary",
        )
        assertNotNull(best)
        assertEquals(10L, best!!.edge.id)
        assertTrue(best.againstOneway)
        // Soft penalty only (same-edge bonus may pull score below raw 18);
        // parallel-correct extra must not apply when no with-flow major exists.
        assertTrue(
            best.score < RoadMapMatcher.ONEWAY_AGAINST_PENALTY +
                RoadMapMatcher.PARALLEL_CORRECT_AGAINST_EXTRA * 0.5,
        )
    }

    @Test
    fun againstPrimaryDoesNotPreferCourtyardParallel() {
        val westOnly = RoadEdge(
            id = 10L,
            highwayClass = "primary",
            lengthM = 800.0,
            fromNode = 0,
            toNode = 1,
            coords = doubleArrayOf(37.62, 55.75, 37.60, 55.75),
            oneway = 1,
        )
        val yard = RoadEdge(
            id = 30L,
            highwayClass = "residential",
            lengthM = 800.0,
            fromNode = 4,
            toNode = 5,
            coords = doubleArrayOf(37.60, 55.7502, 37.62, 55.7502),
        )
        val graph = RoadGraph(
            regionId = "ow-yard",
            graphVersion = 4,
            bbox = doubleArrayOf(37.59, 55.74, 37.63, 55.76),
            edges = listOf(westOnly, yard),
        )
        val eastPose = RoadMatchPose(lat = 55.75002, lon = 37.61, bearingDeg = 90f)
        val best = RoadMapMatcher.pickBest(
            eastPose, listOf(graph), previousEdgeId = 10L, previousRegionId = "ow-yard",
            previousHighwayClass = "primary",
        )
        assertNotNull(best)
        // No major with-flow parallel → soft against still ranks first (LOW later).
        assertEquals(10L, best!!.edge.id)
        assertTrue(best.againstOneway)
    }

    @Test
    fun runtimeRegrabsWithFlowNotAgainstParallel() {
        val westOnly = RoadEdge(
            id = 10L,
            highwayClass = "primary",
            lengthM = 800.0,
            fromNode = 0,
            toNode = 1,
            coords = doubleArrayOf(37.62, 55.75, 37.60, 55.75),
            oneway = 1,
        )
        val eastOnly = RoadEdge(
            id = 20L,
            highwayClass = "primary",
            lengthM = 800.0,
            fromNode = 2,
            toNode = 3,
            coords = doubleArrayOf(37.60, 55.750225, 37.62, 55.750225),
            oneway = 1,
        )
        val graph = RoadGraph(
            "ow-rt",
            4,
            doubleArrayOf(37.59, 55.74, 37.63, 55.76),
            listOf(westOnly, eastOnly),
        )
        RoadGraphStore.clear()
        val dir = createTempDir(prefix = "roads-ow-par-")
        installSingleTileBundle(dir, graph)
        val rt = RoadMatchRuntime(
            mapsDir = { dir },
            pathTriggerM = 1.0,
            timeTriggerMs = 1L,
            switchConfirmCount = 1,
        )
        // Seed briefly on westbound while traveling west (legal).
        assertNotNull(
            rt.maybeCorrect(
                true,
                RoadMatchPose(55.75002, 37.61, 270f),
                speedKmh = 50f,
                nowElapsedMs = 1_000L,
            ),
        )
        assertEquals(10L, rt.debug.edgeId)

        // Turn around east: must leave against westbound and take eastbound parallel.
        val east = rt.maybeCorrect(
            true,
            RoadMatchPose(55.75002, 37.61, 90f),
            speedKmh = 50f,
            nowElapsedMs = 3_000L,
            allowAgainstOneway = false,
        )
        assertNotNull(east)
        assertEquals(20L, rt.debug.edgeId)
        assertTrue(rt.debug.againstOneway != true)
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
        assertEquals(55.75, rt.debug.preMatchLat!!, 1e-9)
        assertEquals(37.61, rt.debug.preMatchLon!!, 1e-9)
        assertEquals(90f, rt.debug.preMatchBearingDeg!!, 0f)
        assertEquals(false, rt.debug.matchApplied)
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
        assertTrue(corrected.lat < pose.lat)
    }

    @Test
    fun softCorrectCatchUpHeadingPullsDespiteLargeResidual() {
        val graph = horizontalEdge()
        val edge = graph.edges.first()
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
        val residual = RoadMapMatcher.smallestAngleDeg(pose.bearingDeg, cand.edgeAzimuthDeg)
        assertTrue(residual >= RoadMapMatcher.BEARING_INHIBIT_RESIDUAL_DEG)
        val corrected = RoadMapMatcher.softCorrect(
            pose,
            cand,
            turnActive = true,
            catchUpHeading = true,
        )
        val pulled = RoadMapMatcher.smallestAngleDeg(pose.bearingDeg, corrected.bearingDeg)
        assertEquals(RoadMapMatcher.MAX_BEARING_STEP_EDGE_CATCHUP_DEG, pulled, 0.05f)
        assertTrue(
            RoadMapMatcher.smallestAngleDeg(corrected.bearingDeg, cand.edgeAzimuthDeg) < residual,
        )
    }

    @Test
    fun softCorrectFreeTurnsCatchUpUsesLargerBearingStep() {
        val graph = horizontalEdge()
        val pose = RoadMatchPose(lat = 55.75, lon = 37.61, bearingDeg = 50f)
        val cand = RoadMapMatcher.pickBest(pose, listOf(graph), null, null)!!
        val ordinary = RoadMapMatcher.softCorrect(
            pose, cand, catchUpHeading = true, lateralSnap = false,
        )
        val free = RoadMapMatcher.softCorrect(
            pose,
            cand,
            catchUpHeading = true,
            lateralSnap = false,
            maxBearingStepCatchupDeg = RoadMatchFreeTurnsMath.MAX_BEARING_STEP_CATCHUP_DEG,
        )
        val ordinaryPull = RoadMapMatcher.smallestAngleDeg(pose.bearingDeg, ordinary.bearingDeg)
        val freePull = RoadMapMatcher.smallestAngleDeg(pose.bearingDeg, free.bearingDeg)
        assertEquals(RoadMapMatcher.MAX_BEARING_STEP_EDGE_CATCHUP_DEG, ordinaryPull, 0.05f)
        assertEquals(RoadMatchFreeTurnsMath.MAX_BEARING_STEP_CATCHUP_DEG, freePull, 0.05f)
        assertTrue(freePull > ordinaryPull)
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
    fun canCommitLinkNeedsTurnEvidence() {
        val link = RoadMapMatcher.Candidate(
            edge = RoadEdge(2, "primary_link", 100.0, 1, 2, doubleArrayOf(0.0, 0.0, 0.0, 1.0)),
            regionId = "r",
            crossTrackM = 2.0,
            alongTrackM = 10.0,
            projLat = 0.0,
            projLon = 0.0,
            edgeAzimuthDeg = 95f,
            score = 2.0,
            connectedFromPrevious = true,
        )
        assertTrue(
            RoadMapMatcher.canCommitLink(
                cand = link,
                previousHighwayClass = null,
                travelBearingDeg = 90f,
                turnHint = null,
                topologyLookAheadEdgeIds = emptySet(),
            ),
        )
        assertFalse(
            RoadMapMatcher.canCommitLink(
                cand = link,
                previousHighwayClass = "primary",
                travelBearingDeg = 90f,
                turnHint = null,
                topologyLookAheadEdgeIds = emptySet(),
                speedKmh = 50f,
            ),
        )
        assertTrue(
            RoadMapMatcher.canCommitLink(
                cand = link,
                previousHighwayClass = "primary",
                travelBearingDeg = 90f,
                turnHint = null,
                topologyLookAheadEdgeIds = emptySet(),
                speedKmh = 20f,
            ),
        )
        assertTrue(
            RoadMapMatcher.canCommitLink(
                cand = link.copy(edgeAzimuthDeg = 130f),
                previousHighwayClass = "primary",
                travelBearingDeg = 90f,
                turnHint = null,
                topologyLookAheadEdgeIds = emptySet(),
            ),
        )
        assertTrue(
            RoadMapMatcher.canCommitLink(
                cand = link,
                previousHighwayClass = "primary",
                travelBearingDeg = 90f,
                turnHint = null,
                topologyLookAheadEdgeIds = setOf("r" to 2L),
            ),
        )
        // Comfort latched hint without intent must not unlock an early shallow link.
        assertFalse(
            RoadMapMatcher.canCommitLink(
                cand = link,
                previousHighwayClass = "primary",
                travelBearingDeg = 90f,
                turnHint = RoadMapMatcher.TurnHint.Right,
                topologyLookAheadEdgeIds = emptySet(),
                speedKmh = 50f,
                turnIntent = false,
            ),
        )
        assertTrue(
            RoadMapMatcher.canCommitLink(
                cand = link.copy(edgeAzimuthDeg = 105f),
                previousHighwayClass = "motorway",
                travelBearingDeg = 90f,
                turnHint = RoadMapMatcher.TurnHint.Right,
                topologyLookAheadEdgeIds = emptySet(),
                speedKmh = 90f,
                turnIntent = true,
                roadProfile = RoadMatchRoadProfile.HIGHWAY,
            ),
        )
    }

    @Test
    fun parallelYardSwitchDetectsHighXtNeighbour() {
        val yard = RoadMapMatcher.Candidate(
            edge = RoadEdge(9, "residential", 200.0, 10, 11, doubleArrayOf(0.0, 0.0, 1.0, 0.0)),
            regionId = "r",
            crossTrackM = 16.0,
            alongTrackM = 20.0,
            projLat = 0.0,
            projLon = 0.0,
            edgeAzimuthDeg = 90f,
            score = 16.0,
            connectedFromPrevious = false,
        )
        assertTrue(
            RoadMapMatcher.isParallelYardSwitch(yard, "residential", 90f),
        )
        assertFalse(
            RoadMapMatcher.isParallelYardSwitch(yard.copy(crossTrackM = 6.0), "residential", 90f),
        )
        assertFalse(
            RoadMapMatcher.isParallelYardSwitch(yard.copy(edgeAzimuthDeg = 20f), "residential", 90f),
        )
        assertFalse(
            RoadMapMatcher.isParallelYardSwitch(yard, null, 90f),
        )
    }

    @Test
    fun rankPenalizesUnhintedConnectedLink() {
        val primary = RoadEdge(
            id = 1L,
            highwayClass = "primary",
            lengthM = 800.0,
            fromNode = 0,
            toNode = 1,
            coords = doubleArrayOf(37.60, 55.75, 37.62, 55.75),
        )
        val link = RoadEdge(
            id = 2L,
            highwayClass = "primary_link",
            lengthM = 250.0,
            fromNode = 1,
            toNode = 2,
            coords = doubleArrayOf(37.62, 55.75, 37.621, 55.7502),
        )
        val graph = RoadGraph(
            "rank-link",
            4,
            doubleArrayOf(37.59, 55.74, 37.63, 55.76),
            listOf(primary, link),
        )
        val pose = RoadMatchPose(55.75003, 37.618, 90f)
        val ranked = RoadMapMatcher.rankCandidates(
            pose = pose,
            graphs = listOf(graph),
            previousEdgeId = 1L,
            previousRegionId = "rank-link",
            previousHighwayClass = "primary",
        )
        val linkCand = ranked.firstOrNull { it.edge.id == 2L }
        val primaryCand = ranked.firstOrNull { it.edge.id == 1L }
        assertNotNull(primaryCand)
        if (linkCand != null) {
            assertTrue(primaryCand!!.score < linkCand.score)
        }
    }

    @Test
    fun runtimeKeepsThroughRoadInsteadOfEarlyLink() {
        val primary = RoadEdge(
            id = 1L,
            highwayClass = "primary",
            lengthM = 800.0,
            fromNode = 0,
            toNode = 1,
            coords = doubleArrayOf(37.60, 55.75, 37.62, 55.75),
        )
        val link = RoadEdge(
            id = 2L,
            highwayClass = "primary_link",
            lengthM = 250.0,
            fromNode = 1,
            toNode = 2,
            coords = doubleArrayOf(37.62, 55.75, 37.622, 55.752),
        )
        val graph = RoadGraph(
            "early-link",
            4,
            doubleArrayOf(37.59, 55.74, 37.63, 55.76),
            listOf(primary, link),
        )
        RoadGraphStore.clear()
        val dir = createTempDir(prefix = "roads-early-link-")
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
            speedKmh = 50f,
            nowElapsedMs = 1_000L,
        )
        assertNotNull(first)
        assertEquals(1L, rt.debug.edgeId)

        val next = rt.maybeCorrect(
            true,
            RoadMatchPose(55.75004, 37.618, 90f),
            speedKmh = 50f,
            nowElapsedMs = 3_000L,
        )
        assertNotNull(next)
        assertEquals(1L, rt.debug.edgeId)
        assertTrue(rt.debug.edgeId != 2L)
    }

    @Test
    fun runtimeCommitsLinkWhenHeadingTurns() {
        val primary = RoadEdge(
            id = 1L,
            highwayClass = "primary",
            lengthM = 800.0,
            fromNode = 0,
            toNode = 1,
            coords = doubleArrayOf(37.60, 55.75, 37.62, 55.75),
        )
        val link = RoadEdge(
            id = 2L,
            highwayClass = "primary_link",
            lengthM = 250.0,
            fromNode = 1,
            toNode = 2,
            coords = doubleArrayOf(37.62, 55.75, 37.622, 55.752),
        )
        val graph = RoadGraph(
            "hint-link",
            4,
            doubleArrayOf(37.59, 55.74, 37.63, 55.76),
            listOf(primary, link),
        )
        RoadGraphStore.clear()
        val dir = createTempDir(prefix = "roads-hint-link-")
        installSingleTileBundle(dir, graph)
        val rt = RoadMatchRuntime(
            mapsDir = { dir },
            pathTriggerM = 1.0,
            timeTriggerMs = 1L,
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
        rt.maybeCorrect(
            true,
            RoadMatchPose(55.7508, 37.6205, 20f),
            speedKmh = 35f,
            nowElapsedMs = 3_000L,
            turnHint = RoadMapMatcher.TurnHint.Left,
            turnIntent = true,
        )
        assertEquals(2L, rt.debug.edgeId)
    }

    @Test
    fun runtimeRestoresParentAfterLinkLeashBreak() {
        val primary = RoadEdge(
            id = 1L,
            highwayClass = "primary",
            lengthM = 800.0,
            fromNode = 0,
            toNode = 1,
            coords = doubleArrayOf(37.60, 55.75, 37.62, 55.75),
        )
        val link = RoadEdge(
            id = 2L,
            highwayClass = "primary_link",
            lengthM = 80.0,
            fromNode = 1,
            toNode = 2,
            coords = doubleArrayOf(37.62, 55.75, 37.6204, 55.7506),
        )
        val graph = RoadGraph(
            "parent-link",
            4,
            doubleArrayOf(37.59, 55.74, 37.63, 55.76),
            listOf(primary, link),
        )
        RoadGraphStore.clear()
        val dir = createTempDir(prefix = "roads-parent-link-")
        installSingleTileBundle(dir, graph)
        val rt = RoadMatchRuntime(
            mapsDir = { dir },
            pathTriggerM = 1.0,
            timeTriggerMs = 1L,
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
        rt.maybeCorrect(
            true,
            RoadMatchPose(55.75035, 37.6202, 15f),
            speedKmh = 30f,
            nowElapsedMs = 3_000L,
            turnHint = RoadMapMatcher.TurnHint.Left,
            turnIntent = true,
        )
        assertEquals(2L, rt.debug.edgeId)

        // Drive back along the parent corridor; xt to the short ramp grows.
        var restored = false
        for (i in 1..8) {
            rt.maybeCorrect(
                true,
                RoadMatchPose(55.75003, 37.616 - i * 0.0004, 90f),
                speedKmh = 40f,
                nowElapsedMs = 3_000L + i * 600L,
            )
            if (rt.debug.edgeId == 1L) {
                restored = true
                break
            }
        }
        assertTrue("expected parent primary after leaving the ramp", restored)
        assertEquals(1L, rt.debug.edgeId)
    }

    @Test
    fun runtimeRejectsParallelYardSwitch() {
        val a = RoadEdge(
            id = 10L,
            highwayClass = "residential",
            lengthM = 500.0,
            fromNode = 0,
            toNode = 1,
            coords = doubleArrayOf(37.60, 55.75, 37.62, 55.75),
        )
        val b = RoadEdge(
            id = 11L,
            highwayClass = "residential",
            lengthM = 500.0,
            fromNode = 20,
            toNode = 21,
            // ~18 m north — parallel neighbour.
            coords = doubleArrayOf(37.60, 55.75016, 37.62, 55.75016),
        )
        val graph = RoadGraph(
            "yard-par",
            4,
            doubleArrayOf(37.59, 55.74, 37.63, 55.76),
            listOf(a, b),
        )
        RoadGraphStore.clear()
        val dir = createTempDir(prefix = "roads-yard-par-")
        installSingleTileBundle(dir, graph)
        val rt = RoadMatchRuntime(
            mapsDir = { dir },
            pathTriggerM = 1.0,
            timeTriggerMs = 1L,
            switchConfirmCount = 1,
        )
        assertNotNull(
            rt.maybeCorrect(
                true,
                RoadMatchPose(55.75002, 37.61, 90f),
                speedKmh = 20f,
                nowElapsedMs = 1_000L,
            ),
        )
        assertEquals(10L, rt.debug.edgeId)
        rt.maybeCorrect(
            true,
            RoadMatchPose(55.75014, 37.61, 90f),
            speedKmh = 18f,
            nowElapsedMs = 3_000L,
        )
        assertTrue(rt.debug.edgeId != 11L)
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

    @Test
    fun runtimeCatchesUpHeadingAfterSwitchEvenDuringTurn() {
        val east = RoadEdge(
            1L, "primary", 80.0, 1, 2,
            doubleArrayOf(37.60000, 55.75000, 37.60120, 55.75000),
        )
        val north = RoadEdge(
            2L, "primary", 80.0, 2, 3,
            doubleArrayOf(37.60120, 55.75000, 37.60120, 55.75080),
        )
        val graph = RoadGraph(
            "hdg-sw", 4, doubleArrayOf(37.599, 55.749, 37.603, 55.752),
            listOf(east, north),
        )
        RoadGraphStore.clear()
        val dir = createTempDir(prefix = "roads-hdg-sw-")
        installSingleTileBundle(dir, graph)
        val rt = RoadMatchRuntime(
            mapsDir = { dir },
            pathTriggerM = 1.0,
            timeTriggerMs = 1L,
            switchConfirmCount = 1,
        )
        val seed = rt.maybeCorrect(
            true,
            RoadMatchPose(55.75000, 37.60040, 90f),
            speedKmh = 36f,
            nowElapsedMs = 1_000L,
        )
        assertNotNull(seed)
        assertEquals(1L, rt.debug.edgeId)

        // Gyro undershot a left onto the north road: pose is on the new edge,
        // heading still ~50° (residual ~50° to north). dueTurn is true.
        val after = rt.maybeCorrect(
            true,
            RoadMatchPose(55.75008, 37.60120, 50f),
            speedKmh = 36f,
            nowElapsedMs = 3_000L,
        )
        assertNotNull(after)
        assertEquals(2L, rt.debug.edgeId)
        assertTrue(rt.debug.switchedEdge)
        val pulled = RoadMapMatcher.smallestAngleDeg(50f, after!!.bearingDeg)
        assertTrue("expected heading catch-up toward north, pulled=$pulled", pulled >= 10f)
        assertTrue(
            RoadMapMatcher.smallestAngleDeg(after.bearingDeg, 0f) <
                RoadMapMatcher.smallestAngleDeg(50f, 0f),
        )
        assertTrue(rt.debug.turnActive != true)
    }

    @Test
    fun runtimeDoesNotPullHeadingBackToOldEdgeWhileTurningAway() {
        val east = RoadEdge(
            1L, "primary", 200.0, 1, 2,
            doubleArrayOf(37.60000, 55.75000, 37.60300, 55.75000),
        )
        val graph = RoadGraph(
            "hdg-away", 4, doubleArrayOf(37.599, 55.749, 37.604, 55.751),
            listOf(east),
        )
        RoadGraphStore.clear()
        val dir = createTempDir(prefix = "roads-hdg-away-")
        installSingleTileBundle(dir, graph)
        val rt = RoadMatchRuntime(
            mapsDir = { dir },
            pathTriggerM = 1.0,
            timeTriggerMs = 1L,
        )
        val seed = rt.maybeCorrect(
            true,
            RoadMatchPose(55.75000, 37.60100, 90f),
            speedKmh = 36f,
            nowElapsedMs = 1_000L,
        )
        assertNotNull(seed)
        assertEquals(1L, rt.debug.edgeId)

        // Early left turn: heading 74° (16° off the east edge). dueTurn stays false
        // (trigger 18°); residual growth must still inhibit a pull back to 90°.
        val during = rt.maybeCorrect(
            true,
            RoadMatchPose(55.75002, 37.60120, 74f),
            speedKmh = 36f,
            nowElapsedMs = 3_000L,
        )
        assertNotNull(during)
        assertEquals(1L, rt.debug.edgeId)
        assertEquals(74f, during!!.bearingDeg, 0.05f)
        assertTrue(rt.debug.turnActive == true)
        assertEquals(0f, rt.debug.bearingDeltaDeg ?: 0f, 0.05f)
    }

    @Test
    fun runtimeCatchesUpStableGyroUndershootOnMatchedEdge() {
        // Field 124442 @ 12:46:44: HIGH on tertiary, residual ~36°, heading stuck,
        // dueTurn false (course barely moved). Old residual>=28 inhibit left the
        // shadow walking sideways; catch-up must still pull toward the edge.
        val edge = RoadEdge(
            1L, "tertiary", 200.0, 1, 2,
            doubleArrayOf(37.60100, 55.75000, 37.60020, 55.75072),
        )
        val graph = RoadGraph(
            "hdg-under", 4, doubleArrayOf(37.599, 55.749, 37.602, 55.752),
            listOf(edge),
        )
        RoadGraphStore.clear()
        val dir = createTempDir(prefix = "roads-hdg-under-")
        installSingleTileBundle(dir, graph)
        val rt = RoadMatchRuntime(
            mapsDir = { dir },
            pathTriggerM = 1.0,
            timeTriggerMs = 1L,
        )
        val seed = rt.maybeCorrect(
            true,
            RoadMatchPose(55.75036, 37.60060, 290f),
            speedKmh = 24f,
            nowElapsedMs = 1_000L,
        )
        assertNotNull(seed)
        assertEquals(1L, rt.debug.edgeId)
        val pulled = RoadMapMatcher.smallestAngleDeg(290f, seed!!.bearingDeg)
        assertTrue("expected undershoot catch-up, pulled=$pulled", pulled >= 10f)
        val edgeAz = rt.debug.edgeBearingDeg
        assertNotNull(edgeAz)
        assertTrue(
            RoadMapMatcher.smallestAngleDeg(seed.bearingDeg, edgeAz!!) <
                RoadMapMatcher.smallestAngleDeg(290f, edgeAz),
        )
    }

    @Test
    fun runtimeDoesNotPullHeadingBackWhenSensorsTurnAway() {
        // Field 143430 @ 14:53: gyro/steer leave the old motorway; catch-up kept
        // mock heading on that edge so dueTurn never fired. DR yaw opposite the
        // pull-toward-edge must inhibit.
        val east = RoadEdge(
            1L, "motorway", 400.0, 1, 2,
            doubleArrayOf(37.60000, 55.75000, 37.60500, 55.75000),
        )
        val graph = RoadGraph(
            "hdg-oppose", 4, doubleArrayOf(37.599, 55.749, 37.606, 55.751),
            listOf(east),
        )
        RoadGraphStore.clear()
        val dir = createTempDir(prefix = "roads-hdg-oppose-")
        installSingleTileBundle(dir, graph)
        val rt = RoadMatchRuntime(
            mapsDir = { dir },
            pathTriggerM = 1.0,
            timeTriggerMs = 1L,
        )
        val seed = rt.maybeCorrect(
            true,
            RoadMatchPose(55.75000, 37.60100, 90f),
            speedKmh = 54f,
            nowElapsedMs = 1_000L,
        )
        assertNotNull(seed)
        assertEquals(1L, rt.debug.edgeId)

        val away = rt.maybeCorrect(
            true,
            RoadMatchPose(55.75002, 37.60140, 84f),
            speedKmh = 54f,
            nowElapsedMs = 2_000L,
        )
        assertNotNull(away)
        assertEquals(1L, rt.debug.edgeId)
        assertEquals(84f, away!!.bearingDeg, 0.05f)
        assertTrue(rt.debug.turnActive == true)
        assertEquals(0f, rt.debug.bearingDeltaDeg ?: 0f, 0.05f)
    }

    @Test
    fun runtimeStillCatchesUpWhenSensorsTurnTowardEdge() {
        val edge = RoadEdge(
            1L, "tertiary", 200.0, 1, 2,
            doubleArrayOf(37.60100, 55.75000, 37.60020, 55.75072),
        )
        val graph = RoadGraph(
            "hdg-toward", 4, doubleArrayOf(37.599, 55.749, 37.602, 55.752),
            listOf(edge),
        )
        RoadGraphStore.clear()
        val dir = createTempDir(prefix = "roads-hdg-toward-")
        installSingleTileBundle(dir, graph)
        val rt = RoadMatchRuntime(
            mapsDir = { dir },
            pathTriggerM = 1.0,
            timeTriggerMs = 1L,
        )
        val seed = rt.maybeCorrect(
            true,
            RoadMatchPose(55.75036, 37.60060, 290f),
            speedKmh = 24f,
            nowElapsedMs = 1_000L,
        )
        assertNotNull(seed)
        val edgeAz = rt.debug.edgeBearingDeg
        assertNotNull(edgeAz)
        val afterFirst = seed!!.bearingDeg
        val toward = RoadMapMatcher.signedAngleDeg(afterFirst, edgeAz!!)
        val stepped = RoadMapMatcher.normalizeDeg(afterFirst + 0.4f * toward)
        val second = rt.maybeCorrect(
            true,
            RoadMatchPose(55.75040, 37.60055, stepped),
            speedKmh = 24f,
            nowElapsedMs = 2_000L,
        )
        assertNotNull(second)
        assertTrue(
            RoadMapMatcher.smallestAngleDeg(second!!.bearingDeg, edgeAz) <
                RoadMapMatcher.smallestAngleDeg(stepped, edgeAz),
        )
    }

    @Test
    fun runtimeDoesNotChaseCurvingLinkWhileGoingStraight() {
        // Field 142148: HIGH on trunk_link while still going straight; same-edge
        // catch-up chased the ramp azimuth 14°/tick. Heading must stay put.
        val motorway = RoadEdge(
            1778L, "motorway", 400.0, 1, 2,
            doubleArrayOf(37.60000, 55.75000, 37.60500, 55.75000),
        )
        val ramp = RoadEdge(
            1783L, "trunk_link", 350.0, 2, 3,
            doubleArrayOf(
                37.60500, 55.75000,
                37.60620, 55.75000,
                37.60720, 55.74970,
                37.60780, 55.74910,
                37.60790, 55.74830,
            ),
        )
        val graph = RoadGraph(
            "link-chase", 4, doubleArrayOf(37.599, 55.747, 37.609, 55.751),
            listOf(motorway, ramp),
        )
        RoadGraphStore.clear()
        val dir = createTempDir(prefix = "roads-link-chase-")
        installSingleTileBundle(dir, graph)
        val rt = RoadMatchRuntime(
            mapsDir = { dir },
            pathTriggerM = 1.0,
            timeTriggerMs = 1L,
            switchConfirmCount = 1,
        )
        val seed = rt.maybeCorrect(
            true,
            RoadMatchPose(55.75000, 37.60200, 90f),
            speedKmh = 72f,
            nowElapsedMs = 1_000L,
        )
        assertNotNull(seed)
        assertEquals(1778L, rt.debug.edgeId)

        val onRamp = rt.maybeCorrect(
            true,
            RoadMatchPose(55.75000, 37.60540, 90f),
            speedKmh = 72f,
            nowElapsedMs = 2_000L,
        )
        assertNotNull(onRamp)
        assertEquals(1783L, rt.debug.edgeId)

        var pose = onRamp!!
        for (i in 1..4) {
            val lon = 37.60540 + 0.00045 * i
            val lat = 55.75000 - 0.00012 * i
            pose = rt.maybeCorrect(
                true,
                RoadMatchPose(lat, lon, pose.bearingDeg),
                speedKmh = 72f,
                nowElapsedMs = 2_000L + i * 1_000L,
            ) ?: pose
            assertEquals(1783L, rt.debug.edgeId)
        }
        val spun = RoadMapMatcher.smallestAngleDeg(90f, pose.bearingDeg)
        assertTrue(
            "straight travel must not chase ramp azimuth, spun=$spun bearing=${pose.bearingDeg}",
            spun < 8f,
        )
        assertTrue(rt.debug.turnActive == true)
        assertEquals(0f, rt.debug.bearingDeltaDeg ?: 0f, 0.05f)
    }

    @Test
    fun runtimeStillCatchesUpHeadingOnLinkAtSwitch() {
        val east = RoadEdge(
            1L, "motorway", 80.0, 1, 2,
            doubleArrayOf(37.60000, 55.75000, 37.60120, 55.75000),
        )
        val exit = RoadEdge(
            2L, "motorway_link", 80.0, 2, 3,
            doubleArrayOf(37.60120, 55.75000, 37.60120, 55.75080),
        )
        val graph = RoadGraph(
            "link-sw", 4, doubleArrayOf(37.599, 55.749, 37.603, 55.752),
            listOf(east, exit),
        )
        RoadGraphStore.clear()
        val dir = createTempDir(prefix = "roads-link-sw-")
        installSingleTileBundle(dir, graph)
        val rt = RoadMatchRuntime(
            mapsDir = { dir },
            pathTriggerM = 1.0,
            timeTriggerMs = 1L,
            switchConfirmCount = 1,
        )
        val seed = rt.maybeCorrect(
            true,
            RoadMatchPose(55.75000, 37.60040, 90f),
            speedKmh = 36f,
            nowElapsedMs = 1_000L,
        )
        assertNotNull(seed)
        assertEquals(1L, rt.debug.edgeId)

        val after = rt.maybeCorrect(
            true,
            RoadMatchPose(55.75008, 37.60120, 50f),
            speedKmh = 36f,
            nowElapsedMs = 3_000L,
        )
        assertNotNull(after)
        assertEquals(2L, rt.debug.edgeId)
        assertTrue(rt.debug.switchedEdge)
        val pulled = RoadMapMatcher.smallestAngleDeg(50f, after!!.bearingDeg)
        assertTrue("switch onto link must still catch up, pulled=$pulled", pulled >= 10f)
    }

    @Test
    fun runtimeDoesNotLockThroughRoadWhenInertialLeadsPastJunction() {
        val mPerDegLon = 111_320.0 * kotlin.math.cos(Math.toRadians(55.75))
        val mPerDegLat = 111_320.0
        val jLon = 37.61
        val jLat = 55.75
        val approach = RoadEdge(
            1L, "primary", 80.0, 0, 1,
            doubleArrayOf(jLon - 80.0 / mPerDegLon, jLat, jLon, jLat),
        )
        val through = RoadEdge(
            2L, "primary", 80.0, 1, 2,
            doubleArrayOf(jLon, jLat, jLon + 80.0 / mPerDegLon, jLat),
        )
        val turn = RoadEdge(
            3L, "primary", 80.0, 1, 3,
            doubleArrayOf(jLon, jLat, jLon, jLat + 80.0 / mPerDegLat),
        )
        val graph = RoadGraph(
            "lag-fork", 4, doubleArrayOf(37.59, 55.74, 37.63, 55.76),
            listOf(approach, through, turn),
        )
        RoadGraphStore.clear()
        val dir = createTempDir(prefix = "roads-lag-fork-")
        installSingleTileBundle(dir, graph)
        val rt = RoadMatchRuntime(
            mapsDir = { dir },
            pathTriggerM = 1.0,
            timeTriggerMs = 1L,
            switchConfirmCount = 1,
        )
        var lon = jLon - 40.0 / mPerDegLon
        var t = 1_000L
        assertNotNull(
            rt.maybeCorrect(true, RoadMatchPose(jLat, lon, 90f), speedKmh = 36f, nowElapsedMs = t),
        )
        assertEquals(1L, rt.debug.edgeId)

        while (lon < jLon + 8.0 / mPerDegLon) {
            lon += 4.0 / mPerDegLon
            t += 500L
            rt.maybeCorrect(true, RoadMatchPose(jLat, lon, 90f), speedKmh = 36f, nowElapsedMs = t)
        }
        assertEquals(
            "10 m rank-lag floor must keep the approach while heading is still east",
            1L,
            rt.debug.edgeId,
        )
        assertTrue((rt.debug.matchLagM ?: 0.0) >= 8.0)

        var lat = jLat
        var heading = 90f
        for (i in 1..8) {
            lat += 2.5 / mPerDegLat
            heading = (90f - 18f * i).coerceAtLeast(0f)
            t += 500L
            rt.maybeCorrect(
                true,
                RoadMatchPose(lat, jLon + 0.5 / mPerDegLon, heading),
                speedKmh = 36f,
                nowElapsedMs = t,
            )
        }
        assertEquals(3L, rt.debug.edgeId)
        assertTrue(rt.debug.switchedEdge || rt.debug.edgeId == 3L)
    }

    @Test
    fun runtimeHighwayLagStretchesToward30m() {
        val mPerDegLon = 111_320.0 * kotlin.math.cos(Math.toRadians(55.75))
        val lat = 55.75
        val edge = RoadEdge(
            1L, "motorway", 400.0, 0, 1,
            doubleArrayOf(37.60, lat, 37.62, lat),
        )
        val graph = RoadGraph(
            "lag-hwy", 4, doubleArrayOf(37.59, 55.74, 37.63, 55.76),
            listOf(edge),
        )
        RoadGraphStore.clear()
        val dir = createTempDir(prefix = "roads-lag-hwy-")
        installSingleTileBundle(dir, graph)
        val rt = RoadMatchRuntime(
            mapsDir = { dir },
            pathTriggerM = 1.0,
            timeTriggerMs = 1L,
        )
        var lon = 37.60 + 20.0 / mPerDegLon
        var t = 1_000L
        assertNotNull(
            rt.maybeCorrect(true, RoadMatchPose(lat, lon, 90f), speedKmh = 108f, nowElapsedMs = t),
        )
        repeat(12) {
            lon += 8.0 / mPerDegLon
            t += 500L
            rt.maybeCorrect(true, RoadMatchPose(lat, lon, 90f), speedKmh = 108f, nowElapsedMs = t)
        }
        val lag = rt.debug.matchLagM ?: 0.0
        assertTrue("highway rank lag should approach 30 m, was $lag", lag in 24.0..32.0)
        assertEquals(1L, rt.debug.edgeId)
    }

    @Test
    fun runtimeWithoutLagLocksThroughRoadWhenInertialLeadsPastJunction() {
        val mPerDegLon = 111_320.0 * kotlin.math.cos(Math.toRadians(55.75))
        val jLon = 37.61
        val jLat = 55.75
        val approach = RoadEdge(
            1L, "primary", 80.0, 0, 1,
            doubleArrayOf(jLon - 80.0 / mPerDegLon, jLat, jLon, jLat),
        )
        val through = RoadEdge(
            2L, "primary", 80.0, 1, 2,
            doubleArrayOf(jLon, jLat, jLon + 80.0 / mPerDegLon, jLat),
        )
        val turn = RoadEdge(
            3L, "primary", 80.0, 1, 3,
            doubleArrayOf(jLon, jLat, jLon, jLat + 80.0 / 111_320.0),
        )
        val graph = RoadGraph(
            "no-lag-fork", 4, doubleArrayOf(37.59, 55.74, 37.63, 55.76),
            listOf(approach, through, turn),
        )
        RoadGraphStore.clear()
        val dir = createTempDir(prefix = "roads-no-lag-fork-")
        installSingleTileBundle(dir, graph)
        val rt = RoadMatchRuntime(
            mapsDir = { dir },
            pathTriggerM = 1.0,
            timeTriggerMs = 1L,
            switchConfirmCount = 1,
            matchLagM = 0.0,
        )
        var lon = jLon - 40.0 / mPerDegLon
        var t = 1_000L
        assertNotNull(
            rt.maybeCorrect(true, RoadMatchPose(jLat, lon, 90f), speedKmh = 36f, nowElapsedMs = t),
        )
        while (lon < jLon + 8.0 / mPerDegLon) {
            lon += 4.0 / mPerDegLon
            t += 500L
            rt.maybeCorrect(true, RoadMatchPose(jLat, lon, 90f), speedKmh = 36f, nowElapsedMs = t)
        }
        assertEquals(
            "without lag the through-road wins while still heading east",
            2L,
            rt.debug.edgeId,
        )
    }

    @Test
    fun applyTurnSignalForkBiasBonusesTowardAndPenalizesThrough() {
        fun cand(id: Long, azimuth: Float, score: Double, connected: Boolean = true) =
            RoadMapMatcher.Candidate(
                edge = RoadEdge(id, "primary", 80.0, id.toInt(), id.toInt() + 1, doubleArrayOf(0.0, 0.0, 1.0, 0.0)),
                regionId = "r",
                crossTrackM = 0.0,
                alongTrackM = 10.0,
                projLat = 0.0,
                projLon = 0.0,
                edgeAzimuthDeg = azimuth,
                score = score,
                connectedFromPrevious = connected,
            )
        val approach = cand(1L, 90f, 2.0)
        val through = cand(2L, 90f, 3.0)
        val turn = cand(3L, 135f, 8.0)
        val ranked = listOf(approach, through, turn)
        val biased = RoadMapMatcher.applyTurnSignalForkBias(
            ranked = ranked,
            travelBearingDeg = 90f,
            hint = RoadMapMatcher.TurnHint.Right,
            previousEdgeId = 1L,
            previousRegionId = "r",
            turnIntent = true,
        )
        val byId = biased.associateBy { it.edge.id }
        assertEquals(2.0, byId.getValue(1L).score, 1e-6)
        assertEquals(3.0 + RoadMapMatcher.TURN_SIGNAL_STRAIGHT_PENALTY, byId.getValue(2L).score, 1e-6)
        assertEquals(8.0 + RoadMapMatcher.TURN_SIGNAL_TOWARD_BONUS, byId.getValue(3L).score, 1e-6)
        assertEquals(1L, biased.first().edge.id)
    }

    @Test
    fun applyTurnSignalForkBiasScalesWithWeight() {
        fun cand(id: Long, azimuth: Float, score: Double) =
            RoadMapMatcher.Candidate(
                edge = RoadEdge(id, "primary", 80.0, id.toInt(), id.toInt() + 1, doubleArrayOf(0.0, 0.0, 1.0, 0.0)),
                regionId = "r",
                crossTrackM = 0.0,
                alongTrackM = 10.0,
                projLat = 0.0,
                projLon = 0.0,
                edgeAzimuthDeg = azimuth,
                score = score,
                connectedFromPrevious = true,
            )
        val ranked = listOf(cand(1L, 90f, 2.0), cand(2L, 90f, 3.0), cand(3L, 135f, 8.0))
        val biased = RoadMapMatcher.applyTurnSignalForkBias(
            ranked = ranked,
            travelBearingDeg = 90f,
            hint = RoadMapMatcher.TurnHint.Right,
            previousEdgeId = 1L,
            previousRegionId = "r",
            weight = RoadMapMatcher.TURN_SIGNAL_ARC_WEIGHT,
            turnIntent = true,
        )
        val byId = biased.associateBy { it.edge.id }
        val w = RoadMapMatcher.TURN_SIGNAL_ARC_WEIGHT
        assertEquals(2.0, byId.getValue(1L).score, 1e-6)
        assertEquals(3.0 + RoadMapMatcher.TURN_SIGNAL_STRAIGHT_PENALTY * w, byId.getValue(2L).score, 1e-6)
        assertEquals(8.0 + RoadMapMatcher.TURN_SIGNAL_TOWARD_BONUS * w, byId.getValue(3L).score, 1e-6)
        assertEquals(1L, biased.first().edge.id)
    }

    @Test
    fun applyTurnSignalForkBiasNoOpWithoutTowardCandidate() {
        fun cand(id: Long, azimuth: Float, score: Double, connected: Boolean = true) =
            RoadMapMatcher.Candidate(
                edge = RoadEdge(id, "primary", 80.0, id.toInt(), id.toInt() + 1, doubleArrayOf(0.0, 0.0, 1.0, 0.0)),
                regionId = "r",
                crossTrackM = 0.0,
                alongTrackM = 10.0,
                projLat = 0.0,
                projLon = 0.0,
                edgeAzimuthDeg = azimuth,
                score = score,
                connectedFromPrevious = connected,
            )
        val approach = cand(1L, 90f, 2.0)
        val through = cand(2L, 88f, 3.0)
        val earlyLink = cand(3L, 95f, 4.0)
        val disconnectedTurn = cand(4L, 135f, 5.0, connected = false)
        val ranked = listOf(approach, through, earlyLink, disconnectedTurn)
        assertFalse(
            RoadMapMatcher.turnSignalTowardExists(ranked, 90f, RoadMapMatcher.TurnHint.Right),
        )
        val biased = RoadMapMatcher.applyTurnSignalForkBias(
            ranked = ranked,
            travelBearingDeg = 90f,
            hint = RoadMapMatcher.TurnHint.Right,
            previousEdgeId = 1L,
            previousRegionId = "r",
            turnIntent = true,
        )
        assertEquals(ranked.map { it.edge.id to it.score }, biased.map { it.edge.id to it.score })
    }

    @Test
    fun runtimeTurnSignalHintKeepsApproachUntilHeadingTurns() {
        val mPerDegLon = 111_320.0 * kotlin.math.cos(Math.toRadians(55.75))
        val mPerDegLat = 111_320.0
        val jLon = 37.61
        val jLat = 55.75
        val turnRad = Math.toRadians(130.0)
        val approach = RoadEdge(
            1L, "primary", 80.0, 0, 1,
            doubleArrayOf(jLon - 80.0 / mPerDegLon, jLat, jLon, jLat),
        )
        val through = RoadEdge(
            2L, "primary", 80.0, 1, 2,
            doubleArrayOf(jLon, jLat, jLon + 80.0 / mPerDegLon, jLat),
        )
        val turn = RoadEdge(
            3L, "primary", 80.0, 1, 3,
            doubleArrayOf(
                jLon,
                jLat,
                jLon + 80.0 * kotlin.math.sin(turnRad) / mPerDegLon,
                jLat + 80.0 * kotlin.math.cos(turnRad) / mPerDegLat,
            ),
        )
        val graph = RoadGraph(
            "hint-fork", 4, doubleArrayOf(37.59, 55.74, 37.63, 55.76),
            listOf(approach, through, turn),
        )
        RoadGraphStore.clear()
        val dir = createTempDir(prefix = "roads-hint-fork-")
        installSingleTileBundle(dir, graph)

        fun runtime() = RoadMatchRuntime(
            mapsDir = { dir },
            pathTriggerM = 1.0,
            timeTriggerMs = 1L,
            switchConfirmCount = 1,
            matchLagM = 0.0,
        )

        val without = runtime()
        var lon = jLon - 40.0 / mPerDegLon
        var t = 1_000L
        assertNotNull(
            without.maybeCorrect(true, RoadMatchPose(jLat, lon, 90f), speedKmh = 36f, nowElapsedMs = t),
        )
        while (lon < jLon + 8.0 / mPerDegLon) {
            lon += 4.0 / mPerDegLon
            t += 500L
            without.maybeCorrect(true, RoadMatchPose(jLat, lon, 90f), speedKmh = 36f, nowElapsedMs = t)
        }
        assertEquals(
            "without stalk the through-road still wins past the node",
            2L,
            without.debug.edgeId,
        )

        val withHint = runtime()
        lon = jLon - 40.0 / mPerDegLon
        t = 1_000L
        assertNotNull(
            withHint.maybeCorrect(
                true,
                RoadMatchPose(jLat, lon, 90f),
                speedKmh = 36f,
                nowElapsedMs = t,
                turnHint = RoadMapMatcher.TurnHint.Right,
                turnIntent = true,
            ),
        )
        assertEquals(1L, withHint.debug.edgeId)
        while (lon < jLon + 8.0 / mPerDegLon) {
            lon += 4.0 / mPerDegLon
            t += 500L
            withHint.maybeCorrect(
                true,
                RoadMatchPose(jLat, lon, 90f),
                speedKmh = 36f,
                nowElapsedMs = t,
                turnHint = RoadMapMatcher.TurnHint.Right,
                turnIntent = true,
            )
        }
        assertEquals(
            "Right stalk must not lock the through-road while heading is still east",
            1L,
            withHint.debug.edgeId,
        )
        assertEquals("R", withHint.debug.turnHint)
        assertTrue(withHint.debug.turnActive == true)

        var heading = 90f
        var distM = 0.0
        for (i in 1..10) {
            distM += 4.0
            heading = (90f + 8f * i).coerceAtMost(130f)
            t += 500L
            val lat = jLat + distM * kotlin.math.cos(turnRad) / mPerDegLat
            val turnLon = jLon + distM * kotlin.math.sin(turnRad) / mPerDegLon
            withHint.maybeCorrect(
                true,
                RoadMatchPose(lat, turnLon, heading),
                speedKmh = 36f,
                nowElapsedMs = t,
                turnHint = RoadMapMatcher.TurnHint.Right,
                turnIntent = true,
            )
        }
        assertEquals(3L, withHint.debug.edgeId)
    }

    @Test
    fun runtimeTurnSignalHintDoesNotChaseEarlyStraightLink() {
        val motorway = RoadEdge(
            1778L, "motorway", 400.0, 1, 2,
            doubleArrayOf(37.60000, 55.75000, 37.60500, 55.75000),
        )
        val ramp = RoadEdge(
            1783L, "trunk_link", 350.0, 2, 3,
            doubleArrayOf(
                37.60500, 55.75000,
                37.60620, 55.75000,
                37.60720, 55.74970,
                37.60780, 55.74910,
                37.60790, 55.74830,
            ),
        )
        val graph = RoadGraph(
            "hint-link", 4, doubleArrayOf(37.599, 55.747, 37.609, 55.751),
            listOf(motorway, ramp),
        )
        RoadGraphStore.clear()
        val dir = createTempDir(prefix = "roads-hint-link-")
        installSingleTileBundle(dir, graph)
        val rt = RoadMatchRuntime(
            mapsDir = { dir },
            pathTriggerM = 1.0,
            timeTriggerMs = 1L,
            switchConfirmCount = 1,
        )
        val seed = rt.maybeCorrect(
            true,
            RoadMatchPose(55.75000, 37.60200, 90f),
            speedKmh = 72f,
            nowElapsedMs = 1_000L,
            turnHint = RoadMapMatcher.TurnHint.Right,
        )
        assertNotNull(seed)
        assertEquals(1778L, rt.debug.edgeId)
        assertNull(rt.debug.turnHint)

        val onRamp = rt.maybeCorrect(
            true,
            RoadMatchPose(55.75000, 37.60540, 90f),
            speedKmh = 72f,
            nowElapsedMs = 2_000L,
            turnHint = RoadMapMatcher.TurnHint.Right,
        )
        assertNotNull(onRamp)
        assertEquals(1783L, rt.debug.edgeId)
        assertNull(
            "early slip-road still ~straight — stalk must not become a fork hint",
            rt.debug.turnHint,
        )

        var pose = onRamp!!
        for (i in 1..4) {
            val lon = 37.60540 + 0.00045 * i
            val lat = 55.75000 - 0.00012 * i
            pose = rt.maybeCorrect(
                true,
                RoadMatchPose(lat, lon, pose.bearingDeg),
                speedKmh = 72f,
                nowElapsedMs = 2_000L + i * 1_000L,
                turnHint = RoadMapMatcher.TurnHint.Right,
            ) ?: pose
            assertEquals(1783L, rt.debug.edgeId)
        }
        val spun = RoadMapMatcher.smallestAngleDeg(90f, pose.bearingDeg)
        assertTrue(
            "Right stalk must not chase the curving ramp, spun=$spun bearing=${pose.bearingDeg}",
            spun < 8f,
        )
    }

    @Test
    fun isBentOnewayArcDetectsShortCurveAndIgnoresStraightOrLong() {
        val mPerDegLon = 111_320.0 * kotlin.math.cos(Math.toRadians(55.75))
        val mPerDegLat = 111_320.0
        val lon0 = 37.61
        val lat0 = 55.75
        val bent = RoadEdge(
            1L, "secondary", 55.0, 1, 2,
            doubleArrayOf(
                lon0, lat0,
                lon0 + 15.0 / mPerDegLon, lat0 + 25.0 / mPerDegLat,
                lon0 + 50.0 / mPerDegLon, lat0 + 30.0 / mPerDegLat,
            ),
            oneway = 1,
        )
        val straight = RoadEdge(
            2L, "secondary", 50.0, 2, 3,
            doubleArrayOf(lon0, lat0, lon0 + 50.0 / mPerDegLon, lat0),
            oneway = 1,
        )
        val twoWayBend = bent.copy(id = 3L, oneway = 0)
        val longGentle = RoadEdge(
            4L, "secondary", 400.0, 4, 5,
            doubleArrayOf(
                lon0, lat0,
                lon0 + 200.0 / mPerDegLon, lat0 + 20.0 / mPerDegLat,
                lon0 + 400.0 / mPerDegLon, lat0 + 30.0 / mPerDegLat,
            ),
            oneway = 1,
        )
        assertTrue(RoadMapMatcher.polylineBendDeg(bent) >= RoadMapMatcher.BENT_ONEWAY_ARC_MIN_BEND_DEG)
        assertTrue(RoadMapMatcher.isBentOnewayArc(bent))
        assertFalse(RoadMapMatcher.isBentOnewayArc(straight))
        assertFalse(RoadMapMatcher.isBentOnewayArc(twoWayBend))
        assertFalse(RoadMapMatcher.isBentOnewayArc(longGentle))
        val shortChord = RoadEdge(
            5L, "secondary", 22.0, 5, 6,
            doubleArrayOf(lon0, lat0, lon0, lat0 + 22.0 / mPerDegLat),
            oneway = 1,
        )
        val shortLink = shortChord.copy(id = 6L, highwayClass = "primary_link")
        assertTrue(RoadMapMatcher.isBentOnewayArc(shortChord))
        assertFalse(RoadMapMatcher.isBentOnewayArc(shortLink))
    }

    @Test
    fun headingToleranceKeepsSameEdgeAndCirculatingSuccessor() {
        val lon0 = 37.61
        val lat0 = 55.75
        val chord = RoadEdge(
            1L, "secondary", 22.0, 1, 2,
            doubleArrayOf(lon0, lat0, lon0, lat0 + 0.0002),
            oneway = 1,
        )
        val ordinary = RoadEdge(
            2L, "secondary", 80.0, 2, 3,
            doubleArrayOf(lon0, lat0, lon0 + 0.001, lat0),
            oneway = 0,
        )
        assertEquals(
            RoadMapMatcher.SAME_EDGE_HEADING_TOLERANCE_DEG,
            RoadMapMatcher.headingToleranceDeg(ordinary, sameEdge = true, connected = false),
            1e-6,
        )
        assertEquals(
            RoadMapMatcher.CIRCULATING_HEADING_TOLERANCE_DEG,
            RoadMapMatcher.headingToleranceDeg(chord, sameEdge = false, connected = true),
            1e-6,
        )
        assertEquals(
            RoadMapMatcher.HEADING_TOLERANCE_DEG,
            RoadMapMatcher.headingToleranceDeg(ordinary, sameEdge = false, connected = true),
            1e-6,
        )
        assertEquals(
            RoadMapMatcher.CIRCULATING_HEADING_TOLERANCE_DEG,
            RoadMapMatcher.headingToleranceDeg(
                ordinary, sameEdge = false, connected = true, circulatingManeuver = true,
            ),
            1e-6,
        )
    }

    @Test
    fun reachableTopologyDistance_rejectsDisconnectedAndOverBudgetNavigatorTargets() {
        val mPerDegLon = 111_320.0 * kotlin.math.cos(Math.toRadians(55.75))
        val lon0 = 37.61
        val lat0 = 55.75
        fun eastEdge(id: Long, from: Int, to: Int, startM: Double, endM: Double) = RoadEdge(
            id, "secondary", endM - startM, from, to,
            doubleArrayOf(
                lon0 + startM / mPerDegLon, lat0,
                lon0 + endM / mPerDegLon, lat0,
            ),
            oneway = 1,
        )
        val a = eastEdge(1L, 1, 2, 0.0, 20.0)
        val b = eastEdge(2L, 2, 3, 20.0, 60.0)
        val c = eastEdge(3L, 3, 4, 60.0, 90.0)
        val disconnected = eastEdge(99L, 20, 21, 200.0, 230.0)
        val graph = RoadGraph(
            "budget", 4, doubleArrayOf(37.60, 55.74, 37.62, 55.76),
            listOf(a, b, c, disconnected),
        )
        val start = RoadMapMatcher.TopologyAnchor("budget", 1L, 15.0, false)
        val reachable = RoadMapMatcher.TopologyAnchor("budget", 3L, 10.0, false)
        // 5 m to end A + 40 m on B + 10 m on C = 55 m.
        assertEquals(
            55.0,
            RoadMapMatcher.reachableTopologyDistanceM(
                listOf(graph), start, reachable, maxDistanceM = 55.1,
            )!!,
            0.5,
        )
        assertNull(
            RoadMapMatcher.reachableTopologyDistanceM(
                listOf(graph), start, reachable, maxDistanceM = 54.0,
            ),
        )
        assertNull(
            RoadMapMatcher.reachableTopologyDistanceM(
                listOf(graph),
                start,
                RoadMapMatcher.TopologyAnchor("budget", 99L, 5.0, false),
                maxDistanceM = 200.0,
            ),
        )
        assertNull(
            RoadMapMatcher.reachableTopologyDistanceM(
                listOf(graph),
                start,
                RoadMapMatcher.TopologyAnchor("budget", 1L, 5.0, false),
                maxDistanceM = 200.0,
            ),
        )
    }

    @Test
    fun rankKeepsSameEdgeAndNextChordWhenHeadingSwings() {
        val mPerDegLon = 111_320.0 * kotlin.math.cos(Math.toRadians(55.75))
        val mPerDegLat = 111_320.0
        val lon0 = 37.61
        val lat0 = 55.75
        val entry = RoadEdge(
            1L, "secondary", 22.0, 1, 2,
            doubleArrayOf(lon0, lat0, lon0, lat0 + 22.0 / mPerDegLat),
            oneway = 1,
        )
        val next = RoadEdge(
            2L, "secondary", 30.0, 2, 3,
            doubleArrayOf(
                lon0, lat0 + 22.0 / mPerDegLat,
                lon0 - 30.0 / mPerDegLon, lat0 + 22.0 / mPerDegLat,
            ),
            oneway = 1,
        )
        val graph = RoadGraph(
            "ring", 4, doubleArrayOf(37.608, 55.748, 37.612, 55.752),
            listOf(entry, next),
        )
        val pose = RoadMatchPose(
            lat0 + 16.0 / mPerDegLat,
            lon0 - 4.0 / mPerDegLon,
            290f,
        )
        val ranked = RoadMapMatcher.rankCandidates(
            pose = pose,
            graphs = listOf(graph),
            previousEdgeId = 1L,
            previousRegionId = "ring",
        )
        assertTrue(ranked.any { it.edge.id == 1L })
        assertTrue(ranked.any { it.edge.id == 2L })
        val skipped = RoadEdge(
            99L, "residential", 80.0, 9, 10,
            doubleArrayOf(
                lon0 - 40.0 / mPerDegLon, lat0 + 10.0 / mPerDegLat,
                lon0, lat0 + 10.0 / mPerDegLat,
            ),
        )
        val skipCand = RoadMapMatcher.Candidate(
            edge = skipped,
            regionId = "ring",
            crossTrackM = 1.0,
            alongTrackM = 5.0,
            projLat = lat0,
            projLon = lon0,
            edgeAzimuthDeg = 270f,
            score = -10.0,
            connectedFromPrevious = false,
        )
        val nextCand = ranked.first { it.edge.id == 2L }
        val promoted = RoadMapMatcher.preferImmediateSuccessor(
            ranked = listOf(skipCand, nextCand),
            graphs = listOf(graph),
            previous = entry,
            previousRegionId = "ring",
            travelAgainstCoords = false,
            travelBearingDeg = 0f,
            allowAgainstOneway = false,
        )
        assertEquals(2L, promoted.first().edge.id)
    }

    @Test
    fun runtimeKeepsWeakTurnHintOnBentOnewayArc() {
        val mPerDegLon = 111_320.0 * kotlin.math.cos(Math.toRadians(55.75))
        val mPerDegLat = 111_320.0
        val lon0 = 37.61
        val lat0 = 55.75
        val arc = RoadEdge(
            1L, "secondary", 55.0, 1, 2,
            doubleArrayOf(
                lon0, lat0,
                lon0 + 15.0 / mPerDegLon, lat0 + 25.0 / mPerDegLat,
                lon0 + 50.0 / mPerDegLon, lat0 + 30.0 / mPerDegLat,
            ),
            oneway = 1,
        )
        val exit = RoadEdge(
            2L, "secondary", 80.0, 2, 3,
            doubleArrayOf(
                lon0 + 50.0 / mPerDegLon, lat0 + 30.0 / mPerDegLat,
                lon0 + 130.0 / mPerDegLon, lat0 + 30.0 / mPerDegLat,
            ),
            oneway = 1,
        )
        val graph = RoadGraph(
            "ring-hint", 4, doubleArrayOf(37.608, 55.748, 37.614, 55.752),
            listOf(arc, exit),
        )
        RoadGraphStore.clear()
        val dir = createTempDir(prefix = "roads-ring-hint-")
        installSingleTileBundle(dir, graph)
        val rt = RoadMatchRuntime(
            mapsDir = { dir },
            pathTriggerM = 1.0,
            timeTriggerMs = 1L,
            switchConfirmCount = 1,
            matchLagM = 0.0,
        )
        val seedLat = lat0 + 12.0 / mPerDegLat
        val seedLon = lon0 + 8.0 / mPerDegLon
        assertNotNull(
            rt.maybeCorrect(
                true,
                RoadMatchPose(seedLat, seedLon, 45f),
                speedKmh = 36f,
                nowElapsedMs = 1_000L,
            ),
        )
        assertEquals(1L, rt.debug.edgeId)
        assertTrue(RoadMapMatcher.isBentOnewayArc(arc))

        val nearExit = rt.maybeCorrect(
            true,
            RoadMatchPose(
                lat0 + 26.0 / mPerDegLat,
                lon0 + 46.0 / mPerDegLon,
                50f,
            ),
            speedKmh = 36f,
            nowElapsedMs = 2_000L,
            turnHint = RoadMapMatcher.TurnHint.Right,
            turnIntent = true,
        )
        assertNotNull(nearExit)
        assertEquals(
            "Weak Right stalk on a bent oneway arc must not yank onto the exit",
            1L,
            rt.debug.edgeId,
        )
        assertEquals(
            "toward-exit on an arc still logs the hint, at reduced weight",
            "R",
            rt.debug.turnHint,
        )
    }

    @Test
    fun runtimeDropsCorridorWhenHeadingOpposesEdge() {
        val entry = RoadEdge(
            1L, "primary", 20.0, 1, 2,
            doubleArrayOf(37.60000, 55.75000, 37.60032, 55.75000),
        )
        val north = RoadEdge(
            2L, "primary", 120.0, 2, 3,
            doubleArrayOf(37.60032, 55.75000, 37.60032, 55.75110),
        )
        val graph = RoadGraph(
            "corridor-opp", 4, doubleArrayOf(37.599, 55.749, 37.603, 55.753),
            listOf(entry, north),
        )
        RoadGraphStore.clear()
        val dir = createTempDir(prefix = "roads-corridor-opp-")
        installSingleTileBundle(dir, graph)
        val rt = RoadMatchRuntime(
            mapsDir = { dir },
            pathTriggerM = 1.0,
            timeTriggerMs = 1L,
            switchConfirmCount = 1,
        )
        assertNotNull(
            rt.maybeCorrect(
                true,
                RoadMatchPose(55.75000, 37.60016, 90f),
                speedKmh = 36f,
                nowElapsedMs = 1_000L,
            ),
        )
        rt.maybeCorrect(
            true,
            RoadMatchPose(55.75000, 37.60096, 180f),
            speedKmh = 36f,
            nowElapsedMs = 3_000L,
        )
        assertTrue(
            "southbound heading must not ride the north corridor, conf=${rt.debug.confidence}",
            rt.debug.confidence != "CONNECTED_CORRIDOR",
        )
        assertTrue(rt.debug.rejectReason != "no_candidate_corridor")
    }

    @Test
    fun sameNodeSuccessorBeatsNearbySpatialExit() {
        val mPerDegLon = 111_320.0 * kotlin.math.cos(Math.toRadians(55.75))
        val mPerDegLat = 111_320.0
        val lonJ = 37.61000
        val latJ = 55.75000
        val arc = RoadEdge(
            1L, "secondary", 50.0, 1, 2,
            doubleArrayOf(lonJ - 50.0 / mPerDegLon, latJ, lonJ, latJ),
            oneway = 1,
        )
        val loop = RoadEdge(
            2L, "secondary", 60.0, 2, 3,
            doubleArrayOf(lonJ, latJ, lonJ, latJ + 60.0 / mPerDegLat),
            oneway = 1,
        )
        val exit = RoadEdge(
            3L, "secondary", 80.0, 99, 100,
            doubleArrayOf(
                lonJ + 11.0 / mPerDegLon, latJ,
                lonJ + 91.0 / mPerDegLon, latJ,
            ),
            oneway = 1,
        )
        val graph = RoadGraph(
            "ring-node", 4, doubleArrayOf(37.608, 55.748, 37.614, 55.752),
            listOf(arc, loop, exit),
        )
        assertTrue(graph.isConnected(1L, 2L))
        assertFalse(
            "11 m different-node exit must not count as connected when a same-node loop exists",
            RoadMapMatcher.isConnectedFromPrevious(
                listOf(graph), 1L, "ring-node", exit, "ring-node",
            ),
        )
        assertTrue(
            RoadMapMatcher.isConnectedFromPrevious(
                listOf(graph), 1L, "ring-node", loop, "ring-node",
            ),
        )
        assertEquals(
            1,
            RoadMapMatcher.forwardSuccessorCount(
                listOf(graph), "ring-node", arc,
                travelAgainstCoords = false,
                allowAgainstOneway = false,
            ),
        )
        val pred = RoadMapMatcher.advanceAlongTopology(
            graphs = listOf(graph),
            start = RoadMapMatcher.TopologyAnchor("ring-node", 1L, 45.0, false),
            distanceM = 20.0,
            targetBearingDeg = 90f,
        )
        assertNotNull(pred)
        assertEquals(
            "same-node loop must win over a heading-aligned 11 m exit",
            2L,
            pred!!.edge.id,
        )
    }

    @Test
    fun spatialSeamStillConnectsWhenNoSameNodeSuccessor() {
        val mPerDegLon = 111_320.0 * kotlin.math.cos(Math.toRadians(55.75))
        val a = RoadEdge(
            10L, "primary", 100.0, 1, 2,
            doubleArrayOf(37.60, 55.75, 37.601, 55.75),
        )
        val b = RoadEdge(
            20L, "primary", 100.0, 99, 100,
            doubleArrayOf(37.601 + 8.0 / mPerDegLon, 55.75, 37.603, 55.75),
        )
        val graph = RoadGraph(
            "seam", 4, doubleArrayOf(37.59, 55.74, 37.61, 55.76),
            listOf(a, b),
        )
        assertTrue(
            RoadMapMatcher.isConnectedFromPrevious(listOf(graph), 10L, "seam", b, "seam"),
        )
        assertEquals(
            1,
            RoadMapMatcher.forwardSuccessorCount(
                listOf(graph), "seam", a,
                travelAgainstCoords = false,
                allowAgainstOneway = false,
            ),
        )
    }

    @Test
    fun runtimeStaysOnSameNodeLoopNotNearbyExit() {
        val mPerDegLon = 111_320.0 * kotlin.math.cos(Math.toRadians(55.75))
        val mPerDegLat = 111_320.0
        val lonJ = 37.61000
        val latJ = 55.75000
        val arc = RoadEdge(
            1L, "secondary", 50.0, 1, 2,
            doubleArrayOf(lonJ - 50.0 / mPerDegLon, latJ, lonJ, latJ),
            oneway = 1,
        )
        val loop = RoadEdge(
            2L, "secondary", 60.0, 2, 3,
            doubleArrayOf(lonJ, latJ, lonJ, latJ + 60.0 / mPerDegLat),
            oneway = 1,
        )
        val exit = RoadEdge(
            3L, "secondary", 80.0, 99, 100,
            doubleArrayOf(
                lonJ + 11.0 / mPerDegLon, latJ,
                lonJ + 91.0 / mPerDegLon, latJ,
            ),
            oneway = 1,
        )
        val graph = RoadGraph(
            "ring-stay", 4, doubleArrayOf(37.608, 55.748, 37.614, 55.752),
            listOf(arc, loop, exit),
        )
        RoadGraphStore.clear()
        val dir = createTempDir(prefix = "roads-ring-stay-")
        installSingleTileBundle(dir, graph)
        val rt = RoadMatchRuntime(
            mapsDir = { dir },
            pathTriggerM = 1.0,
            timeTriggerMs = 1L,
            switchConfirmCount = 1,
            matchLagM = 0.0,
        )
        val seed = RoadMatchPose(latJ, lonJ - 25.0 / mPerDegLon, 90f)
        assertNotNull(
            rt.maybeCorrect(true, seed, speedKmh = 36f, nowElapsedMs = 1_000L),
        )
        assertEquals(1L, rt.debug.edgeId)
        rt.maybeCorrect(
            true,
            RoadMatchPose(latJ, lonJ + 9.5 / mPerDegLon, 90f),
            speedKmh = 36f,
            nowElapsedMs = 2_000L,
        )
        assertTrue(
            "overshoot toward the 11 m exit must not take it, edge=${rt.debug.edgeId}",
            rt.debug.edgeId == 1L || rt.debug.edgeId == 2L,
        )
        assertTrue(rt.debug.edgeId != 3L)
        rt.maybeCorrect(
            true,
            RoadMatchPose(latJ + 8.0 / mPerDegLat, lonJ + 2.0 / mPerDegLon, 10f),
            speedKmh = 36f,
            nowElapsedMs = 3_000L,
        )
        assertEquals(
            "after turning onto the loop the 11 m exit must stay disconnected",
            2L,
            rt.debug.edgeId,
        )
    }

    @Test
    fun runtimePullsHeadingOnHoldWhenResidualClose() {
        // Field 073412 07:55: HOLD_EDGE residential, residual ~12°, must pull again.
        val east = RoadEdge(
            1L, "residential", 200.0, 1, 2,
            doubleArrayOf(37.60000, 55.75000, 37.60300, 55.75000),
        )
        val graph = RoadGraph(
            "hold-close", 4, doubleArrayOf(37.599, 55.749, 37.604, 55.751),
            listOf(east),
        )
        RoadGraphStore.clear()
        val dir = createTempDir(prefix = "roads-hold-close-")
        installSingleTileBundle(dir, graph)
        val rt = RoadMatchRuntime(
            mapsDir = { dir },
            pathTriggerM = 1.0,
            timeTriggerMs = 1L,
        )
        val seed = rt.maybeCorrect(
            true,
            RoadMatchPose(55.75000, 37.60100, 90f),
            speedKmh = 40f,
            nowElapsedMs = 1_000L,
        )
        assertNotNull(seed)
        assertEquals(1L, rt.debug.edgeId)

        // Residual grows to ~12°: first tick may treat as leaving (headingAway).
        assertNotNull(
            rt.maybeCorrect(
                true,
                RoadMatchPose(55.75012, 37.60140, 102f),
                speedKmh = 40f,
                nowElapsedMs = 3_000L,
            ),
        )
        // Stable overshoot (field 07:55): residual no longer grows → pull resumes.
        val during = rt.maybeCorrect(
            true,
            RoadMatchPose(55.75012, 37.60180, 102f),
            speedKmh = 40f,
            nowElapsedMs = 5_000L,
        )
        assertNotNull(during)
        assertEquals(1L, rt.debug.edgeId)
        val residual = RoadMapMatcher.smallestAngleDeg(102f, 90f)
        assertTrue(residual <= RoadMatchLeashMath.HEADING_PULL_WHEN_CLOSE_DEG)
        val pulled = RoadMapMatcher.smallestAngleDeg(102f, during!!.bearingDeg)
        assertTrue("expected heading pull toward east, pulled=$pulled", pulled >= 3f)
        assertTrue(
            RoadMapMatcher.smallestAngleDeg(during.bearingDeg, 90f) < residual,
        )
    }

    @Test
    fun runtimeRegrabsAlignedRoadAfterLostSticky() {
        // Field 073412 07:58: sticky hold fails, neighbour ahead with residual ~12° / xt ~28 m.
        val south = RoadEdge(
            1L, "tertiary", 200.0, 1, 2,
            doubleArrayOf(37.60000, 55.75000, 37.60300, 55.75000),
        )
        val north = RoadEdge(
            2L, "residential", 200.0, 3, 4,
            doubleArrayOf(37.60000, 55.75028, 37.60300, 55.75028),
        )
        val graph = RoadGraph(
            "regrab", 4, doubleArrayOf(37.599, 55.749, 37.604, 55.752),
            listOf(south, north),
        )
        RoadGraphStore.clear()
        val dir = createTempDir(prefix = "roads-regrab-")
        installSingleTileBundle(dir, graph)
        val rt = RoadMatchRuntime(
            mapsDir = { dir },
            pathTriggerM = 1.0,
            timeTriggerMs = 1L,
            switchConfirmCount = 1,
            matchLagM = 0.0,
        )
        assertNotNull(
            rt.maybeCorrect(
                true,
                RoadMatchPose(55.75000, 37.60100, 90f),
                speedKmh = 30f,
                nowElapsedMs = 1_000L,
            ),
        )
        assertEquals(1L, rt.debug.edgeId)

        // ~31 m north of sticky (outside HOLD 24 m), heading still east → north road wins.
        val again = rt.maybeCorrect(
            true,
            RoadMatchPose(55.75028, 37.60140, 92f),
            speedKmh = 20f,
            nowElapsedMs = 3_000L,
        )
        assertNotNull(again)
        assertEquals(2L, rt.debug.edgeId)
    }

    @Test
    fun runtimeDoesNotFreezeAlongOnOrdinaryLeftTurn() {
        // Field 122235: treating a ~90° left turn on two-way secondary 54447
        // as a circulating reverse-slide armed clampReverseSlide and froze
        // along-track. Circulating clamps must stay on bent oneway arcs only.
        val mPerDegLon = 111_320.0 * kotlin.math.cos(Math.toRadians(55.75))
        val mPerDegLat = 111_320.0
        val lon0 = 37.61
        val lat0 = 55.75
        val north = RoadEdge(
            1L, "secondary", 400.0, 1, 2,
            doubleArrayOf(lon0, lat0, lon0, lat0 + 400.0 / mPerDegLat),
        )
        val graph = RoadGraph(
            "rev-slide-false", 4, doubleArrayOf(37.608, 55.748, 37.614, 55.754),
            listOf(north),
        )
        RoadGraphStore.clear()
        val dir = createTempDir(prefix = "roads-rev-slide-false-")
        installSingleTileBundle(dir, graph)
        val rt = RoadMatchRuntime(
            mapsDir = { dir },
            pathTriggerM = 1.0,
            timeTriggerMs = 1L,
            switchConfirmCount = 3,
            matchLagM = 0.0,
        )
        assertNotNull(
            rt.maybeCorrect(
                true,
                RoadMatchPose(lat0 + 250.0 / mPerDegLat, lon0, 0f),
                speedKmh = 40f,
                nowElapsedMs = 1_000L,
            ),
        )
        assertEquals(1L, rt.debug.edgeId)
        val lockedAlong = rt.alongTrackM()
        assertNotNull(lockedAlong)
        assertTrue(lockedAlong!! in 230.0..270.0)
        assertFalse(RoadMapMatcher.isBentOnewayArc(north))

        val turning = rt.maybeCorrect(
            true,
            RoadMatchPose(
                lat0 + 210.0 / mPerDegLat,
                lon0 - 6.0 / mPerDegLon,
                250f,
            ),
            speedKmh = 40f,
            nowElapsedMs = 3_000L,
        )
        assertNotNull(turning)
        val alongAfter = rt.alongTrackM()
        val frozenOnSameEdge = rt.debug.edgeId == 1L &&
            alongAfter != null &&
            alongAfter >= lockedAlong - 5.0
        assertFalse(
            "ordinary left turn must not clamp along back to $lockedAlong (now $alongAfter)",
            frozenOnSameEdge,
        )
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
