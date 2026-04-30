package vad.dashing.tbox

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.first
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Export/import of the full launcher design bundle as a ZIP archive.
 *
 * Bundle structure:
 * ```
 * layout.json                   ← launcher layout (same as plain JSON export)
 * icons/<pkg.name>.png          ← custom app-launcher icons (from launcher_app_icons/)
 * wallpaper/light/<filename>    ← wallpaper images for light theme (if in file:// internal storage)
 * wallpaper/dark/<filename>     ← wallpaper images for dark theme (if in file:// internal storage)
 * ```
 */
object LauncherBundleExport {

    const val BUNDLE_FILE_EXTENSION = "tboxbundle"
    private const val LAYOUT_JSON_ENTRY = "layout.json"
    private const val ICONS_DIR = "icons/"
    private const val WALLPAPER_LIGHT_DIR = "wallpaper/light/"
    private const val WALLPAPER_DARK_DIR = "wallpaper/dark/"

    /** Folder under filesDir where imported wallpapers are placed. */
    const val WALLPAPER_BUNDLE_DIR = "launcher_bundle_wallpaper"

    data class BundleImportResult(
        val layoutJson: String,
        val iconsImported: Int,
        val lightWallpaperDir: File?,
        val darkWallpaperDir: File?
    )

    suspend fun exportBundle(context: Context, settingsManager: SettingsManager): ByteArray {
        val layoutJson = LauncherLayoutExport.exportJson(context, settingsManager)
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry(LAYOUT_JSON_ENTRY))
            zos.write(layoutJson.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            addDirectoryToZip(
                zos,
                File(context.filesDir, SettingsManager.LAUNCHER_APP_ICONS_DIR),
                ICONS_DIR
            )
            addWallpaperFolderToZip(context, settingsManager, zos)
        }
        return baos.toByteArray()
    }

    /**
     * Extracts the bundle, writes icons and wallpapers to internal storage, and returns
     * [BundleImportResult] so the caller (e.g. MainActivity) can apply settings updates
     * that require ViewModel-level epoch bumps.
     */
    fun extractBundle(context: Context, bytes: ByteArray): Result<BundleImportResult> {
        var layoutJson: String? = null
        val iconsDir = File(context.filesDir, SettingsManager.LAUNCHER_APP_ICONS_DIR)
        var iconsImported = 0
        var lightDir: File? = null
        var darkDir: File? = null

        return runCatching {
            ZipInputStream(bytes.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    when {
                        entry.name == LAYOUT_JSON_ENTRY -> {
                            layoutJson = zis.readBytes().toString(Charsets.UTF_8)
                        }
                        entry.name.startsWith(ICONS_DIR) && !entry.isDirectory -> {
                            val filename = entry.name.removePrefix(ICONS_DIR)
                            if (filename.isNotBlank()) {
                                iconsDir.mkdirs()
                                File(iconsDir, filename).writeBytes(zis.readBytes())
                                iconsImported++
                            }
                        }
                        entry.name.startsWith(WALLPAPER_LIGHT_DIR) && !entry.isDirectory -> {
                            val filename = entry.name.removePrefix(WALLPAPER_LIGHT_DIR)
                            if (filename.isNotBlank()) {
                                val dest = File(context.filesDir, "$WALLPAPER_BUNDLE_DIR/light/$filename")
                                dest.parentFile?.mkdirs()
                                dest.writeBytes(zis.readBytes())
                                lightDir = dest.parentFile
                            }
                        }
                        entry.name.startsWith(WALLPAPER_DARK_DIR) && !entry.isDirectory -> {
                            val filename = entry.name.removePrefix(WALLPAPER_DARK_DIR)
                            if (filename.isNotBlank()) {
                                val dest = File(context.filesDir, "$WALLPAPER_BUNDLE_DIR/dark/$filename")
                                dest.parentFile?.mkdirs()
                                dest.writeBytes(zis.readBytes())
                                darkDir = dest.parentFile
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            val json = layoutJson ?: throw IllegalArgumentException("layout.json not found in bundle")
            BundleImportResult(
                layoutJson = json,
                iconsImported = iconsImported,
                lightWallpaperDir = lightDir,
                darkWallpaperDir = darkDir
            )
        }
    }

    private fun addDirectoryToZip(zos: ZipOutputStream, dir: File, prefix: String) {
        if (!dir.exists() || !dir.isDirectory) return
        dir.listFiles()?.filter { it.isFile }?.forEach { file ->
            zos.putNextEntry(ZipEntry("$prefix${file.name}"))
            file.inputStream().use { it.copyTo(zos) }
            zos.closeEntry()
        }
    }

    private suspend fun addWallpaperFolderToZip(
        context: Context,
        settingsManager: SettingsManager,
        zos: ZipOutputStream
    ) {
        listOf(
            settingsManager.mainScreenWallpaperLightFolderUriFlow to WALLPAPER_LIGHT_DIR,
            settingsManager.mainScreenWallpaperDarkFolderUriFlow to WALLPAPER_DARK_DIR
        ).forEach { (flow, zipDir) ->
            val folderUriStr = flow.first()
            if (folderUriStr.isBlank()) return@forEach
            val uri = Uri.parse(folderUriStr)
            if (uri.scheme == "file") {
                val dir = File(uri.path ?: return@forEach)
                addDirectoryToZip(zos, dir, zipDir)
            }
        }
    }
}
