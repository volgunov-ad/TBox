package vad.dashing.mqtt.mqttclient

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.ConnectException
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException

class BrokerFailureTextTest {
    @Test
    fun futureWrappersShowTheRealCause() {
        val error = ExecutionException(CompletionException(ConnectException("Connection refused")))
        assertEquals("Connection refused", brokerFailureText(error))
    }

    @Test
    fun bareTimeoutGetsAText() {
        assertEquals("Брокер не ответил вовремя", brokerFailureText(TimeoutException()))
    }

    @Test
    fun noMessageFallsBackToTheClassName() {
        assertEquals("IllegalStateException", brokerFailureText(IllegalStateException()))
    }
}
