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

/** Tile: toggle camd «keep stock AVM off» (`AVM ON` / `AVM OFF`). */
const val CAMD_KEEP_AVM_WIDGET_DATA_KEY = "camdKeepAvmWidget"

/** Tile: toggle camd dashcam master switch (`REC ON` / `REC OFF`). */
const val CAMD_RECORDER_WIDGET_DATA_KEY = "camdRecorderWidget"

/** Tile: one-shot camd dashcam emergency save (`REC SOS`). */
const val CAMD_RECORDER_SOS_WIDGET_DATA_KEY = "camdRecorderSosWidget"

/** Fixed center label on [CAMD_KEEP_AVM_WIDGET_DATA_KEY]. */
const val CAMD_KEEP_AVM_WIDGET_CENTER_LABEL = "AVM"

/** Fixed center label on [CAMD_RECORDER_WIDGET_DATA_KEY]. */
const val CAMD_RECORDER_WIDGET_CENTER_LABEL = "REC"

/** Fixed center label on [CAMD_RECORDER_SOS_WIDGET_DATA_KEY]. */
const val CAMD_RECORDER_SOS_WIDGET_CENTER_LABEL = "SOS"

/**
 * Visual «on» for the keep-AVM tile: active when keep-off is **off** (stock AVM works).
 * Inactive when keep-off is on (stock AVM stopped).
 */
fun camdKeepAvmWidgetLooksActive(keepAvm: Boolean): Boolean = !keepAvm

/** How the REC tile paints its center label (recording overrides config colors). */
enum class CamdRecorderWidgetPaint {
    /** No STATUS / missing rec_* — dim inactive. */
    Unknown,
    /** Master off. */
    Disabled,
    /** Master on, no media / not writing. */
    EnabledIdle,
    /** Segments actually being written — always red. */
    Recording,
}

fun camdRecorderWidgetPaint(
    recorderEnabled: Boolean?,
    recordingActive: Boolean?,
): CamdRecorderWidgetPaint {
    if (recorderEnabled == null && recordingActive == null) return CamdRecorderWidgetPaint.Unknown
    if (recordingActive == true) return CamdRecorderWidgetPaint.Recording
    return if (recorderEnabled == true) {
        CamdRecorderWidgetPaint.EnabledIdle
    } else {
        CamdRecorderWidgetPaint.Disabled
    }
}

const val CAMD_PACKAGE_NAME = "vad.dashing.camd"
const val CAMD_START_ACTION = "vad.dashing.camd.START"
const val CAMD_WINDOW_PORT = 8770
const val CAMD_ACTION_OPEN = "open"
const val CAMD_ACTION_CLOSE = "close"
const val CAMD_ACTION_CLOSE_ALL = "close_all"

val CAMD_WINDOW_CAMERA_IDS: List<Int> = listOf(8, 9, 4, 5, 0, 1, 3)
/** camd stock calibrated view ids (vendor AVM mesh views of inputs 8/5/9/4). */
val CAMD_WINDOW_STOCK_CAMERA_IDS: IntRange = 40..45
const val CAMD_WINDOW_DEFAULT_CAMERA = 8
const val CAMD_WINDOW_DEFAULT_X = 2
const val CAMD_WINDOW_DEFAULT_Y = 55
const val CAMD_WINDOW_DEFAULT_W = 40
const val CAMD_WINDOW_DEFAULT_H = 40

/** Frame content modes for the camera tile / automations. */
const val CAMD_FRAME_MODE_AS_IS = 0
const val CAMD_FRAME_MODE_CUSTOM = 1
const val CAMD_FRAME_MODE_PRESET = 2

/** camd preset names are the API address (OPEN preset=name): lowercase latin, digits, underscore. */
const val CAMD_PRESET_NAME_MAX = 32
val CAMD_PRESET_NAME_REGEX = Regex("^[a-z0-9_]{1,32}$")

fun isValidCamdPresetName(name: String): Boolean = CAMD_PRESET_NAME_REGEX.matches(name)

fun camdPresetNameIssue(name: String): String? {
    if (name.isBlank()) return ruEn("Укажите имя пресета", "Enter a preset name")
    if (!isValidCamdPresetName(name)) {
        return ruEn(
            "Имя пресета: строчная латиница, цифры и _, 1–32 символа",
            "Preset name: lowercase latin, digits and _, 1–32 characters",
        )
    }
    return null
}

