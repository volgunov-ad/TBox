package vad.dashing.tbox.mbcan

/**
 * Name-matched mbCAN → VHAL ids for the expert raw window only.
 *
 * Pairs come from matching OEM enum tails to constants in
 * `docs/reference/VehiclePropertyIds.java`. They are not measurements from a
 * head unit and are not copied into [FirmwareVehicleJsonMapper] production maps.
 * Android 9 keeps using the mbCAN ordinal. Android 10 expert Get/Set uses these
 * ids when the production map has no entry.
 */
internal object ExpertRawVhalCandidates {
    fun readId(bus: ExpertRawCanBus, mbCanPropertyId: Int): Int? = when (bus) {
        ExpertRawCanBus.Vehicle -> vehicleReadIds[mbCanPropertyId]
        ExpertRawCanBus.Audio -> audioReadIds[mbCanPropertyId]
        ExpertRawCanBus.VhalDirect, ExpertRawCanBus.MbCanObject -> null
    }

    fun writeId(bus: ExpertRawCanBus, mbCanPropertyId: Int): Int? = when (bus) {
        ExpertRawCanBus.Vehicle -> vehicleWriteIds[mbCanPropertyId]
        ExpertRawCanBus.Audio -> audioWriteIds[mbCanPropertyId]
        ExpertRawCanBus.VhalDirect, ExpertRawCanBus.MbCanObject -> null
    }

    private val audioReadIds: Map<Int, Int> = emptyMap()

