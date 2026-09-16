package vad.dashing.tbox.obd

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/**
 * Live ELM327 / OBD-II state published by [Elm327Manager].
 */
object ObdRepository {
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _statusText = MutableStateFlow("")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _adapterVoltage = MutableStateFlow<Double?>(null)
    val adapterVoltage: StateFlow<Double?> = _adapterVoltage.asStateFlow()

    private val _adapterVersion = MutableStateFlow<String?>(null)
    val adapterVersion: StateFlow<String?> = _adapterVersion.asStateFlow()

    private val _protocolDescription = MutableStateFlow<String?>(null)
    val protocolDescription: StateFlow<String?> = _protocolDescription.asStateFlow()

    private val _protocolNumber = MutableStateFlow<String?>(null)
    val protocolNumber: StateFlow<String?> = _protocolNumber.asStateFlow()

    private val _lastResponseMs = MutableStateFlow<Long?>(null)
    val lastResponseMs: StateFlow<Long?> = _lastResponseMs.asStateFlow()

    private val busErrorCount = AtomicLong(0)
    private val _busErrorCount = MutableStateFlow(0L)
    val busErrors: StateFlow<Long> = _busErrorCount.asStateFlow()

    /** [ObdPid.id] → last decoded value */
    private val _values = MutableStateFlow<Map<String, Double>>(emptyMap())
    val values: StateFlow<Map<String, Double>> = _values.asStateFlow()

    private val _dtcCodes = MutableStateFlow<List<ObdDtc>>(emptyList())
    val dtcCodes: StateFlow<List<ObdDtc>> = _dtcCodes.asStateFlow()

    private val _pendingDtcCodes = MutableStateFlow<List<ObdDtc>>(emptyList())
    val pendingDtcCodes: StateFlow<List<ObdDtc>> = _pendingDtcCodes.asStateFlow()

    private val _permanentDtcCodes = MutableStateFlow<List<ObdDtc>>(emptyList())
    val permanentDtcCodes: StateFlow<List<ObdDtc>> = _permanentDtcCodes.asStateFlow()

    private val _dtcReading = MutableStateFlow(false)
    val dtcReading: StateFlow<Boolean> = _dtcReading.asStateFlow()

    private val _dtcClearing = MutableStateFlow(false)
    val dtcClearing: StateFlow<Boolean> = _dtcClearing.asStateFlow()

    private val _dtcLastReadAtMs = MutableStateFlow(0L)
    val dtcLastReadAtMs: StateFlow<Long> = _dtcLastReadAtMs.asStateFlow()

    private val _pendingDtcLastReadAtMs = MutableStateFlow(0L)
    val pendingDtcLastReadAtMs: StateFlow<Long> = _pendingDtcLastReadAtMs.asStateFlow()

    private val _permanentDtcLastReadAtMs = MutableStateFlow(0L)
    val permanentDtcLastReadAtMs: StateFlow<Long> = _permanentDtcLastReadAtMs.asStateFlow()

    private val _dtcError = MutableStateFlow<String?>(null)
    val dtcError: StateFlow<String?> = _dtcError.asStateFlow()

    private val _dtcReadEverSucceeded = MutableStateFlow(false)
    val dtcReadEverSucceeded: StateFlow<Boolean> = _dtcReadEverSucceeded.asStateFlow()

    private val _pendingDtcReadEverSucceeded = MutableStateFlow(false)
    val pendingDtcReadEverSucceeded: StateFlow<Boolean> = _pendingDtcReadEverSucceeded.asStateFlow()

    private val _permanentDtcReadEverSucceeded = MutableStateFlow(false)
    val permanentDtcReadEverSucceeded: StateFlow<Boolean> = _permanentDtcReadEverSucceeded.asStateFlow()

    private val _vin = MutableStateFlow<String?>(null)
    val vin: StateFlow<String?> = _vin.asStateFlow()

    private val _vinLastReadAtMs = MutableStateFlow(0L)
    val vinLastReadAtMs: StateFlow<Long> = _vinLastReadAtMs.asStateFlow()

    private val _vinReadEverSucceeded = MutableStateFlow(false)
    val vinReadEverSucceeded: StateFlow<Boolean> = _vinReadEverSucceeded.asStateFlow()

    private val _vinError = MutableStateFlow<String?>(null)
    val vinError: StateFlow<String?> = _vinError.asStateFlow()

    private val _diagPackRunning = MutableStateFlow(false)
    val diagPackRunning: StateFlow<Boolean> = _diagPackRunning.asStateFlow()

    private val _diagPackError = MutableStateFlow<String?>(null)
    val diagPackError: StateFlow<String?> = _diagPackError.asStateFlow()

    private val _diagPackLastCompletedAtMs = MutableStateFlow(0L)
    val diagPackLastCompletedAtMs: StateFlow<Long> = _diagPackLastCompletedAtMs.asStateFlow()

