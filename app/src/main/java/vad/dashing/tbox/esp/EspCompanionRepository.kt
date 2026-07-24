package vad.dashing.tbox.esp

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vad.dashing.tbox.LocValues

data class EspDeviceInfo(
    val firmwareVersion: String = "",
    val gpioInCount: Int = 0,
    val relayCount: Int = 0,
    val um980: Boolean = false,
    val um980Baud: Int = 115200,
)

data class Um980LastResponse(
    val cmd: String = "",
    val lines: List<String> = emptyList(),
    val ok: Boolean = false,
    val atMs: Long = 0L,
)

enum class Um980LogDirection {
    TX,
    RX,
}

data class Um980LogEntry(
    val atMs: Long,
    val direction: Um980LogDirection,
    val text: String,
)

/**
 * Live state of the ESP32 USB companion.
 * GPS from the device is also mirrored into [vad.dashing.tbox.TboxRepository] when
 * [LocationSource.ESP32] is selected (see [EspCompanionManager]).
 */
object EspCompanionRepository {
    private const val UM980_LOG_MAX = 100
    private const val UM980_GEO_LOG_MIN_INTERVAL_MS = 5_000L

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _deviceInfo = MutableStateFlow(EspDeviceInfo())
    val deviceInfo: StateFlow<EspDeviceInfo> = _deviceInfo.asStateFlow()

    private val _locValues = MutableStateFlow(LocValues())
    val locValues: StateFlow<LocValues> = _locValues.asStateFlow()

    private val _gpioMask = MutableStateFlow(0)
    val gpioMask: StateFlow<Int> = _gpioMask.asStateFlow()

    private val _relayMask = MutableStateFlow(0)
    val relayMask: StateFlow<Int> = _relayMask.asStateFlow()

    private val _lastHeartbeatAtMs = MutableStateFlow(0L)
    val lastHeartbeatAtMs: StateFlow<Long> = _lastHeartbeatAtMs.asStateFlow()

    private val _lastGpsAtMs = MutableStateFlow(0L)
    val lastGpsAtMs: StateFlow<Long> = _lastGpsAtMs.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _lastMessageAtMs = MutableStateFlow(0L)
    val lastMessageAtMs: StateFlow<Long> = _lastMessageAtMs.asStateFlow()

    private val _lastUm980Response = MutableStateFlow(Um980LastResponse())
    val lastUm980Response: StateFlow<Um980LastResponse> = _lastUm980Response.asStateFlow()

    private val _um980ConfigSnapshot = MutableStateFlow(Um980ConfigSnapshot())
    val um980ConfigSnapshot: StateFlow<Um980ConfigSnapshot> = _um980ConfigSnapshot.asStateFlow()

    private val _otaBusy = MutableStateFlow(false)
    val otaBusy: StateFlow<Boolean> = _otaBusy.asStateFlow()

    private val _otaProgress = MutableStateFlow(0)
    val otaProgress: StateFlow<Int> = _otaProgress.asStateFlow()

    private val _otaError = MutableStateFlow<String?>(null)
    val otaError: StateFlow<String?> = _otaError.asStateFlow()

    /** Profile/SAVECONFIG/refresh batch — UI should disable UM980 controls. */
    private val _um980ConfigBusy = MutableStateFlow(false)
    val um980ConfigBusy: StateFlow<Boolean> = _um980ConfigBusy.asStateFlow()

    private val _um980TrafficLog = MutableStateFlow<List<Um980LogEntry>>(emptyList())
    val um980TrafficLog: StateFlow<List<Um980LogEntry>> = _um980TrafficLog.asStateFlow()

    @Volatile
    private var lastUm980GeoLogAtMs = 0L

    fun isUm980Online(nowMs: Long = System.currentTimeMillis()): Boolean {
        val last = _lastGpsAtMs.value
        return last > 0L && nowMs - last <= EspCompanionProtocol.UM980_ONLINE_TIMEOUT_MS
    }

    fun updateConnected(value: Boolean) {
        _connected.setIfChanged(value)
        if (!value) {
            _deviceInfo.value = EspDeviceInfo()
            _lastHeartbeatAtMs.value = 0L
            _lastGpsAtMs.value = 0L
        }
    }

    fun updateDeviceInfo(info: EspDeviceInfo) {
        _deviceInfo.value = info
    }

    fun updateLocValues(values: LocValues) {
        _locValues.setIfChanged(values)
        _lastGpsAtMs.value = System.currentTimeMillis()
        touchMessage()
    }

