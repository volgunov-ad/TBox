package vad.dashing.tbox.update

import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class YandexDiskClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .build(),
) {
  fun fetchText(publicKey: String, path: String): String {
        val href = resolveDownloadHref(publicKey, path)
        val request = Request.Builder().url(href).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Yandex Disk download failed: HTTP ${response.code}")
            }
            return response.body?.string().orEmpty()
        }
    }

    suspend fun downloadToFile(
        publicKey: String,
        path: String,
        destination: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> },
    ) {
        val href = resolveDownloadHref(publicKey, path)
        val request = Request.Builder().url(href).get().build()
        val coroutineContext = currentCoroutineContext()
        val call = client.newCall(request)
        val cancelHandle = coroutineContext.job.invokeOnCompletion { cause ->
            if (cause is CancellationException) {
                call.cancel()
            }
        }
        try {
            coroutineContext.ensureActive()
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Yandex Disk download failed: HTTP ${response.code}")
                }
                val body = response.body ?: throw IOException("Yandex Disk download body is empty")
                val totalBytes = body.contentLength().takeIf { it >= 0L }
                destination.parentFile?.mkdirs()
                val tempFile = File(destination.parentFile, "${destination.name}.part")
                var completed = false
                try {
                    tempFile.outputStream().use { output ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var downloaded = 0L
                            while (true) {
                                coroutineContext.ensureActive()
                                val read = input.read(buffer)
                                coroutineContext.ensureActive()
                                if (read <= 0) break
                                output.write(buffer, 0, read)
                                downloaded += read
                                onProgress(downloaded, totalBytes)
                            }
                        }
                    }
                    if (destination.exists()) {
                        destination.delete()
                    }
                    if (!tempFile.renameTo(destination)) {
                        throw IOException("Failed to finalize download file")
                    }
                    completed = true
                } finally {
                    if (!completed) {
                        tempFile.delete()
                    }
                }
            }
        } catch (error: IOException) {
            if (!coroutineContext.isActive) {
                throw CancellationException("Update download canceled", error)
            }
            throw error
        } finally {
            cancelHandle.dispose()
        }
    }

    fun fetchPublicResourceSize(publicKey: String, path: String): Long {
        if (publicKey.isBlank()) {
            throw IOException("Update source URL is not configured")
        }
        val encodedKey = URLEncoder.encode(publicKey, Charsets.UTF_8.name())
        val encodedPath = URLEncoder.encode(path, Charsets.UTF_8.name())
        val apiUrl =
            "https://cloud-api.yandex.net/v1/disk/public/resources" +
                "?public_key=$encodedKey&path=$encodedPath"
        val request = Request.Builder().url(apiUrl).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Yandex Disk metadata failed: HTTP ${response.code}")
            }
            val size = JSONObject(response.body?.string().orEmpty()).optLong("size", -1L)
            if (size <= 0L) {
                throw IOException("Yandex Disk metadata returned empty file size")
            }
            return size
        }
    }

    private fun resolveDownloadHref(publicKey: String, path: String): String {
        if (publicKey.isBlank()) {
            throw IOException("Update source URL is not configured")
        }
        val encodedKey = URLEncoder.encode(publicKey, Charsets.UTF_8.name())
        val encodedPath = URLEncoder.encode(path, Charsets.UTF_8.name())
        val apiUrl =
            "https://cloud-api.yandex.net/v1/disk/public/resources/download" +
                "?public_key=$encodedKey&path=$encodedPath"
        val request = Request.Builder().url(apiUrl).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Yandex Disk API failed: HTTP ${response.code}")
            }
            val json = response.body?.string().orEmpty()
            val href = JSONObject(json).optString("href")
            if (href.isBlank()) {
                throw IOException("Yandex Disk API returned empty download link")
            }
            return href
        }
    }
}
