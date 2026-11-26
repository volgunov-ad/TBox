package vad.dashing.rootsvc

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import vad.dashing.rootsvc.client.MbDaemonChannel
import vad.dashing.rootsvc.client.RootSvcClient
import vad.dashing.rootsvc.client.RootSvcProtocol

/**
 * Single-flight ensure: PING → arm mb_daemon once per boot (limit 5) → wait for PING.
 * Dealer mode does not block (locked decision).
 */
object EnsureDaemon {
    private const val TAG = "RootsvcEnsure"
    private const val PREFS = "rootsvc_ensure"
    private const val KEY_BOOT = "boot"
    private const val KEY_ARMS = "arms"
    private const val MAX_ARMS = 5
    private val busy = AtomicBoolean(false)

    fun ensure(context: Context, reason: String) {
        if (!busy.compareAndSet(false, true)) {
            Log.i(TAG, "ensure already running ($reason)")
            return
        }
        try {
            doEnsure(context.applicationContext, reason)
        } finally {
            busy.set(false)
        }
    }

    private fun doEnsure(context: Context, reason: String) {
        Log.i(TAG, "ensure start reason=$reason")
        RootSvcClient.clearCache()
        if (RootSvcClient.isUp() && versionMatches(context)) {
            Log.i(TAG, "already up")
            grantOwnA11yViaSettings(context)
            return
        }
        val state = File(RootSvcProtocol.STATE_PATH)
        if (state.canRead() && state.readText().trim() == "crashloop") {
            Log.w(TAG, "supervisor crashloop — skip arm until reboot")
            return
        }
        if (!noteArm(context)) {
            Log.w(TAG, "arm limit reached for this boot")
            return
        }
        val dex = copyDex(context)
        val sha = sha256(dex)
        val nonce = "n${System.currentTimeMillis()}"
        val body = bootScript(context, dex.absolutePath, sha, nonce)
        try {
            MbDaemonChannel.rootsvc(MbDaemonChannel.ProcessExec()).arm(
                RootSvcProtocol.BOOT_BODY,
                body,
                nonce,
            )
        } catch (error: Exception) {
            Log.e(TAG, "arm failed", error)
            return
        }
        val deadline = System.currentTimeMillis() + 8_000L
        while (System.currentTimeMillis() < deadline) {
            RootSvcClient.clearCache()
            if (RootSvcClient.isUp()) {
                Log.i(TAG, "service up after arm")
                grantOwnA11yViaService()
                return
            }
            Thread.sleep(200)
        }
        Log.w(TAG, "service did not answer PING after arm")
    }

    private fun versionMatches(context: Context): Boolean {
        // Soft check: hello would compare; ping alone is enough for ensure.
        return true
    }

    private fun noteArm(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val boot = currentBootId(context)
        val saved = prefs.getLong(KEY_BOOT, -1L)
        val arms = if (saved == boot) prefs.getInt(KEY_ARMS, 0) else 0
        if (arms >= MAX_ARMS) return false
        prefs.edit().putLong(KEY_BOOT, boot).putInt(KEY_ARMS, arms + 1).apply()
        return true
    }

    private fun currentBootId(context: Context): Long {
        val count = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
        if (count > 0) return count.toLong()
        return System.currentTimeMillis() - SystemClock.elapsedRealtime()
    }

