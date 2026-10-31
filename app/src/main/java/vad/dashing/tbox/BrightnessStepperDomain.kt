package vad.dashing.tbox

/**
 * Pure helpers for HU / ICM / HUD brightness stepper widgets (scale 1…10, step 1).
 *
 * Auto-off-then-step: when auto is on, ± still moves the displayed level by 1 and
 * callers must also switch that target to manual / auto-off.
 */
object BrightnessStepperDomain {
    const val MIN_LEVEL = 1
    const val MAX_LEVEL = 10
    const val DEFAULT_LEVEL = 5

    /** ICM: raw mode 0 = auto, 1 = manual. */
    const val ICM_MODE_AUTO = 0
    const val ICM_MODE_MANUAL = 1

    /** mbCAN [ToggleBinary] values for HUD auto brightness (VHAL remaps on write). */
    const val HUD_AUTO_OFF = 1
    const val HUD_AUTO_ON = 2

    fun normalizeLevel(level: Int?): Int =
        (level ?: DEFAULT_LEVEL).coerceIn(MIN_LEVEL, MAX_LEVEL)

    fun stepLevel(current: Int?, increase: Boolean): Int {
        val base = normalizeLevel(current)
        return if (increase) {
            (base + 1).coerceAtMost(MAX_LEVEL)
        } else {
            (base - 1).coerceAtLeast(MIN_LEVEL)
        }
    }

    fun isIcmAuto(mode: Int?): Boolean = mode == ICM_MODE_AUTO

    /**
     * Next level after ±. [turnAutoOff] is true when auto was on so the caller must
     * disable auto and write the returned level as the manual value.
     */
    fun stepWithAutoOff(
        current: Int?,
        autoOn: Boolean,
        increase: Boolean,
    ): BrightnessStepResult =
        BrightnessStepResult(
            level = stepLevel(current, increase),
            turnAutoOff = autoOn,
        )
}

data class BrightnessStepResult(
    val level: Int,
    val turnAutoOff: Boolean,
)
