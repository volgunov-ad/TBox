package vad.dashing.store.disk

import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException

/**
 * Yandex Disk primary, Mail.ru Cloud mirror fallback (or forced source).
 * [diskPath] is the physical path on the AppStore share, e.g. `/release/apps/catalog.json`.
 */
class StoreCloudFacade(
    private val yandexPublicKey: String,
    private val mailRuWeblink: String,
    private val sourceProvider: () -> CloudSource,
    private val yandex: YandexDiskClient = YandexDiskClient(),
    private val mailRu: MailRuCloudClient = MailRuCloudClient(mailRuWeblink),
) {
    fun fetchText(diskPath: String): String =
        withFallback("fetchText $diskPath") { client ->
            when (client) {
                CloudSource.YANDEX -> yandex.fetchText(yandexPublicKey, diskPath)
                CloudSource.MAILRU -> mailRu.fetchText(diskPath)
                CloudSource.AUTO -> error("unreachable")
            }
        }

    suspend fun downloadToFile(
        diskPath: String,
        destination: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> },
    ) {
        withFallbackSuspend("download $diskPath") { client ->
            when (client) {
                CloudSource.YANDEX -> yandex.downloadToFile(
                    yandexPublicKey,
                    diskPath,
                    destination,
                    onProgress,
                )
                CloudSource.MAILRU -> mailRu.downloadToFile(diskPath, destination, onProgress)
                CloudSource.AUTO -> error("unreachable")
            }
        }
    }

    fun fetchPublicResourceSize(diskPath: String): Long =
        withFallback("size $diskPath") { client ->
            when (client) {
                CloudSource.YANDEX -> yandex.fetchPublicResourceSize(yandexPublicKey, diskPath)
                CloudSource.MAILRU -> mailRu.fetchPublicResourceSize(diskPath)
                CloudSource.AUTO -> error("unreachable")
            }
        }

    private inline fun <T> withFallback(label: String, block: (CloudSource) -> T): T {
        val source = sourceProvider()
        val order = when (source) {
            CloudSource.AUTO -> listOf(CloudSource.YANDEX, CloudSource.MAILRU)
            CloudSource.YANDEX -> listOf(CloudSource.YANDEX)
            CloudSource.MAILRU -> listOf(CloudSource.MAILRU)
        }
        var last: Exception? = null
        for (candidate in order) {
            try {
                return block(candidate)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                last = error
            }
        }
        throw IOException(
            "Cloud $label failed (${source.storageValue}): ${last?.message ?: "unknown"}",
            last,
        )
    }

    private suspend inline fun withFallbackSuspend(
        label: String,
        crossinline block: suspend (CloudSource) -> Unit,
    ) {
        val source = sourceProvider()
        val order = when (source) {
            CloudSource.AUTO -> listOf(CloudSource.YANDEX, CloudSource.MAILRU)
            CloudSource.YANDEX -> listOf(CloudSource.YANDEX)
            CloudSource.MAILRU -> listOf(CloudSource.MAILRU)
        }
        var last: Exception? = null
        for (candidate in order) {
            try {
                block(candidate)
                return
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                last = error
            }
        }
        throw IOException(
            "Cloud $label failed (${source.storageValue}): ${last?.message ?: "unknown"}",
            last,
        )
    }
}
