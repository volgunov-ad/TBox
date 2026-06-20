package vad.dashing.tbox

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Per-theme materialized cache under [filesDir]/[THEMES_ROOT_DIR]/{cacheKey}/.
 * Materialization unpacks zip assets once; activation loads JSON and applies asset paths from cache.
 */
object ThemeMaterialization {

    private val themeDiskMutex = Mutex()

    const val THEMES_ROOT_DIR = "themes"
    const val MANIFEST_FILE = "manifest.json"
    const val THEME_JSON_FILE = "theme.json"
    const val WALLPAPER_LIGHT_DIR = "wallpaper/light"
    const val WALLPAPER_DARK_DIR = "wallpaper/dark"
    const val ICONS_DIR = "icons"
    const val TILE_BACKGROUNDS_DIR = "tile_backgrounds"

    data class ThemeManifest(
        val cacheKey: String,
        val sourceUri: String,
        val sourceDisplayName: String,
        val materializedAtMillis: Long,
        val fingerprint: String,
        val sections: Set<ThemeSection>,
    )

    data class MaterializeResult(
        val manifest: ThemeManifest,
        val iconsWritten: Int,
        val tileBackgroundsWritten: Int,
        val lightWallpaperCount: Int,
        val darkWallpaperCount: Int,
    )

    fun themesRootDir(context: Context): File = File(context.filesDir, THEMES_ROOT_DIR)

    fun cacheDir(context: Context, cacheKey: String): File =
        File(themesRootDir(context), ThemeCacheKeys.sanitizeCacheKey(cacheKey))

    fun isMaterialized(context: Context, cacheKey: String): Boolean {
        val dir = cacheDir(context, cacheKey)
        return File(dir, MANIFEST_FILE).isFile && File(dir, THEME_JSON_FILE).isFile
    }

    fun readManifest(context: Context, cacheKey: String): ThemeManifest? {
        val file = File(cacheDir(context, cacheKey), MANIFEST_FILE)
        if (!file.isFile) return null
        return runCatching { parseManifest(JSONObject(file.readText())) }.getOrNull()
    }

    fun displayNameForCacheKey(cacheKey: String): String = cacheKey.trim()

    suspend fun materializeFromBytes(
        context: Context,
        bytes: ByteArray,
        cacheKey: String,
        sourceUri: String,
        syncExisting: Boolean,
    ): Result<MaterializeResult> = withContext(Dispatchers.IO) {
        themeDiskMutex.withLock {
            runCatching {
                materializeFromBytesLocked(
                    context = context,
                    bytes = bytes,
                    cacheKey = cacheKey,
                    sourceUri = sourceUri,
                    syncExisting = syncExisting,
                )
            }
        }
    }

    suspend fun materializeAndActivateFromCache(
        context: Context,
        settingsManager: SettingsManager,
        settingsViewModel: SettingsViewModel?,
        bytes: ByteArray,
        cacheKey: String,
        sourceUri: String,
        syncExisting: Boolean,
    ): Result<ThemeApply.ApplyResult> = settingsManager.runWithThemeActivation {
        withContext(Dispatchers.IO) {
            themeDiskMutex.withLock {
                runCatching {
                    materializeFromBytesLocked(
                        context = context,
                        bytes = bytes,
                        cacheKey = cacheKey,
                        sourceUri = sourceUri,
                        syncExisting = syncExisting,
                    )
                    activateFromCacheLocked(
                        context = context,
                        settingsManager = settingsManager,
                        settingsViewModel = settingsViewModel,
                        cacheKey = cacheKey,
                    ).getOrThrow()
                }
            }
        }
    }

    suspend fun materializeAndActivateDriveModeFromCache(
        context: Context,
        settingsManager: SettingsManager,
        cacheKey: String,
        sourceUri: String,
    ): Result<ThemeApply.ApplyResult> {
        if (!isMaterialized(context, cacheKey)) {
            return Result.failure(IllegalArgumentException("theme_cache_missing"))
        }
        return activateFromCache(
            context = context,
            settingsManager = settingsManager,
            settingsViewModel = null,
            cacheKey = cacheKey,
        )
    }

