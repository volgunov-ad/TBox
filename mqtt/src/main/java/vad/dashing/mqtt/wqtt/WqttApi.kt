package vad.dashing.mqtt.wqtt

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Client for https://dash.wqtt.ru/api/…
 * Auth: `Authorization: Token <token>` (pasteable token from the WQTT cabinet).
 */
class WqttApi(
    private val client: OkHttpClient = defaultClient(),
    private val baseUrl: String = DEFAULT_BASE,
) {
    fun broker(token: String): WqttBrokerInfo {
        val json = JSONObject(get("/api/broker", token))
        return WqttBrokerInfo(
            server = json.optString("server"),
            port = json.optInt("port"),
            portTls = json.optInt("port_tls"),
            websocket = json.optInt("websocket"),
            user = json.optString("user"),
            password = json.optString("password"),
        )
    }

    fun listDevices(token: String): List<WqttDeviceSummary> {
        val json = JSONObject(get("/api/devices", token))
        val array = json.optJSONArray("devices") ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optInt("id")
                if (id <= 0) continue
                add(
                    WqttDeviceSummary(
                        id = id,
                        type = item.optInt("type"),
                        name = item.optString("name"),
                        room = item.optString("room"),
                    ),
                )
            }
        }
    }

    fun deviceDetail(token: String, id: Int): WqttDeviceDetail {
        val json = JSONObject(get("/api/devices/$id", token))
        val device = json.optJSONObject("device")
            ?: throw WqttApiException(0, "WQTT не вернул устройство $id")
        return parseDetail(device)
    }

    fun createDevice(token: String, payload: WqttDevicePayload): Int {
        val body = post("/api/devices", token, payload.toJson().toString())
        return deviceIdOf(body)
    }

    fun updateDevice(token: String, id: Int, payload: WqttDevicePayload): Int {
        val body = put("/api/devices/$id", token, payload.toJson().toString())
        return deviceIdOf(body)
    }

    fun refresh(token: String): Int {
        val json = JSONObject(get("/api/devices/refresh", token))
        return json.optInt("yandex_status_code", 0)
    }

    fun types(token: String): WqttTypeCatalog {
        val json = JSONObject(get("/api/devices/types", token))
        val root = json.optJSONObject("types") ?: json
        return WqttTypeCatalog(
            devices = namedList(root.optJSONArray("device")),
            ranges = namedList(root.optJSONArray("range")),
            sensorFloats = namedList(root.optJSONArray("sensor_float")),
            sensorEvents = namedList(root.optJSONArray("sensor_event")),
            modes = namedList(root.optJSONArray("mode")),
            toggles = namedList(root.optJSONArray("toggle")),
            colors = namedList(root.optJSONArray("color")),
        )
    }

    /** Connectivity check: broker info is enough and cheap. */
    fun testConnection(token: String): WqttBrokerInfo = broker(token)

    private fun namedList(array: JSONArray?): List<WqttNamedType> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = when {
                    item.has("id") -> item.optInt("id")
                    item.has("type") -> item.optInt("type")
                    item.has("value") -> item.optInt("value")
                    else -> -1
                }
                val name = item.optString("name")
                    .ifBlank { item.optString("title") }
                    .ifBlank { item.optString("label") }
                if (id < 0 || name.isBlank()) continue
                add(WqttNamedType(id, name))
            }
        }
    }

    private fun parseDetail(device: JSONObject): WqttDeviceDetail {
        val states = linkedSetOf<String>()
        val cmds = linkedSetOf<String>()
        collectTopics(device.optJSONArray("on_off"), states, cmds)
        collectTopics(device.optJSONArray("range"), states, cmds)
        collectTopics(device.optJSONArray("color"), states, cmds)
        collectTopics(device.optJSONArray("mode"), states, cmds)
        collectTopics(device.optJSONArray("toggle"), states, cmds)
        collectTopics(device.optJSONArray("sensor_float"), states, cmds)
        collectTopics(device.optJSONArray("sensor_event"), states, cmds)
        return WqttDeviceDetail(
            id = device.optInt("id"),
            type = device.optInt("type"),
            name = device.optString("name"),
            room = device.optString("room"),
            topicStates = states,
            topicCmds = cmds,
        )
    }

    private fun collectTopics(array: JSONArray?, states: MutableSet<String>, cmds: MutableSet<String>) {
        if (array == null) return
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            item.optString("topic_state").trim().takeIf { it.isNotEmpty() }?.let(states::add)
            item.optString("topic_cmd").trim().takeIf { it.isNotEmpty() }?.let(cmds::add)
        }
    }

    private fun deviceIdOf(body: String): Int {
        val json = JSONObject(body)
        val detail = json.optJSONObject("detail")
        val id = detail?.optInt("device_id") ?: json.optInt("device_id")
        if (id <= 0) throw WqttApiException(0, "WQTT не вернул device_id")
        return id
    }

    private fun get(path: String, token: String): String =
        execute(
            Request.Builder()
                .url(baseUrl + path)
                .header("Accept", "application/json")
                .header("Authorization", authHeader(token))
                .get()
                .build(),
        )

    private fun post(path: String, token: String, json: String): String =
        execute(
            Request.Builder()
                .url(baseUrl + path)
                .header("Accept", "application/json")
                .header("Authorization", authHeader(token))
                .post(json.toRequestBody(JSON))
                .build(),
        )

    private fun put(path: String, token: String, json: String): String =
        execute(
            Request.Builder()
                .url(baseUrl + path)
                .header("Accept", "application/json")
                .header("Authorization", authHeader(token))
                .put(json.toRequestBody(JSON))
                .build(),
        )

    private fun execute(request: Request): String {
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw WqttApiException(
                        httpStatus = response.code,
                        message = errorMessage(body, response.code),
                    )
                }
                return body
            }
        } catch (error: WqttApiException) {
            throw error
        } catch (error: Exception) {
            throw WqttApiException(0, error.message ?: "WQTT недоступен")
        }
    }

    private fun errorMessage(body: String, code: Int): String {
        val parsed = runCatching {
            val json = JSONObject(body)
            json.optString("detail")
                .ifBlank { json.optString("message") }
                .ifBlank { json.optJSONArray("non_field_errors")?.optString(0).orEmpty() }
        }.getOrDefault("")
        return parsed.ifBlank { "HTTP $code" }.take(200)
    }

    companion object {
        const val DEFAULT_BASE = "https://dash.wqtt.ru"
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun authHeader(token: String): String {
            val cleaned = normalizeToken(token)
            return if (cleaned.startsWith("Token ", ignoreCase = true)) cleaned else "Token $cleaned"
        }

        fun normalizeToken(raw: String): String =
            raw.trim()
                .replace(Regex("^Bearer\\s+", RegexOption.IGNORE_CASE), "")
                .replace(Regex("^Token\\s+", RegexOption.IGNORE_CASE), "")
                .trim()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}

