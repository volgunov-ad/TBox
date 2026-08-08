package vad.dashing.tbox

import org.json.JSONObject

/** Default panel corner radius (dp): no rounding. */
const val DEFAULT_PANEL_SHAPE = 0

/** Fully transparent ARGB used when panel background color is unset. */
const val TRANSPARENT_PANEL_BACKGROUND_COLOR = 0x00000000

fun normalizePanelShape(rawShape: Int): Int = normalizeWidgetShape(rawShape)

fun resolvePanelBackgroundColorArgb(color: Int?): Int =
    color ?: TRANSPARENT_PANEL_BACKGROUND_COLOR

/**
 * Writes panel background color / image / shape into DataStore-style JSON (raw ARGB ints).
 * Omits defaults (`null` colors/images, shape 0).
 */
fun putPanelBackgroundStyleFieldsDataStore(
    o: JSONObject,
    backgroundColorLight: Int?,
    backgroundColorDark: Int?,
    backgroundImageRelPathLight: String?,
    backgroundImageRelPathDark: String?,
    panelShape: Int,
) {
    backgroundColorLight?.let { o.put("panelBackgroundColorLight", it) }
    backgroundColorDark?.let { o.put("panelBackgroundColorDark", it) }
    backgroundImageRelPathLight?.trim()?.takeIf {
        PanelBackgroundImageStorage.isAllowedStoredRelPath(it)
    }?.let { o.put("panelBackgroundImageRelPathLight", it) }
    backgroundImageRelPathDark?.trim()?.takeIf {
        PanelBackgroundImageStorage.isAllowedStoredRelPath(it)
    }?.let { o.put("panelBackgroundImageRelPathDark", it) }
    val shape = normalizePanelShape(panelShape)
    if (shape != DEFAULT_PANEL_SHAPE) {
        o.put("panelShape", shape)
    }
}

/**
 * Writes panel background fields into theme.json (colors as hex, like collapse strip colors).
 */
fun putPanelBackgroundStyleFieldsTheme(
    o: JSONObject,
    backgroundColorLight: Int?,
    backgroundColorDark: Int?,
    backgroundImageRelPathLight: String?,
    backgroundImageRelPathDark: String?,
    panelShape: Int,
) {
    backgroundColorLight?.let { o.put("panelBackgroundColorLight", colorIntToHex(it)) }
    backgroundColorDark?.let { o.put("panelBackgroundColorDark", colorIntToHex(it)) }
    backgroundImageRelPathLight?.trim()?.takeIf {
        PanelBackgroundImageStorage.isAllowedStoredRelPath(it)
    }?.let { o.put("panelBackgroundImageRelPathLight", it) }
    backgroundImageRelPathDark?.trim()?.takeIf {
        PanelBackgroundImageStorage.isAllowedStoredRelPath(it)
    }?.let { o.put("panelBackgroundImageRelPathDark", it) }
    val shape = normalizePanelShape(panelShape)
    if (shape != DEFAULT_PANEL_SHAPE) {
        o.put("panelShape", shape)
    }
}

data class PanelBackgroundStyleFields(
    val backgroundColorLight: Int? = null,
    val backgroundColorDark: Int? = null,
    val backgroundImageRelPathLight: String? = null,
    val backgroundImageRelPathDark: String? = null,
    val panelShape: Int = DEFAULT_PANEL_SHAPE,
)

fun parsePanelBackgroundStyleFieldsDataStore(obj: JSONObject): PanelBackgroundStyleFields {
    val lightImg = obj.optString("panelBackgroundImageRelPathLight", "").trim()
        .takeIf { PanelBackgroundImageStorage.isAllowedStoredRelPath(it) }
    val darkImg = obj.optString("panelBackgroundImageRelPathDark", "").trim()
        .takeIf { PanelBackgroundImageStorage.isAllowedStoredRelPath(it) }
    return PanelBackgroundStyleFields(
        backgroundColorLight = if (obj.has("panelBackgroundColorLight")) {
            obj.optInt("panelBackgroundColorLight")
        } else {
            null
        },
        backgroundColorDark = if (obj.has("panelBackgroundColorDark")) {
            obj.optInt("panelBackgroundColorDark")
        } else {
            null
        },
        backgroundImageRelPathLight = lightImg,
        backgroundImageRelPathDark = darkImg,
        panelShape = normalizePanelShape(obj.optInt("panelShape", DEFAULT_PANEL_SHAPE)),
    )
}

