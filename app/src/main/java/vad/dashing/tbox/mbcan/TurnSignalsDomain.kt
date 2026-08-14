package vad.dashing.tbox.mbcan

/**
 * Turn indicators + hazard for HU backends (DR / geo consumers).
 *
 * **A9 mbCAN** (`MBCanVehicleTurnLight` / `eMBCAN_VEHICLE_TURNLIGHT`): stock
 * `MB_AIService.AutoMapTransfer` treats raw **2** as lamp/function active.
 * Both left and right **2** ⇒ hazard.
 *
 * **A10 VHAL**: prefer `DirectionIndLeft/Right` (stable while stalk engaged) over
 * blinking `LH/RHTurnlightSts`; hazard from `R_0400_CEM_1_HazardLightSW`.
 * CEM 1-bit light statuses: **1** = on, **0** = off.
 */
data class TurnSignalsState(
    val leftActive: Boolean? = null,
    val rightActive: Boolean? = null,
    val hazardActive: Boolean? = null,
)

enum class TurnSignalSide {
    Left,
    Right,
    Hazard,
}

object TurnSignalsDomain {
    /** mbCAN turn-light byte: active when raw == 2 (stock AutoMapTransfer). */
    fun decodeMbCanTurnLightActive(raw: Int): Boolean? = when (raw) {
        2 -> true
        // Observed idle / off encodings in OEM payloads.
        0, 1 -> false
        else -> null
    }

    /** CEM / VHAL 1-bit light or switch status. */
    fun decodeCemBinaryActive(raw: Int): Boolean? = when (raw) {
        1 -> true
        0 -> false
        else -> null
    }

    /**
     * Hazard from paired turn-light actives (A9 TURNLIGHT path).
     * Both true ⇒ hazard on; either known false with the other not both-true ⇒ hazard off
     * when both sides are known.
     */
    fun hazardFromTurnLightPair(leftActive: Boolean?, rightActive: Boolean?): Boolean? {
        if (leftActive == true && rightActive == true) return true
        if (leftActive != null && rightActive != null) return false
        return null
    }

    /**
     * Effective DR hint: hazard wins; otherwise a single-side turn.
     * Returns null when unknown or both sides on without hazard flag.
     */
    fun effectiveSide(state: TurnSignalsState): TurnSignalSide? {
        if (state.hazardActive == true) return TurnSignalSide.Hazard
        val left = state.leftActive == true
        val right = state.rightActive == true
        return when {
            left && !right -> TurnSignalSide.Left
            right && !left -> TurnSignalSide.Right
            else -> null
        }
    }

    /** Matcher hint: left/right only. Hazard and unknown are not a turn. */
    fun forkHintSide(state: TurnSignalsState): TurnSignalSide? {
        val side = effectiveSide(state) ?: return null
        return side.takeIf { it == TurnSignalSide.Left || it == TurnSignalSide.Right }
    }

    fun merge(
        current: TurnSignalsState,
        leftActive: Boolean? = current.leftActive,
        rightActive: Boolean? = current.rightActive,
        hazardActive: Boolean? = current.hazardActive,
    ): TurnSignalsState = TurnSignalsState(
        leftActive = leftActive,
        rightActive = rightActive,
        hazardActive = hazardActive,
    )

    /** Build A9 state from TURNLIGHT left/right raw bytes. */
    fun fromMbCanTurnLightRaw(leftRaw: Int, rightRaw: Int): TurnSignalsState {
        val left = decodeMbCanTurnLightActive(leftRaw)
        val right = decodeMbCanTurnLightActive(rightRaw)
        return TurnSignalsState(
            leftActive = left,
            rightActive = right,
            hazardActive = hazardFromTurnLightPair(left, right),
        )
    }
}
