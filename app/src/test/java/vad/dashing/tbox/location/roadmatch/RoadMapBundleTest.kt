package vad.dashing.tbox.location.roadmatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RoadMapBundleTest {

    @Test
    fun extractsOneRegionBundleAndSelectsCoveringTile() {
        val root = createTempDir(prefix = "road-bundle-")
        val zip = File(root, "region.tboxroads.zip")
        val index = """
            {
              "format": 1,
              "regionId": "ru-test",
              "graphVersion": 4,
              "bbox": [37.0, 55.0, 38.0, 56.0],
              "tiles": [
                {"id":"a","file":"tiles/a.tboxroads","bbox":[37.0,55.0,37.6,56.0],"bytes":100},
                {"id":"b","file":"tiles/b.tboxroads","bbox":[37.4,55.0,38.0,56.0],"bytes":100}
              ]
            }
        """.trimIndent()
        ZipOutputStream(zip.outputStream()).use { out ->
            fun entry(name: String, bytes: ByteArray) {
                out.putNextEntry(ZipEntry(name))
                out.write(bytes)
                out.closeEntry()
            }
            entry(RoadMapBundle.INDEX_FILE, index.toByteArray())
            entry("tiles/a.tboxroads", pack("ru-test", 4, 1L, 37.2))
            entry("tiles/b.tboxroads", pack("ru-test", 4, 2L, 37.8))
        }

        assertTrue(RoadMapBundle.isBundle(zip))
        val install = File(root, "ru-test${RoadMapBundle.INSTALL_SUFFIX}.part")
        val parsed = RoadMapBundle.extractAndValidate(zip, install, "ru-test")
        assertEquals(4, parsed.graphVersion)
        assertEquals(2, parsed.tiles.size)
        assertEquals(listOf("a"), parsed.covering(55.5, 37.2).map { it.id })
        assertEquals(setOf("a", "b"), parsed.covering(55.5, 37.5).map { it.id }.toSet())
        assertTrue(File(install, "tiles/a.tboxroads").isFile)
        assertFalse(parsed.contains(54.0, 37.5))
    }

    @Test
    fun rejectsZipTraversal() {
        val root = createTempDir(prefix = "road-bundle-unsafe-")
        val zip = File(root, "unsafe.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("../escape"))
            out.write(byteArrayOf(1))
            out.closeEntry()
        }
        val result = runCatching {
            RoadMapBundle.extractAndValidate(zip, File(root, "stage"), "ru-test")
        }
        assertTrue(result.isFailure)
        assertFalse(File(root.parentFile, "escape").exists())
    }

    private fun pack(regionId: String, version: Int, edgeId: Long, lon: Double): ByteArray {
        val json =
            """{"format":1,"regionId":"$regionId","graphVersion":$version,"bbox":[${lon - 0.2},55.0,${lon + 0.2},56.0],"edges":[{"id":$edgeId,"class":"primary","lengthM":100.0,"from":0,"to":1,"coords":[[$lon,55.4],[$lon,55.6]]}]}"""
        val gz = ByteArrayOutputStream()
        GZIPOutputStream(gz).use { it.write(json.toByteArray()) }
        return RoadGraph.MAGIC.toByteArray(Charsets.US_ASCII) + gz.toByteArray()
    }
}
