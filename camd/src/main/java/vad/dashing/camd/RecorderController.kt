package vad.dashing.camd

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

/**
 * Dashcam orchestration: persisted master switch, folder/quota/camera selection,
 * per-camera engines, media-wait loop and the forced stock-AVM hold while recording.
 * Recording requires no overlay permission and starts by itself after boot.
 */
class RecorderController(
    private val context: Context,
    private val windows: CameraWindows,
) {
    enum class RunState { STOPPED, RECORDING, WAITING_MEDIA, OVERFLOW }

    data class UiState(
        val enabled: Boolean = false,
        val runState: RunState = RunState.STOPPED,
        val message: String = "",
        val folder: String = "",
        val quotaGb: Int = DEFAULT_QUOTA_GB,
        val inputs: Set<Int> = emptySet(),
        val usedBytes: Long = 0,
        val sosFolder: String = "",
        val sosPrevious: Int = DEFAULT_SOS_PREVIOUS,
        val segmentSec: Int = DEFAULT_SEGMENT_SEC,
        val fpsDivisor: Int = 1,
        val sosRunning: Boolean = false,
        val repairRunning: Boolean = false,
    )

    private val prefs: SharedPreferences =
        context.getSharedPreferences("camd", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private val engines = HashMap<Int, RecorderEngine>()
    private val waitingInputs = HashSet<Int>()
    private var watcher: Thread? = null
    @Volatile
    private var enabledNow = false

    /** Single-flight guard for the emergency save (see [requestEmergencySave]). */
    @Volatile
    private var sosRunning = false

    /** Single-flight guard for the manual repair of truncated segments (0.8.7). */
    @Volatile
    private var repairRunning = false

    init {
        _state.value = UiState(
            enabled = prefs.getBoolean(PREF_ENABLED, false),
            folder = prefs.getString(PREF_FOLDER, "").orEmpty(),
            quotaGb = prefs.getInt(PREF_QUOTA_GB, DEFAULT_QUOTA_GB),
            inputs = loadInputs(),
            sosFolder = prefs.getString(PREF_SOS_FOLDER, "").orEmpty(),
            sosPrevious = prefs.getInt(PREF_SOS_PREVIOUS, DEFAULT_SOS_PREVIOUS),
            segmentSec = prefs.getInt(PREF_SEGMENT_SEC, DEFAULT_SEGMENT_SEC),
            fpsDivisor = RecorderFps.normalize(prefs.getInt(RecorderFps.PREF_DIVISOR, 1)),
            message = if (prefs.getBoolean(PREF_ENABLED, false)) {
                "Регистратор включён — начнёт запись автоматически"
            } else {
                ""
            },
        )
    }

    private fun loadInputs(): Set<Int> =
        prefs.getStringSet(PREF_INPUTS, emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()

    private fun currentFolder(): File? =
        _state.value.folder.takeIf { it.isNotBlank() }?.let { File(it) }

    private fun quotaBytes(): Long = _state.value.quotaGb * 1_000_000_000L

    // UI mutations -------------------------------------------------------------

    fun setFolder(path: String) {
        if (enabledNow) return
        prefs.edit().putString(PREF_FOLDER, path).apply()
        _state.update { it.copy(folder = path, message = "") }
    }

    fun setQuota(gb: Int) {
        if (enabledNow) return
        val clamped = gb.coerceIn(1, 512)
        prefs.edit().putInt(PREF_QUOTA_GB, clamped).apply()
        _state.update { it.copy(quotaGb = clamped) }
    }

    /** Segment length selector; engine re-reads it per segment, so this applies from the next segment. */
    fun setSegmentSec(sec: Int) {
        if (enabledNow) return
        val ms = sec * 1000L
        val clampedSec = (RecorderFiles.SEGMENT_OPTIONS_MS.firstOrNull { it >= ms } ?: RecorderFiles.SEGMENT_MS).toInt() / 1000
        prefs.edit().putInt(PREF_SEGMENT_SEC, clampedSec).apply()
        _state.update { it.copy(segmentSec = clampedSec) }
    }

    /** Recording rate as a sensor-rate divisor ([RecorderFps]); the grabber thins frames from the next start. */
    fun setFpsDivisor(divisor: Int) {
        if (enabledNow) return
        val normalized = RecorderFps.normalize(divisor)
        prefs.edit().putInt(RecorderFps.PREF_DIVISOR, normalized).apply()
        _state.update { it.copy(fpsDivisor = normalized) }
    }

    /** Emergency-save destination; can be changed anytime, even while recording. */
    fun setSosFolder(path: String) {
        prefs.edit().putString(PREF_SOS_FOLDER, path).apply()
        _state.update { it.copy(sosFolder = path) }
    }

    /** Shared 0–5 count of extra previous segments saved for every recording camera. */
    fun setSosPrevious(count: Int) {
        val clamped = count.coerceIn(0, 5)
        prefs.edit().putInt(PREF_SOS_PREVIOUS, clamped).apply()
        _state.update { it.copy(sosPrevious = clamped) }
    }

    /**
     * Manual repair of segments a power cut left without moov (0.8.7): scans the folder,
     * rebuilds each broken file into a playable MP4 using codec parameters from the
     * newest intact sibling of the same camera and replaces the broken file. Runs on a
     * background thread; recording does not need to be off, but in-flight segments are
     * skipped. Returns a STATUS-style reply fragment for TCP.
     */
    fun requestSegmentRepair(): String {
        if (repairRunning) return "state=repair busy"
        val folder = currentFolder()
        if (folder == null || !folder.isDirectory) {
            _state.update { it.copy(message = "Восстановление: папка записи не задана или недоступна") }
            return "state=error error=no_folder"
        }
        val skipNames = synchronized(engines) {
            engines.values.mapNotNull { it.currentSegmentFile()?.name }.toSet()
        }
        val candidates = SegmentRepair.repairCandidates(folder, skipNames)
        if (candidates.isEmpty()) {
            _state.update { it.copy(message = "Восстановление: битых сегментов не найдено") }
            return "OK state=repair none"
        }
        repairRunning = true
        _state.update { it.copy(repairRunning = true) }
        _state.update { it.copy(message = "Восстановление: разбираю ${candidates.size} файл(ов)…") }
        Thread({
            try {
                val outcomes = candidates.mapNotNull { candidate ->
                    val params = SegmentRepairEngine.codecParamsFor(candidate, folder)
                    if (params == null) {
                        SegmentRepairEngine.Outcome(candidate.file, false, "нет целого ролика этой камеры для образца")
                    } else {
                        SegmentRepairEngine.repair(candidate, params, RecorderFps.fps(_state.value.fpsDivisor))
                    }.also { outcome ->
                        Log.i(LOG_TAG, "repair: ${candidate.file.name}: ${outcome.detail}")
                    }
                }
                val ok = outcomes.count { it.ok }
                val failed = outcomes.filterNot { it.ok }
                    .joinToString("; ") { "${it.file.name}: ${it.detail}" }
                _state.update {
                    it.copy(
                        message = buildString {
                            append("Восстановление: готово ")
                            append(ok)
                            append(" из ")
                            append(outcomes.size)
                            if (failed.isNotEmpty()) {
                                append(" — не удалось: ")
                                append(failed)
                            }
                        },
                    )
                }
            } catch (error: Throwable) {
                Log.i(LOG_TAG, "repair ошибка: ${error.message ?: error.javaClass.simpleName}")
                _state.update {
                    it.copy(
                        message = "Восстановление: ошибка ${error.message ?: error.javaClass.simpleName}",
                    )
                }
            } finally {
                repairRunning = false
                _state.update { it.copy(repairRunning = false) }
            }
        }, "camd-rec-repair").apply { isDaemon = true; start() }
        return "OK state=repair started"
    }

    fun toggleInput(inputId: Int) {
        if (enabledNow) return
        val current = _state.value.inputs.toMutableSet()
        if (inputId in current) {
            current.remove(inputId)
        } else {
            if (current.size >= MAX_CAMERAS) {
                _state.update { it.copy(message = "Не больше $MAX_CAMERAS камер одновременно — столько держат аппаратные энкодеры") }
                return
            }
            current.add(inputId)
        }
        prefs.edit().putStringSet(PREF_INPUTS, current.map { it.toString() }.toSet()).apply()
        _state.update { it.copy(inputs = current, message = "") }
    }

    fun setEnabled(enabled: Boolean) {
        applyEnabledFromApi(enabled)
    }

    /**
     * API / UI master switch. Persists [PREF_ENABLED]; starting the engines is async.
     * Returns a STATUS-style fragment (`state=rec_on|rec_off|error …`) for TCP replies.
     */
    fun applyEnabledFromApi(enabled: Boolean): String {
        val state = _state.value
        val plan = RecorderEnableApi.plan(
            wantEnabled = enabled,
            hasInputs = state.inputs.isNotEmpty(),
            hasFolder = state.folder.isNotBlank(),
            armed = enabledNow,
            prefEnabled = prefs.getBoolean(PREF_ENABLED, false),
        )
        when (plan.action) {
            RecorderEnableApi.Action.ERROR_NO_CAMERAS,
            RecorderEnableApi.Action.ERROR_NO_FOLDER,
            -> {
                _state.update { it.copy(message = plan.message.orEmpty()) }
            }
            RecorderEnableApi.Action.IDEMPOTENT_ON -> Unit
            RecorderEnableApi.Action.TURN_ON -> {
                prefs.edit().putBoolean(PREF_ENABLED, true).apply()
                _state.update { it.copy(enabled = true, message = "Включаю запись…") }
                if (!enabledNow) {
                    Thread({ startWithRetry() }, "camd-rec-start").apply { isDaemon = true; start() }
                }
            }
            RecorderEnableApi.Action.TURN_OFF -> {
                prefs.edit().putBoolean(PREF_ENABLED, false).apply()
                if (enabledNow) {
                    Thread({ stopRecording("Запись остановлена") }, "camd-rec-stop").apply {
                        isDaemon = true
                        start()
                    }
                } else {
                    _state.update { it.copy(enabled = false, runState = RunState.STOPPED, message = "") }
                }
            }
        }
        return plan.reply
    }

    /** Persisted master switch (survives reboot); not the same as [recordingActive]. */
    fun masterEnabled(): Boolean = prefs.getBoolean(PREF_ENABLED, false) || _state.value.enabled

    /** True only while segments are actually being written (not armed-but-waiting). */
    fun recordingActive(): Boolean =
        enabledNow && _state.value.runState == RunState.RECORDING

    /** Separate STATUS fields — do not pack into one blob. */
    fun statusFlags(): String =
        "rec_enabled=${if (masterEnabled()) 1 else 0} rec_active=${if (recordingActive()) 1 else 0}" +
            " rec_sos=${if (sosRunning) 1 else 0} rec_seg=${segmentLengthMs() / 1000}"

    /**
     * Emergency save (0.8.6): engines close the in-flight segments immediately (moov is
     * finalized in ~one frame), then the closed segment + [PREF_SOS_PREVIOUS] previous
     * ones are copied to a `sos_<timestamp>` subfolder of the SOS folder — one tap on
     * the recorder tab or `REC SOS` over the API. Only cameras currently recording take
     * part; copies are never deleted by quota rotation (it only touches the main folder).
     * Returns a STATUS-style reply fragment for TCP.
     */
    fun requestEmergencySave(): String {
        val plan = RecorderSosApi.plan(
            armed = enabledNow,
            hasSosFolder = _state.value.sosFolder.isNotBlank(),
            saveRunning = sosRunning,
        )
        when (plan.action) {
            RecorderSosApi.Action.ERROR_NOT_RECORDING,
            RecorderSosApi.Action.ERROR_NO_SOS_FOLDER,
            -> {
                _state.update { it.copy(message = plan.message.orEmpty()) }
            }
            RecorderSosApi.Action.BUSY -> Unit
            RecorderSosApi.Action.START -> {
                sosRunning = true
                _state.update { it.copy(sosRunning = true) }
                _state.update { it.copy(message = "Аварийное сохранение: закрываю текущие сегменты…") }
                Log.i(LOG_TAG, "SOS: старт")
                Thread({ performEmergencySave() }, "camd-rec-sos").apply { isDaemon = true; start() }
            }
        }
        return plan.reply
    }

    private fun performEmergencySave() {
        try {
            val armedEngines = synchronized(engines) {
                engines.entries.filter { it.value.isAlive }.associate { it.key to it.value }
            }
            if (armedEngines.isEmpty()) {
                _state.update { it.copy(message = "Аварийное сохранение: нет активных камер записи") }
                return
            }
            val source = currentFolder() ?: run {
                _state.update { it.copy(message = "Аварийное сохранение: папка записи не задана") }
                return
            }
            // Counters before the close request: a later increment means "current segment closed".
            val before = armedEngines.mapValues { it.value.closedSegmentCount() }
            synchronized(engines) {
                armedEngines.forEach { (_, engine) -> engine.requestCloseNow() }
            }
            // Wait until every engine closed its in-flight segment (moov written, new one started).
            val deadline = System.currentTimeMillis() + 10_000L
            while (System.currentTimeMillis() < deadline) {
                if (armedEngines.all { (input, engine) -> engine.closedSegmentCount() > before.getValue(input) }) break
                runCatching { Thread.sleep(50) }
            }
            val savedCurrent = armedEngines.filter { (input, engine) ->
                engine.closedSegmentCount() > before.getValue(input)
            }
            if (savedCurrent.isEmpty()) {
                _state.update {
                    it.copy(
                        message = "Аварийное сохранение: камеры не закрыли сегменты — попробуйте ещё раз",
                    )
                }
                return
            }
            val sosRoot = File(_state.value.sosFolder)
            if (!sosRoot.isDirectory && sosRoot.parentFile?.isDirectory == true) sosRoot.mkdirs()
            val eventFolder = File(sosRoot, RecorderFiles.sosFolderName())
            if (!eventFolder.isDirectory && eventFolder.parentFile?.isDirectory == true) eventFolder.mkdirs()
            if (!RecorderFiles.isWritable(eventFolder)) {
                _state.update {
                    it.copy(
                        message = "Аварийное сохранение: папка недоступна — ${eventFolder.absolutePath}",
                    )
                }
                return
            }
            val sosPrevious = _state.value.sosPrevious
            val perCamera = savedCurrent.map { (input, engine) ->
                val current = engine.lastClosedSegmentFile()
                val inFlight = setOfNotNull(engine.currentSegmentFile()?.name)
                // Copy the just-closed segment first, then the requested previous ones.
                val segments = buildList {
                    if (current != null && current.isFile) add(current)
                    addAll(
                        RecorderFiles.sosSegmentsForCamera(
                            source, input, sosPrevious + 1, newest = current, exclude = inFlight,
                        ).asReversed(),
                    )
                }.filter { it.isFile && it.name !in inFlight }.distinctBy { it.absolutePath }.take(sosPrevious + 1)
                val copied = segments.count { segment ->
                    RecorderFiles.copyFile(segment, File(eventFolder, segment.name))
                }
                "cam$input: $copied/${sosPrevious + 1}"
            }
            Log.i(LOG_TAG, "SOS: ${eventFolder.absolutePath} (${perCamera.joinToString(" ")})")
            _state.update {
                it.copy(
                    message = "Аварийно сохранено: ${perCamera.joinToString(", ")} → ${eventFolder.absolutePath}",
                )
            }
        } catch (error: Throwable) {
            Log.i(LOG_TAG, "SOS ошибка: ${error.message ?: error.javaClass.simpleName}")
            _state.update {
                it.copy(
                    message = "Аварийное сохранение: ошибка ${error.message ?: error.javaClass.simpleName}",
                )
            }
        } finally {
            sosRunning = false
            _state.update { it.copy(sosRunning = false) }
        }
    }

    /** Boot path: the persisted master switch starts recording by itself. */
    fun autoStartIfEnabled() {
        val wanted = prefs.getBoolean(PREF_ENABLED, false)
        Log.i(LOG_TAG, "автостарт: pref=$wanted активна=$enabledNow")
        if (!enabledNow && wanted) {
            Thread({ startWithRetry() }, "camd-rec-boot").apply { isDaemon = true; start() }
        }
    }

    fun accessibilityPromptSkipped(): Boolean =
        prefs.getBoolean(PREF_A11Y_PROMPT_SKIPPED, false)

    fun dismissAccessibilityPrompt() {
        prefs.edit().putBoolean(PREF_A11Y_PROMPT_SKIPPED, true).apply()
    }

    /** Shutdown broadcast: finalize segments synchronously, keep the master switch. */
    fun finalizeNow() {
        if (!enabledNow) return
        synchronized(this) {
            if (!enabledNow) return
            enabledNow = false
            watcher?.interrupt()
            watcher = null
            stopEngines()
            windows.setRecorderActive(false)
            Log.i(LOG_TAG, "выключение ГУ: сегменты завершены, переключатель сохранён")
            _state.update {
                it.copy(
                    runState = RunState.STOPPED,
                    message = "Выключение ГУ — запись завершена",
                )
            }
        }
    }

    // Start / stop -------------------------------------------------------------

    /**
     * A failed grabber start (mb_daemon busy with another app's script at boot, or not
     * answering) is retried with growing pauses while the master switch stays on.
     */
    private fun startWithRetry() {
        if (!waitForMedia()) return
        val pauses = RecorderStartRetry.PAUSES_MS
        for (attempt in 0..pauses.size) {
            if (attempt > 0) {
                try {
                    Thread.sleep(pauses[attempt - 1])
                } catch (_: InterruptedException) {
                    return
                }
            }
            if (!prefs.getBoolean(PREF_ENABLED, false) || enabledNow) return
            if (startRecording(retryInMs = pauses.getOrNull(attempt))) return
        }
    }

    /**
     * Holds the start until the recording folder is writable. Before that nothing goes to
     * root (rootsvc / mb_daemon): at boot the USB stick is mounted through mb_daemon by
     * another app, and an early grabber start would occupy that channel for nothing.
     * Returns false when the switch was turned off or recording already runs.
     */
    private fun waitForMedia(): Boolean {
        var announced = false
        while (true) {
            if (!prefs.getBoolean(PREF_ENABLED, false) || enabledNow) return false
            if (mediaReady()) {
                if (announced) Log.i(LOG_TAG, "папка записи появилась: ${_state.value.folder}")
                return true
            }
            if (!announced) {
                announced = true
                Log.i(LOG_TAG, "жду папку записи ${_state.value.folder} — граббер не запускаю")
                _state.update {
                    it.copy(runState = RunState.WAITING_MEDIA, message = "Жду папку записи: ${it.folder}")
                }
            }
            try {
                Thread.sleep(MEDIA_POLL_MS)
            } catch (_: InterruptedException) {
                return false
            }
        }
    }

    private fun mediaReady(): Boolean {
        val folder = currentFolder() ?: return false
        if (!folder.isDirectory && folder.parentFile?.isDirectory == true) folder.mkdirs()
        return RecorderFiles.isWritable(folder)
    }

    /** Returns true once recording runs (started now or already running). */
    private fun startRecording(retryInMs: Long?): Boolean {
        synchronized(this) {
            if (enabledNow) return true
            enabledNow = true
            val inputs = _state.value.inputs
            val folder = currentFolder()
            if (folder != null && !folder.isDirectory && folder.parentFile?.isDirectory == true) {
                folder.mkdirs()
            }
            try {
                windows.setRecorderActive(true)
                windows.setRecorderInputsPref(inputs)
                windows.ensureGrabberFor(inputs)
                prefs.edit().putBoolean(PREF_PREV_KEEP, windows.keepAvmStopped()).apply()
                windows.forceAvmOffForRecorder()
            } catch (error: Throwable) {
                enabledNow = false
                windows.setRecorderActive(false)
                windows.releaseRecorderInputs()
                val reason = error.message ?: error.javaClass.simpleName
                Log.i(LOG_TAG, "граббер не запустился: $reason, повтор через ${retryInMs ?: 0} мс")
                _state.update {
                    it.copy(
                        // The switch stays on while a retry is pending.
                        enabled = retryInMs != null,
                        message = RecorderStartRetry.message(reason, retryInMs),
                    )
                }
                return false
            }
            startEngines(inputs)
            Log.i(
                LOG_TAG,
                "запись стартовала: ${inputs.sorted().joinToString(" ") { "cam$it" }}",
            )
            _state.update {
                it.copy(
                    runState = RunState.RECORDING,
                    message = "Запись идёт: ${inputs.sorted().joinToString(" ") { "cam$it" }} → ${it.folder}",
                )
            }
            startWatcher(inputs)
            return true
        }
    }

    private fun startEngines(inputs: Set<Int>) {
        synchronized(engines) {
            inputs.forEach { inputId ->
                val existing = engines[inputId]
                if (existing != null && existing.isAlive) return@forEach
                existing?.requestStop()
                val engine = RecorderEngine(
                    inputId = inputId,
                    frame = File(GrabberScripts.frame(inputId)),
                    prefs = prefs,
                    nextSegment = ::nextSegment,
                    onSegmentClosed = { recountBytes() },
                    onWaiting = { waiting, reason ->
                        mediaWaitChanged(inputId, waiting)
                        updateRunState(if (waiting) RunState.WAITING_MEDIA else RunState.RECORDING, reason)
                    },
                    onSegmentError = { message ->
                        // A corrupted segment: recording keeps going with the next one,
                        // so the run state stays RECORDING — only surface the reason.
                        _state.update {
                            it.copy(
                                runState = RunState.RECORDING,
                                message = message,
                            )
                        }
                    },
                    onError = { message ->
                        updateRunState(RunState.WAITING_MEDIA, message)
                    },
                    segmentMs = ::segmentLengthMs,
                    fpsDivisor = _state.value.fpsDivisor,
                )
                engines[inputId] = engine
                engine.start()
            }
        }
    }

    private fun stopRecording(message: String) {
        synchronized(this) {
            if (!enabledNow) return
            enabledNow = false
            watcher?.interrupt()
            watcher = null
            stopEngines()
            windows.setRecorderActive(false)
            windows.releaseRecorderInputs()
            val prevKeep = prefs.getBoolean(PREF_PREV_KEEP, false)
            prefs.edit().putBoolean(PREF_PREV_KEEP, false).apply()
            windows.restoreAvmAfterRecorder(prevKeep)
            prefs.edit().putBoolean(PREF_ENABLED, false).apply()
            _state.update {
                it.copy(
                    enabled = false,
                    runState = RunState.STOPPED,
                    message = message,
                )
            }
        }
    }

    /**
     * While every recording camera waits for the media, the grabber stops copying their
     * frames ([CameraWindows.setRecorderPaused]); the first engine that finds the folder
     * again brings the copies back. Only the desired file changes — no root call.
     */
    private fun mediaWaitChanged(inputId: Int, waiting: Boolean) {
        val paused = synchronized(waitingInputs) {
            if (waiting) waitingInputs.add(inputId) else waitingInputs.remove(inputId)
            RecorderMediaPause.paused(_state.value.inputs, waitingInputs)
        }
        windows.setRecorderPaused(paused)
    }

    private fun stopEngines() {
        synchronized(waitingInputs) { waitingInputs.clear() }
        synchronized(engines) {
            engines.values.forEach { it.requestStop() }
            engines.values.forEach { runCatching { it.interrupt() } }
            engines.values.forEach { runCatching { it.join(3_000) } }
            engines.clear()
        }
    }

    private fun startWatcher(inputs: Set<Int>) {
        watcher = Thread({
            var pass = 0
            while (enabledNow) {
                // Every closed segment already recounts; this pass only catches outside
                // changes (files deleted by hand), and a full listing of a FUSE folder
                // with hundreds of segments is not free.
                if (pass++ % RECOUNT_EVERY_PASSES == 0) recountBytes()
                // Restart engines that died; parked (waiting-media) ones stay alive.
                // enabledNow is re-checked under the lock: stopEngines() may have cleared
                // the map while this pass was counting bytes.
                synchronized(engines) {
                    if (!enabledNow || Thread.currentThread().isInterrupted) return@Thread
                    inputs.forEach { inputId ->
                        val engine = engines[inputId]
                        if (engine == null || !engine.isAlive) startEngines(setOf(inputId))
                    }
                }
                try {
                    Thread.sleep(5_000)
                } catch (_: InterruptedException) {
                    return@Thread
                }
            }
        }, "camd-rec-watch").apply { isDaemon = true; start() }
    }

    // Segments, quota and media presence ---------------------------------------

    private fun nextSegment(inputId: Int): File? {
        val folder = currentFolder() ?: return null
        if (!folder.isDirectory && folder.parentFile?.isDirectory == true) folder.mkdirs()
        if (!RecorderFiles.isWritable(folder)) return null
        val quota = quotaBytes()
        // The reserve follows the selected segment length (0.8.6): a 5-minute segment
        // needs more headroom than the default one.
        val reserve = RecorderFiles.segmentEstimateBytes(segmentLengthMs()) * 2
        if (!RecorderFiles.enforceQuota(folder, quota, reserve)) {
            _state.update {
                it.copy(
                    runState = RunState.OVERFLOW,
                    message = "Нет места: квота ${it.quotaGb} ГБ исчерпана, удалять старые сегменты нечего",
                )
            }
            return null
        }
        // An emergency save closes segments mid-second and the engine starts the next
        // one within the same second — the mp4 name must not collide with the file we
        // just closed, or MediaMuxer would truncate that (saved) segment.
        var candidate = File(folder, RecorderFiles.segmentName(inputId))
        var suffix = 1
        while (candidate.exists()) {
            candidate = File(folder, RecorderFiles.segmentName(inputId, suffix = suffix))
            suffix++
        }
        return candidate
    }

    private fun recountBytes() {
        val folder = currentFolder() ?: return
        if (!folder.isDirectory) return
        val used = RecorderFiles.totalBytes(folder)
        _state.update { it.copy(usedBytes = used) }
    }

    /** Persisted segment length in ms (0.8.6), applied per segment by the engines. */
    private fun segmentLengthMs(): Long =
        prefs.getInt(PREF_SEGMENT_SEC, DEFAULT_SEGMENT_SEC) * 1000L

    /**
     * Engine-driven run state updates. `WAITING_MEDIA` from an engine does not override
     * OVERFLOW set by [nextSegment] — the quota reason (place ran out, nothing to delete)
     * stays visible while the engine re-polls every 5 s; RECORDING always wins back.
     */
    private fun updateRunState(runState: RunState, message: String) {
        _state.update { current ->
            if (runState == RunState.WAITING_MEDIA && current.runState == RunState.OVERFLOW) {
                current
            } else {
                current.copy(runState = runState, message = message)
            }
        }
    }

    companion object {
        const val MAX_CAMERAS = 4
        const val DEFAULT_QUOTA_GB = 32
        const val DEFAULT_SOS_PREVIOUS = 1
        const val DEFAULT_SEGMENT_SEC = 180
        private const val LOG_TAG = "CamdRecorder"
        /** Watcher passes are 5 s apart: the folder is re-listed once a minute. */
        private const val RECOUNT_EVERY_PASSES = 12
        private const val MEDIA_POLL_MS = 5_000L
        private const val PREF_ENABLED = "recorder_enabled"
        private const val PREF_FOLDER = "recorder_folder"
        private const val PREF_QUOTA_GB = "recorder_quota_gb"
        private const val PREF_INPUTS = "recorder_inputs"
        private const val PREF_PREV_KEEP = "recorder_prev_keep"
        private const val PREF_A11Y_PROMPT_SKIPPED = "accessibility_prompt_skipped"
        private const val PREF_SOS_FOLDER = "recorder_sos_folder"
        private const val PREF_SOS_PREVIOUS = "recorder_sos_previous"
        private const val PREF_SEGMENT_SEC = "recorder_segment_sec"
    }
}
