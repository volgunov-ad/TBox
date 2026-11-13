package vad.dashing.tbox

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.Locale
import java.util.concurrent.Executors

/** Tile that asks the Камеры ГУ app to open or close one camera window. */
const val CAMD_WINDOW_WIDGET_DATA_KEY = "camdWindowWidget"

const val CAMD_PACKAGE_NAME = "vad.dashing.camd"
const val CAMD_START_ACTION = "vad.dashing.camd.START"
const val CAMD_WINDOW_PORT = 8770
const val CAMD_ACTION_OPEN = "open"
const val CAMD_ACTION_CLOSE = "close"
const val CAMD_ACTION_CLOSE_ALL = "close_all"

val CAMD_WINDOW_CAMERA_IDS: List<Int> = listOf(8, 9, 4, 5, 0, 1, 3)
const val CAMD_WINDOW_DEFAULT_CAMERA = 8
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

fun normalizeCamdRotation(value: Int): Int = when (value) {
    90, 180, 270 -> value
    else -> 0
}

fun camdWindowOpenLine(cameraId: Int, x: Int, y: Int, w: Int, h: Int, rotation: Int = 0): String {
    val camera = normalizeCamdWindowCamera(cameraId)
    val geometry = normalizeCamdWindowGeometry(x, y, w, h)
    val rot = normalizeCamdRotation(rotation)
    // Older camd builds only accepted 6 tokens (no rotation). Omit trailing 0 so
    // Monitor still talks to a service that was installed before the rotation field.
    return if (rot == 0) {
        String.format(
            Locale.US,
            "OPEN %d %.4f %.4f %.4f %.4f",
            camera,
            geometry.x / 100.0,
            geometry.y / 100.0,
            geometry.w / 100.0,
            geometry.h / 100.0,
        )
    } else {
        String.format(
            Locale.US,
            "OPEN %d %.4f %.4f %.4f %.4f %d",
            camera,
            geometry.x / 100.0,
            geometry.y / 100.0,
            geometry.w / 100.0,
            geometry.h / 100.0,
            rot,
        )
    }
}

fun camdWindowCloseLine(cameraId: Int): String {
    return "CLOSE ${normalizeCamdWindowCamera(cameraId)}"
}

/** Status polls must not start the service. Every other line is a command. */
fun camdCommandWakesService(line: String): Boolean =
    !line.trim().equals("STATUS", ignoreCase = true)

fun isCamdPortClosed(error: Throwable?): Boolean = when (error) {
    is ConnectException -> true
    is SocketTimeoutException -> error.message.orEmpty().contains("connect", ignoreCase = true)
    else -> false
}

fun startCamdService(context: Context) {
    val app = context.applicationContext
    // Explicit FGS can wake a force-stopped camd when signature permission is granted.
    try {
        app.startForegroundService(
            Intent().setClassName(CAMD_PACKAGE_NAME, "$CAMD_PACKAGE_NAME.CamdService"),
        )
    } catch (_: Exception) {
        // Missing package, or signature START permission not granted to Monitor yet.
    }
    // Always also broadcast: BootReceiver is exported without the START permission and
    // starts CamdService from inside the camd package (which holds the permission).
    app.sendBroadcast(Intent(CAMD_START_ACTION).setPackage(CAMD_PACKAGE_NAME))
}

object CamdWindowClient {
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun open(context: Context, cameraId: Int, x: Int, y: Int, w: Int, h: Int, rotation: Int = 0) {
        send(context, camdWindowOpenLine(cameraId, x, y, w, h, rotation))
    }

    fun close(context: Context, cameraId: Int) {
        send(context, camdWindowCloseLine(cameraId))
    }

    fun fetchStatus(): CamdServiceStatus? =
        command("STATUS", readTimeoutMs = 2_000).getOrNull()?.let(::parseCamdStatusReply)

    fun command(
        line: String,
        readTimeoutMs: Int = 20_000,
        context: Context? = null,
    ): Result<String> {
        val first = exchange(line, readTimeoutMs, connectTimeoutMs = 1_500)
        if (first.isSuccess || context == null || !camdCommandWakesService(line)) return first
        if (!isCamdPortClosed(first.exceptionOrNull())) return first
        startCamdService(context)
        val deadline = System.nanoTime() + 4_000_000_000L
        var last = first
        while (System.nanoTime() < deadline) {
            Thread.sleep(250)
            last = exchange(line, readTimeoutMs, connectTimeoutMs = 500)
            if (last.isSuccess || !isCamdPortClosed(last.exceptionOrNull())) return last
        }
        return last
    }

