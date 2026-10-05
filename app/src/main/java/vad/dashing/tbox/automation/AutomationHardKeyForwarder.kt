package vad.dashing.tbox.automation

/**
 * Debounced fan-out from the OEM `IMBHardKeyListener` callback into
 * [AutomationTriggerHardKeyEventBus]. Mirrors the CCS stalk debounce window
 * ([AUTOMATION_HARD_KEY_DEBOUNCE_MS]): repeated OEM events for the same
 * keyCode + status pair within the window are dropped. Unknown keyStatus values
 * are ignored.
 */
internal class AutomationHardKeyForwarder(
    private val debounceMillis: Long = AUTOMATION_HARD_KEY_DEBOUNCE_MS,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val lastAcceptedAt = mutableMapOf<Pair<Int, AutomationHardKeyStatus>, Long>()

    /** Safe to call from OEM callback threads. */
    fun onHardKey(keyCode: Int, rawStatus: Int) {
        val status = AutomationHardKeyStatus.fromRawValue(rawStatus) ?: return
        val stamp = keyCode to status
        val now = nowMillis()
        val last = lastAcceptedAt[stamp]
        if (last != null && now - last < debounceMillis) {
            return
        }
        lastAcceptedAt[stamp] = now
        AutomationTriggerHardKeyEventBus.publish(AutomationHardKeyEvent(keyCode, status))
    }
}
