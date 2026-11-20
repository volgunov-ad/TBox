package vad.dashing.tbox.automation

/**
 * A10 steering keys arrive as `adayo.keyEvent` with int extra `InputKey`.
 * Door panel buttons arrive as VHAL values (1 = pressed, 0 = released) and are
 * reported with the same key codes as A9 doors. A door hold also makes stock
 * firmware broadcast `InputKey` 231, which is the right-wheel bottom button,
 * so that broadcast is ignored while a door is held and briefly after release.
 */
internal object Android10HardKeyDecode {
    const val VOICE_ASSIST_INPUT_KEY = 231
    const val VOICE_ASSIST_SUPPRESS_AFTER_DOOR_MS = 1_500L

    val pickerCodes: List<Int> = listOf(
        19, 20, 21, 22, 195, 189,
        24, 25, 88, 87, 91, 4, 231,
        210, 211, 212,
    )

    fun wheelStatus(action: String?): AutomationHardKeyStatus? = when (action) {
        "adayo.keyEvent.onKeyDown" -> AutomationHardKeyStatus.PRESSED
        "adayo.keyEvent.onKeyUp" -> AutomationHardKeyStatus.RELEASED
        else -> null
    }

    fun doorStatus(value: Int): AutomationHardKeyStatus? = when (value) {
        1 -> AutomationHardKeyStatus.PRESSED
        0 -> AutomationHardKeyStatus.RELEASED
        else -> null
    }

    fun suppressVoiceAssist(
        inputKey: Int,
        doorHeld: Boolean,
        nowMillis: Long,
        suppressUntilMillis: Long,
    ): Boolean = inputKey == VOICE_ASSIST_INPUT_KEY && (doorHeld || nowMillis < suppressUntilMillis)
}
