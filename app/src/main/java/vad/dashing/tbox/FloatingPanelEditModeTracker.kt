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

    fun setOverlayEditMode(panelId: String, editing: Boolean) {
        if (panelId.isBlank()) return
        synchronized(lock) {
            if (editing) {
                overlayEditPanelIds.add(panelId)
            } else {
                overlayEditPanelIds.remove(panelId)
            }
            publishLocked()
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

    fun shouldSuppressUsageStatsHide(): Boolean = _suppressUsageStatsHide.value

    private fun publishLocked() {
        _suppressUsageStatsHide.value =
            overlayEditPanelIds.isNotEmpty() || tileEditDialogPanelIds.isNotEmpty()
    }
}
