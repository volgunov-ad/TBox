package vad.dashing.mqtt.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
            wireguardEnabled = true,
            wireguardConf = "wg",
            wireguardFileName = "home.conf",
        )
        val merged = saved.applyingConnection(draft)
        assertEquals("192.168.1.10", merged.brokerHost)
        assertEquals("mqtt", merged.username)
        assertEquals("secret", merged.password)
        assertEquals("token", merged.accessToken)
        assertEquals(setOf("drive_mode"), merged.selectedObjectIds)
        assertEquals("Jetour Dashing", merged.deviceName)
        assertEquals(true, merged.wireguardEnabled)
        assertEquals("wg", merged.wireguardConf)
        assertEquals(10, merged.fastPublishSeconds)
        assertEquals(0, MqttSettings(fastPublishSeconds = -1).normalized().fastPublishSeconds)
        assertEquals(60, MqttSettings(fastPublishSeconds = 90).normalized().fastPublishSeconds)
        assertEquals(false, MqttSettings(wireguardEnabled = true).normalized().wireguardEnabled)
    }

    @Test
    fun individualMediaRowsStaySelectedNextToMusic() {
        val selected = setOf(
            "drive_mode",
            "media",
            "media_title",
            "builtin_media_next",
            "hu_media_volume",
        )
        assertEquals(selected, MqttSettings(selectedObjectIds = selected).normalized().selectedObjectIds)
    }

    @Test
    fun fastPublishDefaultIsTenSeconds() {
        assertEquals(10, MqttSettings().fastPublishSeconds)
        assertEquals(10 to true, fastPublishSecondsOnLoad(stored = null, alreadyMigrated = false))
        assertEquals(10 to true, fastPublishSecondsOnLoad(stored = 5, alreadyMigrated = false))
        assertEquals(3 to true, fastPublishSecondsOnLoad(stored = 3, alreadyMigrated = false))
        assertEquals(5 to false, fastPublishSecondsOnLoad(stored = 5, alreadyMigrated = true))
    }

    @Test
    fun brokerSwitchStopsTheBridgeButKeepsItConfigured() {
        val configured = MqttSettings(accessToken = "token", brokerHost = "192.168.1.10")
        assertTrue(configured.active)
        val off = configured.copy(brokerEnabled = false)
        assertTrue(off.ready)
        assertFalse(off.active)
        assertEquals(configured.connectionKey(), off.connectionKey())
    }

    @Test
    fun draftIsDirtyOnlyForFieldsThatWaitForSave() {
        val saved = MqttSettings(accessToken = "token", brokerHost = "192.168.1.10").normalized()
        assertFalse(connectionChanged(saved, saved))
        assertFalse(connectionChanged(saved, saved.copy(brokerHost = " 192.168.1.10 ")))
        assertFalse(connectionChanged(saved, saved.copy(accessToken = "", deviceName = "другое")))
        assertTrue(connectionChanged(saved, saved.copy(brokerPort = 8883)))
        assertTrue(connectionChanged(saved, saved.copy(wireguardConf = "[Interface]")))
    }

    @Test
    fun savingBrokerFieldsKeepsTheSwitch() {
        val saved = MqttSettings(accessToken = "token", brokerHost = "a", brokerEnabled = false)
        val draft = saved.copy(brokerHost = "b", brokerEnabled = true)
        assertFalse(saved.applyingConnection(draft).brokerEnabled)
    }
}
