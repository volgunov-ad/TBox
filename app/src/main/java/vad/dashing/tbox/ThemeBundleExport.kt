package vad.dashing.tbox

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.first
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ThemeBundleExport {

    const val THEME_FILE_EXTENSION = "tboxtheme"
    /** Skip individual zip entries larger than this (same cap as main-screen wallpaper files). */
    private const val MAX_ENTRY_BYTES = MAIN_SCREEN_WALLPAPER_MAX_FILE_BYTES
    private const val THEME_JSON_ENTRY = "theme.json"
    const val ASSETS_WALLPAPER_LIGHT_DIR = "assets/wallpaper/light/"
    const val ASSETS_WALLPAPER_DARK_DIR = "assets/wallpaper/dark/"
    private const val ASSETS_ICONS_DIR = "assets/icons/"
    private const val ASSETS_TILE_BG_DIR = "assets/tile_backgrounds/"
    const val THEME_WALLPAPER_IMPORT_DIR = "themes/imported_wallpaper"

    data class ThemeExtractResult(
        val themeJson: String,
        val iconsImported: Int,
        val tileBackgroundsImported: Int,
        val lightWallpaperDir: File?,
        val darkWallpaperDir: File?,
    )

    data class ParsedThemeBundle(
        val themeJson: String,
        val icons: Map<String, ByteArray>,
        val tileBackgrounds: Map<String, ByteArray>,
        val lightWallpapers: Map<String, ByteArray>,
        val darkWallpapers: Map<String, ByteArray>,
    )

    fun parseBundleBytes(bytes: ByteArray): Result<ParsedThemeBundle> {
        var themeJson: String? = null
        val icons = linkedMapOf<String, ByteArray>()
        val tileBackgrounds = linkedMapOf<String, ByteArray>()
        val lightWallpapers = linkedMapOf<String, ByteArray>()
        val darkWallpapers = linkedMapOf<String, ByteArray>()

        return runCatching {
            ZipInputStream(bytes.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    when {
                        entry.name == THEME_JSON_ENTRY -> {
                            themeJson = zis.readBytes().toString(Charsets.UTF_8)
                        }
                        entry.name.startsWith(ASSETS_ICONS_DIR) && !entry.isDirectory -> {
                            val filename = entry.name.removePrefix(ASSETS_ICONS_DIR)
                            if (filename.isNotBlank()) {
                                icons[filename] = zis.readBytes()
                            }
                        }
                        entry.name.startsWith(ASSETS_TILE_BG_DIR) && !entry.isDirectory -> {
                            val rel = entry.name.removePrefix(ASSETS_TILE_BG_DIR)
                            if (rel.isNotBlank() && TileBackgroundImageStorage.isAllowedStoredRelPath(
                                    "${TileBackgroundImageStorage.DIR_NAME}/$rel"
                                )
                            ) {
                                tileBackgrounds[rel] = zis.readBytes()
                            }
                        }
                        entry.name.startsWith(ASSETS_WALLPAPER_LIGHT_DIR) && !entry.isDirectory -> {
                            val filename = entry.name.removePrefix(ASSETS_WALLPAPER_LIGHT_DIR)
                            if (filename.isNotBlank()) {
                                lightWallpapers[filename] = zis.readBytes()
                            }
                        }
                        entry.name.startsWith(ASSETS_WALLPAPER_DARK_DIR) && !entry.isDirectory -> {
                            val filename = entry.name.removePrefix(ASSETS_WALLPAPER_DARK_DIR)
                            if (filename.isNotBlank()) {
                                darkWallpapers[filename] = zis.readBytes()
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            val json = themeJson ?: throw IllegalArgumentException("theme.json not found")
            ParsedThemeBundle(
                themeJson = json,
                icons = icons,
                tileBackgrounds = tileBackgrounds,
                lightWallpapers = lightWallpapers,
                darkWallpapers = darkWallpapers,
            )
        }
    }

    suspend fun exportBundle(
        context: Context,
        settingsManager: SettingsManager,
        sections: Set<ThemeSection>,
        output: OutputStream,
    ) {
        val themeJson = ThemeLayoutExport.exportJson(context, settingsManager, sections)
        ZipOutputStream(output).use { zos ->
            putBytesEntry(zos, THEME_JSON_ENTRY, themeJson.toByteArray(Charsets.UTF_8))
            if (ThemeSection.APP_ICONS in sections) {
                addAppIconsToZip(context, settingsManager, sections, zos)
            }
            if (ThemeSection.MAIN_SCREEN in sections || ThemeSection.FLOATING_PANELS in sections) {
                addTileBackgroundsToZip(context, settingsManager, sections, zos)
            }
            if (ThemeSection.MAIN_SCREEN in sections) {
                addWallpaperFoldersToZip(context, settingsManager, zos)
            }
        }
    }

    suspend fun exportBundleToBytes(
        context: Context,
        settingsManager: SettingsManager,
        sections: Set<ThemeSection>,
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        exportBundle(context, settingsManager, sections, baos)
        return baos.toByteArray()
    }

    fun extractBundle(context: Context, bytes: ByteArray): Result<ThemeExtractResult> {
        var themeJson: String? = null
        val iconsDir = File(context.filesDir, SettingsManager.LAUNCHER_APP_ICONS_DIR)
        var iconsImported = 0
        var tileBackgroundsImported = 0
        var lightDir: File? = null
        var darkDir: File? = null

        return runCatching {
            ZipInputStream(bytes.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    when {
                        entry.name == THEME_JSON_ENTRY -> {
                            themeJson = zis.readBytes().toString(Charsets.UTF_8)
                        }
                        entry.name.startsWith(ASSETS_ICONS_DIR) && !entry.isDirectory -> {
                            val filename = entry.name.removePrefix(ASSETS_ICONS_DIR)
                            if (filename.isNotBlank()) {
                                iconsDir.mkdirs()
                                val liveName = LauncherAppIconPaths.liveFileNameFromThemeAsset(filename)
                                File(iconsDir, liveName).writeBytes(zis.readBytes())
                                iconsImported++
                            }
                        }
                        entry.name.startsWith(ASSETS_TILE_BG_DIR) && !entry.isDirectory -> {
                            val rel = entry.name.removePrefix(ASSETS_TILE_BG_DIR)
                            if (rel.isNotBlank() && TileBackgroundImageStorage.isAllowedStoredRelPath(
                                    "${TileBackgroundImageStorage.DIR_NAME}/$rel"
                                )
                            ) {
                                val dest = File(context.filesDir, "${TileBackgroundImageStorage.DIR_NAME}/$rel")
                                dest.parentFile?.mkdirs()
                                dest.writeBytes(zis.readBytes())
                                tileBackgroundsImported++
                            }
                        }
                        entry.name.startsWith(ASSETS_WALLPAPER_LIGHT_DIR) && !entry.isDirectory -> {
                            val filename = entry.name.removePrefix(ASSETS_WALLPAPER_LIGHT_DIR)
                            if (filename.isNotBlank()) {
                                val dest = File(context.filesDir, "$THEME_WALLPAPER_IMPORT_DIR/light/$filename")
                                dest.parentFile?.mkdirs()
                                dest.writeBytes(zis.readBytes())
                                lightDir = dest.parentFile
                            }
                        }
                        entry.name.startsWith(ASSETS_WALLPAPER_DARK_DIR) && !entry.isDirectory -> {
                            val filename = entry.name.removePrefix(ASSETS_WALLPAPER_DARK_DIR)
                            if (filename.isNotBlank()) {
                                val dest = File(context.filesDir, "$THEME_WALLPAPER_IMPORT_DIR/dark/$filename")
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
            val json = themeJson ?: throw IllegalArgumentException("theme.json not found")
            ThemeExtractResult(
                themeJson = json,
                iconsImported = iconsImported,
                tileBackgroundsImported = tileBackgroundsImported,
                lightWallpaperDir = lightDir,
                darkWallpaperDir = darkDir,
            )
        }
    }

    private fun putBytesEntry(zos: ZipOutputStream, name: String, data: ByteArray) {
        zos.putNextEntry(ZipEntry(name))
        zos.write(data)
        zos.closeEntry()
    }

    private fun putFileEntry(zos: ZipOutputStream, name: String, file: File) {
        if (!file.isFile) return
        val size = file.length()
        if (size <= 0L || size > MAX_ENTRY_BYTES) return
        zos.putNextEntry(ZipEntry(name))
        file.inputStream().use { input -> input.copyTo(zos) }
        zos.closeEntry()
    }

    private fun putStreamEntry(zos: ZipOutputStream, name: String, input: InputStream) {
        zos.putNextEntry(ZipEntry(name))
        input.use { it.copyTo(zos) }
        zos.closeEntry()
    }

    private fun putUriEntry(context: Context, zos: ZipOutputStream, name: String, uri: Uri) {
        when (uri.scheme?.lowercase()) {
            "file" -> {
                val path = uri.path ?: return
                putFileEntry(zos, name, File(path))
            }
            else -> {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return
                pfd.use {
                    val size = it.statSize
                    if (size <= 0L || size > MAX_ENTRY_BYTES) return
                    FileInputStream(it.fileDescriptor).use { input ->
                        putStreamEntry(zos, name, input)
                    }
                }
            }
        }
    }

    private suspend fun addAppIconsToZip(
        context: Context,
        settingsManager: SettingsManager,
        sections: Set<ThemeSection>,
        zos: ZipOutputStream,
    ) {
        val lookup = settingsManager.launcherAppIconLookup()
        val packages = ThemeLayoutExport.collectPackagesForSections(context, settingsManager, sections, lookup)
        val filesDir = context.filesDir
        packages.forEach { pkg ->
            val file = LauncherAppIconPaths.resolveIconFile(filesDir, pkg, lookup) ?: return@forEach
            putFileEntry(zos, "$ASSETS_ICONS_DIR$pkg", file)
        }
    }

    private suspend fun addTileBackgroundsToZip(
        context: Context,
        settingsManager: SettingsManager,
        sections: Set<ThemeSection>,
        zos: ZipOutputStream,
    ) {
        val relPaths = linkedSetOf<String>()
        if (ThemeSection.MAIN_SCREEN in sections) {
            settingsManager.mainScreenDashboardsFlow.first().forEach { panel ->
                relPaths.addAll(ThemeLayoutExport.collectTileBackgroundPaths(panel.widgetsConfig))
            }
        }
        if (ThemeSection.FLOATING_PANELS in sections) {
            settingsManager.floatingDashboardsFlow.first().forEach { panel ->
                relPaths.addAll(ThemeLayoutExport.collectTileBackgroundPaths(panel.widgetsConfig))
            }
        }
        val lookup = settingsManager.launcherAppIconLookup()
        relPaths.forEach { relPath ->
            val file = TileBackgroundImageStorage.resolveFile(
                context.filesDir,
                relPath,
                lookup,
            ) ?: return@forEach
            if (!file.isFile) return@forEach
            val zipRel = relPath.removePrefix("${TileBackgroundImageStorage.DIR_NAME}/")
            putFileEntry(zos, "$ASSETS_TILE_BG_DIR$zipRel", file)
        }
    }

    private suspend fun addWallpaperFoldersToZip(
        context: Context,
        settingsManager: SettingsManager,
        zos: ZipOutputStream,
    ) {
        listOf(
            settingsManager.mainScreenWallpaperLightFolderUriFlow to ASSETS_WALLPAPER_LIGHT_DIR,
            settingsManager.mainScreenWallpaperDarkFolderUriFlow to ASSETS_WALLPAPER_DARK_DIR,
        ).forEach { (flow, zipDir) ->
            val folderUriStr = flow.first()
            if (folderUriStr.isBlank()) return@forEach
            val folderUri = Uri.parse(folderUriStr)
            listSortedWallpaperImagesInFolder(context, folderUri).forEach { (name, fileUri) ->
                putUriEntry(context, zos, "$zipDir$name", fileUri)
            }
        }
    }
}
