package vad.dashing.tbox.mbcan

import vad.dashing.tbox.ACC_CRUISE_WIDGET_DATA_KEY
import vad.dashing.tbox.AVH_WIDGET_DATA_KEY
import vad.dashing.tbox.CRUISE_STATUS_WIDGET_DATA_KEY
import vad.dashing.tbox.DRIVE_MODE_CYCLE_WIDGET_DATA_KEY
import vad.dashing.tbox.DRIVE_MODE_WIDGET_DATA_KEY
import vad.dashing.tbox.ESP_OFF_WIDGET_DATA_KEY
import vad.dashing.tbox.FRONT_LEFT_SEAT_HEAT_VENT_SINGLE_WIDGET_DATA_KEY
import vad.dashing.tbox.FRONT_RIGHT_SEAT_HEAT_VENT_SINGLE_WIDGET_DATA_KEY
import vad.dashing.tbox.HDC_WIDGET_DATA_KEY
import vad.dashing.tbox.HEADLIGHT_MODE_CYCLE_WIDGET_DATA_KEY
import vad.dashing.tbox.HMA_WIDGET_DATA_KEY
import vad.dashing.tbox.HVAC_AC_MAX_WIDGET_DATA_KEY
import vad.dashing.tbox.HVAC_BLOW_MODE_CYCLE_WIDGET_DATA_KEY
import vad.dashing.tbox.HVAC_BLOW_MODE_PANEL_WIDGET_HORIZONTAL_DATA_KEY
import vad.dashing.tbox.HVAC_BLOW_MODE_PANEL_WIDGET_VERTICAL_DATA_KEY
import vad.dashing.tbox.HVAC_CLIMATE_WIDGET_DATA_KEYS
import vad.dashing.tbox.HVAC_CUSTOM_MODE_CYCLE_WIDGET_DATA_KEY
import vad.dashing.tbox.HVAC_FAN_WIDGET_HORIZONTAL_DATA_KEY
import vad.dashing.tbox.HVAC_FAN_WIDGET_VERTICAL_DATA_KEY
import vad.dashing.tbox.HVAC_SYNC_WIDGET_DATA_KEY
import vad.dashing.tbox.HVAC_TEMP_LEFT_WIDGET_HORIZONTAL_DATA_KEY
import vad.dashing.tbox.HVAC_TEMP_LEFT_WIDGET_VERTICAL_DATA_KEY
import vad.dashing.tbox.HVAC_TEMP_RIGHT_WIDGET_HORIZONTAL_DATA_KEY
import vad.dashing.tbox.HVAC_TEMP_RIGHT_WIDGET_VERTICAL_DATA_KEY
import vad.dashing.tbox.LDW_WIDGET_DATA_KEY
import vad.dashing.tbox.LKA_WIDGET_DATA_KEY
import vad.dashing.tbox.PARKING_RADAR_WIDGET_DATA_KEY
import vad.dashing.tbox.REAR_FOG_WIDGET_DATA_KEY
import vad.dashing.tbox.REAR_LEFT_SEAT_HEAT_WIDGET_DATA_KEY
import vad.dashing.tbox.REAR_RIGHT_SEAT_HEAT_WIDGET_DATA_KEY
import vad.dashing.tbox.SLA_SPEED_LIMIT_WIDGET_DATA_KEY
import vad.dashing.tbox.SPEED_LIMITER_WIDGET_DATA_KEY
import vad.dashing.tbox.TJA_ICA_WIDGET_DATA_KEY
import vad.dashing.tbox.TRUNK_DOOR_WIDGET_DATA_KEY
import vad.dashing.tbox.WIPER_MAINTENANCE_WIDGET_DATA_KEY

/**
 * Single widget-key → [MbCanSignal] table for A9 mbCAN and A10 VHAL.
 *
 * [panelNeedsCan] (subscription gate) and [signalsForNormalizedKeys] (actual interest)
 * must stay on this map so a panel with only LDW / AC MAX / trunk still subscribes.
 */
