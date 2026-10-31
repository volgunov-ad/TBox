package vad.dashing.tbox.phoneble

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PhoneDirectPhone(
    val id: String,
    val name: String,
    val connected: Boolean = false,
)

enum class PhoneDirectState { OFF, LISTENING, BLUETOOTH_OFF, ERROR }

/** UI state of the direct phone link; actions reach the running [PhoneDirectServer]. */
object PhoneDirectRepository {
    private val _state = MutableStateFlow(PhoneDirectState.OFF)
    val state: StateFlow<PhoneDirectState> = _state.asStateFlow()

    private val _phones = MutableStateFlow<List<PhoneDirectPhone>>(emptyList())
    val phones: StateFlow<List<PhoneDirectPhone>> = _phones.asStateFlow()

    /** elapsedRealtime when the pairing window closes; 0 when closed. */
    private val _learnUntil = MutableStateFlow(0L)
    val learnUntil: StateFlow<Long> = _learnUntil.asStateFlow()

    private val _pending = MutableStateFlow<PhoneDirectPhone?>(null)
    val pending: StateFlow<PhoneDirectPhone?> = _pending.asStateFlow()

    @Volatile
    internal var server: PhoneDirectServer? = null

    fun beginLearn() {
        server?.beginLearn()
    }

    fun endLearn() {
        server?.endLearn()
    }

    fun allow(id: String) {
        server?.allow(id)
    }

    fun deny(id: String) {
        server?.deny(id)
    }

    fun forget(id: String) {
        server?.forget(id)
    }

    internal fun setState(value: PhoneDirectState) {
        _state.value = value
    }

    internal fun setPhones(value: List<PhoneDirectPhone>) {
        _phones.value = value
    }

    internal fun setLearnUntil(value: Long) {
        _learnUntil.value = value
    }

    internal fun setPending(value: PhoneDirectPhone?) {
        _pending.value = value
    }
}
