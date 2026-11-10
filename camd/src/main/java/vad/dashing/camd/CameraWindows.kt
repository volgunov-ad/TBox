package vad.dashing.camd

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * One overlay per camera. Frames are read from files the root grabber writes.
 * Opening the same camera again only moves the window.
 */
class CameraWindows(private val context: Context) {

    private val lock = Any()
    private val openIds = LinkedHashSet<Int>()
    private val views = HashMap<Int, LiveWindow>()
    private val main = Handler(Looper.getMainLooper())
    private var avmStoppedByUs = false

    fun canDraw(): Boolean = Settings.canDrawOverlays(context)

    fun open(command: CamdCommand.Open): String = synchronized(lock) {
        val input = KnownInputs.byId(command.inputId) ?: return "state=error error=unknown_input"
        val moved = views.containsKey(command.inputId)
        openIds.add(command.inputId)
        writeDesired()
        val root = runCatching { prepare(needsAvmStop()) }
        if (root.isFailure) {
            if (!moved) openIds.remove(command.inputId)
            writeDesired()
            maybeStartAvm()
            return "state=error error=${root.exceptionOrNull()?.message}"
        }
        if (!Settings.canDrawOverlays(context)) waitOverlay()
        if (!Settings.canDrawOverlays(context)) {
            if (!moved) openIds.remove(command.inputId)
            writeDesired()
            maybeStartAvm()
            return "state=error error=overlay_permission"
        }
        val shown = show(command, input)
        if (!shown.ok) {
            if (!moved) openIds.remove(command.inputId)
            writeDesired()
            maybeStartAvm()
            return "state=error error=${shown.error}"
        }
        val state = if (moved) "moved" else "open"
        "state=$state input=${command.inputId}"
    }

    fun close(inputId: Int): String = synchronized(lock) {
        openIds.remove(inputId)
        writeDesired()
        hide(inputId)
        maybeStartAvm()
        "state=closed input=$inputId"
    }

    fun closeAll(): String = synchronized(lock) {
        val ids = openIds.toList()
        openIds.clear()
        writeDesired()
        ids.forEach { hide(it) }
        maybeStartAvm()
        "state=closed"
    }

    fun status(): String {
        val ids = synchronized(lock) { if (openIds.isEmpty()) "none" else openIds.joinToString(",") }
        val avm = getprop("init.svc.AVMapp").ifBlank { "unknown" }
        return "windows=$ids avm=$avm"
    }

    private fun needsAvmStop(): Boolean {
        return openIds.any { KnownInputs.byId(it)?.heldByAvm == true }
    }

    private fun prepare(stopAvm: Boolean) {
        val needGrant = !Settings.canDrawOverlays(context)
        val beat = File(GrabberScripts.BEAT)
        val fresh = beat.canRead() && System.currentTimeMillis() - beat.lastModified() < 4_000
        if (fresh && !stopAvm && !needGrant) return
        if (stopAvm) markAvmStopped()
        if (fresh && !stopAvm) {
            val nonce = "o${System.currentTimeMillis()}"
            arm(GrabberScripts.grantOverlay(nonce), nonce)
            return
        }
        installGrabber()
        val nonce = "g${System.currentTimeMillis()}"
        val script = GrabberScripts.launcher(nonce, stopAvm)
        arm(script, nonce)
        if (stopAvm) waitProp("init.svc.AVMapp", "running", present = false)
        if (!fresh) waitBeat()
    }

    private fun waitOverlay() {
        val deadline = System.currentTimeMillis() + 3_000
        while (System.currentTimeMillis() < deadline) {
            if (Settings.canDrawOverlays(context)) return
            Thread.sleep(200)
        }
    }

    private fun maybeStartAvm() {
        if (!avmStoppedByUs || needsAvmStop()) return
        val deadline = System.currentTimeMillis() + 3_000
        while (System.currentTimeMillis() < deadline) {
            val busy = listOf(4, 5, 8, 9).any { File(GrabberScripts.frame(it)).exists() }
            if (!busy) break
            Thread.sleep(100)
        }
        val nonce = "a${System.currentTimeMillis()}"
        arm(GrabberScripts.startAvm(nonce), nonce)
        clearAvmStopped()
        waitProp("init.svc.AVMapp", "running", present = true)
    }

