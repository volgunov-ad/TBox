package vad.dashing.tbox.mbcan

import android.util.Log
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Runtime loader for Android 10 vehicle JSON tables from firmware.
 *
 * Important: `send.json` / `receive.json` contain numeric IDs only.
 * We deliberately avoid heuristic semantic inference (no guessing).
 * Mapping uses:
 * 1) explicit overrides (empty by default),
 * 2) direct identity when requested id exists in firmware tables.
 */
object FirmwareVehicleJsonMapper {
    private const val TAG = "FirmwareVehicleMapper"
    private const val SEND_JSON_PATH = "/system/etc/adayo/vehicle/send.json"
    private const val RECEIVE_JSON_PATH = "/system/etc/adayo/vehicle/receive.json"

    // Direct VHAL telemetry property ids used by Android10VhalRepository.
    const val VHAL_ENGINE_RPM_PROPERTY_ID = 289_414_951 // R_0900_EMS_1_EngineSpd
    const val VHAL_ENGINE_TEMPERATURE_PROPERTY_ID = 289_414_949 // R_0900_EMS1G_EngineCoolantTemperture
    /**
     * Dual-source vehicle speed (both decode `raw/16`):
     * - [VHAL_CAR_SPEED_VSO_SIG_PROPERTY_ID] preferred when raw > 0
     * - [VHAL_CAR_SPEED_DISPLAY_PROPERTY_ID] fallback (ICM; may truncate at buffer edge)
     */
    const val VHAL_CAR_SPEED_VSO_SIG_PROPERTY_ID = 289_412_119 // R_0400_ESP_1_VehicleSpeedVSOSig
    const val VHAL_CAR_SPEED_DISPLAY_PROPERTY_ID = 289_414_964 // R_0900_ICM_1_DisplayVehicleSpeed
    /** Alias of [VHAL_CAR_SPEED_VSO_SIG_PROPERTY_ID] for older call sites. */
    const val VHAL_CAR_SPEED_PROPERTY_ID = VHAL_CAR_SPEED_VSO_SIG_PROPERTY_ID
    /** AAOS gear selection (stock `CarSensorManager.SENSOR_TYPE_GEAR`). */
    const val VHAL_GEAR_SELECTION_PROPERTY_ID = 289_408_000 // GEAR_SELECTION
    /** AAOS current gear (alternate; same PRND bitmask). */
    const val VHAL_CURRENT_GEAR_PROPERTY_ID = 289_408_001 // CURRENT_GEAR
    /** CEM reverse gear switch. */
    const val VHAL_REVERSE_GEAR_SWITCH_PROPERTY_ID = 289_412_135 // R_0400_CEM_2_ReverseGearSwitch
    const val VHAL_FUEL_LEVEL_PROPERTY_ID = 289_414_929 // R_0900_ICM_1_FuelLevel
    const val VHAL_TOTAL_ODOMETER_KM_PROPERTY_ID = 289_414_930 // R_0900_ICM_1_TotalOdometer_Km
    /** Instant fuel L/100km counter; UI = raw / 10. */
    const val VHAL_FUEL_ROLLING_COUNTER_PROPERTY_ID = 289_414_918 // R_0900_ICM_6_FuelRollingCounter
    /** Distance to next maintenance, km as-is. */
    const val VHAL_MAINTENANCE_TIPS_PROPERTY_ID = 289_414_920 // R_0900_ICM_6_Maintenance_tips
    /** Distance to empty, km as-is. */
    const val VHAL_DISTANCE_TO_EMPTY_KM_PROPERTY_ID = 289_414_938 // R_0900_ICM_4_DistenceToEmpty_Km
    /** Cabin PM2.5 density (inside). */
    const val VHAL_PM25_INDENSITY_PROPERTY_ID = 289_412_224 // R_0400_PM2_5_Indensity
    /** Outside PM2.5 density. */
    const val VHAL_PM25_OUTDENSITY_PROPERTY_ID = 289_412_226 // R_0400_PM2_5_Outdensity
    const val VHAL_EXTERNAL_TEMPERATURE_RAW_PROPERTY_ID = 289_412_223 // R_0400_CEM_IPM_3_ExternalTemperatureRaw
    const val VHAL_LF_TYRE_PRESSURE = 289_411_849 // R_0300_CEM_5_LFTyrePressure
    const val VHAL_RF_TYRE_PRESSURE = 289_411_850 // R_0300_CEM_5_RFTyrePressure
    const val VHAL_LR_TYRE_PRESSURE = 289_411_851 // R_0300_CEM_5_LRTyrePressure
    const val VHAL_RR_TYRE_PRESSURE = 289_411_852 // R_0300_CEM_5_RRTyrePressure
    const val VHAL_LF_TYRE_TEMPERATURE = 289_411_853 // R_0300_CEM_5_LFTyreTemperature
    const val VHAL_RF_TYRE_TEMPERATURE = 289_411_854 // R_0300_CEM_5_RFTyreTemperature
    const val VHAL_LR_TYRE_TEMPERATURE = 289_411_855 // R_0300_CEM_5_LRTyreTemperature
    const val VHAL_RR_TYRE_TEMPERATURE = 289_411_856 // R_0300_CEM_5_RRTyreTemperature
    const val VHAL_SLA_SPEED_LIMIT_RAW = 289_415_711 // R_0B00_FCM_2_SLASpdlimit
    const val VHAL_SLA_ON_OFF_STATUS = 289_415_709 // R_0B00_FCM_2_SLAOnOffsts
    const val VHAL_SLA_STATE = 289_415_708 // R_0B00_FCM_2_SLAState
    const val VHAL_SLA_ON_OFF_REQ = 289_415_947 // T_0B01_IHU_8_SLAOnOffReq
    const val VHAL_FRM_ACC_MODE = 289_415_689 // R_0B00_FRM_3_ACCMode
    const val VHAL_FRM_V_SET_DIS = 289_415_680 // R_0B00_FRM_3_VSetDis
    /** Conventional CCS status (2-bit); A9 Gasped [nCruiseControlStatus] analog. */
    const val VHAL_EMS_CRUISE_CONTROL_STATUS = 289_414_945 // R_0900_EMS_1_CruiseControlStatus
    const val VHAL_MFS_CRUISE_CONTROL = 289_415_956 // T_0B01_MFS_Cruise_Control
    const val VHAL_MFS_CANCEL = 289_415_954 // T_0B01_MFS_Cancel
    const val VHAL_MFS_RES_PLUS = 289_415_953 // T_0B01_MFS_RESPlus
    const val VHAL_MFS_SET_MINUS = 289_415_960 // T_0B01_MFS_SETMinus

