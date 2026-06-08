package vad.dashing.tbox.mbcan

import android.content.Context
import android.content.ServiceConnection
import android.os.Handler
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
import java.lang.reflect.Proxy
import vad.dashing.tbox.AppContextHolder
import vad.dashing.tbox.DRIVE_MODE_WIDGET_DATA_KEY
import vad.dashing.tbox.FRONT_LEFT_SEAT_HEAT_VENT_SINGLE_WIDGET_DATA_KEY
import vad.dashing.tbox.FRONT_RIGHT_SEAT_HEAT_VENT_SINGLE_WIDGET_DATA_KEY
import vad.dashing.tbox.REAR_LEFT_SEAT_HEAT_WIDGET_DATA_KEY
import vad.dashing.tbox.REAR_RIGHT_SEAT_HEAT_WIDGET_DATA_KEY
import vad.dashing.tbox.TboxRepository

private class CarPropertyBridge(private val context: Context) {
    private var car: Any? = null
    private var propertyManager: Any? = null
    private var pushListener: Any? = null
    private val registeredPushPropertyIds = mutableSetOf<Int>()
    @Volatile
    private var onPushPropertyChanged: ((propertyId: Int, areaId: Int, value: Int) -> Unit)? = null
    @Volatile
    private var onPushPropertyError: ((propertyId: Int, areaId: Int) -> Unit)? = null
    @Volatile
    private var serviceConnected: Boolean = false
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: android.content.ComponentName?, service: android.os.IBinder?) {
            serviceConnected = true
            Android10VhalRepository.logInfo("Car service connected")
        }

        override fun onServiceDisconnected(name: android.content.ComponentName?) {
            serviceConnected = false
            Android10VhalRepository.logWarn("Car service disconnected")
        }
    }

    fun connect(): MbCanAvailability {
        return runCatching {
            val carClass = Class.forName("android.car.Car")
            val carInstance = createCar(carClass)
                ?: throw IllegalStateException("Car instance is null")
            carClass.getMethod("connect").invoke(carInstance)
            waitForServiceConnection()

            val propertyService = runCatching {
                carClass.getField("PROPERTY_SERVICE").get(null) as String
            }.getOrDefault("property")
            val manager = acquirePropertyManager(carClass, carInstance, propertyService)
            car = carInstance
            propertyManager = manager
            Android10VhalRepository.logInfo("VHAL connected, propertyService=$propertyService")
        }.fold(
            onSuccess = { MbCanAvailability.Available },
            onFailure = {
                val root = (it as? java.lang.reflect.InvocationTargetException)?.targetException ?: it
                val msg = "VHAL connect failed: ${root.javaClass.simpleName}: ${root.message}"
                Android10VhalRepository.logError(msg)
                MbCanAvailability.Unavailable(msg)
            }
        )
    }

    private fun waitForServiceConnection(timeoutMs: Long = 2_500L, stepMs: Long = 50L) {
        val start = System.currentTimeMillis()
        while (!serviceConnected && (System.currentTimeMillis() - start) < timeoutMs) {
            Thread.sleep(stepMs)
        }
    }

    private fun acquirePropertyManager(
        carClass: Class<*>,
        carInstance: Any,
        propertyService: String,
    ): Any {
        var lastError: Throwable? = null
        repeat(20) {
            val manager = runCatching {
                carClass.getMethod("getCarManager", String::class.java)
                    .invoke(carInstance, propertyService)
            }.onFailure { lastError = it }.getOrNull()
            if (manager != null) return manager
            Thread.sleep(100L)
        }
        throw IllegalStateException("CarPropertyManager is null: ${lastError?.javaClass?.simpleName}: ${lastError?.message}")
    }

    private fun createCar(carClass: Class<*>): Any? {
        // Matches stock firmware apps:
        // Car.createCar(Context, ServiceConnection) or Car.createCar(Context, ServiceConnection, Handler)
        val method2 = runCatching {
            carClass.getMethod("createCar", Context::class.java, ServiceConnection::class.java)
        }.getOrNull()
        if (method2 != null) {
            Android10VhalRepository.logInfo("Using Car.createCar(Context, ServiceConnection)")
            return method2.invoke(null, context, serviceConnection)
        }

        val method3 = runCatching {
            carClass.getMethod("createCar", Context::class.java, ServiceConnection::class.java, Handler::class.java)
        }.getOrNull()
        if (method3 != null) {
            Android10VhalRepository.logInfo("Using Car.createCar(Context, ServiceConnection, Handler)")
            return method3.invoke(null, context, serviceConnection, null)
        }

        Android10VhalRepository.logError("No compatible Car.createCar overload found")
        return null
    }

    fun disconnect() {
        runCatching { syncPushSubscriptions(emptySet()) }
        runCatching {
            val c = car ?: return
            c.javaClass.getMethod("disconnect").invoke(c)
            Android10VhalRepository.logInfo("VHAL disconnected")
        }.onFailure {
            Android10VhalRepository.logWarn(
                "VHAL disconnect error: ${it.javaClass.simpleName}: ${it.message}"
            )
        }
        serviceConnected = false
        car = null
        propertyManager = null
        pushListener = null
        registeredPushPropertyIds.clear()
        onPushPropertyChanged = null
        onPushPropertyError = null
    }

    fun getIntProperty(propertyId: Int, areaId: Int = 0): Int? {
        val manager = propertyManager ?: return null
        return runCatching {
            manager.javaClass
                .getMethod("getIntProperty", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .invoke(manager, propertyId, areaId) as Int
        }.onFailure {
            Android10VhalRepository.logReadFailure(propertyId, areaId, it)
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
        }.onFailure {
            Android10VhalRepository.logWriteFailure(propertyId, areaId, value, it)
        }.getOrDefault(false)
    }

    fun setPushCallbacks(
        onChange: (propertyId: Int, areaId: Int, value: Int) -> Unit,
        onError: (propertyId: Int, areaId: Int) -> Unit
    ) {
        onPushPropertyChanged = onChange
        onPushPropertyError = onError
    }

    fun syncPushSubscriptions(propertyIds: Set<Int>) {
        val manager = propertyManager ?: return
        val listener = ensurePushListener()
        val toRemove = registeredPushPropertyIds - propertyIds
        val toAdd = propertyIds - registeredPushPropertyIds
        toRemove.forEach { propertyId ->
            runCatching {
                manager.javaClass
                    .getMethod(
                        "unregisterListener",
                        listener.javaClass.interfaces.first(),
                        Int::class.javaPrimitiveType
                    )
                    .invoke(manager, listener, propertyId)
                registeredPushPropertyIds.remove(propertyId)
                Android10VhalRepository.logInfo("VHAL push unregistered propertyId=$propertyId")
            }.onFailure {
                Android10VhalRepository.logWarn(
                    "VHAL push unregister failed propertyId=$propertyId " +
                        "error=${it.javaClass.simpleName}: ${it.message}"
                )
            }
        }
        toAdd.forEach { propertyId ->
            runCatching {
                manager.javaClass
                    .getMethod(
                        "registerListener",
                        listener.javaClass.interfaces.first(),
                        Int::class.javaPrimitiveType,
                        Float::class.javaPrimitiveType
                    )
                    .invoke(manager, listener, propertyId, 0.0f)
                registeredPushPropertyIds.add(propertyId)
                Android10VhalRepository.logInfo("VHAL push registered propertyId=$propertyId rate=0.0")
            }.onFailure {
                Android10VhalRepository.logWarn(
                    "VHAL push register failed propertyId=$propertyId " +
                        "error=${it.javaClass.simpleName}: ${it.message}"
                )
            }
        }
    }

    private fun ensurePushListener(): Any {
        pushListener?.let { return it }
        val listenerInterface = Class.forName("android.car.hardware.property.CarPropertyManager\$CarPropertyEventListener")
        val proxy = Proxy.newProxyInstance(
            listenerInterface.classLoader,
            arrayOf(listenerInterface)
        ) { _, method, args ->
            when (method.name) {
                "onChangeEvent" -> {
                    val event = args?.getOrNull(0) ?: return@newProxyInstance null
                    val propertyId = runCatching {
                        event.javaClass.getMethod("getPropertyId").invoke(event) as Int
                    }.getOrNull() ?: return@newProxyInstance null
                    val areaId = runCatching {
                        event.javaClass.getMethod("getAreaId").invoke(event) as Int
                    }.getOrDefault(0)
                    val value = runCatching {
                        event.javaClass.getMethod("getValue").invoke(event)
                    }.getOrNull()
                    val intValue = when (value) {
                        is Number -> value.toInt()
                        is Boolean -> if (value) 1 else 0
                        else -> null
                    }
                    if (intValue != null) {
                        onPushPropertyChanged?.invoke(propertyId, areaId, intValue)
                    }
                    null
                }
                "onErrorEvent" -> {
                    val propertyId = (args?.getOrNull(0) as? Int) ?: return@newProxyInstance null
                    val areaId = (args.getOrNull(1) as? Int) ?: 0
                    onPushPropertyError?.invoke(propertyId, areaId)
                    null
                }
                else -> null
            }
        }
        pushListener = proxy
        return proxy
    }
}

