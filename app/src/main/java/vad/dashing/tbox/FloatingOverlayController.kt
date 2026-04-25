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
            val configMap = configs.associateBy { it.id }
            val enabledConfigs = configs.filter { it.enabled }

            val enabledIds = enabledConfigs.map { it.id }.toSet()
            val existingIds = overlayViews.keys.toSet()

            // Remove counters for configs that no longer exist.
            val removedIds = overlayRetryCounts.keys - configMap.keys
            removedIds.forEach { id ->
                overlayRetryCounts.remove(id)
                overlayOffIds.remove(id)
                hiddenFloatingPanelIds.remove(id)
            }

            enabledConfigs.forEach { config ->
                if (hiddenFloatingPanelIds.contains(config.id)) {
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
                    openOverlay(config)
                }
            }

            val idsToClose = existingIds - enabledIds
            idsToClose.forEach { id ->
                closeOverlay(id)
            }

            val disabledIds = configMap.keys - enabledIds
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
            val enabledConfigs = configs.filter { it.enabled }
            enabledConfigs.forEach { config ->
                if (hiddenFloatingPanelIds.contains(config.id)) return@forEach
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
                openOverlay(config)
            }
            FloatingOverlayLoadTimings.mark("float_ensure_done")
            FloatingOverlayLoadTimings.log("Timings.FloatingOverlay.ensure")
        }
    }

    private fun openOverlay(config: FloatingDashboardConfig) {
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

        if (!config.enabled) {
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
}
