package vad.dashing.tbox

import android.app.Activity
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import vad.dashing.tbox.mbcan.UniversalCanRepository

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

/** Delivers an external «open .tboxtheme file» request from [MainActivity] into Compose. */
object ThemeOpenRequestBus {
    private val _pending = MutableStateFlow<ThemeOpenRequest?>(null)
    val pending: StateFlow<ThemeOpenRequest?> = _pending.asStateFlow()

    fun post(request: ThemeOpenRequest) {
        _pending.value = request
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
 *
 * On Android 10 Adayo, when [LaunchMainInStockAppWindowSetting] is enabled, programmatic opens
 * go through [AdayoStockAppWindow] (stock launcher ActivityView) instead of fullscreen
 * [MainActivity].
 */
object MainActivityIntentHelper {

    const val EXTRA_FLOATING_DASHBOARD_PANEL_ID = "extra_floating_dashboard_panel_id"
    const val EXTRA_FLOATING_DASHBOARD_WIDGET_INDEX = "extra_floating_dashboard_widget_index"

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
     * Intent for home-screen widgets / [android.app.PendingIntent]: trampoline that applies
     * stock-window vs fullscreen at click time.
     */
    fun createBringToFrontRouterIntent(context: Context): Intent =
        Intent(context, MainActivityLaunchRouterActivity::class.java).also {
            applyBringToFrontFlags(it, context)
        }

    /**
     * Finish a running [MainActivity] when entering freeform window mode (overlay hosts MainScreen).
     * No-op if MainActivity is not alive.
     */
    const val ACTION_FINISH_FOR_WINDOW_MODE = "vad.dashing.tbox.FINISH_MAIN_FOR_WINDOW_MODE"

    fun requestFinishForWindowMode(context: Context) {
        try {
            context.sendBroadcast(
                Intent(ACTION_FINISH_FOR_WINDOW_MODE).setPackage(context.packageName),
            )
        } catch (_: Exception) {
        }
    }

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

    /** Programmatic bring-to-front; honors A10 stock app-window setting. */
    fun bringToFront(context: Context): Boolean =
        launchMain(context, createBringToFrontIntent(context))

    /**
     * Opens main screen to edit a floating-panel tile. Posts [FloatingDashboardTileEditRequestBus]
     * before launch so Adayo stock-window path (no custom extras) still opens the editor.
     */
    fun openForFloatingDashboardTileEdit(
        context: Context,
        panelId: String,
        widgetIndex: Int,
    ): Boolean {
        if (panelId.isBlank() || widgetIndex < 0) return false
        FloatingDashboardTileEditRequestBus.post(panelId, widgetIndex)
        ioScope.launch {
            runCatching {
                SettingsManager(context.applicationContext)
                    .saveSelectedTab(SettingsManager.MAIN_SCREEN_TAB_KEY)
            }
        }
        return launchMain(
            context,
            createFloatingDashboardTileEditIntent(context, panelId, widgetIndex),
        )
    }

    fun shouldLaunchInStockAppWindow(context: Context): Boolean =
        LaunchMainInStockAppWindowDecision.shouldAttempt(
            settingEnabled = LaunchMainInStockAppWindowSetting.enabled,
            headUnitIsAndroid10 =
                UniversalCanRepository.mode.value == HeadUnitCanMode.Android10Vhal,
            adayoLauncherAvailable = AdayoStockAppWindow.isAvailable(context),
        )

    private fun launchMain(context: Context, fallbackIntent: Intent): Boolean {
        if (shouldLaunchInStockAppWindow(context)) {
            val ok = AdayoStockAppWindow.launchInAppWindow(
                context = context,
                packageName = context.packageName,
                activityClass = MainActivity::class.java.name,
            )
            if (ok) return true
        }
        return try {
            context.startActivity(fallbackIntent)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Third-party app [Intent] from [android.content.pm.PackageManager.getLaunchIntentForPackage]:
     * bring an existing task forward; [Intent.FLAG_ACTIVITY_NEW_TASK] only when [context] is not an [Activity].
     */
    fun applyExternalAppLaunchFlags(intent: Intent, context: Context) {
        intent.addFlags(
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        )
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
