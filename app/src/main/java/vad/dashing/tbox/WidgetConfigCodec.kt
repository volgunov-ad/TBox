package vad.dashing.tbox

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

private const val LEGACY_WIDGETS_SEPARATOR = "|"
private const val LEGACY_APP_LAUNCHER_WIDGET_DATA_KEY = "launchAppWidget"
const val DEFAULT_WIDGET_SCALE = 1.0f
private const val MIN_WIDGET_SCALE = 0.1f
private const val MAX_WIDGET_SCALE = 2f
const val DEFAULT_WIDGET_SHAPE = 0
private const val MIN_WIDGET_SHAPE = 0
private const val MAX_WIDGET_SHAPE = 50

/** Corner radius in dp for main dashboard tiles (matches DashboardWidgetScaffold defaults). */
const val MAIN_DASHBOARD_DEFAULT_WIDGET_SHAPE = 12

/** Elevation in dp for main dashboard tiles. */
const val MAIN_DASHBOARD_DEFAULT_WIDGET_ELEVATION = 4

/** Elevation in dp for floating overlay tiles (flat cards). */
const val FLOATING_DASHBOARD_DEFAULT_WIDGET_ELEVATION = 0

fun normalizeWidgetScale(rawScale: Float): Float {
    if (!rawScale.isFinite()) return DEFAULT_WIDGET_SCALE
    val normalized = rawScale.coerceIn(MIN_WIDGET_SCALE, MAX_WIDGET_SCALE)
    return (normalized * 10f).roundToInt() / 10f
}

fun normalizeWidgetShape(rawShape: Int): Int {
    return rawShape.coerceIn(MIN_WIDGET_SHAPE, MAX_WIDGET_SHAPE)
}

fun parseWidgetConfigsFromAny(rawValue: Any?): List<FloatingDashboardWidgetConfig> {
    return when (rawValue) {
        is JSONArray -> parseWidgetConfigsFromJsonArray(rawValue)
        is String -> parseWidgetConfigsFromString(rawValue)
        else -> emptyList()
    }
}

fun parseWidgetConfigsFromString(rawValue: String): List<FloatingDashboardWidgetConfig> {
    if (rawValue.isBlank()) return emptyList()
    val trimmed = rawValue.trim()
    if (trimmed.startsWith("[")) {
        return try {
            parseWidgetConfigsFromJsonArray(JSONArray(trimmed))
        } catch (_: Exception) {
            parseLegacyWidgetConfigs(trimmed)
        }
    }
    return parseLegacyWidgetConfigs(trimmed)
}

fun serializeWidgetConfigs(configs: List<FloatingDashboardWidgetConfig>): String {
    return serializeWidgetConfigsToJsonArray(configs).toString()
}

fun serializeWidgetConfigsToJsonArray(
    configs: List<FloatingDashboardWidgetConfig>
): JSONArray {
    val array = JSONArray()
    configs.forEach { config ->
        val obj = JSONObject()
        obj.put("dataKey", config.dataKey)
        obj.put("showTitle", config.showTitle)
        obj.put("showUnit", config.showUnit)
        obj.put("singleLineDualMetrics", config.singleLineDualMetrics)
        obj.put("scale", normalizeWidgetScale(config.scale))
        obj.put("shape", normalizeWidgetShape(config.shape))
        obj.put("textColorLight", config.textColorLight)
        obj.put("textColorDark", config.textColorDark)
        config.backgroundColorLight?.let { obj.put("backgroundColorLight", it) }
        config.backgroundColorDark?.let { obj.put("backgroundColorDark", it) }
        val mediaPlayers = orderedMediaPlayerPackages(config.mediaPlayers)
        if (mediaPlayers.isNotEmpty()) {
            obj.put("mediaPlayers", JSONArray(mediaPlayers))
            val selectedPlayer = normalizeMediaPlayerPackages(
                listOf(config.mediaSelectedPlayer)
            ).firstOrNull()
            if (selectedPlayer != null) {
                obj.put("mediaSelectedPlayer", selectedPlayer)
            }
        }
        obj.put("mediaAutoPlayOnInit", config.mediaAutoPlayOnInit)
        obj.put("mediaAutoPlayOnlyWhenEngineRunning", config.mediaAutoPlayOnlyWhenEngineRunning)
        obj.put("mediaKeepPlayerForeground", config.mediaKeepPlayerForeground)
        if (config.launcherAppPackage.isNotBlank()) {
            obj.put("launcherAppPackage", config.launcherAppPackage.trim())
        }
        if (config.appWidgetId != null) {
            obj.put("appWidgetId", config.appWidgetId)
        }
        if (config.customTitle.isNotBlank()) {
            obj.put("customTitle", config.customTitle.trim())
        }
        val acc = config.valueAccuracy
        if (acc != null && acc in 0..2) {
            obj.put("valueAccuracy", acc)
        }
        if (config.selectedVariant != 0) {
            obj.put("selectedVariant", config.selectedVariant)
        }
        array.put(obj)
    }
    return array
}

