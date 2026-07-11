package vad.dashing.tbox.mbcan

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Close liftgate — [MbCanKnownVehiclePropertyId.TRUNK_PLG_CONTROL] pulse 1. */
suspend fun UniversalCanRepository.trunkPulseClose(): MbCanCommandResult =
    execute(MbCanCommand.TrunkPulse(1))

/**
 * Open liftgate — PLG pulse 2.
 * Also used to stop movement while the gate is opening/closing (stock [CarCommon3] tailgate onClick).
 */
suspend fun UniversalCanRepository.trunkPulseOpen(): MbCanCommandResult =
    execute(MbCanCommand.TrunkPulse(2))

suspend fun UniversalCanRepository.trunkPulseStop(): MbCanCommandResult =
    trunkPulseOpen()

/** Double-tap when the liftgate is stopped. */
suspend fun UniversalCanRepository.trunkPulseFromStopped(state: TrunkDoorDisplayState): MbCanCommandResult =
    if (TrunkDoorDomain.shouldPulseClose(state)) {
        trunkPulseClose()
    } else {
        trunkPulseOpen()
    }

fun UniversalCanRepository.launchTrunkCommand(
    scope: CoroutineScope,
    block: suspend UniversalCanRepository.() -> MbCanCommandResult,
) {
    scope.launch {
        block(UniversalCanRepository)
    }
}
