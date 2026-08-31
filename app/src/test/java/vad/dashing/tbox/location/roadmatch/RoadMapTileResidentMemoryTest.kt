package vad.dashing.tbox.location.roadmatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Investigation tests: v4 tiling must keep only covering tiles in [RoadGraphStore].
 * Production code is not changed; these pin the resident-tile / eviction contract
 * that prevented oblast-scale OOM.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RoadMapTileResidentMemoryTest {

    @Before
    fun clearStore() {
        RoadGraphStore.clear()
    }

    @Test
    fun coveringOverlapLoadsAtMostFourTilesAndEvictsDistant() {
        val mapsDir = createTempDir(prefix = "road-resident-")
        installGridBundle(
            mapsDir = mapsDir,
            regionId = "ru-test",
            // 3×3 tiles of 0.1° with 0.01° overlap → corners cover 4 tiles.
            west = 37.0,
            south = 55.0,
            tileDeg = 0.1,
            overlapDeg = 0.01,
            grid = 3,
        )

        val runtime = RoadMatchRuntime(mapsDir = { mapsDir })

        // Interior of a single tile.
        val one = runtime.warmGraphsAt(55.05, 37.05)
        assertEquals(1, one.size)
        assertEquals(1, RoadGraphStore.cachedGraphs().size)

        // On a shared corner (overlap of four tiles).
        val four = runtime.warmGraphsAt(55.10, 37.10)
        assertEquals(4, four.size)
        assertEquals(4, RoadGraphStore.cachedGraphs().size)

        // Move far into another tile — previous set must be evicted.
        val moved = runtime.warmGraphsAt(55.25, 37.25)
        assertEquals(1, moved.size)
        assertEquals(1, RoadGraphStore.cachedGraphs().size)
        assertTrue(RoadGraphStore.cachedGraphs().single().contains(55.25, 37.25))
    }

    @Test
    fun twoInstalledRegionsStackCoveringTilesWithoutLoadingWholePacks() {
        val mapsDir = createTempDir(prefix = "road-multi-")
        installGridBundle(
            mapsDir = mapsDir,
            regionId = "ru-a",
            west = 37.0,
            south = 55.0,
            tileDeg = 0.1,
            overlapDeg = 0.01,
            grid = 2,
        )
        installGridBundle(
            mapsDir = mapsDir,
            regionId = "ru-b",
            west = 37.0,
            south = 55.0,
            tileDeg = 0.1,
            overlapDeg = 0.01,
            grid = 2,
        )

        val runtime = RoadMatchRuntime(mapsDir = { mapsDir })
        val graphs = runtime.warmGraphsAt(55.05, 37.05)
        // One covering tile per region.
        assertEquals(2, graphs.size)
        assertEquals(2, RoadGraphStore.cachedGraphs().size)
        assertEquals(setOf("ru-a", "ru-b"), graphs.map { it.regionId }.toSet())
    }

    @Test
    fun denseSyntheticTileStaysUnderHeapBudgetWhenAdjacencyBuilt() {
        // Dense city-like tile: many short edges. Should stay well under a 32 MB budget
        // on the unit-test JVM (HU heap is tighter, but this guards gross regressions).
        val mapsDir = createTempDir(prefix = "road-dense-")
        val install = File(mapsDir, "ru-dense${RoadMapBundle.INSTALL_SUFFIX}")
        File(install, "tiles").mkdirs()
        val edges = ArrayList<String>(8_000)
        var id = 1L
        // ~8000 edges × 4 points ≈ city-centre density above Moscow's densest published tile.
        for (i in 0 until 100) {
            for (j in 0 until 80) {
                val lon0 = 37.6 + i * 0.001
                val lat0 = 55.75 + j * 0.001
                edges.add(
                    """{"id":$id,"class":"residential","lengthM":80.0,"from":$id,"to":${id + 1},""" +
                        """"coords":[[$lon0,$lat0],[${lon0 + 0.0004},$lat0],""" +
                        """[${lon0 + 0.0004},${lat0 + 0.0004}],[$lon0,${lat0 + 0.0004}]]}""",
                )
                id++
            }
        }
        val tileBytes = packJson(
            """{"format":1,"regionId":"ru-dense","graphVersion":4,""" +
                """"bbox":[37.55,55.70,37.75,55.90],"edges":[${edges.joinToString(",")}]}""",
        )
        File(install, "tiles/0000_0000.tboxroads").writeBytes(tileBytes)
        File(install, RoadMapBundle.INDEX_FILE).writeText(
            """
            {
              "format":1,
              "regionId":"ru-dense",
              "graphVersion":4,
              "bbox":[37.55,55.70,37.75,55.90],
              "tiles":[
                {"id":"0000_0000","file":"tiles/0000_0000.tboxroads",
                 "bbox":[37.55,55.70,37.75,55.90],"bytes":${tileBytes.size},"edgeCount":${edges.size}}
              ]
            }
            """.trimIndent(),
        )

        Runtime.getRuntime().gc()
        val before = usedHeapBytes()
        val runtime = RoadMatchRuntime(mapsDir = { mapsDir })
        val graphs = runtime.warmGraphsAt(55.75, 37.62)
        assertEquals(1, graphs.size)
        // Force adjacency + edgeById (lazy) like matcher / lookahead do.
        val g = graphs.single()
        assertTrue(g.edgeById.isNotEmpty())
        assertTrue(g.neighbors(1L).isNotEmpty() || g.edges.size > 1)
        g.edgesNear(55.75, 37.62, radiusM = 250.0)
        Runtime.getRuntime().gc()
        val delta = usedHeapBytes() - before
        // 8000 edges should be far below the old monolith OOM (~whole oblast as one String).
        assertTrue(
            "dense tile heap delta ${delta / (1024 * 1024)} MB exceeds 32 MB budget",
            delta < 32L * 1024L * 1024L,
        )
        assertEquals(1, RoadGraphStore.cachedGraphs().size)
    }

    @Test
    fun sakhaSizedIndexParsesUnderConfiguredCap() {
        // Published ru-sakha index is ~2.0 MB / 12k tiles — under MAX_INDEX_BYTES (4 MB).
        val tiles = ArrayList<String>(12_500)
        for (x in 0 until 125) {
            for (y in 0 until 100) {
                val id = "%04d_%04d".format(x, y)
                val w = 110.0 + x * 0.1
                val s = 55.0 + y * 0.1
                tiles.add(
                    """{"id":"$id","file":"tiles/$id.tboxroads","bbox":[$w,$s,${w + 0.11},${s + 0.11}],"bytes":1000}""",
                )
            }
        }
        val json =
            """{"format":1,"regionId":"ru-sakha","graphVersion":4,"bbox":[110.0,55.0,122.5,65.0],"tiles":[${tiles.joinToString(",")}]}"""
        assertTrue(json.length < 4 * 1024 * 1024)
        val index = RoadMapBundle.parseIndex(json)
        assertEquals(12_500, index.tiles.size)
        assertEquals(1, index.covering(55.05, 110.05).size)
        assertEquals(4, index.covering(55.10, 110.10).size)
    }

    private fun usedHeapBytes(): Long {
        val rt = Runtime.getRuntime()
        return rt.totalMemory() - rt.freeMemory()
    }

    private fun installGridBundle(
        mapsDir: File,
        regionId: String,
        west: Double,
        south: Double,
        tileDeg: Double,
        overlapDeg: Double,
        grid: Int,
    ) {
        val install = File(mapsDir, "$regionId${RoadMapBundle.INSTALL_SUFFIX}")
        File(install, "tiles").mkdirs()
        val east = west + grid * tileDeg
        val north = south + grid * tileDeg
        val tileJson = ArrayList<String>()
        var edgeId = 1L
        for (x in 0 until grid) {
            for (y in 0 until grid) {
                val coreW = west + x * tileDeg
                val coreS = south + y * tileDeg
                val coreE = coreW + tileDeg
                val coreN = coreS + tileDeg
                val bboxW = coreW - overlapDeg
                val bboxS = coreS - overlapDeg
                val bboxE = coreE + overlapDeg
                val bboxN = coreN + overlapDeg
                val id = "%04d_%04d".format(x, y)
                val lon = (coreW + coreE) / 2.0
                val lat0 = (coreS + coreN) / 2.0 - 0.01
                val lat1 = lat0 + 0.02
                val pack = packJson(
                    """{"format":1,"regionId":"$regionId","graphVersion":4,""" +
                        """"bbox":[$bboxW,$bboxS,$bboxE,$bboxN],""" +
                        """"edges":[{"id":$edgeId,"class":"primary","lengthM":100.0,"from":0,"to":1,""" +
                        """"coords":[[$lon,$lat0],[$lon,$lat1]]}]}""",
                )
                edgeId++
                File(install, "tiles/$id.tboxroads").writeBytes(pack)
                tileJson.add(
                    """{"id":"$id","file":"tiles/$id.tboxroads","bbox":[$bboxW,$bboxS,$bboxE,$bboxN],"bytes":${pack.size}}""",
                )
            }
        }
        File(install, RoadMapBundle.INDEX_FILE).writeText(
            """{"format":1,"regionId":"$regionId","graphVersion":4,"bbox":[$west,$south,$east,$north],"tiles":[${tileJson.joinToString(",")}]}""",
        )
    }

    private fun packJson(json: String): ByteArray {
        val gz = ByteArrayOutputStream()
        GZIPOutputStream(gz).use { it.write(json.toByteArray(Charsets.UTF_8)) }
        return RoadGraph.MAGIC.toByteArray(Charsets.US_ASCII) + gz.toByteArray()
    }

    /** Keep Zip helper available for future fixture installs without changing production. */
    @Suppress("unused")
    private fun writeZip(file: File, entries: Map<String, ByteArray>) {
        ZipOutputStream(file.outputStream()).use { out ->
            for ((name, bytes) in entries) {
                out.putNextEntry(ZipEntry(name))
                out.write(bytes)
                out.closeEntry()
            }
        }
    }
}
