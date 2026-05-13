package vad.dashing.tbox

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Display
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Delivers a «edit floating dashboard tile» request from [MainActivity] into Compose (main screen).
 */
object FloatingDashboardTileEditRequestBus {
    private val _pending = MutableStateFlow<Pair<String, Int>?>(null)
    val pending: StateFlow<Pair<String, Int>?> = _pending.asStateFlow()

    fun post(panelId: String, widgetIndex: Int) {
        _pending.value = panelId to widgetIndex
    }

    fun clear() {
        _pending.value = null
    }
}

/**
 * Brings an existing [MainActivity] (singleTask) to the front when possible.
 *
 * Uses [Intent.FLAG_ACTIVITY_CLEAR_TOP], [Intent.FLAG_ACTIVITY_SINGLE_TOP], and
 * [Intent.FLAG_ACTIVITY_REORDER_TO_FRONT]. [Intent.FLAG_ACTIVITY_NEW_TASK] is added only when
 * [context] is not an [Activity], because starting an activity from a non-Activity context
 * requires it (e.g. [android.app.Service], [Application], app widget [Context]).
 */
object MainActivityIntentHelper {

    const val EXTRA_FLOATING_DASHBOARD_PANEL_ID = "extra_floating_dashboard_panel_id"
    const val EXTRA_FLOATING_DASHBOARD_WIDGET_INDEX = "extra_floating_dashboard_widget_index"

    fun applyBringToFrontFlags(intent: Intent, context: Context) {
        intent.addFlags(
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        )
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun createBringToFrontIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java).also { applyBringToFrontFlags(it, context) }

    /**
     * Opens [MainActivity] on the home main screen to edit one tile of a floating overlay panel.
     */
    fun createFloatingDashboardTileEditIntent(
        context: Context,
        panelId: String,
        widgetIndex: Int,
    ): Intent =
        createBringToFrontIntent(context).apply {
            putExtra(EXTRA_FLOATING_DASHBOARD_PANEL_ID, panelId)
            putExtra(EXTRA_FLOATING_DASHBOARD_WIDGET_INDEX, widgetIndex)
        }

    /**
     * Third-party app [Intent] from [android.content.pm.PackageManager.getLaunchIntentForPackage].
     * When [MainActivity] is the default HOME launcher, the app runs in the root home task; starting
     * another app **must** use [Intent.FLAG_ACTIVITY_NEW_TASK] (and typically
     * [Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED]) or the system will not show the target activity.
     */
    fun applyExternalAppLaunchFlags(intent: Intent, @Suppress("UNUSED_PARAMETER") context: Context) {
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        )
    }

    /**
     * Prefer starting on the built-in display so OEM hosts do not route the activity to a
     * secondary display pipeline owned by another launcher.
     */
    fun launchOnDefaultDisplayOptions(): Bundle? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        return ActivityOptions.makeBasic().apply {
            launchDisplayId = Display.DEFAULT_DISPLAY
        }.toBundle()
    }

    /**
     * Opens a system screen to change the default HOME app (or at least Settings).
     * [Settings.ACTION_HOME_SETTINGS] is often missing once this app is already the launcher.
     */
    fun tryOpenDefaultHomeOrAppSettings(context: Context): Boolean {
        val opts = launchOnDefaultDisplayOptions()
        val candidates = listOf(
            Intent(Settings.ACTION_HOME_SETTINGS),
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
            Intent().setClassName("com.android.settings", "com.android.settings.Settings"),
        )
        for (base in candidates) {
            val intent = Intent(base).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent, opts)
                return true
            } catch (_: Exception) {
                try {
                    context.applicationContext.startActivity(intent, opts)
                    return true
                } catch (_: Exception) {
                }
            }
        }
        return false
    }
}
