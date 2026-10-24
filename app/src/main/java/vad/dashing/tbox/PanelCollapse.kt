package vad.dashing.tbox

import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Which edge hosts the swipe-to-collapse zone when the panel is expanded.
 * When collapsed, the strip sits on the **opposite** edge (panel shrinks toward that opposite edge).
 *
 * Example: [BOTTOM] — swipe zone at the bottom; after collapse the strip remains at the top.
 */
enum class PanelCollapseEdge(val storageValue: String) {
    NONE("none"),
    LEFT("left"),
    RIGHT("right"),
    TOP("top"),
    BOTTOM("bottom");

    companion object {
        val OPTIONS: List<PanelCollapseEdge> = entries

        fun fromStorage(raw: String?): PanelCollapseEdge {
            val key = raw?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.storageValue == key } ?: NONE
        }
    }
}

const val DEFAULT_PANEL_COLLAPSE_STRIP_THICKNESS_DP = 32
const val MIN_PANEL_COLLAPSE_STRIP_THICKNESS_DP = 8
const val MAX_PANEL_COLLAPSE_STRIP_THICKNESS_DP = 64

/** Max touch-target thickness along the collapse edge (visual strip may be thinner). */
const val MAX_PANEL_COLLAPSE_TOUCH_ZONE_THICKNESS_DP = 128

const val DEFAULT_PANEL_COLLAPSE_ON_STRIP_TAP = false

/** Double tap on the strip / touch zone toggles the panel; independent of the single-tap option. */
const val DEFAULT_PANEL_COLLAPSE_ON_STRIP_DOUBLE_TAP = false

/** Opaque Material Grey 400. */
const val DEFAULT_PANEL_COLLAPSE_STRIP_COLOR_LIGHT = 0xFFBDBDBD.toInt()

/** Opaque Material Grey 700. */
const val DEFAULT_PANEL_COLLAPSE_STRIP_COLOR_DARK = 0xFF616161.toInt()

/**
 * Default expanded swipe-zone color: same RGB as collapsed strip with ~20% alpha
 * (previous hard-coded visual).
 */
const val DEFAULT_PANEL_COLLAPSE_STRIP_EXPANDED_COLOR_LIGHT = 0x33BDBDBD.toInt()

const val DEFAULT_PANEL_COLLAPSE_STRIP_EXPANDED_COLOR_DARK = 0x33616161.toInt()

const val DEFAULT_PANEL_COLLAPSE_ON_TILE_TAP = false

/** Delay before auto-collapse after a tile tap (seconds). */
const val DEFAULT_PANEL_COLLAPSE_ON_TILE_TAP_DELAY_SEC = 1
const val MIN_PANEL_COLLAPSE_ON_TILE_TAP_DELAY_SEC = 0
const val MAX_PANEL_COLLAPSE_ON_TILE_TAP_DELAY_SEC = 10

/**
 * Visible strip length along the collapse edge, as percent of that edge.
 * Default 100% = full edge (legacy panels).
 */
const val DEFAULT_PANEL_COLLAPSE_STRIP_LENGTH_PERCENT = 100
const val MIN_PANEL_COLLAPSE_STRIP_LENGTH_PERCENT = 1
const val MAX_PANEL_COLLAPSE_STRIP_LENGTH_PERCENT = 100

/** Shift of the strip from the start of the edge (top for L/R, left for T/B), percent. */
const val DEFAULT_PANEL_COLLAPSE_STRIP_POSITION_PERCENT = 0

/**
 * When false (default), the touch zone spans the full edge; a shorter strip is visual-only.
 * When true, the touch zone matches the strip length and position for the current state.
 */
const val DEFAULT_PANEL_COLLAPSE_TOUCH_ZONE_MATCH_STRIP = false

/** Shared collapse/expand animation length for main-screen and floating panels. */
const val PANEL_COLLAPSE_ANIMATION_MS = 180