    private data class Tables(
        val sendIds: Set<Int>,
        val receiveIds: Set<Int>,
    )

    @Volatile
    private var cached: Tables? = null

    /**
     * Explicit verified translations between legacy mbCAN ids and firmware ids.
     *
     * Evidence source:
     * - D:\Dashing\AirConditioning\sources\android\car\VehiclePropertyIds.java
     * - D:\Dashing\CarSettings\sources\android\car\VehiclePropertyIds.java
     * - usage in AirConditioning / CarSettings app sources (setIntProperty/getIntProperty).
     */
    private val explicitWriteIdMap: Map<Int, Int> = mapOf(
        // MBVehicleProperty.eVEHICLE_SET_MFS_HEAT_SWITCH
        MbCanKnownVehiclePropertyId.STEERING_WHEEL_HEAT_SWITCH to 289412679, // T_0401_SET_MFS_Heat
        // MBVehicleProperty.eVEHICLE_SET_WIPER_MAINTENANCE_SWITCH
        MbCanKnownVehiclePropertyId.WIPER_MAINTENANCE_SWITCH to 289412682, // T_0401_SET_Wiper_Maintenance
        // MBVehicleProperty.eVEHICLE_SET_PAS_SWITCH
        MbCanKnownVehiclePropertyId.PARKING_RADAR_SWITCH to 289415942, // T_0B01_SET_PAS_Switch
        // MBVehicleProperty.eVEHICLE_AVH_SWITCH
        MbCanKnownVehiclePropertyId.AVH_SWITCH to 289415945, // T_0B01_AVH_ON_OFF
        // MBVehicleProperty.eVEHICLE_HDC_SWITCH
        MbCanKnownVehiclePropertyId.HDC_SWITCH to 289415944, // T_0B01_HDC_ON_OFF
        // MBVehicleProperty.eVEHICLE_ESCOFF_SWITCH
        MbCanKnownVehiclePropertyId.ESP_OFF_SWITCH to 289415943, // T_0B01_ESCOFF_ON_OFF
        // MBVehicleProperty.eVEHICLE_LIGHTCONTROL — stock mode StateFlow uses write-echo lightSts
        MbCanKnownVehiclePropertyId.LIGHTCONTROL to 289412613, // T_0405_SET_Lightcontrol
        // MBVehicleProperty.eVEHICLE_REARFOGLIGHT
        MbCanKnownVehiclePropertyId.REAR_FOG_LIGHT to 289412612, // T_0405_SET_Rearfoglight
        MbCanKnownVehiclePropertyId.DOOR_AUTO_LOCK to 289412661,
        MbCanKnownVehiclePropertyId.DOOR_IGNOFF_UNLOCK to 289412660,
        MbCanKnownVehiclePropertyId.HEADLIGHTS_HOMELIGHT_DELAY to 289412656,
        MbCanKnownVehiclePropertyId.DRIVER_UNLOCK_MODE to 289412608,
        MbCanKnownVehiclePropertyId.DEFENCES_PROMPT to 289412668,
        MbCanKnownVehiclePropertyId.WIPER_SENSITIVITY to 289412688,
        MbCanKnownVehiclePropertyId.REAR_WIPER to 289412681,
        MbCanKnownVehiclePropertyId.HIGHBEAM_ADJUST to 289412610,
        MbCanKnownVehiclePropertyId.TURN_FLASH_COUNT to 289412665,
        // MBVehicleProperty.eVEHICLE_PROPERTY_LAS_MODE_SELECTION
        MbCanKnownVehiclePropertyId.LAS_MODE_SELECTION to 289415946, // T_0B01_IHU_8_LDWLKA_LaneAssit_TypeReq
        // MBVehicleProperty.eVEHICLE_PROPERTY_TJA_ICA
        MbCanKnownVehiclePropertyId.TJA_ICA_SWITCH to 289415939, // T_0B01_IHU_8_TJA_ICA_ON_OFF
        MbCanKnownVehiclePropertyId.BLIND_AREA_DETECTION to 289415055, // T_0901_IHU_3_BSDSwitch
        MbCanKnownVehiclePropertyId.DOOR_OPEN_WARNING to 289415065,
        MbCanKnownVehiclePropertyId.FCW_SWITCH to 289415937,
        MbCanKnownVehiclePropertyId.ACC_AUTOBRAKE_SWITCH to 289415941,
        MbCanKnownVehiclePropertyId.SAFE_DISTANCE_WARNING to 289415942,
        MbCanKnownVehiclePropertyId.FCW_SENSITIVITY to 289415936,
        MbCanKnownVehiclePropertyId.LAS_SENSITIVITY_LEVEL to 289415949,
        // MBVehicleProperty.eVEHICLE_SMART_HIGHBEAM_SWITCH (HMA)
        MbCanKnownVehiclePropertyId.HMA_SWITCH to 289415948, // T_0B01_IHU_8_HMAOnOffReq
        // MBVehicleProperty.eHVAC_CUSTOM
        MbCanKnownVehiclePropertyId.HVAC_CUSTOM to 289415317, // T_0201_SET_IPMCustom_Air_Conditioning
        // MBVehicleProperty.eVEHICLE_SET_RRM_ACMAX_REQ
        MbCanKnownVehiclePropertyId.HVAC_AC_MAX to 289412714, // T_0401_SET_IHU_ACMAXReq
        // MBVehicleProperty.eVHEICEL_FRONTWINDSCREEN_HEAT
        MbCanKnownVehiclePropertyId.FRONT_WINDSCREEN_HEAT_SWITCH to 289415309, // T_0201_SET_FrontWindscreenHeatiReq
        // MBVehicleProperty.eVEHICLE_PROPERTY_HVAC_FAN_DIRECTION
        MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION to 289415298, // T_0201_IHU_5_ModeAdjust_Req
        // MBVehicleProperty.eVEHICLE_PROPERTY_HVAC_DEFROSTER (rear window + mirrors)
        MbCanKnownVehiclePropertyId.HVAC_DEFROSTER_SWITCH to 289415299, // T_0201_IHU_5_RearDefrostSwitch_Req
        // MBVehicleProperty.eVEHICLE_PROPERTY_HVAC_AIR_RECIRCULATION
        MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION to 289415302, // T_0201_IHU_5_CirculationMode_Req
        // MBVehicleProperty.eVEHICLE_PROPERTY_HVAC_POWER
        MbCanKnownVehiclePropertyId.HVAC_POWER to 289415300, // T_0201_IHU_5_ACRequestCommand
        // MBVehicleProperty.eVEHICLE_PROPERTY_HVAC_BLOWER_DELAY — AC clean when locked
        MbCanKnownVehiclePropertyId.HVAC_BLOWER_DELAY to 289412666, // T_0401_IHU_1_DVD_SET_IPM_Blower_Delay
        // MBVehicleProperty.eHVAC_AUTO_STATE
        MbCanKnownVehiclePropertyId.HVAC_AUTO_STATE to 289415311, // T_0201_IHU_5_AutoState
        // MBVehicleProperty.eVEHICLE_PROPERTY_HVAC_AQS
        MbCanKnownVehiclePropertyId.HVAC_AQS to 289415310, // T_0201_IHU_5_AnionPurify_Req
        MbCanKnownVehiclePropertyId.POWER_FIRST_BREATH to 289412677,
        MbCanKnownVehiclePropertyId.BT_REDUCED_WIND_SPEED to 289412667,
        MbCanKnownVehiclePropertyId.HVAC_VENTILATION_AUTO_SWITCH to 289412704,
        MbCanKnownVehiclePropertyId.HUD_SWITCH to 289412716,
        MbCanKnownVehiclePropertyId.HUD_HEIGHT to 289412717,
        MbCanKnownVehiclePropertyId.HUD_BRIGHTNESS to 289412719,
        MbCanKnownVehiclePropertyId.HUD_DISPLAY_MODE to 289412718,
        MbCanKnownVehiclePropertyId.HUD_AUTO_BRIGHTNESS to 289412723,
        MbCanKnownVehiclePropertyId.ICM_BRIGHTNESS_MODE to 289415088, // T_0901_IHU_SET_ICMBrightnessMode
        MbCanKnownVehiclePropertyId.ICM_BRIGHTNESS_MANUAL to 289415087, // T_0901_IHU_ICMBrightnessManualAdj
        MbCanKnownVehiclePropertyId.OVERSPEED_ALARM_SET to 289415091, // T_0901_IHU_21_OverspeedAlarm_Set
        MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_LEFT to 289415313, // T_0201_IHU_5_L_Set_Temperature
        MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_RIGHT to 289415314, // T_0201_IHU_5_R_Set_Temperature
        MbCanKnownVehiclePropertyId.HVAC_FAN_SPEED to 289415296, // T_0201_IHU_5_BlowSpeedLevel_Req
        MbCanKnownVehiclePropertyId.HVAC_FRONT_OFF to 289415301, // T_0201_IHU_5_FrontOFF_Req
        MbCanKnownVehiclePropertyId.HVAC_SYNC_SWITCH to 289415308, // T_0201_IHU_5_SyncSwtich_Req
        MbCanKnownVehiclePropertyId.TRUNK_PLG_CONTROL to 289412638, // T_0403_SET_PLG_Control
        MbCanKnownVehiclePropertyId.MIRROR_FOLD_SWITCH to 289412705, // T_0401_SET_Mirror_Fold_Switch
        MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH to 289415316, // T_0201_SET_FLSeatHeatVentSwReq
        MbCanKnownVehiclePropertyId.FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH to 289415315, // T_0201_SET_FRSeatHeatVentSwReq
        MbCanKnownVehiclePropertyId.REAR_LEFT_SEAT_HEAT_SWITCH to 289415345, // T_0203_SET_LRSeatHeatVentSwReq
        MbCanKnownVehiclePropertyId.REAR_RIGHT_SEAT_HEAT_SWITCH to 289415344, // T_0203_SET_RRSeatHeatVentSwReq
        // Car settings (drive/eps)
        MbCanKnownVehiclePropertyId.VEHICLE_PROPERTY_EPS_MODE to 289412662, // T_0401_IHU_1_DVD_SET_EPSmode
        MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE to 289412695, // T_0401_IHU_9_DriveMode
        MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE_6DCT_WET to 289412692, // T_0401_IHU_9_DriveMode_6DCT_Wet
        MbCanKnownVehiclePropertyId.VEHICLE_TSR_SWITCH to VHAL_SLA_ON_OFF_REQ,
        MbCanKnownVehiclePropertyId.MFS_CRUISE_CONTROL to VHAL_MFS_CRUISE_CONTROL,
        MbCanKnownVehiclePropertyId.MFS_CANCEL to VHAL_MFS_CANCEL,
        MbCanKnownVehiclePropertyId.MFS_RES_PLUS to VHAL_MFS_RES_PLUS,
        MbCanKnownVehiclePropertyId.MFS_SET_MINUS to VHAL_MFS_SET_MINUS,
        // Audio
        MbCanKnownAudioPropertyId.VOLUME to 557849090, // AUDIO_CURRENT_MAIN_VOLUME
        MbCanKnownAudioPropertyId.VOLUME_SPEED to 557849227, // AUDIO_VOL_VSC_MOD_REQ
    )

