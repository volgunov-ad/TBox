package vad.dashing.tbox.usbgnss

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vad.dashing.tbox.LocValues
import vad.dashing.tbox.TboxRepository

/**
 * Observable USB GNSS session status for the Geoposition UI.
 */
object UsbGnssRepository {
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _lastNmeaAtMs = MutableStateFlow(0L)
    val lastNmeaAtMs: StateFlow<Long> = _lastNmeaAtMs.asStateFlow()

    private val _connectedAtMs = MutableStateFlow(0L)
    val connectedAtMs: StateFlow<Long> = _connectedAtMs.asStateFlow()

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

    internal fun reset() {
        _connected.value = false
        _lastError.value = null
        _lastNmeaAtMs.value = 0L
        _connectedAtMs.value = 0L
    }

    /**
     * True when connected but no NMEA `$…` line arrived within [silenceMs] after connect.
     */
    fun needsNmeaSilenceReopen(
        nowMs: Long = System.currentTimeMillis(),
        silenceMs: Long = NMEA_SILENCE_REOPEN_MS,
    ): Boolean {
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
            existing.updateTarget(stableId, baud, requestVtg, requestZda)
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
        ).also { it.start(stableId, baud, requestVtg, requestZda) }
        TboxRepository.addLog(
            "INFO",
            "USB GNSS",
            "USB session starting id=$stableId baud=$baud " +
                "vtg=$requestVtg zda=$requestZda",
        )
    }

    @Synchronized
    fun forceReopen() {
        val s = session ?: return
        Log.i(TAG, "NMEA silence — force reopen")
        TboxRepository.addLog("WARN", "USB GNSS", "NMEA silence — reopening USB session")
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
        if (line.startsWith("$")) {
            UsbGnssRepository.markNmeaReceived()
        }
        val loc = accumulator.onLine(line) ?: return
        if (!isActive()) return
        onLocation(loc)
    }
}
