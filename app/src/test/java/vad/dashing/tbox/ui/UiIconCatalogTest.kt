package vad.dashing.tbox.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.R
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
    fun mainScreenCornerButtonsAreInCatalog() {
        val expected = listOf(
            UiIconCatalog.MAIN_SCREEN_SETTINGS to R.drawable.ic_main_open_console,
            UiIconCatalog.MAIN_SCREEN_ADD to R.drawable.ic_main_screen_add,
            UiIconCatalog.MAIN_SCREEN_WALLPAPER_PREV to R.drawable.ic_main_screen_arrow_left,
            UiIconCatalog.MAIN_SCREEN_WALLPAPER_NEXT to R.drawable.ic_main_screen_arrow_right,
            UiIconCatalog.MAIN_SCREEN_WINDOW_EXIT to R.drawable.ic_main_screen_close,
            UiIconCatalog.MAIN_SCREEN_WINDOW_RESTORE to R.drawable.ic_main_screen_window_restore,
        )
        expected.forEach { (key, drawable) ->
            val entry = UiIconCatalog.entry(key)
            assertNotNull(key, entry)
            assertEquals(UiIconCategory.MAIN_SCREEN, entry?.category)
            assertEquals(drawable, entry?.drawableRes)
            assertEquals(key, UiIconCatalog.keyForDrawable(drawable))
        }
    }

    @Test
    fun drawableMappingsAreUnique() {
        val drawableEntries = UiIconCatalog.entries.filter { it.drawableRes != null }
        val grouped = drawableEntries.groupBy { it.drawableRes }

        assertTrue(grouped.filterValues { it.size > 1 }.isEmpty())
    }
}
