package vad.dashing.mqtt.wqtt

import vad.dashing.mqtt.ha.CatalogEntity

/**
 * Creates or updates WQTT devices for selected packs, then calls `/api/devices/refresh`.
 * Idempotent by matching existing devices on name + overlapping topics.
 */
class WqttProvisioner(
    private val api: WqttApi = WqttApi(),
) {
    fun provision(
        token: String,
        packs: Set<WqttPackId>,
        entities: List<CatalogEntity>,
        topicPrefix: String,
        deviceId: String,
        room: String,
        types: WqttTypeCatalog? = null,
        onProgress: (String) -> Unit = {},
    ): WqttProvisionReport {
        val cleaned = WqttApi.normalizeToken(token)
        if (cleaned.isBlank()) {
            return WqttProvisionReport(
                devices = emptyList(),
                refreshed = false,
                refreshMessage = "Вставьте токен API WQTT",
            )
        }
        if (packs.isEmpty()) {
            return WqttProvisionReport(
                devices = emptyList(),
                refreshed = false,
                refreshMessage = "Выберите хотя бы один набор",
            )
        }
        onProgress("Читаю типы устройств WQTT")
        val catalog = types ?: runCatching { api.types(cleaned) }.getOrDefault(WqttTypeCatalog())
        val payloads = WqttDeviceMapper.buildDevices(
            packs = packs,
            entities = entities,
            topicPrefix = topicPrefix,
            deviceId = deviceId,
            room = room,
            types = catalog,
        )
        if (payloads.isEmpty()) {
            return WqttProvisionReport(
                devices = emptyList(),
                refreshed = false,
                refreshMessage = "В каталоге Monitor нет сущностей для выбранных наборов",
            )
        }
        onProgress("Читаю список устройств WQTT")
        val existing = runCatching { api.listDevices(cleaned) }.getOrElse { error ->
            return WqttProvisionReport(
                devices = emptyList(),
                refreshed = false,
                refreshMessage = error.message ?: "Не удалось прочитать устройства",
            )
        }
        val detailsById = HashMap<Int, WqttDeviceDetail>()
        fun detailOf(id: Int): WqttDeviceDetail? {
            detailsById[id]?.let { return it }
            return runCatching { api.deviceDetail(cleaned, id) }
                .onSuccess { detailsById[id] = it }
                .getOrNull()
        }

        val results = mutableListOf<WqttDeviceResult>()
        for (payload in payloads) {
            onProgress("Устройство: ${payload.name}")
            val match = findMatch(existing, payload, ::detailOf)
            val result = try {
                if (match == null) {
                    val id = api.createDevice(cleaned, payload)
                    WqttDeviceResult(
                        stableKey = payload.stableKey,
                        name = payload.name,
                        action = WqttDeviceAction.CREATED,
                        deviceId = id,
                        message = "создано",
                    )
                } else if (sameTopics(match, payload)) {
                    WqttDeviceResult(
                        stableKey = payload.stableKey,
                        name = payload.name,
                        action = WqttDeviceAction.SKIPPED,
                        deviceId = match.id,
                        message = "уже есть",
                    )
                } else {
                    val id = api.updateDevice(cleaned, match.id, payload)
                    WqttDeviceResult(
                        stableKey = payload.stableKey,
                        name = payload.name,
                        action = WqttDeviceAction.UPDATED,
                        deviceId = id,
                        message = "обновлено",
                    )
                }
            } catch (error: Exception) {
                WqttDeviceResult(
                    stableKey = payload.stableKey,
                    name = payload.name,
                    action = WqttDeviceAction.FAILED,
                    message = error.message ?: "ошибка",
                )
            }
            results += result
        }

        val anyOk = results.any { it.action != WqttDeviceAction.FAILED }
        var refreshed = false
        var refreshMessage = ""
        if (anyOk) {
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
            refreshMessage = "нечего обновлять: все устройства с ошибкой"
        }

        return WqttProvisionReport(
            devices = results,
            refreshed = refreshed,
            refreshMessage = refreshMessage,
            enabledObjectIds = payloads.flatMap { it.objectIds }.toSet(),
        )
    }

    private fun findMatch(
        existing: List<WqttDeviceSummary>,
        payload: WqttDevicePayload,
        detailOf: (Int) -> WqttDeviceDetail?,
    ): WqttDeviceDetail? {
        val wantedStates = payload.topicStates()
        val wantedCmds = payload.topicCmds()
        val sameName = existing.filter { it.name.equals(payload.name, ignoreCase = true) }
        for (summary in sameName) {
            val detail = detailOf(summary.id) ?: continue
            if (topicsOverlap(detail, wantedStates, wantedCmds)) return detail
        }
        for (summary in existing) {
            val detail = detailOf(summary.id) ?: continue
            if (topicsOverlap(detail, wantedStates, wantedCmds)) return detail
        }
        return sameName.firstOrNull()?.let { detailOf(it.id) }
    }

    private fun sameTopics(existing: WqttDeviceDetail, payload: WqttDevicePayload): Boolean {
        val wantedStates = payload.topicStates()
        val wantedCmds = payload.topicCmds()
        return existing.topicStates == wantedStates && existing.topicCmds == wantedCmds &&
            existing.name.equals(payload.name, ignoreCase = true) &&
            (payload.room.isBlank() || existing.room.equals(payload.room, ignoreCase = true))
    }

    private fun topicsOverlap(
        detail: WqttDeviceDetail,
        wantedStates: Set<String>,
        wantedCmds: Set<String>,
    ): Boolean {
        if (wantedStates.isNotEmpty() && detail.topicStates.any { it in wantedStates }) return true
        if (wantedCmds.isNotEmpty() && detail.topicCmds.any { it in wantedCmds }) return true
        return false
    }

    private fun WqttDevicePayload.topicStates(): Set<String> = buildSet {
        onOff.map { it.topicState }.filter { it.isNotBlank() }.forEach(::add)
        range.map { it.topicState }.filter { it.isNotBlank() }.forEach(::add)
        sensorFloat.map { it.topicState }.filter { it.isNotBlank() }.forEach(::add)
        sensorEvent.map { it.topicState }.filter { it.isNotBlank() }.forEach(::add)
    }

    private fun WqttDevicePayload.topicCmds(): Set<String> = buildSet {
        onOff.map { it.topicCmd }.filter { it.isNotBlank() }.forEach(::add)
        range.map { it.topicCmd }.filter { it.isNotBlank() }.forEach(::add)
    }
}
