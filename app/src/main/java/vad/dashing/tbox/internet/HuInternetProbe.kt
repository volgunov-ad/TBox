package vad.dashing.tbox.internet

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Pure helpers for HU internet URL normalization and HTTP probing.
 * Default probe target is Yandex; the user may override it in Modem settings.
 */
object HuInternetProbe {
    const val DEFAULT_URL = "https://yandex.ru"
    const val DEFAULT_INTERVAL_SEC = 15
    const val MIN_INTERVAL_SEC = 5
    const val MAX_INTERVAL_SEC = 60

    fun normalizeUrl(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return DEFAULT_URL
        return if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    fun coerceIntervalSec(seconds: Int): Int =
        seconds.coerceIn(MIN_INTERVAL_SEC, MAX_INTERVAL_SEC)

    fun defaultClient(
        connectTimeoutSec: Long = 3,
        readTimeoutSec: Long = 5,
    ): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(connectTimeoutSec, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSec, TimeUnit.SECONDS)
            .writeTimeout(readTimeoutSec, TimeUnit.SECONDS)
            .callTimeout(connectTimeoutSec + readTimeoutSec + 1, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

    /**
     * @return true when the probe URL answers with HTTP 2xx or 3xx.
     */
    fun probe(url: String, client: OkHttpClient = defaultClient()): Boolean {
        val normalized = normalizeUrl(url)
        val request = Request.Builder()
            .url(normalized)
            .get()
            .header("User-Agent", "TBoxMonitor-InternetProbe/1.0")
            .header("Accept", "*/*")
            .build()
        client.newCall(request).execute().use { response ->
            // Drain a tiny bit so keep-alive / redirects settle; ignore body content.
            response.body?.source()?.let { source ->
                runCatching { source.request(1024); source.skip(minOf(1024, source.buffer.size)) }
            }
            return response.code in 200..399
        }
    }
}
