package vad.dashing.tbox

import android.app.Service
import android.graphics.PixelFormat
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vad.dashing.tbox.ui.FloatingDashboardUI
import vad.dashing.tbox.ui.MyLifecycleOwner

/**
 * Foreground package + persisted usage-stats rule sets from [BackgroundService] polling.
 * Hide rules win over force-show when the same panel is listed in both hide and show panel sets.
 */
internal data class UsageStatsOverlayRulesState(
    val foregroundPackage: String?,
    val watchHidePackages: Set<String>,
    val hidePanelIds: Set<String>,
    val watchShowPackages: Set<String>,
    val showPanelIds: Set<String>,
) {
    fun isUsageStatsForceHidden(panelId: String, myPackageName: String): Boolean {
        val fg = foregroundPackage ?: return false
        if (fg == myPackageName) return false
        if (watchHidePackages.isEmpty() || hidePanelIds.isEmpty()) return false
        return fg in watchHidePackages && hidePanelIds.contains(panelId)
    }

    /**
     * When a show-watched app is foreground, show listed panels even if disabled in settings.
     * Suppressed when [foregroundPackage] is in [watchHidePackages] (first list priority) or
     * when the panel is listed in [hidePanelIds] (same-panel intersection: hide wins).
     */
    fun isUsageStatsForceShowing(panelId: String, myPackageName: String): Boolean {
        val fg = foregroundPackage ?: return false
        if (fg == myPackageName) return false
        if (fg in watchHidePackages) return false
        if (hidePanelIds.contains(panelId)) return false
        if (watchShowPackages.isEmpty() || showPanelIds.isEmpty()) return false
        return fg in watchShowPackages && showPanelIds.contains(panelId)
    }

    companion object {
        val EMPTY = UsageStatsOverlayRulesState(null, emptySet(), emptySet(), emptySet(), emptySet())
    }
}

