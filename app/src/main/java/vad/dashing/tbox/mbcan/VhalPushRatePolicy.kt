package vad.dashing.tbox.mbcan

/**
 * VHAL [registerListener] rate candidates.
 *
 * Discrete switches (ADAS, HVAC, trunk, …) must stay **on-change only**.
 * Escalating to 1–5 Hz when Adayo rejects `0.0f` keeps binder callbacks alive
 * forever while main/floating panels show those widgets — including parked.
 */
object VhalPushRatePolicy {
    const val ON_CHANGE_HZ = 0.0f
    const val CONTINUOUS_HZ = 1.0f
    const val CONTINUOUS_FALLBACK_HZ = 5.0f

    /**
     * @param preferred rate from [Android10VhalRepository.pushRateForPropertyId]
     *   (`0` = on-change, `>0` = continuous telemetry).
     */
    fun candidates(preferred: Float): List<Float> {
        return if (preferred > 0f) {
            // Telemetry: keep a continuous rate; do not fall back to on-change (0)
            // which can suppress RPM/speed callbacks on some stacks.
            linkedSetOf(preferred, CONTINUOUS_FALLBACK_HZ).toList()
        } else {
            listOf(ON_CHANGE_HZ)
        }
    }
}
