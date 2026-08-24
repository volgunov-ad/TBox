package vad.dashing.tbox

/**
 * How many panels to mount per staged step.
 * Android 9 (mbCAN) can take a pair; Android 10 (Adayo/VHAL) stays at one per pause.
 */
internal object StagedUiPanelMount {

    const val ANDROID9_STEP_SIZE = 2
    const val ANDROID10_STEP_SIZE = 1

    fun stepSize(mode: HeadUnitCanMode): Int = when (mode) {
        HeadUnitCanMode.Android9MbCan -> ANDROID9_STEP_SIZE
        HeadUnitCanMode.Android10Vhal -> ANDROID10_STEP_SIZE
    }
}
