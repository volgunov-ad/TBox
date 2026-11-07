package vad.dashing.mqtt.wqtt

import vad.dashing.mqtt.ha.CatalogEntity
import vad.dashing.mqtt.ha.HaComponent
import vad.dashing.mqtt.ha.MEDIA_OBJECT_ID
import vad.dashing.mqtt.ha.Topics
import vad.dashing.mqtt.ha.WritePair

enum class WqttPackId {
    CLIMATE,
    SENSORS,
    BODY,
}

data class WqttPack(
    val id: WqttPackId,
    val title: String,
    val description: String,
    val warns: Boolean = false,
)

data class WqttSkippedEntity(
    val objectId: String,
    val name: String,
    val reason: String,
)

data class WqttMapResult(
    val devices: List<WqttDevicePayload>,
    val skipped: List<WqttSkippedEntity> = emptyList(),
)

object WqttPacks {
    val ALL = listOf(
        WqttPack(
            id = WqttPackId.CLIMATE,
            title = "Климат",
            description = "Отмечает питание и уставку; при создании — термостат для Алисы",
        ),
        WqttPack(
            id = WqttPackId.SENSORS,
            title = "Датчики",
            description = "Улица, салон, топливо %, запас хода",
        ),
        WqttPack(
            id = WqttPackId.BODY,
            title = "Кузов",
            description = "Окна и багажник — голосовые команды кузова",
            warns = true,
        ),
    )

    fun byId(id: WqttPackId): WqttPack = ALL.first { it.id == id }

    /** ObjectIds a pack convenience toggle should check/uncheck in the entity list. */
    fun objectIdsForPack(id: WqttPackId, entities: List<CatalogEntity>): Set<String> {
        val known = entities.map { it.objectId }.toSet()
        return when (id) {
            WqttPackId.CLIMATE -> setOf(HVAC_POWER, HVAC_TEMP_LEFT).filter { it in known }.toSet()
            WqttPackId.SENSORS -> SENSOR_OBJECT_IDS.filter { it in known }.toSet()
            WqttPackId.BODY -> bodyObjectIds(entities).filter { it in known }.toSet()
        }
    }

    fun isPackFullySelected(id: WqttPackId, selected: Set<String>, entities: List<CatalogEntity>): Boolean {
        val ids = objectIdsForPack(id, entities)
        return ids.isNotEmpty() && selected.containsAll(ids)
    }

    private fun bodyObjectIds(entities: List<CatalogEntity>): Set<String> = buildSet {
        addAll(BODY_WINDOWS)
        entities.forEach { entity ->
            if (entity.isButton && isTrunkButton(entity)) add(entity.objectId)
            if (entity.objectId == "trunk_door") add(entity.objectId)
        }
    }
}

private const val HVAC_POWER = "hvac_power"
private const val HVAC_TEMP_LEFT = "hvac_temperature_left"
private const val CLIMATE_KEY = "climate_thermostat"

private val SENSOR_OBJECT_IDS = setOf(
    "outside_temperature",
    "inside_temperature",
    "fuel_level_percent",
    "distance_to_empty_km",
)

private val BODY_WINDOWS = listOf(
    "window_front_left",
    "window_front_right",
    "window_rear_left",
    "window_rear_right",
)

/**
 * Builds WQTT payloads from the **currently selected** MQTT entities.
 * SELECT expands to state sensor + one button per writable option.
 * Climate: optional composite thermostat **plus** 1:1 cards for the same entities.
 */
object WqttDeviceMapper {
    const val DEFAULT_ROOM = "Автомобиль"

