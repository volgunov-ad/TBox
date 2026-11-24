package vad.dashing.store.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vad.dashing.store.disk.CloudSource
import vad.dashing.store.disk.UpdateChannel

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StoreCatalogTest {

    @Test
    fun parse_ignoresRemovedLegacyFields() {
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
                  "reviewNotes": "n/a",
                  "copyApkToDownloadsAppId": "other",
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
        // verified:false is no longer skipped
        assertEquals(4, catalog.apps.size)

        val mqtt = catalog.apps.first { it.id == "mqtt" }
        assertEquals("/apps/mqtt/version.json", mqtt.resolveManifestLogicalPath())
        assertTrue(mqtt.supportsHu("a10"))
        assertNull(mqtt.downloads)
        assertTrue(mqtt.sections.isEmpty())

        val camd = catalog.apps.first { it.id == "camd" }
        assertTrue(camd.supportsHu("a9"))
        assertFalse(camd.supportsHu("a10"))

        val nav = catalog.apps.first { it.id == "nav" }
        assertEquals(listOf("abcdef"), nav.signingCertSha256)
        assertEquals("Vendor", nav.publisher)

        assertTrue(catalog.apps.any { it.id == "sketchy" })
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
            "/release/apps/mqtt/files/readme.txt",
            YandexDiskPath.appFilePath(UpdateChannel.RELEASE, "mqtt", "readme.txt"),
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
        assertEquals(7, catalog.apps.size)
        assertEquals(listOf("app", "tool"), catalog.sections.map { it.id })
        assertTrue(catalog.apps.any { it.id == "tbox-store" && it.packageName == "vad.dashing.store" })
        assertTrue(catalog.apps.first { it.id == "camd" }.supportsHu("a9"))
        assertFalse(catalog.apps.first { it.id == "camd" }.supportsHu("a10"))
        val dclInstall = catalog.apps.first { it.id == "dclinstall" }
        assertEquals(listOf("tool"), dclInstall.sections)
        assertTrue(dclInstall.hasDownloadFiles)
        assertEquals("DCL Launcher", dclInstall.downloads?.folder)
        val phone = catalog.apps.first { it.id == "phone" }
        assertEquals(StoreInstallMode.PHONE, phone.installMode)
        assertTrue(phone.isPhoneDistribution)
        assertEquals("vad.dashing.tbox.phone", phone.packageName)
    }

    @Test
    fun parse_installModePhone() {
        val json = """
            {
              "schemaVersion": 1,
              "apps": [
                {
                  "id": "phone",
                  "packageName": "vad.dashing.tbox.phone",
                  "title": "TBox Phone",
                  "verified": true,
                  "installMode": "phone"
                }
              ]
            }
        """.trimIndent()
        val entry = StoreCatalog.parse(json).apps.single()
        assertEquals(StoreInstallMode.PHONE, entry.installMode)
    }

    @Test
    fun parse_sections() {
        val json = """
            {
              "schemaVersion": 1,
              "sections": [
                {"id": "nav", "title": "Навигация", "titleEn": "Navigation"},
                {"id": "BAD", "title": "Bad id"},
                {"id": "empty", "title": ""},
                {"id": "nav", "title": "Duplicate"},
                {"id": "tools", "title": "Инструменты"}
              ],
              "apps": [
                {
                  "id": "maps",
                  "packageName": "com.example.maps",
                  "title": "Maps",
                  "sections": ["nav", "missing", "tools"]
                }
              ]
            }
        """.trimIndent()
        val catalog = StoreCatalog.parse(json)
        assertEquals(listOf("nav", "tools"), catalog.sections.map { it.id })
        assertEquals("Navigation", catalog.sections[0].displayTitle("en"))
        assertEquals("Навигация", catalog.sections[0].displayTitle("ru"))
        assertEquals(listOf("nav", "tools"), catalog.apps.single().sections)
    }

    @Test
    fun parse_downloads() {
        val goodSha = "a".repeat(64)
        val json = """
            {
              "schemaVersion": 1,
              "apps": [
                {
                  "id": "demo",
                  "packageName": "vad.dashing.demo",
                  "title": "Demo",
                  "downloads": {
                    "folder": " My Folder ",
                    "files": [
                      {"name": "ok.txt", "sha256": "$goodSha", "sizeBytes": 10},
                      {"name": "../x", "sha256": "$goodSha", "sizeBytes": 1},
                      {"name": "a/b", "sha256": "$goodSha", "sizeBytes": 1},
                      {"name": "", "sha256": "$goodSha", "sizeBytes": 1},
                      {"name": ".hidden", "sha256": "$goodSha", "sizeBytes": 1},
                      {"name": "x.part", "sha256": "$goodSha", "sizeBytes": 1},
                      {"name": "short", "sha256": "abcd", "sizeBytes": 1},
                      {"name": "OK.TXT", "sha256": "$goodSha", "sizeBytes": 2}
                    ]
                  }
                },
                {
                  "id": "blank-folder",
                  "packageName": "vad.dashing.blank",
                  "title": "Blank",
                  "downloads": {
                    "folder": "   ",
                    "files": [
                      {"name": "a.bin", "sha256": "$goodSha", "sizeBytes": 1}
                    ]
                  }
                }
              ]
            }
        """.trimIndent()
        val catalog = StoreCatalog.parse(json)
        val demo = catalog.apps.first { it.id == "demo" }
        assertEquals("My Folder", demo.downloads?.folder)
        assertEquals(listOf("ok.txt"), demo.downloads?.files?.map { it.name })
        val blank = catalog.apps.first { it.id == "blank-folder" }
        assertEquals("blank-folder", blank.downloads?.folder)
    }

    @Test
    fun resolveChangelogLogicalPath_defaultAndOverride() {
        val entry = StoreCatalogEntry(
            id = "mqtt",
            packageName = "vad.dashing.mqtt",
            title = "MQTT",
        )
        assertEquals("/apps/mqtt/changelog.ru.txt", entry.resolveChangelogLogicalPath("ru"))
        assertEquals("/apps/mqtt/changelog.en.txt", entry.resolveChangelogLogicalPath("en"))
        assertEquals(
            "/release/apps/mqtt/changelog.ru.txt",
            YandexDiskPath.appChangelogPath(UpdateChannel.RELEASE, "mqtt", "ru"),
        )
        val overridden = entry.copy(
            changelogEnUrl = "yandex-disk:/apps/mqtt/notes-en.txt",
        )
        assertEquals("/apps/mqtt/notes-en.txt", overridden.resolveChangelogLogicalPath("en"))
        assertEquals("/apps/mqtt/changelog.ru.txt", overridden.resolveChangelogLogicalPath("ru"))
    }

    @Test
    fun publicShareUrl_apkBrowseUrl() {
        val yandex = "https://disk.yandex.ru/d/KDaHLxbFtPAjYA"
        val mail = "https://cloud.mail.ru/public/voi3/4Lx1RhqV9"
        assertEquals(
            "$yandex/release/apps/phone/tbox-phone-0.1.0.apk",
            StorePublicShareUrl.apkBrowseUrl(
                yandexPublicKey = yandex,
                mailRuPublicUrl = mail,
                cloudSource = CloudSource.AUTO,
                channel = UpdateChannel.RELEASE,
                appId = "phone",
                apkFileName = "tbox-phone-0.1.0.apk",
            ),
        )
        assertEquals(
            "$mail/dev/apps/phone/tbox-phone.apk",
            StorePublicShareUrl.apkBrowseUrl(
                yandexPublicKey = yandex,
                mailRuPublicUrl = mail,
                cloudSource = CloudSource.MAILRU,
                channel = UpdateChannel.DEVELOPMENT,
                appId = "phone",
                apkFileName = "tbox-phone.apk",
            ),
        )
    }
}
