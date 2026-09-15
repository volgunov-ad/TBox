package vad.dashing.tbox

import android.content.Context
import android.os.Build
import android.os.Environment
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import vad.dashing.tbox.location.GeoDebugLogRecorder
import vad.dashing.tbox.location.GeoDebugLogRotate
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Continuous app journal log to Downloads (buffered append).
 * Hooks from [TboxRepository.addLog] while recording. Rotate at [MAX_FILE_BYTES].
 * Session-only (not persisted across process death).
 */
object AppLogFileRecorder {
    const val MAX_FILE_BYTES = GeoDebugLogRecorder.MAX_FILE_BYTES
    const val FLUSH_BYTES = 24 * 1024
    const val FILE_PREFIX = "tbox_app_log_"

    data class UiState(
        val recording: Boolean = false,
        val filePath: String? = null,
        val lines: Int = 0,
        val lastError: String? = null,
        val autoStopped: Boolean = false,
    )

    private val _ui = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _ui.asStateFlow()

    private var appContext: Context? = null
    private var scope: CoroutineScope? = null
    private var flushJob: Job? = null
    @Volatile private var flushInFlight: Boolean = false
    private val writeMutex = Mutex()
    private val pending = StringBuilder(FLUSH_BYTES + 4_096)
    private val pendingLock = Any()
    private var outFile: File? = null
    private var flushedBytes: Long = 0L
    private var partIndex: Int = 1
    private var linesPendingUi: Int = 0

    fun attach(context: Context, scope: CoroutineScope) {
        this.appContext = context.applicationContext
        this.scope = scope
    }

    fun isRecording(): Boolean = _ui.value.recording

    fun start(): Boolean {
        if (_ui.value.recording) return false
        val ctx = appContext ?: return false
        val sc = scope ?: return false
        val file = createLogFile(ctx) ?: run {
            _ui.value = _ui.value.copy(
                recording = false,
                filePath = null,
                lines = 0,
                lastError = "cannot create file",
                autoStopped = false,
            )
            return false
        }
        outFile = file
        pending.clear()
        flushedBytes = 0L
        partIndex = 1
        _ui.value = _ui.value.copy(
            recording = true,
            filePath = file.absolutePath,
            lines = 0,
            lastError = null,
            autoStopped = false,
        )
        pending.append(fileHeader(continuedFrom = null))
        sc.launch(Dispatchers.IO) { flushPending() }
        flushJob?.cancel()
        flushJob = sc.launch(Dispatchers.IO) {
            while (isActive && _ui.value.recording) {
                delay(1_000L)
                flushPending()
            }
        }
        // Avoid recurse through addLog while starting; message still goes to in-memory ring.
        appendDirect("[AppLog] recording started: ${file.name}")
        return true
    }

