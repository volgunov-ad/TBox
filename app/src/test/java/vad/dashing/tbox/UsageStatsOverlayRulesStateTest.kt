package vad.dashing.tbox

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageStatsOverlayRulesStateTest {

    @Test
    fun watchContains_isCaseInsensitive() {
        assertTrue(usageStatsWatchContains(setOf(" Com.Example.App "), "com.example.app"))
        assertFalse(usageStatsWatchContains(setOf("other"), "com.example.app"))
    }

    @Test
    fun forceShow_allowed_when_foreground_only_in_show_watch_hide_targets_other_app() {
        val state = UsageStatsOverlayRulesState(
            foregroundPackage = "com.show.app",
            isMainActivityVisible = false,
            suppressFloatingPanelUsageStatsHide = false,
            watchHidePackages = setOf("com.hide.app"),
            hidePanelIds = setOf("panel_a"),
            watchShowPackages = setOf("com.show.app"),
            showPanelIds = setOf("panel_b"),
        )
        assertFalse(state.isUsageStatsForceHidden("panel_b", "vad.dashing.tbox"))
        assertTrue(state.isUsageStatsForceShowing("panel_b", "vad.dashing.tbox"))
    }

    @Test
    fun forceShow_allowed_when_same_app_in_both_watch_lists_if_panel_not_in_hide_list() {
        val state = UsageStatsOverlayRulesState(
            foregroundPackage = "com.other.app",
            isMainActivityVisible = false,
            suppressFloatingPanelUsageStatsHide = false,
            watchHidePackages = setOf("com.other.app"),
            hidePanelIds = setOf("panel_a"),
            watchShowPackages = setOf("com.other.app"),
            showPanelIds = setOf("panel_b"),
        )
        assertFalse(state.isUsageStatsForceHidden("panel_b", "vad.dashing.tbox"))
        assertTrue(state.isUsageStatsForceShowing("panel_b", "vad.dashing.tbox"))
    }

    @Test
    fun forceShow_blocked_when_hide_rule_applies_to_panel() {
        val state = UsageStatsOverlayRulesState(
            foregroundPackage = "com.other.app",
            isMainActivityVisible = false,
            suppressFloatingPanelUsageStatsHide = false,
            watchHidePackages = setOf("com.other.app"),
            hidePanelIds = setOf("panel_b"),
            watchShowPackages = setOf("com.other.app"),
            showPanelIds = setOf("panel_b"),
        )
        assertTrue(state.isUsageStatsForceHidden("panel_b", "vad.dashing.tbox"))
        assertFalse(state.isUsageStatsForceShowing("panel_b", "vad.dashing.tbox"))
    }

    @Test
    fun forceHide_applies_for_own_package_when_main_activity_visible() {
        val state = UsageStatsOverlayRulesState(
            foregroundPackage = "vad.dashing.tbox",
            isMainActivityVisible = true,
            suppressFloatingPanelUsageStatsHide = false,
            watchHidePackages = setOf("vad.dashing.tbox"),
            hidePanelIds = setOf("panel_main"),
            watchShowPackages = emptySet(),
            showPanelIds = emptySet(),
        )
        assertTrue(state.isUsageStatsForceHidden("panel_main", "vad.dashing.tbox"))
    }

    @Test
    fun forceHide_ignored_for_own_package_when_main_activity_not_visible() {
        val state = UsageStatsOverlayRulesState(
            foregroundPackage = "vad.dashing.tbox",
            isMainActivityVisible = false,
            suppressFloatingPanelUsageStatsHide = false,
            watchHidePackages = setOf("vad.dashing.tbox"),
            hidePanelIds = setOf("panel_main"),
            watchShowPackages = emptySet(),
            showPanelIds = emptySet(),
        )
        assertFalse(state.isUsageStatsForceHidden("panel_main", "vad.dashing.tbox"))
    }

    @Test
    fun forceHide_suppressed_for_any_foreground_when_floating_edit_session_active() {
        val state = UsageStatsOverlayRulesState(
            foregroundPackage = "com.other.app",
            isMainActivityVisible = false,
            suppressFloatingPanelUsageStatsHide = true,
            watchHidePackages = setOf("com.other.app"),
            hidePanelIds = setOf("panel_b"),
            watchShowPackages = emptySet(),
            showPanelIds = emptySet(),
        )
        assertFalse(state.isUsageStatsForceHidden("panel_b", "vad.dashing.tbox"))
    }

    @Test
    fun forceHide_suppressed_for_own_package_and_main_visible_during_floating_edit_session() {
        val state = UsageStatsOverlayRulesState(
            foregroundPackage = "vad.dashing.tbox",
            isMainActivityVisible = true,
            suppressFloatingPanelUsageStatsHide = true,
            watchHidePackages = setOf("vad.dashing.tbox"),
            hidePanelIds = setOf("panel_main"),
            watchShowPackages = emptySet(),
            showPanelIds = emptySet(),
        )
        assertFalse(state.isUsageStatsForceHidden("panel_main", "vad.dashing.tbox"))
    }
}
