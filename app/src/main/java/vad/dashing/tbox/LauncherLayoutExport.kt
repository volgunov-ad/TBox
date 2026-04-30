package vad.dashing.tbox

import android.content.Context
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

/**
 * Self-contained, flat-structure export/import of the launcher layout.
 * Designed to be human-readable and consumable by generative AI models.
 *
 * Format v1 structure:
 * ```
 * {
 *   "formatVersion": 1,
 *   "type": "tbox_launcher_layout",
 *   "exportedAtMillis": 1714470000000,
 *   "screen": { "orientation": "landscape", "aspectRatio": "16:9" },
 *   "theme": { ... },
 *   "settingsButton": { "x": 0.92, "y": 0.04 },
 *   "addButton": { "x": 0.84, "y": 0.04 },
 *   "mainScreenPanels": [ ... ],
 *   "floatingPanels": [ ... ],
 *   "dashboardTab": { "rows": 3, "cols": 4, "widgets": [ ... ] },
 *   "availableWidgets": [ ... ]
 * }
 * ```
 */
object LauncherLayoutExport {

    private const val FORMAT_VERSION = 1
    private const val TYPE = "tbox_launcher_layout"

    suspend fun exportJson(context: Context, settingsManager: SettingsManager): String {
        val root = JSONObject()
        root.put("formatVersion", FORMAT_VERSION)
        root.put("type", TYPE)
        root.put("exportedAtMillis", System.currentTimeMillis())

        root.put("screen", buildScreenContext())
        root.put("theme", buildTheme(settingsManager))
        root.put("settingsButton", buildButtonPosition(settingsManager.mainScreenSettingsButtonFlow.first()))
        root.put("addButton", buildButtonPosition(settingsManager.mainScreenAddButtonFlow.first()))
        root.put("pagingEnabled", settingsManager.mainScreenPagingEnabledFlow.first())
        root.put("mainScreenPanels", buildMainScreenPanels(context, settingsManager))
        root.put("floatingPanels", buildFloatingPanels(context, settingsManager))
        root.put("dashboardTab", buildDashboardTab(context, settingsManager))
        root.put("availableWidgets", buildAvailableWidgetsCatalog(context))

        return root.toString(2)
    }

    suspend fun importJson(context: Context, settingsManager: SettingsManager, json: String): Result<Unit> {
        val root = runCatching { JSONObject(json) }.getOrElse {
            return Result.failure(IllegalArgumentException("invalid_json"))
        }
        val type = root.optString("type")
        if (type != TYPE) {
            return Result.failure(IllegalArgumentException("unsupported_type: $type"))
        }
        val version = root.optInt("formatVersion", -1)
        if (version < 1) {
            return Result.failure(IllegalArgumentException("unsupported_format_version"))
        }
        return runCatching {
            importTheme(root.optJSONObject("theme"), settingsManager)
            importButtons(root, settingsManager)
            if (root.has("pagingEnabled")) {
                settingsManager.saveMainScreenPagingEnabled(root.optBoolean("pagingEnabled", false))
            }
            importMainScreenPanels(root.optJSONArray("mainScreenPanels"), settingsManager)
            importFloatingPanels(root.optJSONArray("floatingPanels"), settingsManager)
            importDashboardTab(root.optJSONObject("dashboardTab"), settingsManager)
        }
    }

    // ── Export helpers ──

    private fun buildScreenContext(): JSONObject {
        val o = JSONObject()
        o.put("orientation", "landscape")
        o.put("aspectRatio", "16:9")
        o.put("referenceDpi", 160)
        return o
    }

    private suspend fun buildTheme(sm: SettingsManager): JSONObject {
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

        val presets = sm.widgetColorPresetSlotsFlow.first()
        val presetsArr = JSONArray()
        presets.forEach { presetsArr.put(colorIntToHex(it)) }
        o.put("colorPresets", presetsArr)

        return o
    }

    private fun buildButtonPosition(pos: MainScreenSettingsButtonPosition): JSONObject {
        val o = JSONObject()
        o.put("x", pos.x.toDouble())
        o.put("y", pos.y.toDouble())
        return o
    }

