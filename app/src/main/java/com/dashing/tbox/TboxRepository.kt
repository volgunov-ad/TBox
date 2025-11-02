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
    val signalLevel: Int = 0,
    val netStatus: String = "", // -, 2G, 3G, 4G, нет сети
    val regStatus: String = "", // "нет сети", "домашняя сеть", "поиск сети", "регистрация отклонена", "роуминг"
    val simStatus: String = "" // "нет SIM", "SIM готова", "требуется PIN", "ошибка SIM"
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
    val speed: Float = 0f,
    val trueDirection: Float = 0f,
    val magneticDirection: Float = 0f,
    val updateTime: Date = Date(),
)

data class UtcTime(
    val year: Int = 0,
    val month: Int = 0,
    val day: Int = 0,
    val hour: Int = 0,
    val minute: Int = 0,
    val second: Int = 0
)

data class VoltagesState(
    val voltage1: Float = 0f,
    val voltage2: Float = 0f,
    val voltage3: Float = 0f,
    val voltage4: Float = 0f,
    val updateTime: Date = Date(),
)

data class HdmData(
    val isPower: Boolean = false,
    val isIgnition: Boolean = false,
    val isCan: Boolean = false,
)

data class OdoData(
    val speed: Float = 0f,
    val odometer: Int = 0,
)

data class EngineSpeedData(
    val rpm: Float = 0f,
    val speed: Float = 0f,
)

data class CarSpeedData(
    val speed: Float = 0f,
)

data class Cruise(
    val speed: Int = 0,
)

data class Wheels(
    val speed1: Float = 0f,
    val speed2: Float = 0f,
    val speed3: Float = 0f,
    val speed4: Float = 0f,
)

data class SteerData(
    val angle: Float = 0f,
    val speed: Int = 0,
)

