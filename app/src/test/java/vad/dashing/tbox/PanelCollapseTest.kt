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

    @Test
    fun normalizeLength_clampsTo1_100() {
        assertEquals(DEFAULT_PANEL_COLLAPSE_STRIP_LENGTH_PERCENT, normalizePanelCollapseStripLengthPercent(100))
        assertEquals(MIN_PANEL_COLLAPSE_STRIP_LENGTH_PERCENT, normalizePanelCollapseStripLengthPercent(0))
        assertEquals(MAX_PANEL_COLLAPSE_STRIP_LENGTH_PERCENT, normalizePanelCollapseStripLengthPercent(999))
        assertEquals(50, normalizePanelCollapseStripLengthPercent(50))
    }

    @Test
    fun normalizePosition_clampsTo100MinusLength() {
        assertEquals(0, normalizePanelCollapseStripPositionPercent(-5, 40))
        assertEquals(60, normalizePanelCollapseStripPositionPercent(99, 40))
        assertEquals(0, normalizePanelCollapseStripPositionPercent(50, 100))
        assertEquals(25, normalizePanelCollapseStripPositionPercent(25, 50))
    }

    @Test
    fun alongEdgeSegment_normalize_fullEdgeWhenLength100() {
        val segment = PanelCollapseAlongEdgeSegment.normalize(100, 40)
        assertEquals(100, segment.lengthPercent)
        assertEquals(0, segment.positionPercent)
        assertTrue(segment.isFullEdge)
    }

    @Test
    fun stripBoundsInContainer_bottomExpanded_shortStripFromLeft() {
        val container = PanelPxBounds(x = 10, y = 20, width = 200, height = 100)
        val strip = panelCollapseStripBoundsInContainer(
            container = container,
            edge = PanelCollapseEdge.BOTTOM,
            thicknessPx = 32,
            lengthPercent = 50,
            positionPercent = 25,
            collapsed = false,
        )
        assertEquals(10 + 50, strip.x) // 25% of 200
        assertEquals(20 + 100 - 32, strip.y)
        assertEquals(100, strip.width) // 50% of 200
        assertEquals(32, strip.height)
    }

    @Test
    fun stripBoundsInContainer_leftCollapsed_shortStripFromTop() {
        // Collapsed LEFT → strip on the right edge of the (collapsed) container.
        val container = PanelPxBounds(x = 100, y = 20, width = 32, height = 200)
        val strip = panelCollapseStripBoundsInContainer(
            container = container,
            edge = PanelCollapseEdge.LEFT,
            thicknessPx = 32,
            lengthPercent = 25,
            positionPercent = 50,
            collapsed = true,
        )
        // LEFT collapsed: strip at right of container (x + width - t).
        assertEquals(100, strip.x)
        assertEquals(20 + 100, strip.y) // 50% of 200
        assertEquals(32, strip.width)
        assertEquals(50, strip.height) // 25% of 200
    }

    @Test
    fun stripBoundsInContainer_length100_ignoresPosition() {
        val container = PanelPxBounds(0, 0, 100, 80)
        val strip = panelCollapseStripBoundsInContainer(
            container = container,
            edge = PanelCollapseEdge.TOP,
            thicknessPx = 16,
            lengthPercent = 100,
            positionPercent = 40,
            collapsed = false,
        )
        assertEquals(0, strip.x)
        assertEquals(0, strip.y)
        assertEquals(100, strip.width)
        assertEquals(16, strip.height)
    }

    @Test
    fun collapseAlongEdgeFields_defaultsPreserveFullEdgeLegacyPanels() {
        // Missing / default raw values → full-edge strip (old panels unchanged).
        assertEquals(
            DEFAULT_PANEL_COLLAPSE_STRIP_LENGTH_PERCENT,
            normalizePanelCollapseStripLengthPercent(DEFAULT_PANEL_COLLAPSE_STRIP_LENGTH_PERCENT),
        )
        assertEquals(
            DEFAULT_PANEL_COLLAPSE_STRIP_POSITION_PERCENT,
            normalizePanelCollapseStripPositionPercent(
                DEFAULT_PANEL_COLLAPSE_STRIP_POSITION_PERCENT,
                DEFAULT_PANEL_COLLAPSE_STRIP_LENGTH_PERCENT,
            ),
        )
        assertFalse(DEFAULT_PANEL_COLLAPSE_TOUCH_ZONE_MATCH_STRIP)

        // Codec-style clamp: position 70 with length 40 → 60; length 999 → 100 forces position 0.
        val lengthCollapsed = normalizePanelCollapseStripLengthPercent(40)
        val positionCollapsed = normalizePanelCollapseStripPositionPercent(70, lengthCollapsed)
        val lengthExpanded = normalizePanelCollapseStripLengthPercent(999)
        val positionExpanded = normalizePanelCollapseStripPositionPercent(10, lengthExpanded)
        assertEquals(40, lengthCollapsed)
        assertEquals(60, positionCollapsed)
        assertEquals(100, lengthExpanded)
        assertEquals(0, positionExpanded)
    }
}
