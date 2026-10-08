package vad.dashing.mqtt.ha

import org.json.JSONArray
import org.json.JSONObject

enum class HaComponent(val storageKey: String) {
    SENSOR("sensor"),
    BINARY_SENSOR("binary_sensor"),
    SWITCH("switch"),
    NUMBER("number"),
    SELECT("select"),
    BUTTON("button"),
    DEVICE_TRACKER("device_tracker"),
}

enum class EntityGroup(val title: String, val order: Int) {
    CLIMATE("Климат", 0),
    BODY("Кузов", 1),
    MOTION("Движение", 2),
    TRIP("Поездка", 3),
    OTHER("Прочее", 4),
}

data class WritePair(
    val state: String,
    val numberValue: Double?,
    val textValue: String?,
)

data class CanCommandBinding(
    val bus: String,
    val propertyId: Int,
    val operation: String,
    val fixedText: String? = null,
    val fixedNumber: Double? = null,
)

data class CatalogEntity(
    val objectId: String,
    val label: String,
    val description: String,
    val group: EntityGroup,
    val component: HaComponent,
    val unit: String = "",
    val deviceClass: String? = null,
    val signalId: String? = null,
    val source: String? = null,
    val writable: Boolean = false,
    val options: List<String> = emptyList(),
    val pairs: List<WritePair> = emptyList(),
    val numberMin: Double? = null,
    val numberMax: Double? = null,
    val numberStep: Double? = null,
    val canCommand: CanCommandBinding? = null,
    val builtinAction: String? = null,
    val automationId: String? = null,
) {
    val isButton: Boolean
        get() = component == HaComponent.BUTTON

    val needsSignal: Boolean
        get() = signalId != null && component != HaComponent.BUTTON
}

data class AutomationRow(
    val id: String,
    val name: String,
)

private val BUTTON_BUILTINS = setOf(
    "open_menu",
    "finish_and_start_trip",
    "reset_motor_hours",
    "toggle_app_day_night_theme",
    "enable_head_unit_auto_theme",
    "toggle_mirror_adjust_mode",
    "toggle_hide_floating_panels",
    "toggle_floating_panels_enabled",
    "media_previous",
    "media_play_pause",
    "media_play",
    "media_next",
    "media_toggle_like",
    "cycle_mock_location_mode",
    "start_vad_voice",
)

fun buildEntities(catalogJson: String, automations: List<AutomationRow> = emptyList()): List<CatalogEntity> {
    val root = JSONObject(catalogJson)
    val signals = root.optJSONArray("signals") ?: JSONArray()
    val actions = root.optJSONArray("actionTypes") ?: JSONArray()
    val rows = mutableListOf<CatalogEntity>()
    val indexBySignal = HashMap<String, Int>()
    for (i in 0 until signals.length()) {
        val item = signals.optJSONObject(i) ?: continue
        val entity = signalEntity(item) ?: continue
        indexBySignal[entity.signalId.orEmpty()] = rows.size
        rows += entity
    }
    for (i in 0 until actions.length()) {
        val item = actions.optJSONObject(i) ?: continue
        when (item.optString("type")) {
            "can_command" -> mergeCommand(rows, indexBySignal, item)
            "builtin" -> builtinEntity(item)?.let { rows += it }
        }
    }
    automations.forEach { row ->
        if (row.id.isBlank()) return@forEach
        rows += CatalogEntity(
            objectId = "automation_${row.id}",
            label = row.name.ifBlank { "Автоматизация" },
            description = "Выполнить сейчас",
            group = EntityGroup.OTHER,
            component = HaComponent.BUTTON,
            writable = true,
            automationId = row.id,
        )
    }
    return rows.sortedWith(compareBy({ it.group.order }, { it.label.lowercase() }, { it.objectId }))
}

private fun signalEntity(item: JSONObject): CatalogEntity? {
    val id = item.optString("id").trim()
    if (id.isEmpty()) return null
    val label = item.optString("label").ifBlank { id }
    val unit = item.optString("unit")
    val valueType = item.optString("valueType")
    val sources = stringList(item.optJSONArray("sources"))
    val stateOptions = stringList(item.optJSONArray("stateOptions"))
    val source = sources.firstOrNull()
    if (id == "geo_position" || valueType == "position") {
        return CatalogEntity(
            objectId = id,
            label = "Местоположение",
            description = "Координаты машины попадут в брокер и в Home Assistant",
            group = EntityGroup.TRIP,
            component = HaComponent.DEVICE_TRACKER,
            signalId = id,
            source = source ?: "app",
        )
    }
    val binary = valueType == "state" && stateOptions == listOf("off", "on")
    val component = if (binary) HaComponent.BINARY_SENSOR else HaComponent.SENSOR
    return CatalogEntity(
        objectId = id,
        label = label,
        description = if (unit.isNotBlank()) unit else "Только чтение",
        group = groupFor(id),
        component = component,
        unit = unit,
        deviceClass = sensorDeviceClass(unit),
        signalId = id,
        source = source,
        options = stateOptions,
    )
}