internal object MbCanWidgetSignalMap {
    fun signalFor(normalizedWidgetKey: String): MbCanSignal? = when (normalizedWidgetKey) {
        "steeringWheelHeatWidget" -> MbCanSignal.SteeringWheelHeat
        WIPER_MAINTENANCE_WIDGET_DATA_KEY -> MbCanSignal.WiperMaintenance
        PARKING_RADAR_WIDGET_DATA_KEY -> MbCanSignal.ParkingRadar
        REAR_FOG_WIDGET_DATA_KEY -> MbCanSignal.RearFogLight
        HEADLIGHT_MODE_CYCLE_WIDGET_DATA_KEY -> MbCanSignal.LightControl
        AVH_WIDGET_DATA_KEY -> MbCanSignal.AvhSwitch
        HDC_WIDGET_DATA_KEY -> MbCanSignal.HdcSwitch
        ESP_OFF_WIDGET_DATA_KEY -> MbCanSignal.EspOffSwitch
        LDW_WIDGET_DATA_KEY,
        LKA_WIDGET_DATA_KEY -> MbCanSignal.LasModeSelection
        TJA_ICA_WIDGET_DATA_KEY -> MbCanSignal.TjaIca
        HMA_WIDGET_DATA_KEY -> MbCanSignal.HmaSwitch
        HVAC_CUSTOM_MODE_CYCLE_WIDGET_DATA_KEY -> MbCanSignal.HvacCustomMode
        HVAC_AC_MAX_WIDGET_DATA_KEY -> MbCanSignal.HvacAcMax
        "frontWindscreenHeatWidget" -> MbCanSignal.FrontWindscreenHeat
        "rearWindowMirrorsDefrostWidget" -> MbCanSignal.HvacDefroster
        "hvacAirRecirculationWidget" -> MbCanSignal.HvacAirRecirculation
        "hvacAcWidget" -> MbCanSignal.HvacAcPower
        "hvacAcCleanWhenLockedWidget" -> MbCanSignal.HvacAcCleanWhenLocked
        "hvacAutoWidget" -> MbCanSignal.HvacAutoState
        "hvacDefrosterFrontWidget" -> MbCanSignal.HvacDefrosterFront
        HVAC_SYNC_WIDGET_DATA_KEY -> MbCanSignal.HvacSync
        HVAC_FAN_WIDGET_HORIZONTAL_DATA_KEY,
        HVAC_FAN_WIDGET_VERTICAL_DATA_KEY -> MbCanSignal.HvacFanSpeed
        HVAC_TEMP_LEFT_WIDGET_HORIZONTAL_DATA_KEY,
        HVAC_TEMP_LEFT_WIDGET_VERTICAL_DATA_KEY -> MbCanSignal.HvacTempLeft
        HVAC_TEMP_RIGHT_WIDGET_HORIZONTAL_DATA_KEY,
        HVAC_TEMP_RIGHT_WIDGET_VERTICAL_DATA_KEY -> MbCanSignal.HvacTempRight
        HVAC_BLOW_MODE_CYCLE_WIDGET_DATA_KEY,
        HVAC_BLOW_MODE_PANEL_WIDGET_HORIZONTAL_DATA_KEY,
        HVAC_BLOW_MODE_PANEL_WIDGET_VERTICAL_DATA_KEY -> MbCanSignal.HvacBlowMode
        TRUNK_DOOR_WIDGET_DATA_KEY -> MbCanSignal.TrunkDoor
        DRIVE_MODE_WIDGET_DATA_KEY,
        DRIVE_MODE_CYCLE_WIDGET_DATA_KEY -> MbCanSignal.CarSettingsVehicleParams
        "frontLeftSeatHeatVentWidget",
        FRONT_LEFT_SEAT_HEAT_VENT_SINGLE_WIDGET_DATA_KEY -> MbCanSignal.FrontLeftSeatMode
        "frontRightSeatHeatVentWidget",
        FRONT_RIGHT_SEAT_HEAT_VENT_SINGLE_WIDGET_DATA_KEY -> MbCanSignal.FrontRightSeatMode
        REAR_LEFT_SEAT_HEAT_WIDGET_DATA_KEY -> MbCanSignal.RearLeftSeatMode
        REAR_RIGHT_SEAT_HEAT_WIDGET_DATA_KEY -> MbCanSignal.RearRightSeatMode
        SLA_SPEED_LIMIT_WIDGET_DATA_KEY -> MbCanSignal.SlaSpeedLimit
        SPEED_LIMITER_WIDGET_DATA_KEY -> MbCanSignal.SpeedLimiter
        ACC_CRUISE_WIDGET_DATA_KEY,
        CRUISE_STATUS_WIDGET_DATA_KEY -> MbCanSignal.AccCruise
        else -> null
    }

    fun panelNeedsCan(dataKeys: Iterable<String>): Boolean =
        dataKeys.any { raw ->
            val key = raw.trim()
            key.isNotBlank() && key != "null" && signalFor(key) != null
        }

    fun signalsForNormalizedKeys(normalizedKeys: Iterable<String>): MutableSet<MbCanSignal> {
        val signals = normalizedKeys.mapNotNull { signalFor(it) }.toMutableSet()
        if (normalizedKeys.any { it in HVAC_CLIMATE_WIDGET_DATA_KEYS }) {
            signals.add(MbCanSignal.HvacFrontOff)
        }
        return signals
    }
}
