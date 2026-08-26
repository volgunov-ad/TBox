package vad.dashing.tbox.mbcan

/**
 * Catalog of mbCAN capabilities collected from vendor apps in the mbCAN workspace.
 * These lists are used as a reference/spec and do not imply automatic subscription.
 */
enum class MbCanConfidence {
    CONFIRMED_IN_APP_CALLS,
    DECLARED_IN_API
}

data class MbCanTelemetryParam(
    val domain: String,
    val name: String,
    val dataType: String,
    val confidence: MbCanConfidence
)

data class MbCanControlParam(
    val domain: String,
    val name: String,
    val property: String,
    val confidence: MbCanConfidence
)

sealed class MbCanCommandPolicy {
    data class ToggleBinary(
        val offValue: Int,
        val onValue: Int,
        val unknownFallbackValue: Int = onValue
    ) : MbCanCommandPolicy()

    /** Front windscreen blow (not heated glass) — [MBFrontDefrostingView] / [AcFragment] ib_front_defrosting. */
    data object ToggleHvacFrontDefrost : MbCanCommandPolicy()

    data class SetExact(
        val allowedValues: Set<Int>
    ) : MbCanCommandPolicy()

    data class SetRange(
        val allowedValues: IntRange
    ) : MbCanCommandPolicy()

    /** Write any int as-is (debug / car-settings raw fields). */
    data object SetAnyInt : MbCanCommandPolicy()
}

data class MbCanCommandSpec(
    val propertyId: Int,
    val policy: MbCanCommandPolicy,
    val refreshSignal: MbCanSignal? = null
)

