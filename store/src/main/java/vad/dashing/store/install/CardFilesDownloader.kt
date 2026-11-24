package vad.dashing.store.install

import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import vad.dashing.store.StoreErrors
import vad.dashing.store.catalog.StoreCatalogEntry
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
            val progress = CardFileProgress(fileMeta.name, index + 1, count)
            onProgress(progress, 0L, fileMeta.sizeBytes)
            val existingFile = existing(folder, fileMeta.name)
            if (existingFile != null &&
                (fileMeta.sizeBytes == null || existingFile.length() == fileMeta.sizeBytes) &&
                ApkVerifier.verifySha256(existingFile, fileMeta.sha256)
            ) {
                unchanged += fileMeta.name
                continue
            }
            val cacheFile = File(File(cacheDir, entry.id), fileMeta.name)
            cacheFile.parentFile?.mkdirs()
            try {
                download(
                    YandexDiskPath.appFilePath(channel, entry.id, fileMeta.name),
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
                saved += fileMeta.name
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failed += CardFileFailure(
                    name = fileMeta.name,
                    error = error.message?.takeIf { it.isNotBlank() }
                        ?: StoreErrors.FILE_WRITE_FAILED,
                )
            } finally {
                cacheFile.delete()
                File(cacheFile.parentFile, "${cacheFile.name}.part").delete()
            }
        }
        return CardFilesResult(
            folder = folder,
            folderPath = "Download/$folder",
            saved = saved,
            unchanged = unchanged,
            failed = failed,
        )
    }
}
