package vad.dashing.mqtt.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MqttLocalStatusTest {
    @Test
    fun encodeDecodeRoundTrip() {
        val status = BridgeStatus(
            monitorUp = true,
            brokerUp = false,
            availability = "offline",
            lastError = "cmd fail",
            monitorError = "",
            brokerError = "timeout",
            wireguardEnabled = true,
            tunnelError = "dial refused",
        )
        val json = MqttLocalStatus.encode(status)
        val decoded = MqttLocalStatus.decode(json)
        assertTrue(decoded.serviceRunning)
        assertTrue(decoded.monitorUp)
        assertFalse(decoded.brokerUp)
        assertTrue(decoded.wireguardEnabled)
        assertEquals("dial refused", decoded.tunnelError)
        assertEquals("timeout", decoded.brokerError)
        assertEquals("cmd fail", decoded.lastError)
        assertEquals("offline", decoded.availability)
    }

    @Test
    fun wireguardUiMapping() {
        assertEquals(WireguardBridgeUi.OFF, wireguardBridgeUi(false, ""))
        assertEquals(WireguardBridgeUi.OFF, wireguardBridgeUi(false, "x"))
        assertEquals(WireguardBridgeUi.UP, wireguardBridgeUi(true, ""))
        assertEquals(WireguardBridgeUi.ERROR, wireguardBridgeUi(true, "peer down"))
    }

    @Test
    fun portConstantMatchesMonitorConvention() {
        assertEquals(8766, MqttLocalStatus.PORT)
        assertEquals("/status", MqttLocalStatus.PATH)
        assertEquals("127.0.0.1", MqttLocalStatus.BIND_HOST)
    }
}
