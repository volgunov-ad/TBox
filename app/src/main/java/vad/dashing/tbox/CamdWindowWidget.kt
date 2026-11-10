package vad.dashing.tbox

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale
import java.util.concurrent.Executors

/** Tile that asks the Камеры ГУ app to open or close one camera window. */
const val CAMD_WINDOW_WIDGET_DATA_KEY = "camdWindowWidget"

const val CAMD_WINDOW_PORT = 8770
const val CAMD_WINDOW_DEFAULT_CAMERA = 4
const val CAMD_WINDOW_DEFAULT_X = 2
const val CAMD_WINDOW_DEFAULT_Y = 55
const val CAMD_WINDOW_DEFAULT_W = 40
const val CAMD_WINDOW_DEFAULT_H = 40

data class CamdWindowGeometry(
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
)

/** Keeps the window inside the screen and large enough for the camera app. */
fun normalizeCamdWindowGeometry(x: Int, y: Int, w: Int, h: Int): CamdWindowGeometry {
    val width = w.coerceIn(5, 100)
    val height = h.coerceIn(5, 100)
    return CamdWindowGeometry(
        x = x.coerceIn(0, 100 - width),
        y = y.coerceIn(0, 100 - height),
        w = width,
        h = height,
    )
}

fun normalizeCamdWindowCamera(id: Int): Int {
    return if (id == 0 || id == 1 || id == 3 || id == 4 || id == 5 || id == 8 || id == 9) {
        id
    } else {
        CAMD_WINDOW_DEFAULT_CAMERA
    }
}

fun camdWindowOpenLine(cameraId: Int, x: Int, y: Int, w: Int, h: Int): String {
    val camera = normalizeCamdWindowCamera(cameraId)
    val geometry = normalizeCamdWindowGeometry(x, y, w, h)
    return String.format(
        Locale.US,
        "OPEN %d %.4f %.4f %.4f %.4f",
        camera,
        geometry.x / 100.0,
        geometry.y / 100.0,
        geometry.w / 100.0,
        geometry.h / 100.0,
    )
}

fun camdWindowCloseLine(cameraId: Int): String {
    return "CLOSE ${normalizeCamdWindowCamera(cameraId)}"
}

object CamdWindowClient {
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun open(context: Context, cameraId: Int, x: Int, y: Int, w: Int, h: Int) {
        send(context, camdWindowOpenLine(cameraId, x, y, w, h))
    }

    fun close(context: Context, cameraId: Int) {
        send(context, camdWindowCloseLine(cameraId))
    }

    private fun send(context: Context, line: String) {
        val app = context.applicationContext
        io.execute {
            if (runCatching { exchange(line) }.isFailure) {
                main.post {
                    Toast.makeText(app, app.getString(R.string.camd_window_unreachable), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun exchange(line: String) {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", CAMD_WINDOW_PORT), 1500)
            socket.soTimeout = 20_000
            val out = socket.getOutputStream()
            out.write((line + "\n").toByteArray(Charsets.UTF_8))
            out.flush()
            val reply = socket.getInputStream().bufferedReader().readLine().orEmpty()
            if (reply.startsWith("ERR") || reply.contains("state=error")) {
                error(reply)
            }
        }
    }
}
