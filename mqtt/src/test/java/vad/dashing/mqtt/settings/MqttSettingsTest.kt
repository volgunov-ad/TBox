package vad.dashing.mqtt.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class MqttSettingsTest {
    @Test
    fun applyingConnectionKeepsTokenAndEntities() {
        val saved = MqttSettings(
            accessToken = "token",
            brokerHost = "old.broker",
            username = "old",
            selectedObjectIds = setOf("drive_mode"),
            deviceName = "Jetour Dashing",
        )
        val draft = saved.copy(
            brokerHost = "192.168.1.10",
            username = "mqtt",
            password = "secret",
            accessToken = "",
            selectedObjectIds = emptySet(),
            deviceName = "другое",
            fastPublishSeconds = 10,
        )
        val merged = saved.applyingConnection(draft)
        assertEquals("192.168.1.10", merged.brokerHost)
        assertEquals("mqtt", merged.username)
        assertEquals("secret", merged.password)
        assertEquals("token", merged.accessToken)
        assertEquals(setOf("drive_mode"), merged.selectedObjectIds)
        assertEquals("Jetour Dashing", merged.deviceName)
        assertEquals(10, merged.fastPublishSeconds)
        assertEquals(0, MqttSettings(fastPublishSeconds = -1).normalized().fastPublishSeconds)
        assertEquals(60, MqttSettings(fastPublishSeconds = 90).normalized().fastPublishSeconds)
    }
}
