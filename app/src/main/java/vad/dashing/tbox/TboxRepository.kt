package vad.dashing.tbox

import java.text.SimpleDateFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale
import java.util.Date

data class NetState(
    val csq: Int = 99,
    val signalLevel: Int = 0,
    val netStatus: String = "", // -, 2G, 3G, 4G, нет сети
    val regStatus: String = "", // "нет сети", "домашняя сеть", "поиск сети", "регистрация отклонена", "роуминг"
    val simStatus: String = "", // "нет SIM", "SIM готова", "требуется PIN", "ошибка SIM"
    val connectionChangeTime: Date? = null
)

data class NetValues(
    val imei: String = "",
    val iccid: String = "",
    val imsi: String = "",
    val operator: String = ""
)

data class APNState(
    val apnStatus: Boolean? = null,
    val apnType: String = "",
    val apnIP: String = "",
    val apnGate: String = "",
    val apnDNS1: String = "",
    val apnDNS2: String = "",
    val changeTime: Date? = null
)

data class LocValues(
    val rawValue: String = "",
    val locateStatus: Boolean = false,
    val utcTime: UtcTime? = null,
    val longitude: Double = 0.0,
    val latitude: Double = 0.0,
    val altitude: Double = 0.0,
    val visibleSatellites: Int = 0,
    val usingSatellites: Int = 0,
    val speed: Float = 0f,
    val trueDirection: Float = 0f,
    val magneticDirection: Float = 0f,
    val updateTime: Date? = null,
)

data class UtcTime(
    val year: Int = 0,
    val month: Int = 0,
    val day: Int = 0,
    val hour: Int = 0,
    val minute: Int = 0,
    val second: Int = 0
) {
    fun formatDateTime(): String {
        return "%02d.%02d.%02d %02d:%02d:%02d".format(
            day, month, year, hour, minute, second
        )
    }

    fun toLocalDateTime(): LocalDateTime? {
        return if (isValid()) {
            LocalDateTime.of(year, month, day, hour, minute, second)
        } else {
            null
        }
    }

    fun toLocalTime(): LocalTime? {
        return if (hour in 0..23 && minute in 0..59 && second in 0..59) {
            LocalTime.of(hour, minute, second)
        } else {
            null
        }
    }

    fun isValid(): Boolean {
        return year > 0 && month in 1..12 && day in 1..31 &&
                hour in 0..23 && minute in 0..59 && second in 0..59
    }
}

data class VoltagesState(
    val voltage1: Float? = null,
    val voltage2: Float? = null,
    val voltage3: Float? = null,
    val updateTime: Date? = null,
)

