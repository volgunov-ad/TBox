package vad.dashing.tbox.adb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import vad.dashing.tbox.TboxRepository
import vad.dashing.tbox.location.GeoDebugLogRotate

object AdbRepository {
    private const val JOURNAL_TAG = "ADB"
    private const val SCRIPT_PAUSE_MS = 1_000L
    private const val LOGCAT_FLUSH_BYTES = 32 * 1024

    const val ACTION_USB_PERMISSION = "vad.dashing.tbox.ADB_USB_PERMISSION"

    enum class Phase {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR,
    }

    enum class TransportType {
        TCP,
        USB,
    }

    enum class ScriptOutcome {
        COMPLETED,
        STOPPED,
        ABORTED,
        CANCELLED,
    }

    data class State(
        val phase: Phase = Phase.DISCONNECTED,
        val transport: TransportType? = null,
        val endpoint: String = "",
        val banner: String = "",
        val error: String? = null,
    )

    data class UsbCandidate(
        val deviceId: Int,
        val name: String,
        val vendorId: Int,
        val productId: Int,
        val hasPermission: Boolean,
        /** Same USB device also carries RNDIS/CDC-net (typical TBox). */
        val sharesNetworkWithHost: Boolean = false,
    )

    /**
     * Interactive shell-script run from a user-picked text file (ADB tab).
     * Idle when [active] is false and [outcome] is null.
     */
    data class ScriptRunState(
        val active: Boolean = false,
        /** 1-based index of the command in progress / last attempted; 0 before first. */
        val currentIndex: Int = 0,
        val total: Int = 0,
        val currentCommand: String = "",
        val awaitingFailureDecision: Boolean = false,
        val failureDetail: String? = null,
        val outcome: ScriptOutcome? = null,
        val completedCount: Int = 0,
        val failedCount: Int = 0,
    )

    /**
     * Full logcat capture to Downloads (`tbox_logcat_…`). Session-only.
     */
    data class LogcatCaptureState(
        val active: Boolean = false,
        val filePath: String? = null,
        val bytesWritten: Long = 0L,
        val lastError: String? = null,
    )

    private const val MAX_LOG_LINES = 500
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val _state = MutableStateFlow(State())
    private val _usbCandidates = MutableStateFlow<List<UsbCandidate>>(emptyList())
    private val _consoleLog = MutableStateFlow<List<String>>(emptyList())
    private val _scriptRun = MutableStateFlow(ScriptRunState())
    private val _logcatCapture = MutableStateFlow(LogcatCaptureState())

    val state: StateFlow<State> = _state.asStateFlow()
    val usbCandidates: StateFlow<List<UsbCandidate>> = _usbCandidates.asStateFlow()
    val consoleLog: StateFlow<List<String>> = _consoleLog.asStateFlow()
    val scriptRun: StateFlow<ScriptRunState> = _scriptRun.asStateFlow()
    val logcatCapture: StateFlow<LogcatCaptureState> = _logcatCapture.asStateFlow()

