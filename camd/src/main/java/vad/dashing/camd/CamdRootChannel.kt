package vad.dashing.camd

import android.content.Context
import android.util.Log
import org.json.JSONObject
import vad.dashing.rootsvc.client.MbDaemonChannel
import vad.dashing.rootsvc.client.RootSvcClient
import vad.dashing.rootsvc.client.RootSvcProtocol
import vad.dashing.rootsvc.client.RootSvcResult
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * A9 root path for `:camd`: allowlisted rootsvc commands first; on
 * [CamdRootBridge.Outcome.FALLBACK] — [MbDaemonChannel] with the legacy shell scripts.
 */
object CamdRootChannel {
    private const val TAG = "CamdRoot"
    private const val A11Y_COMPONENT =
        "vad.dashing.camd/vad.dashing.camd.CamdAccessibilityService"

    /** Serializes rootsvc + legacy mb_daemon arms across OPEN / recorder / recover. */
    private val armLock = Any()

    fun stopAvm(context: Context) = synchronized(armLock) {
        val nonce = "s${System.currentTimeMillis()}"
        callOrLegacy(
            context,
            RootSvcProtocol.CMD_SVC_CTL,
            JSONObject().put("action", "stop").put("name", "AVMapp"),
            5_000L,
        ) {
            legacyArmUnlocked(GrabberScripts.stopAvm(nonce), nonce)
        }
    }

    fun startAvm(context: Context) = synchronized(armLock) {
        val nonce = "a${System.currentTimeMillis()}"
        callOrLegacy(
            context,
            RootSvcProtocol.CMD_SVC_CTL,
            JSONObject().put("action", "start").put("name", "AVMapp"),
            5_000L,
        ) {
            legacyArmUnlocked(GrabberScripts.startAvm(nonce), nonce)
        }
    }

    fun grantOverlay(context: Context) = synchronized(armLock) {
        val nonce = "o${System.currentTimeMillis()}"
        callOrLegacy(
            context,
            RootSvcProtocol.CMD_PKG_APPOPS,
            JSONObject()
                .put("package", "vad.dashing.camd")
                .put("op", "SYSTEM_ALERT_WINDOW")
                .put("mode", "allow"),
            5_000L,
        ) {
            legacyArmUnlocked(GrabberScripts.grantOverlay(nonce), nonce)
        }
    }

    fun grantAccessibility(context: Context) = synchronized(armLock) {
        val nonce = "a${System.currentTimeMillis()}"
        callOrLegacy(
            context,
            RootSvcProtocol.CMD_SETTINGS_A11Y_ADD,
            JSONObject().put("component", A11Y_COMPONENT),
            5_000L,
        ) {
            legacyArmUnlocked(GrabberScripts.grantAccessibility(nonce), nonce)
        }
    }

    /**
     * Ensures `camd_grab` is installed and running via rootsvc `helper.ensure`.
     * On fallback, runs the legacy [GrabberScripts.launcher] (which also applies
     * [stopAvm] / overlay inside the shell script). Returns `true` when the rootsvc
     * path succeeded (caller must then issue discrete `svc.ctl` / `pkg.appops`).
     */
    fun ensureGrabber(context: Context, binary: File, stopAvm: Boolean): Boolean =
        synchronized(armLock) {
            if (!binary.isFile) {
                throw IllegalStateException("нет бинарника граббера: ${binary.absolutePath}")
            }
            val sha = sha256(binary)
            val args = JSONObject()
                .put("name", GrabberScripts.HELPER_NAME)
                .put("path", binary.absolutePath)
                .put("sha256", sha)
                .put("version", GrabberScripts.GRAB_VERSION)
                .put("kind", "exec")
            val nonce = "g${System.currentTimeMillis()}"
            var usedRootsvc = true
            callOrLegacy(
                context,
                RootSvcProtocol.CMD_HELPER_ENSURE,
                args,
                RootSvcProtocol.HELPER_TIMEOUT_MS,
            ) {
                usedRootsvc = false
                legacyArmUnlocked(GrabberScripts.launcher(nonce, stopAvm), nonce)
            }
            usedRootsvc
        }

    private fun callOrLegacy(
        context: Context,
        cmd: String,
        args: JSONObject?,
        timeoutMs: Long,
        legacy: () -> Unit,
    ) {
        var attempt = 0
        while (attempt < CamdRootBridge.BUSY_RETRIES) {
            attempt++
            val result = runCatching {
                RootSvcClient.call(cmd, args, timeoutMs)
            }.getOrElse { error ->
                RootSvcResult.unavailable("call: ${error.message}")
            }
            when (CamdRootBridge.outcome(result, cmd)) {
                CamdRootBridge.Outcome.OK -> return
                CamdRootBridge.Outcome.RETRY_BUSY -> {
                    Thread.sleep(CamdRootBridge.BUSY_SLEEP_MS)
                    continue
                }
                CamdRootBridge.Outcome.FALLBACK -> {
                    Log.i(TAG, "fallback $cmd: ${result.err ?: result.reason}")
                    RootSvcClient.kick(context)
                    legacy()
                    return
                }
                CamdRootBridge.Outcome.FAIL -> {
                    throw IllegalStateException(CamdRootBridge.failMessage(result, cmd))
                }
            }
        }
        Log.i(TAG, "fallback $cmd after busy retries")
        RootSvcClient.kick(context)
        legacy()
    }

    fun legacyArm(script: String, nonce: String) = synchronized(armLock) {
        legacyArmUnlocked(script, nonce)
    }

    private fun legacyArmUnlocked(script: String, nonce: String) {
        val channel = MbDaemonChannel(
            ProcessExec,
            GrabberScripts.OWN_MARKER,
            GrabberScripts.ARM,
        )
        channel.arm(GrabberScripts.ARM_BODY, script, nonce)
    }

    fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        val dig = md.digest()
        val sb = StringBuilder(dig.size * 2)
        for (b in dig) {
            sb.append(String.format(Locale.US, "%02x", b.toInt() and 0xff))
        }
        return sb.toString()
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