fun parsePanelBackgroundStyleFieldsTheme(obj: JSONObject): PanelBackgroundStyleFields {
    val lightImg = obj.optString("panelBackgroundImageRelPathLight", "").trim()
        .takeIf { PanelBackgroundImageStorage.isAllowedStoredRelPath(it) }
    val darkImg = obj.optString("panelBackgroundImageRelPathDark", "").trim()
        .takeIf { PanelBackgroundImageStorage.isAllowedStoredRelPath(it) }
    val lightColor = when {
        !obj.has("panelBackgroundColorLight") -> null
        obj.opt("panelBackgroundColorLight") is String ->
            colorHexToIntOrNull(obj.optString("panelBackgroundColorLight"))
        else -> obj.optInt("panelBackgroundColorLight")
    }
    val darkColor = when {
        !obj.has("panelBackgroundColorDark") -> null
        obj.opt("panelBackgroundColorDark") is String ->
            colorHexToIntOrNull(obj.optString("panelBackgroundColorDark"))
        else -> obj.optInt("panelBackgroundColorDark")
    }
    return PanelBackgroundStyleFields(
        backgroundColorLight = lightColor,
        backgroundColorDark = darkColor,
        backgroundImageRelPathLight = lightImg,
        backgroundImageRelPathDark = darkImg,
        panelShape = normalizePanelShape(obj.optInt("panelShape", DEFAULT_PANEL_SHAPE)),
    )
}

fun collectPanelBackgroundPaths(
    backgroundImageRelPathLight: String?,
    backgroundImageRelPathDark: String?,
): Set<String> = buildSet {
    backgroundImageRelPathLight?.trim()?.takeIf {
        PanelBackgroundImageStorage.isAllowedStoredRelPath(it)
    }?.let { add(it) }
    backgroundImageRelPathDark?.trim()?.takeIf {
        PanelBackgroundImageStorage.isAllowedStoredRelPath(it)
    }?.let { add(it) }
}

fun MainScreenPanelConfig.collectPanelBackgroundPaths(): Set<String> =
    collectPanelBackgroundPaths(
        panelBackgroundImageRelPathLight,
        panelBackgroundImageRelPathDark,
    )

fun FloatingDashboardConfig.collectPanelBackgroundPaths(): Set<String> =
    collectPanelBackgroundPaths(
        panelBackgroundImageRelPathLight,
        panelBackgroundImageRelPathDark,
    )

fun MainScreenPanelConfig.resolvePanelBackgroundColor(currentTheme: Int): Int =
    resolvePanelBackgroundColorArgb(
        if (currentTheme == 2) panelBackgroundColorDark else panelBackgroundColorLight,
    )

fun FloatingDashboardConfig.resolvePanelBackgroundColor(currentTheme: Int): Int =
    resolvePanelBackgroundColorArgb(
        if (currentTheme == 2) panelBackgroundColorDark else panelBackgroundColorLight,
    )

fun MainScreenPanelConfig.resolvePanelBackgroundImageRelPath(currentTheme: Int): String? =
    if (currentTheme == 2) panelBackgroundImageRelPathDark else panelBackgroundImageRelPathLight

fun FloatingDashboardConfig.resolvePanelBackgroundImageRelPath(currentTheme: Int): String? =
    if (currentTheme == 2) panelBackgroundImageRelPathDark else panelBackgroundImageRelPathLight
