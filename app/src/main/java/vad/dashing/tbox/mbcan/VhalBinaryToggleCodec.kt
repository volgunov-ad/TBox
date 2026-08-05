package vad.dashing.tbox.mbcan

/**
 * Stock VHAL binary toggle write encodings (CarSettings / AirConditioning).
 * Kept free of Android framework types so unit tests can cover the table without
 * initializing [Android10VhalRepository].
 */
object VhalBinaryToggleCodec {
    fun isVhalBinaryToggleProperty(propertyId: Int): Boolean = when (propertyId) {
        MbCanKnownVehiclePropertyId.STEERING_WHEEL_HEAT_SWITCH,
        MbCanKnownVehiclePropertyId.WIPER_MAINTENANCE_SWITCH,
        MbCanKnownVehiclePropertyId.PARKING_RADAR_SWITCH,
        MbCanKnownVehiclePropertyId.AVH_SWITCH,
        MbCanKnownVehiclePropertyId.HDC_SWITCH,
        MbCanKnownVehiclePropertyId.ESP_OFF_SWITCH,
        MbCanKnownVehiclePropertyId.REAR_FOG_LIGHT,
        MbCanKnownVehiclePropertyId.DOOR_AUTO_LOCK,
        MbCanKnownVehiclePropertyId.DOOR_IGNOFF_UNLOCK,
        MbCanKnownVehiclePropertyId.REAR_WIPER,
        MbCanKnownVehiclePropertyId.TJA_ICA_SWITCH,
        MbCanKnownVehiclePropertyId.BLIND_AREA_DETECTION,
        MbCanKnownVehiclePropertyId.DOOR_OPEN_WARNING,
        MbCanKnownVehiclePropertyId.FCW_SWITCH,
        MbCanKnownVehiclePropertyId.ACC_AUTOBRAKE_SWITCH,
        MbCanKnownVehiclePropertyId.SAFE_DISTANCE_WARNING,
        MbCanKnownVehiclePropertyId.HMA_SWITCH,
        MbCanKnownVehiclePropertyId.HVAC_AC_MAX,
        MbCanKnownVehiclePropertyId.FRONT_WINDSCREEN_HEAT_SWITCH,
        MbCanKnownVehiclePropertyId.HVAC_DEFROSTER_SWITCH,
        MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION,
        MbCanKnownVehiclePropertyId.HVAC_POWER,
        MbCanKnownVehiclePropertyId.HVAC_BLOWER_DELAY,
        MbCanKnownVehiclePropertyId.HVAC_AUTO_STATE,
        MbCanKnownVehiclePropertyId.HVAC_SYNC_SWITCH,
        MbCanKnownVehiclePropertyId.HVAC_FRONT_OFF,
        MbCanKnownVehiclePropertyId.POWER_FIRST_BREATH,
        MbCanKnownVehiclePropertyId.BT_REDUCED_WIND_SPEED,
        MbCanKnownVehiclePropertyId.HVAC_VENTILATION_AUTO_SWITCH,
        MbCanKnownVehiclePropertyId.HUD_SWITCH,
        MbCanKnownVehiclePropertyId.HUD_AUTO_BRIGHTNESS -> true
        else -> false
    }

    /**
     * @param targetOn UI/feature "on" (for Front OFF: climate section off / widget selected).
     * @return raw write value, or null if [propertyId] has no VHAL-specific encoding.
     */
    fun encodeWriteValue(propertyId: Int, targetOn: Boolean): Int? = when (propertyId) {
        // Stock CarSettings/HVAC: T_0401_SET_MFS_Heat and T_0401_SET_Wiper_Maintenance use 1=on, 2=off.
        // Chassis: T_0B01_AVH/HDC/ESCOFF_ON_OFF also use 1=on, 2=off (CarCommon1).
        MbCanKnownVehiclePropertyId.STEERING_WHEEL_HEAT_SWITCH,
        MbCanKnownVehiclePropertyId.WIPER_MAINTENANCE_SWITCH,
        MbCanKnownVehiclePropertyId.AVH_SWITCH,
        MbCanKnownVehiclePropertyId.HDC_SWITCH,
        MbCanKnownVehiclePropertyId.ESP_OFF_SWITCH,
        // Stock CarOutLightFragment rear fog: T_0405_SET_Rearfoglight — 1=on, 2=off.
        MbCanKnownVehiclePropertyId.REAR_FOG_LIGHT,
        MbCanKnownVehiclePropertyId.DOOR_AUTO_LOCK,
        MbCanKnownVehiclePropertyId.DOOR_IGNOFF_UNLOCK,
        MbCanKnownVehiclePropertyId.REAR_WIPER,
        MbCanKnownVehiclePropertyId.POWER_FIRST_BREATH,
        MbCanKnownVehiclePropertyId.BT_REDUCED_WIND_SPEED,
        MbCanKnownVehiclePropertyId.HVAC_VENTILATION_AUTO_SWITCH,
        MbCanKnownVehiclePropertyId.HUD_SWITCH,
        MbCanKnownVehiclePropertyId.HUD_AUTO_BRIGHTNESS,
        MbCanKnownVehiclePropertyId.BLIND_AREA_DETECTION,
        MbCanKnownVehiclePropertyId.DOOR_OPEN_WARNING,
        // Stock HVAC: T_0201_IHU_5_FrontOFF_Req — selected (climate off) writes 1, else 2.
        MbCanKnownVehiclePropertyId.HVAC_FRONT_OFF ->
            if (targetOn) 1 else 2
        // Stock: these writes use 2=on, 1=off.
        MbCanKnownVehiclePropertyId.PARKING_RADAR_SWITCH,
        MbCanKnownVehiclePropertyId.TJA_ICA_SWITCH,
        MbCanKnownVehiclePropertyId.FCW_SWITCH,
        MbCanKnownVehiclePropertyId.ACC_AUTOBRAKE_SWITCH,
        MbCanKnownVehiclePropertyId.SAFE_DISTANCE_WARNING,
        MbCanKnownVehiclePropertyId.HVAC_AC_MAX,
        MbCanKnownVehiclePropertyId.FRONT_WINDSCREEN_HEAT_SWITCH,
        MbCanKnownVehiclePropertyId.HVAC_DEFROSTER_SWITCH,
        MbCanKnownVehiclePropertyId.HVAC_POWER,
        MbCanKnownVehiclePropertyId.HVAC_AUTO_STATE ->
            if (targetOn) 2 else 1
        // Stock CarOutLightFragment HMA: T_0B01_IHU_8_HMAOnOffReq — 1=on, 0=off.
        MbCanKnownVehiclePropertyId.HMA_SWITCH ->
            if (targetOn) 1 else 0
        // Recirculation: 1=inside(recirc on), 2=outside(recirc off).
        MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION ->
            if (targetOn) MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION_VALUE_ON
            else MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION_VALUE_OFF
        // Stock AirConditioning AcFragment: T_0401_…_Blower_Delay — 1=on, 2=off (≠ mbCAN 2/1).
        MbCanKnownVehiclePropertyId.HVAC_BLOWER_DELAY ->
            if (targetOn) 1 else 2
        MbCanKnownVehiclePropertyId.HVAC_SYNC_SWITCH ->
            HvacClimateDomain.encodeHvacSyncVhalWrite(targetOn)
        else -> null
    }
}
