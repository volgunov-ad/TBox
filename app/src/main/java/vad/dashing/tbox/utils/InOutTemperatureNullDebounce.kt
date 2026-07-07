package vad.dashing.tbox.utils

/**
 * Null debounce for in-cabin and outside temperature CAN readings (TBox path).
 * Invalid/out-of-range samples do not clear the published value until [debounceMs] elapsed
 * since the last valid reading. No disk persistence — in-memory only.
 */
object InOutTemperatureNullDebounce {
    const val DEFAULT_NULL_DEBOUNCE_MS = 300_000L

    fun isValidCelsius(decoded: Float): Boolean = decoded >= -40f && decoded < 87f

    fun resolveAfterProbe(
        current: Float?,
        lastTimeNotNull: Long?,
        decodedCelsius: Float,
        now: Long,
        debounceMs: Long = DEFAULT_NULL_DEBOUNCE_MS,
    ): ResolvedTemperature {
        if (isValidCelsius(decodedCelsius)) {
            return ResolvedTemperature(value = decodedCelsius, lastTimeNotNull = now)
        }
        return if (now - (lastTimeNotNull ?: 0L) > debounceMs) {
            ResolvedTemperature(value = null, lastTimeNotNull = lastTimeNotNull)
        } else {
            ResolvedTemperature(value = current, lastTimeNotNull = lastTimeNotNull)
        }
    }
}

data class ResolvedTemperature(
    val value: Float?,
    val lastTimeNotNull: Long?,
)
