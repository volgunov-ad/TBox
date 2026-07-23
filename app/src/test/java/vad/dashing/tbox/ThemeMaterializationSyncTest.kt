package vad.dashing.tbox

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ThemeMaterializationSyncTest {

    @Test
    fun materializeFromBytes_syncPreservesApplyTargetsFromManifest() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val cacheKey = "sync_apply_targets"
        val bytes = themeZip(
            themeJson = """
                {
                  "formatVersion": 1,
                  "type": "tbox_theme",
                  "sections": ["mainScreen"],
                  "mainScreen": {
                    "wallpaperSelectionByPage": { "light": {}, "dark": {} }
                  }
                }
            """.trimIndent(),
        )
        val sourceUri = "file:///test/sync_apply_targets.tboxtheme"
        val requestedTargets = setOf(ThemeApplyTarget.MAIN_SCREEN_WALLPAPERS)

        ThemeMaterialization.materializeFromBytes(
            context = context,
            bytes = bytes,
            cacheKey = cacheKey,
            sourceUri = sourceUri,
            syncExisting = false,
            applyTargets = requestedTargets,
        ).getOrThrow()

        val firstManifest = ThemeMaterialization.readManifest(context, cacheKey)
        assertEquals(requestedTargets, firstManifest?.applyTargets)

        ThemeMaterialization.materializeFromBytes(
            context = context,
            bytes = bytes,
            cacheKey = cacheKey,
            sourceUri = sourceUri,
            syncExisting = true,
            applyTargets = null,
        ).getOrThrow()

        val secondManifest = ThemeMaterialization.readManifest(context, cacheKey)
        assertEquals(requestedTargets, secondManifest?.applyTargets)
        assertTrue(ThemeMaterialization.isMaterialized(context, cacheKey))
    }

    private fun themeZip(themeJson: String): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("theme.json"))
            zos.write(themeJson.toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("assets/wallpaper/light/a.jpg"))
            zos.write(byteArrayOf(1, 2, 3))
            zos.closeEntry()
        }
        return baos.toByteArray()
    }
}
