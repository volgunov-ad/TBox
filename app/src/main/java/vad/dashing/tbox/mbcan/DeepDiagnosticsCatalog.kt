package vad.dashing.tbox.mbcan

/**
 * Single source of truth for the session-only deep CAN diagnostics mode
 * («Расширенная диагностика mbCAN/VHAL»).
 *
 * Read-only discovery: ids below are only subscribed for change events and
 * logged to the DEBUG journal; nothing in this catalog writes to the vehicle.
 *
 * A10 (VHAL): direct telemetry constants from [FirmwareVehicleJsonMapper],
 * verified read translations from [FirmwareVehicleJsonMapper.explicitReadEntries],
 * plus experimental ids. Write-only OEM `T_*` pulse ids (MFS cruise, SLA req)
 * are deliberately absent — subscribing them yields nothing.
 *
 * A9 (mbCAN): [mbcanDataTypes] names every `MBCanDataType` to subscribe.
 * Names missing on a given OEM build are dropped at runtime and logged,
 * because [MbCanEngineFacade.subscribe] resolves the whole list at once.
 */
object DeepDiagnosticsCatalog {

    /** A10 direct telemetry / status property ids already decoded by the app. */
    private val vhalTelemetryIdNames: List<Pair<Int, String>> = listOf(
        FirmwareVehicleJsonMapper.VHAL_ENGINE_RPM_PROPERTY_ID to "EngineRpm",
        FirmwareVehicleJsonMapper.VHAL_ENGINE_TEMPERATURE_PROPERTY_ID to "EngineCoolantTemp",
        FirmwareVehicleJsonMapper.VHAL_CAR_SPEED_PROPERTY_ID to "CarSpeed",
        FirmwareVehicleJsonMapper.VHAL_MCU_REPLY_ACC_STATUS_PROPERTY_ID to "McuAccStatus",
        FirmwareVehicleJsonMapper.VHAL_STEERING_WHEEL_ANGLE_PROPERTY_ID to "SteeringWheelAngle",
        FirmwareVehicleJsonMapper.VHAL_GEAR_SELECTION_PROPERTY_ID to "GearSelection",
        FirmwareVehicleJsonMapper.VHAL_CURRENT_GEAR_PROPERTY_ID to "CurrentGear",
        FirmwareVehicleJsonMapper.VHAL_REVERSE_GEAR_SWITCH_PROPERTY_ID to "ReverseGearSwitch",
        FirmwareVehicleJsonMapper.VHAL_HAZARD_LIGHT_SW_PROPERTY_ID to "HazardLightSw",
        FirmwareVehicleJsonMapper.VHAL_LH_TURN_LIGHT_STS_PROPERTY_ID to "LhTurnLightSts",
        FirmwareVehicleJsonMapper.VHAL_RH_TURN_LIGHT_STS_PROPERTY_ID to "RhTurnLightSts",
        FirmwareVehicleJsonMapper.VHAL_DIRECTION_IND_LEFT_PROPERTY_ID to "DirectionIndLeft",
        FirmwareVehicleJsonMapper.VHAL_DIRECTION_IND_RIGHT_PROPERTY_ID to "DirectionIndRight",
        FirmwareVehicleJsonMapper.VHAL_FUEL_LEVEL_PROPERTY_ID to "FuelLevel",
        FirmwareVehicleJsonMapper.VHAL_TOTAL_ODOMETER_KM_PROPERTY_ID to "TotalOdometerKm",
        FirmwareVehicleJsonMapper.VHAL_LHF_PULSE_COUNTER_PROPERTY_ID to "LhfPulseCounter",
        FirmwareVehicleJsonMapper.VHAL_RHF_PULSE_COUNTER_PROPERTY_ID to "RhfPulseCounter",
        FirmwareVehicleJsonMapper.VHAL_LHR_PULSE_COUNTER_PROPERTY_ID to "LhrPulseCounter",
        FirmwareVehicleJsonMapper.VHAL_RHR_PULSE_COUNTER_PROPERTY_ID to "RhrPulseCounter",
        FirmwareVehicleJsonMapper.VHAL_FUEL_ROLLING_COUNTER_PROPERTY_ID to "FuelRollingCounter",
        FirmwareVehicleJsonMapper.VHAL_AVERAGE_FUEL_CONSUME_PROPERTY_ID to "AverageFuelConsume",
        FirmwareVehicleJsonMapper.VHAL_MAINTENANCE_TIPS_PROPERTY_ID to "MaintenanceTips",
        FirmwareVehicleJsonMapper.VHAL_DISTANCE_TO_EMPTY_KM_PROPERTY_ID to "DistanceToEmptyKm",
        FirmwareVehicleJsonMapper.VHAL_PM25_INDENSITY_PROPERTY_ID to "Pm25Indensity",
        FirmwareVehicleJsonMapper.VHAL_PM25_OUTDENSITY_PROPERTY_ID to "Pm25Outdensity",
        FirmwareVehicleJsonMapper.VHAL_EXTERNAL_TEMPERATURE_RAW_PROPERTY_ID to "ExternalTemperatureRaw",
        FirmwareVehicleJsonMapper.VHAL_LF_TYRE_PRESSURE to "LfTyrePressure",
        FirmwareVehicleJsonMapper.VHAL_RF_TYRE_PRESSURE to "RfTyrePressure",
        FirmwareVehicleJsonMapper.VHAL_LR_TYRE_PRESSURE to "LrTyrePressure",
        FirmwareVehicleJsonMapper.VHAL_RR_TYRE_PRESSURE to "RrTyrePressure",
        FirmwareVehicleJsonMapper.VHAL_LF_TYRE_TEMPERATURE to "LfTyreTemperature",
        FirmwareVehicleJsonMapper.VHAL_RF_TYRE_TEMPERATURE to "RfTyreTemperature",
        FirmwareVehicleJsonMapper.VHAL_LR_TYRE_TEMPERATURE to "LrTyreTemperature",
        FirmwareVehicleJsonMapper.VHAL_RR_TYRE_TEMPERATURE to "RrTyreTemperature",
        FirmwareVehicleJsonMapper.VHAL_SLA_SPEED_LIMIT_RAW to "SlaSpeedLimitRaw",
        FirmwareVehicleJsonMapper.VHAL_SLA_ON_OFF_STATUS to "SlaOnOffStatus",
        FirmwareVehicleJsonMapper.VHAL_SLA_STATE to "SlaState",
        FirmwareVehicleJsonMapper.VHAL_FRM_ACC_MODE to "FrmAccMode",
        FirmwareVehicleJsonMapper.VHAL_FRM_V_SET_DIS to "FrmVSetDis",
        FirmwareVehicleJsonMapper.VHAL_FRM_DX_TAR_OBJ to "FrmDxTarObj",
        FirmwareVehicleJsonMapper.VHAL_FRM_OBJ_VALID to "FrmObjValid",
        FirmwareVehicleJsonMapper.VHAL_EMS_CRUISE_CONTROL_STATUS to "EmsCruiseControlStatus",
        FirmwareVehicleJsonMapper.VHAL_EMS_GAS_PEDAL_POSITION to "EmsGasPedalPosition",
        FirmwareVehicleJsonMapper.VHAL_EMS_GAS_PEDAL_POSITION_INVALID to "EmsGasPedalPositionInvalid",
        FirmwareVehicleJsonMapper.VHAL_CEM_BRAKE_PEDAL_STS to "CemBrakePedalSts",
        FirmwareVehicleJsonMapper.VHAL_CEM_WIPER_STS to "CemWiperSts",
        FirmwareVehicleJsonMapper.VHAL_CEM_RAIN_DETECTED to "CemRainDetected",
        FirmwareVehicleJsonMapper.VHAL_CEM_HIGH_BEAM_STS to "CemHighBeamSts",
        FirmwareVehicleJsonMapper.VHAL_ICM_EPB_WARNING_LAMP_STS to "IcmEpbWarningLampSts",
        FirmwareVehicleJsonMapper.VHAL_ICM_ENGINE_OIL_PRESSURE to "IcmEngineOilPressure",
        FirmwareVehicleJsonMapper.VHAL_ICM_BRAKE_FLUID_LEVEL to "IcmBrakeFluidLevel",
        FirmwareVehicleJsonMapper.VHAL_GSM_GEAR_SHIFT_POS to "GsmGearShiftPos",
        FirmwareVehicleJsonMapper.VHAL_EMS_TARGET_GEAR_POSITION to "EmsTargetGearPosition",
        FirmwareVehicleJsonMapper.VHAL_SUNSHADE_CMD_STS to "SunshadeCmdSts",
        FirmwareVehicleJsonMapper.VHAL_SUNROOF_CMD_STS to "SunroofCmdSts",
        FirmwareVehicleJsonMapper.VHAL_FL_WIN_POSITION to "FlWinPosition",
        FirmwareVehicleJsonMapper.VHAL_FR_WIN_POSITION to "FrWinPosition",
        FirmwareVehicleJsonMapper.VHAL_RL_WIN_POSITION to "RlWinPosition",
        FirmwareVehicleJsonMapper.VHAL_RR_WIN_POSITION to "RrWinPosition",
    )

