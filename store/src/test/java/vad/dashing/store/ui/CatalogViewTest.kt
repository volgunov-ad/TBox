package vad.dashing.store.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.store.StoreAppRow
import vad.dashing.store.catalog.StoreCatalogEntry
import vad.dashing.store.catalog.StoreSection

class CatalogViewTest {

    private fun row(id: String, sections: List<String> = emptyList()) = StoreAppRow(
        entry = StoreCatalogEntry(
            id = id,
            packageName = "pkg.$id",
            title = id,
            sections = sections,
        ),
    )

    @Test
    fun chips_orderCountsAndEmptyHidden() {
        val rows = listOf(
            row("a", listOf("app")),
            row("b", listOf("app", "tool")),
            row("c", listOf("missing")),
            row("d"),
        )
        val sections = listOf(
            StoreSection("app", "Приложения", "Apps"),
            StoreSection("tool", "Инструменты", "Tools"),
            StoreSection("empty", "Пустой"),
        )
        val view = buildCatalogView(rows, sections, selectedSectionId = null, page = 0, lang = "ru")
        assertEquals(3, view.chips.size)
        assertNull(view.chips[0].id)
        assertEquals(4, view.chips[0].count)
        assertEquals("app", view.chips[1].id)
        assertEquals(2, view.chips[1].count)
        assertEquals("Приложения", view.chips[1].title)
        assertEquals("tool", view.chips[2].id)
        assertEquals(1, view.chips[2].count)
        assertEquals(4, view.pageRows.size)
    }

    @Test
    fun chips_emptyWhenNoNonEmptySections() {
        val view = buildCatalogView(
            rows = listOf(row("a"), row("b")),
            sections = listOf(StoreSection("x", "X")),
            selectedSectionId = null,
            page = 0,
            lang = "en",
        )
        assertTrue(view.chips.isEmpty())
    }

    @Test
    fun filterAndUnknownSelection() {
        val rows = listOf(row("a", listOf("app")), row("b", listOf("tool")))
        val sections = listOf(
            StoreSection("app", "Apps", "Apps"),
            StoreSection("tool", "Tools", "Tools"),
        )
        val filtered = buildCatalogView(rows, sections, "tool", 0, "en")
        assertEquals(listOf("b"), filtered.pageRows.map { it.entry.id })
        assertEquals("tool", filtered.selectedSectionId)

        val unknown = buildCatalogView(rows, sections, "nope", 0, "en")
        assertNull(unknown.selectedSectionId)
        assertEquals(2, unknown.pageRows.size)
    }

    @Test
    fun pagination() {
        val rows = (1..10).map { row("a$it") }
        val one = buildCatalogView(rows, emptyList(), null, 0, "ru")
        assertEquals(1, one.pageCount)
        assertEquals(10, one.pageRows.size)

        val many = (1..23).map { row("r$it") }
        val p0 = buildCatalogView(many, emptyList(), null, 0, "ru")
        assertEquals(3, p0.pageCount)
        assertEquals(10, p0.pageRows.size)
        val p1 = buildCatalogView(many, emptyList(), null, 1, "ru")
        assertEquals(10, p1.pageRows.size)
        val p2 = buildCatalogView(many, emptyList(), null, 2, "ru")
        assertEquals(3, p2.pageRows.size)
        val clamped = buildCatalogView(many, emptyList(), null, 5, "ru")
        assertEquals(2, clamped.page)
        val neg = buildCatalogView(many, emptyList(), null, -1, "ru")
        assertEquals(0, neg.page)

        val empty = buildCatalogView(emptyList(), emptyList(), null, 0, "ru")
        assertEquals(1, empty.pageCount)
        assertTrue(empty.pageRows.isEmpty())
    }

    @Test
    fun enUsesTitleEn() {
        val rows = listOf(row("a", listOf("app")))
        val sections = listOf(StoreSection("app", "Приложения", "Apps"))
        val view = buildCatalogView(rows, sections, null, 0, "en")
        assertEquals("Apps", view.chips[1].title)
    }
}