/** JSON body for POST/PUT (no [WqttDevicePayload.stableKey] / objectIds). */
fun WqttDevicePayload.toJson(): JSONObject {
    val json = JSONObject()
        .put("name", name)
        .put("type", type)
        .put("room", room)
    if (onOff.isNotEmpty()) {
        val array = JSONArray()
        onOff.forEach { item ->
            array.put(
                JSONObject()
                    .put("topic_cmd", item.topicCmd)
                    .put("topic_state", item.topicState)
                    .put("cmd_on", item.cmdOn)
                    .put("cmd_off", item.cmdOff),
            )
        }
        json.put("on_off", array)
    }
    if (range.isNotEmpty()) {
        val array = JSONArray()
        range.forEach { item ->
            array.put(
                JSONObject()
                    .put("type", item.type)
                    .put("topic_cmd", item.topicCmd)
                    .put("topic_state", item.topicState)
                    .put("min", item.min)
                    .put("max", item.max)
                    .put("precision", item.precision)
                    .put("multiplier", item.multiplier),
            )
        }
        json.put("range", array)
    }
    if (sensorFloat.isNotEmpty()) {
        val array = JSONArray()
        sensorFloat.forEach { item ->
            array.put(
                JSONObject()
                    .put("type", item.type)
                    .put("topic_state", item.topicState),
            )
        }
        json.put("sensor_float", array)
    }
    if (sensorEvent.isNotEmpty()) {
        val array = JSONArray()
        sensorEvent.forEach { item ->
            array.put(
                JSONObject()
                    .put("type", item.type)
                    .put("topic_state", item.topicState),
            )
        }
        json.put("sensor_event", array)
    }
    return json
}