object Android10VhalRepository {
    private const val NORMAL_POLL_INTERVAL_MS = 30_000L
    private const val BURST_POLL_INTERVAL_MS = 1_500L
    private const val BURST_DURATION_MS = 15_000L
    private const val LOG_TAG = "VHAL_A10"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sourceSignals = mutableMapOf<String, Set<MbCanSignal>>()
    private val sourceMutex = Mutex()
    private var pollJob: Job? = null
    private var bridge: CarPropertyBridge? = null
    @Volatile
    private var burstUntilMs: Long = 0L
    private val readErrorsLogged = mutableSetOf<String>()
    private val writeErrorsLogged = mutableSetOf<String>()
    private var lastAvailabilityReason: String? = null
    @Volatile
    private var carInfoPermissionDenied: Boolean = false

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

    private fun permissionDeniedReasonOrNull(): String? {
        return if (carInfoPermissionDenied) {
            "Missing permission android.car.permission.CAR_INFO"
        } else {
            null
        }
    }

    internal fun logInfo(message: String) {
        TboxRepository.addLog("INFO", LOG_TAG, message)
    }

    internal fun logWarn(message: String) {
        TboxRepository.addLog("WARN", LOG_TAG, message)
    }

    internal fun logError(message: String) {
        TboxRepository.addLog("ERROR", LOG_TAG, message)
    }

