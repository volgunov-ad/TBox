package vad.dashing.tbox.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

internal fun resizeHandleOffsetForDimension(dimension: Float): Float {
    return when {
        dimension <= 60f -> 30f
        dimension <= 100f -> 50f
        else -> 60f
    }
}

internal fun isInResizeHandleArea(offset: Offset, width: Float, height: Float): Boolean {
    val resizeOffsetX = resizeHandleOffsetForDimension(width)
    val resizeOffsetY = resizeHandleOffsetForDimension(height)
    return offset.x > width - resizeOffsetX && offset.y > height - resizeOffsetY
}

internal fun resizeHandleAreaTopLeft(width: Float, height: Float): Offset {
    val resizeOffsetX = resizeHandleOffsetForDimension(width)
    val resizeOffsetY = resizeHandleOffsetForDimension(height)
    return Offset(
        x = width - resizeOffsetX,
        y = height - resizeOffsetY
    )
}

internal fun resizeHandleAreaSize(width: Float, height: Float): Size {
    return Size(
        width = resizeHandleOffsetForDimension(width),
        height = resizeHandleOffsetForDimension(height)
    )
}