    private val vehicleReadIds: Map<Int, Int> = mapOf(
        // R_0400_CEM_ALM_1_Wash_Car_Status. Shared by OEM 77 and the known wash id 252.
        MbCanOemVehiclePropertyId.WASH_CAR to 289412171,
        MbCanKnownVehiclePropertyId.VEHICLE_VEHWASH_MODESET to 289412171,
        // R_0400_CEM_3_PM25DisplayToggle_Sts
        MbCanKnownVehiclePropertyId.VEHICLE_PM25_DISPLAY_TOGGLE to 289412215,
        // R_0400_WCM_WirelessChargingSet_Status
        MbCanKnownVehiclePropertyId.CHG_WIRELESS_SWITCH to 289412219,
        // R_0400_CEM_MirrorFold_switchSts. Write stays the verified explicit id.
        MbCanKnownVehiclePropertyId.MIRROR_FOLD_SWITCH to 289412195,
        // R_0400_CEM_2_AidTurningIlluminationSts
        MbCanOemVehiclePropertyId.AIDTURNINGLLLUMINATION to 289412147,
        // R_0900_CEM_PEPS_Polling_Sts
        MbCanOemVehiclePropertyId.POLLING to 289414962,
        // R_0400_CEM_2_RemoteTrunkOnlySts
        MbCanOemVehiclePropertyId.REMOTETRUNKONLY to 289412150,
        // R_0400_CEM_2_BlankchanginglaneSts
        MbCanOemVehiclePropertyId.BLANKCHANGINGLANE to 289412125,
        // R_0402_DHM_1_Doorkonb_Time_Freeback
        MbCanOemVehiclePropertyId.DOORKONB_TIME to 289412284,
        // R_0900_EMS1G_ISS_Sts
        MbCanOemVehiclePropertyId.ISS_SWITCH to 289414948,
        // R_0400_CEM_2_External_WelcomelightSts
        MbCanOemVehiclePropertyId.EXTERNAL_LIGHT_WELCOME_LAMP_MODE to 289412137,
        // R_0400_CEM_ALM_1_STAT_64Colour
        MbCanOemVehiclePropertyId.SET_64COLOUR to 289412156,
        // R_0400_CEM_ALM_1_STAT_StaticEffect
        MbCanOemVehiclePropertyId.STATIC_EFFECT to 289412158,
        // R_0400_CEM_ALM_1_STAT_WelcomeWaterLamp
        MbCanOemVehiclePropertyId.WELCOME_LAMP to 289412166,
        // R_0400_LightShowSts
        MbCanOemVehiclePropertyId.LIGHT_SHOW to 289412216,
        // R_0402_CEM_DriverSeat_BackFRSts
        MbCanOemVehiclePropertyId.DRIVER_SEAT_BACK_FR to 289412279,
        // R_0402_CEM_DriverSeat_FRSts
        MbCanOemVehiclePropertyId.DRIVER_SEAT_FR to 289412278,
        // R_0402_CEM_DriverSeat_FrontendUDSts
        MbCanOemVehiclePropertyId.DRIVER_SEAT_FRONTENDUD to 289412310,
        // R_0402_CEM_DriverSeat_RearendUDSts
        MbCanOemVehiclePropertyId.DRIVER_SEAT_REARENDUD to 289412277,
        // R_0402_CEM_DriverLumbart_FRSts
        MbCanOemVehiclePropertyId.DRIVER_LUMBART_FR to 289412293,
        // R_0402_CEM_DriverLumbart_UDSts
        MbCanOemVehiclePropertyId.DRIVER_LUMBART_UD to 289412292,
        // R_0402_RBCM_PassengerSeat_BackFRSts
        MbCanOemVehiclePropertyId.PASSENGER_SEAT_BACK_FR to 289412281,
        // R_0402_CEM_PassengerSeat_FR_Locaton
        MbCanOemVehiclePropertyId.PASSENGER_SEAT_FR to 289412299,
        // R_0402_CEM_PassengerSeat_LegUD_Locaton
        MbCanOemVehiclePropertyId.PASSENGER_SEAT_LEG_UD to 289412300,
        // R_0400_CEM_Passenger_massageSts
        MbCanOemVehiclePropertyId.PASSENGER_MASSAGE_SWITCH to 289412197,
        // R_0400_CEM_UnderVoltageSts
        MbCanOemVehiclePropertyId.UNDER_VOLTAGE_TIP_SWITCH to 289412198,
        // R_0400_RBCM_MFS_ShakeSts
        MbCanOemVehiclePropertyId.MFS_SHAKE_SWITCH to 289412110,
        // R_0400_CEM_3_Welcome_Seat_Sts
        MbCanOemVehiclePropertyId.WELCOME_SEAT to 289412211,
        // R_0400_Welcome_Outsound_SwitchSts
        MbCanOemVehiclePropertyId.WELCOME_SOUND_DEVICE_SWITCH to 289412232,
        // R_0400_ICM_2_RseatBeltBuckleSwitchSts
        MbCanOemVehiclePropertyId.R_SEAT_BELT_BUCKLE_SWITCH to 289412188,
        // R_0400_CEM_LeftMirrorCtrlSts / RightMirrorCtrlSts
        MbCanOemVehiclePropertyId.MIRROR_SET_LEFT_MIRROR_CTRL to 289412191,
        MbCanOemVehiclePropertyId.MIRROR_SET_RIGHT_MIRROR_CTRL to 289412190,
        // R_0400_CEM_Mirror_Driverseat_location
        MbCanOemVehiclePropertyId.MIRROR_SET_DRIVER_MIRROR_LOCATION to 289412192,
        // R_0400_CEM_Lmirror_* / Rmirror_*
        MbCanOemVehiclePropertyId.MIRROR_SET_LMIRROR_LR_LOCATON to 289412201,
        MbCanOemVehiclePropertyId.MIRROR_SET_LMIRROR_UD_LOCATON to 289412202,
        MbCanOemVehiclePropertyId.MIRROR_SET_RMIRROR_LR_LOCATON to 289412203,
        MbCanOemVehiclePropertyId.MIRROR_SET_RMIRROR_UD_LOCATON to 289412204,
        // R_0409_STAT_Navigati_OnDisplay
        MbCanOemVehiclePropertyId.NAVIGATI_ONDISPLAY to 289412240,
        // R_0400_CEM_3_DWM_Sts_Msg
        MbCanOemVehiclePropertyId.DWMCON_SWITCH to 289412208,
        // R_0404_CEM_2_DRLSts
        MbCanOemVehiclePropertyId.DRL_SWITCH to 289412249,
        // R_0404_CEM_Smart_HighBeamSts. HMA on this car is mbCAN 19, not this status.
        MbCanOemVehiclePropertyId.SMART_HIGHBEAM_SWITCH to 289412260,
        // R_0400_RBCM_QueenSeatOpenSts / CloseSts
        MbCanOemVehiclePropertyId.SEAT_QUEENOPENSTS to 289412113,
        MbCanOemVehiclePropertyId.SEAT_QUEENCLOSESTS to 289412112,
    )