    /**
     * A10 ids not yet mapped in [FirmwareVehicleJsonMapper]; semantics guessed from
     * neighbouring OEM ids — the whole point of deep mode is to confirm them on the car.
     */
    val vhalExperimentalIdNames: List<Pair<Int, String>> = listOf(
        289_411_329 to "RadarCh1",
        289_411_330 to "RadarCh2",
        289_411_331 to "RadarCh3",
        289_411_332 to "RadarCh4",
        289_411_333 to "RadarCh5",
        289_411_334 to "RadarCh6",
        289_411_335 to "RadarCh7",
        289_411_336 to "RadarCh8",
        289_412_217 to "Wcm1",
        289_412_218 to "Wcm2",
        289_412_219 to "Wcm3",
        289_412_230 to "Pm25Level",
        289_412_231 to "Pm25Err",
        289_412_250 to "LowBeamSts",
        289_412_253 to "ParkTailLampSts",
        289_412_260 to "SmartHighBeamSts",
        289_412_274 to "PlgSasSts",
        289_412_346 to "WasherFluidLevel",
        289_415_179 to "TempknobRollingCounter",
        289_415_204 to "SeatHeatVentAlt1",
        289_415_205 to "SeatHeatVentAlt2",
    )

    /** All A10 VHAL property ids to subscribe in deep mode. */
    val vhalPropertyIds: List<Int> = (
        vhalTelemetryIdNames.map { it.first } +
            FirmwareVehicleJsonMapper.explicitReadEntries().map { it.second } +
            vhalExperimentalIdNames.map { it.first }
        ).distinct()

