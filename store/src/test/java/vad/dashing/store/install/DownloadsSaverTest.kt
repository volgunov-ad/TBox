package vad.dashing.store.install

import android.Manifest
import android.app.Application
import android.os.Build
import android.os.Environment
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaScannerConnection
import vad.dashing.store.StoreErrors

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DownloadsSaverTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun downloadsDirectoryMatchesMonitorLegacyLayoutOnApi28() {
        @Suppress("DEPRECATION")
        val expected = File(Environment.getExternalStorageDirectory(), "Download")
        assertEquals(expected.absolutePath, DownloadsSaver.downloadsDirectory().absolutePath)
    }

    @Test
    fun needsWritePermissionOnApi28() {
        assertTrue(DownloadsSaver.needsWritePermission())
        assertEquals(Build.VERSION_CODES.P, Build.VERSION.SDK_INT)
    }

    @Test
    fun saveCopiesIntoDownloadsAndRequestsMediaScan() {
        shadowOf(context).grantPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        val source = File(context.cacheDir, "source.apk").apply {
            writeBytes(ByteArray(64 * 1024) { it.toByte() })
        }
        val saved = runBlocking { DownloadsSaver.save(context, source, "app 1.0") }
        assertEquals(File(DownloadsSaver.downloadsDirectory(), "app_1.0.apk"), saved)
        assertArrayEquals(source.readBytes(), saved.readBytes())
        assertTrue(saved.absolutePath in ShadowMediaScannerConnection.getSavedPaths())
    }

    @Test
    fun saveWithoutWritePermissionFailsWithLocalizedKey() {
        shadowOf(context).denyPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        val source = File(context.cacheDir, "source.apk").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val error = assertThrows(IOException::class.java) {
            runBlocking { DownloadsSaver.save(context, source, "app.apk") }
        }
        assertEquals(StoreErrors.DOWNLOADS_PERMISSION, error.message)
    }

    @Test
    fun saveFileKeepsCyrillicAndSpacesAndFindsExisting() {
        shadowOf(context).grantPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        val source = File(context.cacheDir, "src.bin").apply { writeBytes(byteArrayOf(9, 8, 7)) }
        val saved = runBlocking {
            DownloadsSaver.saveFile(context, source, "Моя папка", "файл с пробелом.bin")
        }
        val expected = File(
            File(DownloadsSaver.downloadsDirectory(), "Моя папка"),
            "файл с пробелом.bin",
        )
        assertEquals(expected.absolutePath, saved.absolutePath)
        assertArrayEquals(source.readBytes(), saved.readBytes())
        assertTrue(saved.absolutePath in ShadowMediaScannerConnection.getSavedPaths())
        assertNotNull(DownloadsSaver.existingFile("Моя папка", "файл с пробелом.bin"))

        // overwrite
        source.writeBytes(byteArrayOf(1, 2))
        val again = runBlocking {
            DownloadsSaver.saveFile(context, source, "Моя папка", "файл с пробелом.bin")
        }
        assertArrayEquals(byteArrayOf(1, 2), again.readBytes())
    }

    @Test
    fun saveFileReplacesForbiddenCharacters() {
        shadowOf(context).grantPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        val source = File(context.cacheDir, "src.bin").apply { writeBytes(byteArrayOf(1)) }
        val saved = runBlocking {
            DownloadsSaver.saveFile(context, source, "a:b/c", "x*y?.bin")
        }
        assertTrue(saved.parentFile!!.name.contains("_"))
        assertTrue(saved.name.contains("_"))
        assertFalseSlash(saved.absolutePath)
    }

    private fun assertFalseSlash(path: String) {
        // folder/file segments themselves must not retain raw forbidden chars as path separators
        val relative = path.removePrefix(DownloadsSaver.downloadsDirectory().absolutePath)
        assertTrue(!relative.contains(":"))
        assertTrue(!relative.contains("*"))
        assertTrue(!relative.contains("?"))
    }
}
