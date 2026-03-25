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

    /**
     * @param currentConsumedLiters accumulated consumption for the trip
     * @param lastPercent previous filtered level %, or null on first sample
     * @param percentNow current filtered level %
     * @param tankLiters tank capacity for liter conversion
     * @return updated consumption and the new baseline percent ([percentNow])
     */
    fun applyFuelPercentStep(
        currentConsumedLiters: Float,
        lastPercent: Float?,
        percentNow: Float,
        tankLiters: Float,
    ): Pair<Float, Float> {
        if (lastPercent == null) {
            return Pair(currentConsumedLiters, percentNow)
        }
        val delta = percentNow - lastPercent
        val consumed = when {
            delta >= REFUEL_RISE_PERCENT -> currentConsumedLiters
            delta <= -CONSUME_MIN_DELTA_PERCENT ->
                currentConsumedLiters + (-delta) / 100f * tankLiters
            else -> currentConsumedLiters
        }
        return Pair(consumed, percentNow)
    }
}
