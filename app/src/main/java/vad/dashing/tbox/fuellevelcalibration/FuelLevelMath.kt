package vad.dashing.tbox.fuellevelcalibration

/** Линейный пересчёт уровня топлива: filtered % × номинальный объём бака. */
object FuelLevelMath {
    fun linearLitersFromFilteredPercent(percent: Float, tankLiters: Float): Float =
        percent / 100f * tankLiters.coerceAtLeast(1f)
}
