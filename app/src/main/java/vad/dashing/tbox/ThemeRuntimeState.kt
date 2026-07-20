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
    const val KEY_CURRENT_PAGE_WINDOW_MODE = "currentPageWindowMode"

    data class State(
        val wallpaperSelections: MainScreenWallpaperSelectionsByPage? = null,
        val hasWallpaperSelections: Boolean = false,
        val currentPage: Int? = null,
        val hasCurrentPage: Boolean = false,
        val currentPageWindowMode: Int? = null,
        val hasCurrentPageWindowMode: Boolean = false,
    ) {
        val isEmpty: Boolean
            get() = !hasWallpaperSelections && !hasCurrentPage && !hasCurrentPageWindowMode
    }

    fun readRawText(cacheDir: File): String? {
        val file = File(cacheDir, RUNTIME_JSON_FILE)
        return if (file.isFile) file.readText() else null
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
            currentPageWindowMode = if (obj.has(KEY_CURRENT_PAGE_WINDOW_MODE)) {
                obj.optInt(KEY_CURRENT_PAGE_WINDOW_MODE, SettingsManager.DEFAULT_MAIN_SCREEN_CURRENT_PAGE)
            } else {
                null
            },
            hasCurrentPageWindowMode = obj.has(KEY_CURRENT_PAGE_WINDOW_MODE),
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
        if (state.hasCurrentPageWindowMode) {
            json.put(
                KEY_CURRENT_PAGE_WINDOW_MODE,
                state.currentPageWindowMode ?: SettingsManager.DEFAULT_MAIN_SCREEN_CURRENT_PAGE,
            )
        }
        file.writeText(json.toString(2))
    }

    fun patch(
        cacheDir: File,
        wallpaperSelections: MainScreenWallpaperSelectionsByPage? = null,
        currentPage: Int? = null,
        currentPageWindowMode: Int? = null,
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
            currentPageWindowMode = currentPageWindowMode ?: existing.currentPageWindowMode,
            hasCurrentPageWindowMode = currentPageWindowMode != null || existing.hasCurrentPageWindowMode,
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
        if (state.hasCurrentPageWindowMode) {
            settingsManager.saveMainScreenWindowModeCurrentPage(
                state.currentPageWindowMode ?: SettingsManager.DEFAULT_MAIN_SCREEN_CURRENT_PAGE,
            )
        }
    }

    /**
     * Applies incoming theme cache state to global DataStore on activation.
     * Wallpaper selections are always resolved from [cacheDir] so stale DataStore values
     * from the previously active theme are replaced before the main screen is shown.
     */
    suspend fun applyActivationOverrides(
        settingsManager: SettingsManager,
        cacheDir: File,
        themeJson: String,
        applyTargets: Set<ThemeApplyTarget>,
    ): MainScreenWallpaperSelectionsByPage {
        if (ThemeApplyTarget.MAIN_SCREEN_WALLPAPERS !in applyTargets) {
            return settingsManager.mainScreenWallpaperSelectionsSnapshot()
        }
        val selections = resolveWallpaperSelectionsForActivation(cacheDir, themeJson)
        settingsManager.saveMainScreenWallpaperSelectionsByPage(selections)
        val runtime = read(cacheDir)
        val pageFromRuntime = if (runtime.hasCurrentPage) {
            runtime.currentPage ?: SettingsManager.DEFAULT_MAIN_SCREEN_CURRENT_PAGE
        } else {
            null
        }
        val pageFromThemeJson = currentPageFromThemeJson(themeJson)
        val resolvedPage = normalizeCurrentPageForWallpaperSelections(
            requestedPage = pageFromRuntime ?: pageFromThemeJson,
            selections = selections,
        )
        if (resolvedPage != null) {
            settingsManager.saveMainScreenCurrentPage(resolvedPage)
        }
        if (runtime.hasCurrentPageWindowMode) {
            settingsManager.saveMainScreenWindowModeCurrentPage(
                runtime.currentPageWindowMode ?: SettingsManager.DEFAULT_MAIN_SCREEN_CURRENT_PAGE,
            )
        }
        return selections
    }

    internal fun currentPageFromThemeJson(themeJson: String): Int? {
        val root = runCatching { JSONObject(themeJson) }.getOrNull() ?: return null
        val mainScreen = root.optJSONObject(ThemeSection.MAIN_SCREEN.jsonKey) ?: return null
        if (!mainScreen.has(KEY_CURRENT_PAGE)) return null
        return mainScreen.optInt(KEY_CURRENT_PAGE, SettingsManager.DEFAULT_MAIN_SCREEN_CURRENT_PAGE)
    }

    /**
     * Keeps [requestedPage] when it has a wallpaper entry; otherwise uses the first page that does.
     */
    internal fun normalizeCurrentPageForWallpaperSelections(
        requestedPage: Int?,
        selections: MainScreenWallpaperSelectionsByPage,
    ): Int? {
        if (selections.isEmpty()) return requestedPage
        val candidate = requestedPage ?: SettingsManager.DEFAULT_MAIN_SCREEN_CURRENT_PAGE
        if (selections.lightByPage.containsKey(candidate) || selections.darkByPage.containsKey(candidate)) {
            return candidate
        }
        val pagesWithSelection = (selections.lightByPage.keys + selections.darkByPage.keys).sorted()
        return pagesWithSelection.firstOrNull() ?: candidate
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
        return wallpaperSelectionsFromThemeJson(themeJson)
    }

    fun wallpaperSelectionsFromThemeJson(themeJson: String): MainScreenWallpaperSelectionsByPage {
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
     * Seeds [RUNTIME_JSON_FILE] from [theme.json] mainScreen section when the cache has no runtime
     * state yet (first materialization or legacy cache). Existing runtime.json is never overwritten.
     */
    fun seedFromThemeJsonIfMissing(cacheDir: File, themeJson: String) {
        if (File(cacheDir, RUNTIME_JSON_FILE).isFile) return
        val root = runCatching { JSONObject(themeJson) }.getOrNull() ?: return
        val mainScreen = root.optJSONObject(ThemeSection.MAIN_SCREEN.jsonKey) ?: return
        val hasWallpaperSelections = mainScreen.has(KEY_WALLPAPER_SELECTION_BY_PAGE)
        val hasCurrentPage = mainScreen.has(KEY_CURRENT_PAGE)
        if (!hasWallpaperSelections && !hasCurrentPage) return
        write(
            cacheDir,
            State(
                wallpaperSelections = if (hasWallpaperSelections) {
                    MainScreenWallpaperSelectionsByPage.fromJson(
                        mainScreen.optJSONObject(KEY_WALLPAPER_SELECTION_BY_PAGE),
                    )
                } else {
                    null
                },
                hasWallpaperSelections = hasWallpaperSelections,
                currentPage = if (hasCurrentPage) {
                    mainScreen.optInt(KEY_CURRENT_PAGE, SettingsManager.DEFAULT_MAIN_SCREEN_CURRENT_PAGE)
                } else {
                    null
                },
                hasCurrentPage = hasCurrentPage,
            ),
        )
    }
}
