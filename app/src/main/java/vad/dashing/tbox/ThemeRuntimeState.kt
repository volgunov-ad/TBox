package vad.dashing.tbox

import org.json.JSONObject
import java.io.File

/**
 * Per-theme runtime state stored beside [ThemeMaterialization.THEME_JSON_FILE] in the cache dir.
 * Holds last main-screen wallpaper selection and current page; overrides [theme.json] on activation
 * when present.
 */
object ThemeRuntimeState {

    const val RUNTIME_JSON_FILE = "runtime.json"

    const val KEY_WALLPAPER_SELECTION_BY_PAGE = MainScreenWallpaperSelectionsByPage.JSON_KEY
    const val KEY_CURRENT_PAGE = "currentPage"

    data class State(
        val wallpaperSelections: MainScreenWallpaperSelectionsByPage? = null,
        val hasWallpaperSelections: Boolean = false,
        val currentPage: Int? = null,
        val hasCurrentPage: Boolean = false,
    ) {
        val isEmpty: Boolean
            get() = !hasWallpaperSelections && !hasCurrentPage
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
        if (state.hasCurrentPage) {
            json.put(KEY_CURRENT_PAGE, state.currentPage ?: SettingsManager.DEFAULT_MAIN_SCREEN_CURRENT_PAGE)
        }
        file.writeText(json.toString(2))
    }

    fun patch(
        cacheDir: File,
        wallpaperSelections: MainScreenWallpaperSelectionsByPage? = null,
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
        if (state.hasCurrentPage) {
            settingsManager.saveMainScreenCurrentPage(
                state.currentPage ?: SettingsManager.DEFAULT_MAIN_SCREEN_CURRENT_PAGE,
            )
        }
    }
}
