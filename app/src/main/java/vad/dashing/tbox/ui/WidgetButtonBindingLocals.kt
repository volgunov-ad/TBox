package vad.dashing.tbox.ui

import androidx.compose.runtime.staticCompositionLocalOf
import vad.dashing.tbox.WidgetButtonBinding

/**
 * Per-tile physical-button binding while the tile is composed.
 * Null = no binding or panel not eligible (collapsed / wrong surface).
 */
data class WidgetButtonBindingHost(
    val binding: WidgetButtonBinding?,
    val active: Boolean,
)

val LocalWidgetButtonBindingHost = staticCompositionLocalOf {
    WidgetButtonBindingHost(binding = null, active = false)
}
