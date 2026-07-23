package vad.dashing.tbox

/** Stored [FloatingDashboardWidgetConfig.textAlign]: center. */
const val WIDGET_TEXT_ALIGN_CENTER = 0

/** Stored [FloatingDashboardWidgetConfig.textAlign]: start (left in LTR). */
const val WIDGET_TEXT_ALIGN_START = 1

/** Stored [FloatingDashboardWidgetConfig.textAlign]: end (right in LTR). */
const val WIDGET_TEXT_ALIGN_END = 2

const val DEFAULT_WIDGET_TEXT_ALIGN = WIDGET_TEXT_ALIGN_CENTER

/** Stored [FloatingDashboardWidgetConfig.fontWeight]: standard (Normal / 400). */
const val WIDGET_FONT_WEIGHT_NORMAL = 0

/** Stored [FloatingDashboardWidgetConfig.fontWeight]: medium (Medium / 500, default for tiles). */
const val WIDGET_FONT_WEIGHT_MEDIUM = 1

/** Stored [FloatingDashboardWidgetConfig.fontWeight]: semi-bold (SemiBold / 600). */
const val WIDGET_FONT_WEIGHT_SEMI_BOLD = 2

const val DEFAULT_WIDGET_FONT_WEIGHT = WIDGET_FONT_WEIGHT_MEDIUM

/** Stored [FloatingDashboardWidgetConfig.titlePosition]: title above value. */
const val WIDGET_TITLE_POSITION_TOP = 0

/** Stored [FloatingDashboardWidgetConfig.titlePosition]: title below value. */
const val WIDGET_TITLE_POSITION_BOTTOM = 1

const val DEFAULT_WIDGET_TITLE_POSITION = WIDGET_TITLE_POSITION_TOP

const val DEFAULT_MAIN_TAB_DASHBOARD_GRID_SPACING_DP = 8
const val DEFAULT_PANEL_GRID_SPACING_DP = 0
const val MIN_PANEL_GRID_SPACING_DP = 0
const val MAX_PANEL_GRID_SPACING_DP = 32

const val DEFAULT_WIDGET_PADDING_PERCENT = 0
const val MIN_WIDGET_PADDING_PERCENT = 0
const val MAX_WIDGET_PADDING_PERCENT = 50

const val DEFAULT_PANEL_LAYOUT_SNAP_DP = 1
const val MIN_PANEL_LAYOUT_SNAP_DP = 1
const val MAX_PANEL_LAYOUT_SNAP_DP = 50

/** Minimum relative size for main-screen panels (width/height as fraction of container). */
const val MIN_MAIN_SCREEN_PANEL_REL_FRACTION = 0.03f

/** Same floor as [MIN_MAIN_SCREEN_PANEL_REL_FRACTION], in percent for settings UI. */
const val MIN_MAIN_SCREEN_PANEL_REL_PERCENT = 3

/**
 * Layout guide grid on the main screen is drawn only when snap step is strictly greater than this
 * (dp) and the “show grid” setting is on.
 */
const val MAIN_SCREEN_LAYOUT_GRID_MIN_SNAP_DP_EXCLUSIVE = 5

fun normalizeWidgetTextAlign(raw: Int): Int =
    raw.coerceIn(WIDGET_TEXT_ALIGN_CENTER, WIDGET_TEXT_ALIGN_END)

fun normalizeWidgetFontWeight(raw: Int): Int =
    raw.coerceIn(WIDGET_FONT_WEIGHT_NORMAL, WIDGET_FONT_WEIGHT_SEMI_BOLD)

fun normalizeWidgetTitlePosition(raw: Int): Int =
    raw.coerceIn(WIDGET_TITLE_POSITION_TOP, WIDGET_TITLE_POSITION_BOTTOM)

fun normalizePanelGridSpacingDp(raw: Int): Int =
    raw.coerceIn(MIN_PANEL_GRID_SPACING_DP, MAX_PANEL_GRID_SPACING_DP)

fun normalizeWidgetPaddingPercent(raw: Int): Int =
    raw.coerceIn(MIN_WIDGET_PADDING_PERCENT, MAX_WIDGET_PADDING_PERCENT)

fun normalizePanelLayoutSnapDp(raw: Int): Int =
    raw.coerceIn(MIN_PANEL_LAYOUT_SNAP_DP, MAX_PANEL_LAYOUT_SNAP_DP)

/** Rounds [value] to the nearest multiple of [stepPx] (step must be >= 1). */
fun snapToGrid(value: Float, stepPx: Float): Float {
    val step = stepPx.coerceAtLeast(1f)
    return kotlin.math.round(value / step) * step
}

/** Like [snapToGrid], but returns [value] unchanged when [stepPx] is below 1 (snap disabled). */
fun maybeSnapToGrid(value: Float, stepPx: Float): Float {
    if (stepPx < 1f) return value
    return snapToGrid(value, stepPx)
}

/** Default title position when the field is absent from persisted JSON. */
fun resolveDefaultTitlePositionForDataKey(dataKey: String): Int =
    if (dataKey == APP_LAUNCHER_WIDGET_DATA_KEY) {
        WIDGET_TITLE_POSITION_BOTTOM
    } else {
        DEFAULT_WIDGET_TITLE_POSITION
    }

fun FloatingDashboardWidgetConfig.effectiveTitlePosition(): Int =
    normalizeWidgetTitlePosition(titlePosition)
