package vad.dashing.tbox

/**
 * Interaction mode for companion relay tiles ([espRelay0] / [espRelay1]).
 *
 * - [BUTTON]: single tap pulses the output on for [BUTTON_PULSE_MS]; double tap toggles latch.
 * - [RELAY]: single tap toggles latch.
 */
enum class EspRelayWidgetMode(val storageKey: String) {
    BUTTON("button"),
    RELAY("relay"),
    ;

    companion object {
        const val BUTTON_PULSE_MS = 500L

        val DEFAULT: EspRelayWidgetMode = RELAY

        fun fromStorageKey(key: String?): EspRelayWidgetMode {
            val normalized = key?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.storageKey == normalized } ?: DEFAULT
        }
    }
}

fun isEspRelayWidgetDataKey(dataKey: String): Boolean =
    dataKey == "espRelay0" || dataKey == "espRelay1"
