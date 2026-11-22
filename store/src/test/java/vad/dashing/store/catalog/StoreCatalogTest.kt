package vad.dashing.store.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vad.dashing.store.disk.UpdateChannel

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StoreCatalogTest {

    @Test
    fun parse_skipsUnverifiedAndKeepsThirdParty() {
        val json = """
            {
              "schemaVersion": 1,
              "publishedAt": "2026-11-21T12:00:00Z",
              "apps": [
                {
                  "id": "mqtt",
                  "packageName": "vad.dashing.mqtt",
                  "title": "TBox MQTT",
                  "summary": "MQTT bridge",
                  "publisher": "TBox",
                  "origin": "first-party",
                  "hu": ["a9", "a10"],
                  "category": "app",
                  "verified": true,
                  "manifest": "yandex-disk:/apps/mqtt/version.json"
                },
                {
                  "id": "camd",
                  "packageName": "vad.dashing.camd",
                  "title": "Camd",
                  "hu": ["a9"],
                  "verified": true
                },
                {
                  "id": "sketchy",
                  "packageName": "com.example.bad",
                  "title": "Bad",
                  "verified": false
                },
                {
                  "id": "nav",
                  "packageName": "com.example.nav",
                  "title": "Nav",
                  "origin": "third-party",
                  "publisher": "Vendor",
                  "verified": true,
                  "signingCertSha256": ["AbCdEf"],
                  "reviewNotes": "ok"
                }
              ]
            }
        """.trimIndent()

        val catalog = StoreCatalog.parse(json)
        assertEquals(1, catalog.schemaVersion)
        assertEquals("2026-11-21T12:00:00Z", catalog.publishedAt)
        assertEquals(3, catalog.apps.size)

        val mqtt = catalog.apps.first { it.id == "mqtt" }
        assertEquals(StoreAppOrigin.FIRST_PARTY, mqtt.origin)
        assertEquals("/apps/mqtt/version.json", mqtt.resolveManifestLogicalPath())
        assertTrue(mqtt.supportsHu("a10"))

        val camd = catalog.apps.first { it.id == "camd" }
        assertTrue(camd.supportsHu("a9"))
        assertFalse(camd.supportsHu("a10"))
        assertEquals("/apps/camd/version.json", camd.resolveManifestLogicalPath())

        val nav = catalog.apps.first { it.id == "nav" }
        assertEquals(StoreAppOrigin.THIRD_PARTY, nav.origin)
        assertEquals(listOf("abcdef"), nav.signingCertSha256)
        assertEquals("Vendor", nav.publisher)
    }

    @Test
    fun iconPath_onlyWhenExplicit() {
        val withIcon = StoreCatalogEntry(
            id = "mqtt",
            packageName = "vad.dashing.mqtt",
            title = "MQTT",
            iconUrl = "yandex-disk:/apps/mqtt/icon.png",
        )
        assertEquals("/apps/mqtt/icon.png", withIcon.resolveIconLogicalPath())
        assertTrue(withIcon.hasExplicitIcon())

        val without = StoreCatalogEntry(
            id = "mqtt",
            packageName = "vad.dashing.mqtt",
            title = "MQTT",
        )
        assertEquals(null, without.resolveIconLogicalPath())
        assertFalse(without.hasExplicitIcon())
    }

    @Test
    fun yandexDiskPath_prefixesChannelRoot() {
        assertEquals(
            "/release/apps/catalog.json",
            YandexDiskPath.catalogPath(UpdateChannel.RELEASE),
        )
        assertEquals(
            "/dev/apps/catalog.json",
            YandexDiskPath.catalogPath(UpdateChannel.DEVELOPMENT),
        )
        assertEquals(
            "/release/apps/mqtt/app.apk",
            YandexDiskPath.appApkPath(UpdateChannel.RELEASE, "mqtt", "app.apk"),
        )
        assertEquals(
            "/release/apps/mqtt/version.json",
            YandexDiskPath.physicalPath(
                UpdateChannel.RELEASE,
                "yandex-disk:/apps/mqtt/version.json",
            ),
        )
        assertEquals(
            "/dev/apps/mqtt/version.json",
            YandexDiskPath.physicalPath(
                UpdateChannel.DEVELOPMENT,
                "/apps/mqtt/version.json",
            ),
        )
        assertEquals(
            "/release/apps/mqtt/version.json",
            YandexDiskPath.physicalPath(
                UpdateChannel.DEVELOPMENT,
                "/release/apps/mqtt/version.json",
            ),
        )
    }

    @Test
    fun parse_sampleCatalogResource() {
        val json = javaClass.classLoader!!
            .getResourceAsStream("sample_apps_catalog.json")!!
            .bufferedReader()
            .readText()
        val catalog = StoreCatalog.parse(json)
        assertEquals(6, catalog.apps.size)
        assertTrue(catalog.apps.any { it.id == "tbox-store" && it.packageName == "vad.dashing.store" })
        assertTrue(catalog.apps.any { it.id == "dcl-launcher" && it.origin == StoreAppOrigin.THIRD_PARTY })
        assertTrue(catalog.apps.first { it.id == "camd" }.supportsHu("a9"))
        assertFalse(catalog.apps.first { it.id == "camd" }.supportsHu("a10"))
        val dclInstall = catalog.apps.first { it.id == "dclinstall" }
        assertEquals("dcl-launcher", dclInstall.copyApkToDownloadsAppId)
        assertEquals(StoreAppCategory.TOOL, dclInstall.category)
    }

    @Test
    fun parse_copyApkToDownloadsAppId() {
        val json = """
            {
              "schemaVersion": 1,
              "apps": [
                {
                  "id": "dclinstall",
                  "packageName": "vad.dashing.dclinstall",
                  "title": "DCL Install",
                  "verified": true,
                  "copyApkToDownloadsAppId": "dcl-launcher"
                }
              ]
            }
        """.trimIndent()
        val entry = StoreCatalog.parse(json).apps.single()
        assertEquals("dcl-launcher", entry.copyApkToDownloadsAppId)
    }
}
