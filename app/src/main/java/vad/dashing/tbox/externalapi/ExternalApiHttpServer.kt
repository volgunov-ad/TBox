package vad.dashing.tbox.externalapi

import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.nio.charset.Charset
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
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
        workerExecutor = ThreadPoolExecutor(
            0,
            ExternalApiConstants.MAX_CONCURRENT_CLIENTS,
            60L,
            TimeUnit.SECONDS,
            SynchronousQueue(),
        ) { runnable ->
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
                try {
                    val executor = workerExecutor ?: throw RejectedExecutionException()
                    executor.execute { handleClient(client) }
                } catch (_: RejectedExecutionException) {
                    closeQuietly(client)
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
                val request = try {
                    readRequest(input) ?: return
                } catch (error: RequestRejectedException) {
                    writeResponse(output, errorResponse(error.status, error.code, error.message.orEmpty()))
                    return
                }
                val response = try {
                    handler(
                        request.method,
                        request.path,
                        request.query,
                        request.headers,
                        request.body,
                    )
                } catch (error: Exception) {
                    lastError = error.message ?: error.javaClass.simpleName
                    ExternalApiHttpResponse(
                        status = 500,
                        body = JSONObject()
                            .put(
                                "error",
                                JSONObject()
                                    .put("code", "internal")
                                    .put("message", error.javaClass.simpleName),
                            )
                            .toString(),
                    )
                }
                writeResponse(output, response)
            }
        } catch (_: IOException) {
        } finally {
            closeQuietly(client)
        }
    }

    private fun closeQuietly(client: Socket) {
        try {
            client.close()
        } catch (_: IOException) {
        }
    }

    private class RequestRejectedException(
        val status: Int,
        val code: String,
        message: String,
    ) : IOException(message)

    private fun errorResponse(status: Int, code: String, message: String) =
        ExternalApiHttpResponse(
            status = status,
            body = JSONObject()
                .put("error", JSONObject().put("code", code).put("message", message))
                .toString(),
        )

    private data class ParsedRequest(
        val method: String,
        val path: String,
        val query: Map<String, String>,
        val headers: Map<String, String>,
        val body: String,
    )

    private fun readRequest(input: BufferedInputStream): ParsedRequest? {
        val requestLine = readLine(input, ExternalApiConstants.MAX_REQUEST_LINE_BYTES) ?: return null
        if (requestLine.isBlank()) return null
        val parts = requestLine.split(' ')
        if (parts.size < 2) return null
        val method = parts[0].uppercase()
        val target = parts[1]
        val pathAndQuery = target.split('?', limit = 2)
        val path = pathAndQuery[0]
        val query = parseQuery(pathAndQuery.getOrNull(1).orEmpty())
        val headers = linkedMapOf<String, String>()
        var headerBytes = 0
        var headerCount = 0
        while (true) {
            val line = readLine(input, ExternalApiConstants.MAX_HEADER_BYTES) ?: break
            if (line.isEmpty()) break
            headerBytes += line.length
            headerCount += 1
            if (headerBytes > ExternalApiConstants.MAX_HEADER_BYTES ||
                headerCount > ExternalApiConstants.MAX_HEADER_COUNT
            ) {
                throw RequestRejectedException(431, "headers_too_large", "Request headers too large")
            }
            val separator = line.indexOf(':')
            if (separator <= 0) continue
            val name = line.substring(0, separator).trim().lowercase()
            val value = line.substring(separator + 1).trim()
            headers[name] = value
        }
        val contentLength = headers["content-length"]?.let { raw ->
            raw.toLongOrNull()?.takeIf { it >= 0 }
                ?: throw RequestRejectedException(400, "invalid_request", "Invalid Content-Length")
        } ?: 0L
        if (contentLength > ExternalApiConstants.MAX_BODY_BYTES) {
            throw RequestRejectedException(413, "payload_too_large", "Request body too large")
        }
        val bodyBytes = if (contentLength > 0) {
            readExact(input, contentLength.toInt())
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
        try {
            URLDecoder.decode(value, charset.name())
        } catch (_: IllegalArgumentException) {
            value
        }

    private fun readLine(input: BufferedInputStream, maxBytes: Int): String? {
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
                if (buffer.size() >= maxBytes) {
                    throw RequestRejectedException(431, "line_too_long", "Request line too long")
                }
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
        413 -> "Payload Too Large"
        431 -> "Request Header Fields Too Large"
        500 -> "Internal Server Error"
        else -> "OK"
    }
}
