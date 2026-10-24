package vad.dashing.tbox.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.isOutOfBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt
import vad.dashing.tbox.DEFAULT_PANEL_COLLAPSE_STRIP_LENGTH_PERCENT
import vad.dashing.tbox.DEFAULT_PANEL_COLLAPSE_STRIP_POSITION_PERCENT
import vad.dashing.tbox.DEFAULT_PANEL_COLLAPSE_TOUCH_ZONE_MATCH_STRIP
import vad.dashing.tbox.PANEL_COLLAPSE_ANIMATION_MS
import vad.dashing.tbox.PanelCollapseAlongEdgeSegment
import vad.dashing.tbox.PanelCollapseEdge

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
    collapseOnStripDoubleTap: Boolean,
    isEditMode: Boolean,
    onCollapsedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    stripLengthPercent: Int = DEFAULT_PANEL_COLLAPSE_STRIP_LENGTH_PERCENT,
    stripPositionPercent: Int = DEFAULT_PANEL_COLLAPSE_STRIP_POSITION_PERCENT,
    touchZoneMatchStrip: Boolean = DEFAULT_PANEL_COLLAPSE_TOUCH_ZONE_MATCH_STRIP,
    content: @Composable BoxScope.() -> Unit,
) {
    if (edge == PanelCollapseEdge.NONE) {
        Box(modifier = modifier, content = content)
        return
    }

    val touchZoneDp = touchZoneThicknessDp.coerceAtLeast(stripThicknessDp)
    val alongEdge = PanelCollapseAlongEdgeSegment.normalize(stripLengthPercent, stripPositionPercent)
    val gestureModifier = if (isEditMode) {
        Modifier
    } else {
        Modifier.pointerInput(edge, collapsed, collapseOnStripTap, collapseOnStripDoubleTap) {
            detectCollapseStripGestures(
                edge = edge,
                collapsed = collapsed,
                collapseOnStripTap = collapseOnStripTap,
                collapseOnStripDoubleTap = collapseOnStripDoubleTap,
                onToggle = { onCollapsedChange(!collapsed) },
            )
        }
    }

    Box(modifier = modifier) {
        if (collapsed) {
            // Collapsed: only the strip color is drawn (no panel/tile underlay behind it).
            // Fully transparent stripColor → nothing visible; the touch zone still receives gestures.
            // Floating overlay window stays full-edge; only the drawn strip (and optional
            // matched touch zone) use length/position.
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val alongPx = collapseAlongEdgePx(edge, maxWidth, maxHeight, density)
                val segment = collapseAlongEdgeSegmentPx(alongPx, alongEdge)
                val lengthDp = with(density) { segment.lengthPx.toDp() }
                val offsetDp = with(density) { segment.offsetPx.toDp() }
                if (stripColor.alpha > 0f) {
                    CollapseAlongEdgeBox(
                        edge = edge,
                        collapsed = true,
                        thicknessDp = stripThicknessDp,
                        lengthDp = lengthDp,
                        offsetDp = offsetDp,
                        fullEdge = false,
                        modifier = Modifier.background(stripColor),
                    )
                }
                val touchMatchStrip = touchZoneMatchStrip && !alongEdge.isFullEdge
                if (touchMatchStrip) {
                    CollapseAlongEdgeBox(
                        edge = edge,
                        collapsed = true,
                        thicknessDp = touchZoneDp,
                        lengthDp = lengthDp,
                        offsetDp = offsetDp,
                        fullEdge = false,
                        modifier = gestureModifier,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(gestureModifier),
                    )
                }
            }
        } else {
            content()
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val alongPx = collapseAlongEdgePx(edge, maxWidth, maxHeight, density)
                val segment = collapseAlongEdgeSegmentPx(alongPx, alongEdge)
                val lengthDp = with(density) { segment.lengthPx.toDp() }
                val offsetDp = with(density) { segment.offsetPx.toDp() }
                val touchMatchStrip = touchZoneMatchStrip && !alongEdge.isFullEdge
                CollapseAlongEdgeBox(
                    edge = edge,
                    collapsed = false,
                    thicknessDp = touchZoneDp,
                    lengthDp = lengthDp,
                    offsetDp = offsetDp,
                    // Default: full edge; match mode: strip length+position.
                    fullEdge = !touchMatchStrip,
                    modifier = gestureModifier,
                ) {
                    // Strip is flush with the panel's outer edge; the extra touch-zone
                    // thickness (touchZoneDp - stripThicknessDp) grows inward, toward the content.
                    if (stripExpandedColor.alpha > 0f) {
                        val stripOffsetInParent = if (touchMatchStrip) 0.dp else offsetDp
                        CollapseAlongEdgeBox(
                            edge = edge,
                            collapsed = false,
                            thicknessDp = stripThicknessDp,
                            lengthDp = lengthDp,
                            offsetDp = stripOffsetInParent,
                            fullEdge = false,
                            modifier = Modifier.background(stripExpandedColor),
                        )
                    }
                }
            }
        }
    }
}

