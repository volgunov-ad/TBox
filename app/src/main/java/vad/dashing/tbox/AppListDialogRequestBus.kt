package vad.dashing.tbox

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Requests opening the app-list dialog from any dashboard surface (including floating overlays).
 * Hosted in [vad.dashing.tbox.ui.TboxApp] / window-mode overlay so Compose [androidx.compose.ui.window.Dialog]
 * runs with an Activity (or overlay MainScreen) context.
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
