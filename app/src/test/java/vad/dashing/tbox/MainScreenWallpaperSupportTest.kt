package vad.dashing.tbox

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
