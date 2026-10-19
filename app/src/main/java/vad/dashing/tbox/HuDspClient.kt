package vad.dashing.tbox

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import vad.dashing.tbox.adb.AdbShellResults
import vad.dashing.tbox.adb.LocalhostAdbSession

internal sealed interface HuDspResult {
    data class Accepted(val mode: Int) : HuDspResult
    data class AdbFailed(val reason: String) : HuDspResult
    data class ServiceFailed(val status: Int?) : HuDspResult
}

internal object HuDspClient {
    private val gate = Mutex()

    /**
     * TCP stays on across the whole retry series and is restored once at the end: toggling it
     * per attempt would restart adbd up to 10 times during HU boot.
     */
    suspend fun applyAtStartup(context: Context, mode: Int) {
        val enabledTcp = AtomicBoolean(false)
        try {
            HuDspStartup.run(pause = { delay(it) }) {
                applyOnce(context, mode, LocalhostAdbSession.AfterSession.LeaveTcpEnabled) {
                    enabledTcp.set(true)
                }
            }
        } finally {
            if (enabledTcp.get()) LocalhostAdbSession.restoreTcpOff(LocalhostAdbSession.AndroidGateway())
        }
    }

    suspend fun apply(context: Context, mode: Int): HuDspResult =
        applyOnce(context, mode, LocalhostAdbSession.AfterSession.RestorePreviousTcp) {}

    private suspend fun applyOnce(
        context: Context,
        mode: Int,
        afterSession: LocalhostAdbSession.AfterSession,
        onTcpEnabled: () -> Unit,
    ): HuDspResult = gate.withLock {
        currentCoroutineContext().ensureActive()
        val command = HuDspMode.writeCommand(mode)
        val app = context.applicationContext
        val session = LocalhostAdbSession.run(
            gateway = LocalhostAdbSession.AndroidGateway(),
            keysDir = app.filesDir.resolve("adb"),
            clientName = LocalhostAdbSession.defaultClientName(),
            afterSession = afterSession,
            onTcpEnabled = onTcpEnabled,
        ) { execute ->
            val reply = execute(command)
            val status = if (AdbShellResults.isFailure(reply)) null else HuDspMode.parseStatus(reply.stdout)
            if (status == 0) HuDspResult.Accepted(mode) else HuDspResult.ServiceFailed(status)
        }
        val result = when (session) {
            is LocalhostAdbSession.Result.Failed -> HuDspResult.AdbFailed(session.reason.name)
            is LocalhostAdbSession.Result.Ok -> session.value
        }
        TboxRepository.addLog(
            level = if (result is HuDspResult.Accepted) "INFO" else "WARN",
            tag = "HU_DSP",
            message = "Set DSP mode $mode: $result",
        )
        result
    }
}
