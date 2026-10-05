package vad.dashing.tbox.externalapi

import org.json.JSONArray
import org.json.JSONObject

data class ExternalApiPairedClient(
    val clientId: String,
    val clientName: String,
    val tokenHash: String,
    val createdAtEpochMs: Long,
    val lastUsedAtEpochMs: Long = createdAtEpochMs,
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("clientId", clientId)
            .put("clientName", clientName)
            .put("tokenHash", tokenHash)
            .put("createdAtEpochMs", createdAtEpochMs)
            .put("lastUsedAtEpochMs", lastUsedAtEpochMs)

    companion object {
        fun fromJson(json: JSONObject): ExternalApiPairedClient {
            val createdAtEpochMs = json.getLong("createdAtEpochMs")
            return ExternalApiPairedClient(
                clientId = json.getString("clientId"),
                clientName = json.getString("clientName"),
                tokenHash = json.getString("tokenHash"),
                createdAtEpochMs = createdAtEpochMs,
                lastUsedAtEpochMs = json.optLong("lastUsedAtEpochMs", createdAtEpochMs),
            )
        }

        fun encodeList(clients: List<ExternalApiPairedClient>): String {
            val array = JSONArray()
            clients.forEach { array.put(it.toJson()) }
            return array.toString()
        }

        fun decodeList(raw: String): List<ExternalApiPairedClient> {
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || trimmed == "[]") return emptyList()
            val array = JSONArray(trimmed)
            return buildList(array.length()) {
                for (index in 0 until array.length()) {
                    add(fromJson(array.getJSONObject(index)))
                }
            }
        }
    }
}
