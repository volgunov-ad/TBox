package vad.dashing.camdbench

import android.content.Context
import android.util.Log
import org.json.JSONObject
import vad.dashing.rootsvc.client.MbDaemonChannel
import vad.dashing.rootsvc.client.RootSvcClient
import vad.dashing.rootsvc.client.RootSvcProtocol
import vad.dashing.rootsvc.client.RootSvcResult
import java.util.concurrent.TimeUnit

/**
 * Root path for `:camdbench`: `script.run` via rootsvc first; on fallback —
 * [MbDaemonChannel] (same §3.4 lock protocol as `:camd`).
 */
object RootChannel {
    private const val TAG = "CamdBenchRoot"
    private val armLock = Any()

    fun arm(context: Context, script: String, nonce: String) = synchronized(armLock) {
        var attempt = 0
        while (attempt < CamdBenchRootBridge.BUSY_RETRIES) {
            attempt++
            val args = JSONObject()
                .put("body", script)
                .put("timeoutMs", 15_000L)
            val result = runCatching {
                RootSvcClient.callWithBootWait(RootSvcProtocol.CMD_SCRIPT_RUN, args, 15_000L)
            }.getOrElse { error ->
                RootSvcResult.unavailable("call: ${error.message}")
            }
            when (CamdBenchRootBridge.outcome(result, RootSvcProtocol.CMD_SCRIPT_RUN)) {
                CamdBenchRootBridge.Outcome.OK -> return
                CamdBenchRootBridge.Outcome.RETRY_BUSY -> {
                    Thread.sleep(CamdBenchRootBridge.BUSY_SLEEP_MS)
                    continue
                }
                CamdBenchRootBridge.Outcome.FALLBACK -> {
                    Log.i(TAG, "fallback script.run: ${result.err ?: result.reason}")
                    RootSvcClient.kick(context)
                    legacyArm(script, nonce)
                    return
                }
                CamdBenchRootBridge.Outcome.FAIL -> {
                    throw IllegalStateException(
                        CamdBenchRootBridge.failMessage(result, RootSvcProtocol.CMD_SCRIPT_RUN),
                    )
                }
            }
        }
        Log.i(TAG, "fallback script.run after busy retries")
        RootSvcClient.kick(context)
        legacyArm(script, nonce)
    }

    private fun legacyArm(script: String, nonce: String) {
        val channel = MbDaemonChannel(
            ProcessExec,
            BenchScripts.OWN_MARKER,
            BenchScripts.ARM,
        )
        channel.arm(BenchScripts.ARM_BODY, script, nonce)
    }

    private object ProcessExec : MbDaemonChannel.Exec {
        override fun run(vararg command: String) {
            val process = ProcessBuilder(*command).redirectErrorStream(true).start()
            process.inputStream.use { it.readBytes() }
            if (!process.waitFor(8, TimeUnit.SECONDS)) {
                process.destroy()
                throw IllegalStateException("timeout ${command.firstOrNull()}")
            }
        }
    }
}
