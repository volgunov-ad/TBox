package vad.dashing.mqtt.api

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ApiCallException(
    val code: String,
    message: String,
    val httpStatus: Int,
) : Exception(message)

data class HealthSnapshot(
    val ok: Boolean,
    val catalogVersion: Int,
)

data class PairRequestResult(
    val requestId: String,
)

data class PairStatusResult(
    val status: String,
    val accessToken: String?,
)

data class SignalSample(
    val id: String,
    val available: Boolean,
    val text: String?,
    val number: Double?,
    val latitude: Double?,
    val longitude: Double?,
)

data class AutomationSummary(
    val id: String,
    val name: String,
)

class MonitorApi(
    private val client: OkHttpClient = defaultClient(),
) {
    fun health(port: Int): HealthSnapshot {
        val body = get(port, "/v1/health", token = null)
        val json = JSONObject(body)
        return HealthSnapshot(
            ok = json.optBoolean("ok"),
            catalogVersion = json.optInt("catalogVersion"),
        )
    }

    fun pairRequest(port: Int, clientId: String, clientName: String): PairRequestResult {
        val payload = JSONObject()
            .put("clientId", clientId)
            .put("clientName", clientName)
            .put("clientKind", "mqtt")
            .toString()
        val body = post(port, "/v1/pair/request", token = null, json = payload, accept202 = true)
        val json = JSONObject(body)
        val requestId = json.optString("requestId")
        if (requestId.isBlank()) error("Monitor не вернул requestId")
        return PairRequestResult(requestId)
    }

    fun pairStatus(port: Int, requestId: String): PairStatusResult {
        val url = base(port).toHttpUrl().newBuilder()
            .addPathSegments("v1/pair/status")
            .addQueryParameter("requestId", requestId)
            .build()
        val body = execute(
            Request.Builder().url(url).header("Accept", "application/json").get().build(),
        )
        val json = JSONObject(body)
        return PairStatusResult(
            status = json.optString("status"),
            accessToken = json.optString("accessToken").takeIf { it.isNotBlank() },
        )
    }

    fun catalog(port: Int, token: String): String =
        get(port, "/v1/catalog", token)

    fun automations(port: Int, token: String): List<AutomationSummary> {
        val body = get(port, "/v1/automations", token)
        val array = JSONObject(body).optJSONArray("automations") ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("id")
                if (id.isBlank()) continue
                add(AutomationSummary(id, item.optString("name")))
            }
        }
    }

    fun signals(port: Int, token: String, ids: List<String>, source: String): List<SignalSample> {
        val url = base(port).toHttpUrl().newBuilder()
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
        val array = JSONObject(body).optJSONArray("signals") ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(parseSignal(item))
            }
        }
    }

    fun invoke(port: Int, token: String, actionsJson: String): Boolean {
        val body = post(port, "/v1/actions/invoke", token, actionsJson, accept202 = false)
        val results = JSONObject(body).optJSONArray("results") ?: return false
        if (results.length() == 0) return false
        for (i in 0 until results.length()) {
            val item = results.optJSONObject(i) ?: return false
            if (!item.optBoolean("success")) return false
        }
        return true
    }

    fun runAutomation(port: Int, token: String, id: String): Boolean {
        val encoded = java.net.URLEncoder.encode(id, Charsets.UTF_8.name()).replace("+", "%20")
        val body = post(port, "/v1/automations/$encoded/run", token, "{}", accept202 = true)
        return JSONObject(body).optBoolean("accepted")
    }

    private fun parseSignal(item: JSONObject): SignalSample {
        val available = item.optBoolean("available")
        val raw = if (item.isNull("value")) null else item.opt("value")
        var text: String? = null
        var number: Double? = null
        var latitude: Double? = null
        var longitude: Double? = null
        if (available) {
            when (raw) {
                is Number -> number = raw.toDouble()
                is String -> text = raw
                is JSONObject -> {
                    latitude = raw.optDoubleOrNull("latitude")
                    longitude = raw.optDoubleOrNull("longitude")
                }
            }
        }
        return SignalSample(
            id = item.optString("id"),
            available = available,
            text = text,
            number = number,
            latitude = latitude,
            longitude = longitude,
        )
    }

    private fun get(port: Int, path: String, token: String?): String {
        val builder = Request.Builder()
            .url(base(port) + path)
            .header("Accept", "application/json")
            .get()
        if (!token.isNullOrBlank()) builder.header("Authorization", "Bearer $token")
        return execute(builder.build())
    }

    private fun post(
        port: Int,
        path: String,
        token: String?,
        json: String,
        accept202: Boolean,
    ): String {
        val builder = Request.Builder()
            .url(base(port) + path)
            .header("Accept", "application/json")
            .post(json.toRequestBody(JSON))
        if (!token.isNullOrBlank()) builder.header("Authorization", "Bearer $token")
        return execute(builder.build(), accept202)
    }

    private fun execute(request: Request, accept202: Boolean = false): String {
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val ok = response.isSuccessful || (accept202 && response.code == 202)
                if (!ok) {
                    val parsed = errorOf(body)
                    throw ApiCallException(
                        code = parsed.first,
                        message = parsed.second.ifBlank { "HTTP ${response.code}" },
                        httpStatus = response.code,
                    )
                }
                return body
            }
        } catch (error: ApiCallException) {
            throw error
        } catch (error: Exception) {
            throw ApiCallException("unreachable", error.message ?: "Monitor недоступен", 0)
        }
    }

    private fun errorOf(raw: String): Pair<String, String> {
        return runCatching {
            val err = JSONObject(raw).optJSONObject("error") ?: return "" to raw.take(160)
            err.optString("code") to err.optString("message")
        }.getOrDefault("" to raw.take(160))
    }

    private fun base(port: Int): String = "http://127.0.0.1:$port"

    private fun JSONObject.optDoubleOrNull(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        return optDouble(key).takeIf { !it.isNaN() }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .build()
    }
}
