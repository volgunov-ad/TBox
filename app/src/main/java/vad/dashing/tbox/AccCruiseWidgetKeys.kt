package vad.dashing.tbox

/** Adaptive / conventional cruise: enable and ramp to a configured setpoint. */
const val ACC_CRUISE_WIDGET_DATA_KEY = "accCruiseWidget"

/**
 * Cruise status toggle (TTG-like first button): shows live ACC VSetDis / CCS on-off;
 * single tap = pause/enable (210), double tap = full cancel (212).
 */
const val CRUISE_STATUS_WIDGET_DATA_KEY = "cruiseStatusWidget"

const val ACC_CRUISE_TARGET_KMH_MIN = 30
const val ACC_CRUISE_TARGET_KMH_MAX = 150
const val ACC_CRUISE_TARGET_KMH_DEFAULT = 90

const val ACC_CRUISE_STEP_INTERVAL_MS_MIN = 50
const val ACC_CRUISE_STEP_INTERVAL_MS_MAX = 1500
const val ACC_CRUISE_STEP_INTERVAL_MS_DEFAULT = 150

/**
 * Per-tile cruise path selection for [ACC_CRUISE_WIDGET_DATA_KEY] / [CRUISE_STATUS_WIDGET_DATA_KEY].
 *
 * - [AUTO]: FRM feedback seen this session → ACC, else CCS (runtime heuristic).
 * - [ACC] / [CCS]: force that path regardless of FRM.
 */
enum class CruiseControlType(val storageKey: String) {
    AUTO("auto"),
    ACC("acc"),
    CCS("ccs"),
    ;

    companion object {
        val DEFAULT: CruiseControlType = AUTO

        fun fromStorageKey(key: String?): CruiseControlType {
            val normalized = key?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.storageKey == normalized } ?: DEFAULT
        }
    }
}

fun normalizeAccCruiseTargetKmh(value: Int): Int =
    value.coerceIn(ACC_CRUISE_TARGET_KMH_MIN, ACC_CRUISE_TARGET_KMH_MAX)

fun normalizeAccCruiseStepIntervalMs(value: Int): Int =
    value.coerceIn(ACC_CRUISE_STEP_INTERVAL_MS_MIN, ACC_CRUISE_STEP_INTERVAL_MS_MAX)

fun isAccCruiseWidgetDataKey(dataKey: String): Boolean =
    dataKey == ACC_CRUISE_WIDGET_DATA_KEY

fun isCruiseStatusWidgetDataKey(dataKey: String): Boolean =
    dataKey == CRUISE_STATUS_WIDGET_DATA_KEY

/** Either cruise tile that needs AccCruise FRM / Gasped interest. */
fun isCruiseWidgetDataKey(dataKey: String): Boolean =
    isAccCruiseWidgetDataKey(dataKey) || isCruiseStatusWidgetDataKey(dataKey)
