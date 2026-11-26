package vad.dashing.rootsvc

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject
import vad.dashing.rootsvc.client.MbDaemonChannel
import vad.dashing.rootsvc.client.RootSvcClient
import vad.dashing.rootsvc.client.RootSvcProtocol
import vad.dashing.rootsvc.client.RootSvcResult

/**
 * Single-flight ensure: PING → arm mb_daemon once per boot (limit 5) → wait for PING.
 * Dealer mode does not block (locked decision). A9-only: never arms on A10.
 */
object EnsureDaemon {
    private const val TAG = "RootsvcEnsure"
    private const val PREFS = "rootsvc_ensure"
    private const val KEY_BOOT = "boot"
    private const val KEY_ARMS = "arms"
    private const val MAX_ARMS = 5
    private val busy = AtomicBoolean(false)

    fun ensure(context: Context, reason: String, force: Boolean = false) {
        if (!busy.compareAndSet(false, true)) {
            Log.i(TAG, "ensure already running ($reason)")
            return
        }
        try {
            doEnsure(context.applicationContext, reason, force)
        } finally {
            busy.set(false)
        }
    }

    private fun doEnsure(context: Context, reason: String, force: Boolean) {
        Log.i(TAG, "ensure start reason=$reason force=$force")
        if (!isA9Hu()) {
            Log.w(TAG, "not A9 Mengbo HU — skip arm (RELEASE=${Build.VERSION.RELEASE})")
            return
        }
        RootSvcClient.clearCache()
        if (!force && RootSvcClient.isUp()) {
            if (versionMatches()) {
                Log.i(TAG, "already up")
                grantOwnA11yViaSettings(context)
                return
            }
            Log.i(TAG, "server version mismatch — admin.upgrade")
            if (tryUpgrade(context)) {
                Log.i(TAG, "upgraded in place")
                grantOwnA11yViaSettings(context)
                return
            }
            Log.w(TAG, "admin.upgrade failed — re-arm supervisor")
        }
        if (!hasStoragePermission(context)) {
            Log.e(
                TAG,
                "WRITE_EXTERNAL_STORAGE not granted — cannot write "
                    + RootSvcProtocol.BOOT_BODY
                    + "; grant via installer/pm or open the app once",
            )
            return
        }
        val state = File(RootSvcProtocol.STATE_PATH)
        if (!force && state.canRead() && state.readText().trim() == "crashloop") {
            Log.w(TAG, "supervisor crashloop — skip arm until reboot or force")
            return
        }
        if (!noteArm(context, force)) {
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

    /** A9 Mengbo only — same gate as dcl_install. */
    internal fun isA9Hu(): Boolean {
        if (Build.VERSION.RELEASE != "9") return false
        return getprop("ro.wt.solution.provider") == "MB"
    }

    private fun getprop(key: String): String {
        return try {
            val process = ProcessBuilder("getprop", key).start()
            val out = ByteArrayOutputStream()
            process.inputStream.use { input ->
                val buf = ByteArray(256)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                }
            }
            process.waitFor()
            out.toString(StandardCharsets.UTF_8.name()).trim()
        } catch (_: Exception) {
            ""
        }
    }

    private fun versionMatches(): Boolean {
        val hello = RootSvcClient.call(
            RootSvcProtocol.CMD_HELLO,
            null,
            2_000L,
            RootSvcProtocol.PACKAGE,
            BuildConfig.VERSION_NAME,
        )
        if (!hello.isOk()) return false
        val raw = hello.raw ?: return false
        return try {
            JSONObject(raw).optString("server") == RootSvcProtocol.SERVER_VERSION
        } catch (_: Exception) {
            false
        }
    }

    private fun tryUpgrade(context: Context): Boolean {
        val dex = copyDex(context)
        val sha = sha256(dex)
        val args = JSONObject()
            .put("path", dex.absolutePath)
            .put("sha256", sha)
        val result = RootSvcClient.call(
            RootSvcProtocol.CMD_ADMIN_UPGRADE,
            args,
            RootSvcProtocol.HELPER_TIMEOUT_MS,
            RootSvcProtocol.PACKAGE,
            BuildConfig.VERSION_NAME,
        )
        if (!result.isOk() && result.kind != RootSvcResult.Kind.UNKNOWN_OUTCOME) {
            Log.w(TAG, "admin.upgrade rejected: ${result.err ?: result.reason}")
            return false
        }
        val deadline = System.currentTimeMillis() + 8_000L
        while (System.currentTimeMillis() < deadline) {
            RootSvcClient.clearCache()
            if (RootSvcClient.isUp() && versionMatches()) {
                return true
            }
            Thread.sleep(200)
        }
        return false
    }

    private fun noteArm(context: Context, force: Boolean): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val boot = currentBootId(context)
        val saved = prefs.getLong(KEY_BOOT, -1L)
        val arms = if (saved == boot) prefs.getInt(KEY_ARMS, 0) else 0
        if (!force && arms >= MAX_ARMS) return false
        val next = if (force && arms >= MAX_ARMS) MAX_ARMS else arms + 1
        prefs.edit().putLong(KEY_BOOT, boot).putInt(KEY_ARMS, next.coerceAtMost(MAX_ARMS + 1)).apply()
        return true
    }