data class HdmData(
    val isPower: Boolean = false,
    val isIgnition: Boolean = false,
    val isCan: Boolean = false,
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

    private val _apnStatus = MutableStateFlow(false)
    val apnStatus: StateFlow<Boolean> = _apnStatus.asStateFlow()

    private val _voltages = MutableStateFlow(VoltagesState())
    val voltages: StateFlow<VoltagesState> = _voltages.asStateFlow()

    private val _hdm = MutableStateFlow(HdmData())
    val hdm: StateFlow<HdmData> = _hdm.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _atLogs = MutableStateFlow<List<String>>(emptyList())
    val atLogs: StateFlow<List<String>> = _atLogs.asStateFlow()

    private val _tboxConnected = MutableStateFlow(false)
    val tboxConnected: StateFlow<Boolean> = _tboxConnected.asStateFlow()

    private val _preventRestartSend = MutableStateFlow(false)
    val preventRestartSend: StateFlow<Boolean> = _preventRestartSend.asStateFlow()

    private val _suspendTboxAppSend = MutableStateFlow(false)
    val suspendTboxAppSend: StateFlow<Boolean> = _suspendTboxAppSend.asStateFlow()

    private val _tboxAppStoped = MutableStateFlow(false)
    val tboxAppStoped: StateFlow<Boolean> = _tboxAppStoped.asStateFlow()

    private val _tboxAppVersionAnswer = MutableStateFlow(false)
    val tboxAppVersionAnswer: StateFlow<Boolean> = _tboxAppVersionAnswer.asStateFlow()

    //private val _locationSubscribed = MutableStateFlow(false)
    //val locationSubscribed: StateFlow<Boolean> = _locationSubscribed.asStateFlow()

    private val _tboxConnectionTime = MutableStateFlow(Date())
    val tboxConnectionTime: StateFlow<Date> = _tboxConnectionTime.asStateFlow()

    private val _serviceStartTime = MutableStateFlow(Date())
    val serviceStartTime: StateFlow<Date> = _serviceStartTime.asStateFlow()

    private val _locationUpdateTime = MutableStateFlow<Date?>(null)
    val locationUpdateTime: StateFlow<Date?> = _locationUpdateTime.asStateFlow()

    private val _modemStatus = MutableStateFlow(0)
    val modemStatus: StateFlow<Int> = _modemStatus.asStateFlow()

    private val _locValues = MutableStateFlow(LocValues())
    val locValues: StateFlow<LocValues> = _locValues.asStateFlow()

    private val _isLocValuesTrue = MutableStateFlow(false)
    val isLocValuesTrue: StateFlow<Boolean> = _isLocValuesTrue.asStateFlow()

    private val _currentTheme = MutableStateFlow(1)
    val currentTheme: StateFlow<Int> = _currentTheme.asStateFlow()

    private val _canFrameTime = MutableStateFlow<Date?>(null)
    val canFrameTime: StateFlow<Date?> = _canFrameTime.asStateFlow()

    private val _cycleSignalTime = MutableStateFlow<Date?>(null)
    val cycleSignalTime: StateFlow<Date?> = _cycleSignalTime.asStateFlow()

    private val _ipList = MutableStateFlow<List<String>>(emptyList())
    val ipList: StateFlow<List<String>> = _ipList.asStateFlow()

    private val _didDataCSV = MutableStateFlow<List<String>>(emptyList())
    val didDataCSV: StateFlow<List<String>> = _didDataCSV.asStateFlow()

    private val _floatingDashboardShownIds = MutableStateFlow<Set<String>>(emptySet())
    val floatingDashboardShownIds: StateFlow<Set<String>> = _floatingDashboardShownIds.asStateFlow()

    private const val MAX_LOGS = 100

    private val timeFormat: ThreadLocal<SimpleDateFormat> = ThreadLocal.withInitial {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    }

    fun addLog(level: String, tag: String, message: String) {
        val timestamp = timeFormat.get()?.format(Date())
        val logEntry = "[$timestamp] $level: $tag. $message"

        _logs.update { currentLogs ->
            (currentLogs + logEntry).takeLast(MAX_LOGS)
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun addATLog(message: String) {
        val timestamp = timeFormat.get()?.format(Date())
        val logEntry = "[$timestamp]: $message"

        _atLogs.update { currentLogs ->
            (currentLogs + logEntry).takeLast(MAX_LOGS)
        }
    }

    fun clearATLogs() {
        _atLogs.value = emptyList()
    }

    fun addDidDataCSV(did: String, rawValue: String, value: String) {
        val entry = "$did;$rawValue;$value"

        _didDataCSV.update { currentData ->
            (currentData + entry)
        }
    }

    fun updateCycleSignalTime() {
        _cycleSignalTime.value = Date()
    }

    fun updateCanFrameTime() {
        _canFrameTime.value = Date()
    }

    fun updateTboxConnected(value: Boolean) {
        _tboxConnected.value = value
    }

    /*fun updateLocationSubscribed(value: Boolean) {
        _locationSubscribed.value = value
    }*/

    fun updateTboxConnectionTime() {
        _tboxConnectionTime.value = Date()
    }

    fun updateServiceStartTime() {
        _serviceStartTime.value = Date()
    }

    fun updateLocationUpdateTime() {
        _locationUpdateTime.value = Date()
    }

    fun updateModemStatus(value: Int) {
        _modemStatus.value = value
    }

    fun updateCurrentTheme(value: Int) {
        _currentTheme.value = value
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

    fun updateAPNStatus(value: Boolean) {
        _apnStatus.value = value
    }

    fun updateLocValues(newValues: LocValues) {
        _locValues.value = newValues
    }

    fun updateIsLocValuesTrue(newValues: Boolean) {
        _isLocValuesTrue.value = newValues
    }

    fun updatePreventRestartSend(newValue: Boolean) {
        _preventRestartSend.value = newValue
    }

    fun updateSuspendTboxAppSend(newValue: Boolean) {
        _suspendTboxAppSend.value = newValue
    }

    fun updateTboxAppStoped(newValue: Boolean) {
        _tboxAppStoped.value = newValue
    }

    fun updateTboxAppVersionAnswer(newValue: Boolean) {
        _tboxAppVersionAnswer.value = newValue
    }

    fun updateVoltages(newValue: VoltagesState) {
        _voltages.value = newValue
    }

    fun updateHdm(newValue: HdmData) {
        _hdm.value = newValue
    }

    fun updateIPList(value: List<String>) {
        _ipList.value = value
    }

    // Метод для сброса всех данных (при переподключении)
    fun resetConnectionData() {
        _tboxConnected.value = false
        _netState.value = NetState()
        _apnState.value = APNState()
        _apn2State.value = APNState()
        _apnStatus.value = false
        _preventRestartSend.value = false
        _suspendTboxAppSend.value = false
        _tboxAppStoped.value = false
    }

    fun updateFloatingDashboardShown(panelId: String, isShown: Boolean) {
        if (panelId.isBlank()) return
        _floatingDashboardShownIds.update { current ->
            if (isShown) {
                current + panelId
            } else {
                current - panelId
            }
        }
    }
}
