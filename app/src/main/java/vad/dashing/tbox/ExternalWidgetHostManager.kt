package vad.dashing.tbox

import android.appwidget.AppWidgetHost
import android.content.Context

object ExternalWidgetHostManager {
    private const val HOST_ID = 1024
    private var host: AppWidgetHost? = null
    private var refCount = 0

    @Synchronized
    private fun ensureHost(context: Context): AppWidgetHost {
        return host ?: AppWidgetHost(context.applicationContext, HOST_ID).also {
            host = it
        }
    }

    @Synchronized
    fun acquireHost(context: Context): AppWidgetHost {
        val resolved = ensureHost(context)
        if (refCount == 0) {
            try {
                resolved.startListening()
            } catch (_: Exception) {
                // Ignore start listening issues to keep overlay running.
            }
        }
        refCount++
        return resolved
    }

    @Synchronized
    fun releaseHost() {
        if (refCount <= 0) return
        refCount--
        if (refCount == 0) {
            try {
                host?.stopListening()
            } catch (_: Exception) {
                // Ignore stop listening issues to keep overlay running.
            }
        }
    }

    @Synchronized
    fun allocateAppWidgetId(context: Context): Int {
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
