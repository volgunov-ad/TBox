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
class DownloadsApkSaverTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun downloadsDirectoryMatchesMonitorLegacyLayoutOnApi28() {
        @Suppress("DEPRECATION")
        val expected = File(Environment.getExternalStorageDirectory(), "Download")
        assertEquals(expected.absolutePath, DownloadsApkSaver.downloadsDirectory().absolutePath)
    }

    @Test
    fun needsWritePermissionOnApi28() {
        assertTrue(DownloadsApkSaver.needsWritePermission())
        assertEquals(Build.VERSION_CODES.P, Build.VERSION.SDK_INT)
    }

    @Test
    fun saveCopiesIntoDownloadsAndRequestsMediaScan() {
        shadowOf(context).grantPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        val source = File(context.cacheDir, "source.apk").apply {
            writeBytes(ByteArray(64 * 1024) { it.toByte() })
        }
        val saved = runBlocking { DownloadsApkSaver.save(context, source, "app 1.0") }
        assertEquals(File(DownloadsApkSaver.downloadsDirectory(), "app_1.0.apk"), saved)
        assertArrayEquals(source.readBytes(), saved.readBytes())
        assertTrue(saved.absolutePath in ShadowMediaScannerConnection.getSavedPaths())
    }

    @Test
    fun saveWithoutWritePermissionFailsWithLocalizedKey() {
        shadowOf(context).denyPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        val source = File(context.cacheDir, "source.apk").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val error = assertThrows(IOException::class.java) {
            runBlocking { DownloadsApkSaver.save(context, source, "app.apk") }
        }
        assertEquals(StoreErrors.DOWNLOADS_PERMISSION, error.message)
    }
}
