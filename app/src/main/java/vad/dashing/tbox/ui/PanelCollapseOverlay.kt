package vad.dashing.tbox.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.isOutOfBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
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
    collapseOnStripDoubleTap: Boolean,
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
                // Strip is flush with the panel's outer edge; the extra touch-zone
                // thickness (touchZoneDp - stripThicknessDp) grows inward, toward the content.
                Box(
                    modifier = Modifier
                        .align(zoneAlignment)
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

/** Aligns the visible strip on the outer edge of the collapsed panel (the edge it shrank toward). */
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
