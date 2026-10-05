package vad.dashing.tbox

/** Horizontal page-picker tile for the main screen (1..pageCount buttons in a row). */
const val MAIN_SCREEN_PAGE_SELECTOR_WIDGET_HORIZONTAL_DATA_KEY =
    "mainScreenPageSelectorWidgetHorizontal"

/** Vertical page-picker tile for the main screen (1..pageCount buttons in a column). */
const val MAIN_SCREEN_PAGE_SELECTOR_WIDGET_VERTICAL_DATA_KEY =
    "mainScreenPageSelectorWidgetVertical"

val MAIN_SCREEN_PAGE_SELECTOR_WIDGET_DATA_KEYS: Set<String> = setOf(
    MAIN_SCREEN_PAGE_SELECTOR_WIDGET_HORIZONTAL_DATA_KEY,
    MAIN_SCREEN_PAGE_SELECTOR_WIDGET_VERTICAL_DATA_KEY,
)

fun isMainScreenPageSelectorWidgetDataKey(dataKey: String): Boolean =
    dataKey in MAIN_SCREEN_PAGE_SELECTOR_WIDGET_DATA_KEYS

/**
 * Pure helpers for the main-screen page selector tile: which page is highlighted, and
 * whether a tap should change page / clear a freeform overlay pin.
 */
object MainScreenPageSelectorLogic {
    fun resolveDisplayedPage(
        windowModeActive: Boolean,
        pinnedOverlayPage: Int?,
        windowModeCurrentPage: Int?,
        normalCurrentPage: Int,
        pageCount: Int,
    ): Int {
        val count = PagingStateNormalizer.normalizePageCount(pageCount)
        val raw = if (windowModeActive) {
            pinnedOverlayPage ?: windowModeCurrentPage ?: normalCurrentPage
        } else {
            normalCurrentPage
        }
        return PagingStateNormalizer.normalizeCurrentPage(raw, count)
    }

    data class SelectPlan(
        /** False when the target page is already the displayed page (tap is a no-op). */
        val shouldApply: Boolean,
        /** When true, clear [vad.dashing.tbox.freeform.FreeformCompanionSession] overlay pin first. */
        val clearPinnedOverlayPage: Boolean,
        val windowMode: Boolean,
        val page: Int,
    )

    fun planSelect(
        targetPage: Int,
        windowModeActive: Boolean,
        pinnedOverlayPage: Int?,
        windowModeCurrentPage: Int?,
        normalCurrentPage: Int,
        pageCount: Int,
    ): SelectPlan {
        val count = PagingStateNormalizer.normalizePageCount(pageCount)
        val page = PagingStateNormalizer.normalizeCurrentPage(targetPage, count)
        val current = resolveDisplayedPage(
            windowModeActive = windowModeActive,
            pinnedOverlayPage = pinnedOverlayPage,
            windowModeCurrentPage = windowModeCurrentPage,
            normalCurrentPage = normalCurrentPage,
            pageCount = count,
        )
        if (page == current) {
            return SelectPlan(
                shouldApply = false,
                clearPinnedOverlayPage = false,
                windowMode = windowModeActive,
                page = page,
            )
        }
        return SelectPlan(
            shouldApply = true,
            clearPinnedOverlayPage = windowModeActive && pinnedOverlayPage != null,
            windowMode = windowModeActive,
            page = page,
        )
    }
}
