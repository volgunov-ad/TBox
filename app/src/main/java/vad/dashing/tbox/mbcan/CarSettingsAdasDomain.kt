package vad.dashing.tbox.mbcan

/** Normalized ADAS values shared by the Android 9 mbCAN and Android 10 VHAL backends. */
enum class FcwSensitivity {
    Far, Standard, Near
}

enum class LdwSensitivity {
    High, Low
}

object CarSettingsAdasDomain {
    /**
     * Stock A9 (`array_fcw` Close/Standard/Far + `array_both_fcw_value`) and A10
     * (`car_assist4_2_*` Far/Standard/Near) share the same CAN values:
     * **3** Far, **1** Standard, **2** Near.
     */
    fun decodeFcwSensitivityMbCan(raw: Int): FcwSensitivity? = decodeFcwSensitivity(raw)

    fun encodeFcwSensitivityMbCan(value: FcwSensitivity): Int = encodeFcwSensitivity(value)

    fun decodeFcwSensitivityVhal(raw: Int): FcwSensitivity? = decodeFcwSensitivity(raw)

    fun encodeFcwSensitivityVhal(value: FcwSensitivity): Int = encodeFcwSensitivity(value)

    private fun decodeFcwSensitivity(raw: Int): FcwSensitivity? = when (raw) {
        3 -> FcwSensitivity.Far
        1 -> FcwSensitivity.Standard
        2 -> FcwSensitivity.Near
        else -> null
    }

    private fun encodeFcwSensitivity(value: FcwSensitivity): Int = when (value) {
        FcwSensitivity.Far -> 3
        FcwSensitivity.Standard -> 1
        FcwSensitivity.Near -> 2
    }

    fun decodeLdwSensitivityMbCan(raw: Int): LdwSensitivity? = when (raw) {
        1 -> LdwSensitivity.High
        0 -> LdwSensitivity.Low
        else -> null
    }

    /** Stock A10 read conversion is inverted; write values retain the UI polarity. */
    fun decodeLdwSensitivityVhal(raw: Int): LdwSensitivity? = when (raw) {
        0 -> LdwSensitivity.High
        1 -> LdwSensitivity.Low
        else -> null
    }

    fun encodeLdwSensitivityVhal(value: LdwSensitivity): Int = when (value) {
        LdwSensitivity.High -> 1
        LdwSensitivity.Low -> 0
    }
}