    private fun materializeFromBytesLocked(
        context: Context,
        bytes: ByteArray,
        cacheKey: String,
        sourceUri: String,
        syncExisting: Boolean,
    ): MaterializeResult {
        val dir = cacheDir(context, cacheKey)
        dir.mkdirs()
        val parsed = ThemeBundleExport.parseBundleBytes(bytes).getOrThrow()
        File(dir, THEME_JSON_FILE).writeText(parsed.themeJson)

        val iconsWritten = syncAssetDirectory(
            targetDir = File(dir, ICONS_DIR),
            archiveFiles = parsed.icons,
            syncExisting = syncExisting,
        )
        val tileBackgroundsWritten = syncAssetDirectory(
            targetDir = File(dir, TILE_BACKGROUNDS_DIR),
            archiveFiles = parsed.tileBackgrounds,
            syncExisting = syncExisting,
        )
        val lightWallpaperCount = syncAssetDirectory(
            targetDir = File(dir, WALLPAPER_LIGHT_DIR),
            archiveFiles = parsed.lightWallpapers,
            syncExisting = syncExisting,
        )
        val darkWallpaperCount = syncAssetDirectory(
            targetDir = File(dir, WALLPAPER_DARK_DIR),
            archiveFiles = parsed.darkWallpapers,
            syncExisting = syncExisting,
        )

        val sections = ThemeLayoutExport.parseSectionsFromThemeJson(parsed.themeJson)
        val fingerprint = ThemeFingerprint.sha256(parsed.themeJson)
        val manifest = ThemeManifest(
            cacheKey = cacheKey,
            sourceUri = sourceUri.trim(),
            sourceDisplayName = ThemeFileResolver.displayName(sourceUri),
            materializedAtMillis = System.currentTimeMillis(),
            fingerprint = fingerprint,
            sections = sections,
        )
        writeManifest(dir, manifest)
        ThemeRuntimeState.seedFromThemeJsonIfMissing(dir, parsed.themeJson)
        return MaterializeResult(
            manifest = manifest,
            iconsWritten = iconsWritten,
            tileBackgroundsWritten = tileBackgroundsWritten,
            lightWallpaperCount = lightWallpaperCount,
            darkWallpaperCount = darkWallpaperCount,
        )
    }

    suspend fun activateFromCache(
        context: Context,
        settingsManager: SettingsManager,
        settingsViewModel: SettingsViewModel?,
        cacheKey: String,
    ): Result<ThemeApply.ApplyResult> = settingsManager.runWithThemeActivation {
        withContext(Dispatchers.IO) {
            themeDiskMutex.withLock {
                activateFromCacheLocked(
                    context = context,
                    settingsManager = settingsManager,
                    settingsViewModel = settingsViewModel,
                    cacheKey = cacheKey,
                )
            }
        }
    }

    private suspend fun activateFromCacheLocked(
        context: Context,
        settingsManager: SettingsManager,
        settingsViewModel: SettingsViewModel?,
        cacheKey: String,
    ): Result<ThemeApply.ApplyResult> {
        return runCatching {
            val dir = cacheDir(context, cacheKey)
            val manifest = readManifest(context, cacheKey)
                ?: throw IllegalArgumentException("theme_cache_missing")
            val themeJson = File(dir, THEME_JSON_FILE).readText()
            ThemeRuntimeState.seedFromThemeJsonIfMissing(dir, themeJson)
            val sections = ThemeSection.parseJsonArray(
                runCatching { JSONObject(themeJson) }.getOrNull()?.optJSONArray("sections"),
            ).ifEmpty { manifest.sections }

            settingsManager.saveActiveTheme(
                uri = cacheKey,
                fingerprint = manifest.fingerprint,
                sections = sections,
            )

            val importResult = ThemeLayoutExport.importJson(context, settingsManager, themeJson)
            if (importResult.isFailure) {
                throw importResult.exceptionOrNull() ?: IllegalArgumentException("theme_import_failed")
            }

            ThemeRuntimeState.applyActivationOverrides(
                settingsManager = settingsManager,
                cacheDir = dir,
                themeJson = themeJson,
            )

            applyWallpaperDirsFromCache(settingsManager, dir, sections)
            settingsManager.bumpMainScreenWallpaperRevision()

            settingsManager.bumpLauncherAppIconRevision()
            settingsManager.bumpTileBackgroundImageRevision()

            val iconsInTheme = if (ThemeSection.APP_ICONS in sections) {
                LauncherAppIconPaths.countThemeCacheIcons(context.filesDir, cacheKey)
            } else {
                0
            }
            val tileBackgroundsInTheme = if (
                ThemeSection.MAIN_SCREEN in sections || ThemeSection.FLOATING_PANELS in sections
            ) {
                TileBackgroundImageStorage.countThemeCacheFiles(context.filesDir, cacheKey)
            } else {
                0
            }

            ThemeApply.ApplyResult(
                sections = sections,
                iconsImported = iconsInTheme,
                tileBackgroundsImported = tileBackgroundsInTheme,
            )
        }
    }