fun normalizeCamdWindowFrameMode(value: Int): Int = when (value) {
    CAMD_FRAME_MODE_CUSTOM, CAMD_FRAME_MODE_PRESET -> value
    else -> CAMD_FRAME_MODE_AS_IS
}

data class CamdWindowGeometry(
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
)

/** Sub-rectangle of the frame in percent; the camera app stretches it to fill the window. */
data class CamdWindowCrop(
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

fun normalizeCamdWindowCrop(x: Int, y: Int, w: Int, h: Int): CamdWindowCrop {
    val width = w.coerceIn(5, 100)
    val height = h.coerceIn(5, 100)
    return CamdWindowCrop(
        x = x.coerceIn(0, 100 - width),
        y = y.coerceIn(0, 100 - height),
        w = width,
        h = height,
    )
}

fun normalizeCamdWindowCamera(id: Int): Int {
    return if (id == 0 || id == 1 || id == 3 || id == 4 || id == 5 || id == 8 || id == 9 ||
        id in CAMD_WINDOW_STOCK_CAMERA_IDS
    ) {
        id
    } else {
        CAMD_WINDOW_DEFAULT_CAMERA
    }
}

fun normalizeCamdRotation(value: Int): Int = when (value) {
    90, 180, 270 -> value
    else -> 0
}

fun camdWindowOpenLine(
    cameraId: Int,
    x: Int,
    y: Int,
    w: Int,
    h: Int,
    rotation: Int = 0,
    flipH: Boolean = false,
    flipV: Boolean = false,
    crop: CamdWindowCrop? = null,
    circle: Boolean = false,
): String {
    val camera = normalizeCamdWindowCamera(cameraId)
    val geometry = normalizeCamdWindowGeometry(x, y, w, h)
    val rot = normalizeCamdRotation(rotation)
    val fx = if (flipH) 1 else 0
    val fy = if (flipV) 1 else 0
    // Older camd: 6 tokens only; then 7 with rotation. Plain 6-token line is "as is"
    // since camd 0.5.0 (no rot/flip/crop). Flips require the rotation slot (even 0),
    // same as camd's parser. camd before 0.4.0 answers ERR bad_command to crop=,
    // before 0.5.0 to shape= — the toast shows the detail.
    val line = when {
        fx == 0 && fy == 0 && rot == 0 -> String.format(
            Locale.US,
            "OPEN %d %.4f %.4f %.4f %.4f",
            camera,
            geometry.x / 100.0,
            geometry.y / 100.0,
            geometry.w / 100.0,
            geometry.h / 100.0,
        )
        fx == 0 && fy == 0 -> String.format(
            Locale.US,
            "OPEN %d %.4f %.4f %.4f %.4f %d",
            camera,
            geometry.x / 100.0,
            geometry.y / 100.0,
            geometry.w / 100.0,
            geometry.h / 100.0,
            rot,
        )
        fy == 0 -> String.format(
            Locale.US,
            "OPEN %d %.4f %.4f %.4f %.4f %d %d",
            camera,
            geometry.x / 100.0,
            geometry.y / 100.0,
            geometry.w / 100.0,
            geometry.h / 100.0,
            rot,
            fx,
        )
        else -> String.format(
            Locale.US,
            "OPEN %d %.4f %.4f %.4f %.4f %d %d %d",
            camera,
            geometry.x / 100.0,
            geometry.y / 100.0,
            geometry.w / 100.0,
            geometry.h / 100.0,
            rot,
            fx,
            fy,
        )
    }
    return line + camdCropSuffix(crop) + camdShapeSuffix(circle)
}

/** Preset form: the camera comes from the preset; caller sets only the window rect. */
fun camdWindowPresetOpenLine(
    presetName: String,
    x: Int,
    y: Int,
    w: Int,
    h: Int,
    circle: Boolean = false,
): String? {
    if (!isValidCamdPresetName(presetName)) return null
    val geometry = normalizeCamdWindowGeometry(x, y, w, h)
    val line = String.format(
        Locale.US,
        "OPEN preset=%s %.4f %.4f %.4f %.4f",
        presetName,
        geometry.x / 100.0,
        geometry.y / 100.0,
        geometry.w / 100.0,
        geometry.h / 100.0,
    )
    return line + camdShapeSuffix(circle)
}

private fun camdCropSuffix(crop: CamdWindowCrop?): String {
    if (crop == null) return ""
    return String.format(
        Locale.US,
        " crop=%.4f,%.4f,%.4f,%.4f",
        crop.x / 100.0,
        crop.y / 100.0,
        crop.w / 100.0,
        crop.h / 100.0,
    )
}

private fun camdShapeSuffix(circle: Boolean): String =
    if (circle) " shape=circle" else ""

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
    // Always also broadcast: StartReceiver (signature START) starts CamdService
    // from inside the camd package. Same-signature Monitor holds the permission.
    app.sendBroadcast(Intent(CAMD_START_ACTION).setPackage(CAMD_PACKAGE_NAME))
}

