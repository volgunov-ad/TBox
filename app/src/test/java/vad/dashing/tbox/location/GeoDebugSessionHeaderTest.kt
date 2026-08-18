package vad.dashing.tbox.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GeoDebugSessionHeaderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun missingDirIsDash() {
        assertEquals("-", GeoDebugSessionHeader.installedMapsLabel(null))
        assertEquals("-", GeoDebugSessionHeader.installedMapsLabel(tmp.newFolder("empty")))
    }

    @Test
    fun listsInstalledBundlesWithGraphVersion() {
        val maps = tmp.newFolder("road_maps")
        val moscow = java.io.File(maps, "ru-moscow.tboxroads.d").also { it.mkdirs() }
        java.io.File(moscow, "index.json").writeText(
            """
            {
              "format": 1,
              "regionId": "ru-moscow",
              "graphVersion": 4,
              "bbox": [36.0, 55.0, 38.0, 56.0],
              "tiles": [
                {
                  "id": "t0",
                  "file": "tiles/t0.tboxroads",
                  "bbox": [36.0, 55.0, 38.0, 56.0],
                  "bytes": 10
                }
              ]
            }
            """.trimIndent(),
        )
        java.io.File(maps, "ru-yaroslavl.tboxroads.d").mkdirs()
        val label = GeoDebugSessionHeader.installedMapsLabel(maps)
        assertEquals("ru-moscow@4,ru-yaroslavl", label)
    }

    @Test
    fun commentLinesIncludeAppVerMapsAndPeriod() {
        val text = GeoDebugSessionHeader.commentLines(
            "0.18.0",
            "ru-moscow@4",
            500L,
            500L,
            maxFileBytes = 20L * 1024L * 1024L,
            part = 2,
            continuedFrom = "tbox_geo_debug_20260816_135245.txt",
        )
        assertTrue(text.contains("# appVer=0.18.0"))
        assertTrue(text.contains("# maps=ru-moscow@4"))
        assertTrue(text.contains("# matchPeriodMs=500"))
        assertTrue(text.contains("# logPeriodMs=500"))
        assertTrue(text.contains("# maxFileBytes=20971520"))
        assertTrue(text.contains("# part=2"))
        assertTrue(text.contains("# continuedFrom=tbox_geo_debug_20260816_135245.txt"))
    }

    @Test
    fun firstPartOmitsContinuedFrom() {
        val text = GeoDebugSessionHeader.commentLines(
            "0.18.0",
            "-",
            500L,
            500L,
            maxFileBytes = GeoDebugLogRecorder.MAX_FILE_BYTES,
        )
        assertTrue(text.contains("# part=1"))
        assertTrue(text.contains("# maxFileBytes=20971520"))
        assertTrue(!text.contains("continuedFrom"))
    }
}
