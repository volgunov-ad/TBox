package vad.dashing.mqtt.wqtt

import vad.dashing.mqtt.ha.Topics

/**
 * Finds and deletes WQTT cabinet devices that look like TBox MQTT provisioned ones.
 *
 * Safety: match by MQTT topics under the current `topicPrefix/deviceId` base.
 * Room «Автомобиль» alone is never enough — only topic (or locally remembered ids).
 */
class WqttCleanup(
    private val api: WqttApi = WqttApi(),
) {
    fun findTboxDevices(
        token: String,
        topicPrefix: String,
        deviceId: String,
        knownIds: Set<Int> = emptySet(),
        onProgress: (String) -> Unit = {},
    ): List<WqttCleanupCandidate> {
        val cleaned = WqttApi.normalizeToken(token)
        if (cleaned.isBlank()) return emptyList()
        onProgress("Читаю список устройств WQTT")
        val existing = api.listDevices(cleaned)
        if (existing.isEmpty()) return emptyList()
        val base = Topics.base(topicPrefix, deviceId)
        val matched = LinkedHashMap<Int, WqttCleanupCandidate>()
        for ((index, summary) in existing.withIndex()) {
            onProgress("Проверяю устройство ${index + 1} из ${existing.size}")
            if (summary.id in knownIds) {
                matched[summary.id] = WqttCleanupCandidate(
                    id = summary.id,
                    name = summary.name,
                    room = summary.room,
                )
                continue
            }
            val detail = runCatching { api.deviceDetail(cleaned, summary.id) }.getOrNull()
                ?: continue
            if (WqttTboxDeviceMatch.matches(detail, base, knownIds)) {
                matched[detail.id] = WqttCleanupCandidate(
                    id = detail.id,
                    name = detail.name.ifBlank { summary.name },
                    room = detail.room.ifBlank { summary.room },
                )
            }
        }
        return matched.values.toList()
    }

    fun deleteDevices(
        token: String,
        candidates: List<WqttCleanupCandidate>,
        onProgress: (String) -> Unit = {},
    ): WqttCleanupReport {
        val cleaned = WqttApi.normalizeToken(token)
        if (cleaned.isBlank()) {
            return WqttCleanupReport(
                deleted = 0,
                failed = 0,
                candidates = candidates,
                refreshMessage = "Вставьте токен API WQTT",
            )
        }
        if (candidates.isEmpty()) {
            return WqttCleanupReport(
                deleted = 0,
                failed = 0,
                candidates = emptyList(),
                refreshMessage = "Нет устройств TBox для удаления",
            )
        }
        var deleted = 0
        var failed = 0
        val errors = mutableListOf<String>()
        for ((index, candidate) in candidates.withIndex()) {
            onProgress("Удаляю ${index + 1} из ${candidates.size}: ${candidate.name}")
            try {
                api.deleteDevice(cleaned, candidate.id)
                deleted += 1
            } catch (error: Exception) {
                failed += 1
                errors += "${candidate.name}: ${error.message ?: "ошибка"}"
            }
        }
        var refreshed = false
        var refreshMessage = ""
        if (deleted > 0) {
            onProgress("Обновляю список в Яндексе")
            try {
                val status = api.refresh(cleaned)
                refreshed = true
                refreshMessage = if (status > 0) {
                    "Яндекс ответил $status"
                } else {
                    "список устройств обновлён"
                }
            } catch (error: Exception) {
                refreshMessage = error.message ?: "refresh не выполнен"
            }
        } else {
            refreshMessage = "нечего обновлять: ничего не удалено"
        }
        return WqttCleanupReport(
            deleted = deleted,
            failed = failed,
            candidates = candidates,
            refreshed = refreshed,
            refreshMessage = refreshMessage,
            errors = errors,
        )
    }
}

/** Pure topic / id matching for unit tests. */
object WqttTboxDeviceMatch {
    /**
     * True when any `topic_state` / `topic_cmd` is under [topicBase], or [knownIds] contains
     * the device id. Room «Автомобиль» alone never matches (too weak without topics).
     */
    fun matches(
        detail: WqttDeviceDetail,
        topicBase: String,
        knownIds: Set<Int> = emptySet(),
    ): Boolean {
        if (detail.id > 0 && detail.id in knownIds) return true
        return hasTopicUnderBase(detail, topicBase)
    }

    fun hasTopicUnderBase(detail: WqttDeviceDetail, topicBase: String): Boolean {
        val base = topicBase.trim().trim('/')
        if (base.isEmpty()) return false
        val topics = detail.topicStates + detail.topicCmds
        return topics.any { topicUnderBase(it, base) }
    }

    fun topicUnderBase(topic: String, topicBase: String): Boolean {
        val cleaned = topic.trim().trim('/')
        val base = topicBase.trim().trim('/')
        if (cleaned.isEmpty() || base.isEmpty()) return false
        return cleaned == base || cleaned.startsWith("$base/")
    }
}
