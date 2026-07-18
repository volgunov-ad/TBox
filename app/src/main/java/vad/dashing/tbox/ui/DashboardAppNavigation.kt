package vad.dashing.tbox.ui

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import vad.dashing.tbox.APP_LAUNCHER_WIDGET_DATA_KEY
import vad.dashing.tbox.BackgroundService
import vad.dashing.tbox.FloatingDashboardWidgetConfig
import vad.dashing.tbox.MainActivityIntentHelper
import vad.dashing.tbox.MirrorAdjustModeRepository
import vad.dashing.tbox.freeform.FreeformCompanionSession
import vad.dashing.tbox.freeform.FreeformLaunchHelper
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId

private val steeringHeatToggleLock = Any()
private var steeringHeatToggleBlockedUntilMs = 0L
private const val STEERING_HEAT_TOGGLE_LOCKOUT_MS = 500L

private val windscreenHeatToggleLock = Any()
private var windscreenHeatToggleBlockedUntilMs = 0L

private val wiperMaintenanceToggleLock = Any()
private var wiperMaintenanceToggleBlockedUntilMs = 0L

private val parkingRadarToggleLock = Any()
private var parkingRadarToggleBlockedUntilMs = 0L

private val hvacDefrosterToggleLock = Any()
private var hvacDefrosterToggleBlockedUntilMs = 0L

private val hvacAirRecirculationToggleLock = Any()
private var hvacAirRecirculationToggleBlockedUntilMs = 0L

private val hvacAcToggleLock = Any()
private var hvacAcToggleBlockedUntilMs = 0L

private val hvacAutoToggleLock = Any()
private var hvacAutoToggleBlockedUntilMs = 0L

private val hvacDefrosterFrontToggleLock = Any()
private var hvacDefrosterFrontToggleBlockedUntilMs = 0L

private val hvacSyncToggleLock = Any()
private var hvacSyncToggleBlockedUntilMs = 0L

internal fun launchAppFromWidget(context: Context, packageName: String) {
    launchAppFromWidget(
        context,
        FloatingDashboardWidgetConfig(
            dataKey = APP_LAUNCHER_WIDGET_DATA_KEY,
            launcherAppPackage = packageName,
        ),
    )
}

internal fun launchAppFromWidget(context: Context, config: FloatingDashboardWidgetConfig) {
    val packageName = config.launcherAppPackage.trim()
    if (packageName.isBlank()) return

    if (!config.launcherFreeformEnabled) {
        FreeformCompanionSession.clear()
        try {
            val pm = context.packageManager
            val launchIntent = pm.getLaunchIntentForPackage(packageName) ?: return
            MainActivityIntentHelper.applyExternalAppLaunchFlags(launchIntent, context)
            context.startActivity(launchIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return
    }

    if (FreeformCompanionSession.isActiveFor(packageName)) {
        FreeformLaunchHelper.exitWindowMode(context)
        return
    }

    val launched = FreeformLaunchHelper.launchCompanion(
        context = context,
        packageName = packageName,
        side = config.launcherFreeformSide,
        percent = config.launcherFreeformPercent,
    )
    if (!launched) {
        try {
            val pm = context.packageManager
            val launchIntent = pm.getLaunchIntentForPackage(packageName) ?: return
            MainActivityIntentHelper.applyExternalAppLaunchFlags(launchIntent, context)
            context.startActivity(launchIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
    val now = SystemClock.uptimeMillis()
    synchronized(steeringHeatToggleLock) {
        if (now < steeringHeatToggleBlockedUntilMs) return
        steeringHeatToggleBlockedUntilMs = now + STEERING_HEAT_TOGGLE_LOCKOUT_MS
    }
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

internal fun sendToggleFrontWindscreenHeat(context: Context) {
    val now = SystemClock.uptimeMillis()
    synchronized(windscreenHeatToggleLock) {
        if (now < windscreenHeatToggleBlockedUntilMs) return
        windscreenHeatToggleBlockedUntilMs = now + STEERING_HEAT_TOGGLE_LOCKOUT_MS
    }
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
                    MbCanKnownVehiclePropertyId.FRONT_WINDSCREEN_HEAT_SWITCH
                )
            }
        )
    } catch (_: Exception) {
    }
}

internal fun sendToggleWiperMaintenance(context: Context) {
    val now = SystemClock.uptimeMillis()
    synchronized(wiperMaintenanceToggleLock) {
        if (now < wiperMaintenanceToggleBlockedUntilMs) return
        wiperMaintenanceToggleBlockedUntilMs = now + STEERING_HEAT_TOGGLE_LOCKOUT_MS
    }
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
                    MbCanKnownVehiclePropertyId.WIPER_MAINTENANCE_SWITCH
                )
            }
        )
    } catch (_: Exception) {
    }
}

