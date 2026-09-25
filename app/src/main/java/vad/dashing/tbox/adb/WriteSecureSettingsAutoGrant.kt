package vad.dashing.tbox.adb

import android.content.Context
import java.io.File
import vad.dashing.tbox.AppPermissions
import vad.dashing.tbox.TboxRepository

/**
 * Grants [android.Manifest.permission.WRITE_SECURE_SETTINGS] via a short-lived localhost ADB session.
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
        fun isPermissionGranted(): Boolean
    }

    suspend fun grant(context: Context): Outcome {
        val appContext = context.applicationContext
        return grantWith(
            gateway = AndroidGateway(appContext),
            keysDir = appContext.filesDir.resolve("adb"),
            clientName = LocalhostAdbSession.defaultClientName(),
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

        val session = LocalhostAdbSession.run(
            gateway = gateway,
            keysDir = keysDir,
            clientName = clientName,
            host = host,
            port = port,
            readyTimeoutMs = readyTimeoutMs,
            nowMs = nowMs,
            delayMs = delayMs,
        ) { execute ->
            execute(AppPermissions.buildWriteSecureSettingsShellCommand(packageName))
        }

        return when (session) {
            is LocalhostAdbSession.Result.Failed -> Outcome.Failed(
                reason = when (session.reason) {
                    LocalhostAdbSession.Reason.TcpEnableFailed -> Reason.TcpEnableFailed
                    LocalhostAdbSession.Reason.TcpNotReady -> Reason.TcpNotReady
                    LocalhostAdbSession.Reason.AdbConnectFailed -> Reason.AdbConnectFailed
                },
                detail = session.detail,
            )
            is LocalhostAdbSession.Result.Ok -> {
                val shellResult = session.value
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
        host, port, readyTimeoutMs, probeTimeoutMs, probeIntervalMs, isOpen, nowMs, delayMs,
    )

    private class AndroidGateway(
        private val context: Context,
    ) : Gateway, LocalhostAdbSession.Gateway by LocalhostAdbSession.AndroidGateway() {
        override fun isPermissionGranted(): Boolean =
            AppPermissions.hasWriteSecureSettings(context)
    }
}
