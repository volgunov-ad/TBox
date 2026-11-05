package vad.dashing.tbox.adb

import android.content.Context
import java.io.File
import kotlinx.coroutines.delay
import vad.dashing.tbox.automation.AutomationActionResult

/**
 * Automation helpers for head-unit ADB TCP toggle and one-shot localhost shell.
 *
 * Shell uses [LocalhostAdbSession] with [LocalhostAdbSession.AfterSession.RestorePreviousTcp]:
 * enable :5555 only if not already available; restore off only if this session enabled it.
 *
 * During the [LocalhostAdbBootReady] window after BACKGROUND_SERVICE_STARTED, session-open
 * failures are retried with the shared boot backoff.
 */
object AdbAutomationActions {
    const val MAX_SHELL_COMMAND_CHARS = 4_000

    suspend fun setTcpEnabled(enabled: Boolean): AutomationActionResult {
        val error = LocalhostAdbSession.withSessionLock {
            HuAdbControl.consumeError()
            HuAdbControl.setTcpEnabled(enabled)
            HuAdbControl.refresh()
            HuAdbControl.lastError.value
        }
        if (error != null) {
            return AutomationActionResult.failure(error)
        }
        return AutomationActionResult.ok(
            if (enabled) "ADB TCP включён (:5555)" else "ADB TCP выключен",
        )
    }

    suspend fun forceStopPackage(context: Context, packageName: String): AutomationActionResult {
        val pkg = packageName.trim()
        if (pkg.isEmpty()) {
            return AutomationActionResult.failure("Выберите приложение")
        }
        return runShellCommand(context, PackageAdbActions.buildForceStopCommand(pkg))
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
        delayMs: (suspend (Long) -> Unit)? = null,
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
        // Tests inject delayMs — single attempt keeps FakeGateway deterministic.
        val delays = if (delayMs != null) {
            listOf(0L)
        } else {
            LocalhostAdbBootReady.delaysForCurrentWindow()
        }
        val attempt = LocalhostAdbBootReady.runAttempts(
            delaysMs = delays,
            pause = delayMs ?: { ms -> delay(ms) },
            shouldRetry = { shell ->
                shell.sessionReason != null &&
                    LocalhostAdbBootReady.isRetryableSessionReason(shell.sessionReason)
            },
            attempt = {
                runShellOnce(
                    gateway = gateway,
                    keysDir = keysDir,
                    clientName = clientName,
                    host = host,
                    port = port,
                    readyTimeoutMs = readyTimeoutMs,
                    nowMs = nowMs,
                    delayMs = delayMs,
                    trimmed = trimmed,
                )
            },
        )
        return attempt.result
    }

    private data class ShellAttempt(
        val result: AutomationActionResult,
        val sessionReason: LocalhostAdbSession.Reason? = null,
    )

    private suspend fun runShellOnce(
        gateway: LocalhostAdbSession.Gateway,
        keysDir: File,
        clientName: String,
        host: String,
        port: Int,
        readyTimeoutMs: Long,
        nowMs: () -> Long,
        delayMs: (suspend (Long) -> Unit)?,
        trimmed: String,
    ): ShellAttempt {
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
            is LocalhostAdbSession.Result.Failed -> ShellAttempt(
                result = AutomationActionResult.failure(
                    LocalhostAdbReasons.message(
                        context = null,
                        reason = session.reason,
                        detail = session.detail,
                    ),
                ),
                sessionReason = session.reason,
            )
            is LocalhostAdbSession.Result.Ok -> {
                val shell = session.value
                val failure = AdbShellResults.failureDetail(shell)
                if (failure != null) {
                    return ShellAttempt(AutomationActionResult.failure(failure))
                }
                val output = listOf(shell.stdout, shell.stderr)
                    .firstOrNull { it.isNotBlank() }
                    .orEmpty()
                    .trim()
                    .lines()
                    .firstOrNull { it.isNotBlank() }
                    .orEmpty()
                    .take(120)
                ShellAttempt(
                    AutomationActionResult.ok(
                        if (output.isBlank()) {
                            "Команда выполнена"
                        } else {
                            "Команда выполнена: $output"
                        },
                    ),
                )
            }
        }
    }
}