internal fun sendToggleParkingRadar(context: Context) {
    val now = SystemClock.uptimeMillis()
    synchronized(parkingRadarToggleLock) {
        if (now < parkingRadarToggleBlockedUntilMs) return
        parkingRadarToggleBlockedUntilMs = now + STEERING_HEAT_TOGGLE_LOCKOUT_MS
    }
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
                    MbCanKnownVehiclePropertyId.PARKING_RADAR_SWITCH
                )
            }
        )
    } catch (_: Exception) {
    }
}

internal fun sendToggleRearWindowMirrorsDefrost(context: Context) {
    val now = SystemClock.uptimeMillis()
    synchronized(hvacDefrosterToggleLock) {
        if (now < hvacDefrosterToggleBlockedUntilMs) return
        hvacDefrosterToggleBlockedUntilMs = now + STEERING_HEAT_TOGGLE_LOCKOUT_MS
    }
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
                    MbCanKnownVehiclePropertyId.HVAC_DEFROSTER_SWITCH
                )
            }
        )
    } catch (_: Exception) {
    }
}

internal fun sendToggleHvacAirRecirculation(context: Context) {
    val now = SystemClock.uptimeMillis()
    synchronized(hvacAirRecirculationToggleLock) {
        if (now < hvacAirRecirculationToggleBlockedUntilMs) return
        hvacAirRecirculationToggleBlockedUntilMs = now + STEERING_HEAT_TOGGLE_LOCKOUT_MS
    }
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
                    MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION
                )
            }
        )
    } catch (_: Exception) {
    }
}

internal fun sendToggleHvacAc(context: Context) {
    val now = SystemClock.uptimeMillis()
    synchronized(hvacAcToggleLock) {
        if (now < hvacAcToggleBlockedUntilMs) return
        hvacAcToggleBlockedUntilMs = now + STEERING_HEAT_TOGGLE_LOCKOUT_MS
    }
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
                    MbCanKnownVehiclePropertyId.HVAC_POWER
                )
            }
        )
    } catch (_: Exception) {
    }
}

internal fun sendToggleHvacAuto(context: Context) {
    val now = SystemClock.uptimeMillis()
    synchronized(hvacAutoToggleLock) {
        if (now < hvacAutoToggleBlockedUntilMs) return
        hvacAutoToggleBlockedUntilMs = now + STEERING_HEAT_TOGGLE_LOCKOUT_MS
    }
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
                    MbCanKnownVehiclePropertyId.HVAC_AUTO_STATE
                )
            }
        )
    } catch (_: Exception) {
    }
}

internal fun sendToggleHvacDefrosterFront(context: Context) {
    val now = SystemClock.uptimeMillis()
    synchronized(hvacDefrosterFrontToggleLock) {
        if (now < hvacDefrosterFrontToggleBlockedUntilMs) return
        hvacDefrosterFrontToggleBlockedUntilMs = now + STEERING_HEAT_TOGGLE_LOCKOUT_MS
    }
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
                    MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION
                )
            }
        )
    } catch (_: Exception) {
    }
}

internal fun sendToggleHvacSync(context: Context) {
    val now = SystemClock.uptimeMillis()
    synchronized(hvacSyncToggleLock) {
        if (now < hvacSyncToggleBlockedUntilMs) return
        hvacSyncToggleBlockedUntilMs = now + STEERING_HEAT_TOGGLE_LOCKOUT_MS
    }
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
                    MbCanKnownVehiclePropertyId.HVAC_SYNC_SWITCH
                )
            }
        )
    } catch (_: Exception) {
    }
}

internal fun sendToggleMirrorAdjustMode(context: Context) {
    try {
        MirrorAdjustModeRepository.toggleMirrorAdjustMode(context)
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
