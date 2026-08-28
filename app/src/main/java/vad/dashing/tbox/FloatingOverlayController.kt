package vad.dashing.tbox

import android.app.Service
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import vad.dashing.tbox.ui.FloatingDashboardUI
import vad.dashing.tbox.ui.MainScreenWindowOverlayUI
import vad.dashing.tbox.ui.MyLifecycleOwner
import vad.dashing.tbox.freeform.FreeformCompanionSession
import vad.dashing.tbox.freeform.FreeformDisplaySpaces
import vad.dashing.tbox.freeform.FreeformLaunchBounds
import vad.dashing.tbox.freeform.MainScreenWindowOverlayLayout
import kotlin.math.roundToInt

/**
 * Foreground package + persisted usage-stats rule sets from [BackgroundService] polling.
 * Hide wins when it actually applies to a panel ([isUsageStatsForceHidden]).
 *
 * When [isMainActivityVisible] is true:
 * - force-show is always off (do not mount overlays over the main window);
 * - if own package is in the hide-watch list, hide listed panels immediately even when
 *   sticky UsageStats still reports maps/nav as foreground (avoids show→hide thrash on open).
 */
internal data class UsageStatsOverlayRulesState(
    val foregroundPackage: String?,
    val isMainActivityVisible: Boolean,
    val suppressFloatingPanelUsageStatsHide: Boolean,
    /** When true, force-show rules do not open panels (startup settle / boot open episode). */
    val suppressFloatingPanelUsageStatsForceShow: Boolean,
    val watchHidePackages: Set<String>,
    val hidePanelIds: Set<String>,
    val watchShowPackages: Set<String>,
    val showPanelIds: Set<String>,
) {
    private fun shouldIgnoreOwnPackageForeground(myPackageName: String, fg: String): Boolean {
        return fg == myPackageName && !isMainActivityVisible
    }

    fun isUsageStatsForceHidden(panelId: String, myPackageName: String): Boolean {
        if (suppressFloatingPanelUsageStatsHide) return false
        if (watchHidePackages.isEmpty() || hidePanelIds.isEmpty()) return false
        // Prefer MainActivity visibility over sticky non-own UsageStats foreground for
        // «hide when TBox main window is shown» rules (own package in hide-watch).
        if (isMainActivityVisible &&
            usageStatsWatchContains(watchHidePackages, myPackageName) &&
            hidePanelIds.contains(panelId)
        ) {
            return true
        }
        val fg = foregroundPackage ?: return false
        if (shouldIgnoreOwnPackageForeground(myPackageName, fg)) return false
        return usageStatsWatchContains(watchHidePackages, fg) && hidePanelIds.contains(panelId)
    }

    /**
     * When a show-watched app is foreground, show listed panels even if disabled in settings.
     * Suppressed when this panel is actually hidden by the hide rule ([isUsageStatsForceHidden]),
     * when MainActivity is visible, or when [suppressFloatingPanelUsageStatsForceShow] is set.
     */
    fun isUsageStatsForceShowing(panelId: String, myPackageName: String): Boolean {
        if (suppressFloatingPanelUsageStatsForceShow) return false
        // Never force-show over the main window (sticky maps FG used to keep force-show
        // active after MainActivity resumed → overlay thrash / crash with nav widgets).
        if (isMainActivityVisible) return false
        val fg = foregroundPackage ?: return false
        if (shouldIgnoreOwnPackageForeground(myPackageName, fg)) return false
        if (isUsageStatsForceHidden(panelId, myPackageName)) return false
        if (watchShowPackages.isEmpty() || showPanelIds.isEmpty()) return false
        return usageStatsWatchContains(watchShowPackages, fg) && showPanelIds.contains(panelId)
    }

    companion object {
        val EMPTY = UsageStatsOverlayRulesState(
            foregroundPackage = null,
            isMainActivityVisible = false,
            suppressFloatingPanelUsageStatsHide = false,
            suppressFloatingPanelUsageStatsForceShow = false,
            watchHidePackages = emptySet(),
            hidePanelIds = emptySet(),
            watchShowPackages = emptySet(),
            showPanelIds = emptySet()
        )
    }
}

/** Usage stats package names vs launcher-picked entries (trim / case). */
internal fun usageStatsWatchContains(watch: Set<String>, foregroundPackage: String): Boolean {
    val f = foregroundPackage.trim()
    if (f.isEmpty()) return false
    return watch.any { w -> w.trim().equals(f, ignoreCase = true) }
}