    /** A9 `MBCanDataType` names already used by production signals. */
    val mbcanProductionDataTypes: List<String> = listOf(
        "eMBCAN_CFG_VEHICLE",
        "eMBCAN_CFG_AUDIO",
        "eMBCAN_VEHICLE_ENGINE",
        "eMBCAN_VEHICLE_SPEED",
        "eMBCAN_VEHICLE_GEAR",
        "eMBCAN_VEHICLE_ACCSTATUS",
        "eMBCAN_VEHICLE_BCM_STATUS",
        "eMBCAN_VEHICLE_ICM_DRIVE_INFO",
        "eMBCAN_VEHICLE_FUELLEVEL",
        "eMBCAN_VEHICLE_TOTALODOMETER",
        "eMBCAN_VEHICLE_WHEEL",
        "eMBCAN_VEHICLE_EXTERNAL_TEMP_RAW",
        "eMBCAN_VEHICLE_LKA_STATUS",
        "eMBCAN_VEHICLE_TIRE",
        "eMBCAN_VEHICLE_ICM_INFO",
        "eMBCAN_ICM_TRIP_INFO",
        "eMBCAN_PM25INFO",
        "eMBCAN_VEHICLE_STEERING_ANGLE",
        "eMBCAN_VEHICLE_TURNLIGHT",
    )