fun normalizePanelCollapseStripThicknessDp(raw: Int): Int =
    raw.coerceIn(MIN_PANEL_COLLAPSE_STRIP_THICKNESS_DP, MAX_PANEL_COLLAPSE_STRIP_THICKNESS_DP)

fun normalizePanelCollapseTouchZoneThicknessDp(raw: Int, stripThicknessDp: Int): Int {
    val strip = normalizePanelCollapseStripThicknessDp(stripThicknessDp)
    val minTouch = maxOf(MIN_PANEL_COLLAPSE_STRIP_THICKNESS_DP, strip)
    return raw.coerceIn(minTouch, MAX_PANEL_COLLAPSE_TOUCH_ZONE_THICKNESS_DP)
}

fun normalizePanelCollapseOnTileTapDelaySec(raw: Int): Int =
    raw.coerceIn(MIN_PANEL_COLLAPSE_ON_TILE_TAP_DELAY_SEC, MAX_PANEL_COLLAPSE_ON_TILE_TAP_DELAY_SEC)

fun normalizePanelCollapseStripLengthPercent(raw: Int): Int =
    raw.coerceIn(MIN_PANEL_COLLAPSE_STRIP_LENGTH_PERCENT, MAX_PANEL_COLLAPSE_STRIP_LENGTH_PERCENT)

/**
 * Clamps position so the strip never extends past the edge:
 * `offset = min(offset, 100 - length)`.
 */
fun normalizePanelCollapseStripPositionPercent(raw: Int, lengthPercent: Int): Int {
    val length = normalizePanelCollapseStripLengthPercent(lengthPercent)
    return raw.coerceIn(0, 100 - length)
}

/** Normalized along-edge length + position for one collapse state (collapsed or expanded). */
data class PanelCollapseAlongEdgeSegment(
    val lengthPercent: Int,
    val positionPercent: Int,
) {
    val isFullEdge: Boolean get() = lengthPercent >= MAX_PANEL_COLLAPSE_STRIP_LENGTH_PERCENT

    companion object {
        val FULL = PanelCollapseAlongEdgeSegment(
            lengthPercent = DEFAULT_PANEL_COLLAPSE_STRIP_LENGTH_PERCENT,
            positionPercent = DEFAULT_PANEL_COLLAPSE_STRIP_POSITION_PERCENT,
        )

        fun normalize(lengthPercent: Int, positionPercent: Int): PanelCollapseAlongEdgeSegment {
            val length = normalizePanelCollapseStripLengthPercent(lengthPercent)
            return PanelCollapseAlongEdgeSegment(
                lengthPercent = length,
                positionPercent = normalizePanelCollapseStripPositionPercent(positionPercent, length),
            )
        }
    }
}

/** Pixel layout of a panel (overlay or main-screen absolute box). */
data class PanelPxBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) {
    fun coercedPositive(): PanelPxBounds =
        copy(width = width.coerceAtLeast(1), height = height.coerceAtLeast(1))
}

/**
 * Expanded panel bounds → collapsed strip bounds.
 * The strip keeps the full length along the chosen edge and [thicknessPx] across it,
 * anchored on the edge **opposite** to [edge].
 */
fun collapsedPanelBounds(
    expanded: PanelPxBounds,
    edge: PanelCollapseEdge,
    thicknessPx: Int,
): PanelPxBounds {
    val e = expanded.coercedPositive()
    if (edge == PanelCollapseEdge.NONE) return e
    val maxAlong = when (edge) {
        PanelCollapseEdge.TOP, PanelCollapseEdge.BOTTOM -> e.height
        PanelCollapseEdge.LEFT, PanelCollapseEdge.RIGHT -> e.width
        PanelCollapseEdge.NONE -> return e
    }
    val t = thicknessPx.coerceIn(1, maxAlong)
    return when (edge) {
        // Swipe at bottom → shrink toward top; strip at top.
        PanelCollapseEdge.BOTTOM -> PanelPxBounds(e.x, e.y, e.width, t)
        // Swipe at top → shrink toward bottom; strip at bottom.
        PanelCollapseEdge.TOP -> PanelPxBounds(e.x, e.y + e.height - t, e.width, t)
        // Swipe at right → shrink toward left; strip at left.
        PanelCollapseEdge.RIGHT -> PanelPxBounds(e.x, e.y, t, e.height)
        // Swipe at left → shrink toward right; strip at right.
        PanelCollapseEdge.LEFT -> PanelPxBounds(e.x + e.width - t, e.y, t, e.height)
        PanelCollapseEdge.NONE -> e
    }
}

