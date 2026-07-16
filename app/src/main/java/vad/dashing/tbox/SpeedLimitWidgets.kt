package vad.dashing.tbox

const val SLA_SPEED_LIMIT_WIDGET_DATA_KEY = "slaSpeedLimitWidget"
const val SPEED_LIMITER_WIDGET_DATA_KEY = "speedLimiterWidget"

fun isSpeedLimiterWidgetDataKey(dataKey: String): Boolean =
    dataKey == SPEED_LIMITER_WIDGET_DATA_KEY
