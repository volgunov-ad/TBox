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
import vad.dashing.tbox.automation.AutomationSignalStateEncoding
import vad.dashing.tbox.automation.AutomationSignalValue
import vad.dashing.tbox.automation.AutomationSignalValueType
import vad.dashing.tbox.automation.huInterestForSignal
import vad.dashing.tbox.mbcan.BodyComfortDomain
import vad.dashing.tbox.mbcan.MbCanSignal
import vad.dashing.tbox.mbcan.UniversalCanRepository

class ExternalApiSignalReader {
    private val headUnitInterestLock = Any()
    private var headUnitInterest: Set<MbCanSignal> = emptySet()

    suspend fun read(key: AutomationSignalKey): AutomationSignalValue {
        val flow = AutomationSignalReads.flowFor(key) ?: return AutomationSignalValue.Unavailable
        return withTimeoutOrNull(500) { flow.first() } ?: AutomationSignalValue.Unavailable
    }

    suspend fun readSnapshot(
        ids: List<String>,
        source: AutomationSignalSource,
    ): JSONObject {
        if (source == AutomationSignalSource.HEAD_UNIT) {
            ensureHeadUnitSignals(ids)
        }
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

    /**
     * Widget interests do not cover every signal the page asks for. Without this,
     * a zone that has no tile on the head unit stays empty in the snapshot.
     */
    private suspend fun ensureHeadUnitSignals(ids: List<String>) {
        val signals = ids.mapNotNull { rawId ->
            AutomationSignalId.fromStorageKey(rawId)
                ?.takeIf {
                    AutomationSignalCatalog.resolveSource(it, AutomationSignalSource.HEAD_UNIT) ==
                        AutomationSignalSource.HEAD_UNIT
                }
                ?.let { huInterestForSignal(it) }
        }.toSet()
        if (signals.isEmpty()) return
        val changed = synchronized(headUnitInterestLock) {
            if (signals == headUnitInterest) {
                false
            } else {
                headUnitInterest = signals
                true
            }
        }
        if (changed) {
            UniversalCanRepository.setSourceSignals(HEAD_UNIT_SOURCE_ID, signals)
        }
        UniversalCanRepository.refreshSignalsNow(signals)
    }

    companion object {
        private const val HEAD_UNIT_SOURCE_ID = "external-api"

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
                    .also {
                        val signal = AutomationSignalId.fromStorageKey(id)
                        if (signal != null && AutomationSignalStateEncoding.isWindowSignal(signal)) {
                            it.put("open", BodyComfortDomain.windowStateOpen(value.value))
                        }
                    }

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
