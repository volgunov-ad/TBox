package vad.dashing.mqtt.service

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tiny loopback-only HTTP server: GET [MqttLocalStatus.PATH] → BridgeStatus JSON.
 * Bound to 127.0.0.1; no auth (HU-local).
 */
class MqttStatusHttpServer(
    private val statusBody: () -> String,
) {
    private val charset = Charset.forName("UTF-8")
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null

    @Volatile
    var lastError: String? = null
        private set

    val isRunning: Boolean
        get() = running.get() && serverSocket?.isClosed == false

    fun start(port: Int = MqttLocalStatus.PORT) {
        stop()
        lastError = null
        val socket = ServerSocket()
        socket.reuseAddress = true
        socket.bind(InetSocketAddress(MqttLocalStatus.BIND_HOST, port))
        serverSocket = socket
        running.set(true)
        acceptThread = Thread({ acceptLoop(socket) }, "mqtt-status-http").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running.set(false)
        try {
            serverSocket?.close()
        } catch (_: IOException) {
        }
        serverSocket = null
        acceptThread = null
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (running.get() && !socket.isClosed) {
            try {
                val client = socket.accept()
                Thread({ handleClient(client) }, "mqtt-status-worker").apply {
                    isDaemon = true
                    start()
                }
            } catch (error: SocketException) {
                if (running.get()) {
                    lastError = error.message ?: error.javaClass.simpleName
                }
                break
            } catch (error: IOException) {
                if (running.get()) {
                    lastError = error.message ?: error.javaClass.simpleName
                }
            }
        }
    }

    private fun handleClient(client: Socket) {
        client.soTimeout = 5_000
        try {
            client.use { socket ->
                val input = BufferedInputStream(socket.getInputStream())
                val output = BufferedOutputStream(socket.getOutputStream())
                val requestLine = readLine(input) ?: return
                val parts = requestLine.split(' ')
                if (parts.size < 2) {
                    write(output, 400, """{"error":"bad_request"}""")
                    return
                }
                val method = parts[0].uppercase()
                val path = parts[1].substringBefore('?')
                // Drain headers so clients that send more are fine.
                while (true) {
                    val line = readLine(input) ?: break
                    if (line.isEmpty()) break
                }
                if (method != "GET") {
                    write(output, 405, """{"error":"method_not_allowed"}""")
                    return
                }
                if (path != MqttLocalStatus.PATH) {
                    write(output, 404, """{"error":"not_found"}""")
                    return
                }
                val body = try {
                    statusBody()
                } catch (error: Exception) {
                    lastError = error.message ?: error.javaClass.simpleName
                    write(output, 500, """{"error":"internal"}""")
                    return
                }
                write(output, 200, body)
            }
        } catch (_: IOException) {
        }
    }

    private fun readLine(input: BufferedInputStream): String? {
        val buffer = ByteArrayOutputStream()
        while (true) {
            val byte = input.read()
            if (byte == -1) {
                return if (buffer.size() == 0) null else buffer.toString(charset.name())
            }
            if (byte == '\n'.code) break
            if (byte != '\r'.code) {
                if (buffer.size() >= 2048) return null
                buffer.write(byte)
            }
        }
        return buffer.toString(charset.name())
    }

    private fun write(output: BufferedOutputStream, status: Int, body: String) {
        val bodyBytes = body.toByteArray(charset)
        val reason = when (status) {
            200 -> "OK"
            400 -> "Bad Request"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            else -> "Error"
        }
        val headers = "HTTP/1.1 $status $reason\r\n" +
            "Content-Type: application/json; charset=utf-8\r\n" +
            "Content-Length: ${bodyBytes.size}\r\n" +
            "Connection: close\r\n\r\n"
        output.write(headers.toByteArray(charset))
        output.write(bodyBytes)
        output.flush()
    }
}
