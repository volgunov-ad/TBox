package vad.dashing.voice.api

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ExternalApiClient(
    private val client: OkHttpClient = defaultClient(),
) {
    fun health(host: String, port: Int): Result<HealthStatus> = runCatching {
        val body = get(host, port, "/v1/health", token = null)
        parseHealth(body)
    }

    fun catalog(host: String, port: Int, token: String): Result<ApiCatalog> = runCatching {
        requireToken(token)
        val body = get(host, port, "/v1/catalog", token = token)
        parseCatalog(body)
    }

    fun automations(host: String, port: Int, token: String): Result<List<AutomationSummary>> = runCatching {
        requireToken(token)
        val body = get(host, port, "/v1/automations", token = token)
        parseAutomations(body)
    }

    fun signals(
        host: String,
        port: Int,
        token: String,
        ids: List<String>,
        source: String,
    ): Result<SignalsSnapshot> = runCatching {
        requireToken(token)
        require(ids.isNotEmpty()) { "ids must not be empty" }
        val url = baseUrl(host, port).toHttpUrl().newBuilder()
            .addPathSegments("v1/signals")
            .addQueryParameter("ids", ids.joinToString(","))
            .addQueryParameter("source", source)
            .build()
        val body = execute(
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .get()
                .build(),
        )
        parseSignals(body)
    }

    fun invokeActions(
        host: String,
        port: Int,
        token: String,
        actionsJsonBody: String,
    ): Result<InvokeResults> = runCatching {
        requireToken(token)
        val body = post(host, port, "/v1/actions/invoke", token, actionsJsonBody)
        parseInvokeResults(body)
    }

    fun runAutomation(
        host: String,
        port: Int,
        token: String,
        automationId: String,
    ): Result<RunAutomationResult> = runCatching {
        requireToken(token)
        require(automationId.isNotBlank()) { "automation id is required" }
        val encoded = java.net.URLEncoder.encode(automationId, Charsets.UTF_8.name())
            .replace("+", "%20")
        val body = post(host, port, "/v1/automations/$encoded/run", token, "{}")
        parseRunAutomation(body)
    }

    private fun get(host: String, port: Int, path: String, token: String?): String {
        val builder = Request.Builder()
            .url(baseUrl(host, port) + path)
            .header("Accept", "application/json")
            .get()
        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }
        return execute(builder.build())
    }

    private fun post(
        host: String,
        port: Int,
        path: String,
        token: String,
        jsonBody: String,
    ): String {
        val builder = Request.Builder()
            .url(baseUrl(host, port) + path)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .post(jsonBody.toRequestBody(JSON))
        return execute(builder.build())
    }

    private fun execute(request: Request): String {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            // RunNow returns 202 Accepted.
            if (!response.isSuccessful && response.code != 202) {
                val message = errorMessage(body) ?: "HTTP ${response.code}: ${body.take(200)}"
                error(message)
            }
            return body
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

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

        fun requireToken(token: String) {
            require(token.isNotBlank()) { "Нужен Bearer-токен (Настройки → API → Создать токен)" }
        }

        fun errorMessage(raw: String): String? {
            if (raw.isBlank()) return null
            return runCatching {
                val err = JSONObject(raw).optJSONObject("error") ?: return null
                val code = err.optString("code")
                val message = err.optString("message")
                listOf(code, message).filter { it.isNotBlank() }.joinToString(": ")
            }.getOrNull()
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

        fun parseCatalog(raw: String): ApiCatalog {
            val json = JSONObject(raw)
            val signalsJson = json.optJSONArray("signals") ?: JSONArray()
            val actionsJson = json.optJSONArray("actionTypes") ?: JSONArray()
            val signals = buildList {
                for (i in 0 until signalsJson.length()) {
                    val item = signalsJson.optJSONObject(i) ?: continue
                    val sources = stringList(item.optJSONArray("sources"))
                    val aliases = stringList(item.optJSONArray("voiceAliasesRu"))
                    val named = linkedMapOf<String, String>()
                    val namedArr = item.optJSONArray("namedValues")
                    if (namedArr != null) {
                        for (j in 0 until namedArr.length()) {
                            val nv = namedArr.optJSONObject(j) ?: continue
                            val value = nv.optString("value")
                            val label = nv.optString("label")
                            if (value.isNotBlank()) named[value] = label.ifBlank { value }
                        }
                    }
                    add(
                        CatalogSignal(
                            id = item.optString("id"),
                            label = item.optString("label"),
                            unit = item.optString("unit"),
                            valueType = item.optString("valueType"),
                            sources = sources,
                            voiceAliasesRu = aliases,
                            namedValues = named,
                        ),
                    )
                }
            }
            val actions = buildList {
                for (i in 0 until actionsJson.length()) {
                    val item = actionsJson.optJSONObject(i) ?: continue
                    add(
                        CatalogAction(
                            type = item.optString("type"),
                            actionType = item.optString("actionType").takeIf { it.isNotBlank() },
                            bus = item.optString("bus").takeIf { it.isNotBlank() },
                            propertyId = if (item.has("propertyId")) item.optInt("propertyId") else null,
                            label = item.optString("label").takeIf { it.isNotBlank() },
                            safety = item.optString("safety").takeIf { it.isNotBlank() },
                            voiceAliasesRu = stringList(item.optJSONArray("voiceAliasesRu")),
                        ),
                    )
                }
            }
            return ApiCatalog(
                catalogVersion = json.optInt("catalogVersion", 0),
                signals = signals.filter { it.id.isNotBlank() },
                actionTypes = actions.filter { it.type.isNotBlank() },
            )
        }

        fun parseAutomations(raw: String): List<AutomationSummary> {
            val json = JSONObject(raw)
            val arr = json.optJSONArray("automations") ?: JSONArray()
            return buildList {
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val id = item.optString("id")
                    if (id.isBlank()) continue
                    add(
                        AutomationSummary(
                            id = id,
                            name = item.optString("name"),
                            enabled = item.optBoolean("enabled", false),
                        ),
                    )
                }
            }
        }

        fun parseSignals(raw: String): SignalsSnapshot {
            val json = JSONObject(raw)
            val arr = json.optJSONArray("signals") ?: JSONArray()
            val signals = buildList {
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val id = item.optString("id")
                    if (id.isBlank()) continue
                    val available = item.optBoolean("available", false)
                    val value: Any? = when {
                        !available || item.isNull("value") -> null
                        else -> {
                            val rawValue = item.get("value")
                            when (rawValue) {
                                is Number, is String, is Boolean -> rawValue
                                JSONObject.NULL -> null
                                else -> rawValue.toString()
                            }
                        }
                    }
                    add(
                        SignalReading(
                            id = id,
                            available = available,
                            valueType = item.optString("valueType"),
                            value = value,
                        ),
                    )
                }
            }
            return SignalsSnapshot(signals = signals)
        }

        fun parseInvokeResults(raw: String): InvokeResults {
            val json = JSONObject(raw)
            val arr = json.optJSONArray("results") ?: JSONArray()
            val results = buildList {
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    add(
                        InvokeResultItem(
                            success = item.optBoolean("success", false),
                            message = item.optString("message"),
                        ),
                    )
                }
            }
            return InvokeResults(results = results)
        }

        fun parseRunAutomation(raw: String): RunAutomationResult {
            val json = JSONObject(raw)
            return RunAutomationResult(
                accepted = json.optBoolean("accepted", false),
                message = json.optString("message"),
            )
        }

        private fun stringList(array: JSONArray?): List<String> {
            if (array == null) return emptyList()
            return buildList {
                for (i in 0 until array.length()) {
                    val value = array.optString(i).trim()
                    if (value.isNotEmpty()) add(value)
                }
            }
        }
    }
}
