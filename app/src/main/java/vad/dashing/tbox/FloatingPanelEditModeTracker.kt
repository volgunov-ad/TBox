package vad.dashing.tbox

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks floating overlay edit sessions for usage-stats hide bypass.
 *
 * - Overlay grid edit mode ([vad.dashing.tbox.ui.FloatingDashboard] long-press).
 * - Tile configuration dialog opened from a floating panel on [MainActivity].
 *
 * Main-screen embedded panels ([vad.dashing.tbox.ui.MainScreenDashboardPanel]) are not tracked.
 */
object FloatingPanelEditModeTracker {

    private val lock = Any()
    private val overlayEditPanelIds = mutableSetOf<String>()
    private val tileEditDialogPanelIds = mutableSetOf<String>()

    private val _suppressUsageStatsHide = MutableStateFlow(false)
    val suppressUsageStatsHide: StateFlow<Boolean> = _suppressUsageStatsHide.asStateFlow()

    private val _overlayEditEpoch = MutableStateFlow(0)
    /** Bumps when overlay edit membership changes — overlays re-layout (e.g. expand while collapsed). */
    val overlayEditEpoch: StateFlow<Int> = _overlayEditEpoch.asStateFlow()

    fun setOverlayEditMode(panelId: String, editing: Boolean) {
        if (panelId.isBlank()) return
        synchronized(lock) {
            val changed = if (editing) {
                overlayEditPanelIds.add(panelId)
            } else {
                overlayEditPanelIds.remove(panelId)
            }
            publishLocked()
            if (changed) {
                _overlayEditEpoch.value = _overlayEditEpoch.value + 1
            }
        }
    }

    fun setTileEditDialogOpen(panelId: String, open: Boolean) {
        if (panelId.isBlank()) return
        synchronized(lock) {
            if (open) {
                tileEditDialogPanelIds.add(panelId)
            } else {
                tileEditDialogPanelIds.remove(panelId)
            }
            publishLocked()
        }
    }

    fun isOverlayInEditMode(panelId: String): Boolean {
        if (panelId.isBlank()) return false
        synchronized(lock) {
            return panelId in overlayEditPanelIds
        }
    }

    fun shouldSuppressUsageStatsHide(): Boolean = _suppressUsageStatsHide.value

    private fun publishLocked() {
        _suppressUsageStatsHide.value =
            overlayEditPanelIds.isNotEmpty() || tileEditDialogPanelIds.isNotEmpty()
    }
}

/**
 * While a floating panel runs its in-Compose collapse/expand animation, overlay sync must keep
 * the WindowManager frame at the **expanded** size so the animation has room to play.
 */
object FloatingPanelCollapseAnimationGate {
    private val lock = Any()
    private val animatingPanelIds = mutableSetOf<String>()

    fun setAnimating(panelId: String, animating: Boolean) {
        if (panelId.isBlank()) return
        synchronized(lock) {
            if (animating) {
                animatingPanelIds.add(panelId)
            } else {
                animatingPanelIds.remove(panelId)
            }
        }
    }

    fun isAnimating(panelId: String): Boolean {
        if (panelId.isBlank()) return false
        synchronized(lock) {
            return panelId in animatingPanelIds
        }
    }
}
