package vad.dashing.tbox.client.data

import android.os.Bundle
import java.util.Date
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    private val _updateTicker = MutableStateFlow(0L)
    val updateTicker: StateFlow<Long> = _updateTicker.asStateFlow()

    fun updateFromBundle(bundle: Bundle) {
        _netState.value = bundle.getBundle("netState")?.toNetState() ?: NetState()
        _netValues.value = bundle.getBundle("netValues")?.toNetValues() ?: NetValues()
        _apnState.value = bundle.getBundle("apnState")?.toApnState() ?: APNState()
        _apn2State.value = bundle.getBundle("apn2State")?.toApnState() ?: APNState()
        _apnStatus.value = bundle.getBoolean("apnStatus", false)
        _voltages.value = bundle.getBundle("voltages")?.toVoltagesState() ?: VoltagesState()
        _hdm.value = bundle.getBundle("hdm")?.toHdmData() ?: HdmData()
        _logs.value = bundle.getStringArrayList("logs") ?: emptyList()
        _atLogs.value = bundle.getStringArrayList("atLogs") ?: emptyList()
        _tboxConnected.value = bundle.getBoolean("tboxConnected", false)
        _preventRestartSend.value = bundle.getBoolean("preventRestartSend", false)
        _suspendTboxAppSend.value = bundle.getBoolean("suspendTboxAppSend", false)
        _tboxAppStoped.value = bundle.getBoolean("tboxAppStoped", false)
        _tboxAppVersionAnswer.value = bundle.getBoolean("tboxAppVersionAnswer", false)
        _tboxConnectionTime.value = bundle.getDateOrNull("tboxConnectionTime")
            ?: _tboxConnectionTime.value
        _serviceStartTime.value = bundle.getDateOrNull("serviceStartTime")
            ?: _serviceStartTime.value
        _locationUpdateTime.value = bundle.getDateOrNull("locationUpdateTime")
        _modemStatus.value = bundle.getInt("modemStatus", 0)
        _locValues.value = bundle.getBundle("locValues")?.toLocValues() ?: LocValues()
        _isLocValuesTrue.value = bundle.getBoolean("isLocValuesTrue", false)
        _currentTheme.value = bundle.getInt("currentTheme", 1)
        _canFrameTime.value = bundle.getDateOrNull("canFrameTime")
        _cycleSignalTime.value = bundle.getDateOrNull("cycleSignalTime")
        _ipList.value = bundle.getStringArrayList("ipList") ?: emptyList()
        _didDataCSV.value = bundle.getStringArrayList("didDataCSV") ?: emptyList()
        _floatingDashboardShownIds.value = (
            bundle.getStringArrayList("floatingDashboardShownIds") ?: emptyList()
            ).toSet()
        touch()
    }

    private fun touch() {
        _updateTicker.value = _updateTicker.value + 1
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

    private val _gearBoxDriveMode = MutableStateFlow("")
    val gearBoxDriveMode: StateFlow<String> = _gearBoxDriveMode.asStateFlow()

    private val _gearBoxWork = MutableStateFlow("")
    val gearBoxWork: StateFlow<String> = _gearBoxWork.asStateFlow()

    private val _frontLeftSeatMode = MutableStateFlow<UInt?>(null)
    val frontLeftSeatMode: StateFlow<UInt?> = _frontLeftSeatMode.asStateFlow()

    private val _frontRightSeatMode = MutableStateFlow<UInt?>(null)
    val frontRightSeatMode: StateFlow<UInt?> = _frontRightSeatMode.asStateFlow()

    private val _outsideTemperature = MutableStateFlow<Float?>(null)
    val outsideTemperature: StateFlow<Float?> = _outsideTemperature.asStateFlow()

    private val _insideTemperature = MutableStateFlow<Float?>(null)
    val insideTemperature: StateFlow<Float?> = _insideTemperature.asStateFlow()

    private val _isWindowsBlocked = MutableStateFlow<Boolean?>(null)
    val isWindowsBlocked: StateFlow<Boolean?> = _isWindowsBlocked.asStateFlow()

    private val _canIds = MutableStateFlow<List<String>>(emptyList())
    val canIds: StateFlow<List<String>> = _canIds.asStateFlow()

    private val _updateTicker = MutableStateFlow(0L)
    val updateTicker: StateFlow<Long> = _updateTicker.asStateFlow()

    fun updateFromBundle(bundle: Bundle) {
        _cruiseSetSpeed.value = bundle.getUIntOrNull("cruiseSetSpeed")
        _wheelsSpeed.value = bundle.getBundle("wheelsSpeed")?.toWheels() ?: Wheels()
        _wheelsPressure.value = bundle.getBundle("wheelsPressure")?.toWheels() ?: Wheels()
        _wheelsTemperature.value = bundle.getBundle("wheelsTemperature")?.toWheels() ?: Wheels()
        _climateSetTemperature1.value = bundle.getFloatOrNull("climateSetTemperature1")
        _engineRPM.value = bundle.getFloatOrNull("engineRPM")
        _param1.value = bundle.getFloatOrNull("param1")
        _param2.value = bundle.getFloatOrNull("param2")
        _param3.value = bundle.getFloatOrNull("param3")
        _param4.value = bundle.getFloatOrNull("param4")
        _steerAngle.value = bundle.getFloatOrNull("steerAngle")
        _steerSpeed.value = bundle.getIntOrNull("steerSpeed")
        _engineTemperature.value = bundle.getFloatOrNull("engineTemperature")
        _odometer.value = bundle.getUIntOrNull("odometer")
        _distanceToNextMaintenance.value = bundle.getUIntOrNull("distanceToNextMaintenance")
        _distanceToFuelEmpty.value = bundle.getUIntOrNull("distanceToFuelEmpty")
        _breakingForce.value = bundle.getUIntOrNull("breakingForce")
        _carSpeed.value = bundle.getFloatOrNull("carSpeed")
        _carSpeedAccurate.value = bundle.getFloatOrNull("carSpeedAccurate")
        _voltage.value = bundle.getFloatOrNull("voltage")
        _fuelLevelPercentage.value = bundle.getUIntOrNull("fuelLevelPercentage")
        _fuelLevelPercentageFiltered.value = bundle.getUIntOrNull("fuelLevelPercentageFiltered")
        _gearBoxMode.value = bundle.getString("gearBoxMode").orEmpty()
        _gearBoxCurrentGear.value = bundle.getIntOrNull("gearBoxCurrentGear")
        _gearBoxPreparedGear.value = bundle.getIntOrNull("gearBoxPreparedGear")
        _gearBoxChangeGear.value = bundle.getBooleanOrNull("gearBoxChangeGear")
        _gearBoxOilTemperature.value = bundle.getIntOrNull("gearBoxOilTemperature")
        _gearBoxDriveMode.value = bundle.getString("gearBoxDriveMode").orEmpty()
        _gearBoxWork.value = bundle.getString("gearBoxWork").orEmpty()
        _frontLeftSeatMode.value = bundle.getUIntOrNull("frontLeftSeatMode")
        _frontRightSeatMode.value = bundle.getUIntOrNull("frontRightSeatMode")
        _outsideTemperature.value = bundle.getFloatOrNull("outsideTemperature")
        _insideTemperature.value = bundle.getFloatOrNull("insideTemperature")
        _isWindowsBlocked.value = bundle.getBooleanOrNull("isWindowsBlocked")
        _canIds.value = bundle.getStringArrayList("canIds") ?: emptyList()
        touch()
    }

    private fun touch() {
        _updateTicker.value = _updateTicker.value + 1
    }
}

