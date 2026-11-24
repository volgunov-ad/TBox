package vad.dashing.mqtt.service

import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.net.Socket

class MqttStatusHttpServerTest {
    @Test
    fun statusGetUnknownPathAndWrongMethod() {
        val server = MqttStatusHttpServer { """{"ok":true}""" }
        server.start(0)
        try {
            val port = server.localPort
            val ok = request(port, "GET /status HTTP/1.0\r\n\r\n")
            assertTrue(ok.startsWith("HTTP/1.1 200"))
            assertTrue(ok.contains("\"ok\":true"))
            val missing = request(port, "GET /missing HTTP/1.0\r\n\r\n")
            assertTrue(missing.startsWith("HTTP/1.1 404"))
            val method = request(port, "POST /status HTTP/1.0\r\n\r\n")
            assertTrue(method.startsWith("HTTP/1.1 405"))
        } finally {
            server.stop()
        }
    }

    private fun request(port: Int, head: String): String {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", port), 2_000)
            socket.soTimeout = 2_000
            socket.getOutputStream().write(head.toByteArray())
            return socket.getInputStream().bufferedReader().readText()
        }
    }
}