object MbCanCatalog {
    val telemetry: List<MbCanTelemetryParam> = listOf(
        MbCanTelemetryParam("Powertrain", "Vehicle speed", "eMBCAN_VEHICLE_SPEED", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("Powertrain", "Vehicle gear", "eMBCAN_VEHICLE_GEAR", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("Powertrain", "Vehicle engine", "eMBCAN_VEHICLE_ENGINE", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("Powertrain", "Vehicle engine+gear", "eMBCAN_VEHICLE_ENGINE_GEAR", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("Powertrain", "EBS SOC", "eMBCAN_VEHICLE_EBS_SOC", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("Powertrain", "Consumption", "eMBCAN_VEHICLE_CONSUMPTION", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("Powertrain", "Inverter status", "eMBCAN_VEHICLE_INVERTER_STATUS", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("Body/BCM", "Door", "eMBCAN_VEHICLE_DOOR", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("Body/BCM", "BCM status", "eMBCAN_VEHICLE_BCM_STATUS", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("Body/BCM", "Seat belt status", "eMBCAN_SEAT_BELT_STATUS", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("Body/BCM", "Seat status", "eMBCAN_SEAT_STATUS", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("Body/BCM", "WPC status", "eMBCAN_WPC_STATUS", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("Climate", "PM2.5", "eMBCAN_PM25INFO", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("Climate", "AQS status", "eMBCAN_VEHICLE_AQS_STATUS", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("ADAS", "Radar sensor", "eMBCAN_RADARSENSOR", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("ADAS", "RCTA alarm", "eMBCAN_RCTA_ALARM", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("ADAS", "BSD alarm", "eMBCAN_BSD_ALARM", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("ADAS", "DOW alarm", "eMBCAN_DOW_ALARM", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("ADAS", "LKA status", "eMBCAN_VEHICLE_LKA_STATUS", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("ADAS", "FRM info", "eMBCAN_VEHICLE_FRM_INFO", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("Multimedia", "Audio cfg", "eMBCAN_CFG_AUDIO", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("Multimedia", "Vehicle cfg", "eMBCAN_CFG_VEHICLE", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("Multimedia", "DMS cfg", "eMBCAN_CFG_DMS", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("EV/Charge", "Charging reserve", "eMBCAN_CHARGING_RESERVE", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("System", "System mode", "eMBCAN_SYSTEMMODE", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("System", "Hard key", "eMBCAN_HARDKEY", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("System", "Upgrade progress", "eMBCAN_UPGRADE_PROGRESS", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("System", "DVR status", "eMBCAN_DVR_STATUS", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("System", "DVR params", "eMBCAN_VEHICLE_DVR_PARAM", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("System", "DTC", "eMBCAN_DTC", MbCanConfidence.DECLARED_IN_API),
        MbCanTelemetryParam("System", "External temp raw", "eMBCAN_VEHICLE_EXTERNAL_TEMP_RAW", MbCanConfidence.DECLARED_IN_API),
        MbCanTelemetryParam("System", "ICM drive info", "eMBCAN_VEHICLE_ICM_DRIVE_INFO", MbCanConfidence.DECLARED_IN_API)
    )

    val controls: List<MbCanControlParam> = listOf(
        MbCanControlParam("Powertrain", "Drive mode", "eVEHICLE_DRIVEMODE", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Powertrain", "Power mode", "eVEHICLE_POWERMODE", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Powertrain", "AVH switch", "eVEHICLE_AVH_SWITCH", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Powertrain", "HDC switch", "eVEHICLE_HDC_SWITCH", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Powertrain", "ESC off switch", "eVEHICLE_ESCOFF_SWITCH", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Powertrain", "ISS switch", "eVEHICLE_ISS_SWITCH", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("EV/Charge", "Wireless phone charging switch", "eVEHICLE_CHG_WIRELESS_SWITCH", MbCanConfidence.DECLARED_IN_API),
        MbCanControlParam("Body/BCM", "Door auto lock", "eVEHICLE_PROPERTY_DOOR_AUTO_LOCK", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Body/BCM", "Ignition-off unlock", "eVEHICLE_PROPERTY_DOOR_IGNOFF_UNLOCK", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Body/BCM", "Mirror reverse turn location", "eVEHICLE_SET_MIRROR_REVERSE_TURN_LOC", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Climate", "HVAC power", "eVEHICLE_PROPERTY_HVAC_POWER", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Climate", "HVAC auto", "eHVAC_AUTO_STATE", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Climate", "HVAC fan speed", "eVEHICLE_PROPERTY_HVAC_FAN_SPEED", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Climate", "HVAC air recirculation", "eVEHICLE_PROPERTY_HVAC_AIR_RECIRCULATION", MbCanConfidence.DECLARED_IN_API),
        MbCanControlParam("Climate", "HVAC blower delay / AC clean when locked", "eVEHICLE_PROPERTY_HVAC_BLOWER_DELAY", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Climate", "PM25 display source", "eVEHICLE_PM25_DISPLAY_TOGGLE", MbCanConfidence.DECLARED_IN_API),
        MbCanControlParam("Climate", "UV lamp request", "eVEHICLE_UV_LAMP_REQ", MbCanConfidence.DECLARED_IN_API),
        MbCanControlParam("Climate", "Sterilize strength request", "eVEHICLE_STERILIZE_STRENGTH_REQ", MbCanConfidence.DECLARED_IN_API),
        MbCanControlParam("Climate", "HVAC front defrost blow", "eVEHICLE_PROPERTY_HVAC_FAN_DIRECTION", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Climate", "HVAC temperature", "eVEHICLE_PROPERTY_HVAC_TEMPERATURE", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Climate", "Anion air purification", "eVEHICLE_PROPERTY_HVAC_AQS", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Climate", "Fragrance switch", "eVEHICLE_PROPERTY_FRAGRANCE_SWITCH", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Climate", "Fragrance smell", "eVEHICLE_PROPERTY_FRAGRANCE_SMELL", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Climate", "Fragrance concentration", "eVEHICLE_PROPERTY_FRAGRANCE_CONCENTRATION", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("ADAS", "FCW switch", "eFCW_SWTICH", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("ADAS", "Auto brake switch", "eVEHICLE_PROPERTY_ACC_AUTOBRAKE_SW", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("ADAS", "LKA sensitivity", "eVEHICLE_PROPERTY_LAS_SENSITIVITY_LEVEL", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("ADAS", "LAS mode (LDW/LKA/OFF)", "eVEHICLE_PROPERTY_LAS_MODE_SELECTION", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("ADAS", "TJA/ICA switch", "eVEHICLE_PROPERTY_TJA_ICA", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("ADAS", "HMA / smart high beam", "eVEHICLE_SMART_HIGHBEAM_SWITCH", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Climate", "HVAC custom mode (ECO/Comfort/Strong)", "eHVAC_CUSTOM", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Climate", "AC MAX", "eVEHICLE_SET_RRM_ACMAX_REQ", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Multimedia", "EQ mode", "eAUDIO_PROPERTY_EQMODE", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Multimedia", "EQ bass band", "eAUDIO_PROPERTY_EQBAND_BASS", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Multimedia", "EQ middle band", "eAUDIO_PROPERTY_EQBAND_MIDDLE", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Multimedia", "EQ treble band", "eAUDIO_PROPERTY_EQBAND_TREBLE", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Multimedia", "Balance", "eAUDIO_PROPERTY_BALANCE_BALANCE", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Multimedia", "Fader", "eAUDIO_PROPERTY_BALANCE_FADER", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Multimedia", "Media volume key mode", "eAUDIO_PROPERTY_VOLUME_KEY", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Multimedia", "Volume vs speed", "eAUDIO_PROPERTY_VOLUME_SPEED", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Multimedia", "AVM language", "eAVM_SET_LANG", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("System", "System reboot", "eSYSTEM_REBOOT", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("System", "ICM brightness mode", "eVEHICLE_SET_ICM_BRIGHTNESS_MODE", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("System", "Steering wheel heating switch", "eVEHICLE_SET_MFS_HEAT_SWITCH", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("System", "Wiper maintenance switch", "eVEHICLE_SET_WIPER_MAINTENANCE_SWITCH", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("System", "Parking radar switch", "eVEHICLE_SET_PAS_SWITCH", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Chassis", "AVH / Auto Hold switch", "eVEHICLE_AVH_SWITCH", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Chassis", "HDC switch", "eVEHICLE_HDC_SWITCH", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Chassis", "ESP off switch", "eVEHICLE_ESCOFF_SWITCH", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Lights", "Headlight mode", "eVEHICLE_LIGHTCONTROL", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Lights", "Rear fog light", "eVEHICLE_REARFOGLIGHT", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
    )
}

object MbCanKnownVehiclePropertyId {
    // MBVehicleProperty.eVEHICLE_SET_MFS_HEAT_SWITCH.
    const val STEERING_WHEEL_HEAT_SWITCH = 188
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_SET_WIPER_MAINTENANCE_SWITCH]. */
    const val WIPER_MAINTENANCE_SWITCH = 185
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_SET_PAS_SWITCH]. */
    const val PARKING_RADAR_SWITCH = 218
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_AVH_SWITCH] — Auto Hold. */
    const val AVH_SWITCH = 142
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_HDC_SWITCH] — Hill Descent Control. */
    const val HDC_SWITCH = 143
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_ESCOFF_SWITCH] — ESP off. */
    const val ESP_OFF_SWITCH = 144
    /**
     * [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_LIGHTCONTROL] —
     * headlight mode: **1** AUTO, **2** PARK, **3** LOW, **4** OFF
     * (stock A9 [Em_HeadlampControl_ListItem_value] / A10 [CarOutLightFragment]).
     */
    const val LIGHTCONTROL = 135
    const val LIGHTCONTROL_AUTO = 1
    const val LIGHTCONTROL_PARK = 2
    const val LIGHTCONTROL_LOW = 3
    const val LIGHTCONTROL_OFF = 4
    /**
     * [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_REARFOGLIGHT] —
     * rear fog; mbCAN **1** off / **2** on; A10 VHAL **1** on / **2** off.
     */
    const val REAR_FOG_LIGHT = 136
    /** Door auto lock: mbCAN 1 off / 2 on; VHAL write 2 off / 1 on. */
    const val DOOR_AUTO_LOCK = 1
    /** Ignition-off door unlock: mbCAN 1 off / 2 on; VHAL write 2 off / 1 on. */
    const val DOOR_IGNOFF_UNLOCK = 2
    /** Follow-me-home delay: mbCAN 30/60/3(off), VHAL 1/2/3(off). */
    const val HEADLIGHTS_HOMELIGHT_DELAY = 7
    /** Driver-only (1) or all-door (2) unlock. */
    const val DRIVER_UNLOCK_MODE = 131
    /** Remote lock feedback: light+horn (1), light (2), horn (3). */
    const val DEFENCES_PROMPT = 3
    /** Wiper sensitivity level 1..4. */
    const val WIPER_SENSITIVITY = 191
    /** Rear wiper: mbCAN 1 off / 2 on; VHAL write 2 off / 1 on. */
    const val REAR_WIPER = 186
    /** Low-beam height UI level 1..4 (VHAL feedback/write are inverted). */
    const val HIGHBEAM_ADJUST = 129
    /** Turn-signal flash count 1..3 (VHAL feedback is zero-based). */
    const val TURN_FLASH_COUNT = 8
    /**
     * [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_PROPERTY_LAS_MODE_SELECTION] —
     * lane assist mode: **1** LDW, **2** LKA, **3** OFF.
     */
    const val LAS_MODE_SELECTION = 17
    const val LAS_MODE_LDW = 1
    const val LAS_MODE_LKA = 2
    const val LAS_MODE_OFF = 3
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_PROPERTY_TJA_ICA] — 1 off, 2 on. */
    const val TJA_ICA_SWITCH = 23
    /** Blind-spot detection: mbCAN 1 off / 2 on; VHAL writes 2 off / 1 on. */
    const val BLIND_AREA_DETECTION = 15
    /** Door-open warning: mbCAN 1 off / 2 on; VHAL writes 2 off / 1 on. */
    const val DOOR_OPEN_WARNING = 13
    /** Forward-collision warning master; enabled value is 2 on both backends. */
    const val FCW_SWITCH = 96
    /** Coupled with [FCW_SWITCH] by stock CarSettings. */
    const val ACC_AUTOBRAKE_SWITCH = 20
    /** Coupled with [FCW_SWITCH] by stock CarSettings. */
    const val SAFE_DISTANCE_WARNING = 22
    /** FCW warning-distance setting. */
    const val FCW_SENSITIVITY = 97
    /** LDW sensitivity: mbCAN 0 low / 1 high. */
    const val LAS_SENSITIVITY_LEVEL = 16
    /**
     * [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_SMART_HIGHBEAM_SWITCH] —
     * HMA / intelligent high beam (A9 id **130**; not headlights **19**).
     */
    const val HMA_SWITCH = 130
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eHVAC_CUSTOM] — ECO/Comfort/Strong; write 1/2/3. */
    const val HVAC_CUSTOM = 140
    const val HVAC_CUSTOM_ECO = 1
    const val HVAC_CUSTOM_COMFORT = 2
    const val HVAC_CUSTOM_STRONG = 3
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_SET_RRM_ACMAX_REQ] — AC MAX; 1 off, 2 on. */
    const val HVAC_AC_MAX = 228
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVHEICEL_FRONTWINDSCREEN_HEAT] */
    const val FRONT_WINDSCREEN_HEAT_SWITCH = 316
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_PROPERTY_HVAC_DEFROSTER] — rear window + mirrors. */
    const val HVAC_DEFROSTER_SWITCH = 41
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_PROPERTY_HVAC_AIR_RECIRCULATION] — property id. */
    const val HVAC_AIR_RECIRCULATION = 39
    /** [canSetVehicleParam]/[canGetVehicleParam] value: recirculation on. */
    const val HVAC_AIR_RECIRCULATION_VALUE_ON = 1
    /** Same property: recirculation off. */
    const val HVAC_AIR_RECIRCULATION_VALUE_OFF = 2
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_PROPERTY_HVAC_POWER] — AC compressor; 1 off, 2 on. */
    const val HVAC_POWER = 36
    /**
     * [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_PROPERTY_HVAC_BLOWER_DELAY] —
     * AC clean when locked (stock ACSettings Set switch); mbCAN 1 off, 2 on.
     */
    const val HVAC_BLOWER_DELAY = 52
    /** mbCAN / [MBWTSwitch] on value for [HVAC_BLOWER_DELAY]. */
    const val HVAC_BLOWER_DELAY_VALUE_ON = 2
    /** mbCAN / [MBWTSwitch] off value for [HVAC_BLOWER_DELAY]. */
    const val HVAC_BLOWER_DELAY_VALUE_OFF = 1
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eHVAC_AUTO_STATE] — AUTO mode; 1 off, 2 on. */
    const val HVAC_AUTO_STATE = 110
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_PROPERTY_HVAC_AQS] — anion purification; 1 off, 2 on. */
    const val HVAC_AQS = 42
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_PROPERTY_FRAGRANCE_SWITCH] — 1 off, 2 on (A9 only). */
    const val FRAGRANCE_SWITCH = 33
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_PROPERTY_FRAGRANCE_SMELL] — 1 Meteor, 2 Boss, 3 Tea (A9 only). */
    const val FRAGRANCE_SMELL = 34
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_PROPERTY_FRAGRANCE_CONCENTRATION] — 1 low, 2 mid, 3 high (A9 only). */
    const val FRAGRANCE_CONCENTRATION = 35
    /** First blowing after vehicle start: mbCAN 1 off / 2 on; VHAL 2 off / 1 on. */
    const val POWER_FIRST_BREATH = 53
    /** Reduce fan speed while Bluetooth is active: mbCAN 1 off / 2 on; VHAL 2 off / 1 on. */
    const val BT_REDUCED_WIND_SPEED = 51
    /** Automatic ventilation: mbCAN 1 off / 2 on; VHAL 2 off / 1 on. */
    const val HVAC_VENTILATION_AUTO_SWITCH = 141
    /** HUD master switch: mbCAN 1 off / 2 on; VHAL 2 off / 1 on. */
    const val HUD_SWITCH = 220
    /** HUD height level, 1..10. */
    const val HUD_HEIGHT = 221
    /** HUD brightness level, 1..10. */
    const val HUD_BRIGHTNESS = 222
    /** HUD display mode: 1 standard, 2 snow. */
    const val HUD_DISPLAY_MODE = 223
    /** HUD automatic brightness: mbCAN 1 off / 2 on; VHAL 2 off / 1 on. */
    const val HUD_AUTO_BRIGHTNESS = 227
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_SET_ICM_BRIGHTNESS_MODE] — 0 auto / 1 manual. */
    const val ICM_BRIGHTNESS_MODE = 208
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_ICM_BRIGHTNESS_MANUAL_ADJ] — manual level 1..10. */
    const val ICM_BRIGHTNESS_MANUAL = 209
    /** Overspeed alarm threshold, raw value maps to km/h through [CarSettingsHudDomain]. */
    const val OVERSPEED_ALARM_SET = 296
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_PROPERTY_HVAC_TEMPERATURE] — left zone, °C×10. */
    const val HVAC_TEMPERATURE_LEFT = 37
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eHVAC_FR_TEMPERATURE] — right zone, °C×10. */
    const val HVAC_TEMPERATURE_RIGHT = 111
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_PROPERTY_HVAC_FAN_SPEED] — 0..7. */
    const val HVAC_FAN_SPEED = 38
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eFRONT_OFF] — front climate off; 1 running, 2 off. */
    const val HVAC_FRONT_OFF = 90
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eSYNCSWTICH_REQ] — dual-zone sync; 1 off, 2 on. */
    const val HVAC_SYNC_SWITCH = 94
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_PLG_CONTROL] — power liftgate pulse. */
    const val TRUNK_PLG_CONTROL = 134
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_PROPERTY_DOOR_TRUNK_POS] — max opening angle setting (stock dialog). */
    const val DOOR_TRUNK_POS = 6
    /** VHAL [R_0402_PLG_1_RearDoorStatus] — 0 closed, 1 open (stock [CarCommon3]). */
    const val TRUNK_STATUS = 71343
    /** PLG movement direction (A10 read); also BCM `nRearDoorMoveDir` push on A9. */
    const val TRUNK_REAR_DOOR_MOVE_DIR = 71341
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_SET_MIRROR_FOLD_SWITCH] — fold=1, unfold=2. */
    const val MIRROR_FOLD_SWITCH = 230
    /** [MBVehicleProperty.eVEHICLE_PROPERTY_MIRROR_AUTOFOLD_SW] — 1 off, 2 on. */
    const val MIRROR_AUTOFOLD_SW = 4
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_PROPERTY_HVAC_FAN_DIRECTION] — blow mode. */
    const val HVAC_FAN_DIRECTION = 40
    /** mbCAN blow modes (Android 9 [MBFrontDefrostingView]). */
    const val HVAC_FAN_DIRECTION_FACE = 1
    const val HVAC_FAN_DIRECTION_FOOT = 2
    const val HVAC_FAN_DIRECTION_FACE_FOOT = 3
    const val HVAC_FAN_DIRECTION_DEFROST = 4
    const val HVAC_FAN_DIRECTION_DEFROST_FOOT = 5
    /** VHAL blow modes (Android 10 [AcFragment.mWindModeIds]). */
    const val HVAC_FAN_DIRECTION_VHAL_FACE = 0
    const val HVAC_FAN_DIRECTION_VHAL_DEFROST_FOOT = 3
    const val HVAC_FAN_DIRECTION_VHAL_DEFROST = 4
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_CHG_WIRELESS_SWITCH] — 1 off, 2 on. */
    const val CHG_WIRELESS_SWITCH = 264
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_PROPERTY_STEERING_MODE] — 0–6. */
    const val VEHICLE_PROPERTY_STEERING_MODE = 24
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_PROPERTY_EPS_MODE] — 0–6. */
    const val VEHICLE_PROPERTY_EPS_MODE = 25
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eSYSTEM_MODE] — 0–6. */
    const val SYSTEM_MODE = 73
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eSYSTEM_REBOOT] — head unit reboot via [canSetVehicleParam]. */
    const val SYSTEM_REBOOT = 74
    /** Value written to [SYSTEM_REBOOT] to request HU reboot. */
    const val SYSTEM_REBOOT_VALUE = 1
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_DRIVEMODE] — 0–6. */
    const val VEHICLE_DRIVEMODE = 145
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_POWERMODE] — 0–6. */
    const val VEHICLE_POWERMODE = 147
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_DRIVEMODE_6DCT_WET] — 0–6. */
    const val VEHICLE_DRIVEMODE_6DCT_WET = 149
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_PROPERTY_TSR_SPEED_LIMIT_SIGN] — SLA/TSR; 1 off, 2 on. */
    const val VEHICLE_TSR_SWITCH = 18
    /**
     * [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_SPEEDLIMIT_VALUESET] — km/h raw.
     * Live read feeds the speed-limiter widget / car-settings raw field. VHAL map may be missing on some HUs.
     */
    const val VEHICLE_SPEEDLIMIT_VALUESET = 253
    /**
     * [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_SPEEDLIMIT_SWITCH] — typically 1 off, 2 on (mbCAN).
     * Live read feeds widget active state / car-settings raw field. VHAL map may be missing on some HUs.
     */
    const val VEHICLE_SPEEDLIMIT_SWITCH = 254
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_PM25_DISPLAY_TOGGLE] — 1 inside, 2 outside. */
    const val VEHICLE_PM25_DISPLAY_TOGGLE = 163
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_UV_LAMP_REQ] — 1 off, 2 on, 3 auto. */
    const val VEHICLE_UV_LAMP_REQ = 164
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_STERILIZE_STRENGTH_REQ] — 1 low, 2 medium, 3 high. */
    const val VEHICLE_STERILIZE_STRENGTH_REQ = 165
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eSOURCE_STATION_MODE] — 1 off, 2 on. */
    const val SOURCE_STATION_MODE = 127
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_VEHWASH_MODESET] — 1 off, 2 on. */
    const val VEHICLE_VEHWASH_MODESET = 252
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICEL_BRAKE_PEDA_FEEL_MODE] — 0–6. */
    const val VEHICEL_BRAKE_PEDA_FEEL_MODE = 300
    const val FRONT_LEFT_SEAT_HEAT_VENT_SWITCH = 138
    const val FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH = 139
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVHEICEL_SEAT_LR_HEATVENTSW] — rear heat only (values 1–4). */
    const val REAR_LEFT_SEAT_HEAT_SWITCH = 318
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVHEICEL_SEAT_RR_HEATVENTSW] — rear heat only (values 1–4). */
    const val REAR_RIGHT_SEAT_HEAT_SWITCH = 319
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_MFS_CRUISE_CONTROL] — main cruise switch (enable / full off). */
    const val MFS_CRUISE_CONTROL = 210
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_MFS_CANCEL] — pause Active→Standby on Dashing. */
    const val MFS_CANCEL = 212
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_MFS_RESPLUS] — RES / +1 km/h pulse. */
    const val MFS_RES_PLUS = 213
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_MFS_SETMINUS] — SET / −1 km/h pulse. */
    const val MFS_SET_MINUS = 214
}

