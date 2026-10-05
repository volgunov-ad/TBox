package vad.dashing.tbox

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Requests opening the app-list dialog from any dashboard surface (including floating overlays
 * and window-mode main screen). Hosted only in [vad.dashing.tbox.ui.TboxApp] so Compose
 * [androidx.compose.ui.window.Dialog] runs with an Activity context.
 *
 * Callers in window mode must exit to fullscreen first (see [vad.dashing.tbox.ui.openAppListDialog]);
 * a TYPE_APPLICATION_OVERLAY with FLAG_NOT_FOCUSABLE cannot show the dialog.
 */
object AppListDialogRequestBus {
    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    fun requestShow() {
        _visible.value = true
    }

    fun dismiss() {
        _visible.value = false
    }
}
