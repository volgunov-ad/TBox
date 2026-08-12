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
        RoadGraphStore.put("rt", graph)
        val dir = java.io.File.createTempFile("roads", "dir").apply {
            delete()
            mkdirs()
        }
        // Runtime loads from files; seed a pack file via tool-less write of cached graph isn't enough —
        // write a minimal pack using RoadGraph companion isn't available. Place empty and inject via mapsDir
        // by writing bytes from test helper.
        val pack = java.io.File(dir, "rt.tboxroads")
        pack.writeBytes(packBytesFor(graph))

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
        val dir = java.io.File.createTempFile("roads", "dir").apply {
            delete()
            mkdirs()
        }
        java.io.File(dir, "amb.tboxroads").writeBytes(packBytesFor(graph))
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
        val dir = java.io.File.createTempFile("roads", "dir").apply {
            delete()
            mkdirs()
        }
        java.io.File(dir, "hold.tboxroads").writeBytes(packBytesFor(graph))
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