/** [com.mengbo.mbCan.defines.MBAudioProperty] integer ids for [com.mengbo.mbCan.MBCanEngine.canGetAudioParam]. */
object MbCanKnownAudioPropertyId {
    /** [com.mengbo.mbCan.defines.MBAudioProperty.eAUDIO_PROPERTY_VOLUME] */
    const val VOLUME = 2
    /** [com.mengbo.mbCan.defines.MBAudioProperty.eAUDIO_PROPERTY_VOLUME_SPEED] */
    const val VOLUME_SPEED = 13
    /** `eAUDIO_PROPERTY_BALANCE_BALANCE`: raw 0…14 maps to UI −7…+7. */
    const val BALANCE = 3
    /** `eAUDIO_PROPERTY_BALANCE_FADER`: raw 0…14 maps to UI −7…+7. */
    const val FADER = 4
    /** `eAUDIO_PROPERTY_EQBAND_BASS`: −7…+7. */
    const val EQ_BAND_BASS = 5
    /** `eAUDIO_PROPERTY_EQBAND_MIDDLE`: −7…+7. */
    const val EQ_BAND_MIDDLE = 6
    /** `eAUDIO_PROPERTY_EQBAND_TREBLE`: −7…+7. */
    const val EQ_BAND_TREBLE = 7
    /** `eAUDIO_PROPERTY_EQMODE`: 1 Pop, 2 Rock, 3 Jazz, 4 Classic, 5 Voice, 255 Custom. */
    const val EQ_MODE = 10
    /** `eAUDIO_PROPERTY_VOLUME_RADAS`: 1 Low, 2 Medium, 3 High. */
    const val VOLUME_RADAR = 11
    /** `eAUDIO_PROPERTY_VOLUME_KEY`: 0 Mute, 1 Low, 2 Medium, 3 High. */
    const val VOLUME_KEY = 17
    /** `eAUDIO_AUDIO_HEADREST_SPEAKER`: A9 0 close / 1 headrest / 2 auxiliary. */
    const val HEADREST_SPEAKER = 37
}