    internal fun logReadFailure(propertyId: Int, areaId: Int, throwable: Throwable) {
        val key = "$propertyId:$areaId:${throwable.javaClass.name}:${throwable.message}"
        synchronized(readErrorsLogged) {
            if (!readErrorsLogged.add(key)) return
        }
        val root = (throwable.cause ?: throwable)
        val prefix = if (root is SecurityException) "POSSIBLE_PERMISSION" else "READ_FAILED"
        if (root is SecurityException && root.message?.contains("android.car.permission.CAR_INFO") == true) {
            carInfoPermissionDenied = true
            _availability.value = MbCanAvailability.Unavailable("Missing permission android.car.permission.CAR_INFO")
        }
        TboxRepository.addLog(
            "WARN",
            LOG_TAG,
            "$prefix propertyId=$propertyId areaId=$areaId " +
                "error=${root.javaClass.simpleName}: ${root.message}"
        )
    }

    internal fun logWriteFailure(propertyId: Int, areaId: Int, value: Int, throwable: Throwable) {
        val key = "$propertyId:$areaId:$value:${throwable.javaClass.name}:${throwable.message}"
        synchronized(writeErrorsLogged) {
            if (!writeErrorsLogged.add(key)) return
        }
        val root = (throwable.cause ?: throwable)
        val prefix = if (root is SecurityException) "POSSIBLE_PERMISSION" else "WRITE_FAILED"
        if (root is SecurityException && root.message?.contains("android.car.permission.CAR_INFO") == true) {
            carInfoPermissionDenied = true
            _availability.value = MbCanAvailability.Unavailable("Missing permission android.car.permission.CAR_INFO")
        }
        TboxRepository.addLog(
            "WARN",
            LOG_TAG,
            "$prefix propertyId=$propertyId areaId=$areaId value=$value " +
                "error=${root.javaClass.simpleName}: ${root.message}"
        )
    }

    private suspend fun ensureConnected(): MbCanAvailability = withContext(Dispatchers.Default) {
        val context = AppContextHolder.appContextOrNull
            ?: return@withContext MbCanAvailability.Unavailable("No app context").also {
                val reason = "No app context"
                if (lastAvailabilityReason != reason) {
                    logWarn("Availability: $reason")
                    lastAvailabilityReason = reason
                }
            }
        val existing = bridge
        if (existing != null && availability.value is MbCanAvailability.Available) {
            return@withContext availability.value
        }
        val newBridge = CarPropertyBridge(context)
        val result = newBridge.connect()
        _availability.value = result
        if (result is MbCanAvailability.Available) {
            bridge = newBridge
            if (lastAvailabilityReason != "AVAILABLE") {
                logInfo("Availability: AVAILABLE")
                lastAvailabilityReason = "AVAILABLE"
            }
        } else {
            newBridge.disconnect()
            val reason = (result as? MbCanAvailability.Unavailable)?.reason ?: "VHAL unavailable"
            if (lastAvailabilityReason != reason) {
                logWarn("Availability: $reason")
                lastAvailabilityReason = reason
            }
        }
        result
    }

