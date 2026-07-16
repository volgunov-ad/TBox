package vad.dashing.tbox.mbcan

/** SLA / TSR speed-limit sign recognition and vehicle speed limiter encoding. */
object SlaSpeedLimitDomain {
    const val SPEED_LIMITER_KMH_MIN = 0
    const val SPEED_LIMITER_KMH_MAX = 150
    const val SPEED_LIMITER_KMH_DEFAULT = 60
    const val SPEED_LIMITER_KMH_STEP = 5

    const val SLA_SWITCH_OFF = 1
    const val SLA_SWITCH_ON = 2

    const val SPEED_LIMITER_SWITCH_OFF = 1
    const val SPEED_LIMITER_SWITCH_ON = 2

    /**
     * Recognized sign from [FCM_2_SLASpdlimit] / VHAL `R_0B00_FCM_2_SLASpdlimit`.
     * Formula: `(raw - 1) * 5` km/h; raw <= 1 means no sign.
     */
    fun decodeRecognizedSpeedKmh(slaLimitRaw: Int): Int? {
        if (slaLimitRaw <= 1) return null
        return (slaLimitRaw - 1) * 5
    }

    /** mbCAN / write: 1 off, 2 on. */
    fun decodeSlaOnOffRaw(raw: Int): MbCanBinaryState = when (raw) {
        SLA_SWITCH_ON -> MbCanBinaryState.On
        SLA_SWITCH_OFF -> MbCanBinaryState.Off
        else -> MbCanBinaryState.Unknown
    }

    /** VHAL [R_0B00_FCM_2_SLAOnOffsts] read: 1 on, 0 off. */
    fun decodeSlaOnOffVhalRaw(raw: Int): MbCanBinaryState = when (raw) {
        1 -> MbCanBinaryState.On
        0 -> MbCanBinaryState.Off
        else -> MbCanBinaryState.Unknown
    }

    fun decodeSpeedLimiterSwitchRaw(raw: Int): MbCanBinaryState = when (raw) {
        SPEED_LIMITER_SWITCH_ON -> MbCanBinaryState.On
        SPEED_LIMITER_SWITCH_OFF -> MbCanBinaryState.Off
        else -> MbCanBinaryState.Unknown
    }

    fun encodeSlaSwitchOn(on: Boolean): Int = if (on) SLA_SWITCH_ON else SLA_SWITCH_OFF

    fun encodeSpeedLimiterSwitchOn(on: Boolean): Int =
        if (on) SPEED_LIMITER_SWITCH_ON else SPEED_LIMITER_SWITCH_OFF

    fun clampLimiterTargetKmh(value: Int): Int {
        val coerced = value.coerceIn(SPEED_LIMITER_KMH_MIN, SPEED_LIMITER_KMH_MAX)
        if (coerced == 0) return 0
        val rounded = ((coerced + SPEED_LIMITER_KMH_STEP / 2) / SPEED_LIMITER_KMH_STEP) * SPEED_LIMITER_KMH_STEP
        return rounded.coerceIn(SPEED_LIMITER_KMH_STEP, SPEED_LIMITER_KMH_MAX)
    }

    fun stepLimiterTargetKmh(current: Int, increase: Boolean): Int {
        val delta = if (increase) SPEED_LIMITER_KMH_STEP else -SPEED_LIMITER_KMH_STEP
        return clampLimiterTargetKmh(current + delta)
    }
}
