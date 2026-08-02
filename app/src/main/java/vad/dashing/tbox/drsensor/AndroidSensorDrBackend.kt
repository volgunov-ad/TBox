package vad.dashing.tbox.drsensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock

/**
 * Fallback via Android [SensorManager] (`TYPE_GYROSCOPE` + `TYPE_ACCELEROMETER`).
 * Stock Jetour apps prefer OEM IIO/Adayo paths; this may still work if HAL exposes sensors.
 * Gyro is converted from rad/s to ˜/s so [DrSensorSnapshot.gyroYaw] matches A9/A10 units.
 */
class AndroidSensorDrBackend(
    context: Context,
) : DrSensorBackend {
    override val source: DrSensorSource = DrSensorSource.SENSOR_MANAGER

    private val sensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var listener: SensorEventListener? = null
    private var gyroYaw: Float? = null
    private var gyroPitch: Float? = null
    private var gyroRoll: Float? = null
    private var accelX: Float? = null
    private var accelY: Float? = null
    private var accelZ: Float? = null
    private var statusText: String = ""

    override fun start(onUpdate: (DrSensorSnapshot) -> Unit) {
        stop()
        val gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (gyro == null && accel == null) {
            statusText = "no TYPE_GYROSCOPE / TYPE_ACCELEROMETER"
            onUpdate(
                DrSensorSnapshot(
                    source = source,
                    statusText = statusText,
                    lastUpdateElapsedMs = SystemClock.elapsedRealtime(),
                ),
            )
            return
        }
        statusText = buildString {
            append(if (gyro != null) "gyro ok" else "no gyro")
            append("; ")
            append(if (accel != null) "accel ok" else "no accel")
        }
        val l = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_GYROSCOPE -> {
                        // Android: values[0]=x, [1]=y, [2]=z (rad/s) ? °/s for UI / mock retention.
                        gyroPitch = event.values.getOrNull(0)?.let { Math.toDegrees(it.toDouble()).toFloat() }
                        gyroYaw = event.values.getOrNull(1)?.let { Math.toDegrees(it.toDouble()).toFloat() }
                        gyroRoll = event.values.getOrNull(2)?.let { Math.toDegrees(it.toDouble()).toFloat() }
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        accelX = event.values.getOrNull(0)
                        accelY = event.values.getOrNull(1)
                        accelZ = event.values.getOrNull(2)
                    }
                }
                onUpdate(buildSnapshot())
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        listener = l
        gyro?.let {
            sensorManager.registerListener(l, it, SensorManager.SENSOR_DELAY_UI)
        }
        accel?.let {
            sensorManager.registerListener(l, it, SensorManager.SENSOR_DELAY_UI)
        }
        onUpdate(buildSnapshot())
    }

    override fun stop() {
        listener?.let { sensorManager.unregisterListener(it) }
        listener = null
    }

    private fun buildSnapshot(): DrSensorSnapshot = DrSensorSnapshot(
        source = source,
        statusText = statusText,
        gyroYaw = gyroYaw,
        gyroPitch = gyroPitch,
        gyroRoll = gyroRoll,
        accelX = accelX,
        accelY = accelY,
        accelZ = accelZ,
        lastUpdateElapsedMs = SystemClock.elapsedRealtime(),
    )
}
