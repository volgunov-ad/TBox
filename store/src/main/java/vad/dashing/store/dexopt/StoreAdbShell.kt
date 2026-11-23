package vad.dashing.store.dexopt

import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import vad.dashing.tbox.adb.AdbAuthKeys
import vad.dashing.tbox.adb.AdbConnection
import vad.dashing.tbox.adb.AdbShellResult
import vad.dashing.tbox.adb.AdbTcpTransport

/**
 * Shell (no root) through the head unit's own adbd on 127.0.0.1:5555, same transport as
 * the Firewall / Data Resize apps. Blocking; call from a background thread.
 * The first connect with a new key shows the one-time «Allow USB debugging» dialog on the HU.
 */
class StoreAdbShell private constructor(private val connection: AdbConnection) : AutoCloseable {

    fun execute(command: String): AdbShellResult = connection.execute(command)

    override fun close() {
        connection.close()
    }

    companion object {
        const val HOST = "127.0.0.1"
        const val PORT = 5555
        private const val CONNECT_TIMEOUT_MS = 8_000

        /** Long enough to accept the debugging dialog on the HU. */
        private const val SESSION_TIMEOUT_MS = 30_000

        private const val PROP_PERSIST = "persist.adb.tcp.port"
        private const val PROP_SERVICE = "service.adb.tcp.port"

        fun open(keysDir: File, clientName: String): StoreAdbShell {
            val transport = AdbTcpTransport.connect(HOST, PORT, timeoutMs = CONNECT_TIMEOUT_MS)
            val connection = try {
                transport.setReadTimeoutMs(SESSION_TIMEOUT_MS)
                AdbConnection(transport, AdbAuthKeys.loadOrCreate(keysDir), clientName)
            } catch (e: Exception) {
                runCatching { transport.close() }
                throw e
            }
            try {
                connection.connect()
            } catch (e: Exception) {
                connection.close()
                throw e
            }
            return StoreAdbShell(connection)
        }

        fun isPortOpen(): Boolean = try {
            Socket().use { it.connect(InetSocketAddress(HOST, PORT), 300) }
            true
        } catch (_: IOException) {
            false
        }

        /**
         * Turns ADB over TCP on (same props as TBox Monitor; adbd restarts).
         * Returns true when this call changed the state, so the caller restores it.
         */
        fun enableTcpIfNeeded(): Boolean {
            if (isPortOpen()) return false
            setTcpPort(PORT)
            repeat(40) {
                if (isPortOpen()) return true
                Thread.sleep(300)
            }
            throw IOException("ADB по сети не включился ($HOST:$PORT)")
        }

        /** Closes the TCP port again; detached dex2oat in installd keeps running. */
        fun disableTcp() {
            setTcpPort(-1)
        }

        private fun setTcpPort(port: Int) {
            for (args in listOf(
                listOf("setprop", PROP_PERSIST, "$port"),
                listOf("setprop", PROP_SERVICE, "$port"),
                listOf("setprop", "ctl.restart", "adbd"),
            )) {
                runCatching { ProcessBuilder(args).start().waitFor(5, TimeUnit.SECONDS) }
            }
        }
    }
}
