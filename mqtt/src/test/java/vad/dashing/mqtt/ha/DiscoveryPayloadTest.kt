package vad.dashing.mqtt.ha

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryPayloadTest {
    @Test
    fun selectHasStateAndSetWithoutForceUpdate() {
        val entity = sample().copy(
            component = HaComponent.SELECT,
            writable = true,
            options = listOf("ECO", "NOR", "SPT"),
        )
        val json = JSONObject(
            DiscoveryPayload.entityConfig(entity, "tbox", "homeassistant", "dashing", "Моя машина", "0.1.0"),
        )
        assertEquals("tbox/dashing/drive_mode/state", json.getString("state_topic"))
        assertEquals("tbox/dashing/drive_mode/set", json.getString("command_topic"))
        assertFalse(json.has("force_update"))
        assertEquals("all", json.getString("availability_mode"))
        assertEquals(2, json.getJSONArray("availability").length())
        val device = json.getJSONObject("device")
        assertEquals("Моя машина", device.getString("name"))
        assertEquals("tbox_dashing", device.getJSONArray("identifiers").getString(0))
        assertEquals("0.1.0", device.getString("sw_version"))
        assertEquals("TBox MQTT", json.getJSONObject("origin").getString("name"))
    }

    @Test
    fun sensorKeepsLastValueWithoutAvailability() {
        val json = JSONObject(
            DiscoveryPayload.entityConfig(
                sample().copy(unit = "°C", deviceClass = "temperature"),
                "tbox",
                "homeassistant",
                "dashing",
                "Jetour Dashing",
                "0.1.0",
            ),
        )
        assertFalse(json.has("availability"))
        assertEquals("temperature", json.getString("device_class"))
        assertEquals("°C", json.getString("unit_of_measurement"))
    }

    @Test
    fun trackerPublishesAttributesAndServiceEntitiesStayOutOfThePicker() {
        val tracker = sample().copy(
            objectId = "geo_position",
            component = HaComponent.DEVICE_TRACKER,
            label = "Местоположение",
        )
        val json = JSONObject(
            DiscoveryPayload.entityConfig(tracker, "tbox", "homeassistant", "dashing", "Jetour Dashing", "0.1.0"),
        )
        assertEquals("gps", json.getString("source_type"))
        assertEquals(
            "tbox/dashing/geo_position/attributes",
            json.getString("json_attributes_topic"),
        )
        assertFalse(json.has("availability"))
        val link = JSONObject(DiscoveryPayload.connectivityConfig("tbox", "dashing", "Jetour Dashing", "0.1.0"))
        assertEquals("connectivity", link.getString("device_class"))
        assertEquals("online", link.getString("payload_on"))
        val seen = JSONObject(DiscoveryPayload.lastSeenConfig("tbox", "dashing", "Jetour Dashing", "0.1.0"))
        assertEquals("timestamp", seen.getString("device_class"))
        assertEquals("tbox/dashing/last_seen/state", seen.getString("state_topic"))
    }

    @Test
    fun musicIsASensorWithAttributesAndNoCommandTopic() {
        val entity = sample().copy(
            objectId = "media",
            label = "Музыка",
            component = HaComponent.SENSOR,
            signalId = null,
            writable = true,
            media = MediaBundle(
                signalIds = setOf("media_title"),
                source = "app",
                playAction = "media_play",
                pauseToggleAction = "media_play_pause",
                nextAction = "media_next",
                previousAction = "media_previous",
                volumeAction = "set_media_volume",
                volumeMin = 0,
                volumeMax = 31,
            ),
        )
        val topic = DiscoveryPayload.configTopic("homeassistant", "dashing", entity)
        assertEquals("homeassistant/sensor/tbox_dashing/media/config", topic)
        val json = JSONObject(
            DiscoveryPayload.entityConfig(entity, "tbox", "homeassistant", "dashing", "Jetour Dashing", "0.1.0"),
        )
        assertEquals("tbox/dashing/media/state", json.getString("state_topic"))
        assertEquals("tbox/dashing/media/state", json.getString("json_attributes_topic"))
        assertEquals("mdi:music", json.getString("icon"))
        assertTrue(json.getString("value_template").contains("value_json.title"))
        assertFalse(json.has("command_topic"))
    }

    private fun sample() = CatalogEntity(
        objectId = "drive_mode",
        label = "Режим вождения",
        description = "",
        group = EntityGroup.MOTION,
        component = HaComponent.SENSOR,
        signalId = "drive_mode",
        source = "head_unit",
    )
}
