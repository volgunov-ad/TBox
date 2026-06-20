package vad.dashing.tbox.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class UpdateManifestTest {

    @Test
    fun parse_validManifest_selectsFlavorRelease() {
        val json = """
            {
              "schemaVersion": 1,
              "releases": [
                {
                  "versionCode": 1601,
                  "versionName": "0.16.1",
                  "flavor": "ru",
                  "apkFileName": "tbox-ru.apk",
                  "sha256": "abc123",
                  "changelog": "Fix"
                }
              ]
            }
        """.trimIndent()
        val manifest = UpdateManifest.parse(json)
        val release = manifest.releaseFor("ru")
        assertNotNull(release)
        assertEquals(1601L, release!!.versionCode)
        assertEquals("0.16.1", release.versionName)
        assertEquals("tbox-ru.apk", release.apkFileName)
    }

    @Test
    fun isUpdateNewer_comparesVersionCode() {
        assertTrue(isUpdateNewer(remoteVersionCode = 1601, currentVersionCode = 1600))
        assertFalse(isUpdateNewer(remoteVersionCode = 1600, currentVersionCode = 1600))
        assertFalse(isUpdateNewer(remoteVersionCode = 1599, currentVersionCode = 1600))
    }

    @Test
    fun updateChannel_fromStorageValue_defaultsToRelease() {
        assertEquals(UpdateChannel.RELEASE, UpdateChannel.fromStorageValue(null))
        assertEquals(UpdateChannel.DEVELOPMENT, UpdateChannel.fromStorageValue("development"))
    }
}
