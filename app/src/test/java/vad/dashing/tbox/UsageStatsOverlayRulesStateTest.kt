package vad.dashing.tbox

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageStatsOverlayRulesStateTest {

    private val myPkg = "vad.dashing.tbox"

    private fun state(
        fg: String? = null,
        mainVisible: Boolean = false,
        suppressHide: Boolean = false,
        suppressShow: Boolean = false,
        hideWatch: Set<String> = emptySet(),
        hidePanels: Set<String> = emptySet(),
        showWatch: Set<String> = emptySet(),
        showPanels: Set<String> = emptySet(),
    ) = UsageStatsOverlayRulesState(
        foregroundPackage = fg,
        isMainActivityVisible = mainVisible,
        suppressFloatingPanelUsageStatsHide = suppressHide,
        suppressFloatingPanelUsageStatsForceShow = suppressShow,
        watchHidePackages = hideWatch,
        hidePanelIds = hidePanels,
        watchShowPackages = showWatch,
        showPanelIds = showPanels,
    )

    @Test
    fun watchContains_isCaseInsensitive() {
        assertTrue(usageStatsWatchContains(setOf(" Com.Example.App "), "com.example.app"))
        assertFalse(usageStatsWatchContains(setOf("other"), "com.example.app"))
    }

    @Test
    fun forceShow_allowed_when_foreground_only_in_show_watch_hide_targets_other_app() {
        val s = state(
            fg = "com.show.app",
            hideWatch = setOf("com.hide.app"),
            hidePanels = setOf("panel_a"),
            showWatch = setOf("com.show.app"),
            showPanels = setOf("panel_b"),
        )
        assertFalse(s.isUsageStatsForceHidden("panel_b", myPkg))
        assertTrue(s.isUsageStatsForceShowing("panel_b", myPkg))
    }

    @Test
    fun forceShow_allowed_when_same_app_in_both_watch_lists_if_panel_not_in_hide_list() {
        val s = state(
            fg = "com.other.app",
            hideWatch = setOf("com.other.app"),
            hidePanels = setOf("panel_a"),
            showWatch = setOf("com.other.app"),
            showPanels = setOf("panel_b"),
        )
        assertFalse(s.isUsageStatsForceHidden("panel_b", myPkg))
        assertTrue(s.isUsageStatsForceShowing("panel_b", myPkg))
    }

    @Test
    fun forceShow_blocked_when_hide_rule_applies_to_panel() {
        val s = state(
            fg = "com.other.app",
            hideWatch = setOf("com.other.app"),
            hidePanels = setOf("panel_b"),
            showWatch = setOf("com.other.app"),
            showPanels = setOf("panel_b"),
        )
        assertTrue(s.isUsageStatsForceHidden("panel_b", myPkg))
        assertFalse(s.isUsageStatsForceShowing("panel_b", myPkg))
    }

    @Test
    fun forceHide_applies_for_own_package_when_main_activity_visible() {
        val s = state(
            fg = myPkg,
            mainVisible = true,
            hideWatch = setOf(myPkg),
            hidePanels = setOf("panel_main"),
        )
        assertTrue(s.isUsageStatsForceHidden("panel_main", myPkg))
    }

    @Test
    fun forceHide_ignored_for_own_package_when_main_activity_not_visible() {
        val s = state(
            fg = myPkg,
            mainVisible = false,
            hideWatch = setOf(myPkg),
            hidePanels = setOf("panel_main"),
        )
        assertFalse(s.isUsageStatsForceHidden("panel_main", myPkg))
    }

    @Test
    fun forceHide_suppressed_for_any_foreground_when_floating_edit_session_active() {
        val s = state(
            fg = "com.other.app",
            suppressHide = true,
            hideWatch = setOf("com.other.app"),
            hidePanels = setOf("panel_b"),
        )
        assertFalse(s.isUsageStatsForceHidden("panel_b", myPkg))
    }

    @Test
    fun forceHide_suppressed_for_own_package_and_main_visible_during_floating_edit_session() {
        val s = state(
            fg = myPkg,
            mainVisible = true,
            suppressHide = true,
            hideWatch = setOf(myPkg),
            hidePanels = setOf("panel_main"),
        )
        assertFalse(s.isUsageStatsForceHidden("panel_main", myPkg))
    }

    @Test
    fun forceShow_blocked_when_main_activity_visible_even_if_sticky_maps_fg() {
        val s = state(
            fg = "com.maps.app",
            mainVisible = true,
            hideWatch = setOf(myPkg),
            hidePanels = setOf("nav_panel"),
            showWatch = setOf("com.maps.app"),
            showPanels = setOf("nav_panel"),
        )
        assertFalse(s.isUsageStatsForceShowing("nav_panel", myPkg))
        assertTrue(s.isUsageStatsForceHidden("nav_panel", myPkg))
    }

    @Test
    fun forceHide_applies_on_main_visible_with_own_package_in_hide_watch_despite_sticky_maps_fg() {
        val s = state(
            fg = "com.maps.app",
            mainVisible = true,
            hideWatch = setOf(myPkg),
            hidePanels = setOf("nav_panel"),
            showWatch = setOf("com.maps.app"),
            showPanels = setOf("nav_panel"),
        )
        assertTrue(s.isUsageStatsForceHidden("nav_panel", myPkg))
    }

    @Test
    fun forceShow_suppressed_by_startup_flag() {
        val s = state(
            fg = "com.maps.app",
            suppressShow = true,
            showWatch = setOf("com.maps.app"),
            showPanels = setOf("nav_panel"),
        )
        assertFalse(s.isUsageStatsForceShowing("nav_panel", myPkg))
    }
}
