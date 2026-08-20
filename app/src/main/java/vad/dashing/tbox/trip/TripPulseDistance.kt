package vad.dashing.tbox.trip

/**
 * Trip distance with integer odometer as truth and wheel-pulse fraction inside the open km.
 *
 * `distanceKm = (odoNow − odoStart) + pulseSinceLastOdoM / 1000`
 */
object TripPulseDistance {
    fun hybridKm(
        odoStartKm: UInt,
        odoNowKm: UInt,
        pulseSinceLastOdoM: Float,
    ): Float {
        if (odoNowKm < odoStartKm) return 0f
        val completedKm = (odoNowKm - odoStartKm).toFloat()
        val fractionKm = (pulseSinceLastOdoM / 1000f).coerceAtLeast(0f)
        return TripDistanceFormat.roundKm(completedKm + fractionKm)
    }

    /**
     * When pulse hybrid is off or data incomplete — classic whole-km delta from odo ticks.
     */
    fun classicOdoDeltaKm(
        odoNowKm: UInt?,
        lastOdoKm: UInt?,
    ): Float {
        if (odoNowKm == null || lastOdoKm == null || odoNowKm < lastOdoKm) return 0f
        return TripDistanceFormat.roundKm((odoNowKm - lastOdoKm).toFloat())
    }

    fun resolveDistanceKm(
        currentDistanceKm: Float,
        odoStartKm: UInt?,
        odoNowKm: UInt?,
        lastOdoKm: UInt?,
        pulseSinceLastOdoM: Float,
        hybridEnabled: Boolean,
    ): Float {
        if (hybridEnabled && odoStartKm != null && odoNowKm != null && odoNowKm >= odoStartKm) {
            return hybridKm(odoStartKm, odoNowKm, pulseSinceLastOdoM)
        }
        return TripDistanceFormat.addKm(
            currentDistanceKm,
            classicOdoDeltaKm(odoNowKm, lastOdoKm),
        )
    }
}
