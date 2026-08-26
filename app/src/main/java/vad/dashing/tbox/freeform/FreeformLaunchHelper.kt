package vad.dashing.tbox.freeform

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.widget.Toast
import vad.dashing.tbox.AdayoStockAppWindow
import vad.dashing.tbox.BackgroundService
import vad.dashing.tbox.HeadUnitCanMode
import vad.dashing.tbox.MainActivityIntentHelper
import vad.dashing.tbox.R
import vad.dashing.tbox.TboxRepository
import vad.dashing.tbox.mbcan.UniversalCanRepository

/**
 * Freeform launch for companion apps only (Taskbar-style).
 * MainScreen for the remaining area is shown as a dedicated overlay via [BackgroundService].
 */
object FreeformLaunchHelper {
    private const val TAG = "FreeformLaunch"
    private const val LOG_TAG = "WindowMode"
    private const val ANCHOR_DELAY_MS = 200L
    /** Let the overlay click/gesture finish before tearing down Compose. */
    private const val EXIT_DEFER_FROM_CLICK_MS = 150L
    /**
     * After a full exit finishes (overlay gone), wait before launching another companion so the
     * HU freeform stack can settle.
     */
    private const val AFTER_FULL_EXIT_RELAUNCH_DELAY_MS = 700L

    private const val FREEFORM_STACK_ID = 2
    private const val WINDOWING_MODE_FREEFORM = 5

    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile
    private var exitInProgress = false
    private var pendingExitRunnable: Runnable? = null
    private var pendingAnchorLaunchRunnable: Runnable? = null
    private var pendingRelaunchRunnable: Runnable? = null

    private sealed interface PendingAfterExit {
        data class CompanionLaunch(
            val packageName: String,
            val side: FreeformLaunchSide,
            val percent: Int,
            val overlayCrop: Boolean,
            val pinnedOverlayPage: Int?,
        ) : PendingAfterExit

        /** Non-freeform work after teardown (e.g. fullscreen / stock launcher). */
        data class Action(val run: () -> Unit) : PendingAfterExit
    }

    @Volatile
    private var pendingAfterExit: PendingAfterExit? = null
    @Volatile
    private var pendingAppContext: Context? = null

    private fun dbg(message: String) {
        TboxRepository.addLog("DEBUG", LOG_TAG, message)
        Log.d(TAG, message)
    }

    /**
     * Removes posted exit / launch / relaunch work. When [clearExitInProgress] is true and an
     * exit was still pending, resets [exitInProgress] so it cannot stick after cancel.
     */
    private fun cancelPostedWork(clearExitInProgress: Boolean) {
        pendingExitRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingExitRunnable = null
        pendingAnchorLaunchRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingAnchorLaunchRunnable = null
        pendingRelaunchRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingRelaunchRunnable = null
        if (clearExitInProgress && exitInProgress) {
            exitInProgress = false
            dbg("exitInProgress cleared (posted work cancelled)")
        }
    }

    /**
     * Whether we should attempt freeform companion launch.
     *
     * Adayo Android 10 HUs (Jetour) often honor [ActivityOptions] freeform windowing/bounds
     * without advertising [PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT] or setting
     * `enable_freeform_support`. Gating only on those flags produced a false
     * “window mode unavailable” toast on A10.
     */
    fun hasFreeformSupport(context: Context): Boolean {
        val advertised = hasAdvertisedFreeformSupport(context)
        val adayoOrA10 =
            AdayoStockAppWindow.isAvailable(context) ||
                UniversalCanRepository.mode.value == HeadUnitCanMode.Android10Vhal
        return evaluateFreeformSupport(
            advertised = advertised,
            adayoOrAndroid10Hu = adayoOrA10,
            canBuildActivityOptions = canBuildFreeformActivityOptions(),
        )
    }

    /** Pure decision for unit tests. */
    internal fun evaluateFreeformSupport(
        advertised: Boolean,
        adayoOrAndroid10Hu: Boolean,
        canBuildActivityOptions: Boolean,
    ): Boolean {
        if (advertised) return true
        // Jetour Adayo A10 (and selected Android 10 HU mode): try when APIs exist.
        return adayoOrAndroid10Hu && canBuildActivityOptions
    }

