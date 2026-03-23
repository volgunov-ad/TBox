package vad.dashing.tbox

import android.appwidget.AppWidgetHost
import android.content.Context
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
    private var host: AppWidgetHost? = null
    private var refCount = 0
    private var listening = false

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
    }
}
