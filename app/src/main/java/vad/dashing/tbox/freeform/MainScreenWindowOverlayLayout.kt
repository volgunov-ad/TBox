package vad.dashing.tbox.freeform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vad.dashing.tbox.MainScreenWindowModeGeometry

/**
 * Layout hints for [vad.dashing.tbox.ui.MainScreenWindowOverlayUI]: whether to crop a
 * full-display MainScreen into the overlay window, and the full canvas + origin of the
 * visible viewport in that canvas.
 */
object MainScreenWindowOverlayLayout {
    data class State(
        /** Crop full MainScreen into the overlay; false = fill/shrink to overlay size. */
        val cropEnabled: Boolean = false,
        /** Full main-screen canvas width (overlay WM / activity display space), px. */
        val fullWidthPx: Int = 1,
        /** Full main-screen canvas height, px. */
        val fullHeightPx: Int = 1,
        /** Left edge of the overlay viewport in full-canvas coordinates. */
        val originXPx: Int = 0,
        /** Top edge of the overlay viewport in full-canvas coordinates. */
        val originYPx: Int = 0,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun update(
        cropEnabled: Boolean,
        fullWidthPx: Int,
        fullHeightPx: Int,
        geometry: MainScreenWindowModeGeometry,
    ) {
        _state.value = State(
            cropEnabled = cropEnabled,
            fullWidthPx = fullWidthPx.coerceAtLeast(1),
            fullHeightPx = fullHeightPx.coerceAtLeast(1),
            originXPx = geometry.startX.coerceAtLeast(0),
            originYPx = geometry.startY.coerceAtLeast(0),
        )
    }

    fun clear() {
        _state.value = State()
    }

    /**
     * Content offset so that full-canvas pixel ([originXPx], [originYPx]) maps to overlay (0, 0).
     */
    fun contentOffsetX(state: State): Int = -state.originXPx

    fun contentOffsetY(state: State): Int = -state.originYPx
}