    /** If the process died after stopping AVMapp, the next start puts it back. */
    fun recover() {
        synchronized(lock) {
            val marker = File(context.filesDir, "avm.stopped")
            if (!marker.exists()) return
            avmStoppedByUs = true
            writeDesired()
            runCatching { maybeStartAvm() }
        }
    }

    private fun installGrabber() {
        val dest = File(context.filesDir, "camd_grab.new")
        context.assets.open("camd_grab").use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        dest.setExecutable(true, false)
    }

    private fun markAvmStopped() {
        avmStoppedByUs = true
        val marker = File(context.filesDir, "avm.stopped")
        marker.parentFile?.mkdirs()
        marker.writeText("1\n")
    }

    private fun clearAvmStopped() {
        avmStoppedByUs = false
        File(context.filesDir, "avm.stopped").delete()
    }

    private fun show(command: CamdCommand.Open, input: CameraInput): ShowResult {
        val latch = CountDownLatch(1)
        val result = ShowResult()
        main.post {
            try {
                val existing = views[command.inputId]
                if (existing != null) {
                    place(existing.params, command)
                    windowManager().updateViewLayout(existing.root, existing.params)
                } else {
                    val live = LiveWindow(command.inputId, input)
                    place(live.params, command)
                    windowManager().addView(live.root, live.params)
                    views[command.inputId] = live
                    live.pump()
                }
                result.ok = true
            } catch (error: Throwable) {
                result.error = error.message ?: "overlay"
            } finally {
                latch.countDown()
            }
        }
        if (!latch.await(3, TimeUnit.SECONDS)) result.error = "overlay_timeout"
        return result
    }

    private fun hide(inputId: Int) {
        val latch = CountDownLatch(1)
        main.post {
            val live = views.remove(inputId)
            live?.running = false
            if (live != null) {
                runCatching { windowManager().removeView(live.root) }
            }
            latch.countDown()
        }
        latch.await(2, TimeUnit.SECONDS)
    }

    private fun place(params: WindowManager.LayoutParams, command: CamdCommand.Open) {
        val metrics = DisplayMetrics()
        windowManager().defaultDisplay.getRealMetrics(metrics)
        params.x = (command.x * metrics.widthPixels).toInt()
        params.y = (command.y * metrics.heightPixels).toInt()
        params.width = (command.w * metrics.widthPixels).toInt().coerceAtLeast(1)
        params.height = (command.h * metrics.heightPixels).toInt().coerceAtLeast(1)
    }

    private fun windowManager(): WindowManager {
        return context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    private fun writeDesired() {
        val file = File(GrabberScripts.DESIRED)
        file.parentFile?.mkdirs()
        val text = openIds.joinToString("\n")
        file.writeText(if (text.isEmpty()) "" else text + "\n")
    }

    private fun arm(script: String, nonce: String) {
        val autorun = File(DaemonScripts.AUTORUN)
        autorun.writeText(script)
        exec(arrayOf("chmod", "777", DaemonScripts.AUTORUN))
        File(DaemonScripts.AUTORUN_FLAG).createNewFile()
        exec(arrayOf("setprop", DaemonScripts.AUTORUN_PROP, ""))
        Thread.sleep(300)
        exec(arrayOf("setprop", DaemonScripts.AUTORUN_PROP, DaemonScripts.AUTORUN_FLAG))
        val deadline = System.currentTimeMillis() + 8_000
        val marker = File(GrabberScripts.ARM)
        while (System.currentTimeMillis() < deadline) {
            val text = runCatching { if (marker.canRead()) marker.readText() else "" }.getOrDefault("")
            if (text.contains(nonce)) return
            Thread.sleep(200)
        }
        throw IllegalStateException("mb_daemon не ответил")
    }

    private fun waitBeat() {
        val deadline = System.currentTimeMillis() + 8_000
        val beat = File(GrabberScripts.BEAT)
        while (System.currentTimeMillis() < deadline) {
            if (beat.canRead() && System.currentTimeMillis() - beat.lastModified() < 4_000) return
            Thread.sleep(200)
        }
        throw IllegalStateException("камера не запустилась, лог ${GrabberScripts.LOG}")
    }

    private fun waitProp(name: String, value: String, present: Boolean) {
        val deadline = System.currentTimeMillis() + 4_000
        while (System.currentTimeMillis() < deadline) {
            val now = getprop(name) == value
            if (now == present) return
            Thread.sleep(200)
        }
    }

    private fun getprop(name: String): String {
        return runCatching {
            val process = ProcessBuilder("getprop", name).redirectErrorStream(true).start()
            val text = process.inputStream.bufferedReader().readText().trim()
            process.waitFor(3, TimeUnit.SECONDS)
            text
        }.getOrDefault("")
    }

    private fun exec(cmd: Array<String>) {
        val process = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(8, TimeUnit.SECONDS)) {
            process.destroy()
            throw IllegalStateException("timeout ${cmd[0]}")
        }
        if (process.exitValue() != 0) {
            throw IllegalStateException("${cmd[0]} завершился ${process.exitValue()} $output".trim())
        }
    }

