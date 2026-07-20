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

/** Opaque Material Grey 400. */
const val DEFAULT_PANEL_COLLAPSE_STRIP_COLOR_LIGHT = 0xFFBDBDBD.toInt()

/** Opaque Material Grey 700. */
const val DEFAULT_PANEL_COLLAPSE_STRIP_COLOR_DARK = 0xFF616161.toInt()

const val DEFAULT_PANEL_COLLAPSE_ON_TILE_TAP = false

/** Shared collapse/expand animation length for main-screen and floating panels. */
const val PANEL_COLLAPSE_ANIMATION_MS = 180

fun normalizePanelCollapseStripThicknessDp(raw: Int): Int =
    raw.coerceIn(MIN_PANEL_COLLAPSE_STRIP_THICKNESS_DP, MAX_PANEL_COLLAPSE_STRIP_THICKNESS_DP)

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
