package vad.dashing.store.ui

import vad.dashing.store.StoreAppRow
import vad.dashing.store.catalog.StoreSection

const val STORE_PAGE_SIZE = 10

/** [id] == null → «Все». */
data class SectionChip(
    val id: String?,
    val title: String,
    val count: Int,
)

data class CatalogView(
    val chips: List<SectionChip>,
    val selectedSectionId: String?,
    val pageRows: List<StoreAppRow>,
    val page: Int,
    val pageCount: Int,
)

fun buildCatalogView(
    rows: List<StoreAppRow>,
    sections: List<StoreSection>,
    selectedSectionId: String?,
    page: Int,
    lang: String,
    pageSize: Int = STORE_PAGE_SIZE,
): CatalogView {
    val size = pageSize.coerceAtLeast(1)
    val chips = buildList {
        val nonEmpty = sections.mapNotNull { section ->
            val count = rows.count { section.id in it.entry.sections }
            if (count <= 0) null else SectionChip(section.id, section.displayTitle(lang), count)
        }
        if (nonEmpty.isEmpty()) return@buildList
        add(SectionChip(id = null, title = "", count = rows.size))
        addAll(nonEmpty)
    }
    val normalizedSection = when {
        selectedSectionId.isNullOrBlank() -> null
        chips.any { it.id == selectedSectionId } -> selectedSectionId
        else -> null
    }
    val filtered = if (normalizedSection == null) {
        rows
    } else {
        rows.filter { normalizedSection in it.entry.sections }
    }
    val pageCount = ((filtered.size + size - 1) / size).coerceAtLeast(1)
    val clampedPage = page.coerceIn(0, pageCount - 1)
    val from = clampedPage * size
    val pageRows = if (from >= filtered.size) {
        emptyList()
    } else {
        filtered.subList(from, minOf(from + size, filtered.size))
    }
    return CatalogView(
        chips = chips,
        selectedSectionId = normalizedSection,
        pageRows = pageRows,
        page = clampedPage,
        pageCount = pageCount,
    )
}
