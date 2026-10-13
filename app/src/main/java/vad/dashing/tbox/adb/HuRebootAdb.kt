package vad.dashing.tbox.adb

import android.content.Context
import vad.dashing.tbox.TboxRepository

internal sealed interface HuRebootResult {
    data object Sent : HuRebootResult
    data object NoSu : HuRebootResult
    data class Failed(val detail: String) : HuRebootResult
}

/**
 * Android 10 (VHAL) HU reboot: there is no mbCAN `SYSTEM_REBOOT`, and the shell uid lacks
 * `android.permission.REBOOT`, so the localhost ADB shell escalates with `su 0`.
 * The reboot is detached and delayed so the shell returns the marker before adbd dies.
 */
internal object HuRebootAdb {
    private const val TAG = "HU_REBOOT"
    private const val OK_MARKER = "TBOX_REBOOT_OK"
    private const val NOSU_MARKER = "TBOX_REBOOT_ERR nosu"

    const val SHELL_COMMAND =
        "if command -v su >/dev/null 2>&1; then " +
            "setsid su 0 sh -c 'sleep 1; svc power reboot' </dev/null >/dev/null 2>&1 & " +
            "echo $OK_MARKER; " +
            "else echo $NOSU_MARKER; fi"

    fun parseOutput(output: String): HuRebootResult = when {
        output.contains(OK_MARKER) -> HuRebootResult.Sent
        output.contains(NOSU_MARKER) -> HuRebootResult.NoSu
        else -> HuRebootResult.Failed(output.trim().take(120))
    }

    suspend fun reboot(context: Context): HuRebootResult {
        val app = context.applicationContext
        val session = LocalhostAdbSession.run(
            gateway = LocalhostAdbSession.AndroidGateway(),
            keysDir = app.filesDir.resolve("adb"),
            clientName = LocalhostAdbSession.defaultClientName(),
            afterSession = LocalhostAdbSession.AfterSession.LeaveTcpEnabled,
        ) { execute ->
            execute(SHELL_COMMAND)
        }
        val result = when (session) {
            is LocalhostAdbSession.Result.Failed -> HuRebootResult.Failed(session.reason.name)
            is LocalhostAdbSession.Result.Ok ->
                parseOutput("${session.value.stdout}\n${session.value.stderr}")
        }
        TboxRepository.addLog(
            level = if (result is HuRebootResult.Sent) "INFO" else "ERROR",
            tag = TAG,
            message = "HU reboot via localhost ADB: $result",
        )
        return result
    }
}
