package vad.dashing.tbox.location.roadmatch

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Rails mode: lock + topology advance + free breakaway; Ordinary path unchanged.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RoadMatchRailsModeTest {

    private lateinit var mapsDir: File

    @Before
    fun setUp() {
        RoadGraphStore.clear()
        mapsDir = createTempDir(prefix = "roads-rails-")
        installSingleTileBundle(mapsDir, horizontalEdge())
    }

    @After
    fun tearDown() {
        RoadGraphStore.clear()
        mapsDir.deleteRecursively()
    }

    @Test
    fun railsFirstLock_snapsOntoEdge() {
        val runtime = RoadMatchRuntime(mapsDir = { mapsDir }, matchLagM = 0.0)
        val pose = RoadMatchPose(55.75005, 37.61, 90f)
        val out = runtime.maybeCorrect(
            enabled = true,
            pose = pose,
            speedKmh = 40f,
            nowElapsedMs = 1_000L,
            mode = RoadMatchMode.RAILS,
        )
        assertNotNull(out)
        assertEquals(1L, runtime.debug.edgeId)
        assertEquals(RoadMatchMode.RAILS.name, runtime.debug.matchMode)
        assertTrue((out!!.lat - 55.75) < 0.0002)
        assertEquals(90f, out.bearingDeg, 1f)
    }

    @Test
    fun railsAdvance_staysOnGraphWhileFreeDiverges() {
        val runtime = RoadMatchRuntime(mapsDir = { mapsDir }, matchLagM = 0.0)
        var free = RoadMatchPose(55.75002, 37.6100, 90f)
        val lock = runtime.maybeCorrect(
            enabled = true,
            pose = free,
            speedKmh = 36f,
            nowElapsedMs = 1_000L,
            mode = RoadMatchMode.RAILS,
        )
        assertNotNull(lock)
        assertEquals(1L, runtime.debug.edgeId)

        var now = 1_000L
        // Drive east along the road (instrument ≈ rail).
        repeat(6) {
            val dest = RoadMatchLeashMath.destination(free.lat, free.lon, 90f, 8.0)
            free = RoadMatchPose(dest.first, dest.second, 90f)
            now += 500L
            val rail = runtime.maybeCorrect(
                enabled = true,
                pose = free,
                speedKmh = 36f,
                nowElapsedMs = now,
                mode = RoadMatchMode.RAILS,
            )
            assertNotNull("expected rail hold/advance", rail)
            assertEquals(1L, runtime.debug.edgeId)
            assertTrue(
                "rail lat should stay on east-west edge",
                kotlin.math.abs(rail!!.lat - 55.75) < 0.0003,
            )
        }
    }

    @Test
    fun railsBreak_whenFreeLeavesCorridor() {
        val runtime = RoadMatchRuntime(mapsDir = { mapsDir }, matchLagM = 0.0)
        var free = RoadMatchPose(55.75002, 37.61, 90f)
        assertNotNull(
            runtime.maybeCorrect(
                enabled = true,
                pose = free,
                speedKmh = 36f,
                nowElapsedMs = 1_000L,
                mode = RoadMatchMode.RAILS,
            ),
        )

        var now = 1_000L
        var broke = false
        // Turn north into a "yard" — retain stays free (caller must not snap).
        repeat(20) {
            val dest = RoadMatchLeashMath.destination(free.lat, free.lon, 0f, 5.0)
            free = RoadMatchPose(dest.first, dest.second, 0f)
            now += 500L
            val out = runtime.maybeCorrect(
                enabled = true,
                pose = free,
                speedKmh = 36f,
                nowElapsedMs = now,
                mode = RoadMatchMode.RAILS,
            )
            if (runtime.debug.leash == "break" ||
                runtime.debug.rejectReason == "rails_break" ||
                runtime.debug.skippedReason == "rails_break"
            ) {
                broke = true
                assertNotNull(out)
                assertEquals(free.lat, out!!.lat, 1e-9)
                assertEquals(free.lon, out.lon, 1e-9)
                assertNull(runtime.debug.edgeId)
            }
        }
        assertTrue("expected rails_break after driving north off the edge", broke)
    }

    @Test
    fun modeSwitch_resetsStickyState() {
        val runtime = RoadMatchRuntime(mapsDir = { mapsDir }, matchLagM = 0.0)
        val pose = RoadMatchPose(55.75005, 37.61, 90f)
        assertNotNull(
            runtime.maybeCorrect(
                enabled = true,
                pose = pose,
                speedKmh = 40f,
                nowElapsedMs = 1_000L,
                mode = RoadMatchMode.RAILS,
            ),
        )
        assertEquals(1L, runtime.debug.edgeId)

        // Ordinary after Rails must not inherit the rails sticky edge blindly —
        // reset clears; next ordinary tick re-locks from scratch.
        val ordinary = runtime.maybeCorrect(
            enabled = true,
            pose = pose,
            speedKmh = 40f,
            nowElapsedMs = 2_000L,
            mode = RoadMatchMode.ORDINARY,
        )
        assertNotNull(ordinary)
        assertEquals(RoadMatchMode.ORDINARY.name, runtime.debug.matchMode)
        assertEquals(1L, runtime.debug.edgeId)
    }

    @Test
    fun ordinaryDefault_stillSoftCorrects() {
        val runtime = RoadMatchRuntime(mapsDir = { mapsDir }, matchLagM = 0.0)
        val pose = RoadMatchPose(55.75008, 37.61, 90f)
        val out = runtime.maybeCorrect(
            enabled = true,
            pose = pose,
            speedKmh = 40f,
            nowElapsedMs = 1_000L,
            // default mode = ORDINARY
        )
        assertNotNull(out)
        assertEquals(RoadMatchMode.ORDINARY.name, runtime.debug.matchMode)
        assertNotEquals(pose.lat, out!!.lat, 1e-12)
        assertTrue(kotlin.math.abs(out.lat - 55.75) < kotlin.math.abs(pose.lat - 55.75))
    }

    private fun horizontalEdge(): RoadGraph {
        val edge = RoadEdge(
            id = 1L,
            highwayClass = "primary",
            lengthM = 1_000.0,
            fromNode = 0,
            toNode = 1,
            coords = doubleArrayOf(37.60, 55.75, 37.62, 55.75),
        )
        return RoadGraph(
            regionId = "rails",
            graphVersion = 1,
            bbox = doubleArrayOf(37.59, 55.74, 37.63, 55.76),
            edges = listOf(edge),
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
