package vad.dashing.tbox.mbcan

/** Normalized values used by Car Settings across the mbCAN and VHAL backends. */
enum class FollowMeHomeMode(val mbCanWriteValue: Int, val vhalWriteValue: Int) {
    Sec30(mbCanWriteValue = 30, vhalWriteValue = 1),
    Sec60(mbCanWriteValue = 60, vhalWriteValue = 2),
    Off(mbCanWriteValue = 3, vhalWriteValue = 3);

    companion object {
        fun fromMbCanRaw(raw: Int): FollowMeHomeMode? = entries.firstOrNull { it.mbCanWriteValue == raw }
        fun fromVhalRaw(raw: Int): FollowMeHomeMode? = entries.firstOrNull { it.vhalWriteValue == raw }
    }
}

object CarSettingsLocksLightsDomain {
    /**
     * Shared Car Settings / mbCAN values from stock A9
     * (`display_carLock_CapsuleViewTime` Flash/Beep/Flash+beep):
     * **1** light, **2** horn, **3** light+horn.
     */
    const val REMOTE_LOCK_FEEDBACK_LIGHT = 1
    const val REMOTE_LOCK_FEEDBACK_HORN = 2
    const val REMOTE_LOCK_FEEDBACK_LIGHT_HORN = 3

    /**
     * Stock A10 status `R_0400_CEM_2_RemoteLockFeedbackSts` is 0/1/2
     * (CarSet1: 0=light+horn, 1=light, 2=horn). Normalize to mbCAN 1/2/3.
     */
    fun decodeRemoteLockFeedbackVhal(raw: Int): Int? = when (raw) {
        0 -> REMOTE_LOCK_FEEDBACK_LIGHT_HORN
        1 -> REMOTE_LOCK_FEEDBACK_LIGHT
        2 -> REMOTE_LOCK_FEEDBACK_HORN
        else -> null
    }

    /**
     * Stock A10 writes `T_0401_IHU_1_DVD_SET_RemoteLockFeedback`
     * as **2** light / **3** horn / **1** light+horn.
     */
    fun encodeRemoteLockFeedbackVhal(mbCanValue: Int): Int? = when (mbCanValue) {
        REMOTE_LOCK_FEEDBACK_LIGHT -> 2
        REMOTE_LOCK_FEEDBACK_HORN -> 3
        REMOTE_LOCK_FEEDBACK_LIGHT_HORN -> 1
        else -> null
    }

    /** A10 feedback 0..3 is inverse of UI levels 1..4. */
    fun decodeLowBeamHeightVhal(raw: Int): Int? = (4 - raw).takeIf { it in 1..4 }
    fun encodeLowBeamHeightVhal(uiLevel: Int): Int? = (5 - uiLevel).takeIf { uiLevel in 1..4 }

    /** A10 status `R_0404_CEM_2_BlankingnumberSts` is 0/1/2; writes use 1/2/3. */
    fun decodeTurnFlashCountVhal(raw: Int): Int? = (raw + 1).takeIf { it in 1..3 }

    /**
     * Stock A9 `array_get_the_flash` / A10 `car_out_flicker_light_*`:
     * CAN **1/2/3** → **3/5/7** flashes.
     */
    fun turnFlashCountBlinks(raw: Int): Int? = when (raw) {
        1 -> 3
        2 -> 5
        3 -> 7
        else -> null
    }
}
