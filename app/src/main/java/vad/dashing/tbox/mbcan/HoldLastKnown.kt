package vad.dashing.tbox.mbcan

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Keeps the last known non-null value in a [MutableStateFlow].
 *
 * Transient mbCAN poll/push samples that decode to null (sentinel `-1`, out-of-range, or
 * unavailable read) must not blank Car Settings ModeButtons / numeric rows.
 */
internal object HoldLastKnown {
    fun <T : Any> set(flow: MutableStateFlow<T?>, next: T?) {
        if (next != null) {
            flow.value = next
        }
    }
}
