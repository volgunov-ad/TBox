package vad.dashing.tbox.location.roadmatch

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RoadMatchFreeTurnsModeTest {

    private lateinit var mapsDir: File

    @Before
    fun setUp() {
        RoadGraphStore.clear()
        mapsDir = createTempDir(prefix = "roads-free-turns-")
    }

    @After
    fun tearDown() {
        RoadGraphStore.clear()
        mapsDir.deleteRecursively()
    }

    @Test
    fun freeTurns_unbinds30mBeforeFourWay_andRebinds10mAfter() {
        val graph = RoadMatchFreeTurnsMathTest.fourWayGraph()
        installSingleTileBundle(mapsDir, graph)
        val runtime = runtime()
        val west = graph.edgeById[1L]!!
        val start = RoadMapMatcher.poseOnEdge(graph.regionId, west, 50.0, false)!!
        var pose = RoadMatchPose(start.lat, start.lon, 90f)
        var now = 1_000L
        val lock = runtime.maybeCorrect(
            enabled = true,
            pose = pose,
            speedKmh = 36f,
            nowElapsedMs = now,
            mode = RoadMatchMode.FREE_TURNS,
        )
        assertNotNull(lock)
        assertEquals(1L, runtime.debug.edgeId)
        assertEquals(RoadMatchMode.FREE_TURNS.name, runtime.debug.matchMode)

        var released = false
        var pathAfterRelease = 0.0
        repeat(20) {
            val dest = RoadMatchLeashMath.destination(pose.lat, pose.lon, 90f, 8.0)
            val prev = pose
            pose = RoadMatchPose(dest.first, dest.second, 90f)
            now += 500L
            val out = runtime.maybeCorrect(
                enabled = true,
                pose = pose,
                speedKmh = 36f,
                nowElapsedMs = now,
                mode = RoadMatchMode.FREE_TURNS,
            )
            if (!released && runtime.debug.skippedReason ==
                RoadMatchRuntime.FREE_TURNS_JUNCTION_SKIP
            ) {
                released = true
                val toNode = RoadGraph.haversineM(
                    pose.lat,
                    pose.lon,
                    RoadMatchFreeTurnsMathTest.CENTER_LAT,
                    RoadMatchFreeTurnsMathTest.CENTER_LON,
                )
                assertTrue("unbind should start within ~30 m of the node, was $toNode", toNode <= 32.0)
                assertNull(out)
                assertNull(runtime.debug.edgeId)
            } else if (released) {
                pathAfterRelease += RoadGraph.haversineM(prev.lat, prev.lon, pose.lat, pose.lon)
                if (pathAfterRelease < 10.0) {
                    assertEquals(
                        RoadMatchRuntime.FREE_TURNS_JUNCTION_SKIP,
                        runtime.debug.skippedReason,
                    )
                    assertNull(out)
                }
            }
        }
        assertTrue("expected a free-turns unbind before the 4-way", released)
        assertTrue("expected to travel past the rebind distance", pathAfterRelease >= 10.0)
        assertTrue(
            "should rematch after 10 m past the node",
            runtime.debug.active || runtime.debug.edgeId != null,
        )
        assertTrue(runtime.debug.skippedReason != RoadMatchRuntime.FREE_TURNS_JUNCTION_SKIP)
    }

    @Test
    fun freeTurns_unbindsAtTJunction() {
        val graph = RoadMatchFreeTurnsMathTest.tJunctionGraph()
        installSingleTileBundle(mapsDir, graph)
        val runtime = runtime()
        val west = graph.edgeById[1L]!!
        val start = RoadMapMatcher.poseOnEdge(graph.regionId, west, 50.0, false)!!
        var pose = RoadMatchPose(start.lat, start.lon, 90f)
        var now = 1_000L
        assertNotNull(
            runtime.maybeCorrect(
                enabled = true,
                pose = pose,
                speedKmh = 36f,
                nowElapsedMs = now,
                mode = RoadMatchMode.FREE_TURNS,
            ),
        )
        var released = false
        repeat(12) {
            val dest = RoadMatchLeashMath.destination(pose.lat, pose.lon, 90f, 8.0)
            pose = RoadMatchPose(dest.first, dest.second, 90f)
            now += 500L
            runtime.maybeCorrect(
                enabled = true,
                pose = pose,
                speedKmh = 36f,
                nowElapsedMs = now,
                mode = RoadMatchMode.FREE_TURNS,
            )
            if (runtime.debug.skippedReason == RoadMatchRuntime.FREE_TURNS_JUNCTION_SKIP) {
                released = true
            }
        }
        assertTrue("T-junction / fork should unbind within 30 m", released)
    }

    @Test
    fun freeTurns_doesNotUnbindOnSimpleContinuation() {
        val graph = RoadMatchFreeTurnsMathTest.throughRoadGraph()
        installSingleTileBundle(mapsDir, graph)
        val runtime = runtime()
        val west = graph.edgeById[1L]!!
        val start = RoadMapMatcher.poseOnEdge(graph.regionId, west, 50.0, false)!!
        var pose = RoadMatchPose(start.lat, start.lon, 90f)
        var now = 1_000L
        assertNotNull(
            runtime.maybeCorrect(
                enabled = true,
                pose = pose,
                speedKmh = 36f,
                nowElapsedMs = now,
                mode = RoadMatchMode.FREE_TURNS,
            ),
        )
        repeat(12) {
            val dest = RoadMatchLeashMath.destination(pose.lat, pose.lon, 90f, 8.0)
            pose = RoadMatchPose(dest.first, dest.second, 90f)
            now += 500L
            runtime.maybeCorrect(
                enabled = true,
                pose = pose,
                speedKmh = 36f,
                nowElapsedMs = now,
                mode = RoadMatchMode.FREE_TURNS,
            )
            assertTrue(
                "2-edge pack split must not unbind, edge=${runtime.debug.edgeId} skip=${runtime.debug.skippedReason}",
                runtime.debug.skippedReason != RoadMatchRuntime.FREE_TURNS_JUNCTION_SKIP,
            )
        }
    }

    @Test
    fun freeTurns_headingCatchUpIsStrongerThanOrdinary() {
        val graph = RoadMatchFreeTurnsMathTest.fourWayGraph()
        installSingleTileBundle(mapsDir, graph)
        val west = graph.edgeById[1L]!!
        val onRoad = RoadMapMatcher.poseOnEdge(graph.regionId, west, 40.0, false)!!
        val skewed = RoadMatchPose(onRoad.lat, onRoad.lon, 50f)

        val ordinaryRt = runtime()
        val freeRt = runtime()
        val ordinary = ordinaryRt.maybeCorrect(
            enabled = true,
            pose = skewed,
            speedKmh = 36f,
            nowElapsedMs = 1_000L,
            mode = RoadMatchMode.ORDINARY,
        )!!
        val free = freeRt.maybeCorrect(
            enabled = true,
            pose = skewed,
            speedKmh = 36f,
            nowElapsedMs = 1_000L,
            mode = RoadMatchMode.FREE_TURNS,
        )!!
        val ordinaryPull = RoadMapMatcher.smallestAngleDeg(50f, ordinary.bearingDeg)
        val freePull = RoadMapMatcher.smallestAngleDeg(50f, free.bearingDeg)
        assertTrue("ordinary pull $ordinaryPull free $freePull", freePull > ordinaryPull + 5f)
        assertTrue(abs(free.bearingDeg - 90f) < abs(ordinary.bearingDeg - 90f))
    }

    private fun runtime() = RoadMatchRuntime(
        mapsDir = { mapsDir },
        matchLagM = 0.0,
        pathTriggerM = 0.1,
        timeTriggerMs = 1L,
        turnTriggerDeg = 90f,
    )

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
}