    fun updateGpioMask(mask: Int) {
        _gpioMask.setIfChanged(mask and 0xFFFF)
        touchMessage()
    }

    fun applyGpioEvent(channel: Int, level: Boolean) {
        if (channel !in 0..15) return
        val bit = 1 shl channel
        val next = if (level) _gpioMask.value or bit else _gpioMask.value and bit.inv()
        updateGpioMask(next)
    }

    fun updateRelayMask(mask: Int) {
        _relayMask.setIfChanged(mask and 0xFF)
        touchMessage()
    }

    fun updateHeartbeat(uptimeMs: Long) {
        _lastHeartbeatAtMs.value = System.currentTimeMillis()
        touchMessage()
    }

    /** Any parsed RX from companion (hello/hb/gps/gpio/um980/ota…). */
    fun noteRxMessage() {
        touchMessage()
    }

    fun updateLastError(message: String?) {
        _lastError.value = message
    }

    fun updateUm980Response(cmd: String, lines: List<String>, ok: Boolean) {
        _lastUm980Response.value = Um980LastResponse(
            cmd = cmd,
            lines = lines,
            ok = ok,
            atMs = System.currentTimeMillis(),
        )
        touchMessage()
        if (cmd.equals("CONFIG", ignoreCase = true) ||
            cmd.equals("MODE", ignoreCase = true) ||
            cmd.equals("MASK", ignoreCase = true) ||
            cmd.equals("VERSION", ignoreCase = true) ||
            cmd.equals("VERSIONA", ignoreCase = true) ||
            lines.any {
                it.contains("CONFIG", ignoreCase = true) ||
                    it.contains("MODE", ignoreCase = true) ||
                    it.contains("MASK", ignoreCase = true) ||
                    it.contains("VERSION", ignoreCase = true)
            }
        ) {
            val merged = (_um980ConfigSnapshot.value.rawLines + lines).distinct()
            _um980ConfigSnapshot.value = Um980Commands.parseConfigSnapshot(merged)
        }
    }

    fun replaceUm980ConfigSnapshot(snapshot: Um980ConfigSnapshot) {
        _um980ConfigSnapshot.value = snapshot
    }

    fun beginOta() {
        _otaBusy.value = true
        _otaProgress.value = 0
        _otaError.value = null
    }

    fun updateOtaProgress(percent: Int) {
        _otaProgress.value = percent.coerceIn(0, 100)
    }

    fun finishOta(error: String?) {
        _otaBusy.value = false
        if (error != null) {
            _otaError.value = error
            _lastError.value = error
        } else {
            _otaProgress.value = 100
            _otaError.value = null
        }
    }

    fun beginUm980ConfigBusy() {
        _um980ConfigBusy.value = true
    }

    fun finishUm980ConfigBusy() {
        _um980ConfigBusy.value = false
    }

    /**
     * Append a UM980 traffic log line (TX command or RX reply / GPS).
     * [isGeo] entries are accepted at most once per [UM980_GEO_LOG_MIN_INTERVAL_MS].
     */
    fun appendUm980TrafficLog(
        direction: Um980LogDirection,
        text: String,
        isGeo: Boolean = false,
        atMs: Long = System.currentTimeMillis(),
    ) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        if (isGeo) {
            if (atMs - lastUm980GeoLogAtMs < UM980_GEO_LOG_MIN_INTERVAL_MS) return
            lastUm980GeoLogAtMs = atMs
        }
        val entry = Um980LogEntry(
            atMs = atMs,
            direction = direction,
            text = trimmed.take(500),
        )
        val cur = _um980TrafficLog.value
        _um980TrafficLog.value = if (cur.size < UM980_LOG_MAX) {
            cur + entry
        } else {
            cur.drop(cur.size + 1 - UM980_LOG_MAX) + entry
        }
    }

    fun clearUm980TrafficLog() {
        _um980TrafficLog.value = emptyList()
        lastUm980GeoLogAtMs = 0L
    }

    fun clearLocValues() {
        _locValues.value = LocValues()
        _lastGpsAtMs.value = 0L
    }

    fun gpioLevel(channel: Int): Boolean {
        if (channel !in 0..15) return false
        return (_gpioMask.value and (1 shl channel)) != 0
    }

    fun relayLevel(channel: Int): Boolean {
        if (channel !in 0..7) return false
        return (_relayMask.value and (1 shl channel)) != 0
    }

    private fun touchMessage() {
        _lastMessageAtMs.value = System.currentTimeMillis()
    }

    private fun <T> MutableStateFlow<T>.setIfChanged(newValue: T) {
        if (value != newValue) {
            value = newValue
        }
    }
}
