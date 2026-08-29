package vad.dashing.tbox.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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
    touchZoneThicknessDp: Int,
    stripColor: Color,
    stripExpandedColor: Color,
    collapseOnStripTap: Boolean,
    isEditMode: Boolean,
    onCollapsedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    if (edge == PanelCollapseEdge.NONE) {
        Box(modifier = modifier, content = content)
        return
    }

    val touchZoneDp = touchZoneThicknessDp.coerceAtLeast(stripThicknessDp)
    val gestureModifier = if (isEditMode) {
        Modifier
    } else {
        Modifier.pointerInput(edge, collapsed, collapseOnStripTap) {
            coroutineScope {
                if (collapseOnStripTap) {
                    launch {
                        detectTapGestures {
                            onCollapsedChange(!collapsed)
                        }
                    }
                }
                launch {
                    var distance = 0f
                    detectDragGestures(
                        onDragStart = { distance = 0f },
                        onDrag = { change, dragAmount ->
                            val towardCollapsedRest = when (edge) {
                                PanelCollapseEdge.BOTTOM -> -dragAmount.y
                                PanelCollapseEdge.TOP -> dragAmount.y
                                PanelCollapseEdge.RIGHT -> -dragAmount.x
                                PanelCollapseEdge.LEFT -> dragAmount.x
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
            }
        }
    }

    val zoneAlignment = collapseZoneAlignment(edge)
    val stripOuterAlignment = collapseStripOuterAlignment(edge)

    Box(modifier = modifier) {
        if (collapsed) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .align(stripOuterAlignment)
                        .then(collapseStripSizeModifier(edge, stripThicknessDp))
                        .background(stripColor),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(gestureModifier),
                )
            }
        } else {
            content()
            Box(
                modifier = Modifier
                    .align(zoneAlignment)
                    .then(collapseTouchZoneSizeModifier(edge, touchZoneDp))
                    .then(gestureModifier),
            ) {
                Box(
                    modifier = Modifier
                        .align(stripOuterAlignment)
                        .then(collapseStripSizeModifier(edge, stripThicknessDp))
                        .background(stripExpandedColor),
                )
            }
        }
    }
}

private fun collapseZoneAlignment(edge: PanelCollapseEdge): Alignment =
    when (edge) {
        PanelCollapseEdge.BOTTOM -> Alignment.BottomCenter
        PanelCollapseEdge.TOP -> Alignment.TopCenter
        PanelCollapseEdge.RIGHT -> Alignment.CenterEnd
        PanelCollapseEdge.LEFT -> Alignment.CenterStart
        PanelCollapseEdge.NONE -> Alignment.Center
    }

/** Aligns the visible strip on the outer edge of the collapse zone / collapsed panel. */
private fun collapseStripOuterAlignment(edge: PanelCollapseEdge): Alignment =
    when (edge) {
        PanelCollapseEdge.BOTTOM -> Alignment.TopCenter
        PanelCollapseEdge.TOP -> Alignment.BottomCenter
        PanelCollapseEdge.RIGHT -> Alignment.CenterStart
        PanelCollapseEdge.LEFT -> Alignment.CenterEnd
        PanelCollapseEdge.NONE -> Alignment.Center
    }

private fun collapseTouchZoneSizeModifier(edge: PanelCollapseEdge, touchZoneDp: Int): Modifier =
    when (edge) {
        PanelCollapseEdge.TOP, PanelCollapseEdge.BOTTOM ->
            Modifier.fillMaxWidth().height(touchZoneDp.dp)
        PanelCollapseEdge.LEFT, PanelCollapseEdge.RIGHT ->
            Modifier.fillMaxHeight().width(touchZoneDp.dp)
        PanelCollapseEdge.NONE -> Modifier
    }

private fun collapseStripSizeModifier(edge: PanelCollapseEdge, stripThicknessDp: Int): Modifier =
    when (edge) {
        PanelCollapseEdge.TOP, PanelCollapseEdge.BOTTOM ->
            Modifier.fillMaxWidth().height(stripThicknessDp.dp)
        PanelCollapseEdge.LEFT, PanelCollapseEdge.RIGHT ->
            Modifier.fillMaxHeight().width(stripThicknessDp.dp)
        PanelCollapseEdge.NONE -> Modifier
    }
