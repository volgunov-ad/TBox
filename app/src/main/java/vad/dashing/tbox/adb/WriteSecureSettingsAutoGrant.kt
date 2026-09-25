package vad.dashing.tbox.adb

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import java.io.File
import kotlinx.coroutines.Dispatchers
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

    internal interface Gateway : LocalhostAdbSession.Gateway {
        suspend fun isPermissionGranted(): Boolean
    }

    suspend fun grant(context: Context): Outcome {
        val appContext = context.applicationContext
        return grantWith(
            gateway = AndroidGateway(appContext),
            keysDir = LocalhostAdbSession.keysDir(appContext),
            clientName = LocalhostAdbSession.clientName(),
            packageName = appContext.packageName,
        )
    }

    internal suspend fun grantWith(
        gateway: Gateway,
        keysDir: File,
        clientName: String,
        packageName: String,
        host: String = LocalhostAdbSession.LOCAL_HOST,
        port: Int = HuAdbControl.TCP_ENABLED_PORT,
        readyTimeoutMs: Long = LocalhostAdbSession.TCP_READY_TIMEOUT_MS,
        nowMs: () -> Long = { System.currentTimeMillis() },
        delayMs: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
    ): Outcome {
        if (gateway.isPermissionGranted()) {
            return Outcome.AlreadyGranted
        }

        val command = AppPermissions.buildWriteSecureSettingsShellCommand(packageName)
        val session = LocalhostAdbSession.execute(
            gateway = gateway,
            command = command,
            afterSession = LocalhostAdbSession.AfterSession.RestorePreviousTcp,
            keysDir = keysDir,
            clientName = clientName,
            host = host,
            port = port,
            readyTimeoutMs = readyTimeoutMs,
            nowMs = nowMs,
            delayMs = delayMs,
        )

        return when (session) {
            is LocalhostAdbSession.Outcome.Failed -> {
                val reason = when (session.reason) {
                    LocalhostAdbSession.Reason.TcpEnableFailed -> Reason.TcpEnableFailed
                    LocalhostAdbSession.Reason.TcpNotReady -> Reason.TcpNotReady
                    LocalhostAdbSession.Reason.AdbConnectFailed -> Reason.AdbConnectFailed
                }
                Outcome.Failed(reason, session.detail)
            }
            is LocalhostAdbSession.Outcome.Success -> {
                val shellResult = session.shell
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
                    return Outcome.Failed(Reason.GrantCommandFailed, detail)
                }

                if (!gateway.isPermissionGranted()) {
                    val detail = listOf(shellResult.stderr, shellResult.stdout)
                        .firstOrNull { it.isNotBlank() }
                        .orEmpty()
                    return Outcome.Failed(Reason.StillMissingAfterGrant, detail)
                }

                TboxRepository.addLog(
                    level = "INFO",
                    tag = TAG,
                    message = "WRITE_SECURE_SETTINGS granted via localhost ADB",
                )
                Outcome.Success
            }
        }
    }

    /** @see LocalhostAdbSession.waitUntilTcpReady */
    internal suspend fun waitUntilTcpReady(
        host: String,
        port: Int,
        readyTimeoutMs: Long,
        probeTimeoutMs: Int,
        probeIntervalMs: Long,
        isOpen: (String, Int, Int) -> Boolean,
        nowMs: () -> Long,
        delayMs: suspend (Long) -> Unit,
    ): Boolean = LocalhostAdbSession.waitUntilTcpReady(
        host = host,
        port = port,
        readyTimeoutMs = readyTimeoutMs,
        probeTimeoutMs = probeTimeoutMs,
        probeIntervalMs = probeIntervalMs,
        isOpen = isOpen,
        nowMs = nowMs,
        delayMs = delayMs,
    )

    private class AndroidGateway(
        private val context: Context,
    ) : Gateway {
        private val sessionGateway = LocalhostAdbSession.androidGateway()

        override suspend fun isPermissionGranted(): Boolean =
            withContext(Dispatchers.IO) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_SECURE_SETTINGS,
                ) == PackageManager.PERMISSION_GRANTED
            }

        override suspend fun refreshHuAdb() = sessionGateway.refreshHuAdb()

        override suspend fun isTcpEnabled(): Boolean = sessionGateway.isTcpEnabled()

        override suspend fun setTcpEnabled(enabled: Boolean) =
            sessionGateway.setTcpEnabled(enabled)

        override fun isTcpPortOpen(host: String, port: Int, timeoutMs: Int): Boolean =
            sessionGateway.isTcpPortOpen(host, port, timeoutMs)

        override fun runAdbShell(
            host: String,
            port: Int,
            command: String,
            connectTimeoutMs: Int,
            sessionTimeoutMs: Int,
            keysDir: File,
            clientName: String,
        ): AdbShellResult = sessionGateway.runAdbShell(
            host = host,
            port = port,
            command = command,
            connectTimeoutMs = connectTimeoutMs,
            sessionTimeoutMs = sessionTimeoutMs,
            keysDir = keysDir,
            clientName = clientName,
        )
    }
}
