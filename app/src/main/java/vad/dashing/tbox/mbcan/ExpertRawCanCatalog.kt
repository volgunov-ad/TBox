package vad.dashing.tbox.mbcan

import vad.dashing.tbox.esp.HuCanMarkLog

/** Bus for raw expert Get/Set (A9 JNI channels / A10 mapped VHAL ids). */
enum class ExpertRawCanBus {
    Vehicle,
    Audio,
    /** A10 VHAL id with no mbCAN logical id. A9 Get/Set refuses it. */
    VhalDirect,
}

/**
 * One catalog row: known mbCAN logical id plus optional A10 VHAL read/write ids
 * when [FirmwareVehicleJsonMapper] has an explicit (or firmware-table) mapping.
 */
data class ExpertRawCanParam(
    val name: String,
    val mbCanId: Int,
    val bus: ExpertRawCanBus,
    val vhalReadId: Int?,
    val vhalWriteId: Int?,
) {
    /** True when A10 read and write property ids differ. */
    val readWriteDiffer: Boolean
        get() = vhalReadId != null && vhalWriteId != null && vhalReadId != vhalWriteId

    fun displayLabel(): String = "$name ($mbCanId)"
}

data class ExpertRawGetResult(
    val success: Boolean,
    val rawValue: Int? = null,
    /** Actual backend property id used for the read (mbCAN ordinal or VHAL id). */
    val effectivePropertyId: Int? = null,
    val message: String,
)

data class ExpertRawSetResult(
    val success: Boolean,
    /** Actual backend property id used for the write. */
    val effectivePropertyId: Int? = null,
    val message: String,
)

/**
 * Builds the expert Get/Set parameter catalog from known vehicle/audio property ids.
 * Pure helper — safe for unit tests without OEM bindings.
 */
object ExpertRawCanCatalog {
    private const val LOG_TAG = "EXPERT_CAN"

    fun allParams(): List<ExpertRawCanParam> {
        // Catalog display uses explicit maps only (no firmware JSON / Log probe).
        // Live Get/Set still goes through resolveRead/WritePropertyId on device.
        // Names come from uniqueConstNameMap. Id objects use @JvmField (not const)
        // plus ProGuard keep so R8 does not drop the static ints.
        // Direct VHAL rows are a fixed list of inlined const ids (see directVhalParams).
        val vehicle = HuCanMarkLog.uniqueConstNameMap(MbCanKnownVehiclePropertyId::class.java)
            .entries
            .map { (id, name) ->
                ExpertRawCanParam(
                    name = name,
                    mbCanId = id,
                    bus = ExpertRawCanBus.Vehicle,
                    vhalReadId = FirmwareVehicleJsonMapper.peekExplicitReadPropertyId(id),
                    vhalWriteId = FirmwareVehicleJsonMapper.peekExplicitWritePropertyId(id),
                )
            }
        val audio = HuCanMarkLog.uniqueConstNameMap(MbCanKnownAudioPropertyId::class.java)
            .entries
            .map { (id, name) ->
                ExpertRawCanParam(
                    name = name,
                    mbCanId = id,
                    bus = ExpertRawCanBus.Audio,
                    vhalReadId = FirmwareVehicleJsonMapper.peekExplicitReadPropertyId(id),
                    vhalWriteId = FirmwareVehicleJsonMapper.peekExplicitWritePropertyId(id),
                )
            }
        return (vehicle + audio + directVhalParams()).sortedWith(
            compareBy<ExpertRawCanParam> { it.bus.ordinal }
                .thenBy { it.name },
        )
    }

    /**
     * Decoded A10 VHAL ids that are not already the numeric explicit id of a
     * logical row (windows, shade, roof). The deprecated speed alias is omitted.
     * Names are the [FirmwareVehicleJsonMapper] const names. `const val` is inlined
     * here so R8 cannot drop the ids.
     */
    private fun directVhalParams(): List<ExpertRawCanParam> =
        directVhalIds.map { (name, id) ->
            ExpertRawCanParam(
                name = name,
                mbCanId = id,
                bus = ExpertRawCanBus.VhalDirect,
                vhalReadId = id,
                vhalWriteId = id,
            )
        }

