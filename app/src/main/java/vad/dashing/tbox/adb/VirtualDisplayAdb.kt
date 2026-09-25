package vad.dashing.tbox.adb

import android.content.Context
import android.content.pm.PackageManager
import java.io.File
import vad.dashing.tbox.TboxRepository

/**
 * Lists HU displays and launches apps onto a chosen display via localhost ADB.
 *
 * - [refreshDisplayList]: restore ADB TCP to the previous state after the dump (like permission grants).
 * - [launchOnDisplay]: leave ADB TCP enabled after `am start --display`.
 */
object VirtualDisplayAdb {
    private const val TAG = "VD_ADB"
    const val DUMPSYS_DISPLAY_COMMAND = "dumpsys display"

    sealed class RefreshOutcome {
        data class Success(val displays: List<HuDisplayInfo>) : RefreshOutcome()
        data class Failed(
            val reason: Reason,
            val detail: String = "",
        ) : RefreshOutcome()
    }

    sealed class LaunchOutcome {
        data object Success : LaunchOutcome()
        data class Failed(
            val reason: Reason,
            val detail: String = "",
        ) : LaunchOutcome()
    }

    enum class Reason {
        TcpEnableFailed,
        TcpNotReady,
        AdbConnectFailed,
        DumpParseEmpty,
        ShellCommandFailed,
        NoLaunchActivity,
        InvalidDisplayId,
    }

    suspend fun refreshDisplayList(context: Context): RefreshOutcome {
        val appContext = context.applicationContext
        return refreshDisplayListWith(
            gateway = LocalhostAdbSession.AndroidGateway(),
            keysDir = appContext.filesDir.resolve("adb"),
            clientName = LocalhostAdbSession.defaultClientName(),
        )
    }

    internal suspend fun refreshDisplayListWith(
        gateway: LocalhostAdbSession.Gateway,
        keysDir: File,
        clientName: String,
        dumpCommand: String = DUMPSYS_DISPLAY_COMMAND,
        host: String = LocalhostAdbSession.LOCAL_HOST,
        port: Int = HuAdbControl.TCP_ENABLED_PORT,
        readyTimeoutMs: Long = LocalhostAdbSession.TCP_READY_TIMEOUT_MS,
        nowMs: () -> Long = { System.currentTimeMillis() },
        delayMs: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
    ): RefreshOutcome {
        val session = LocalhostAdbSession.run(
            gateway = gateway,
            keysDir = keysDir,
            clientName = clientName,
            host = host,
            port = port,
            readyTimeoutMs = readyTimeoutMs,
            nowMs = nowMs,
            delayMs = delayMs,
            afterSession = LocalhostAdbSession.AfterSession.RestorePreviousTcp,
        ) { execute ->
            execute(dumpCommand)
        }
        return when (session) {
            is LocalhostAdbSession.Result.Failed ->
                RefreshOutcome.Failed(mapSessionReason(session.reason), session.detail)
            is LocalhostAdbSession.Result.Ok -> {
                val shell = session.value
                val dump = listOf(shell.stdout, shell.stderr)
                    .firstOrNull { it.isNotBlank() }
                    .orEmpty()
                if (shell.exitCode != null &&
                    shell.exitCode != 0 &&
                    dump.isBlank()
                ) {
                    return RefreshOutcome.Failed(
                        Reason.ShellCommandFailed,
                        "exit ${shell.exitCode}",
                    )
                }
                val displays = HuDisplayDumpParser.parse(dump)
                if (displays.isEmpty()) {
                    TboxRepository.addLog(
                        level = "WARN",
                        tag = TAG,
                        message = "dumpsys display parsed 0 displays (stdout ${shell.stdout.length} chars)",
                    )
                    return RefreshOutcome.Failed(Reason.DumpParseEmpty)
                }
                TboxRepository.addLog(
                    level = "INFO",
                    tag = TAG,
                    message = "HU displays refreshed: ${displays.joinToString { it.label() }}",
                )
                RefreshOutcome.Success(displays)
            }
        }
    }

