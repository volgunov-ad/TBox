package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingOverlayVisibilityTest {

    private val myPkg = "vad.dashing.tbox"

    private fun rules(
        fg: String? = "com.other.app",
        hideWatch: Set<String> = emptySet(),
        hidePanels: Set<String> = emptySet(),
        showWatch: Set<String> = emptySet(),
        showPanels: Set<String> = emptySet(),
        suppressHide: Boolean = false,
        suppressShow: Boolean = false,
        mainVisible: Boolean = false,
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
    fun shouldShow_enabledUnlessUsageStatsForceHidden() {
        assertTrue(
            FloatingOverlayVisibility.shouldShowBySettingsAndUsageStats(
                panelId = "p1",
                enabled = true,
                myPackageName = myPkg,
                rules = rules(),
            ),
        )
        assertFalse(
            FloatingOverlayVisibility.shouldShowBySettingsAndUsageStats(
                panelId = "p1",
                enabled = true,
                myPackageName = myPkg,
                rules = rules(
                    hideWatch = setOf("com.nav"),
                    hidePanels = setOf("p1"),
                    fg = "com.nav",
                ),
            ),
        )
    }

    @Test
    fun shouldShow_disabledUnlessUsageStatsForceShowing() {
        assertFalse(
            FloatingOverlayVisibility.shouldShowBySettingsAndUsageStats(
                panelId = "p1",
                enabled = false,
                myPackageName = myPkg,
                rules = rules(),
            ),
        )
        assertTrue(
            FloatingOverlayVisibility.shouldShowBySettingsAndUsageStats(
                panelId = "p1",
                enabled = false,
                myPackageName = myPkg,
                rules = rules(
                    showWatch = setOf("com.map"),
                    showPanels = setOf("p1"),
                    fg = "com.map",
                ),
            ),
        )
    }

    @Test
    fun shouldShow_hideWinsOverForceShow() {
        assertFalse(
            FloatingOverlayVisibility.shouldShowBySettingsAndUsageStats(
                panelId = "p1",
                enabled = false,
                myPackageName = myPkg,
                rules = rules(
                    fg = "com.map",
                    hideWatch = setOf("com.map"),
                    hidePanels = setOf("p1"),
                    showWatch = setOf("com.map"),
                    showPanels = setOf("p1"),
                ),
            ),
        )
    }

    @Test
    fun temporarilyHidden_widgetListOrUsageStats() {
        assertTrue(
            FloatingOverlayVisibility.isTemporarilyHidden(
                panelId = "p1",
                myPackageName = myPkg,
                hiddenFloatingPanelIds = setOf("p1"),
                rules = rules(),
            ),
        )
        assertTrue(
            FloatingOverlayVisibility.isTemporarilyHidden(
                panelId = "p1",
                myPackageName = myPkg,
                hiddenFloatingPanelIds = emptySet(),
                rules = rules(
                    fg = "com.nav",
                    hideWatch = setOf("com.nav"),
                    hidePanels = setOf("p1"),
                ),
            ),
        )
        assertFalse(
            FloatingOverlayVisibility.isTemporarilyHidden(
                panelId = "p1",
                myPackageName = myPkg,
                hiddenFloatingPanelIds = setOf("other"),
                rules = rules(),
            ),
        )
    }

    @Test
    fun shouldShow_forceShowOffWhenMainVisibleDespiteStickyMapsFg() {
        assertFalse(
            FloatingOverlayVisibility.shouldShowBySettingsAndUsageStats(
                panelId = "nav",
                enabled = false,
                myPackageName = myPkg,
                rules = rules(
                    fg = "com.maps",
                    mainVisible = true,
                    hideWatch = setOf(myPkg),
                    hidePanels = setOf("nav"),
                    showWatch = setOf("com.maps"),
                    showPanels = setOf("nav"),
                ),
            ),
        )
    }

    @Test
    fun syncReorderZOrderAfterHideToggle_onlyWhenRevealing() {
        assertFalse(FloatingOverlayVisibility.syncReorderZOrderAfterHideToggle(revealing = false))
        assertTrue(FloatingOverlayVisibility.syncReorderZOrderAfterHideToggle(revealing = true))
    }
}
