package vad.dashing.tbox

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

private const val LEGACY_WIDGETS_SEPARATOR = "|"
const val DEFAULT_WIDGET_SCALE = 1.0f
private const val MIN_WIDGET_SCALE = 0.5f
private const val MAX_WIDGET_SCALE = 2f

fun normalizeWidgetScale(rawScale: Float): Float {
    if (!rawScale.isFinite()) return DEFAULT_WIDGET_SCALE
    val normalized = rawScale.coerceIn(MIN_WIDGET_SCALE, MAX_WIDGET_SCALE)
    return (normalized * 10f).roundToInt() / 10f
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
        obj.put("scale", normalizeWidgetScale(config.scale))
        array.put(obj)
    }
    return array
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
    widgetCount: Int
): List<DashboardWidget> {
    return (0 until widgetCount).map { index ->
        val dataKey = configs.getOrNull(index)?.dataKey ?: ""
        if (dataKey.isNotEmpty() && dataKey != "null") {
            DashboardWidget(
                id = index,
                title = WidgetsRepository.getTitleForDataKey(dataKey),
                unit = WidgetsRepository.getUnitForDataKey(dataKey),
                dataKey = dataKey
            )
        } else {
            DashboardWidget(
                id = index,
                title = "",
                dataKey = ""
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
                val dataKey = item.optString("dataKey").ifBlank {
                    item.optString("type")
                }
                configs.add(
                    FloatingDashboardWidgetConfig(
                        dataKey = dataKey.trim(),
                        showTitle = item.optBoolean("showTitle", false),
                        showUnit = item.optBoolean("showUnit", true),
                        scale = normalizeWidgetScale(
                            item.optDouble("scale", DEFAULT_WIDGET_SCALE.toDouble()).toFloat()
                        )
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