    private val directVhalIds: List<Pair<String, Int>> = listOf(
        "VHAL_ENGINE_RPM_PROPERTY_ID" to FirmwareVehicleJsonMapper.VHAL_ENGINE_RPM_PROPERTY_ID,
        "VHAL_ENGINE_TEMPERATURE_PROPERTY_ID" to FirmwareVehicleJsonMapper.VHAL_ENGINE_TEMPERATURE_PROPERTY_ID,
        "VHAL_CAR_SPEED_PROPERTY_ID" to FirmwareVehicleJsonMapper.VHAL_CAR_SPEED_PROPERTY_ID,
        "VHAL_MCU_REPLY_ACC_STATUS_PROPERTY_ID" to FirmwareVehicleJsonMapper.VHAL_MCU_REPLY_ACC_STATUS_PROPERTY_ID,
        "VHAL_STEERING_WHEEL_ANGLE_PROPERTY_ID" to FirmwareVehicleJsonMapper.VHAL_STEERING_WHEEL_ANGLE_PROPERTY_ID,
        "VHAL_GEAR_SELECTION_PROPERTY_ID" to FirmwareVehicleJsonMapper.VHAL_GEAR_SELECTION_PROPERTY_ID,
        "VHAL_CURRENT_GEAR_PROPERTY_ID" to FirmwareVehicleJsonMapper.VHAL_CURRENT_GEAR_PROPERTY_ID,
        "VHAL_REVERSE_GEAR_SWITCH_PROPERTY_ID" to FirmwareVehicleJsonMapper.VHAL_REVERSE_GEAR_SWITCH_PROPERTY_ID,
        "VHAL_HAZARD_LIGHT_SW_PROPERTY_ID" to FirmwareVehicleJsonMapper.VHAL_HAZARD_LIGHT_SW_PROPERTY_ID,
        "VHAL_LH_TURN_LIGHT_STS_PROPERTY_ID" to FirmwareVehicleJsonMapper.VHAL_LH_TURN_LIGHT_STS_PROPERTY_ID,
        "VHAL_RH_TURN_LIGHT_STS_PROPERTY_ID" to FirmwareVehicleJsonMapper.VHAL_RH_TURN_LIGHT_STS_PROPERTY_ID,
        "VHAL_DIRECTION_IND_LEFT_PROPERTY_ID" to FirmwareVehicleJsonMapper.VHAL_DIRECTION_IND_LEFT_PROPERTY_ID,
        "VHAL_DIRECTION_IND_RIGHT_PROPERTY_ID" to FirmwareVehicleJsonMapper.VHAL_DIRECTION_IND_RIGHT_PROPERTY_ID,
        "VHAL_FUEL_LEVEL_PROPERTY_ID" to FirmwareVehicleJsonMapper.VHAL_FUEL_LEVEL_PROPERTY_ID,
        "VHAL_TOTAL_ODOMETER_KM_PROPERTY_ID" to FirmwareVehicleJsonMapper.VHAL_TOTAL_ODOMETER_KM_PROPERTY_ID,
        "VHAL_LHF_PULSE_COUNTER_PROPERTY_ID" to FirmwareVehicleJsonMapper.VHAL_LHF_PULSE_COUNTER_PROPERTY_ID,
        "VHAL_RHF_PULSE_COUNTER_PROPERTY_ID" to FirmwareVehicleJsonMapper.VHAL_RHF_PULSE_COUNTER_PROPERTY_ID,
        "VHAL_LHR_PULSE_COUNTER_PROPERTY_ID" to FirmwareVehicleJsonMapper.VHAL_LHR_PULSE_COUNTER_PROPERTY_ID,
        "VHAL_RHR_PULSE_COUNTER_PROPERTY_ID" to FirmwareVehicleJsonMapper.VHAL_RHR_PULSE_COUNTER_PROPERTY_ID,
        "VHAL_FUEL_ROLLING_COUNTER_PROPERTY_ID" to FirmwareVehicleJsonMapper.VHAL_FUEL_ROLLING_COUNTER_PROPERTY_ID,
        "VHAL_AVERAGE_FUEL_CONSUME_PROPERTY_ID" to FirmwareVehicleJsonMapper.VHAL_AVERAGE_FUEL_CONSUME_PROPERTY_ID,
        "VHAL_MAINTENANCE_TIPS_PROPERTY_ID" to FirmwareVehicleJsonMapper.VHAL_MAINTENANCE_TIPS_PROPERTY_ID,
        "VHAL_DISTANCE_TO_EMPTY_KM_PROPERTY_ID" to FirmwareVehicleJsonMapper.VHAL_DISTANCE_TO_EMPTY_KM_PROPERTY_ID,
        "VHAL_PM25_INDENSITY_PROPERTY_ID" to FirmwareVehicleJsonMapper.VHAL_PM25_INDENSITY_PROPERTY_ID,
        "VHAL_PM25_OUTDENSITY_PROPERTY_ID" to FirmwareVehicleJsonMapper.VHAL_PM25_OUTDENSITY_PROPERTY_ID,
        "VHAL_EXTERNAL_TEMPERATURE_RAW_PROPERTY_ID" to FirmwareVehicleJsonMapper.VHAL_EXTERNAL_TEMPERATURE_RAW_PROPERTY_ID,
        "VHAL_LF_TYRE_PRESSURE" to FirmwareVehicleJsonMapper.VHAL_LF_TYRE_PRESSURE,
        "VHAL_RF_TYRE_PRESSURE" to FirmwareVehicleJsonMapper.VHAL_RF_TYRE_PRESSURE,
        "VHAL_LR_TYRE_PRESSURE" to FirmwareVehicleJsonMapper.VHAL_LR_TYRE_PRESSURE,
        "VHAL_RR_TYRE_PRESSURE" to FirmwareVehicleJsonMapper.VHAL_RR_TYRE_PRESSURE,
        "VHAL_LF_TYRE_TEMPERATURE" to FirmwareVehicleJsonMapper.VHAL_LF_TYRE_TEMPERATURE,
        "VHAL_RF_TYRE_TEMPERATURE" to FirmwareVehicleJsonMapper.VHAL_RF_TYRE_TEMPERATURE,
        "VHAL_LR_TYRE_TEMPERATURE" to FirmwareVehicleJsonMapper.VHAL_LR_TYRE_TEMPERATURE,
        "VHAL_RR_TYRE_TEMPERATURE" to FirmwareVehicleJsonMapper.VHAL_RR_TYRE_TEMPERATURE,
        "VHAL_SLA_SPEED_LIMIT_RAW" to FirmwareVehicleJsonMapper.VHAL_SLA_SPEED_LIMIT_RAW,
        "VHAL_SLA_ON_OFF_STATUS" to FirmwareVehicleJsonMapper.VHAL_SLA_ON_OFF_STATUS,
        "VHAL_SLA_STATE" to FirmwareVehicleJsonMapper.VHAL_SLA_STATE,
        "VHAL_SLA_ON_OFF_REQ" to FirmwareVehicleJsonMapper.VHAL_SLA_ON_OFF_REQ,
        "VHAL_FRM_ACC_MODE" to FirmwareVehicleJsonMapper.VHAL_FRM_ACC_MODE,
        "VHAL_FRM_V_SET_DIS" to FirmwareVehicleJsonMapper.VHAL_FRM_V_SET_DIS,
        "VHAL_FRM_DX_TAR_OBJ" to FirmwareVehicleJsonMapper.VHAL_FRM_DX_TAR_OBJ,
        "VHAL_FRM_OBJ_VALID" to FirmwareVehicleJsonMapper.VHAL_FRM_OBJ_VALID,
        "VHAL_EMS_CRUISE_CONTROL_STATUS" to FirmwareVehicleJsonMapper.VHAL_EMS_CRUISE_CONTROL_STATUS,
        "VHAL_EMS_GAS_PEDAL_POSITION" to FirmwareVehicleJsonMapper.VHAL_EMS_GAS_PEDAL_POSITION,
        "VHAL_EMS_GAS_PEDAL_POSITION_INVALID" to FirmwareVehicleJsonMapper.VHAL_EMS_GAS_PEDAL_POSITION_INVALID,
        "VHAL_CEM_BRAKE_PEDAL_STS" to FirmwareVehicleJsonMapper.VHAL_CEM_BRAKE_PEDAL_STS,
        "VHAL_CEM_WIPER_STS" to FirmwareVehicleJsonMapper.VHAL_CEM_WIPER_STS,
        "VHAL_CEM_RAIN_DETECTED" to FirmwareVehicleJsonMapper.VHAL_CEM_RAIN_DETECTED,
        "VHAL_CEM_HIGH_BEAM_STS" to FirmwareVehicleJsonMapper.VHAL_CEM_HIGH_BEAM_STS,
        "VHAL_ICM_EPB_WARNING_LAMP_STS" to FirmwareVehicleJsonMapper.VHAL_ICM_EPB_WARNING_LAMP_STS,
        "VHAL_ICM_ENGINE_OIL_PRESSURE" to FirmwareVehicleJsonMapper.VHAL_ICM_ENGINE_OIL_PRESSURE,
        "VHAL_ICM_BRAKE_FLUID_LEVEL" to FirmwareVehicleJsonMapper.VHAL_ICM_BRAKE_FLUID_LEVEL,
        "VHAL_GSM_GEAR_SHIFT_POS" to FirmwareVehicleJsonMapper.VHAL_GSM_GEAR_SHIFT_POS,
        "VHAL_EMS_TARGET_GEAR_POSITION" to FirmwareVehicleJsonMapper.VHAL_EMS_TARGET_GEAR_POSITION,
        "VHAL_CEM2_DRIVER_DOOR_STS" to FirmwareVehicleJsonMapper.VHAL_CEM2_DRIVER_DOOR_STS,
        "VHAL_CEM2_PSNGR_DOOR_STS" to FirmwareVehicleJsonMapper.VHAL_CEM2_PSNGR_DOOR_STS,
        "VHAL_CEM2_LHR_DOOR_STS" to FirmwareVehicleJsonMapper.VHAL_CEM2_LHR_DOOR_STS,
        "VHAL_CEM2_RHR_DOOR_STS" to FirmwareVehicleJsonMapper.VHAL_CEM2_RHR_DOOR_STS,
        "VHAL_CEM2_HOOD_STS" to FirmwareVehicleJsonMapper.VHAL_CEM2_HOOD_STS,
        "VHAL_ICM1_DRIVER_SEAT_BELT_WARNING" to FirmwareVehicleJsonMapper.VHAL_ICM1_DRIVER_SEAT_BELT_WARNING,
        "VHAL_ICM1_PASSENGER_SEAT_BELT_WARNING" to FirmwareVehicleJsonMapper.VHAL_ICM1_PASSENGER_SEAT_BELT_WARNING,
        "VHAL_MFS_CRUISE_CONTROL" to FirmwareVehicleJsonMapper.VHAL_MFS_CRUISE_CONTROL,
        "VHAL_MFS_CANCEL" to FirmwareVehicleJsonMapper.VHAL_MFS_CANCEL,
        "VHAL_MFS_RES_PLUS" to FirmwareVehicleJsonMapper.VHAL_MFS_RES_PLUS,
        "VHAL_MFS_SET_MINUS" to FirmwareVehicleJsonMapper.VHAL_MFS_SET_MINUS,
    )

