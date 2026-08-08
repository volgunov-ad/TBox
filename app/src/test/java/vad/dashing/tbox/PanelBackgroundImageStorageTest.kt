package vad.dashing.tbox

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PanelBackgroundImageStorageTest {

    @Test
    fun relativePathFor_usesLightAndDarkSuffix() {
        assertEquals(
            "panel_backgrounds/panel_a_light",
            PanelBackgroundImageStorage.relativePathFor("panel_a", darkTheme = false),
        )
        assertEquals(
            "panel_backgrounds/panel_a_dark",
            PanelBackgroundImageStorage.relativePathFor("panel_a", darkTheme = true),
        )
    }

    @Test
    fun resolveFile_prefersThemeCacheOverSharedOverride() {
        val root = createTempDir()
        val rel = PanelBackgroundImageStorage.relativePathFor("panel_a", darkTheme = false)
        val sharedFile = File(root, rel.replace('/', File.separatorChar))
        sharedFile.parentFile?.mkdirs()
        sharedFile.writeBytes(byteArrayOf(1))
        val themeDir = PanelBackgroundImageStorage.themeCacheDir(root, "my_theme")
        File(themeDir, "panel_a_light").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(2))
        }
        val lookup = LauncherAppIconPaths.Lookup(
            activeThemeCacheKey = "my_theme",
            activeThemeApplyTargets = setOf(ThemeApplyTarget.MAIN_SCREEN_PANELS),
        )
        val resolved = PanelBackgroundImageStorage.resolveFile(root, rel, lookup)
        assertNotNull(resolved)
        assertArrayEquals(byteArrayOf(2), resolved!!.readBytes())
        root.deleteRecursively()
    }

    @Test
    fun resolveFile_skipsThemeCacheWhenPanelsTargetsMissing() {
        val root = createTempDir()
        val rel = PanelBackgroundImageStorage.relativePathFor("panel_c", darkTheme = false)
        val themeDir = PanelBackgroundImageStorage.themeCacheDir(root, "theme_c")
        File(themeDir, "panel_c_light").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(3))
        }
        val sharedFile = File(root, rel.replace('/', File.separatorChar))
        sharedFile.parentFile?.mkdirs()
        sharedFile.writeBytes(byteArrayOf(5))
        val lookup = LauncherAppIconPaths.Lookup(
            activeThemeCacheKey = "theme_c",
            activeThemeApplyTargets = setOf(ThemeApplyTarget.APP_ICONS),
        )
        val resolved = PanelBackgroundImageStorage.resolveFile(root, rel, lookup)
        assertNotNull(resolved)
        assertArrayEquals(byteArrayOf(5), resolved!!.readBytes())
        root.deleteRecursively()
    }

    @Test
    fun parseAndPut_dataStoreRoundTrip() {
        val o = JSONObject()
        putPanelBackgroundStyleFieldsDataStore(
            o = o,
            backgroundColorLight = 0x80FF0000.toInt(),
            backgroundColorDark = null,
            backgroundImageRelPathLight = "panel_backgrounds/p1_light",
            backgroundImageRelPathDark = null,
            panelShape = 12,
        )
        val parsed = parsePanelBackgroundStyleFieldsDataStore(o)
        assertEquals(0x80FF0000.toInt(), parsed.backgroundColorLight)
        assertNull(parsed.backgroundColorDark)
        assertEquals("panel_backgrounds/p1_light", parsed.backgroundImageRelPathLight)
        assertEquals(12, parsed.panelShape)
    }

    @Test
    fun parseTheme_acceptsHexColors() {
        val o = JSONObject()
        o.put("panelBackgroundColorLight", "#80FF0000")
        o.put("panelShape", 8)
        val parsed = parsePanelBackgroundStyleFieldsTheme(o)
        assertEquals(0x80FF0000.toInt(), parsed.backgroundColorLight)
        assertEquals(8, parsed.panelShape)
        assertTrue(parsed.backgroundImageRelPathLight == null)
    }

    @Test
    fun collectPanelBackgroundPaths_filtersInvalid() {
        val paths = collectPanelBackgroundPaths(
            backgroundImageRelPathLight = "panel_backgrounds/ok_light",
            backgroundImageRelPathDark = "../evil",
        )
        assertEquals(setOf("panel_backgrounds/ok_light"), paths)
    }
}