    private val _discoveryRunning = MutableStateFlow(false)
    val discoveryRunning: StateFlow<Boolean> = _discoveryRunning.asStateFlow()

    private val _discoveryError = MutableStateFlow<String?>(null)
    val discoveryError: StateFlow<String?> = _discoveryError.asStateFlow()

    private val _freezeFrameReading = MutableStateFlow(false)
    val freezeFrameReading: StateFlow<Boolean> = _freezeFrameReading.asStateFlow()

    private val _freezeFrameError = MutableStateFlow<String?>(null)
    val freezeFrameError: StateFlow<String?> = _freezeFrameError.asStateFlow()

    private val _freezeFrameDtc = MutableStateFlow<ObdDtc?>(null)
    val freezeFrameDtc: StateFlow<ObdDtc?> = _freezeFrameDtc.asStateFlow()

    /** [ObdPid.id] → decoded Mode 02 value */
    private val _freezeFrameValues = MutableStateFlow<Map<String, Double>>(emptyMap())
    val freezeFrameValues: StateFlow<Map<String, Double>> = _freezeFrameValues.asStateFlow()

    private val _freezeFrameLastReadAtMs = MutableStateFlow(0L)
    val freezeFrameLastReadAtMs: StateFlow<Long> = _freezeFrameLastReadAtMs.asStateFlow()

    private val _freezeFrameReadEverSucceeded = MutableStateFlow(false)
    val freezeFrameReadEverSucceeded: StateFlow<Boolean> = _freezeFrameReadEverSucceeded.asStateFlow()

    private val _monitorReading = MutableStateFlow(false)
    val monitorReading: StateFlow<Boolean> = _monitorReading.asStateFlow()

    private val _monitorError = MutableStateFlow<String?>(null)
    val monitorError: StateFlow<String?> = _monitorError.asStateFlow()

    /** Mode 01 PID `01` — status since DTCs cleared. */
    private val _monitorSinceCleared = MutableStateFlow<ObdMonitorStatus?>(null)
    val monitorSinceCleared: StateFlow<ObdMonitorStatus?> = _monitorSinceCleared.asStateFlow()

    /** Mode 01 PID `41` — this drive cycle. */
    private val _monitorThisCycle = MutableStateFlow<ObdMonitorStatus?>(null)
    val monitorThisCycle: StateFlow<ObdMonitorStatus?> = _monitorThisCycle.asStateFlow()

    private val _monitorLastReadAtMs = MutableStateFlow(0L)
    val monitorLastReadAtMs: StateFlow<Long> = _monitorLastReadAtMs.asStateFlow()

    private val _monitorReadEverSucceeded = MutableStateFlow(false)
    val monitorReadEverSucceeded: StateFlow<Boolean> = _monitorReadEverSucceeded.asStateFlow()

    fun setConnected(value: Boolean) {
        _connected.value = value
    }

    fun setStatus(text: String) {
        _statusText.value = text
    }

    fun setLastError(message: String?) {
        _lastError.value = message
    }

    fun setAdapterVoltage(volts: Double?) {
        _adapterVoltage.value = volts
    }

    fun setAdapterVersion(version: String?) {
        _adapterVersion.value = version
    }

    fun setProtocolDescription(text: String?) {
        _protocolDescription.value = text
    }

    fun setProtocolNumber(text: String?) {
        _protocolNumber.value = text
    }

    fun setLastResponseMs(ms: Long?) {
        _lastResponseMs.value = ms
    }

    fun noteBusOk(responseMs: Long) {
        _lastResponseMs.value = responseMs
    }

    fun noteBusError(responseMs: Long? = null) {
        if (responseMs != null) _lastResponseMs.value = responseMs
        _busErrorCount.value = busErrorCount.incrementAndGet()
    }

    fun resetBusStats() {
        busErrorCount.set(0)
        _busErrorCount.value = 0
        _lastResponseMs.value = null
        _protocolDescription.value = null
        _protocolNumber.value = null
    }

    fun putValue(pidId: String, value: Double) {
        _values.update { it + (pidId to value) }
    }

    fun clearValues() {
        _values.value = emptyMap()
        _adapterVoltage.value = null
        _adapterVersion.value = null
    }

    fun setDtcReading(reading: Boolean) {
        _dtcReading.value = reading
    }

    fun setDtcClearing(clearing: Boolean) {
        _dtcClearing.value = clearing
    }

    fun setDtcSuccess(codes: List<ObdDtc>, atMs: Long = System.currentTimeMillis()) {
        _dtcCodes.value = codes
        _dtcLastReadAtMs.value = atMs
        _dtcError.value = null
        _dtcReadEverSucceeded.value = true
    }

    fun setPendingDtcSuccess(codes: List<ObdDtc>, atMs: Long = System.currentTimeMillis()) {
        _pendingDtcCodes.value = codes
        _pendingDtcLastReadAtMs.value = atMs
        _dtcError.value = null
        _pendingDtcReadEverSucceeded.value = true
    }

