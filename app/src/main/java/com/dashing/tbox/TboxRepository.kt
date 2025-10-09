package com.dashing.tbox

import android.icu.text.SimpleDateFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale
import java.util.Date

data class NetState(
    val csq: Int = 99,
    val netStatus: String = "",
    val regStatus: String = "",
    val simStatus: String = ""
)

data class NetValues(
    val imei: String = "",
    val iccid: String = "",
    val imsi: String = "",
    val operator: String = ""
)

data class APNState(
    val apnStatus: String = "",
    val apnType: String = "",
    val apnIP: String = "",
    val apnGate: String = "",
    val apnDNS1: String = "",
    val apnDNS2: String = ""
)

data class LocValues(
    val rawValue: String = "",
    val locateStatus: Boolean = false,
    val utcTime: UtcTime = UtcTime(),
    val longitude: Double = 0.0,
    val latitude: Double = 0.0,
    val altitude: Double = 0.0,
    val visibleSatellites: Int = 0,
    val usingSatellites: Int = 0,
    val speed: Double = 0.0,
    val trueDirection: Double = 0.0,
    val magneticDirection: Double = 0.0
)

data class UtcTime(
    val year: Int = 0,
    val month: Int = 0,
    val day: Int = 0,
    val hour: Int = 0,
    val minute: Int = 0,
    val second: Int = 0
)

object TboxRepository {
    private val _netState = MutableStateFlow(NetState())
    val netState: StateFlow<NetState> = _netState.asStateFlow()

    private val _netValues = MutableStateFlow(NetValues())
    val netValues: StateFlow<NetValues> = _netValues.asStateFlow()

    private val _apnState = MutableStateFlow(APNState())
    val apnState: StateFlow<APNState> = _apnState.asStateFlow()

    private val _apn2State = MutableStateFlow(APNState())
    val apn2State: StateFlow<APNState> = _apn2State.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _tboxConnected = MutableStateFlow(false)
    val tboxConnected: StateFlow<Boolean> = _tboxConnected.asStateFlow()

    private val _locationSubscribed = MutableStateFlow(false)
    val locationSubscribed: StateFlow<Boolean> = _locationSubscribed.asStateFlow()

    private val _tboxConnectionTime = MutableStateFlow(Date())
    val tboxConnectionTime: StateFlow<Date> = _tboxConnectionTime.asStateFlow()

    private val _locUpdateTime = MutableStateFlow(Date())
    val locUpdateTime: StateFlow<Date> = _locUpdateTime.asStateFlow()

    private val _modemStatus = MutableStateFlow(0)
    val modemStatus: StateFlow<Int> = _modemStatus.asStateFlow()

    private val _locValues = MutableStateFlow(LocValues())
    val locValues: StateFlow<LocValues> = _locValues.asStateFlow()

    private const val MAX_LOGS = 100

    fun addLog(level: String, tag: String, message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val logEntry = "[$timestamp] $level: $tag. $message"

        _logs.update { currentLogs ->
            (currentLogs + logEntry).takeLast(MAX_LOGS)
        }
    }

    fun updateTboxConnected(value: Boolean) {
        _tboxConnected.value = value
    }

    fun updateLocationSubscribed(value: Boolean) {
        _locationSubscribed.value = value
    }

    fun updateTboxConnectionTime() {
        _tboxConnectionTime.value = Date()
    }

    fun updateLocUpdateTime() {
        _locUpdateTime.value = Date()
    }

    fun updateModemStatus(value: Int) {
        _modemStatus.value = value
    }

    fun updateNetState(newState: NetState) {
        _netState.value = newState
    }

    fun updateNetValues(newValues: NetValues) {
        _netValues.value = newValues
    }

    fun updateAPNState(newState: APNState) {
        _apnState.value = newState
    }

    fun updateAPN2State(newState: APNState) {
        _apn2State.value = newState
    }

    fun updateLocValues(newValues: LocValues) {
        _locValues.value = newValues
    }
}
