package vad.dashing.tbox.adb

import android.os.Build
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
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
 * If props say TCP is on but the port stays closed, `adbd` is restarted once (props unchanged).
 *
 * Shared by [PermissionsAutoGrant], [WriteSecureSettingsAutoGrant], [MockLocationAutoGrant],
 * [VirtualDisplayAdb],
 * [AdbAutomationActions], and [PackageAdbActions]. Always runs blocking socket work on
 * [Dispatchers.IO]. Production shell I/O goes through [AdbRepository.withTcpShellSession]:
 * reuses a live ADB-tab TCP client when present, otherwise opens an ephemeral socket so
 * every consumer can start a session with the tab disconnected. Stream id handling for
 * sequential shells (grant-all) lives in [AdbConnection].
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
    /** Brief pause before one reconnect after a flaky first CNXN / shell open. */
    const val ADB_CONNECT_RETRY_DELAY_MS = 350L

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
        /**
         * [tcpEnabledBySession]: this session turned TCP on (decided under the session mutex);
         * with [AfterSession.LeaveTcpEnabled] the caller owns turning it back off.
         */
        data class Ok<T>(val value: T, val tcpEnabledBySession: Boolean = false) : Result<T>()
        data class Failed(val reason: Reason, val detail: String = "") : Result<Nothing>()
    }

    interface Gateway {
        suspend fun refreshHuAdb()
        suspend fun isTcpEnabled(): Boolean
        suspend fun setTcpEnabled(enabled: Boolean)
        fun isTcpPortOpen(host: String, port: Int, timeoutMs: Int): Boolean
        suspend fun <T> withShellSession(
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
        delayMs: (suspend (Long) -> Unit)? = null,
        afterSession: AfterSession = AfterSession.RestorePreviousTcp,
        /** Called as soon as this session turned TCP on — survives cancellation, unlike the result. */
        onTcpEnabled: () -> Unit = {},
        block: (execute: (String) -> AdbShellResult) -> T,
    ): Result<T> = withContext(Dispatchers.IO) {
        val delayFn: suspend (Long) -> Unit = delayMs ?: { ms -> delay(ms) }
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
                        TboxRepository.addLog(
                            level = "ERROR",
                            tag = TAG,
                            message = "ADB TCP enable failed (props off, port closed)",
                        )
                        return@withLock Result.Failed(Reason.TcpEnableFailed)
                    }
                    // We invoked enable because TCP was not available; restore only in this case.
                    enabledByUs = true
                    onTcpEnabled()
                }

                var waitMs = if (enabledByUs) {
                    maxOf(readyTimeoutMs, TCP_READY_AFTER_ENABLE_TIMEOUT_MS)
                } else {
                    readyTimeoutMs
                }
                var ready = awaitTcpPort(gateway, host, port, waitMs, nowMs, delayFn)
                if (!ready && !enabledByUs && restartAdbdIfTcpPropsOn(gateway, host, port)) {
                    waitMs += TCP_READY_AFTER_ENABLE_TIMEOUT_MS
                    ready = awaitTcpPort(
                        gateway,
                        host,
                        port,
                        TCP_READY_AFTER_ENABLE_TIMEOUT_MS,
                        nowMs,
                        delayFn,
                    )
                }
                if (!ready) {
                    TboxRepository.addLog(
                        level = "ERROR",
                        tag = TAG,
                        message = "ADB TCP not ready within ${waitMs}ms ($host:$port)",
                    )
                    return@withLock Result.Failed(Reason.TcpNotReady)
                }

                try {
                    val value = openShellSessionWithRetry(
                        gateway = gateway,
                        host = host,
                        port = port,
                        keysDir = keysDir,
                        clientName = clientName,
                        delayMs = delayFn,
                        block = block,
                    )
                    Result.Ok(value, tcpEnabledBySession = enabledByUs)
                } catch (e: CancellationException) {
                    // Composition / ViewModel teardown must not become a user-facing
                    // "ADB transport closed" Toast via Result.Failed.
                    throw e
                } catch (e: Exception) {
                    val detail = e.message ?: e.javaClass.simpleName
                    // Expected EOF during shutdown / intentional close: keep toast quiet (#401)
                    // and avoid ERROR spam in the default INFO journal — DEBUG still records it.
                    val level = if (
                        AdbIoErrors.isBenignDisconnectMessage(detail) &&
                        AdbShutdownGate.shouldSuppressBenignDisconnect()
                    ) {
                        "DEBUG"
                    } else {
                        "ERROR"
                    }
                    TboxRepository.addLog(
                        level = level,
                        tag = TAG,
                        message = "ADB session failed: $detail",
                    )
                    Result.Failed(
                        Reason.AdbConnectFailed,
                        detail,
                    )
                }
            } finally {
                val shouldRestore =
                    enabledByUs && afterSession == AfterSession.RestorePreviousTcp
                if (shouldRestore) {
                    restoreTcpOffUnlocked(gateway)
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

    /**
     * Turns TCP back off after a series of [AfterSession.LeaveTcpEnabled] sessions that
     * reported [Result.Ok.tcpEnabledBySession].
     */
    suspend fun restoreTcpOff(gateway: Gateway) {
        withContext(NonCancellable + Dispatchers.IO) {
            mutex.withLock { restoreTcpOffUnlocked(gateway) }
        }
    }

    /** Explicit TCP toggles outside a session: never restart adbd under a running shell. */
    suspend fun <T> withSessionLock(block: suspend () -> T): T =
        withContext(Dispatchers.IO) { mutex.withLock { block() } }

    private suspend fun restoreTcpOffUnlocked(gateway: Gateway) {
        // ctl.restart adbd drops live tab/ephemeral clients — expected EOF.
        // NonCancellable: a cancelled caller must not leave persist TCP on.
        withContext(NonCancellable) {
            AdbShutdownGate.withIntentionalTransportCloseSuspending {
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

    private suspend fun awaitTcpPort(
        gateway: Gateway,
        host: String,
        port: Int,
        readyTimeoutMs: Long,
        nowMs: () -> Long,
        delayMs: suspend (Long) -> Unit,
    ): Boolean = waitUntilTcpReady(
        host = host,
        port = port,
        readyTimeoutMs = readyTimeoutMs,
        probeTimeoutMs = TCP_PROBE_TIMEOUT_MS,
        probeIntervalMs = TCP_PROBE_INTERVAL_MS,
        isOpen = gateway::isTcpPortOpen,
        nowMs = nowMs,
        delayMs = delayMs,
    )

    /**
     * Props say TCP is on, yet nothing listens on the port: adbd is stopped (A10 stops it when a
     * phone cable takes the USB port) or not up yet after boot. `ctl.restart adbd` starts it
     * again; the props stay as found, so the session does not own TCP and never restores it.
     */
    private suspend fun restartAdbdIfTcpPropsOn(gateway: Gateway, host: String, port: Int): Boolean {
        gateway.refreshHuAdb()
        if (!gateway.isTcpEnabled()) return false
        TboxRepository.addLog(
            level = "WARN",
            tag = TAG,
            message = "ADB TCP props on but $host:$port closed; restarting adbd",
        )
        gateway.setTcpEnabled(true)
        gateway.refreshHuAdb()
        return true
    }

    /**
     * Opens one shell session; on a retryable first-connect failure (typical right after
     * `ctl.restart adbd` / cold Monitor start), waits briefly and tries once more.
     */
    private suspend fun <T> openShellSessionWithRetry(
        gateway: Gateway,
        host: String,
        port: Int,
        keysDir: File,
        clientName: String,
        delayMs: suspend (Long) -> Unit,
        block: (execute: (String) -> AdbShellResult) -> T,
    ): T {
        try {
            return gateway.withShellSession(
                host = host,
                port = port,
                connectTimeoutMs = ADB_CONNECT_TIMEOUT_MS,
                sessionTimeoutMs = ADB_SESSION_TIMEOUT_MS,
                keysDir = keysDir,
                clientName = clientName,
                block = block,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val detail = e.message ?: e.javaClass.simpleName
            if (!AdbIoErrors.isRetryableConnectFailure(detail)) throw e
            TboxRepository.addLog(
                level = "WARN",
                tag = TAG,
                message = "ADB session connect flaky ($detail); retrying once",
            )
            delayMs(ADB_CONNECT_RETRY_DELAY_MS)
            return gateway.withShellSession(
                host = host,
                port = port,
                connectTimeoutMs = ADB_CONNECT_TIMEOUT_MS,
                sessionTimeoutMs = ADB_SESSION_TIMEOUT_MS,
                keysDir = keysDir,
                clientName = clientName,
                block = block,
            )
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

        override suspend fun <T> withShellSession(
            host: String,
            port: Int,
            connectTimeoutMs: Int,
            sessionTimeoutMs: Int,
            keysDir: File,
            clientName: String,
            block: (execute: (String) -> AdbShellResult) -> T,
        ): T = AdbRepository.withTcpShellSession(
            host = host,
            port = port,
            connectTimeoutMs = connectTimeoutMs,
            sessionTimeoutMs = sessionTimeoutMs,
            keysDir = keysDir,
            clientName = clientName,
            block = block,
        )
    }

    fun defaultClientName(): String = "tbox@${Build.MODEL}"
}