    fun buildFromSelected(
        selectedObjectIds: Set<String>,
        entities: List<CatalogEntity>,
        topicPrefix: String,
        deviceId: String,
        room: String,
        types: WqttTypeCatalog,
        /** When true and both climate entities are selected, also emit the composite thermostat. */
        compositeClimate: Boolean = true,
    ): WqttMapResult {
        val byId = entities.associateBy { it.objectId }
        val base = Topics.base(topicPrefix, deviceId)
        val roomName = room.trim().ifEmpty { DEFAULT_ROOM }
        val selected = selectedObjectIds.filter { it.isNotBlank() }.toSet()
        if (selected.isEmpty()) {
            return WqttMapResult(devices = emptyList(), skipped = emptyList())
        }

        val devices = mutableListOf<WqttDevicePayload>()
        val skipped = mutableListOf<WqttSkippedEntity>()

        val power = byId[HVAC_POWER]
        val temp = byId[HVAC_TEMP_LEFT]
        if (compositeClimate &&
            HVAC_POWER in selected &&
            HVAC_TEMP_LEFT in selected &&
            power != null &&
            temp != null &&
            temp.component == HaComponent.NUMBER
        ) {
            devices += climateComposite(power, temp, base, roomName, types)
        }

        selected.sorted().forEach { objectId ->
            val entity = byId[objectId]
            if (entity == null) {
                skipped += WqttSkippedEntity(objectId, objectId, "нет в каталоге Monitor")
                return@forEach
            }
            when (val mapped = mapEntity(entity, base, roomName, types)) {
                is MapOutcome.Devices -> devices += mapped.payloads
                is MapOutcome.Skip -> skipped += WqttSkippedEntity(
                    objectId = entity.objectId,
                    name = entity.label.ifBlank { entity.objectId },
                    reason = mapped.reason,
                )
            }
        }
        return WqttMapResult(devices = devices, skipped = skipped)
    }

    /** Pack objectIds for tests / UI helpers. */
    fun objectIdsForPacks(packs: Set<WqttPackId>, entities: List<CatalogEntity>): Set<String> =
        packs.flatMap { WqttPacks.objectIdsForPack(it, entities) }.toSet()

    private fun climateComposite(
        power: CatalogEntity,
        temp: CatalogEntity,
        base: String,
        room: String,
        types: WqttTypeCatalog,
    ): WqttDevicePayload = WqttDevicePayload(
        stableKey = CLIMATE_KEY,
        name = "Климат",
        type = WqttTypeResolver.thermostatType(types),
        room = room,
        onOff = listOf(
            WqttOnOff(
                topicCmd = Topics.set(base, power.objectId),
                topicState = Topics.state(base, power.objectId),
                cmdOn = "ON",
                cmdOff = "OFF",
            ),
        ),
        range = listOf(
            WqttRange(
                type = WqttTypeResolver.temperatureRangeType(types),
                topicCmd = Topics.set(base, temp.objectId),
                topicState = Topics.state(base, temp.objectId),
                min = temp.numberMin ?: 16.0,
                max = temp.numberMax ?: 30.0,
                precision = temp.numberStep ?: 0.5,
                multiplier = 1.0,
            ),
        ),
        objectIds = setOf(power.objectId, temp.objectId),
    )

    private fun mapEntity(
        entity: CatalogEntity,
        base: String,
        room: String,
        types: WqttTypeCatalog,
    ): MapOutcome {
        if (entity.objectId == MEDIA_OBJECT_ID || entity.media != null) {
            return MapOutcome.Skip("карточка «Музыка» — составной JSON, не тип WQTT")
        }
        return when (entity.component) {
            HaComponent.SWITCH -> MapOutcome.Devices(listOf(switchDevice(entity, base, room, types)))
            HaComponent.BINARY_SENSOR -> MapOutcome.Devices(listOf(binarySensorDevice(entity, base, room, types)))
            HaComponent.SENSOR -> mapSensor(entity, base, room, types)
            HaComponent.NUMBER -> MapOutcome.Devices(listOf(numberDevice(entity, base, room, types)))
            HaComponent.SELECT -> mapSelect(entity, base, room, types)
            HaComponent.BUTTON -> MapOutcome.Devices(listOf(buttonDevice(entity, base, room, types)))
            HaComponent.DEVICE_TRACKER -> MapOutcome.Skip("местоположение — нет типа WQTT")
        }
    }

