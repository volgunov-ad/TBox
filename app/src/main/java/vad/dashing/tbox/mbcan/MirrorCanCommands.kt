package vad.dashing.tbox.mbcan

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import vad.dashing.tbox.MIRROR_FOLD_SWITCH_VALUE_FOLD
import vad.dashing.tbox.MIRROR_FOLD_SWITCH_VALUE_UNFOLD
import vad.dashing.tbox.MirrorFoldLastCommandStore
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId.MIRROR_FOLD_SWITCH

fun UniversalCanRepository.launchMirrorFoldCommand(
    scope: CoroutineScope,
    block: suspend UniversalCanRepository.() -> MbCanCommandResult,
) {
    scope.launch {
        block(UniversalCanRepository)
    }
}

/**
 * Single tap: send the opposite of the last remembered fold/unfold command and persist it.
 * Double tap: always fold and remember fold.
 */
fun UniversalCanRepository.launchMirrorFoldSingleTap(
    scope: CoroutineScope,
    context: Context,
) {
    val appContext = context.applicationContext
    launchMirrorFoldCommand(scope) {
        val value = MirrorFoldLastCommandStore.nextSingleTapValue(appContext)
        pulseMirrorFold(value).also { result ->
            if (result.success) {
                MirrorFoldLastCommandStore.rememberSent(appContext, value)
            }
        }
    }
}

fun UniversalCanRepository.launchMirrorFoldDoubleTap(
    scope: CoroutineScope,
    context: Context,
) {
    val appContext = context.applicationContext
    launchMirrorFoldCommand(scope) {
        mirrorFoldPulseFold().also { result ->
            if (result.success) {
                MirrorFoldLastCommandStore.rememberSent(
                    appContext,
                    MIRROR_FOLD_SWITCH_VALUE_FOLD,
                )
            }
        }
    }
}

suspend fun UniversalCanRepository.mirrorFoldPulseFold(): MbCanCommandResult =
    pulseMirrorFold(MIRROR_FOLD_SWITCH_VALUE_FOLD)

suspend fun UniversalCanRepository.mirrorFoldPulseUnfold(): MbCanCommandResult =
    pulseMirrorFold(MIRROR_FOLD_SWITCH_VALUE_UNFOLD)

private suspend fun UniversalCanRepository.pulseMirrorFold(value: Int): MbCanCommandResult =
    execute(MbCanCommand.SetProperty(MIRROR_FOLD_SWITCH, value))