    fun stop(auto: Boolean = false): Boolean {
        val was = _ui.value.recording
        flushJob?.cancel()
        flushJob = null
        if (!was && outFile == null) return false
        val sc = scope
        val path = outFile?.absolutePath
        if (sc != null) {
            sc.launch(Dispatchers.IO) {
                writeMutex.withLock {
                    pending.append(
                        "\n# stopped=${formatWall(System.currentTimeMillis())}" +
                            " auto=$auto lines=${_ui.value.lines}\n",
                    )
                    flushPendingLocked()
                }
                val ctx = appContext
                if (ctx != null && path != null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            ctx,
                            ctx.getString(R.string.toast_saved_to, path),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        }
        _ui.value = _ui.value.copy(
            recording = false,
            autoStopped = auto,
            filePath = path,
        )
        outFile = null
        return was
    }

    /**
     * Append one journal line while recording.
     * Called from [TboxRepository.addLog] after the in-memory ring is updated.
     */
    fun append(logEntry: String) {
        if (!_ui.value.recording) return
        val trimmed = logEntry.trimEnd()
        if (trimmed.isEmpty()) return
        appendLine("$trimmed\n")
    }

    private fun appendDirect(line: String) {
        if (!_ui.value.recording) return
        appendLine("$line\n")
    }

    private fun appendLine(line: String) {
        val needFlush: Boolean
        val bump: Int
        val needRotate: Boolean
        synchronized(pendingLock) {
            val nextBytes = GeoDebugLogRotate.utf8Bytes(line)
            val pendingBytes = GeoDebugLogRotate.utf8Bytes(pending)
            needRotate = GeoDebugLogRotate.shouldRotate(
                flushedBytes,
                pendingBytes,
                nextBytes,
                MAX_FILE_BYTES,
            )
            pending.append(line)
            linesPendingUi++
            needFlush = pending.length >= FLUSH_BYTES
            bump = linesPendingUi
            linesPendingUi = 0
        }
        if (bump > 0) {
            _ui.value = _ui.value.copy(lines = _ui.value.lines + bump)
        }
        if (needRotate) {
            requestRotateAndFlush()
        } else if (needFlush) {
            requestFlush()
        }
    }

    private fun requestFlush() {
        if (!_ui.value.recording) return
        val sc = scope ?: return
        if (flushInFlight) return
        flushInFlight = true
        sc.launch(Dispatchers.IO) {
            try {
                flushPending()
            } finally {
                flushInFlight = false
                val stillFull = synchronized(pendingLock) { pending.length >= FLUSH_BYTES }
                if (stillFull && _ui.value.recording) {
                    requestFlush()
                }
            }
        }
    }

    private fun requestRotateAndFlush() {
        if (!_ui.value.recording) return
        val sc = scope ?: return
        val ctx = appContext ?: return
        sc.launch(Dispatchers.IO) {
            writeMutex.withLock {
                if (!rotateFileLocked(ctx)) {
                    _ui.value = _ui.value.copy(lastError = "rotate failed")
                    // Keep buffering; periodic flush will continue on current file if rotate failed.
                }
                flushPendingLocked()
            }
        }
    }

    private fun createLogFile(context: Context): File? {
        return try {
            val savePath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
            } else {
                Environment.getExternalStorageDirectory().absolutePath + "/Download"
            }
            val dir = File(savePath)
            if (!dir.exists()) dir.mkdirs()
            GeoDebugLogRotate.uniqueFile(dir, System.currentTimeMillis(), prefix = FILE_PREFIX)
                .also { f ->
                    FileOutputStream(f, false).use { /* create empty */ }
                }
        } catch (e: Exception) {
            _ui.value = _ui.value.copy(lastError = e.message)
            null
        }
    }

    private suspend fun flushPending() {
        writeMutex.withLock { flushPendingLocked() }
    }

    private fun flushPendingLocked() {
        val chunk: String
        synchronized(pendingLock) {
            if (pending.isEmpty()) return
            chunk = pending.toString()
            pending.clear()
        }
        val file = outFile ?: return
        val bytes = chunk.toByteArray(StandardCharsets.UTF_8)
        try {
            FileOutputStream(file, true).use { fos ->
                fos.write(bytes)
            }
            flushedBytes += bytes.size
        } catch (e: Exception) {
            _ui.value = _ui.value.copy(lastError = e.message)
        }
    }

    private fun fileHeader(continuedFrom: String?): String {
        val cont = if (continuedFrom.isNullOrBlank()) {
            ""
        } else {
            "# continuedFrom=$continuedFrom\n"
        }
        return "# tbox app journal log\n" +
            "# started=${formatWall(System.currentTimeMillis())}\n" +
            "# appVer=${BuildConfig.VERSION_NAME}\n" +
            "# maxFileBytes=$MAX_FILE_BYTES part=$partIndex\n" +
            cont +
            "\n"
    }

    private fun rotateFileLocked(ctx: Context): Boolean {
        val prev = outFile ?: return false
        val next = createLogFile(ctx) ?: return false
        pending.append(
            "\n# stopped=${formatWall(System.currentTimeMillis())}" +
                " rotated=true next=${next.name} lines=${_ui.value.lines}\n",
        )
        flushPendingLocked()
        outFile = next
        flushedBytes = 0L
        partIndex += 1
        pending.append(fileHeader(continuedFrom = prev.name))
        flushPendingLocked()
        _ui.value = _ui.value.copy(filePath = next.absolutePath)
        return true
    }

    private fun formatWall(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(ms))
}
