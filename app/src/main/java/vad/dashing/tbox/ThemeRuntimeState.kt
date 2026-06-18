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

    const val KEY_WALLPAPER_LIGHT_SELECTED_FILE = "wallpaperLightSelectedFile"
    const val KEY_WALLPAPER_DARK_SELECTED_FILE = "wallpaperDarkSelectedFile"
    const val KEY_CURRENT_PAGE = "currentPage"

    data class State(
        val wallpaperLightSelectedFile: String? = null,
        val hasWallpaperLightSelectedFile: Boolean = false,
        val wallpaperDarkSelectedFile: String? = null,
        val hasWallpaperDarkSelectedFile: Boolean = false,
        val currentPage: Int? = null,
        val hasCurrentPage: Boolean = false,
    ) {
        val isEmpty: Boolean
            get() = !hasWallpaperLightSelectedFile && !hasWallpaperDarkSelectedFile && !hasCurrentPage
    }

    fun read(cacheDir: File): State {
        val file = File(cacheDir, RUNTIME_JSON_FILE)
        if (!file.isFile) return State()
        val obj = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return State()
        return State(
            wallpaperLightSelectedFile = if (obj.has(KEY_WALLPAPER_LIGHT_SELECTED_FILE)) {
                obj.optString(KEY_WALLPAPER_LIGHT_SELECTED_FILE, "")
            } else {
                null
            },
            hasWallpaperLightSelectedFile = obj.has(KEY_WALLPAPER_LIGHT_SELECTED_FILE),
            wallpaperDarkSelectedFile = if (obj.has(KEY_WALLPAPER_DARK_SELECTED_FILE)) {
                obj.optString(KEY_WALLPAPER_DARK_SELECTED_FILE, "")
            } else {
                null
            },
            hasWallpaperDarkSelectedFile = obj.has(KEY_WALLPAPER_DARK_SELECTED_FILE),
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
        if (state.hasWallpaperLightSelectedFile) {
            json.put(KEY_WALLPAPER_LIGHT_SELECTED_FILE, state.wallpaperLightSelectedFile.orEmpty())
        }
        if (state.hasWallpaperDarkSelectedFile) {
            json.put(KEY_WALLPAPER_DARK_SELECTED_FILE, state.wallpaperDarkSelectedFile.orEmpty())
        }
        if (state.hasCurrentPage) {
            json.put(KEY_CURRENT_PAGE, state.currentPage ?: SettingsManager.DEFAULT_MAIN_SCREEN_CURRENT_PAGE)
        }
        file.writeText(json.toString(2))
    }

    fun patch(
        cacheDir: File,
        lightSelectedFile: String? = null,
        darkSelectedFile: String? = null,
        currentPage: Int? = null,
    ): State {
        val existing = read(cacheDir)
        val merged = State(
            wallpaperLightSelectedFile = lightSelectedFile ?: existing.wallpaperLightSelectedFile,
            hasWallpaperLightSelectedFile = lightSelectedFile != null || existing.hasWallpaperLightSelectedFile,
            wallpaperDarkSelectedFile = darkSelectedFile ?: existing.wallpaperDarkSelectedFile,
            hasWallpaperDarkSelectedFile = darkSelectedFile != null || existing.hasWallpaperDarkSelectedFile,
            currentPage = currentPage ?: existing.currentPage,
            hasCurrentPage = currentPage != null || existing.hasCurrentPage,
        )
        write(cacheDir, merged)
        return merged
    }

    suspend fun applyOverrides(settingsManager: SettingsManager, state: State) {
        if (state.hasWallpaperLightSelectedFile) {
            settingsManager.saveMainScreenWallpaperLightSelectedFileName(
                state.wallpaperLightSelectedFile.orEmpty(),
            )
        }
        if (state.hasWallpaperDarkSelectedFile) {
            settingsManager.saveMainScreenWallpaperDarkSelectedFileName(
                state.wallpaperDarkSelectedFile.orEmpty(),
            )
        }
        if (state.hasCurrentPage) {
            settingsManager.saveMainScreenCurrentPage(
                state.currentPage ?: SettingsManager.DEFAULT_MAIN_SCREEN_CURRENT_PAGE,
            )
        }
    }
}
