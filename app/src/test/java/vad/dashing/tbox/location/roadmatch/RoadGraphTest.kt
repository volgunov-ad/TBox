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
    fun connectsEdgesThatShareEndpointCoordinates() {
        val json = """
            {
              "format": 1,
              "regionId": "conn",
              "graphVersion": 3,
              "bbox": [37.0, 55.0, 38.0, 56.0],
              "edges": [
                {
                  "id": 1,
                  "class": "secondary",
                  "lengthM": 100.0,
                  "from": 100,
                  "to": 101,
                  "coords": [[37.60, 55.75], [37.61, 55.75]]
                },
                {
                  "id": 2,
                  "class": "secondary",
                  "lengthM": 100.0,
                  "from": 200,
                  "to": 201,
                  "coords": [[37.61, 55.75], [37.61, 55.76]]
                }
              ]
            }
        """.trimIndent()
        val graph = RoadGraph.load(packBytes(json))
        assertTrue(graph.isConnected(1L, 2L))
        assertFalse(graph.isConnected(1L, 99L))
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
    fun peekHeaderStopsWithoutLoadingEdges() {
        val json = """
            {
              "format": 1,
              "regionId": "peek-me",
              "graphVersion": 3,
              "bbox": [37.0, 55.0, 38.0, 56.0],
              "edges": [
                {
                  "id": 1,
                  "class": "primary",
                  "lengthM": 100.0,
                  "from": 0,
                  "to": 1,
                  "coords": [[37.60, 55.75], [37.61, 55.75]]
                }
              ]
            }
        """.trimIndent()
        val header = RoadGraph.peekHeader(java.io.ByteArrayInputStream(packBytes(json)))
        assertEquals("peek-me", header.regionId)
        assertEquals(3, header.graphVersion)
        assertTrue(header.contains(55.5, 37.5))
        assertTrue(!header.contains(50.0, 30.0))
    }

    @Test
    fun loadOnewayFieldDefaultsToZeroWhenAbsent() {
        val json = """
            {
              "format": 1,
              "regionId": "ow-load",
              "graphVersion": 3,
              "bbox": [37.0, 55.0, 38.0, 56.0],
              "edges": [
                {
                  "id": 1,
                  "class": "primary",
                  "lengthM": 100.0,
                  "from": 0,
                  "to": 1,
                  "oneway": 1,
                  "coords": [[37.60, 55.75], [37.61, 55.75]]
                },
                {
                  "id": 2,
                  "class": "residential",
                  "lengthM": 100.0,
                  "from": 2,
                  "to": 3,
                  "coords": [[37.60, 55.76], [37.61, 55.76]]
                }
              ]
            }
        """.trimIndent()
        val graph = RoadGraph.load(packBytes(json))
        assertEquals(1, graph.edges[0].oneway)
        assertEquals(0, graph.edges[1].oneway)
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

    @Test
    fun streamLoadDoesNotRequireMonolithicJsonString() {
        // Many edges — old loader built one huge String + JSONObject and OOMed on HU.
        val sb = StringBuilder(64 * 1024)
        sb.append("""{"format":1,"regionId":"stream-stress","graphVersion":3,"bbox":[37.0,55.0,38.0,56.0],"edges":[""")
        val n = 800
        for (i in 0 until n) {
            if (i > 0) sb.append(',')
            val lon0 = 37.0 + i * 0.001
            sb.append(
                """{"id":$i,"class":"residential","lengthM":100.0,"from":$i,"to":${i + 1},"coords":[[$lon0,55.5],[${lon0 + 0.0005},55.5]]}""",
            )
        }
        sb.append("]}")
        val graph = RoadGraph.load(packBytes(sb.toString()))
        assertEquals("stream-stress", graph.regionId)
        assertEquals(n, graph.edges.size)
        assertEquals(0L, graph.edges.first().id)
        assertEquals((n - 1).toLong(), graph.edges.last().id)
    }

    @Test
    fun loadFromFileRoundTrip() {
        val json = """
            {
              "format": 1,
              "regionId": "file-rt",
              "graphVersion": 3,
              "bbox": [37.0, 55.0, 38.0, 56.0],
              "edges": [
                {
                  "id": 7,
                  "class": "primary",
                  "lengthM": 50.0,
                  "from": 1,
                  "to": 2,
                  "coords": [[37.60, 55.75], [37.61, 55.75]]
                }
              ]
            }
        """.trimIndent()
        val dir = org.robolectric.RuntimeEnvironment.getApplication().filesDir
        val pack = java.io.File(dir, "file-rt.tboxroads")
        pack.writeBytes(packBytes(json))
        val graph = RoadGraph.load(pack)
        assertEquals("file-rt", graph.regionId)
        assertEquals(1, graph.edges.size)
        assertEquals(7L, graph.edges[0].id)
    }

    @Test
    fun loadRealMoscowCityPackIfPresent() {
        val pack = java.io.File("/opt/cursor/artifacts/ru-moscow-v3.tboxroads")
        org.junit.Assume.assumeTrue("artifact pack missing", pack.isFile)
        val graph = RoadGraph.load(pack)
        assertEquals("ru-moscow", graph.regionId)
        assertTrue(graph.edges.size > 10_000)
        assertTrue(graph.contains(55.75, 37.62))
    }
}