    private val explicitReadIdMap: Map<Int, Int> = mapOf(
        // MBVehicleProperty.eVEHICLE_SET_MFS_HEAT_SWITCH
        MbCanKnownVehiclePropertyId.STEERING_WHEEL_HEAT_SWITCH to 289412111, // R_0400_RBCM_MFS_HeatSts
        // MBVehicleProperty.eVEHICLE_SET_WIPER_MAINTENANCE_SWITCH
        MbCanKnownVehiclePropertyId.WIPER_MAINTENANCE_SWITCH to 289412194, // R_0400_CEM_Wiper_MaintenanceSts
        // MBVehicleProperty.eVEHICLE_SET_PAS_SWITCH
        MbCanKnownVehiclePropertyId.PARKING_RADAR_SWITCH to 289412233, // R_0400_CEM_RAD1_Pas_Switch
        // MBVehicleProperty.eVEHICLE_AVH_SWITCH
        MbCanKnownVehiclePropertyId.AVH_SWITCH to 289412184, // R_0400_ESP_3_AVHSts
        // MBVehicleProperty.eVEHICLE_HDC_SWITCH
        MbCanKnownVehiclePropertyId.HDC_SWITCH to 289412117, // R_0400_ESP_1_HDCCtrlSts
        // MBVehicleProperty.eVEHICLE_ESCOFF_SWITCH
        MbCanKnownVehiclePropertyId.ESP_OFF_SWITCH to 289412118, // R_0400_ESP_1_VDCControlSts
        // MBVehicleProperty.eVEHICLE_LIGHTCONTROL — stock CarSettings lightSts listens to SET echo
        // (R_0404_CEM_2_LowBeamSts 289412250 is binary low-beam status, not the 1..4 mode).
        MbCanKnownVehiclePropertyId.LIGHTCONTROL to 289412613, // T_0405_SET_Lightcontrol
        // MBVehicleProperty.eVEHICLE_REARFOGLIGHT
        MbCanKnownVehiclePropertyId.REAR_FOG_LIGHT to 289412136, // R_0400_CEM_2_RearFogLightSts
        MbCanKnownVehiclePropertyId.DOOR_AUTO_LOCK to 289412149,
        MbCanKnownVehiclePropertyId.DOOR_IGNOFF_UNLOCK to 289412143,
        MbCanKnownVehiclePropertyId.HEADLIGHTS_HOMELIGHT_DELAY to 289412130,
        MbCanKnownVehiclePropertyId.DRIVER_UNLOCK_MODE to 289412214,
        MbCanKnownVehiclePropertyId.DEFENCES_PROMPT to 289412144,
        MbCanKnownVehiclePropertyId.WIPER_SENSITIVITY to 289412140,
        MbCanKnownVehiclePropertyId.REAR_WIPER to 289412193,
        MbCanKnownVehiclePropertyId.HIGHBEAM_ADJUST to 289412261,
        MbCanKnownVehiclePropertyId.TURN_FLASH_COUNT to 289412257,
        // MBVehicleProperty.eVEHICLE_PROPERTY_LAS_MODE_SELECTION
        MbCanKnownVehiclePropertyId.LAS_MODE_SELECTION to 289415706, // R_0B00_FCM_2_LDWLKA_LaneAssitfeedback
        // MBVehicleProperty.eVEHICLE_PROPERTY_TJA_ICA
        MbCanKnownVehiclePropertyId.TJA_ICA_SWITCH to 289415716, // R_0B00_FCM_2_TJA_ICA_ON_OFF_Sts
        MbCanKnownVehiclePropertyId.BLIND_AREA_DETECTION to 289415723, // R_0B00_SRR_1_BSDState
        MbCanKnownVehiclePropertyId.DOOR_OPEN_WARNING to 289415729,
        MbCanKnownVehiclePropertyId.FCW_SWITCH to 289415696,
        MbCanKnownVehiclePropertyId.ACC_AUTOBRAKE_SWITCH to 289415698,
        MbCanKnownVehiclePropertyId.SAFE_DISTANCE_WARNING to 289415699,
        MbCanKnownVehiclePropertyId.FCW_SENSITIVITY to 289415697,
        MbCanKnownVehiclePropertyId.LAS_SENSITIVITY_LEVEL to 289415707,
        // MBVehicleProperty.eVEHICLE_SMART_HIGHBEAM_SWITCH (HMA)
        MbCanKnownVehiclePropertyId.HMA_SWITCH to 289415702, // R_0B00_FCM_2_HMAOnOffsts
        // MBVehicleProperty.eHVAC_CUSTOM
        MbCanKnownVehiclePropertyId.HVAC_CUSTOM to 289415186, // R_0200_CEM_IPM_Custom_Air_Conditioning
        // MBVehicleProperty.eVEHICLE_SET_RRM_ACMAX_REQ
        MbCanKnownVehiclePropertyId.HVAC_AC_MAX to 289412209, // R_0400_CEM_IPM_3_ACMAXReq_Sts
        // MBVehicleProperty.eVHEICEL_FRONTWINDSCREEN_HEAT
        MbCanKnownVehiclePropertyId.FRONT_WINDSCREEN_HEAT_SWITCH to 289412114, // R_0400_RBCM_FGHeat_Request_CommandFeedb
        // MBVehicleProperty.eVEHICLE_PROPERTY_HVAC_FAN_DIRECTION
        MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION to 289415174, // R_0200_CEM_IPM_FrontBlowModeSts
        // MBVehicleProperty.eVEHICLE_PROPERTY_HVAC_DEFROSTER (rear window + mirrors)
        MbCanKnownVehiclePropertyId.HVAC_DEFROSTER_SWITCH to 289415177, // R_0200_CEM_IPM_RearDefrosts
        // MBVehicleProperty.eVEHICLE_PROPERTY_HVAC_AIR_RECIRCULATION
        MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION to 289415172, // R_0200_CEM_IPM_RecyMode
        // MBVehicleProperty.eVEHICLE_PROPERTY_HVAC_POWER
        MbCanKnownVehiclePropertyId.HVAC_POWER to 289415180, // R_0200_CEM_IPM_AC_DisplaySts
        // MBVehicleProperty.eVEHICLE_PROPERTY_HVAC_BLOWER_DELAY — AC clean when locked
        MbCanKnownVehiclePropertyId.HVAC_BLOWER_DELAY to 289415189, // R_0200_CEM_IPM_Blower_DelaySts
        // MBVehicleProperty.eHVAC_AUTO_STATE
        MbCanKnownVehiclePropertyId.HVAC_AUTO_STATE to 289415182, // R_0200_CEM_IPM_FrontAutoACSts
        // MBVehicleProperty.eVEHICLE_PROPERTY_HVAC_AQS
        MbCanKnownVehiclePropertyId.HVAC_AQS to 289415191, // R_0200_CEM_IPM_AnionPurify
        MbCanKnownVehiclePropertyId.POWER_FIRST_BREATH to 289415188,
        MbCanKnownVehiclePropertyId.BT_REDUCED_WIND_SPEED to 289415190,
        MbCanKnownVehiclePropertyId.HVAC_VENTILATION_AUTO_SWITCH to 289415187,
        MbCanKnownVehiclePropertyId.HUD_SWITCH to 289412235,
        MbCanKnownVehiclePropertyId.HUD_HEIGHT to 289412236,
        MbCanKnownVehiclePropertyId.HUD_BRIGHTNESS to 289412238,
        MbCanKnownVehiclePropertyId.HUD_DISPLAY_MODE to 289412239,
        MbCanKnownVehiclePropertyId.HUD_AUTO_BRIGHTNESS to 289412243,
        MbCanKnownVehiclePropertyId.ICM_BRIGHTNESS_MODE to 289415088, // T_0901_IHU_SET_ICMBrightnessMode
        MbCanKnownVehiclePropertyId.ICM_BRIGHTNESS_MANUAL to 289414939, // R_0900_ICM_4_BrightnessFed
        MbCanKnownVehiclePropertyId.OVERSPEED_ALARM_SET to 289415091, // write echo / stock read
        MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_LEFT to 289415169, // R_0200_CEM_IPM_FLTempsts
        MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_RIGHT to 289415168, // R_0200_CEM_IPM_FRTempsts
        MbCanKnownVehiclePropertyId.HVAC_FAN_SPEED to 289415171, // R_0200_CEM_IPM_FrontBlowSpdCtrlsts
        MbCanKnownVehiclePropertyId.HVAC_FRONT_OFF to 289415175, // R_0200_CEM_IPM_FrontOFFSts
        MbCanKnownVehiclePropertyId.HVAC_SYNC_SWITCH to 289415181, // R_0200_CEM_IPM_SyncSts
        MbCanKnownVehiclePropertyId.TRUNK_REAR_DOOR_MOVE_DIR to 289412272, // R_0402_PLG_1_RearDoorMoveDir
        MbCanKnownVehiclePropertyId.TRUNK_STATUS to 289412273, // R_0402_PLG_1_RearDoorStatus
        // Seat states
        MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH to 289415193, // R_0200_CEM_IPM_FLSeatHeatVentSwSts
        MbCanKnownVehiclePropertyId.FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH to 289415192, // R_0200_CEM_IPM_FRSeatHeatVentSwSts
        MbCanKnownVehiclePropertyId.REAR_LEFT_SEAT_HEAT_SWITCH to 289415203, // R_0202_RBCM_2_LRSeatHeatVentSwSts
        MbCanKnownVehiclePropertyId.REAR_RIGHT_SEAT_HEAT_SWITCH to 289415202, // R_0202_RBCM_2__RRSeatHeatVentSwSts
        // Car settings (drive/eps)
        MbCanKnownVehiclePropertyId.VEHICLE_PROPERTY_EPS_MODE to 289412124, // R_0400_EPS_1_EPSModeSts
        MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE to 289412123, // R_0400_TCU_G_DriverMode_7
        MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE_6DCT_WET to 289412692, // T_0401_IHU_9_DriveMode_6DCT_Wet
        MbCanKnownVehiclePropertyId.VEHICLE_TSR_SWITCH to VHAL_SLA_ON_OFF_STATUS,
        // Audio
        MbCanKnownAudioPropertyId.VOLUME to 557849090, // AUDIO_CURRENT_MAIN_VOLUME
        MbCanKnownAudioPropertyId.VOLUME_SPEED to 557849227, // AUDIO_VOL_VSC_MOD_REQ
    )

