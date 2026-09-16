package vad.dashing.tbox.obd

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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

    /** [ObdPid.id] → last decoded value */
    private val _values = MutableStateFlow<Map<String, Double>>(emptyMap())
    val values: StateFlow<Map<String, Double>> = _values.asStateFlow()

    private val _dtcCodes = MutableStateFlow<List<ObdDtc>>(emptyList())
    val dtcCodes: StateFlow<List<ObdDtc>> = _dtcCodes.asStateFlow()

    private val _dtcReading = MutableStateFlow(false)
    val dtcReading: StateFlow<Boolean> = _dtcReading.asStateFlow()

    private val _dtcLastReadAtMs = MutableStateFlow(0L)
    val dtcLastReadAtMs: StateFlow<Long> = _dtcLastReadAtMs.asStateFlow()

    private val _dtcError = MutableStateFlow<String?>(null)
    val dtcError: StateFlow<String?> = _dtcError.asStateFlow()

    private val _dtcReadEverSucceeded = MutableStateFlow(false)
    val dtcReadEverSucceeded: StateFlow<Boolean> = _dtcReadEverSucceeded.asStateFlow()

    private val _discoveryRunning = MutableStateFlow(false)
    val discoveryRunning: StateFlow<Boolean> = _discoveryRunning.asStateFlow()

    private val _discoveryError = MutableStateFlow<String?>(null)
    val discoveryError: StateFlow<String?> = _discoveryError.asStateFlow()

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

    fun setDtcSuccess(codes: List<ObdDtc>, atMs: Long = System.currentTimeMillis()) {
        _dtcCodes.value = codes
        _dtcLastReadAtMs.value = atMs
        _dtcError.value = null
        _dtcReadEverSucceeded.value = true
    }

    fun setDtcError(message: String) {
        _dtcError.value = message
    }

    fun setDiscoveryRunning(running: Boolean) {
        _discoveryRunning.value = running
    }

    fun setDiscoveryError(message: String?) {
        _discoveryError.value = message
    }

    fun resetConnectionState() {
        _connected.value = false
        clearValues()
    }

    fun resetAll() {
        resetConnectionState()
        _statusText.value = ""
        _lastError.value = null
        _dtcCodes.value = emptyList()
        _dtcReading.value = false
        _dtcLastReadAtMs.value = 0L
        _dtcError.value = null
        _dtcReadEverSucceeded.value = false
        _discoveryRunning.value = false
        _discoveryError.value = null
    }
}