data class CanFrame(
    val date: Date,
    val rawValue: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CanFrame

        if (date != other.date) return false
        if (!rawValue.contentEquals(other.rawValue)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = date.hashCode()
        result = 31 * result + rawValue.contentHashCode()
        return result
    }
}

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

    private val _odo = MutableStateFlow(OdoData())
    val odo: StateFlow<OdoData> = _odo.asStateFlow()

    private val _engineSpeed = MutableStateFlow(EngineSpeedData())
    val engineSpeed: StateFlow<EngineSpeedData> = _engineSpeed.asStateFlow()

    private val _carSpeed = MutableStateFlow(CarSpeedData())
    val carSpeed: StateFlow<CarSpeedData> = _carSpeed.asStateFlow()

    private val _cruise = MutableStateFlow(Cruise())
    val cruise: StateFlow<Cruise> = _cruise.asStateFlow()

    private val _wheels = MutableStateFlow(Wheels())
    val wheels: StateFlow<Wheels> = _wheels.asStateFlow()

    private val _steer = MutableStateFlow(SteerData())
    val steer: StateFlow<SteerData> = _steer.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _tboxConnected = MutableStateFlow(false)
    val tboxConnected: StateFlow<Boolean> = _tboxConnected.asStateFlow()

    private val _preventRestartSend = MutableStateFlow(false)
    val preventRestartSend: StateFlow<Boolean> = _preventRestartSend.asStateFlow()

    private val _suspendTboxAppSend = MutableStateFlow(false)
    val suspendTboxAppSend: StateFlow<Boolean> = _suspendTboxAppSend.asStateFlow()

    //private val _locationSubscribed = MutableStateFlow(false)
    //val locationSubscribed: StateFlow<Boolean> = _locationSubscribed.asStateFlow()

    private val _tboxConnectionTime = MutableStateFlow(Date())
    val tboxConnectionTime: StateFlow<Date> = _tboxConnectionTime.asStateFlow()

    private val _serviceStartTime = MutableStateFlow(Date())
    val serviceStartTime: StateFlow<Date> = _serviceStartTime.asStateFlow()

    private val _modemStatus = MutableStateFlow(0)
    val modemStatus: StateFlow<Int> = _modemStatus.asStateFlow()

    private val _locValues = MutableStateFlow(LocValues())
    val locValues: StateFlow<LocValues> = _locValues.asStateFlow()

    private val _currentTheme = MutableStateFlow(1)
    val currentTheme: StateFlow<Int> = _currentTheme.asStateFlow()

    private val _canFrameTime = MutableStateFlow(Date())
    val canFrameTime: StateFlow<Date> = _canFrameTime.asStateFlow()

    private val _cycleSignalTime = MutableStateFlow(Date())
    val cycleSignalTime: StateFlow<Date> = _cycleSignalTime.asStateFlow()

    private val _ipList = MutableStateFlow<List<String>>(emptyList())
    val ipList: StateFlow<List<String>> = _ipList.asStateFlow()

    private val _didDataCSV = MutableStateFlow<List<String>>(emptyList())
    val didDataCSV: StateFlow<List<String>> = _didDataCSV.asStateFlow()

    //private val _canFramesList = MutableStateFlow<List<String>>(emptyList())
    //val canFramesList: StateFlow<List<String>> = _canFramesList.asStateFlow()

    private val _canFramesStructured = MutableStateFlow<Map<String, List<CanFrame>>>(emptyMap())
    val canFramesStructured: StateFlow<Map<String, List<CanFrame>>> = _canFramesStructured.asStateFlow()

    private const val MAX_LOGS = 100
    private const val MAX_CAN_FRAMES = 5
    private const val MAX_FRAMES_PER_ID = 5

    fun addLog(level: String, tag: String, message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val logEntry = "[$timestamp] $level: $tag. $message"

        _logs.update { currentLogs ->
            (currentLogs + logEntry).takeLast(MAX_LOGS)
        }
    }

    fun addDidDataCSV(did: String, rawValue: String, value: String) {
        val entry = "$did;$rawValue;$value"

        _didDataCSV.update { currentData ->
            (currentData + entry)
        }
    }

    /*fun addCanFrame(rawValue: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val entry = "$timestamp;$rawValue"

        _canFramesList.update { currentData ->
            (currentData + entry).takeLast(MAX_CAN_FRAMES)
        }
        _canFrameTime.value = Date()
    }*/

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

    fun updatePreventRestartSend(newValue: Boolean) {
        _preventRestartSend.value = newValue
    }

    fun updateSuspendTboxAppSend(newValue: Boolean) {
        _suspendTboxAppSend.value = newValue
    }

    fun updateVoltages(newValue: VoltagesState) {
        _voltages.value = newValue
    }

    fun updateHdm(newValue: HdmData) {
        _hdm.value = newValue
    }

    fun updateOdo(newValue: OdoData) {
        _odo.value = newValue
    }

    fun updateEngineSpeed(newValue: EngineSpeedData) {
        _engineSpeed.value = newValue
    }

    fun updateCarSpeed(newValue: CarSpeedData) {
        _carSpeed.value = newValue
    }

    fun updateCruise(newValue: Cruise) {
        _cruise.value = newValue
    }

    fun updateWheels(newValue: Wheels) {
        _wheels.value = newValue
    }

    fun updateSteer(newValue: SteerData) {
        _steer.value = newValue
    }

    fun updateIPList(value: List<String>) {
        _ipList.value = value
    }

    fun addCanFrameStructured(canId: String, rawValue: ByteArray) {
        val date = Date()
        val frame = CanFrame(date, rawValue)

        _canFramesStructured.update { currentMap ->
            val currentFrames = currentMap[canId] ?: emptyList()
            val updatedFrames = (currentFrames + frame).takeLast(MAX_FRAMES_PER_ID)

            currentMap + (canId to updatedFrames)
        }
    }

    fun getFramesForId(canId: String): List<CanFrame> {
        return _canFramesStructured.value[canId] ?: emptyList()
    }

    // Получить последний фрейм для конкретного CAN ID
    fun getLastFrameForId(canId: String): CanFrame? {
        return _canFramesStructured.value[canId]?.lastOrNull()
    }

    // Получить все CAN ID
    fun getAllCanIds(): Set<String> {
        return _canFramesStructured.value.keys
    }

    // Очистить все фреймы для конкретного CAN ID
    fun clearFramesForId(canId: String) {
        _canFramesStructured.update { currentMap ->
            currentMap - canId
        }
    }

    // Очистить все фреймы
    fun clearAllFrames() {
        _canFramesStructured.value = emptyMap()
    }
}
