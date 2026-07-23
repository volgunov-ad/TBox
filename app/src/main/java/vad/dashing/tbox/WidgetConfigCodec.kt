package vad.dashing.tbox

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import vad.dashing.tbox.freeform.FreeformLaunchBounds
import vad.dashing.tbox.freeform.FreeformLaunchSide
import vad.dashing.tbox.trip.TripWidgetTileDisplay
import kotlin.math.roundToInt

private const val LEGACY_WIDGETS_SEPARATOR = "|"
private const val LEGACY_APP_LAUNCHER_WIDGET_DATA_KEY = "launchAppWidget"
private val REMOVED_WIDGET_DATA_KEYS = setOf(
    "wirelessChargingWidget",
    // Speed limiter widget: not offered until fully debugged (also commented out in WidgetsRepository).
    SPEED_LIMITER_WIDGET_DATA_KEY,
)
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

/** Same range as [normalizeWidgetShape]; used when [FloatingDashboardWidgetConfig.controlShape] is set. */
fun normalizeWidgetControlShape(rawShape: Int): Int = normalizeWidgetShape(rawShape)

/** True when all eight control color fields are null (UI «colors by default»). */
fun FloatingDashboardWidgetConfig.usesDefaultControlColors(): Boolean {
    return controlInactiveColorLight == null &&
        controlInactiveColorDark == null &&
        controlActiveColorLight == null &&
        controlActiveColorDark == null &&
        controlInactiveBackgroundColorLight == null &&
        controlInactiveBackgroundColorDark == null &&
        controlActiveBackgroundColorLight == null &&
        controlActiveBackgroundColorDark == null
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
        if (config.dataKey == APP_LAUNCHER_WIDGET_DATA_KEY && config.launcherFreeformEnabled) {
            obj.put("launcherFreeformEnabled", true)
            obj.put("launcherFreeformSide", config.launcherFreeformSide.storageKey)
            obj.put(
                "launcherFreeformPercent",
                FreeformLaunchBounds.normalizePercent(config.launcherFreeformPercent),
            )
        }
        if (config.dataKey == HTTP_REQUEST_WIDGET_DATA_KEY) {
            obj.put("httpRequestYaml", config.httpRequestYaml.ifBlank { DEFAULT_HTTP_REQUEST_WIDGET_YAML })
            obj.put("httpOpenBrowser", config.httpOpenBrowser)
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
        val dateTimeFormat = sanitizeDateTimeWidgetFormat(config.dataKey, config.dateTimeFormat)
        if (dateTimeFormat.isNotBlank()) {
            obj.put("dateTimeFormat", dateTimeFormat)
        }
        if (config.selectedVariant != 0) {
            obj.put("selectedVariant", config.selectedVariant)
        }
        val selectedDriveMode = normalizeDriveModeWidgetRawValue(config.selectedDriveMode)
        if (selectedDriveMode != DRIVE_MODE_WIDGET_DEFAULT_RAW_VALUE) {
            obj.put("selectedDriveMode", selectedDriveMode)
        }
        obj.put("useMbCanVhal", config.useMbCanVhal)
        if (config.stepperAdjustIconStyle != STEPPER_ADJUST_ICON_PLUS_MINUS) {
            obj.put(
                "stepperAdjustIconStyle",
                normalizeStepperAdjustIconStyle(config.stepperAdjustIconStyle),
            )
        }
        config.tileBackgroundImageRelPathLight?.let {
            if (TileBackgroundImageStorage.isAllowedStoredRelPath(it)) {
                obj.put("tileBackgroundImageRelPathLight", it)
            }
        }
        config.tileBackgroundImageRelPathDark?.let {
            if (TileBackgroundImageStorage.isAllowedStoredRelPath(it)) {
                obj.put("tileBackgroundImageRelPathDark", it)
            }
        }
        if (isActiveTripWidgetDataKey(config.dataKey)) {
            if (!config.tripWidgetShowRowDividers) {
                obj.put("tripWidgetShowRowDividers", false)
            }
            if (config.tripWidgetLabelColumnWidthPercent !=
                TripWidgetTileDisplay.DEFAULT_LABEL_COLUMN_WIDTH_PERCENT
            ) {
                obj.put(
                    "tripWidgetLabelColumnWidthPercent",
                    TripWidgetTileDisplay.normalizeLabelColumnWidthPercent(
                        config.tripWidgetLabelColumnWidthPercent,
                    ),
                )
            }
            if (config.tripWidgetSource != TRIP_WIDGET_SOURCE_CURRENT) {
                obj.put("tripWidgetSource", normalizeTripWidgetSource(config.tripWidgetSource))
            }
        }
        if (config.textAlign != DEFAULT_WIDGET_TEXT_ALIGN) {
            obj.put("textAlign", normalizeWidgetTextAlign(config.textAlign))
        }
        if (config.fontWeight != DEFAULT_WIDGET_FONT_WEIGHT) {
            obj.put("fontWeight", normalizeWidgetFontWeight(config.fontWeight))
        }
        val defaultTitlePosition = resolveDefaultTitlePositionForDataKey(config.dataKey)
        if (config.titlePosition != defaultTitlePosition) {
            obj.put("titlePosition", normalizeWidgetTitlePosition(config.titlePosition))
        }
        val paddingTop = normalizeWidgetPaddingPercent(config.paddingTopPercent)
        if (paddingTop != DEFAULT_WIDGET_PADDING_PERCENT) {
            obj.put("paddingTopPercent", paddingTop)
        }
        val paddingBottom = normalizeWidgetPaddingPercent(config.paddingBottomPercent)
        if (paddingBottom != DEFAULT_WIDGET_PADDING_PERCENT) {
            obj.put("paddingBottomPercent", paddingBottom)
        }
        val paddingStart = normalizeWidgetPaddingPercent(config.paddingStartPercent)
        if (paddingStart != DEFAULT_WIDGET_PADDING_PERCENT) {
            obj.put("paddingStartPercent", paddingStart)
        }
        val paddingEnd = normalizeWidgetPaddingPercent(config.paddingEndPercent)
        if (paddingEnd != DEFAULT_WIDGET_PADDING_PERCENT) {
            obj.put("paddingEndPercent", paddingEnd)
        }
        config.controlInactiveColorLight?.let { obj.put("controlInactiveColorLight", it) }
        config.controlInactiveColorDark?.let { obj.put("controlInactiveColorDark", it) }
        config.controlActiveColorLight?.let { obj.put("controlActiveColorLight", it) }
        config.controlActiveColorDark?.let { obj.put("controlActiveColorDark", it) }
        config.controlInactiveBackgroundColorLight?.let {
            obj.put("controlInactiveBackgroundColorLight", it)
        }
        config.controlInactiveBackgroundColorDark?.let {
            obj.put("controlInactiveBackgroundColorDark", it)
        }
        config.controlActiveBackgroundColorLight?.let {
            obj.put("controlActiveBackgroundColorLight", it)
        }
        config.controlActiveBackgroundColorDark?.let {
            obj.put("controlActiveBackgroundColorDark", it)
        }
        config.controlShape?.let { obj.put("controlShape", normalizeWidgetControlShape(it)) }
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
    widgetCount: Int,
    context: Context,
    defaultBackgroundLight: Int = DEFAULT_WIDGET_BACKGROUND_COLOR_LIGHT_MAIN,
    defaultBackgroundDark: Int = DEFAULT_WIDGET_BACKGROUND_COLOR_DARK_MAIN
): List<DashboardWidget> {
    return (0 until widgetCount).map { index ->
        val widgetConfig = configs.getOrNull(index)
            ?: FloatingDashboardWidgetConfig(dataKey = "")
        val dataKey = widgetConfig.dataKey
            .takeUnless { it in REMOVED_WIDGET_DATA_KEYS }
            .orEmpty()
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
                val dataKey = when {
                    rawDataKey == LEGACY_APP_LAUNCHER_WIDGET_DATA_KEY -> APP_LAUNCHER_WIDGET_DATA_KEY
                    rawDataKey in REMOVED_WIDGET_DATA_KEYS -> ""
                    else -> rawDataKey
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
                val tileLight = item.optString("tileBackgroundImageRelPathLight", "").trim()
                    .takeIf { TileBackgroundImageStorage.isAllowedStoredRelPath(it) }
                val tileDark = item.optString("tileBackgroundImageRelPathDark", "").trim()
                    .takeIf { TileBackgroundImageStorage.isAllowedStoredRelPath(it) }
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
                        launcherFreeformEnabled = dataKey == APP_LAUNCHER_WIDGET_DATA_KEY &&
                            item.optBoolean("launcherFreeformEnabled", false),
                        launcherFreeformSide = if (dataKey == APP_LAUNCHER_WIDGET_DATA_KEY) {
                            FreeformLaunchSide.fromStorageKey(
                                item.optString("launcherFreeformSide", ""),
                            )
                        } else {
                            FreeformLaunchSide.DEFAULT
                        },
                        launcherFreeformPercent = if (dataKey == APP_LAUNCHER_WIDGET_DATA_KEY) {
                            FreeformLaunchBounds.normalizePercent(
                                item.optInt(
                                    "launcherFreeformPercent",
                                    FreeformLaunchBounds.DEFAULT_PERCENT,
                                ),
                            )
                        } else {
                            FreeformLaunchBounds.DEFAULT_PERCENT
                        },
                        httpRequestYaml = if (dataKey == HTTP_REQUEST_WIDGET_DATA_KEY) {
                            item.optString("httpRequestYaml", DEFAULT_HTTP_REQUEST_WIDGET_YAML)
                                .ifBlank { DEFAULT_HTTP_REQUEST_WIDGET_YAML }
                        } else {
                            DEFAULT_HTTP_REQUEST_WIDGET_YAML
                        },
                        httpOpenBrowser = if (dataKey == HTTP_REQUEST_WIDGET_DATA_KEY) {
                            item.optBoolean("httpOpenBrowser", false)
                        } else {
                            false
                        },
                        appWidgetId = if (dataKey == WidgetsRepository.EXTERNAL_WIDGET_DATA_KEY) {
                            appWidgetId
                        } else {
                            null
                        },
                        customTitle = item.optString("customTitle", "").trim(),
                        valueAccuracy = valueAccuracy,
                        dateTimeFormat = sanitizeDateTimeWidgetFormat(
                            dataKey,
                            item.optString("dateTimeFormat", ""),
                        ),
                        selectedVariant = item.optInt("selectedVariant", 0),
                        selectedDriveMode = normalizeDriveModeWidgetRawValue(
                            item.optInt("selectedDriveMode", DRIVE_MODE_WIDGET_DEFAULT_RAW_VALUE)
                        ),
                        useMbCanVhal = item.optBoolean("useMbCanVhal", false),
                        stepperAdjustIconStyle = normalizeStepperAdjustIconStyle(
                            item.optInt("stepperAdjustIconStyle", STEPPER_ADJUST_ICON_PLUS_MINUS),
                        ),
                        tileBackgroundImageRelPathLight = tileLight,
                        tileBackgroundImageRelPathDark = tileDark,
                        tripWidgetShowRowDividers = item.optBoolean(
                            "tripWidgetShowRowDividers",
                            TripWidgetTileDisplay.DEFAULT_SHOW_ROW_DIVIDERS,
                        ),
                        tripWidgetLabelColumnWidthPercent = TripWidgetTileDisplay.normalizeLabelColumnWidthPercent(
                            item.optInt(
                                "tripWidgetLabelColumnWidthPercent",
                                TripWidgetTileDisplay.DEFAULT_LABEL_COLUMN_WIDTH_PERCENT,
                            ),
                        ),
                        tripWidgetSource = if (isActiveTripWidgetDataKey(dataKey)) {
                            normalizeTripWidgetSource(
                                item.optInt("tripWidgetSource", TRIP_WIDGET_SOURCE_CURRENT),
                            )
                        } else {
                            TRIP_WIDGET_SOURCE_CURRENT
                        },
                        textAlign = normalizeWidgetTextAlign(
                            item.optInt("textAlign", DEFAULT_WIDGET_TEXT_ALIGN)
                        ),
                        fontWeight = normalizeWidgetFontWeight(
                            item.optInt("fontWeight", DEFAULT_WIDGET_FONT_WEIGHT)
                        ),
                        titlePosition = if (item.has("titlePosition")) {
                            normalizeWidgetTitlePosition(item.optInt("titlePosition"))
                        } else {
                            resolveDefaultTitlePositionForDataKey(dataKey)
                        },
                        paddingTopPercent = normalizeWidgetPaddingPercent(
                            item.optInt("paddingTopPercent", DEFAULT_WIDGET_PADDING_PERCENT)
                        ),
                        paddingBottomPercent = normalizeWidgetPaddingPercent(
                            item.optInt("paddingBottomPercent", DEFAULT_WIDGET_PADDING_PERCENT)
                        ),
                        paddingStartPercent = normalizeWidgetPaddingPercent(
                            item.optInt("paddingStartPercent", DEFAULT_WIDGET_PADDING_PERCENT)
                        ),
                        paddingEndPercent = normalizeWidgetPaddingPercent(
                            item.optInt("paddingEndPercent", DEFAULT_WIDGET_PADDING_PERCENT)
                        ),
                        controlInactiveColorLight = parseBackgroundColor(
                            item,
                            "controlInactiveColorLight",
                        ),
                        controlInactiveColorDark = parseBackgroundColor(
                            item,
                            "controlInactiveColorDark",
                        ),
                        controlActiveColorLight = parseBackgroundColor(
                            item,
                            "controlActiveColorLight",
                        ),
                        controlActiveColorDark = parseBackgroundColor(
                            item,
                            "controlActiveColorDark",
                        ),
                        controlInactiveBackgroundColorLight = parseBackgroundColor(
                            item,
                            "controlInactiveBackgroundColorLight",
                        ),
                        controlInactiveBackgroundColorDark = parseBackgroundColor(
                            item,
                            "controlInactiveBackgroundColorDark",
                        ),
                        controlActiveBackgroundColorLight = parseBackgroundColor(
                            item,
                            "controlActiveBackgroundColorLight",
                        ),
                        controlActiveBackgroundColorDark = parseBackgroundColor(
                            item,
                            "controlActiveBackgroundColorDark",
                        ),
                        controlShape = if (item.has("controlShape")) {
                            normalizeWidgetControlShape(item.optInt("controlShape"))
                        } else {
                            null
                        },
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
        val normalizedDataKey = dataKey.trim()
            .takeUnless { it in REMOVED_WIDGET_DATA_KEYS }
            .orEmpty()
        FloatingDashboardWidgetConfig(dataKey = normalizedDataKey)
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
