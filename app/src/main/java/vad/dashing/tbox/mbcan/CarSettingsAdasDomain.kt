package vad.dashing.tbox.mbcan

/** Normalized ADAS values shared by the Android 9 mbCAN and Android 10 VHAL backends. */
enum class FcwSensitivity {
    Far, Standard, Near
}

enum class LdwSensitivity {
    High, Low
}

/**
 * ACC following-distance time-gap (`eTIMEGAPSET1REQ` / mbCAN **95**).
 * Raw **1…4** (typical closest→farthest). A9 HU log saw **1** with TJA on and **4** with TJA off.
 */
enum class AccTimeGap {
    Level1, Level2, Level3, Level4
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

    /** mbCAN / VHAL share raw **1…4** for [AccTimeGap] (A9 log: 1, 4). */
    fun decodeAccTimeGapMbCan(raw: Int): AccTimeGap? = decodeAccTimeGap(raw)

    fun encodeAccTimeGapMbCan(value: AccTimeGap): Int = encodeAccTimeGap(value)

    fun decodeAccTimeGapVhal(raw: Int): AccTimeGap? = decodeAccTimeGap(raw)

    fun encodeAccTimeGapVhal(value: AccTimeGap): Int = encodeAccTimeGap(value)

    private fun decodeAccTimeGap(raw: Int): AccTimeGap? = when (raw) {
        1 -> AccTimeGap.Level1
        2 -> AccTimeGap.Level2
        3 -> AccTimeGap.Level3
        4 -> AccTimeGap.Level4
        else -> null
    }

    private fun encodeAccTimeGap(value: AccTimeGap): Int = when (value) {
        AccTimeGap.Level1 -> 1
        AccTimeGap.Level2 -> 2
        AccTimeGap.Level3 -> 3
        AccTimeGap.Level4 -> 4
    }

    /**
     * LDW master switch (`eDVD_LDWSWITCH` / mbCAN **80**): **1** Off / **2** On.
     * A9 log co-moves with LAS **17** (LKA→80=1, LDW→80=2).
     */
    fun decodeLdwSwitchMbCan(raw: Int): MbCanBinaryState? = when (raw) {
        2 -> MbCanBinaryState.On
        1 -> MbCanBinaryState.Off
        else -> null
    }
}
