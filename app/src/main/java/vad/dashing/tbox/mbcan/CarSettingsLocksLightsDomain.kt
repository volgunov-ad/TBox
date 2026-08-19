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
    /** A10 feedback 0/1/2 corresponds to stock UI values 1/2/3. */
    fun decodeRemoteLockFeedbackVhal(raw: Int): Int? = (raw + 1).takeIf { it in 1..3 }

    /**
     * Stock A10 writes RemoteLockFeedback as 1/2/3 (same as mbCAN UI values).
     * Read echo may still arrive as 0/1/2 on the status id — see [decodeRemoteLockFeedbackVhal].
     */
    fun encodeRemoteLockFeedbackVhal(uiValue: Int): Int? = uiValue.takeIf { it in 1..3 }

    /** A10 feedback 0..3 is inverse of UI levels 1..4. */
    fun decodeLowBeamHeightVhal(raw: Int): Int? = (4 - raw).takeIf { it in 1..4 }
    fun encodeLowBeamHeightVhal(uiLevel: Int): Int? = (5 - uiLevel).takeIf { uiLevel in 1..4 }

    /** A10 feedback is zero-based; writes use the shared one-based UI value. */
    fun decodeTurnFlashCountVhal(raw: Int): Int? = (raw + 1).takeIf { it in 1..3 }
}