    /** Empty [query] returns [params] unchanged (no HU-mode filtering). */
    fun filterParams(params: List<ExpertRawCanParam>, query: String): List<ExpertRawCanParam> {
        val q = query.trim()
        if (q.isEmpty()) return params
        return params.filter { param ->
            param.name.contains(q, ignoreCase = true) ||
                param.mbCanId.toString().contains(q) ||
                param.vhalReadId?.toString()?.contains(q) == true ||
                param.vhalWriteId?.toString()?.contains(q) == true
        }
    }

    fun formatIdsSummary(param: ExpertRawCanParam, mode: HeadUnitCanModeLabel): String {
        if (param.bus == ExpertRawCanBus.VhalDirect) {
            return when (mode) {
                HeadUnitCanModeLabel.Android9MbCan -> "vhal=${param.mbCanId} (A10 only)"
                HeadUnitCanModeLabel.Android10Vhal -> "vhal=${param.mbCanId}"
            }
        }
        return when (mode) {
            HeadUnitCanModeLabel.Android9MbCan ->
                "mbCAN ${param.bus.name.lowercase()} id=${param.mbCanId}"
            HeadUnitCanModeLabel.Android10Vhal -> {
                val read = param.vhalReadId?.toString() ?: "—"
                val write = param.vhalWriteId?.toString() ?: "—"
                if (param.readWriteDiffer || param.vhalReadId != param.vhalWriteId) {
                    "logical=${param.mbCanId} read=$read write=$write"
                } else {
                    "logical=${param.mbCanId} vhal=${param.vhalReadId ?: param.vhalWriteId ?: "—"}"
                }
            }
        }
    }

