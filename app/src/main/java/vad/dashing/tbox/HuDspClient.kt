package vad.dashing.tbox

import android.content.Context
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

    suspend fun applyAtStartup(context: Context, mode: Int) {
        HuDspStartup.run(pause = { delay(it) }, apply = { apply(context, mode) })
    }

    suspend fun apply(context: Context, mode: Int): HuDspResult = gate.withLock {
        currentCoroutineContext().ensureActive()
        val command = HuDspMode.writeCommand(mode)
        val app = context.applicationContext
        val session = LocalhostAdbSession.run(
            gateway = LocalhostAdbSession.AndroidGateway(),
            keysDir = app.filesDir.resolve("adb"),
            clientName = LocalhostAdbSession.defaultClientName(),
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
