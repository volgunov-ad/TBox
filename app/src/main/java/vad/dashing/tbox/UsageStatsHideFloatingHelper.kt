package vad.dashing.tbox

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStats
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
     * Returns the package that is most likely in the foreground within [windowMs].
     * Tries [UsageStatsManager.queryUsageStats] first (often more reliable on OEM / automotive
     * head units), then falls back to [UsageStatsManager.queryEvents].
     */
    fun lastForegroundPackageWithin(context: Context, windowMs: Long): String? {
        if (!hasUsageAccessPermission(context)) return null
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val end = System.currentTimeMillis()
        val begin = (end - windowMs).coerceAtLeast(0L)
        foregroundFromQueryUsageStats(usm, begin, end)?.let { return it }
        return foregroundFromUsageEvents(usm, begin, end)
    }

    private fun foregroundFromQueryUsageStats(
        usm: UsageStatsManager,
        begin: Long,
        end: Long,
    ): String? {
        @Suppress("DEPRECATION")
        val stats: List<UsageStats> = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, begin, end)
            ?: return null
        if (stats.isEmpty()) return null
        // lastTimeUsed must be recent enough to reflect a foreground switch, not background work
        val freshnessCutoff = end - minOf(120_000L, (end - begin).coerceAtLeast(30_000L))
        val candidates = stats.filter { it.lastTimeUsed >= freshnessCutoff }
        if (candidates.isEmpty()) return null
        return candidates.maxByOrNull { it.lastTimeUsed }?.packageName
    }

    private fun foregroundFromUsageEvents(usm: UsageStatsManager, begin: Long, end: Long): String? {
        val events = usm.queryEvents(begin, end)
        val event = UsageEvents.Event()
        var lastPkg: String? = null
        var lastStamp = 0L
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val t = event.eventType
            if (t == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                t == UsageEvents.Event.ACTIVITY_RESUMED
            ) {
                if (event.timeStamp >= lastStamp) {
                    lastStamp = event.timeStamp
                    lastPkg = event.packageName
                }
            }
        }
        return lastPkg
    }
}
