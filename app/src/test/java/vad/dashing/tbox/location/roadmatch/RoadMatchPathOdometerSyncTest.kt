package vad.dashing.tbox.location.roadmatch

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.math.abs
import kotlin.math.cos

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RoadMatchPathOdometerSyncTest {

    private lateinit var mapsDir: File

    @Before
    fun setUp() {
        RoadGraphStore.clear()
        mapsDir = createTempDir(prefix = "roads-path-odo-")
    }

    @After
    fun tearDown() {
        RoadGraphStore.clear()
        mapsDir.deleteRecursively()
    }

    @Test
    fun instrumentPathPullsForwardWhenPoseStallsAfterSnap() {
        installStraightEast()
        val rt = runtime()
        val seedLon = lonAtAlongM(40.0)
        val seed = rt.maybeCorrect(
            true,
            RoadMatchPose(LAT, seedLon, 90f),
            speedKmh = 36f,
            nowElapsedMs = 1_000L,
        )
        assertNotNull(seed)
        assertEquals(1L, rt.debug.edgeId)

        val stalled = rt.maybeCorrect(
            enabled = true,
            pose = RoadMatchPose(LAT, seedLon, 90f),
            speedKmh = 36f,
            nowElapsedMs = 1_500L,
            instrumentStepM = 12.0,
        )
        assertNotNull(stalled)
        val pulledM = eastM(seedLon, stalled!!.lon)
        assertTrue("expected along-track catch-up, got $pulledM m", pulledM > 1.5)
        assertTrue(pulledM <= RoadMatchRuntime.PATH_ODO_SYNC_MAX_STEP_M + 0.4)
        assertTrue((rt.debug.pathOdoGapM ?: 0.0) > 2.0)
    }

    @Test
    fun midTurnDoesNotPullAlongTrack() {
        installStraightEast()
        val rt = runtime()
        val seedLon = lonAtAlongM(40.0)
        assertNotNull(
            rt.maybeCorrect(
                true,
                RoadMatchPose(LAT, seedLon, 90f),
                speedKmh = 36f,
                nowElapsedMs = 1_000L,
            ),
        )

        val duringTurn = rt.maybeCorrect(
            enabled = true,
            pose = RoadMatchPose(LAT, seedLon, 60f),
            speedKmh = 36f,
            nowElapsedMs = 1_500L,
            instrumentStepM = 12.0,
        )
        assertNotNull(duringTurn)
        val movedM = abs(eastM(seedLon, duringTurn!!.lon))
        assertTrue("mid-turn must not apply path-odo pull, moved $movedM m", movedM < 0.8)
    }

    @Test
    fun disabledTuningDoesNotPull() {
        installStraightEast()
        val rt = runtime()
        val seedLon = lonAtAlongM(40.0)
        assertNotNull(
            rt.maybeCorrect(
                true,
                RoadMatchPose(LAT, seedLon, 90f),
                speedKmh = 36f,
                nowElapsedMs = 1_000L,
            ),
        )
        val off = RoadMatchTuning.DEFAULT.withBool(RoadMatchTuningKey.PATH_ODO_SYNC_ENABLED, false)
        val stalled = rt.maybeCorrect(
            enabled = true,
            pose = RoadMatchPose(LAT, seedLon, 90f),
            speedKmh = 36f,
            nowElapsedMs = 1_500L,
            tuning = off,
            instrumentStepM = 12.0,
        )
        assertNotNull(stalled)
        val movedM = abs(eastM(seedLon, stalled!!.lon))
        assertTrue("disabled path-odo must not pull, moved $movedM m", movedM < 0.8)
    }

    @Test
    fun doesNotRewindWhenPoseIsAlreadyAhead() {
        installStraightEast()
        val rt = runtime()
        val seedLon = lonAtAlongM(40.0)
        assertNotNull(
            rt.maybeCorrect(
                true,
                RoadMatchPose(LAT, seedLon, 90f),
                speedKmh = 36f,
                nowElapsedMs = 1_000L,
            ),
        )
        val aheadLon = lonAtAlongM(55.0)
        val ahead = rt.maybeCorrect(
            enabled = true,
            pose = RoadMatchPose(LAT, aheadLon, 90f),
            speedKmh = 36f,
            nowElapsedMs = 1_500L,
            instrumentStepM = 2.0,
        )
        assertNotNull(ahead)
        val backM = eastM(ahead!!.lon, aheadLon)
        assertTrue("must not rewind toward a lagging topology cursor, $backM m", backM < 0.8)
    }

    @Test
    fun turnAtEdgeEndDoesNotSnapBackToVertex() {
        val east = RoadEdge(
            1L, "primary", 80.0, 1, 2,
            doubleArrayOf(37.60000, LAT, 37.60120, LAT),
        )
        val north = RoadEdge(
            2L, "primary", 80.0, 2, 3,
            doubleArrayOf(37.60120, LAT, 37.60120, LAT + 0.00080),
        )
        val graph = RoadGraph(
            "path-odo-corner", 4, doubleArrayOf(37.599, 55.749, 37.603, 55.752),
            listOf(east, north),
        )
        installSingleTileBundle(mapsDir, graph)
        val rt = runtime()
        assertNotNull(
            rt.maybeCorrect(
                true,
                RoadMatchPose(LAT, 37.60040, 90f),
                speedKmh = 36f,
                nowElapsedMs = 1_000L,
            ),
        )
        val pastLon = 37.60120 + 6.0 / mPerDegLon(LAT)
        val pastLat = LAT + 4.0 / 111_320.0
        val during = rt.maybeCorrect(
            true,
            RoadMatchPose(pastLat, pastLon, 50f),
            speedKmh = 36f,
            nowElapsedMs = 1_500L,
        )
        assertNotNull(during)
        val backToVertexM = RoadGraph.haversineM(during!!.lat, during.lon, LAT, 37.60120)
        val fromInputM = RoadGraph.haversineM(pastLat, pastLon, LAT, 37.60120)
        assertTrue(
            "turn must not yank pose back to the vertex ($backToVertexM vs $fromInputM)",
            backToVertexM + 0.4 >= fromInputM,
        )
    }

    private fun runtime() = RoadMatchRuntime(
        mapsDir = { mapsDir },
        matchLagM = 0.0,
        pathTriggerM = 0.1,
        timeTriggerMs = 1L,
        switchConfirmCount = 1,
        turnTriggerDeg = 18f,
    )

    private fun installStraightEast() {
        val edge = RoadEdge(
            1L, "primary", 1_000.0, 0, 1,
            doubleArrayOf(37.60, LAT, 37.62, LAT),
        )
        val graph = RoadGraph(
            "path-odo", 1, doubleArrayOf(37.59, 55.74, 37.63, 55.76),
            listOf(edge),
        )
        installSingleTileBundle(mapsDir, graph)
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
            """{"id":${e.id},"class":"${e.highwayClass}","lengthM":${e.lengthM},"from":${e.fromNode},"to":${e.toNode},"coords":[$coords]}"""
        }
        val json =
            """{"format":1,"regionId":"${graph.regionId}","graphVersion":${graph.graphVersion},"bbox":[${graph.bbox.joinToString(",")}],"edges":[$edgesJson]}"""
        val gz = java.io.ByteArrayOutputStream()
        java.util.zip.GZIPOutputStream(gz).use { it.write(json.toByteArray(Charsets.UTF_8)) }
        return RoadGraph.MAGIC.toByteArray(Charsets.US_ASCII) + gz.toByteArray()
    }

    private companion object {
        const val LAT = 55.75
        fun mPerDegLon(lat: Double): Double = 111_320.0 * cos(Math.toRadians(lat))
        fun lonAtAlongM(alongM: Double): Double = 37.60 + alongM / mPerDegLon(LAT)
        fun eastM(fromLon: Double, toLon: Double): Double =
            (toLon - fromLon) * mPerDegLon(LAT)
    }
}
