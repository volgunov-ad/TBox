package vad.dashing.tbox.mbcan

/** Normalized ADAS values shared by the Android 9 mbCAN and Android 10 VHAL backends. */
enum class FcwSensitivity {
    Far, Standard, Near
}

enum class LdwSensitivity {
    High, Low
}

object CarSettingsAdasDomain {
    fun decodeFcwSensitivityMbCan(raw: Int): FcwSensitivity? = when (raw) {
        2 -> FcwSensitivity.Far
        1 -> FcwSensitivity.Standard
        3 -> FcwSensitivity.Near
        else -> null
    }

    fun encodeFcwSensitivityMbCan(value: FcwSensitivity): Int = when (value) {
        FcwSensitivity.Far -> 2
        FcwSensitivity.Standard -> 1
        FcwSensitivity.Near -> 3
    }

    fun decodeFcwSensitivityVhal(raw: Int): FcwSensitivity? = when (raw) {
        3 -> FcwSensitivity.Far
        1 -> FcwSensitivity.Standard
        2 -> FcwSensitivity.Near
        else -> null
    }

    fun encodeFcwSensitivityVhal(value: FcwSensitivity): Int = when (value) {
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