    suspend fun bind(_scope: CoroutineScope) {
        logInfo("bind()")
        carInfoPermissionDenied = false
        ensureConnected()
        restartPolling()
    }

    suspend fun unbind() {
        logInfo("unbind()")
        pollJob?.cancel()
        pollJob = null
        bridge?.disconnect()
        bridge = null
    }

    suspend fun warmUpAvailabilityForUi() {
        logInfo("warmUpAvailabilityForUi()")
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
        syncPushListeners(interestedSignals)
        if (interestedSignals.isEmpty()) {
            logInfo("polling stopped: no interested signals")
            return
        }
        logInfo("polling started: signals=${interestedSignals.size} ${interestedSignals.joinToString()}")
        pollJob = scope.launch {
            while (true) {
                interestedSignals.forEach { signal -> refreshSignal(signal) }
                val now = System.currentTimeMillis()
                val delayMs = if (now < burstUntilMs) BURST_POLL_INTERVAL_MS else NORMAL_POLL_INTERVAL_MS
                delay(delayMs)
            }
        }
    }

    private fun requestBurstPolling() {
        burstUntilMs = System.currentTimeMillis() + BURST_DURATION_MS
        logInfo("polling burst requested until=$burstUntilMs")
    }

    private fun signalReadPropertyIds(signal: MbCanSignal): Set<Int> {
        fun resolved(id: Int): Int = FirmwareVehicleJsonMapper.resolveReadPropertyId(id) ?: id
        return when (signal) {
            MbCanSignal.SteeringWheelHeat -> setOf(resolved(MbCanKnownVehiclePropertyId.STEERING_WHEEL_HEAT_SWITCH))
            MbCanSignal.FrontWindscreenHeat -> setOf(resolved(MbCanKnownVehiclePropertyId.FRONT_WINDSCREEN_HEAT_SWITCH))
            MbCanSignal.HvacDefroster -> setOf(resolved(MbCanKnownVehiclePropertyId.HVAC_DEFROSTER_SWITCH))
            MbCanSignal.HvacAirRecirculation -> setOf(resolved(MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION))
            MbCanSignal.HvacDefrosterFront -> setOf(resolved(MbCanKnownVehiclePropertyId.HVAC_DEFROSTER_FRONT))
            MbCanSignal.AudioVolume -> setOf(resolved(MbCanKnownAudioPropertyId.VOLUME))
            MbCanSignal.AudioVolumeSpeed -> setOf(resolved(MbCanKnownAudioPropertyId.VOLUME_SPEED))
            MbCanSignal.CarSettingsVehicleParams -> setOf(
                resolved(MbCanKnownVehiclePropertyId.VEHICLE_PROPERTY_EPS_MODE),
                resolved(MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE),
                resolved(MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE_6DCT_WET)
            )
            MbCanSignal.FrontLeftSeatMode -> setOf(resolved(MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH))
            MbCanSignal.FrontRightSeatMode -> setOf(resolved(MbCanKnownVehiclePropertyId.FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH))
            MbCanSignal.RearLeftSeatMode -> setOf(resolved(MbCanKnownVehiclePropertyId.REAR_LEFT_SEAT_HEAT_SWITCH))
            MbCanSignal.RearRightSeatMode -> setOf(resolved(MbCanKnownVehiclePropertyId.REAR_RIGHT_SEAT_HEAT_SWITCH))
            MbCanSignal.WirelessChargingSwitch -> emptySet()
        }
    }

