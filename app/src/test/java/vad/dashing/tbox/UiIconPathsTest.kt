package vad.dashing.tbox

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UiIconPathsTest {

    @Test
    fun resolveIconFile_prefersActiveThemeThenShared() {
        val root = createTempDir()
        val key = "dashboard.vehicle.trunk"
        File(UiIconPaths.sharedIconsDir(root), key).apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1))
        }
        File(UiIconPaths.themeIconsDir(root, "night"), key).apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(2))
        }
        val themedLookup = LauncherAppIconPaths.Lookup(
            activeThemeCacheKey = "night",
            activeThemeApplyTargets = setOf(ThemeApplyTarget.UI_ICONS),
        )

        assertArrayEquals(
            byteArrayOf(2),
            UiIconPaths.resolveIconFile(root, key, themedLookup)?.readBytes(),
        )
        assertArrayEquals(
            byteArrayOf(1),
            UiIconPaths.resolveIconFile(root, key, LauncherAppIconPaths.Lookup.None)?.readBytes(),
        )
        root.deleteRecursively()
    }

    @Test
    fun destinationIconFile_usesThemeOnlyWhenUiIconTargetIsActive() {
        val root = createTempDir()
        val key = "menu.tab.settings"
        val themedLookup = LauncherAppIconPaths.Lookup(
            activeThemeCacheKey = "night",
            activeThemeApplyTargets = setOf(ThemeApplyTarget.UI_ICONS),
        )
        val unrelatedLookup = themedLookup.copy(
            activeThemeApplyTargets = setOf(ThemeApplyTarget.APP_ICONS),
        )

        assertEquals(
            File(UiIconPaths.themeIconsDir(root, "night"), key),
            UiIconPaths.destinationIconFile(root, key, themedLookup),
        )
        assertEquals(
            File(UiIconPaths.sharedIconsDir(root), key),
            UiIconPaths.destinationIconFile(root, key, unrelatedLookup),
        )
        root.deleteRecursively()
    }

    @Test
    fun invalidKeysCannotEscapeStorageDirectory() {
        val root = createTempDir()

        listOf("../icon", "menu/icon", "", "UPPERCASE", ".hidden").forEach { key ->
            assertFalse(UiIconPaths.isValidKey(key))
            assertNull(UiIconPaths.destinationIconFile(root, key, LauncherAppIconPaths.Lookup.None))
            assertNull(UiIconPaths.resolveIconFile(root, key, LauncherAppIconPaths.Lookup.None))
        }
        assertTrue(UiIconPaths.isValidKey("dashboard.hvac.blow.face"))
        root.deleteRecursively()
    }

    @Test
    fun deleteCurrentOverride_removesThemeBeforeShared() {
        val root = createTempDir()
        val key = "navigation.home"
        val shared = File(UiIconPaths.sharedIconsDir(root), key).apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1))
        }
        val themed = File(UiIconPaths.themeIconsDir(root, "theme"), key).apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(2))
        }
        val lookup = LauncherAppIconPaths.Lookup(
            activeThemeCacheKey = "theme",
            activeThemeApplyTargets = setOf(ThemeApplyTarget.UI_ICONS),
        )

        assertTrue(UiIconPaths.deleteCurrentOverride(root, key, lookup))
        assertFalse(themed.exists())
        assertTrue(shared.exists())
        assertArrayEquals(byteArrayOf(1), UiIconPaths.resolveIconFile(root, key, lookup)?.readBytes())
        root.deleteRecursively()
    }
}
