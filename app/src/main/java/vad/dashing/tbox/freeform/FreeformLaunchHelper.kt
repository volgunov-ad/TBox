package vad.dashing.tbox.freeform

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import vad.dashing.tbox.BackgroundService
import vad.dashing.tbox.MainActivityIntentHelper
import vad.dashing.tbox.R
import vad.dashing.tbox.TboxRepository

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
     * After a full exit finishes (overlay gone, MainActivity restored), wait before launching
     * another companion so the HU freeform stack can settle.
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

    private data class PendingCompanionLaunch(
        val packageName: String,
        val side: FreeformLaunchSide,
        val percent: Int,
    )

    @Volatile
    private var pendingAfterExit: PendingCompanionLaunch? = null
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

    fun hasFreeformSupport(context: Context): Boolean {
        val pm = context.packageManager
        if (pm.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)) {
            return true
        }
        return try {
            Settings.Global.getInt(context.contentResolver, "enable_freeform_support", 0) != 0 ||
                (Build.VERSION.SDK_INT <= 25 &&
                    Settings.Global.getInt(context.contentResolver, "force_resizable_activities", 0) != 0)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Launch [packageName] in freeform on [side], then ask [BackgroundService] to show the
     * main-screen window overlay. Returns false if freeform launch was not started.
     *
     * If another companion session is already active, performs a **full** window-mode exit
     * (same as the overlay close button), then launches the new companion after settle.
     */
    fun launchCompanion(
        context: Context,
        packageName: String,
        side: FreeformLaunchSide,
        percent: Int,
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

        // Switching companion: exit completely (like the X button), then relaunch.
        if (FreeformCompanionSession.isActive || exitInProgress) {
            pendingAppContext = appContext
            pendingAfterExit = PendingCompanionLaunch(pkg, side, percent)
            dbg(
                "queue launch after full exit pkg=$pkg side=${side.storageKey} pct=$percent " +
                    "exitInProgress=$exitInProgress session=${FreeformCompanionSession.isActive}",
            )
            if (!exitInProgress) {
                beginExitWindowMode(appContext, EXIT_DEFER_FROM_CLICK_MS)
            }
            return true
        }

        pendingAfterExit = null
        pendingAppContext = null
        return startCompanionLaunch(appContext, pkg, side, percent)
    }

    private fun startCompanionLaunch(
        appContext: Context,
        pkg: String,
        side: FreeformLaunchSide,
        percent: Int,
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
        val launchContext = FreeformDisplaySpaces.contextForDisplay(
            appContext,
            activityDisplay.displayId,
        )
        val (appBounds, tboxBounds) = FreeformLaunchBounds.computeAppAndTboxBounds(
            displayW,
            displayH,
            side,
            percent,
        )
        val appBundle = activityOptionsBundle(
            freeformWindowingModeId(),
            appBounds,
            launchDisplayId = activityDisplay.displayId,
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
            "launch start pkg=$pkg side=${side.storageKey} pct=$percent " +
                "displayId=${activityDisplay.displayId} act=${displayW}x${displayH} " +
                "appBounds=$appBounds tboxBounds=$tboxBounds " +
                "displays=[${FreeformDisplaySpaces.summarizeDisplays(appContext)}]",
        )

        return try {
            ensureFreeformAnchor(
                launchContext,
                displayW,
                displayH,
                displayId = activityDisplay.displayId,
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
                    )
                    dbg(
                        "launch ok pkg=$pkg displayId=${activityDisplay.displayId} " +
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

    /** Hide overlay, finish anchor, bring MainActivity fullscreen, clear session. */
    fun exitWindowMode(context: Context) {
        // Explicit exit (X / same-tile toggle) — do not relaunch a queued companion.
        pendingAfterExit = null
        pendingAppContext = null
        if (exitInProgress) {
            dbg("exit ignored (already in progress)")
            return
        }
        beginExitWindowMode(context.applicationContext, EXIT_DEFER_FROM_CLICK_MS)
    }

    private fun beginExitWindowMode(appContext: Context, deferMs: Long) {
        // Replace any prior deferred exit/relaunch; keep flag true for the new exit.
        cancelPostedWork(clearExitInProgress = false)
        exitInProgress = true
        val exitRunnable = Runnable {
            pendingExitRunnable = null
            FreeformCompanionSession.clear()
            dbg("exit request → service")
            try {
                appContext.startService(
                    Intent(appContext, BackgroundService::class.java).apply {
                        action = BackgroundService.ACTION_EXIT_WINDOW_MODE
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

    /** Alias used by UI that previously called [exitToFullscreen]. */
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
            if (pending == null || appContext == null) return@post
            dbg(
                "exit done → relaunch ${pending.packageName} side=${pending.side.storageKey} " +
                    "pct=${pending.percent} after ${AFTER_FULL_EXIT_RELAUNCH_DELAY_MS}ms",
            )
            val relaunchRunnable = Runnable {
                pendingRelaunchRunnable = null
                startCompanionLaunch(
                    appContext = appContext,
                    pkg = pending.packageName,
                    side = pending.side,
                    percent = pending.percent,
                )
            }
            pendingRelaunchRunnable = relaunchRunnable
            mainHandler.postDelayed(relaunchRunnable, AFTER_FULL_EXIT_RELAUNCH_DELAY_MS)
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

    private fun ensureFreeformAnchor(context: Context, displayW: Int, displayH: Int, displayId: Int) {
        if (FreeformInvisibleAnchorActivity.isRunning) return
        val freeformMode = freeformWindowingModeId()
        val tiny = Rect(displayW, displayH, displayW + 1, displayH + 1)
        val bundle = activityOptionsBundle(freeformMode, tiny, launchDisplayId = displayId) ?: return
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

    private fun activityOptionsBundle(
        windowingMode: Int,
        bounds: Rect,
        launchDisplayId: Int,
    ): Bundle? {
        val options = try {
            ActivityOptions.makeBasic()
        } catch (e: Exception) {
            Log.w(TAG, "ActivityOptions.makeBasic failed", e)
            return null
        }
        return try {
            val method = ActivityOptions::class.java.getMethod(
                windowingModeMethodName(),
                Int::class.javaPrimitiveType,
            )
            method.invoke(options, windowingMode)
            if (Build.VERSION.SDK_INT >= 24) {
                options.setLaunchBounds(bounds)
            }
            if (Build.VERSION.SDK_INT >= 26) {
                applyLaunchDisplayId(options, launchDisplayId)
            }
            options.toBundle()
        } catch (e: Exception) {
            Log.w(TAG, "Hidden ActivityOptions windowing API unavailable", e)
            null
        }
    }

    private fun applyLaunchDisplayId(options: ActivityOptions, displayId: Int) {
        try {
            options.setLaunchDisplayId(displayId)
            return
        } catch (e: Exception) {
            Log.w(TAG, "setLaunchDisplayId($displayId) direct failed, trying reflection", e)
        }
        try {
            ActivityOptions::class.java
                .getMethod("setLaunchDisplayId", Int::class.javaPrimitiveType)
                .invoke(options, displayId)
        } catch (e: Exception) {
            Log.w(TAG, "setLaunchDisplayId($displayId) reflection failed", e)
        }
    }
}
