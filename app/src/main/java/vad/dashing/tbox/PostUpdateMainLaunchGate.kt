package vad.dashing.tbox

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * One-shot gate: first [MainActivity] external stock-window redirect after install/update
 * may stay fullscreen. Armed by [Intent.ACTION_MY_PACKAGE_REPLACED] and/or a versionCode
 * stamp mismatch. Does **not** affect [MainActivityIntentHelper] / boot / widgets.
 */
internal object PostUpdateMainLaunchGate {
    private const val TAG = "PostUpdateMainLaunch"
    private const val PREFS = "post_update_main_launch"
    private const val KEY_LAST_SEEN_VERSION_CODE = "last_seen_version_code"
    private const val KEY_SKIP_NEXT_EXTERNAL_REDIRECT = "skip_next_external_redirect"

    fun armFromPackageReplaced(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_SKIP_NEXT_EXTERNAL_REDIRECT, true)
            .apply()
        Log.i(TAG, "Armed skip of next Main external stock-window redirect (MY_PACKAGE_REPLACED)")
    }

    /**
     * If this process's first default-display Main cold open after install/update should
     * skip DCL/Adayo redirect, consume the one-shot and stamp [currentVersionCode].
     */
    fun consumeSkipExternalRedirect(context: Context): Boolean {
        val current = currentVersionCode(context)
        val sp = prefs(context)
        val lastSeen = sp.getLong(KEY_LAST_SEEN_VERSION_CODE, 0L)
        val armed = sp.getBoolean(KEY_SKIP_NEXT_EXTERNAL_REDIRECT, false)
        val skip = LaunchMainInStockAppWindowDecision.shouldSkipExternalRedirectForFirstOpenAfterUpdate(
            lastSeenVersionCode = lastSeen,
            currentVersionCode = current,
            packageReplacedArmed = armed,
        )
        if (!skip) return false
        sp.edit()
            .putLong(KEY_LAST_SEEN_VERSION_CODE, current)
            .putBoolean(KEY_SKIP_NEXT_EXTERNAL_REDIRECT, false)
            .apply()
        Log.i(
            TAG,
            "Consuming first-open-after-update redirect skip (version=$current lastSeen=$lastSeen armed=$armed)",
        )
        return true
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun currentVersionCode(context: Context): Long {
        return try {
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
        } catch (_: Exception) {
            0L
        }
    }
}

/** Arms [PostUpdateMainLaunchGate] after APK replace so the next Main cold redirect may fullscreen. */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        PostUpdateMainLaunchGate.armFromPackageReplaced(context)
    }
}
