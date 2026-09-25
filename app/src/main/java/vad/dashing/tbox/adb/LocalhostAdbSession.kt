package vad.dashing.tbox.adb

import android.content.Context
import android.os.Build
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import vad.dashing.tbox.TboxRepository

/**
 * Short-lived localhost ADB shell sessions on the head unit (`127.0.0.1:5555`).
 *
 * Shared by [WriteSecureSettingsAutoGrant] and virtual-display app launches:
 * snapshot ADB TCP → enable if needed → wait for port → connect → one shell command →
 * disconnect → optionally restore previous TCP state.
 */
object LocalhostAdbSession {
    private const val TAG = "LOCALHOST_ADB"
    const val LOCAL_HOST = "127.0.0.1"
    const val TCP_READY_TIMEOUT_MS = 3_000L
    const val TCP_PROBE_TIMEOUT_MS = 250
    const val TCP_PROBE_INTERVAL_MS = 150L
    const val ADB_CONNECT_TIMEOUT_MS = 8_000
    /** Long enough for the user to accept the one-time «Allow USB debugging» dialog. */
    const val ADB_SESSION_TIMEOUT_MS = 25_000

    private val mutex = Mutex()

    enum class AfterSession {
        /** If this session turned TCP on, turn it back off. */
        RestorePreviousTcp,
        /** Keep ADB TCP enabled after disconnect (even if we enabled it). */
        LeaveTcpEnabled,
    }

    sealed class Outcome {
        data class Success(val shell: AdbShellResult) : Outcome()
        data class Failed(
            val reason: Reason,
            val detail: String = "",
        ) : Outcome()
    }

    enum class Reason {
        TcpEnableFailed,
        TcpNotReady,
        AdbConnectFailed,
    }

    interface Gateway {
        suspend fun refreshHuAdb()
        suspend fun isTcpEnabled(): Boolean
        suspend fun setTcpEnabled(enabled: Boolean)
        fun isTcpPortOpen(host: String, port: Int, timeoutMs: Int): Boolean
        fun runAdbShell(
            host: String,
            port: Int,
            command: String,
            connectTimeoutMs: Int,
            sessionTimeoutMs: Int,
            keysDir: File,
            clientName: String,
        ): AdbShellResult
    }

    fun androidGateway(): Gateway = AndroidGateway()

    fun keysDir(context: Context): File = context.applicationContext.filesDir.resolve("adb")

    fun clientName(): String = "tbox@${Build.MODEL}"

    suspend fun execute(
        gateway: Gateway,
        command: String,
        afterSession: AfterSession,
        keysDir: File,
        clientName: String,
        host: String = LOCAL_HOST,
        port: Int = HuAdbControl.TCP_ENABLED_PORT,
        readyTimeoutMs: Long = TCP_READY_TIMEOUT_MS,
        nowMs: () -> Long = { System.currentTimeMillis() },
        delayMs: suspend (Long) -> Unit = { delay(it) },
    ): Outcome = mutex.withLock {
        gateway.refreshHuAdb()
        val tcpWasEnabled = gateway.isTcpEnabled()
        var enabledByUs = false

        try {
            if (!tcpWasEnabled) {
                gateway.setTcpEnabled(true)
                gateway.refreshHuAdb()
                if (!gateway.isTcpEnabled() &&
                    !gateway.isTcpPortOpen(host, port, TCP_PROBE_TIMEOUT_MS)
                ) {
                    return@withLock Outcome.Failed(Reason.TcpEnableFailed)
                }
                enabledByUs = true
            }

            val ready = waitUntilTcpReady(
                host = host,
                port = port,
                readyTimeoutMs = readyTimeoutMs,
                probeTimeoutMs = TCP_PROBE_TIMEOUT_MS,
                probeIntervalMs = TCP_PROBE_INTERVAL_MS,
                isOpen = gateway::isTcpPortOpen,
                nowMs = nowMs,
                delayMs = delayMs,
            )
            if (!ready) {
                return@withLock Outcome.Failed(Reason.TcpNotReady)
            }

            val shellResult = try {
                gateway.runAdbShell(
                    host = host,
                    port = port,
                    command = command,
                    connectTimeoutMs = ADB_CONNECT_TIMEOUT_MS,
                    sessionTimeoutMs = ADB_SESSION_TIMEOUT_MS,
                    keysDir = keysDir,
                    clientName = clientName,
                )
            } catch (e: Exception) {
                TboxRepository.addLog(
                    level = "ERROR",
                    tag = TAG,
                    message = "ADB connect/shell failed: ${e.message ?: e.javaClass.simpleName}",
                )
                return@withLock Outcome.Failed(
                    Reason.AdbConnectFailed,
                    e.message ?: e.javaClass.simpleName,
                )
            }

            Outcome.Success(shellResult)
        } finally {
            val shouldRestore =
                enabledByUs && afterSession == AfterSession.RestorePreviousTcp
            if (shouldRestore) {
                runCatching {
                    gateway.setTcpEnabled(false)
                    gateway.refreshHuAdb()
                }.onFailure { e ->
                    TboxRepository.addLog(
                        level = "WARN",
                        tag = TAG,
                        message = "Failed to restore ADB TCP off: ${e.message ?: e.javaClass.simpleName}",
                    )
                }
            } else if (enabledByUs && afterSession == AfterSession.LeaveTcpEnabled) {
                TboxRepository.addLog(
                    level = "INFO",
                    tag = TAG,
                    message = "ADB TCP left enabled after localhost session",
                )
            }
        }
    }

    internal suspend fun waitUntilTcpReady(
        host: String,
        port: Int,
        readyTimeoutMs: Long,
        probeTimeoutMs: Int,
        probeIntervalMs: Long,
        isOpen: (String, Int, Int) -> Boolean,
        nowMs: () -> Long,
        delayMs: suspend (Long) -> Unit,
    ): Boolean {
        val deadline = nowMs() + readyTimeoutMs
        while (true) {
            if (isOpen(host, port, probeTimeoutMs)) return true
            val remaining = deadline - nowMs()
            if (remaining <= 0L) return false
            delayMs(minOf(probeIntervalMs, remaining))
        }
    }

    private class AndroidGateway : Gateway {
        override suspend fun refreshHuAdb() {
            HuAdbControl.refresh()
        }

        override suspend fun isTcpEnabled(): Boolean = HuAdbControl.state.value.tcpEnabled

        override suspend fun setTcpEnabled(enabled: Boolean) {
            HuAdbControl.setTcpEnabled(enabled)
        }

        override fun isTcpPortOpen(host: String, port: Int, timeoutMs: Int): Boolean {
            return try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), timeoutMs)
                }
                true
            } catch (_: Exception) {
                false
            }
        }

        override fun runAdbShell(
            host: String,
            port: Int,
            command: String,
            connectTimeoutMs: Int,
            sessionTimeoutMs: Int,
            keysDir: File,
            clientName: String,
        ): AdbShellResult {
            val transport = AdbTcpTransport.connect(
                host,
                port,
                timeoutMs = maxOf(connectTimeoutMs, sessionTimeoutMs),
            )
            return transport.use { tcp ->
                AdbConnection(
                    tcp,
                    AdbAuthKeys.loadOrCreate(keysDir),
                    clientName,
                ).use { connection ->
                    connection.connect()
                    connection.execute(command)
                }
            }
        }
    }
}
