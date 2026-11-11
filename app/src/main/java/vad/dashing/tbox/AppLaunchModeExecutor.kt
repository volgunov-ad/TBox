package vad.dashing.tbox

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import vad.dashing.tbox.dcl.DclAppLaunchHelper
import vad.dashing.tbox.dcl.DclLauncherEnvironmentRouting
import vad.dashing.tbox.dcl.DclLauncherEnvironmentSetting
import vad.dashing.tbox.freeform.FreeformLaunchHelper

private val coldVirtualDisplayLaunchScope =
    CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * Launches [packageName] using the same mode routing as the App Shortcut widget
 * ([FloatingDashboardWidgetConfig.launcherLaunchMode] and related fields).
 *
 * When [settingsViewModel] is null (e.g. cold play from [SharedMediaControlService]),
 * freeform still launches with the stored overlay page as a pin hint, and virtual-display
 * launch uses [vad.dashing.tbox.adb.VirtualDisplayAdb] without persisting display remaps /
 * user toasts.
 */
fun launchPackageWithLaunchMode(
    context: Context,
    packageName: String,
    config: FloatingDashboardWidgetConfig,
    settingsViewModel: SettingsViewModel? = null,
) {
    val pkg = packageName.trim()
    if (pkg.isBlank()) return

    val launchMode = AppLauncherLaunchMode.fromStored(
        config.launcherLaunchMode.storageKey,
        config.launcherFreeformEnabled,
    )

    when (launchMode) {
        AppLauncherLaunchMode.STOCK_WINDOW -> {
            FreeformLaunchHelper.runAfterExitingWindowMode(context) {
                val ok = AdayoStockAppWindow.launchInAppWindow(context, pkg)
                if (!ok) {
                    launchPackageFullscreen(context, pkg)
                }
            }
        }
        AppLauncherLaunchMode.FREEFORM -> {
            val launch = { pinnedOverlayPage: Int? ->
                val launched = FreeformLaunchHelper.launchCompanion(
                    context = context,
                    packageName = pkg,
                    side = config.launcherFreeformSide,
                    percent = config.launcherFreeformPercent,
                    overlayCrop = config.launcherFreeformOverlayCrop,
                    pinnedOverlayPage = pinnedOverlayPage,
                )
                if (!launched) {
                    FreeformLaunchHelper.runAfterExitingWindowMode(context) {
                        launchPackageFullscreen(context, pkg)
                    }
                }
            }
            val overlayPage = config.launcherFreeformOverlayPage
            if (overlayPage == null) {
                launch(null)
            } else if (settingsViewModel != null) {
                settingsViewModel.applyLauncherFreeformOverlayPage(overlayPage, launch)
            } else {
                launch(overlayPage)
            }
        }
        AppLauncherLaunchMode.FULLSCREEN,
        AppLauncherLaunchMode.DCL_FULLSCREEN,
        -> {
            val dclMode = DclLauncherEnvironmentRouting.dclLaunchModeFor(
                envEnabled = DclLauncherEnvironmentSetting.enabled,
                launchMode = launchMode,
            )
            FreeformLaunchHelper.runAfterExitingWindowMode(context) {
                if (dclMode != null) {
                    DclAppLaunchHelper.launch(
                        context = context,
                        packageName = pkg,
                        mode = dclMode,
                        onRawFallback = { launchPackageFullscreen(context, pkg) },
                    )
                } else {
                    launchPackageFullscreen(context, pkg)
                }
            }
        }
        AppLauncherLaunchMode.VIRTUAL_DISPLAY -> {
            val displayId = config.launcherVirtualDisplayId
            if (displayId == null || displayId <= 0) return
            FreeformLaunchHelper.runAfterExitingWindowMode(context) {
                if (settingsViewModel != null) {
                    settingsViewModel.launchAppOnVirtualDisplay(
                        context = context,
                        packageName = pkg,
                        displayId = displayId,
                        displayWidthPx = config.launcherVirtualDisplayWidthPx,
                        displayHeightPx = config.launcherVirtualDisplayHeightPx,
                    ) { outcome ->
                        when (outcome) {
                            is vad.dashing.tbox.adb.VirtualDisplayAdb.LaunchOutcome.Failed -> {
                                val detail = vad.dashing.tbox.adb.LocalhostAdbReasons.forVirtualDisplay(
                                    context,
                                    outcome.reason,
                                    outcome.detail,
                                )
                                if (!vad.dashing.tbox.adb.AdbIoErrors.shouldSuppressUserFacingFailure(
                                        detail,
                                        context,
                                    )
                                ) {
                                    android.widget.Toast.makeText(
                                        context,
                                        context.getString(
                                            R.string.widget_app_launcher_virtual_display_launch_fail,
                                            detail,
                                        ),
                                        android.widget.Toast.LENGTH_LONG,
                                    ).show()
                                }
                            }
                            is vad.dashing.tbox.adb.VirtualDisplayAdb.LaunchOutcome.Success -> {
                                if (outcome.remapped) {
                                    android.widget.Toast.makeText(
                                        context,
                                        context.getString(
                                            R.string.widget_app_launcher_virtual_display_remapped,
                                            displayId,
                                            outcome.displayId,
                                            outcome.widthPx,
                                            outcome.heightPx,
                                        ),
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                        }
                    }
                } else {
                    // Best-effort cold path without SettingsViewModel (no display-list persist).
                    coldVirtualDisplayLaunchScope.launch {
                        vad.dashing.tbox.adb.VirtualDisplayAdb.launchOnDisplay(
                            context = context,
                            packageName = pkg,
                            displayId = displayId,
                            displayWidthPx = config.launcherVirtualDisplayWidthPx,
                            displayHeightPx = config.launcherVirtualDisplayHeightPx,
                            cachedDisplays = emptyList(),
                        )
                    }
                }
            }
        }
    }
}

/** Plain `startActivity` with external-app launch flags (raw fullscreen / DCL fallback). */
fun launchPackageFullscreen(context: Context, packageName: String) {
    try {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
        MainActivityIntentHelper.applyExternalAppLaunchFlags(launchIntent, context)
        context.startActivity(launchIntent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

/** Widgets that persist and expose [FloatingDashboardWidgetConfig.launcherLaunchMode]. */
fun usesAppLauncherLaunchModeFields(dataKey: String): Boolean =
    dataKey == APP_LAUNCHER_WIDGET_DATA_KEY || isMusicWidgetDataKey(dataKey)