    fun resolveWritePropertyId(requestedPropertyId: Int): Int? {
        explicitWriteIdMap[requestedPropertyId]?.let { return it }
        val tables = loadTables() ?: return null
        return requestedPropertyId.takeIf { tables.sendIds.contains(it) }
    }

    fun resolveReadPropertyId(requestedPropertyId: Int): Int? {
        explicitReadIdMap[requestedPropertyId]?.let { return it }
        val tables = loadTables() ?: return null
        return requestedPropertyId.takeIf { tables.receiveIds.contains(it) }
    }

    private fun loadTables(): Tables? {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val sendFile = File(SEND_JSON_PATH)
            val receiveFile = File(RECEIVE_JSON_PATH)
            if (!sendFile.isFile || !receiveFile.isFile) {
                Log.w(TAG, "Vehicle json files not found: $SEND_JSON_PATH / $RECEIVE_JSON_PATH")
                return null
            }
            val sendIds = runCatching { parseSendIds(sendFile.readText()) }.getOrElse {
                Log.e(TAG, "Failed to parse send.json: ${it.message}")
                emptySet()
            }
            val receiveIds = runCatching { parseReceiveIds(receiveFile.readText()) }.getOrElse {
                Log.e(TAG, "Failed to parse receive.json: ${it.message}")
                emptySet()
            }
            val tables = Tables(sendIds = sendIds, receiveIds = receiveIds)
            cached = tables
            return tables
        }
    }

    private fun parseSendIds(raw: String): Set<Int> {
        val root = JSONObject(raw)
        val send = root.optJSONArray("SendJSON") ?: JSONArray()
        val ids = linkedSetOf<Int>()
        for (i in 0 until send.length()) {
            val funcObj = send.optJSONObject(i) ?: continue
            val cmdMap = funcObj.optJSONArray("CmdMap") ?: continue
            for (j in 0 until cmdMap.length()) {
                val cmdObj = cmdMap.optJSONObject(j) ?: continue
                val cmdData = cmdObj.optJSONArray("cmdData") ?: continue
                for (k in 0 until cmdData.length()) {
                    val cd = cmdData.optJSONObject(k) ?: continue
                    val sendData = cd.optJSONArray("sendData") ?: continue
                    for (z in 0 until sendData.length()) {
                        val item = sendData.optJSONObject(z) ?: continue
                        item.takeIf { it.has("ID") }?.optInt("ID")?.let { ids.add(it) }
                    }
                }
            }
        }
        return ids
    }

    private fun parseReceiveIds(raw: String): Set<Int> {
        val root = JSONObject(raw)
        val receive = root.optJSONArray("ReceiveJSON") ?: JSONArray()
        val ids = linkedSetOf<Int>()
        for (i in 0 until receive.length()) {
            val funcObj = receive.optJSONObject(i) ?: continue
            val cmdMap = funcObj.optJSONArray("CmdMap") ?: continue
            for (j in 0 until cmdMap.length()) {
                val cmdObj = cmdMap.optJSONObject(j) ?: continue
                val rcvMap = cmdObj.optJSONArray("RcvMap") ?: continue
                for (k in 0 until rcvMap.length()) {
                    val item = rcvMap.optJSONObject(k) ?: continue
                    item.takeIf { it.has("ID") }?.optInt("ID")?.let { ids.add(it) }
                }
            }
        }
        return ids
    }
}
