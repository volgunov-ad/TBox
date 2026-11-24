package vad.dashing.store.install

import android.os.Build
import android.os.Environment
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DownloadsApkSaverTest {
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
}
