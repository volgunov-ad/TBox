package vad.dashing.mqtt.mqttclient

import com.hivemq.client.mqtt.MqttClientState
import org.junit.Assert.assertEquals
import org.junit.Test

class MqttConnectStepTest {
    @Test
    fun onlyAStoppedClientStartsANewConnect() {
        assertEquals(MqttConnectStep.CONNECT, mqttConnectStep(MqttClientState.DISCONNECTED))
        assertEquals(MqttConnectStep.ALREADY_UP, mqttConnectStep(MqttClientState.CONNECTED))
        assertEquals(MqttConnectStep.WAIT, mqttConnectStep(MqttClientState.CONNECTING))
        assertEquals(MqttConnectStep.WAIT, mqttConnectStep(MqttClientState.CONNECTING_RECONNECT))
        assertEquals(MqttConnectStep.WAIT, mqttConnectStep(MqttClientState.DISCONNECTED_RECONNECT))
    }
}
