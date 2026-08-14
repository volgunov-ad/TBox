package vad.dashing.tbox.location.roadmatch

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import vad.dashing.tbox.location.GeoBearingSource
import vad.dashing.tbox.location.GeoDisplayState
import vad.dashing.tbox.location.GeoSpeedSource

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RoadMatchControllerTest {

    private lateinit var mapsDir: File

    @Before
    fun setUp() {
        RoadGraphStore.clear()
        RoadMatchAnchorRepository.clear()
        RoadMatchRuntimeDebug.clear()
        mapsDir = createTempDir(prefix = "roads-shared-")
        installSingleTileBundle(mapsDir, horizontalEdge())
    }

    @After
    fun tearDown() {
        RoadGraphStore.clear()
        RoadMatchAnchorRepository.clear()
        RoadMatchRuntimeDebug.clear()
        mapsDir.deleteRecursively()
    }

    @Test
    fun poseFromDisplay_requiresFiniteCoordsAndBearing() {
        assertNull(RoadMatchController.poseFromDisplay(GeoDisplayState.EMPTY))
        assertNull(
            RoadMatchController.poseFromDisplay(
                GeoDisplayState(
                    latitude = 55.75,
                    longitude = 37.61,
                    bearingDeg = null,
                ),
            ),
        )
        val pose = RoadMatchController.poseFromDisplay(
            GeoDisplayState(
                latitude = 55.75,
                longitude = 37.61,
                speedKmh = 40f,
                speedSource = GeoSpeedSource.GNSS,
                bearingDeg = 90f,
                bearingSource = GeoBearingSource.GNSS,
                hasReliableBearing = true,
            ),
        )
        assertNotNull(pose)
        assertEquals(55.75, pose!!.lat, 1e-9)
        assertEquals(37.61, pose.lon, 1e-9)
        assertEquals(90f, pose.bearingDeg, 0.01f)
    }

    @Test
    fun widgetOnlyTick_publishesAnchorWithoutRequiringPoseApply() {
        val controller = RoadMatchController { mapsDir }
        val widgetOnly = RoadMatchDemand(matchNeeded = true, correctPose = false)
        val pose = RoadMatchPose(55.75005, 37.61, 90f)
        val matched = controller.tick(
            demand = widgetOnly,
            pose = pose,
            speedKmh = 40f,
            nowElapsedMs = 1_000L,
        )
        assertNotNull(matched)
        assertNotSame(pose, matched)
        val anchor = RoadMatchAnchorRepository.state.value
        assertTrue(anchor.matchNeeded)
        assertFalse(anchor.correctPose)
        assertEquals(1L, anchor.edgeId)
        assertEquals("test", anchor.regionId)
    }

    @Test
    fun sameControllerKeepsAnchorWhenCorrectPoseTurnsOn() {
        val controller = RoadMatchController { mapsDir }
        val pose = RoadMatchPose(55.75005, 37.61, 90f)
        controller.tick(
            demand = RoadMatchDemand(matchNeeded = true, correctPose = false),
            pose = pose,
            speedKmh = 40f,
            nowElapsedMs = 1_000L,
        )
        val firstEdge = RoadMatchAnchorRepository.state.value.edgeId
        assertEquals(1L, firstEdge)

        val matched = controller.tick(
            demand = RoadMatchDemand(matchNeeded = true, correctPose = true),
            pose = pose,
            speedKmh = 40f,
            nowElapsedMs = 3_500L,
        )
        assertNotNull(matched)
        val anchor = RoadMatchAnchorRepository.state.value
        assertTrue(anchor.correctPose)
        assertEquals(firstEdge, anchor.edgeId)
        assertEquals(firstEdge, controller.runtime.debug.edgeId)
    }

    @Test
    fun noneResetsPublishedAnchor() {
        val controller = RoadMatchController { mapsDir }
        controller.tick(
            demand = RoadMatchDemand(matchNeeded = true, correctPose = false),
            pose = RoadMatchPose(55.75005, 37.61, 90f),
            speedKmh = 40f,
            nowElapsedMs = 1_000L,
        )
        assertEquals(1L, RoadMatchAnchorRepository.state.value.edgeId)

        val matched = controller.tick(
            demand = RoadMatchDemand.NONE,
            pose = RoadMatchPose(55.75005, 37.61, 90f),
            speedKmh = 40f,
            nowElapsedMs = 2_000L,
        )
        assertNull(matched)
        assertEquals(RoadMatchAnchorState.EMPTY, RoadMatchAnchorRepository.state.value)
        assertNull(controller.runtime.debug.edgeId)
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
            regionId = "test",
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
            """{"id":${e.id},"class":"${e.highwayClass}","lengthM":${e.lengthM},"from":${e.fromNode},"to":${e.toNode},"coords":[$coords]}"""
        }
        val json =
            """{"format":1,"regionId":"${graph.regionId}","graphVersion":${graph.graphVersion},"bbox":[${graph.bbox.joinToString(",")}],"edges":[$edgesJson]}"""
        val gz = java.io.ByteArrayOutputStream()
        java.util.zip.GZIPOutputStream(gz).use { it.write(json.toByteArray(Charsets.UTF_8)) }
        return RoadGraph.MAGIC.toByteArray(Charsets.US_ASCII) + gz.toByteArray()
    }
}
