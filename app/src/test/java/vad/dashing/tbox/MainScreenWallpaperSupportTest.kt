package vad.dashing.tbox

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MainScreenWallpaperSupportTest {

    @Test
    fun localFileFromEmbeddedPath_stripsRootPrefix() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        val sub = File(ctx.cacheDir, "light_test").apply {
            deleteRecursively()
            mkdirs()
        }
        val img = File(sub, "a.jpg").apply { writeText("x") }
        val fakePath = "/root" + img.absolutePath
        val uri = Uri.Builder()
            .scheme("content")
            .authority("com.example.fileprovider")
            .path(fakePath)
            .build()
        val resolved = localFileFromEmbeddedStoragePath(uri)
        assertNotNull(resolved)
        assertEquals(img.absolutePath, resolved!!.absolutePath)
    }

    @Test
    fun resolveWallpaperSource_fileUri_usesParentFolder() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        val dir = File(ctx.cacheDir, "wp_test_dir").apply {
            deleteRecursively()
            mkdirs()
        }
        val img = File(dir, "hero.jpg").apply { writeText("x") }
        val uri = Uri.fromFile(img)
        val res = runBlocking { resolveWallpaperSourceFromPickedImageUri(ctx, uri) }
        assertEquals(Uri.fromFile(dir).toString(), res?.folderUriString)
        assertEquals("hero.jpg", res?.selectedFileName)
    }

    @Test
    fun effectiveWallpaperFileName_prefersSavedWhenPresent() {
        val names = listOf("a.png", "b.png", "c.png")
        assertEquals("b.png", effectiveWallpaperFileName(names, "b.png"))
    }

    @Test
    fun effectiveWallpaperFileName_fallsBackToFirstWhenMissing() {
        val names = listOf("a.png", "b.png")
        assertEquals("a.png", effectiveWallpaperFileName(names, "gone.png"))
    }

    @Test
    fun effectiveWallpaperFileName_emptyList() {
        assertNull(effectiveWallpaperFileName(emptyList(), "x.png"))
    }

    @Test
    fun isWallpaperFileOverSizeLimit_fileUri_trueWhenHuge() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        val f = File(ctx.cacheDir, "wallpaper_oversize_test.bin")
        java.io.RandomAccessFile(f, "rw").use { it.setLength(MAIN_SCREEN_WALLPAPER_MAX_FILE_BYTES + 1) }
        assertTrue(isWallpaperFileOverSizeLimit(ctx, Uri.fromFile(f)))
        f.delete()
    }

    @Test
    fun listSortedImageFilesInDir_skipsOversizeFile() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        val dir = File(ctx.cacheDir, "wp_size_filter").apply {
            deleteRecursively()
            mkdirs()
        }
        File(dir, "ok.png").writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        val huge = File(dir, "huge.png")
        java.io.RandomAccessFile(huge, "rw").use { it.setLength(MAIN_SCREEN_WALLPAPER_MAX_FILE_BYTES + 1) }
        val listed = runBlocking { listSortedWallpaperImagesInFolder(ctx, Uri.fromFile(dir)) }
        assertEquals(1, listed.size)
        assertEquals("ok.png", listed.first().first)
        dir.deleteRecursively()
    }
}