    suspend fun clearThemeCachesExcept(
        context: Context,
        settingsManager: SettingsManager,
        keepCacheKey: String?,
    ) = withContext(Dispatchers.IO) {
        settingsManager.runWithThemeActivation {
            themeDiskMutex.withLock {
                clearThemeCachesExceptLocked(context, keepCacheKey)
            }
        }
    }

    private fun clearThemeCachesExceptLocked(context: Context, keepCacheKey: String?) {
        val keep = keepCacheKey?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { ThemeCacheKeys.sanitizeCacheKey(it) }
        val root = themesRootDir(context)
        if (!root.isDirectory) return
        root.listFiles()?.forEach { child ->
            if (!child.isDirectory) return@forEach
            if (keep != null && child.name == keep) return@forEach
            child.deleteRecursively()
        }
    }

    internal fun wallpaperFolderUriFromCacheDir(dir: File): String? {
        return if (dir.isDirectory && dir.listFiles()?.any { it.isFile } == true) {
            Uri.fromFile(dir).toString()
        } else {
            null
        }
    }

    fun formatRuntimeJsonDebugText(
        context: Context,
        cacheKeyRaw: String,
        dataStoreSelections: MainScreenWallpaperSelectionsByPage,
    ): String {
        val cacheKey = cacheKeyRaw.trim()
        if (cacheKey.isEmpty()) {
            return context.getString(R.string.themes_runtime_debug_no_active_theme)
        }
        if (!ThemeCacheKeys.isLikelyCacheKey(cacheKey)) {
            return context.getString(R.string.themes_runtime_debug_not_cache_key, cacheKey)
        }
        if (!isMaterialized(context, cacheKey)) {
            return context.getString(R.string.themes_runtime_debug_cache_missing, cacheKey)
        }
        val dir = cacheDir(context, cacheKey)
        val runtimeFile = File(dir, ThemeRuntimeState.RUNTIME_JSON_FILE)
        val raw = ThemeRuntimeState.readRawText(dir)
        return buildString {
            append(context.getString(R.string.themes_runtime_debug_cache_key_label))
            append(": ")
            appendLine(cacheKey)
            append(context.getString(R.string.themes_runtime_debug_file_path_label))
            append(": ")
            appendLine(runtimeFile.absolutePath)
            appendLine()
            append(context.getString(R.string.themes_runtime_debug_datastore_label))
            appendLine()
            appendLine(dataStoreSelections.toJson().toString(2))
            appendLine()
            append(context.getString(R.string.themes_runtime_debug_runtime_label))
            appendLine()
            if (raw == null) {
                append(context.getString(R.string.themes_runtime_debug_file_missing))
            } else {
                append(raw)
            }
        }
    }

    suspend fun syncWallpaperSelectionToActiveThemeCache(
        context: Context,
        settingsManager: SettingsManager,
        wallpaperSelections: MainScreenWallpaperSelectionsByPage? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        if (wallpaperSelections == null) return@withContext false
        syncRuntimeStateToThemeCache(
            context = context,
            cacheKey = settingsManager.activeThemeUriFlow.first().trim(),
            wallpaperSelections = wallpaperSelections,
        )
    }

