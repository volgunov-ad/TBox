package vad.dashing.tbox

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import kotlinx.coroutines.flow.first
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    private const val ASSETS_HTTP_REQUEST_ICONS_DIR = "assets/http_request_icons/"
    private const val ASSETS_TILE_BG_DIR = "assets/tile_backgrounds/"
    private const val ASSETS_PANEL_BG_DIR = "assets/panel_backgrounds/"
    const val THEME_WALLPAPER_IMPORT_DIR = "themes/imported_wallpaper"

    data class ThemeExtractResult(
        val themeJson: String,
        val iconsImported: Int,
        val httpRequestIconsImported: Int,
        val tileBackgroundsImported: Int,
        val lightWallpaperDir: File?,
        val darkWallpaperDir: File?,
    )

    data class ParsedThemeBundle(
        val themeJson: String,
        val icons: Map<String, ByteArray>,
        val httpRequestIcons: Map<String, ByteArray>,
        val tileBackgrounds: Map<String, ByteArray>,
        val panelBackgrounds: Map<String, ByteArray> = emptyMap(),
        val lightWallpapers: Map<String, ByteArray>,
        val darkWallpapers: Map<String, ByteArray>,
    )

    fun parseBundleBytes(bytes: ByteArray): Result<ParsedThemeBundle> {
        return runCatching {
            if (!looksLikeZipArchive(bytes)) {
                throw IllegalArgumentException("not_a_zip_archive")
            }
            val state = ZipBundleReadState()
            ZipInputStream(bytes.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    readZipBundleEntry(state, entry, zis)
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            val json = state.themeJson ?: throw IllegalArgumentException("theme.json not found")
            ParsedThemeBundle(
                themeJson = json,
                icons = state.icons,
                httpRequestIcons = state.httpRequestIcons,
                tileBackgrounds = state.tileBackgrounds,
                panelBackgrounds = state.panelBackgrounds,
                lightWallpapers = state.lightWallpapers,
                darkWallpapers = state.darkWallpapers,
            )
        }
    }

    internal fun normalizeZipEntryPath(raw: String): String {
        var path = raw.replace('\\', '/').trim()
        while (path.startsWith("./")) {
            path = path.removePrefix("./")
        }
        return path.trimStart('/')
    }

    internal fun themeJsonZipEntryPriority(normalizedPath: String): Int? {
        return when {
            normalizedPath == THEME_JSON_ENTRY -> 0
            normalizedPath.endsWith("/$THEME_JSON_ENTRY") -> normalizedPath.length
            else -> null
        }
    }

    internal fun looksLikeZipArchive(bytes: ByteArray): Boolean {
        return bytes.size >= 2 &&
            bytes[0] == 'P'.code.toByte() &&
            bytes[1] == 'K'.code.toByte()
    }

    private data class ZipBundleReadState(
        var themeJson: String? = null,
        var themeJsonPriority: Int = Int.MAX_VALUE,
        val icons: MutableMap<String, ByteArray> = linkedMapOf(),
        val httpRequestIcons: MutableMap<String, ByteArray> = linkedMapOf(),
        val tileBackgrounds: MutableMap<String, ByteArray> = linkedMapOf(),
        val panelBackgrounds: MutableMap<String, ByteArray> = linkedMapOf(),
        val lightWallpapers: MutableMap<String, ByteArray> = linkedMapOf(),
        val darkWallpapers: MutableMap<String, ByteArray> = linkedMapOf(),
    )

    private fun zipAssetSuffix(normalizedPath: String, assetsDir: String): String? {
        val idx = normalizedPath.indexOf(assetsDir)
        if (idx < 0) return null
        val suffix = normalizedPath.substring(idx + assetsDir.length)
        return suffix.takeIf { it.isNotBlank() && !suffix.endsWith('/') }
    }

    private fun readZipBundleEntry(state: ZipBundleReadState, entry: ZipEntry, zis: ZipInputStream) {
        if (entry.isDirectory) return
        val normalized = normalizeZipEntryPath(entry.name)
        themeJsonZipEntryPriority(normalized)?.let { priority ->
            if (priority < state.themeJsonPriority) {
                state.themeJson = zis.readBytes().toString(Charsets.UTF_8)
                state.themeJsonPriority = priority
            }
            return
        }
        zipAssetSuffix(normalized, ASSETS_ICONS_DIR)?.let { filename ->
            state.icons[filename] = zis.readBytes()
            return
        }
        zipAssetSuffix(normalized, ASSETS_HTTP_REQUEST_ICONS_DIR)?.let { filename ->
            state.httpRequestIcons[filename] = zis.readBytes()
            return
        }
        zipAssetSuffix(normalized, ASSETS_TILE_BG_DIR)?.let { rel ->
            if (TileBackgroundImageStorage.isAllowedStoredRelPath(
                    "${TileBackgroundImageStorage.DIR_NAME}/$rel"
                )
            ) {
                state.tileBackgrounds[rel] = zis.readBytes()
            }
            return
        }
        zipAssetSuffix(normalized, ASSETS_PANEL_BG_DIR)?.let { rel ->
            if (PanelBackgroundImageStorage.isAllowedStoredRelPath(
                    "${PanelBackgroundImageStorage.DIR_NAME}/$rel"
                )
            ) {
                state.panelBackgrounds[rel] = zis.readBytes()
            }
            return
        }
        zipAssetSuffix(normalized, ASSETS_WALLPAPER_LIGHT_DIR)?.let { filename ->
            state.lightWallpapers[filename] = zis.readBytes()
            return
        }
        zipAssetSuffix(normalized, ASSETS_WALLPAPER_DARK_DIR)?.let { filename ->
            state.darkWallpapers[filename] = zis.readBytes()
        }
    }

    @JvmName("exportBundleFromSections")
    suspend fun exportBundle(
        context: Context,
        settingsManager: SettingsManager,
        sections: Set<ThemeSection>,
        output: OutputStream,
    ) {
        exportBundle(
            context = context,
            settingsManager = settingsManager,
            applyTargets = ThemeApplyTarget.fromLegacySections(sections),
            output = output,
        )
    }

    @JvmName("exportBundleFromApplyTargets")
    suspend fun exportBundle(
        context: Context,
        settingsManager: SettingsManager,
        applyTargets: Set<ThemeApplyTarget>,
        output: OutputStream,
    ) {
        val themeJson = ThemeLayoutExport.exportJson(context, settingsManager, applyTargets)
        val sections = ThemeApplyTarget.exportSectionsFromTargets(applyTargets)
        ZipOutputStream(output).use { zos ->
            putBytesEntry(zos, THEME_JSON_ENTRY, themeJson.toByteArray(Charsets.UTF_8))
            if (ThemeApplyTarget.APP_ICONS in applyTargets) {
                addAppIconsToZip(context, settingsManager, sections, zos)
                addHttpRequestIconsToZip(context, settingsManager, sections, zos)
            }
            if (
                ThemeApplyTarget.TILE_BACKGROUNDS in applyTargets &&
                (ThemeApplyTarget.MAIN_SCREEN_PANELS in applyTargets || ThemeApplyTarget.FLOATING_PANELS in applyTargets)
            ) {
                addTileBackgroundsToZip(context, settingsManager, sections, zos)
            }
            if (
                ThemeApplyTarget.MAIN_SCREEN_PANELS in applyTargets ||
                ThemeApplyTarget.FLOATING_PANELS in applyTargets
            ) {
                addPanelBackgroundsToZip(context, settingsManager, sections, zos)
            }
            if (ThemeApplyTarget.MAIN_SCREEN_WALLPAPERS in applyTargets) {
                addWallpaperFoldersToZip(context, settingsManager, zos)
            }
        }
    }

    @JvmName("exportBundleToFileFromSections")
    suspend fun exportBundleToFile(
        context: Context,
        settingsManager: SettingsManager,
        sections: Set<ThemeSection>,
        file: File,
    ) {
        exportBundleToFile(
            context = context,
            settingsManager = settingsManager,
            applyTargets = ThemeApplyTarget.fromLegacySections(sections),
            file = file,
        )
    }

    @JvmName("exportBundleToFileFromApplyTargets")
    suspend fun exportBundleToFile(
        context: Context,
        settingsManager: SettingsManager,
        applyTargets: Set<ThemeApplyTarget>,
        file: File,
    ) {
        file.parentFile?.mkdirs()
        file.outputStream().buffered().use { output ->
            exportBundle(context, settingsManager, applyTargets, output)
        }
    }

    fun defaultThemeExportBaseName(): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
        return "theme_$timestamp"
    }

    fun sanitizeThemeExportBaseName(input: String): String? {
        var name = input.trim()
        if (name.endsWith(".$THEME_FILE_EXTENSION", ignoreCase = true)) {
            name = name.dropLast(THEME_FILE_EXTENSION.length + 1).trim()
        }
        name = name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim('.')
        if (name.isBlank() || name == "." || name == "..") return null
        if (name.length > 120) name = name.take(120)
        return name
    }

    fun themeFileNameFromBaseName(baseName: String): String = "$baseName.$THEME_FILE_EXTENSION"

    fun downloadsDir(): File {
        val savePath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
        } else {
            Environment.getExternalStorageDirectory().absolutePath + "/Download"
        }
        return File(savePath).also { it.mkdirs() }
    }

    fun downloadsThemeExportFile(baseName: String): File =
        File(downloadsDir(), themeFileNameFromBaseName(baseName))

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
        val iconsDir = File(context.filesDir, SettingsManager.LAUNCHER_APP_ICONS_DIR)
        val httpRequestIconsDir = File(context.filesDir, SettingsManager.HTTP_REQUEST_ICONS_DIR)
        var iconsImported = 0
        var httpRequestIconsImported = 0
        var tileBackgroundsImported = 0
        var lightDir: File? = null
        var darkDir: File? = null

        return runCatching {
            if (!looksLikeZipArchive(bytes)) {
                throw IllegalArgumentException("not_a_zip_archive")
            }
            val state = ZipBundleReadState()
            ZipInputStream(bytes.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    readZipBundleEntry(state, entry, zis)
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            state.icons.forEach { (filename, data) ->
                iconsDir.mkdirs()
                val liveName = LauncherAppIconPaths.liveFileNameFromThemeAsset(filename)
                File(iconsDir, liveName).writeBytes(data)
                iconsImported++
            }
            state.httpRequestIcons.forEach { (filename, data) ->
                httpRequestIconsDir.mkdirs()
                File(httpRequestIconsDir, filename).writeBytes(data)
                httpRequestIconsImported++
            }
            state.tileBackgrounds.forEach { (rel, data) ->
                val dest = File(context.filesDir, "${TileBackgroundImageStorage.DIR_NAME}/$rel")
                dest.parentFile?.mkdirs()
                dest.writeBytes(data)
                tileBackgroundsImported++
            }
            state.lightWallpapers.forEach { (filename, data) ->
                val dest = File(context.filesDir, "$THEME_WALLPAPER_IMPORT_DIR/light/$filename")
                dest.parentFile?.mkdirs()
                dest.writeBytes(data)
                lightDir = dest.parentFile
            }
            state.darkWallpapers.forEach { (filename, data) ->
                val dest = File(context.filesDir, "$THEME_WALLPAPER_IMPORT_DIR/dark/$filename")
                dest.parentFile?.mkdirs()
                dest.writeBytes(data)
                darkDir = dest.parentFile
            }
            val json = state.themeJson ?: throw IllegalArgumentException("theme.json not found")
            ThemeExtractResult(
                themeJson = json,
                iconsImported = iconsImported,
                httpRequestIconsImported = httpRequestIconsImported,
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

    private suspend fun addHttpRequestIconsToZip(
        context: Context,
        settingsManager: SettingsManager,
        sections: Set<ThemeSection>,
        zos: ZipOutputStream,
    ) {
        val lookup = settingsManager.launcherAppIconLookup()
        val keys = linkedSetOf<String>()
        if (ThemeSection.MAIN_SCREEN in sections) {
            settingsManager.mainScreenDashboardsFlow.first().forEach { panel ->
                keys.addAll(ThemeLayoutExport.collectHttpRequestIconKeys(panel.id, panel.widgetsConfig))
            }
        }
        if (ThemeSection.FLOATING_PANELS in sections) {
            settingsManager.floatingDashboardsFlow.first().forEach { panel ->
                keys.addAll(ThemeLayoutExport.collectHttpRequestIconKeys(panel.id, panel.widgetsConfig))
            }
        }
        val filesDir = context.filesDir
        keys.forEach { key ->
            val file = HttpRequestIconPaths.resolveIconFile(filesDir, key, lookup) ?: return@forEach
            putFileEntry(zos, "$ASSETS_HTTP_REQUEST_ICONS_DIR$key", file)
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

    private suspend fun addPanelBackgroundsToZip(
        context: Context,
        settingsManager: SettingsManager,
        sections: Set<ThemeSection>,
        zos: ZipOutputStream,
    ) {
        val relPaths = linkedSetOf<String>()
        if (ThemeSection.MAIN_SCREEN in sections) {
            settingsManager.mainScreenDashboardsFlow.first().forEach { panel ->
                relPaths.addAll(ThemeLayoutExport.collectPanelBackgroundPathsFromMain(panel))
            }
        }
        if (ThemeSection.FLOATING_PANELS in sections) {
            settingsManager.floatingDashboardsFlow.first().forEach { panel ->
                relPaths.addAll(ThemeLayoutExport.collectPanelBackgroundPathsFromFloating(panel))
            }
        }
        val lookup = settingsManager.launcherAppIconLookup()
        relPaths.forEach { relPath ->
            val file = PanelBackgroundImageStorage.resolveFile(
                context.filesDir,
                relPath,
                lookup,
            ) ?: return@forEach
            if (!file.isFile) return@forEach
            val zipRel = relPath.removePrefix("${PanelBackgroundImageStorage.DIR_NAME}/")
            putFileEntry(zos, "$ASSETS_PANEL_BG_DIR$zipRel", file)
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
