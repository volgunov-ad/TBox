package vad.dashing.tbox.adb

import android.content.Context
import java.io.File
import vad.dashing.tbox.automation.AutomationActionResult

/**
 * Automation helpers for head-unit ADB TCP toggle and one-shot localhost shell.
 *
 * Shell uses [LocalhostAdbSession] with [LocalhostAdbSession.AfterSession.RestorePreviousTcp]:
 * enable :5555 only if not already available; restore off only if this session enabled it.
 */
object AdbAutomationActions {
    const val MAX_SHELL_COMMAND_CHARS = 4_000

    suspend fun setTcpEnabled(enabled: Boolean): AutomationActionResult {
        HuAdbControl.consumeError()
        HuAdbControl.setTcpEnabled(enabled)
        HuAdbControl.refresh()
        val error = HuAdbControl.lastError.value
        if (error != null) {
            return AutomationActionResult.failure(error)
        }
        return AutomationActionResult.ok(
            if (enabled) "ADB TCP включён (:5555)" else "ADB TCP выключен",
        )
    }

    suspend fun runShellCommand(context: Context, command: String): AutomationActionResult {
        val appContext = context.applicationContext
        return runShellCommandWith(
            gateway = LocalhostAdbSession.AndroidGateway(),
            keysDir = appContext.filesDir.resolve("adb"),
            clientName = LocalhostAdbSession.defaultClientName(),
            command = command,
        )
    }

    internal suspend fun runShellCommandWith(
        gateway: LocalhostAdbSession.Gateway,
        keysDir: File,
        clientName: String,
        command: String,
        host: String = LocalhostAdbSession.LOCAL_HOST,
        port: Int = HuAdbControl.TCP_ENABLED_PORT,
        readyTimeoutMs: Long = LocalhostAdbSession.TCP_READY_TIMEOUT_MS,
        nowMs: () -> Long = { System.currentTimeMillis() },
        delayMs: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
    ): AutomationActionResult {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) {
            return AutomationActionResult.failure("Команда пуста")
        }
        if (trimmed.length > MAX_SHELL_COMMAND_CHARS) {
            return AutomationActionResult.failure(
                "Команда длиннее $MAX_SHELL_COMMAND_CHARS символов",
            )
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
            afterSession = LocalhostAdbSession.AfterSession.RestorePreviousTcp,
        ) { execute ->
            execute(trimmed)
        }
        return when (session) {
            is LocalhostAdbSession.Result.Failed -> AutomationActionResult.failure(
                when (session.reason) {
                    LocalhostAdbSession.Reason.TcpEnableFailed ->
                        "Не удалось включить ADB TCP"
                    LocalhostAdbSession.Reason.TcpNotReady ->
                        "ADB TCP не поднялся вовремя"
                    LocalhostAdbSession.Reason.AdbConnectFailed ->
                        session.detail.ifBlank {
                            "Не удалось подключиться к localhost ADB"
                        }
                }.let { base ->
                    if (session.detail.isNotBlank() &&
                        session.reason != LocalhostAdbSession.Reason.AdbConnectFailed
                    ) {
                        "$base: ${session.detail}"
                    } else {
                        base
                    }
                },
            )
            is LocalhostAdbSession.Result.Ok -> {
                val shell = session.value
                val failure = AdbShellResults.failureDetail(shell)
                if (failure != null) {
                    return AutomationActionResult.failure(failure)
                }
                val output = listOf(shell.stdout, shell.stderr)
                    .firstOrNull { it.isNotBlank() }
                    .orEmpty()
                    .trim()
                    .lines()
                    .firstOrNull { it.isNotBlank() }
                    .orEmpty()
                    .take(120)
                AutomationActionResult.ok(
                    if (output.isBlank()) {
                        "Команда выполнена"
                    } else {
                        "Команда выполнена: $output"
                    },
                )
            }
        }
    }
}