    /** A9 `MBCanDataType` names unused by production signals (A10 parity / discovery candidates). */
    val mbcanExperimentalDataTypes: List<String> = listOf(
        "eMBCAN_VEHICLE_DOOR",
        "eMBCAN_SEAT_BELT_STATUS",
        "eMBCAN_SEAT_STATUS",
        "eMBCAN_WPC_STATUS",
        "eMBCAN_VEHICLE_AQS_STATUS",
        "eMBCAN_RADARSENSOR",
        "eMBCAN_RCTA_ALARM",
        "eMBCAN_BSD_ALARM",
        "eMBCAN_DOW_ALARM",
        "eMBCAN_VEHICLE_FRM_INFO",
        "eMBCAN_CFG_DMS",
        "eMBCAN_CHARGING_RESERVE",
        "eMBCAN_SYSTEMMODE",
        "eMBCAN_HARDKEY",
        "eMBCAN_UPGRADE_PROGRESS",
        "eMBCAN_DVR_STATUS",
        "eMBCAN_VEHICLE_DVR_PARAM",
        "eMBCAN_DTC",
        "eMBCAN_VEHICLE_EBS_SOC",
        "eMBCAN_VEHICLE_CONSUMPTION",
        "eMBCAN_VEHICLE_INVERTER_STATUS",
        "eMBCAN_VEHICLE_ENGINE_GEAR",
    )

    /** All A9 `MBCanDataType` names to subscribe in deep mode. */
    val mbcanDataTypes: List<String> = mbcanProductionDataTypes + mbcanExperimentalDataTypes

    /**
     * Short signal name for a raw A10 property id, or null when unknown
     * (unmapped read translations resolve to `mbcan#<logicalId>`).
     */
    fun annotateVhalPropertyId(propertyId: Int): String? = vhalAnnotations[propertyId]

    /**
     * Name of an A9 cfg push item: `MBVehicleProperty` id for vehicle cfg,
     * `MBAudioProperty` id for audio cfg. Vehicle map wins on id collisions
     * (e.g. 2 = DOOR_IGNOFF_UNLOCK vs VOLUME); annotation only, raw id is logged too.
     */
    fun annotateMbCanItem(item: Int): String? =
        vehicleItemNames[item] ?: audioItemNames[item]

    val vhalAnnotations: Map<Int, String> by lazy {
        buildMap {
            vhalTelemetryIdNames.forEach { (id, name) -> putIfAbsent(id, name) }
            vhalExperimentalIdNames.forEach { (id, name) -> putIfAbsent(id, name) }
            FirmwareVehicleJsonMapper.explicitReadEntries().forEach { (logicalId, vhalId) ->
                putIfAbsent(vhalId, vehicleItemNames[logicalId] ?: "mbcan#$logicalId")
            }
        }
    }

    private val vehicleItemNames: Map<Int, String> by lazy {
        reflectIntFieldNames(MbCanKnownVehiclePropertyId)
    }

    private val audioItemNames: Map<Int, String> by lazy {
        reflectIntFieldNames(MbCanKnownAudioPropertyId)
    }

    /**
     * `const val` fields of a Kotlin object compile to public static ints; reflect them
     * into id → name. Alphabetical + first-wins keeps value constants
     * (e.g. LIGHTCONTROL_AUTO = 1) deterministic but secondary to real ids.
     */
    private fun reflectIntFieldNames(owner: Any): Map<Int, String> {
        val result = linkedMapOf<Int, String>()
        owner.javaClass.fields
            .filter { it.type == Int::class.javaPrimitiveType }
            .sortedBy { it.name }
            .forEach { field ->
                runCatching { field.getInt(null) }.getOrNull()?.let { value ->
                    if (!result.containsKey(value)) result[value] = field.name
                }
            }
        return result
    }
}