    private class ShowResult {
        var ok: Boolean = false
        var error: String = "overlay"
    }

    private inner class LiveWindow(val id: Int, val input: CameraInput) {
        val root: FrameLayout = FrameLayout(context)
        val image: ImageView = ImageView(context)
        val caption: TextView = TextView(context)
        val params: WindowManager.LayoutParams
        @Volatile var running: Boolean = true

        init {
            image.scaleType = ImageView.ScaleType.FIT_XY
            image.setBackgroundColor(Color.BLACK)
            root.addView(
                image,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            caption.text = "жду кадр ${input.id}"
            caption.setTextColor(Color.WHITE)
            caption.textSize = 18f
            caption.setPadding(16, 16, 16, 16)
            root.addView(caption, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ))
            val close = TextView(context)
            close.text = "✕"
            close.setTextColor(Color.WHITE)
            close.gravity = Gravity.CENTER
            close.textSize = 36f
            close.setBackgroundColor(0xCC000000.toInt())
            close.setOnClickListener {
                Thread({ this@CameraWindows.close(id) }, "camd-close-$id").start()
            }
            val px = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                88f,
                context.resources.displayMetrics
            ).toInt()
            val closeParams = FrameLayout.LayoutParams(px, px, Gravity.END or Gravity.TOP)
            closeParams.setMargins(0, 8, 8, 0)
            root.addView(close, closeParams)
            params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.START or Gravity.TOP
        }

        fun pump() {
            Thread({
                var modified = 0L
                var bitmap: Bitmap? = null
                while (running) {
                    val frame = readFrame(id, modified)
                    if (frame != null) {
                        modified = frame.modified
                        main.post {
                            if (!running) return@post
                            val current = bitmap
                            val target = if (current != null && current.width == frame.width && current.height == frame.height) {
                                current
                            } else {
                                Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.ARGB_8888).also { bitmap = it }
                            }
                            target.setPixels(frame.pixels, 0, frame.width, 0, 0, frame.width, frame.height)
                            image.setImageBitmap(target)
                            caption.visibility = View.GONE
                        }
                    }
                    Thread.sleep(80)
                }
            }, "camd-win-$id").apply { isDaemon = true }.start()
        }
    }

    private class Decoded(val width: Int, val height: Int, val pixels: IntArray, val modified: Long)

    private fun readFrame(id: Int, previousModified: Long): Decoded? {
        val file = File(GrabberScripts.frame(id))
        if (!file.canRead()) return null
        val modified = file.lastModified()
        if (modified == previousModified) return null
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        if (bytes.size < 16) return null
        val width = be(bytes, 0)
        val height = be(bytes, 4)
        if (width <= 0 || height <= 0 || width > 1920 || height > 1200) return null
        val pixelsSize = width * height
        if (bytes.size < 16 + pixelsSize * 4) return null
        val pixels = IntArray(pixelsSize)
        var offset = 16
        for (i in pixels.indices) {
            pixels[i] = be(bytes, offset)
            offset += 4
        }
        return Decoded(width, height, pixels, modified)
    }

    private fun be(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
    }
}
