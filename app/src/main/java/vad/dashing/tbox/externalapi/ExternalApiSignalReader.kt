package vad.dashing.tbox.externalapi

import android.os.SystemClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import vad.dashing.tbox.automation.AutomationSignalCatalog
import vad.dashing.tbox.automation.AutomationSignalId
import vad.dashing.tbox.automation.AutomationSignalKey
import vad.dashing.tbox.automation.AutomationSignalReads
import vad.dashing.tbox.automation.AutomationSignalSource
import vad.dashing.tbox.automation.AutomationSignalValue
import vad.dashing.tbox.automation.AutomationSignalValueType

class ExternalApiSignalReader {
    suspend fun read(key: AutomationSignalKey): AutomationSignalValue {
        val flow = AutomationSignalReads.flowFor(key) ?: return AutomationSignalValue.Unavailable
        return withTimeoutOrNull(500) { flow.first() } ?: AutomationSignalValue.Unavailable
    }

    suspend fun readSnapshot(
        ids: List<String>,
        source: AutomationSignalSource,
    ): JSONObject {
        val observedAt = SystemClock.elapsedRealtime()
        val signals = org.json.JSONArray()
        ids.forEach { rawId ->
            val signalId = AutomationSignalId.fromStorageKey(rawId)
            val descriptor = AutomationSignalCatalog.entries.firstOrNull { it.id == signalId }
            val key = signalId?.let { AutomationSignalKey(signal = it, source = source) }
            val supportsSource = descriptor?.sources?.contains(source) == true
            val value = if (key != null && supportsSource) {
                read(key)
            } else {
                AutomationSignalValue.Unavailable
            }
            signals.put(
                signalToJson(
                    id = rawId.trim(),
                    source = source,
                    valueType = descriptor?.id?.valueType ?: AutomationSignalValueType.NUMBER,
                    value = value,
                ),
            )
        }
        return JSONObject()
            .put("observedAtElapsedMillis", observedAt)
            .put("signals", signals)
    }

    companion object {
        fun signalToJson(
            id: String,
            source: AutomationSignalSource,
            valueType: AutomationSignalValueType,
            value: AutomationSignalValue,
        ): JSONObject {
            val json = JSONObject()
                .put("id", id)
                .put("source", source.storageKey)
                .put("valueType", valueType.name.lowercase())
            return when (value) {
                is AutomationSignalValue.Number -> json
                    .put("value", value.value)
                    .put("available", true)

                is AutomationSignalValue.State -> json
                    .put("value", value.value)
                    .put("available", true)

                is AutomationSignalValue.Position -> json
                    .put(
                        "value",
                        JSONObject()
                            .put("latitude", value.latitude)
                            .put("longitude", value.longitude),
                    )
                    .put("available", true)

                AutomationSignalValue.Unavailable -> json
                    .put("value", JSONObject.NULL)
                    .put("available", false)
            }
        }
    }
}