    private fun exchange(line: String, readTimeoutMs: Int, connectTimeoutMs: Int): Result<String> =
        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", CAMD_WINDOW_PORT), connectTimeoutMs)
                socket.soTimeout = readTimeoutMs
                val out = socket.getOutputStream()
                out.write((line + "\n").toByteArray(Charsets.UTF_8))
                out.flush()
                val reply = socket.getInputStream().bufferedReader().readLine().orEmpty()
                if (reply.startsWith("ERR") || reply.contains("state=error")) {
                    throw CamdCommandException(reply.ifBlank { "empty" })
                }
                reply
            }
        }

    private fun send(context: Context, line: String) {
        val app = context.applicationContext
        io.execute {
            val result = command(line, context = app)
            val error = result.exceptionOrNull() ?: return@execute
            main.post {
                Toast.makeText(app, camdFailureToast(app, error), Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/** camd answered on TCP, but the command failed (bad line or state=error). */
class CamdCommandException(val reply: String) : IllegalStateException(reply)

/** User-facing toast: unreachable vs a real error string from the camera service. */
fun camdFailureToast(context: Context, error: Throwable): String {
    val reply = (error as? CamdCommandException)?.reply
        ?: error.message?.takeIf { it.startsWith("ERR") || it.contains("state=error") }
    if (reply.isNullOrBlank()) {
        return context.getString(R.string.camd_window_unreachable)
    }
    val detail = camdFailureDetail(reply)
    return if (detail.isNullOrBlank()) {
        context.getString(R.string.camd_window_unreachable)
    } else {
        context.getString(R.string.camd_window_command_failed, detail)
    }
}

fun camdFailureDetail(reply: String): String? {
    val text = reply.trim()
    if (text.isEmpty()) return null
    Regex("""error=(\S+)""").find(text)?.groupValues?.get(1)?.let { return it }
    if (text.startsWith("ERR")) {
        return text.removePrefix("ERR").trim().ifBlank { text }
    }
    return text
}

data class CamdServiceStatus(
    val windows: List<Int>,
    val avm: String,
    val keepAvm: Boolean,
)

data class CamdAutomationWindowArgs(
    val mode: String,
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
    val rotation: Int = 0,
)

fun isCamdPackageInstalled(packageManager: PackageManager): Boolean =
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(CAMD_PACKAGE_NAME, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(CAMD_PACKAGE_NAME, 0)
        }
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

/** `OK windows=none|4,5 avm=running|stopped keep_avm=0|1` */
fun parseCamdStatusReply(reply: String): CamdServiceStatus? {
    val text = reply.trim()
    if (!text.startsWith("OK")) return null
    val body = text.removePrefix("OK").trim()
    val windows = Regex("""windows=(\S+)""").find(body)?.groupValues?.get(1) ?: return null
    val avm = Regex("""avm=(\S+)""").find(body)?.groupValues?.get(1) ?: return null
    val keep = Regex("""keep_avm=([01])""").find(body)?.groupValues?.get(1) ?: return null
    val ids = if (windows == "none") {
        emptyList()
    } else {
        windows.split(',').mapNotNull { it.toIntOrNull() }
    }
    return CamdServiceStatus(windows = ids, avm = avm, keepAvm = keep == "1")
}

fun camdKeepAvmCommandLine(keepStopped: Boolean): String =
    if (keepStopped) "AVM ON" else "AVM OFF"

fun formatCamdAutomationWindowArgs(
    mode: String,
    x: Int,
    y: Int,
    w: Int,
    h: Int,
    rotation: Int = 0,
): String =
    if (mode == CAMD_ACTION_OPEN) {
        "$CAMD_ACTION_OPEN $x $y $w $h ${normalizeCamdRotation(rotation)}"
    } else {
        mode
    }

fun parseCamdAutomationWindowArgs(raw: String): CamdAutomationWindowArgs? {
    val parts = raw.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (parts.isEmpty()) return null
    val mode = parts[0].lowercase(Locale.US)
    if (mode != CAMD_ACTION_OPEN && mode != CAMD_ACTION_CLOSE && mode != CAMD_ACTION_CLOSE_ALL) {
        return null
    }
    if (mode != CAMD_ACTION_OPEN) {
        if (parts.size != 1) return null
        return CamdAutomationWindowArgs(
            mode = mode,
            x = CAMD_WINDOW_DEFAULT_X,
            y = CAMD_WINDOW_DEFAULT_Y,
            w = CAMD_WINDOW_DEFAULT_W,
            h = CAMD_WINDOW_DEFAULT_H,
        )
    }
    if (parts.size != 5 && parts.size != 6) return null
    val rotation = if (parts.size == 6) {
        val value = parts[5].toIntOrNull() ?: return null
        if (value != 0 && value != 90 && value != 180 && value != 270) return null
        value
    } else {
        0
    }
    return CamdAutomationWindowArgs(
        mode = mode,
        x = parts[1].toIntOrNull() ?: return null,
        y = parts[2].toIntOrNull() ?: return null,
        w = parts[3].toIntOrNull() ?: return null,
        h = parts[4].toIntOrNull() ?: return null,
        rotation = rotation,
    )
}

fun camdWindowGeometryIssue(x: Int, y: Int, w: Int, h: Int): String? {
    if (w !in 5..100 || h !in 5..100) return "Ширина и высота окна: 5–100 %"
    if (x !in 0..(100 - w) || y !in 0..(100 - h)) return "Окно выходит за экран"
    return null
}

/** Command line for an automation, or null when the saved fields are not a valid window request. */
fun camdAutomationCommandLine(cameraId: Int, raw: String): String? {
    val args = parseCamdAutomationWindowArgs(raw) ?: return null
    return when (args.mode) {
        CAMD_ACTION_CLOSE_ALL -> "CLOSE"
        CAMD_ACTION_CLOSE -> {
            if (cameraId !in CAMD_WINDOW_CAMERA_IDS) null else "CLOSE $cameraId"
        }
        CAMD_ACTION_OPEN -> {
            if (cameraId !in CAMD_WINDOW_CAMERA_IDS) return null
            if (camdWindowGeometryIssue(args.x, args.y, args.w, args.h) != null) return null
            camdWindowOpenLine(cameraId, args.x, args.y, args.w, args.h, args.rotation)
        }
        else -> null
    }
}