private data class CollapseAlongEdgeSegmentPx(
    val lengthPx: Int,
    val offsetPx: Int,
)

private fun collapseAlongEdgePx(
    edge: PanelCollapseEdge,
    maxWidth: Dp,
    maxHeight: Dp,
    density: Density,
): Int = with(density) {
    when (edge) {
        PanelCollapseEdge.TOP, PanelCollapseEdge.BOTTOM -> maxWidth.roundToPx()
        PanelCollapseEdge.LEFT, PanelCollapseEdge.RIGHT -> maxHeight.roundToPx()
        PanelCollapseEdge.NONE -> maxWidth.roundToPx()
    }.coerceAtLeast(1)
}

private fun collapseAlongEdgeSegmentPx(
    alongPx: Int,
    segment: PanelCollapseAlongEdgeSegment,
): CollapseAlongEdgeSegmentPx {
    val lengthPx = ((alongPx * segment.lengthPercent) / 100f).roundToInt().coerceIn(1, alongPx)
    val offsetPx = ((alongPx * segment.positionPercent) / 100f).roundToInt()
        .coerceIn(0, (alongPx - lengthPx).coerceAtLeast(0))
    return CollapseAlongEdgeSegmentPx(lengthPx = lengthPx, offsetPx = offsetPx)
}

/** Outer-edge alignment for the thickness axis (collapsed = opposite edge). */
private fun collapseZoneOuterAlignment(edge: PanelCollapseEdge, collapsed: Boolean): Alignment =
    when (edge) {
        PanelCollapseEdge.BOTTOM -> if (collapsed) Alignment.TopStart else Alignment.BottomStart
        PanelCollapseEdge.TOP -> if (collapsed) Alignment.BottomStart else Alignment.TopStart
        PanelCollapseEdge.RIGHT -> if (collapsed) Alignment.TopStart else Alignment.TopEnd
        PanelCollapseEdge.LEFT -> if (collapsed) Alignment.TopEnd else Alignment.TopStart
        PanelCollapseEdge.NONE -> Alignment.Center
    }

@Composable
private fun BoxScope.CollapseAlongEdgeBox(
    edge: PanelCollapseEdge,
    collapsed: Boolean,
    thicknessDp: Int,
    lengthDp: Dp,
    offsetDp: Dp,
    fullEdge: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val density = LocalDensity.current
    val offsetPx = with(density) { offsetDp.roundToPx() }
    Box(
        modifier = Modifier
            .align(collapseZoneOuterAlignment(edge, collapsed))
            .offset {
                if (fullEdge) {
                    IntOffset.Zero
                } else {
                    when (edge) {
                        PanelCollapseEdge.TOP, PanelCollapseEdge.BOTTOM -> IntOffset(offsetPx, 0)
                        PanelCollapseEdge.LEFT, PanelCollapseEdge.RIGHT -> IntOffset(0, offsetPx)
                        PanelCollapseEdge.NONE -> IntOffset.Zero
                    }
                }
            }
            .then(
                when (edge) {
                    PanelCollapseEdge.TOP, PanelCollapseEdge.BOTTOM ->
                        if (fullEdge) {
                            Modifier.fillMaxWidth().height(thicknessDp.dp)
                        } else {
                            Modifier.width(lengthDp).height(thicknessDp.dp)
                        }
                    PanelCollapseEdge.LEFT, PanelCollapseEdge.RIGHT ->
                        if (fullEdge) {
                            Modifier.fillMaxHeight().width(thicknessDp.dp)
                        } else {
                            Modifier.height(lengthDp).width(thicknessDp.dp)
                        }
                    PanelCollapseEdge.NONE -> Modifier
                },
            )
            .then(modifier),
        content = content,
    )
}

/**
 * Unified single-stream detector for the collapse strip / touch zone. Replaces the previous pair of
 * concurrent `detectTapGestures` + `detectDragGestures`, which fought over one pointer stream
 * (the tap detector cancelled after the drag detector consumed the first move, and the drag path
 * added its own extra 24 dp on top of the Compose touch slop — a "dead zone" for small movements).
 *
 * Behavior:
 * - swipe (always active): fires when the pointer travels [collapseSwipeThresholdPx] toward the
 *   opposite collapse state; movement past the touch slop is consumed;
 * - single tap ([collapseOnStripTap]): immediate, or delayed by the system double-tap timeout
 *   when double taps are also enabled;
 * - double tap ([collapseOnStripDoubleTap]): two taps within the system double-tap window.
 */
