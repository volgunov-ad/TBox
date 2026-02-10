package vad.dashing.tbox

import org.json.JSONArray
import org.json.JSONObject

private const val LEGACY_WIDGETS_SEPARATOR = "|"

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
                        showUnit = item.optBoolean("showUnit", true)
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
