package vad.dashing.tbox

import android.content.Context
import android.content.Intent
import android.util.Log

/** Starts [BackgroundService] as a foreground service; a rejected start is logged instead of crashing the caller. */
fun startBackgroundServiceSafely(context: Context, intent: Intent, tag: String): Boolean =
    try {
        context.startForegroundService(intent)
        true
    } catch (e: Exception) {
        Log.e(tag, "Failed to start BackgroundService (${intent.action})", e)
        false
    }
