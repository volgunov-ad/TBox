package vad.dashing.tbox.mbcan

/** SLA / TSR speed-limit sign recognition and vehicle speed limiter encoding. */
object SlaSpeedLimitDomain {
    const val SPEED_LIMITER_KMH_MIN = 0
    const val SPEED_LIMITER_KMH_MAX = 150
    const val SPEED_LIMITER_KMH_DEFAULT = 60
    /** First write when CAN VALUESET has no data yet; further ± can go down to [SPEED_LIMITER_KMH_MIN]. */
    const val SPEED_LIMITER_KMH_BOOTSTRAP = 30
    const val SPEED_LIMITER_KMH_STEP = 5

    const val SLA_SWITCH_OFF = 1
    const val SLA_SWITCH_ON = 2

    const val SPEED_LIMITER_SWITCH_OFF = 1
    const val SPEED_LIMITER_SWITCH_ON = 2

    /**
     * [FCM_2_SLAState] as used by stock AdasCard (`MB_AIService`).
     * Values 1/2/3 are treated identically in stock UI (no separate labels found).
     */
    const val SLA_STATE_OFF = 0
    const val SLA_STATE_ACTIVE_1 = 1
    const val SLA_STATE_ACTIVE_2 = 2
    const val SLA_STATE_ACTIVE_3 = 3
    const val SLA_STATE_FAULT = 4

    /** LKA/FCM [FCM_2_SLAOnOffsts]: SLA feature enabled for sign display. */
    const val SLA_LKA_ON_OFF_ENABLED = 2

    /** [FCM_2_SLASpdlimit] raw: end-of-restriction (release) sign. */
    const val SLA_SPDLIMIT_END_OF_RESTRICTION = 1

    /** Max raw before stock AdasCard caps displayed km/h at 130. */
    private const val SLA_SPDLIMIT_RAW_CAP = 27

    /**
     * Recognized numeric limit from [FCM_2_SLASpdlimit] / VHAL `R_0B00_FCM_2_SLASpdlimit`.
     * Formula: `(raw - 1) * 5` km/h; raw ≤ 1 means no numeric limit (0 = none, 1 = end-of-restriction).
     */
    fun decodeRecognizedSpeedKmh(slaLimitRaw: Int): Int? {
        if (slaLimitRaw <= 1) return null
        if (slaLimitRaw > SLA_SPDLIMIT_RAW_CAP) return 130
        return (slaLimitRaw - 1) * 5
    }

    /**
     * Stock AdasCard display rules from [FCM_2_SLAOnOffsts], [FCM_2_SLAState], [FCM_2_SLASpdlimit].
     *
     * - OnOff ≠ 2, State = 0, or Spdlimit = 0 → inactive (dimmed dash)
     * - OnOff = 2, State ∈ {1,2,3}, Spdlimit = 1 → end-of-restriction sign
     * - OnOff = 2, State ∈ {1,2,3}, Spdlimit ≥ 2 → numeric limit
     * - State = 4 (fault) → inactive in our widget (stock shows fault icon)
     */
    fun resolveSlaSignUiState(
        slaOnOffRaw: Int?,
        slaStateRaw: Int?,
        slaLimitRaw: Int?,
    ): SlaSignUiState {
        val onOff = slaOnOffRaw ?: return SlaSignUiState.Inactive
        val state = slaStateRaw ?: return SlaSignUiState.Inactive
        val limit = slaLimitRaw ?: return SlaSignUiState.Inactive
        if (onOff != SLA_LKA_ON_OFF_ENABLED || state == SLA_STATE_OFF || limit == 0) {
            return SlaSignUiState.Inactive
        }
        if (state !in SLA_STATE_ACTIVE_1..SLA_STATE_ACTIVE_3) {
            return SlaSignUiState.Inactive
        }
        if (limit == SLA_SPDLIMIT_END_OF_RESTRICTION) {
            return SlaSignUiState.EndOfRestriction
        }
        val kmh = decodeRecognizedSpeedKmh(limit) ?: return SlaSignUiState.Inactive
        return SlaSignUiState.Limit(kmh)
    }

    /** mbCAN / write: 1 off, 2 on. */
    fun decodeSlaOnOffRaw(raw: Int): MbCanBinaryState = when (raw) {
        SLA_SWITCH_ON -> MbCanBinaryState.On
        SLA_SWITCH_OFF -> MbCanBinaryState.Off
        else -> MbCanBinaryState.Unknown
    }

    /** VHAL [R_0B00_FCM_2_SLAOnOffsts] read: stock-style selected when raw == 1. */
    fun decodeSlaOnOffVhalRaw(raw: Int): MbCanBinaryState =
        if (raw == 1) MbCanBinaryState.On else MbCanBinaryState.Off

    /** mbCAN / write-side: 1 off, 2 on. */
    fun decodeSpeedLimiterSwitchRaw(raw: Int): MbCanBinaryState = when (raw) {
        SPEED_LIMITER_SWITCH_ON -> MbCanBinaryState.On
        SPEED_LIMITER_SWITCH_OFF -> MbCanBinaryState.Off
        else -> MbCanBinaryState.Unknown
    }

    /** VHAL read: stock-style selected when raw == 1. */
    fun decodeSpeedLimiterSwitchVhalRaw(raw: Int): MbCanBinaryState =
        if (raw == 1) MbCanBinaryState.On else MbCanBinaryState.Off

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

    /**
     * Next widget ± target from live CAN VALUESET.
     * When [currentFromCan] is null (no data), returns [SPEED_LIMITER_KMH_BOOTSTRAP].
     */
    fun nextLimiterTargetFromCan(currentFromCan: Int?, increase: Boolean): Int {
        if (currentFromCan == null) return SPEED_LIMITER_KMH_BOOTSTRAP
        return stepLimiterTargetKmh(currentFromCan, increase)
    }

    /** Target to write when enabling limiter with no live VALUESET. */
    fun resolveLimiterTargetOrBootstrap(currentFromCan: Int?): Int =
        currentFromCan?.let(::clampLimiterTargetKmh) ?: SPEED_LIMITER_KMH_BOOTSTRAP
}

/** Dashboard SLA sign presentation (stock AdasCard-aligned). */
sealed class SlaSignUiState {
    /** Numeric speed-limit sign, full opacity. */
    data class Limit(val kmh: Int) : SlaSignUiState()

    /** Grey end-of-restriction sign (no center dash). */
    data object EndOfRestriction : SlaSignUiState()

    /** Dimmed red-ring sign with center dash. */
    data object Inactive : SlaSignUiState()
}
