package vad.dashing.tbox.location.roadmatch

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RoadMapLegacyPurgeTest {

    @Test
    fun ensureLoadedDeletesRootMonolithWithoutParsingAndClearsManifest() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var savedManifest = """
            {"version":1,"installed":[{"id":"ru-moscow-oblast","graphVersion":3,
             "fileName":"ru-moscow-oblast.tboxroads","bytesOnDisk":999,"installedAtEpochMs":1}]}
        """.trimIndent()

        val manager = RoadMapDownloadManager(
            appContext = context,
            scope = CoroutineScope(Dispatchers.Unconfined),
            loadManifestJson = { savedManifest },
            saveManifestJson = { savedManifest = it },
        )
        val mono = manager.legacyMonolithFileFor("ru-moscow-oblast")
        // Intentionally write a non-empty blob; purge must delete without RoadGraph.load.
        mono.writeBytes(fakeMonolithBytes())
        assertTrue(mono.isFile)

        manager.ensureLoaded()

        assertFalse(mono.exists())
        assertTrue(
            manager.snapshot.value.regions.none {
                it.region.id == "ru-moscow-oblast" && it.status == RoadMapRegionStatus.INSTALLED
            },
        )
        assertFalse(savedManifest.contains("ru-moscow-oblast.tboxroads"))
        assertEquals(0L, manager.snapshot.value.totalBytesOnDisk)
    }

    private fun fakeMonolithBytes(): ByteArray {
        val json =
            """{"format":1,"regionId":"ru-moscow-oblast","graphVersion":3,"bbox":[0,0,1,1],"edges":[]}"""
        val gz = ByteArrayOutputStream()
        GZIPOutputStream(gz).use { it.write(json.toByteArray()) }
        return RoadGraph.MAGIC.toByteArray(Charsets.US_ASCII) + gz.toByteArray()
    }
}
