package vad.dashing.tbox

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import kotlin.math.min

internal object UsageStatsHideFloatingHelper {

    /**
     * UsageEvents can arrive late or in bursts on OEM / automotive stacks; use a longer lookback
     * than the poll window so the last MOVE_TO_FOREGROUND / ACTIVITY_RESUMED is still in range.
     */
    private const val USAGE_EVENTS_LOOKBACK_MS = 300_000L

    /** queryUsageStats is coarse on short windows; query a wider span when used as fallback. */
    private const val MIN_STATS_QUERY_WINDOW_MS = 120_000L

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
     * Returns the package that is most likely in the foreground within roughly [windowMs].
     * Uses [UsageStatsManager.queryEvents] first (including this app’s own package when it appears
     * in the event stream), then falls back to [UsageStatsManager.queryUsageStats].
     */
    fun lastForegroundPackageWithin(context: Context, windowMs: Long): String? {
        if (!hasUsageAccessPermission(context)) return null
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val end = System.currentTimeMillis()
        val statsBegin = (end - maxOf(windowMs, MIN_STATS_QUERY_WINDOW_MS)).coerceAtLeast(0L)
        val eventBegin = (end - maxOf(windowMs, USAGE_EVENTS_LOOKBACK_MS)).coerceAtLeast(0L)

        foregroundFromUsageEvents(usm, eventBegin, end)?.let { return normalizePackage(it) }

        foregroundFromQueryUsageStats(usm, statsBegin, end)?.let { return normalizePackage(it) }

        return null
    }

    private fun normalizePackage(pkg: String?): String? {
        val p = pkg?.trim().orEmpty()
        return p.takeIf { it.isNotEmpty() }
    }

    private fun foregroundFromQueryUsageStats(
        usm: UsageStatsManager,
        begin: Long,
        end: Long,
    ): String? {
        return try {
            @Suppress("DEPRECATION")
            val stats: List<UsageStats> = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, begin, end)
                ?: return null
            if (stats.isEmpty()) return null
            val span = (end - begin).coerceAtLeast(30_000L)
            val freshnessCutoff = end - minOf(180_000L, span)
            val candidates = stats.filter { it.lastTimeUsed >= freshnessCutoff }
            if (candidates.isEmpty()) return null
            candidates.maxByOrNull { it.lastTimeUsed }?.packageName
        } catch (_: Exception) {
            null
        }
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