    private fun applyPushPropertyUpdate(propertyId: Int, raw: Int) {
        fun resolved(id: Int): Int = FirmwareVehicleJsonMapper.resolveReadPropertyId(id) ?: id
        when (propertyId) {
            resolved(MbCanKnownVehiclePropertyId.STEERING_WHEEL_HEAT_SWITCH) ->
                stateEngine.applySteeringCandidate(MbCanSignalStateEngine.decodeSteeringWheelHeatRaw(raw))
            resolved(MbCanKnownVehiclePropertyId.FRONT_WINDSCREEN_HEAT_SWITCH) ->
                stateEngine.applyWindshieldHeatCandidate(MbCanSignalStateEngine.decodeFrontWindscreenHeatRaw(raw))
            resolved(MbCanKnownVehiclePropertyId.HVAC_DEFROSTER_SWITCH) ->
                stateEngine.applyHvacDefrosterCandidate(MbCanSignalStateEngine.decodeHvacDefrosterRaw(raw))
            resolved(MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION) ->
                stateEngine.applyHvacAirRecirculationCandidate(MbCanSignalStateEngine.decodeHvacAirRecirculationRaw(raw))
            resolved(MbCanKnownVehiclePropertyId.HVAC_DEFROSTER_FRONT) ->
                stateEngine.applyHvacDefrosterFrontCandidate(MbCanSignalStateEngine.decodeHvacDefrosterFrontRaw(raw))
            resolved(MbCanKnownAudioPropertyId.VOLUME) -> {
                _audioVolumeState.value = raw.coerceAtLeast(0)
                if (raw > 0) _audioVolumeLastNonZeroInSession.value = raw
            }
            resolved(MbCanKnownAudioPropertyId.VOLUME_SPEED) ->
                stateEngine.applyVolumeSpeedCandidate(MbCanSignalStateEngine.decodeVolumeSpeedRaw(raw))
            resolved(MbCanKnownVehiclePropertyId.VEHICLE_PROPERTY_EPS_MODE) ->
                _carSettingsEpsMode.value = raw
            resolved(MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE) ->
                _carSettingsDriveMode.value = raw
            resolved(MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE_6DCT_WET) ->
                _carSettingsDriveMode6dctWet.value = raw
            resolved(MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH) ->
                stateEngine.applySeatCandidate(MbCanSeatSlot.FrontLeft, MbCanSignalStateEngine.decodeSeatModeRaw(raw))
            resolved(MbCanKnownVehiclePropertyId.FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH) ->
                stateEngine.applySeatCandidate(MbCanSeatSlot.FrontRight, MbCanSignalStateEngine.decodeSeatModeRaw(raw))
            resolved(MbCanKnownVehiclePropertyId.REAR_LEFT_SEAT_HEAT_SWITCH) ->
                stateEngine.applySeatCandidate(MbCanSeatSlot.RearLeft, MbCanSignalStateEngine.decodeRearSeatHeatRaw(raw))
            resolved(MbCanKnownVehiclePropertyId.REAR_RIGHT_SEAT_HEAT_SWITCH) ->
                stateEngine.applySeatCandidate(MbCanSeatSlot.RearRight, MbCanSignalStateEngine.decodeRearSeatHeatRaw(raw))
        }
    }

    private suspend fun syncPushListeners(interestedSignals: Set<MbCanSignal>) {
        val localBridge = bridge ?: return
        val interestedPropertyIds = interestedSignals.flatMapTo(mutableSetOf()) { signalReadPropertyIds(it) }
        localBridge.setPushCallbacks(
            onChange = { propertyId, areaId, value ->
                scope.launch {
                    logInfo("VHAL push onChange propertyId=$propertyId areaId=$areaId value=$value")
                    applyPushPropertyUpdate(propertyId, value)
                }
            },
            onError = { propertyId, areaId ->
                logWarn("VHAL push onError propertyId=$propertyId areaId=$areaId")
            }
        )
        localBridge.syncPushSubscriptions(interestedPropertyIds)
    }

