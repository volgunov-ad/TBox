package vad.dashing.camd

import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

/**
 * One line in, one line out, on localhost only.
 * OPEN <id> <x> <y> <w> <h>
 * CLOSE
 * STATUS
 */
class CamdApiServer(private val engine: CamdEngine) {

    @Volatile
    private var running = false

    fun start() {
        if (running) return
        running = true
        thread(name = "camd-api", isDaemon = true) {
            val server = ServerSocket(DaemonScripts.API_PORT, 8, InetAddress.getByName("127.0.0.1"))
            while (running) {
                val socket = server.accept()
                thread(name = "camd-api-client", isDaemon = true) {
                    socket.use { client ->
                        val request = client.getInputStream().bufferedReader().readLine() ?: return@thread
                        val reply = reply(request)
                        client.getOutputStream().write((reply + "\n").toByteArray())
                    }
                }
            }
        }
    }

    fun reply(request: String): String {
        val command = CamdCommands.parse(request) ?: return "ERR bad_command"
        return when (command) {
            is CamdCommand.Open -> "OK " + engine.open(command)
            CamdCommand.Close -> "OK " + engine.close()
            CamdCommand.Status -> "OK " + engine.status().ifBlank { "state=unknown" }
        }
    }
}