    private fun mapSensor(
        entity: CatalogEntity,
        base: String,
        room: String,
        types: WqttTypeCatalog,
    ): MapOutcome {
        val unit = entity.unit.trim()
        val temperature = unit == "°C" || entity.deviceClass == "temperature" ||
            entity.objectId.contains("temperature", ignoreCase = true)
        if (temperature || unit.isNotEmpty() || looksNumericSensor(entity)) {
            val floatType = if (temperature) {
                WqttTypeResolver.temperatureFloatType(types)
            } else {
                WqttTypeResolver.genericFloatType(types)
            }
            val deviceType = if (temperature) {
                WqttTypeResolver.temperatureSensorType(types)
            } else {
                WqttTypeResolver.floatSensorType(types)
            }
            return MapOutcome.Devices(
                listOf(
                    WqttDevicePayload(
                        stableKey = "entity_${entity.objectId}",
                        name = entity.label.ifBlank { entity.objectId },
                        type = deviceType,
                        room = room,
                        sensorFloat = listOf(
                            WqttSensorFloat(
                                type = floatType,
                                topicState = Topics.state(base, entity.objectId),
                            ),
                        ),
                        objectIds = setOf(entity.objectId),
                    ),
                ),
            )
        }
        if (entity.options.isNotEmpty()) {
            return MapOutcome.Devices(listOf(eventSensorDevice(entity, base, room, types)))
        }
        return MapOutcome.Skip("текстовый датчик без единицы — нет типа WQTT")
    }

    /**
     * SELECT → state sensor + one WQTT button per writable [WritePair].
     * Button payload is the option **state** key (`opened` / `vent` / `ECO` …) that
     * `commandToInvoke` already accepts on the entity set topic — not PRESS.
     */
    private fun mapSelect(
        entity: CatalogEntity,
        base: String,
        room: String,
        types: WqttTypeCatalog,
    ): MapOutcome {
        val label = entity.label.ifBlank { entity.objectId }
        val out = mutableListOf<WqttDevicePayload>()
        out += eventSensorDevice(
            entity = entity,
            base = base,
            room = room,
            types = types,
            stableKey = "entity_${entity.objectId}_state",
            name = label,
        )
        if (entity.writable) {
            val pairs = entity.pairs.ifEmpty {
                entity.options.map { WritePair(it, null, it) }
            }
            if (pairs.isEmpty()) {
                return MapOutcome.Devices(out)
            }
            pairs.forEach { pair ->
                val action = optionActionLabel(pair.state)
                out += WqttDevicePayload(
                    stableKey = "entity_${entity.objectId}_opt_${Topics.safeObjectId(pair.state)}",
                    name = "$label: $action",
                    type = WqttTypeResolver.buttonType(types),
                    room = room,
                    onOff = listOf(
                        WqttOnOff(
                            topicCmd = Topics.set(base, entity.objectId),
                            topicState = "",
                            cmdOn = pair.state,
                            cmdOff = pair.state,
                        ),
                    ),
                    objectIds = setOf(entity.objectId),
                )
            }
        }
        return MapOutcome.Devices(out)
    }

    private fun switchDevice(
        entity: CatalogEntity,
        base: String,
        room: String,
        types: WqttTypeCatalog,
    ): WqttDevicePayload = WqttDevicePayload(
        stableKey = "entity_${entity.objectId}",
        name = entity.label.ifBlank { entity.objectId },
        type = WqttTypeResolver.switchType(types),
        room = room,
        onOff = listOf(
            WqttOnOff(
                topicCmd = if (entity.writable) Topics.set(base, entity.objectId) else "",
                topicState = Topics.state(base, entity.objectId),
                cmdOn = "ON",
                cmdOff = "OFF",
            ),
        ),
        objectIds = setOf(entity.objectId),
    )

    private fun binarySensorDevice(
        entity: CatalogEntity,
        base: String,
        room: String,
        types: WqttTypeCatalog,
    ): WqttDevicePayload {
        val openLike = entity.objectId.contains("door") ||
            entity.objectId.contains("trunk") ||
            entity.objectId.contains("window") ||
            entity.objectId.contains("hood")
        if (openLike) {
            return eventSensorDevice(entity, base, room, types)
        }
        return WqttDevicePayload(
            stableKey = "entity_${entity.objectId}",
            name = entity.label.ifBlank { entity.objectId },
            type = WqttTypeResolver.switchType(types),
            room = room,
            onOff = listOf(
                WqttOnOff(
                    topicCmd = "",
                    topicState = Topics.state(base, entity.objectId),
                    cmdOn = "ON",
                    cmdOff = "OFF",
                ),
            ),
            objectIds = setOf(entity.objectId),
        )
    }

