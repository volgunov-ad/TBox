package vad.dashing.tbox.usbgnss

import android.content.Context
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vad.dashing.tbox.LocValues
import vad.dashing.tbox.TboxRepository

/**
 * Observable USB GNSS session status for the Geoposition UI.
 */
object UsbGnssRepository {
    enum class AutoBaudPhase {
        IDLE,
        RUNNING,
        SUCCESS,
        FAILED,
    }

    enum class ModuleProbePhase {
        IDLE,
        RUNNING,
        SUCCESS,
        FAILED,
    }

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _lastNmeaAtMs = MutableStateFlow(0L)
    val lastNmeaAtMs: StateFlow<Long> = _lastNmeaAtMs.asStateFlow()

    private val _connectedAtMs = MutableStateFlow(0L)
    val connectedAtMs: StateFlow<Long> = _connectedAtMs.asStateFlow()

    private val _autoBaudPhase = MutableStateFlow(AutoBaudPhase.IDLE)
    val autoBaudPhase: StateFlow<AutoBaudPhase> = _autoBaudPhase.asStateFlow()

    private val _autoBaudTryingBaud = MutableStateFlow(0)
    val autoBaudTryingBaud: StateFlow<Int> = _autoBaudTryingBaud.asStateFlow()

    private val _autoBaudFoundBaud = MutableStateFlow(0)
    val autoBaudFoundBaud: StateFlow<Int> = _autoBaudFoundBaud.asStateFlow()

    private val _moduleProbePhase = MutableStateFlow(ModuleProbePhase.IDLE)
    val moduleProbePhase: StateFlow<ModuleProbePhase> = _moduleProbePhase.asStateFlow()

    private val autoBaudRequest = AtomicBoolean(false)
    private val moduleProbeRequest = AtomicBoolean(false)
    private val _probeEpochMs = MutableStateFlow(0L)
    private val _lastValidChecksumAtMs = MutableStateFlow(0L)

    internal fun setConnected(value: Boolean, atMs: Long = System.currentTimeMillis()) {
        _connected.value = value
        if (value) {
            _connectedAtMs.value = atMs
            _lastNmeaAtMs.value = 0L
        } else {
            _connectedAtMs.value = 0L
        }
    }

    internal fun setLastError(message: String?) {
        _lastError.value = message
    }

    internal fun markNmeaReceived(atMs: Long = System.currentTimeMillis()) {
        _lastNmeaAtMs.value = atMs
    }

    internal fun markValidChecksumNmea(atMs: Long = System.currentTimeMillis()) {
        _lastValidChecksumAtMs.value = atMs
        markNmeaReceived(atMs)
    }

    fun requestAutoBaudDetect() {
        autoBaudRequest.set(true)
    }

    fun consumeAutoBaudRequest(): Boolean = autoBaudRequest.getAndSet(false)

    fun requestModuleProbe() {
        moduleProbeRequest.set(true)
    }

    fun consumeModuleProbeRequest(): Boolean = moduleProbeRequest.getAndSet(false)

    fun isAutoBaudRunning(): Boolean = _autoBaudPhase.value == AutoBaudPhase.RUNNING

    fun isModuleProbeRunning(): Boolean = _moduleProbePhase.value == ModuleProbePhase.RUNNING

    internal fun beginModuleProbe() {
        _moduleProbePhase.value = ModuleProbePhase.RUNNING
    }

    internal fun finishModuleProbeSuccess() {
        _moduleProbePhase.value = ModuleProbePhase.SUCCESS
    }

    internal fun finishModuleProbeFailed() {
        _moduleProbePhase.value = ModuleProbePhase.FAILED
    }

    internal fun clearModuleProbePhaseIfTerminal() {
        when (_moduleProbePhase.value) {
            ModuleProbePhase.SUCCESS, ModuleProbePhase.FAILED -> {
                _moduleProbePhase.value = ModuleProbePhase.IDLE
            }
            else -> Unit
        }
    }

