package vad.dashing.tbox

import org.json.JSONObject
import java.io.File

/**
 * Per-theme runtime state stored beside [ThemeMaterialization.THEME_JSON_FILE] in the cache dir.
 * Holds last main-screen wallpaper folders, per-page file selections, and current page;
 * overrides [theme.json] on activation when present.
 */
object ThemeRuntimeState {

    const val RUNTIME_JSON_FILE = "runtime.json"

    const val KEY_WALLPAPER_SELECTION_BY_PAGE = MainScreenWallpaperSelectionsByPage.JSON_KEY
    const val KEY_WALLPAPER_LIGHT_FOLDER_URI = "wallpaperLightFolderUri"
    const val KEY_WALLPAPER_DARK_FOLDER_URI = "wallpaperDarkFolderUri"
    const val KEY_CURRENT_PAGE = "currentPage"

    data class State(
        val wallpaperSelections: MainScreenWallpaperSelectionsByPage? = null,
        val hasWallpaperSelections: Boolean = false,
        val wallpaperLightFolderUri: String? = null,
        val hasWallpaperLightFolderUri: Boolean = false,
        val wallpaperDarkFolderUri: String? = null,
        val hasWallpaperDarkFolderUri: Boolean = false,
        val currentPage: Int? = null,
        val hasCurrentPage: Boolean = false,
    ) {
        val isEmpty: Boolean
            get() = !hasWallpaperSelections &&
                !hasWallpaperLightFolderUri &&
                !hasWallpaperDarkFolderUri &&
                !hasCurrentPage
    }

    fun read(cacheDir: File): State {
        val file = File(cacheDir, RUNTIME_JSON_FILE)
        if (!file.isFile) return State()
        val obj = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return State()
        return State(
            wallpaperSelections = if (obj.has(KEY_WALLPAPER_SELECTION_BY_PAGE)) {
                MainScreenWallpaperSelectionsByPage.fromJson(obj.optJSONObject(KEY_WALLPAPER_SELECTION_BY_PAGE))
            } else {
                null
            },
            hasWallpaperSelections = obj.has(KEY_WALLPAPER_SELECTION_BY_PAGE),
            wallpaperLightFolderUri = if (obj.has(KEY_WALLPAPER_LIGHT_FOLDER_URI)) {
                obj.optString(KEY_WALLPAPER_LIGHT_FOLDER_URI, "")
            } else {
                null
            },
            hasWallpaperLightFolderUri = obj.has(KEY_WALLPAPER_LIGHT_FOLDER_URI),
            wallpaperDarkFolderUri = if (obj.has(KEY_WALLPAPER_DARK_FOLDER_URI)) {
                obj.optString(KEY_WALLPAPER_DARK_FOLDER_URI, "")
            } else {
                null
            },
            hasWallpaperDarkFolderUri = obj.has(KEY_WALLPAPER_DARK_FOLDER_URI),
            currentPage = if (obj.has(KEY_CURRENT_PAGE)) {
                obj.optInt(KEY_CURRENT_PAGE, SettingsManager.DEFAULT_MAIN_SCREEN_CURRENT_PAGE)
            } else {
                null
            },
            hasCurrentPage = obj.has(KEY_CURRENT_PAGE),
        )
    }

    fun write(cacheDir: File, state: State) {
        val file = File(cacheDir, RUNTIME_JSON_FILE)
        if (state.isEmpty) {
            file.delete()
            return
        }
        val json = JSONObject()
        if (state.hasWallpaperSelections) {
            json.put(
                KEY_WALLPAPER_SELECTION_BY_PAGE,
                state.wallpaperSelections?.toJson() ?: JSONObject(),
            )
        }
        if (state.hasWallpaperLightFolderUri) {
            json.put(KEY_WALLPAPER_LIGHT_FOLDER_URI, state.wallpaperLightFolderUri.orEmpty())
        }
        if (state.hasWallpaperDarkFolderUri) {
            json.put(KEY_WALLPAPER_DARK_FOLDER_URI, state.wallpaperDarkFolderUri.orEmpty())
        }
        if (state.hasCurrentPage) {
            json.put(KEY_CURRENT_PAGE, state.currentPage ?: SettingsManager.DEFAULT_MAIN_SCREEN_CURRENT_PAGE)
        }
        file.writeText(json.toString(2))
    }