object CycleDataRepository {
    private val _odometer = MutableStateFlow<UInt?>(null)
    val odometer: StateFlow<UInt?> = _odometer.asStateFlow()

    private val _carSpeed = MutableStateFlow<Float?>(null)
    val carSpeed: StateFlow<Float?> = _carSpeed.asStateFlow()

    private val _voltage = MutableStateFlow<Float?>(null)
    val voltage: StateFlow<Float?> = _voltage.asStateFlow()

    private val _longitudinalAcceleration = MutableStateFlow<Float?>(null)
    val longitudinalAcceleration: StateFlow<Float?> = _longitudinalAcceleration.asStateFlow()

    private val _lateralAcceleration = MutableStateFlow<Float?>(null)
    val lateralAcceleration: StateFlow<Float?> = _lateralAcceleration.asStateFlow()

    private val _yawRate = MutableStateFlow<Float?>(null)
    val yawRate: StateFlow<Float?> = _yawRate.asStateFlow()

    private val _pressure1 = MutableStateFlow<Float?>(null)
    val pressure1: StateFlow<Float?> = _pressure1.asStateFlow()

    private val _pressure2 = MutableStateFlow<Float?>(null)
    val pressure2: StateFlow<Float?> = _pressure2.asStateFlow()

    private val _pressure3 = MutableStateFlow<Float?>(null)
    val pressure3: StateFlow<Float?> = _pressure3.asStateFlow()

