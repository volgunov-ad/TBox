package vad.dashing.tbox.mbcan

/** Normalized ADAS values shared by the Android 9 mbCAN and Android 10 VHAL backends. */
enum class FcwSensitivity {
    Far, Standard, Near
}

enum class LdwSensitivity {
    High, Low
}

/**
 * ACC following distance. Stock has three gaps, not four.
 *
 * Factory `RRM_8_TimeGapSet1Req` (`eTIMEGAPSET1REQ`): **0** Not_Active, **1** tauGap_0,
 * **2** tauGap_1, **3** tauGap_2, **4…7** reserved.
 * Cluster `FRM_3_TimeGapSet_ICM` (and A10 DVD echo) uses the same names at **0 / 1 / 2**;
 * **3** is no icon. Stock launcher and TTG draw **0** as two bars (medium), **1** as three
 * bars (far), **2** as one bar (near).
 *
 * Journal `tbox_app_log_20261010_115553`: the user changed the gap with ACC active.
 * Request **1 / 2 / 3** landed on status **0 / 1 / 2**. Cfg **95** read **4** together
 * with status **3** while ACC was not showing the gap (ACCMode 0 at 11:57:38, and
 * again at 12:01:13 as ACC left). **4** stays "not a gap".
 */
enum class AccTimeGap {
    /** tauGap_2 — closest. Status **2**, request **3**. */
    Near,
    /** tauGap_0 — middle. Status **0**, request **1**. */
    Medium,
    /** tauGap_1 — farthest. Status **1**, request **2**. */
    Far,
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

    /**
     * Stock A10 `R_0B00_FCM_2_LDWLKA_Sensitivityfeedback` (289415707) via
     * `ConvertValue.convertValue`: raw **1** → UI High, raw **0** → UI Low
     * (titles2 = {High, Low}) — same polarity as the A10 write
     * `T_0B01_IHU_8_LDW_LKA_SensitivityReq` (High=1, Low=0) and as mbCAN.
     */
    fun decodeLdwSensitivityVhal(raw: Int): LdwSensitivity? = when (raw) {
        1 -> LdwSensitivity.High
        0 -> LdwSensitivity.Low
        else -> null
    }

    fun encodeLdwSensitivityVhal(value: LdwSensitivity): Int = when (value) {
        LdwSensitivity.High -> 1
        LdwSensitivity.Low -> 0
    }

    /** Cluster / DVD status: **0** medium, **1** far, **2** near. **3** is no icon. */
    fun decodeAccTimeGapStatus(raw: Int): AccTimeGap? = when (raw) {
        0 -> AccTimeGap.Medium
        1 -> AccTimeGap.Far
        2 -> AccTimeGap.Near
        else -> null
    }

    /**
     * `eTIMEGAPSET1REQ` / VHAL `TimeGapSet1Req`: **1** medium, **2** far, **3** near.
     * **0** is Not_Active and is not a gap.
     */
    fun decodeAccTimeGapRequest(raw: Int): AccTimeGap? = when (raw) {
        1 -> AccTimeGap.Medium
        2 -> AccTimeGap.Far
        3 -> AccTimeGap.Near
        else -> null
    }

    fun encodeAccTimeGapRequest(value: AccTimeGap): Int = when (value) {
        AccTimeGap.Medium -> 1
        AccTimeGap.Far -> 2
        AccTimeGap.Near -> 3
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