    /**
     * Optional human hint when a domain helper already exists; never invents new decoders.
     * A9: mbCAN [ToggleBinary] off/on. A10: [VhalBinaryToggleCodec.decodeReadState] when known.
     */
    fun optionalDecodeHint(
        param: ExpertRawCanParam,
        raw: Int,
        mode: HeadUnitCanModeLabel,
    ): String? {
        if (param.bus != ExpertRawCanBus.Vehicle) return null
        val policy = MbCanCommandRegistry.get(param.mbCanId)?.policy as? MbCanCommandPolicy.ToggleBinary
        if (mode == HeadUnitCanModeLabel.Android9MbCan && policy != null) {
            return when (raw) {
                policy.offValue -> "Off(${policy.offValue})"
                policy.onValue -> "On(${policy.onValue})"
                else -> null
            }
        }
        if (mode == HeadUnitCanModeLabel.Android10Vhal &&
            VhalBinaryToggleCodec.isVhalBinaryToggleProperty(param.mbCanId)
        ) {
            VhalBinaryToggleCodec.decodeReadState(param.mbCanId, raw)?.let { state ->
                return when (state) {
                    MbCanBinaryState.On -> "On"
                    MbCanBinaryState.Off -> "Off"
                    MbCanBinaryState.Unknown -> "Unknown"
                    is MbCanBinaryState.Unavailable -> "Unavailable"
                }
            }
            // Fall back to comparing against known VHAL write encodings for on/off.
            val onEnc = VhalBinaryToggleCodec.encodeWriteValue(param.mbCanId, true)
            val offEnc = VhalBinaryToggleCodec.encodeWriteValue(param.mbCanId, false)
            return when (raw) {
                onEnc -> "On($raw)"
                offEnc -> "Off($raw)"
                else -> null
            }
        }
        return null
    }

