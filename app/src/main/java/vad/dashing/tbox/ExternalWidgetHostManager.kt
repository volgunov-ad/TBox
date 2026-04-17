package vad.dashing.tbox

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.RemoteViews
import android.util.Log
import kotlinx.coroutines.delay

private class TrackingAppWidgetHost(
    context: Context,
    hostId: Int,
    private val onRemoteViewsReceived: (Int) -> Unit
) : AppWidgetHost(context, hostId) {
    override fun onCreateView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo
    ): AppWidgetHostView {
        return TrackingAppWidgetHostView(context) { hasRemoteViews ->
            if (hasRemoteViews) {
                onRemoteViewsReceived(appWidgetId)
            }
        }
    }
}

private class TrackingAppWidgetHostView(
    context: Context,
    private val onRemoteViewsUpdated: (Boolean) -> Unit
) : AppWidgetHostView(context) {
    override fun updateAppWidget(remoteViews: RemoteViews?) {
        super.updateAppWidget(remoteViews)
        onRemoteViewsUpdated(remoteViews != null)
    }
}

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
    /** Max failed [AppWidgetHost.startListening] attempts from the main-looper retry runnable. */
    private const val MAX_LISTENING_RETRY_ATTEMPTS = 5
    private val mainHandler = Handler(Looper.getMainLooper())
    private var host: AppWidgetHost? = null
    private var refCount = 0
    private var listening = false
    private var listenRetryAppContext: Context? = null
    private var listenRetryAttempt = 0
    private val listenRetryDelaysMs = longArrayOf(400L, 900L, 2_000L, 4_000L, 8_000L, 15_000L)
    private val providerRefreshLastAtMs = mutableMapOf<Int, Long>()
    private val remoteViewsReceivedAtMs = mutableMapOf<Int, Long>()

    private val listenRetryRunnable = object : Runnable {
        override fun run() {
            val ctx = listenRetryAppContext ?: return
            synchronized(ExternalWidgetHostManager) {
                if (listening) {
                    listenRetryAppContext = null
                    listenRetryAttempt = 0
                    return
                }
                try {
                    ensureHost(ctx).startListening()
                    listening = true
                    listenRetryAppContext = null
                    listenRetryAttempt = 0
                    mainHandler.removeCallbacks(this)
                    Log.i(TAG, "AppWidgetHost.startListening succeeded (retry path)")
                } catch (e: Exception) {
                    Log.w(
                        TAG,
                        "AppWidgetHost.startListening failed (attempt=${listenRetryAttempt + 1})",
                        e
                    )
                    listenRetryAttempt++
                    val delayMs = listenRetryDelaysMs.getOrElse(listenRetryAttempt - 1) { 15_000L }
                    if (listenRetryAttempt < MAX_LISTENING_RETRY_ATTEMPTS) {
                        mainHandler.postDelayed(this, delayMs)
                    } else {
                        listenRetryAppContext = null
                        listenRetryAttempt = 0
                    }
                }
            }
        }
    }

    @Synchronized
    private fun ensureHost(context: Context): AppWidgetHost {
        return host ?: TrackingAppWidgetHost(
            context = context.applicationContext,
            hostId = HOST_ID,
            onRemoteViewsReceived = ::markRemoteViewsReceived
        ).also {
            host = it
        }
    }

    @Synchronized
    private fun markRemoteViewsReceived(appWidgetId: Int) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        remoteViewsReceivedAtMs[appWidgetId] = SystemClock.elapsedRealtime()
    }

    /**
     * [AppWidgetHost.startListening] must run on the main thread; failures are retried with backoff.
     */
    @Synchronized
    private fun ensureListening(context: Context) {
        val appCtx = context.applicationContext
        if (listening) return
        listenRetryAppContext = appCtx
        mainHandler.removeCallbacks(listenRetryRunnable)
        listenRetryAttempt = 0
        mainHandler.post {
            synchronized(this@ExternalWidgetHostManager) {
                if (listening) {
                    listenRetryAppContext = null
                    listenRetryAttempt = 0
                    return@post
                }
                try {
                    ensureHost(appCtx).startListening()
                    listening = true
                    listenRetryAppContext = null
                    listenRetryAttempt = 0
                    mainHandler.removeCallbacks(listenRetryRunnable)
                } catch (e: Exception) {
                    Log.e(
                        TAG,
                        "AppWidgetHost.startListening failed; scheduling retries on main",
                        e
                    )
                    listenRetryAppContext = appCtx
                    listenRetryAttempt = 0
                    mainHandler.removeCallbacks(listenRetryRunnable)
                    mainHandler.postDelayed(listenRetryRunnable, listenRetryDelaysMs[0])
                }
            }
        }
    }

    @Synchronized
    fun isListening(): Boolean = listening

    /**
     * Suspends until [isListening] becomes true or [timeoutMs] elapses (embedded widgets need this
     * before [android.appwidget.AppWidgetHost.createView] for reliable first RemoteViews).
     */
    suspend fun awaitListeningReady(timeoutMs: Long = 10_000L) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs.coerceAtLeast(0L)
        while (SystemClock.elapsedRealtime() < deadline) {
            if (isListening()) return
            delay(50)
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
        remoteViewsReceivedAtMs.remove(appWidgetId)
    }

    @Synchronized
    fun hasRemoteViewsSince(appWidgetId: Int, sinceElapsedRealtimeMs: Long): Boolean {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return false
        val lastAt = remoteViewsReceivedAtMs[appWidgetId] ?: return false
        return lastAt >= sinceElapsedRealtimeMs
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
