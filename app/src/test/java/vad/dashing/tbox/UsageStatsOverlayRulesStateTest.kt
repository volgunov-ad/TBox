package vad.dashing.tbox

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageStatsOverlayRulesStateTest {

    @Test
    fun forceShow_allowed_when_foreground_only_in_show_watch_hide_targets_other_app() {
        val state = UsageStatsOverlayRulesState(
            foregroundPackage = "com.show.app",
            watchHidePackages = setOf("com.hide.app"),
            hidePanelIds = setOf("panel_a"),
            watchShowPackages = setOf("com.show.app"),
            showPanelIds = setOf("panel_b"),
        )
        assertFalse(state.isUsageStatsForceHidden("panel_b", "vad.dashing.tbox"))
        assertTrue(state.isUsageStatsForceShowing("panel_b", "vad.dashing.tbox"))
    }

    @Test
    fun forceShow_blocked_when_same_app_in_both_watch_lists() {
        val state = UsageStatsOverlayRulesState(
            foregroundPackage = "com.other.app",
            watchHidePackages = setOf("com.other.app"),
            hidePanelIds = setOf("panel_a"),
            watchShowPackages = setOf("com.other.app"),
            showPanelIds = setOf("panel_b"),
        )
        assertFalse(state.isUsageStatsForceShowing("panel_b", "vad.dashing.tbox"))
    }

    @Test
    fun forceShow_blocked_when_hide_rule_applies_to_panel() {
        val state = UsageStatsOverlayRulesState(
            foregroundPackage = "com.other.app",
            watchHidePackages = setOf("com.other.app"),
            hidePanelIds = setOf("panel_b"),
            watchShowPackages = setOf("com.other.app"),
            showPanelIds = setOf("panel_b"),
        )
        assertTrue(state.isUsageStatsForceHidden("panel_b", "vad.dashing.tbox"))
        assertFalse(state.isUsageStatsForceShowing("panel_b", "vad.dashing.tbox"))
    }
}