    internal fun beginAutoBaudRun() {
        _autoBaudPhase.value = AutoBaudPhase.RUNNING
        _autoBaudTryingBaud.value = 0
        _autoBaudFoundBaud.value = 0
        _probeEpochMs.value = 0L
        _lastValidChecksumAtMs.value = 0L
    }

    /** Show candidate baud in UI before USB reopen; does not arm the NMEA epoch. */
    internal fun previewAutoBaudTrying(baud: Int) {
        _autoBaudTryingBaud.value = baud
    }

    internal fun setAutoBaudTrying(baud: Int, epochMs: Long) {
        _autoBaudTryingBaud.value = baud
        _probeEpochMs.value = epochMs
        _lastValidChecksumAtMs.value = 0L
    }

    fun hasValidNmeaSinceProbeEpoch(): Boolean {
        val epoch = _probeEpochMs.value
        if (epoch <= 0L) return false
        return _lastValidChecksumAtMs.value >= epoch
    }

    internal fun finishAutoBaudSuccess(baud: Int) {
        _autoBaudFoundBaud.value = baud
        _autoBaudTryingBaud.value = 0
        _probeEpochMs.value = 0L
        _autoBaudPhase.value = AutoBaudPhase.SUCCESS
    }

    internal fun finishAutoBaudFailed() {
        _autoBaudTryingBaud.value = 0
        _autoBaudFoundBaud.value = 0
        _probeEpochMs.value = 0L
        _autoBaudPhase.value = AutoBaudPhase.FAILED
    }

    internal fun clearAutoBaudPhaseIfTerminal() {
        when (_autoBaudPhase.value) {
            AutoBaudPhase.SUCCESS, AutoBaudPhase.FAILED -> {
                _autoBaudPhase.value = AutoBaudPhase.IDLE
            }
            else -> Unit
        }
    }

    internal fun reset() {
        _connected.value = false
        _lastError.value = null
        _lastNmeaAtMs.value = 0L
        _connectedAtMs.value = 0L
        // Do not clear an in-flight auto-baud request/phase here — stop() during probe
        // would otherwise lose UI status. Probe orchestration owns phase lifecycle.
    }

    /**
     * True when connected but no NMEA `$…` line arrived within [silenceMs] after connect.
     * Skipped while auto-baud probe is running.
     */
    fun needsNmeaSilenceReopen(
        nowMs: Long = System.currentTimeMillis(),
        silenceMs: Long = NMEA_SILENCE_REOPEN_MS,
    ): Boolean {
        if (isAutoBaudRunning() || isModuleProbeRunning()) return false
        if (!_connected.value) return false
        val connectedAt = _connectedAtMs.value
        if (connectedAt <= 0L) return false
        if (nowMs - connectedAt < silenceMs) return false
        val lastNmea = _lastNmeaAtMs.value
        return lastNmea < connectedAt
    }

    const val NMEA_SILENCE_REOPEN_MS = 10_000L
}

/**
 * Owns [UsbNmeaGnssSession] and publishes NMEA fixes into [TboxRepository] when active.
 */