    private fun eventSensorDevice(
        entity: CatalogEntity,
        base: String,
        room: String,
        types: WqttTypeCatalog,
        stableKey: String = "entity_${entity.objectId}",
        name: String = entity.label.ifBlank { entity.objectId },
    ): WqttDevicePayload = WqttDevicePayload(
        stableKey = stableKey,
        name = name,
        type = WqttTypeResolver.floatSensorType(types),
        room = room,
        sensorEvent = listOf(
            WqttSensorEvent(
                type = WqttTypeResolver.openEventType(types),
                topicState = Topics.state(base, entity.objectId),
            ),
        ),
        objectIds = setOf(entity.objectId),
    )

    private fun numberDevice(
        entity: CatalogEntity,
        base: String,
        room: String,
        types: WqttTypeCatalog,
    ): WqttDevicePayload {
        val temperature = entity.unit == "°C" || entity.deviceClass == "temperature" ||
            entity.objectId.contains("temperature", ignoreCase = true)
        if (!entity.writable) {
            val floatType = if (temperature) {
                WqttTypeResolver.temperatureFloatType(types)
            } else {
                WqttTypeResolver.genericFloatType(types)
            }
            val deviceType = if (temperature) {
                WqttTypeResolver.temperatureSensorType(types)
            } else {
                WqttTypeResolver.floatSensorType(types)
            }
            return WqttDevicePayload(
                stableKey = "entity_${entity.objectId}",
                name = entity.label.ifBlank { entity.objectId },
                type = deviceType,
                room = room,
                sensorFloat = listOf(
                    WqttSensorFloat(
                        type = floatType,
                        topicState = Topics.state(base, entity.objectId),
                    ),
                ),
                objectIds = setOf(entity.objectId),
            )
        }
        // Range lives on thermostat/light-like device types in WQTT.
        return WqttDevicePayload(
            stableKey = "entity_${entity.objectId}",
            name = entity.label.ifBlank { entity.objectId },
            type = WqttTypeResolver.thermostatType(types),
            room = room,
            range = listOf(
                WqttRange(
                    type = if (temperature) {
                        WqttTypeResolver.temperatureRangeType(types)
                    } else {
                        WqttTypeResolver.FALLBACK_RANGE_BRIGHTNESS
                    },
                    topicCmd = Topics.set(base, entity.objectId),
                    topicState = Topics.state(base, entity.objectId),
                    min = entity.numberMin ?: 0.0,
                    max = entity.numberMax ?: 100.0,
                    precision = entity.numberStep ?: 1.0,
                    multiplier = 1.0,
                ),
            ),
            objectIds = setOf(entity.objectId),
        )
    }

    private fun buttonDevice(
        entity: CatalogEntity,
        base: String,
        room: String,
        types: WqttTypeCatalog,
    ): WqttDevicePayload = WqttDevicePayload(
        stableKey = "entity_${entity.objectId}",
        name = entity.label.ifBlank { entity.objectId },
        type = WqttTypeResolver.buttonType(types),
        room = room,
        onOff = listOf(
            WqttOnOff(
                topicCmd = Topics.set(base, entity.objectId),
                topicState = "",
                cmdOn = "PRESS",
                cmdOff = "PRESS",
            ),
        ),
        objectIds = setOf(entity.objectId),
    )

    private fun looksNumericSensor(entity: CatalogEntity): Boolean {
        val unit = entity.unit.trim()
        if (unit.isEmpty()) return false
        return unit in setOf("%", "км", "км/ч", "об/мин", "В", "л", "л/100км", "бар", "кПа") ||
            entity.objectId.contains("fuel") ||
            entity.objectId.contains("speed") ||
            entity.objectId.contains("odometer") ||
            entity.objectId.contains("distance") ||
            entity.objectId.contains("voltage") ||
            entity.objectId.contains("rpm")
    }

    /** Russian action labels for common window/cover/mode option keys. */
    internal fun optionActionLabel(state: String): String = when (state.lowercase()) {
        "opened", "open" -> "открыть"
        "closed", "close" -> "закрыть"
        "vent" -> "щель"
        "comfort_open", "comfort" -> "comfort"
        else -> state
    }

    private sealed class MapOutcome {
        data class Devices(val payloads: List<WqttDevicePayload>) : MapOutcome()
        data class Skip(val reason: String) : MapOutcome()
    }
}

private fun isTrunkButton(entity: CatalogEntity): Boolean =
    entity.canCommand?.operation == "trunk_pulse" ||
        entity.objectId.contains("trunk", ignoreCase = true) ||
        entity.label.contains("багажник", ignoreCase = true)
