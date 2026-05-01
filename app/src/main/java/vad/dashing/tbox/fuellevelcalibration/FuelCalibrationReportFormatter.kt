package vad.dashing.tbox.fuellevelcalibration

import kotlin.math.min

/**
 * Текстовый отчёт по зонам для экрана заправок (только чтение, без записи в настройки).
 */
object FuelCalibrationReportFormatter {

    /** null — нет данных или JSON не соответствует числу зон. */
    fun linesOrNull(json: String, tankLiters: Int, zoneCount: Int): List<String>? {
        val tank = tankLiters.coerceAtLeast(1).toDouble()
        val zones = zoneCount.coerceIn(3, 20)
        if (json.isBlank()) return null
        val decoded = FuelCalibrationJson.decode(json)?.takeIf { it.realLiters.size == zones }
            ?: return null
        val sensorMin = tank * 2.0 / 50.0
        val sensorMax = min(tank * 48.0 / 50.0, tank - 1e-3)
        val estimator = FuelSmartEstimator(
            tankCapacity = tank,
            sensorMin = sensorMin,
            sensorMax = sensorMax,
            zoneCount = zones,
            initialCalibration = decoded,
            onCalibrationPersist = { },
        )
        return estimator.getCalibrationReport()
    }
}
