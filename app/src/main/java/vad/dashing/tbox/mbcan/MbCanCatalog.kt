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

    data class SetExact(
        val allowedValues: Set<Int>
    ) : MbCanCommandPolicy()
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
        MbCanControlParam("Climate", "PM25 display source", "eVEHICLE_PM25_DISPLAY_TOGGLE", MbCanConfidence.DECLARED_IN_API),
        MbCanControlParam("Climate", "UV lamp request", "eVEHICLE_UV_LAMP_REQ", MbCanConfidence.DECLARED_IN_API),
        MbCanControlParam("Climate", "Sterilize strength request", "eVEHICLE_STERILIZE_STRENGTH_REQ", MbCanConfidence.DECLARED_IN_API),
        MbCanControlParam("Climate", "HVAC front defroster", "eHVAC_DEFROSTER_FRONT", MbCanConfidence.DECLARED_IN_API),
        MbCanControlParam("Climate", "HVAC temperature", "eVEHICLE_PROPERTY_HVAC_TEMPERATURE", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Climate", "Fragrance switch", "eVEHICLE_PROPERTY_FRAGRANCE_SWITCH", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("ADAS", "FCW switch", "eFCW_SWTICH", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("ADAS", "Auto brake switch", "eVEHICLE_PROPERTY_ACC_AUTOBRAKE_SW", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("ADAS", "LKA sensitivity", "eVEHICLE_PROPERTY_LAS_SENSITIVITY_LEVEL", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Multimedia", "EQ mode", "eAUDIO_PROPERTY_EQMODE", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Multimedia", "Media volume key mode", "eAUDIO_PROPERTY_VOLUME_KEY", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Multimedia", "Volume vs speed", "eAUDIO_PROPERTY_VOLUME_SPEED", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Multimedia", "AVM language", "eAVM_SET_LANG", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("System", "System reboot", "eSYSTEM_REBOOT", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("System", "ICM brightness mode", "eVEHICLE_SET_ICM_BRIGHTNESS_MODE", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("System", "Steering wheel heating switch", "eVEHICLE_SET_MFS_HEAT_SWITCH", MbCanConfidence.CONFIRMED_IN_APP_CALLS)
    )
}

object MbCanKnownVehiclePropertyId {
    // MBVehicleProperty.eVEHICLE_SET_MFS_HEAT_SWITCH.
    const val STEERING_WHEEL_HEAT_SWITCH = 188
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
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eHVAC_DEFROSTER_FRONT] — 1 off, 2 on. */
    const val HVAC_DEFROSTER_FRONT = 122
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
}

/** [com.mengbo.mbCan.defines.MBAudioProperty] integer ids for [com.mengbo.mbCan.MBCanEngine.canGetAudioParam]. */
object MbCanKnownAudioPropertyId {
    /** [com.mengbo.mbCan.defines.MBAudioProperty.eAUDIO_PROPERTY_VOLUME] */
    const val VOLUME = 2
    /** [com.mengbo.mbCan.defines.MBAudioProperty.eAUDIO_PROPERTY_VOLUME_SPEED] */
    const val VOLUME_SPEED = 13
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
            policy = MbCanCommandPolicy.ToggleBinary(
                offValue = 1,
                onValue = 2,
                unknownFallbackValue = 2,
            ),
            refreshSignal = MbCanSignal.AudioVolumeSpeed,
        ),
    ).associateBy { it.propertyId }

    fun get(propertyId: Int): MbCanAudioCommandSpec? = specsByPropertyId[propertyId]
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
            propertyId = MbCanKnownVehiclePropertyId.HVAC_DEFROSTER_FRONT,
            policy = MbCanCommandPolicy.ToggleBinary(
                offValue = 1,
                onValue = 2,
                unknownFallbackValue = 2
            ),
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
        )
    ).associateBy { it.propertyId }

    fun get(propertyId: Int): MbCanCommandSpec? = specsByPropertyId[propertyId]
}