    private val _pressure4 = MutableStateFlow<Float?>(null)
    val pressure4: StateFlow<Float?> = _pressure4.asStateFlow()

    private val _temperature1 = MutableStateFlow<Float?>(null)
    val temperature1: StateFlow<Float?> = _temperature1.asStateFlow()

    private val _temperature2 = MutableStateFlow<Float?>(null)
    val temperature2: StateFlow<Float?> = _temperature2.asStateFlow()

    private val _temperature3 = MutableStateFlow<Float?>(null)
    val temperature3: StateFlow<Float?> = _temperature3.asStateFlow()

    private val _temperature4 = MutableStateFlow<Float?>(null)
    val temperature4: StateFlow<Float?> = _temperature4.asStateFlow()

    private val _engineRPM = MutableStateFlow<Float?>(null)
    val engineRPM: StateFlow<Float?> = _engineRPM.asStateFlow()

    private val _updateTicker = MutableStateFlow(0L)
    val updateTicker: StateFlow<Long> = _updateTicker.asStateFlow()

    fun updateFromBundle(bundle: Bundle) {
        _odometer.value = bundle.getUIntOrNull("odometer")
        _carSpeed.value = bundle.getFloatOrNull("carSpeed")
        _voltage.value = bundle.getFloatOrNull("voltage")
        _longitudinalAcceleration.value = bundle.getFloatOrNull("longitudinalAcceleration")
        _lateralAcceleration.value = bundle.getFloatOrNull("lateralAcceleration")
        _yawRate.value = bundle.getFloatOrNull("yawRate")
        _pressure1.value = bundle.getFloatOrNull("pressure1")
        _pressure2.value = bundle.getFloatOrNull("pressure2")
        _pressure3.value = bundle.getFloatOrNull("pressure3")
        _pressure4.value = bundle.getFloatOrNull("pressure4")
        _temperature1.value = bundle.getFloatOrNull("temperature1")
        _temperature2.value = bundle.getFloatOrNull("temperature2")
        _temperature3.value = bundle.getFloatOrNull("temperature3")
        _temperature4.value = bundle.getFloatOrNull("temperature4")
        _engineRPM.value = bundle.getFloatOrNull("engineRPM")
        touch()
    }

    private fun touch() {
        _updateTicker.value = _updateTicker.value + 1
    }
}

private fun Bundle.toNetState(): NetState {
    return NetState(
        csq = getInt("csq", 99),
        signalLevel = getInt("signalLevel", 0),
        netStatus = getString("netStatus").orEmpty(),
        regStatus = getString("regStatus").orEmpty(),
        simStatus = getString("simStatus").orEmpty(),
        connectionChangeTime = getDateOrNull("connectionChangeTime")
    )
}

