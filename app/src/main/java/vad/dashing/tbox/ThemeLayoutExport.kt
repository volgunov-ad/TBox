package vad.dashing.tbox

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import vad.dashing.tbox.ui.theme.TboxFontFamily

/**
 * Export/import of theme JSON sections for [.tboxtheme] archives.
 */
object ThemeLayoutExport {

    private const val FORMAT_VERSION = 1
    private const val TYPE = "tbox_theme"

    suspend fun exportJson(
        context: Context,
        settingsManager: SettingsManager,
        sections: Set<ThemeSection>,
    ): String {
        val root = JSONObject()
        root.put("formatVersion", FORMAT_VERSION)
        root.put("type", TYPE)
        root.put("exportedAtMillis", System.currentTimeMillis())
        root.put("sections", ThemeSection.toJsonArray(sections))
        if (ThemeSection.MAIN_SCREEN in sections) {
            root.put(ThemeSection.MAIN_SCREEN.jsonKey, buildMainScreenSection(context, settingsManager))
        }
        if (ThemeSection.FLOATING_PANELS in sections) {
            root.put(ThemeSection.FLOATING_PANELS.jsonKey, buildFloatingPanelsSection(context, settingsManager))
        }
        if (ThemeSection.APP_ICONS in sections) {
            root.put(ThemeSection.APP_ICONS.jsonKey, buildAppIconsSection(context, settingsManager, sections))
        }
        return root.toString(2)
    }

    suspend fun importJson(
        context: Context,
        settingsManager: SettingsManager,
        json: String,
    ): Result<Set<ThemeSection>> {
        val root = runCatching { JSONObject(json) }.getOrElse {
            return Result.failure(IllegalArgumentException("invalid_json"))
        }
        if (root.optString("type") != TYPE) {
            return Result.failure(IllegalArgumentException("unsupported_type"))
        }
        if (root.optInt("formatVersion", -1) < 1) {
            return Result.failure(IllegalArgumentException("unsupported_format_version"))
        }
        val sections = ThemeSection.parseJsonArray(root.optJSONArray("sections"))
        return runCatching {
            if (ThemeSection.MAIN_SCREEN in sections && root.has(ThemeSection.MAIN_SCREEN.jsonKey)) {
                importMainScreenSection(root.optJSONObject(ThemeSection.MAIN_SCREEN.jsonKey), settingsManager)
            }
            if (ThemeSection.FLOATING_PANELS in sections && root.has(ThemeSection.FLOATING_PANELS.jsonKey)) {
                importFloatingPanelsSection(
                    root.optJSONObject(ThemeSection.FLOATING_PANELS.jsonKey),
                    settingsManager,
                )
            }
            sections
        }
    }