/** Boot / NLS early-start: wake «Камеры ГУ» only when the package is installed. */
fun wakeCamdServiceIfInstalled(context: Context) {
    if (isCamdPackageInstalled(context.packageManager)) {
        startCamdService(context)
    }
}

object CamdWindowClient {
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun open(
        context: Context,
        cameraId: Int,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        rotation: Int = 0,
        flipH: Boolean = false,
        flipV: Boolean = false,
        crop: CamdWindowCrop? = null,
        circle: Boolean = false,
    ) {
        send(context, camdWindowOpenLine(cameraId, x, y, w, h, rotation, flipH, flipV, crop, circle))
    }

    fun openPreset(
        context: Context,
        presetName: String,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        circle: Boolean = false,
    ) {
        val line = camdWindowPresetOpenLine(presetName, x, y, w, h, circle) ?: return
        send(context, line)
    }

    fun close(context: Context, cameraId: Int) {
        send(context, camdWindowCloseLine(cameraId))
    }

    fun fetchStatus(): CamdServiceStatus? =
        command("STATUS", readTimeoutMs = 2_000).getOrNull()?.let(::parseCamdStatusReply)

    fun fetchPresets(): List<CamdPresetInfo>? =
        command("PRESETS", readTimeoutMs = 2_000).getOrNull()?.let(::parseCamdPresetsReply)

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

