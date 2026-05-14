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
     * Returns the package that most recently moved to foreground within [windowMs],
     * or null if usage access is missing or events are empty.
     */
    fun lastForegroundPackageWithin(context: Context, windowMs: Long): String? {
        if (!hasUsageAccessPermission(context)) return null
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val end = System.currentTimeMillis()
        val begin = (end - windowMs).coerceAtLeast(0L)
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
