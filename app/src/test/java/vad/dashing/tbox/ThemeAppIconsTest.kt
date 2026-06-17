package vad.dashing.tbox

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ThemeAppIconsTest {

    @Test
    fun resolveStoredIconFile_prefersPackageNameWithoutExtension() {
        val dir = createTempDir().apply {
            File(this, "com.example.app").writeBytes(byteArrayOf(1, 2, 3))
            File(this, "com.example.app.png").writeBytes(byteArrayOf(9))
        }
        val resolved = LauncherAppIconPaths.resolveStoredIconFile(dir, "com.example.app")
        assertNotNull(resolved)
        assertEquals("com.example.app", resolved!!.name)
        assertArrayEquals(byteArrayOf(1, 2, 3), resolved.readBytes())
        dir.deleteRecursively()
    }

    @Test
    fun listStoredPackageNames_includesExtensionlessFiles() {
        val dir = createTempDir().apply {
            File(this, "com.foo.bar").writeBytes(byteArrayOf(1))
            File(this, "readme.txt").writeBytes(byteArrayOf(2))
        }
        assertEquals(setOf("com.foo.bar"), LauncherAppIconPaths.listStoredPackageNames(dir))
        dir.deleteRecursively()
    }

    @Test
    fun liveFileNameFromThemeAsset_stripsPngSuffix() {
        assertEquals("com.example.app", LauncherAppIconPaths.liveFileNameFromThemeAsset("com.example.app.png"))
        assertEquals("com.example.app", LauncherAppIconPaths.liveFileNameFromThemeAsset("com.example.app"))
    }

    @Test
    fun parseBundleAndInstall_roundTripUsesLiveIconNames() {
        val pkg = "com.example.music"
        val iconBytes = byteArrayOf(1, 2, 3, 4)
        val zipBytes = buildZip(
            "theme.json" to """{"formatVersion":1,"type":"tbox_theme","sections":["appIcons"]}""".toByteArray(),
            "assets/icons/$pkg" to iconBytes,
        )

        val parsed = ThemeBundleExport.parseBundleBytes(zipBytes).getOrThrow()
        assertEquals(1, parsed.icons.size)
        assertArrayEquals(iconBytes, parsed.icons[pkg])

        val cacheDir = createTempDir()
        val cacheIcons = File(cacheDir, "icons").apply { mkdirs() }
        parsed.icons.forEach { (name, bytes) -> File(cacheIcons, name).writeBytes(bytes) }

        val liveDir = createTempDir()
        val installed = LauncherAppIconPaths.installFromThemeCacheDirectory(cacheIcons, liveDir)
        assertEquals(1, installed)

        val liveFile = File(liveDir, pkg)
        assertTrue(liveFile.isFile)
        assertArrayEquals(iconBytes, liveFile.readBytes())

        cacheDir.deleteRecursively()
        liveDir.deleteRecursively()
    }

    private fun buildZip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            entries.forEach { (name, data) ->
                val entry = ZipEntry(name)
                entry.method = ZipEntry.STORED
                entry.size = data.size.toLong()
                entry.compressedSize = data.size.toLong()
                val crc = CRC32()
                crc.update(data)
                entry.crc = crc.value
                zos.putNextEntry(entry)
                zos.write(data)
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }
}
