package vad.dashing.fwunlock.device

import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Base64
import java.util.concurrent.TimeUnit
import vad.dashing.tbox.adb.AdbAuthKeys
import vad.dashing.tbox.adb.AdbConnection
import vad.dashing.tbox.adb.AdbShellResult
import vad.dashing.tbox.adb.AdbTcpTransport

/**
 * Root shell on the head unit through its own adbd on 127.0.0.1:5555 and `su 0`.
 * Blocking; call from a background thread. Same transport as the Data Resize app.
 */
class RootShell private constructor(private val connection: AdbConnection) : AutoCloseable {

    fun shell(command: String): AdbShellResult = connection.execute(command)

    /** [command] must not contain single quotes. */
    fun root(command: String): AdbShellResult {
        require('\'' !in command) { "single quote in root command" }
        return shell("su 0 sh -c '$command'")
    }

    fun rootText(command: String): String {
        val result = root(command)
        if (result.exitCode != null && result.exitCode != 0) {
            throw IOException(result.stderr.ifBlank { result.stdout }.trim().ifBlank { "exit ${result.exitCode}" })
        }
        return result.stdout
    }

    fun writeFile(path: String, data: ByteArray) {
        val tmp = "$path.part"
        rootText("rm -f $tmp")
        val encoder = Base64.getEncoder()
        var off = 0
        while (off < data.size) {
            val len = minOf(UPLOAD_CHUNK, data.size - off)
            val b64 = encoder.encodeToString(data.copyOfRange(off, off + len))
            rootText("echo $b64 | base64 -d >> $tmp")
            off += len
        }
        if (data.isEmpty()) rootText(": > $tmp")
        rootText("mv $tmp $path")
    }

    override fun close() {
        connection.close()
    }

    companion object {
        const val HOST = "127.0.0.1"
        const val PORT = 5555
        /** Outgoing ADB packets are limited to 4 KiB; 2400 B is 3200 base64 chars plus the command. */
        private const val UPLOAD_CHUNK = 2400
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val SESSION_TIMEOUT_MS = 300_000

        fun open(keysDir: File, clientName: String): RootShell {
            if (!isPortOpen()) enableTcpAdb()
            val transport = AdbTcpTransport.connect(HOST, PORT, timeoutMs = CONNECT_TIMEOUT_MS)
            transport.setReadTimeoutMs(SESSION_TIMEOUT_MS)
            val connection = AdbConnection(transport, AdbAuthKeys.loadOrCreate(keysDir), clientName)
            try {
                connection.connect()
            } catch (e: Exception) {
                connection.close()
                throw e
            }
            return RootShell(connection)
        }

        fun isPortOpen(): Boolean = try {
            Socket().use { it.connect(InetSocketAddress(HOST, PORT), 300) }
            true
        } catch (_: IOException) {
            false
        }

        /** Same props TBox Monitor sets for ADB over TCP; adbd restarts and listens on [PORT]. */
        private fun enableTcpAdb() {
            for (args in listOf(
                listOf("setprop", "persist.adb.tcp.port", "$PORT"),
                listOf("setprop", "service.adb.tcp.port", "$PORT"),
                listOf("setprop", "ctl.restart", "adbd"),
            )) {
                runCatching { ProcessBuilder(args).start().waitFor(5, TimeUnit.SECONDS) }
            }
            repeat(40) {
                if (isPortOpen()) return
                Thread.sleep(300)
            }
            throw IOException("ADB по сети не включился (127.0.0.1:$PORT)")
        }
    }
}
