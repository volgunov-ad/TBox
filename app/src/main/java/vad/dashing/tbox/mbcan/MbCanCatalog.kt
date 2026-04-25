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
        MbCanControlParam("Body/BCM", "Door auto lock", "eVEHICLE_PROPERTY_DOOR_AUTO_LOCK", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Body/BCM", "Ignition-off unlock", "eVEHICLE_PROPERTY_DOOR_IGNOFF_UNLOCK", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Body/BCM", "Mirror reverse turn location", "eVEHICLE_SET_MIRROR_REVERSE_TURN_LOC", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Climate", "HVAC power", "eVEHICLE_PROPERTY_HVAC_POWER", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Climate", "HVAC auto", "eHVAC_AUTO_STATE", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Climate", "HVAC fan speed", "eVEHICLE_PROPERTY_HVAC_FAN_SPEED", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Climate", "HVAC temperature", "eVEHICLE_PROPERTY_HVAC_TEMPERATURE", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Climate", "Fragrance switch", "eVEHICLE_PROPERTY_FRAGRANCE_SWITCH", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("ADAS", "FCW switch", "eFCW_SWTICH", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("ADAS", "Auto brake switch", "eVEHICLE_PROPERTY_ACC_AUTOBRAKE_SW", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("ADAS", "LKA sensitivity", "eVEHICLE_PROPERTY_LAS_SENSITIVITY_LEVEL", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Multimedia", "EQ mode", "eAUDIO_PROPERTY_EQMODE", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Multimedia", "Media volume key mode", "eAUDIO_PROPERTY_VOLUME_KEY", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Multimedia", "AVM language", "eAVM_SET_LANG", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("System", "System reboot", "eSYSTEM_REBOOT", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("System", "ICM brightness mode", "eVEHICLE_SET_ICM_BRIGHTNESS_MODE", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("System", "Steering wheel heating switch", "eVEHICLE_SET_MFS_HEAT_SWITCH", MbCanConfidence.CONFIRMED_IN_APP_CALLS)
    )
}

object MbCanKnownVehiclePropertyId {
    // MBVehicleProperty.eVEHICLE_SET_MFS_HEAT_SWITCH
    const val STEERING_WHEEL_HEAT_SWITCH = 188
}