    suspend fun launchOnDisplay(
        context: Context,
        packageName: String,
        displayId: Int,
    ): LaunchOutcome {
        val appContext = context.applicationContext
        val component = resolveLaunchComponent(appContext.packageManager, packageName)
            ?: return LaunchOutcome.Failed(Reason.NoLaunchActivity)
        if (displayId < 0) {
            return LaunchOutcome.Failed(Reason.InvalidDisplayId)
        }
        return launchOnDisplayWith(
            gateway = LocalhostAdbSession.AndroidGateway(),
            keysDir = appContext.filesDir.resolve("adb"),
            clientName = LocalhostAdbSession.defaultClientName(),
            displayId = displayId,
            component = component,
        )
    }

    internal suspend fun launchOnDisplayWith(
        gateway: LocalhostAdbSession.Gateway,
        keysDir: File,
        clientName: String,
        displayId: Int,
        component: String,
        host: String = LocalhostAdbSession.LOCAL_HOST,
        port: Int = HuAdbControl.TCP_ENABLED_PORT,
        readyTimeoutMs: Long = LocalhostAdbSession.TCP_READY_TIMEOUT_MS,
        nowMs: () -> Long = { System.currentTimeMillis() },
        delayMs: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
    ): LaunchOutcome {
        val command = buildAmStartOnDisplayCommand(displayId, component)
        val session = LocalhostAdbSession.run(
            gateway = gateway,
            keysDir = keysDir,
            clientName = clientName,
            host = host,
            port = port,
            readyTimeoutMs = readyTimeoutMs,
            nowMs = nowMs,
            delayMs = delayMs,
            afterSession = LocalhostAdbSession.AfterSession.LeaveTcpEnabled,
        ) { execute ->
            execute(command)
        }
        return when (session) {
            is LocalhostAdbSession.Result.Failed ->
                LaunchOutcome.Failed(mapSessionReason(session.reason), session.detail)
            is LocalhostAdbSession.Result.Ok -> {
                val shell = session.value
                if (shell.exitCode != null && shell.exitCode != 0) {
                    val detail = listOf(shell.stderr, shell.stdout)
                        .firstOrNull { it.isNotBlank() }
                        .orEmpty()
                        .ifBlank { "exit ${shell.exitCode}" }
                    TboxRepository.addLog(
                        level = "ERROR",
                        tag = TAG,
                        message = "am start --display failed: $detail",
                    )
                    return LaunchOutcome.Failed(Reason.ShellCommandFailed, detail)
                }
                // Legacy shell may omit exit code; treat Error-like stdout as failure.
                val combined = "${shell.stderr}\n${shell.stdout}"
                if (combined.contains("Error:", ignoreCase = true) ||
                    combined.contains("Exception", ignoreCase = true)
                ) {
                    val detail = combined.lineSequence()
                        .map { it.trim() }
                        .firstOrNull { it.isNotEmpty() }
                        .orEmpty()
                    return LaunchOutcome.Failed(Reason.ShellCommandFailed, detail)
                }
                TboxRepository.addLog(
                    level = "INFO",
                    tag = TAG,
                    message = "Launched $component on display $displayId via localhost ADB",
                )
                LaunchOutcome.Success
            }
        }
    }

    fun buildAmStartOnDisplayCommand(displayId: Int, component: String): String =
        "am start --display $displayId -n $component"

    fun resolveLaunchComponent(pm: PackageManager, packageName: String): String? {
        val pkg = packageName.trim()
        if (pkg.isEmpty()) return null
        val intent = runCatching { pm.getLaunchIntentForPackage(pkg) }.getOrNull() ?: return null
        val component = intent.component ?: return null
        return "${component.packageName}/${component.className}"
    }

    private fun mapSessionReason(reason: LocalhostAdbSession.Reason): Reason = when (reason) {
        LocalhostAdbSession.Reason.TcpEnableFailed -> Reason.TcpEnableFailed
        LocalhostAdbSession.Reason.TcpNotReady -> Reason.TcpNotReady
        LocalhostAdbSession.Reason.AdbConnectFailed -> Reason.AdbConnectFailed
    }
}
