package vad.dashing.tbox.mbcan

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import vad.dashing.tbox.AppContextHolder
import vad.dashing.tbox.DRIVE_MODE_WIDGET_DATA_KEY
import vad.dashing.tbox.FRONT_LEFT_SEAT_HEAT_VENT_SINGLE_WIDGET_DATA_KEY
import vad.dashing.tbox.FRONT_RIGHT_SEAT_HEAT_VENT_SINGLE_WIDGET_DATA_KEY
import vad.dashing.tbox.REAR_LEFT_SEAT_HEAT_WIDGET_DATA_KEY
import vad.dashing.tbox.REAR_RIGHT_SEAT_HEAT_WIDGET_DATA_KEY

private class CarPropertyBridge(private val context: Context) {
    private var car: Any? = null
    private var propertyManager: Any? = null

    fun connect(): MbCanAvailability {
        return runCatching {
            val carClass = Class.forName("android.car.Car")
            val createCar = carClass.methods.firstOrNull { method ->
                method.name == "createCar" &&
                    java.lang.reflect.Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.firstOrNull() == Context::class.java
            } ?: throw IllegalStateException("Car.createCar(Context) not found")
            val carInstance = createCar.invoke(null, context)
                ?: throw IllegalStateException("Car instance is null")
            carClass.getMethod("connect").invoke(carInstance)

            val propertyService = runCatching {
                carClass.getField("PROPERTY_SERVICE").get(null) as String
            }.getOrDefault("property")
            val manager = carClass.getMethod("getCarManager", String::class.java)
                .invoke(carInstance, propertyService)
                ?: throw IllegalStateException("CarPropertyManager is null")
            car = carInstance
            propertyManager = manager
        }.fold(
            onSuccess = { MbCanAvailability.Available },
            onFailure = { MbCanAvailability.Unavailable("VHAL connect failed: ${it.message}") }
        )
    }

    fun disconnect() {
        runCatching {
            val c = car ?: return
            c.javaClass.getMethod("disconnect").invoke(c)
        }
        car = null
        propertyManager = null
    }

