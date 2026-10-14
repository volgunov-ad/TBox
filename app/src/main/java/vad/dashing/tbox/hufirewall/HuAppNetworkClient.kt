package vad.dashing.tbox.hufirewall

import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import vad.dashing.tbox.TboxRepository
import vad.dashing.tbox.adb.LocalhostAdbSession

internal sealed interface HuAppNetworkResult {
    data class Done(val outcome: HuAppNetworkOutcome) : HuAppNetworkResult
    data class AdbFailed(val reason: String) : HuAppNetworkResult
}

internal object HuAppNetworkClient {
    private const val TAG = "HU_APP_NETWORK"

    /**
     * CarService rebuilds the chains (dropping our rule) on init and on `ADAYO_BOOT_COMPLETED`,
     * which may come after TBox start, so the rule is re-checked on a schedule, not only once.
     */
    private val RECHECK_DELAYS_MS = listOf(30_000L, 60_000L, 120_000L, 300_000L)
    private const val STEADY_RECHECK_MS = 30 * 60_000L

    private val gate = Mutex()

    suspend fun keepOpen(context: Context) {
        var step = 0
        while (true) {
            val result = apply(context)
            val outcome = (result as? HuAppNetworkResult.Done)?.outcome
            if (outcome is HuAppNetworkOutcome.NoSu || outcome is HuAppNetworkOutcome.NoFirewall) return
            delay(RECHECK_DELAYS_MS.getOrElse(step++) { STEADY_RECHECK_MS })
        }
    }

    suspend fun apply(context: Context): HuAppNetworkResult = gate.withLock {
        val app = context.applicationContext
        val session = LocalhostAdbSession.run(
            gateway = LocalhostAdbSession.AndroidGateway(),
            keysDir = app.filesDir.resolve("adb"),
            clientName = LocalhostAdbSession.defaultClientName(),
        ) { execute ->
            execute(HuAppNetworkCodec.SHELL_COMMAND)
        }
        val result = when (session) {
            is LocalhostAdbSession.Result.Failed -> HuAppNetworkResult.AdbFailed(session.reason.name)
            is LocalhostAdbSession.Result.Ok -> HuAppNetworkResult.Done(
                HuAppNetworkCodec.parseOutput("${session.value.stdout}\n${session.value.stderr}"),
            )
        }
        val outcome = (result as? HuAppNetworkResult.Done)?.outcome
        if (outcome !is HuAppNetworkOutcome.Open || outcome.added > 0) {
            TboxRepository.addLog(
                level = if (outcome is HuAppNetworkOutcome.Open) "INFO" else "WARN",
                tag = TAG,
                message = "Open app network: $result",
            )
        }
        result
    }
}