    suspend fun refreshSignal(signal: MbCanSignal) {
        permissionDeniedReasonOrNull()?.let { deniedReason ->
            when (signal) {
                MbCanSignal.SteeringWheelHeat -> stateEngine.applySteeringCandidate(MbCanBinaryState.Unavailable(deniedReason))
                MbCanSignal.FrontWindscreenHeat -> stateEngine.applyWindshieldHeatCandidate(MbCanBinaryState.Unavailable(deniedReason))
                MbCanSignal.HvacDefroster -> stateEngine.applyHvacDefrosterCandidate(MbCanBinaryState.Unavailable(deniedReason))
                MbCanSignal.HvacAirRecirculation -> stateEngine.applyHvacAirRecirculationCandidate(MbCanBinaryState.Unavailable(deniedReason))
                MbCanSignal.HvacDefrosterFront -> stateEngine.applyHvacDefrosterFrontCandidate(MbCanBinaryState.Unavailable(deniedReason))
                MbCanSignal.AudioVolumeSpeed -> stateEngine.applyVolumeSpeedCandidate(MbCanBinaryState.Unavailable(deniedReason))
                MbCanSignal.FrontLeftSeatMode ->
                    stateEngine.applySeatCandidate(MbCanSeatSlot.FrontLeft, MbCanSeatModeState.Unavailable(deniedReason))
                MbCanSignal.FrontRightSeatMode ->
                    stateEngine.applySeatCandidate(MbCanSeatSlot.FrontRight, MbCanSeatModeState.Unavailable(deniedReason))
                MbCanSignal.RearLeftSeatMode ->
                    stateEngine.applySeatCandidate(MbCanSeatSlot.RearLeft, MbCanSeatModeState.Unavailable(deniedReason))
                MbCanSignal.RearRightSeatMode ->
                    stateEngine.applySeatCandidate(MbCanSeatSlot.RearRight, MbCanSeatModeState.Unavailable(deniedReason))
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
        permissionDeniedReasonOrNull()?.let { deniedReason ->
            return MbCanCommandResult(false, deniedReason)
        }
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
                logInfo("ToggleProperty request=${command.propertyId} effective=$effectivePropertyId")
                val current = bridge?.getIntProperty(effectivePropertyId)
                    ?: return MbCanCommandResult(false, "Property read failed")
                val target = when (current) {
                    policy.onValue -> policy.offValue
                    policy.offValue -> policy.onValue
                    else -> policy.unknownFallbackValue
                }
                val ok = bridge?.setIntProperty(effectivePropertyId, target) == true
                logInfo("ToggleProperty result=$ok propertyId=$effectivePropertyId current=$current target=$target")
                if (ok) requestBurstPolling()
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
                logInfo(
                    "SetProperty request=${command.propertyId} effective=$effectivePropertyId " +
                        "value=${command.value}"
                )
                val ok = bridge?.setIntProperty(effectivePropertyId, command.value) == true
                logInfo("SetProperty result=$ok propertyId=$effectivePropertyId value=${command.value}")
                if (ok) requestBurstPolling()
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
                logInfo("ToggleAudioProperty request=${command.propertyId} effective=$effectivePropertyId")
                val current = bridge?.getIntProperty(effectivePropertyId)
                    ?: return MbCanCommandResult(false, "Audio property read failed")
                val target = when (current) {
                    policy.onValue -> policy.offValue
                    policy.offValue -> policy.onValue
                    else -> policy.unknownFallbackValue
                }
                val ok = bridge?.setIntProperty(effectivePropertyId, target) == true
                logInfo("ToggleAudioProperty result=$ok propertyId=$effectivePropertyId current=$current target=$target")
                if (ok) requestBurstPolling()
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
                logInfo(
                    "SetAudioProperty request=${command.propertyId} effective=$effectivePropertyId " +
                        "value=${command.value}"
                )
                val ok = bridge?.setIntProperty(effectivePropertyId, command.value) == true
                logInfo("SetAudioProperty result=$ok propertyId=$effectivePropertyId value=${command.value}")
                if (ok) requestBurstPolling()
                spec.refreshSignal?.let { refreshSignal(it) }
                MbCanCommandResult(ok, if (ok) "Set ok" else "Set failed")
            }
            is MbCanCommand.RefreshSignal -> {
                logInfo("RefreshSignal ${command.signal}")
                refreshSignal(command.signal)
                MbCanCommandResult(true, "Refresh requested")
            }
        }
    }

    suspend fun setAudioVolume(value: Int): MbCanCommandResult {
        permissionDeniedReasonOrNull()?.let { deniedReason ->
            return MbCanCommandResult(false, deniedReason)
        }
        val connection = ensureConnected()
        if (connection !is MbCanAvailability.Available) {
            return MbCanCommandResult(false, currentUnavailableReason())
        }
        val target = value.coerceAtLeast(0)
        val effectiveVolumeId = FirmwareVehicleJsonMapper.resolveWritePropertyId(MbCanKnownAudioPropertyId.VOLUME)
            ?: MbCanKnownAudioPropertyId.VOLUME
        logInfo("setAudioVolume request=$value effectivePropertyId=$effectiveVolumeId")
        val ok = bridge?.setIntProperty(effectiveVolumeId, target) == true
        logInfo("setAudioVolume result=$ok value=$target propertyId=$effectiveVolumeId")
        if (ok) {
            requestBurstPolling()
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