    suspend fun syncCurrentPageToActiveThemeCache(
        context: Context,
        settingsManager: SettingsManager,
        currentPage: Int,
    ): Boolean = withContext(Dispatchers.IO) {
        syncRuntimeStateToThemeCache(
            context = context,
            cacheKey = settingsManager.activeThemeUriFlow.first().trim(),
            currentPage = currentPage,
        )
    }

    suspend fun syncRuntimeStateToThemeCache(
        context: Context,
        cacheKey: String,
        wallpaperSelections: MainScreenWallpaperSelectionsByPage? = null,
        currentPage: Int? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        val normalizedKey = cacheKey.trim()
        if (!ThemeCacheKeys.isLikelyCacheKey(normalizedKey)) return@withContext false
        if (!isMaterialized(context, normalizedKey)) return@withContext false
        val dir = cacheDir(context, normalizedKey)
        ThemeRuntimeState.patch(
            cacheDir = dir,
            wallpaperSelections = wallpaperSelections,
            currentPage = currentPage,
        )
        true
    }

    private suspend fun applyWallpaperDirsFromCache(
        settingsManager: SettingsManager,
        cacheDir: File,
        sections: Set<ThemeSection>,
    ) {
        if (ThemeSection.MAIN_SCREEN !in sections) return

        val lightUri = wallpaperFolderUriFromCacheDir(File(cacheDir, WALLPAPER_LIGHT_DIR))
        if (lightUri != null) {
            settingsManager.saveMainScreenWallpaperLightFolderUri(lightUri)
        }

        val darkUri = wallpaperFolderUriFromCacheDir(File(cacheDir, WALLPAPER_DARK_DIR))
        if (darkUri != null) {
            settingsManager.saveMainScreenWallpaperDarkFolderUri(darkUri)
        }
        settingsManager.bumpMainScreenWallpaperRevision()
    }

    /**
     * Writes [archiveFiles]; when [syncExisting] is true, keeps existing same-name files and
     * deletes cache files that are not present in the archive.
     */
    private fun syncAssetDirectory(
        targetDir: File,
        archiveFiles: Map<String, ByteArray>,
        syncExisting: Boolean,
    ): Int {
        targetDir.mkdirs()
        var written = 0
        archiveFiles.forEach { (name, bytes) ->
            val dest = File(targetDir, name)
            if (syncExisting && dest.isFile) {
                return@forEach
            }
            dest.parentFile?.mkdirs()
            dest.writeBytes(bytes)
            written++
        }
        if (syncExisting) {
            val archiveNames = archiveFiles.keys.toSet()
            if (targetDir.isDirectory) {
                targetDir.walkTopDown().filter { it.isFile }.forEach { existing ->
                    val rel = existing.relativeTo(targetDir).path.replace('\\', '/')
                    if (rel !in archiveNames) {
                        existing.delete()
                    }
                }
            }
        }
        return written
    }

    private fun writeManifest(dir: File, manifest: ThemeManifest) {
        val json = JSONObject()
        json.put("cacheKey", manifest.cacheKey)
        json.put("sourceUri", manifest.sourceUri)
        json.put("sourceDisplayName", manifest.sourceDisplayName)
        json.put("materializedAtMillis", manifest.materializedAtMillis)
        json.put("fingerprint", manifest.fingerprint)
        json.put("sections", ThemeSection.toJsonArray(manifest.sections))
        File(dir, MANIFEST_FILE).writeText(json.toString(2))
    }

    private fun parseManifest(obj: JSONObject): ThemeManifest {
        return ThemeManifest(
            cacheKey = obj.optString("cacheKey"),
            sourceUri = obj.optString("sourceUri"),
            sourceDisplayName = obj.optString("sourceDisplayName"),
            materializedAtMillis = obj.optLong("materializedAtMillis"),
            fingerprint = obj.optString("fingerprint"),
            sections = ThemeSection.parseJsonArray(obj.optJSONArray("sections")),
        )
    }
}
