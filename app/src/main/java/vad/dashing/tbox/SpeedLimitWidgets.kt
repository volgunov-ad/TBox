package vad.dashing.tbox

const val SLA_SPEED_LIMIT_WIDGET_DATA_KEY = "slaSpeedLimitWidget"
const val SPEED_LIMITER_WIDGET_DATA_KEY = "speedLimiterWidget"
/** OSM current/next speed-limit tile (UI in a later step). */
const val OSM_SPEED_LIMIT_WIDGET_DATA_KEY = "osmSpeedLimitWidget"

fun isSpeedLimiterWidgetDataKey(dataKey: String): Boolean =
    dataKey == SPEED_LIMITER_WIDGET_DATA_KEY

fun isOsmSpeedLimitWidgetDataKey(dataKey: String): Boolean =
    dataKey == OSM_SPEED_LIMIT_WIDGET_DATA_KEY
