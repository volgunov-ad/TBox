package vad.dashing.tbox.update

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class UpdateDownloadCacheTest {

    @Test
    fun clearUpdateDownloadCache_removesApkAndPartialFiles() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apkFile = File(updatesDir, "tbox-ru.apk").apply { writeText("apk") }
        val partialFile = File(updatesDir, "tbox-ru.apk.part").apply { writeText("part") }

        clearUpdateDownloadCache(context)

        assertFalse(apkFile.exists())
        assertFalse(partialFile.exists())
        assertTrue(updatesDir.exists())
    }

    @Test
    fun clearUpdateDownloadCache_isNoOpWhenDirectoryMissing() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val updatesDir = File(context.cacheDir, "updates")
        updatesDir.deleteRecursively()

        clearUpdateDownloadCache(context)

        assertFalse(updatesDir.exists())
    }
}
