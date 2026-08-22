package vad.dashing.tbox.esp

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
import vad.dashing.tbox.BuildConfig
import vad.dashing.tbox.R
import vad.dashing.tbox.TboxRepository
import vad.dashing.tbox.location.GeoDebugLogRecorder
import vad.dashing.tbox.location.GeoDebugLogRotate
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Companion USB protocol log to Downloads (buffered append).
 * Caller filters (skip hb; throttle gps). Rotate at [MAX_FILE_BYTES].
 */
object CompanionProtocolLogRecorder {
    const val MAX_FILE_BYTES = GeoDebugLogRecorder.MAX_FILE_BYTES
    const val FLUSH_BYTES = 24 * 1024
    const val FILE_PREFIX = "tbox_companion_log_"

    data class UiState(
        val recording: Boolean = false,
        val filePath: String? = null,
        val events: Int = 0,
        val lastError: String? = null,
        val autoStopped: Boolean = false,
    )

    private val _ui = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _ui.asStateFlow()

    private var appContext: Context? = null
    private var scope: CoroutineScope? = null
    private var flushJob: Job? = null
    /** Single in-flight flush coroutine; append must not spawn IO per line. */
    @Volatile private var flushInFlight: Boolean = false
    private val writeMutex = Mutex()
    private val pending = StringBuilder(FLUSH_BYTES + 4_096)
    private val pendingLock = Any()
    private var outFile: File? = null
    private var flushedBytes: Long = 0L
    private var partIndex: Int = 1
    private var eventsPendingUi: Int = 0

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
            _ui.value = UiState(lastError = "cannot create file")
            return false
        }
        outFile = file
        pending.clear()
        flushedBytes = 0L
        partIndex = 1
        _ui.value = UiState(
            recording = true,
            filePath = file.absolutePath,
            events = 0,
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
        TboxRepository.addLog("INFO", "CompanionLog", "recording started: ${file.name}")
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
                            " auto=$auto events=${_ui.value.events}\n",
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
        TboxRepository.addLog(
            "INFO",
            "CompanionLog",
            if (auto) "recording auto-stopped: $path" else "recording stopped: $path",
        )
        return was
    }

    /**
     * Append one protocol line. Caller filters (skip heartbeat; throttle gps).
     * Buffers on the caller thread; disk flush is coalesced (size threshold or 1s job).
     */
    fun append(direction: CompanionLogDirection, text: String) {
        if (!_ui.value.recording) return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val dir = if (direction == CompanionLogDirection.TX) "TX" else "RX"
        val line = "${formatWall(System.currentTimeMillis())} $dir $trimmed\n"
        val needFlush: Boolean
        val bump: Int
        synchronized(pendingLock) {
            pending.append(line)
            eventsPendingUi++
            needFlush = pending.length >= FLUSH_BYTES
            bump = eventsPendingUi
            eventsPendingUi = 0
        }
        if (bump > 0) {
            _ui.value = _ui.value.copy(events = _ui.value.events + bump)
        }
        if (needFlush) {
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
            TboxRepository.addLog("ERROR", "CompanionLog", "create file: ${e.message}")
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
            TboxRepository.addLog("ERROR", "CompanionLog", "flush: ${e.message}")
            _ui.value = _ui.value.copy(lastError = e.message)
        }
    }

    private fun fileHeader(continuedFrom: String?): String {
        val cont = if (continuedFrom.isNullOrBlank()) {
            ""
        } else {
            "# continuedFrom=$continuedFrom\n"
        }
        return "# tbox companion protocol log\n" +
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
                " rotated=true next=${next.name} events=${_ui.value.events}\n",
        )
        flushPendingLocked()
        outFile = next
        flushedBytes = 0L
        partIndex += 1
        pending.append(fileHeader(continuedFrom = prev.name))
        flushPendingLocked()
        _ui.value = _ui.value.copy(filePath = next.absolutePath)
        TboxRepository.addLog(
            "INFO",
            "CompanionLog",
            "rotated ${prev.name} → ${next.name} part=$partIndex",
        )
        return true
    }

    private fun formatWall(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(ms))
}