    fun getIntProperty(propertyId: Int, areaId: Int = 0): Int? {
        val manager = propertyManager ?: return null
        return runCatching {
            manager.javaClass
                .getMethod("getIntProperty", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .invoke(manager, propertyId, areaId) as Int
        }.getOrNull()
    }

    fun setIntProperty(propertyId: Int, value: Int, areaId: Int = 0): Boolean {
        val manager = propertyManager ?: return false
        return runCatching {
            manager.javaClass
                .getMethod(
                    "setIntProperty",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
                .invoke(manager, propertyId, areaId, value)
            true
        }.getOrDefault(false)
    }
}

object Android10VhalRepository {
    private const val POLL_INTERVAL_MS = 1_500L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sourceSignals = mutableMapOf<String, Set<MbCanSignal>>()
    private val sourceMutex = Mutex()
    private var pollJob: Job? = null
    private var bridge: CarPropertyBridge? = null

    private val _availability = MutableStateFlow<MbCanAvailability>(MbCanAvailability.Unknown)
    val availability: StateFlow<MbCanAvailability> = _availability.asStateFlow()

    private val _steeringWheelHeatState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val steeringWheelHeatState: StateFlow<MbCanBinaryState> = _steeringWheelHeatState.asStateFlow()
    private val _frontWindscreenHeatState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val frontWindscreenHeatState: StateFlow<MbCanBinaryState> = _frontWindscreenHeatState.asStateFlow()
    private val _hvacDefrosterState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val hvacDefrosterState: StateFlow<MbCanBinaryState> = _hvacDefrosterState.asStateFlow()
    private val _hvacAirRecirculationState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val hvacAirRecirculationState: StateFlow<MbCanBinaryState> = _hvacAirRecirculationState.asStateFlow()
    private val _hvacDefrosterFrontState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val hvacDefrosterFrontState: StateFlow<MbCanBinaryState> = _hvacDefrosterFrontState.asStateFlow()
    private val _audioVolumeSpeedState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val audioVolumeSpeedState: StateFlow<MbCanBinaryState> = _audioVolumeSpeedState.asStateFlow()
    private val _audioVolumeState = MutableStateFlow<Int?>(null)
    val audioVolumeState: StateFlow<Int?> = _audioVolumeState.asStateFlow()
    private val _audioVolumeLastNonZeroInSession = MutableStateFlow<Int?>(null)

    private val _frontLeftSeatModeState = MutableStateFlow<MbCanSeatModeState>(MbCanSeatModeState.Unknown)
    val frontLeftSeatModeState: StateFlow<MbCanSeatModeState> = _frontLeftSeatModeState.asStateFlow()
    private val _frontRightSeatModeState = MutableStateFlow<MbCanSeatModeState>(MbCanSeatModeState.Unknown)
    val frontRightSeatModeState: StateFlow<MbCanSeatModeState> = _frontRightSeatModeState.asStateFlow()
    private val _rearLeftSeatModeState = MutableStateFlow<MbCanSeatModeState>(MbCanSeatModeState.Unknown)
    val rearLeftSeatModeState: StateFlow<MbCanSeatModeState> = _rearLeftSeatModeState.asStateFlow()
    private val _rearRightSeatModeState = MutableStateFlow<MbCanSeatModeState>(MbCanSeatModeState.Unknown)
    val rearRightSeatModeState: StateFlow<MbCanSeatModeState> = _rearRightSeatModeState.asStateFlow()

    private val _carSettingsEpsMode = MutableStateFlow<Int?>(null)
    val carSettingsEpsMode: StateFlow<Int?> = _carSettingsEpsMode.asStateFlow()
    private val _carSettingsDriveMode = MutableStateFlow<Int?>(null)
    val carSettingsDriveMode: StateFlow<Int?> = _carSettingsDriveMode.asStateFlow()
    private val _carSettingsDriveMode6dctWet = MutableStateFlow<Int?>(null)
    val carSettingsDriveMode6dctWet: StateFlow<Int?> = _carSettingsDriveMode6dctWet.asStateFlow()

    private val stateEngine = MbCanSignalStateEngine(
        steeringFlow = _steeringWheelHeatState,
        windshieldHeatFlow = _frontWindscreenHeatState,
        hvacDefrosterFlow = _hvacDefrosterState,
        hvacAirRecirculationFlow = _hvacAirRecirculationState,
        hvacDefrosterFrontFlow = _hvacDefrosterFrontState,
        wirelessChargingFlow = MutableStateFlow(MbCanBinaryState.Unknown),
        volumeSpeedFlow = _audioVolumeSpeedState,
        frontLeftSeatFlow = _frontLeftSeatModeState,
        frontRightSeatFlow = _frontRightSeatModeState,
        rearLeftSeatFlow = _rearLeftSeatModeState,
        rearRightSeatFlow = _rearRightSeatModeState
    )

    private fun currentUnavailableReason(): String =
        (availability.value as? MbCanAvailability.Unavailable)?.reason ?: "VHAL unavailable"

    private suspend fun ensureConnected(): MbCanAvailability = withContext(Dispatchers.Default) {
        val context = AppContextHolder.appContextOrNull
            ?: return@withContext MbCanAvailability.Unavailable("No app context")
        val existing = bridge
        if (existing != null && availability.value is MbCanAvailability.Available) {
            return@withContext availability.value
        }
        val newBridge = CarPropertyBridge(context)
        val result = newBridge.connect()
        _availability.value = result
        if (result is MbCanAvailability.Available) {
            bridge = newBridge
        } else {
            newBridge.disconnect()
        }
        result
    }

    suspend fun bind(_scope: CoroutineScope) {
        ensureConnected()
        restartPolling()
    }

    suspend fun unbind() {
        pollJob?.cancel()
        pollJob = null
        bridge?.disconnect()
        bridge = null
    }

    suspend fun warmUpAvailabilityForUi() {
        ensureConnected()
    }

    suspend fun setSourceWidgetKeys(sourceId: String, widgetKeys: Set<String>) {
        val signals = widgetKeys.mapNotNull { key ->
            when (key) {
                "steeringWheelHeatWidget" -> MbCanSignal.SteeringWheelHeat
                "frontWindscreenHeatWidget" -> MbCanSignal.FrontWindscreenHeat
                "rearWindowMirrorsDefrostWidget" -> MbCanSignal.HvacDefroster
                "hvacAirRecirculationWidget" -> MbCanSignal.HvacAirRecirculation
                "hvacDefrosterFrontWidget" -> MbCanSignal.HvacDefrosterFront
                DRIVE_MODE_WIDGET_DATA_KEY -> MbCanSignal.CarSettingsVehicleParams
                "frontLeftSeatHeatVentWidget" -> MbCanSignal.FrontLeftSeatMode
                "frontRightSeatHeatVentWidget" -> MbCanSignal.FrontRightSeatMode
                FRONT_LEFT_SEAT_HEAT_VENT_SINGLE_WIDGET_DATA_KEY -> MbCanSignal.FrontLeftSeatMode
                FRONT_RIGHT_SEAT_HEAT_VENT_SINGLE_WIDGET_DATA_KEY -> MbCanSignal.FrontRightSeatMode
                REAR_LEFT_SEAT_HEAT_WIDGET_DATA_KEY -> MbCanSignal.RearLeftSeatMode
                REAR_RIGHT_SEAT_HEAT_WIDGET_DATA_KEY -> MbCanSignal.RearRightSeatMode
                else -> null
            }
        }.toSet()
        sourceMutex.withLock {
            if (signals.isEmpty()) sourceSignals.remove(sourceId) else sourceSignals[sourceId] = signals
        }
        restartPolling()
    }

    suspend fun setSourceSignals(sourceId: String, signals: Set<MbCanSignal>) {
        sourceMutex.withLock {
            if (signals.isEmpty()) sourceSignals.remove(sourceId) else sourceSignals[sourceId] = signals
        }
        restartPolling()
    }

    fun enqueueClearSource(sourceId: String) {
        scope.launch {
            sourceMutex.withLock { sourceSignals.remove(sourceId) }
            restartPolling()
        }
    }

    fun widgetConfigsNeedMbCan(dataKeys: Iterable<String>): Boolean {
        return dataKeys.any { key ->
            key in setOf(
                "steeringWheelHeatWidget",
                "frontWindscreenHeatWidget",
                "rearWindowMirrorsDefrostWidget",
                "hvacAirRecirculationWidget",
                "hvacDefrosterFrontWidget",
                DRIVE_MODE_WIDGET_DATA_KEY,
                "frontLeftSeatHeatVentWidget",
                "frontRightSeatHeatVentWidget",
                FRONT_LEFT_SEAT_HEAT_VENT_SINGLE_WIDGET_DATA_KEY,
                FRONT_RIGHT_SEAT_HEAT_VENT_SINGLE_WIDGET_DATA_KEY,
                REAR_LEFT_SEAT_HEAT_WIDGET_DATA_KEY,
                REAR_RIGHT_SEAT_HEAT_WIDGET_DATA_KEY
            )
        }
    }

    private suspend fun restartPolling() {
        pollJob?.cancel()
        val interestedSignals = sourceMutex.withLock { sourceSignals.values.flatten().toSet() }
        if (interestedSignals.isEmpty()) return
        pollJob = scope.launch {
            while (true) {
                interestedSignals.forEach { signal -> refreshSignal(signal) }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    suspend fun refreshSignal(signal: MbCanSignal) {
        val connection = ensureConnected()
        if (connection !is MbCanAvailability.Available) {
            val reason = currentUnavailableReason()
            when (signal) {
                MbCanSignal.SteeringWheelHeat -> stateEngine.applySteeringCandidate(MbCanBinaryState.Unavailable(reason))
                MbCanSignal.FrontWindscreenHeat -> stateEngine.applyWindshieldHeatCandidate(MbCanBinaryState.Unavailable(reason))
                MbCanSignal.HvacDefroster -> stateEngine.applyHvacDefrosterCandidate(MbCanBinaryState.Unavailable(reason))
                MbCanSignal.HvacAirRecirculation -> stateEngine.applyHvacAirRecirculationCandidate(MbCanBinaryState.Unavailable(reason))
                MbCanSignal.HvacDefrosterFront -> stateEngine.applyHvacDefrosterFrontCandidate(MbCanBinaryState.Unavailable(reason))
                MbCanSignal.AudioVolumeSpeed -> stateEngine.applyVolumeSpeedCandidate(MbCanBinaryState.Unavailable(reason))
                MbCanSignal.FrontLeftSeatMode ->
                    stateEngine.applySeatCandidate(MbCanSeatSlot.FrontLeft, MbCanSeatModeState.Unavailable(reason))
                MbCanSignal.FrontRightSeatMode ->
                    stateEngine.applySeatCandidate(MbCanSeatSlot.FrontRight, MbCanSeatModeState.Unavailable(reason))
                MbCanSignal.RearLeftSeatMode ->
                    stateEngine.applySeatCandidate(MbCanSeatSlot.RearLeft, MbCanSeatModeState.Unavailable(reason))
                MbCanSignal.RearRightSeatMode ->
                    stateEngine.applySeatCandidate(MbCanSeatSlot.RearRight, MbCanSeatModeState.Unavailable(reason))
                MbCanSignal.AudioVolume -> _audioVolumeState.value = null
                MbCanSignal.CarSettingsVehicleParams -> {
                    _carSettingsEpsMode.value = null
                    _carSettingsDriveMode.value = null
                    _carSettingsDriveMode6dctWet.value = null
                }
                MbCanSignal.WirelessChargingSwitch -> Unit
            }
            return
        }

        when (signal) {
            MbCanSignal.SteeringWheelHeat -> {
                val propertyId = FirmwareVehicleJsonMapper
                    .resolveReadPropertyId(MbCanKnownVehiclePropertyId.STEERING_WHEEL_HEAT_SWITCH)
                    ?: MbCanKnownVehiclePropertyId.STEERING_WHEEL_HEAT_SWITCH
                val raw = bridge?.getIntProperty(propertyId)
                stateEngine.applySteeringCandidate(
                    raw?.let(MbCanSignalStateEngine::decodeSteeringWheelHeatRaw) ?: MbCanBinaryState.Unknown
                )
            }
            MbCanSignal.FrontWindscreenHeat -> {
                val propertyId = FirmwareVehicleJsonMapper
                    .resolveReadPropertyId(MbCanKnownVehiclePropertyId.FRONT_WINDSCREEN_HEAT_SWITCH)
                    ?: MbCanKnownVehiclePropertyId.FRONT_WINDSCREEN_HEAT_SWITCH
                val raw = bridge?.getIntProperty(propertyId)
                stateEngine.applyWindshieldHeatCandidate(
                    raw?.let(MbCanSignalStateEngine::decodeFrontWindscreenHeatRaw) ?: MbCanBinaryState.Unknown
                )
            }
            MbCanSignal.HvacDefroster -> {
                val propertyId = FirmwareVehicleJsonMapper
                    .resolveReadPropertyId(MbCanKnownVehiclePropertyId.HVAC_DEFROSTER_SWITCH)
                    ?: MbCanKnownVehiclePropertyId.HVAC_DEFROSTER_SWITCH
                val raw = bridge?.getIntProperty(propertyId)
                stateEngine.applyHvacDefrosterCandidate(
                    raw?.let(MbCanSignalStateEngine::decodeHvacDefrosterRaw) ?: MbCanBinaryState.Unknown
                )
            }
            MbCanSignal.HvacAirRecirculation -> {
                val propertyId = FirmwareVehicleJsonMapper
                    .resolveReadPropertyId(MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION)
                    ?: MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION
                val raw = bridge?.getIntProperty(propertyId)
                stateEngine.applyHvacAirRecirculationCandidate(
                    raw?.let(MbCanSignalStateEngine::decodeHvacAirRecirculationRaw) ?: MbCanBinaryState.Unknown
                )
            }
            MbCanSignal.HvacDefrosterFront -> {
                val propertyId = FirmwareVehicleJsonMapper
                    .resolveReadPropertyId(MbCanKnownVehiclePropertyId.HVAC_DEFROSTER_FRONT)
                    ?: MbCanKnownVehiclePropertyId.HVAC_DEFROSTER_FRONT
                val raw = bridge?.getIntProperty(propertyId)
                stateEngine.applyHvacDefrosterFrontCandidate(
                    raw?.let(MbCanSignalStateEngine::decodeHvacDefrosterFrontRaw) ?: MbCanBinaryState.Unknown
                )
            }
            MbCanSignal.AudioVolume -> {
                val propertyId = FirmwareVehicleJsonMapper
                    .resolveReadPropertyId(MbCanKnownAudioPropertyId.VOLUME)
                    ?: MbCanKnownAudioPropertyId.VOLUME
                val raw = bridge?.getIntProperty(propertyId)
                _audioVolumeState.value = raw?.coerceAtLeast(0)
                if ((raw ?: 0) > 0) _audioVolumeLastNonZeroInSession.value = raw
            }
            MbCanSignal.AudioVolumeSpeed -> {
                val propertyId = FirmwareVehicleJsonMapper
                    .resolveReadPropertyId(MbCanKnownAudioPropertyId.VOLUME_SPEED)
                    ?: MbCanKnownAudioPropertyId.VOLUME_SPEED
                val raw = bridge?.getIntProperty(propertyId)
                stateEngine.applyVolumeSpeedCandidate(
                    raw?.let(MbCanSignalStateEngine::decodeVolumeSpeedRaw) ?: MbCanBinaryState.Unknown
                )
            }
            MbCanSignal.CarSettingsVehicleParams -> {
                val epsId = FirmwareVehicleJsonMapper
                    .resolveReadPropertyId(MbCanKnownVehiclePropertyId.VEHICLE_PROPERTY_EPS_MODE)
                    ?: MbCanKnownVehiclePropertyId.VEHICLE_PROPERTY_EPS_MODE
                val driveId = FirmwareVehicleJsonMapper
                    .resolveReadPropertyId(MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE)
                    ?: MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE
                val driveWetId = FirmwareVehicleJsonMapper
                    .resolveReadPropertyId(MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE_6DCT_WET)
                    ?: MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE_6DCT_WET
                _carSettingsEpsMode.value = bridge?.getIntProperty(epsId)
                _carSettingsDriveMode.value = bridge?.getIntProperty(driveId)
                _carSettingsDriveMode6dctWet.value = bridge?.getIntProperty(driveWetId)
            }
            MbCanSignal.FrontLeftSeatMode -> {
                val propertyId = FirmwareVehicleJsonMapper
                    .resolveReadPropertyId(MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH)
                    ?: MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH
                val raw = bridge?.getIntProperty(propertyId)
                val decoded = raw?.let(MbCanSignalStateEngine::decodeSeatModeRaw) ?: MbCanSeatModeState.Unknown
                stateEngine.applySeatCandidate(MbCanSeatSlot.FrontLeft, decoded)
            }
            MbCanSignal.FrontRightSeatMode -> {
                val propertyId = FirmwareVehicleJsonMapper
                    .resolveReadPropertyId(MbCanKnownVehiclePropertyId.FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH)
                    ?: MbCanKnownVehiclePropertyId.FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH
                val raw = bridge?.getIntProperty(propertyId)
                val decoded = raw?.let(MbCanSignalStateEngine::decodeSeatModeRaw) ?: MbCanSeatModeState.Unknown
                stateEngine.applySeatCandidate(MbCanSeatSlot.FrontRight, decoded)
            }
            MbCanSignal.RearLeftSeatMode -> {
                val propertyId = FirmwareVehicleJsonMapper
                    .resolveReadPropertyId(MbCanKnownVehiclePropertyId.REAR_LEFT_SEAT_HEAT_SWITCH)
                    ?: MbCanKnownVehiclePropertyId.REAR_LEFT_SEAT_HEAT_SWITCH
                val raw = bridge?.getIntProperty(propertyId)
                val decoded = raw?.let(MbCanSignalStateEngine::decodeRearSeatHeatRaw) ?: MbCanSeatModeState.Unknown
                stateEngine.applySeatCandidate(MbCanSeatSlot.RearLeft, decoded)
            }
            MbCanSignal.RearRightSeatMode -> {
                val propertyId = FirmwareVehicleJsonMapper
                    .resolveReadPropertyId(MbCanKnownVehiclePropertyId.REAR_RIGHT_SEAT_HEAT_SWITCH)
                    ?: MbCanKnownVehiclePropertyId.REAR_RIGHT_SEAT_HEAT_SWITCH
                val raw = bridge?.getIntProperty(propertyId)
                val decoded = raw?.let(MbCanSignalStateEngine::decodeRearSeatHeatRaw) ?: MbCanSeatModeState.Unknown
                stateEngine.applySeatCandidate(MbCanSeatSlot.RearRight, decoded)
            }
            MbCanSignal.WirelessChargingSwitch -> Unit
        }
    }

    suspend fun execute(command: MbCanCommand): MbCanCommandResult {
        val connection = ensureConnected()
        if (connection !is MbCanAvailability.Available) {
            return MbCanCommandResult(false, currentUnavailableReason())
        }
        return when (command) {
            is MbCanCommand.ToggleProperty -> {
                val spec = MbCanCommandRegistry.get(command.propertyId)
                    ?: return MbCanCommandResult(false, "No command policy for propertyId=${command.propertyId}")
                val policy = spec.policy as? MbCanCommandPolicy.ToggleBinary
                    ?: return MbCanCommandResult(false, "Toggle unsupported for propertyId=${command.propertyId}")
                val effectivePropertyId = FirmwareVehicleJsonMapper.resolveWritePropertyId(command.propertyId)
                    ?: command.propertyId
                val current = bridge?.getIntProperty(effectivePropertyId)
                    ?: return MbCanCommandResult(false, "Property read failed")
                val target = when (current) {
                    policy.onValue -> policy.offValue
                    policy.offValue -> policy.onValue
                    else -> policy.unknownFallbackValue
                }
                val ok = bridge?.setIntProperty(effectivePropertyId, target) == true
                spec.refreshSignal?.let { refreshSignal(it) }
                MbCanCommandResult(ok, if (ok) "Set ok" else "Set failed")
            }
            is MbCanCommand.SetProperty -> {
                val spec = MbCanCommandRegistry.get(command.propertyId)
                    ?: return MbCanCommandResult(false, "No command policy for propertyId=${command.propertyId}")
                val policy = spec.policy as? MbCanCommandPolicy.SetExact
                    ?: return MbCanCommandResult(false, "Set unsupported for propertyId=${command.propertyId}")
                if (!policy.allowedValues.contains(command.value)) {
                    return MbCanCommandResult(false, "Value ${command.value} is not allowed")
                }
                val effectivePropertyId = FirmwareVehicleJsonMapper.resolveWritePropertyId(command.propertyId)
                    ?: command.propertyId
                val ok = bridge?.setIntProperty(effectivePropertyId, command.value) == true
                spec.refreshSignal?.let { refreshSignal(it) }
                MbCanCommandResult(ok, if (ok) "Set ok" else "Set failed")
            }
            is MbCanCommand.ToggleAudioProperty -> {
                val spec = MbCanAudioCommandRegistry.get(command.propertyId)
                    ?: return MbCanCommandResult(false, "No audio command policy for propertyId=${command.propertyId}")
                val policy = spec.policy as? MbCanCommandPolicy.ToggleBinary
                    ?: return MbCanCommandResult(false, "Toggle unsupported for audio propertyId=${command.propertyId}")
                val effectivePropertyId = FirmwareVehicleJsonMapper.resolveWritePropertyId(command.propertyId)
                    ?: command.propertyId
                val current = bridge?.getIntProperty(effectivePropertyId)
                    ?: return MbCanCommandResult(false, "Audio property read failed")
                val target = when (current) {
                    policy.onValue -> policy.offValue
                    policy.offValue -> policy.onValue
                    else -> policy.unknownFallbackValue
                }
                val ok = bridge?.setIntProperty(effectivePropertyId, target) == true
                spec.refreshSignal?.let { refreshSignal(it) }
                MbCanCommandResult(ok, if (ok) "Set ok" else "Set failed")
            }
            is MbCanCommand.SetAudioProperty -> {
                val spec = MbCanAudioCommandRegistry.get(command.propertyId)
                    ?: return MbCanCommandResult(false, "No audio command policy for propertyId=${command.propertyId}")
                val policy = spec.policy as? MbCanCommandPolicy.SetExact
                    ?: return MbCanCommandResult(false, "Set unsupported for audio propertyId=${command.propertyId}")
                if (!policy.allowedValues.contains(command.value)) {
                    return MbCanCommandResult(false, "Value ${command.value} is not allowed")
                }
                val effectivePropertyId = FirmwareVehicleJsonMapper.resolveWritePropertyId(command.propertyId)
                    ?: command.propertyId
                val ok = bridge?.setIntProperty(effectivePropertyId, command.value) == true
                spec.refreshSignal?.let { refreshSignal(it) }
                MbCanCommandResult(ok, if (ok) "Set ok" else "Set failed")
            }
            is MbCanCommand.RefreshSignal -> {
                refreshSignal(command.signal)
                MbCanCommandResult(true, "Refresh requested")
            }
        }
    }

    suspend fun setAudioVolume(value: Int): MbCanCommandResult {
        val connection = ensureConnected()
        if (connection !is MbCanAvailability.Available) {
            return MbCanCommandResult(false, currentUnavailableReason())
        }
        val target = value.coerceAtLeast(0)
        val effectiveVolumeId = FirmwareVehicleJsonMapper.resolveWritePropertyId(MbCanKnownAudioPropertyId.VOLUME)
            ?: MbCanKnownAudioPropertyId.VOLUME
        val ok = bridge?.setIntProperty(effectiveVolumeId, target) == true
        if (ok) {
            _audioVolumeState.value = target
            if (target > 0) _audioVolumeLastNonZeroInSession.value = target
        }
        return MbCanCommandResult(ok, if (ok) "Set ok" else "Set failed")
    }

    fun rememberAudioVolumeLastNonZeroInSession(value: Int) {
        if (value > 0) {
            _audioVolumeLastNonZeroInSession.value = value
        }
    }

    fun audioVolumeRestoreCandidate(defaultValue: Int = 10): Int {
        return (_audioVolumeLastNonZeroInSession.value ?: defaultValue).coerceAtLeast(1)
    }
}
