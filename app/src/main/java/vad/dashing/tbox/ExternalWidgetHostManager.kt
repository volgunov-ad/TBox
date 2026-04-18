package vad.dashing.tbox

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.Context
import android.os.Handler
import android.os.Looper
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
    private val mainHandler = Handler(Looper.getMainLooper())
    private var host: AppWidgetHost? = null
    private var refCount = 0
    private var listening = false
    private val providerRefreshRequestedIds = mutableSetOf<Int>()

    @Synchronized
    private fun ensureHost(context: Context): AppWidgetHost {
        return host ?: AppWidgetHost(context.applicationContext, HOST_ID).also {
            host = it
        }
    }

    /**
     * [AppWidgetHost.startListening] should run on the main thread. If already on main, run
     * immediately; otherwise post to the main looper (callers may get the host before listening
     * completes — typical callers are on the main thread).
     */
    @Synchronized
    private fun ensureListening(context: Context) {
        val appCtx = context.applicationContext
        if (listening) return
        val startRunnable = Runnable {
            synchronized(this@ExternalWidgetHostManager) {
                if (listening) return@Runnable
                try {
                    ensureHost(appCtx).startListening()
                    listening = true
                } catch (e: Exception) {
                    Log.e(TAG, "AppWidgetHost.startListening failed; embedded widgets may not refresh", e)
                }
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            startRunnable.run()
        } else {
            mainHandler.post(startRunnable)
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
        providerRefreshRequestedIds.remove(appWidgetId)
    }

    /**
     * Requests provider update broadcast at most once per appWidgetId lifecycle.
     * Prevents redundant refresh storms when the same external widget is composed on
     * multiple screens/panels or repeatedly re-attached after recomposition.
     */
    @Synchronized
    fun requestProviderRefreshOnce(context: Context, appWidgetId: Int) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        if (!providerRefreshRequestedIds.add(appWidgetId)) return

        val appWidgetManager = AppWidgetManager.getInstance(context.applicationContext)
        val provider = appWidgetManager.getAppWidgetInfo(appWidgetId)?.provider
        if (provider == null) {
            // Provider info is not ready yet; allow a later successful retry.
            providerRefreshRequestedIds.remove(appWidgetId)
            return
        }

        try {
            context.applicationContext.sendBroadcast(
                Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                    component = provider
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
                }
            )
        } catch (e: Exception) {
            // Allow retries if sending failed due to transient state.
            providerRefreshRequestedIds.remove(appWidgetId)
            Log.w(TAG, "Could not request provider refresh for appWidgetId=$appWidgetId", e)
        }
    }
}
