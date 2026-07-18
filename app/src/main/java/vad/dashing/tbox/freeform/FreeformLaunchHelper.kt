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
    private const val ANCHOR_DELAY_MS = 120L
    /** Extra settle time after tearing down the previous freeform companion / anchor. */
    private const val REPLACE_COMPANION_DELAY_MS = 280L

    private const val FREEFORM_STACK_ID = 2
    private const val WINDOWING_MODE_FREEFORM = 5

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun dbg(message: String) {
        TboxRepository.addLog("DEBUG", LOG_TAG, message)
        Log.d(TAG, message)
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
     * If another companion session is already active, resets the freeform workspace first so
     * new [side]/[percent] bounds are applied (OEM stacks often ignore bounds when reusing
     * the previous freeform window).
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

        val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg) ?: run {
            Toast.makeText(
                context,
                context.getString(R.string.widget_app_launcher_freeform_launch_failed),
                Toast.LENGTH_SHORT,
            ).show()
            return false
        }

        val activityDisplay = FreeformDisplaySpaces.resolveActivityDisplay(context)
        val displayW = activityDisplay.widthPx
        val displayH = activityDisplay.heightPx
        val (appBounds, tboxBounds) = FreeformLaunchBounds.computeAppAndTboxBounds(
            displayW,
            displayH,
            side,
            percent,
        )
        val appBundle = activityOptionsBundle(freeformWindowingModeId(), appBounds) ?: run {
            Toast.makeText(
                context,
                context.getString(R.string.widget_app_launcher_freeform_unsupported),
                Toast.LENGTH_LONG,
            ).show()
            return false
        }

        // Freeform-specific flags only. Do NOT use applyExternalAppLaunchFlags
        // (CLEAR_TOP / SINGLE_TOP / REORDER_TO_FRONT) — those reuse the previous freeform
        // task and ignore setLaunchBounds (e.g. second companion stays on the right).
        launchIntent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                Intent.FLAG_ACTIVITY_NO_ANIMATION,
        )

        val appContext = context.applicationContext
        val replacingExisting = FreeformCompanionSession.isActive
        mainHandler.removeCallbacksAndMessages(null)

        dbg(
            "launch start pkg=$pkg side=${side.storageKey} pct=$percent " +
                "displayId=${activityDisplay.displayId} act=${displayW}x${displayH} " +
                "appBounds=$appBounds tboxBounds=$tboxBounds replace=$replacingExisting " +
                "displays=[${FreeformDisplaySpaces.summarizeDisplays(context)}]",
        )

        fun startCompanionAfterAnchor() {
            ensureFreeformAnchor(appContext, displayW, displayH)
            mainHandler.postDelayed({
                try {
                    appContext.startActivity(launchIntent, appBundle)
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
            }, ANCHOR_DELAY_MS)
        }

        return try {
            if (replacingExisting) {
                // Tear down previous freeform slot + overlay geometry, then relaunch.
                requestHideMainScreenWindow(appContext, immediate = true)
                finishFreeformAnchor(appContext)
                FreeformCompanionSession.clear()
                mainHandler.postDelayed({ startCompanionAfterAnchor() }, REPLACE_COMPANION_DELAY_MS)
            } else {
                startCompanionAfterAnchor()
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Freeform companion launch failed", e)
            Toast.makeText(
                context,
                context.getString(R.string.widget_app_launcher_freeform_launch_failed),
                Toast.LENGTH_SHORT,
            ).show()
            false
        }
    }

    /** Hide overlay, finish anchor, bring MainActivity fullscreen, clear session. */
    fun exitWindowMode(context: Context) {
        mainHandler.removeCallbacksAndMessages(null)
        val appContext = context.applicationContext
        // Defer off the overlay Compose click so we do not tear down the tree mid-gesture.
        mainHandler.post {
            FreeformCompanionSession.clear()
            dbg("exit request → service")
            try {
                appContext.startService(
                    Intent(appContext, BackgroundService::class.java).apply {
                        action = BackgroundService.ACTION_EXIT_WINDOW_MODE
                    },
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to request window-mode exit", e)
                dbg("exit request fail err=${e.message}")
            }
        }
    }

    /** Alias used by UI that previously called [exitToFullscreen]. */
    fun exitToFullscreen(context: Context) = exitWindowMode(context)

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

    private fun ensureFreeformAnchor(context: Context, displayW: Int, displayH: Int) {
        if (FreeformInvisibleAnchorActivity.isRunning) return
        val freeformMode = freeformWindowingModeId()
        val tiny = Rect(displayW, displayH, displayW + 1, displayH + 1)
        val bundle = activityOptionsBundle(freeformMode, tiny) ?: return
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

    private fun finishFreeformAnchor(context: Context) {
        context.sendBroadcast(
            Intent(FreeformInvisibleAnchorActivity.ACTION_FINISH).setPackage(context.packageName),
        )
    }

    private fun freeformWindowingModeId(): Int =
        if (Build.VERSION.SDK_INT >= 28) WINDOWING_MODE_FREEFORM else FREEFORM_STACK_ID

    private fun windowingModeMethodName(): String =
        if (Build.VERSION.SDK_INT >= 28) "setLaunchWindowingMode" else "setLaunchStackId"

    private fun activityOptionsBundle(windowingMode: Int, bounds: Rect): Bundle? {
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
            options.toBundle()
        } catch (e: Exception) {
            Log.w(TAG, "Hidden ActivityOptions windowing API unavailable", e)
            null
        }
    }
}