/**
 * Window / hit-test bounds when collapsed and the touch zone is thicker than the visible strip.
 * The extra thickness extends **inward** from the strip (toward where the expanded panel was).
 * Along-edge length stays the full panel edge (floating overlay window is not shrunk to strip length).
 */
fun collapsedPanelInteractionBounds(
    expanded: PanelPxBounds,
    edge: PanelCollapseEdge,
    stripThicknessPx: Int,
    touchZoneThicknessPx: Int,
): PanelPxBounds {
    val visual = collapsedPanelBounds(expanded, edge, stripThicknessPx)
    val touch = touchZoneThicknessPx.coerceAtLeast(stripThicknessPx)
    if (touch <= stripThicknessPx) return visual
    val extra = touch - stripThicknessPx
    return when (edge) {
        PanelCollapseEdge.BOTTOM -> visual.copy(height = touch)
        PanelCollapseEdge.TOP -> visual.copy(y = visual.y - extra, height = touch)
        PanelCollapseEdge.LEFT -> visual.copy(x = visual.x - extra, width = touch)
        PanelCollapseEdge.RIGHT -> visual.copy(width = touch)
        PanelCollapseEdge.NONE -> visual
    }
}

/**
 * Pixel rect of the visible strip (or matching along-edge touch segment) inside [container].
 *
 * - [thicknessPx] is across the edge (into the panel).
 * - [lengthPercent] / [positionPercent] are along the edge (start = top for left/right,
 *   left for top/bottom); position is clamped via [normalizePanelCollapseStripPositionPercent].
 * - When [collapsed] is true, the strip sits on the edge **opposite** to [edge]
 *   (same convention as [collapsedPanelBounds]).
 */
fun panelCollapseStripBoundsInContainer(
    container: PanelPxBounds,
    edge: PanelCollapseEdge,
    thicknessPx: Int,
    lengthPercent: Int,
    positionPercent: Int,
    collapsed: Boolean,
): PanelPxBounds {
    val c = container.coercedPositive()
    if (edge == PanelCollapseEdge.NONE) return c
    val segment = PanelCollapseAlongEdgeSegment.normalize(lengthPercent, positionPercent)
    val horizontalStrip = edge == PanelCollapseEdge.TOP || edge == PanelCollapseEdge.BOTTOM
    val alongFull = if (horizontalStrip) c.width else c.height
    val acrossFull = if (horizontalStrip) c.height else c.width
    val lengthPx = ((alongFull * segment.lengthPercent) / 100f).roundToInt().coerceIn(1, alongFull)
    val offsetPx = ((alongFull * segment.positionPercent) / 100f).roundToInt()
        .coerceIn(0, (alongFull - lengthPx).coerceAtLeast(0))
    val t = thicknessPx.coerceIn(1, acrossFull)
    return when (edge) {
        PanelCollapseEdge.BOTTOM -> if (collapsed) {
            PanelPxBounds(c.x + offsetPx, c.y, lengthPx, t)
        } else {
            PanelPxBounds(c.x + offsetPx, c.y + c.height - t, lengthPx, t)
        }
        PanelCollapseEdge.TOP -> if (collapsed) {
            PanelPxBounds(c.x + offsetPx, c.y + c.height - t, lengthPx, t)
        } else {
            PanelPxBounds(c.x + offsetPx, c.y, lengthPx, t)
        }
        PanelCollapseEdge.RIGHT -> if (collapsed) {
            PanelPxBounds(c.x, c.y + offsetPx, t, lengthPx)
        } else {
            PanelPxBounds(c.x + c.width - t, c.y + offsetPx, t, lengthPx)
        }
        PanelCollapseEdge.LEFT -> if (collapsed) {
            PanelPxBounds(c.x + c.width - t, c.y + offsetPx, t, lengthPx)
        } else {
            PanelPxBounds(c.x, c.y + offsetPx, t, lengthPx)
        }
        PanelCollapseEdge.NONE -> c
    }
}

