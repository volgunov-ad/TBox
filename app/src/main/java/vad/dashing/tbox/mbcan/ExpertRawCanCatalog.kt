package vad.dashing.tbox.mbcan

import vad.dashing.tbox.esp.HuCanMarkLog

/** Bus for raw expert Get/Set (A9 JNI channels / A10 mapped VHAL ids). */
enum class ExpertRawCanBus {
    Vehicle,
    Audio,
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
        return (vehicle + audio).sortedWith(
            compareBy<ExpertRawCanParam> { it.bus.ordinal }
                .thenBy { it.name },
        )
    }

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
        }
}

/** Avoid importing [vad.dashing.tbox.HeadUnitCanMode] into catalog unit tests that only need labels. */
enum class HeadUnitCanModeLabel {
    Android9MbCan,
    Android10Vhal,
}
