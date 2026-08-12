package vad.dashing.tbox.location.roadmatch

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RoadMapBundledPacksTest {

    @Test
    fun bundledCatalogHasPilotRegionsAndLoadablePacks() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val json = context.assets.open("road_maps/catalog.json").bufferedReader().use { it.readText() }
        val catalog = RoadMapCatalog.parse(json)
        assertTrue(catalog.version >= 2)
        val ids = catalog.regions.map { it.id }.toSet()
        assertTrue("ru-nizhny" in ids)
        assertTrue("ru-moscow" in ids)
        assertTrue("ru-dnr" in ids)

        val withUrl = catalog.regions.filter { it.hasDownloadUrl }
        assertTrue(withUrl.size >= 8)
        for (region in withUrl) {
            assertTrue(region.url.startsWith("asset://"))
            val path = region.url.removePrefix("asset://")
            val bytes = context.assets.open(path).use { it.readBytes() }
            val graph = RoadGraph.load(bytes)
            assertEquals(region.id, graph.regionId)
            assertTrue(graph.edges.isNotEmpty())
            assertTrue(graph.contains((graph.bbox[1] + graph.bbox[3]) / 2, (graph.bbox[0] + graph.bbox[2]) / 2))
        }
    }
}
