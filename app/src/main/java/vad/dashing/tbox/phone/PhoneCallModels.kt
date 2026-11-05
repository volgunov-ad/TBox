package vad.dashing.tbox.phone

/**
 * Normalized HFP / Telecom call state for automations and External API
 * (`phone_call_state`, source=app).
 */
enum class PhoneCallState(val storageKey: String) {
    IDLE("idle"),
    DIALING("dialing"),
    ACTIVE("active"),
    INCOMING("incoming"),
    HELD("held"),
    UNKNOWN("unknown");

    companion object {
        val OPTIONS: List<String> = entries.map { it.storageKey }

        fun fromStorageKey(raw: String?): PhoneCallState =
            entries.firstOrNull { it.storageKey.equals(raw?.trim(), ignoreCase = true) }
                ?: UNKNOWN
    }
}

enum class PhoneCallDirection(val storageKey: String) {
    NONE("none"),
    OUTGOING("outgoing"),
    INCOMING("incoming");

    companion object {
        val OPTIONS: List<String> = entries.map { it.storageKey }
    }
}

data class PhoneCallSnapshot(
    val hfpConnected: Boolean = false,
    val callState: PhoneCallState = PhoneCallState.UNKNOWN,
    val direction: PhoneCallDirection = PhoneCallDirection.NONE,
    /** Current or last known number (as observed); may be blank. */
    val number: String = "",
    val answerSupported: Boolean = false,
    val hangupSupported: Boolean = false,
    val observerActive: Boolean = false,
)

/**
 * `phone_call_number` keeps the last number after a call ends, but a new call that arrives
 * without a number (withheld caller) must not inherit the previous caller's number.
 */
internal fun nextObservedCallNumber(
    previousState: PhoneCallState,
    previousNumber: String,
    newState: PhoneCallState,
    observedNumber: String,
): String = when {
    observedNumber.isNotBlank() -> observedNumber
    newState == PhoneCallState.IDLE || newState == PhoneCallState.UNKNOWN -> previousNumber
    previousState == PhoneCallState.IDLE || previousState == PhoneCallState.UNKNOWN -> ""
    else -> previousNumber
}

sealed class PhoneCallControlResult {
    data object Success : PhoneCallControlResult()
    data class Failure(val message: String) : PhoneCallControlResult()
}
