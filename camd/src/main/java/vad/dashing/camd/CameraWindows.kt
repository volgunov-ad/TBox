package vad.dashing.camd

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * One overlay per camera. The root grabber publishes raw UYVY in a shared file.
 * This window paints it with a GPU shader. Opening the same camera again only moves the window.
 */
class CameraWindows(private val context: Context) {

    private val lock = Any()
    private val openIds = LinkedHashSet<Int>()
    private val views = ConcurrentHashMap<Int, LiveWindow>()
    private val main = Handler(Looper.getMainLooper())
    private val prefs: SharedPreferences =
        context.getSharedPreferences("camd", Context.MODE_PRIVATE)
    private var avmStoppedByUs = false
    private val closeExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "camd-close").apply { isDaemon = true }
    }
    private val propCache = PropCache()

    fun showCloseButton(): Boolean = prefs.getBoolean(SHOW_CLOSE_BUTTON_KEY, true)

    fun tapToClose(): Boolean = prefs.getBoolean(TAP_TO_CLOSE_KEY, false)

    /** Persists window UI options and applies them to already open windows. */
    fun setUiOptions(showCloseButton: Boolean, tapToClose: Boolean) = synchronized(lock) {
        prefs.edit()
            .putBoolean(SHOW_CLOSE_BUTTON_KEY, showCloseButton)
            .putBoolean(TAP_TO_CLOSE_KEY, tapToClose)
            .apply()
        views.values.forEach { live ->
            main.post { live.applyUiOptions(showCloseButton, tapToClose) }
        }
    }

    fun keepAvmStopped(): Boolean = prefs.getBoolean(KEEP_AVM_STOPPED_KEY, false)

    /** Stop AVMapp at startup and do not start it when the last surround window closes. */
    fun setKeepAvmStopped(keep: Boolean): String = synchronized(lock) {
        prefs.edit().putBoolean(KEEP_AVM_STOPPED_KEY, keep).apply()
        if (keep) {
            markAvmStopped()
            if (getprop("init.svc.AVMapp") == "running") {
                val nonce = "k${System.currentTimeMillis()}"
                arm(GrabberScripts.stopAvm(nonce), nonce)
                waitProp("init.svc.AVMapp", "running", present = false)
            }
            return "state=avm_held"
        }
        val ids = openIds.toList()
        openIds.clear()
        writeDesired()
        ids.forEach { hide(it) }
        avmStoppedByUs = true
        maybeStartAvm()
        "state=avm_restore"
    }

    fun canDraw(): Boolean = Settings.canDrawOverlays(context)

    fun lensCorrection(inputId: Int): CamdLensCorrection =
        CamdLensCorrectionStore.load(prefs, inputId)

    fun presets(cameraId: Int? = null): List<CamdPreset> {
        val all = CamdPresetStore.all(prefs)
        return if (cameraId == null) all else all.filter { it.cameraId == cameraId }
    }

    fun preset(name: String): CamdPreset? = CamdPresetStore.byName(prefs, name)

    fun saveUserPreset(preset: CamdPreset): String? = CamdPresetStore.saveUser(prefs, preset)

    fun saveBuiltinPreset(
        name: String,
        rotation: Int,
        flipH: Boolean,
        flipV: Boolean,
        crop: CamdCrop,
    ): Boolean = CamdPresetStore.saveBuiltinOverride(prefs, name, rotation, flipH, flipV, crop)

    fun deleteUserPreset(name: String): Boolean = CamdPresetStore.deleteUser(prefs, name)

    /** Persist per-camera visual correction and push it to an open overlay if any. */
    fun setLensCorrection(inputId: Int, value: CamdLensCorrection): CamdLensCorrection = synchronized(lock) {
        val clamped = CamdLensCorrection.clamp(value)
        CamdLensCorrectionStore.save(prefs, inputId, clamped)
        val live = views[inputId]
        if (live != null) {
            main.post { live.setLensCorrection(clamped) }
        }
        clamped
    }

    fun open(command: CamdCommand.Open): String = synchronized(lock) {
        val resolved = resolvePreset(command) ?: return "state=error error=unknown_preset"
        val input = KnownInputs.byId(resolved.inputId) ?: return "state=error error=unknown_input"
        val moved = views.containsKey(resolved.inputId)
        openIds.add(resolved.inputId)
        writeDesired()
        val root = runCatching { prepare(shouldStopAvmNow()) }
        if (root.isFailure) {
            if (!moved) openIds.remove(resolved.inputId)
            writeDesired()
            maybeStartAvm()
            return "state=error error=${root.exceptionOrNull()?.message}"
        }
        if (!Settings.canDrawOverlays(context)) waitOverlay()
        if (!Settings.canDrawOverlays(context)) {
            if (!moved) openIds.remove(resolved.inputId)
            writeDesired()
            maybeStartAvm()
            return "state=error error=overlay_permission"
        }
        val shown = show(resolved, input)
        if (!shown.ok) {
            if (!moved) openIds.remove(resolved.inputId)
            writeDesired()
            maybeStartAvm()
            return "state=error error=${shown.error}"
        }
        val state = if (moved) "moved" else "open"
        "state=$state input=${resolved.inputId}"
    }

    /** Preset form: settings come from the store; the caller can only set the window rect. */
    private fun resolvePreset(command: CamdCommand.Open): CamdCommand.Open? {
        val name = command.presetName ?: return command
        val preset = CamdPresetStore.byName(prefs, name) ?: return null
        return command.copy(
            inputId = preset.cameraId,
            rotation = preset.rotation,
            flipH = preset.flipH,
            flipV = preset.flipV,
            crop = if (preset.crop.isIdentity()) null else preset.crop,
        )
    }

    /** One-line preset listing for the PRESETS command: `presets=cam:name:kind,...` */
    fun presetsListing(): String {
        val items = CamdPresetStore.all(prefs)
        if (items.isEmpty()) return "presets=none"
        return "presets=" + items.joinToString(",") { preset ->
            "${preset.cameraId}:${preset.name}:${if (preset.builtin) "builtin" else "user"}"
        }
    }

    fun exportSettings(): String = CamdBackup.exportJson(prefs)

    fun importSettings(text: String): CamdImportSummary? = CamdBackup.importJson(prefs, text)

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
        val keep = if (keepAvmStopped()) "1" else "0"
        return "windows=$ids avm=$avm keep_avm=$keep"
    }

    private fun shouldStopAvmNow(): Boolean {
        if (!needsAvmStop()) return false
        return getprop("init.svc.AVMapp") == "running"
    }

    private fun needsAvmStop(): Boolean {
        return openIds.any { KnownInputs.byId(it)?.heldByAvm == true }
    }

    private fun prepare(stopAvm: Boolean) {
        val needGrant = !Settings.canDrawOverlays(context)
        val beat = File(GrabberScripts.BEAT)
        val versionOk = runCatching {
            File(GrabberScripts.VERSION_FILE).readText().trim() == GrabberScripts.GRAB_VERSION
        }.getOrDefault(false)
        val fresh = beat.canRead() &&
            System.currentTimeMillis() - beat.lastModified() < 4_000 &&
            versionOk
        if (fresh && !stopAvm && !needGrant) return
        if (stopAvm) markAvmStopped()
        if (fresh && stopAvm && !needGrant) {
            val nonce = "s${System.currentTimeMillis()}"
            arm(GrabberScripts.stopAvm(nonce), nonce)
            waitProp("init.svc.AVMapp", "running", present = false)
            return
        }
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
        if (keepAvmStopped() || !avmStoppedByUs || needsAvmStop()) return
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
            if (keepAvmStopped()) {
                markAvmStopped()
                if (getprop("init.svc.AVMapp") == "running") {
                    runCatching {
                        val nonce = "k${System.currentTimeMillis()}"
                        arm(GrabberScripts.stopAvm(nonce), nonce)
                    }
                }
                return
            }
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
                    existing.setRotation(command.rotation)
                    existing.setFlip(command.flipH, command.flipV)
                    existing.setLensCorrection(CamdLensCorrectionStore.load(prefs, command.inputId))
                    existing.setCrop(command.crop ?: CamdCrop.IDENTITY)
                    existing.setShape(command.shape)
                    windowManager().updateViewLayout(existing.root, existing.params)
                } else {
                    val live = LiveWindow(
                        command.inputId,
                        input,
                        command.rotation,
                        command.flipH,
                        command.flipV,
                        CamdLensCorrectionStore.load(prefs, command.inputId),
                        command.crop ?: CamdCrop.IDENTITY,
                        command.shape,
                    )
                    place(live.params, command)
                    windowManager().addView(live.root, live.params)
                    views[command.inputId] = live
                    live.start()
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
            live?.stop()
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
            if ((readProp(name) == value) == present) {
                propCache.invalidate()
                return
            }
            Thread.sleep(200)
        }
        propCache.invalidate()
    }

    private fun getprop(name: String): String {
        return propCache.get(name) { key -> readProp(key) }
    }

    private fun readProp(name: String): String {
        return runCatching { CamdShell.exec(arrayOf("getprop", name), timeoutMs = 3_000) }.getOrDefault("")
    }

    private fun exec(cmd: Array<String>) {
        CamdShell.exec(cmd)
    }

    private class ShowResult {
        var ok: Boolean = false
        var error: String = "overlay"
    }

    private inner class LiveWindow(
        val id: Int,
        val input: CameraInput,
        rotation: Int,
        flipH: Boolean,
        flipV: Boolean,
        lens: CamdLensCorrection,
        crop: CamdCrop,
        shape: CamdShape,
    ) {
        val root: FrameLayout = FrameLayout(context)
        val picture: TextureView = TextureView(context)
        val caption: TextView = TextView(context)
        val params: WindowManager.LayoutParams
        private val closeButton: TextView
        private var tapToCloseEnabled: Boolean = prefs.getBoolean(TAP_TO_CLOSE_KEY, false)
        private val renderer: UyvyRenderer

        init {
            picture.isOpaque = false
            applyShapeBackground(shape)
            root.addView(
                picture,
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
            closeButton = TextView(context)
            closeButton.text = "✕"
            closeButton.setTextColor(Color.WHITE)
            closeButton.gravity = Gravity.CENTER
            closeButton.textSize = 36f
            closeButton.setShadowLayer(10f, 0f, 0f, Color.BLACK)
            closeButton.setBackgroundColor(Color.TRANSPARENT)
            closeButton.setOnClickListener {
                closeExecutor.execute { this@CameraWindows.close(id) }
            }
            val px = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                88f,
                context.resources.displayMetrics
            ).toInt()
            val closeParams = FrameLayout.LayoutParams(px, px, Gravity.END or Gravity.TOP)
            closeParams.setMargins(0, 8, 8, 0)
            root.addView(closeButton, closeParams)
            // Tap anywhere in the window (the ✕ consumes its own touches first).
            root.setOnClickListener {
                if (tapToCloseEnabled) {
                    closeExecutor.execute { this@CameraWindows.close(id) }
                }
            }
            applyUiOptions(
                prefs.getBoolean(SHOW_CLOSE_BUTTON_KEY, true),
                prefs.getBoolean(TAP_TO_CLOSE_KEY, false),
            )
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
            renderer = UyvyRenderer(
                picture,
                File(GrabberScripts.frame(id)),
                main,
                rotation,
                flipH,
                flipV,
                lens,
                crop,
                shape,
            ) {
                caption.visibility = View.GONE
            }
            root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    renderer.setWindowAttached(true)
                }

                override fun onViewDetachedFromWindow(v: View) {
                    renderer.setWindowAttached(false)
                }
            })
        }

        /** Circle mode keeps the overlay transparent outside the picture; rect mode stays black. */
        private fun applyShapeBackground(shape: CamdShape) {
            root.setBackgroundColor(if (shape == CamdShape.CIRCLE) Color.TRANSPARENT else Color.BLACK)
        }

        fun setRotation(degrees: Int) {
            renderer.setRotation(degrees)
        }

        fun setFlip(horizontal: Boolean, vertical: Boolean) {
            renderer.setFlip(horizontal, vertical)
        }

        fun setLensCorrection(value: CamdLensCorrection) {
            renderer.setLensCorrection(value)
        }

        fun setCrop(value: CamdCrop) {
            renderer.setCrop(value)
        }

        fun setShape(value: CamdShape) {
            applyShapeBackground(value)
            renderer.setShape(value)
        }

        fun applyUiOptions(showCloseButton: Boolean, tapToClose: Boolean) {
            closeButton.visibility = if (showCloseButton) View.VISIBLE else View.GONE
            tapToCloseEnabled = tapToClose
        }

        fun start() {
            renderer.start()
        }

        fun stop() {
            renderer.stop()
        }
    }

    companion object {
        const val KEEP_AVM_STOPPED_KEY = "keep_avm_stopped"
        const val SHOW_CLOSE_BUTTON_KEY = "show_close_button"
        const val TAP_TO_CLOSE_KEY = "tap_to_close"
    }
}
