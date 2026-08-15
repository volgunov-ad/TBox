package vad.dashing.tbox.freeform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single freeform companion app session (one package beside the main-screen window overlay).
 */
object FreeformCompanionSession {
    data class State(
        val packageName: String,
        val side: FreeformLaunchSide,
        val percent: Int,
        /** Display size used for freeform launch bounds (activity / virtual display). */
        val activityDisplayWidth: Int,
        val activityDisplayHeight: Int,
        /** [android.view.Display.getDisplayId] for the app / virtual display. */
        val activityDisplayId: Int,
        /**
         * When true, MainScreen is laid out at full display size and clipped to the overlay
         * window (viewport crop). When false, MainScreen fills/shrinks to the overlay size.
         */
        val overlayCrop: Boolean = false,
        /**
         * Explicit page selected by the launcher widget for this companion session.
         * Theme activation may still update the persisted window-mode page underneath it.
         * A manual overlay swipe clears this pin.
         */
        val pinnedOverlayPage: Int? = null,
    )

    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state.asStateFlow()

    val isActive: Boolean
        get() = _state.value != null

    val isOverlayCrop: Boolean
        get() = _state.value?.overlayCrop == true

    fun companionPackage(): String? = _state.value?.packageName

    fun isActiveFor(packageName: String): Boolean {
        val pkg = packageName.trim()
        if (pkg.isEmpty()) return false
        return _state.value?.packageName == pkg
    }

    fun set(
        packageName: String,
        side: FreeformLaunchSide,
        percent: Int,
        activityDisplayWidth: Int,
        activityDisplayHeight: Int,
        activityDisplayId: Int,
        overlayCrop: Boolean = false,
        pinnedOverlayPage: Int? = null,
    ) {
        val pkg = packageName.trim()
        if (pkg.isEmpty()) {
            clear()
            return
        }
        _state.value = State(
            packageName = pkg,
            side = side,
            percent = FreeformLaunchBounds.normalizePercent(percent),
            activityDisplayWidth = activityDisplayWidth.coerceAtLeast(1),
            activityDisplayHeight = activityDisplayHeight.coerceAtLeast(1),
            activityDisplayId = activityDisplayId,
            overlayCrop = overlayCrop,
            pinnedOverlayPage = pinnedOverlayPage?.takeIf { it > 0 },
        )
    }

    fun clearPinnedOverlayPage() {
        val current = _state.value ?: return
        if (current.pinnedOverlayPage != null) {
            _state.value = current.copy(pinnedOverlayPage = null)
        }
    }

    fun clear() {
        _state.value = null
    }
}
