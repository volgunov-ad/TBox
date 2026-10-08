package vad.dashing.mqtt.ha

import org.json.JSONArray
import org.json.JSONObject

object DiscoveryPayload {
    const val LINK_OBJECT_ID = "link"
    const val LAST_SEEN_OBJECT_ID = "last_seen"
    const val LINK_NAME = "Связь с машиной"
    const val LAST_SEEN_NAME = "Последняя связь"

    fun configTopic(discoveryPrefix: String, deviceId: String, entity: CatalogEntity): String =
        Topics.discoveryConfig(
            discoveryPrefix,
            entity.component.storageKey,
            deviceId,
            entity.objectId,
        )

    fun serviceTopics(discoveryPrefix: String, deviceId: String): List<String> = listOf(
        Topics.discoveryConfig(discoveryPrefix, HaComponent.BINARY_SENSOR.storageKey, deviceId, LINK_OBJECT_ID),
        Topics.discoveryConfig(discoveryPrefix, HaComponent.SENSOR.storageKey, deviceId, LAST_SEEN_OBJECT_ID),
    )

    fun entityConfig(
        entity: CatalogEntity,
        topicPrefix: String,
        discoveryPrefix: String,
        deviceId: String,
        deviceName: String,
        swVersion: String,
    ): String {
        val base = Topics.base(topicPrefix, deviceId)
        val json = baseFields(entity.label, deviceId, entity.objectId, deviceName, swVersion)
        when (entity.component) {
            HaComponent.SENSOR, HaComponent.BINARY_SENSOR -> {
                val stateTopic = Topics.state(base, entity.objectId)
                json.put("state_topic", stateTopic)
                if (entity.component == HaComponent.BINARY_SENSOR) {
                    json.put("payload_on", "ON")
                    json.put("payload_off", "OFF")
                }
                if (entity.media != null) {
                    json.put("icon", "mdi:music")
                    json.put(
                        "value_template",
                        "{{ value_json.title if value_json.title else value_json.state }}",
                    )
                    json.put("json_attributes_topic", stateTopic)
                }
                putUnit(json, entity)
            }
            HaComponent.DEVICE_TRACKER -> {
                val attributes = Topics.attributes(base, entity.objectId)
                json.put("state_topic", attributes)
                json.put("json_attributes_topic", attributes)
                json.put("source_type", "gps")
            }
            HaComponent.SWITCH, HaComponent.NUMBER, HaComponent.SELECT, HaComponent.BUTTON -> {
                if (entity.component != HaComponent.BUTTON) {
                    json.put("state_topic", Topics.state(base, entity.objectId))
                }
                json.put("command_topic", Topics.set(base, entity.objectId))
                putAvailability(json, base)
                when (entity.component) {
                    HaComponent.SWITCH -> {
                        json.put("payload_on", "ON")
                        json.put("payload_off", "OFF")
                    }
                    HaComponent.SELECT -> json.put("options", JSONArray(entity.options))
                    HaComponent.NUMBER -> {
                        entity.numberMin?.let { json.put("min", it) }
                        entity.numberMax?.let { json.put("max", it) }
                        entity.numberStep?.let { json.put("step", it) }
                        putUnit(json, entity)
                    }
                    HaComponent.BUTTON -> json.put("payload_press", "PRESS")
                    else -> Unit
                }
            }
        }
        return json.toString()
    }

    fun connectivityConfig(
        topicPrefix: String,
        deviceId: String,
        deviceName: String,
        swVersion: String,
    ): String {
        val base = Topics.base(topicPrefix, deviceId)
        return baseFields(LINK_NAME, deviceId, LINK_OBJECT_ID, deviceName, swVersion)
            .put("state_topic", Topics.status(base))
            .put("payload_on", "online")
            .put("payload_off", "offline")
            .put("device_class", "connectivity")
            .toString()
    }

    fun lastSeenConfig(
        topicPrefix: String,
        deviceId: String,
        deviceName: String,
        swVersion: String,
    ): String {
        val base = Topics.base(topicPrefix, deviceId)
        return baseFields(LAST_SEEN_NAME, deviceId, LAST_SEEN_OBJECT_ID, deviceName, swVersion)
            .put("state_topic", Topics.lastSeen(base))
            .put("device_class", "timestamp")
            .toString()
    }

    private fun baseFields(
        name: String,
        deviceId: String,
        objectId: String,
        deviceName: String,
        swVersion: String,
    ): JSONObject = JSONObject()
        .put("name", name)
        .put("unique_id", Topics.uniqueId(deviceId, objectId))
        .put("object_id", objectId)
        .put(
            "device",
            JSONObject()
                .put("identifiers", JSONArray().put(Topics.deviceIdentifier(deviceId)))
                .put("name", Topics.deviceName(deviceName))
                .put("manufacturer", "TBox")
                .put("model", "Dashing")
                .put("sw_version", swVersion),
        )
        .put(
            "origin",
            JSONObject()
                .put("name", "TBox MQTT")
                .put("sw", swVersion),
        )

    private fun putAvailability(json: JSONObject, base: String) {
        json.put(
            "availability",
            JSONArray()
                .put(
                    JSONObject()
                        .put("topic", Topics.status(base))
                        .put("payload_available", "online")
                        .put("payload_not_available", "offline"),
                )
                .put(
                    JSONObject()
                        .put("topic", Topics.commands(base))
                        .put("payload_available", "on")
                        .put("payload_not_available", "off"),
                ),
        )
        json.put("availability_mode", "all")
    }

    private fun putUnit(json: JSONObject, entity: CatalogEntity) {
        val unit = measurementUnit(entity.unit)
        if (unit.isNotBlank()) json.put("unit_of_measurement", unit)
        entity.deviceClass?.let { json.put("device_class", it) }
    }
}
