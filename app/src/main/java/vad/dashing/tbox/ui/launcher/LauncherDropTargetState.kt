package vad.dashing.tbox.ui.launcher

import android.graphics.Rect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class LauncherDropZoneKind { Grid, Dock }

data class LauncherDropZone(
    val kind: LauncherDropZoneKind,
    val slotIndex: Int,
    val bounds: Rect,
)

/** Tracks on-screen drop targets while dragging apps from the drawer. */
object LauncherDropTargetState {
    private val zones = mutableStateMapOf<String, LauncherDropZone>()
    var draggingPackage by mutableStateOf<String?>(null)

    fun register(key: String, zone: LauncherDropZone) {
        zones[key] = zone
    }

    fun unregister(key: String) {
        zones.remove(key)
    }

    fun findDropAt(screenX: Int, screenY: Int): LauncherDropZone? =
        zones.values.firstOrNull { zone ->
            zone.bounds.contains(screenX, screenY)
        }

    fun clearDrag() {
        draggingPackage = null
    }
}