    private fun buildButtonPosition(pos: MainScreenAddButtonPosition): JSONObject {
        val o = JSONObject()
        o.put("x", pos.x.toDouble())
        o.put("y", pos.y.toDouble())
        return o
    }

    private suspend fun buildMainScreenPanels(context: Context, sm: SettingsManager): JSONArray {
        val panels = sm.mainScreenDashboardsFlow.first()
        val arr = JSONArray()
        panels.forEach { panel ->
            val o = JSONObject()
            o.put("id", panel.id)
            o.put("name", panel.name)
            o.put("enabled", panel.enabled)
            // Stored relX/relY are normalized against (containerSize - panelSize).
            o.put("positionMode", "remaining")
            val grid = JSONObject()
            grid.put("rows", panel.rows)
            grid.put("cols", panel.cols)
            o.put("grid", grid)
            val position = JSONObject()
            position.put("x", panel.relX.toDouble())
            position.put("y", panel.relY.toDouble())
            o.put("position", position)
            val size = JSONObject()
            size.put("width", panel.relWidth.toDouble())
            size.put("height", panel.relHeight.toDouble())
            o.put("size", size)
            o.put("background", panel.background)
            o.put("clickAction", panel.clickAction)
            o.put("showTboxDisconnectIndicator", panel.showTboxDisconnectIndicator)
            if (panel.pageIndex >= 0) o.put("pageIndex", panel.pageIndex)
            o.put("widgets", serializeWidgetConfigsToJsonArray(panel.widgetsConfig, context))
            arr.put(o)
        }
        return arr
    }

    private suspend fun buildFloatingPanels(context: Context, sm: SettingsManager): JSONArray {
        val panels = sm.floatingDashboardsFlow.first()
        val arr = JSONArray()
        panels.forEach { panel ->
            val o = JSONObject()
            o.put("id", panel.id)
            o.put("name", panel.name)
            o.put("enabled", panel.enabled)
            val grid = JSONObject()
            grid.put("rows", panel.rows)
            grid.put("cols", panel.cols)
            o.put("grid", grid)
            o.put("width", panel.width)
            o.put("height", panel.height)
            o.put("startX", panel.startX)
            o.put("startY", panel.startY)
            o.put("background", panel.background)
            o.put("clickAction", panel.clickAction)
            o.put("showTboxDisconnectIndicator", panel.showTboxDisconnectIndicator)
            o.put("widgets", serializeWidgetConfigsToJsonArray(panel.widgetsConfig, context))
            arr.put(o)
        }
        return arr
    }

    private suspend fun buildDashboardTab(context: Context, sm: SettingsManager): JSONObject {
        val o = JSONObject()
        o.put("rows", sm.dashboardRowsFlow.first())
        o.put("cols", sm.dashboardColsFlow.first())
        val widgets = sm.dashboardWidgetsFlow.first()
        o.put("widgets", serializeWidgetConfigsToJsonArray(widgets, context))
        return o
    }

    private fun buildAvailableWidgetsCatalog(context: Context): JSONArray {
        val arr = JSONArray()
        val allKeys = WidgetsRepository.getAvailableDataKeysWidgets()
        allKeys.forEach { dataKey ->
            val o = JSONObject()
            o.put("dataKey", dataKey)
            val title = WidgetsRepository.getTitleForDataKey(context, dataKey)
            if (title.isNotBlank()) o.put("title", title)
            val unit = WidgetsRepository.getUnitForDataKey(context, dataKey)
            if (unit.isNotBlank()) o.put("unit", unit)
            o.put("supportsSingleLine", WidgetsRepository.supportsSingleLineDualMetrics(dataKey))
            arr.put(o)
        }
        return arr
    }

    // ── Import helpers ──

