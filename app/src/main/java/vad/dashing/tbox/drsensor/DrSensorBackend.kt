package vad.dashing.tbox.drsensor

/** One DR/IMU source that can push updates into [DrSensorRepository]. */
interface DrSensorBackend {
    val source: DrSensorSource
    fun start(onUpdate: (DrSensorSnapshot) -> Unit)
    fun stop()
}
