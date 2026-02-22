package vad.dashing.tbox

import java.text.SimpleDateFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale
import java.util.Date


data class Wheels(
    val wheel1: Float? = null,
    val wheel2: Float? = null,
    val wheel3: Float? = null,
    val wheel4: Float? = null,
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

object CanDataRepository {
    private val _cruiseSetSpeed = MutableStateFlow<UInt?>(null)
    val cruiseSetSpeed: StateFlow<UInt?> = _cruiseSetSpeed.asStateFlow()

    private val _wheelsSpeed = MutableStateFlow(Wheels())
    val wheelsSpeed: StateFlow<Wheels> = _wheelsSpeed.asStateFlow()

    private val _wheelsPressure = MutableStateFlow(Wheels())
    val wheelsPressure: StateFlow<Wheels> = _wheelsPressure.asStateFlow()

    private val _wheelsTemperature = MutableStateFlow(Wheels())
    val wheelsTemperature: StateFlow<Wheels> = _wheelsTemperature.asStateFlow()

    private val _climateSetTemperature1 = MutableStateFlow<Float?>(null)
    val climateSetTemperature1: StateFlow<Float?> = _climateSetTemperature1.asStateFlow()

    private val _engineRPM = MutableStateFlow<Float?>(null)
    val engineRPM: StateFlow<Float?> = _engineRPM.asStateFlow()

    private val _param1 = MutableStateFlow<Float?>(null)
    val param1: StateFlow<Float?> = _param1.asStateFlow()

    private val _param2 = MutableStateFlow<Float?>(null)
    val param2: StateFlow<Float?> = _param2.asStateFlow()

    private val _param3 = MutableStateFlow<Float?>(null)
    val param3: StateFlow<Float?> = _param3.asStateFlow()

    private val _param4 = MutableStateFlow<Float?>(null)
    val param4: StateFlow<Float?> = _param4.asStateFlow()

    private val _steerAngle = MutableStateFlow<Float?>(null)
    val steerAngle: StateFlow<Float?> = _steerAngle.asStateFlow()

    private val _steerSpeed = MutableStateFlow<Int?>(null)
    val steerSpeed: StateFlow<Int?> = _steerSpeed.asStateFlow()

    private val _engineTemperature = MutableStateFlow<Float?>(null)
    val engineTemperature: StateFlow<Float?> = _engineTemperature.asStateFlow()

    private val _odometer = MutableStateFlow<UInt?>(null)
    val odometer: StateFlow<UInt?> = _odometer.asStateFlow()

    private val _distanceToNextMaintenance = MutableStateFlow<UInt?>(null)
    val distanceToNextMaintenance: StateFlow<UInt?> = _distanceToNextMaintenance.asStateFlow()

    private val _distanceToFuelEmpty = MutableStateFlow<UInt?>(null)
    val distanceToFuelEmpty: StateFlow<UInt?> = _distanceToFuelEmpty.asStateFlow()

    private val _breakingForce = MutableStateFlow<UInt?>(null)
    val breakingForce: StateFlow<UInt?> = _breakingForce.asStateFlow()

    private val _carSpeed = MutableStateFlow<Float?>(null)
    val carSpeed: StateFlow<Float?> = _carSpeed.asStateFlow()

    private val _carSpeedAccurate = MutableStateFlow<Float?>(null)
    val carSpeedAccurate: StateFlow<Float?> = _carSpeedAccurate.asStateFlow()

    private val _voltage = MutableStateFlow<Float?>(null)
    val voltage: StateFlow<Float?> = _voltage.asStateFlow()

    private val _fuelLevelPercentage = MutableStateFlow<UInt?>(null)
    val fuelLevelPercentage: StateFlow<UInt?> = _fuelLevelPercentage.asStateFlow()

    private val _fuelLevelPercentageFiltered = MutableStateFlow<UInt?>(null)
    val fuelLevelPercentageFiltered: StateFlow<UInt?> = _fuelLevelPercentageFiltered.asStateFlow()

    private val _gearBoxMode = MutableStateFlow("")
    val gearBoxMode: StateFlow<String> = _gearBoxMode.asStateFlow()

    private val _gearBoxCurrentGear = MutableStateFlow<Int?>(null)
    val gearBoxCurrentGear: StateFlow<Int?> = _gearBoxCurrentGear.asStateFlow()

    private val _gearBoxPreparedGear = MutableStateFlow<Int?>(null)
    val gearBoxPreparedGear: StateFlow<Int?> = _gearBoxPreparedGear.asStateFlow()

    private val _gearBoxChangeGear = MutableStateFlow<Boolean?>(null)
    val gearBoxChangeGear: StateFlow<Boolean?> = _gearBoxChangeGear.asStateFlow()

    private val _gearBoxOilTemperature = MutableStateFlow<Int?>(null)
    val gearBoxOilTemperature: StateFlow<Int?> = _gearBoxOilTemperature.asStateFlow()

    private val _gearBoxDriveMode = MutableStateFlow<String>("")
    val gearBoxDriveMode: StateFlow<String> = _gearBoxDriveMode.asStateFlow()

    private val _gearBoxWork = MutableStateFlow<String>("")
    val gearBoxWork: StateFlow<String> = _gearBoxWork.asStateFlow()

    private val _frontLeftSeatMode = MutableStateFlow<UInt?>(null)
    val frontLeftSeatMode: StateFlow<UInt?> = _frontLeftSeatMode.asStateFlow()

    private val _frontRightSeatMode = MutableStateFlow<UInt?>(null)
    val frontRightSeatMode: StateFlow<UInt?> = _frontRightSeatMode.asStateFlow()

    private val _outsideTemperature = MutableStateFlow<Float?>(null)
    val outsideTemperature: StateFlow<Float?> = _outsideTemperature.asStateFlow()

    private val _insideTemperature = MutableStateFlow<Float?>(null)
    val insideTemperature: StateFlow<Float?> = _insideTemperature.asStateFlow()

    private val _outsideAirQuality = MutableStateFlow<UInt?>(null)
    val outsideAirQuality: StateFlow<UInt?> = _outsideAirQuality.asStateFlow()

    private val _insideAirQuality = MutableStateFlow<UInt?>(null)
    val insideAirQuality: StateFlow<UInt?> = _insideAirQuality.asStateFlow()

    private val _isWindowsBlocked = MutableStateFlow<Boolean?>(null)
    val isWindowsBlocked: StateFlow<Boolean?> = _isWindowsBlocked.asStateFlow()

    private val _canFramesStructured = MutableStateFlow<Map<String, List<CanFrame>>>(emptyMap())
    val canFramesStructured: StateFlow<Map<String, List<CanFrame>>> = _canFramesStructured.asStateFlow()

    private val _motorHoursTrip = MutableStateFlow<Float?>(null)
    val motorHoursTrip: StateFlow<Float?> = _motorHoursTrip.asStateFlow()

    private const val MAX_CAN_FRAMES = 5
    private const val MAX_CAN_IDS = 500
    private const val MAX_FRAMES_PER_ID = 10

    private val timeFormat: ThreadLocal<SimpleDateFormat> = ThreadLocal.withInitial {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    }

    private fun <T> MutableStateFlow<T>.setIfChanged(newValue: T) {
        if (value != newValue) {
            value = newValue
        }
    }

    /*fun addCanFrame(rawValue: String) {
        val timestamp = timeFormat.format(Date())
        val entry = "$timestamp;$rawValue"

        _canFramesList.update { currentData ->
            (currentData + entry).takeLast(MAX_CAN_FRAMES)
        }
        _canFrameTime.value = Date()
    }*/

    fun updateVoltage(newValue: Float) {
        _voltage.setIfChanged(newValue)
    }

    fun updateCarSpeed(newValue: Float) {
        _carSpeed.setIfChanged(newValue)
    }

    fun updateCarSpeedAccurate(newValue: Float) {
        _carSpeedAccurate.setIfChanged(newValue)
    }

    fun updateOdometer(newValue: UInt) {
        _odometer.setIfChanged(newValue)
    }

    fun updateDistanceToNextMaintenance(newValue: UInt) {
        _distanceToNextMaintenance.setIfChanged(newValue)
    }

    fun updateDistanceToFuelEmpty(newValue: UInt) {
        _distanceToFuelEmpty.setIfChanged(newValue)
    }

    fun updateBreakingForce(newValue: UInt) {
        _breakingForce.setIfChanged(newValue)
    }

    fun updateFuelLevelPercentage(newValue: UInt) {
        _fuelLevelPercentage.setIfChanged(newValue)
    }

    fun updateFuelLevelPercentageFiltered(newValue: UInt) {
        _fuelLevelPercentageFiltered.setIfChanged(newValue)
    }

    fun updateCruiseSetSpeed(newValue: UInt) {
        _cruiseSetSpeed.setIfChanged(newValue)
    }

    fun updateWheelsSpeed(newValue: Wheels) {
        _wheelsSpeed.setIfChanged(newValue)
    }

    fun updateWheelsPressure(newValue: Wheels) {
        _wheelsPressure.setIfChanged(newValue)
    }

    fun updateWheelsTemperature(newValue: Wheels) {
        _wheelsTemperature.setIfChanged(newValue)
    }

    fun updateEngineRPM(newValue: Float) {
        _engineRPM.setIfChanged(newValue)
    }

    fun updateParam1(newValue: Float) {
        _param1.setIfChanged(newValue)
    }

    fun updateParam2(newValue: Float) {
        _param2.setIfChanged(newValue)
    }

    fun updateParam3(newValue: Float) {
        _param3.setIfChanged(newValue)
    }

    fun updateParam4(newValue: Float) {
        _param4.setIfChanged(newValue)
    }

    fun updateSteerAngle(newValue: Float?) {
        _steerAngle.setIfChanged(newValue)
    }

    fun updateSteerSpeed(newValue: Int) {
        _steerSpeed.setIfChanged(newValue)
    }

    fun updateClimateSetTemperature1(newValue: Float) {
        _climateSetTemperature1.setIfChanged(newValue)
    }

    fun updateEngineTemperature(newValue: Float) {
        _engineTemperature.setIfChanged(newValue)
    }

    fun updateGearBoxMode(newValue: String) {
        _gearBoxMode.setIfChanged(newValue)
    }

    fun updateGearBoxCurrentGear(newValue: Int) {
        _gearBoxCurrentGear.setIfChanged(newValue)
    }

    fun updateGearBoxChangeGear(newValue: Boolean) {
        _gearBoxChangeGear.setIfChanged(newValue)
    }

    fun updateGearBoxPreparedGear(newValue: Int) {
        _gearBoxPreparedGear.setIfChanged(newValue)
    }

    fun updateGearBoxOilTemperature(newValue: Int) {
        _gearBoxOilTemperature.setIfChanged(newValue)
    }

    fun updateGearBoxDriveMode(newValue: String) {
        _gearBoxDriveMode.setIfChanged(newValue)
    }

    fun updateGearBoxWork(newValue: String) {
        _gearBoxWork.setIfChanged(newValue)
    }

    fun updateFrontLeftSeatMode(newValue: UInt) {
        _frontLeftSeatMode.setIfChanged(newValue)
    }

    fun updateFrontRightSeatMode(newValue: UInt) {
        _frontRightSeatMode.setIfChanged(newValue)
    }

    fun updateOutsideTemperature(newValue: Float?) {
        _outsideTemperature.setIfChanged(newValue)
    }

    fun updateInsideTemperature(newValue: Float?) {
        _insideTemperature.setIfChanged(newValue)
    }

    fun updateOutsideAirQuality(newValue: UInt?) {
        _outsideAirQuality.setIfChanged(newValue)
    }

    fun updateInsideAirQuality(newValue: UInt?) {
        _insideAirQuality.setIfChanged(newValue)
    }

    fun updateIsWindowsBlocked(newValue: Boolean) {
        _isWindowsBlocked.setIfChanged(newValue)
    }

    fun addMotorHoursTrip(newValue: Float) {
        _motorHoursTrip.value = (_motorHoursTrip.value ?: 0f) + newValue
    }

    fun addCanFrameStructured(canId: String, rawValue: ByteArray, maxFrames: Int = MAX_FRAMES_PER_ID) {
        val date = Date()
        val frame = CanFrame(date, rawValue)

        _canFramesStructured.update { currentMap ->
            // Use insertion-ordered map so we can evict the oldest CAN IDs.
            val limitedMap = LinkedHashMap(currentMap)
            val currentFrames = limitedMap.remove(canId) ?: emptyList()
            val updatedFrames = (currentFrames + frame).takeLast(maxFrames)
            limitedMap[canId] = updatedFrames

            while (limitedMap.size > MAX_CAN_IDS) {
                val oldestCanId = limitedMap.keys.firstOrNull() ?: break
                limitedMap.remove(oldestCanId)
            }
            limitedMap
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

    // Метод для сброса всех данных (при переподключении)
    fun resetConnectionData() {
    }
}
