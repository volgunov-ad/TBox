package vad.dashing.tbox.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import vad.dashing.tbox.PanelCollapseEdge
import vad.dashing.tbox.PANEL_COLLAPSE_ANIMATION_MS

internal data class PanelCollapseEdgeDropdownOption(
    val edge: PanelCollapseEdge,
    private val label: String,
) {
    override fun toString(): String = label
}

@Composable
internal fun rememberPanelCollapseProgress(collapsed: Boolean): State<Float> =
    animateFloatAsState(
        targetValue = if (collapsed) 1f else 0f,
        animationSpec = tween(durationMillis = PANEL_COLLAPSE_ANIMATION_MS),
        label = "panelCollapseProgress",
    )

internal fun collapseSwipeThresholdPx(density: Density): Float = with(density) { 24.dp.toPx() }

@Composable
internal fun CollapsiblePanelFrame(
    edge: PanelCollapseEdge,
    collapsed: Boolean,
    stripThicknessDp: Int,
    stripColor: Color,
    isEditMode: Boolean,
    onCollapsedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    if (edge == PanelCollapseEdge.NONE) {
        Box(modifier = modifier, content = content)
        return
    }

    val swipeModifier = if (isEditMode) Modifier else Modifier.pointerInput(edge, collapsed) {
        var distance = 0f
        detectDragGestures(
            onDragStart = { distance = 0f },
            onDrag = { change, dragAmount ->
                // Positive = toward the strip's resting edge after collapse (opposite to swipe zone).
                // Expand (collapsed): swipe inward from the strip — same sign as today.
                // Collapse (expanded): swipe the other way — toward where the panel shrinks.
                val towardCollapsedRest = when (edge) {
                    PanelCollapseEdge.BOTTOM -> -dragAmount.y // up (panel shrinks to top)
                    PanelCollapseEdge.TOP -> dragAmount.y // down (panel shrinks to bottom)
                    PanelCollapseEdge.RIGHT -> -dragAmount.x // left (panel shrinks to left)
                    PanelCollapseEdge.LEFT -> dragAmount.x // right (panel shrinks to right)
                    PanelCollapseEdge.NONE -> 0f
                }
                val amount = if (collapsed) -towardCollapsedRest else towardCollapsedRest
                distance += amount
                change.consume()
            },
            onDragEnd = {
                if (distance >= collapseSwipeThresholdPx(this@pointerInput)) {
                    onCollapsedChange(!collapsed)
                }
            },
        )
    }

    Box(modifier = modifier) {
        if (collapsed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(stripColor)
                    .then(swipeModifier),
            )
        } else {
            content()
            val zoneAlignment = when (edge) {
                PanelCollapseEdge.BOTTOM -> Alignment.BottomCenter
                PanelCollapseEdge.TOP -> Alignment.TopCenter
                PanelCollapseEdge.RIGHT -> Alignment.CenterEnd
                PanelCollapseEdge.LEFT -> Alignment.CenterStart
                PanelCollapseEdge.NONE -> Alignment.Center
            }
            val zoneSize = when (edge) {
                PanelCollapseEdge.TOP, PanelCollapseEdge.BOTTOM ->
                    Modifier
                        .fillMaxWidth()
                        .height(stripThicknessDp.dp)
                PanelCollapseEdge.LEFT, PanelCollapseEdge.RIGHT ->
                    Modifier
                        .fillMaxHeight()
                        .width(stripThicknessDp.dp)
                PanelCollapseEdge.NONE -> Modifier
            }
            Box(
                modifier = Modifier
                    .align(zoneAlignment)
                    .then(zoneSize)
                    .then(swipeModifier),
            )
        }
    }
}
