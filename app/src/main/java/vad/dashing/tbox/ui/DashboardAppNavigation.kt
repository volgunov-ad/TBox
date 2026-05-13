package vad.dashing.tbox.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import vad.dashing.tbox.BackgroundService
import vad.dashing.tbox.MainActivityIntentHelper
import vad.dashing.tbox.R

internal fun launchAppFromWidget(context: Context, packageName: String) {
    if (packageName.isBlank()) return
    val appCtx = context.applicationContext
    try {
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(packageName) ?: run {
            Toast.makeText(
                appCtx,
                appCtx.getString(R.string.widget_app_launcher_no_launch_intent, packageName),
                Toast.LENGTH_LONG
            ).show()
            return
        }
        MainActivityIntentHelper.applyExternalAppLaunchFlags(launchIntent, context)
        val opts = MainActivityIntentHelper.launchOnDefaultDisplayOptions()
        try {
            context.startActivity(launchIntent, opts)
        } catch (e: Exception) {
            try {
                appCtx.startActivity(launchIntent, opts)
            } catch (e2: Exception) {
                Toast.makeText(
                    appCtx,
                    e2.message ?: e.message
                        ?: appCtx.getString(R.string.widget_app_launch_start_failed),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    } catch (e: Exception) {
        Toast.makeText(
            appCtx,
            e.message ?: appCtx.getString(R.string.widget_app_launch_start_failed),
            Toast.LENGTH_LONG
        ).show()
    }
}

internal fun openMainActivityFromWidget(context: Context) {
    try {
        val intent = MainActivityIntentHelper.createBringToFrontIntent(context)
        context.startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

internal fun sendToggleHideOtherFloatingPanels(
    context: Context,
    originPanelId: String,
    excludeOriginPanel: Boolean = true
) {
    if (excludeOriginPanel && originPanelId.isBlank()) return
    try {
        context.startService(
            Intent(context, BackgroundService::class.java).apply {
                action = BackgroundService.ACTION_TOGGLE_HIDE_OTHER_FLOATING_PANELS
                putExtra(BackgroundService.EXTRA_FLOATING_PANEL_ORIGIN_ID, originPanelId)
                putExtra(BackgroundService.EXTRA_FLOATING_HIDE_EXCLUDE_ORIGIN, excludeOriginPanel)
            }
        )
    } catch (_: Exception) {
    }
}

/**
 * Double-tap on «toggle floating panels enabled» tile: flip [FloatingDashboardConfig.enabled]
 * for every panel except [originPanelId], or for all panels when [toggleAllPanels] is true.
 */
internal fun sendToggleFloatingPanelsEnabled(
    context: Context,
    originPanelId: String,
    toggleAllPanels: Boolean
) {
    if (!toggleAllPanels && originPanelId.isBlank()) return
    try {
        context.startService(
            Intent(context, BackgroundService::class.java).apply {
                action = BackgroundService.ACTION_TOGGLE_FLOATING_PANELS_ENABLED
                putExtra(BackgroundService.EXTRA_FLOATING_PANEL_ORIGIN_ID, originPanelId)
                putExtra(BackgroundService.EXTRA_TOGGLE_FLOATING_ENABLED_ALL, toggleAllPanels)
            }
        )
    } catch (_: Exception) {
    }
}
