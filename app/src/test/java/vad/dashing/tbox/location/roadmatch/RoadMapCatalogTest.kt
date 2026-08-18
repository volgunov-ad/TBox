package vad.dashing.tbox.location.roadmatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vad.dashing.tbox.location.MockCanSpeedMode
import vad.dashing.tbox.location.MockPowerState

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RoadMapCatalogTest {

    private val sampleJson = """
        {
          "version": 1,
          "regions": [
            {
              "id": "ru-moscow",
              "country": "RU",
              "title_ru": "Москва",
              "title_en": "Moscow",
              "bbox": [37.2, 55.5, 37.9, 56.0],
              "url": "asset://road_maps/stubs/ru-moscow-demo.tboxroads",
              "bytes": 64,
              "graphVersion": 1
            },
            {
              "id": "ru-dnr",
              "country": "ru",
              "title_ru": "ДНР",
              "title_en": "DNR",
              "bbox": [37.2, 47.0, 39.2, 48.7],
              "url": "",
              "bytes": 0,
              "graphVersion": 1
            }
          ]
        }
    """.trimIndent()

    @Test
    fun parseCatalogAndCoverage() {
        val cat = RoadMapCatalog.parse(sampleJson)
        assertEquals(1, cat.version)
        assertEquals(2, cat.regions.size)
        assertTrue(cat.findById("ru-moscow")!!.hasDownloadUrl)
        assertFalse(cat.findById("ru-dnr")!!.hasDownloadUrl)
        assertTrue(cat.findById("ru-moscow")!!.contains(55.75, 37.6))
        assertFalse(cat.findById("ru-moscow")!!.contains(59.9, 30.3))
        assertEquals(
            listOf("ru-dnr", "ru-moscow"),
            cat.regionsByCountry(isRussian = true)["RU"]!!.map { it.id },
        )
    }

    @Test
    fun regionsAreSortedByLocalizedTitle() {
        fun region(id: String, ru: String, en: String) = RoadMapRegion(
            id = id,
            country = "RU",
            titleRu = ru,
            titleEn = en,
            bbox = doubleArrayOf(0.0, 0.0, 0.0, 0.0),
            url = "",
            bytes = 0L,
            graphVersion = 1,
        )
        val cat = RoadMapCatalog(
            version = 1,
            regions = listOf(
                region("z-id", "Ярославская область", "Altai Region"),
                region("a-id", "Алтайский край", "Yaroslavl Region"),
                region("m-id", "Ёлкинская область", "Moscow Region"),
            ),
        )

        assertEquals(
            listOf("a-id", "m-id", "z-id"),
            cat.regionsByCountry(isRussian = true)["RU"]!!.map { it.id },
        )
        assertEquals(
            listOf("z-id", "m-id", "a-id"),
            cat.regionsByCountry(isRussian = false)["RU"]!!.map { it.id },
        )
    }

    @Test
    fun installManifestRoundTrip() {
        val entries = mapOf(
            "ru-moscow" to RoadMapInstallEntry(
                id = "ru-moscow",
                graphVersion = 1,
                fileName = "ru-moscow.tboxroads",
                bytesOnDisk = 64,
                installedAtEpochMs = 1000L,
            ),
        )
        val json = RoadMapInstallManifest.toJson(entries)
        val back = RoadMapInstallManifest.parse(json)
        assertEquals(1, back.size)
        assertEquals(64L, back["ru-moscow"]!!.bytesOnDisk)
        assertTrue(RoadMapInstallManifest.parse(null).isEmpty())
        assertTrue(RoadMapInstallManifest.parse("").isEmpty())
    }

    @Test
    fun roadMatchToggleAvailability() {
        assertFalse(
            RoadMatchAvailability.isToggleEnabled(MockPowerState.OFF, MockCanSpeedMode.CONSTANT),
        )
        assertTrue(
            RoadMatchAvailability.isToggleEnabled(MockPowerState.WHEN_NO_FIX, MockCanSpeedMode.NONE),
        )
        assertFalse(
            RoadMatchAvailability.isToggleEnabled(MockPowerState.ALWAYS_ON, MockCanSpeedMode.NONE),
        )
        assertTrue(
            RoadMatchAvailability.isToggleEnabled(
                MockPowerState.ALWAYS_ON,
                MockCanSpeedMode.WHEN_FIX_LOST,
            ),
        )
    }
}
