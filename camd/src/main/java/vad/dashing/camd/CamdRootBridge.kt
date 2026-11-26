package vad.dashing.camd

import vad.dashing.rootsvc.client.FallbackContract
import vad.dashing.rootsvc.client.RootSvcProtocol
import vad.dashing.rootsvc.client.RootSvcResult

/**
 * Pure routing for rootsvc → mb_daemon fallback (plan §6). Unit-tested without Android.
 */
object CamdRootBridge {
    enum class Outcome {
        /** Command succeeded on rootsvc. */
        OK,
        /** Socket missing / unsupported / idempotent unknown — use legacy arm. */
        FALLBACK,
        /** busy: retry the same rootsvc call. */
        RETRY_BUSY,
        /** forbidden / bad_args / failed — do not arm legacy. */
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
