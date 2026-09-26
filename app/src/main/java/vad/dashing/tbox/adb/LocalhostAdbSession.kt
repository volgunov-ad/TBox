package vad.dashing.tbox.adb

import android.os.Build
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import vad.dashing.tbox.TboxRepository

/**
 * Short-lived localhost ADB session against the head unit's own adbd.
 *
 * If TCP :5555 is already accepting connections (or props say TCP is on), connect
 * without toggling. Otherwise enable TCP via [HuAdbControl], wait for the port
 * (longer after `adbd` restart), run shell commands, disconnect, then optionally
 * restore previous TCP state — but only when this session actually turned TCP on.
 *
 * Shared by [PermissionsAutoGrant], [WriteSecureSettingsAutoGrant], and
 * [VirtualDisplayAdb]. Always runs blocking socket work on [Dispatchers.IO].
 */
internal object LocalhostAdbSession {
    private const val TAG = "LOCAL_ADB"
    const val LOCAL_HOST = "127.0.0.1"
    /** Ready wait when TCP was already available (port open or props on). */
    const val TCP_READY_TIMEOUT_MS = 3_000L
    /** Ready wait after this session enables TCP (`ctl.restart adbd`). */
    const val TCP_READY_AFTER_ENABLE_TIMEOUT_MS = 12_000L
    const val TCP_PROBE_TIMEOUT_MS = 250
    const val TCP_PROBE_INTERVAL_MS = 150L
    const val ADB_CONNECT_TIMEOUT_MS = 8_000
    /** Long enough for the user to accept the one-time «Allow USB debugging» dialog. */
    const val ADB_SESSION_TIMEOUT_MS = 25_000

    private val mutex = Mutex()

    enum class AfterSession {
        /** If this session turned TCP on, turn it back off (permission grants / display refresh). */
        RestorePreviousTcp,
        /** Keep ADB TCP enabled after disconnect (virtual-display app launch). */
        LeaveTcpEnabled,
    }

    enum class Reason {
        TcpEnableFailed,
        TcpNotReady,
        AdbConnectFailed,
    }

    sealed class Result<out T> {
        data class Ok<T>(val value: T) : Result<T>()
        data class Failed(val reason: Reason, val detail: String = "") : Result<Nothing>()
    }

    interface Gateway {
        suspend fun refreshHuAdb()
        suspend fun isTcpEnabled(): Boolean
        suspend fun setTcpEnabled(enabled: Boolean)
        fun isTcpPortOpen(host: String, port: Int, timeoutMs: Int): Boolean
        fun <T> withShellSession(
            host: String,
            port: Int,
            connectTimeoutMs: Int,
            sessionTimeoutMs: Int,
            keysDir: File,
            clientName: String,
            block: (execute: (String) -> AdbShellResult) -> T,
        ): T
    }

    suspend fun <T> run(
        gateway: Gateway,
        keysDir: File,
        clientName: String,
        host: String = LOCAL_HOST,
        port: Int = HuAdbControl.TCP_ENABLED_PORT,
        readyTimeoutMs: Long = TCP_READY_TIMEOUT_MS,
        nowMs: () -> Long = { System.currentTimeMillis() },
        delayMs: suspend (Long) -> Unit = { delay(it) },
        afterSession: AfterSession = AfterSession.RestorePreviousTcp,
        block: (execute: (String) -> AdbShellResult) -> T,
    ): Result<T> = withContext(Dispatchers.IO) {
        mutex.withLock {
            gateway.refreshHuAdb()
            val propsSayEnabled = gateway.isTcpEnabled()
            val portAlreadyOpen = gateway.isTcpPortOpen(host, port, TCP_PROBE_TIMEOUT_MS)
            // Port open wins over lying/stale props — never restart adbd or restore-off.
            val tcpAlreadyAvailable = propsSayEnabled || portAlreadyOpen
            var enabledByUs = false

            try {
                if (!tcpAlreadyAvailable) {
                    gateway.setTcpEnabled(true)
                    gateway.refreshHuAdb()
                    if (!gateway.isTcpEnabled() &&
                        !gateway.isTcpPortOpen(host, port, TCP_PROBE_TIMEOUT_MS)
                    ) {
                        return@withLock Result.Failed(Reason.TcpEnableFailed)
                    }
                    // We invoked enable because TCP was not available; restore only in this case.
                    enabledByUs = true
                }

                val waitMs = if (enabledByUs) {
                    maxOf(readyTimeoutMs, TCP_READY_AFTER_ENABLE_TIMEOUT_MS)
                } else {
                    readyTimeoutMs
                }
                val ready = waitUntilTcpReady(
                    host = host,
                    port = port,
                    readyTimeoutMs = waitMs,
                    probeTimeoutMs = TCP_PROBE_TIMEOUT_MS,
                    probeIntervalMs = TCP_PROBE_INTERVAL_MS,
                    isOpen = gateway::isTcpPortOpen,
                    nowMs = nowMs,
                    delayMs = delayMs,
                )
                if (!ready) {
                    return@withLock Result.Failed(Reason.TcpNotReady)
                }

                try {
                    val value = gateway.withShellSession(
                        host = host,
                        port = port,
                        connectTimeoutMs = ADB_CONNECT_TIMEOUT_MS,
                        sessionTimeoutMs = ADB_SESSION_TIMEOUT_MS,
                        keysDir = keysDir,
                        clientName = clientName,
                        block = block,
                    )
                    Result.Ok(value)
                } catch (e: Exception) {
                    TboxRepository.addLog(
                        level = "ERROR",
                        tag = TAG,
                        message = "ADB session failed: ${e.message ?: e.javaClass.simpleName}",
                    )
                    Result.Failed(
                        Reason.AdbConnectFailed,
                        e.message ?: e.javaClass.simpleName,
                    )
                }
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
    }

    suspend fun waitUntilTcpReady(
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

    class AndroidGateway : Gateway {
        override suspend fun refreshHuAdb() {
            HuAdbControl.refresh()
        }

        /**
         * Props from [HuAdbControl] plus a live port probe — props alone can lag or lie
         * while adbd is already listening on :5555.
         */
        override suspend fun isTcpEnabled(): Boolean {
            if (HuAdbControl.state.value.tcpEnabled) return true
            return isTcpPortOpen(LOCAL_HOST, HuAdbControl.TCP_ENABLED_PORT, TCP_PROBE_TIMEOUT_MS)
        }

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

        override fun <T> withShellSession(
            host: String,
            port: Int,
            connectTimeoutMs: Int,
            sessionTimeoutMs: Int,
            keysDir: File,
            clientName: String,
            block: (execute: (String) -> AdbShellResult) -> T,
        ): T {
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
                    block { command -> connection.execute(command) }
                }
            }
        }
    }

    fun defaultClientName(): String = "tbox@${Build.MODEL}"
}
