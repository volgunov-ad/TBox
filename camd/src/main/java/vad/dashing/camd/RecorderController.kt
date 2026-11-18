package vad.dashing.camd

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        val recording: Boolean = false,
        val runState: RunState = RunState.STOPPED,
        val message: String = "",
        val folder: String = "",
        val quotaGb: Int = DEFAULT_QUOTA_GB,
        val inputs: Set<Int> = emptySet(),
        val usedBytes: Long = 0,
    )

    private val prefs: SharedPreferences =
        context.getSharedPreferences("camd", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private val engines = HashMap<Int, RecorderEngine>()
    private var watcher: Thread? = null
    private var enabledNow = false

    init {
        _state.value = UiState(
            enabled = prefs.getBoolean(PREF_ENABLED, false),
            folder = prefs.getString(PREF_FOLDER, "").orEmpty(),
            quotaGb = prefs.getInt(PREF_QUOTA_GB, DEFAULT_QUOTA_GB),
            inputs = loadInputs(),
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
        _state.value = _state.value.copy(folder = path, message = "")
    }

    fun setQuota(gb: Int) {
        if (enabledNow) return
        val clamped = gb.coerceIn(1, 512)
        prefs.edit().putInt(PREF_QUOTA_GB, clamped).apply()
        _state.value = _state.value.copy(quotaGb = clamped)
    }

    fun toggleInput(inputId: Int) {
        if (enabledNow) return
        val current = _state.value.inputs.toMutableSet()
        if (inputId in current) {
            current.remove(inputId)
        } else {
            if (current.size >= MAX_CAMERAS) {
                _state.value = _state.value.copy(message = "Не больше $MAX_CAMERAS камер одновременно — столько держат аппаратные энкодеры")
                return
            }
            current.add(inputId)
        }
        prefs.edit().putStringSet(PREF_INPUTS, current.map { it.toString() }.toSet()).apply()
        _state.value = _state.value.copy(inputs = current, message = "")
    }

    fun setEnabled(enabled: Boolean) {
        if (enabledNow == enabled) return
        if (enabled) {
            val state = _state.value
            if (state.inputs.isEmpty()) {
                _state.value = state.copy(message = "Выберите хотя бы одну камеру")
                return
            }
            if (state.folder.isBlank()) {
                _state.value = state.copy(message = "Выберите папку для записи")
                return
            }
            prefs.edit().putBoolean(PREF_ENABLED, true).apply()
            _state.value = _state.value.copy(enabled = true, message = "Включаю запись…")
            Thread({ startRecording() }, "camd-rec-start").apply { isDaemon = true; start() }
        } else {
            prefs.edit().putBoolean(PREF_ENABLED, false).apply()
            Thread({ stopRecording("Запись остановлена") }, "camd-rec-stop").apply { isDaemon = true; start() }
        }
    }

    /** Boot path: the persisted master switch starts recording by itself. */
    fun autoStartIfEnabled() {
        val wanted = prefs.getBoolean(PREF_ENABLED, false)
        Log.i(LOG_TAG, "автостарт: pref=$wanted активна=$enabledNow")
        if (!enabledNow && wanted) {
            Thread({ startRecording() }, "camd-rec-boot").apply { isDaemon = true; start() }
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
            stopEngines()
            watcher?.interrupt()
            watcher = null
            windows.setRecorderActive(false)
            Log.i(LOG_TAG, "выключение ГУ: сегменты завершены, переключатель сохранён")
            _state.value = _state.value.copy(
                recording = false,
                runState = RunState.STOPPED,
                message = "Выключение ГУ — запись завершена",
            )
        }
    }

    // Start / stop -------------------------------------------------------------

    private fun startRecording() {
        synchronized(this) {
            if (enabledNow) return
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
                _state.value = _state.value.copy(
                    enabled = false,
                    message = "Не удалось запустить граббер: ${error.message ?: error.javaClass.simpleName}",
                )
                return
            }
            startEngines(inputs)
            Log.i(
                LOG_TAG,
                "запись стартовала: ${inputs.sorted().joinToString(" ") { "cam$it" }}",
            )
            _state.value = _state.value.copy(
                recording = true,
                runState = RunState.RECORDING,
                message = "Запись идёт: ${inputs.sorted().joinToString(" ") { "cam$it" }} → ${_state.value.folder}",
            )
            startWatcher(inputs)
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
                        updateRunState(if (waiting) RunState.WAITING_MEDIA else RunState.RECORDING, reason)
                    },
                    onError = { message ->
                        updateRunState(RunState.WAITING_MEDIA, message)
                    },
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
            stopEngines()
            watcher?.interrupt()
            watcher = null
            windows.setRecorderActive(false)
            windows.releaseRecorderInputs()
            val prevKeep = prefs.getBoolean(PREF_PREV_KEEP, false)
            prefs.edit().putBoolean(PREF_PREV_KEEP, false).apply()
            windows.restoreAvmAfterRecorder(prevKeep)
            prefs.edit().putBoolean(PREF_ENABLED, false).apply()
            _state.value = _state.value.copy(
                enabled = false,
                recording = false,
                runState = RunState.STOPPED,
                message = message,
            )
        }
    }

    private fun stopEngines() {
        synchronized(engines) {
            engines.values.forEach { it.requestStop() }
            engines.values.forEach { runCatching { it.interrupt() } }
            engines.values.forEach { runCatching { it.join(3_000) } }
            engines.clear()
        }
    }

    private fun startWatcher(inputs: Set<Int>) {
        watcher = Thread({
            while (enabledNow) {
                recountBytes()
                // Restart engines that died; parked (waiting-media) ones stay alive.
                synchronized(engines) {
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
        if (!RecorderFiles.enforceQuota(folder, quota, RecorderFiles.SEGMENT_ESTIMATE_BYTES * 2)) {
            _state.value = _state.value.copy(
                runState = RunState.OVERFLOW,
                message = "Нет места: квота ${_state.value.quotaGb} ГБ исчерпана, удалять старые сегменты нечего",
            )
            return null
        }
        return File(folder, RecorderFiles.segmentName(inputId))
    }

    private fun recountBytes() {
        val folder = currentFolder() ?: return
        if (!folder.isDirectory) return
        val used = RecorderFiles.totalBytes(folder)
        _state.value = _state.value.copy(usedBytes = used)
    }

    private fun updateRunState(runState: RunState, message: String) {
        _state.value = _state.value.copy(runState = runState, message = message)
    }

    companion object {
        const val MAX_CAMERAS = 4
        const val DEFAULT_QUOTA_GB = 32
        private const val LOG_TAG = "CamdRecorder"
        private const val PREF_ENABLED = "recorder_enabled"
        private const val PREF_FOLDER = "recorder_folder"
        private const val PREF_QUOTA_GB = "recorder_quota_gb"
        private const val PREF_INPUTS = "recorder_inputs"
        private const val PREF_PREV_KEEP = "recorder_prev_keep"
        private const val PREF_A11Y_PROMPT_SKIPPED = "accessibility_prompt_skipped"
    }
}
