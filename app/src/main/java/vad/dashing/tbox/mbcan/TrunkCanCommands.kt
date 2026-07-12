package vad.dashing.tbox.mbcan

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Toggle liftgate when stopped — [MbCanKnownVehiclePropertyId.TRUNK_PLG_CONTROL] pulse 1.
 * Stock A9 [RearDoorView] onLongClick and A10 [CarCommon3] long-press both use pulse 1
 * regardless of open/closed position.
 */
suspend fun UniversalCanRepository.trunkPulseToggle(): MbCanCommandResult =
    execute(MbCanCommand.TrunkPulse(1))

/** Stop movement while opening/closing — PLG pulse 2 (stock [RearDoorView] onClick). */
suspend fun UniversalCanRepository.trunkPulseStop(): MbCanCommandResult =
    execute(MbCanCommand.TrunkPulse(2))

/** Double-tap when the liftgate is stopped (stock PLG pulse 1). */
suspend fun UniversalCanRepository.trunkPulseFromStopped(state: TrunkDoorDisplayState): MbCanCommandResult =
    if (TrunkDoorDomain.shouldPulseToggleWhenStopped(state)) {
        trunkPulseToggle()
    } else {
        trunkPulseStop()
    }

fun UniversalCanRepository.launchTrunkCommand(
    scope: CoroutineScope,
    block: suspend UniversalCanRepository.() -> MbCanCommandResult,
) {
    scope.launch {
        block(UniversalCanRepository)
    }
}