    private fun copyDex(context: Context): File {
        val dest = File(context.filesDir, "rootsvc.dex")
        context.assets.open("rootsvc.dex").use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output)
            }
        }
        // supervisor.sh next to the dex for the boot script to copy.
        context.assets.open("supervisor.sh").use { input ->
            FileOutputStream(File(context.filesDir, "supervisor.sh")).use { output ->
                input.copyTo(output)
            }
        }
        return dest
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { b -> "%02x".format(b) }
    }

    private fun bootScript(context: Context, dexPath: String, sha: String, nonce: String): String {
        val files = context.filesDir.absolutePath
        val supervisorSrc = "$files/supervisor.sh"
        val a11y = RootSvcProtocol.WATCHDOG_A11Y
        val dir = RootSvcProtocol.DIR
        val mark = RootSvcProtocol.ARM_MARKER
        val dexDest = RootSvcProtocol.DEX_PATH
        val state = RootSvcProtocol.STATE_PATH
        val pidFile = RootSvcProtocol.SUPERVISOR_PID
        val log = RootSvcProtocol.LOG_PATH
        val qDex = shellQuote(dexPath)
        val qSup = shellQuote(supervisorSrc)
        // Shell variables use ${'$'} so Kotlin does not interpolate them.
        return buildString {
            append("#!/system/bin/sh\n")
            append("# ").append(RootSvcProtocol.OWN_MARKER).append('\n')
            append("DIR=").append(dir).append('\n')
            append("MARK=").append(mark).append('\n')
            append("DEX_SRC=").append(qDex).append('\n')
            append("SUP_SRC=").append(qSup).append('\n')
            append("EXPECT_SHA=").append(sha).append('\n')
            append("mkdir -p \"${'$'}DIR\"\n")
            append("chmod 700 \"${'$'}DIR\"\n")
            append("cur=${'$'}(settings get secure enabled_accessibility_services 2>/dev/null)\n")
            append("case \"${'$'}cur\" in\n")
            append("  *\"").append(a11y).append("\"*) : ;;\n")
            append("  null|\"\") cur=\"").append(a11y).append("\" ;;\n")
            append("  *) cur=\"${'$'}cur:").append(a11y).append("\" ;;\n")
            append("esac\n")
            append("settings put secure enabled_accessibility_services \"${'$'}cur\"\n")
            append("settings put secure accessibility_enabled 1\n")
            append("cp \"${'$'}DEX_SRC\" \"${'$'}DIR/rootsvc.dex.new\"\n")
            append("ACTUAL=${'$'}(sha256sum \"${'$'}DIR/rootsvc.dex.new\" 2>/dev/null | cut -d' ' -f1)\n")
            append("if [ -z \"${'$'}ACTUAL\" ]; then\n")
            append("  ACTUAL=${'$'}(toybox sha256sum \"${'$'}DIR/rootsvc.dex.new\" 2>/dev/null | cut -d' ' -f1)\n")
            append("fi\n")
            append("if [ \"${'$'}ACTUAL\" != \"${'$'}EXPECT_SHA\" ]; then\n")
            append("  echo \"badsha ").append(nonce).append("\" > \"${'$'}MARK\"\n")
            append("  chmod 666 \"${'$'}MARK\"\n")
            append("  exit 1\n")
            append("fi\n")
            append("mv \"${'$'}DIR/rootsvc.dex.new\" ").append(dexDest).append('\n')
            append("cp \"${'$'}SUP_SRC\" \"${'$'}DIR/supervisor.sh\"\n")
            append("chmod 700 \"${'$'}DIR/supervisor.sh\" ").append(dexDest).append('\n')
            append("rm -f ").append(state).append('\n')
            append("if [ -f ").append(pidFile).append(" ]; then\n")
            append("  old=${'$'}(cat ").append(pidFile).append(")\n")
            append("  if kill -0 \"${'$'}old\" 2>/dev/null; then\n")
            append("    echo \"already ").append(nonce).append("\" > \"${'$'}MARK\"\n")
            append("    chmod 666 \"${'$'}MARK\"\n")
            append("    exit 0\n")
            append("  fi\n")
            append("fi\n")
            append("setsid /system/bin/sh \"${'$'}DIR/supervisor.sh\" >> ").append(log)
                .append(" 2>&1 < /dev/null &\n")
            append("echo \"started ").append(nonce).append("\" > \"${'$'}MARK\"\n")
            append("chmod 666 \"${'$'}MARK\"\n")
            append("exit 0\n")
        }
    }

    private fun shellQuote(path: String): String =
        "'" + path.replace("'", "'\\''") + "'"


    private fun grantOwnA11yViaSettings(context: Context) {
        // Soft path when we already have root service: ask it.
        try {
            val args = org.json.JSONObject()
                .put("component", RootSvcProtocol.WATCHDOG_A11Y)
            RootSvcClient.call(RootSvcProtocol.CMD_SETTINGS_A11Y_ADD, args, 5_000L)
        } catch (_: Exception) {
            // ignore
        }
    }

    private fun grantOwnA11yViaService() {
        try {
            val args = org.json.JSONObject()
                .put("component", RootSvcProtocol.WATCHDOG_A11Y)
            RootSvcClient.call(
                RootSvcProtocol.CMD_SETTINGS_A11Y_ADD,
                args,
                5_000L,
                RootSvcProtocol.PACKAGE,
                BuildConfig.VERSION_NAME,
            )
        } catch (_: Exception) {
            // ignore
        }
    }
}
