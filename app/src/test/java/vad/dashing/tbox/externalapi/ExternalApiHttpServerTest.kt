package vad.dashing.tbox.externalapi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ExternalApiHttpServerTest {
    private fun liveServerSocket(server: ExternalApiHttpServer): ServerSocket {
        val field = ExternalApiHttpServer::class.java.getDeclaredField("serverSocket")
        field.isAccessible = true
        return field.get(server) as ServerSocket
    }

    private fun newServer() = ExternalApiHttpServer { _, _, _, _, _ ->
        ExternalApiHttpResponse(200, body = "{\"ok\":true}")
    }

    @Test
    fun unexpectedAcceptDeath_stopsServerAndFiresCallback() {
        val server = newServer()
        val fired = CountDownLatch(1)
        server.onUnexpectedStop = { fired.countDown() }
        server.start(0)
        assertTrue(server.isRunning)

        liveServerSocket(server).close()

        assertTrue(
            "unexpected accept death must fire the callback",
            fired.await(5, TimeUnit.SECONDS),
        )
        assertFalse("dead server must report isRunning=false", server.isRunning)
        server.stop()
    }

    @Test
    fun deliberateStop_doesNotFireUnexpectedCallback() {
        val server = newServer()
        val fired = CountDownLatch(1)
        server.onUnexpectedStop = { fired.countDown() }
        server.start(0)

        server.stop()

        assertFalse(
            "deliberate stop must not fire the unexpected-death callback",
            fired.await(700, TimeUnit.MILLISECONDS),
        )
        assertFalse(server.isRunning)
    }

    @Test
    fun requestResponse_roundTripAfterRestart() {
        val server = newServer()
        server.start(0)
        val firstPort = server.boundPort!!
        assertTrue(speakOnce(firstPort).startsWith("HTTP/1.1 200"))

        liveServerSocket(server).close()
        server.start(firstPort)

        assertTrue(server.isRunning)
        assertEquals(firstPort, server.boundPort)
        assertTrue(speakOnce(firstPort).startsWith("HTTP/1.1 200"))
        server.stop()
    }

    private fun speakOnce(port: Int): String {
        val client = Socket("127.0.0.1", port)
        client.soTimeout = 5_000
        val out = client.getOutputStream()
        out.write("GET /v1/health HTTP/1.1\r\nHost: localhost\r\n\r\n".toByteArray())
        out.flush()
        val input = client.getInputStream()
        val buffer = ByteArray(4096)
        var total = 0
        while (total < buffer.size) {
            val read = input.read(buffer, total, buffer.size - total)
            if (read < 0) break
            total += read
        }
        client.close()
        return String(buffer, 0, total, Charsets.UTF_8)
    }
}
