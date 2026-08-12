package vad.dashing.tbox.location.roadmatch

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RoadMapBundledPacksTest {

    @Test
    fun bundledCatalogContainsOnlyWholeRussiaAndBelarusRegions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val json = context.assets.open("road_maps/catalog.json").bufferedReader().use { it.readText() }
        val catalog = RoadMapCatalog.parse(json)
        assertTrue(catalog.version >= 3)
        assertEquals(96, catalog.regions.size)
        assertEquals(setOf("RU", "BY"), catalog.regions.map { it.country }.toSet())
        val ids = catalog.regions.map { it.id }.toSet()
        assertTrue("ru-nizhny-novgorod" in ids)
        assertTrue("ru-moscow" in ids)
        assertTrue("ru-dnr" in ids)
        assertTrue("ru-lnr" in ids)
        assertTrue("ru-crimea" in ids)
        assertTrue("by-brest" in ids)
        assertTrue("by-minsk-region" in ids)

        // Fallback catalog never exposes links that may not exist. The app replaces
        // it with /maps/catalog.json from the public Yandex Disk release share.
        assertTrue(catalog.regions.none { it.hasDownloadUrl })
        assertFalse(ids.any { it.startsWith("kz-") || it.startsWith("am-") || it.startsWith("az-") || it.startsWith("uz-") })
    }

    @Test
    fun yandexMapPathsAreStrictlyInsideMapsFolder() {
        assertEquals(
            "/maps/ru-nizhny-novgorod-v3.tboxroads",
            RoadMapRemoteUrl.yandexPathOrNull(
                "yandex-disk:/maps/ru-nizhny-novgorod-v3.tboxroads",
            ),
        )
        assertEquals(null, RoadMapRemoteUrl.yandexPathOrNull("https://example.test/map"))
        assertEquals(null, RoadMapRemoteUrl.yandexPathOrNull("yandex-disk:/release/app.apk"))
        assertEquals(null, RoadMapRemoteUrl.yandexPathOrNull("yandex-disk:/maps/../version.json"))
    }
}
