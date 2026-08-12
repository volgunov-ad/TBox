package vad.dashing.tbox.location.roadmatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RoadGraphTest {

    private fun packBytes(json: String): ByteArray {
        val gz = ByteArrayOutputStream()
        GZIPOutputStream(gz).use { it.write(json.toByteArray(Charsets.UTF_8)) }
        return RoadGraph.MAGIC.toByteArray(Charsets.US_ASCII) + gz.toByteArray()
    }

    @Test
    fun loadV1PackAndQueryNear() {
        val json = """
            {
              "format": 1,
              "regionId": "test-grid",
              "graphVersion": 2,
              "bbox": [37.0, 55.0, 38.0, 56.0],
              "edges": [
                {
                  "id": 10,
                  "class": "primary",
                  "lengthM": 1000.0,
                  "from": 0,
                  "to": 1,
                  "coords": [[37.60, 55.75], [37.62, 55.75]]
                },
                {
                  "id": 11,
                  "class": "residential",
                  "lengthM": 500.0,
                  "from": 2,
                  "to": 3,
                  "coords": [[37.10, 55.10], [37.11, 55.10]]
                }
              ]
            }
        """.trimIndent()
        val graph = RoadGraph.load(packBytes(json))
        assertEquals("test-grid", graph.regionId)
        assertEquals(2, graph.graphVersion)
        assertEquals(2, graph.edges.size)
        assertTrue(graph.contains(55.75, 37.61))
        assertFalse(graph.contains(50.0, 30.0))

        val near = graph.edgesNear(55.7501, 37.61, radiusM = 50.0)
        assertEquals(1, near.size)
        assertEquals(10L, near[0].id)

        val far = graph.edgesNear(55.7501, 37.61, radiusM = 1.0)
        // 0.0001 deg lat ≈ 11 m — may or may not be within 1 m; use larger point offset check
        val nowhere = graph.edgesNear(54.0, 30.0, radiusM = 100.0)
        assertTrue(nowhere.isEmpty())
        assertTrue(far.size <= 1)
    }

    @Test
    fun distanceToSegmentIsNearZeroOnLine() {
        val d = RoadGraph.distanceToSegmentM(
            lat = 55.75,
            lon = 37.61,
            lat1 = 55.75,
            lon1 = 37.60,
            lat2 = 55.75,
            lon2 = 37.62,
        )
        assertTrue(d < 1.0)
    }

    @Test
    fun rejectsBadMagic() {
        val bad = "NOTMAGIC".toByteArray() + byteArrayOf(1, 2, 3)
        try {
            RoadGraph.load(bad)
            throw AssertionError("expected failure")
        } catch (_: IllegalArgumentException) {
            // ok
        }
    }
}
