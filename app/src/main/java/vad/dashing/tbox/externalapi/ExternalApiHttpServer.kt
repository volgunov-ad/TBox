package vad.dashing.tbox.externalapi

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.charset.Charset
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class ExternalApiHttpResponse(
    val status: Int,
    val contentType: String = "application/json; charset=utf-8",
    val body: String = "",
    val headers: Map<String, String> = emptyMap(),
)

typealias ExternalApiHttpHandler = (
    method: String,
    path: String,
    query: Map<String, String>,
    headers: Map<String, String>,
    body: String,
) -> ExternalApiHttpResponse

class ExternalApiHttpServer(
    private val bindHost: String = "0.0.0.0",
    private val handler: ExternalApiHttpHandler,
) {
    private val charset = Charset.forName("UTF-8")
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var acceptExecutor: ExecutorService? = null
    private var workerExecutor: ExecutorService? = null

    @Volatile
    var boundPort: Int? = null
        private set

    @Volatile
    var lastError: String? = null
        private set

    val isRunning: Boolean
        get() = running.get() && serverSocket?.isClosed == false

    fun start(port: Int) {
        stop()
        lastError = null
        val socket = ServerSocket()
        socket.reuseAddress = true
        socket.bind(InetSocketAddress(bindHost, port))
        serverSocket = socket
        boundPort = socket.localPort
        running.set(true)
        acceptExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "external-api-accept").apply { isDaemon = true }
        }
        workerExecutor = Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "external-api-worker").apply { isDaemon = true }
        }
        acceptExecutor?.execute {
            acceptLoop(socket)
        }
    }

    fun stop() {
        running.set(false)
        acceptExecutor?.shutdownNow()
        workerExecutor?.shutdownNow()
        acceptExecutor = null
        workerExecutor = null
        try {
            serverSocket?.close()
        } catch (_: IOException) {
        }
        serverSocket = null
        boundPort = null
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (running.get() && !socket.isClosed) {
            try {
                val client = socket.accept()
                workerExecutor?.execute {
                    handleClient(client)
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
        client.soTimeout = 30_000
        try {
            client.use { socket ->
                val input = BufferedInputStream(socket.getInputStream())
                val output = BufferedOutputStream(socket.getOutputStream())
                val request = readRequest(input) ?: return
                val response = handler(
                    request.method,
                    request.path,
                    request.query,
                    request.headers,
                    request.body,
                )
                writeResponse(output, response)
            }
        } catch (_: IOException) {
        } finally {
            try {
                client.close()
            } catch (_: IOException) {
            }
        }
    }

    private data class ParsedRequest(
        val method: String,
        val path: String,
        val query: Map<String, String>,
        val headers: Map<String, String>,
        val body: String,
    )

    private fun readRequest(input: BufferedInputStream): ParsedRequest? {
        val requestLine = readLine(input) ?: return null
        if (requestLine.isBlank()) return null
        val parts = requestLine.split(' ')
        if (parts.size < 2) return null
        val method = parts[0].uppercase()
        val target = parts[1]
        val pathAndQuery = target.split('?', limit = 2)
        val path = pathAndQuery[0]
        val query = parseQuery(pathAndQuery.getOrNull(1).orEmpty())
        val headers = linkedMapOf<String, String>()
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            val separator = line.indexOf(':')
            if (separator <= 0) continue
            val name = line.substring(0, separator).trim().lowercase()
            val value = line.substring(separator + 1).trim()
            headers[name] = value
        }
        val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
        val bodyBytes = if (contentLength > 0) {
            readExact(input, contentLength)
        } else {
            ByteArray(0)
        }
        val body = bodyBytes.toString(charset)
        return ParsedRequest(method, path, query, headers, body)
    }

    private fun parseQuery(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return raw.split('&')
            .mapNotNull { pair ->
                if (pair.isBlank()) return@mapNotNull null
                val pieces = pair.split('=', limit = 2)
                val key = urlDecode(pieces[0])
                val value = urlDecode(pieces.getOrNull(1).orEmpty())
                key to value
            }
            .toMap()
    }

    private fun urlDecode(value: String): String =
        java.net.URLDecoder.decode(value, charset.name())

    private fun readLine(input: BufferedInputStream): String? {
        val buffer = ByteArrayOutputStream()
        while (true) {
            val byte = input.read()
            if (byte == -1) {
                return if (buffer.size() == 0) null else buffer.toString(charset.name())
            }
            if (byte == '\n'.code) {
                break
            }
            if (byte != '\r'.code) {
                buffer.write(byte)
            }
        }
        return buffer.toString(charset.name())
    }

    private fun readExact(input: BufferedInputStream, length: Int): ByteArray {
        val buffer = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = input.read(buffer, offset, length - offset)
            if (read < 0) break
            offset += read
        }
        return if (offset == length) buffer else buffer.copyOf(offset)
    }

    private fun writeResponse(output: BufferedOutputStream, response: ExternalApiHttpResponse) {
        val bodyBytes = response.body.toByteArray(charset)
        val statusLine = "HTTP/1.1 ${response.status} ${statusText(response.status)}\r\n"
        val headers = buildString {
            append(statusLine)
            append("Content-Type: ${response.contentType}\r\n")
            append("Content-Length: ${bodyBytes.size}\r\n")
            response.headers.forEach { (name, value) ->
                append(name)
                append(": ")
                append(value)
                append("\r\n")
            }
            append("Connection: close\r\n")
            append("\r\n")
        }
        output.write(headers.toByteArray(charset))
        output.write(bodyBytes)
        output.flush()
    }

    private fun statusText(status: Int): String = when (status) {
        200 -> "OK"
        202 -> "Accepted"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        403 -> "Forbidden"
        404 -> "Not Found"
        405 -> "Method Not Allowed"
        500 -> "Internal Server Error"
        else -> "OK"
    }
}
