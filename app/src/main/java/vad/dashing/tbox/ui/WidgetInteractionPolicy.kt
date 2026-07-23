package vad.dashing.tbox.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset

internal enum class DashboardWidgetInteractionMode {
    STANDARD,
    EDIT
}

internal fun interface WidgetHitExclusion {
    fun contains(offset: Offset, width: Float, height: Float): Boolean
}

internal data class DashboardWidgetInteractionPolicy(
    val mode: DashboardWidgetInteractionMode = DashboardWidgetInteractionMode.STANDARD,
    val exclusions: List<WidgetHitExclusion> = emptyList()
) {
    fun isActionAllowed(offset: Offset, width: Float, height: Float): Boolean {
        return exclusions.none { exclusion ->
            exclusion.contains(offset, width, height)
        }
    }
}

internal val ResizeHandleWidgetHitExclusion = WidgetHitExclusion { offset, width, height ->
    isInResizeHandleArea(offset, width, height)
}

internal val LocalDashboardWidgetInteractionPolicy =
    staticCompositionLocalOf { DashboardWidgetInteractionPolicy() }

/**
 * Notifies the hosting panel that a tile was single- or double-tapped
 * (card surface or an inner control). Used for collapse-after-tap.
 * Default is a no-op (e.g. main dashboard tab without panel collapse).
 */
internal val LocalNotifyPanelTileTap =
    staticCompositionLocalOf { {} }