    private suspend fun importTheme(theme: JSONObject?, sm: SettingsManager) {
        if (theme == null) return

        val canvas = theme.optJSONObject("canvasBackground")
        if (canvas != null) {
            colorHexToIntOrNull(canvas.optString("light"))?.let {
                sm.saveMainScreenCanvasBackgroundLight(it)
            }
            colorHexToIntOrNull(canvas.optString("dark"))?.let {
                sm.saveMainScreenCanvasBackgroundDark(it)
            }
        }

        val cornerButtons = theme.optJSONObject("cornerButtons")
        if (cornerButtons != null) {
            val sizeDp = cornerButtons.optInt("sizeDp", -1)
            if (sizeDp > 0) sm.saveMainScreenCornerButtonSizeDp(sizeDp)

            val bg = cornerButtons.optJSONObject("background")
            if (bg != null) {
                colorHexToIntOrNull(bg.optString("light"))?.let {
                    sm.saveMainScreenCornerButtonBackgroundLight(it)
                }
                colorHexToIntOrNull(bg.optString("dark"))?.let {
                    sm.saveMainScreenCornerButtonBackgroundDark(it)
                }
            }
            val icon = cornerButtons.optJSONObject("icon")
            if (icon != null) {
                colorHexToIntOrNull(icon.optString("light"))?.let {
                    sm.saveMainScreenCornerButtonIconLight(it)
                }
                colorHexToIntOrNull(icon.optString("dark"))?.let {
                    sm.saveMainScreenCornerButtonIconDark(it)
                }
            }
        }

        if (theme.has("wallpaperCrop")) {
            sm.saveMainScreenWallpaperCrop(theme.optBoolean("wallpaperCrop"))
        }

        val presets = theme.optJSONArray("colorPresets")
        if (presets != null) {
            val colors = mutableListOf<Int>()
            for (i in 0 until presets.length()) {
                val hex = presets.optString(i)
                colorHexToIntOrNull(hex)?.let { colors.add(it) }
            }
            colors.forEachIndexed { index, color ->
                if (index < SettingsManager.WIDGET_COLOR_PRESET_SLOT_COUNT) {
                    sm.saveWidgetColorPresetSlot(index, color)
                }
            }
        }
    }

    private suspend fun importButtons(root: JSONObject, sm: SettingsManager) {
        val settingsBtn = root.optJSONObject("settingsButton")
        if (settingsBtn != null) {
            val pos = MainScreenSettingsButtonPosition(
                x = settingsBtn.optDouble("x", MainScreenSettingsButtonPosition.Default.x.toDouble()).toFloat().coerceIn(0f, 1f),
                y = settingsBtn.optDouble("y", MainScreenSettingsButtonPosition.Default.y.toDouble()).toFloat().coerceIn(0f, 1f)
            )
            sm.saveMainScreenSettingsButton(pos)
        }
        val addBtn = root.optJSONObject("addButton")
        if (addBtn != null) {
            val pos = MainScreenAddButtonPosition(
                x = addBtn.optDouble("x", MainScreenAddButtonPosition.Default.x.toDouble()).toFloat().coerceIn(0f, 1f),
                y = addBtn.optDouble("y", MainScreenAddButtonPosition.Default.y.toDouble()).toFloat().coerceIn(0f, 1f)
            )
            sm.saveMainScreenAddButton(pos)
        }
    }

    private suspend fun importMainScreenPanels(panels: JSONArray?, sm: SettingsManager) {
        if (panels == null || panels.length() == 0) return
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

            // Support two coordinate systems for exports:
            // - remaining: relX/relY are already normalized against (container - panelSize) (native app storage)
            // - absolute: x/y are normalized against full container; convert to remaining-normalized relX/relY
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
                    pageIndex = o.optInt("pageIndex", -1)
                )
            )
        }
        if (configs.isNotEmpty()) {
            sm.saveMainScreenDashboards(configs)
        }
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
                    showTboxDisconnectIndicator = o.optBoolean("showTboxDisconnectIndicator", true)
                )
            )
        }
        if (configs.isNotEmpty()) {
            sm.saveFloatingDashboards(configs)
        }
    }

    private suspend fun importDashboardTab(tab: JSONObject?, sm: SettingsManager) {
        if (tab == null) return
        if (tab.has("rows")) {
            sm.saveDashboardRows(tab.optInt("rows", 3))
        }
        if (tab.has("cols")) {
            sm.saveDashboardCols(tab.optInt("cols", 4))
        }
        if (tab.has("widgets")) {
            val widgets = parseWidgetConfigsFromAny(tab.opt("widgets"))
            sm.saveDashboardWidgets(widgets)
        }
    }
}
