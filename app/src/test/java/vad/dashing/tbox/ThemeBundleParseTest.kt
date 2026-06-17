package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ThemeBundleParseTest {

    @Test
    fun normalizeZipEntryPath_stripsLeadingNoise() {
        assertEquals("theme.json", ThemeBundleExport.normalizeZipEntryPath("./theme.json"))
        assertEquals("theme.json", ThemeBundleExport.normalizeZipEntryPath("/theme.json"))
        assertEquals(
            "my_theme/theme.json",
            ThemeBundleExport.normalizeZipEntryPath(".\\my_theme\\theme.json"),
        )
    }

    @Test
    fun parseBundleBytes_findsThemeJsonAtArchiveRoot() {
        val bytes = zipOf("theme.json" to MINIMAL_THEME_JSON.toByteArray())
        val parsed = ThemeBundleExport.parseBundleBytes(bytes).getOrThrow()
        assertEquals(MINIMAL_THEME_JSON, parsed.themeJson)
    }

    @Test
    fun parseBundleBytes_findsThemeJsonWithLeadingDotSlash() {
        val bytes = zipOf("./theme.json" to MINIMAL_THEME_JSON.toByteArray())
        val parsed = ThemeBundleExport.parseBundleBytes(bytes).getOrThrow()
        assertEquals(MINIMAL_THEME_JSON, parsed.themeJson)
    }

    @Test
    fun parseBundleBytes_findsThemeJsonInsideWrapperFolder() {
        val bytes = zipOf("exported/my_theme.tboxtheme/theme.json" to MINIMAL_THEME_JSON.toByteArray())
        val parsed = ThemeBundleExport.parseBundleBytes(bytes).getOrThrow()
        assertEquals(MINIMAL_THEME_JSON, parsed.themeJson)
    }

    @Test
    fun parseBundleBytes_findsAssetsInsideWrapperFolder() {
        val iconBytes = byteArrayOf(1, 2, 3)
        val bytes = zipOf(
            "bundle/theme.json" to MINIMAL_THEME_JSON.toByteArray(),
            "bundle/assets/icons/com.example.app.png" to iconBytes,
        )
        val parsed = ThemeBundleExport.parseBundleBytes(bytes).getOrThrow()
        assertEquals(iconBytes.toList(), parsed.icons["com.example.app.png"]?.toList())
    }

    @Test
    fun parseBundleBytes_rejectsNonZip() {
        val result = ThemeBundleExport.parseBundleBytes("not a zip".toByteArray())
        assertTrue(result.isFailure)
        assertEquals("not_a_zip_archive", result.exceptionOrNull()?.message)
    }

    @Test
    fun parseBundleBytes_rejectsZipWithoutThemeJson() {
        val bytes = zipOf("readme.txt" to "hello".toByteArray())
        val result = ThemeBundleExport.parseBundleBytes(bytes)
        assertTrue(result.isFailure)
        assertEquals("theme.json not found", result.exceptionOrNull()?.message)
    }

    @Test
    fun looksLikeZipArchive_detectsPkHeader() {
        assertTrue(ThemeBundleExport.looksLikeZipArchive(zipOf("theme.json" to MINIMAL_THEME_JSON.toByteArray())))
        assertFalse(ThemeBundleExport.looksLikeZipArchive(byteArrayOf(0, 1, 2)))
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            entries.forEach { (name, data) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(data)
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    companion object {
        private const val MINIMAL_THEME_JSON = """{"sections":["main_screen"]}"""
    }
}