    fun patch(
        cacheDir: File,
        wallpaperSelections: MainScreenWallpaperSelectionsByPage? = null,
        wallpaperLightFolderUri: String? = null,
        wallpaperDarkFolderUri: String? = null,
        patchWallpaperLightFolderUri: Boolean = false,
        patchWallpaperDarkFolderUri: Boolean = false,
        currentPage: Int? = null,
    ): State {
        val existing = read(cacheDir)
        val mergedSelections = when {
            wallpaperSelections != null -> wallpaperSelections
            existing.hasWallpaperSelections -> existing.wallpaperSelections
            else -> null
        }
        val merged = State(
            wallpaperSelections = mergedSelections,
            hasWallpaperSelections = wallpaperSelections != null || existing.hasWallpaperSelections,
            wallpaperLightFolderUri = when {
                patchWallpaperLightFolderUri -> wallpaperLightFolderUri.orEmpty()
                existing.hasWallpaperLightFolderUri -> existing.wallpaperLightFolderUri
                else -> null
            },
            hasWallpaperLightFolderUri = patchWallpaperLightFolderUri || existing.hasWallpaperLightFolderUri,
            wallpaperDarkFolderUri = when {
                patchWallpaperDarkFolderUri -> wallpaperDarkFolderUri.orEmpty()
                existing.hasWallpaperDarkFolderUri -> existing.wallpaperDarkFolderUri
                else -> null
            },
            hasWallpaperDarkFolderUri = patchWallpaperDarkFolderUri || existing.hasWallpaperDarkFolderUri,
            currentPage = currentPage ?: existing.currentPage,
            hasCurrentPage = currentPage != null || existing.hasCurrentPage,
        )
        write(cacheDir, merged)
        return merged
    }

    suspend fun applyOverrides(settingsManager: SettingsManager, state: State) {
        if (state.hasWallpaperSelections) {
            settingsManager.saveMainScreenWallpaperSelectionsByPage(
                state.wallpaperSelections ?: MainScreenWallpaperSelectionsByPage.empty(),
            )
        }
        if (state.hasWallpaperLightFolderUri) {
            settingsManager.saveMainScreenWallpaperLightFolderUri(
                state.wallpaperLightFolderUri?.takeIf { it.isNotBlank() },
            )
        }
        if (state.hasWallpaperDarkFolderUri) {
            settingsManager.saveMainScreenWallpaperDarkFolderUri(
                state.wallpaperDarkFolderUri?.takeIf { it.isNotBlank() },
            )
        }
        if (state.hasCurrentPage) {
            settingsManager.saveMainScreenCurrentPage(
                state.currentPage ?: SettingsManager.DEFAULT_MAIN_SCREEN_CURRENT_PAGE,
            )
        }
    }

    /**
     * Resolves wallpaper file choices when activating a materialized theme cache.
     * [runtime.json] overrides [theme.json]; if neither defines wallpapers, returns empty
     * so stale DataStore values from the previous active theme are not kept.
     */
    fun resolveWallpaperSelectionsForActivation(
        cacheDir: File,
        themeJson: String,
    ): MainScreenWallpaperSelectionsByPage {
        val runtime = read(cacheDir)
        if (runtime.hasWallpaperSelections) {
            return runtime.wallpaperSelections ?: MainScreenWallpaperSelectionsByPage.empty()
        }
        val root = runCatching { JSONObject(themeJson) }.getOrNull() ?: return MainScreenWallpaperSelectionsByPage.empty()
        val mainScreen = root.optJSONObject(ThemeSection.MAIN_SCREEN.jsonKey)
            ?: return MainScreenWallpaperSelectionsByPage.empty()
        if (!mainScreen.has(KEY_WALLPAPER_SELECTION_BY_PAGE)) {
            return MainScreenWallpaperSelectionsByPage.empty()
        }
        return MainScreenWallpaperSelectionsByPage.fromJson(
            mainScreen.optJSONObject(KEY_WALLPAPER_SELECTION_BY_PAGE),
        )
    }

    /**
     * Resolves wallpaper folder URIs when activating a materialized theme cache.
     * When [runtime.json] records a folder URI (even blank), that value wins; otherwise
     * returns null so the caller can fall back to bundled assets in the theme cache.
     */
    fun resolveWallpaperLightFolderUriForActivation(cacheDir: File): String? {
        val runtime = read(cacheDir)
        return if (runtime.hasWallpaperLightFolderUri) {
            runtime.wallpaperLightFolderUri.orEmpty()
        } else {
            null
        }
    }

    fun resolveWallpaperDarkFolderUriForActivation(cacheDir: File): String? {
        val runtime = read(cacheDir)
        return if (runtime.hasWallpaperDarkFolderUri) {
            runtime.wallpaperDarkFolderUri.orEmpty()
        } else {
            null
        }
    }
}
