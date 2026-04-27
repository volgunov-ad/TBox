package vad.dashing.tbox.ui

import android.content.Context
import android.content.Intent
import vad.dashing.tbox.BackgroundService
import vad.dashing.tbox.MainActivityIntentHelper
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId

internal fun launchAppFromWidget(context: Context, packageName: String) {
    if (packageName.isBlank()) return
    try {
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(packageName) ?: return
        MainActivityIntentHelper.applyExternalAppLaunchFlags(launchIntent, context)
        context.startActivity(launchIntent)
    } catch (e: Exception) {
        e.printStackTrace()
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

internal fun sendToggleSteeringWheelHeat(context: Context) {
    try {
        context.startService(
            Intent(context, BackgroundService::class.java).apply {
                action = BackgroundService.ACTION_MBCAN_COMMAND
                putExtra(
                    BackgroundService.EXTRA_MBCAN_COMMAND_TYPE,
                    BackgroundService.MBCAN_COMMAND_TOGGLE_PROPERTY
                )
                putExtra(
                    BackgroundService.EXTRA_MBCAN_PROPERTY_ID,
                    MbCanKnownVehiclePropertyId.STEERING_WHEEL_HEAT_SWITCH
                )
            }
        )
    } catch (_: Exception) {
    }
}

internal fun sendSetMbCanProperty(context: Context, propertyId: Int, value: Int) {
    try {
        context.startService(
            Intent(context, BackgroundService::class.java).apply {
                action = BackgroundService.ACTION_MBCAN_COMMAND
                putExtra(
                    BackgroundService.EXTRA_MBCAN_COMMAND_TYPE,
                    BackgroundService.MBCAN_COMMAND_SET_PROPERTY
                )
                putExtra(BackgroundService.EXTRA_MBCAN_PROPERTY_ID, propertyId)
                putExtra(BackgroundService.EXTRA_MBCAN_VALUE, value)
            }
        )
    } catch (_: Exception) {
    }
}