    fun logGetAttempt(
        param: ExpertRawCanParam,
        result: ExpertRawGetResult,
        modeLabel: String,
    ) {
        val prop = propLabel(param)
        val detail =
            "GET $prop mode=$modeLabel effective=${result.effectivePropertyId} " +
                "raw=${result.rawValue} → ${if (result.success) "ok" else "fail"} ${result.message}"
        MbCanDiagnostics.log("DEBUG", LOG_TAG, detail)
        HuCanMarkLog.markUi("expertGet $detail")
    }

    fun logSetAttempt(
        param: ExpertRawCanParam,
        value: Int,
        result: ExpertRawSetResult,
        modeLabel: String,
    ) {
        val prop = propLabel(param)
        val detail =
            "SET $prop=$value mode=$modeLabel effective=${result.effectivePropertyId} " +
                "→ ${if (result.success) "ok" else "fail"} ${result.message}"
        MbCanDiagnostics.log("DEBUG", LOG_TAG, detail)
        HuCanMarkLog.markUi("expertSet $detail")
    }

    private fun propLabel(param: ExpertRawCanParam): String =
        when (param.bus) {
            ExpertRawCanBus.Vehicle -> HuCanMarkLog.vehicleProp(param.mbCanId)
            ExpertRawCanBus.Audio -> HuCanMarkLog.audioProp(param.mbCanId)
            ExpertRawCanBus.VhalDirect -> "${param.name}(${param.mbCanId})"
        }
}

/** Avoid importing [vad.dashing.tbox.HeadUnitCanMode] into catalog unit tests that only need labels. */
enum class HeadUnitCanModeLabel {
    Android9MbCan,
    Android10Vhal,
}
