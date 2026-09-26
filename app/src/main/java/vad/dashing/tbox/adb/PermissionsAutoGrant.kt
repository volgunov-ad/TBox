package vad.dashing.tbox.adb

import android.content.Context
import android.os.Build
import java.io.File
import vad.dashing.tbox.AppPermissionId
import vad.dashing.tbox.AppPermissions
import vad.dashing.tbox.TboxRepository

/**
 * Grants every missing entry from [AppPermissions.snapshot] via one localhost ADB session
 * (same command set as `scripts/hu-device-test/run_hu_full_test.py`).
 */
object PermissionsAutoGrant {
    private const val TAG = "PERMS_AUTO_GRANT"

    sealed class Outcome {
        data object AlreadyAllGranted : Outcome()
        data class Success(
            val newlyGranted: List<AppPermissionId>,
        ) : Outcome()
        data class Partial(
            val newlyGranted: List<AppPermissionId>,
            val stillMissing: List<AppPermissionId>,
        ) : Outcome()
        data class Failed(
            val reason: Reason,
            val detail: String = "",
            val stillMissing: List<AppPermissionId> = emptyList(),
        ) : Outcome()
    }

    enum class Reason {
        TcpEnableFailed,
        TcpNotReady,
        AdbConnectFailed,
        ShellCommandFailed,
    }

    data class ApplyGrantsResult(
        val commandFailures: List<String> = emptyList(),
    )

    internal interface Gateway : LocalhostAdbSession.Gateway {
        fun missingPermissionIds(): List<AppPermissionId>
        fun refreshMissingPermissionIds(): List<AppPermissionId>
    }

    suspend fun grantMissing(context: Context): Outcome {
        val appContext = context.applicationContext
        return grantMissingWith(
            gateway = AndroidGateway(appContext),
            keysDir = appContext.filesDir.resolve("adb"),
            clientName = LocalhostAdbSession.defaultClientName(),
            packageName = appContext.packageName,
            sdkInt = Build.VERSION.SDK_INT,
        )
    }

    internal suspend fun grantMissingWith(
        gateway: Gateway,
        keysDir: File,
        clientName: String,
        packageName: String,
        sdkInt: Int,
        host: String = LocalhostAdbSession.LOCAL_HOST,
        port: Int = HuAdbControl.TCP_ENABLED_PORT,
        readyTimeoutMs: Long = LocalhostAdbSession.TCP_READY_TIMEOUT_MS,
        nowMs: () -> Long = { System.currentTimeMillis() },
        delayMs: (suspend (Long) -> Unit)? = null,
    ): Outcome {
        val missingBefore = gateway.missingPermissionIds()
        if (missingBefore.isEmpty()) {
            return Outcome.AlreadyAllGranted
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
            applyGrants(
                missing = missingBefore,
                packageName = packageName,
                sdkInt = sdkInt,
                execute = execute,
            )
        }

        return when (session) {
            is LocalhostAdbSession.Result.Failed -> Outcome.Failed(
                reason = when (session.reason) {
                    LocalhostAdbSession.Reason.TcpEnableFailed -> Reason.TcpEnableFailed
                    LocalhostAdbSession.Reason.TcpNotReady -> Reason.TcpNotReady
                    LocalhostAdbSession.Reason.AdbConnectFailed -> Reason.AdbConnectFailed
                },
                detail = session.detail,
                stillMissing = missingBefore,
            )
            is LocalhostAdbSession.Result.Ok -> {
                val applyResult = session.value
                val stillMissing = gateway.refreshMissingPermissionIds()
                val newlyGranted = missingBefore.filterNot { it in stillMissing }
                when {
                    stillMissing.isEmpty() -> {
                        TboxRepository.addLog(
                            level = "INFO",
                            tag = TAG,
                            message = "All permissions granted via localhost ADB " +
                                "(${newlyGranted.joinToString { it.name }})",
                        )
                        Outcome.Success(newlyGranted)
                    }
                    newlyGranted.isEmpty() && applyResult.commandFailures.isNotEmpty() -> {
                        val detail = applyResult.commandFailures.joinToString("; ")
                        TboxRepository.addLog(
                            level = "ERROR",
                            tag = TAG,
                            message = "Permission grant shell commands failed: $detail",
                        )
                        Outcome.Failed(
                            reason = Reason.ShellCommandFailed,
                            detail = detail,
                            stillMissing = stillMissing,
                        )
                    }
                    newlyGranted.isEmpty() -> Outcome.Partial(
                        newlyGranted = emptyList(),
                        stillMissing = stillMissing,
                    )
                    else -> {
                        TboxRepository.addLog(
                            level = "WARN",
                            tag = TAG,
                            message = "Partial permission grant: ok=${newlyGranted.map { it.name }}, " +
                                "missing=${stillMissing.map { it.name }}" +
                                if (applyResult.commandFailures.isEmpty()) {
                                    ""
                                } else {
                                    ", shellFailures=${applyResult.commandFailures}"
                                },
                        )
                        Outcome.Partial(newlyGranted, stillMissing)
                    }
                }
            }
        }
    }

    internal fun applyGrants(
        missing: List<AppPermissionId>,
        packageName: String,
        sdkInt: Int,
        execute: (String) -> AdbShellResult,
    ): ApplyGrantsResult {
        val failures = mutableListOf<String>()
        fun runChecked(command: String, requireSuccess: Boolean) {
            val result = execute(command)
            val detail = AdbShellResults.failureDetail(result)
            if (detail != null && requireSuccess) {
                failures += "$command → $detail"
            }
        }

        for (id in missing) {
            when (id) {
                AppPermissionId.NotificationListener -> {
                    val component = AppPermissions.notificationListenerComponent(packageName)
                    val getResult = execute("settings get secure enabled_notification_listeners")
                    // Read failures are soft — still attempt put with empty/current stdout.
                    val current = getResult.stdout.trim()
                    val put = AppPermissions.buildNotificationListenerEnableCommand(
                        component = component,
                        currentListeners = current,
                    )
                    if (put != null) {
                        runChecked(put, requireSuccess = true)
                    }
                }
                else -> {
                    for (command in AppPermissions.buildAutoGrantShellCommands(id, packageName, sdkInt)) {
                        runChecked(command, requireSuccess = true)
                    }
                }
            }
        }
        return ApplyGrantsResult(commandFailures = failures)
    }

    private class AndroidGateway(
        private val context: Context,
    ) : Gateway, LocalhostAdbSession.Gateway by LocalhostAdbSession.AndroidGateway() {
        override fun missingPermissionIds(): List<AppPermissionId> =
            AppPermissions.snapshot(context).filterNot { it.granted }.map { it.id }

        override fun refreshMissingPermissionIds(): List<AppPermissionId> =
            missingPermissionIds()
    }
}
