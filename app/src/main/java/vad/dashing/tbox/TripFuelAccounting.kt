package vad.dashing.tbox

/**
 * Per-trip fuel consumed from filtered tank percentage. Handles refueling (sharp level increase)
 * and filters sensor noise on downward moves.
 */
object TripFuelAccounting {

    /** Ignore drops smaller than this (percentage points) — sensor noise, not consumption. */
    const val CONSUME_MIN_DELTA_PERCENT = 0.15f

    /** A single-step rise of at least this (percentage points) is treated as refueling, not negative consumption. */
    const val REFUEL_RISE_PERCENT = 2f

    data class FuelStepResult(
        val consumedLiters: Float,
        val baselinePercent: Float,
        val refuelDetected: Boolean,
    )

    /**
     * @param currentConsumedLiters accumulated consumption for the trip
     * @param lastPercent previous filtered level %, or null on first sample
     * @param percentNow current filtered level %
     * @param tankLiters tank capacity for liter conversion
     */
    fun applyFuelPercentStep(
        currentConsumedLiters: Float,
        lastPercent: Float?,
        percentNow: Float,
        tankLiters: Float,
    ): FuelStepResult {
        if (lastPercent == null) {
            return FuelStepResult(currentConsumedLiters, percentNow, refuelDetected = false)
        }
        val delta = percentNow - lastPercent
        val refuel = delta >= REFUEL_RISE_PERCENT
        val consumed = when {
            refuel -> currentConsumedLiters
            delta <= -CONSUME_MIN_DELTA_PERCENT ->
                currentConsumedLiters + (-delta) / 100f * tankLiters
            else -> currentConsumedLiters
        }
        return FuelStepResult(consumed, percentNow, refuelDetected = refuel)
    }
}