/**
 * Interpolates between expanded and collapsed bounds for [fraction] in `0f..1f`
 * (`0` = expanded, `1` = fully collapsed).
 */
fun lerpPanelBounds(
    expanded: PanelPxBounds,
    collapsed: PanelPxBounds,
    fraction: Float,
): PanelPxBounds {
    val f = fraction.coerceIn(0f, 1f)
    return PanelPxBounds(
        x = (expanded.x + (collapsed.x - expanded.x) * f).roundToInt(),
        y = (expanded.y + (collapsed.y - expanded.y) * f).roundToInt(),
        width = (expanded.width + (collapsed.width - expanded.width) * f).roundToInt().coerceAtLeast(1),
        height = (expanded.height + (collapsed.height - expanded.height) * f).roundToInt().coerceAtLeast(1),
    )
}

/**
 * Theme-independent map of panel id → collapsed flag, stored separately in DataStore.
 * Survives theme switches when the same panel ids remain.
 */
object PanelCollapseStates {
    const val DATASTORE_KEY = "panel_collapse_states"

    fun parse(json: String?): Map<String, Boolean> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val obj = JSONObject(json)
            buildMap {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val id = keys.next()
                    if (id.isBlank()) continue
                    put(id, obj.optBoolean(id, false))
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun serialize(states: Map<String, Boolean>): String {
        if (states.isEmpty()) return ""
        val obj = JSONObject()
        states.forEach { (id, collapsed) ->
            if (id.isNotBlank()) obj.put(id, collapsed)
        }
        return obj.toString()
    }

    fun isCollapsed(states: Map<String, Boolean>, panelId: String): Boolean =
        states[panelId] == true

    fun withCollapsed(
        states: Map<String, Boolean>,
        panelId: String,
        collapsed: Boolean,
    ): Map<String, Boolean> {
        if (panelId.isBlank()) return states
        return if (collapsed) {
            states + (panelId to true)
        } else {
            // Keep explicit false only if previously tracked; prefer omitting expanded.
            if (!states.containsKey(panelId)) states
            else states - panelId
        }
    }
}

fun MainScreenPanelConfig.collapseEdgeOrNone(): PanelCollapseEdge =
    PanelCollapseEdge.fromStorage(collapseEdge)

fun FloatingDashboardConfig.collapseEdgeOrNone(): PanelCollapseEdge =
    PanelCollapseEdge.fromStorage(collapseEdge)

fun MainScreenPanelConfig.resolveStripColor(currentTheme: Int): Int =
    if (currentTheme == 2) collapseStripColorDark else collapseStripColorLight

fun FloatingDashboardConfig.resolveStripColor(currentTheme: Int): Int =
    if (currentTheme == 2) collapseStripColorDark else collapseStripColorLight

fun MainScreenPanelConfig.resolveStripExpandedColor(currentTheme: Int): Int =
    if (currentTheme == 2) collapseStripExpandedColorDark else collapseStripExpandedColorLight

fun FloatingDashboardConfig.resolveStripExpandedColor(currentTheme: Int): Int =
    if (currentTheme == 2) collapseStripExpandedColorDark else collapseStripExpandedColorLight
