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
)

/**
 * Live state of the ESP32 USB companion.
 * GPS from the device is also mirrored into [vad.dashing.tbox.TboxRepository] when
 * [LocationSource.ESP32] is selected (see [EspCompanionManager]).
 */
object EspCompanionRepository {
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

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _lastMessageAtMs = MutableStateFlow(0L)
    val lastMessageAtMs: StateFlow<Long> = _lastMessageAtMs.asStateFlow()

    fun updateConnected(value: Boolean) {
        _connected.setIfChanged(value)
        if (!value) {
            _deviceInfo.value = EspDeviceInfo()
            _lastHeartbeatAtMs.value = 0L
        }
    }

    fun updateDeviceInfo(info: EspDeviceInfo) {
        _deviceInfo.value = info
    }

    fun updateLocValues(values: LocValues) {
        _locValues.setIfChanged(values)
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

    fun updateLastError(message: String?) {
        _lastError.value = message
    }

    fun clearLocValues() {
        _locValues.value = LocValues()
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
