package vad.dashing.voice.llm

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import vad.dashing.voice.api.ExternalApiHttpException
import vad.dashing.voice.settings.AccessTokenNormalizer

data class CloudModelsResult(
    val modelIds: List<String>,
)

/**
 * Minimal OpenAI-compatible HTTP client: `GET {base}/models` for stage B ping + model list.
 */
class OpenAiCompatibleClient(
    private val client: OkHttpClient = defaultClient(),
) {
    fun listModels(baseUrl: String, apiKey: String): Result<CloudModelsResult> = runCatching {
        val key = AccessTokenNormalizer.normalize(apiKey)
        require(key.isNotBlank()) { "Нужен API-ключ облачной модели" }
        val url = CloudBaseUrlNormalizer.modelsUrl(baseUrl)
        val body = execute(
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $key")
                .header("Accept", "application/json")
                .get()
                .build(),
        )
        parseModels(body)
    }

    private fun execute(request: Request): String {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = errorMessage(body) ?: "HTTP ${response.code}: ${body.take(200)}"
                throw ExternalApiHttpException(response.code, message)
            }
            return body
        }
    }

    companion object {
        fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .callTimeout(25, TimeUnit.SECONDS)
                .build()

        fun parseModels(raw: String): CloudModelsResult {
            val json = JSONObject(raw)
            val data = json.optJSONArray("data")
                ?: error("Ответ /models без data")
            val ids = buildList {
                for (i in 0 until data.length()) {
                    val item = data.optJSONObject(i) ?: continue
                    val id = item.optString("id").trim()
                    if (id.isNotEmpty()) add(id)
                }
            }.distinct()
            return CloudModelsResult(modelIds = ids)
        }

        fun errorMessage(raw: String): String? {
            if (raw.isBlank()) return null
            return runCatching {
                val root = JSONObject(raw)
                val err = root.optJSONObject("error")
                when {
                    err != null -> {
                        val message = err.optString("message").ifBlank {
                            err.optString("code")
                        }
                        message.takeIf { it.isNotBlank() }
                    }
                    else -> root.optString("message").takeIf { it.isNotBlank() }
                }
            }.getOrNull()
        }
    }
}
