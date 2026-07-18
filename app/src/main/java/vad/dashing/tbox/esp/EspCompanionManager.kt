package vad.dashing.tbox.esp

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import vad.dashing.tbox.LocValues
import vad.dashing.tbox.TboxRepository
import vad.dashing.tbox.location.LocationMockManager
import java.util.Date

/**
 * Owns USB session lifecycle, protocol dispatch, and publishing active location
 * when [LocationSource.ESP32] is selected.
 */
class EspCompanionManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val locationSource: StateFlow<LocationSource>,
    private val mockLocation: StateFlow<Boolean>,
    private val locationMockManager: LocationMockManager,
    private val isLocValuesTrueEvaluator: (LocValues) -> Boolean,
) {
    companion object {
        private const val TAG = "EspCompanionManager"
        private const val WATCHDOG_MS = 1_000L
        private const val RECONNECT_MS = 3_000L
    }

    private var session: EspUsbSerialSession? = null
    private var watchdogJob: Job? = null
    private var sourceJob: Job? = null
    private var androidLocationSource: AndroidLocationSource? = null

    fun start() {
        if (session != null) return
        session = EspUsbSerialSession(
            context = context,
            onLine = { line -> handleLine(line) },
            onConnectionChanged = { connected ->
                EspCompanionRepository.updateConnected(connected)
                if (!connected && locationSource.value == LocationSource.ESP32) {
                    // Keep last fix briefly; watchdog clears on timeout.
                }
            },
            onError = { msg ->
                Log.w(TAG, msg)
                EspCompanionRepository.updateLastError(msg)
                TboxRepository.addLog("WARN", "ESP32", msg)
            },
        ).also { it.start() }

        watchdogJob = scope.launch {
            while (isActive) {
                delay(WATCHDOG_MS)
                val last = EspCompanionRepository.lastHeartbeatAtMs.value
                val connected = EspCompanionRepository.connected.value
                if (connected && last > 0L &&
                    System.currentTimeMillis() - last > EspCompanionProtocol.HEARTBEAT_TIMEOUT_MS
                ) {
                    EspCompanionRepository.updateConnected(false)
                    EspCompanionRepository.updateLastError("ESP heartbeat timeout")
                    session?.close()
                    session = null
                    delay(RECONNECT_MS)
                    if (isActive) {
                        start()
                    }
                    return@launch
                }
            }
        }

        sourceJob = scope.launch {
            locationSource.collect { source ->
                applyLocationSource(source)
            }
        }
    }

    fun stop() {
        sourceJob?.cancel()
        sourceJob = null
        watchdogJob?.cancel()
        watchdogJob = null
        androidLocationSource?.stop()
        androidLocationSource = null
        session?.close()
        session = null
        EspCompanionRepository.updateConnected(false)
    }

    fun setRelayMask(mask: Int) {
        val normalized = mask and 0xFF
        session?.writeLine(EspCompanionProtocol.encodeRelaySet(normalized).trimEnd())
        // Optimistic local state; device will confirm with relay message.
        EspCompanionRepository.updateRelayMask(normalized)
    }

    fun toggleRelay(channel: Int) {
        if (channel !in 0..7) return
        val bit = 1 shl channel
        val next = EspCompanionRepository.relayMask.value xor bit
        setRelayMask(next)
    }

    fun requestHello() {
        session?.writeLine(EspCompanionProtocol.encodeHello().trimEnd())
    }

    fun tryReconnect() {
        session?.tryConnect()
    }

    private fun handleLine(line: String) {
        when (val msg = EspCompanionProtocol.parseLine(line)) {
            is EspMessage.Hello -> {
                EspCompanionRepository.updateDeviceInfo(
                    EspDeviceInfo(
                        firmwareVersion = msg.fw,
                        gpioInCount = msg.gpioInCount,
                        relayCount = msg.relayCount,
                        um980 = msg.um980,
                    )
                )
                EspCompanionRepository.updateHeartbeat(0L)
                EspCompanionRepository.updateLastError(null)
            }
            is EspMessage.Heartbeat -> {
                EspCompanionRepository.updateHeartbeat(msg.uptimeMs)
                EspCompanionRepository.updateConnected(true)
            }
            is EspMessage.Gps -> {
                val loc = EspCompanionProtocol.gpsToLocValues(msg, Date())
                EspCompanionRepository.updateLocValues(loc)
                if (locationSource.value == LocationSource.ESP32) {
                    publishActiveLocation(loc)
                }
            }
            is EspMessage.Gpio -> EspCompanionRepository.updateGpioMask(msg.mask)
            is EspMessage.GpioEvent -> EspCompanionRepository.applyGpioEvent(msg.channel, msg.level)
            is EspMessage.Relay -> EspCompanionRepository.updateRelayMask(msg.mask)
            null -> Unit
        }
    }

    private fun applyLocationSource(source: LocationSource) {
        when (source) {
            LocationSource.ANDROID -> {
                if (androidLocationSource == null) {
                    androidLocationSource = AndroidLocationSource(context) { loc ->
                        if (locationSource.value == LocationSource.ANDROID) {
                            publishActiveLocation(loc)
                        }
                    }.also { it.start() }
                }
                locationMockManager.stopMockLocation()
            }
            LocationSource.ESP32 -> {
                androidLocationSource?.stop()
                androidLocationSource = null
                val loc = EspCompanionRepository.locValues.value
                if (loc.updateTime != null) {
                    publishActiveLocation(loc)
                }
            }
            LocationSource.TBOX -> {
                androidLocationSource?.stop()
                androidLocationSource = null
                // TBox path continues to update via BackgroundService.ansLOCValues
            }
        }
        if (source == LocationSource.ANDROID && mockLocation.value) {
            locationMockManager.stopMockLocation()
        }
    }

    private fun publishActiveLocation(loc: LocValues) {
        TboxRepository.updateLocationUpdateTime()
        if (loc.rawValue != TboxRepository.locValues.value.rawValue ||
            loc.latitude != TboxRepository.locValues.value.latitude ||
            loc.longitude != TboxRepository.locValues.value.longitude
        ) {
            TboxRepository.updateLocValues(loc)
        }
        val truth = when {
            !loc.locateStatus || (loc.latitude == 0.0 && loc.longitude == 0.0) -> false
            locationSource.value == LocationSource.TBOX -> isLocValuesTrueEvaluator(loc)
            else -> loc.locateStatus
        }
        TboxRepository.updateIsLocValuesTrue(truth)

        val source = locationSource.value
        if (mockLocation.value && source != LocationSource.ANDROID && loc.locateStatus) {
            locationMockManager.setMockLocation(loc)
        }
    }

    /** Called from TBox LOC path when source is TBOX so mock stays in sync. */
    fun onTboxLocValues(loc: LocValues) {
        if (locationSource.value != LocationSource.TBOX) return
        if (mockLocation.value && loc.locateStatus) {
            locationMockManager.setMockLocation(loc)
        }
    }
}
