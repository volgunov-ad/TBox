package vad.dashing.tbox.mbcan

/**
 * CEM rain-sensor detection (`getRainDetectedSts` / `R_0400_CEM_2_RainDetected`).
 *
 * Electrical manual `S_RAIN`: **0x1 = TRUE** (rain). CEM 1-bit:
 * **1** detected / **0** dry; any other raw → unknown.
 * Not [R_0400_CEM_2_RainSensorFailSts] (sensor fault, unused here).
 */
object RainDetectedDomain {
    fun decodeDetected(raw: Int): Boolean? = when (raw) {
        1 -> true
        0 -> false
        else -> null
    }
}
