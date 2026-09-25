package vad.dashing.tbox.adb

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import vad.dashing.tbox.AppPermissions
import vad.dashing.tbox.TboxRepository

/**
 * Grants [Manifest.permission.WRITE_SECURE_SETTINGS] via a short-lived localhost ADB session.
 *
 * Flow: snapshot ADB TCP → enable if needed → wait ≤3s for :5555 → connect →
 * `pm grant` → disconnect → restore previous TCP state.
 */
object WriteSecureSettingsAutoGrant {
    private const val TAG = "WSS_AUTO_GRANT"
    const val LOCAL_HOST = "127.0.0.1"
    const val TCP_READY_TIMEOUT_MS = 3_000L
    const val TCP_PROBE_TIMEOUT_MS = 250
    const val TCP_PROBE_INTERVAL_MS = 150L
    const val ADB_CONNECT_TIMEOUT_MS = 8_000
    /** Long enough for the user to accept the one-time «Allow USB debugging» dialog. */
    const val ADB_SESSION_TIMEOUT_MS = 25_000

    private val mutex = Mutex()

    sealed class Outcome {
        data object AlreadyGranted : Outcome()
        data object Success : Outcome()
        data class Failed(
            val reason: Reason,
            val detail: String = "",
        ) : Outcome()
    }

    enum class Reason {
        TcpEnableFailed,
        TcpNotReady,
        AdbConnectFailed,
        GrantCommandFailed,
        StillMissingAfterGrant,
    }

    internal interface Gateway {
        suspend fun isPermissionGranted(): Boolean
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

    suspend fun grant(context: Context): Outcome {
        val appContext = context.applicationContext
        return grantWith(
            gateway = AndroidGateway(appContext),
            keysDir = appContext.filesDir.resolve("adb"),
            clientName = "tbox@${Build.MODEL}",
            packageName = appContext.packageName,
        )
    }

    internal suspend fun grantWith(
        gateway: Gateway,
        keysDir: File,
        clientName: String,
        packageName: String,
        host: String = LOCAL_HOST,
        port: Int = HuAdbControl.TCP_ENABLED_PORT,
        readyTimeoutMs: Long = TCP_READY_TIMEOUT_MS,
        nowMs: () -> Long = { System.currentTimeMillis() },
        delayMs: suspend (Long) -> Unit = { delay(it) },
    ): Outcome = mutex.withLock {
        if (gateway.isPermissionGranted()) {
            return@withLock Outcome.AlreadyGranted
        }

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

            val command = AppPermissions.buildWriteSecureSettingsShellCommand(packageName)
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
                    message = "ADB connect/grant failed: ${e.message ?: e.javaClass.simpleName}",
                )
                return@withLock Outcome.Failed(
                    Reason.AdbConnectFailed,
                    e.message ?: e.javaClass.simpleName,
                )
            }

            if (shellResult.exitCode != null && shellResult.exitCode != 0) {
                val detail = listOf(shellResult.stderr, shellResult.stdout)
                    .firstOrNull { it.isNotBlank() }
                    .orEmpty()
                    .ifBlank { "exit ${shellResult.exitCode}" }
                TboxRepository.addLog(
                    level = "ERROR",
                    tag = TAG,
                    message = "pm grant failed: $detail",
                )
                return@withLock Outcome.Failed(Reason.GrantCommandFailed, detail)
            }

            if (!gateway.isPermissionGranted()) {
                // Legacy shell may omit exit code; still treat missing grant as failure.
                val detail = listOf(shellResult.stderr, shellResult.stdout)
                    .firstOrNull { it.isNotBlank() }
                    .orEmpty()
                return@withLock Outcome.Failed(Reason.StillMissingAfterGrant, detail)
            }

            TboxRepository.addLog(
                level = "INFO",
                tag = TAG,
                message = "WRITE_SECURE_SETTINGS granted via localhost ADB",
            )
            Outcome.Success
        } finally {
            if (enabledByUs) {
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

    private class AndroidGateway(
        private val context: Context,
    ) : Gateway {
        override suspend fun isPermissionGranted(): Boolean =
            withContext(Dispatchers.IO) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_SECURE_SETTINGS,
                ) == PackageManager.PERMISSION_GRANTED
            }

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
            // Port readiness was already probed; use the longer session timeout so the
            // one-time «Allow USB debugging» dialog can be accepted before AUTH completes.
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