    private val vehicleWriteIds: Map<Int, Int> = mapOf(
        // T_0401_IHU_1_DVD_Set_Wash_Car
        MbCanOemVehiclePropertyId.WASH_CAR to 289412663,
        MbCanKnownVehiclePropertyId.VEHICLE_VEHWASH_MODESET to 289412663,
        // T_0901_IHU_5_PM25DisplayToggle
        MbCanKnownVehiclePropertyId.VEHICLE_PM25_DISPLAY_TOGGLE to 289415348,
        // T_0401_IHU_1_SET_AidTurningIllumination
        MbCanOemVehiclePropertyId.AIDTURNINGLLLUMINATION to 289412655,
        // T_0401_IHU_1_DVD_SET_Polling
        MbCanOemVehiclePropertyId.POLLING to 289412671,
        // T_0901_IHU_3_CalibrationRequest
        MbCanOemVehiclePropertyId.CALIBRATION to 289415057,
        // T_0901_IHU_3_DVD_Set_SteeringWheel
        MbCanOemVehiclePropertyId.STREERINGWHEEL to 289415054,
        // T_0901_IHU_3_DVD_Set_RadarWarining
        MbCanOemVehiclePropertyId.RADARWARING to 289415059,
        // T_0901_IHU_3_DVD_DefDispMode
        MbCanOemVehiclePropertyId.DEFDISPMODE to 289415062,
        // T_0401_IHU_9_NavSpeedLimit / Status / Units
        MbCanOemVehiclePropertyId.NAVI_SPEEDLIMIT to 289412690,
        MbCanOemVehiclePropertyId.NAVI_SPEEDLIMIT_STATUS to 289412691,
        MbCanOemVehiclePropertyId.NAVI_SPEEDLIMIT_UNITS to 289412693,
        // T_0403_SET_Doorkonb_Time
        MbCanOemVehiclePropertyId.DOORKONB_TIME to 289412640,
        // T_0405_SET_Tunnel_Lamp
        MbCanOemVehiclePropertyId.TUNNEL_LAMP to 289412611,
        // T_0401_IHU_9_ECOModeSWSts
        MbCanOemVehiclePropertyId.ECOMODESWSTS to 289412696,
        // T_0401_IHU_1_Set_External_Welcome_Light
        MbCanOemVehiclePropertyId.EXTERNAL_LIGHT_WELCOME_LAMP_MODE to 289412669,
        // T_0401_IHU_1_DVD_SetRolloControlEnable
        MbCanOemVehiclePropertyId.ROLLO_CONTROL to 289412675,
        // T_0901_IHU_2 radio cluster
        MbCanOemVehiclePropertyId.RADIO_FREQUANCE_MODE to 289415043,
        MbCanOemVehiclePropertyId.RADIO_RESEARCH_STS to 289415042,
        MbCanOemVehiclePropertyId.RADIO_SOURCE_STATION_MODE to 289415041,
        MbCanOemVehiclePropertyId.FMRADIO_FREQUANCE_NUMBER to 289415044,
        MbCanOemVehiclePropertyId.AMRADIO_FREQUANCE_NUMBER to 289415046,
        MbCanOemVehiclePropertyId.KNOB_STS to 289415048,
        MbCanOemVehiclePropertyId.FREQUENCY_STS to 289415050,
        // T_0407_IHU_6_SET_64Colour / StaticEffect / WelcomeWaterLamp / SceneMode
        MbCanOemVehiclePropertyId.SET_64COLOUR to 289412627,
        MbCanOemVehiclePropertyId.STATIC_EFFECT to 289412623,
        MbCanOemVehiclePropertyId.WELCOME_LAMP to 289412618,
        MbCanOemVehiclePropertyId.AMBIENTLIGHT_SCENEMODE to 289412629,
        // T_0401_IHU_1_DVD_Set_Show_Mode
        MbCanOemVehiclePropertyId.LIGHT_SHOW to 289412670,
        // T_0403 seat motion
        MbCanOemVehiclePropertyId.DRIVER_SEAT_BACK_FR to 289412637,
        MbCanOemVehiclePropertyId.DRIVER_SEAT_FR to 289412644,
        MbCanOemVehiclePropertyId.DRIVER_SEAT_FRONTENDUD to 289412643,
        MbCanOemVehiclePropertyId.DRIVER_SEAT_REARENDUD to 289412642,
        MbCanOemVehiclePropertyId.DRIVER_LUMBART_FR to 289412641,
        MbCanOemVehiclePropertyId.DRIVER_LUMBART_UD to 289412648,
        MbCanOemVehiclePropertyId.PASSENGER_SEAT_BACK_FR to 289412647,
        MbCanOemVehiclePropertyId.PASSENGER_SEAT_FR to 289412646,
        MbCanOemVehiclePropertyId.PASSENGER_SEAT_LEG_UD to 289412645,
        MbCanOemVehiclePropertyId.PASSENGER_MASSAGE_SWITCH to 289412651,
        // T_0403_SET_Massagemode
        MbCanOemVehiclePropertyId.PASSENGER_MASSAGE_MODE to 289412649,
        // T_0401_SET_UnderVoltage / MFS_Shake / Welcome_Seat
        MbCanOemVehiclePropertyId.UNDER_VOLTAGE_TIP_SWITCH to 289412680,
        MbCanOemVehiclePropertyId.MFS_SHAKE_SWITCH to 289412684,
        MbCanOemVehiclePropertyId.WELCOME_SEAT to 289412683,
        // T_0401_Welcome_Sound_Device_Switching / Sound_Options_Signal
        MbCanOemVehiclePropertyId.WELCOME_SOUND_DEVICE_SWITCH to 289412687,
        MbCanOemVehiclePropertyId.WELCOME_SOUND_OPTIONS_SIGNAL to 289412686,
        // T_0401_IHU_SpeechControl
        MbCanOemVehiclePropertyId.RRM_SPEECH_CONTROL to 289412711,
        // Mirror ctrl and location
        MbCanOemVehiclePropertyId.MIRROR_SET_LEFT_MIRROR_CTRL to 289412702,
        MbCanOemVehiclePropertyId.MIRROR_SET_RIGHT_MIRROR_CTRL to 289412712,
        MbCanOemVehiclePropertyId.MIRROR_SET_DRIVER_MIRROR_LOCATION to 289412713,
        MbCanOemVehiclePropertyId.MIRROR_SET_PASSENGER_MIRROR_LOCATION to 289412706,
        MbCanOemVehiclePropertyId.MIRROR_SET_LMIRROR_LR_LOCATON to 289412707,
        MbCanOemVehiclePropertyId.MIRROR_SET_LMIRROR_UD_LOCATON to 289412708,
        MbCanOemVehiclePropertyId.MIRROR_SET_RMIRROR_LR_LOCATON to 289412709,
        MbCanOemVehiclePropertyId.MIRROR_SET_RMIRROR_UD_LOCATON to 289412710,
        // T_0901 scene / fatigue / animations
        MbCanOemVehiclePropertyId.SMART_SCENE_MODE to 289415086,
        MbCanOemVehiclePropertyId.FATIGUE_DRIVING to 289415085,
        MbCanOemVehiclePropertyId.UNLOCK_ANIMATION to 289415084,
        MbCanOemVehiclePropertyId.LOCK_ANIMATION to 289415089,
        // T_0B01 MFS pulses
        MbCanOemVehiclePropertyId.MFS_SEEPD_LIMIT to 289415955,
        MbCanOemVehiclePropertyId.RESERVED_MFS_SHIFT_UP to 289415959,
        MbCanOemVehiclePropertyId.RESERVED_MFS_SHIFT_DOWN to 289415958,
        MbCanOemVehiclePropertyId.MFS_TIME_GAP to 289415957,
        // T_0401_IHU_Set_Navigati_OnDisplay
        MbCanOemVehiclePropertyId.NAVIGATI_ONDISPLAY to 289412720,
        // T_0901_IHU_5_DVD_DWMCon_Req
        MbCanOemVehiclePropertyId.DWMCON_SWITCH to 289415349,
        // T_0901_IHU_21_FatigueDrivingReminder_Set
        MbCanOemVehiclePropertyId.FATIGUEDRIVINGREMINDER_SET to 289415090,
        // T_0401_IHU_1_RegenerateLevelCtrl
        MbCanOemVehiclePropertyId.REGENERATE_LEVEL_CTRL to 289412676,
        // T_0901_IHU_21VoiceWake
        MbCanOemVehiclePropertyId.VOICE_WAKE to 289415097,
    )

    private val audioWriteIds: Map<Int, Int> = mapOf(
        // T_0901_IHU_6_SET_MusicLoudness_*
        MbCanOemAudioPropertyId.MUSICLOUDNESS_120HZ to 289415083,
        MbCanOemAudioPropertyId.MUSICLOUDNESS_250HZ to 289415082,
        MbCanOemAudioPropertyId.MUSICLOUDNESS_500HZ to 289415081,
        MbCanOemAudioPropertyId.MUSICLOUDNESS_1000HZ to 289415080,
        MbCanOemAudioPropertyId.MUSICLOUDNESS_2000HZ to 289415078,
        MbCanOemAudioPropertyId.MUSICLOUDNESS_6000HZ to 289415077,
        MbCanOemAudioPropertyId.MUSICLOUDNESS_1500HZ to 289415079,
    )
}