internal class FloatingOverlayController(
    private val service: Service,
    private val settingsManager: SettingsManager,
    private val appDataManager: AppDataManager,
    private val overlayScope: CoroutineScope,
    private val onRebootTbox: () -> Unit,
    private val onTripFinishAndStart: () -> Unit,
) {
    private var windowManager: WindowManager? = null
    private val overlayViews = linkedMapOf<String, ComposeView>()
    private val overlayParams = mutableMapOf<String, WindowManager.LayoutParams>()
    private val overlayRetryCounts = mutableMapOf<String, Int>()
    private val overlayOffIds = mutableSetOf<String>()
    /** Panels temporarily closed by the «hide other floating panels» tile; cleared on restore or global suspend. */
    private val hiddenFloatingPanelIds = mutableSetOf<String>()
    private var usageStatsOverlayRules: UsageStatsOverlayRulesState = UsageStatsOverlayRulesState.EMPTY
    private var overlaysSuspended = false
    private val lifecycleOwner by lazy { MyLifecycleOwner() }
    private val overlaySyncMutex = Mutex()
    /** Serializes staged [WindowManager.addView] bursts after large theme / config imports. */
    private var stagedOpenJob: Job? = null
    private var stagedOpenGeneration = 0L

    private data class OverlayOpenWork(
        val visibleConfigs: List<FloatingDashboardConfig>,
        val pendingOpens: List<FloatingDashboardConfig>,
        val myPkg: String,
        val reorderZOrder: Boolean,
    )

    /** Dedicated MainScreen window-mode overlay (not a floating panel id). */
    private var mainScreenWindowView: ComposeView? = null
    private var mainScreenWindowParams: WindowManager.LayoutParams? = null
    /**
     * WM for the MainScreen overlay — preferably the HU app/virtual display context
     * so x/y match freeform companion bounds (not the full physical panel).
     */
    private var mainScreenWindowManager: WindowManager? = null
    /**
     * Separate from [lifecycleOwner] used by floating panels — sharing ViewModelStore with a
     * full MainScreen composition caused process crashes on dispose during window-mode exit.
     */
    private var mainScreenLifecycleOwner: MyLifecycleOwner? = null

    companion object {
        private const val TAG = "Floating Dashboard"
        private const val MAX_OVERLAY_RETRIES = 3
        private const val MIN_OVERLAY_SIZE = MIN_FLOATING_PANEL_SIZE_PX
        private const val OVERLAY_FADE_MS = 300L
        private const val MAIN_SCREEN_WINDOW_TAG = "MainScreenWindow"
    }

    val isMainScreenWindowVisible: Boolean
        get() = mainScreenWindowView != null

    @Volatile
    private var overlaysClosing = false

    fun suspendOverlays() {
        try {
            cancelStagedOverlayOpens()
            overlaysSuspended = true
            hiddenFloatingPanelIds.clear()
            usageStatsOverlayRules = UsageStatsOverlayRulesState.EMPTY
            closeAllOverlays()
        } catch (e: Exception) {
            Log.e(TAG, "suspendOverlays failed", e)
            TboxRepository.addLog("ERROR", TAG, "suspendOverlays: ${e.message}")
        }
    }

    fun resumeOverlays() {
        overlaysSuspended = false
    }

    /** Clears temporary hide list (e.g. after persisted floating `enabled` toggles). */
    suspend fun clearHiddenFloatingPanelIds() {
        withContext(Dispatchers.Main) {
            hiddenFloatingPanelIds.clear()
        }
    }

    fun closeAllOverlays() {
        cancelStagedOverlayOpens()
        overlaysClosing = true
        try {
            val ids = overlayViews.keys.toList()
            ids.forEach { id ->
                try {
                    closeOverlay(id, immediate = true)
                } catch (e: Exception) {
                    Log.e(TAG, "closeAllOverlays failed for $id", e)
                }
            }
            try {
                removeMainScreenWindowImmediate()
            } catch (e: Exception) {
                Log.e(TAG, "removeMainScreenWindowImmediate failed", e)
            }
        } finally {
            overlaysClosing = false
        }
    }

    fun onDestroy() {
        try {
            cancelStagedOverlayOpens()
            hiddenFloatingPanelIds.clear()
            usageStatsOverlayRules = UsageStatsOverlayRulesState.EMPTY
            closeAllOverlays()
            lifecycleOwner.setCurrentState(Lifecycle.State.DESTROYED)
            lifecycleOwner.clear()
            overlayRetryCounts.clear()
            overlayOffIds.clear()
            overlayParams.clear()
            windowManager = null
            mainScreenWindowManager = null
        } catch (e: Exception) {
            Log.e(TAG, "onDestroy overlay cleanup failed", e)
        }
    }

    /**
     * Shows the full MainScreen as a TYPE_APPLICATION_OVERLAY using geometry from settings.
     * Not part of floating-panel sync / edit mode.
     */
    suspend fun showMainScreenWindow() {
        withContext(Dispatchers.Main) {
            if (overlaysSuspended) return@withContext
            if (!Settings.canDrawOverlays(service)) {
                TboxRepository.addLog("ERROR", MAIN_SCREEN_WINDOW_TAG, "Cannot draw overlay")
                return@withContext
            }
            val session = FreeformCompanionSession.state.value
            val activityDisplay = if (session != null) {
                FreeformDisplaySpaces.ActivityDisplay(
                    displayId = session.activityDisplayId,
                    widthPx = session.activityDisplayWidth,
                    heightPx = session.activityDisplayHeight,
                )
            } else {
                FreeformDisplaySpaces.resolveActivityDisplay(service)
            }
            val msWm = FreeformDisplaySpaces.windowManagerForDisplay(service, activityDisplay.displayId)
                ?: run {
                    TboxRepository.addLog("ERROR", MAIN_SCREEN_WINDOW_TAG, "WindowManager unavailable")
                    return@withContext
                }
            val (wmW, wmH) = FreeformDisplaySpaces.sizePxForWindowManager(msWm)
            val autoGeometry = settingsManager.mainScreenWindowModeAutoGeometryFlow.first()
            val actW = (session?.activityDisplayWidth ?: wmW).coerceAtLeast(1)
            val actH = (session?.activityDisplayHeight ?: wmH).coerceAtLeast(1)
            // Same coordinate space as freeform (app VD) when the overlay WM is bound to it.
            // If WM falls back to a larger physical panel, map through an estimated origin.
            val geometry = when {
                autoGeometry && session != null -> {
                    val wmMatchesActivity =
                        FreeformDisplaySpaces.displaySizesMatch(actW, actH, wmW, wmH)
                    val (originX, originY) = if (wmMatchesActivity) {
                        0 to 0
                    } else {
                        FreeformDisplaySpaces.estimateActivityOriginInOverlay(
                            activityWidthPx = actW,
                            activityHeightPx = actH,
                            overlayWidthPx = wmW,
                            overlayHeightPx = wmH,
                        )
                    }
                    // Prefer activity size when WM matches so % split is not clamped by a
                    // slightly smaller currentWindowMetrics.
                    val overlayW = if (wmMatchesActivity) actW else wmW
                    val overlayH = if (wmMatchesActivity) actH else wmH
                    FreeformLaunchBounds.computeComplementOverlayGeometry(
                        activityDisplayWidth = actW,
                        activityDisplayHeight = actH,
                        overlayDisplayWidth = overlayW,
                        overlayDisplayHeight = overlayH,
                        side = session.side,
                        percent = session.percent,
                        activityOriginInOverlayX = originX,
                        activityOriginInOverlayY = originY,
                    )
                }
                else -> {
                    (
                        settingsManager.mainScreenWindowModeGeometryFlow.first()
                            ?: MainScreenWindowModeGeometry.defaultForDisplay(wmW, wmH)
                        ).normalized()
                }
            }
            val cropEnabled = session?.overlayCrop == true
            // Crop viewport must stay in activity/VD space (not WM-mapped x/y), whenever a
            // freeform session is active — even if auto-geometry is off for the window rect.
            val cropLayout = if (session != null) {
                MainScreenWindowOverlayLayout.cropViewportForCompanion(
                    activityWidthPx = actW,
                    activityHeightPx = actH,
                    side = session.side,
                    percent = session.percent,
                ).copy(cropEnabled = cropEnabled)
            } else {
                MainScreenWindowOverlayLayout.State(
                    cropEnabled = cropEnabled,
                    fullWidthPx = actW,
                    fullHeightPx = actH,
                    originXPx = geometry.startX,
                    originYPx = geometry.startY,
                )
            }
            MainScreenWindowOverlayLayout.update(
                cropEnabled = cropLayout.cropEnabled,
                fullWidthPx = cropLayout.fullWidthPx,
                fullHeightPx = cropLayout.fullHeightPx,
                geometry = MainScreenWindowModeGeometry(
                    startX = cropLayout.originXPx,
                    startY = cropLayout.originYPx,
                    width = geometry.width,
                    height = geometry.height,
                ),
            )

            val existing = mainScreenWindowView
            val existingParams = mainScreenWindowParams
            val geomSummary =
                "x=${geometry.startX} y=${geometry.startY} w=${geometry.width} h=${geometry.height}"
            val cropSummary =
                "origin=${cropLayout.originXPx},${cropLayout.originYPx} " +
                    "full=${cropLayout.fullWidthPx}x${cropLayout.fullHeightPx}"
            val sessionSummary = if (session != null) {
                "pkg=${session.packageName} side=${session.side.storageKey} pct=${session.percent} " +
                    "crop=${session.overlayCrop} " +
                    "act=${session.activityDisplayWidth}x${session.activityDisplayHeight}"
            } else {
                "session=null"
            }
            if (existing != null && existingParams != null && mainScreenWindowManager === msWm) {
                existingParams.width = geometry.width.coerceAtLeast(MIN_OVERLAY_SIZE)
                existingParams.height = geometry.height.coerceAtLeast(MIN_OVERLAY_SIZE)
                existingParams.x = geometry.startX.coerceAtLeast(0)
                existingParams.y = geometry.startY.coerceAtLeast(0)
                try {
                    if (existing.isAttachedToWindow) {
                        msWm.updateViewLayout(existing, existingParams)
                        TboxRepository.addLog(
                            "DEBUG",
                            "WindowMode",
                            "overlay update auto=$autoGeometry crop=$cropEnabled " +
                                "$sessionSummary wm=${wmW}x${wmH} geo=$geomSummary $cropSummary",
                        )
                    }
                } catch (e: Exception) {
                    Log.e(MAIN_SCREEN_WINDOW_TAG, "Failed to update layout", e)
                }
                return@withContext
            }
            if (existing != null) {
                // Re-bind to a different display WM — remove immediately (no fade race).
                removeMainScreenWindowImmediate()
            }

            val owner = MyLifecycleOwner().also { created ->
                created.setCurrentState(Lifecycle.State.CREATED)
                created.setCurrentState(Lifecycle.State.STARTED)
            }
            mainScreenLifecycleOwner = owner

            val layoutParams = WindowManager.LayoutParams(
                geometry.width.coerceAtLeast(MIN_OVERLAY_SIZE),
                geometry.height.coerceAtLeast(MIN_OVERLAY_SIZE),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = geometry.startX.coerceAtLeast(0)
                y = geometry.startY.coerceAtLeast(0)
            }

            val composeView = ComposeView(service)
            try {
                composeView.apply {
                    setViewTreeLifecycleOwner(owner)
                    setViewTreeSavedStateRegistryOwner(owner)
                    setViewTreeViewModelStoreOwner(owner)
                    setContent {
                        MainScreenWindowOverlayUI(
                            settingsManager = settingsManager,
                            appDataManager = appDataManager,
                            onRebootTbox = onRebootTbox,
                            onTripFinishAndStart = onTripFinishAndStart,
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(MAIN_SCREEN_WINDOW_TAG, "Error creating view", e)
                TboxRepository.addLog("ERROR", MAIN_SCREEN_WINDOW_TAG, "Failed to create: ${e.message}")
                destroyMainScreenLifecycleOwner()
                return@withContext
            }

            try {
                composeView.alpha = 0f
                msWm.addView(composeView, layoutParams)
                mainScreenWindowManager = msWm
                mainScreenWindowView = composeView
                mainScreenWindowParams = layoutParams
                composeView.animate()
                    .alpha(1f)
                    .setDuration(OVERLAY_FADE_MS)
                    .start()
                TboxRepository.addLog(
                    "DEBUG",
                    "WindowMode",
                    "overlay shown auto=$autoGeometry crop=$cropEnabled $sessionSummary " +
                        "wm=${wmW}x${wmH} geo=$geomSummary $cropSummary " +
                        FreeformDisplaySpaces.describeOverlayWm(service, activityDisplay.displayId),
                )
            } catch (e: Exception) {
                Log.e(MAIN_SCREEN_WINDOW_TAG, "Error adding view", e)
                TboxRepository.addLog("ERROR", MAIN_SCREEN_WINDOW_TAG, "Failed to show: ${e.message}")
                destroyMainScreenLifecycleOwner()
            }
        }
    }

    suspend fun hideMainScreenWindow(immediate: Boolean = false) {
        withContext(Dispatchers.Main) {
            if (immediate) {
                removeMainScreenWindowImmediate()
            } else {
                hideMainScreenWindowInternal()
            }
        }
    }

    private fun hideMainScreenWindowInternal() {
        // Prefer immediate teardown — fade + disposeComposition races with gesture/MainActivity.
        removeMainScreenWindowImmediate()
    }

    private fun removeMainScreenWindowImmediate() {
        val view = mainScreenWindowView ?: return
        val wm = mainScreenWindowManager ?: windowManager
        mainScreenWindowView = null
        mainScreenWindowParams = null
        mainScreenWindowManager = null
        try {
            view.animate().cancel()
        } catch (_: Exception) {
        }
        // Stop collectors / ViewModels before tearing down Compose (shared-owner bugs crash here).
        try {
            mainScreenLifecycleOwner?.setCurrentState(Lifecycle.State.DESTROYED)
        } catch (_: Exception) {
        }
        try {
            mainScreenLifecycleOwner?.clear()
        } catch (_: Exception) {
        }
        try {
            view.setContent { }
        } catch (_: Exception) {
        }
        try {
            view.disposeComposition()
        } catch (e: Exception) {
            Log.w(MAIN_SCREEN_WINDOW_TAG, "disposeComposition failed", e)
        }
        try {
            if (view.isAttachedToWindow) {
                try {
                    wm?.removeViewImmediate(view)
                } catch (_: Exception) {
                    wm?.removeView(view)
                }
            }
        } catch (e: Exception) {
            TboxRepository.addLog("ERROR", MAIN_SCREEN_WINDOW_TAG, "Error removing view")
            Log.e(MAIN_SCREEN_WINDOW_TAG, "Error removing view", e)
        }
        destroyMainScreenLifecycleOwner()
        MainScreenWindowOverlayLayout.clear()
        TboxRepository.addLog("DEBUG", "WindowMode", "overlay closed immediate")
    }

    private fun destroyMainScreenLifecycleOwner() {
        val owner = mainScreenLifecycleOwner ?: return
        mainScreenLifecycleOwner = null
        try {
            if (owner.lifecycle.currentState != Lifecycle.State.DESTROYED) {
                owner.setCurrentState(Lifecycle.State.DESTROYED)
            }
        } catch (_: Exception) {
        }
        try {
            owner.clear()
        } catch (_: Exception) {
        }
    }

    private fun ensureWindowManager() {
        if (windowManager != null) return
        try {
            windowManager = service.getSystemService(WindowManager::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating Window manager", e)
            TboxRepository.addLog("ERROR", TAG, "Error creating Window manager: ${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    private fun displaySizePx(): Pair<Int, Int> {
        return try {
            val wm = windowManager ?: service.getSystemService(WindowManager::class.java)
                ?: return 1280 to 720
            // Floating panels: full physical metrics (unchanged). MainScreen window uses
            // FreeformDisplaySpaces / mainScreenWindowManager instead.
            if (Build.VERSION.SDK_INT >= 30) {
                val bounds = wm.maximumWindowMetrics.bounds
                bounds.width() to bounds.height()
            } else {
                val metrics = DisplayMetrics()
                wm.defaultDisplay.getRealMetrics(metrics)
                metrics.widthPixels to metrics.heightPixels
            }
        } catch (e: Exception) {
            Log.w(TAG, "displaySizePx failed", e)
            1280 to 720
        }
    }

    /**
     * WindowManager / ComposeView must run on the main thread; callers may use any dispatcher.
     *
     * @param reorderZOrder when false, skips remove/add z-order pass (safer for usage-stats hide sync).
     * @param closeImmediate when true, removes overlays without fade (avoids race with next open).
     */
    suspend fun syncFloatingDashboards(
        configs: List<FloatingDashboardConfig>,
        reorderZOrder: Boolean = true,
        closeImmediate: Boolean = false,
    ) {
        val stagedWork = overlaySyncMutex.withLock {
            withContext(Dispatchers.Main) {
                try {
                    FloatingOverlayLoadTimings.reset()
                    FloatingOverlayLoadTimings.mark("float_sync_enter")
                    if (overlaysSuspended || overlaysClosing) {
                        if (overlayViews.isNotEmpty() && !overlaysClosing) {
                            closeAllOverlays()
                        }
                        FloatingOverlayLoadTimings.mark("float_sync_suspended")
                        FloatingOverlayLoadTimings.log("Timings.FloatingOverlay.sync")
                        return@withContext null
                    }
                    val myPkg = service.packageName
                    val configMap = configs.associateBy { it.id }
                    val visibleConfigs = configs.filter { cfg ->
                        shouldShowFloatingOverlay(cfg, myPkg)
                    }

                    val visibleIds = visibleConfigs.map { it.id }.toSet()
                    val existingIds = overlayViews.keys.toSet()

                    val removedIds = overlayRetryCounts.keys - configMap.keys
                    removedIds.forEach { id ->
                        overlayRetryCounts.remove(id)
                        overlayOffIds.remove(id)
                        hiddenFloatingPanelIds.remove(id)
                    }

                    val pendingOpens = mutableListOf<FloatingDashboardConfig>()
                    visibleConfigs.forEach { config ->
                        try {
                            if (usageStatsOverlayRules.isUsageStatsForceShowing(config.id, myPkg)) {
                                overlayOffIds.remove(config.id)
                            }
                            if (isFloatingPanelTemporarilyHidden(config.id, myPkg)) {
                                if (overlayViews.containsKey(config.id)) {
                                    closeOverlay(config.id, immediate = closeImmediate)
                                }
                                return@forEach
                            }
                            overlayOffIds.remove(config.id)

                            val view = overlayViews[config.id]
                            if (view != null) {
                                updateOverlayLayout(config)
                            } else if (shouldQueueOverlayOpen(config, myPkg)) {
                                pendingOpens.add(config)
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.e(TAG, "sync panel failed id=${config.id}", e)
                            TboxRepository.addLog(
                                "ERROR",
                                TAG,
                                "sync panel ${config.id}: ${e.message}",
                            )
                        }
                    }

                    existingIds.forEach { id ->
                        if (id in visibleIds) return@forEach
                        try {
                            closeOverlay(id, immediate = closeImmediate)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.e(TAG, "sync close failed id=$id", e)
                            TboxRepository.addLog("ERROR", TAG, "sync close $id: ${e.message}")
                        }
                    }

                    val disabledIds = configMap.keys - visibleIds
                    disabledIds.forEach { id ->
                        overlayRetryCounts.remove(id)
                        overlayOffIds.remove(id)
                        hiddenFloatingPanelIds.remove(id)
                    }

                    if (pendingOpens.isEmpty()) {
                        maybeReorderVisibleOverlays(visibleConfigs, reorderZOrder)
                        FloatingOverlayLoadTimings.mark("float_sync_done")
                        FloatingOverlayLoadTimings.log("Timings.FloatingOverlay.sync")
                        return@withContext null
                    }

                    if (!FloatingOverlayOpenPlan.shouldUseStagedOpen(pendingOpens.size)) {
                        pendingOpens.forEach { config ->
                            try {
                                openOverlay(config, myPkg)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.e(TAG, "sync open failed id=${config.id}", e)
                                TboxRepository.addLog("ERROR", TAG, "sync open ${config.id}: ${e.message}")
                            }
                        }
                        maybeReorderVisibleOverlays(visibleConfigs, reorderZOrder)
                        FloatingOverlayLoadTimings.mark("float_sync_done")
                        FloatingOverlayLoadTimings.log("Timings.FloatingOverlay.sync")
                        return@withContext null
                    }

                    FloatingOverlayLoadTimings.mark("float_sync_staged_${pendingOpens.size}")
                    FloatingOverlayLoadTimings.log("Timings.FloatingOverlay.sync")
                    OverlayOpenWork(
                        visibleConfigs = visibleConfigs,
                        pendingOpens = pendingOpens,
                        myPkg = myPkg,
                        reorderZOrder = reorderZOrder,
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "syncFloatingDashboards failed", e)
                    TboxRepository.addLog("ERROR", TAG, "sync failed: ${e.message}")
                    null
                }
            }
        }
        stagedWork?.let { scheduleStagedOverlayOpens(it) }
    }

    suspend fun ensureFloatingDashboards(configs: List<FloatingDashboardConfig>) {
        if (stagedOpenJob?.isActive == true) return
        val stagedWork = overlaySyncMutex.withLock {
            withContext(Dispatchers.Main) {
                try {
                    FloatingOverlayLoadTimings.reset()
                    FloatingOverlayLoadTimings.mark("float_ensure_enter")
                    if (overlaysSuspended || overlaysClosing) {
                        FloatingOverlayLoadTimings.mark("float_ensure_suspended")
                        FloatingOverlayLoadTimings.log("Timings.FloatingOverlay.ensure")
                        return@withContext null
                    }
                    val myPkg = service.packageName
                    val visibleConfigs = configs.filter { cfg -> shouldShowFloatingOverlay(cfg, myPkg) }
                    val pendingOpens = FloatingOverlayOpenPlan.pendingOpensInConfigOrder(
                        visibleConfigs = visibleConfigs,
                        alreadyMountedIds = overlayViews.keys,
                        shouldOpen = { config -> shouldQueueOverlayOpenForEnsure(config, myPkg) },
                    )
                    if (pendingOpens.isEmpty()) {
                        FloatingOverlayLoadTimings.mark("float_ensure_done")
                        FloatingOverlayLoadTimings.log("Timings.FloatingOverlay.ensure")
                        return@withContext null
                    }
                    if (!FloatingOverlayOpenPlan.shouldUseStagedOpen(pendingOpens.size)) {
                        pendingOpens.forEach { config ->
                            try {
                                openOverlay(config, myPkg)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.e(TAG, "ensure open failed id=${config.id}", e)
                                TboxRepository.addLog("ERROR", TAG, "ensure open ${config.id}: ${e.message}")
                            }
                        }
                        FloatingOverlayLoadTimings.mark("float_ensure_done")
                        FloatingOverlayLoadTimings.log("Timings.FloatingOverlay.ensure")
                        return@withContext null
                    }
                    FloatingOverlayLoadTimings.mark("float_ensure_staged_${pendingOpens.size}")
                    FloatingOverlayLoadTimings.log("Timings.FloatingOverlay.ensure")
                    OverlayOpenWork(
                        visibleConfigs = visibleConfigs,
                        pendingOpens = pendingOpens,
                        myPkg = myPkg,
                        reorderZOrder = false,
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "ensureFloatingDashboards failed", e)
                    TboxRepository.addLog("ERROR", TAG, "ensure failed: ${e.message}")
                    null
                }
            }
        }
        stagedWork?.let { scheduleStagedOverlayOpens(it) }
    }

    private fun cancelStagedOverlayOpens() {
        stagedOpenGeneration += 1
        stagedOpenJob?.cancel()
        stagedOpenJob = null
    }

    private fun scheduleStagedOverlayOpens(work: OverlayOpenWork) {
        cancelStagedOverlayOpens()
        val generation = stagedOpenGeneration
        stagedOpenJob = overlayScope.launch {
            runStagedOverlayOpens(generation, work)
        }
    }

    private suspend fun runStagedOverlayOpens(generation: Long, work: OverlayOpenWork) {
        FloatingOverlayLoadTimings.reset()
        FloatingOverlayLoadTimings.mark("float_staged_enter_${work.pendingOpens.size}")
        val openStepSize = FloatingOverlayOpenPlan.stagedOpenStepSize(
            settingsManager.headUnitCanModeFlow.first(),
        )
        val batches = FloatingOverlayOpenPlan.pendingOpenBatches(work.pendingOpens, openStepSize)
        for (batch in batches) {
            for (config in batch) {
                if (generation != stagedOpenGeneration) return
                overlaySyncMutex.withLock {
                    withContext(Dispatchers.Main) {
                        if (generation != stagedOpenGeneration) return@withContext
                        if (overlaysSuspended || overlaysClosing) return@withContext
                        if (!shouldShowFloatingOverlay(config, work.myPkg)) return@withContext
                        if (isFloatingPanelTemporarilyHidden(config.id, work.myPkg)) return@withContext
                        if (overlayOffIds.contains(config.id)) return@withContext
                        if (overlayViews.containsKey(config.id)) return@withContext
                        try {
                            openOverlay(config, work.myPkg)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.e(TAG, "staged open failed id=${config.id}", e)
                            TboxRepository.addLog("ERROR", TAG, "staged open ${config.id}: ${e.message}")
                        }
                    }
                }
            }
            if (generation != stagedOpenGeneration) return
            delay(FloatingOverlayOpenPlan.STAGED_OPEN_DELAY_MS)
        }
        if (generation != stagedOpenGeneration) return
        overlaySyncMutex.withLock {
            withContext(Dispatchers.Main) {
                if (generation != stagedOpenGeneration) return@withContext
                maybeReorderVisibleOverlays(work.visibleConfigs, work.reorderZOrder)
                FloatingOverlayLoadTimings.mark("float_staged_done")
                FloatingOverlayLoadTimings.log("Timings.FloatingOverlay.staged")
            }
        }
    }

    private fun maybeReorderVisibleOverlays(
        visibleConfigs: List<FloatingDashboardConfig>,
        reorderZOrder: Boolean,
    ) {
        if (!reorderZOrder || FloatingPanelEditModeTracker.shouldSuppressUsageStatsHide()) return
        val mountedInConfigOrder = visibleConfigs.map { it.id }.filter { id ->
            overlayViews.containsKey(id)
        }
        try {
            reorderVisibleOverlays(mountedInConfigOrder)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "reorderVisibleOverlays failed", e)
            TboxRepository.addLog("ERROR", TAG, "reorder: ${e.message}")
        }
    }

    private fun shouldQueueOverlayOpen(config: FloatingDashboardConfig, myPkg: String): Boolean {
        if (overlayOffIds.contains(config.id)) return false
        return true
    }

    private fun shouldQueueOverlayOpenForEnsure(
        config: FloatingDashboardConfig,
        myPkg: String,
    ): Boolean {
        if (isFloatingPanelTemporarilyHidden(config.id, myPkg)) return false
        if (usageStatsOverlayRules.isUsageStatsForceShowing(config.id, myPkg)) {
            overlayOffIds.remove(config.id)
        }
        if (overlayOffIds.contains(config.id)) return false
        val retryCount = overlayRetryCounts[config.id] ?: 0
        if (retryCount >= MAX_OVERLAY_RETRIES * 2) {
            TboxRepository.addLog("ERROR", TAG, "Can't show: ${config.id}")
            overlayOffIds.add(config.id)
            return false
        }
        overlayRetryCounts[config.id] = retryCount + 1
        return true
    }

    private fun shouldShowFloatingOverlay(config: FloatingDashboardConfig, myPackageName: String): Boolean =
        FloatingOverlayVisibility.shouldShowBySettingsAndUsageStats(
            panelId = config.id,
            enabled = config.enabled,
            myPackageName = myPackageName,
            rules = usageStatsOverlayRules,
        )

    private fun isFloatingPanelTemporarilyHidden(panelId: String, myPackageName: String): Boolean =
        FloatingOverlayVisibility.isTemporarilyHidden(
            panelId = panelId,
            myPackageName = myPackageName,
            hiddenFloatingPanelIds = hiddenFloatingPanelIds,
            rules = usageStatsOverlayRules,
        )

    private suspend fun openOverlay(config: FloatingDashboardConfig, myPackageName: String) {
        ensureWindowManager()
        if (windowManager == null) return

        if (!config.enabled && !usageStatsOverlayRules.isUsageStatsForceShowing(config.id, myPackageName)) {
            TboxRepository.addLog("DEBUG", TAG, "Setting off: ${config.id}")
            return
        }
        if (overlayViews.containsKey(config.id)) {
            TboxRepository.addLog("DEBUG", TAG, "Already shown: ${config.id}")
            return
        }

        if (!Settings.canDrawOverlays(service)) {
            TboxRepository.addLog("ERROR", TAG, "Cannot draw overlay")
            return
        }

        val bounds = try {
            effectiveOverlayBounds(config)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "effectiveOverlayBounds failed for ${config.id}", e)
            TboxRepository.addLog("ERROR", TAG, "bounds ${config.id}: ${e.message}")
            return
        }
        val layoutParams = WindowManager.LayoutParams(
            bounds.width,
            bounds.height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bounds.x
            y = bounds.y
        }

        val newComposeView = ComposeView(service)

        try {
            newComposeView.apply {
                setViewTreeLifecycleOwner(lifecycleOwner)
                setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                setViewTreeViewModelStoreOwner(lifecycleOwner)

                setContent {
                    FloatingDashboardUI(
                        settingsManager = settingsManager,
                        appDataManager = appDataManager,
                        onUpdateWindowSize = { panelId, width, height ->
                            updateWindowSize(panelId, width, height)
                        },
                        onUpdateWindowPosition = { panelId, x, y ->
                            updateWindowPosition(panelId, x, y)
                        },
                        onUpdateWindowFrame = { panelId, x, y, width, height ->
                            updateOverlayFrame(panelId, x, y, width, height)
                        },
                        onRebootTbox = onRebootTbox,
                        onTripFinishAndStart = onTripFinishAndStart,
                        panelId = config.id,
                        params = layoutParams
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("FloatingDashboard", "Error creating view", e)
            TboxRepository.addLog("ERROR", TAG, "Failed to create: ${e.message}")
            try {
                newComposeView.disposeComposition()
            } catch (_: Exception) {
            }
            return
        }

        try {
            newComposeView.alpha = 0f
            windowManager?.addView(newComposeView, layoutParams)
            overlayViews[config.id] = newComposeView
            overlayParams[config.id] = layoutParams
            try {
                newComposeView.animate()
                    .alpha(1f)
                    .setDuration(OVERLAY_FADE_MS)
                    .start()
            } catch (e: Exception) {
                Log.w(TAG, "fade-in failed for ${config.id}", e)
                newComposeView.alpha = 1f
            }

            val lifecycleState = lifecycleOwner.lifecycle.currentState
            if (lifecycleState != Lifecycle.State.DESTROYED &&
                (!lifecycleOwner.isInitialized || !lifecycleState.isAtLeast(Lifecycle.State.STARTED))
            ) {
                // Step through CREATED — jumping INITIALIZED → STARTED can throw and leave
                // the shared overlay owner stuck below STARTED (breaks collectors / double-taps).
                if (!lifecycleState.isAtLeast(Lifecycle.State.CREATED)) {
                    lifecycleOwner.setCurrentState(Lifecycle.State.CREATED)
                }
                lifecycleOwner.setCurrentState(Lifecycle.State.STARTED)
            }

            overlayRetryCounts[config.id] = 0
            TboxRepository.addLog("DEBUG", TAG, "Shown: ${config.id}")
            FloatingOverlayLoadTimings.mark("float_shown_${config.id.replace(Regex("[^a-zA-Z0-9_-]"), "_")}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error adding view", e)
            TboxRepository.addLog("ERROR", TAG, "Failed to show: ${e.message}")
            overlayViews.remove(config.id)
            overlayParams.remove(config.id)
            try {
                if (newComposeView.isAttachedToWindow) {
                    windowManager?.removeView(newComposeView)
                }
            } catch (_: Exception) {
            }
            try {
                newComposeView.disposeComposition()
            } catch (_: Exception) {
            }
        }
    }

    private fun closeOverlay(panelId: String, immediate: Boolean = false) {
        val view = overlayViews.remove(panelId) ?: return
        overlayParams.remove(panelId)
        overlayRetryCounts.remove(panelId)
        overlayOffIds.remove(panelId)

        fun finishClose() {
            try {
                if (view.isAttachedToWindow) {
                    windowManager?.removeView(view)
                }
            } catch (e: Exception) {
                TboxRepository.addLog("ERROR", TAG, "Error removing view")
                Log.e(TAG, "Error removing view", e)
            }
            try {
                view.disposeComposition()
            } catch (_: Exception) {
            }
            TboxRepository.addLog("DEBUG", TAG, "Closed: $panelId")
        }

        try {
            view.animate().cancel()
        } catch (_: Exception) {
        }

        if (!immediate && view.isAttachedToWindow && view.alpha > 0f) {
            try {
                view.animate()
                    .alpha(0f)
                    .setDuration(OVERLAY_FADE_MS)
                    .withEndAction { finishClose() }
                    .start()
            } catch (e: Exception) {
                Log.w(TAG, "closeOverlay animate failed for $panelId", e)
                finishClose()
            }
        } else {
            finishClose()
        }
    }

    private fun updateWindowPosition(panelId: String, x: Int, y: Int) {
        val params = overlayParams[panelId] ?: return
        if (params.x == x && params.y == y) return
        params.x = x.coerceAtLeast(0)
        params.y = y.coerceAtLeast(0)
        overlayViews[panelId]?.let { view ->
            try {
                if (view.isAttachedToWindow) {
                    windowManager?.updateViewLayout(view, params)
                }
            } catch (e: Exception) {
                Log.w(TAG, "updateWindowPosition failed for $panelId", e)
            }
        }
    }

    private fun updateWindowSize(panelId: String, width: Int, height: Int) {
        val params = overlayParams[panelId] ?: return
        if (params.width == width && params.height == height) return
        params.width = width.coerceAtLeast(MIN_OVERLAY_SIZE)
        params.height = height.coerceAtLeast(MIN_OVERLAY_SIZE)
        overlayViews[panelId]?.let { view ->
            try {
                if (view.isAttachedToWindow) {
                    windowManager?.updateViewLayout(view, params)
                }
            } catch (e: Exception) {
                Log.w(TAG, "updateWindowSize failed for $panelId", e)
            }
        }
    }

    /**
     * Sets overlay frame without the edit-resize [MIN_OVERLAY_SIZE] floor so collapsed strips
     * can be thinner than 50px.
     */
    private fun updateOverlayFrame(panelId: String, x: Int, y: Int, width: Int, height: Int) {
        val params = overlayParams[panelId] ?: return
        val newX = x.coerceAtLeast(0)
        val newY = y
        val newW = width.coerceAtLeast(1)
        val newH = height.coerceAtLeast(1)
        if (params.x == newX && params.y == newY && params.width == newW && params.height == newH) {
            return
        }
        params.x = newX
        params.y = newY
        params.width = newW
        params.height = newH
        overlayViews[panelId]?.let { view ->
            try {
                if (view.isAttachedToWindow) {
                    windowManager?.updateViewLayout(view, params)
                }
            } catch (e: Exception) {
                Log.w(TAG, "updateOverlayFrame failed for $panelId", e)
            }
        }
    }

    private suspend fun updateOverlayLayout(config: FloatingDashboardConfig) {
        val params = overlayParams[config.id] ?: return
        val bounds = try {
            effectiveOverlayBounds(config)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "updateOverlayLayout bounds failed for ${config.id}", e)
            return
        }
        val newWidth = bounds.width
        val newHeight = bounds.height
        val newX = bounds.x
        val newY = bounds.y
        if (params.width == newWidth &&
            params.height == newHeight &&
            params.x == newX &&
            params.y == newY
        ) {
            return
        }
        params.width = newWidth
        params.height = newHeight
        params.x = newX
        params.y = newY
        overlayViews[config.id]?.let { view ->
            try {
                if (view.isAttachedToWindow) {
                    windowManager?.updateViewLayout(view, params)
                }
            } catch (e: Exception) {
                Log.w(TAG, "updateOverlayLayout failed for ${config.id}", e)
            }
        }
    }

    private suspend fun effectiveOverlayBounds(config: FloatingDashboardConfig): PanelPxBounds {
        val expanded = PanelPxBounds(
            x = config.startX.coerceAtLeast(0),
            y = config.startY.coerceAtLeast(0),
            width = config.width.coerceAtLeast(1),
            height = config.height.coerceAtLeast(1),
        )
        val edge = config.collapseEdgeOrNone()
        if (edge == PanelCollapseEdge.NONE) return expanded
        // While editing or animating collapse in Compose, keep the full frame.
        if (FloatingPanelEditModeTracker.isOverlayInEditMode(config.id)) return expanded
        if (FloatingPanelCollapseAnimationGate.isAnimating(config.id)) return expanded
        val states = try {
            settingsManager.panelCollapseStatesFlow.first()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "panelCollapseStatesFlow failed for ${config.id}", e)
            return expanded
        }
        if (!PanelCollapseStates.isCollapsed(states, config.id)) return expanded
        val stripThicknessPx = (
            normalizePanelCollapseStripThicknessDp(config.collapseStripThicknessDp) *
                service.resources.displayMetrics.density
            ).roundToInt()
        val touchZoneThicknessPx = (
            normalizePanelCollapseTouchZoneThicknessDp(
                config.collapseTouchZoneThicknessDp,
                config.collapseStripThicknessDp,
            ) * service.resources.displayMetrics.density
            ).roundToInt()
        return collapsedPanelInteractionBounds(
            expanded = expanded,
            edge = edge,
            stripThicknessPx = stripThicknessPx,
            touchZoneThicknessPx = touchZoneThicknessPx,
        )
    }

    /**
     * Aligns WindowManager z-order with [mountedInConfigOrder] (bottom → top), but only inside
     * geometrically overlapping clusters. Non-overlapping panels are left alone (no flicker).
     * Callers must pass only ids that currently have an overlay view.
     */
    private fun reorderVisibleOverlays(mountedInConfigOrder: List<String>) {
        val wm = windowManager ?: return
        if (mountedInConfigOrder.size <= 1) return

        val boundsById = buildMap {
            for (id in mountedInConfigOrder) {
                val params = overlayParams[id] ?: continue
                put(
                    id,
                    PanelPxBounds(
                        x = params.x,
                        y = params.y,
                        width = params.width,
                        height = params.height,
                    ),
                )
            }
        }
        val components = FloatingOverlayZOrder.overlappingComponentsInConfigOrder(
            mountedInConfigOrder,
            boundsById,
        )
        if (components.isEmpty()) return

        for (desiredOrder in components) {
            val desiredSet = desiredOrder.toHashSet()
            val currentInComponent = overlayViews.keys.filter { it in desiredSet }
            if (!FloatingOverlayZOrder.componentNeedsRemount(desiredOrder, currentInComponent)) {
                continue
            }
            desiredOrder.forEach { panelId ->
                val view = overlayViews[panelId] ?: return@forEach
                val params = overlayParams[panelId] ?: return@forEach
                try {
                    wm.removeView(view)
                    wm.addView(view, params)
                } catch (_: Exception) {
                    // Best-effort z-order update; normal sync/ensure loop will recover if needed.
                }
            }
        }
    }

    /**
     * If [hiddenFloatingPanelIds] is empty, hides every floating panel in [currentlyShownIds] except [originPanelId].
     * If already hiding, clears the hidden set so panels can be shown again (caller should run sync/ensure).
     *
     * @return true when temporary hide was cleared (panels should be shown again); false when hiding
     * or when the call was a no-op.
     */
    suspend fun toggleHideOtherFloatingPanels(
        originPanelId: String,
        currentlyShownIds: Set<String>,
        /** When false (e.g. tile on main tab / MainScreen), hide every currently shown floating panel. */
        excludeOriginPanel: Boolean = true
    ): Boolean {
        return withContext(Dispatchers.Main) {
            try {
                if (hiddenFloatingPanelIds.isNotEmpty()) {
                    hiddenFloatingPanelIds.clear()
                    return@withContext true
                }
                if (excludeOriginPanel && originPanelId.isBlank()) return@withContext false
                val toHide = if (excludeOriginPanel) {
                    currentlyShownIds - originPanelId
                } else {
                    currentlyShownIds
                }
                hiddenFloatingPanelIds.addAll(toHide)
                toHide.forEach { panelId ->
                    try {
                        if (overlayViews.containsKey(panelId)) {
                            closeOverlay(panelId, immediate = true)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "toggleHide close failed id=$panelId", e)
                    }
                }
                false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "toggleHideOtherFloatingPanels failed", e)
                TboxRepository.addLog("ERROR", TAG, "toggleHide: ${e.message}")
                false
            }
        }
    }

    /**
     * Updates usage-stats-driven visibility; [BackgroundService] calls this every poll before
     * [syncFloatingDashboards] / [ensureFloatingDashboards].
     */
    suspend fun setUsageStatsOverlayRulesState(state: UsageStatsOverlayRulesState) {
        withContext(Dispatchers.Main) {
            usageStatsOverlayRules = state
        }
    }
}
