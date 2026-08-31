package vad.dashing.tbox

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process

internal object UsageStatsHideFloatingHelper {

    fun hasUsageAccessPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Last MOVE_TO_FOREGROUND / ACTIVITY_RESUMED in exactly [windowMs].
     * Empty window → null (caller keeps a sticky last package).
     */
    fun lastForegroundPackageWithin(context: Context, windowMs: Long): String? {
        if (!hasUsageAccessPermission(context)) return null
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null
        val end = System.currentTimeMillis()
        val begin = (end - windowMs.coerceAtLeast(0L)).coerceAtLeast(0L)
        return normalizePackage(foregroundFromUsageEvents(usm, begin, end))
    }

    private fun normalizePackage(pkg: String?): String? {
        val p = pkg?.trim().orEmpty()
        return p.takeIf { it.isNotEmpty() }
    }

    private fun isForegroundEventType(type: Int): Boolean {
        if (type == UsageEvents.Event.MOVE_TO_FOREGROUND) return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            type == UsageEvents.Event.ACTIVITY_RESUMED
        } else {
            false
        }
    }

    private fun foregroundFromUsageEvents(usm: UsageStatsManager, begin: Long, end: Long): String? {
        return try {
            val events = usm.queryEvents(begin, end)
            val event = UsageEvents.Event()
            var bestPkg: String? = null
            var bestStamp = -1L
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (!isForegroundEventType(event.eventType)) continue
                val pkg = event.packageName ?: continue
                if (event.timeStamp >= bestStamp) {
                    bestStamp = event.timeStamp
                    bestPkg = pkg
                }
            }
            bestPkg
        } catch (_: Exception) {
            null
        }
    }
}
