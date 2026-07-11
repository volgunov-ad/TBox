package vad.dashing.tbox.mbcan

/**
 * Trunk / power liftgate display state.
 *
 * Movement: BCM / PLG `RearDoorMoveDir` (0 = closing, 1 = opening, 2 = stopped) — same as stock
 * [com.mengbo.mbcanwidget.car.RearDoorView] and [CarCommon3].
 *
 * Open/closed when stopped:
 * - A9: BCM door push `nTrunkSts` (0 = closed, 1 = open), same encoding as Launcher `TrunkSt`.
 * - A10: VHAL [R_0402_PLG_1_RearDoorStatus] (0 = closed, 1 = open), same as [CarCommon3].
 */
data class TrunkDoorDisplayState(
    val isOpen: Boolean? = null,
    val isMoving: Boolean = false,
) {
    companion object {
        val Unknown = TrunkDoorDisplayState(isOpen = null, isMoving = false)
    }
}

object TrunkDoorDomain {
    /** Stock [RearDoorView]: 0 = down/closing, 1 = up/opening, 2 = stopped. */
    fun isMoveDirActive(moveDir: Int?): Boolean = moveDir == 0 || moveDir == 1

    /** Stock door/trunk status bytes: 0 = closed, 1 = open. */
    fun decodeBinaryOpen(raw: Int?): Boolean? = when (raw) {
        0 -> false
        1 -> true
        else -> null
    }

    fun buildDisplayState(isOpen: Boolean?, moveDir: Int?): TrunkDoorDisplayState =
        TrunkDoorDisplayState(
            isOpen = isOpen,
            isMoving = isMoveDirActive(moveDir),
        )

    fun iconUsesActiveColor(state: TrunkDoorDisplayState): Boolean =
        state.isMoving || state.isOpen == true

    fun shouldPulseStop(state: TrunkDoorDisplayState): Boolean = state.isMoving

    /** Double-tap when stopped: close if open. */
    fun shouldPulseClose(state: TrunkDoorDisplayState): Boolean =
        !state.isMoving && state.isOpen == true

    /** Double-tap when stopped and closed (or unknown position). */
    fun shouldPulseOpen(state: TrunkDoorDisplayState): Boolean =
        !state.isMoving && state.isOpen != true
}
