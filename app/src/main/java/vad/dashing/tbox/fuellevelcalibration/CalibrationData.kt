package vad.dashing.tbox.fuellevelcalibration

/**
 * Снимок накопленных литров по зонам бака для сериализации в настройках (формат совместим с tochBak).
 */
class CalibrationData(
    val realLiters: DoubleArray,
    val sensorLiters: DoubleArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CalibrationData) return false
        return realLiters.contentEquals(other.realLiters) &&
            sensorLiters.contentEquals(other.sensorLiters)
    }

    override fun hashCode(): Int =
        31 * realLiters.contentHashCode() + sensorLiters.contentHashCode()
}