    fun setPermanentDtcSuccess(codes: List<ObdDtc>, atMs: Long = System.currentTimeMillis()) {
        _permanentDtcCodes.value = codes
        _permanentDtcLastReadAtMs.value = atMs
        _dtcError.value = null
        _permanentDtcReadEverSucceeded.value = true
    }

    fun setDtcError(message: String) {
        _dtcError.value = message
    }

    fun setVinSuccess(vin: String?, atMs: Long = System.currentTimeMillis()) {
        _vin.value = vin
        _vinLastReadAtMs.value = atMs
        _vinError.value = null
        _vinReadEverSucceeded.value = true
    }

    fun setVinError(message: String?) {
        _vinError.value = message
    }

    fun setDiagPackRunning(running: Boolean) {
        _diagPackRunning.value = running
    }

    fun setDiagPackError(message: String?) {
        _diagPackError.value = message
    }

    fun setDiagPackCompleted(atMs: Long = System.currentTimeMillis()) {
        _diagPackLastCompletedAtMs.value = atMs
        _diagPackError.value = null
        _diagPackRunning.value = false
    }

    fun setDiscoveryRunning(running: Boolean) {
        _discoveryRunning.value = running
    }

    fun setDiscoveryError(message: String?) {
        _discoveryError.value = message
    }

    fun setFreezeFrameReading(reading: Boolean) {
        _freezeFrameReading.value = reading
    }

    fun setFreezeFrameError(message: String?) {
        _freezeFrameError.value = message
    }

    fun setFreezeFrameSuccess(
        dtc: ObdDtc?,
        values: Map<String, Double>,
        atMs: Long = System.currentTimeMillis(),
    ) {
        _freezeFrameDtc.value = dtc
        _freezeFrameValues.value = values
        _freezeFrameLastReadAtMs.value = atMs
        _freezeFrameError.value = null
        _freezeFrameReadEverSucceeded.value = true
    }

    fun clearFreezeFrame() {
        _freezeFrameDtc.value = null
        _freezeFrameValues.value = emptyMap()
        _freezeFrameLastReadAtMs.value = 0L
        _freezeFrameError.value = null
        _freezeFrameReadEverSucceeded.value = false
        _freezeFrameReading.value = false
    }

    fun setMonitorReading(reading: Boolean) {
        _monitorReading.value = reading
    }

    fun setMonitorError(message: String?) {
        _monitorError.value = message
    }

    fun setMonitorSuccess(
        sinceCleared: ObdMonitorStatus?,
        thisCycle: ObdMonitorStatus?,
        atMs: Long = System.currentTimeMillis(),
    ) {
        _monitorSinceCleared.value = sinceCleared
        _monitorThisCycle.value = thisCycle
        _monitorLastReadAtMs.value = atMs
        _monitorError.value = null
        _monitorReadEverSucceeded.value = true
    }

    fun clearMonitorStatus() {
        _monitorSinceCleared.value = null
        _monitorThisCycle.value = null
        _monitorLastReadAtMs.value = 0L
        _monitorError.value = null
        _monitorReadEverSucceeded.value = false
        _monitorReading.value = false
    }

    fun clearDtcListsAfterSuccessfulClear() {
        _dtcCodes.value = emptyList()
        _pendingDtcCodes.value = emptyList()
        // Permanent DTCs are not cleared by Mode 04 on many ECUs — leave list but mark stale.
        _dtcError.value = null
        _dtcReadEverSucceeded.value = true
        _pendingDtcReadEverSucceeded.value = true
        val now = System.currentTimeMillis()
        _dtcLastReadAtMs.value = now
        _pendingDtcLastReadAtMs.value = now
        clearFreezeFrame()
        clearMonitorStatus()
    }

    fun resetConnectionState() {
        _connected.value = false
        clearValues()
        resetBusStats()
    }

    fun resetAll() {
        resetConnectionState()
        _statusText.value = ""
        _lastError.value = null
        _dtcCodes.value = emptyList()
        _pendingDtcCodes.value = emptyList()
        _permanentDtcCodes.value = emptyList()
        _dtcReading.value = false
        _dtcClearing.value = false
        _dtcLastReadAtMs.value = 0L
        _pendingDtcLastReadAtMs.value = 0L
        _permanentDtcLastReadAtMs.value = 0L
        _dtcError.value = null
        _dtcReadEverSucceeded.value = false
        _pendingDtcReadEverSucceeded.value = false
        _permanentDtcReadEverSucceeded.value = false
        _vin.value = null
        _vinLastReadAtMs.value = 0L
        _vinReadEverSucceeded.value = false
        _vinError.value = null
        _diagPackRunning.value = false
        _diagPackError.value = null
        _diagPackLastCompletedAtMs.value = 0L
        _discoveryRunning.value = false
        _discoveryError.value = null
        clearFreezeFrame()
        clearMonitorStatus()
    }
}
