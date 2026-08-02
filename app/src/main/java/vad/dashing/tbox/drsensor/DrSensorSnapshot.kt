package vad.dashing.tbox.drsensor

/**
 * Live dead-reckoning / IMU sample for Geoposition diagnostics.
 * [gyroYaw] (°/s, left +, right ?) is also used by mock retention heading integration.
 */
data class DrSensorSnapshot(
    val source: DrSensorSource = DrSensorSource.NONE,
    val statusText: String = "",
    val gyroYaw: Float? = null,
    val gyroPitch: Float? = null,
    val gyroRoll: Float? = null,
    val gyroTemp: Float? = null,
    val accelX: Float? = null,
    val accelY: Float? = null,
    val accelZ: Float? = null,
    val pulseValue: Float? = null,
    val pulseGear: Int? = null,
    val mountExist: Boolean? = null,
    val mountYaw: Float? = null,
    val mountPitch: Float? = null,
    val mountRoll: Float? = null,
    val lastUpdateElapsedMs: Long = 0L,
) {
    companion object {
        val EMPTY = DrSensorSnapshot()
    }
}

enum class DrSensorSource {
    NONE,
    SENSOR_MANAGER,
    A9_UDS,
    A10_NAVI_DR,
    ;

    fun displayLabel(): String = when (this) {
        NONE -> "—"
        SENSOR_MANAGER -> "SensorManager"
        A9_UDS -> "A9 uds-sensor"
        A10_NAVI_DR -> "A10 NaviDR"
    }
}
