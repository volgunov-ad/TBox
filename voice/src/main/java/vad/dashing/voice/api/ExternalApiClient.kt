package vad.dashing.voice.api

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class HealthStatus(
    val ok: Boolean,
    val apiVersion: Int,
    val catalogVersion: Int,
    val serverEnabled: Boolean,
    val pairingActive: Boolean,
    val appVersion: String,
)

class ExternalApiClient(
    private val client: OkHttpClient = defaultClient(),
) {
    fun health(host: String, port: Int): Result<HealthStatus> = runCatching {
        val url = baseUrl(host, port) + "/v1/health"
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("HTTP ${response.code}: ${body.take(200)}")
            }
            parseHealth(body)
        }
    }

    companion object {
        fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .callTimeout(15, TimeUnit.SECONDS)
                .build()

        fun baseUrl(host: String, port: Int): String {
            val h = host.trim().ifEmpty { "127.0.0.1" }
            return "http://$h:$port"
        }

        fun parseHealth(raw: String): HealthStatus {
            val json = JSONObject(raw)
            return HealthStatus(
                ok = json.optBoolean("ok", false),
                apiVersion = json.optInt("apiVersion", 0),
                catalogVersion = json.optInt("catalogVersion", 0),
                serverEnabled = json.optBoolean("serverEnabled", false),
                pairingActive = json.optBoolean("pairingActive", false),
                appVersion = json.optString("appVersion", ""),
            )
        }
    }
}