    /**
     * Fire-and-forget `REC SOS`: the reply is one of the stable `state=rec_sos …` /
     * `state=error error=…` classes, so the toast is built from the whole result
     * (success included) instead of the error-only [send].
     */
    fun recorderSos(context: Context) {
        val app = context.applicationContext
        io.execute {
            val result = command(camdRecorderSosCommandLine(), context = app)
            main.post {
                Toast.makeText(app, camdRecorderSosToast(app, result), Toast.LENGTH_SHORT).show()
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
    camdKnownErrorToast(context, reply)?.let { return it }
    val detail = camdFailureDetail(reply)
    return if (detail.isNullOrBlank()) {
        context.getString(R.string.camd_window_unreachable)
    } else {
        context.getString(R.string.camd_window_command_failed, detail)
    }
}

/**
 * Friendly copy for stable camd `error=` codes (e.g. `OK state=error error=no_cameras`
 * from REC ON). Null → fall back to [camdFailureDetail] wrapping.
 */
fun camdKnownErrorToast(context: Context, reply: String): String? {
    val code = Regex("""error=(\S+)""").find(reply)?.groupValues?.get(1)
        ?: reply.removePrefix("ERR").trim().takeIf { reply.trimStart().startsWith("ERR") }
    return when (code) {
        "no_cameras" -> context.getString(R.string.camd_recorder_error_no_cameras)
        "no_folder" -> context.getString(R.string.camd_recorder_error_no_folder)
        else -> null
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
    /** Master switch (`REC ON`); null when the camd build omits `rec_enabled=`. */
    val recorderEnabled: Boolean? = null,
    /** Segments are actually being written (`rec_active=`); null when omitted. Not the same as enabled. */
    val recordingActive: Boolean? = null,
)

data class CamdAutomationWindowArgs(
    val mode: String,
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
    val rotation: Int = 0,
    val flipH: Boolean = false,
    val flipV: Boolean = false,
    val crop: CamdWindowCrop? = null,
    val presetName: String? = null,
    val circle: Boolean = false,
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

/** `OK windows=none|4,5 avm=running|stopped keep_avm=0|1 [rec_enabled=0|1 rec_active=0|1]` */
fun parseCamdStatusReply(reply: String): CamdServiceStatus? {
    val text = reply.trim()
    if (!text.startsWith("OK")) return null
    val body = text.removePrefix("OK").trim()
    val windows = Regex("""windows=(\S+)""").find(body)?.groupValues?.get(1) ?: return null
    val avm = Regex("""avm=(\S+)""").find(body)?.groupValues?.get(1) ?: return null
    val keep = Regex("""keep_avm=([01])""").find(body)?.groupValues?.get(1) ?: return null
    val recEnabled = Regex("""rec_enabled=([01])""").find(body)?.groupValues?.get(1)
    val recActive = Regex("""rec_active=([01])""").find(body)?.groupValues?.get(1)
    val ids = if (windows == "none") {
        emptyList()
    } else {
        windows.split(',').mapNotNull { it.toIntOrNull() }
    }
    return CamdServiceStatus(
        windows = ids,
        avm = avm,
        keepAvm = keep == "1",
        recorderEnabled = recEnabled?.let { it == "1" },
        recordingActive = recActive?.let { it == "1" },
    )
}

fun camdKeepAvmCommandLine(keepStopped: Boolean): String =
    if (keepStopped) "AVM ON" else "AVM OFF"

/** `REC ON` / `REC OFF` — dashcam master switch (not recording-active). */
fun camdRecorderCommandLine(enabled: Boolean): String =
    if (enabled) "REC ON" else "REC OFF"

/** `REC SOS` — dashcam emergency save (camd 0.8.6+; alias `REC SAVE`). */
fun camdRecorderSosCommandLine(): String = "REC SOS"

/** How a `REC SOS` tap resolved (reply classes documented by camd 0.8.6+). */
enum class CamdRecorderSosResult {
    /** `OK state=rec_sos started` — save kicked off. */
    Started,
    /** `OK state=rec_sos busy` — a save is already running. */
    Busy,
    /** `OK state=error error=rec_off` — dashcam master switch is off. */
    RecorderOff,
    /** `OK state=error error=no_sos_folder` — SOS folder missing. */
    NoSosFolder,
    /** `ERR …` (camd < 0.8.6 answers `ERR bad_command`) or an unknown reply. */
    Failed,
}

fun parseCamdRecorderSosResult(result: Result<String>): CamdRecorderSosResult {
    val reply = result.getOrNull()
        ?: (result.exceptionOrNull() as? CamdCommandException)?.reply
        ?: return CamdRecorderSosResult.Failed
    return when {
        "state=rec_sos started" in reply -> CamdRecorderSosResult.Started
        "state=rec_sos busy" in reply -> CamdRecorderSosResult.Busy
        "error=rec_off" in reply -> CamdRecorderSosResult.RecorderOff
        "error=no_sos_folder" in reply -> CamdRecorderSosResult.NoSosFolder
        else -> CamdRecorderSosResult.Failed
    }
}

/** Result toast for the SOS tile; unknown failures fall back to [camdFailureToast]. */
fun camdRecorderSosToast(context: Context, result: Result<String>): String =
    when (parseCamdRecorderSosResult(result)) {
        CamdRecorderSosResult.Started -> context.getString(R.string.camd_recorder_sos_started)
        CamdRecorderSosResult.Busy -> context.getString(R.string.camd_recorder_sos_busy)
        CamdRecorderSosResult.RecorderOff -> context.getString(R.string.camd_recorder_error_rec_off)
        CamdRecorderSosResult.NoSosFolder -> context.getString(R.string.camd_recorder_error_no_sos_folder)
        CamdRecorderSosResult.Failed -> {
            val error = result.exceptionOrNull() ?: CamdCommandException(result.getOrNull().orEmpty())
            camdFailureToast(context, error)
        }
    }

fun formatCamdAutomationWindowArgs(
    mode: String,
    x: Int,
    y: Int,
    w: Int,
    h: Int,
    rotation: Int = 0,
    flipH: Boolean = false,
    flipV: Boolean = false,
    crop: CamdWindowCrop? = null,
    presetName: String? = null,
    circle: Boolean = false,
): String {
    if (mode != CAMD_ACTION_OPEN) return mode
    val positional = if (presetName != null) {
        "$CAMD_ACTION_OPEN preset=$presetName $x $y $w $h"
    } else {
        val rot = normalizeCamdRotation(rotation)
        val fx = if (flipH) 1 else 0
        val fy = if (flipV) 1 else 0
        when {
            fx == 0 && fy == 0 && rot == 0 -> "$CAMD_ACTION_OPEN $x $y $w $h"
            fx == 0 && fy == 0 -> "$CAMD_ACTION_OPEN $x $y $w $h $rot"
            fy == 0 -> "$CAMD_ACTION_OPEN $x $y $w $h $rot $fx"
            else -> "$CAMD_ACTION_OPEN $x $y $w $h $rot $fx $fy"
        } + if (crop != null) " crop=${crop.x},${crop.y},${crop.w},${crop.h}" else ""
    }
    return positional + camdShapeSuffix(circle)
}

fun isCamdCropToken(token: String): Boolean =
    token.startsWith("crop=", ignoreCase = true)

fun isCamdShapeToken(token: String): Boolean =
    token.startsWith("shape=", ignoreCase = true)

fun isCamdPresetToken(token: String): Boolean =
    token.startsWith("preset=", ignoreCase = true)

private fun isCamdNamedAutomationToken(token: String): Boolean =
    isCamdCropToken(token) || isCamdShapeToken(token) || isCamdPresetToken(token)

/** `crop=x,y,w,h` in percent, same bounds as the window. */
fun parseCamdWindowCropToken(raw: String): CamdWindowCrop? {
    val body = raw.substringAfter('=', "").trim()
    val fields = body.split(",")
    if (fields.size != 4) return null
    val x = fields[0].trim().toIntOrNull() ?: return null
    val y = fields[1].trim().toIntOrNull() ?: return null
    val w = fields[2].trim().toIntOrNull() ?: return null
    val h = fields[3].trim().toIntOrNull() ?: return null
    val crop = CamdWindowCrop(x, y, w, h)
    if (camdWindowCropIssue(x, y, w, h) != null) return null
    return crop
}

fun parseCamdAutomationWindowArgs(raw: String): CamdAutomationWindowArgs? {
    val parts = raw.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (parts.isEmpty()) return null
    val mode = parts[0].lowercase(Locale.US)
    if (mode != CAMD_ACTION_OPEN && mode != CAMD_ACTION_CLOSE && mode != CAMD_ACTION_CLOSE_ALL) {
        return null
    }
    val cropTokens = parts.filter { isCamdCropToken(it) }
    val shapeTokens = parts.filter { isCamdShapeToken(it) }
    val presetTokens = parts.filter { isCamdPresetToken(it) }
    if (cropTokens.size > 1 || shapeTokens.size > 1 || presetTokens.size > 1) return null
    val circle = shapeTokens.firstOrNull()?.let { token ->
        when (token.substringAfter('=', "").lowercase(Locale.US)) {
            "circle" -> true
            else -> return null
        }
    } ?: false
    val presetName = presetTokens.firstOrNull()?.substringAfter('=', "")
    val positional = parts.filterNot { isCamdNamedAutomationToken(it) }
    if (mode != CAMD_ACTION_OPEN) {
        if (positional.size != 1 || cropTokens.isNotEmpty() || presetName != null || shapeTokens.isNotEmpty()) {
            return null
        }
        return CamdAutomationWindowArgs(
            mode = mode,
            x = CAMD_WINDOW_DEFAULT_X,
            y = CAMD_WINDOW_DEFAULT_Y,
            w = CAMD_WINDOW_DEFAULT_W,
            h = CAMD_WINDOW_DEFAULT_H,
        )
    }
    if (presetName != null) {
        if (!isValidCamdPresetName(presetName)) return null
        if (cropTokens.isNotEmpty()) return null
        if (positional.size != 5) return null
        return CamdAutomationWindowArgs(
            mode = mode,
            x = positional[1].toIntOrNull() ?: return null,
            y = positional[2].toIntOrNull() ?: return null,
            w = positional[3].toIntOrNull() ?: return null,
            h = positional[4].toIntOrNull() ?: return null,
            presetName = presetName,
            circle = circle,
        )
    }
    val crop = cropTokens.firstOrNull()?.let { parseCamdWindowCropToken(it) ?: return null }
    if (positional.size !in 5..8) return null
    val rotation = if (positional.size >= 6) {
        val value = positional[5].toIntOrNull() ?: return null
        if (value != 0 && value != 90 && value != 180 && value != 270) return null
        value
    } else {
        0
    }
    val flipH = if (positional.size >= 7) {
        when (positional[6].toIntOrNull()) {
            0 -> false
            1 -> true
            else -> return null
        }
    } else {
        false
    }
    val flipV = if (positional.size >= 8) {
        when (positional[7].toIntOrNull()) {
            0 -> false
            1 -> true
            else -> return null
        }
    } else {
        false
    }
    return CamdAutomationWindowArgs(
        mode = mode,
        x = positional[1].toIntOrNull() ?: return null,
        y = positional[2].toIntOrNull() ?: return null,
        w = positional[3].toIntOrNull() ?: return null,
        h = positional[4].toIntOrNull() ?: return null,
        rotation = rotation,
        flipH = flipH,
        flipV = flipV,
        crop = crop,
        circle = circle,
    )
}

data class CamdPresetInfo(
    val cameraId: Int,
    val name: String,
    val builtin: Boolean,
)

/** `OK presets=cam:name:kind,...` / `OK presets=none` from the camd PRESETS command. */
fun parseCamdPresetsReply(reply: String): List<CamdPresetInfo>? {
    val text = reply.trim()
    if (!text.startsWith("OK")) return null
    val body = text.removePrefix("OK").trim()
    val match = Regex("""presets=(\S+)""").find(body) ?: return null
    val value = match.groupValues[1]
    if (value == "none") return emptyList()
    return value.split(',').map { item ->
        val fields = item.split(':')
        if (fields.size != 3) return null
        val cameraId = fields[0].toIntOrNull() ?: return null
        val name = fields[1]
        if (!isValidCamdPresetName(name)) return null
        val builtin = when (fields[2]) {
            "builtin" -> true
            "user" -> false
            else -> return null
        }
        CamdPresetInfo(cameraId, name, builtin)
    }
}

fun camdWindowGeometryIssue(x: Int, y: Int, w: Int, h: Int): String? {
    if (w !in 5..100 || h !in 5..100) {
        return ruEn("Ширина и высота окна: 5–100 %", "Window width and height: 5–100%")
    }
    if (x !in 0..(100 - w) || y !in 0..(100 - h)) {
        return ruEn("Окно выходит за экран", "Window goes off-screen")
    }
    return null
}

fun camdWindowCropIssue(x: Int, y: Int, w: Int, h: Int): String? {
    if (w !in 5..100 || h !in 5..100) {
        return ruEn("Ширина и высота вырезки: 5–100 %", "Crop width and height: 5–100%")
    }
    if (x !in 0..(100 - w) || y !in 0..(100 - h)) {
        return ruEn("Вырезка выходит за кадр", "Crop goes outside the frame")
    }
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
            if (camdWindowGeometryIssue(args.x, args.y, args.w, args.h) != null) return null
            if (args.circle && args.presetName == null && cameraId !in CAMD_WINDOW_CAMERA_IDS) return null
            val preset = args.presetName
            if (preset != null) {
                if (camdPresetNameIssue(preset) != null) return null
                return camdWindowPresetOpenLine(preset, args.x, args.y, args.w, args.h, args.circle)
            }
            if (cameraId !in CAMD_WINDOW_CAMERA_IDS) return null
            if (args.crop != null && camdWindowCropIssue(args.crop.x, args.crop.y, args.crop.w, args.crop.h) != null) {
                return null
            }
            camdWindowOpenLine(
                cameraId,
                args.x,
                args.y,
                args.w,
                args.h,
                args.rotation,
                args.flipH,
                args.flipV,
                args.crop,
                args.circle,
            )
        }
        else -> null
    }
}
