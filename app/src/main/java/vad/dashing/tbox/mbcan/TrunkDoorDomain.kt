package vad.dashing.tbox.mbcan

import androidx.compose.ui.graphics.Color

/**
 * Trunk / power liftgate display state.
 *
 * Movement: BCM / PLG `RearDoorMoveDir` (0 = closing, 1 = opening, 2 = stopped).
 *
 * Open/closed when stopped:
 * - A9 mbCAN `nTrunkSts`: 1 = closed, 2 = open (observed on Jetour Dashing HU).
 * - A10 VHAL [R_0402_PLG_1_RearDoorStatus]: 0 = closed, 1 = open ([CarCommon3]).
 */
data class TrunkDoorDisplayState(
    val isOpen: Boolean? = null,
    val moveDir: Int? = null,
) {
    val movement: TrunkMovement
        get() = TrunkDoorDomain.movementFromMoveDir(moveDir)

    val isMoving: Boolean
        get() = movement != TrunkMovement.Stopped

    companion object {
        val Unknown = TrunkDoorDisplayState(isOpen = null, moveDir = null)
    }
}

enum class TrunkMovement {
    Stopped,
    Opening,
    Closing,
}

/** Resolved icon tint for [DashboardTrunkDoorWidgetItem]. */
sealed class TrunkIconTint {
    data class Solid(val color: Color) : TrunkIconTint()
    data class Pulsing(val from: Color, val to: Color) : TrunkIconTint()
}

object TrunkDoorDomain {
    /** Stock [RearDoorView]: 0 = down/closing, 1 = up/opening, 2 = stopped. */
    fun isMoveDirActive(moveDir: Int?): Boolean = moveDir == 0 || moveDir == 1

    fun movementFromMoveDir(moveDir: Int?): TrunkMovement = when (moveDir) {
        0 -> TrunkMovement.Closing
        1 -> TrunkMovement.Opening
        else -> TrunkMovement.Stopped
    }

    /** A10 [R_0402_PLG_1_RearDoorStatus]: 0 = closed, 1 = open. */
    fun decodeBinaryOpenVhal(raw: Int?): Boolean? = when (raw) {
        0 -> false
        1 -> true
        else -> null
    }

    /** A9 BCM `nTrunkSts` on Dashing: 1 = closed, 2 = open. */
    fun decodeBinaryOpenMbCan(raw: Int?): Boolean? = when (raw) {
        1 -> false
        2 -> true
        0 -> false
        else -> null
    }

    fun buildDisplayState(isOpen: Boolean?, moveDir: Int?): TrunkDoorDisplayState =
        TrunkDoorDisplayState(isOpen = isOpen, moveDir = moveDir)

    fun shouldPulseStop(state: TrunkDoorDisplayState): Boolean = state.isMoving

    /** Double-tap when stopped: close if open. */
    fun shouldPulseClose(state: TrunkDoorDisplayState): Boolean =
        !state.isMoving && state.isOpen == true

    /** Double-tap when stopped and closed (or unknown position). */
    fun shouldPulseOpen(state: TrunkDoorDisplayState): Boolean =
        !state.isMoving && state.isOpen != true

    fun resolveIconTint(
        state: TrunkDoorDisplayState,
        idleColor: Color,
        openColor: Color,
        openingAccentColor: Color,
    ): TrunkIconTint = when (state.movement) {
        TrunkMovement.Opening -> TrunkIconTint.Pulsing(
            from = openingAccentColor,
            to = openColor,
        )
        TrunkMovement.Closing -> TrunkIconTint.Pulsing(
            from = openColor,
            to = idleColor,
        )
        TrunkMovement.Stopped -> TrunkIconTint.Solid(
            color = if (state.isOpen == true) openColor else idleColor,
        )
    }
}
