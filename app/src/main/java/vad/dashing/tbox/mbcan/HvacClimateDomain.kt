package vad.dashing.tbox.mbcan

/** Canonical blow modes use mbCAN integer ids (Android 9). */
enum class HvacBlowMode(val mbCanValue: Int) {
    Face(MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FACE),
    Foot(MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FOOT),
    FaceFoot(MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FACE_FOOT),
    Defrost(MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_DEFROST),
    DefrostFoot(MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_DEFROST_FOOT),
    ;

    companion object {
        val cycleOrder: List<HvacBlowMode> = listOf(
            Face,
            Foot,
            FaceFoot,
            DefrostFoot,
            Defrost,
        )

        fun fromMbCanRaw(raw: Int): HvacBlowMode? =
            entries.firstOrNull { it.mbCanValue == raw }

        fun fromVhalRaw(raw: Int): HvacBlowMode? =
            fromMbCanRaw(HvacClimateDomain.vhalBlowModeToMbCan(raw))

        fun nextInCycle(current: HvacBlowMode?): HvacBlowMode {
            if (current == null) return Face
            val index = cycleOrder.indexOf(current)
            if (index < 0) return Face
            return cycleOrder[(index + 1) % cycleOrder.size]
        }
    }
}

object HvacClimateDomain {
    const val TEMP_MB_CAN_MIN = 160
    const val TEMP_MB_CAN_MAX = 300
    const val TEMP_MB_CAN_STEP = 5
    const val TEMP_VHAL_MIN = 32
    const val TEMP_VHAL_MAX = 60
    const val FAN_SPEED_MIN = 0
    const val FAN_SPEED_MAX = 7
    const val TRUNK_PULSE_RESET_MS = 310L

    val HVAC_TEMP_MB_CAN_ALLOWED: Set<Int> =
        (TEMP_MB_CAN_MIN..TEMP_MB_CAN_MAX step TEMP_MB_CAN_STEP).toSet()

    fun mbCanTempRawToCelsius(raw: Int): Float? =
        raw.takeIf { it in TEMP_MB_CAN_MIN..TEMP_MB_CAN_MAX }?.div(10f)

    fun vhalTempRawToCelsius(raw: Int): Float? =
        raw.takeIf { it in TEMP_VHAL_MIN..TEMP_VHAL_MAX }?.div(2f)

    fun celsiusToMbCanTempRaw(celsius: Float): Int {
        val tenths = (celsius * 10f).toInt()
        val stepped = ((tenths - TEMP_MB_CAN_MIN) / TEMP_MB_CAN_STEP) * TEMP_MB_CAN_STEP + TEMP_MB_CAN_MIN
        return stepped.coerceIn(TEMP_MB_CAN_MIN, TEMP_MB_CAN_MAX)
    }

    fun celsiusToVhalTempRaw(celsius: Float): Int {
        val doubled = (celsius * 2f).toInt()
        return doubled.coerceIn(TEMP_VHAL_MIN, TEMP_VHAL_MAX)
    }

    fun mbCanTempRawToVhalWrite(mbCanRaw: Int): Int? =
        mbCanTempRawToCelsius(mbCanRaw)?.let(::celsiusToVhalTempRaw)

    fun adjustMbCanTempRaw(currentRaw: Int?, increase: Boolean): Int {
        val base = currentRaw?.takeIf { it in TEMP_MB_CAN_MIN..TEMP_MB_CAN_MAX } ?: TEMP_MB_CAN_MIN
        val delta = if (increase) TEMP_MB_CAN_STEP else -TEMP_MB_CAN_STEP
        return (base + delta).coerceIn(TEMP_MB_CAN_MIN, TEMP_MB_CAN_MAX)
    }

    fun adjustCelsius(current: Float?, increase: Boolean): Float {
        val base = current ?: (TEMP_MB_CAN_MIN / 10f)
        val delta = TEMP_MB_CAN_STEP / 10f
        val next = if (increase) base + delta else base - delta
        return next.coerceIn(TEMP_MB_CAN_MIN / 10f, TEMP_MB_CAN_MAX / 10f)
    }

    fun mbCanBlowModeToVhalWrite(mbCanValue: Int): Int? = when (mbCanValue) {
        MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FACE -> 0
        MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FOOT -> 2
        MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FACE_FOOT -> 1
        MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_DEFROST -> 4
        MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_DEFROST_FOOT -> 3
        else -> null
    }

    fun vhalBlowModeToMbCan(vhalValue: Int): Int = when (vhalValue) {
        0 -> MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FACE
        2 -> MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FOOT
        1 -> MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FACE_FOOT
        4 -> MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_DEFROST
        3 -> MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_DEFROST_FOOT
        else -> -1
    }

    /** mbCAN / write: 1 off, 2 on. */
    fun decodeHvacSyncMbCanRaw(raw: Int): MbCanBinaryState = when (raw) {
        2 -> MbCanBinaryState.On
        1 -> MbCanBinaryState.Off
        else -> MbCanBinaryState.Unknown
    }

    /** VHAL read: stock-style selected when raw == 1. */
    fun decodeHvacSyncVhalRaw(raw: Int): MbCanBinaryState =
        if (raw == 1) MbCanBinaryState.On else MbCanBinaryState.Off

    fun encodeHvacSyncMbCanWrite(targetOn: Boolean): Int = if (targetOn) 2 else 1

    fun encodeHvacSyncVhalWrite(targetOn: Boolean): Int = if (targetOn) 2 else 1

    /**
     * [R_0200_CEM_IPM_FrontOFFSts] / mbCAN status: 1 = front climate off, 2 = front climate on.
     * Mapped to [MbCanBinaryState.On] when the front section is off (widget should dim).
     */
    fun decodeHvacFrontOffMbCanRaw(raw: Int): MbCanBinaryState = when (raw) {
        1 -> MbCanBinaryState.On
        2 -> MbCanBinaryState.Off
        else -> MbCanBinaryState.Unknown
    }

    /**
     * VHAL [R_0200_CEM_IPM_FrontOFFSts] as in stock AirConditioning:
     * selected (front climate off) when raw == 0; otherwise not selected.
     */
    fun decodeHvacFrontOffVhalRaw(raw: Int): MbCanBinaryState =
        if (raw == 0) MbCanBinaryState.On else MbCanBinaryState.Off

    /** Write [T_0201_IHU_5_FrontOFF_Req]: 2 = climate on, 1 = climate off. */
    fun encodeHvacFrontOffMbCanWrite(targetClimateOn: Boolean): Int = if (targetClimateOn) 2 else 1

    fun formatCelsius(celsius: Float): String {
        val tenths = (celsius * 10f).toInt()
        return if (tenths % 10 == 0) {
            "${tenths / 10}"
        } else {
            String.format("%.1f", celsius)
        }
    }
}
