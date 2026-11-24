package vad.dashing.store.install

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import vad.dashing.store.StoreErrors
import vad.dashing.store.catalog.StoreCatalogEntry
import vad.dashing.store.catalog.StoreDownloadFile
import vad.dashing.store.catalog.StoreDownloads
import vad.dashing.store.disk.UpdateChannel

class CardFilesDownloaderTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun sha(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun skipsIdenticalExistingFile() = runBlocking {
        val payload = "hello".toByteArray()
        val existingDir = temp.newFolder("Download", "Folder")
        val existing = File(existingDir, "a.txt").apply { writeBytes(payload) }
        var downloads = 0
        val downloader = CardFilesDownloader(
            cacheDir = temp.newFolder("cache"),
            download = { _, _, _ -> downloads++ },
            publish = { _, _, _ -> fail("should not publish"); error("x") },
            existing = { folder, name ->
                if (folder == "Folder" && name == "a.txt") existing else null
            },
        )
        val entry = StoreCatalogEntry(
            id = "demo",
            packageName = "p",
            title = "Demo",
            downloads = StoreDownloads(
                folder = "Folder",
                files = listOf(
                    StoreDownloadFile("a.txt", sha(payload), payload.size.toLong()),
                ),
            ),
        )
        val result = downloader.saveAll(UpdateChannel.RELEASE, entry) { _, _, _ -> }
        assertEquals(0, downloads)
        assertEquals(listOf("a.txt"), result.unchanged)
        assertTrue(result.saved.isEmpty())
        assertTrue(result.failed.isEmpty())
    }

    @Test
    fun unreadableExistingFileIsDownloadedAgain() = runBlocking {
        val payload = "fresh".toByteArray()
        val published = mutableListOf<String>()
        val downloader = CardFilesDownloader(
            cacheDir = temp.newFolder("cache"),
            download = { _, dest, _ ->
                dest.parentFile?.mkdirs()
                dest.writeBytes(payload)
            },
            publish = { source, _, name ->
                published += name
                source
            },
            existing = { _, _ -> throw SecurityException("scoped storage") },
        )
        val entry = StoreCatalogEntry(
            id = "demo",
            packageName = "p",
            title = "Demo",
            downloads = StoreDownloads(
                folder = "F",
                files = listOf(StoreDownloadFile("a.txt", sha(payload), payload.size.toLong())),
            ),
        )
        val result = downloader.saveAll(UpdateChannel.RELEASE, entry) { _, _, _ -> }
        assertEquals(listOf("a.txt"), published)
        assertEquals(listOf("a.txt"), result.saved)
        assertTrue(result.failed.isEmpty())
    }

    @Test
    fun shaMismatchFailsSoftAndContinues() = runBlocking {
        val good = "ok".toByteArray()
        val bad = "bad".toByteArray()
        val published = mutableListOf<String>()
        val downloader = CardFilesDownloader(
            cacheDir = temp.newFolder("cache"),
            download = { _, dest, onProgress ->
                onProgress(1L, 1L)
                val body = if (dest.name == "bad.txt") bad else good
                dest.parentFile?.mkdirs()
                dest.writeBytes(body)
            },
            publish = { source, _, name ->
                published += name
                source
            },
            existing = { _, _ -> null },
        )
        val entry = StoreCatalogEntry(
            id = "demo",
            packageName = "p",
            title = "Demo",
            downloads = StoreDownloads(
                folder = "F",
                files = listOf(
                    StoreDownloadFile("bad.txt", sha(good), good.size.toLong()),
                    StoreDownloadFile("good.txt", sha(good), good.size.toLong()),
                ),
            ),
        )
        val progress = mutableListOf<CardFileProgress>()
        val result = downloader.saveAll(UpdateChannel.RELEASE, entry) { p, _, _ -> progress += p }
        assertEquals(listOf("good.txt"), published)
        assertEquals(1, result.failed.size)
        assertEquals("bad.txt", result.failed[0].name)
        assertEquals(StoreErrors.FILE_CHECKSUM_MISMATCH, result.failed[0].error)
        assertEquals(listOf("good.txt"), result.saved)
        assertEquals(1, progress.first { it.name == "bad.txt" }.index)
        assertEquals(2, progress.first { it.name == "bad.txt" }.count)
    }

    @Test
    fun publishExceptionIsSoftFailure() = runBlocking {
        val payload = "x".toByteArray()
        val downloader = CardFilesDownloader(
            cacheDir = temp.newFolder("cache"),
            download = { _, dest, _ ->
                dest.parentFile?.mkdirs()
                dest.writeBytes(payload)
            },
            publish = { _, _, _ -> throw IOException(StoreErrors.FILE_WRITE_FAILED) },
            existing = { _, _ -> null },
        )
        val entry = StoreCatalogEntry(
            id = "demo",
            packageName = "p",
            title = "Demo",
            downloads = StoreDownloads(
                folder = "F",
                files = listOf(StoreDownloadFile("a.txt", sha(payload), payload.size.toLong())),
            ),
        )
        val result = downloader.saveAll(UpdateChannel.RELEASE, entry) { _, _, _ -> }
        assertEquals(1, result.failed.size)
        assertEquals(StoreErrors.FILE_WRITE_FAILED, result.failed[0].error)
    }

    @Test
    fun cancellationPropagatesAndClearsCache() = runBlocking {
        val cache = temp.newFolder("cache")
        val downloader = CardFilesDownloader(
            cacheDir = cache,
            download = { _, dest, _ ->
                dest.parentFile?.mkdirs()
                dest.writeBytes(byteArrayOf(1))
                throw CancellationException("cancel")
            },
            publish = { source, _, _ -> source },
            existing = { _, _ -> null },
        )
        val entry = StoreCatalogEntry(
            id = "demo",
            packageName = "p",
            title = "Demo",
            downloads = StoreDownloads(
                folder = "F",
                files = listOf(
                    StoreDownloadFile("a.txt", "a".repeat(64), 1L),
                ),
            ),
        )
        try {
            downloader.saveAll(UpdateChannel.RELEASE, entry) { _, _, _ -> }
            fail("expected cancellation")
        } catch (_: CancellationException) {
            // expected
        }
        val leftovers = cache.walkTopDown().filter { it.isFile }.toList()
        assertTrue(leftovers.isEmpty())
        assertFalse(File(cache, "demo/a.txt").exists())
    }
}
