package vad.dashing.tbox.automation

import vad.dashing.tbox.esp.LocationSource
import vad.dashing.tbox.location.GeoDisplayState
import vad.dashing.tbox.location.GeoSpeedSource
import vad.dashing.tbox.location.LocIndicatorState

/**
 * Stable External API / automation keys for GeoDisplay enrichment (separate signal ids).
 * Does not change [AutomationSignalId.GEO_POSITION] / [AutomationSignalId.LOCATE_STATUS].
 */
object GeoAutomationSignals {
    const val SPEED_NONE = "none"
    const val SPEED_GNSS = "gnss"
    const val SPEED_CAN = "can"
    const val SPEED_RETENTION = "retention"

    val SPEED_SOURCE_OPTIONS = listOf(SPEED_GNSS, SPEED_CAN, SPEED_RETENTION, SPEED_NONE)

    const val SOURCE_TBOX = "tbox"
    const val SOURCE_COMPANION = "companion"
    const val SOURCE_ANDROID = "android"
    const val SOURCE_USB = "usb"

    val GEO_SOURCE_OPTIONS = listOf(SOURCE_TBOX, SOURCE_COMPANION, SOURCE_ANDROID, SOURCE_USB)

    /** LIVE / RETAINING still carry a usable snapshot; NONE / LOST do not. */
    fun hasDisplayData(state: GeoDisplayState): Boolean =
        when (state.indicator) {
            LocIndicatorState.LIVE, LocIndicatorState.RETAINING -> true
            LocIndicatorState.NONE, LocIndicatorState.LOST -> false
        }

    fun speedSourceKey(state: GeoDisplayState): String {
        if (!hasDisplayData(state)) return SPEED_NONE
        return when (state.speedSource) {
            GeoSpeedSource.GNSS -> SPEED_GNSS
            GeoSpeedSource.CAN -> SPEED_CAN
            GeoSpeedSource.RETENTION -> SPEED_RETENTION
        }
    }

    fun locationSourceKey(source: LocationSource): String =
        when (source) {
            LocationSource.TBOX -> SOURCE_TBOX
            LocationSource.ESP32 -> SOURCE_COMPANION
            LocationSource.ANDROID -> SOURCE_ANDROID
            LocationSource.USB -> SOURCE_USB
        }

    fun altitudeM(state: GeoDisplayState): AutomationSignalValue {
        if (!hasDisplayData(state)) return AutomationSignalValue.Unavailable
        val alt = state.altitude
        return if (alt.isFinite()) {
            AutomationSignalValue.Number(alt)
        } else {
            AutomationSignalValue.Unavailable
        }
    }

    fun speedKmh(state: GeoDisplayState): AutomationSignalValue {
        if (!hasDisplayData(state)) return AutomationSignalValue.Unavailable
        val speed = state.speedKmh.toDouble()
        return if (speed.isFinite()) {
            AutomationSignalValue.Number(speed)
        } else {
            AutomationSignalValue.Unavailable
        }
    }

    fun bearingDeg(state: GeoDisplayState): AutomationSignalValue {
        if (!hasDisplayData(state)) return AutomationSignalValue.Unavailable
        val bearing = state.bearingDeg?.toDouble() ?: return AutomationSignalValue.Unavailable
        return if (bearing.isFinite()) {
            AutomationSignalValue.Number(bearing)
        } else {
            AutomationSignalValue.Unavailable
        }
    }

    fun satellitesVisible(state: GeoDisplayState): AutomationSignalValue {
        if (!hasDisplayData(state)) return AutomationSignalValue.Unavailable
        return AutomationSignalValue.Number(state.visibleSatellites.toDouble())
    }

    fun satellitesUsed(state: GeoDisplayState): AutomationSignalValue {
        if (!hasDisplayData(state)) return AutomationSignalValue.Unavailable
        return AutomationSignalValue.Number(state.usingSatellites.toDouble())
    }

    fun gnssTruthfulState(state: GeoDisplayState): AutomationSignalValue =
        AutomationSignalValue.State(if (state.gnssTruthful) "on" else "off")
}
