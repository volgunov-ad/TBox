package vad.dashing.tbox

object PagingStateNormalizer {
    fun normalizePageCount(raw: Int): Int =
        raw.coerceIn(SettingsManager.MIN_MAIN_SCREEN_PAGE_COUNT, SettingsManager.MAX_MAIN_SCREEN_PAGE_COUNT)

    fun normalizeCurrentPage(raw: Int, pageCount: Int): Int {
        val count = normalizePageCount(pageCount)
        return raw.coerceIn(1, count)
    }

    fun normalizePanelPageNumber(raw: Int, pageCount: Int): Int {
        val count = normalizePageCount(pageCount)
        return raw.coerceIn(1, count)
    }

    fun clampPanelsToPageCount(
        panels: List<MainScreenPanelConfig>,
        pageCount: Int,
    ): List<MainScreenPanelConfig> {
        val count = normalizePageCount(pageCount)
        return panels.map { panel ->
            if (panel.pageNumber > count) {
                panel.copy(pageNumber = count)
            } else {
                panel
            }
        }
    }

    /**
     * Keeps panel layout stable when the user enables multi-page mode after arranging panels
     * on a single page, and persists clamped page numbers when the page count shrinks.
     */
    fun adjustPanelsForPageCountChange(
        panels: List<MainScreenPanelConfig>,
        oldPageCount: Int,
        newPageCount: Int,
    ): List<MainScreenPanelConfig> {
        val oldCount = normalizePageCount(oldPageCount)
        val newCount = normalizePageCount(newPageCount)
        return when {
            oldCount == 1 && newCount > 1 -> panels.map { it.copy(pageNumber = 1) }
            newCount < oldCount -> clampPanelsToPageCount(panels, newCount)
            else -> clampPanelsToPageCount(panels, newCount)
        }
    }
}

fun MainScreenPanelConfig.isVisibleOnMainScreenPage(
    pageCount: Int,
    currentPage: Int,
): Boolean {
    val count = PagingStateNormalizer.normalizePageCount(pageCount)
    val page = PagingStateNormalizer.normalizeCurrentPage(currentPage, count)
    return enabled &&
        pageNumber in 1..count &&
        pageNumber == page
}
