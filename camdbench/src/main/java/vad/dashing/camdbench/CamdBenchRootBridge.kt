package vad.dashing.camdbench

import vad.dashing.rootsvc.client.FallbackContract
import vad.dashing.rootsvc.client.RootSvcProtocol
import vad.dashing.rootsvc.client.RootSvcResult

/** Pure rootsvc → mb_daemon routing for `:camdbench` (same table as `:camd`). */
object CamdBenchRootBridge {
    enum class Outcome {
        OK,
        FALLBACK,
        RETRY_BUSY,
        FAIL,
    }

    const val BUSY_RETRIES = 3
    const val BUSY_SLEEP_MS = 300L

    @JvmStatic
    fun outcome(result: RootSvcResult?, cmd: String): Outcome {
        if (result == null) return Outcome.FALLBACK
        if (result.isErr(RootSvcProtocol.ERR_BUSY)) return Outcome.RETRY_BUSY
        if (FallbackContract.shouldFallback(result, cmd)) return Outcome.FALLBACK
        if (result.isOk()) return Outcome.OK
        return Outcome.FAIL
    }

    @JvmStatic
    fun failMessage(result: RootSvcResult?, cmd: String): String {
        if (result == null) return "rootsvc null"
        val detail = result.err ?: result.reason ?: result.msg ?: result.kind.name
        return "rootsvc $cmd: $detail"
    }
}