    fun parseSectionsFromThemeJson(json: String): Set<ThemeSection> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptySet()
        return ThemeSection.parseJsonArray(root.optJSONArray("sections"))
    }

    fun collectLauncherPackages(widgets: List<FloatingDashboardWidgetConfig>): Set<String> =
        widgets.mapNotNull { widget ->
            widget.launcherAppPackage.trim().takeIf { pkg ->
                pkg.isNotEmpty() && widget.dataKey == APP_LAUNCHER_WIDGET_DATA_KEY
            }
        }.toSet()

    fun collectTileBackgroundPaths(widgets: List<FloatingDashboardWidgetConfig>): Set<String> =
        buildSet {
            widgets.forEach { widget ->
                widget.tileBackgroundImageRelPathLight?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
                widget.tileBackgroundImageRelPathDark?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
            }
        }

    fun collectHttpRequestIconKeys(
        panelStorageId: String,
        widgets: List<FloatingDashboardWidgetConfig>,
    ): Set<String> =
        buildSet {
            widgets.forEachIndexed { index, widget ->
                if (widget.dataKey == HTTP_REQUEST_WIDGET_DATA_KEY) {
                    add(HttpRequestIconPaths.iconKey(panelStorageId, index))
                }
            }
        }

    suspend fun collectPackagesForSections(
        context: Context,
        settingsManager: SettingsManager,
        sections: Set<ThemeSection>,
        lookup: LauncherAppIconPaths.Lookup,
    ): Set<String> {
        if (ThemeSection.APP_ICONS !in sections) return emptySet()
        if (sections == setOf(ThemeSection.APP_ICONS)) {
            return LauncherAppIconPaths.listAllResolvablePackageNames(context.filesDir, lookup)
        }
        val packages = linkedSetOf<String>()
        if (ThemeSection.MAIN_SCREEN in sections) {
            settingsManager.mainScreenDashboardsFlow.first().forEach { panel ->
                packages.addAll(collectLauncherPackages(panel.widgetsConfig))
            }
        }
        if (ThemeSection.FLOATING_PANELS in sections) {
            settingsManager.floatingDashboardsFlow.first().forEach { panel ->
                packages.addAll(collectLauncherPackages(panel.widgetsConfig))
            }
        }
        return packages
    }

    private suspend fun buildAppIconsSection(
        context: Context,
        settingsManager: SettingsManager,
        sections: Set<ThemeSection>,
    ): JSONObject {
        val lookup = settingsManager.launcherAppIconLookup()
        val packages = collectPackagesForSections(context, settingsManager, sections, lookup)
        val httpIconKeys = collectHttpRequestIconKeysForSections(settingsManager, sections)
        val arr = JSONArray()
        packages.sorted().forEach { arr.put(it) }
        val httpArr = JSONArray()
        httpIconKeys.sorted().forEach { httpArr.put(it) }
        return JSONObject()
            .put("packages", arr)
            .put("httpRequestIconKeys", httpArr)
    }

    private suspend fun collectHttpRequestIconKeysForSections(
        settingsManager: SettingsManager,
        sections: Set<ThemeSection>,
    ): Set<String> {
        if (ThemeSection.APP_ICONS !in sections) return emptySet()
        val keys = linkedSetOf<String>()
        if (ThemeSection.MAIN_SCREEN in sections) {
            settingsManager.mainScreenDashboardsFlow.first().forEach { panel ->
                keys.addAll(collectHttpRequestIconKeys(panel.id, panel.widgetsConfig))
            }
        }
        if (ThemeSection.FLOATING_PANELS in sections) {
            settingsManager.floatingDashboardsFlow.first().forEach { panel ->
                keys.addAll(collectHttpRequestIconKeys(panel.id, panel.widgetsConfig))
            }
        }
        return keys
    }

    private suspend fun buildMainScreenSection(context: Context, sm: SettingsManager): JSONObject {
        val o = JSONObject()
        o.put("pageCount", sm.mainScreenPageCountFlow.first())
        o.put("currentPage", sm.mainScreenCurrentPageFlow.first())
        o.put("theme", buildVisualTheme(sm))
        o.put("settingsButton", buildNormalizedPosition(sm.mainScreenSettingsButtonFlow.first()))
        o.put("addButton", buildNormalizedPosition(sm.mainScreenAddButtonFlow.first()))
        o.put("pagePrevButton", buildNormalizedPosition(sm.mainScreenPagePrevButtonFlow.first()))
        o.put("pageNextButton", buildNormalizedPosition(sm.mainScreenPageNextButtonFlow.first()))
        o.put(
            MainScreenWallpaperSelectionsByPage.JSON_KEY,
            sm.mainScreenWallpaperSelectionByPageFlow.first().toJson(),
        )
        val lightFolderStr = sm.mainScreenWallpaperLightFolderUriFlow.first()
        if (lightFolderStr.isNotBlank()) {
            val lightImages = listSortedWallpaperImagesInFolder(context, Uri.parse(lightFolderStr))
            if (lightImages.isNotEmpty()) {
                o.put("wallpaperLightFolderBundledPath", ThemeBundleExport.ASSETS_WALLPAPER_LIGHT_DIR)
            }
        }
        val darkFolderStr = sm.mainScreenWallpaperDarkFolderUriFlow.first()
        if (darkFolderStr.isNotBlank()) {
            val darkImages = listSortedWallpaperImagesInFolder(context, Uri.parse(darkFolderStr))
            if (darkImages.isNotEmpty()) {
                o.put("wallpaperDarkFolderBundledPath", ThemeBundleExport.ASSETS_WALLPAPER_DARK_DIR)
            }
        }
        o.put("panels", buildMainScreenPanels(context, sm))
        return o
    }

    private suspend fun buildFloatingPanelsSection(context: Context, sm: SettingsManager): JSONObject {
        val o = JSONObject()
        o.put("panels", buildFloatingPanels(context, sm))
        return o
    }

    private suspend fun buildVisualTheme(sm: SettingsManager): JSONObject {
        val o = JSONObject()
        val canvas = JSONObject()
        canvas.put("light", colorIntToHex(sm.mainScreenCanvasBackgroundLightFlow.first()))
        canvas.put("dark", colorIntToHex(sm.mainScreenCanvasBackgroundDarkFlow.first()))
        o.put("canvasBackground", canvas)
        val cornerBg = JSONObject()
        cornerBg.put("light", colorIntToHex(sm.mainScreenCornerButtonBackgroundLightFlow.first()))
        cornerBg.put("dark", colorIntToHex(sm.mainScreenCornerButtonBackgroundDarkFlow.first()))
        val cornerIcon = JSONObject()
        cornerIcon.put("light", colorIntToHex(sm.mainScreenCornerButtonIconLightFlow.first()))
        cornerIcon.put("dark", colorIntToHex(sm.mainScreenCornerButtonIconDarkFlow.first()))
        val cornerButtons = JSONObject()
        cornerButtons.put("sizeDp", sm.mainScreenCornerButtonSizeDpFlow.first())
        cornerButtons.put("background", cornerBg)
        cornerButtons.put("icon", cornerIcon)
        o.put("cornerButtons", cornerButtons)
        o.put("wallpaperCrop", sm.mainScreenWallpaperCropFlow.first())
        val presetsArr = JSONArray()
        sm.widgetColorPresetSlotsFlow.first().forEach { presetsArr.put(colorIntToHex(it)) }
        o.put("colorPresets", presetsArr)
        val typography = JSONObject()
        typography.put("fontFamily", TboxFontFamily.fromId(sm.appFontFamilyIdFlow.first()).slug)
        o.put("typography", typography)
        return o
    }

    private fun buildNormalizedPosition(pos: MainScreenSettingsButtonPosition): JSONObject =
        JSONObject().apply {
            put("x", pos.x.toDouble())
            put("y", pos.y.toDouble())
        }

    private fun buildNormalizedPosition(pos: MainScreenAddButtonPosition): JSONObject =
        JSONObject().apply {
            put("x", pos.x.toDouble())
            put("y", pos.y.toDouble())
        }

    private fun buildNormalizedPosition(pos: MainScreenPagePrevButtonPosition): JSONObject =
        JSONObject().apply {
            put("x", pos.x.toDouble())
            put("y", pos.y.toDouble())
        }

    private fun buildNormalizedPosition(pos: MainScreenPageNextButtonPosition): JSONObject =
        JSONObject().apply {
            put("x", pos.x.toDouble())
            put("y", pos.y.toDouble())
        }

    private suspend fun buildMainScreenPanels(context: Context, sm: SettingsManager): JSONArray {
        val arr = JSONArray()
        sm.mainScreenDashboardsFlow.first().forEach { panel ->
            val o = JSONObject()
            o.put("id", panel.id)
            o.put("name", panel.name)
            o.put("enabled", panel.enabled)
            o.put("positionMode", "remaining")
            o.put(
                "grid",
                JSONObject().apply {
                    put("rows", panel.rows)
                    put("cols", panel.cols)
                },
            )
            o.put(
                "position",
                JSONObject().apply {
                    put("x", panel.relX.toDouble())
                    put("y", panel.relY.toDouble())
                },
            )
            o.put(
                "size",
                JSONObject().apply {
                    put("width", panel.relWidth.toDouble())
                    put("height", panel.relHeight.toDouble())
                },
            )
            o.put("background", panel.background)
            o.put("clickAction", panel.clickAction)
            o.put("showTboxDisconnectIndicator", panel.showTboxDisconnectIndicator)
            o.put("pageNumber", panel.pageNumber)
            o.put("widgets", serializeWidgetConfigsToJsonArray(panel.widgetsConfig))
            arr.put(o)
        }
        return arr
    }

    private suspend fun buildFloatingPanels(context: Context, sm: SettingsManager): JSONArray {
        val arr = JSONArray()
        sm.floatingDashboardsFlow.first().forEach { panel ->
            val o = JSONObject()
            o.put("id", panel.id)
            o.put("name", panel.name)
            o.put("enabled", panel.enabled)
            o.put(
                "grid",
                JSONObject().apply {
                    put("rows", panel.rows)
                    put("cols", panel.cols)
                },
            )
            o.put("width", panel.width)
            o.put("height", panel.height)
            o.put("startX", panel.startX)
            o.put("startY", panel.startY)
            o.put("background", panel.background)
            o.put("clickAction", panel.clickAction)
            o.put("showTboxDisconnectIndicator", panel.showTboxDisconnectIndicator)
            o.put("widgets", serializeWidgetConfigsToJsonArray(panel.widgetsConfig))
            arr.put(o)
        }
        return arr
    }

    private suspend fun importMainScreenSection(section: JSONObject?, sm: SettingsManager) {
        if (section == null) return
        if (section.has("pageCount")) {
            sm.saveMainScreenPageCount(section.optInt("pageCount", SettingsManager.DEFAULT_MAIN_SCREEN_PAGE_COUNT))
        }
        if (section.has("currentPage")) {
            sm.saveMainScreenCurrentPage(
                section.optInt("currentPage", SettingsManager.DEFAULT_MAIN_SCREEN_CURRENT_PAGE),
            )
        }
        importVisualTheme(section.optJSONObject("theme"), sm)
        importMainScreenButtons(section, sm)
        importMainScreenPanels(section.optJSONArray("panels"), sm)
    }

    private suspend fun importVisualTheme(theme: JSONObject?, sm: SettingsManager) {
        if (theme == null) return
        theme.optJSONObject("canvasBackground")?.let { canvas ->
            colorHexToIntOrNull(canvas.optString("light"))?.let { sm.saveMainScreenCanvasBackgroundLight(it) }
            colorHexToIntOrNull(canvas.optString("dark"))?.let { sm.saveMainScreenCanvasBackgroundDark(it) }
        }
        theme.optJSONObject("cornerButtons")?.let { cornerButtons ->
            val sizeDp = cornerButtons.optInt("sizeDp", -1)
            if (sizeDp > 0) sm.saveMainScreenCornerButtonSizeDp(sizeDp)
            cornerButtons.optJSONObject("background")?.let { bg ->
                colorHexToIntOrNull(bg.optString("light"))?.let { sm.saveMainScreenCornerButtonBackgroundLight(it) }
                colorHexToIntOrNull(bg.optString("dark"))?.let { sm.saveMainScreenCornerButtonBackgroundDark(it) }
            }
            cornerButtons.optJSONObject("icon")?.let { icon ->
                colorHexToIntOrNull(icon.optString("light"))?.let { sm.saveMainScreenCornerButtonIconLight(it) }
                colorHexToIntOrNull(icon.optString("dark"))?.let { sm.saveMainScreenCornerButtonIconDark(it) }
            }
        }
        if (theme.has("wallpaperCrop")) {
            sm.saveMainScreenWallpaperCrop(theme.optBoolean("wallpaperCrop"))
        }
        theme.optJSONObject("typography")?.optString("fontFamily")?.let { slug ->
            TboxFontFamily.fromSlug(slug)?.let { sm.saveAppFontFamilyId(it.id) }
        }
        theme.optJSONArray("colorPresets")?.let { presets ->
            for (i in 0 until presets.length()) {
                colorHexToIntOrNull(presets.optString(i))?.let { color ->
                    if (i < SettingsManager.WIDGET_COLOR_PRESET_SLOT_COUNT) {
                        sm.saveWidgetColorPresetSlot(i, color)
                    }
                }
            }
        }
    }

    private suspend fun importMainScreenButtons(section: JSONObject, sm: SettingsManager) {
        section.optJSONObject("settingsButton")?.let { btn ->
            sm.saveMainScreenSettingsButton(
                MainScreenSettingsButtonPosition(
                    x = btn.optDouble("x", MainScreenSettingsButtonPosition.Default.x.toDouble()).toFloat(),
                    y = btn.optDouble("y", MainScreenSettingsButtonPosition.Default.y.toDouble()).toFloat(),
                ),
            )
        }
        section.optJSONObject("addButton")?.let { btn ->
            sm.saveMainScreenAddButton(
                MainScreenAddButtonPosition(
                    x = btn.optDouble("x", MainScreenAddButtonPosition.Default.x.toDouble()).toFloat(),
                    y = btn.optDouble("y", MainScreenAddButtonPosition.Default.y.toDouble()).toFloat(),
                ),
            )
        }
        section.optJSONObject("pagePrevButton")?.let { btn ->
            sm.saveMainScreenPagePrevButton(
                MainScreenPagePrevButtonPosition(
                    x = btn.optDouble("x", MainScreenPagePrevButtonPosition.Default.x.toDouble()).toFloat(),
                    y = btn.optDouble("y", MainScreenPagePrevButtonPosition.Default.y.toDouble()).toFloat(),
                ),
            )
        }
        section.optJSONObject("pageNextButton")?.let { btn ->
            sm.saveMainScreenPageNextButton(
                MainScreenPageNextButtonPosition(
                    x = btn.optDouble("x", MainScreenPageNextButtonPosition.Default.x.toDouble()).toFloat(),
                    y = btn.optDouble("y", MainScreenPageNextButtonPosition.Default.y.toDouble()).toFloat(),
                ),
            )
        }
    }

    private suspend fun importMainScreenPanels(panels: JSONArray?, sm: SettingsManager) {
        if (panels == null || panels.length() == 0) return
        val pageCount = sm.mainScreenPageCountFlow.first()
        val configs = mutableListOf<MainScreenPanelConfig>()
        for (i in 0 until panels.length()) {
            val o = panels.optJSONObject(i) ?: continue
            val id = o.optString("id").trim()
            if (id.isEmpty()) continue
            val grid = o.optJSONObject("grid")
            val position = o.optJSONObject("position")
            val size = o.optJSONObject("size")
            val relWidth = (size?.optDouble("width", 0.4)?.toFloat() ?: 0.4f).coerceIn(0.08f, 1f)
            val relHeight = (size?.optDouble("height", 0.3)?.toFloat() ?: 0.3f).coerceIn(0.08f, 1f)
            val mode = o.optString("positionMode", "absolute").trim().lowercase()
            val rawX = (position?.optDouble("x", 0.05)?.toFloat() ?: 0.05f).coerceIn(0f, 1f)
            val rawY = (position?.optDouble("y", 0.1)?.toFloat() ?: 0.1f).coerceIn(0f, 1f)
            val relX = if (mode == "remaining") {
                rawX
            } else {
                val maxAbsX = (1f - relWidth).coerceAtLeast(0f)
                val absX = rawX.coerceIn(0f, maxAbsX)
                if (maxAbsX <= 0f) 0f else (absX / maxAbsX).coerceIn(0f, 1f)
            }
            val relY = if (mode == "remaining") {
                rawY
            } else {
                val maxAbsY = (1f - relHeight).coerceAtLeast(0f)
                val absY = rawY.coerceIn(0f, maxAbsY)
                if (maxAbsY <= 0f) 0f else (absY / maxAbsY).coerceIn(0f, 1f)
            }
            configs.add(
                MainScreenPanelConfig(
                    id = id,
                    name = o.optString("name").ifBlank { id },
                    enabled = o.optBoolean("enabled", true),
                    widgetsConfig = parseWidgetConfigsFromAny(o.opt("widgets")),
                    rows = (grid?.optInt("rows", 1) ?: 1)
                        .coerceIn(1, SettingsManager.DASHBOARD_PANEL_MAX_GRID_DIMENSION),
                    cols = (grid?.optInt("cols", 1) ?: 1)
                        .coerceIn(1, SettingsManager.DASHBOARD_PANEL_MAX_GRID_DIMENSION),
                    relX = relX,
                    relY = relY,
                    relWidth = relWidth,
                    relHeight = relHeight,
                    background = o.optBoolean("background", false),
                    clickAction = o.optBoolean("clickAction", false),
                    showTboxDisconnectIndicator = o.optBoolean("showTboxDisconnectIndicator", false),
                    pageNumber = PagingStateNormalizer.normalizePanelPageNumber(
                        o.optInt("pageNumber", SettingsManager.DEFAULT_MAIN_SCREEN_PANEL_PAGE_NUMBER),
                        pageCount,
                    ),
                ),
            )
        }
        if (configs.isNotEmpty()) {
            sm.saveMainScreenDashboards(configs)
        }
    }

    private suspend fun importFloatingPanelsSection(section: JSONObject?, sm: SettingsManager) {
        importFloatingPanels(section?.optJSONArray("panels"), sm)
    }

    private suspend fun importFloatingPanels(panels: JSONArray?, sm: SettingsManager) {
        if (panels == null || panels.length() == 0) return
        val configs = mutableListOf<FloatingDashboardConfig>()
        for (i in 0 until panels.length()) {
            val o = panels.optJSONObject(i) ?: continue
            val id = o.optString("id").trim()
            if (id.isEmpty()) continue
            val grid = o.optJSONObject("grid")
            configs.add(
                FloatingDashboardConfig(
                    id = id,
                    name = o.optString("name").ifBlank { id },
                    enabled = o.optBoolean("enabled", false),
                    widgetsConfig = parseWidgetConfigsFromAny(o.opt("widgets")),
                    rows = (grid?.optInt("rows", 1) ?: 1)
                        .coerceIn(1, SettingsManager.DASHBOARD_PANEL_MAX_GRID_DIMENSION),
                    cols = (grid?.optInt("cols", 1) ?: 1)
                        .coerceIn(1, SettingsManager.DASHBOARD_PANEL_MAX_GRID_DIMENSION),
                    width = o.optInt("width", 100),
                    height = o.optInt("height", 100),
                    startX = o.optInt("startX", 50),
                    startY = o.optInt("startY", 50),
                    background = o.optBoolean("background", false),
                    clickAction = o.optBoolean("clickAction", true),
                    showTboxDisconnectIndicator = o.optBoolean("showTboxDisconnectIndicator", true),
                ),
            )
        }
        if (configs.isNotEmpty()) {
            sm.saveFloatingDashboards(configs)
        }
    }
}