data class MbCanAudioCommandSpec(
    val propertyId: Int,
    val policy: MbCanCommandPolicy,
    val refreshSignal: MbCanSignal? = null,
)

object MbCanAudioCommandRegistry {
    private val specsByPropertyId: Map<Int, MbCanAudioCommandSpec> = listOf(
        MbCanAudioCommandSpec(
            propertyId = MbCanKnownAudioPropertyId.VOLUME_SPEED,
            policy = MbCanCommandPolicy.SetExact(allowedValues = setOf(1, 2, 3, 4)),
            refreshSignal = MbCanSignal.AudioVolumeSpeed,
        ),
        MbCanAudioCommandSpec(
            propertyId = MbCanKnownAudioPropertyId.VOLUME_KEY,
            policy = MbCanCommandPolicy.SetExact(allowedValues = setOf(0, 1, 2, 3)),
            refreshSignal = MbCanSignal.AudioKeyToneVolume,
        ),
        MbCanAudioCommandSpec(
            propertyId = MbCanKnownAudioPropertyId.VOLUME_RADAR,
            policy = MbCanCommandPolicy.SetExact(allowedValues = setOf(1, 2, 3)),
            refreshSignal = MbCanSignal.AudioRadarAlarmVolume,
        ),
        MbCanAudioCommandSpec(MbCanKnownAudioPropertyId.EQ_MODE, MbCanCommandPolicy.SetExact(CarSettingsAudioDomain.eqModes), MbCanSignal.AudioEqMode),
        MbCanAudioCommandSpec(MbCanKnownAudioPropertyId.EQ_BAND_BASS, MbCanCommandPolicy.SetRange(CarSettingsAudioDomain.eqBandUiRange), MbCanSignal.AudioEqBass),
        MbCanAudioCommandSpec(MbCanKnownAudioPropertyId.EQ_BAND_MIDDLE, MbCanCommandPolicy.SetRange(CarSettingsAudioDomain.eqBandUiRange), MbCanSignal.AudioEqMiddle),
        MbCanAudioCommandSpec(MbCanKnownAudioPropertyId.EQ_BAND_TREBLE, MbCanCommandPolicy.SetRange(CarSettingsAudioDomain.eqBandUiRange), MbCanSignal.AudioEqTreble),
        MbCanAudioCommandSpec(MbCanKnownAudioPropertyId.BALANCE, MbCanCommandPolicy.SetRange(CarSettingsAudioDomain.balanceFaderUiRange), MbCanSignal.AudioBalance),
        MbCanAudioCommandSpec(MbCanKnownAudioPropertyId.FADER, MbCanCommandPolicy.SetRange(CarSettingsAudioDomain.balanceFaderUiRange), MbCanSignal.AudioFader),
    ).associateBy { it.propertyId }

