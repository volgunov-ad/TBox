package vad.dashing.store.install

import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import vad.dashing.store.StoreErrors
import vad.dashing.store.catalog.StoreCatalogEntry
import vad.dashing.store.catalog.StoreDownloadFile
import vad.dashing.store.catalog.StoreFileNames
import vad.dashing.store.catalog.YandexDiskPath
import vad.dashing.store.disk.UpdateChannel

data class CardFileProgress(
    val name: String,
    val index: Int,
    val count: Int,
)

data class CardFileFailure(
    val name: String,
    val error: String,
)

data class CardFilesResult(
    val folder: String,
    val folderPath: String,
    val saved: List<String> = emptyList(),
    val unchanged: List<String> = emptyList(),
    val failed: List<CardFileFailure> = emptyList(),
) {
    val readyCount: Int get() = saved.size + unchanged.size
}

/**
 * Downloads card attachments into public Downloads with sha256 checks.
 * Injected I/O keeps unit tests free of Android / network.
 */
internal class CardFilesDownloader(
    private val cacheDir: File,
    private val download: suspend (
        diskPath: String,
        dest: File,
        onProgress: (Long, Long?) -> Unit,
    ) -> Unit,
    private val publish: suspend (source: File, folder: String, fileName: String) -> File,
    private val existing: (folder: String, fileName: String) -> File?,
) {
    suspend fun saveAll(
        channel: UpdateChannel,
        entry: StoreCatalogEntry,
        onProgress: (CardFileProgress, Long, Long?) -> Unit,
    ): CardFilesResult {
        val downloads = entry.downloads ?: return CardFilesResult(folder = "", folderPath = "")
        val folder = StoreFileNames.safeFolder(downloads.folder, entry.id)
        val files = downloads.files
        if (files.isEmpty()) {
            return CardFilesResult(folder = folder, folderPath = "Download/$folder")
        }
        val saved = mutableListOf<String>()
        val unchanged = mutableListOf<String>()
        val failed = mutableListOf<CardFileFailure>()
        val count = files.size
        for ((index, fileMeta) in files.withIndex()) {
            val one = saveFile(
                channel = channel,
                entryId = entry.id,
                folder = folder,
                fileMeta = fileMeta,
                index = index + 1,
                count = count,
                onProgress = onProgress,
            )
            saved += one.saved
            unchanged += one.unchanged
            failed += one.failed
        }
        return CardFilesResult(
            folder = folder,
            folderPath = "Download/$folder",
            saved = saved,
            unchanged = unchanged,
            failed = failed,
        )
    }

    /**
     * Downloads a single catalog file by [fileName] into `Download/<folder>/`.
     * Unknown names become a soft failure (no network call).
     */
    suspend fun saveOne(
        channel: UpdateChannel,
        entry: StoreCatalogEntry,
        fileName: String,
        onProgress: (CardFileProgress, Long, Long?) -> Unit,
    ): CardFilesResult {
        val downloads = entry.downloads ?: return CardFilesResult(folder = "", folderPath = "")
        val folder = StoreFileNames.safeFolder(downloads.folder, entry.id)
        val fileMeta = downloads.files.firstOrNull { it.name == fileName }
        if (fileMeta == null) {
            return CardFilesResult(
                folder = folder,
                folderPath = "Download/$folder",
                failed = listOf(
                    CardFileFailure(name = fileName, error = StoreErrors.FILE_WRITE_FAILED),
                ),
            )
        }
        return saveFile(
            channel = channel,
            entryId = entry.id,
            folder = folder,
            fileMeta = fileMeta,
            index = 1,
            count = 1,
            onProgress = onProgress,
        )
    }

    private suspend fun saveFile(
        channel: UpdateChannel,
        entryId: String,
        folder: String,
        fileMeta: StoreDownloadFile,
        index: Int,
        count: Int,
        onProgress: (CardFileProgress, Long, Long?) -> Unit,
    ): CardFilesResult {
        val progress = CardFileProgress(fileMeta.name, index, count)
        onProgress(progress, 0L, fileMeta.sizeBytes)
        if (isIdenticalExisting(folder, fileMeta)) {
            return CardFilesResult(
                folder = folder,
                folderPath = "Download/$folder",
                unchanged = listOf(fileMeta.name),
            )
        }
        val cacheFile = File(File(cacheDir, entryId), fileMeta.name)
        cacheFile.parentFile?.mkdirs()
        return try {
            download(
                YandexDiskPath.appFilePath(channel, entryId, fileMeta.name),
                cacheFile,
            ) { downloaded, total ->
                onProgress(progress, downloaded, total ?: fileMeta.sizeBytes)
            }
            if (fileMeta.sizeBytes != null && cacheFile.length() != fileMeta.sizeBytes) {
                throw IOException(StoreErrors.FILE_CHECKSUM_MISMATCH)
            }
            if (!ApkVerifier.verifySha256(cacheFile, fileMeta.sha256)) {
                throw IOException(StoreErrors.FILE_CHECKSUM_MISMATCH)
            }
            publish(cacheFile, folder, fileMeta.name)
            CardFilesResult(
                folder = folder,
                folderPath = "Download/$folder",
                saved = listOf(fileMeta.name),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            CardFilesResult(
                folder = folder,
                folderPath = "Download/$folder",
                failed = listOf(
                    CardFileFailure(
                        name = fileMeta.name,
                        error = error.message?.takeIf { it.isNotBlank() }
                            ?: StoreErrors.FILE_WRITE_FAILED,
                    ),
                ),
            )
        } finally {
            cacheFile.delete()
            File(cacheFile.parentFile, "${cacheFile.name}.part").delete()
        }
    }

    /** An unreadable existing file (e.g. another app's file under scoped storage) is just re-downloaded. */
    private fun isIdenticalExisting(folder: String, fileMeta: StoreDownloadFile): Boolean =
        try {
            val file = existing(folder, fileMeta.name)
            file != null &&
                (fileMeta.sizeBytes == null || file.length() == fileMeta.sizeBytes) &&
                ApkVerifier.verifySha256(file, fileMeta.sha256)
        } catch (_: Exception) {
            false
        }
}
