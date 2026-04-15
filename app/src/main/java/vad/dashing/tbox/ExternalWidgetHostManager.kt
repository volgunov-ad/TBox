package vad.dashing.tbox

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.Context
import android.os.SystemClock
import android.util.Log

/**
 * Single [AppWidgetHost] for embedded third-party widgets.
 *
 * We must not call [AppWidgetHost.stopListening] while any widget id may still be bound to this
 * host: the system keeps delivering [IAppWidgetHost.updateAppWidget] and a stopped host produces
 * DeadObjectException in system logs (and widgets stop updating). Ref-counted [releaseHost] only
 * tracks consumers; listening stays active for the process after the first [startListening].
 */
object ExternalWidgetHostManager {
    private const val TAG = "ExternalWidgetHost"
    private const val HOST_ID = 1024
    private const val DEFAULT_PROVIDER_REFRESH_DEBOUNCE_MS = 60_000L
    private var host: AppWidgetHost? = null
    private var refCount = 0
    private var listening = false
    private val providerRefreshLastAtMs = mutableMapOf<Int, Long>()

    @Synchronized
    private fun ensureHost(context: Context): AppWidgetHost {
        return host ?: AppWidgetHost(context.applicationContext, HOST_ID).also {
            host = it
        }
    }

    @Synchronized
    private fun ensureListening(context: Context) {
        val resolved = ensureHost(context)
        if (listening) return
        try {
            resolved.startListening()
            listening = true
        } catch (e: Exception) {
            Log.e(TAG, "AppWidgetHost.startListening failed; embedded widgets may not refresh", e)
        }
    }

    @Synchronized
    fun acquireHost(context: Context): AppWidgetHost {
        ensureListening(context)
        refCount++
        return ensureHost(context)
    }

    /**
     * Decrements the UI consumer count. Does **not** call [AppWidgetHost.stopListening]: bound
     * widgets outlive individual composables (e.g. user leaves the tab while tiles stay in prefs).
     */
    @Synchronized
    fun releaseHost() {
        if (refCount <= 0) return
        refCount--
    }

    @Synchronized
    fun allocateAppWidgetId(context: Context): Int {
        // WidgetPickerActivity can run without any dashboard holding acquireHost(); listening is
        // still required for bind/configure to receive RemoteViews from the system.
        ensureListening(context)
        return ensureHost(context).allocateAppWidgetId()
    }

    @Synchronized
    fun deleteAppWidgetId(context: Context, appWidgetId: Int) {
        try {
            ensureHost(context).deleteAppWidgetId(appWidgetId)
        } catch (_: Exception) {
            // Ignore delete errors to avoid crashing config flow.
        }
        providerRefreshLastAtMs.remove(appWidgetId)
    }

    /**
     * Requests a provider update broadcast with per-widget debounce.
     * Use [force] for explicit user/setup actions where refresh must run immediately.
     */
    @Synchronized
    fun requestProviderRefresh(
        context: Context,
        appWidgetId: Int,
        force: Boolean = false,
        minIntervalMs: Long = DEFAULT_PROVIDER_REFRESH_DEBOUNCE_MS
    ): Boolean {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return false
        val now = SystemClock.elapsedRealtime()
        if (!force) {
            val lastAt = providerRefreshLastAtMs[appWidgetId]
            if (lastAt != null && now - lastAt < minIntervalMs.coerceAtLeast(0L)) {
                return false
            }
        }

        val appWidgetManager = AppWidgetManager.getInstance(context.applicationContext)
        val provider = appWidgetManager.getAppWidgetInfo(appWidgetId)?.provider
        if (provider == null) {
            return false
        }

        try {
            context.applicationContext.sendBroadcast(
                Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                    component = provider
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
                }
            )
            providerRefreshLastAtMs[appWidgetId] = now
            return true
        } catch (e: Exception) {
            Log.w(TAG, "Could not request provider refresh for appWidgetId=$appWidgetId", e)
            return false
        }
    }
}
