package vad.dashing.tbox.fuellevelcalibration

/**
 * Одна заправка для обучения калибровки (как во внешней ветке tochBak).
 */
data class FuelEntry(
    /** Текущий пробег машины, км. */
    val odometerKm: Double,
    /** Наработка двигателя (моточасы). */
    val engineHours: Double,
    /** Сколько литров «видел» датчик до заправки (линейная модель по % и объёму бака). */
    val sensorBefore: Double,
    /** Сколько литров «видел» датчик после заправки. */
    val sensorAfter: Double,
    /** Объём по чеку АЗС, л. */
    val litersByCheck: Double,
    /** Температура за бортом в момент заправки, °C (для термокомпенсации). */
    val ambientTemp: Double = 15.0,
)
