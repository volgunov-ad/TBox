package vad.dashing.tbox.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

private const val LAYOUT_GRID_LINE_ALPHA = 0.22f

/**
 * Semi-transparent horizontal and vertical guide lines for main-screen layout.
 * Drawn after content so lines stay visible over wallpaper/panels; does not affect hit-testing.
 */
internal fun Modifier.mainScreenLayoutGrid(
    enabled: Boolean,
    stepPx: Float,
    lineColor: Color,
): Modifier {
    if (!enabled || stepPx < 1f) return this
    val color = lineColor.copy(alpha = LAYOUT_GRID_LINE_ALPHA)
    return this.drawWithContent {
        drawContent()
        var x = stepPx
        while (x < size.width) {
            drawLine(
                color = color,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1f,
            )
            x += stepPx
        }
        var y = stepPx
        while (y < size.height) {
            drawLine(
                color = color,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f,
            )
            y += stepPx
        }
    }
}
