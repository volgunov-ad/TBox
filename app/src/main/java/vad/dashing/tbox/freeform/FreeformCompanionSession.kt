package vad.dashing.tbox.freeform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single freeform companion app session (one package beside/behind the main-screen host).
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
         * When true, MainActivity hosts MainScreen fullscreen under freeform (no
         * TYPE_APPLICATION_OVERLAY). When false, complementary overlay beside the companion.
         */
        val overlayBehind: Boolean = false,
    )

    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state.asStateFlow()

    val isActive: Boolean
        get() = _state.value != null

    /** True when window mode uses MainActivity behind freeform (not the side overlay). */
    val isOverlayBehind: Boolean
        get() = _state.value?.overlayBehind == true

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
        overlayBehind: Boolean = false,
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
            overlayBehind = overlayBehind,
        )
    }

    fun clear() {
        _state.value = null
    }
}