/**
 * After backup import, third-party app widget IDs may be invalid (e.g. provider app reinstalled).
 * Reset those tiles to empty slots while preserving layout/visual options.
 */
fun clearExternalAppWidgetsAfterBackupImport(
    configs: List<FloatingDashboardWidgetConfig>
): List<FloatingDashboardWidgetConfig> =
    configs.map { cfg ->
        if (cfg.dataKey == WidgetsRepository.EXTERNAL_WIDGET_DATA_KEY) {
            cfg.copy(dataKey = "", appWidgetId = null)
        } else {
            cfg
        }
    }

fun normalizeWidgetConfigs(
    configs: List<FloatingDashboardWidgetConfig>,
    widgetCount: Int
): List<FloatingDashboardWidgetConfig> {
    if (widgetCount <= 0) return emptyList()
    val normalized = configs.take(widgetCount).toMutableList()
    if (normalized.size < widgetCount) {
        normalized.addAll(
            List(widgetCount - normalized.size) { FloatingDashboardWidgetConfig(dataKey = "") }
        )
    }
    return normalized
}

fun loadWidgetsFromConfig(
    configs: List<FloatingDashboardWidgetConfig>,
    widgetCount: Int,
    context: Context,
    defaultBackgroundLight: Int = DEFAULT_WIDGET_BACKGROUND_COLOR_LIGHT_MAIN,
    defaultBackgroundDark: Int = DEFAULT_WIDGET_BACKGROUND_COLOR_DARK_MAIN
): List<DashboardWidget> {
    return (0 until widgetCount).map { index ->
        val widgetConfig = configs.getOrNull(index)
            ?: FloatingDashboardWidgetConfig(dataKey = "")
        val dataKey = widgetConfig.dataKey
        if (dataKey.isNotEmpty() && dataKey != "null") {
            DashboardWidget(
                id = index,
                title = WidgetsRepository.getTitleForDataKey(context, dataKey),
                unit = WidgetsRepository.getUnitForDataKey(context, dataKey),
                dataKey = dataKey,
                textColorLight = widgetConfig.textColorLight,
                textColorDark = widgetConfig.textColorDark,
                backgroundColorLight = widgetConfig.backgroundColorLight ?: defaultBackgroundLight,
                backgroundColorDark = widgetConfig.backgroundColorDark ?: defaultBackgroundDark,
                valueAccuracy = widgetConfig.valueAccuracy
            )
        } else {
            DashboardWidget(
                id = index,
                title = "",
                dataKey = "",
                textColorLight = widgetConfig.textColorLight,
                textColorDark = widgetConfig.textColorDark,
                backgroundColorLight = widgetConfig.backgroundColorLight ?: defaultBackgroundLight,
                backgroundColorDark = widgetConfig.backgroundColorDark ?: defaultBackgroundDark,
                valueAccuracy = widgetConfig.valueAccuracy
            )
        }
    }
}

