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

    internal fun setConnected(value: Boolean) {
        _connected.value = value
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
    }
}

/**
 * Owns [UsbNmeaGnssSession] and publishes NMEA fixes into [TboxRepository] when active.
 */
class UsbNmeaLocationSource(
    context: Context,
    private val isActive: () -> Boolean,
    private val onLocation: (LocValues) -> Unit,
) {
    companion object {
        private const val TAG = "UsbNmeaLocation"
    }

    private val appContext = context.applicationContext
    private val accumulator = NmeaFixAccumulator()
    private var session: UsbNmeaGnssSession? = null

    @Synchronized
    fun start(stableId: String, baud: Int) {
        if (stableId.isBlank()) {
            Log.i(TAG, "start skipped: empty device id")
            UsbGnssRepository.setLastError(null)
            stop()
            return
        }
        val existing = session
        if (existing != null) {
            accumulator.reset()
            existing.updateTarget(stableId, baud)
            return
        }
        accumulator.reset()
        UsbGnssRepository.reset()
        session = UsbNmeaGnssSession(
            context = appContext,
            onLine = { line -> handleLine(line) },
            onConnectionChanged = { connected ->
                UsbGnssRepository.setConnected(connected)
                if (!connected) {
                    accumulator.reset()
                }
            },
            onError = { err ->
                Log.w(TAG, err)
                UsbGnssRepository.setLastError(err)
                TboxRepository.addLog("WARN", "USB GNSS", err)
            },
        ).also { it.start(stableId, baud) }
        TboxRepository.addLog(
            "INFO",
            "USB GNSS",
            "session started id=$stableId baud=$baud",
        )
    }

    @Synchronized
    fun stop() {
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
