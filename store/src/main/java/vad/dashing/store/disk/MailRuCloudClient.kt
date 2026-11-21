package vad.dashing.store.disk

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

/**
 * Public Mail.ru Cloud weblink downloads (no account).
 *
 * Listing: `GET /api/v2/folder?weblink=…`
 * Download: `{dispatcher.weblink_get[0].url}/{weblink}{path}` (+ optional `?key=` token).
 */
class MailRuCloudClient(
    private val weblink: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .build(),
) {
    private val weblinkTrimmed = weblink.trim().trim('/')
    @Volatile private var cachedGetBase: String? = null

    fun fetchText(diskPath: String): String {
        val href = resolveDownloadHref(diskPath)
        val request = publicRequest(href)
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Mail.ru download failed: HTTP ${response.code}")
            }
            return response.body?.string().orEmpty()
        }
    }

    suspend fun downloadToFile(
        diskPath: String,
        destination: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> },
    ) {
        val href = resolveDownloadHref(diskPath)
        val request = publicRequest(href)
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
                    throw IOException("Mail.ru download failed: HTTP ${response.code}")
                }
                val body = response.body ?: throw IOException("Mail.ru download body is empty")
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
                        throw IOException("Failed to finalize Mail.ru download file")
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
                throw CancellationException("Mail.ru download canceled", error)
            }
            throw error
        } finally {
            cancelHandle.dispose()
        }
    }

    fun fetchPublicResourceSize(diskPath: String): Long {
        val wlPath = toWeblinkPath(diskPath)
        val encoded = URLEncoder.encode(wlPath, Charsets.UTF_8.name())
        val apiUrl = "https://cloud.mail.ru/api/v2/file?weblink=$encoded"
        val request = publicRequest(apiUrl)
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Mail.ru metadata failed: HTTP ${response.code}")
            }
            val body = JSONObject(response.body?.string().orEmpty()).optJSONObject("body")
                ?: throw IOException("Mail.ru metadata body missing")
            if (body.optString("type") != "file") {
                throw IOException("Mail.ru path is not a file")
            }
            val size = body.optLong("size", -1L)
            if (size <= 0L) {
                throw IOException("Mail.ru metadata returned empty file size")
            }
            return size
        }
    }

    fun toWeblinkPath(diskPath: String): String {
        val path = diskPath.trim().trimStart('/')
        if (path.isEmpty()) return weblinkTrimmed
        return "$weblinkTrimmed/$path"
    }

    private fun resolveDownloadHref(diskPath: String): String {
        if (weblinkTrimmed.isBlank()) {
            throw IOException("Mail.ru weblink is not configured")
        }
        val base = downloadBaseUrl()
        val wlPath = toWeblinkPath(diskPath)
        val encodedPath = wlPath.split('/').joinToString("/") { segment ->
            URLEncoder.encode(segment, Charsets.UTF_8.name()).replace("+", "%20")
        }
        val url = "$base/$encodedPath"
        val token = runCatching { downloadToken() }.getOrNull()
        return if (!token.isNullOrBlank()) "$url?key=${URLEncoder.encode(token, Charsets.UTF_8.name())}" else url
    }

    private fun downloadBaseUrl(): String {
        cachedGetBase?.let { return it }
        val request = publicRequest("https://cloud.mail.ru/api/v2/dispatcher")
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Mail.ru dispatcher failed: HTTP ${response.code}")
            }
            val root = JSONObject(response.body?.string().orEmpty())
            val body = root.optJSONObject("body")
                ?: throw IOException("Mail.ru dispatcher body missing")
            val arr = body.optJSONArray("weblink_get")
                ?: throw IOException("Mail.ru weblink_get missing")
            val url = arr.optJSONObject(0)?.optString("url").orEmpty()
            if (url.isBlank()) {
                throw IOException("Mail.ru weblink_get url empty")
            }
            cachedGetBase = url.trimEnd('/')
            return cachedGetBase!!
        }
    }

    private fun downloadToken(): String? {
        val request = publicRequest("https://cloud.mail.ru/api/v2/tokens/download")
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val root = JSONObject(response.body?.string().orEmpty())
            if (root.optInt("status", 0) != 200) return null
            val body = root.opt("body")
            return when (body) {
                is JSONObject -> body.optString("token").takeIf { it.isNotBlank() }
                else -> null
            }
        }
    }

    private fun publicRequest(url: String): Request =
        Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://cloud.mail.ru/public/$weblinkTrimmed")
            .header("Origin", "https://cloud.mail.ru")
            .build()

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