internal class FloatingOverlayController(
    private val service: Service,
    private val settingsManager: SettingsManager,
    private val appDataManager: AppDataManager,
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

    companion object {
        private const val TAG = "Floating Dashboard"
        private const val MAX_OVERLAY_RETRIES = 3
        private const val MIN_OVERLAY_SIZE = 50
    }

    fun suspendOverlays() {
        overlaysSuspended = true
        hiddenFloatingPanelIds.clear()
        usageStatsOverlayRules = UsageStatsOverlayRulesState.EMPTY
        closeAllOverlays()
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
        val ids = overlayViews.keys.toList()
        ids.forEach { closeOverlay(it) }
    }

    fun onDestroy() {
        hiddenFloatingPanelIds.clear()
        usageStatsOverlayRules = UsageStatsOverlayRulesState.EMPTY
        closeAllOverlays()
        lifecycleOwner.setCurrentState(Lifecycle.State.DESTROYED)
        lifecycleOwner.clear()
        overlayRetryCounts.clear()
        overlayOffIds.clear()
        overlayParams.clear()
        windowManager = null
    }

    /**
     * WindowManager / ComposeView must run on the main thread; callers may use any dispatcher.
     */
    suspend fun syncFloatingDashboards(configs: List<FloatingDashboardConfig>) {
        withContext(Dispatchers.Main) {
            FloatingOverlayLoadTimings.reset()
            FloatingOverlayLoadTimings.mark("float_sync_enter")
            if (overlaysSuspended) {
                if (overlayViews.isNotEmpty()) {
                    closeAllOverlays()
                }
                FloatingOverlayLoadTimings.mark("float_sync_suspended")
                FloatingOverlayLoadTimings.log("Timings.FloatingOverlay.sync")
                return@withContext
            }
            val myPkg = service.packageName
            val configMap = configs.associateBy { it.id }
            val visibleConfigs = configs.filter { cfg ->
                shouldShowFloatingOverlay(cfg, myPkg)
            }

            val visibleIds = visibleConfigs.map { it.id }.toSet()
            val existingIds = overlayViews.keys.toSet()

            // Remove counters for configs that no longer exist.
            val removedIds = overlayRetryCounts.keys - configMap.keys
            removedIds.forEach { id ->
                overlayRetryCounts.remove(id)
                overlayOffIds.remove(id)
                hiddenFloatingPanelIds.remove(id)
            }

            visibleConfigs.forEach { config ->
                if (isFloatingPanelTemporarilyHidden(config.id, myPkg)) {
                    if (overlayViews.containsKey(config.id)) {
                        closeOverlay(config.id)
                    }
                    return@forEach
                }
                overlayOffIds.remove(config.id)

                val view = overlayViews[config.id]
                if (view != null) {
                    updateOverlayLayout(config)
                } else {
                    openOverlay(config, myPkg)
                }
            }

            val idsToClose = existingIds - visibleIds
            idsToClose.forEach { id ->
                closeOverlay(id)
            }

            val disabledIds = configMap.keys - visibleIds
            disabledIds.forEach { id ->
                overlayRetryCounts.remove(id)
                overlayOffIds.remove(id)
                hiddenFloatingPanelIds.remove(id)
            }
            FloatingOverlayLoadTimings.mark("float_sync_done")
            FloatingOverlayLoadTimings.log("Timings.FloatingOverlay.sync")
        }
    }

    suspend fun ensureFloatingDashboards(configs: List<FloatingDashboardConfig>) {
        withContext(Dispatchers.Main) {
            FloatingOverlayLoadTimings.reset()
            FloatingOverlayLoadTimings.mark("float_ensure_enter")
            if (overlaysSuspended) {
                FloatingOverlayLoadTimings.mark("float_ensure_suspended")
                FloatingOverlayLoadTimings.log("Timings.FloatingOverlay.ensure")
                return@withContext
            }
            val myPkg = service.packageName
            val visibleConfigs = configs.filter { cfg -> shouldShowFloatingOverlay(cfg, myPkg) }
            visibleConfigs.forEach { config ->
                if (isFloatingPanelTemporarilyHidden(config.id, myPkg)) return@forEach
                if (overlayOffIds.contains(config.id)) return@forEach
                if (overlayViews.containsKey(config.id)) {
                    overlayRetryCounts[config.id] = 0
                    return@forEach
                }

                val retryCount = overlayRetryCounts[config.id] ?: 0
                if (retryCount >= MAX_OVERLAY_RETRIES * 2) {
                    TboxRepository.addLog("ERROR", TAG, "Can't show: ${config.id}")
                    overlayOffIds.add(config.id)
                    return@forEach
                }
                overlayRetryCounts[config.id] = retryCount + 1
                openOverlay(config, myPkg)
            }
            FloatingOverlayLoadTimings.mark("float_ensure_done")
            FloatingOverlayLoadTimings.log("Timings.FloatingOverlay.ensure")
        }
    }

    private fun shouldShowFloatingOverlay(config: FloatingDashboardConfig, myPackageName: String): Boolean {
        if (usageStatsOverlayRules.isUsageStatsForceHidden(config.id, myPackageName)) {
            return false
        }
        if (config.enabled) return true
        return usageStatsOverlayRules.isUsageStatsForceShowing(config.id, myPackageName)
    }

    private fun openOverlay(config: FloatingDashboardConfig, myPackageName: String) {
        if (windowManager == null) {
            try {
                windowManager = service.getSystemService(WindowManager::class.java)
            } catch (e: Exception) {
                Log.e("FloatingDashboard", "Error creating Window manager", e)
                TboxRepository.addLog(
                    "ERROR",
                    TAG,
                    "Error creating Window manager: ${e.message}"
                )
                return
            }
        }

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

        val layoutParams = WindowManager.LayoutParams(
            config.width.coerceAtLeast(MIN_OVERLAY_SIZE),
            config.height.coerceAtLeast(MIN_OVERLAY_SIZE),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = config.startX.coerceAtLeast(0)
            y = config.startY.coerceAtLeast(0)
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
                        onRebootTbox = onRebootTbox,
                        onTripFinishAndStart = onTripFinishAndStart,
                        panelId = config.id,
                        params = layoutParams
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("FloatingDashboard", "Error creating view", e)
            TboxRepository.addLog("ERROR", TAG, "Failed to create: ${e.message}")
            return
        }

        try {
            windowManager?.addView(newComposeView, layoutParams)
            overlayViews[config.id] = newComposeView
            overlayParams[config.id] = layoutParams

            if (!lifecycleOwner.isInitialized || lifecycleOwner.lifecycle.currentState.isAtLeast(
                    Lifecycle.State.DESTROYED
                )
            ) {
                lifecycleOwner.setCurrentState(Lifecycle.State.STARTED)
            }

            overlayRetryCounts[config.id] = 0
            TboxRepository.addLog("INFO", TAG, "Shown: ${config.id}")
            FloatingOverlayLoadTimings.mark("float_shown_${config.id.replace(Regex("[^a-zA-Z0-9_-]"), "_")}")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding view", e)
            TboxRepository.addLog("ERROR", TAG, "Failed to show: ${e.message}")
        }
    }

    private fun closeOverlay(panelId: String) {
        val view = overlayViews.remove(panelId)
        overlayParams.remove(panelId)
        if (view != null) {
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
                TboxRepository.addLog("ERROR", TAG, "Error removing view")
                Log.e(TAG, "Error removing view", e)
            }
        }

        overlayRetryCounts.remove(panelId)
        overlayOffIds.remove(panelId)
        TboxRepository.addLog("INFO", TAG, "Closed: $panelId")
    }

    private fun updateWindowPosition(panelId: String, x: Int, y: Int) {
        val params = overlayParams[panelId] ?: return
        if (params.x == x && params.y == y) return
        params.x = x.coerceAtLeast(0)
        params.y = y.coerceAtLeast(0)
        overlayViews[panelId]?.let { view ->
            windowManager?.updateViewLayout(view, params)
        }
    }

    private fun updateWindowSize(panelId: String, width: Int, height: Int) {
        val params = overlayParams[panelId] ?: return
        if (params.width == width && params.height == height) return
        params.width = width.coerceAtLeast(MIN_OVERLAY_SIZE)
        params.height = height.coerceAtLeast(MIN_OVERLAY_SIZE)
        overlayViews[panelId]?.let { view ->
            windowManager?.updateViewLayout(view, params)
        }
    }

    private fun updateOverlayLayout(config: FloatingDashboardConfig) {
        val params = overlayParams[config.id] ?: return
        val newWidth = config.width.coerceAtLeast(MIN_OVERLAY_SIZE)
        val newHeight = config.height.coerceAtLeast(MIN_OVERLAY_SIZE)
        val newX = config.startX.coerceAtLeast(0)
        val newY = config.startY.coerceAtLeast(0)
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
            windowManager?.updateViewLayout(view, params)
        }
    }

    /**
     * If [hiddenFloatingPanelIds] is empty, hides every floating panel in [currentlyShownIds] except [originPanelId].
     * If already hiding, clears the hidden set so panels can be shown again (caller should run sync/ensure).
     */
    suspend fun toggleHideOtherFloatingPanels(
        originPanelId: String,
        currentlyShownIds: Set<String>,
        /** When false (e.g. tile on main tab / MainScreen), hide every currently shown floating panel. */
        excludeOriginPanel: Boolean = true
    ) {
        withContext(Dispatchers.Main) {
            if (hiddenFloatingPanelIds.isNotEmpty()) {
                hiddenFloatingPanelIds.clear()
                return@withContext
            }
            if (excludeOriginPanel && originPanelId.isBlank()) return@withContext
            val toHide = if (excludeOriginPanel) {
                currentlyShownIds - originPanelId
            } else {
                currentlyShownIds
            }
            hiddenFloatingPanelIds.addAll(toHide)
            toHide.forEach { panelId ->
                if (overlayViews.containsKey(panelId)) {
                    closeOverlay(panelId)
                }
            }
        }
    }

    private fun isFloatingPanelTemporarilyHidden(panelId: String, myPackageName: String): Boolean =
        hiddenFloatingPanelIds.contains(panelId) ||
            usageStatsOverlayRules.isUsageStatsForceHidden(panelId, myPackageName)

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
