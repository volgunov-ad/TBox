package vad.dashing.tbox

/**
 * Remembers the last mirror fold/unfold command we sent **in this process only**.
 * Actual mirror position is not readable over mbCAN/VHAL, so single-tap toggle
 * alternates relative to this value. On a fresh process start mirrors are assumed
 * unfolded (car unfolds them on ignition), so the first single tap sends fold.
 */
object MirrorFoldLastCommandStore {
    const val DEFAULT_LAST_VALUE = MIRROR_FOLD_SWITCH_VALUE_UNFOLD

    @Volatile
    private var lastValue: Int = DEFAULT_LAST_VALUE

    fun readLastValue(): Int = MirrorFoldToggleLogic.normalizeStoredValue(lastValue)

    fun rememberSent(value: Int) {
        lastValue = MirrorFoldToggleLogic.normalizeStoredValue(value)
    }

    /** Opposite of the last sent command — value for the next single tap. */
    fun nextSingleTapValue(): Int =
        MirrorFoldToggleLogic.nextSingleTapValue(readLastValue())

    /** Test helper: restore default (unfolded) session state. */
    internal fun resetForTests() {
        lastValue = DEFAULT_LAST_VALUE
    }
}

/** Pure toggle policy for mirror fold commands (unit-testable without Android). */
object MirrorFoldToggleLogic {
    fun normalizeStoredValue(value: Int): Int =
        if (value == MIRROR_FOLD_SWITCH_VALUE_FOLD) {
            MIRROR_FOLD_SWITCH_VALUE_FOLD
        } else {
            MIRROR_FOLD_SWITCH_VALUE_UNFOLD
        }

    fun opposite(value: Int): Int =
        if (normalizeStoredValue(value) == MIRROR_FOLD_SWITCH_VALUE_FOLD) {
            MIRROR_FOLD_SWITCH_VALUE_UNFOLD
        } else {
            MIRROR_FOLD_SWITCH_VALUE_FOLD
        }

    fun nextSingleTapValue(lastSentValue: Int): Int = opposite(lastSentValue)
}
