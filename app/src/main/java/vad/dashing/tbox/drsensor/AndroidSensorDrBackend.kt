package vad.dashing.tbox.drsensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Fallback via Android [SensorManager] (`TYPE_GYROSCOPE` + `TYPE_ACCELEROMETER`).
 * Stock Jetour apps prefer OEM IIO/Adayo paths; this may still work if HAL exposes sensors.
 * Gyro is converted from rad/s to °/s so [DrSensorSnapshot.gyroYaw] matches A9/A10 units.
 */
class AndroidSensorDrBackend(
    context: Context,
) : DrSensorBackend {
    override val source: DrSensorSource = DrSensorSource.SENSOR_MANAGER

    private val sensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null
    private var listener: SensorEventListener? = null
    private var gyroYaw: Float? = null
    private var gyroPitch: Float? = null
    private var gyroRoll: Float? = null
    private var accelX: Float? = null
    private var accelY: Float? = null
    private var accelZ: Float? = null
    private var statusText: String = ""
    private var halRefused: Boolean = false

    override fun start(onUpdate: (DrSensorSnapshot) -> Unit) {
        stop()
        gyroYaw = null
        gyroPitch = null
        gyroRoll = null
        accelX = null
        accelY = null
        accelZ = null
        halRefused = false
        val gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (gyro == null && accel == null) {
            halRefused = true
            statusText = HAL_REFUSED_STATUS
            onUpdate(
                DrSensorSnapshot(
                    source = source,
                    statusText = statusText,
                    lastUpdateElapsedMs = SystemClock.elapsedRealtime(),
                    halRefused = true,
                ),
            )
            return
        }
        val l = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_GYROSCOPE -> {
                        // Android: values[0]=x, [1]=y, [2]=z (rad/s) ? °/s for UI / mock retention.
                        gyroPitch = event.values.getOrNull(0)?.let { Math.toDegrees(it.toDouble()).toFloat() }
                        gyroYaw = event.values.getOrNull(1)?.let { Math.toDegrees(it.toDouble()).toFloat() }
                        gyroRoll = event.values.getOrNull(2)?.let { Math.toDegrees(it.toDouble()).toFloat() }
                        if (gyroYaw != null) {
                            halRefused = false
                            statusText = buildString {
                                append("gyro ok; ")
                                append(if (accel != null) "accel ok" else "no accel")
                            }
                        }
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
        val gyroRegistered = gyro?.let {
            sensorManager.registerListener(l, it, SensorManager.SENSOR_DELAY_UI)
        } ?: false
        val accelRegistered = accel?.let {
            sensorManager.registerListener(l, it, SensorManager.SENSOR_DELAY_UI)
        } ?: false
        if (gyro == null || !gyroRegistered) {
            halRefused = true
            statusText = HAL_REFUSED_STATUS
        } else {
            statusText = buildString {
                append("gyro waiting; ")
                append(if (accelRegistered) "accel ok" else "no accel")
            }
            val checkRunnable = Runnable {
                if (listener === l && gyroYaw == null) {
                    halRefused = true
                    statusText = HAL_REFUSED_STATUS
                    onUpdate(buildSnapshot())
                }
            }
            timeoutRunnable = checkRunnable
            mainHandler.postDelayed(checkRunnable, HAL_SILENT_TIMEOUT_MS)
        }
        onUpdate(buildSnapshot())
    }

    override fun stop() {
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        timeoutRunnable = null
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
        halRefused = halRefused,
    )

    companion object {
        const val HAL_SILENT_TIMEOUT_MS = 3_000L
        const val HAL_REFUSED_STATUS = "sensor HAL refused activation"
    }
}
