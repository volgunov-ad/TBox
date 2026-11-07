package vad.dashing.mqtt.wqtt

import vad.dashing.mqtt.ha.CatalogEntity
import vad.dashing.mqtt.ha.HaComponent
import vad.dashing.mqtt.ha.Topics

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

object WqttPacks {
    val ALL = listOf(
        WqttPack(
            id = WqttPackId.CLIMATE,
            title = "Климат",
            description = "Один термостат: питание климата и уставка слева",
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
}

/**
 * Builds WQTT device payloads for selected packs from the live TBox entity list.
 * Missing entities are skipped (HU may not expose every signal).
 */
object WqttDeviceMapper {
    const val DEFAULT_ROOM = "Автомобиль"
    private const val CLIMATE_KEY = "climate_thermostat"
    private const val HVAC_POWER = "hvac_power"
    private const val HVAC_TEMP_LEFT = "hvac_temperature_left"

    private val SENSOR_SPECS = listOf(
        SensorSpec("outside_temperature", "Температура снаружи", temperature = true),
        SensorSpec("inside_temperature", "Температура в салоне", temperature = true),
        SensorSpec("fuel_level_percent", "Топливо", temperature = false),
        SensorSpec("distance_to_empty_km", "Запас хода", temperature = false),
    )

    private val BODY_WINDOWS = listOf(
        "window_front_left",
        "window_front_right",
        "window_rear_left",
        "window_rear_right",
    )

    fun buildDevices(
        packs: Set<WqttPackId>,
        entities: List<CatalogEntity>,
        topicPrefix: String,
        deviceId: String,
        room: String,
        types: WqttTypeCatalog,
    ): List<WqttDevicePayload> {
        val byId = entities.associateBy { it.objectId }
        val base = Topics.base(topicPrefix, deviceId)
        val roomName = room.trim().ifEmpty { DEFAULT_ROOM }
        val out = mutableListOf<WqttDevicePayload>()
        if (WqttPackId.CLIMATE in packs) {
            climateDevice(byId, base, roomName, types)?.let { out += it }
        }
        if (WqttPackId.SENSORS in packs) {
            out += sensorDevices(byId, base, roomName, types)
        }
        if (WqttPackId.BODY in packs) {
            out += bodyDevices(byId, base, roomName, types)
        }
        return out
    }

    /** ObjectIds that a pack needs enabled in local MQTT selection. */
    fun objectIdsForPacks(packs: Set<WqttPackId>, entities: List<CatalogEntity>): Set<String> {
        val devices = buildDevices(
            packs = packs,
            entities = entities,
            topicPrefix = Topics.DEFAULT_TOPIC_PREFIX,
            deviceId = Topics.DEFAULT_DEVICE_ID,
            room = DEFAULT_ROOM,
            types = WqttTypeCatalog(),
        )
        return devices.flatMap { it.objectIds }.toSet()
    }

    private fun climateDevice(
        byId: Map<String, CatalogEntity>,
        base: String,
        room: String,
        types: WqttTypeCatalog,
    ): WqttDevicePayload? {
        val power = byId[HVAC_POWER] ?: return null
        val temp = byId[HVAC_TEMP_LEFT]
        val objectIds = buildSet {
            add(power.objectId)
            if (temp != null) add(temp.objectId)
        }
        val onOff = WqttOnOff(
            topicCmd = Topics.set(base, power.objectId),
            topicState = Topics.state(base, power.objectId),
            cmdOn = "ON",
            cmdOff = "OFF",
        )
        val ranges = if (temp != null && temp.component == HaComponent.NUMBER) {
            listOf(
                WqttRange(
                    type = WqttTypeResolver.temperatureRangeType(types),
                    topicCmd = Topics.set(base, temp.objectId),
                    topicState = Topics.state(base, temp.objectId),
                    min = temp.numberMin ?: 16.0,
                    max = temp.numberMax ?: 30.0,
                    precision = temp.numberStep ?: 0.5,
                    multiplier = 1.0,
                ),
            )
        } else {
            emptyList()
        }
        return WqttDevicePayload(
            stableKey = CLIMATE_KEY,
            name = "Климат",
            type = WqttTypeResolver.thermostatType(types),
            room = room,
            onOff = listOf(onOff),
            range = ranges,
            objectIds = objectIds,
        )
    }

    private fun sensorDevices(
        byId: Map<String, CatalogEntity>,
        base: String,
        room: String,
        types: WqttTypeCatalog,
    ): List<WqttDevicePayload> {
        return SENSOR_SPECS.mapNotNull { spec ->
            val entity = byId[spec.objectId] ?: return@mapNotNull null
            val floatType = if (spec.temperature) {
                WqttTypeResolver.temperatureFloatType(types)
            } else {
                WqttTypeResolver.genericFloatType(types)
            }
            val deviceType = if (spec.temperature) {
                WqttTypeResolver.temperatureSensorType(types)
            } else {
                WqttTypeResolver.floatSensorType(types)
            }
            WqttDevicePayload(
                stableKey = "sensor_${entity.objectId}",
                name = entity.label.ifBlank { spec.fallbackName },
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
    }

    private fun bodyDevices(
        byId: Map<String, CatalogEntity>,
        base: String,
        room: String,
        types: WqttTypeCatalog,
    ): List<WqttDevicePayload> {
        val out = mutableListOf<WqttDevicePayload>()
        val openableType = WqttTypeResolver.openableType(types)
        BODY_WINDOWS.forEach { objectId ->
            val entity = byId[objectId] ?: return@forEach
            if (!entity.writable) return@forEach
            out += WqttDevicePayload(
                stableKey = "body_$objectId",
                name = entity.label.ifBlank { objectId },
                type = openableType,
                room = room,
                onOff = listOf(
                    WqttOnOff(
                        topicCmd = Topics.set(base, entity.objectId),
                        topicState = Topics.state(base, entity.objectId),
                        // Window select uses closed/opened; Alice open/close maps poorly —
                        // publish opened/closed as ON/OFF stand-ins for a relay-like card.
                        cmdOn = "opened",
                        cmdOff = "closed",
                    ),
                ),
                objectIds = setOf(entity.objectId),
            )
        }
        byId.values
            .filter { entity ->
                entity.isButton && (
                    entity.canCommand?.operation == "trunk_pulse" ||
                        entity.objectId.contains("trunk", ignoreCase = true) ||
                        entity.label.contains("багажник", ignoreCase = true)
                    )
            }
            .sortedBy { it.objectId }
            .forEach { entity ->
                out += WqttDevicePayload(
                    stableKey = "body_${entity.objectId}",
                    name = entity.label.ifBlank { entity.objectId },
                    type = WqttTypeResolver.switchType(types),
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
            }
        val trunk = byId["trunk_door"]
        if (trunk != null &&
            (trunk.component == HaComponent.BINARY_SENSOR || trunk.component == HaComponent.SENSOR)
        ) {
            out += WqttDevicePayload(
                stableKey = "body_trunk_door",
                name = trunk.label.ifBlank { "Багажник" },
                type = WqttTypeResolver.floatSensorType(types),
                room = room,
                sensorEvent = listOf(
                    WqttSensorEvent(
                        type = WqttTypeResolver.openEventType(types),
                        topicState = Topics.state(base, trunk.objectId),
                    ),
                ),
                objectIds = setOf(trunk.objectId),
            )
        }
        return out
    }

    private data class SensorSpec(
        val objectId: String,
        val fallbackName: String,
        val temperature: Boolean,
    )
}
