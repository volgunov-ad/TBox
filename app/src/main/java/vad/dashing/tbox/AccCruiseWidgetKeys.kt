package vad.dashing.tbox

/** Adaptive cruise control (ACC) enable / setpoint / cancel tile. */
const val ACC_CRUISE_WIDGET_DATA_KEY = "accCruiseWidget"

const val ACC_CRUISE_TARGET_KMH_MIN = 30
const val ACC_CRUISE_TARGET_KMH_MAX = 150
const val ACC_CRUISE_TARGET_KMH_DEFAULT = 90

const val ACC_CRUISE_STEP_INTERVAL_MS_MIN = 50
const val ACC_CRUISE_STEP_INTERVAL_MS_MAX = 1500
const val ACC_CRUISE_STEP_INTERVAL_MS_DEFAULT = 150

fun normalizeAccCruiseTargetKmh(value: Int): Int =
    value.coerceIn(ACC_CRUISE_TARGET_KMH_MIN, ACC_CRUISE_TARGET_KMH_MAX)

fun normalizeAccCruiseStepIntervalMs(value: Int): Int =
    value.coerceIn(ACC_CRUISE_STEP_INTERVAL_MS_MIN, ACC_CRUISE_STEP_INTERVAL_MS_MAX)

fun isAccCruiseWidgetDataKey(dataKey: String): Boolean =
    dataKey == ACC_CRUISE_WIDGET_DATA_KEY
