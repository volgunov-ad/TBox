package vad.dashing.store.disk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppReleaseManifestTest {

    @Test
    fun parse_prefersDefaultFlavorThenFirst() {
        val json = """
            {
              "schemaVersion": 1,
              "releases": [
                {
                  "versionCode": 10,
                  "versionName": "0.1.0",
                  "flavor": "ru",
                  "apkFileName": "mqtt-ru.apk",
                  "sha256": "aaa"
                },
                {
                  "versionCode": 11,
                  "versionName": "0.1.1",
                  "flavor": "default",
                  "apkFileName": "mqtt.apk",
                  "sha256": "bbb",
                  "apkSizeBytes": 1234,
                  "changelog": "fix"
                }
              ]
            }
        """.trimIndent()
        val manifest = AppReleaseManifest.parse(json)
        val release = manifest.preferredRelease()
        assertEquals(11L, release?.versionCode)
        assertEquals("mqtt.apk", release?.apkFileName)
        assertEquals(1234L, release?.apkSizeBytes)
        assertEquals("fix", release?.changelog)
    }

    @Test
    fun parse_blankFlavorBecomesDefault() {
        val json = """
            {
              "schemaVersion": 1,
              "releases": [{
                "versionCode": 5,
                "versionName": "0.0.5",
                "apkFileName": "x.apk",
                "sha256": "ccc"
              }]
            }
        """.trimIndent()
        val release = AppReleaseManifest.parse(json).preferredRelease()
        assertEquals("default", release?.flavor)
        assertEquals(5L, release?.versionCode)
    }

    @Test
    fun isReleaseNewer_comparesVersionCode() {
        assertTrue(isReleaseNewer(2, 1))
        assertFalse(isReleaseNewer(1, 1))
        assertFalse(isReleaseNewer(1, 2))
    }

    @Test
    fun preferredRelease_picksRequestedLanguageFlavor() {
        val json = """
            {
              "schemaVersion": 1,
              "releases": [
                {
                  "versionCode": 10,
                  "versionName": "1.0.0-ru",
                  "flavor": "ru",
                  "apkFileName": "app-ru.apk",
                  "sha256": "aaa"
                },
                {
                  "versionCode": 10,
                  "versionName": "1.0.0-en",
                  "flavor": "en",
                  "apkFileName": "app-en.apk",
                  "sha256": "bbb"
                }
              ]
            }
        """.trimIndent()
        val manifest = AppReleaseManifest.parse(json)
        assertTrue(manifest.hasLanguageChoice())
        assertEquals(listOf("ru", "en"), manifest.languageFlavors())
        assertEquals("app-en.apk", manifest.preferredRelease("en")?.apkFileName)
        assertEquals("app-ru.apk", manifest.preferredRelease("ru")?.apkFileName)
    }
}