    private fun currentBootId(context: Context): Long {
        val count = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
        if (count > 0) return count.toLong()
        return System.currentTimeMillis() - SystemClock.elapsedRealtime()
    }

    private fun hasStoragePermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun copyDex(context: Context): File {
        val dest = File(context.filesDir, "rootsvc.dex")
        context.assets.open("rootsvc.dex").use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output)
            }
        }
        // supervisor.sh next to the dex for the boot script to copy.
        // Always rewrite as LF-only: Windows/cloud packaging can leave CRLF in assets.
        val supervisor = File(context.filesDir, "supervisor.sh")
        context.assets.open("supervisor.sh").use { input ->
            val text = input.reader(StandardCharsets.UTF_8).readText()
                .replace("\r\n", "\n")
                .replace("\r", "\n")
            supervisor.writeText(text, StandardCharsets.UTF_8)
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
        val helpers = RootSvcProtocol.HELPERS_DIR
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
            append("HELPERS=").append(helpers).append('\n')
            append("MARK=").append(mark).append('\n')
            append("DEX_SRC=").append(qDex).append('\n')
            append("SUP_SRC=").append(qSup).append('\n')
            append("EXPECT_SHA=").append(sha).append('\n')
            append("mkdir -p \"${'$'}DIR\" \"${'$'}HELPERS\"\n")
            append("chmod 711 \"${'$'}DIR\" \"${'$'}HELPERS\"\n")
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
            // Atomic replace of supervisor (N8): temp then mv, not cp onto running inode.
            append("cp \"${'$'}SUP_SRC\" \"${'$'}DIR/supervisor.sh.new\"\n")
            append("mv \"${'$'}DIR/supervisor.sh.new\" \"${'$'}DIR/supervisor.sh\"\n")
            append("chmod 700 \"${'$'}DIR/supervisor.sh\" ").append(dexDest).append('\n')
            append("rm -f ").append(state).append('\n')
            // We only arm when a restart is needed — kill a live supervisor so the new dex runs.
            append("if [ -f ").append(pidFile).append(" ]; then\n")
            append("  old=${'$'}(cat ").append(pidFile).append(")\n")
            append("  if kill -0 \"${'$'}old\" 2>/dev/null; then\n")
            append("    kill \"${'$'}old\" 2>/dev/null\n")
            append("    sleep 0.3\n")
            append("    kill -9 \"${'$'}old\" 2>/dev/null\n")
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
            val args = JSONObject()
                .put("component", RootSvcProtocol.WATCHDOG_A11Y)
            RootSvcClient.call(RootSvcProtocol.CMD_SETTINGS_A11Y_ADD, args, 5_000L)
        } catch (_: Exception) {
            // ignore
        }
    }

    private fun grantOwnA11yViaService() {
        try {
            val args = JSONObject()
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