private fun mergeCommand(
    rows: MutableList<CatalogEntity>,
    indexBySignal: MutableMap<String, Int>,
    item: JSONObject,
) {
    if (item.optString("safety") == "dangerous") return
    val write = item.optJSONObject("write") ?: return
    val kind = write.optString("kind")
    val pairs = parsePairs(write.optJSONArray("options"))
    if (pairs.isEmpty()) return
    val signalId = item.optString("signalId").takeIf { it.isNotBlank() && !item.isNull("signalId") }
    val bus = item.optString("bus").ifBlank { "vehicle" }
    val propertyId = item.optInt("propertyId")
    val label = item.optString("label").ifBlank { "Команда" }
    if (signalId != null) {
        val index = indexBySignal[signalId] ?: return
        val base = rows[index]
        rows[index] = base.copy(
            component = componentForWrite(kind),
            description = "Состояние и команда",
            writable = true,
            options = pairs.map { it.state },
            pairs = pairs,
            numberMin = write.optDoubleOrNull("min"),
            numberMax = write.optDoubleOrNull("max"),
            numberStep = write.optDoubleOrNull("step"),
            unit = write.optString("unit").ifBlank { base.unit },
            deviceClass = sensorDeviceClass(write.optString("unit").ifBlank { base.unit }),
            canCommand = CanCommandBinding(
                bus = bus,
                propertyId = propertyId,
                operation = "set",
            ),
        )
        return
    }
    if (kind != "pulse") return
    pairs.forEach { pair ->
        val action = when (pair.state) {
            "open" -> "Открыть"
            "close" -> "Закрыть"
            else -> pair.state
        }
        rows += CatalogEntity(
            objectId = "can_${bus}_${propertyId}_${pair.state}",
            label = "$label: $action",
            description = "Кнопка",
            group = groupFor("trunk"),
            component = HaComponent.BUTTON,
            writable = true,
            pairs = listOf(pair),
            canCommand = CanCommandBinding(
                bus = bus,
                propertyId = propertyId,
                operation = "trunk_pulse",
                fixedText = pair.textValue,
                fixedNumber = pair.numberValue,
            ),
        )
    }
}

private fun builtinEntity(item: JSONObject): CatalogEntity? {
    val actionType = item.optString("actionType")
    if (actionType !in BUTTON_BUILTINS) return null
    if (item.optString("safety") == "dangerous") return null
    val label = builtinLabel(actionType)
    return CatalogEntity(
        objectId = "builtin_$actionType",
        label = label,
        description = "Кнопка",
        group = EntityGroup.OTHER,
        component = HaComponent.BUTTON,
        writable = true,
        builtinAction = actionType,
    )
}

private fun componentForWrite(kind: String): HaComponent = when (kind) {
    "binary" -> HaComponent.SWITCH
    "options" -> HaComponent.SELECT
    "number" -> HaComponent.NUMBER
    "pulse" -> HaComponent.BUTTON
    else -> HaComponent.SENSOR
}

fun sensorDeviceClass(unit: String): String? = when (unit) {
    "°C" -> "temperature"
    "км/ч" -> "speed"
    else -> null
}

fun measurementUnit(unit: String): String = when (unit) {
    "км/ч" -> "km/h"
    else -> unit
}

fun groupFor(id: String): EntityGroup {
    val key = id.lowercase()
    return when {
        key.startsWith("hvac_") || key.startsWith("fragrance") || key == "inside_temperature" ->
            EntityGroup.CLIMATE
        key.startsWith("window_") || key.startsWith("door_") || key.startsWith("sun") ||
            key.contains("trunk") || key.contains("seat") || key.contains("wiper") ||
            key.contains("mirror") || key.contains("light") || key.contains("fog") ||
            key.contains("headlight") || key == "steering_wheel_heat" || key.startsWith("hud") ||
            key.startsWith("parking") ->
            EntityGroup.BODY
        key.contains("speed") || key.contains("rpm") || key.contains("gear") ||
            key.contains("drive_mode") || key.contains("steering") || key.contains("pedal") ||
            key in setOf("avh", "hdc", "esp_off", "acc_status", "acc_cruise_state", "ccs_cruise_state") ->
            EntityGroup.MOTION
        key.contains("fuel") || key.contains("odometer") || key.contains("distance") ||
            key.contains("trip") || key == "geo_position" || key.contains("consumption") ||
            key == "voltage" ->
            EntityGroup.TRIP
        else -> EntityGroup.OTHER
    }
}

private fun builtinLabel(actionType: String): String = when (actionType) {
    "media_next" -> "Следующий трек"
    "media_previous" -> "Предыдущий трек"
    "media_play" -> "Воспроизведение"
    "media_play_pause" -> "Пауза"
    "media_toggle_like" -> "Нравится"
    "open_menu" -> "Открыть меню"
    "finish_and_start_trip" -> "Новая поездка"
    "reset_motor_hours" -> "Сбросить моточасы"
    "toggle_app_day_night_theme" -> "Тема день/ночь"
    "enable_head_unit_auto_theme" -> "Автотема ГУ"
    "toggle_mirror_adjust_mode" -> "Регулировка зеркал"
    "toggle_hide_floating_panels" -> "Скрыть панели"
    "toggle_floating_panels_enabled" -> "Плавающие панели"
    "cycle_mock_location_mode" -> "Режим подмены геопозиции"
    "start_vad_voice" -> "Голосовой помощник"
    else -> actionType
}

private fun parsePairs(array: JSONArray?): List<WritePair> {
    if (array == null) return emptyList()
    return buildList {
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val state = item.optString("state")
            if (state.isEmpty()) continue
            val raw = item.opt("value")
            val number = (raw as? Number)?.toDouble()
            val text = (raw as? String)
            if (number == null && text == null) continue
            add(WritePair(state, number, text))
        }
    }
}

private fun stringList(array: JSONArray?): List<String> {
    if (array == null) return emptyList()
    return buildList {
        for (i in 0 until array.length()) {
            val value = array.optString(i)
            if (value.isNotEmpty()) add(value)
        }
    }
}

private fun JSONObject.optDoubleOrNull(key: String): Double? {
    if (!has(key) || isNull(key)) return null
    val value = optDouble(key)
    return value.takeIf { !it.isNaN() }
}