private fun Bundle.toNetValues(): NetValues {
    return NetValues(
        imei = getString("imei").orEmpty(),
        iccid = getString("iccid").orEmpty(),
        imsi = getString("imsi").orEmpty(),
        operator = getString("operator").orEmpty(),
    )
}

private fun Bundle.toApnState(): APNState {
    return APNState(
        apnStatus = getBooleanOrNull("apnStatus"),
        apnType = getString("apnType").orEmpty(),
        apnIP = getString("apnIP").orEmpty(),
        apnGate = getString("apnGate").orEmpty(),
        apnDNS1 = getString("apnDNS1").orEmpty(),
        apnDNS2 = getString("apnDNS2").orEmpty(),
        changeTime = getDateOrNull("changeTime"),
    )
}

private fun Bundle.toVoltagesState(): VoltagesState {
    return VoltagesState(
        voltage1 = getFloatOrNull("voltage1"),
        voltage2 = getFloatOrNull("voltage2"),
        voltage3 = getFloatOrNull("voltage3"),
        updateTime = getDateOrNull("updateTime"),
    )
}

private fun Bundle.toHdmData(): HdmData {
    return HdmData(
        isPower = getBoolean("isPower", false),
        isIgnition = getBoolean("isIgnition", false),
        isCan = getBoolean("isCan", false),
    )
}

private fun Bundle.toLocValues(): LocValues {
    return LocValues(
        rawValue = getString("rawValue").orEmpty(),
        locateStatus = getBoolean("locateStatus", false),
        utcTime = getBundle("utcTime")?.toUtcTime(),
        longitude = if (containsKey("longitude")) getDouble("longitude") else 0.0,
        latitude = if (containsKey("latitude")) getDouble("latitude") else 0.0,
        altitude = if (containsKey("altitude")) getDouble("altitude") else 0.0,
        visibleSatellites = getInt("visibleSatellites", 0),
        usingSatellites = getInt("usingSatellites", 0),
        speed = if (containsKey("speed")) getFloat("speed") else 0f,
        trueDirection = if (containsKey("trueDirection")) getFloat("trueDirection") else 0f,
        magneticDirection = if (containsKey("magneticDirection")) getFloat("magneticDirection") else 0f,
        updateTime = getDateOrNull("updateTime"),
    )
}

private fun Bundle.toUtcTime(): UtcTime {
    return UtcTime(
        year = getInt("year", 0),
        month = getInt("month", 0),
        day = getInt("day", 0),
        hour = getInt("hour", 0),
        minute = getInt("minute", 0),
        second = getInt("second", 0),
    )
}

private fun Bundle.toWheels(): Wheels {
    return Wheels(
        wheel1 = getFloatOrNull("wheel1"),
        wheel2 = getFloatOrNull("wheel2"),
        wheel3 = getFloatOrNull("wheel3"),
        wheel4 = getFloatOrNull("wheel4"),
    )
}

private fun Bundle.getFloatOrNull(key: String): Float? {
    return if (containsKey(key)) getFloat(key) else null
}

private fun Bundle.getIntOrNull(key: String): Int? {
    return if (containsKey(key)) getInt(key) else null
}

private fun Bundle.getBooleanOrNull(key: String): Boolean? {
    return if (containsKey(key)) getBoolean(key) else null
}

private fun Bundle.getLongOrNull(key: String): Long? {
    return if (containsKey(key)) getLong(key) else null
}

private fun Bundle.getDateOrNull(key: String): Date? {
    return getLongOrNull(key)?.let { Date(it) }
}

private fun Bundle.getUIntOrNull(key: String): UInt? {
    val value = getLongOrNull(key) ?: return null
    return if (value < 0) null else value.toUInt()
}
