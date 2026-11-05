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

/** Immediate hangup failure when the observed state is clearly idle (do not try the stack). */
internal const val HANGUP_NO_ACTIVE_CALL_MESSAGE = "Нет активного звонка"

/**
 * After a best-effort hangup attempt while [PhoneCallState.UNKNOWN]: clearer than
 * [HANGUP_NO_ACTIVE_CALL_MESSAGE] because we do not know whether a call exists.
 */
internal const val HANGUP_STATE_UNKNOWN_MESSAGE =
    "Состояние звонка неизвестно; сброс не подтверждён"

/**
 * Pre-attempt hangup gate: only [PhoneCallState.IDLE] fails immediately.
 * [PhoneCallState.UNKNOWN] returns null so the caller can try Telecom / HeadsetClient.
 */
internal fun hangupImmediateFailureMessage(state: PhoneCallState): String? =
    when (state) {
        PhoneCallState.IDLE -> HANGUP_NO_ACTIVE_CALL_MESSAGE
        else -> null
    }
