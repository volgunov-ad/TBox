package vad.dashing.camd

import android.util.Log
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * One line in, one line out, on localhost only.
 * OPEN <id> <x> <y> <w> <h> [0|90|180|270 [flipH [flipV]]] [crop=cx,cy,cw,ch] [shape=circle]
 * OPEN preset=<name> <x> <y> <w> <h> [shape=circle]
 * CLOSE
 * CLOSE <id>
 * AVM ON|OFF
 * STATUS
 * PRESETS
 *
 * Listen loop is supervised: bind/accept failures retry with backoff (P1-2 / P1-5).
 * [stop] closes the [ServerSocket] so a stuck accept wakes and the thread exits.
 */
class CamdApiServer(
    private val engine: CamdEngine,
    private val windows: CameraWindows? = null,
    private val clientTimeoutMs: Int = CLIENT_TIMEOUT_MS,
    private val port: Int = DaemonScripts.API_PORT,
    private val sleeper: (Long) -> Unit = { ms -> Thread.sleep(ms) },
    /** Test seam: override to simulate a failed bind without racing SO_REUSEADDR. */
    private val binder: (ServerSocket) -> Unit = { server ->
        server.bind(
            InetSocketAddress(InetAddress.getByName("127.0.0.1"), port),
            8,
        )
    },
) {

    @Volatile
    private var running = false

    @Volatile
    private var serverSocket: ServerSocket? = null

    private val generation = AtomicInteger(0)

    fun start() {
        if (running) return
        running = true
        val gen = generation.incrementAndGet()
        thread(name = "camd-api", isDaemon = true) {
            supervise(gen)
        }
    }

    /** Stop accepting and close the listen socket (idempotent). */
    fun stop() {
        running = false
        generation.incrementAndGet()
        val socket = serverSocket
        serverSocket = null
        runCatching { socket?.close() }
    }

    /** True while the supervisor intends to keep listening. */
    fun isRunning(): Boolean = running

    private fun supervise(gen: Int) {
        var backoffMs = CamdApiListenBackoff.INITIAL_MS
        while (running && generation.get() == gen) {
            var server: ServerSocket? = null
            try {
                server = ServerSocket()
                server.reuseAddress = true
                binder(server)
                serverSocket = server
                backoffMs = CamdApiListenBackoff.INITIAL_MS
                // Log stubs throw in JVM unit tests — never let logging kill the supervisor.
                runCatching { Log.i(TAG, "API слушает $port") }
                while (running && generation.get() == gen) {
                    val socket = server.accept()
                    thread(name = "camd-api-client", isDaemon = true) {
                        serveClient(socket)
                    }
                }
            } catch (error: Throwable) {
                if (!running || generation.get() != gen) break
                // Closing the socket from [stop] wakes accept with SocketException — expected.
                if (error is SocketException && !running) break
                runCatching {
                    Log.e(TAG, "API не слушает $port — повтор через ${backoffMs}ms", error)
                }
                runCatching { server?.close() }
                if (serverSocket === server) serverSocket = null
                try {
                    sleeper(backoffMs)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
                backoffMs = CamdApiListenBackoff.next(backoffMs)
            } finally {
                runCatching { server?.close() }
                if (serverSocket === server) serverSocket = null
            }
        }
        running = false
        runCatching { Log.i(TAG, "API supervisor остановлен (port=$port)") }
    }

    internal fun serveClient(socket: Socket) {
        runCatching { socket.soTimeout = clientTimeoutMs }
        try {
            socket.use { client ->
                val request = client.getInputStream().bufferedReader().readLine() ?: return
                val reply = reply(request)
                client.getOutputStream().write((reply + "\n").toByteArray())
                client.getOutputStream().flush()
            }
        } catch (_: SocketTimeoutException) {
        } catch (error: Throwable) {
            runCatching { Log.e(TAG, "запрос API", error) }
        }
    }

    fun reply(request: String): String {
        val command = CamdCommands.parse(request) ?: return "ERR bad_command"
        return when (command) {
            is CamdCommand.Open -> windows?.let { "OK " + it.open(command) } ?: "ERR no_windows"
            is CamdCommand.Close -> windows?.let { "OK " + it.close(command.inputId) } ?: "ERR no_windows"
            CamdCommand.CloseAll -> windows?.let { "OK " + it.closeAll() } ?: "ERR no_windows"
            is CamdCommand.SetKeepAvm -> windows?.let { "OK " + it.setKeepAvmStopped(command.keep) } ?: "ERR no_windows"
            CamdCommand.Status -> {
                val text = windows?.status() ?: engine.status()
                "OK " + text.ifBlank { "state=unknown" }
            }
            CamdCommand.ListPresets -> windows?.let { "OK " + it.presetsListing() } ?: "ERR no_windows"
        }
    }

    private companion object {
        const val TAG = "CamdApi"
        const val CLIENT_TIMEOUT_MS = 5_000
    }
}
