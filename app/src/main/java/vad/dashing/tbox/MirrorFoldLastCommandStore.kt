package vad.dashing.tbox

import android.content.Context

/**
 * Persists the last mirror fold/unfold command we sent.
 * Actual mirror position is not readable over mbCAN/VHAL, so single-tap toggle
 * alternates relative to this remembered command (survives process/reboots).
 */
object MirrorFoldLastCommandStore {
    private const val PREFS = "mirror_fold_last_command"
    private const val KEY_LAST_VALUE = "last_value"

    /**
     * Default: treat mirrors as unfolded so the first single tap sends fold.
     */
    const val DEFAULT_LAST_VALUE = MIRROR_FOLD_SWITCH_VALUE_UNFOLD

    fun readLastValue(context: Context): Int {
        val stored = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_LAST_VALUE, DEFAULT_LAST_VALUE)
        return MirrorFoldToggleLogic.normalizeStoredValue(stored)
    }

    fun rememberSent(context: Context, value: Int) {
        val normalized = MirrorFoldToggleLogic.normalizeStoredValue(value)
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_LAST_VALUE, normalized)
            .apply()
    }

    /** Opposite of the last sent command — value for the next single tap. */
    fun nextSingleTapValue(context: Context): Int =
        MirrorFoldToggleLogic.nextSingleTapValue(readLastValue(context))
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
