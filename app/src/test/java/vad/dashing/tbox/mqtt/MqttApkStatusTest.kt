package vad.dashing.tbox.mqtt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MqttApkStatusTest {
    @Test
    fun encodeDecodeRoundTrip() {
        val snap = MqttBridgeStatusSnapshot(
            serviceRunning = true,
            monitorUp = false,
            brokerUp = true,
            wireguardEnabled = true,
            tunnelError = "",
            brokerError = "",
            monitorError = "401",
            lastError = "",
            availability = "online",
        )
        val decoded = MqttApkStatus.decode(MqttApkStatus.encode(snap))
        assertEquals(snap, decoded)
    }

    @Test
    fun wireguardUiMapping() {
        assertEquals(MqttWireguardUi.OFF, mqttWireguardUi(false, ""))
        assertEquals(MqttWireguardUi.UP, mqttWireguardUi(true, "  "))
        assertEquals(MqttWireguardUi.ERROR, mqttWireguardUi(true, "route"))
    }

    @Test
    fun rowDetailOnlyWhenDown() {
        assertEquals("", mqttRowDetail(true, "ignored"))
        assertEquals("timeout", mqttRowDetail(false, "timeout"))
        assertEquals("fallback", mqttRowDetail(false, "", "fallback"))
        assertEquals("", mqttRowDetail(false, "  ", "  "))
    }

    @Test
    fun statusUrlAndPortAlignWithMqttApk() {
        assertEquals("vad.dashing.mqtt", MqttApkStatus.PACKAGE_NAME)
        assertEquals(8766, MqttApkStatus.PORT)
        assertEquals("http://127.0.0.1:8766/status", MqttApkStatus.statusUrl())
        assertFalse(MqttApkStatus.POLL_MS < 1_000L)
        assertTrue(MqttApkStatus.POLL_MS <= 3_000L)
    }
}
