package vad.dashing.camd

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
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
    /** inputId → preset name the window was opened with; empty for as-is / custom forms. */
    private val windowSources = HashMap<Int, String>()
    private val main = Handler(Looper.getMainLooper())
    private val overlayOps = OverlayOpSequencer()
    private val prefs: SharedPreferences =
        context.getSharedPreferences("camd", Context.MODE_PRIVATE)
    private var avmStoppedByUs = false
    private val closeExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "camd-close").apply { isDaemon = true }
    }
    private val propCache = PropCache()

    /** mb_daemon runs one shared autorun.sh: a second writer would replace the first script. */
    private val armLock = Any()

    /** Loaded once on the first stock OPEN; null when the HU has no calibration dir. */
    private val stockCalibration: CamdCalibration? by lazy {
        runCatching { CamdCalibration.load(File(CamdCalibration.DIR)) }.getOrNull()
    }

    @Volatile
    private var recorderActive = false

    /** While recording, stock AVM is force-held off and AVM commands are denied. */
    fun setRecorderActive(active: Boolean) {
        recorderActive = active
    }

    /**
     * Self-grant of the accessibility service through the mb_daemon root channel,
     * same as the overlay grant. Blocking; call from a worker thread.
     */
    fun grantAccessibility() {
        val nonce = "a${System.currentTimeMillis()}"
        arm(GrabberScripts.grantAccessibility(nonce), nonce)
    }

    fun setRecorderInputsPref(inputs: Set<Int>) {
        prefs.edit()
            .putStringSet(RECORDER_INPUTS_KEY, inputs.map { it.toString() }.toSet())
            .apply()
    }

    private fun recorderInputsPref(): Set<Int> =
        prefs.getStringSet(RECORDER_INPUTS_KEY, emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()

    /**
     * Prepares the grabber for [inputs]: persists them as recorder inputs, writes the
     * desired union from that set (not a stale prefs snapshot), arms root scripts, waits
     * for the beat. Callers may still call [setRecorderInputsPref] first; this uses
     * [inputs] either way (P2-5).
     */
    fun ensureGrabberFor(inputs: Set<Int>) {
        val stopAvm = synchronized(lock) {
            prefs.edit()
                .putStringSet(RECORDER_INPUTS_KEY, inputs.map { it.toString() }.toSet())
                .apply()
            writeDesired(recorderInputs = inputs)
            shouldStopAvmNow()
        }
        // Root arm / beat waits stay outside the lock (P1-1).
        prepare(stopAvm)
    }

    /** Recomputes the desired list after recorder inputs are gone and restarts AVM when nothing holds it. */
    fun releaseRecorderInputs() {
        synchronized(lock) { writeDesired() }
        maybeStartAvm()
    }

    /** Stops the stock AVM and holds it off, ignoring the user's keep flag while the recorder runs. */
    fun forceAvmOffForRecorder() {
        val nonce = synchronized(lock) {
            markAvmStopped()
            if (getprop("init.svc.AVMapp") != "running") return
            "r${System.currentTimeMillis()}"
        }
        arm(GrabberScripts.stopAvm(nonce), nonce)
        waitProp("init.svc.AVMapp", "running", present = false)
    }

    /** Restores the user's pre-recording keep flag; starts AVM when it was not held. */
    fun restoreAvmAfterRecorder(prevKeep: Boolean) {
        synchronized(lock) {
            prefs.edit().putBoolean(KEEP_AVM_STOPPED_KEY, prevKeep).apply()
        }
        if (!prevKeep) {
            maybeStartAvm()
        }
    }

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
    fun setKeepAvmStopped(keep: Boolean): String {
        if (recorderActive) {
            return "state=error error=recorder_active"
        }
        if (keep) {
            val nonce = synchronized(lock) {
                prefs.edit().putBoolean(KEEP_AVM_STOPPED_KEY, true).apply()
                markAvmStopped()
                if (getprop("init.svc.AVMapp") != "running") return "state=avm_held"
                "k${System.currentTimeMillis()}"
            }
            arm(GrabberScripts.stopAvm(nonce), nonce)
            waitProp("init.svc.AVMapp", "running", present = false)
            return "state=avm_held"
        }
        val ids = synchronized(lock) {
            prefs.edit().putBoolean(KEEP_AVM_STOPPED_KEY, false).apply()
            val snapshot = openIds.toList()
            openIds.clear()
            writeDesired()
            avmStoppedByUs = true
            snapshot
        }
        ids.forEach { hide(it) }
        maybeStartAvm()
        return "state=avm_restore"
    }

    fun canDraw(): Boolean = Settings.canDrawOverlays(context)

    /** Slider values not yet written to prefs; readers see them before the debounced save. */
    private val pendingLens = ConcurrentHashMap<Int, CamdLensCorrection>()

    fun lensCorrection(inputId: Int): CamdLensCorrection =
        pendingLens[inputId] ?: CamdLensCorrectionStore.load(prefs, inputId)

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
        defaultCorrection: Boolean,
        correction: CamdLensCorrection,
    ): Boolean = CamdPresetStore.saveBuiltinOverride(
        prefs, name, rotation, flipH, flipV, crop, defaultCorrection, correction,
    )

    fun deleteUserPreset(name: String): Boolean = CamdPresetStore.deleteUser(prefs, name)

    /** Persist per-camera visual correction and push it to an open overlay if any. */
    fun setLensCorrection(inputId: Int, value: CamdLensCorrection): CamdLensCorrection = synchronized(lock) {
        if (CamdSources.isStock(inputId)) return CamdLensCorrection.IDENTITY
        val clamped = CamdLensCorrection.clamp(value)
        // Sliders fire on every move: the window follows at once, prefs are written once
        // the value settles.
        pendingLens[inputId] = clamped
        val token = lensSaveToken(inputId)
        main.removeCallbacksAndMessages(token)
        main.postAtTime(
            {
                pendingLens.remove(inputId)?.let { CamdLensCorrectionStore.save(prefs, inputId, it) }
            },
            token,
            android.os.SystemClock.uptimeMillis() + LENS_SAVE_DELAY_MS,
        )
        val live = views[inputId]
        if (live != null) {
            main.post { live.setLensCorrection(clamped) }
        }
        clamped
    }

    fun open(command: CamdCommand.Open): String {
        data class Prep(
            val resolved: CamdCommand.Open,
            val input: CameraInput,
            val stockView: CamdStockView?,
            val moved: Boolean,
            val stopAvm: Boolean,
        )

        val prep = synchronized(lock) {
            val resolved = resolvePreset(command) ?: return "state=error error=unknown_preset"
            if (!CamdSources.isKnown(resolved.inputId)) return "state=error error=unknown_input"
            val stockView = if (CamdSources.isStock(resolved.inputId)) {
                stockCalibration?.bySource(resolved.inputId) ?: return "state=error error=stock_cal_missing"
            } else {
                null
            }
            val input = KnownInputs.byId(stockView?.inputId ?: resolved.inputId)
                ?: return "state=error error=unknown_input"
            val moved = views.containsKey(resolved.inputId)
            openIds.add(resolved.inputId)
            writeDesired()
            Prep(resolved, input, stockView, moved, shouldStopAvmNow())
        }

        // Root waits and overlay latch must not hold [lock] (P1-1).
        val root = runCatching { prepare(prep.stopAvm) }
        if (root.isFailure) {
            rollbackOpen(prep.resolved.inputId, prep.moved)
            return "state=error error=${root.exceptionOrNull()?.message}"
        }
        if (!Settings.canDrawOverlays(context)) waitOverlay()
        if (!Settings.canDrawOverlays(context)) {
            rollbackOpen(prep.resolved.inputId, prep.moved)
            return "state=error error=overlay_permission"
        }
        val shown = show(prep.resolved, prep.input, prep.stockView)
        if (!shown.ok) {
            rollbackOpen(prep.resolved.inputId, prep.moved)
            return "state=error error=${overlayFailureReason(shown.error)}"
        }
        synchronized(lock) {
            windowSources[prep.resolved.inputId] = prep.resolved.presetName.orEmpty()
        }
        val state = if (prep.moved) "moved" else "open"
        return "state=$state input=${prep.resolved.inputId}"
    }

    private fun rollbackOpen(inputId: Int, moved: Boolean) {
        synchronized(lock) {
            if (!moved) openIds.remove(inputId)
            writeDesired()
        }
        maybeStartAvm()
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
            // defaultCorrection=true keeps the null override: the window then reads
            // the current per-camera correction at draw time. false freezes the
            // values stored in the preset.
            lensOverride = if (preset.defaultCorrection) null else preset.correction,
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

    private val lensSaveTokens = ConcurrentHashMap<Int, Any>()

    private fun lensSaveToken(inputId: Int): Any = lensSaveTokens.getOrPut(inputId) { Any() }

    /** Writes debounced slider values now (export, backup, preset prefill). */
    private fun flushPendingLens() {
        pendingLens.keys.toList().forEach { inputId ->
            main.removeCallbacksAndMessages(lensSaveToken(inputId))
            pendingLens.remove(inputId)?.let { CamdLensCorrectionStore.save(prefs, inputId, it) }
        }
    }

    fun exportSettings(): String {
        flushPendingLens()
        return CamdBackup.exportJson(prefs)
    }

    fun importSettings(text: String): CamdImportSummary? {
        flushPendingLens()
        return CamdBackup.importJson(prefs, text)
    }

    fun exportPresets(): String = CamdBackup.exportPresetsJson(prefs)

    fun importPresets(text: String): CamdPresetsImportSummary? = CamdBackup.importPresetsJson(prefs, text)

    /**
     * Live preview: moves / reshapes the open window of [inputId] from UI state.
     * No-op when that camera has no window; other windows are not touched.
     */
    fun previewWindowGeometry(inputId: Int, x: Float, y: Float, w: Float, h: Float, circle: Boolean) =
        synchronized(lock) {
            val live = views[inputId] ?: return@synchronized
            val shape = if (circle) CamdShape.CIRCLE else CamdShape.RECT
            main.post {
                try {
                    place(
                        live.params,
                        CamdCommand.Open(inputId, x, y, w, h),
                    )
                    live.setShape(shape)
                    windowManager().updateViewLayout(live.root, live.params)
                } catch (_: Throwable) {
                    // Window may be mid-teardown; the next OPEN restores geometry.
                }
            }
        }

    /**
     * Live apply after a preset save: updates every window that was opened with
     * exactly this preset (rotation / flips / crop / corrections). Window geometry
     * stays command-owned and is not touched.
     */
    fun applyPresetToWindows(preset: CamdPreset) = synchronized(lock) {
        val targets = windowSources.filterValues { it == preset.name }.keys.toList()
        if (targets.isEmpty()) return@synchronized
        val lens = when {
            CamdSources.isStock(preset.cameraId) -> CamdLensCorrection.IDENTITY
            preset.defaultCorrection -> lensCorrection(preset.cameraId)
            else -> preset.correction
        }
        val crop = if (preset.crop.isIdentity()) CamdCrop.IDENTITY else preset.crop
        targets.forEach { inputId ->
            val live = views[inputId] ?: return@forEach
            main.post {
                live.setRotation(preset.rotation)
                live.setFlip(preset.flipH, preset.flipV)
                live.setCrop(crop)
                live.setLensCorrection(lens)
            }
        }
    }

    fun close(inputId: Int): String {
        synchronized(lock) {
            openIds.remove(inputId)
            writeDesired()
        }
        val hidden = hide(inputId)
        maybeStartAvm()
        if (!hidden.ok) {
            return "state=error error=${hidden.error} input=$inputId"
        }
        return "state=closed input=$inputId"
    }

    fun closeAll(): String {
        val ids = synchronized(lock) {
            val snapshot = openIds.toList()
            openIds.clear()
            writeDesired()
            snapshot
        }
        var hideFailed = false
        var hideError = OverlayOpSequencer.ERROR_TIMEOUT
        ids.forEach { id ->
            val hidden = hide(id)
            if (!hidden.ok) {
                hideFailed = true
                hideError = hidden.error
            }
        }
        maybeStartAvm()
        if (hideFailed) {
            return "state=error error=$hideError"
        }
        return "state=closed"
    }

    /**
     * Close overlays, release the close executor. Used by [CamdRuntime.stop].
     * Idempotent enough for process teardown.
     */
    fun shutdown() {
        closeAll()
        closeExecutor.shutdownNow()
    }

    fun status(): String {
        val ids = synchronized(lock) { if (openIds.isEmpty()) "none" else openIds.joinToString(",") }
        val avm = getprop("init.svc.AVMapp").ifBlank { "unknown" }
        val keep = if (keepAvmStopped()) "1" else "0"
        // Recorder flags (rec_enabled / rec_active) are appended by CamdApiServer from
        // RecorderController — do not pack them into one blob here.
        return "windows=$ids avm=$avm keep_avm=$keep"
    }

    private fun shouldStopAvmNow(): Boolean {
        if (!needsAvmStop()) return false
        return getprop("init.svc.AVMapp") == "running"
    }

    private fun needsAvmStop(): Boolean {
        if (recorderActive && recorderInputsPref().any { CamdSources.heldByAvm(it) }) return true
        return openIds.any { CamdSources.heldByAvm(it) }
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
        val should = synchronized(lock) {
            !(recorderActive || keepAvmStopped() || !avmStoppedByUs || needsAvmStop())
        }
        if (!should) return
        val deadline = System.currentTimeMillis() + 3_000
        while (System.currentTimeMillis() < deadline) {
            val busy = KnownInputs.all.any { it.heldByAvm && File(GrabberScripts.frame(it.id)).exists() }
            if (!busy) break
            Thread.sleep(100)
        }
        val nonce = "a${System.currentTimeMillis()}"
        arm(GrabberScripts.startAvm(nonce), nonce)
        synchronized(lock) { clearAvmStopped() }
        waitProp("init.svc.AVMapp", "running", present = true)
    }

    /** If the process died after stopping AVMapp, the next start puts it back. */
    fun recover() {
        val stopNonce = synchronized(lock) {
            if (keepAvmStopped()) {
                markAvmStopped()
                if (getprop("init.svc.AVMapp") == "running") {
                    "k${System.currentTimeMillis()}"
                } else {
                    return
                }
            } else {
                null
            }
        }
        if (stopNonce != null) {
            runCatching { arm(GrabberScripts.stopAvm(stopNonce), stopNonce) }
            return
        }
        val shouldRestore = synchronized(lock) {
            val marker = File(context.filesDir, "avm.stopped")
            if (!marker.exists()) return
            avmStoppedByUs = true
            writeDesired()
            true
        }
        if (shouldRestore) {
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

    private fun show(command: CamdCommand.Open, input: CameraInput, stockView: CamdStockView?): ShowResult {
        val inputId = command.inputId
        val token = overlayOps.begin(inputId)
        val latch = CountDownLatch(1)
        val result = ShowResult()
        main.post {
            try {
                if (!overlayOps.isCurrent(inputId, token)) {
                    result.markSuperseded()
                    return@post
                }
                val existing = views[inputId]
                val lens = if (stockView != null) {
                    CamdLensCorrection.IDENTITY
                } else {
                    command.lensOverride ?: lensCorrection(inputId)
                }
                if (existing != null) {
                    place(existing.params, command)
                    existing.setRotation(command.rotation)
                    existing.setFlip(command.flipH, command.flipV)
                    existing.setLensCorrection(lens)
                    existing.setCrop(command.crop ?: CamdCrop.IDENTITY)
                    existing.setShape(command.shape)
                    if (!overlayOps.isCurrent(inputId, token)) {
                        result.markSuperseded()
                        return@post
                    }
                    windowManager().updateViewLayout(existing.root, existing.params)
                } else {
                    val live = LiveWindow(
                        inputId,
                        input,
                        stockView,
                        command.rotation,
                        command.flipH,
                        command.flipV,
                        lens,
                        command.crop ?: CamdCrop.IDENTITY,
                        command.shape,
                    )
                    place(live.params, command)
                    if (!overlayOps.isCurrent(inputId, token)) {
                        result.markSuperseded()
                        return@post
                    }
                    windowManager().addView(live.root, live.params)
                    if (!overlayOps.isCurrent(inputId, token)) {
                        runCatching { windowManager().removeView(live.root) }
                        result.markSuperseded()
                        return@post
                    }
                    views[inputId] = live
                    if (!overlayOps.isCurrent(inputId, token)) {
                        // Timeout / newer op won after we committed — undo before start.
                        if (views.remove(inputId, live)) {
                            live.stop()
                            runCatching { windowManager().removeView(live.root) }
                        }
                        result.markSuperseded()
                        return@post
                    }
                    live.start()
                }
                if (!overlayOps.isCurrent(inputId, token)) {
                    // Moved an existing window, then cancelled: leave layout as-is; a newer
                    // show/hide owns the generation. Do not mark ok.
                    result.markSuperseded()
                    return@post
                }
                result.ok = true
            } catch (error: Throwable) {
                result.error = error.message ?: "overlay"
            } finally {
                latch.countDown()
            }
        }
        if (!latch.await(3, TimeUnit.SECONDS)) {
            // Only bump if this show still owns the generation — a newer show must keep it.
            overlayOps.cancelIfCurrent(inputId, token)
            result.error = OverlayOpSequencer.ERROR_TIMEOUT
            val count = OverlayLatchMetrics.onShowTimeout()
            Log.w(OVERLAY_LOG_TAG, "show timeout input=$inputId token=$token count=$count")
        }
        return result
    }

    private class HideResult {
        var ok: Boolean = false
        var error: String = OverlayOpSequencer.ERROR_TIMEOUT

        fun markSuperseded() {
            ok = false
            error = OverlayOpSequencer.ERROR_SUPERSEDED
        }
    }

    private fun hide(inputId: Int): HideResult {
        synchronized(lock) { windowSources.remove(inputId) }
        val token = overlayOps.begin(inputId)
        val latch = CountDownLatch(1)
        val result = HideResult()
        main.post {
            try {
                if (!overlayOps.isCurrent(inputId, token)) {
                    // Newer OPEN/CLOSE won — do not remove the new overlay; report like show.
                    result.markSuperseded()
                    return@post
                }
                val live = views.remove(inputId)
                live?.stop()
                if (live != null) {
                    runCatching { windowManager().removeView(live.root) }
                }
                if (!overlayOps.isCurrent(inputId, token)) {
                    result.markSuperseded()
                    return@post
                }
                result.ok = true
            } catch (error: Throwable) {
                result.ok = false
                result.error = error.message ?: "overlay"
            } finally {
                latch.countDown()
            }
        }
        if (!latch.await(2, TimeUnit.SECONDS)) {
            // The token stays current: the late Runnable still removes the window unless a
            // newer OPEN/CLOSE took over, so a slow main thread cannot leave it on screen.
            val count = OverlayLatchMetrics.onHideTimeout()
            Log.w(OVERLAY_LOG_TAG, "hide timeout input=$inputId token=$token count=$count")
            result.ok = false
            result.error = OverlayOpSequencer.ERROR_TIMEOUT
        }
        return result
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

    private fun writeDesired(recorderInputs: Set<Int>? = null) {
        val file = File(GrabberScripts.DESIRED)
        file.parentFile?.mkdirs()
        val recorderIds = when {
            recorderInputs != null -> recorderInputs
            recorderActive -> recorderInputsPref()
            else -> emptySet()
        }
        val divisor = RecorderFps.normalize(prefs.getInt(RecorderFps.PREF_DIVISOR, 1))
        val text = GrabDesiredIds.lines(openIds, recorderIds, divisor).joinToString("\n")
        file.writeText(if (text.isEmpty()) "" else text + "\n")
    }

    private fun arm(script: String, nonce: String) = synchronized(armLock) {
        val autorun = File(DaemonScripts.AUTORUN)
        waitForeignAutorun(autorun)
        autorun.writeText(script)
        // With the trigger still armed by another app, mb_daemon runs and deletes the
        // script the moment it lands, so chmod finds no file. The nonce marker below
        // is the real answer.
        runCatching { exec(arrayOf("chmod", "777", DaemonScripts.AUTORUN)) }
        File(DaemonScripts.AUTORUN_FLAG).createNewFile()
        exec(arrayOf("setprop", DaemonScripts.AUTORUN_PROP, ""))
        Thread.sleep(300)
        exec(arrayOf("setprop", DaemonScripts.AUTORUN_PROP, DaemonScripts.AUTORUN_FLAG))
        val deadline = System.currentTimeMillis() + 8_000
        val marker = File(GrabberScripts.ARM)
        while (System.currentTimeMillis() < deadline) {
            val text = runCatching { if (marker.canRead()) marker.readText() else "" }.getOrDefault("")
            if (text.contains(nonce)) return@synchronized
            Thread.sleep(200)
        }
        throw IllegalStateException("mb_daemon не ответил")
    }

    private fun waitForeignAutorun(autorun: File) {
        val deadline = System.currentTimeMillis() + DaemonScripts.FOREIGN_AUTORUN_WAIT_MS
        while (System.currentTimeMillis() < deadline) {
            val text = if (autorun.exists()) runCatching { autorun.readText() }.getOrDefault("") else null
            if (!DaemonScripts.isForeignAutorun(text)) return
            Thread.sleep(200)
        }
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
        /** Non-null reason for callers when [ok] is false; never leave this null. */
        var error: String = "overlay"

        fun markSuperseded() {
            ok = false
            error = OverlayOpSequencer.ERROR_SUPERSEDED
        }
    }

    private inner class LiveWindow(
        val id: Int,
        val input: CameraInput,
        val stockView: CamdStockView?,
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
            caption.setShadowLayer(8f, 0f, 0f, Color.BLACK)
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
            val margin = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                8f,
                context.resources.displayMetrics,
            ).toInt()
            closeParams.setMargins(0, margin, margin, 0)
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
                File(GrabberScripts.frame(stockView?.inputId ?: id)),
                main,
                rotation,
                flipH,
                flipV,
                lens,
                crop,
                shape,
                stockView,
                inputId = id,
                onStall = { stalled ->
                    if (stalled) {
                        caption.text = "нет сигнала ${input.id}"
                        caption.visibility = View.VISIBLE
                    } else {
                        caption.visibility = View.GONE
                    }
                },
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
        const val RECORDER_INPUTS_KEY = "recorder_inputs"
        private const val OVERLAY_LOG_TAG = "CamdOverlay"
        private const val LENS_SAVE_DELAY_MS = 300L
    }
}
