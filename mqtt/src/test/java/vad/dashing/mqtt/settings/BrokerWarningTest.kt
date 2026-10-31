package vad.dashing.mqtt.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrokerWarningTest {
    @Test
    fun warnsOnlyForOpenBrokerOnAPublicAddress() {
        assertTrue(BrokerWarning.shouldWarn("", tlsEnabled = false, host = "broker.example.com"))
        assertTrue(BrokerWarning.shouldWarn("", tlsEnabled = false, host = "8.8.8.8"))
        assertFalse(BrokerWarning.shouldWarn("user", tlsEnabled = false, host = "8.8.8.8"))
        assertFalse(BrokerWarning.shouldWarn("", tlsEnabled = true, host = "8.8.8.8"))
        assertFalse(BrokerWarning.shouldWarn("", tlsEnabled = false, host = "192.168.1.10"))
        assertFalse(BrokerWarning.shouldWarn("", tlsEnabled = false, host = "10.0.0.5"))
        assertFalse(BrokerWarning.shouldWarn("", tlsEnabled = false, host = "mosquitto.local"))
        assertFalse(BrokerWarning.shouldWarn("", tlsEnabled = false, host = ""))
    }
}
