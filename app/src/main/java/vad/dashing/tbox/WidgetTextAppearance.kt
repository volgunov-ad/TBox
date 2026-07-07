package vad.dashing.tbox

/** Stored [FloatingDashboardWidgetConfig.textAlign]: center. */
const val WIDGET_TEXT_ALIGN_CENTER = 0

/** Stored [FloatingDashboardWidgetConfig.textAlign]: start (left in LTR). */
const val WIDGET_TEXT_ALIGN_START = 1

/** Stored [FloatingDashboardWidgetConfig.textAlign]: end (right in LTR). */
const val WIDGET_TEXT_ALIGN_END = 2

const val DEFAULT_WIDGET_TEXT_ALIGN = WIDGET_TEXT_ALIGN_CENTER

/** Stored [FloatingDashboardWidgetConfig.fontWeight]: normal. */
const val WIDGET_FONT_WEIGHT_NORMAL = 0

/** Stored [FloatingDashboardWidgetConfig.fontWeight]: semi-bold (default for tiles). */
const val WIDGET_FONT_WEIGHT_SEMI_BOLD = 1

/** Stored [FloatingDashboardWidgetConfig.fontWeight]: bold. */
const val WIDGET_FONT_WEIGHT_BOLD = 2

const val DEFAULT_WIDGET_FONT_WEIGHT = WIDGET_FONT_WEIGHT_SEMI_BOLD

/** Stored [FloatingDashboardWidgetConfig.titlePosition]: title above value. */
const val WIDGET_TITLE_POSITION_TOP = 0

/** Stored [FloatingDashboardWidgetConfig.titlePosition]: title below value. */
const val WIDGET_TITLE_POSITION_BOTTOM = 1

const val DEFAULT_WIDGET_TITLE_POSITION = WIDGET_TITLE_POSITION_TOP

const val DEFAULT_MAIN_TAB_DASHBOARD_GRID_SPACING_DP = 8
const val DEFAULT_PANEL_GRID_SPACING_DP = 0
const val MIN_PANEL_GRID_SPACING_DP = 0
const val MAX_PANEL_GRID_SPACING_DP = 32

fun normalizeWidgetTextAlign(raw: Int): Int =
    raw.coerceIn(WIDGET_TEXT_ALIGN_CENTER, WIDGET_TEXT_ALIGN_END)

fun normalizeWidgetFontWeight(raw: Int): Int =
    raw.coerceIn(WIDGET_FONT_WEIGHT_NORMAL, WIDGET_FONT_WEIGHT_BOLD)

fun normalizeWidgetTitlePosition(raw: Int): Int =
    raw.coerceIn(WIDGET_TITLE_POSITION_TOP, WIDGET_TITLE_POSITION_BOTTOM)

fun normalizePanelGridSpacingDp(raw: Int): Int =
    raw.coerceIn(MIN_PANEL_GRID_SPACING_DP, MAX_PANEL_GRID_SPACING_DP)

/** Default title position when the field is absent from persisted JSON. */
fun resolveDefaultTitlePositionForDataKey(dataKey: String): Int =
    if (dataKey == APP_LAUNCHER_WIDGET_DATA_KEY) {
        WIDGET_TITLE_POSITION_BOTTOM
    } else {
        DEFAULT_WIDGET_TITLE_POSITION
    }

fun FloatingDashboardWidgetConfig.effectiveTitlePosition(): Int =
    normalizeWidgetTitlePosition(titlePosition)
