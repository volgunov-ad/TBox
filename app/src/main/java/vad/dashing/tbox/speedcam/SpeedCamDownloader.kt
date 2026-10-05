package vad.dashing.tbox.speedcam

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * HTTPS download of SpeedCamOnline igoext dumps.
 * Uses OkHttp (more reliable TLS on API 28 HUs than [java.net.HttpURLConnection])
 * with browser-like headers and a few retries for mid-stream SSL aborts.
 */
internal object SpeedCamDownloader {
    const val DEFAULT_URL = "https://speedcamonline.ru/igoext/Rus/"
    const val REFERER = "https://speedcamonline.ru/map/Rus/"
    const val MAX_ATTEMPTS = 3

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun downloadToFile(
        destination: File,
        url: String = DEFAULT_URL,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> },
    ) {
        var lastError: Exception? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                downloadOnce(destination, url, onProgress)
                return
            } catch (e: IOException) {
                lastError = e
                destination.delete()
                if (attempt < MAX_ATTEMPTS - 1) {
                    Thread.sleep(400L * (attempt + 1))
                }
            }
        }
        throw lastError ?: IOException("download failed")
    }

    private fun downloadOnce(
        destination: File,
        url: String,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ) {
        destination.parentFile?.mkdirs()
        if (destination.exists()) destination.delete()
        val request = Request.Builder()
            .url(url)
            .get()
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 9; TBoxMonitor) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
            )
            .header("Accept", "text/plain,text/*,*/*")
            .header("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.8")
            .header("Accept-Encoding", "identity")
            .header("Referer", REFERER)
            .header("Connection", "close")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("empty body")
            val total = body.contentLength().takeIf { it > 0L }
            var written = 0L
            FileOutputStream(destination).use { out ->
                body.byteStream().use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        written += n
                        onProgress(written, total)
                    }
                }
            }
            if (written <= 0L) {
                destination.delete()
                throw IOException("empty download")
            }
            // Server often omits Content-Length; still require a plausible CSV size.
            if (written < MIN_BYTES) {
                destination.delete()
                throw IOException("download too small ($written bytes)")
            }
        }
    }

    /** Heuristic for truncated SSL abort bodies (full Rus dump is ~3 MB). */
    const val MIN_BYTES = 8_192L

    fun isTransientNetworkFailure(message: String?): Boolean {
        val m = message?.lowercase().orEmpty()
        return m.contains("ssl") ||
            m.contains("connection abort") ||
            m.contains("connection reset") ||
            m.contains("broken pipe") ||
            m.contains("unexpected end") ||
            m.contains("software caused") ||
            m.contains("timeout") ||
            m.contains("failed to connect")
    }
}
