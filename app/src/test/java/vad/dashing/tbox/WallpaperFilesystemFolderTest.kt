package vad.dashing.tbox

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class WallpaperFilesystemFolderTest {

    @Test
    fun normalizeFilesystemWallpaperFolderPath_acceptsAbsolutePath() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        val dir = File(ctx.cacheDir, "wallpaper_norm").apply {
            deleteRecursively()
            mkdirs()
        }
        val uri = normalizeFilesystemWallpaperFolderPath(dir.absolutePath)
        assertEquals(Uri.fromFile(dir).toString(), uri)
    }

    @Test
    fun normalizeFilesystemWallpaperFolderPath_rejectsMissing() {
        assertNull(normalizeFilesystemWallpaperFolderPath("/no/such/folder/12345"))
    }

    @Test
    fun displayWallpaperFolderSummary_fileUri_showsPath() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        val dir = File(ctx.cacheDir, "wallpaper_summary_display").apply {
            deleteRecursively()
            mkdirs()
        }
        // `Uri.fromFile().toString()` can be percent-encoded on some SDKs/Robolectric; Java URI round-trips reliably.
        val summary = displayWallpaperFolderSummary(dir.toURI().toString())
        assertEquals(dir.canonicalFile.absolutePath, summary)
    }
}
