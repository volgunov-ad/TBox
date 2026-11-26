package vad.dashing.tbox.rootsvc

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONObject
import vad.dashing.rootsvc.client.RootSvcClient
import vad.dashing.rootsvc.client.RootSvcProtocol
import vad.dashing.rootsvc.client.RootSvcResult

/** Snapshot for Settings → System «Root-служба» (A9). */
data class RootSvcMonitorStatus(
    val packageInstalled: Boolean,
    val packageVersion: String?,
    val up: Boolean,
    val serverVersion: String?,
    val proto: Int?,
    val uptimeMs: Long?,
    val state: String?,
    val crashloop: Boolean,
    val detail: String?,
)

object RootSvcMonitor {
    /** Avoid Android {@code optString(key, null)} — null fallback can NPE when the key is absent. */
    private fun jsonString(o: JSONObject, key: String): String? {
        if (!o.has(key) || o.isNull(key)) return null
        val value = o.getString(key)
        return value.takeIf { it.isNotEmpty() }
    }

    private fun jsonInt(o: JSONObject, key: String): Int? =
        if (o.has(key) && !o.isNull(key)) o.getInt(key) else null

    private fun jsonLong(o: JSONObject, key: String): Long? =
        if (o.has(key) && !o.isNull(key)) o.getLong(key) else null

    fun isPackageInstalled(packageManager: PackageManager): Boolean =
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    RootSvcProtocol.PACKAGE,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(RootSvcProtocol.PACKAGE, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    fun packageVersionName(packageManager: PackageManager): String? =
        try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    RootSvcProtocol.PACKAGE,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(RootSvcProtocol.PACKAGE, 0)
            }
            info.versionName
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }

    fun openPackage(context: Context): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage(RootSvcProtocol.PACKAGE)
            ?: return false
        launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(launch)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Blocking; call off the main thread. */
    fun fetchStatus(context: Context): RootSvcMonitorStatus {
        val pm = context.packageManager
        val installed = isPackageInstalled(pm)
        val pkgVer = if (installed) packageVersionName(pm) else null
        if (!installed) {
            return RootSvcMonitorStatus(
                packageInstalled = false,
                packageVersion = null,
                up = false,
                serverVersion = null,
                proto = null,
                uptimeMs = null,
                state = null,
                crashloop = false,
                detail = "not_installed",
            )
        }
        RootSvcClient.clearCache()
        val statusResult = RootSvcClient.call(
            RootSvcProtocol.CMD_STATUS,
            null,
            3_000L,
            context.packageName,
            null,
        )
        if (statusResult.isOk()) {
            return parseStatusResult(statusResult, installed, pkgVer)
        }
        // Older daemon without status — fall back to hello/ping.
        val hello = RootSvcClient.call(
            RootSvcProtocol.CMD_HELLO,
            null,
            2_000L,
            context.packageName,
            null,
        )
        if (hello.isOk()) {
            return parseHelloFallback(hello, installed, pkgVer)
        }
        val up = RootSvcClient.isUp()
        return RootSvcMonitorStatus(
            packageInstalled = true,
            packageVersion = pkgVer,
            up = up,
            serverVersion = null,
            proto = null,
            uptimeMs = null,
            state = null,
            crashloop = false,
            detail = statusResult.err ?: statusResult.reason ?: hello.reason,
        )
    }

    /** Blocking. Restart if up; otherwise force-ensure. */
    fun ensureOrRestart(context: Context): RootSvcResult {
        RootSvcClient.clearCache()
        if (RootSvcClient.isUp()) {
            val restart = RootSvcClient.call(
                RootSvcProtocol.CMD_ADMIN_RESTART,
                null,
                3_000L,
                context.packageName,
                null,
            )
            if (restart.isOk() || restart.kind == RootSvcResult.Kind.UNKNOWN_OUTCOME) {
                // Daemon is exiting; also nudge watchdog in case supervisor died.
                RootSvcClient.kick(context, true)
                return restart
            }
            // Unknown cmd on older daemon — fall through to ensure.
        }
        RootSvcClient.kick(context, true)
        return RootSvcResult.ok(null, 0, null, null, 0L, "kick")
    }

    fun ensure(context: Context) {
        RootSvcClient.kick(context, false)
    }

    internal fun parseStatusResult(
        result: RootSvcResult,
        installed: Boolean,
        pkgVer: String?,
    ): RootSvcMonitorStatus {
        val raw = result.raw ?: return RootSvcMonitorStatus(
            packageInstalled = installed,
            packageVersion = pkgVer,
            up = true,
            serverVersion = null,
            proto = null,
            uptimeMs = null,
            state = null,
            crashloop = false,
            detail = null,
        )
        return try {
            val o = JSONObject(raw)
            RootSvcMonitorStatus(
                packageInstalled = installed,
                packageVersion = pkgVer,
                up = true,
                serverVersion = jsonString(o, "server"),
                proto = jsonInt(o, "proto"),
                uptimeMs = jsonLong(o, "uptimeMs"),
                state = jsonString(o, "state"),
                crashloop = o.optBoolean("crashloop", false),
                detail = null,
            )
        } catch (_: Exception) {
            RootSvcMonitorStatus(
                packageInstalled = installed,
                packageVersion = pkgVer,
                up = true,
                serverVersion = null,
                proto = null,
                uptimeMs = null,
                state = null,
                crashloop = false,
                detail = "bad_status_json",
            )
        }
    }

    private fun parseHelloFallback(
        result: RootSvcResult,
        installed: Boolean,
        pkgVer: String?,
    ): RootSvcMonitorStatus {
        val raw = result.raw
        var server: String? = null
        var proto: Int? = null
        if (raw != null) {
            try {
                val o = JSONObject(raw)
                server = jsonString(o, "server")
                proto = jsonInt(o, "proto")
            } catch (_: Exception) {
                // ignore
            }
        }
        return RootSvcMonitorStatus(
            packageInstalled = installed,
            packageVersion = pkgVer,
            up = true,
            serverVersion = server,
            proto = proto,
            uptimeMs = null,
            state = null,
            crashloop = false,
            detail = null,
        )
    }
}
