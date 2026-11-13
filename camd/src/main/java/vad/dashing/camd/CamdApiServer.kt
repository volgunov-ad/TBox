package vad.dashing.camd

import android.util.Log
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import kotlin.concurrent.thread

/**
 * One line in, one line out, on localhost only.
 * OPEN <id> <x> <y> <w> <h> [0|90|180|270 [flipH [flipV]]]
 * CLOSE
 * CLOSE <id>
 * AVM ON|OFF
 * STATUS
 */
class CamdApiServer(
    private val engine: CamdEngine,
    private val windows: CameraWindows? = null,
    private val clientTimeoutMs: Int = CLIENT_TIMEOUT_MS,
) {

    @Volatile
    private var running = false

    fun start() {
        if (running) return
        running = true
        thread(name = "camd-api", isDaemon = true) {
            try {
                val server = ServerSocket()
                server.reuseAddress = true
                server.bind(
                    InetSocketAddress(InetAddress.getByName("127.0.0.1"), DaemonScripts.API_PORT),
                    8
                )
                while (running) {
                    val socket = server.accept()
                    thread(name = "camd-api-client", isDaemon = true) {
                        serveClient(socket)
                    }
                }
            } catch (error: Throwable) {
                running = false
                Log.e(TAG, "API не слушает ${DaemonScripts.API_PORT}", error)
            }
        }
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
            Log.e(TAG, "запрос API", error)
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
        }
    }

    private companion object {
        const val TAG = "CamdApi"
        const val CLIENT_TIMEOUT_MS = 5_000
    }
}
