package vad.dashing.tbox.drsensor

import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.SystemClock
import android.util.Log
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * A9 Mengbo FactoryMode path: LocalSocket abstract name `uds-sensor`.
 */
class A9UdsSensorBackend : DrSensorBackend {
    override val source: DrSensorSource = DrSensorSource.A9_UDS

    private val running = AtomicBoolean(false)
    private var worker: Thread? = null
    private var socket: LocalSocket? = null

    private var gyroYaw: Float? = null
    private var gyroPitch: Float? = null
    private var gyroRoll: Float? = null
    private var gyroTemp: Float? = null
    private var accelX: Float? = null
    private var accelY: Float? = null
    private var accelZ: Float? = null
    private var statusText: String = "connecting…"

    override fun start(onUpdate: (DrSensorSnapshot) -> Unit) {
        stop()
        running.set(true)
        statusText = "connecting…"
        onUpdate(buildSnapshot())
        worker = thread(name = "a9-uds-sensor", isDaemon = true) {
            val buf = ByteArray(4096)
            var clockSent = false
            while (running.get()) {
                try {
                    val sock = LocalSocket()
                    sock.connect(LocalSocketAddress(SOCKET_NAME, LocalSocketAddress.Namespace.ABSTRACT))
                    sock.soTimeout = 5_000
                    socket = sock
                    statusText = "connected"
                    onUpdate(buildSnapshot())
                    val input = sock.inputStream
                    val output = sock.outputStream
                    while (running.get()) {
                        val available = try {
                            input.available()
                        } catch (_: IOException) {
                            -1
                        }
                        if (available < 0) break
                        if (available > 0) {
                            if (!clockSent) {
                                try {
                                    output.write(CLOCK_CMD)
                                    output.flush()
                                    clockSent = true
                                } catch (_: IOException) {
                                    break
                                }
                            }
                            val n = input.read(buf)
                            if (n <= 0) break
                            val raw = String(buf, 0, n, Charset.forName("UTF-8"))
                            applyParsed(UdsSensorParser.parse(raw))
                            statusText = "ok"
                            onUpdate(buildSnapshot())
                        } else {
                            Thread.sleep(5L)
                        }
                    }
                } catch (e: Exception) {
                    statusText = "error: ${e.javaClass.simpleName}: ${e.message}"
                    Log.d(TAG, "uds-sensor: $statusText")
                    onUpdate(buildSnapshot())
                } finally {
                    closeSocketQuietly()
                    clockSent = false
                }
                if (running.get()) {
                    try {
                        Thread.sleep(RECONNECT_MS)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }
        }
    }

    override fun stop() {
        running.set(false)
        closeSocketQuietly()
        worker?.interrupt()
        worker = null
    }

    private fun applyParsed(parsed: UdsSensorParser.ParseResult) {
        parsed.gyros.lastOrNull()?.let { g ->
            gyroTemp = g.temperature
            gyroPitch = g.pitch
            gyroYaw = g.yaw
            gyroRoll = g.roll
        }
        parsed.accels.lastOrNull()?.let { a ->
            // OEM labels pitch/yaw/roll for A3D — map to X/Y/Z display fields.
            accelX = a.pitch
            accelY = a.yaw
            accelZ = a.roll
        }
    }

    private fun buildSnapshot(): DrSensorSnapshot = DrSensorSnapshot(
        source = source,
        statusText = statusText,
        gyroYaw = gyroYaw,
        gyroPitch = gyroPitch,
        gyroRoll = gyroRoll,
        gyroTemp = gyroTemp,
        accelX = accelX,
        accelY = accelY,
        accelZ = accelZ,
        lastUpdateElapsedMs = SystemClock.elapsedRealtime(),
    )

    private fun closeSocketQuietly() {
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
    }

    companion object {
        private const val TAG = "A9UdsSensor"
        private const val SOCKET_NAME = "uds-sensor"
        private const val RECONNECT_MS = 2_000L
        private val CLOCK_CMD = "\$SET,CLOCK,7\r\n".toByteArray(Charsets.UTF_8)
    }
}
