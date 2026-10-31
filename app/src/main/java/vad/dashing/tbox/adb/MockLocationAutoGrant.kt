package vad.dashing.tbox.adb

import android.content.Context
import java.io.File
import vad.dashing.tbox.AppPermissions
import vad.dashing.tbox.TboxRepository
import vad.dashing.tbox.utils.isAppSelectedAsMockProvider

/**
 * Selects this app as the mock-location provider via a short-lived localhost ADB session.
 * Runs `appops set … MOCK_LOCATION allow` and `settings put secure mock_location <package>`.
 */
object MockLocationAutoGrant {
    private const val TAG = "MOCK_LOC_AUTO_GRANT"

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
        fun isMockProviderSelected(): Boolean
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
        delayMs: (suspend (Long) -> Unit)? = null,
    ): Outcome {
        if (gateway.isMockProviderSelected()) {
            return Outcome.AlreadyGranted
        }

        val commands = AppPermissions.buildMockLocationShellCommands(packageName)
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
            commands.map { execute(it) }
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
                val failures = session.value.mapNotNull { result ->
                    AdbShellResults.failureDetail(result)
                }
                if (failures.isNotEmpty()) {
                    val detail = failures.joinToString("; ")
                    TboxRepository.addLog(
                        level = "ERROR",
                        tag = TAG,
                        message = "mock location grant failed: $detail",
                    )
                    return Outcome.Failed(Reason.GrantCommandFailed, detail)
                }
                if (!gateway.isMockProviderSelected()) {
                    val detail = session.value
                        .flatMap { listOf(it.stderr, it.stdout) }
                        .firstOrNull { it.isNotBlank() }
                        .orEmpty()
                    TboxRepository.addLog(
                        level = "WARN",
                        tag = TAG,
                        message = "mock location provider still not selected after grant" +
                            if (detail.isBlank()) "" else ": $detail",
                    )
                    return Outcome.Failed(Reason.StillMissingAfterGrant, detail)
                }
                TboxRepository.addLog(
                    level = "INFO",
                    tag = TAG,
                    message = "Mock location provider selected via localhost ADB",
                )
                Outcome.Success
            }
        }
    }

    private class AndroidGateway(
        private val context: Context,
    ) : Gateway, LocalhostAdbSession.Gateway by LocalhostAdbSession.AndroidGateway() {
        override fun isMockProviderSelected(): Boolean =
            context.isAppSelectedAsMockProvider()
    }
}