    fun get(propertyId: Int): MbCanAudioCommandSpec? = specsByPropertyId[propertyId]

    fun all(): List<MbCanAudioCommandSpec> = specsByPropertyId.values.sortedBy { it.propertyId }
}

object MbCanCommandRegistry {
    private val specsByPropertyId: Map<Int, MbCanCommandSpec> = listOf(
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.STEERING_WHEEL_HEAT_SWITCH,
            policy = MbCanCommandPolicy.ToggleBinary(
                offValue = 1,
                onValue = 2,
                unknownFallbackValue = 2
            ),
            refreshSignal = MbCanSignal.SteeringWheelHeat
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.WIPER_MAINTENANCE_SWITCH,
            // Same as доп. меню: service on=2, working/off=1.
            policy = MbCanCommandPolicy.ToggleBinary(
                offValue = 1,
                onValue = 2,
                unknownFallbackValue = 2
            ),
            refreshSignal = MbCanSignal.WiperMaintenance
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.PARKING_RADAR_SWITCH,
            policy = MbCanCommandPolicy.ToggleBinary(
                offValue = 1,
                onValue = 2,
                unknownFallbackValue = 2
            ),
            refreshSignal = MbCanSignal.ParkingRadar
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.AVH_SWITCH,
            policy = MbCanCommandPolicy.ToggleBinary(
                offValue = 1,
                onValue = 2,
                unknownFallbackValue = 2
            ),
            refreshSignal = MbCanSignal.AvhSwitch
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.HDC_SWITCH,
            policy = MbCanCommandPolicy.ToggleBinary(
                offValue = 1,
                onValue = 2,
                unknownFallbackValue = 2
            ),
            refreshSignal = MbCanSignal.HdcSwitch
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.ESP_OFF_SWITCH,
            policy = MbCanCommandPolicy.ToggleBinary(
                offValue = 1,
                onValue = 2,
                unknownFallbackValue = 2
            ),
            refreshSignal = MbCanSignal.EspOffSwitch
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.LIGHTCONTROL,
            policy = MbCanCommandPolicy.SetExact(
                allowedValues = setOf(
                    MbCanKnownVehiclePropertyId.LIGHTCONTROL_AUTO,
                    MbCanKnownVehiclePropertyId.LIGHTCONTROL_PARK,
                    MbCanKnownVehiclePropertyId.LIGHTCONTROL_LOW,
                    MbCanKnownVehiclePropertyId.LIGHTCONTROL_OFF,
                )
            ),
            refreshSignal = MbCanSignal.LightControl
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.REAR_FOG_LIGHT,
            policy = MbCanCommandPolicy.ToggleBinary(
                offValue = 1,
                onValue = 2,
                unknownFallbackValue = 2
            ),
            refreshSignal = MbCanSignal.RearFogLight
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.DOOR_AUTO_LOCK,
            policy = MbCanCommandPolicy.ToggleBinary(offValue = 1, onValue = 2, unknownFallbackValue = 2),
            refreshSignal = MbCanSignal.AutoLock,
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.DOOR_IGNOFF_UNLOCK,
            policy = MbCanCommandPolicy.ToggleBinary(offValue = 1, onValue = 2, unknownFallbackValue = 2),
            refreshSignal = MbCanSignal.AutoUnlock,
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.HEADLIGHTS_HOMELIGHT_DELAY,
            policy = MbCanCommandPolicy.SetExact(allowedValues = setOf(30, 60, 3)),
            refreshSignal = MbCanSignal.FollowMeHome,
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.DRIVER_UNLOCK_MODE,
            policy = MbCanCommandPolicy.SetExact(allowedValues = setOf(1, 2)),
            refreshSignal = MbCanSignal.DriverUnlockMode,
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.DEFENCES_PROMPT,
            policy = MbCanCommandPolicy.SetExact(allowedValues = setOf(1, 2, 3)),
            refreshSignal = MbCanSignal.RemoteLockFeedback,
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.WIPER_SENSITIVITY,
            policy = MbCanCommandPolicy.SetExact(allowedValues = (1..4).toSet()),
            refreshSignal = MbCanSignal.WiperSensitivity,
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.REAR_WIPER,
            policy = MbCanCommandPolicy.ToggleBinary(offValue = 1, onValue = 2, unknownFallbackValue = 2),
            refreshSignal = MbCanSignal.RearWiper,
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.MIRROR_AUTOFOLD_SW,
            policy = MbCanCommandPolicy.ToggleBinary(offValue = 1, onValue = 2, unknownFallbackValue = 2),
            refreshSignal = MbCanSignal.MirrorAutoFold,
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.HIGHBEAM_ADJUST,
            policy = MbCanCommandPolicy.SetExact(allowedValues = (1..4).toSet()),
            refreshSignal = MbCanSignal.LowBeamHeight,
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.TURN_FLASH_COUNT,
            policy = MbCanCommandPolicy.SetExact(allowedValues = (1..3).toSet()),
            refreshSignal = MbCanSignal.TurnFlashCount,
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.LAS_MODE_SELECTION,
            policy = MbCanCommandPolicy.SetExact(
                allowedValues = setOf(
                    MbCanKnownVehiclePropertyId.LAS_MODE_LDW,
                    MbCanKnownVehiclePropertyId.LAS_MODE_LKA,
                    MbCanKnownVehiclePropertyId.LAS_MODE_OFF,
                )
            ),
            refreshSignal = MbCanSignal.LasModeSelection
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.TJA_ICA_SWITCH,
            policy = MbCanCommandPolicy.ToggleBinary(
                offValue = 1,
                onValue = 2,
                unknownFallbackValue = 2
            ),
            refreshSignal = MbCanSignal.TjaIca
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.BLIND_AREA_DETECTION,
            policy = MbCanCommandPolicy.ToggleBinary(offValue = 1, onValue = 2, unknownFallbackValue = 2),
            refreshSignal = MbCanSignal.Bsd,
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.DOOR_OPEN_WARNING,
            policy = MbCanCommandPolicy.ToggleBinary(offValue = 1, onValue = 2, unknownFallbackValue = 2),
            refreshSignal = MbCanSignal.Dow,
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.FCW_SWITCH,
            policy = MbCanCommandPolicy.ToggleBinary(offValue = 1, onValue = 2, unknownFallbackValue = 2),
            refreshSignal = MbCanSignal.Fcw,
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.ACC_AUTOBRAKE_SWITCH,
            policy = MbCanCommandPolicy.ToggleBinary(offValue = 1, onValue = 2, unknownFallbackValue = 2),
            refreshSignal = MbCanSignal.Fcw,
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.SAFE_DISTANCE_WARNING,
            policy = MbCanCommandPolicy.ToggleBinary(offValue = 1, onValue = 2, unknownFallbackValue = 2),
            refreshSignal = MbCanSignal.Fcw,
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.FCW_SENSITIVITY,
            policy = MbCanCommandPolicy.SetExact(allowedValues = setOf(1, 2, 3)),
            refreshSignal = MbCanSignal.FcwSensitivity,
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.LAS_SENSITIVITY_LEVEL,
            policy = MbCanCommandPolicy.SetExact(allowedValues = setOf(0, 1)),
            refreshSignal = MbCanSignal.LdwSensitivity,
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.HMA_SWITCH,
            policy = MbCanCommandPolicy.ToggleBinary(
                offValue = 1,
                onValue = 2,
                unknownFallbackValue = 2
            ),
            refreshSignal = MbCanSignal.HmaSwitch
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.HVAC_CUSTOM,
            policy = MbCanCommandPolicy.SetExact(
                allowedValues = setOf(
                    MbCanKnownVehiclePropertyId.HVAC_CUSTOM_ECO,
                    MbCanKnownVehiclePropertyId.HVAC_CUSTOM_COMFORT,
                    MbCanKnownVehiclePropertyId.HVAC_CUSTOM_STRONG,
                )
            ),
            refreshSignal = MbCanSignal.HvacCustomMode
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.HVAC_AC_MAX,
            policy = MbCanCommandPolicy.ToggleBinary(
                offValue = 1,
                onValue = 2,
                unknownFallbackValue = 2
            ),
            refreshSignal = MbCanSignal.HvacAcMax
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.FRONT_WINDSCREEN_HEAT_SWITCH,
            policy = MbCanCommandPolicy.ToggleBinary(
                offValue = 1,
                onValue = 2,
                unknownFallbackValue = 2
            ),
            refreshSignal = MbCanSignal.FrontWindscreenHeat
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.HVAC_DEFROSTER_SWITCH,
            policy = MbCanCommandPolicy.ToggleBinary(
                offValue = 1,
                onValue = 2,
                unknownFallbackValue = 2
            ),
            refreshSignal = MbCanSignal.HvacDefroster
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION,
            policy = MbCanCommandPolicy.ToggleBinary(
                offValue = MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION_VALUE_OFF,
                onValue = MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION_VALUE_ON,
                unknownFallbackValue = MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION_VALUE_OFF,
            ),
            refreshSignal = MbCanSignal.HvacAirRecirculation
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.HVAC_POWER,
            policy = MbCanCommandPolicy.ToggleBinary(
                offValue = 1,
                onValue = 2,
                unknownFallbackValue = 2
            ),
            refreshSignal = MbCanSignal.HvacAcPower
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.HVAC_BLOWER_DELAY,
            policy = MbCanCommandPolicy.ToggleBinary(
                offValue = MbCanKnownVehiclePropertyId.HVAC_BLOWER_DELAY_VALUE_OFF,
                onValue = MbCanKnownVehiclePropertyId.HVAC_BLOWER_DELAY_VALUE_ON,
                unknownFallbackValue = MbCanKnownVehiclePropertyId.HVAC_BLOWER_DELAY_VALUE_ON,
            ),
            refreshSignal = MbCanSignal.HvacAcCleanWhenLocked
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.HVAC_AUTO_STATE,
            policy = MbCanCommandPolicy.ToggleBinary(
                offValue = 1,
                onValue = 2,
                unknownFallbackValue = 2
            ),
            refreshSignal = MbCanSignal.HvacAutoState
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.HVAC_AQS,
            policy = MbCanCommandPolicy.ToggleBinary(offValue = 1, onValue = 2, unknownFallbackValue = 2),
            refreshSignal = MbCanSignal.HvacAnionPurify,
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.FRAGRANCE_SWITCH,
            policy = MbCanCommandPolicy.ToggleBinary(offValue = 1, onValue = 2, unknownFallbackValue = 2),
            refreshSignal = MbCanSignal.FragranceSwitch,
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.FRAGRANCE_SMELL,
            policy = MbCanCommandPolicy.SetExact(allowedValues = setOf(1, 2, 3)),
            refreshSignal = MbCanSignal.FragranceSmell,
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.FRAGRANCE_CONCENTRATION,
            policy = MbCanCommandPolicy.SetExact(allowedValues = setOf(1, 2, 3)),
            refreshSignal = MbCanSignal.FragranceConcentration,
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.POWER_FIRST_BREATH,
            policy = MbCanCommandPolicy.ToggleBinary(offValue = 1, onValue = 2, unknownFallbackValue = 2),
            refreshSignal = MbCanSignal.FirstBlowing,
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.BT_REDUCED_WIND_SPEED,
            policy = MbCanCommandPolicy.ToggleBinary(offValue = 1, onValue = 2, unknownFallbackValue = 2),
            refreshSignal = MbCanSignal.BtReduceFan,
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.HVAC_VENTILATION_AUTO_SWITCH,
            policy = MbCanCommandPolicy.ToggleBinary(offValue = 1, onValue = 2, unknownFallbackValue = 2),
            refreshSignal = MbCanSignal.AutoVentilation,
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.HUD_SWITCH,
            policy = MbCanCommandPolicy.ToggleBinary(offValue = 1, onValue = 2, unknownFallbackValue = 2),
            refreshSignal = MbCanSignal.HudSwitch,
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.HUD_HEIGHT,
            policy = MbCanCommandPolicy.SetExact(allowedValues = (1..10).toSet()),
            refreshSignal = MbCanSignal.HudHeight,
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.HUD_BRIGHTNESS,
            policy = MbCanCommandPolicy.SetExact(allowedValues = (1..10).toSet()),
            refreshSignal = MbCanSignal.HudBrightness,
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.HUD_DISPLAY_MODE,
            policy = MbCanCommandPolicy.SetExact(allowedValues = setOf(1, 2)),
            refreshSignal = MbCanSignal.HudDisplayMode,
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.HUD_AUTO_BRIGHTNESS,
            policy = MbCanCommandPolicy.ToggleBinary(offValue = 1, onValue = 2, unknownFallbackValue = 2),
            refreshSignal = MbCanSignal.HudAutoBrightness,
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.ICM_BRIGHTNESS_MODE,
            policy = MbCanCommandPolicy.SetExact(allowedValues = setOf(0, 1)),
            refreshSignal = MbCanSignal.IcmBrightnessMode,
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.ICM_BRIGHTNESS_MANUAL,
            policy = MbCanCommandPolicy.SetExact(allowedValues = (1..10).toSet()),
            refreshSignal = MbCanSignal.IcmManualBrightness,
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.OVERSPEED_ALARM_SET,
            policy = MbCanCommandPolicy.SetExact(
                allowedValues = CarSettingsHudDomain.OVERSPEED_RAW_RANGE.toSet(),
            ),
            refreshSignal = MbCanSignal.OverspeedAlarm,
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION,
            policy = MbCanCommandPolicy.ToggleHvacFrontDefrost,
            refreshSignal = MbCanSignal.HvacDefrosterFront
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.CHG_WIRELESS_SWITCH,
            policy = MbCanCommandPolicy.ToggleBinary(
                offValue = 1,
                onValue = 2,
                unknownFallbackValue = 2
            ),
            refreshSignal = MbCanSignal.WirelessChargingSwitch
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.VEHICLE_PROPERTY_STEERING_MODE,
            policy = MbCanCommandPolicy.SetExact(allowedValues = (0..6).toSet()),
            refreshSignal = MbCanSignal.CarSettingsVehicleParams
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.VEHICLE_PROPERTY_EPS_MODE,
            policy = MbCanCommandPolicy.SetExact(allowedValues = (0..6).toSet()),
            refreshSignal = MbCanSignal.CarSettingsVehicleParams
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.SYSTEM_MODE,
            policy = MbCanCommandPolicy.SetExact(allowedValues = (0..6).toSet()),
            refreshSignal = MbCanSignal.CarSettingsVehicleParams
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.SYSTEM_REBOOT,
            policy = MbCanCommandPolicy.SetExact(
                allowedValues = setOf(MbCanKnownVehiclePropertyId.SYSTEM_REBOOT_VALUE),
            ),
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE,
            policy = MbCanCommandPolicy.SetExact(allowedValues = (0..6).toSet()),
            refreshSignal = MbCanSignal.CarSettingsVehicleParams
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.VEHICLE_POWERMODE,
            policy = MbCanCommandPolicy.SetExact(allowedValues = (0..6).toSet()),
            refreshSignal = MbCanSignal.CarSettingsVehicleParams
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE_6DCT_WET,
            policy = MbCanCommandPolicy.SetExact(allowedValues = (0..6).toSet()),
            refreshSignal = MbCanSignal.CarSettingsVehicleParams
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.VEHICLE_TSR_SWITCH,
            policy = MbCanCommandPolicy.SetExact(
                allowedValues = setOf(
                    SlaSpeedLimitDomain.SLA_SWITCH_OFF,
                    SlaSpeedLimitDomain.SLA_SWITCH_ON,
                )
            ),
            refreshSignal = MbCanSignal.SlaSpeedLimit
        ),
        // Speed limiter — raw SetAnyInt for car-settings probing; widget ± still clamps in domain.
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.VEHICLE_SPEEDLIMIT_SWITCH,
            policy = MbCanCommandPolicy.SetAnyInt,
            refreshSignal = MbCanSignal.SpeedLimiter
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.VEHICLE_SPEEDLIMIT_VALUESET,
            policy = MbCanCommandPolicy.SetAnyInt,
            refreshSignal = MbCanSignal.SpeedLimiter
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.VEHICLE_PM25_DISPLAY_TOGGLE,
            policy = MbCanCommandPolicy.SetExact(allowedValues = setOf(1, 2)),
            refreshSignal = MbCanSignal.CarSettingsVehicleParams
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.VEHICLE_UV_LAMP_REQ,
            policy = MbCanCommandPolicy.SetExact(allowedValues = setOf(1, 2, 3)),
            refreshSignal = MbCanSignal.CarSettingsVehicleParams
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.VEHICLE_STERILIZE_STRENGTH_REQ,
            policy = MbCanCommandPolicy.SetExact(allowedValues = setOf(1, 2, 3)),
            refreshSignal = MbCanSignal.CarSettingsVehicleParams
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.VEHICEL_BRAKE_PEDA_FEEL_MODE,
            policy = MbCanCommandPolicy.SetExact(allowedValues = (0..6).toSet()),
            refreshSignal = MbCanSignal.CarSettingsVehicleParams
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.SOURCE_STATION_MODE,
            policy = MbCanCommandPolicy.ToggleBinary(
                offValue = 1,
                onValue = 2,
                unknownFallbackValue = 2
            ),
            refreshSignal = MbCanSignal.CarSettingsVehicleParams
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.VEHICLE_VEHWASH_MODESET,
            policy = MbCanCommandPolicy.ToggleBinary(
                offValue = 1,
                onValue = 2,
                unknownFallbackValue = 2
            ),
            refreshSignal = MbCanSignal.CarSettingsVehicleParams
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH,
            policy = MbCanCommandPolicy.SetExact(allowedValues = (1..7).toSet()),
            refreshSignal = MbCanSignal.FrontLeftSeatMode
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH,
            policy = MbCanCommandPolicy.SetExact(allowedValues = (1..7).toSet()),
            refreshSignal = MbCanSignal.FrontRightSeatMode
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.REAR_LEFT_SEAT_HEAT_SWITCH,
            policy = MbCanCommandPolicy.SetExact(allowedValues = (1..4).toSet()),
            refreshSignal = MbCanSignal.RearLeftSeatMode
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.REAR_RIGHT_SEAT_HEAT_SWITCH,
            policy = MbCanCommandPolicy.SetExact(allowedValues = (1..4).toSet()),
            refreshSignal = MbCanSignal.RearRightSeatMode
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_LEFT,
            policy = MbCanCommandPolicy.SetExact(
                allowedValues = HvacClimateDomain.HVAC_TEMP_MB_CAN_ALLOWED
            ),
            refreshSignal = MbCanSignal.HvacTempLeft
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_RIGHT,
            policy = MbCanCommandPolicy.SetExact(
                allowedValues = HvacClimateDomain.HVAC_TEMP_MB_CAN_ALLOWED
            ),
            refreshSignal = MbCanSignal.HvacTempRight
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.HVAC_FAN_SPEED,
            policy = MbCanCommandPolicy.SetExact(
                allowedValues = (HvacClimateDomain.FAN_SPEED_MIN..HvacClimateDomain.FAN_SPEED_MAX).toSet()
            ),
            refreshSignal = MbCanSignal.HvacFanSpeed
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.HVAC_FRONT_OFF,
            policy = MbCanCommandPolicy.ToggleBinary(
                offValue = 1,
                onValue = 2,
                unknownFallbackValue = 1
            ),
            refreshSignal = MbCanSignal.HvacFrontOff
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.HVAC_SYNC_SWITCH,
            policy = MbCanCommandPolicy.ToggleBinary(
                offValue = 1,
                onValue = 2,
                unknownFallbackValue = 1
            ),
            refreshSignal = MbCanSignal.HvacSync
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.TRUNK_PLG_CONTROL,
            policy = MbCanCommandPolicy.SetExact(allowedValues = setOf(0, 1, 2)),
            refreshSignal = MbCanSignal.TrunkDoor
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.MIRROR_FOLD_SWITCH,
            policy = MbCanCommandPolicy.SetExact(allowedValues = setOf(1, 2)),
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.MFS_CRUISE_CONTROL,
            policy = MbCanCommandPolicy.SetExact(allowedValues = setOf(AccCruiseDomain.MFS_PULSE_VALUE)),
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.MFS_CANCEL,
            policy = MbCanCommandPolicy.SetExact(allowedValues = setOf(AccCruiseDomain.MFS_PULSE_VALUE)),
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.MFS_RES_PLUS,
            policy = MbCanCommandPolicy.SetExact(allowedValues = setOf(AccCruiseDomain.MFS_PULSE_VALUE)),
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.MFS_SET_MINUS,
            policy = MbCanCommandPolicy.SetExact(allowedValues = setOf(AccCruiseDomain.MFS_PULSE_VALUE)),
        ),
    ).associateBy { it.propertyId }

    fun get(propertyId: Int): MbCanCommandSpec? = specsByPropertyId[propertyId]

    fun all(): List<MbCanCommandSpec> = specsByPropertyId.values.sortedBy { it.propertyId }
}

