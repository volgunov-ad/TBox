package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Test

class PagingStateNormalizerTest {

    private fun panel(id: String, pageNumber: Int) = MainScreenPanelConfig(
        id = id,
        name = id,
        enabled = true,
        widgetsConfig = emptyList(),
        rows = 1,
        cols = 1,
        relX = 0.1f,
        relY = 0.2f,
        relWidth = 0.4f,
        relHeight = 0.3f,
        background = false,
        clickAction = false,
        pageNumber = pageNumber,
    )

    @Test
    fun adjustPanelsForPageCountChange_keepsAllPanelsOnPageOneWhenEnablingPaging() {
        val panels = listOf(
            panel("a", pageNumber = 1),
            panel("b", pageNumber = 2),
            panel("c", pageNumber = 3),
        )
        val adjusted = PagingStateNormalizer.adjustPanelsForPageCountChange(
            panels = panels,
            oldPageCount = 1,
            newPageCount = 3,
        )
        assertEquals(listOf(1, 1, 1), adjusted.map { it.pageNumber })
        assertEquals(0.1f, adjusted[1].relX)
    }

    @Test
    fun adjustPanelsForPageCountChange_clampsAndPersistsWhenDecreasing() {
        val panels = listOf(
            panel("a", pageNumber = 1),
            panel("b", pageNumber = 3),
        )
        val adjusted = PagingStateNormalizer.adjustPanelsForPageCountChange(
            panels = panels,
            oldPageCount = 3,
            newPageCount = 1,
        )
        assertEquals(listOf(1, 1), adjusted.map { it.pageNumber })
    }

    @Test
    fun adjustPanelsForPageCountChange_preservesAssignmentsWhenIncreasingFromMultiPage() {
        val panels = listOf(
            panel("a", pageNumber = 1),
            panel("b", pageNumber = 2),
        )
        val adjusted = PagingStateNormalizer.adjustPanelsForPageCountChange(
            panels = panels,
            oldPageCount = 2,
            newPageCount = 3,
        )
        assertEquals(listOf(1, 2), adjusted.map { it.pageNumber })
    }
}
