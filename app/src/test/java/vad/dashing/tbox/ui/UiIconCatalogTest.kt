package vad.dashing.tbox.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.UiIconPaths

class UiIconCatalogTest {

    @Test
    fun keysAreUniqueAndSafeForSidecarFileNames() {
        val keys = UiIconCatalog.entries.map { it.key }

        assertEquals(keys.size, keys.toSet().size)
        assertTrue(keys.all(UiIconPaths::isValidKey))
    }

    @Test
    fun everyLeftMenuEntryHasCatalogMetadata() {
        LeftMenuTabField.entries.forEach { field ->
            val entry = UiIconCatalog.entry(field.iconKey)
            assertNotNull(field.id, entry)
            assertEquals(UiIconCategory.NAVIGATION, entry?.category)
        }
    }

    @Test
    fun drawableMappingsAreUnique() {
        val drawableEntries = UiIconCatalog.entries.filter { it.drawableRes != null }
        val grouped = drawableEntries.groupBy { it.drawableRes }

        assertTrue(grouped.filterValues { it.size > 1 }.isEmpty())
    }
}
