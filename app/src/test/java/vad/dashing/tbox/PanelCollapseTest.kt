package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelCollapseTest {

    @Test
    fun collapsedBounds_bottom_shrinksTowardTop() {
        val expanded = PanelPxBounds(x = 10, y = 20, width = 200, height = 100)
        val collapsed = collapsedPanelBounds(expanded, PanelCollapseEdge.BOTTOM, thicknessPx = 32)
        assertEquals(10, collapsed.x)
        assertEquals(20, collapsed.y)
        assertEquals(200, collapsed.width)
        assertEquals(32, collapsed.height)
    }

    @Test
    fun collapsedBounds_top_shrinksTowardBottom() {
        val expanded = PanelPxBounds(x = 10, y = 20, width = 200, height = 100)
        val collapsed = collapsedPanelBounds(expanded, PanelCollapseEdge.TOP, thicknessPx = 32)
        assertEquals(10, collapsed.x)
        assertEquals(20 + 100 - 32, collapsed.y)
        assertEquals(200, collapsed.width)
        assertEquals(32, collapsed.height)
    }

    @Test
    fun collapsedBounds_left_shrinksTowardRight() {
        val expanded = PanelPxBounds(x = 10, y = 20, width = 200, height = 100)
        val collapsed = collapsedPanelBounds(expanded, PanelCollapseEdge.LEFT, thicknessPx = 32)
        assertEquals(10 + 200 - 32, collapsed.x)
        assertEquals(20, collapsed.y)
        assertEquals(32, collapsed.width)
        assertEquals(100, collapsed.height)
    }

    @Test
    fun collapsedBounds_right_shrinksTowardLeft() {
        val expanded = PanelPxBounds(x = 10, y = 20, width = 200, height = 100)
        val collapsed = collapsedPanelBounds(expanded, PanelCollapseEdge.RIGHT, thicknessPx = 32)
        assertEquals(10, collapsed.x)
        assertEquals(20, collapsed.y)
        assertEquals(32, collapsed.width)
        assertEquals(100, collapsed.height)
    }

    @Test
    fun lerpPanelBounds_midpoint() {
        val expanded = PanelPxBounds(0, 0, 100, 100)
        val collapsed = PanelPxBounds(0, 0, 100, 20)
        val mid = lerpPanelBounds(expanded, collapsed, 0.5f)
        assertEquals(0, mid.x)
        assertEquals(0, mid.y)
        assertEquals(100, mid.width)
        assertEquals(60, mid.height)
    }

    @Test
    fun panelCollapseStates_withCollapsed_tracksAndOmitsExpanded() {
        val withCollapsed = PanelCollapseStates.withCollapsed(emptyMap(), "p1", true)
        assertTrue(PanelCollapseStates.isCollapsed(withCollapsed, "p1"))

        val expanded = PanelCollapseStates.withCollapsed(withCollapsed, "p1", false)
        assertFalse(PanelCollapseStates.isCollapsed(expanded, "p1"))
        assertFalse(expanded.containsKey("p1"))
    }

    @Test
    fun normalizeThickness_clamps() {
        assertEquals(DEFAULT_PANEL_COLLAPSE_STRIP_THICKNESS_DP, normalizePanelCollapseStripThicknessDp(32))
        assertEquals(MIN_PANEL_COLLAPSE_STRIP_THICKNESS_DP, normalizePanelCollapseStripThicknessDp(1))
        assertEquals(MAX_PANEL_COLLAPSE_STRIP_THICKNESS_DP, normalizePanelCollapseStripThicknessDp(999))
    }

    @Test
    fun normalizeTouchZone_clampsToStripAndMax() {
        assertEquals(32, normalizePanelCollapseTouchZoneThicknessDp(32, 32))
        assertEquals(32, normalizePanelCollapseTouchZoneThicknessDp(16, 32))
        assertEquals(64, normalizePanelCollapseTouchZoneThicknessDp(64, 32))
        assertEquals(
            MAX_PANEL_COLLAPSE_TOUCH_ZONE_THICKNESS_DP,
            normalizePanelCollapseTouchZoneThicknessDp(999, 32),
        )
    }

    @Test
    fun collapsedInteractionBounds_bottom_extendsInward() {
        val expanded = PanelPxBounds(x = 10, y = 20, width = 200, height = 100)
        val interaction = collapsedPanelInteractionBounds(
            expanded = expanded,
            edge = PanelCollapseEdge.BOTTOM,
            stripThicknessPx = 32,
            touchZoneThicknessPx = 64,
        )
        assertEquals(10, interaction.x)
        assertEquals(20, interaction.y)
        assertEquals(200, interaction.width)
        assertEquals(64, interaction.height)
    }

    @Test
    fun collapsedInteractionBounds_matchesStripWhenEqual() {
        val expanded = PanelPxBounds(x = 0, y = 0, width = 100, height = 80)
        val visual = collapsedPanelBounds(expanded, PanelCollapseEdge.LEFT, 24)
        val interaction = collapsedPanelInteractionBounds(
            expanded = expanded,
            edge = PanelCollapseEdge.LEFT,
            stripThicknessPx = 24,
            touchZoneThicknessPx = 24,
        )
        assertEquals(visual, interaction)
    }

    @Test
    fun edgeFromStorage_defaultsToNone() {
        assertEquals(PanelCollapseEdge.NONE, PanelCollapseEdge.fromStorage(null))
        assertEquals(PanelCollapseEdge.NONE, PanelCollapseEdge.fromStorage(""))
        assertEquals(PanelCollapseEdge.BOTTOM, PanelCollapseEdge.fromStorage("bottom"))
    }
}
