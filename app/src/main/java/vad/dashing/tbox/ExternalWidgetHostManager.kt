package vad.dashing.tbox

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup

/**
 * Single [AppWidgetHost] for embedded third-party widgets.
 *
 * We must not call [AppWidgetHost.stopListening] while any widget id may still be bound to this
 * host: the system keeps delivering [IAppWidgetHost.updateAppWidget] and a stopped host produces
 * DeadObjectException in system logs (and widgets stop updating). Ref-counted [releaseHost] only
 * tracks consumers; listening stays active for the process after the first [startListening].
 *
 * [getOrCreateHostView] caches [AppWidgetHostView]s so page switches do not recreate RemoteViews
 * on every composition (expensive on A10 HUs).
 */
object ExternalWidgetHostManager {
    private const val TAG = "ExternalWidgetHost"
    private const val HOST_ID = 1024
    private val mainHandler = Handler(Looper.getMainLooper())
    private var host: AppWidgetHost? = null
    private var refCount = 0
    private var listening = false
    private val hostViews = HashMap<Int, AppWidgetHostView>()

    /** Delay before first [createView] so the tile placeholder / panel layout can paint first. */
    const val DEFER_HOST_VIEW_MOUNT_MS = 80L

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

    /**
     * Returns a cached host view for [appWidgetId], creating it once. Detaches from any previous
     * parent so the caller can attach it to a new [AndroidView] hierarchy.
     */
    @Synchronized
    fun getOrCreateHostView(
        context: Context,
        appWidgetId: Int,
        info: AppWidgetProviderInfo,
    ): AppWidgetHostView? {
        if (appWidgetId == android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID) return null
        ensureListening(context)
        val existing = hostViews[appWidgetId]
        if (existing != null) {
            (existing.parent as? ViewGroup)?.removeView(existing)
            return existing
        }
        return try {
            val created = ensureHost(context).createView(context.applicationContext, appWidgetId, info).apply {
                setAppWidget(appWidgetId, info)
                setPadding(0, 0, 0, 0)
            }
            hostViews[appWidgetId] = created
            created
        } catch (e: Exception) {
            Log.e(TAG, "createView failed for id=$appWidgetId", e)
            null
        }
    }

    @Synchronized
    fun peekHostView(appWidgetId: Int): AppWidgetHostView? = hostViews[appWidgetId]

    @Synchronized
    fun allocateAppWidgetId(context: Context): Int {
        // WidgetPickerActivity can run without any dashboard holding acquireHost(); listening is
        // still required for bind/configure to receive RemoteViews from the system.
        ensureListening(context)
        return ensureHost(context).allocateAppWidgetId()
    }

    @Synchronized
    fun deleteAppWidgetId(context: Context, appWidgetId: Int) {
        hostViews.remove(appWidgetId)?.let { view ->
            try {
                (view.parent as? ViewGroup)?.removeView(view)
            } catch (_: Exception) {
            }
        }
        try {
            ensureHost(context).deleteAppWidgetId(appWidgetId)
        } catch (_: Exception) {
            // Ignore delete errors to avoid crashing config flow.
        }
    }
}
