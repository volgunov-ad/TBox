package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainScreenPageSelectorLogicTest {

    @Test
    fun resolve_normalMode_usesNormalCurrentPage() {
        assertEquals(
            2,
            MainScreenPageSelectorLogic.resolveDisplayedPage(
                windowModeActive = false,
                pinnedOverlayPage = 3,
                windowModeCurrentPage = 4,
                normalCurrentPage = 2,
                pageCount = 5,
            ),
        )
    }

    @Test
    fun resolve_windowMode_prefersPinnedThenWindowThenNormal() {
        assertEquals(
            3,
            MainScreenPageSelectorLogic.resolveDisplayedPage(
                windowModeActive = true,
                pinnedOverlayPage = 3,
                windowModeCurrentPage = 4,
                normalCurrentPage = 2,
                pageCount = 5,
            ),
        )
        assertEquals(
            4,
            MainScreenPageSelectorLogic.resolveDisplayedPage(
                windowModeActive = true,
                pinnedOverlayPage = null,
                windowModeCurrentPage = 4,
                normalCurrentPage = 2,
                pageCount = 5,
            ),
        )
        assertEquals(
            2,
            MainScreenPageSelectorLogic.resolveDisplayedPage(
                windowModeActive = true,
                pinnedOverlayPage = null,
                windowModeCurrentPage = null,
                normalCurrentPage = 2,
                pageCount = 5,
            ),
        )
    }

    @Test
    fun planSelect_samePage_isNoOp() {
        val plan = MainScreenPageSelectorLogic.planSelect(
            targetPage = 2,
            windowModeActive = true,
            pinnedOverlayPage = 2,
            windowModeCurrentPage = 1,
            normalCurrentPage = 1,
            pageCount = 3,
        )
        assertFalse(plan.shouldApply)
        assertFalse(plan.clearPinnedOverlayPage)
        assertEquals(2, plan.page)
    }

    @Test
    fun planSelect_windowModeWithPin_clearsPinAndApplies() {
        val plan = MainScreenPageSelectorLogic.planSelect(
            targetPage = 3,
            windowModeActive = true,
            pinnedOverlayPage = 1,
            windowModeCurrentPage = 2,
            normalCurrentPage = 1,
            pageCount = 4,
        )
        assertTrue(plan.shouldApply)
        assertTrue(plan.clearPinnedOverlayPage)
        assertTrue(plan.windowMode)
        assertEquals(3, plan.page)
    }

    @Test
    fun planSelect_normalMode_doesNotClearPin() {
        val plan = MainScreenPageSelectorLogic.planSelect(
            targetPage = 2,
            windowModeActive = false,
            pinnedOverlayPage = 3,
            windowModeCurrentPage = null,
            normalCurrentPage = 1,
            pageCount = 3,
        )
        assertTrue(plan.shouldApply)
        assertFalse(plan.clearPinnedOverlayPage)
        assertFalse(plan.windowMode)
        assertEquals(2, plan.page)
    }
}
