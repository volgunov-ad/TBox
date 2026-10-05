package vad.dashing.tbox

import vad.dashing.tbox.automation.AutomationSignalId
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId

const val SLA_SPEED_LIMIT_WIDGET_DATA_KEY = "slaSpeedLimitWidget"
const val SPEED_LIMITER_WIDGET_DATA_KEY = "speedLimiterWidget"
/** OSM current/next speed-limit tile from road-match lookahead. */
const val OSM_SPEED_LIMIT_WIDGET_DATA_KEY = "osmSpeedLimitWidget"

/**
 * Hide the **configurable vehicle speed limiter** from pickers and car-settings.
 *
 * Already placed `speedLimiterWidget` tiles keep rendering. Saved automation JSON
 * still decodes and runs. Does **not** hide SLA/TSR sign recognition
 * (`slaSpeedLimitWidget`, `tsr_switch`, car-settings «Распознавание дорожных знаков»)
 * or OSM/camera limit tiles (`osmSpeedLimitWidget`).
 *
 * Flip to `false` to restore the UI.
 */
const val SPEED_LIMITER_UI_HIDDEN = true

/** mbCAN 253/254 — raw limiter switch and target. Not the SLA sign (property 18). */
val SPEED_LIMITER_AUTOMATION_PROPERTY_IDS: Set<Int> = setOf(
    MbCanKnownVehiclePropertyId.VEHICLE_SPEEDLIMIT_SWITCH,
    MbCanKnownVehiclePropertyId.VEHICLE_SPEEDLIMIT_VALUESET,
)

/**
 * High-level limiter signals for automation pickers.
 * Empty today: limiter was never published as [AutomationSignalId] (only raw CAN probes).
 */
val SPEED_LIMITER_AUTOMATION_SIGNAL_IDS: Set<AutomationSignalId> = emptySet()

fun isSpeedLimiterWidgetDataKey(dataKey: String): Boolean =
    dataKey == SPEED_LIMITER_WIDGET_DATA_KEY

fun isOsmSpeedLimitWidgetDataKey(dataKey: String): Boolean =
    dataKey == OSM_SPEED_LIMIT_WIDGET_DATA_KEY

fun isSpeedLimiterHiddenFromWidgetPicker(dataKey: String): Boolean =
    SPEED_LIMITER_UI_HIDDEN && isSpeedLimiterWidgetDataKey(dataKey)

fun isSpeedLimiterHiddenFromAutomationPicker(propertyId: Int): Boolean =
    SPEED_LIMITER_UI_HIDDEN && propertyId in SPEED_LIMITER_AUTOMATION_PROPERTY_IDS

fun isSpeedLimiterHiddenFromAutomationPicker(signalId: AutomationSignalId): Boolean =
    SPEED_LIMITER_UI_HIDDEN && signalId in SPEED_LIMITER_AUTOMATION_SIGNAL_IDS