    @Volatile private var initialized = false
    private lateinit var appContext: Context
    private lateinit var usbManager: UsbManager
    private var connection: AdbConnection? = null
    private var connectedUsbDeviceId: Int? = null
    /** Set for the whole USB open/connect attempt, including before CNXN completes. */
    @Volatile private var activeUsbDeviceId: Int? = null
    private var pendingUsbDeviceId: Int? = null
    private var scriptJob: Job? = null
    private val scriptStopRequested = AtomicBoolean(false)
    @Volatile private var scriptFailureDecision: CompletableDeferred<Boolean>? = null
    private var logcatJob: Job? = null
    private val logcatStopRequested = AtomicBoolean(false)
    private val logcatExclusive = AtomicBoolean(false)

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            runCatching {
                when (intent?.action) {
                    UsbManager.ACTION_USB_DEVICE_ATTACHED -> refreshUsbDevices()
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        val device = extraUsbDevice(intent)
                        refreshUsbDevices()
                        if (device == null) return@runCatching
                        val detachedId = device.deviceId
                        // Always drop a parked handle for this id — device is gone.
                        AdbUsbTransport.discardParkedNetworkConnection(detachedId)
                        val wasActive = detachedId == connectedUsbDeviceId ||
                            detachedId == activeUsbDeviceId
                        if (wasActive) {
                            scope.launch {
                                mutex.withLock {
                                    closeConnection(deviceGone = true)
                                    setError(
                                        TransportType.USB,
                                        runCatching { device.deviceName }.getOrDefault("USB"),
                                        "USB device disconnected",
                                    )
                                }
                            }
                        }
                    }
                    ACTION_USB_PERMISSION -> {
                        val device = extraUsbDevice(intent)
                        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        refreshUsbDevices()
                        if (device != null && device.deviceId == pendingUsbDeviceId) {
                            pendingUsbDeviceId = null
                            if (granted) {
                                connectUsb(device.deviceId)
                            } else {
                                setError(TransportType.USB, device.deviceName, "USB permission denied")
                            }
                        }
                    }
                }
            }
        }
    }

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        usbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager
        val filter = IntentFilter(ACTION_USB_PERMISSION).apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            appContext.registerReceiver(usbReceiver, filter)
        }
        initialized = true
        refreshUsbDevices()
    }

    fun refreshUsbDevices() {
        if (!initialized) return
        _usbCandidates.value = runCatching {
            usbManager.deviceList.values
                .filter(AdbUsbTransport::isAdbDevice)
                // Prefer dedicated ADB gadgets over TBox RNDIS+ADB composites.
                .sortedWith(
                    compareBy<UsbDevice> { AdbUsbTransport.sharesNetworkWithHost(it) }
                        .thenBy { it.deviceName },
                )
                .map {
                    UsbCandidate(
                        deviceId = it.deviceId,
                        name = usbDisplayName(it),
                        vendorId = it.vendorId,
                        productId = it.productId,
                        hasPermission = runCatching { usbManager.hasPermission(it) }.getOrDefault(false),
                        sharesNetworkWithHost = AdbUsbTransport.sharesNetworkWithHost(it),
                    )
                }
        }.getOrDefault(_usbCandidates.value)
    }

    fun requestUsbPermission(deviceId: Int) {
        if (!initialized) return
        val device = findUsbDevice(deviceId) ?: run {
            setError(TransportType.USB, "", "ADB USB device not found")
            return
        }
        if (usbManager.hasPermission(device)) {
            connectUsb(deviceId)
            return
        }
        pendingUsbDeviceId = deviceId
        _state.value = State(
            phase = Phase.CONNECTING,
            transport = TransportType.USB,
            endpoint = usbDisplayName(device),
        )
        appendLog("USB permission requested for ${usbDisplayName(device)}")
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val intent = PendingIntent.getBroadcast(
            appContext,
            deviceId,
            Intent(ACTION_USB_PERMISSION).setPackage(appContext.packageName),
            flags,
        )
        usbManager.requestPermission(device, intent)
    }

    fun connectTcp(host: String, port: Int) {
        if (!initialized) return
        scope.launch {
            mutex.withLock {
                closeConnection()
                val endpoint = "$host:$port"
                _state.value = State(Phase.CONNECTING, TransportType.TCP, endpoint)
                appendLog("Connecting to $endpoint")
                runCatching {
                    val transport = AdbTcpTransport.connect(host, port)
                    connectTransport(transport, TransportType.TCP, endpoint, null)
                }.onFailure {
                    closeConnection()
                    setError(TransportType.TCP, endpoint, it.message ?: it.javaClass.simpleName)
                }
            }
        }
    }

    fun connectUsb(deviceId: Int) {
        if (!initialized) return
        val device = findUsbDevice(deviceId) ?: run {
            setError(TransportType.USB, "", "ADB USB device not found")
            return
        }
        if (!usbManager.hasPermission(device)) {
            requestUsbPermission(deviceId)
            return
        }
        scope.launch {
            mutex.withLock {
                closeConnection()
                val endpoint = usbDisplayName(device)
                val sharesNetwork = AdbUsbTransport.sharesNetworkWithHost(device)
                activeUsbDeviceId = deviceId
                _state.value = State(Phase.CONNECTING, TransportType.USB, endpoint)
                appendLog("Connecting to USB $endpoint")
                if (sharesNetwork) {
                    appendLog(
                        "Note: composite USB with RNDIS (TBox). " +
                            "Keeping USB handle across disconnect so RNDIS is not unbound.",
                    )
                }
                runCatching {
                    val transport = AdbUsbTransport.open(usbManager, device)
                    connectTransport(transport, TransportType.USB, endpoint, deviceId)
                }.onFailure {
                    val gone = findUsbDevice(deviceId) == null
                    closeConnection(deviceGone = gone)
                    if (_state.value.error != "USB device disconnected") {
                        setError(TransportType.USB, endpoint, it.message ?: it.javaClass.simpleName)
                    }
                }
            }
        }
    }

    fun disconnect() {
        cancelScriptRun(ScriptOutcome.CANCELLED)
        requestLogcatStop()
        scope.launch {
            // Unblock a stuck logcat stream before tearing down the transport.
            awaitLogcatJob()
            mutex.withLock {
                val endpoint = _state.value.endpoint
                AdbShutdownGate.withIntentionalTransportClose {
                    closeConnection()
                }
                _state.value = State()
                if (endpoint.isNotEmpty()) appendLog("Disconnected from $endpoint")
            }
        }
    }

    fun execute(command: String) {
        val normalized = command.trim()
        if (normalized.isEmpty()) return
        if (_scriptRun.value.active) {
            appendLog("Script run in progress — shell field blocked")
            return
        }
        if (logcatExclusive.get() || _logcatCapture.value.active) {
            appendLog("Logcat capture in progress — shell field blocked")
            return
        }
        scope.launch {
            mutex.withLock {
                executeConnectedLocked(normalized)
            }
        }
    }

    /**
     * Starts a sequential shell-script run on the live tab connection.
     * Returns false if already running, not connected, or [commands] is empty.
     */
    fun startScript(commands: List<String>): Boolean {
        if (commands.isEmpty()) return false
        if (_scriptRun.value.active) return false
        if (logcatExclusive.get() || _logcatCapture.value.active) return false
        if (_state.value.phase != Phase.CONNECTED || connection == null) return false
        scriptStopRequested.set(false)
        scriptFailureDecision = null
        _scriptRun.value = ScriptRunState(
            active = true,
            currentIndex = 0,
            total = commands.size,
        )
        appendLog("Script: starting ${commands.size} command(s)")
        TboxRepository.addLog("INFO", JOURNAL_TAG, "Script start: ${commands.size} command(s)")
        scriptJob = scope.launch {
            runScript(commands)
        }
        return true
    }

    fun isLogcatCapturing(): Boolean = _logcatCapture.value.active || logcatExclusive.get()

    /**
     * Starts full logcat capture (`logcat -v threadtime`) to Downloads.
     * Uses the live ADB-tab connection (TCP or USB). Returns false if busy / not connected.
     */
    fun startLogcatCapture(clearBufferFirst: Boolean): Boolean {
        if (!initialized) return false
        if (_logcatCapture.value.active || logcatExclusive.get()) return false
        if (_scriptRun.value.active) return false
        if (_state.value.phase != Phase.CONNECTED || connection == null) return false
        val file = createLogcatFile() ?: run {
            _logcatCapture.value = LogcatCaptureState(
                active = false,
                lastError = "cannot create Downloads file",
            )
            appendLog("Logcat: cannot create Downloads file")
            return false
        }
        if (!logcatExclusive.compareAndSet(false, true)) return false
        logcatStopRequested.set(false)
        _logcatCapture.value = LogcatCaptureState(
            active = true,
            filePath = file.absolutePath,
            bytesWritten = 0L,
            lastError = null,
        )
        appendLog("Logcat: recording → ${file.name}")
        TboxRepository.addLog("INFO", JOURNAL_TAG, "Logcat capture start: ${file.name}")
        logcatJob = scope.launch {
            runLogcatCapture(file, clearBufferFirst)
        }
        return true
    }

    /** Request stop of an active logcat capture (current stream ends via CLSE). */
    fun stopLogcatCapture() {
        if (!_logcatCapture.value.active && logcatJob == null) return
        requestLogcatStop()
        appendLog("Logcat: stop requested")
    }

    /** Request stop of remaining commands (current command still finishes). */
    fun stopScript() {
        if (!_scriptRun.value.active) return
        scriptStopRequested.set(true)
        // If paused on failure, treat Stop as abort.
        scriptFailureDecision?.complete(false)
        appendLog("Script: stop requested")
    }

    /** Continue after a failed command (user chose continue). */
    fun continueScriptAfterFailure() {
        scriptFailureDecision?.complete(true)
    }

    /** Abort remaining commands after a failed command. */
    fun abortScriptAfterFailure() {
        scriptFailureDecision?.complete(false)
    }

    /** Clears a terminal [ScriptRunState.outcome] so the UI can dismiss the summary. */
    fun acknowledgeScriptFinished() {
        val current = _scriptRun.value
        if (current.active || current.outcome == null) return
        _scriptRun.value = ScriptRunState()
    }

    fun isScriptRunning(): Boolean = _scriptRun.value.active

    fun clearLog() {
        _consoleLog.value = emptyList()
    }

    /**
     * Runs [block] with a shell executor for [host]:[port], exclusive vs the ADB tab.
     *
     * If this repository already holds a live TCP session to the same endpoint, reuses
     * that connection under [mutex] (no second adbd client). Otherwise opens an ephemeral
     * TCP session without replacing a USB or unrelated TCP session.
     *
     * Used by [LocalhostAdbSession.AndroidGateway] for grants, automations, virtual display,
     * and AppList advanced ADB actions.
     */
    suspend fun <T> withTcpShellSession(
        host: String,
        port: Int,
        connectTimeoutMs: Int,
        sessionTimeoutMs: Int,
        keysDir: File,
        clientName: String,
        block: (execute: (String) -> AdbShellResult) -> T,
    ): T {
        if (!initialized) {
            return AdbExclusiveTcpShell.openEphemeral(
                host = host,
                port = port,
                connectTimeoutMs = connectTimeoutMs,
                sessionTimeoutMs = sessionTimeoutMs,
                keysDir = keysDir,
                clientName = clientName,
                block = block,
            )
        }
        // Logcat holds the live shell stream; never reuse that connection mid-capture.
        if (logcatExclusive.get() || _logcatCapture.value.active) {
            return AdbExclusiveTcpShell.openEphemeral(
                host = host,
                port = port,
                connectTimeoutMs = connectTimeoutMs,
                sessionTimeoutMs = sessionTimeoutMs,
                keysDir = keysDir,
                clientName = clientName,
                block = block,
            )
        }
        return mutex.withLock {
            if (logcatExclusive.get() || _logcatCapture.value.active) {
                return@withLock AdbExclusiveTcpShell.openEphemeral(
                    host = host,
                    port = port,
                    connectTimeoutMs = connectTimeoutMs,
                    sessionTimeoutMs = sessionTimeoutMs,
                    keysDir = keysDir,
                    clientName = clientName,
                    block = block,
                )
            }
            val state = _state.value
            val active = connection
            val mode = AdbExclusiveTcpShell.selectMode(
                repositoryInitialized = true,
                phase = state.phase,
                transport = state.transport,
                endpoint = state.endpoint,
                hasConnection = active != null,
                host = host,
                port = port,
            )
            when (mode) {
                AdbExclusiveTcpShell.Mode.ReuseLiveTcp -> {
                    val live = checkNotNull(active) { "ReuseLiveTcp without connection" }
                    appendLog("Reusing open TCP session for automation (${AdbEndpoints.format(host, port)})")
                    block { command -> live.execute(command) }
                }
                AdbExclusiveTcpShell.Mode.Ephemeral -> {
                    AdbExclusiveTcpShell.openEphemeral(
                        host = host,
                        port = port,
                        connectTimeoutMs = connectTimeoutMs,
                        sessionTimeoutMs = sessionTimeoutMs,
                        keysDir = keysDir,
                        clientName = clientName,
                        block = block,
                    )
                }
            }
        }
    }

    private fun requestLogcatStop() {
        logcatStopRequested.set(true)
    }

    private suspend fun awaitLogcatJob() {
        val job = logcatJob ?: return
        // Prefer clean CLSE stop; if still stuck, intentional close unblocks receive.
        if (job.isActive) {
            requestLogcatStop()
            val joined = withTimeoutOrNull(2_000L) { job.join() }
            if (joined == null && job.isActive) {
                AdbShutdownGate.withIntentionalTransportClose {
                    runCatching { connection?.close() }
                }
                runCatching { job.join() }
            }
        }
    }

    private suspend fun runLogcatCapture(file: File, clearBufferFirst: Boolean) {
        val pending = StringBuilder(LOGCAT_FLUSH_BYTES + 4_096)
        var bytesWritten = 0L
        fun flushPending() {
            if (pending.isEmpty()) return
            val chunk = pending.toString()
            pending.clear()
            val written = runCatching {
                FileOutputStream(file, true).use { fos ->
                    val bytes = chunk.toByteArray(Charsets.UTF_8)
                    fos.write(bytes)
                    bytes.size
                }
            }.getOrElse { error ->
                _logcatCapture.value = _logcatCapture.value.copy(lastError = error.message)
                0
            }
            if (written > 0) {
                bytesWritten += written.toLong()
                _logcatCapture.value = _logcatCapture.value.copy(bytesWritten = bytesWritten)
            }
        }
        fun onChunk(data: ByteArray) {
            if (data.isEmpty()) return
            pending.append(data.toString(Charsets.UTF_8))
            if (pending.length >= LOGCAT_FLUSH_BYTES) flushPending()
        }
        try {
            val active = mutex.withLock {
                connection.takeIf { _state.value.phase == Phase.CONNECTED }
            }
            if (active == null) {
                appendLog("Logcat: not connected")
                return
            }
            val commands = AdbLogcatCapture.buildCommands(clearBufferFirst)
            for (command in commands) {
                if (logcatStopRequested.get() || AdbShutdownGate.isAppShuttingDown()) break
                if (command == AdbLogcatCapture.CLEAR_COMMAND) {
                    appendLog("$ $command")
                    // Short execute still needs exclusive access vs tab shell / scripts.
                    mutex.withLock {
                        if (connection !== active || _state.value.phase != Phase.CONNECTED) {
                            return@withLock
                        }
                        runCatching { active.execute(command) }
                            .onSuccess { result ->
                                appendOutput(result.stdout)
                                appendOutput(result.stderr)
                                result.exitCode?.let { appendLog("Exit code: $it") }
                            }
                            .onFailure { error ->
                                val message = error.message ?: error.javaClass.simpleName
                                if (AdbIoErrors.isBenignDisconnectMessage(message) &&
                                    AdbShutdownGate.shouldSuppressBenignDisconnect()
                                ) {
                                    appendLog("Logcat: stopped ($message)")
                                } else {
                                    appendLog("Logcat clear failed: $message")
                                    _logcatCapture.value =
                                        _logcatCapture.value.copy(lastError = message)
                                }
                            }
                    }
                    continue
                }
                appendLog("$ $command")
                // Stream without holding [mutex] so disconnect / USB detach can proceed;
                // [logcatExclusive] blocks tab shell and forces ephemeral automation sessions.
                val end = runCatching {
                    active.streamShell(
                        command = command,
                        shouldStop = {
                            logcatStopRequested.get() || AdbShutdownGate.isAppShuttingDown()
                        },
                        onStdout = ::onChunk,
                        onStderr = ::onChunk,
                    )
                }.fold(
                    onSuccess = { it },
                    onFailure = { error ->
                        val message = error.message ?: error.javaClass.simpleName
                        if (AdbIoErrors.isBenignDisconnectMessage(message) &&
                            (
                                logcatStopRequested.get() ||
                                    AdbShutdownGate.shouldSuppressBenignDisconnect()
                                )
                        ) {
                            appendLog("Logcat: stopped ($message)")
                            TboxRepository.addLog(
                                "DEBUG",
                                JOURNAL_TAG,
                                "Logcat stopped ($message)",
                            )
                            AdbShellStreamEnd.Stopped
                        } else if (AdbIoErrors.isBenignDisconnectMessage(message)) {
                            // Unexpected peer close mid-capture — console only, never Toast (#401/#402).
                            appendLog("Logcat: transport closed ($message)")
                            AdbShellStreamEnd.Stopped
                        } else {
                            appendLog("Logcat error: $message")
                            _logcatCapture.value =
                                _logcatCapture.value.copy(lastError = message)
                            null
                        }
                    },
                )
                when (end) {
                    AdbShellStreamEnd.Stopped -> appendLog("Logcat: stopped")
                    is AdbShellStreamEnd.Completed -> {
                        end.exitCode?.let { appendLog("Logcat exit code: $it") }
                        appendLog("Logcat: stream ended")
                    }
                    null -> Unit
                }
            }
        } catch (e: CancellationException) {
            appendLog("Logcat: cancelled")
            throw e
        } finally {
            flushPending()
            val path = file.absolutePath
            appendLog("Logcat: saved $path ($bytesWritten bytes)")
            TboxRepository.addLog(
                "INFO",
                JOURNAL_TAG,
                "Logcat capture end: ${file.name} bytes=$bytesWritten",
            )
            _logcatCapture.value = _logcatCapture.value.copy(
                active = false,
                filePath = path,
                bytesWritten = bytesWritten,
            )
            logcatExclusive.set(false)
            logcatStopRequested.set(false)
            logcatJob = null
        }
    }

    private fun createLogcatFile(): File? {
        return try {
            val savePath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
            } else {
                Environment.getExternalStorageDirectory().absolutePath + "/Download"
            }
            val dir = File(savePath)
            if (!dir.exists()) dir.mkdirs()
            val wallMs = System.currentTimeMillis()
            val preferred = File(dir, AdbLogcatCapture.fileName(wallMs))
            val file = if (preferred.exists()) {
                GeoDebugLogRotate.uniqueFile(dir, wallMs, prefix = AdbLogcatCapture.FILE_PREFIX)
            } else {
                preferred
            }
            FileOutputStream(file, false).use { /* create empty */ }
            file
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun runScript(commands: List<String>) {
        var completed = 0
        var failed = 0
        var outcome = ScriptOutcome.COMPLETED
        try {
            for ((index, rawCommand) in commands.withIndex()) {
                val n = index + 1
                if (AdbShutdownGate.isAppShuttingDown()) {
                    outcome = ScriptOutcome.CANCELLED
                    break
                }
                if (scriptStopRequested.get()) {
                    outcome = ScriptOutcome.STOPPED
                    break
                }
                _scriptRun.value = _scriptRun.value.copy(
                    active = true,
                    currentIndex = n,
                    total = commands.size,
                    currentCommand = rawCommand,
                    awaitingFailureDecision = false,
                    failureDetail = null,
                    completedCount = completed,
                    failedCount = failed,
                )

                val stepResult = mutex.withLock {
                    executeConnectedLocked(rawCommand)
                }

                when (stepResult) {
                    is ScriptStepResult.Ok -> {
                        completed++
                        TboxRepository.addLog(
                            "INFO",
                            JOURNAL_TAG,
                            "Script [$n/${commands.size}] ok: $rawCommand",
                        )
                    }
                    is ScriptStepResult.Failed -> {
                        failed++
                        TboxRepository.addLog(
                            "WARN",
                            JOURNAL_TAG,
                            "Script [$n/${commands.size}] fail: $rawCommand — ${stepResult.detail}",
                        )
                        if (scriptStopRequested.get() || AdbShutdownGate.isAppShuttingDown()) {
                            outcome = if (AdbShutdownGate.isAppShuttingDown()) {
                                ScriptOutcome.CANCELLED
                            } else {
                                ScriptOutcome.STOPPED
                            }
                            break
                        }
                        val decision = CompletableDeferred<Boolean>()
                        scriptFailureDecision = decision
                        _scriptRun.value = _scriptRun.value.copy(
                            awaitingFailureDecision = true,
                            failureDetail = stepResult.detail,
                            completedCount = completed,
                            failedCount = failed,
                        )
                        val continueRun = try {
                            decision.await()
                        } finally {
                            if (scriptFailureDecision === decision) {
                                scriptFailureDecision = null
                            }
                        }
                        _scriptRun.value = _scriptRun.value.copy(
                            awaitingFailureDecision = false,
                            failureDetail = null,
                        )
                        if (!continueRun) {
                            outcome = if (scriptStopRequested.get()) {
                                ScriptOutcome.STOPPED
                            } else {
                                ScriptOutcome.ABORTED
                            }
                            break
                        }
                    }
                    is ScriptStepResult.TransportLost -> {
                        failed++
                        TboxRepository.addLog(
                            "WARN",
                            JOURNAL_TAG,
                            "Script [$n/${commands.size}] fail: $rawCommand — ${stepResult.detail}",
                        )
                        outcome = if (AdbShutdownGate.shouldSuppressBenignDisconnect()) {
                            ScriptOutcome.CANCELLED
                        } else {
                            ScriptOutcome.ABORTED
                        }
                        break
                    }
                    is ScriptStepResult.NotConnected -> {
                        failed++
                        TboxRepository.addLog(
                            "WARN",
                            JOURNAL_TAG,
                            "Script [$n/${commands.size}] fail: not connected",
                        )
                        outcome = ScriptOutcome.ABORTED
                        break
                    }
                }

                if (n < commands.size) {
                    if (scriptStopRequested.get()) {
                        outcome = ScriptOutcome.STOPPED
                        break
                    }
                    if (AdbShutdownGate.isAppShuttingDown()) {
                        outcome = ScriptOutcome.CANCELLED
                        break
                    }
                    delay(SCRIPT_PAUSE_MS)
                    if (scriptStopRequested.get()) {
                        outcome = ScriptOutcome.STOPPED
                        break
                    }
                    if (AdbShutdownGate.isAppShuttingDown()) {
                        outcome = ScriptOutcome.CANCELLED
                        break
                    }
                }
            }
        } catch (e: CancellationException) {
            outcome = ScriptOutcome.CANCELLED
            throw e
        } finally {
            finishScript(
                outcome = outcome,
                completed = completed,
                failed = failed,
                total = commands.size,
            )
        }
    }

    private fun finishScript(
        outcome: ScriptOutcome,
        completed: Int,
        failed: Int,
        total: Int,
    ) {
        scriptFailureDecision = null
        scriptStopRequested.set(false)
        val summary = when (outcome) {
            ScriptOutcome.COMPLETED -> "Script: all $total command(s) completed"
            ScriptOutcome.STOPPED ->
                "Script: stopped after $completed ok / $failed fail of $total"
            ScriptOutcome.ABORTED ->
                "Script: aborted after $completed ok / $failed fail of $total"
            ScriptOutcome.CANCELLED ->
                "Script: cancelled after $completed ok / $failed fail of $total"
        }
        appendLog(summary)
        val level = if (outcome == ScriptOutcome.COMPLETED) "INFO" else "WARN"
        TboxRepository.addLog(level, JOURNAL_TAG, summary)
        _scriptRun.value = ScriptRunState(
            active = false,
            currentIndex = completed + failed,
            total = total,
            outcome = outcome,
            completedCount = completed,
            failedCount = failed,
        )
        scriptJob = null
    }

    private fun cancelScriptRun(outcome: ScriptOutcome) {
        if (!_scriptRun.value.active && scriptJob == null) return
        scriptStopRequested.set(true)
        scriptFailureDecision?.complete(false)
        val job = scriptJob
        if (job != null) {
            job.cancel()
            // finishScript runs in runScript's finally with CANCELLED (or STOPPED if already decided).
        } else if (_scriptRun.value.active) {
            val prev = _scriptRun.value
            _scriptRun.value = ScriptRunState(
                active = false,
                currentIndex = prev.currentIndex,
                total = prev.total,
                outcome = outcome,
                completedCount = prev.completedCount,
                failedCount = prev.failedCount,
            )
        }
    }

    private sealed class ScriptStepResult {
        data object Ok : ScriptStepResult()
        data class Failed(val detail: String) : ScriptStepResult()
        data class TransportLost(val detail: String) : ScriptStepResult()
        data object NotConnected : ScriptStepResult()
    }

    /**
     * Same path as the manual shell field; caller must hold [mutex].
     */
    private fun executeConnectedLocked(normalized: String): ScriptStepResult {
        val active = connection
        if (active == null || _state.value.phase != Phase.CONNECTED) {
            appendLog("Not connected")
            return ScriptStepResult.NotConnected
        }
        appendLog("$ $normalized")
        return runCatching { active.execute(normalized) }
            .fold(
                onSuccess = { result ->
                    appendOutput(result.stdout)
                    appendOutput(result.stderr)
                    result.exitCode?.let { appendLog("Exit code: $it") }
                    val failure = AdbShellResults.failureDetail(result)
                    if (failure != null) {
                        appendLog("Command failed: $failure")
                        ScriptStepResult.Failed(failure)
                    } else {
                        ScriptStepResult.Ok
                    }
                },
                onFailure = { error ->
                    val previous = _state.value
                    val gone = previous.transport == TransportType.USB &&
                        connectedUsbDeviceId?.let { findUsbDevice(it) == null } == true
                    closeConnection(deviceGone = gone)
                    val message = error.message ?: error.javaClass.simpleName
                    if (AdbIoErrors.isBenignDisconnectMessage(message) &&
                        AdbShutdownGate.shouldSuppressBenignDisconnect()
                    ) {
                        _state.value = State()
                        appendLog("Disconnected ($message)")
                        // Expected teardown: ADB-tab console + DEBUG journal (not ERROR spam).
                        TboxRepository.addLog(
                            "DEBUG",
                            JOURNAL_TAG,
                            "Disconnected ($message)",
                        )
                    } else {
                        setError(
                            previous.transport,
                            previous.endpoint,
                            message,
                        )
                    }
                    ScriptStepResult.TransportLost(message)
                },
            )
    }

    private fun connectTransport(
        transport: AdbTransport,
        type: TransportType,
        endpoint: String,
        usbDeviceId: Int?,
    ) {
        val newConnection = AdbConnection(
            transport,
            AdbAuthKeys.loadOrCreate(appContext.filesDir.resolve("adb")),
            "tbox@${Build.MODEL}",
        )
        try {
            val info = newConnection.connect()
            connection = newConnection
            connectedUsbDeviceId = usbDeviceId
            _state.value = State(
                phase = Phase.CONNECTED,
                transport = type,
                endpoint = endpoint,
                banner = info.banner,
            )
            appendLog("Connected to $endpoint")
        } catch (error: Exception) {
            val deviceGone = usbDeviceId != null && findUsbDevice(usbDeviceId) == null
            runCatching {
                if (deviceGone) newConnection.abandonUsb() else newConnection.close()
            }
            throw error
        }
    }

    private fun closeConnection(deviceGone: Boolean = false) {
        val previous = connection
        connection = null
        connectedUsbDeviceId = null
        activeUsbDeviceId = null
        if (deviceGone) {
            runCatching { previous?.abandonUsb() }
        } else {
            runCatching { previous?.close() }
        }
    }

    private fun setError(type: TransportType?, endpoint: String, message: String) {
        if (AdbIoErrors.isBenignDisconnectMessage(message) &&
            AdbShutdownGate.shouldSuppressBenignDisconnect()
        ) {
            _state.value = State()
            appendLog("Disconnected ($message)")
            // Expected teardown: keep default INFO journal quiet; DEBUG still records.
            TboxRepository.addLog("DEBUG", JOURNAL_TAG, "Disconnected ($message)")
            return
        }
        _state.value = State(
            phase = Phase.ERROR,
            transport = type,
            endpoint = endpoint,
            error = message,
        )
        appendLog("Error: $message")
        // ADB-tab console is primary for interactive use; mirror real failures to the journal.
        TboxRepository.addLog("ERROR", JOURNAL_TAG, message)
    }

    private fun appendOutput(output: String) {
        if (output.isEmpty()) return
        output.replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n')
            .dropLastWhile { it.isEmpty() }
            .forEach(::appendLog)
    }

    private fun appendLog(message: String) {
        val time = synchronized(timeFormat) { timeFormat.format(Date()) }
        _consoleLog.value = (_consoleLog.value + "[$time] $message").takeLast(MAX_LOG_LINES)
    }

    private fun findUsbDevice(deviceId: Int): UsbDevice? =
        usbManager.deviceList.values.firstOrNull {
            it.deviceId == deviceId && AdbUsbTransport.isAdbDevice(it)
        }

    private fun usbDisplayName(device: UsbDevice): String {
        val product = runCatching { device.productName }.getOrNull()?.takeIf { it.isNotBlank() }
        return product ?: device.deviceName
    }

    private fun extraUsbDevice(intent: Intent): UsbDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }
}
