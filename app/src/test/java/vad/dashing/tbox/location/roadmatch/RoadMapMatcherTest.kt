package vad.dashing.tbox.location.roadmatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
        val pose = RoadMatchPose(55.7502, 37.61, 90f)
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
            """{"id":${e.id},"class":"${e.highwayClass}","lengthM":${e.lengthM},"from":${e.fromNode},"to":${e.toNode},"coords":[$coords]}"""
        }
        val json =
            """{"format":1,"regionId":"${graph.regionId}","graphVersion":${graph.graphVersion},"bbox":[${graph.bbox.joinToString(",")}],"edges":[$edgesJson]}"""
        val gz = java.io.ByteArrayOutputStream()
        java.util.zip.GZIPOutputStream(gz).use { it.write(json.toByteArray(Charsets.UTF_8)) }
        return RoadGraph.MAGIC.toByteArray(Charsets.US_ASCII) + gz.toByteArray()
    }
}