    private fun hasAdvertisedFreeformSupport(context: Context): Boolean {
        val pm = context.packageManager
        if (pm.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)) {
            return true
        }
        try {
            val cr = context.contentResolver
            if (Settings.Global.getInt(cr, "enable_freeform_support", 0) != 0) {
                return true
            }
            if (Settings.Global.getInt(cr, "force_resizable_activities", 0) != 0) {
                return true
            }
        } catch (_: Exception) {
            // Fall through to framework config / HU heuristics.
        }
        return try {
            val res = Resources.getSystem()
            val id = res.getIdentifier(
                "config_supportsFreeformWindowManagement",
                "bool",
                "android",
            )
            id != 0 && res.getBoolean(id)
        } catch (_: Exception) {
            false
        }
    }

    /** True when hidden [ActivityOptions] freeform setters are present (API 28+). */
    private fun canBuildFreeformActivityOptions(): Boolean {
        val tiny = Rect(0, 0, 1, 1)
        return activityOptionsBundle(freeformWindowingModeId(), tiny, launchDisplayId = null) != null
    }

    /**
     * Launch [packageName] in freeform on [side], then ask [BackgroundService] to show the
     * main-screen window overlay. Returns false if freeform launch was not started.
     *
     * [overlayCrop]: MainScreen at full display size clipped to the overlay (vs shrink-to-fit).
     *
     * Same package already active with the same [overlayCrop]: re-assert freeform launch and
     * show/update the overlay (idempotent — no duplicate MainScreen overlay).
     *
     * If another companion session is already active (or crop mode differs), performs a
     * **full** window-mode exit, then launches the new companion after settle.
     */
    fun launchCompanion(
        context: Context,
        packageName: String,
        side: FreeformLaunchSide,
        percent: Int,
        overlayCrop: Boolean = false,
        pinnedOverlayPage: Int? = null,
    ): Boolean {
        val pkg = packageName.trim()
        if (pkg.isEmpty()) return false

        if (!hasFreeformSupport(context)) {
            Toast.makeText(
                context,
                context.getString(R.string.widget_app_launcher_freeform_unsupported),
                Toast.LENGTH_LONG,
            ).show()
            return false
        }

        if (context.packageManager.getLaunchIntentForPackage(pkg) == null) {
            Toast.makeText(
                context,
                context.getString(R.string.widget_app_launcher_freeform_launch_failed),
                Toast.LENGTH_SHORT,
            ).show()
            return false
        }

        val appContext = context.applicationContext
        val session = FreeformCompanionSession.state.value

        // Same companion + same crop mode: re-launch in freeform and ensure overlay is shown.
        if (
            FreeformCompanionSession.isActiveFor(pkg) &&
            !exitInProgress &&
            session?.overlayCrop == overlayCrop
        ) {
            pendingAfterExit = null
            pendingAppContext = null
            dbg(
                "re-assert same companion pkg=$pkg side=${side.storageKey} pct=$percent " +
                    "crop=$overlayCrop",
            )
            return startCompanionLaunch(
                appContext,
                pkg,
                side,
                percent,
                overlayCrop,
                pinnedOverlayPage,
            )
        }

        // Switching companion / crop mode (or exit in progress): exit completely, then relaunch.
        if (FreeformCompanionSession.isActive || exitInProgress) {
            pendingAppContext = appContext
            pendingAfterExit = PendingAfterExit.CompanionLaunch(
                pkg,
                side,
                percent,
                overlayCrop,
                pinnedOverlayPage,
            )
            dbg(
                "queue launch after full exit pkg=$pkg side=${side.storageKey} pct=$percent " +
                    "crop=$overlayCrop exitInProgress=$exitInProgress " +
                    "session=${FreeformCompanionSession.isActive}",
            )
            if (!exitInProgress) {
                beginExitWindowMode(
                    appContext,
                    EXIT_DEFER_FROM_CLICK_MS,
                    restoreMainActivity = false,
                )
            }
            return true
        }

        pendingAfterExit = null
        pendingAppContext = null
        return startCompanionLaunch(
            appContext,
            pkg,
            side,
            percent,
            overlayCrop,
            pinnedOverlayPage,
        )
    }

    /**
     * If window mode is active (companion session, freeform anchor, or exit in progress),
     * fully exit without restoring MainActivity, then run [action].
     * Otherwise runs [action] immediately.
     *
     * Used when an app-launcher tile starts fullscreen / stock window so the main-screen
     * overlay does not stay on top of the newly launched app.
     */
    fun runAfterExitingWindowMode(context: Context, action: () -> Unit) {
        val appContext = context.applicationContext
        val needsExit = FreeformCompanionSession.isActive ||
            exitInProgress ||
            FreeformInvisibleAnchorActivity.isRunning
        if (!needsExit) {
            action()
            return
        }
        pendingAppContext = appContext
        pendingAfterExit = PendingAfterExit.Action(action)
        dbg(
            "queue action after full exit exitInProgress=$exitInProgress " +
                "session=${FreeformCompanionSession.isActive} " +
                "anchor=${FreeformInvisibleAnchorActivity.isRunning}",
        )
        if (!exitInProgress) {
            beginExitWindowMode(
                appContext,
                EXIT_DEFER_FROM_CLICK_MS,
                restoreMainActivity = false,
            )
        }
    }

    private fun startCompanionLaunch(
        appContext: Context,
        pkg: String,
        side: FreeformLaunchSide,
        percent: Int,
        overlayCrop: Boolean,
        pinnedOverlayPage: Int?,
    ): Boolean {
        val launchIntent = appContext.packageManager.getLaunchIntentForPackage(pkg) ?: run {
            Toast.makeText(
                appContext,
                appContext.getString(R.string.widget_app_launcher_freeform_launch_failed),
                Toast.LENGTH_SHORT,
            ).show()
            return false
        }

        val activityDisplay = FreeformDisplaySpaces.resolveActivityDisplay(appContext)
        val displayW = activityDisplay.widthPx
        val displayH = activityDisplay.heightPx
        // On default display, omit setLaunchDisplayId (pre-38b343c path): some images
        // drop freeform windowing/bounds when launchDisplayId is set even to 0.
        // On inset app VDs (HU), keep display-bound context + setLaunchDisplayId.
        val bindLaunchToDisplay = activityDisplay.displayId != Display.DEFAULT_DISPLAY
        val launchContext = if (bindLaunchToDisplay) {
            FreeformDisplaySpaces.contextForDisplay(appContext, activityDisplay.displayId)
        } else {
            appContext
        }
        val (appBounds, tboxBounds) = FreeformLaunchBounds.computeAppAndTboxBounds(
            displayW,
            displayH,
            side,
            percent,
        )
        val appBundle = activityOptionsBundle(
            freeformWindowingModeId(),
            appBounds,
            launchDisplayId = if (bindLaunchToDisplay) activityDisplay.displayId else null,
        ) ?: run {
            Toast.makeText(
                appContext,
                appContext.getString(R.string.widget_app_launcher_freeform_unsupported),
                Toast.LENGTH_LONG,
            ).show()
            return false
        }

        launchIntent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                Intent.FLAG_ACTIVITY_NO_ANIMATION,
        )

        // Drop any prior deferred exit/relaunch so this launch is not racing cancelled work.
        cancelPostedWork(clearExitInProgress = true)

        dbg(
            "launch start pkg=$pkg side=${side.storageKey} pct=$percent crop=$overlayCrop " +
                "displayId=${activityDisplay.displayId} bindDisplay=$bindLaunchToDisplay " +
                "act=${displayW}x${displayH} " +
                "appBounds=$appBounds tboxBounds=$tboxBounds " +
                "displays=[${FreeformDisplaySpaces.summarizeDisplays(appContext)}]",
        )

        return try {
            ensureFreeformAnchor(
                launchContext,
                displayW,
                displayH,
                launchDisplayId = if (bindLaunchToDisplay) activityDisplay.displayId else null,
            )
            val launchRunnable = Runnable {
                pendingAnchorLaunchRunnable = null
                try {
                    launchContext.startActivity(launchIntent, appBundle)
                    FreeformCompanionSession.set(
                        packageName = pkg,
                        side = side,
                        percent = percent,
                        activityDisplayWidth = displayW,
                        activityDisplayHeight = displayH,
                        activityDisplayId = activityDisplay.displayId,
                        overlayCrop = overlayCrop,
                        pinnedOverlayPage = pinnedOverlayPage,
                    )
                    dbg(
                        "launch ok pkg=$pkg displayId=${activityDisplay.displayId} " +
                            "bindDisplay=$bindLaunchToDisplay crop=$overlayCrop " +
                            "act=${displayW}x${displayH} side=${side.storageKey} pct=$percent",
                    )
                    requestShowMainScreenWindow(appContext)
                    MainActivityIntentHelper.requestFinishForWindowMode(appContext)
                } catch (e: Exception) {
                    Log.w(TAG, "Freeform companion launch failed", e)
                    dbg("launch fail pkg=$pkg err=${e.message}")
                    Toast.makeText(
                        appContext,
                        appContext.getString(R.string.widget_app_launcher_freeform_launch_failed),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
            pendingAnchorLaunchRunnable = launchRunnable
            mainHandler.postDelayed(launchRunnable, ANCHOR_DELAY_MS)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Freeform companion launch failed", e)
            Toast.makeText(
                appContext,
                appContext.getString(R.string.widget_app_launcher_freeform_launch_failed),
                Toast.LENGTH_SHORT,
            ).show()
            false
        }
    }

    /** Hide overlay, finish freeform anchor, clear session. Does not start MainActivity (X button). */
    fun exitWindowMode(context: Context) {
        requestExitWindowMode(context, restoreMainActivity = false)
    }

    /** Exit window mode and bring MainActivity fullscreen (square button). */
    fun exitWindowModeToFullscreen(context: Context) {
        requestExitWindowMode(context, restoreMainActivity = true)
    }

    private fun requestExitWindowMode(context: Context, restoreMainActivity: Boolean) {
        // Explicit exit — do not relaunch a queued companion.
        pendingAfterExit = null
        pendingAppContext = null
        if (exitInProgress) {
            dbg("exit ignored (already in progress) restoreMain=$restoreMainActivity")
            return
        }
        beginExitWindowMode(
            context.applicationContext,
            EXIT_DEFER_FROM_CLICK_MS,
            restoreMainActivity = restoreMainActivity,
        )
    }

    private fun beginExitWindowMode(
        appContext: Context,
        deferMs: Long,
        restoreMainActivity: Boolean,
    ) {
        // Replace any prior deferred exit/relaunch; keep flag true for the new exit.
        cancelPostedWork(clearExitInProgress = false)
        exitInProgress = true
        val exitRunnable = Runnable {
            pendingExitRunnable = null
            FreeformCompanionSession.clear()
            dbg("exit request → service restoreMain=$restoreMainActivity")
            try {
                appContext.startService(
                    Intent(appContext, BackgroundService::class.java).apply {
                        action = BackgroundService.ACTION_EXIT_WINDOW_MODE
                        putExtra(
                            BackgroundService.EXTRA_EXIT_WINDOW_MODE_RESTORE_MAIN,
                            restoreMainActivity,
                        )
                    },
                )
            } catch (e: Exception) {
                exitInProgress = false
                pendingAfterExit = null
                pendingAppContext = null
                Log.w(TAG, "Failed to request window-mode exit", e)
                dbg("exit request fail err=${e.message}")
            }
        }
        pendingExitRunnable = exitRunnable
        mainHandler.postDelayed(exitRunnable, deferMs)
    }

    /** Alias: exit without restoring MainActivity (same as [exitWindowMode]). */
    fun exitToFullscreen(context: Context) = exitWindowMode(context)

    /** Called by [BackgroundService] when the exit sequence finishes (success or fail). */
    fun markExitFinished() {
        // Service coroutine may call this off the main thread — serialize on the handler.
        mainHandler.post {
            exitInProgress = false
            pendingExitRunnable = null
            val pending = pendingAfterExit
            val appContext = pendingAppContext
            pendingAfterExit = null
            pendingAppContext = null
            when (pending) {
                is PendingAfterExit.CompanionLaunch -> {
                    if (appContext == null) return@post
                    dbg(
                        "exit done → relaunch ${pending.packageName} side=${pending.side.storageKey} " +
                            "pct=${pending.percent} crop=${pending.overlayCrop} " +
                            "after ${AFTER_FULL_EXIT_RELAUNCH_DELAY_MS}ms",
                    )
                    val relaunchRunnable = Runnable {
                        pendingRelaunchRunnable = null
                        startCompanionLaunch(
                            appContext = appContext,
                            pkg = pending.packageName,
                            side = pending.side,
                            percent = pending.percent,
                            overlayCrop = pending.overlayCrop,
                            pinnedOverlayPage = pending.pinnedOverlayPage,
                        )
                    }
                    pendingRelaunchRunnable = relaunchRunnable
                    mainHandler.postDelayed(relaunchRunnable, AFTER_FULL_EXIT_RELAUNCH_DELAY_MS)
                }
                is PendingAfterExit.Action -> {
                    dbg("exit done → run pending non-freeform action")
                    // Overlay/anchor already torn down in the service; no freeform settle delay.
                    pending.run()
                }
                null -> Unit
            }
        }
    }

    fun requestShowMainScreenWindow(context: Context) {
        try {
            context.startService(
                Intent(context, BackgroundService::class.java).apply {
                    action = BackgroundService.ACTION_SHOW_MAIN_SCREEN_WINDOW
                },
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to request main-screen window overlay", e)
        }
    }

    fun requestHideMainScreenWindow(context: Context, immediate: Boolean = false) {
        try {
            context.startService(
                Intent(context, BackgroundService::class.java).apply {
                    action = BackgroundService.ACTION_HIDE_MAIN_SCREEN_WINDOW
                    putExtra(BackgroundService.EXTRA_MAIN_SCREEN_WINDOW_IMMEDIATE, immediate)
                },
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to hide main-screen window overlay", e)
        }
    }

    private fun ensureFreeformAnchor(
        context: Context,
        displayW: Int,
        displayH: Int,
        launchDisplayId: Int?,
    ) {
        if (FreeformInvisibleAnchorActivity.isRunning) return
        val freeformMode = freeformWindowingModeId()
        val tiny = Rect(displayW, displayH, displayW + 1, displayH + 1)
        val bundle = activityOptionsBundle(freeformMode, tiny, launchDisplayId) ?: return
        val intent = Intent(context, FreeformInvisibleAnchorActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION,
            )
        }
        try {
            context.startActivity(intent, bundle)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start freeform anchor", e)
        }
    }

    private fun freeformWindowingModeId(): Int =
        if (Build.VERSION.SDK_INT >= 28) WINDOWING_MODE_FREEFORM else FREEFORM_STACK_ID

    private fun windowingModeMethodName(): String =
        if (Build.VERSION.SDK_INT >= 28) "setLaunchWindowingMode" else "setLaunchStackId"

    /**
     * @param launchDisplayId when non-null, sets [ActivityOptions.setLaunchDisplayId] (inset VD).
     *   Null keeps the pre-multi-VD path so freeform bounds are not dropped on default display.
     */
    private fun activityOptionsBundle(
        windowingMode: Int,
        bounds: Rect,
        launchDisplayId: Int?,
    ): Bundle? {
        val options = try {
            ActivityOptions.makeBasic()
        } catch (e: Exception) {
            Log.w(TAG, "ActivityOptions.makeBasic failed", e)
            return null
        }
        try {
            val method = ActivityOptions::class.java.getMethod(
                windowingModeMethodName(),
                Int::class.javaPrimitiveType,
            )
            method.invoke(options, windowingMode)
        } catch (e: Exception) {
            Log.w(TAG, "setLaunchWindowingMode / setLaunchStackId failed", e)
            return null
        }
        try {
            val setBounds = ActivityOptions::class.java.getMethod("setLaunchBounds", Rect::class.java)
            setBounds.invoke(options, bounds)
        } catch (e: Exception) {
            Log.w(TAG, "setLaunchBounds failed", e)
            return null
        }
        if (launchDisplayId != null && Build.VERSION.SDK_INT >= 26) {
            try {
                options.launchDisplayId = launchDisplayId
            } catch (e: Exception) {
                Log.w(TAG, "setLaunchDisplayId failed", e)
            }
        }
        return try {
            options.toBundle()
        } catch (e: Exception) {
            Log.w(TAG, "ActivityOptions.toBundle failed", e)
            null
        }
    }
}