private fun parseWidgetConfigsFromJsonArray(
    array: JSONArray
): List<FloatingDashboardWidgetConfig> {
    val configs = mutableListOf<FloatingDashboardWidgetConfig>()
    for (i in 0 until array.length()) {
        val item = array.opt(i)
        when (item) {
            is JSONObject -> {
                val rawDataKey = item.optString("dataKey").ifBlank {
                    item.optString("type")
                }.trim()
                val dataKey = if (rawDataKey == LEGACY_APP_LAUNCHER_WIDGET_DATA_KEY) {
                    APP_LAUNCHER_WIDGET_DATA_KEY
                } else {
                    rawDataKey
                }
                val appWidgetId = item.optInt("appWidgetId", -1)
                    .takeIf { it != -1 }
                val valueAccuracy = if (item.has("valueAccuracy")) {
                    item.optInt("valueAccuracy").takeIf { it in 0..2 }
                } else {
                    null
                }
                val mediaPlayers = parseMediaPlayers(item)
                val launcherAppPackage = item.optString("launcherAppPackage", "").trim().ifBlank {
                    item.optString("appPackageName", "").trim()
                }
                configs.add(
                    FloatingDashboardWidgetConfig(
                        dataKey = dataKey,
                        showTitle = item.optBoolean("showTitle", false),
                        showUnit = item.optBoolean("showUnit", true),
                        singleLineDualMetrics = item.optBoolean("singleLineDualMetrics", false),
                        scale = normalizeWidgetScale(
                            item.optDouble("scale", DEFAULT_WIDGET_SCALE.toDouble()).toFloat()
                        ),
                        shape = normalizeWidgetShape(
                            item.optInt("shape", DEFAULT_WIDGET_SHAPE)
                        ),
                        textColorLight = item.optInt(
                            "textColorLight",
                            DEFAULT_WIDGET_TEXT_COLOR_LIGHT
                        ),
                        textColorDark = item.optInt(
                            "textColorDark",
                            DEFAULT_WIDGET_TEXT_COLOR_DARK
                        ),
                        backgroundColorLight = parseBackgroundColor(item, "backgroundColorLight"),
                        backgroundColorDark = parseBackgroundColor(item, "backgroundColorDark"),
                        mediaPlayers = mediaPlayers,
                        mediaSelectedPlayer = parseSelectedMediaPlayer(item, mediaPlayers),
                        mediaAutoPlayOnInit = item.optBoolean("mediaAutoPlayOnInit", false),
                        mediaAutoPlayOnlyWhenEngineRunning = item.optBoolean(
                            "mediaAutoPlayOnlyWhenEngineRunning",
                            false
                        ),
                        mediaKeepPlayerForeground = item.optBoolean(
                            "mediaKeepPlayerForeground",
                            false
                        ),
                        launcherAppPackage = if (dataKey == APP_LAUNCHER_WIDGET_DATA_KEY) {
                            launcherAppPackage
                        } else {
                            ""
                        },
                        appWidgetId = if (dataKey == WidgetsRepository.EXTERNAL_WIDGET_DATA_KEY) {
                            appWidgetId
                        } else {
                            null
                        },
                        customTitle = item.optString("customTitle", "").trim(),
                        valueAccuracy = valueAccuracy,
                        selectedVariant = item.optInt("selectedVariant", 0)
                    )
                )
            }
            is String -> {
                configs.add(FloatingDashboardWidgetConfig(dataKey = item.trim()))
            }
            else -> {
                configs.add(FloatingDashboardWidgetConfig(dataKey = ""))
            }
        }
    }
    return configs
}

private fun parseLegacyWidgetConfigs(rawValue: String): List<FloatingDashboardWidgetConfig> {
    if (rawValue.isBlank()) return emptyList()
    return rawValue.split(LEGACY_WIDGETS_SEPARATOR).map { dataKey ->
        FloatingDashboardWidgetConfig(dataKey = dataKey.trim())
    }
}

private fun parseMediaPlayers(item: JSONObject): List<String> {
    val rawPlayers = mutableListOf<String>()
    val playersArray = item.optJSONArray("mediaPlayers")
    if (playersArray != null) {
        for (idx in 0 until playersArray.length()) {
            rawPlayers.add(playersArray.optString(idx))
        }
    } else {
        val legacyPlayer = item.optString("mediaPlayer")
        if (legacyPlayer.isNotBlank()) {
            rawPlayers.add(legacyPlayer)
        }
    }

    return orderedMediaPlayerPackages(rawPlayers)
}

private fun parseSelectedMediaPlayer(
    item: JSONObject,
    mediaPlayers: List<String>
): String {
    val value = item.optString("mediaSelectedPlayer")
    val selected = canonicalMediaPlayerPackage(value).orEmpty()
    return if (selected in mediaPlayers) selected else ""
}

private fun parseBackgroundColor(item: JSONObject, key: String): Int? {
    return if (item.has(key)) item.optInt(key) else null
}
