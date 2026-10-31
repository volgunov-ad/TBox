package vad.dashing.tbox.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.LauncherAppIconPaths
import vad.dashing.tbox.ThemeApplyTarget

class SizedMemoryCacheTest {

    @Test
    fun evictsLeastRecentlyUsedBySize() {
        val cache = SizedMemoryCache<String, String>(maxBytes = 10) { it.length }
        cache.put("a", 0, "xxxx")
        cache.put("b", 0, "yyyy")
        assertEquals("xxxx", cache.get("a", 0))
        cache.put("c", 0, "zzzz")
        assertNull(cache.get("b", 0))
        assertEquals("xxxx", cache.get("a", 0))
        assertEquals("zzzz", cache.get("c", 0))
    }

    @Test
    fun newerGenerationDropsOlderEntries() {
        val cache = SizedMemoryCache<String, String>(maxBytes = 100) { 1 }
        cache.put("a", 1, "old")
        cache.put("b", 2, "new")
        assertNull(cache.get("a", 1))
        assertEquals("new", cache.get("b", 2))
        cache.put("a", 1, "late")
        assertNull(cache.get("a", 1))
    }

    @Test
    fun iconKeyDependsOnThemeAndRevisions() {
        val shared = LauncherAppIconPaths.Lookup.None
        val theme = LauncherAppIconPaths.Lookup("abc", setOf(ThemeApplyTarget.APP_ICONS))
        val base = AppIconCache.Key("pkg", 256, shared, false, 3, 0)
        assertNotEquals(base, base.copy(lookup = theme))
        assertNotEquals(base, base.copy(suppressCustomIcon = true))
        assertTrue(base.copy(customIconRevision = 4).generation > base.generation)
        assertTrue(base.copy(packagesRevision = 1, customIconRevision = 0).generation > base.generation)
    }
}
