package vad.dashing.tbox.mbcan

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import vad.dashing.tbox.MIRROR_FOLD_SWITCH_VALUE_FOLD
import vad.dashing.tbox.MIRROR_FOLD_SWITCH_VALUE_UNFOLD
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId.MIRROR_FOLD_SWITCH

fun UniversalCanRepository.launchMirrorFoldCommand(
    scope: CoroutineScope,
    block: suspend UniversalCanRepository.() -> MbCanCommandResult,
) {
    scope.launch {
        block(UniversalCanRepository)
    }
}

suspend fun UniversalCanRepository.mirrorFoldPulseFold(): MbCanCommandResult =
    execute(MbCanCommand.SetProperty(MIRROR_FOLD_SWITCH, MIRROR_FOLD_SWITCH_VALUE_FOLD))

suspend fun UniversalCanRepository.mirrorFoldPulseUnfold(): MbCanCommandResult =
    execute(MbCanCommand.SetProperty(MIRROR_FOLD_SWITCH, MIRROR_FOLD_SWITCH_VALUE_UNFOLD))