class UsbNmeaLocationSource(
    context: Context,
    private val isActive: () -> Boolean,
    private val onLocation: (LocValues) -> Unit,
    private val onStableIdResolved: (String) -> Unit = {},
) {
    companion object {
        private const val TAG = "UsbNmeaLocation"
    }

    private val appContext = context.applicationContext
    private val accumulator = NmeaFixAccumulator()
    private var session: UsbNmeaGnssSession? = null

    @Synchronized
    fun start(
        stableId: String,
        baud: Int,
        requestVtg: Boolean = false,
        requestZda: Boolean = false,
        requestGst: Boolean = false,
    ) {
        if (stableId.isBlank()) {
            Log.i(TAG, "start skipped: empty device id")
            UsbGnssRepository.setLastError(null)
            stop()
            return
        }
        val existing = session
        if (existing != null) {
            accumulator.reset()
            existing.updateTarget(stableId, baud, requestVtg, requestZda, requestGst)
            return
        }
        accumulator.reset()
        UsbGnssRepository.reset()
        session = UsbNmeaGnssSession(
            context = appContext,
            onLine = { line -> handleLine(line) },
            onConnectionChanged = { connected ->
                val was = UsbGnssRepository.connected.value
                UsbGnssRepository.setConnected(connected)
                if (was != connected) {
                    Log.i(TAG, if (connected) "USB connected" else "USB disconnected")
                    TboxRepository.addLog(
                        "INFO",
                        "USB GNSS",
                        if (connected) "USB connected" else "USB disconnected",
                    )
                }
                if (!connected) {
                    accumulator.reset()
                }
            },
            onError = { err ->
                Log.w(TAG, err)
                UsbGnssRepository.setLastError(err)
                TboxRepository.addLog("WARN", "USB GNSS", err)
            },
            onStableIdResolved = onStableIdResolved,
        ).also { it.start(stableId, baud, requestVtg, requestZda, requestGst) }
        TboxRepository.addLog(
            "INFO",
            "USB GNSS",
            "USB session starting id=$stableId baud=$baud " +
                "vtg=$requestVtg zda=$requestZda gst=$requestGst",
        )
    }

    @Synchronized
    fun forceReopen() {
        val s = session ?: return
        Log.i(TAG, "NMEA silence — force reopen")
        TboxRepository.addLog("WARN", "USB GNSS", "NMEA silence — reopening USB session")
        s.forceReopen()
    }

    fun currentSessionOrNull(): UsbNmeaGnssSession? = synchronized(this) { session }

    /**
     * Run identity probe on the open session (blocking IO). Returns null if no session.
     * Does not hold the source lock while waiting for replies.
     */
    fun probeModuleIdentity(): GnssModuleIdentity? {
        val s = synchronized(this) { session } ?: return null
        if (!UsbGnssRepository.connected.value) return null
        return GnssModuleProbe.probe(s)
    }

    fun writeAsciiLine(line: String): Boolean =
        synchronized(this) { session }?.writeAsciiLine(line) == true

    fun writeRaw(payload: ByteArray): Boolean =
        synchronized(this) { session }?.writeRaw(payload) == true

    fun execAsciiCommand(cmd: String, timeoutMs: Long = 2_000L): List<String> =
        synchronized(this) { session }?.execAsciiCommand(cmd, timeoutMs).orEmpty()

    /**
     * Auto-baud: apply [baud] and **always** close/reopen so UART vendor init runs even when
     * the rate matches the current session (updateTarget alone would skip reconnect).
     */
    @Synchronized
    fun reopenForAutoBaudProbe(stableId: String, baud: Int) {
        if (stableId.isBlank()) return
        start(
            stableId = stableId,
            baud = baud,
            requestVtg = false,
            requestZda = false,
            requestGst = false,
        )
        val s = session ?: return
        Log.i(TAG, "auto-baud reopen id=$stableId baud=$baud")
        s.forceReopen()
    }

    @Synchronized
    fun stop() {
        if (session != null) {
            Log.i(TAG, "stopping USB GNSS session")
            TboxRepository.addLog("INFO", "USB GNSS", "USB session stopped")
        }
        session?.close()
        session = null
        accumulator.reset()
        UsbGnssRepository.reset()
    }

    private fun handleLine(line: String) {
        if (!isActive()) return
        if (UsbGnssAutoBaud.hasValidChecksum(line)) {
            UsbGnssRepository.markValidChecksumNmea()
        } else if (line.startsWith("$")) {
            UsbGnssRepository.markNmeaReceived()
        }
        val loc = accumulator.onLine(line) ?: return
        if (!isActive()) return
        onLocation(loc)
    }
}