private suspend fun PointerInputScope.detectCollapseStripGestures(
    edge: PanelCollapseEdge,
    collapsed: Boolean,
    collapseOnStripTap: Boolean,
    collapseOnStripDoubleTap: Boolean,
    onToggle: () -> Unit,
) {
    val swipeThresholdPx = collapseSwipeThresholdPx(this)
    awaitEachGesture {
        val firstDown = awaitFirstDown(requireUnconsumed = false)
        val first = awaitCollapseStripPointerCompletion(firstDown, edge, collapsed)
        if (first is CollapseStripGesture.Tap && collapseOnStripDoubleTap) {
            val secondDown = awaitSecondTapDown(first.up)
            if (secondDown == null) {
                // No second tap within the window — fire the (delayed) single tap.
                if (collapseOnStripTap) onToggle()
            } else {
                val second = awaitCollapseStripPointerCompletion(secondDown, edge, collapsed)
                val secondIsToggle = when (second) {
                    is CollapseStripGesture.Tap -> true
                    is CollapseStripGesture.Drag -> second.distance >= swipeThresholdPx
                    CollapseStripGesture.Canceled -> false
                }
                if (secondIsToggle) onToggle()
            }
        } else {
            when (first) {
                is CollapseStripGesture.Tap -> if (collapseOnStripTap) onToggle()
                is CollapseStripGesture.Drag ->
                    if (first.distance >= swipeThresholdPx) onToggle()
                CollapseStripGesture.Canceled -> Unit
            }
        }
    }
}

/** Outcome of one pointer press tracked from [down] until it lifts or the gesture is canceled. */
private sealed interface CollapseStripGesture {
    data class Tap(val up: PointerInputChange) : CollapseStripGesture
    data class Drag(val distance: Float) : CollapseStripGesture
    object Canceled : CollapseStripGesture
}

private suspend fun AwaitPointerEventScope.awaitCollapseStripPointerCompletion(
    down: PointerInputChange,
    edge: PanelCollapseEdge,
    collapsed: Boolean,
): CollapseStripGesture {
    val slop = viewConfiguration.touchSlop
    var distance = 0f
    var travel = 0f
    var drag = false
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == down.id } ?: continue
        val delta = change.positionChangeIgnoreConsumed()
        // Accumulate for the whole press, including after the drag flip — the swipe threshold
        // must be measured against the full travel, not just up to the touch slop.
        distance += collapseToggleDelta(delta, edge, collapsed)
        if (!drag) {
            travel += delta.getDistance()
            if (abs(distance) > slop || travel > slop) {
                drag = true
            } else if (change.isConsumed ||
                change.isOutOfBounds(size, extendedTouchPadding)
            ) {
                // Another gesture detector stole this pointer or it left the zone — cancel the tap.
                return CollapseStripGesture.Canceled
            }
        }
        if (drag) {
            change.consume()
        }
        if (!change.pressed) {
            // System cancels arrive as a synthetic up with the down-change consumed. For drags the
            // up is consumed by ourselves above, so drag must be checked before isConsumed.
            return when {
                drag -> CollapseStripGesture.Drag(distance)
                change.isConsumed -> CollapseStripGesture.Canceled
                else -> CollapseStripGesture.Tap(change)
            }
        }
    }
}

/** Waits [ViewConfiguration.doubleTapTimeoutMillis] for a second press, mirroring Compose's
 *  `awaitSecondDown` (second tap must also be older than `doubleTapMinTimeMillis`). */
private suspend fun AwaitPointerEventScope.awaitSecondTapDown(
    firstUp: PointerInputChange,
): PointerInputChange? =
    withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
        val minUptime = firstUp.uptimeMillis + viewConfiguration.doubleTapMinTimeMillis
        var change: PointerInputChange
        do {
            change = awaitFirstDown(requireUnconsumed = false)
        } while (change.uptimeMillis < minUptime)
        change
    }

/** Signed movement along the collapse axis; positive = toward the opposite collapse state. */
private fun collapseToggleDelta(delta: Offset, edge: PanelCollapseEdge, collapsed: Boolean): Float {
    val towardCollapsedRest = when (edge) {
        PanelCollapseEdge.BOTTOM -> -delta.y
        PanelCollapseEdge.TOP -> delta.y
        PanelCollapseEdge.RIGHT -> -delta.x
        PanelCollapseEdge.LEFT -> delta.x
        PanelCollapseEdge.NONE -> 0f
    }
    return if (collapsed) -towardCollapsedRest else towardCollapsedRest
}
