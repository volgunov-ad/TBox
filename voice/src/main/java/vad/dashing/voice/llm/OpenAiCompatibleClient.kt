package vad.dashing.voice.llm

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import vad.dashing.voice.api.ExternalApiHttpException
import vad.dashing.voice.settings.AccessTokenNormalizer

data class CloudModelsResult(
    val modelIds: List<String>,
)

/**
 * OpenAI-compatible HTTP client: `/models` (stage B) and `/chat/completions` (stage C).
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

    fun chatCompletions(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<CloudChatMessage>,
        tools: JSONArray,
    ): Result<CloudChatResult> = runCatching {
        val key = AccessTokenNormalizer.normalize(apiKey)
        require(key.isNotBlank()) { "Нужен API-ключ облачной модели" }
        require(model.isNotBlank()) { "Нужна модель облачного API" }
        require(messages.isNotEmpty()) { "messages пусты" }
        val root = CloudBaseUrlNormalizer.normalize(baseUrl)
        require(root.isNotBlank()) { "Нужен base URL облачного API" }
        val payload = JSONObject()
            .put("model", model.trim())
            .put("messages", messagesToJson(messages))
            .put("tools", tools)
            .put("tool_choice", "auto")
            .put("temperature", 0)
        val body = execute(
            Request.Builder()
                .url("$root/chat/completions")
                .header("Authorization", "Bearer $key")
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(JSON))
                .build(),
        )
        parseChatCompletion(body)
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
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                // Freeform stories can take longer than short tool calls.
                .readTimeout(90, TimeUnit.SECONDS)
                .callTimeout(100, TimeUnit.SECONDS)
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

        fun parseChatCompletion(raw: String): CloudChatResult {
            val json = JSONObject(raw)
            val choice = json.optJSONArray("choices")?.optJSONObject(0)
                ?: error("Ответ chat/completions без choices")
            val message = choice.optJSONObject("message")
                ?: error("choice без message")
            val toolCallsJson = message.optJSONArray("tool_calls")
            val toolCalls = buildList {
                if (toolCallsJson != null) {
                    for (i in 0 until toolCallsJson.length()) {
                        val item = toolCallsJson.optJSONObject(i) ?: continue
                        val function = item.optJSONObject("function") ?: continue
                        val name = function.optString("name").trim()
                        if (name.isEmpty()) continue
                        add(
                            CloudToolCall(
                                id = item.optString("id").ifBlank { "call_$i" },
                                name = name,
                                argumentsJson = function.optString("arguments").ifBlank { "{}" },
                            ),
                        )
                    }
                }
            }
            val content = message.optString("content").takeIf { it.isNotBlank() }
            return CloudChatResult(
                message = CloudChatMessage(
                    role = message.optString("role", "assistant"),
                    content = content,
                    toolCalls = toolCalls,
                ),
                finishReason = choice.optString("finish_reason"),
            )
        }

        fun messagesToJson(messages: List<CloudChatMessage>): JSONArray {
            val arr = JSONArray()
            for (message in messages) {
                val obj = JSONObject().put("role", message.role)
                if (message.content != null) {
                    obj.put("content", message.content)
                }
                if (message.toolCalls.isNotEmpty()) {
                    val tools = JSONArray()
                    for (call in message.toolCalls) {
                        tools.put(
                            JSONObject()
                                .put("id", call.id)
                                .put("type", "function")
                                .put(
                                    "function",
                                    JSONObject()
                                        .put("name", call.name)
                                        .put("arguments", call.argumentsJson),
                                ),
                        )
                    }
                    obj.put("tool_calls", tools)
                }
                arr.put(obj)
            }
            return arr
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

