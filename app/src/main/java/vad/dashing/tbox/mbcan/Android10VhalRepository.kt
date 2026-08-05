package vad.dashing.tbox.mbcan

import android.content.Context
import android.content.ServiceConnection
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.lang.reflect.Proxy
import vad.dashing.tbox.AppContextHolder
import vad.dashing.tbox.ACC_CRUISE_WIDGET_DATA_KEY
import vad.dashing.tbox.CRUISE_STATUS_WIDGET_DATA_KEY
import vad.dashing.tbox.DRIVE_MODE_WIDGET_DATA_KEY
import vad.dashing.tbox.DRIVE_MODE_CYCLE_WIDGET_DATA_KEY
import vad.dashing.tbox.HVAC_BLOW_MODE_CYCLE_WIDGET_DATA_KEY
import vad.dashing.tbox.HVAC_BLOW_MODE_PANEL_WIDGET_HORIZONTAL_DATA_KEY
import vad.dashing.tbox.HVAC_BLOW_MODE_PANEL_WIDGET_VERTICAL_DATA_KEY
import vad.dashing.tbox.HVAC_CLIMATE_WIDGET_DATA_KEYS
import vad.dashing.tbox.HVAC_FAN_WIDGET_HORIZONTAL_DATA_KEY
import vad.dashing.tbox.HVAC_FAN_WIDGET_VERTICAL_DATA_KEY
import vad.dashing.tbox.HVAC_SYNC_WIDGET_DATA_KEY
import vad.dashing.tbox.HVAC_TEMP_LEFT_WIDGET_HORIZONTAL_DATA_KEY
import vad.dashing.tbox.HVAC_TEMP_LEFT_WIDGET_VERTICAL_DATA_KEY
import vad.dashing.tbox.HVAC_TEMP_RIGHT_WIDGET_HORIZONTAL_DATA_KEY
import vad.dashing.tbox.HVAC_TEMP_RIGHT_WIDGET_VERTICAL_DATA_KEY
import vad.dashing.tbox.TRUNK_DOOR_WIDGET_DATA_KEY
import vad.dashing.tbox.FRONT_LEFT_SEAT_HEAT_VENT_SINGLE_WIDGET_DATA_KEY
import vad.dashing.tbox.FRONT_RIGHT_SEAT_HEAT_VENT_SINGLE_WIDGET_DATA_KEY
import vad.dashing.tbox.AVH_WIDGET_DATA_KEY
import vad.dashing.tbox.ESP_OFF_WIDGET_DATA_KEY
import vad.dashing.tbox.HEADLIGHT_MODE_CYCLE_WIDGET_DATA_KEY
import vad.dashing.tbox.LDW_WIDGET_DATA_KEY
import vad.dashing.tbox.LKA_WIDGET_DATA_KEY
import vad.dashing.tbox.TJA_ICA_WIDGET_DATA_KEY
import vad.dashing.tbox.HMA_WIDGET_DATA_KEY
import vad.dashing.tbox.HVAC_AC_MAX_WIDGET_DATA_KEY
import vad.dashing.tbox.HVAC_CUSTOM_MODE_CYCLE_WIDGET_DATA_KEY
import vad.dashing.tbox.HDC_WIDGET_DATA_KEY
import vad.dashing.tbox.PARKING_RADAR_WIDGET_DATA_KEY
import vad.dashing.tbox.REAR_FOG_WIDGET_DATA_KEY
import vad.dashing.tbox.REAR_LEFT_SEAT_HEAT_WIDGET_DATA_KEY
import vad.dashing.tbox.REAR_RIGHT_SEAT_HEAT_WIDGET_DATA_KEY
import vad.dashing.tbox.SLA_SPEED_LIMIT_WIDGET_DATA_KEY
import vad.dashing.tbox.SPEED_LIMITER_WIDGET_DATA_KEY
import vad.dashing.tbox.WIPER_MAINTENANCE_WIDGET_DATA_KEY
import vad.dashing.tbox.Wheels

private class CarPropertyBridge(private val context: Context) {
    private var car: Any? = null
    private var propertyManager: Any? = null
    private var pushListener: Any? = null
    private val registeredPushPropertyIds = mutableSetOf<Int>()
    @Volatile
    private var onPushPropertyChanged: ((propertyId: Int, areaId: Int, value: Any?) -> Unit)? = null
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
        // Matches confirmed working path from latest production logs and stock apps.
        // Keep only Car.createCar(Context, ServiceConnection).
        val method2 = runCatching {
            carClass.getMethod("createCar", Context::class.java, ServiceConnection::class.java)
        }.getOrNull()
        if (method2 != null) {
            Android10VhalRepository.logInfo("Using Car.createCar(Context, ServiceConnection)")
            return method2.invoke(null, context, serviceConnection)
        }
        Android10VhalRepository.logError("Missing Car.createCar(Context, ServiceConnection)")
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
        if (Android10VhalRepository.isPropertyPermissionDenied(propertyId)) return null
        val manager = propertyManager ?: return null
        return runCatching {
            manager.javaClass
                .getMethod("getIntProperty", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .invoke(manager, propertyId, areaId) as Int
        }.onFailure {
            Android10VhalRepository.logReadFailure(propertyId, areaId, it)
        }.getOrNull()
    }

    fun getFloatProperty(propertyId: Int, areaId: Int = 0): Float? {
        val manager = propertyManager ?: return null
        return runCatching {
            manager.javaClass
                .getMethod("getFloatProperty", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .invoke(manager, propertyId, areaId) as Float
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
        onChange: (propertyId: Int, areaId: Int, value: Any?) -> Unit,
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
                Android10VhalRepository.logDebug("VHAL push unregistered propertyId=$propertyId")
            }.onFailure {
                Android10VhalRepository.logWarn(
                    "VHAL push unregister failed propertyId=$propertyId " +
                        "error=${it.javaClass.simpleName}: ${it.message}"
                )
            }
        }
        toAdd.forEach { propertyId ->
            runCatching {
                Android10VhalRepository.logPropertyConfigOnce(manager, propertyId)
                val candidateRates = linkedSetOf(
                    Android10VhalRepository.pushRateForPropertyId(propertyId),
                    0.0f,
                    1.0f,
                    5.0f
                )
                var registered = false
                var lastError: Throwable? = null
                for (rateHz in candidateRates) {
                    val attempt = runCatching {
                        manager.javaClass
                            .getMethod(
                                "registerListener",
                                listener.javaClass.interfaces.first(),
                                Int::class.javaPrimitiveType,
                                Float::class.javaPrimitiveType
                            )
                            .invoke(manager, listener, propertyId, rateHz)
                    }
                    if (attempt.isSuccess) {
                        val accepted = (attempt.getOrNull() as? Boolean) ?: true
                        if (accepted) {
                            registeredPushPropertyIds.add(propertyId)
                            Android10VhalRepository.logDebug("VHAL push registered propertyId=$propertyId rate=$rateHz")
                            registered = true
                            break
                        } else {
                            Android10VhalRepository.logWarn("VHAL push register rejected propertyId=$propertyId rate=$rateHz")
                        }
                    } else {
                        val root = unwrapReflectionThrowable(attempt.exceptionOrNull())
                        lastError = root
                        Android10VhalRepository.logWarn(
                            "VHAL push register attempt failed propertyId=$propertyId rate=$rateHz " +
                                "error=${root.javaClass.simpleName}: ${root.message}"
                        )
                    }
                }
                if (!registered) {
                    val root = unwrapReflectionThrowable(lastError)
                    throw IllegalStateException(
                        "All registerListener attempts failed propertyId=$propertyId " +
                            "error=${root.javaClass.simpleName}: ${root.message}",
                        root
                    )
                }
            }.onFailure {
                val root = unwrapReflectionThrowable(it)
                Android10VhalRepository.logWarn(
                    "VHAL push register failed propertyId=$propertyId " +
                        "error=${root.javaClass.simpleName}: ${root.message}"
                )
            }
        }
    }

    private fun unwrapReflectionThrowable(throwable: Throwable?): Throwable {
        var current = throwable ?: return IllegalStateException("Unknown reflection error")
        while (current is java.lang.reflect.InvocationTargetException && current.targetException != null) {
            current = current.targetException
        }
        return current
    }

    private fun ensurePushListener(): Any {
        pushListener?.let { return it }
        val listenerInterface = Class.forName("android.car.hardware.property.CarPropertyManager\$CarPropertyEventListener")
        val proxy = Proxy.newProxyInstance(
            listenerInterface.classLoader,
            arrayOf(listenerInterface)
        ) { proxyObj, method, args ->
            when {
                method.declaringClass == Any::class.java && method.name == "hashCode" ->
                    System.identityHashCode(proxyObj)
                method.declaringClass == Any::class.java && method.name == "equals" ->
                    (proxyObj === args?.getOrNull(0))
                method.declaringClass == Any::class.java && method.name == "toString" ->
                    "CarPropertyEventListenerProxy@" + Integer.toHexString(System.identityHashCode(proxyObj))
                method.name == "onChangeEvent" -> {
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
                    onPushPropertyChanged?.invoke(propertyId, areaId, value)
                    null
                }
                method.name == "onErrorEvent" -> {
                    val propertyId = (args?.getOrNull(0) as? Number)?.toInt()
                        ?: return@newProxyInstance null
                    val areaId = (args.getOrNull(1) as? Number)?.toInt() ?: 0
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
    private val VHAL_ENGINE_RPM_PROPERTY_ID = FirmwareVehicleJsonMapper.VHAL_ENGINE_RPM_PROPERTY_ID
    private val VHAL_ENGINE_TEMPERATURE_PROPERTY_ID = FirmwareVehicleJsonMapper.VHAL_ENGINE_TEMPERATURE_PROPERTY_ID
    private val VHAL_CAR_SPEED_VSO_SIG_PROPERTY_ID =
        FirmwareVehicleJsonMapper.VHAL_CAR_SPEED_VSO_SIG_PROPERTY_ID
    private val VHAL_CAR_SPEED_DISPLAY_PROPERTY_ID =
        FirmwareVehicleJsonMapper.VHAL_CAR_SPEED_DISPLAY_PROPERTY_ID
    private val VHAL_GEAR_SELECTION_PROPERTY_ID = FirmwareVehicleJsonMapper.VHAL_GEAR_SELECTION_PROPERTY_ID
    private val VHAL_CURRENT_GEAR_PROPERTY_ID = FirmwareVehicleJsonMapper.VHAL_CURRENT_GEAR_PROPERTY_ID
    private val VHAL_REVERSE_GEAR_SWITCH_PROPERTY_ID =
        FirmwareVehicleJsonMapper.VHAL_REVERSE_GEAR_SWITCH_PROPERTY_ID
    private val VHAL_FUEL_LEVEL_PROPERTY_ID = FirmwareVehicleJsonMapper.VHAL_FUEL_LEVEL_PROPERTY_ID
    private val VHAL_TOTAL_ODOMETER_KM_PROPERTY_ID = FirmwareVehicleJsonMapper.VHAL_TOTAL_ODOMETER_KM_PROPERTY_ID
    private val VHAL_EXTERNAL_TEMPERATURE_RAW_PROPERTY_ID =
        FirmwareVehicleJsonMapper.VHAL_EXTERNAL_TEMPERATURE_RAW_PROPERTY_ID
    private val VHAL_FUEL_ROLLING_COUNTER_PROPERTY_ID =
        FirmwareVehicleJsonMapper.VHAL_FUEL_ROLLING_COUNTER_PROPERTY_ID
    private val VHAL_MAINTENANCE_TIPS_PROPERTY_ID = FirmwareVehicleJsonMapper.VHAL_MAINTENANCE_TIPS_PROPERTY_ID
    private val VHAL_DISTANCE_TO_EMPTY_KM_PROPERTY_ID =
        FirmwareVehicleJsonMapper.VHAL_DISTANCE_TO_EMPTY_KM_PROPERTY_ID
    private val VHAL_PM25_INDENSITY_PROPERTY_ID = FirmwareVehicleJsonMapper.VHAL_PM25_INDENSITY_PROPERTY_ID
    private val VHAL_PM25_OUTDENSITY_PROPERTY_ID = FirmwareVehicleJsonMapper.VHAL_PM25_OUTDENSITY_PROPERTY_ID
    private val VHAL_LF_TYRE_PRESSURE = FirmwareVehicleJsonMapper.VHAL_LF_TYRE_PRESSURE
    private val VHAL_RF_TYRE_PRESSURE = FirmwareVehicleJsonMapper.VHAL_RF_TYRE_PRESSURE
    private val VHAL_LR_TYRE_PRESSURE = FirmwareVehicleJsonMapper.VHAL_LR_TYRE_PRESSURE
    private val VHAL_RR_TYRE_PRESSURE = FirmwareVehicleJsonMapper.VHAL_RR_TYRE_PRESSURE
    private val VHAL_LF_TYRE_TEMPERATURE = FirmwareVehicleJsonMapper.VHAL_LF_TYRE_TEMPERATURE
    private val VHAL_RF_TYRE_TEMPERATURE = FirmwareVehicleJsonMapper.VHAL_RF_TYRE_TEMPERATURE
    private val VHAL_LR_TYRE_TEMPERATURE = FirmwareVehicleJsonMapper.VHAL_LR_TYRE_TEMPERATURE
    private val VHAL_RR_TYRE_TEMPERATURE = FirmwareVehicleJsonMapper.VHAL_RR_TYRE_TEMPERATURE
    private const val VHAL_ENGINE_RPM_SCALE = 4f
    private const val VHAL_ENGINE_TEMPERATURE_SCALE = 0.75f
    private const val VHAL_ENGINE_TEMPERATURE_OFFSET = -48f
    private const val NORMAL_POLL_INTERVAL_MS = 30_000L
    private const val BURST_POLL_INTERVAL_MS = 1_500L
    private const val BURST_DURATION_MS = 15_000L
    private const val LOG_TAG = "VHAL_A10"
    private const val CAR_INFO_PERMISSION = "android.car.permission.CAR_INFO"
    private const val CAR_ENGINE_DETAILED_PERMISSION = "android.car.permission.CAR_ENGINE_DETAILED"
    private const val PUSH_RATE_ON_CHANGE = 0.0f
    private const val PUSH_RATE_CONTINUOUS = 1.0f
    private const val CLEAR_SOURCE_PUSH_DEBOUNCE_MS = 3 * 60_000L
    private const val PUSH_STATE_COALESCE_MS = 200L
    private const val PUSH_DEBUG_LOG_COALESCE_MS = 1_000L
    private val carSettingsZeroToSixRange = 0..6
    private val loggedPropertyConfigs = mutableSetOf<Int>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val debouncedClearSourceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val pendingDebouncedClearJobs = java.util.concurrent.ConcurrentHashMap<String, Job>()
    private val pushCoalesceHandler = Handler(Looper.getMainLooper())
    private val pendingPushStateLock = Any()
    private val pendingPushState = mutableMapOf<Int, Any?>()
    private var pushStateFlushScheduled = false
    private val flushPushStateRunnable = Runnable { flushPendingPushState() }
    private val pendingPushDebugLock = Any()
    private val pendingPushDebug = mutableMapOf<Int, PushDebugBucket>()
    private var pushDebugFlushScheduled = false
    private val flushPushDebugRunnable = Runnable { flushPendingPushDebug() }
    private val sourceSignals = mutableMapOf<String, Set<MbCanSignal>>()
    private val sourceMutex = Mutex()
    private var pollJob: Job? = null
    private var bridge: CarPropertyBridge? = null
    /** Serializes connect/unbind so parallel bind/execute cannot orphan Car sessions. */
    private val carConnectMutex = Mutex()
    @Volatile
    private var burstUntilMs: Long = 0L
    private val readErrorsLogged = mutableSetOf<String>()
    private val writeErrorsLogged = mutableSetOf<String>()
    private var lastAvailabilityReason: String? = null
    private val permissionDenialTracker = VhalPermissionDenialTracker()

    private val _availability = MutableStateFlow<MbCanAvailability>(MbCanAvailability.Unknown)
    val availability: StateFlow<MbCanAvailability> = _availability.asStateFlow()

    private val _steeringWheelHeatState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val steeringWheelHeatState: StateFlow<MbCanBinaryState> = _steeringWheelHeatState.asStateFlow()
    private val _wiperMaintenanceState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val wiperMaintenanceState: StateFlow<MbCanBinaryState> = _wiperMaintenanceState.asStateFlow()
    private val _parkingRadarState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val parkingRadarState: StateFlow<MbCanBinaryState> = _parkingRadarState.asStateFlow()
    private val _rearFogState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val rearFogState: StateFlow<MbCanBinaryState> = _rearFogState.asStateFlow()
    private val _autoLockState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val autoLockState: StateFlow<MbCanBinaryState> = _autoLockState.asStateFlow()
    private val _autoUnlockState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val autoUnlockState: StateFlow<MbCanBinaryState> = _autoUnlockState.asStateFlow()
    private val _rearWiperState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val rearWiperState: StateFlow<MbCanBinaryState> = _rearWiperState.asStateFlow()
    private val _followMeHomeMode = MutableStateFlow<FollowMeHomeMode?>(null)
    val followMeHomeMode: StateFlow<FollowMeHomeMode?> = _followMeHomeMode.asStateFlow()
    private val _driverUnlockMode = MutableStateFlow<Int?>(null)
    val driverUnlockMode: StateFlow<Int?> = _driverUnlockMode.asStateFlow()
    private val _remoteLockFeedback = MutableStateFlow<Int?>(null)
    val remoteLockFeedback: StateFlow<Int?> = _remoteLockFeedback.asStateFlow()
    private val _wiperSensitivity = MutableStateFlow<Int?>(null)
    val wiperSensitivity: StateFlow<Int?> = _wiperSensitivity.asStateFlow()
    private val _lowBeamHeight = MutableStateFlow<Int?>(null)
    val lowBeamHeight: StateFlow<Int?> = _lowBeamHeight.asStateFlow()
    private val _turnFlashCount = MutableStateFlow<Int?>(null)
    val turnFlashCount: StateFlow<Int?> = _turnFlashCount.asStateFlow()
    private val _avhState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val avhState: StateFlow<MbCanBinaryState> = _avhState.asStateFlow()
    private val _hdcState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val hdcState: StateFlow<MbCanBinaryState> = _hdcState.asStateFlow()
    private val _espOffState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val espOffState: StateFlow<MbCanBinaryState> = _espOffState.asStateFlow()
    private val _lasModeRaw = MutableStateFlow<Int?>(null)
    val lasModeRaw: StateFlow<Int?> = _lasModeRaw.asStateFlow()
    private val _headlightModeRaw = MutableStateFlow<Int?>(null)
    val headlightModeRaw: StateFlow<Int?> = _headlightModeRaw.asStateFlow()
    private val _tjaIcaState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val tjaIcaState: StateFlow<MbCanBinaryState> = _tjaIcaState.asStateFlow()
    private val _hmaState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val hmaState: StateFlow<MbCanBinaryState> = _hmaState.asStateFlow()
    private val _bsdState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val bsdState: StateFlow<MbCanBinaryState> = _bsdState.asStateFlow()
    private val _dowState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val dowState: StateFlow<MbCanBinaryState> = _dowState.asStateFlow()
    private val _fcwState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val fcwState: StateFlow<MbCanBinaryState> = _fcwState.asStateFlow()
    private val _fcwSensitivity = MutableStateFlow<FcwSensitivity?>(null)
    val fcwSensitivity: StateFlow<FcwSensitivity?> = _fcwSensitivity.asStateFlow()
    private val _ldwSensitivity = MutableStateFlow<LdwSensitivity?>(null)
    val ldwSensitivity: StateFlow<LdwSensitivity?> = _ldwSensitivity.asStateFlow()
    private val _hvacAcMaxState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val hvacAcMaxState: StateFlow<MbCanBinaryState> = _hvacAcMaxState.asStateFlow()
    private val _frontWindscreenHeatState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val frontWindscreenHeatState: StateFlow<MbCanBinaryState> = _frontWindscreenHeatState.asStateFlow()
    private val _hvacDefrosterState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val hvacDefrosterState: StateFlow<MbCanBinaryState> = _hvacDefrosterState.asStateFlow()
    private val _hvacAirRecirculationState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val hvacAirRecirculationState: StateFlow<MbCanBinaryState> = _hvacAirRecirculationState.asStateFlow()
    private val _hvacAcPowerState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val hvacAcPowerState: StateFlow<MbCanBinaryState> = _hvacAcPowerState.asStateFlow()
    private val _hvacAcCleanWhenLockedState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val hvacAcCleanWhenLockedState: StateFlow<MbCanBinaryState> = _hvacAcCleanWhenLockedState.asStateFlow()
    private val _hvacAutoState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val hvacAutoState: StateFlow<MbCanBinaryState> = _hvacAutoState.asStateFlow()
    private val _firstBlowingState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val firstBlowingState: StateFlow<MbCanBinaryState> = _firstBlowingState.asStateFlow()
    private val _btReduceFanState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val btReduceFanState: StateFlow<MbCanBinaryState> = _btReduceFanState.asStateFlow()
    private val _autoVentilationState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val autoVentilationState: StateFlow<MbCanBinaryState> = _autoVentilationState.asStateFlow()
    private val _hvacDefrosterFrontState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val hvacDefrosterFrontState: StateFlow<MbCanBinaryState> = _hvacDefrosterFrontState.asStateFlow()
    private val _audioVolumeSpeedState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val audioVolumeSpeedState: StateFlow<MbCanBinaryState> = _audioVolumeSpeedState.asStateFlow()
    private val _audioVolumeSpeedModeState = MutableStateFlow<Int?>(null)
    val audioVolumeSpeedModeState: StateFlow<Int?> = _audioVolumeSpeedModeState.asStateFlow()
    private val _audioVolumeState = MutableStateFlow<Int?>(null)
    val audioVolumeState: StateFlow<Int?> = _audioVolumeState.asStateFlow()
    private val _audioVolumeLastNonZeroInSession = MutableStateFlow<Int?>(null)
    private val _engineRpmState = MutableStateFlow<Float?>(null)
    val engineRpmState: StateFlow<Float?> = _engineRpmState.asStateFlow()
    private val _engineTemperatureState = MutableStateFlow<Float?>(null)
    val engineTemperatureState: StateFlow<Float?> = _engineTemperatureState.asStateFlow()
    private val _carSpeedState = MutableStateFlow<Float?>(null)
    val carSpeedState: StateFlow<Float?> = _carSpeedState.asStateFlow()
    /** Last VHAL raw for VSOSig / Display (dual-source); null = not yet read / cleared. */
    @Volatile
    private var lastVsoSpeedRaw: Float? = null
    @Volatile
    private var lastDisplaySpeedRaw: Float? = null
    private val _gearBoxModeState = MutableStateFlow<String?>(null)
    val gearBoxModeState: StateFlow<String?> = _gearBoxModeState.asStateFlow()
    private val _reverseGearSwitchState = MutableStateFlow<Boolean?>(null)
    val reverseGearSwitchState: StateFlow<Boolean?> = _reverseGearSwitchState.asStateFlow()
    private val _fuelLevelPercentState = MutableStateFlow<UInt?>(null)
    val fuelLevelPercentState: StateFlow<UInt?> = _fuelLevelPercentState.asStateFlow()
    private val _odometerKmState = MutableStateFlow<UInt?>(null)
    val odometerKmState: StateFlow<UInt?> = _odometerKmState.asStateFlow()
    private val _outsideTemperatureState = MutableStateFlow<Float?>(null)
    val outsideTemperatureState: StateFlow<Float?> = _outsideTemperatureState.asStateFlow()
    private val _wheelsPressureState = MutableStateFlow(Wheels())
    val wheelsPressureState: StateFlow<Wheels> = _wheelsPressureState.asStateFlow()
    private val _wheelsTemperatureState = MutableStateFlow(Wheels())
    val wheelsTemperatureState: StateFlow<Wheels> = _wheelsTemperatureState.asStateFlow()
    private val _currentFuelConsumptionState = MutableStateFlow<Float?>(null)
    val currentFuelConsumptionState: StateFlow<Float?> = _currentFuelConsumptionState.asStateFlow()
    private val _distanceToNextMaintenanceKmState = MutableStateFlow<UInt?>(null)
    val distanceToNextMaintenanceKmState: StateFlow<UInt?> = _distanceToNextMaintenanceKmState.asStateFlow()
    private val _distanceToFuelEmptyKmState = MutableStateFlow<UInt?>(null)
    val distanceToFuelEmptyKmState: StateFlow<UInt?> = _distanceToFuelEmptyKmState.asStateFlow()
    private val _insideAirQualityState = MutableStateFlow<UInt?>(null)
    val insideAirQualityState: StateFlow<UInt?> = _insideAirQualityState.asStateFlow()
    private val _outsideAirQualityState = MutableStateFlow<UInt?>(null)
    val outsideAirQualityState: StateFlow<UInt?> = _outsideAirQualityState.asStateFlow()
    private val _steerAngleState = MutableStateFlow<Float?>(null)
    val steerAngleState: StateFlow<Float?> = _steerAngleState.asStateFlow()
    private val _steerSpeedState = MutableStateFlow<Float?>(null)
    val steerSpeedState: StateFlow<Float?> = _steerSpeedState.asStateFlow()

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
    private val _hudSwitchState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val hudSwitchState: StateFlow<MbCanBinaryState> = _hudSwitchState.asStateFlow()
    private val _hudHeight = MutableStateFlow<Int?>(null)
    val hudHeight: StateFlow<Int?> = _hudHeight.asStateFlow()
    private val _hudBrightness = MutableStateFlow<Int?>(null)
    val hudBrightness: StateFlow<Int?> = _hudBrightness.asStateFlow()
    private val _hudDisplayMode = MutableStateFlow<Int?>(null)
    val hudDisplayMode: StateFlow<Int?> = _hudDisplayMode.asStateFlow()
    private val _hudAutoBrightnessState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val hudAutoBrightnessState: StateFlow<MbCanBinaryState> = _hudAutoBrightnessState.asStateFlow()
    private val _overspeedAlarmKmh = MutableStateFlow<Int?>(null)
    val overspeedAlarmKmh: StateFlow<Int?> = _overspeedAlarmKmh.asStateFlow()
    private val _slaRecognizedSpeedLimitKmh = MutableStateFlow<Int?>(null)
    val slaRecognizedSpeedLimitKmh: StateFlow<Int?> = _slaRecognizedSpeedLimitKmh.asStateFlow()
    private val _slaOnOffState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val slaOnOffState: StateFlow<MbCanBinaryState> = _slaOnOffState.asStateFlow()
    private var slaLkaOnOffRaw: Int? = null
    private var slaLkaStateRaw: Int? = null
    private var slaLkaLimitRaw: Int? = null
    private val _slaSignUiState = MutableStateFlow<SlaSignUiState>(SlaSignUiState.Inactive)
    val slaSignUiState: StateFlow<SlaSignUiState> = _slaSignUiState.asStateFlow()
    private val _speedLimiterState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val speedLimiterState: StateFlow<MbCanBinaryState> = _speedLimiterState.asStateFlow()
    private val _accCruiseMode = MutableStateFlow<Int?>(null)
    val accCruiseMode: StateFlow<Int?> = _accCruiseMode.asStateFlow()
    private val _accCruiseVSetDisKmh = MutableStateFlow<Int?>(null)
    val accCruiseVSetDisKmh: StateFlow<Int?> = _accCruiseVSetDisKmh.asStateFlow()
    private val _accFrmFeedbackAvailable = MutableStateFlow(false)
    val accFrmFeedbackAvailable: StateFlow<Boolean> = _accFrmFeedbackAvailable.asStateFlow()
    /** Conventional CCS: EMS CruiseControlStatus (2-bit). Engaged ∈ {1,2} like A9 Gasped. */
    private val _ccsCruiseStatus = MutableStateFlow<Int?>(null)
    val ccsCruiseStatus: StateFlow<Int?> = _ccsCruiseStatus.asStateFlow()

    private val stateEngine = MbCanSignalStateEngine(
        steeringFlow = _steeringWheelHeatState,
        wiperMaintenanceFlow = _wiperMaintenanceState,
        parkingRadarFlow = _parkingRadarState,
        rearFogFlow = _rearFogState,
        avhFlow = _avhState,
        hdcFlow = _hdcState,
        espOffFlow = _espOffState,
        tjaIcaFlow = _tjaIcaState,
        hmaFlow = _hmaState,
        hvacAcMaxFlow = _hvacAcMaxState,
        windshieldHeatFlow = _frontWindscreenHeatState,
        hvacDefrosterFlow = _hvacDefrosterState,
        hvacAirRecirculationFlow = _hvacAirRecirculationState,
        hvacAcPowerFlow = _hvacAcPowerState,
        hvacAcCleanWhenLockedFlow = _hvacAcCleanWhenLockedState,
        hvacAutoStateFlow = _hvacAutoState,
        hvacDefrosterFrontFlow = _hvacDefrosterFrontState,
        wirelessChargingFlow = MutableStateFlow(MbCanBinaryState.Unknown),
        volumeSpeedFlow = _audioVolumeSpeedState,
        frontLeftSeatFlow = _frontLeftSeatModeState,
        frontRightSeatFlow = _frontRightSeatModeState,
        rearLeftSeatFlow = _rearLeftSeatModeState,
        rearRightSeatFlow = _rearRightSeatModeState,
        onBurstRequested = { requestBurstPolling() },
    )

    private data class PushDebugBucket(
        val count: Int,
        val areaId: Int,
        val value: Any?,
    )

    private fun currentUnavailableReason(): String =
        (availability.value as? MbCanAvailability.Unavailable)?.reason ?: "VHAL unavailable"

    internal fun isPropertyPermissionDenied(propertyId: Int): Boolean =
        permissionDenialTracker.isDenied(propertyId)

    private fun permissionDeniedReasonForProperty(propertyId: Int): String? {
        return if (permissionDenialTracker.isDenied(propertyId)) {
            "Missing VHAL permission for propertyId=$propertyId"
        } else {
            null
        }
    }

    private fun permissionDeniedReasonForSignal(signal: MbCanSignal): String? {
        val ids = signalReadPropertyIds(signal)
        return if (permissionDenialTracker.areAllDenied(ids)) {
            "Missing VHAL permission for signal=$signal"
        } else {
            null
        }
    }

    internal fun logInfo(message: String) {
        MbCanDiagnostics.log(level = "INFO", tag = LOG_TAG, message = message)
    }

    internal fun logDebug(message: String) {
        MbCanDiagnostics.log(level = "DEBUG", tag = LOG_TAG, message = message)
    }

    internal fun logWarn(message: String) {
        MbCanDiagnostics.log(level = "WARN", tag = LOG_TAG, message = message)
    }

    internal fun logError(message: String) {
        MbCanDiagnostics.log(level = "ERROR", tag = LOG_TAG, message = message)
    }

    internal fun pushRateForPropertyId(propertyId: Int): Float {
        // RPM/speed are continuous signals on many VHAL stacks; 0.0f can suppress callbacks there.
        return when (propertyId) {
            VHAL_ENGINE_RPM_PROPERTY_ID,
            VHAL_CAR_SPEED_VSO_SIG_PROPERTY_ID,
            VHAL_CAR_SPEED_DISPLAY_PROPERTY_ID,
            VHAL_ENGINE_TEMPERATURE_PROPERTY_ID -> PUSH_RATE_CONTINUOUS
            else -> PUSH_RATE_ON_CHANGE
        }
    }

    internal fun logPropertyConfigOnce(manager: Any, propertyId: Int) {
        synchronized(loggedPropertyConfigs) {
            if (!loggedPropertyConfigs.add(propertyId)) return
        }
        runCatching {
            val getPropertyList = manager.javaClass.methods.firstOrNull {
                it.name == "getPropertyList" && it.parameterCount == 0
            } ?: return@runCatching logWarn("VHAL property config unavailable propertyId=$propertyId (getPropertyList missing)")
            val list = getPropertyList.invoke(manager) as? Iterable<*>
                ?: return@runCatching logWarn("VHAL property config unavailable propertyId=$propertyId (empty property list)")
            val cfg = list.firstOrNull { item ->
                val id = runCatching { item?.javaClass?.getMethod("getPropertyId")?.invoke(item) as? Int }.getOrNull()
                id == propertyId
            } ?: return@runCatching logWarn("VHAL property config not found propertyId=$propertyId")

            fun invokeInt(name: String): Int? =
                runCatching { cfg.javaClass.getMethod(name).invoke(cfg) as? Int }.getOrNull()
            fun invokeFloat(name: String): Float? =
                runCatching { cfg.javaClass.getMethod(name).invoke(cfg) as? Float }.getOrNull()
            fun invokeAreaIds(): String {
                val value = runCatching { cfg.javaClass.getMethod("getAreaIds").invoke(cfg) }.getOrNull()
                return when (value) {
                    is IntArray -> value.joinToString(prefix = "[", postfix = "]")
                    is Array<*> -> value.joinToString(prefix = "[", postfix = "]")
                    else -> "[]"
                }
            }

            val changeMode = invokeInt("getChangeMode")
            val access = invokeInt("getAccess")
            val minRate = invokeFloat("getMinSampleRate")
            val maxRate = invokeFloat("getMaxSampleRate")
            val areaIds = invokeAreaIds()
            logInfo(
                "VHAL property config propertyId=$propertyId changeMode=$changeMode access=$access " +
                    "minRate=$minRate maxRate=$maxRate areaIds=$areaIds"
            )
        }.onFailure {
            logWarn("VHAL property config read failed propertyId=$propertyId error=${it.javaClass.simpleName}: ${it.message}")
        }
    }

    internal fun logReadFailure(propertyId: Int, areaId: Int, throwable: Throwable) {
        val key = "$propertyId:$areaId:${throwable.javaClass.name}:${throwable.message}"
        synchronized(readErrorsLogged) {
            if (!readErrorsLogged.add(key)) return
        }
        val root = (throwable.cause ?: throwable)
        val prefix = if (root is SecurityException) "POSSIBLE_PERMISSION" else "READ_FAILED"
        if (
            root is SecurityException &&
            (
                root.message?.contains(CAR_INFO_PERMISSION) == true ||
                    root.message?.contains(CAR_ENGINE_DETAILED_PERMISSION) == true
                )
        ) {
            permissionDenialTracker.markDenied(propertyId)
        }
        MbCanDiagnostics.log(
            level = "WARN",
            tag = LOG_TAG,
            message = "$prefix propertyId=$propertyId areaId=$areaId " +
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
        if (
            root is SecurityException &&
            (
                root.message?.contains(CAR_INFO_PERMISSION) == true ||
                    root.message?.contains(CAR_ENGINE_DETAILED_PERMISSION) == true
                )
        ) {
            permissionDenialTracker.markDenied(propertyId)
        }
        MbCanDiagnostics.log(
            level = "WARN",
            tag = LOG_TAG,
            message = "$prefix propertyId=$propertyId areaId=$areaId value=$value " +
                "error=${root.javaClass.simpleName}: ${root.message}"
        )
    }

    private suspend fun ensureConnected(): MbCanAvailability = withContext(Dispatchers.Default) {
        carConnectMutex.withLock {
            val context = AppContextHolder.appContextOrNull
                ?: return@withLock MbCanAvailability.Unavailable("No app context").also {
                    val reason = "No app context"
                    if (lastAvailabilityReason != reason) {
                        logWarn("Availability: $reason")
                        lastAvailabilityReason = reason
                    }
                }
            val existing = bridge
            if (existing != null && availability.value is MbCanAvailability.Available) {
                return@withLock availability.value
            }
            // Drop stale bridge before opening another Car session (vendor wedge risk).
            if (existing != null) {
                runCatching { existing.disconnect() }
                bridge = null
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
                runCatching { newBridge.disconnect() }
                val reason = (result as? MbCanAvailability.Unavailable)?.reason ?: "VHAL unavailable"
                if (lastAvailabilityReason != reason) {
                    logWarn("Availability: $reason")
                    lastAvailabilityReason = reason
                }
            }
            result
        }
    }

    suspend fun bind(_scope: CoroutineScope) {
        logDebug("bind()")
        permissionDenialTracker.clear()
        ensureConnected()
        restartPolling()
    }

    suspend fun unbind() {
        logDebug("unbind()")
        carConnectMutex.withLock {
            pollJob?.cancel()
            pollJob = null
            _fuelLevelPercentState.value = null
            _odometerKmState.value = null
            _outsideTemperatureState.value = null
            _wheelsPressureState.value = Wheels()
            _wheelsTemperatureState.value = Wheels()
            pushCoalesceHandler.removeCallbacks(flushPushStateRunnable)
            pushCoalesceHandler.removeCallbacks(flushPushDebugRunnable)
            synchronized(pendingPushStateLock) {
                pendingPushState.clear()
                pushStateFlushScheduled = false
            }
            synchronized(pendingPushDebugLock) {
                pendingPushDebug.clear()
                pushDebugFlushScheduled = false
            }
            bridge?.disconnect()
            bridge = null
        }
    }

    suspend fun warmUpAvailabilityForUi() {
        logDebug("warmUpAvailabilityForUi()")
        ensureConnected()
    }

    suspend fun setSourceWidgetKeys(sourceId: String, widgetKeys: Set<String>) {
        cancelDebouncedClearSource(sourceId)
        val normalizedKeys = widgetKeys.map { UniversalCanRepository.normalizeWidgetDataKey(it) }
        val signals = normalizedKeys.mapNotNull { key ->
            when (key) {
                "steeringWheelHeatWidget" -> MbCanSignal.SteeringWheelHeat
                WIPER_MAINTENANCE_WIDGET_DATA_KEY -> MbCanSignal.WiperMaintenance
                PARKING_RADAR_WIDGET_DATA_KEY -> MbCanSignal.ParkingRadar
                REAR_FOG_WIDGET_DATA_KEY -> MbCanSignal.RearFogLight
                HEADLIGHT_MODE_CYCLE_WIDGET_DATA_KEY -> MbCanSignal.LightControl
                AVH_WIDGET_DATA_KEY -> MbCanSignal.AvhSwitch
                HDC_WIDGET_DATA_KEY -> MbCanSignal.HdcSwitch
                ESP_OFF_WIDGET_DATA_KEY -> MbCanSignal.EspOffSwitch
                LDW_WIDGET_DATA_KEY -> MbCanSignal.LasModeSelection
                LKA_WIDGET_DATA_KEY -> MbCanSignal.LasModeSelection
                TJA_ICA_WIDGET_DATA_KEY -> MbCanSignal.TjaIca
                HMA_WIDGET_DATA_KEY -> MbCanSignal.HmaSwitch
                HVAC_CUSTOM_MODE_CYCLE_WIDGET_DATA_KEY -> MbCanSignal.HvacCustomMode
                HVAC_AC_MAX_WIDGET_DATA_KEY -> MbCanSignal.HvacAcMax
                "frontWindscreenHeatWidget" -> MbCanSignal.FrontWindscreenHeat
                "rearWindowMirrorsDefrostWidget" -> MbCanSignal.HvacDefroster
                "hvacAirRecirculationWidget" -> MbCanSignal.HvacAirRecirculation
                "hvacAcWidget" -> MbCanSignal.HvacAcPower
                "hvacAcCleanWhenLockedWidget" -> MbCanSignal.HvacAcCleanWhenLocked
                "hvacAutoWidget" -> MbCanSignal.HvacAutoState
                "hvacDefrosterFrontWidget" -> MbCanSignal.HvacDefrosterFront
                HVAC_SYNC_WIDGET_DATA_KEY -> MbCanSignal.HvacSync
                HVAC_FAN_WIDGET_HORIZONTAL_DATA_KEY,
                HVAC_FAN_WIDGET_VERTICAL_DATA_KEY -> MbCanSignal.HvacFanSpeed
                HVAC_TEMP_LEFT_WIDGET_HORIZONTAL_DATA_KEY,
                HVAC_TEMP_LEFT_WIDGET_VERTICAL_DATA_KEY -> MbCanSignal.HvacTempLeft
                HVAC_TEMP_RIGHT_WIDGET_HORIZONTAL_DATA_KEY,
                HVAC_TEMP_RIGHT_WIDGET_VERTICAL_DATA_KEY -> MbCanSignal.HvacTempRight
                HVAC_BLOW_MODE_CYCLE_WIDGET_DATA_KEY,
                HVAC_BLOW_MODE_PANEL_WIDGET_HORIZONTAL_DATA_KEY,
                HVAC_BLOW_MODE_PANEL_WIDGET_VERTICAL_DATA_KEY -> MbCanSignal.HvacBlowMode
                TRUNK_DOOR_WIDGET_DATA_KEY -> MbCanSignal.TrunkDoor
                DRIVE_MODE_WIDGET_DATA_KEY -> MbCanSignal.CarSettingsVehicleParams
                DRIVE_MODE_CYCLE_WIDGET_DATA_KEY -> MbCanSignal.CarSettingsVehicleParams
                SLA_SPEED_LIMIT_WIDGET_DATA_KEY -> MbCanSignal.SlaSpeedLimit
                SPEED_LIMITER_WIDGET_DATA_KEY -> MbCanSignal.SpeedLimiter
                ACC_CRUISE_WIDGET_DATA_KEY,
                CRUISE_STATUS_WIDGET_DATA_KEY,
                -> MbCanSignal.AccCruise
                "frontLeftSeatHeatVentWidget" -> MbCanSignal.FrontLeftSeatMode
                "frontRightSeatHeatVentWidget" -> MbCanSignal.FrontRightSeatMode
                FRONT_LEFT_SEAT_HEAT_VENT_SINGLE_WIDGET_DATA_KEY -> MbCanSignal.FrontLeftSeatMode
                FRONT_RIGHT_SEAT_HEAT_VENT_SINGLE_WIDGET_DATA_KEY -> MbCanSignal.FrontRightSeatMode
                REAR_LEFT_SEAT_HEAT_WIDGET_DATA_KEY -> MbCanSignal.RearLeftSeatMode
                REAR_RIGHT_SEAT_HEAT_WIDGET_DATA_KEY -> MbCanSignal.RearRightSeatMode
                else -> null
            }
        }.toMutableSet()
        // Mirror A9 cfg piggyback: climate panels also need Front OFF on VHAL (289415175).
        if (normalizedKeys.any { it in HVAC_CLIMATE_WIDGET_DATA_KEYS }) {
            signals.add(MbCanSignal.HvacFrontOff)
        }
        logDebug(
            "setSourceWidgetKeys sourceId=$sourceId widgetKeys=${widgetKeys.joinToString()} " +
                "signals=${signals.joinToString()}"
        )
        sourceMutex.withLock {
            if (signals.isEmpty()) sourceSignals.remove(sourceId) else sourceSignals[sourceId] = signals
        }
        restartPolling()
    }

    suspend fun setSourceSignals(sourceId: String, signals: Set<MbCanSignal>) {
        cancelDebouncedClearSource(sourceId)
        logDebug("setSourceSignals sourceId=$sourceId signals=${signals.joinToString()}")
        sourceMutex.withLock {
            if (signals.isEmpty()) sourceSignals.remove(sourceId) else sourceSignals[sourceId] = signals
        }
        restartPolling()
    }

    fun enqueueClearSource(sourceId: String) {
        pendingDebouncedClearJobs.remove(sourceId)?.cancel()
        val job = debouncedClearSourceScope.launch {
            delay(CLEAR_SOURCE_PUSH_DEBOUNCE_MS)
            if (pendingDebouncedClearJobs.remove(sourceId, coroutineContext.job)) {
                logDebug("enqueueClearSource sourceId=$sourceId")
                sourceMutex.withLock { sourceSignals.remove(sourceId) }
                restartPolling()
            }
        }
        pendingDebouncedClearJobs[sourceId] = job
    }

    fun widgetConfigsNeedMbCan(dataKeys: Iterable<String>): Boolean {
        return dataKeys.map(UniversalCanRepository::normalizeWidgetDataKey).any { key ->
            key in setOf(
                "steeringWheelHeatWidget",
                WIPER_MAINTENANCE_WIDGET_DATA_KEY,
                PARKING_RADAR_WIDGET_DATA_KEY,
                REAR_FOG_WIDGET_DATA_KEY,
                HEADLIGHT_MODE_CYCLE_WIDGET_DATA_KEY,
                AVH_WIDGET_DATA_KEY,
                HDC_WIDGET_DATA_KEY,
                ESP_OFF_WIDGET_DATA_KEY,
                "frontWindscreenHeatWidget",
                "rearWindowMirrorsDefrostWidget",
                "hvacAirRecirculationWidget",
                "hvacAcWidget",
                "hvacAcCleanWhenLockedWidget",
                "hvacAutoWidget",
                "hvacDefrosterFrontWidget",
                DRIVE_MODE_WIDGET_DATA_KEY,
                DRIVE_MODE_CYCLE_WIDGET_DATA_KEY,
                SLA_SPEED_LIMIT_WIDGET_DATA_KEY,
                SPEED_LIMITER_WIDGET_DATA_KEY,
                ACC_CRUISE_WIDGET_DATA_KEY,
                CRUISE_STATUS_WIDGET_DATA_KEY,
                "frontLeftSeatHeatVentWidget",
                "frontRightSeatHeatVentWidget",
                FRONT_LEFT_SEAT_HEAT_VENT_SINGLE_WIDGET_DATA_KEY,
                FRONT_RIGHT_SEAT_HEAT_VENT_SINGLE_WIDGET_DATA_KEY,
                REAR_LEFT_SEAT_HEAT_WIDGET_DATA_KEY,
                REAR_RIGHT_SEAT_HEAT_WIDGET_DATA_KEY,
            ) || key in HVAC_CLIMATE_WIDGET_DATA_KEYS
        }
    }

    private suspend fun restartPolling() {
        pollJob?.cancel()
        val interestedSignals = sourceMutex.withLock { sourceSignals.values.flatten().toSet() }
        syncPushListeners(interestedSignals)
        if (interestedSignals.isEmpty()) {
            logDebug("polling stopped: no interested signals")
            return
        }
        logDebug("polling started: signals=${interestedSignals.size} ${interestedSignals.joinToString()}")
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
        logDebug("polling burst requested until=$burstUntilMs")
    }

    private fun signalReadPropertyIds(signal: MbCanSignal): Set<Int> {
        fun resolved(id: Int): Int = FirmwareVehicleJsonMapper.resolveReadPropertyId(id) ?: id
        return when (signal) {
            MbCanSignal.SteeringWheelHeat -> setOf(resolved(MbCanKnownVehiclePropertyId.STEERING_WHEEL_HEAT_SWITCH))
            MbCanSignal.WiperMaintenance -> setOf(resolved(MbCanKnownVehiclePropertyId.WIPER_MAINTENANCE_SWITCH))
            MbCanSignal.ParkingRadar -> setOf(resolved(MbCanKnownVehiclePropertyId.PARKING_RADAR_SWITCH))
            MbCanSignal.RearFogLight -> setOf(resolved(MbCanKnownVehiclePropertyId.REAR_FOG_LIGHT))
            MbCanSignal.AutoLock -> setOf(resolved(MbCanKnownVehiclePropertyId.DOOR_AUTO_LOCK))
            MbCanSignal.AutoUnlock -> setOf(resolved(MbCanKnownVehiclePropertyId.DOOR_IGNOFF_UNLOCK))
            MbCanSignal.FollowMeHome -> setOf(resolved(MbCanKnownVehiclePropertyId.HEADLIGHTS_HOMELIGHT_DELAY))
            MbCanSignal.DriverUnlockMode -> setOf(resolved(MbCanKnownVehiclePropertyId.DRIVER_UNLOCK_MODE))
            MbCanSignal.RemoteLockFeedback -> setOf(resolved(MbCanKnownVehiclePropertyId.DEFENCES_PROMPT))
            MbCanSignal.WiperSensitivity -> setOf(resolved(MbCanKnownVehiclePropertyId.WIPER_SENSITIVITY))
            MbCanSignal.RearWiper -> setOf(resolved(MbCanKnownVehiclePropertyId.REAR_WIPER))
            MbCanSignal.LowBeamHeight -> setOf(resolved(MbCanKnownVehiclePropertyId.HIGHBEAM_ADJUST))
            MbCanSignal.TurnFlashCount -> setOf(resolved(MbCanKnownVehiclePropertyId.TURN_FLASH_COUNT))
            MbCanSignal.LightControl -> setOf(resolved(MbCanKnownVehiclePropertyId.LIGHTCONTROL))
            MbCanSignal.AvhSwitch -> setOf(resolved(MbCanKnownVehiclePropertyId.AVH_SWITCH))
            MbCanSignal.HdcSwitch -> setOf(resolved(MbCanKnownVehiclePropertyId.HDC_SWITCH))
            MbCanSignal.EspOffSwitch -> setOf(resolved(MbCanKnownVehiclePropertyId.ESP_OFF_SWITCH))
            MbCanSignal.LasModeSelection -> setOf(resolved(MbCanKnownVehiclePropertyId.LAS_MODE_SELECTION))
            MbCanSignal.TjaIca -> setOf(resolved(MbCanKnownVehiclePropertyId.TJA_ICA_SWITCH))
            MbCanSignal.HmaSwitch -> setOf(resolved(MbCanKnownVehiclePropertyId.HMA_SWITCH))
            MbCanSignal.Bsd -> setOf(resolved(MbCanKnownVehiclePropertyId.BLIND_AREA_DETECTION))
            MbCanSignal.Dow -> setOf(resolved(MbCanKnownVehiclePropertyId.DOOR_OPEN_WARNING))
            MbCanSignal.Fcw -> setOf(resolved(MbCanKnownVehiclePropertyId.FCW_SWITCH))
            MbCanSignal.FcwSensitivity -> setOf(resolved(MbCanKnownVehiclePropertyId.FCW_SENSITIVITY))
            MbCanSignal.LdwSensitivity -> setOf(resolved(MbCanKnownVehiclePropertyId.LAS_SENSITIVITY_LEVEL))
            MbCanSignal.HvacCustomMode -> setOf(resolved(MbCanKnownVehiclePropertyId.HVAC_CUSTOM))
            MbCanSignal.HvacAcMax -> setOf(resolved(MbCanKnownVehiclePropertyId.HVAC_AC_MAX))
            MbCanSignal.FrontWindscreenHeat -> setOf(resolved(MbCanKnownVehiclePropertyId.FRONT_WINDSCREEN_HEAT_SWITCH))
            MbCanSignal.HvacDefroster -> setOf(resolved(MbCanKnownVehiclePropertyId.HVAC_DEFROSTER_SWITCH))
            MbCanSignal.HvacAirRecirculation -> setOf(resolved(MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION))
            MbCanSignal.HvacAcPower -> setOf(resolved(MbCanKnownVehiclePropertyId.HVAC_POWER))
            MbCanSignal.HvacAcCleanWhenLocked -> setOf(resolved(MbCanKnownVehiclePropertyId.HVAC_BLOWER_DELAY))
            MbCanSignal.HvacAutoState -> setOf(resolved(MbCanKnownVehiclePropertyId.HVAC_AUTO_STATE))
            MbCanSignal.FirstBlowing -> setOf(resolved(MbCanKnownVehiclePropertyId.POWER_FIRST_BREATH))
            MbCanSignal.BtReduceFan -> setOf(resolved(MbCanKnownVehiclePropertyId.BT_REDUCED_WIND_SPEED))
            MbCanSignal.AutoVentilation -> setOf(resolved(MbCanKnownVehiclePropertyId.HVAC_VENTILATION_AUTO_SWITCH))
            MbCanSignal.HvacDefrosterFront -> setOf(resolved(MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION))
            MbCanSignal.HvacFrontOff -> setOf(resolved(MbCanKnownVehiclePropertyId.HVAC_FRONT_OFF))
            MbCanSignal.HvacTempLeft -> setOf(resolved(MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_LEFT))
            MbCanSignal.HvacTempRight -> setOf(resolved(MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_RIGHT))
            MbCanSignal.HvacFanSpeed -> setOf(resolved(MbCanKnownVehiclePropertyId.HVAC_FAN_SPEED))
            MbCanSignal.HvacSync -> setOf(resolved(MbCanKnownVehiclePropertyId.HVAC_SYNC_SWITCH))
            MbCanSignal.HvacBlowMode -> setOf(resolved(MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION))
            MbCanSignal.HudSwitch -> setOf(resolved(MbCanKnownVehiclePropertyId.HUD_SWITCH))
            MbCanSignal.HudHeight -> setOf(resolved(MbCanKnownVehiclePropertyId.HUD_HEIGHT))
            MbCanSignal.HudBrightness -> setOf(resolved(MbCanKnownVehiclePropertyId.HUD_BRIGHTNESS))
            MbCanSignal.HudDisplayMode -> setOf(resolved(MbCanKnownVehiclePropertyId.HUD_DISPLAY_MODE))
            MbCanSignal.HudAutoBrightness -> setOf(resolved(MbCanKnownVehiclePropertyId.HUD_AUTO_BRIGHTNESS))
            MbCanSignal.OverspeedAlarm -> setOf(resolved(MbCanKnownVehiclePropertyId.OVERSPEED_ALARM_SET))
            MbCanSignal.TrunkDoor -> setOf(
                resolved(MbCanKnownVehiclePropertyId.TRUNK_STATUS),
                resolved(MbCanKnownVehiclePropertyId.TRUNK_REAR_DOOR_MOVE_DIR),
            )
            MbCanSignal.AudioVolume -> setOf(resolved(MbCanKnownAudioPropertyId.VOLUME))
            MbCanSignal.AudioVolumeSpeed -> setOf(resolved(MbCanKnownAudioPropertyId.VOLUME_SPEED))
            MbCanSignal.CarSettingsVehicleParams -> setOf(
                resolved(MbCanKnownVehiclePropertyId.VEHICLE_PROPERTY_EPS_MODE),
                resolved(MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE),
                resolved(MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE_6DCT_WET)
            )
            MbCanSignal.SlaSpeedLimit -> setOf(
                FirmwareVehicleJsonMapper.VHAL_SLA_SPEED_LIMIT_RAW,
                FirmwareVehicleJsonMapper.VHAL_SLA_ON_OFF_STATUS,
                FirmwareVehicleJsonMapper.VHAL_SLA_STATE,
            )
            MbCanSignal.SpeedLimiter -> setOfNotNull(
                FirmwareVehicleJsonMapper.resolveReadPropertyId(MbCanKnownVehiclePropertyId.VEHICLE_SPEEDLIMIT_SWITCH)
                    ?: MbCanKnownVehiclePropertyId.VEHICLE_SPEEDLIMIT_SWITCH,
            )
            MbCanSignal.AccCruise -> setOf(
                FirmwareVehicleJsonMapper.VHAL_FRM_ACC_MODE,
                FirmwareVehicleJsonMapper.VHAL_FRM_V_SET_DIS,
                FirmwareVehicleJsonMapper.VHAL_EMS_CRUISE_CONTROL_STATUS,
            )
            MbCanSignal.FrontLeftSeatMode -> setOf(resolved(MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH))
            MbCanSignal.FrontRightSeatMode -> setOf(resolved(MbCanKnownVehiclePropertyId.FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH))
            MbCanSignal.RearLeftSeatMode -> setOf(resolved(MbCanKnownVehiclePropertyId.REAR_LEFT_SEAT_HEAT_SWITCH))
            MbCanSignal.RearRightSeatMode -> setOf(resolved(MbCanKnownVehiclePropertyId.REAR_RIGHT_SEAT_HEAT_SWITCH))
            MbCanSignal.WirelessChargingSwitch -> emptySet()
            MbCanSignal.EngineRpm -> setOf(VHAL_ENGINE_RPM_PROPERTY_ID)
            MbCanSignal.EngineTemperature -> setOf(VHAL_ENGINE_TEMPERATURE_PROPERTY_ID)
            MbCanSignal.CarSpeed -> setOf(
                VHAL_CAR_SPEED_VSO_SIG_PROPERTY_ID,
                VHAL_CAR_SPEED_DISPLAY_PROPERTY_ID,
            )
            MbCanSignal.VehicleGear -> setOf(VHAL_GEAR_SELECTION_PROPERTY_ID, VHAL_CURRENT_GEAR_PROPERTY_ID)
            MbCanSignal.ReverseGearSwitch -> setOf(VHAL_REVERSE_GEAR_SWITCH_PROPERTY_ID)
            MbCanSignal.FuelLevel -> setOf(VHAL_FUEL_LEVEL_PROPERTY_ID)
            MbCanSignal.TotalOdometer -> setOf(VHAL_TOTAL_ODOMETER_KM_PROPERTY_ID)
            MbCanSignal.OutsideTemperature -> setOf(VHAL_EXTERNAL_TEMPERATURE_RAW_PROPERTY_ID)
            MbCanSignal.VehicleTires -> setOf(
                VHAL_LF_TYRE_PRESSURE,
                VHAL_RF_TYRE_PRESSURE,
                VHAL_LR_TYRE_PRESSURE,
                VHAL_RR_TYRE_PRESSURE,
                VHAL_LF_TYRE_TEMPERATURE,
                VHAL_RF_TYRE_TEMPERATURE,
                VHAL_LR_TYRE_TEMPERATURE,
                VHAL_RR_TYRE_TEMPERATURE,
            )
            MbCanSignal.CurrentFuelConsumption -> setOf(VHAL_FUEL_ROLLING_COUNTER_PROPERTY_ID)
            MbCanSignal.DistanceToNextMaintenance -> setOf(VHAL_MAINTENANCE_TIPS_PROPERTY_ID)
            MbCanSignal.DistanceToFuelEmpty -> setOf(VHAL_DISTANCE_TO_EMPTY_KM_PROPERTY_ID)
            MbCanSignal.Pm25AirQuality -> setOf(
                VHAL_PM25_INDENSITY_PROPERTY_ID,
                VHAL_PM25_OUTDENSITY_PROPERTY_ID,
            )
            // Steering angle is A9 mbCAN-only; no VHAL property in stock VehiclePropertyIds.
            MbCanSignal.SteeringAngle -> emptySet()
        }
    }

    private fun asIntValue(raw: Any?): Int? {
        return when (raw) {
            is Number -> raw.toInt()
            is Boolean -> if (raw) 1 else 0
            else -> null
        }
    }

    private fun decodeEngineRpm(raw: Any?): Float? {
        val numeric = (raw as? Number)?.toFloat() ?: return null
        if (numeric < 0f) return null
        return numeric * VHAL_ENGINE_RPM_SCALE
    }

    private fun decodeEngineTemperature(raw: Any?): Float? {
        val numeric = (raw as? Number)?.toFloat() ?: return null
        return numeric * VHAL_ENGINE_TEMPERATURE_SCALE + VHAL_ENGINE_TEMPERATURE_OFFSET
    }

    private fun asSpeedRawFloat(raw: Any?): Float? {
        val numeric = (raw as? Number)?.toFloat() ?: return null
        if (!numeric.isFinite() || numeric < 0f) return null
        return numeric
    }

    private fun clearCarSpeedCacheAndState() {
        lastVsoSpeedRaw = null
        lastDisplaySpeedRaw = null
        _carSpeedState.value = null
    }

    private fun publishCarSpeedFromCachedRaws() {
        _carSpeedState.value = VehicleSpeedDomain.resolvePreferredKmh(
            lastVsoSpeedRaw,
            lastDisplaySpeedRaw,
        )
    }

    private fun decodeVehicleGear(raw: Any?): String? {
        val value = (raw as? Number)?.toInt() ?: return null
        return VehicleGearDomain.decodePrndBitmask(value)
    }

    private fun decodeReverseGearSwitch(raw: Any?): Boolean? {
        val value = (raw as? Number)?.toInt() ?: return null
        return VehicleGearDomain.decodeReverseGearSwitch(value)
    }

    private fun decodeFuelLevelPercent(raw: Any?): UInt? {
        val value = (raw as? Int) ?: return null
        return value.takeIf { it in 0..100 }?.toUInt()
    }

    private fun decodeOdometerKm(raw: Any?): UInt? {
        return when (raw) {
            is Int -> raw.takeIf { it >= 0 }?.toUInt()
            is Float -> raw.takeIf { it.isFinite() && it >= 0f }?.toUInt()
            else -> null
        }
    }

    private fun decodeOutsideTemperature(raw: Any?): Float? {
        val value = (raw as? Int) ?: return null
        return OutsideTemperatureDomain.decodeVhalRaw(value)
    }

    private fun decodeVhalTirePressure(raw: Any?): Float? {
        val value = asIntValue(raw) ?: return null
        return TirePressureDomain.decodeVhalPressureBar(value)
    }

    private fun decodeVhalTireTemperature(raw: Any?): Float? {
        val value = asIntValue(raw) ?: return null
        return TirePressureDomain.decodeVhalTemperatureC(value)
    }

    private fun applyVhalTirePressureCorner(corner: Int, bar: Float?) {
        val now = SystemClock.elapsedRealtime()
        _wheelsPressureState.value = TirePressureDomain.mergeWheelsPressureCorner(
            current = _wheelsPressureState.value,
            corner = corner,
            incoming = bar,
            now = now,
            debounceMs = UniversalCanRepository.wheelPressureNullDebounceMs,
        )
    }

    /** Disk restore for HU tire pressure (same AppData keys as TBox). */
    fun restoreWheelsPressureFromSaved(saved: Wheels) {
        val now = SystemClock.elapsedRealtime()
        val cur = _wheelsPressureState.value
        val merged = TirePressureDomain.restoreMissingPressures(cur, saved, now)
        if (merged != cur) {
            _wheelsPressureState.value = merged
        }
    }

    private fun applyVhalTireTemperatureCorner(corner: Int, celsius: Float?) {
        val cur = _wheelsTemperatureState.value
        _wheelsTemperatureState.value = when (corner) {
            0 -> cur.copy(wheel1 = celsius)
            1 -> cur.copy(wheel2 = celsius)
            2 -> cur.copy(wheel3 = celsius)
            3 -> cur.copy(wheel4 = celsius)
            else -> cur
        }
    }

    private fun decodeCarSettingsIntZeroToSix(raw: Int?): Int? {
        val value = raw ?: return null
        return if (value in carSettingsZeroToSixRange) value else null
    }

    private fun publishSlaSignUiState() {
        _slaSignUiState.value = SlaSpeedLimitDomain.resolveSlaSignUiState(
            slaOnOffRaw = slaLkaOnOffRaw,
            slaStateRaw = slaLkaStateRaw,
            slaLimitRaw = slaLkaLimitRaw,
        )
    }

    private fun clearSlaSignUiState() {
        slaLkaOnOffRaw = null
        slaLkaStateRaw = null
        slaLkaLimitRaw = null
        publishSlaSignUiState()
    }

    // For several VHAL read-status properties in stock apps, ON is encoded as 1 and OFF as 2.
    private fun isVhalBinaryToggleProperty(propertyId: Int): Boolean =
        VhalBinaryToggleCodec.isVhalBinaryToggleProperty(propertyId)

    // Keep this decoder local to Android10VhalRepository to avoid affecting mbCAN behavior.
    /** Stock VHAL binary ON/OFF read: selected when raw == 1, otherwise off. */
    private fun decodeVhalBinaryOneIsOn(raw: Int): MbCanBinaryState =
        if (raw == 1) MbCanBinaryState.On else MbCanBinaryState.Off

    private fun decodeVhalBinaryReadState(propertyId: Int, raw: Int): MbCanBinaryState = when (propertyId) {
        MbCanKnownVehiclePropertyId.STEERING_WHEEL_HEAT_SWITCH,
        MbCanKnownVehiclePropertyId.WIPER_MAINTENANCE_SWITCH,
        MbCanKnownVehiclePropertyId.PARKING_RADAR_SWITCH,
        MbCanKnownVehiclePropertyId.REAR_FOG_LIGHT,
        MbCanKnownVehiclePropertyId.FRONT_WINDSCREEN_HEAT_SWITCH,
        MbCanKnownVehiclePropertyId.HVAC_DEFROSTER_SWITCH,
        MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION,
        MbCanKnownVehiclePropertyId.HVAC_POWER,
        MbCanKnownVehiclePropertyId.HVAC_BLOWER_DELAY,
        MbCanKnownVehiclePropertyId.HVAC_AUTO_STATE,
        MbCanKnownVehiclePropertyId.POWER_FIRST_BREATH,
        MbCanKnownVehiclePropertyId.BT_REDUCED_WIND_SPEED,
        MbCanKnownVehiclePropertyId.HVAC_VENTILATION_AUTO_SWITCH,
        MbCanKnownVehiclePropertyId.HUD_SWITCH,
        MbCanKnownVehiclePropertyId.HUD_AUTO_BRIGHTNESS ->
            decodeVhalBinaryOneIsOn(raw)
        MbCanKnownVehiclePropertyId.AVH_SWITCH,
        MbCanKnownVehiclePropertyId.HDC_SWITCH ->
            MbCanSignalStateEngine.decodeAvhHdcStatusRaw(raw)
        MbCanKnownVehiclePropertyId.ESP_OFF_SWITCH ->
            MbCanSignalStateEngine.decodeEspOffStatusRaw(raw)
        MbCanKnownVehiclePropertyId.TJA_ICA_SWITCH,
        MbCanKnownVehiclePropertyId.HMA_SWITCH,
        MbCanKnownVehiclePropertyId.BLIND_AREA_DETECTION,
        MbCanKnownVehiclePropertyId.DOOR_OPEN_WARNING,
        MbCanKnownVehiclePropertyId.FCW_SWITCH,
        MbCanKnownVehiclePropertyId.ACC_AUTOBRAKE_SWITCH,
        MbCanKnownVehiclePropertyId.SAFE_DISTANCE_WARNING ->
            decodeVhalBinaryOneIsOn(raw)
        MbCanKnownVehiclePropertyId.HVAC_AC_MAX ->
            MbCanSignalStateEngine.decodeHvacAcMaxVhalRaw(raw)
        MbCanKnownVehiclePropertyId.HVAC_SYNC_SWITCH ->
            HvacClimateDomain.decodeHvacSyncVhalRaw(raw)
        MbCanKnownVehiclePropertyId.HVAC_FRONT_OFF ->
            HvacClimateDomain.decodeHvacFrontOffVhalRaw(raw)
        MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION ->
            MbCanSignalStateEngine.decodeHvacFrontDefrostVhalRaw(raw)
        else -> MbCanBinaryState.Unknown
    }

    private fun encodeVhalBinaryWriteValue(propertyId: Int, targetOn: Boolean): Int? =
        VhalBinaryToggleCodec.encodeWriteValue(propertyId, targetOn)

    private fun latestBinaryState(propertyId: Int): MbCanBinaryState = when (propertyId) {
        MbCanKnownVehiclePropertyId.STEERING_WHEEL_HEAT_SWITCH -> _steeringWheelHeatState.value
        MbCanKnownVehiclePropertyId.WIPER_MAINTENANCE_SWITCH -> _wiperMaintenanceState.value
        MbCanKnownVehiclePropertyId.PARKING_RADAR_SWITCH -> _parkingRadarState.value
        MbCanKnownVehiclePropertyId.REAR_FOG_LIGHT -> _rearFogState.value
        MbCanKnownVehiclePropertyId.AVH_SWITCH -> _avhState.value
        MbCanKnownVehiclePropertyId.HDC_SWITCH -> _hdcState.value
        MbCanKnownVehiclePropertyId.ESP_OFF_SWITCH -> _espOffState.value
        MbCanKnownVehiclePropertyId.TJA_ICA_SWITCH -> _tjaIcaState.value
        MbCanKnownVehiclePropertyId.HMA_SWITCH -> _hmaState.value
        MbCanKnownVehiclePropertyId.BLIND_AREA_DETECTION -> _bsdState.value
        MbCanKnownVehiclePropertyId.DOOR_OPEN_WARNING -> _dowState.value
        MbCanKnownVehiclePropertyId.FCW_SWITCH -> _fcwState.value
        MbCanKnownVehiclePropertyId.HVAC_AC_MAX -> _hvacAcMaxState.value
        MbCanKnownVehiclePropertyId.FRONT_WINDSCREEN_HEAT_SWITCH -> _frontWindscreenHeatState.value
        MbCanKnownVehiclePropertyId.HVAC_DEFROSTER_SWITCH -> _hvacDefrosterState.value
        MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION -> _hvacAirRecirculationState.value
        MbCanKnownVehiclePropertyId.HVAC_POWER -> _hvacAcPowerState.value
        MbCanKnownVehiclePropertyId.HVAC_BLOWER_DELAY -> _hvacAcCleanWhenLockedState.value
        MbCanKnownVehiclePropertyId.HVAC_AUTO_STATE -> _hvacAutoState.value
        MbCanKnownVehiclePropertyId.POWER_FIRST_BREATH -> _firstBlowingState.value
        MbCanKnownVehiclePropertyId.BT_REDUCED_WIND_SPEED -> _btReduceFanState.value
        MbCanKnownVehiclePropertyId.HVAC_VENTILATION_AUTO_SWITCH -> _autoVentilationState.value
        MbCanKnownVehiclePropertyId.HUD_SWITCH -> _hudSwitchState.value
        MbCanKnownVehiclePropertyId.HUD_AUTO_BRIGHTNESS -> _hudAutoBrightnessState.value
        MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION -> _hvacDefrosterFrontState.value
        else -> MbCanBinaryState.Unknown
    }

    private fun readNumericProperty(propertyId: Int): Float? {
        val asInt = bridge?.getIntProperty(propertyId)
        if (asInt != null) return asInt.toFloat()
        return bridge?.getFloatProperty(propertyId)
    }

    private fun encodeVhalSetValue(propertyId: Int, mbCanValue: Int): Int? = when (propertyId) {
        MbCanKnownVehiclePropertyId.HEADLIGHTS_HOMELIGHT_DELAY ->
            FollowMeHomeMode.fromMbCanRaw(mbCanValue)?.vhalWriteValue
        MbCanKnownVehiclePropertyId.HIGHBEAM_ADJUST ->
            CarSettingsLocksLightsDomain.encodeLowBeamHeightVhal(mbCanValue)
        MbCanKnownVehiclePropertyId.FCW_SENSITIVITY ->
            CarSettingsAdasDomain.decodeFcwSensitivityMbCan(mbCanValue)
                ?.let(CarSettingsAdasDomain::encodeFcwSensitivityVhal)
        MbCanKnownVehiclePropertyId.LAS_SENSITIVITY_LEVEL ->
            CarSettingsAdasDomain.decodeLdwSensitivityMbCan(mbCanValue)
                ?.let(CarSettingsAdasDomain::encodeLdwSensitivityVhal)
        MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_LEFT,
        MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_RIGHT ->
            HvacClimateDomain.mbCanTempRawToVhalWrite(mbCanValue)
        MbCanKnownVehiclePropertyId.HVAC_FAN_SPEED ->
            mbCanValue.takeIf { it in HvacClimateDomain.FAN_SPEED_MIN..HvacClimateDomain.FAN_SPEED_MAX }
        MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION ->
            HvacClimateDomain.mbCanBlowModeToVhalWrite(mbCanValue)
        MbCanKnownVehiclePropertyId.TRUNK_PLG_CONTROL -> mbCanValue
        else -> mbCanValue
    }

    private fun clearCertifiedCarSettings() {
        _autoLockState.value = MbCanBinaryState.Unknown
        _autoUnlockState.value = MbCanBinaryState.Unknown
        _rearWiperState.value = MbCanBinaryState.Unknown
        _followMeHomeMode.value = null
        _driverUnlockMode.value = null
        _remoteLockFeedback.value = null
        _wiperSensitivity.value = null
        _lowBeamHeight.value = null
        _turnFlashCount.value = null
    }

    private suspend fun refreshCertifiedCarSettings(signal: MbCanSignal) {
        val id = when (signal) {
            MbCanSignal.AutoLock -> MbCanKnownVehiclePropertyId.DOOR_AUTO_LOCK
            MbCanSignal.AutoUnlock -> MbCanKnownVehiclePropertyId.DOOR_IGNOFF_UNLOCK
            MbCanSignal.FollowMeHome -> MbCanKnownVehiclePropertyId.HEADLIGHTS_HOMELIGHT_DELAY
            MbCanSignal.DriverUnlockMode -> MbCanKnownVehiclePropertyId.DRIVER_UNLOCK_MODE
            MbCanSignal.RemoteLockFeedback -> MbCanKnownVehiclePropertyId.DEFENCES_PROMPT
            MbCanSignal.WiperSensitivity -> MbCanKnownVehiclePropertyId.WIPER_SENSITIVITY
            MbCanSignal.RearWiper -> MbCanKnownVehiclePropertyId.REAR_WIPER
            MbCanSignal.LowBeamHeight -> MbCanKnownVehiclePropertyId.HIGHBEAM_ADJUST
            MbCanSignal.TurnFlashCount -> MbCanKnownVehiclePropertyId.TURN_FLASH_COUNT
            else -> return
        }
        applyCertifiedCarSettings(signal, readMappedIntProperty(id))
    }

    private fun applyCertifiedCarSettings(signal: MbCanSignal, raw: Int?) {
        when (signal) {
            MbCanSignal.AutoLock -> _autoLockState.value = raw?.let(::decodeVhalBinaryOneIsOn) ?: MbCanBinaryState.Unknown
            MbCanSignal.AutoUnlock -> _autoUnlockState.value = raw?.let(::decodeVhalBinaryOneIsOn) ?: MbCanBinaryState.Unknown
            MbCanSignal.RearWiper -> _rearWiperState.value = raw?.let(::decodeVhalBinaryOneIsOn) ?: MbCanBinaryState.Unknown
            MbCanSignal.FollowMeHome -> _followMeHomeMode.value = raw?.let(FollowMeHomeMode::fromVhalRaw)
            MbCanSignal.DriverUnlockMode -> _driverUnlockMode.value = raw?.takeIf { it in 1..2 }
            MbCanSignal.RemoteLockFeedback -> _remoteLockFeedback.value = raw?.let(CarSettingsLocksLightsDomain::decodeRemoteLockFeedbackVhal)
            MbCanSignal.WiperSensitivity -> _wiperSensitivity.value = raw?.takeIf { it in 1..4 }
            MbCanSignal.LowBeamHeight -> _lowBeamHeight.value = raw?.let(CarSettingsLocksLightsDomain::decodeLowBeamHeightVhal)
            MbCanSignal.TurnFlashCount -> _turnFlashCount.value = raw?.let(CarSettingsLocksLightsDomain::decodeTurnFlashCountVhal)
            else -> Unit
        }
    }

    private fun readMappedIntProperty(mbCanPropertyId: Int): Int? {
        val propertyId = FirmwareVehicleJsonMapper.resolveReadPropertyId(mbCanPropertyId) ?: mbCanPropertyId
        return bridge?.getIntProperty(propertyId)
    }

    private suspend fun applyPushPropertyUpdate(propertyId: Int, rawValue: Any?) {
        fun resolved(id: Int): Int = FirmwareVehicleJsonMapper.resolveReadPropertyId(id) ?: id
        val raw = asIntValue(rawValue)
        when (propertyId) {
            resolved(MbCanKnownVehiclePropertyId.STEERING_WHEEL_HEAT_SWITCH) ->
                raw?.let {
                    stateEngine.applySteeringCandidate(decodeVhalBinaryOneIsOn(it))
                }
            resolved(MbCanKnownVehiclePropertyId.WIPER_MAINTENANCE_SWITCH) ->
                raw?.let {
                    stateEngine.applyWiperMaintenanceCandidate(decodeVhalBinaryOneIsOn(it))
                }
            resolved(MbCanKnownVehiclePropertyId.PARKING_RADAR_SWITCH) ->
                raw?.let {
                    stateEngine.applyParkingRadarCandidate(decodeVhalBinaryOneIsOn(it))
                }
            resolved(MbCanKnownVehiclePropertyId.REAR_FOG_LIGHT) ->
                raw?.let {
                    stateEngine.applyRearFogCandidate(decodeVhalBinaryOneIsOn(it))
                }
            resolved(MbCanKnownVehiclePropertyId.DOOR_AUTO_LOCK) ->
                applyCertifiedCarSettings(MbCanSignal.AutoLock, raw)
            resolved(MbCanKnownVehiclePropertyId.DOOR_IGNOFF_UNLOCK) ->
                applyCertifiedCarSettings(MbCanSignal.AutoUnlock, raw)
            resolved(MbCanKnownVehiclePropertyId.HEADLIGHTS_HOMELIGHT_DELAY) ->
                applyCertifiedCarSettings(MbCanSignal.FollowMeHome, raw)
            resolved(MbCanKnownVehiclePropertyId.DRIVER_UNLOCK_MODE) ->
                applyCertifiedCarSettings(MbCanSignal.DriverUnlockMode, raw)
            resolved(MbCanKnownVehiclePropertyId.DEFENCES_PROMPT) ->
                applyCertifiedCarSettings(MbCanSignal.RemoteLockFeedback, raw)
            resolved(MbCanKnownVehiclePropertyId.WIPER_SENSITIVITY) ->
                applyCertifiedCarSettings(MbCanSignal.WiperSensitivity, raw)
            resolved(MbCanKnownVehiclePropertyId.REAR_WIPER) ->
                applyCertifiedCarSettings(MbCanSignal.RearWiper, raw)
            resolved(MbCanKnownVehiclePropertyId.HIGHBEAM_ADJUST) ->
                applyCertifiedCarSettings(MbCanSignal.LowBeamHeight, raw)
            resolved(MbCanKnownVehiclePropertyId.TURN_FLASH_COUNT) ->
                applyCertifiedCarSettings(MbCanSignal.TurnFlashCount, raw)
            resolved(MbCanKnownVehiclePropertyId.AVH_SWITCH) ->
                raw?.let {
                    stateEngine.applyAvhCandidate(MbCanSignalStateEngine.decodeAvhHdcStatusRaw(it))
                }
            resolved(MbCanKnownVehiclePropertyId.HDC_SWITCH) ->
                raw?.let {
                    stateEngine.applyHdcCandidate(MbCanSignalStateEngine.decodeAvhHdcStatusRaw(it))
                }
            resolved(MbCanKnownVehiclePropertyId.ESP_OFF_SWITCH) ->
                raw?.let {
                    stateEngine.applyEspOffCandidate(MbCanSignalStateEngine.decodeEspOffStatusRaw(it))
                }
            resolved(MbCanKnownVehiclePropertyId.LAS_MODE_SELECTION) ->
                raw?.let { _lasModeRaw.value = MbCanSignalStateEngine.decodeLasModeRaw(it) }
            resolved(MbCanKnownVehiclePropertyId.LIGHTCONTROL) ->
                raw?.let { _headlightModeRaw.value = MbCanSignalStateEngine.decodeLightControlRaw(it) }
            resolved(MbCanKnownVehiclePropertyId.TJA_ICA_SWITCH) ->
                raw?.let {
                    stateEngine.applyTjaIcaCandidate(decodeVhalBinaryOneIsOn(it))
                }
            resolved(MbCanKnownVehiclePropertyId.HMA_SWITCH) ->
                raw?.let {
                    stateEngine.applyHmaCandidate(decodeVhalBinaryOneIsOn(it))
                }
            resolved(MbCanKnownVehiclePropertyId.HVAC_CUSTOM) ->
                raw?.let { HvacClimateCanRepository.applyCustomModeVhal(it) }
            resolved(MbCanKnownVehiclePropertyId.HVAC_AC_MAX) ->
                raw?.let {
                    stateEngine.applyHvacAcMaxCandidate(MbCanSignalStateEngine.decodeHvacAcMaxVhalRaw(it))
                }
            resolved(MbCanKnownVehiclePropertyId.FRONT_WINDSCREEN_HEAT_SWITCH) ->
                raw?.let {
                    stateEngine.applyWindshieldHeatCandidate(decodeVhalBinaryOneIsOn(it))
                }
            resolved(MbCanKnownVehiclePropertyId.HVAC_DEFROSTER_SWITCH) ->
                raw?.let {
                    stateEngine.applyHvacDefrosterCandidate(decodeVhalBinaryOneIsOn(it))
                }
            resolved(MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION) ->
                raw?.let {
                    stateEngine.applyHvacAirRecirculationCandidate(decodeVhalBinaryOneIsOn(it))
                }
            resolved(MbCanKnownVehiclePropertyId.HVAC_POWER) ->
                raw?.let {
                    stateEngine.applyHvacAcPowerCandidate(decodeVhalBinaryOneIsOn(it))
                }
            resolved(MbCanKnownVehiclePropertyId.HVAC_BLOWER_DELAY) ->
                raw?.let {
                    stateEngine.applyHvacAcCleanWhenLockedCandidate(decodeVhalBinaryOneIsOn(it))
                }
            resolved(MbCanKnownVehiclePropertyId.HVAC_AUTO_STATE) ->
                raw?.let {
                    stateEngine.applyHvacAutoStateCandidate(decodeVhalBinaryOneIsOn(it))
                }
            resolved(MbCanKnownVehiclePropertyId.POWER_FIRST_BREATH) ->
                _firstBlowingState.value = raw?.let(::decodeVhalBinaryOneIsOn) ?: MbCanBinaryState.Unknown
            resolved(MbCanKnownVehiclePropertyId.BT_REDUCED_WIND_SPEED) ->
                _btReduceFanState.value = raw?.let(::decodeVhalBinaryOneIsOn) ?: MbCanBinaryState.Unknown
            resolved(MbCanKnownVehiclePropertyId.HVAC_VENTILATION_AUTO_SWITCH) ->
                _autoVentilationState.value = raw?.let(::decodeVhalBinaryOneIsOn) ?: MbCanBinaryState.Unknown
            resolved(MbCanKnownVehiclePropertyId.HUD_SWITCH) ->
                _hudSwitchState.value = raw?.let(::decodeVhalBinaryOneIsOn) ?: MbCanBinaryState.Unknown
            resolved(MbCanKnownVehiclePropertyId.HUD_HEIGHT) -> _hudHeight.value = raw?.takeIf { it in 1..10 }
            resolved(MbCanKnownVehiclePropertyId.HUD_BRIGHTNESS) -> _hudBrightness.value = raw?.takeIf { it in 1..10 }
            resolved(MbCanKnownVehiclePropertyId.HUD_DISPLAY_MODE) -> _hudDisplayMode.value = raw?.takeIf { it in 1..2 }
            resolved(MbCanKnownVehiclePropertyId.HUD_AUTO_BRIGHTNESS) ->
                _hudAutoBrightnessState.value = raw?.let(::decodeVhalBinaryOneIsOn) ?: MbCanBinaryState.Unknown
            resolved(MbCanKnownVehiclePropertyId.OVERSPEED_ALARM_SET) ->
                _overspeedAlarmKmh.value = raw?.let(CarSettingsHudDomain::decodeOverspeedKmh)
            resolved(MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION) ->
                raw?.let {
                    stateEngine.applyHvacDefrosterFrontCandidate(
                        MbCanSignalStateEngine.decodeHvacFrontDefrostVhalRaw(it)
                    )
                    HvacClimateCanRepository.applyBlowModeVhal(it)
                }
            resolved(MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_LEFT) ->
                raw?.let { HvacClimateCanRepository.applyTempLeftVhal(it) }
            resolved(MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_RIGHT) ->
                raw?.let { HvacClimateCanRepository.applyTempRightVhal(it) }
            resolved(MbCanKnownVehiclePropertyId.HVAC_FAN_SPEED) ->
                raw?.let { HvacClimateCanRepository.applyFanSpeed(it) }
            resolved(MbCanKnownVehiclePropertyId.HVAC_SYNC_SWITCH) ->
                raw?.let { HvacClimateCanRepository.applySyncVhal(it) }
            resolved(MbCanKnownVehiclePropertyId.HVAC_FRONT_OFF) ->
                raw?.let { HvacClimateCanRepository.applyFrontOffVhal(it) }
            resolved(MbCanKnownVehiclePropertyId.TRUNK_STATUS) ->
                raw?.let { TrunkDoorRepository.applyVhalOpenRaw(it) }
            resolved(MbCanKnownVehiclePropertyId.TRUNK_REAR_DOOR_MOVE_DIR) ->
                raw?.let { TrunkDoorRepository.applyMoveDirRaw(it) }
            resolved(MbCanKnownAudioPropertyId.VOLUME) -> raw?.let {
                _audioVolumeState.value = it.coerceAtLeast(0)
                if (it > 0) _audioVolumeLastNonZeroInSession.value = it
            }
            resolved(MbCanKnownAudioPropertyId.VOLUME_SPEED) ->
                raw?.let {
                    _audioVolumeSpeedModeState.value = decodeAudioVolumeSpeedMode(it)
                    stateEngine.applyVolumeSpeedCandidate(MbCanSignalStateEngine.decodeVolumeSpeedRaw(it))
                }
            resolved(MbCanKnownVehiclePropertyId.VEHICLE_PROPERTY_EPS_MODE) ->
                _carSettingsEpsMode.value = decodeCarSettingsIntZeroToSix(raw)
            resolved(MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE) ->
                _carSettingsDriveMode.value = decodeCarSettingsIntZeroToSix(raw)
            resolved(MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE_6DCT_WET) ->
                _carSettingsDriveMode6dctWet.value = decodeCarSettingsIntZeroToSix(raw)
            FirmwareVehicleJsonMapper.VHAL_SLA_SPEED_LIMIT_RAW -> {
                slaLkaLimitRaw = raw
                _slaRecognizedSpeedLimitKmh.value = raw?.let(SlaSpeedLimitDomain::decodeRecognizedSpeedKmh)
                publishSlaSignUiState()
            }
            FirmwareVehicleJsonMapper.VHAL_SLA_ON_OFF_STATUS -> {
                slaLkaOnOffRaw = raw
                _slaOnOffState.value = raw?.let(SlaSpeedLimitDomain::decodeSlaOnOffVhalRaw) ?: MbCanBinaryState.Unknown
                publishSlaSignUiState()
            }
            FirmwareVehicleJsonMapper.VHAL_SLA_STATE -> {
                slaLkaStateRaw = raw
                publishSlaSignUiState()
            }
            resolved(MbCanKnownVehiclePropertyId.VEHICLE_SPEEDLIMIT_SWITCH) ->
                _speedLimiterState.value = raw?.let(SlaSpeedLimitDomain::decodeSpeedLimiterSwitchVhalRaw)
                    ?: MbCanBinaryState.Unknown
            FirmwareVehicleJsonMapper.VHAL_FRM_ACC_MODE -> {
                _accFrmFeedbackAvailable.value = true
                _accCruiseMode.value = raw
            }
            FirmwareVehicleJsonMapper.VHAL_FRM_V_SET_DIS -> {
                _accFrmFeedbackAvailable.value = true
                _accCruiseVSetDisKmh.value = raw?.let(AccCruiseDomain::decodeVhalVSetDisKmh)
            }
            FirmwareVehicleJsonMapper.VHAL_EMS_CRUISE_CONTROL_STATUS -> {
                _ccsCruiseStatus.value = raw?.let(AccCruiseDomain::decodeMbCanCruiseControlStatus)
            }
            resolved(MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH) ->
                raw?.let {
                    stateEngine.applySeatCandidate(MbCanSeatSlot.FrontLeft, MbCanSignalStateEngine.decodeSeatModeRaw(it))
                }
            resolved(MbCanKnownVehiclePropertyId.FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH) ->
                raw?.let {
                    stateEngine.applySeatCandidate(MbCanSeatSlot.FrontRight, MbCanSignalStateEngine.decodeSeatModeRaw(it))
                }
            resolved(MbCanKnownVehiclePropertyId.REAR_LEFT_SEAT_HEAT_SWITCH) ->
                raw?.let {
                    stateEngine.applySeatCandidate(MbCanSeatSlot.RearLeft, MbCanSignalStateEngine.decodeRearSeatHeatRaw(it))
                }
            resolved(MbCanKnownVehiclePropertyId.REAR_RIGHT_SEAT_HEAT_SWITCH) ->
                raw?.let {
                    stateEngine.applySeatCandidate(MbCanSeatSlot.RearRight, MbCanSignalStateEngine.decodeRearSeatHeatRaw(it))
                }
            VHAL_ENGINE_RPM_PROPERTY_ID ->
                _engineRpmState.value = decodeEngineRpm(rawValue)
            VHAL_ENGINE_TEMPERATURE_PROPERTY_ID ->
                _engineTemperatureState.value = decodeEngineTemperature(rawValue)
            VHAL_CAR_SPEED_VSO_SIG_PROPERTY_ID -> {
                lastVsoSpeedRaw = asSpeedRawFloat(rawValue)
                publishCarSpeedFromCachedRaws()
            }
            VHAL_CAR_SPEED_DISPLAY_PROPERTY_ID -> {
                lastDisplaySpeedRaw = asSpeedRawFloat(rawValue)
                publishCarSpeedFromCachedRaws()
            }
            VHAL_GEAR_SELECTION_PROPERTY_ID, VHAL_CURRENT_GEAR_PROPERTY_ID ->
                _gearBoxModeState.value = decodeVehicleGear(rawValue)
            VHAL_REVERSE_GEAR_SWITCH_PROPERTY_ID ->
                _reverseGearSwitchState.value = decodeReverseGearSwitch(rawValue)
            VHAL_FUEL_LEVEL_PROPERTY_ID ->
                _fuelLevelPercentState.value = decodeFuelLevelPercent(rawValue)
            VHAL_TOTAL_ODOMETER_KM_PROPERTY_ID ->
                _odometerKmState.value = decodeOdometerKm(rawValue)
            VHAL_EXTERNAL_TEMPERATURE_RAW_PROPERTY_ID ->
                _outsideTemperatureState.value = decodeOutsideTemperature(rawValue)
            VHAL_FUEL_ROLLING_COUNTER_PROPERTY_ID ->
                asIntValue(rawValue)?.let {
                    _currentFuelConsumptionState.value = InstantFuelConsumptionDomain.decodeRawCounter(it)
                }
            VHAL_MAINTENANCE_TIPS_PROPERTY_ID ->
                asIntValue(rawValue)?.let {
                    _distanceToNextMaintenanceKmState.value = MaintenanceTipsDomain.decodeKm(it)
                }
            VHAL_DISTANCE_TO_EMPTY_KM_PROPERTY_ID ->
                asIntValue(rawValue)?.let {
                    _distanceToFuelEmptyKmState.value = DistanceToEmptyDomain.decodeKm(it)
                }
            VHAL_PM25_INDENSITY_PROPERTY_ID ->
                asIntValue(rawValue)?.let {
                    _insideAirQualityState.value = Pm25AirQualityDomain.decodeDensity(it)
                }
            VHAL_PM25_OUTDENSITY_PROPERTY_ID ->
                asIntValue(rawValue)?.let {
                    _outsideAirQualityState.value = Pm25AirQualityDomain.decodeDensity(it)
                }
            VHAL_LF_TYRE_PRESSURE -> applyVhalTirePressureCorner(0, decodeVhalTirePressure(rawValue))
            VHAL_RF_TYRE_PRESSURE -> applyVhalTirePressureCorner(1, decodeVhalTirePressure(rawValue))
            VHAL_LR_TYRE_PRESSURE -> applyVhalTirePressureCorner(2, decodeVhalTirePressure(rawValue))
            VHAL_RR_TYRE_PRESSURE -> applyVhalTirePressureCorner(3, decodeVhalTirePressure(rawValue))
            VHAL_LF_TYRE_TEMPERATURE -> applyVhalTireTemperatureCorner(0, decodeVhalTireTemperature(rawValue))
            VHAL_RF_TYRE_TEMPERATURE -> applyVhalTireTemperatureCorner(1, decodeVhalTireTemperature(rawValue))
            VHAL_LR_TYRE_TEMPERATURE -> applyVhalTireTemperatureCorner(2, decodeVhalTireTemperature(rawValue))
            VHAL_RR_TYRE_TEMPERATURE -> applyVhalTireTemperatureCorner(3, decodeVhalTireTemperature(rawValue))
        }
    }

    private suspend fun syncPushListeners(interestedSignals: Set<MbCanSignal>) {
        val localBridge = bridge ?: return
        val interestedPropertyIds = interestedSignals.flatMapTo(mutableSetOf()) { signalReadPropertyIds(it) }
        localBridge.setPushCallbacks(
            onChange = { propertyId, areaId, value ->
                enqueuePushOnChange(propertyId = propertyId, areaId = areaId, value = value)
            },
            onError = { propertyId, areaId ->
                logWarn("VHAL push onError propertyId=$propertyId areaId=$areaId")
            }
        )
        localBridge.syncPushSubscriptions(interestedPropertyIds)
    }

    private fun enqueuePushOnChange(propertyId: Int, areaId: Int, value: Any?) {
        synchronized(pendingPushStateLock) {
            pendingPushState[propertyId] = value
            if (!pushStateFlushScheduled) {
                pushStateFlushScheduled = true
                pushCoalesceHandler.postDelayed(flushPushStateRunnable, PUSH_STATE_COALESCE_MS)
            }
        }
        synchronized(pendingPushDebugLock) {
            val prev = pendingPushDebug[propertyId]
            pendingPushDebug[propertyId] = PushDebugBucket(
                count = (prev?.count ?: 0) + 1,
                areaId = areaId,
                value = value
            )
            if (!pushDebugFlushScheduled) {
                pushDebugFlushScheduled = true
                pushCoalesceHandler.postDelayed(flushPushDebugRunnable, PUSH_DEBUG_LOG_COALESCE_MS)
            }
        }
    }

    private fun flushPendingPushState() {
        val snapshot = synchronized(pendingPushStateLock) {
            pushStateFlushScheduled = false
            if (pendingPushState.isEmpty()) return
            pendingPushState.toMap().also { pendingPushState.clear() }
        }
        scope.launch {
            snapshot.forEach { (propertyId, value) ->
                applyPushPropertyUpdate(propertyId, value)
            }
        }
    }

    private fun flushPendingPushDebug() {
        val snapshot = synchronized(pendingPushDebugLock) {
            pushDebugFlushScheduled = false
            if (pendingPushDebug.isEmpty()) return
            pendingPushDebug.toMap().also { pendingPushDebug.clear() }
        }
        val body = snapshot.entries.joinToString("; ") { (propertyId, bucket) ->
            "propertyId=$propertyId count=${bucket.count} areaId=${bucket.areaId} " +
                "last=${bucket.value} type=${bucket.value?.javaClass?.simpleName ?: "null"}"
        }
        logDebug("VHAL push coalesced[$body]")
    }

    suspend fun refreshSignal(signal: MbCanSignal) {
        permissionDeniedReasonForSignal(signal)?.let { deniedReason ->
            when (signal) {
                MbCanSignal.SteeringWheelHeat -> stateEngine.applySteeringCandidate(MbCanBinaryState.Unavailable(deniedReason))
                MbCanSignal.WiperMaintenance -> stateEngine.applyWiperMaintenanceCandidate(MbCanBinaryState.Unavailable(deniedReason))
                MbCanSignal.ParkingRadar -> stateEngine.applyParkingRadarCandidate(MbCanBinaryState.Unavailable(deniedReason))
                MbCanSignal.RearFogLight -> stateEngine.applyRearFogCandidate(MbCanBinaryState.Unavailable(deniedReason))
                MbCanSignal.AutoLock, MbCanSignal.AutoUnlock, MbCanSignal.FollowMeHome,
                MbCanSignal.DriverUnlockMode, MbCanSignal.RemoteLockFeedback, MbCanSignal.WiperSensitivity,
                MbCanSignal.RearWiper, MbCanSignal.LowBeamHeight, MbCanSignal.TurnFlashCount -> clearCertifiedCarSettings()
                MbCanSignal.AvhSwitch -> stateEngine.applyAvhCandidate(MbCanBinaryState.Unavailable(deniedReason))
                MbCanSignal.HdcSwitch -> stateEngine.applyHdcCandidate(MbCanBinaryState.Unavailable(deniedReason))
                MbCanSignal.EspOffSwitch -> stateEngine.applyEspOffCandidate(MbCanBinaryState.Unavailable(deniedReason))
                MbCanSignal.LasModeSelection -> _lasModeRaw.value = null
                MbCanSignal.LightControl -> _headlightModeRaw.value = null
                MbCanSignal.TjaIca -> stateEngine.applyTjaIcaCandidate(MbCanBinaryState.Unavailable(deniedReason))
                MbCanSignal.HmaSwitch -> stateEngine.applyHmaCandidate(MbCanBinaryState.Unavailable(deniedReason))
                MbCanSignal.Bsd -> _bsdState.value = MbCanBinaryState.Unavailable(deniedReason)
                MbCanSignal.Dow -> _dowState.value = MbCanBinaryState.Unavailable(deniedReason)
                MbCanSignal.Fcw -> _fcwState.value = MbCanBinaryState.Unavailable(deniedReason)
                MbCanSignal.FcwSensitivity -> _fcwSensitivity.value = null
                MbCanSignal.LdwSensitivity -> _ldwSensitivity.value = null
                MbCanSignal.HvacCustomMode -> HvacClimateCanRepository.applyCustomModeVhal(-1)
                MbCanSignal.HvacAcMax -> stateEngine.applyHvacAcMaxCandidate(MbCanBinaryState.Unavailable(deniedReason))
                MbCanSignal.FrontWindscreenHeat -> stateEngine.applyWindshieldHeatCandidate(MbCanBinaryState.Unavailable(deniedReason))
                MbCanSignal.HvacDefroster -> stateEngine.applyHvacDefrosterCandidate(MbCanBinaryState.Unavailable(deniedReason))
                MbCanSignal.HvacAirRecirculation -> stateEngine.applyHvacAirRecirculationCandidate(MbCanBinaryState.Unavailable(deniedReason))
                MbCanSignal.HvacAcPower -> stateEngine.applyHvacAcPowerCandidate(MbCanBinaryState.Unavailable(deniedReason))
                MbCanSignal.HvacAcCleanWhenLocked -> stateEngine.applyHvacAcCleanWhenLockedCandidate(MbCanBinaryState.Unavailable(deniedReason))
                MbCanSignal.HvacAutoState -> stateEngine.applyHvacAutoStateCandidate(MbCanBinaryState.Unavailable(deniedReason))
                MbCanSignal.FirstBlowing -> _firstBlowingState.value = MbCanBinaryState.Unavailable(deniedReason)
                MbCanSignal.BtReduceFan -> _btReduceFanState.value = MbCanBinaryState.Unavailable(deniedReason)
                MbCanSignal.AutoVentilation -> _autoVentilationState.value = MbCanBinaryState.Unavailable(deniedReason)
                MbCanSignal.HvacDefrosterFront -> stateEngine.applyHvacDefrosterFrontCandidate(MbCanBinaryState.Unavailable(deniedReason))
                MbCanSignal.HvacFrontOff -> HvacClimateCanRepository.applyFrontOffVhal(0)
                MbCanSignal.HvacTempLeft -> HvacClimateCanRepository.applyTempLeftVhal(-1)
                MbCanSignal.HvacTempRight -> HvacClimateCanRepository.applyTempRightVhal(-1)
                MbCanSignal.HvacFanSpeed -> HvacClimateCanRepository.applyFanSpeed(-1)
                MbCanSignal.HvacSync -> HvacClimateCanRepository.applySyncVhal(-1)
                MbCanSignal.HvacBlowMode -> HvacClimateCanRepository.applyBlowModeVhal(-1)
                MbCanSignal.HudSwitch -> _hudSwitchState.value = MbCanBinaryState.Unavailable(deniedReason)
                MbCanSignal.HudHeight -> _hudHeight.value = null
                MbCanSignal.HudBrightness -> _hudBrightness.value = null
                MbCanSignal.HudDisplayMode -> _hudDisplayMode.value = null
                MbCanSignal.HudAutoBrightness -> _hudAutoBrightnessState.value = MbCanBinaryState.Unavailable(deniedReason)
                MbCanSignal.OverspeedAlarm -> _overspeedAlarmKmh.value = null
                MbCanSignal.TrunkDoor -> TrunkDoorRepository.clear()
                MbCanSignal.AudioVolumeSpeed -> {
                    _audioVolumeSpeedModeState.value = null
                    stateEngine.applyVolumeSpeedCandidate(MbCanBinaryState.Unavailable(deniedReason))
                }
                MbCanSignal.FrontLeftSeatMode ->
                    stateEngine.applySeatCandidate(MbCanSeatSlot.FrontLeft, MbCanSeatModeState.Unavailable(deniedReason))
                MbCanSignal.FrontRightSeatMode ->
                    stateEngine.applySeatCandidate(MbCanSeatSlot.FrontRight, MbCanSeatModeState.Unavailable(deniedReason))
                MbCanSignal.RearLeftSeatMode ->
                    stateEngine.applySeatCandidate(MbCanSeatSlot.RearLeft, MbCanSeatModeState.Unavailable(deniedReason))
                MbCanSignal.RearRightSeatMode ->
                    stateEngine.applySeatCandidate(MbCanSeatSlot.RearRight, MbCanSeatModeState.Unavailable(deniedReason))
                MbCanSignal.AudioVolume -> _audioVolumeState.value = null
                MbCanSignal.EngineRpm -> _engineRpmState.value = null
                MbCanSignal.EngineTemperature -> _engineTemperatureState.value = null
                MbCanSignal.CarSpeed -> clearCarSpeedCacheAndState()
                MbCanSignal.VehicleGear -> _gearBoxModeState.value = null
                MbCanSignal.ReverseGearSwitch -> _reverseGearSwitchState.value = null
                MbCanSignal.FuelLevel -> _fuelLevelPercentState.value = null
                MbCanSignal.TotalOdometer -> _odometerKmState.value = null
                MbCanSignal.OutsideTemperature -> _outsideTemperatureState.value = null
                MbCanSignal.VehicleTires -> {
                    _wheelsPressureState.value = Wheels()
                    _wheelsTemperatureState.value = Wheels()
                }
                MbCanSignal.CurrentFuelConsumption -> _currentFuelConsumptionState.value = null
                MbCanSignal.DistanceToNextMaintenance -> _distanceToNextMaintenanceKmState.value = null
                MbCanSignal.DistanceToFuelEmpty -> _distanceToFuelEmptyKmState.value = null
                MbCanSignal.Pm25AirQuality -> {
                    _insideAirQualityState.value = null
                    _outsideAirQualityState.value = null
                }
                MbCanSignal.SteeringAngle -> {
                    _steerAngleState.value = null
                    _steerSpeedState.value = null
                }
                MbCanSignal.CarSettingsVehicleParams -> {
                    _carSettingsEpsMode.value = null
                    _carSettingsDriveMode.value = null
                    _carSettingsDriveMode6dctWet.value = null
                }
                MbCanSignal.SlaSpeedLimit -> {
                    _slaRecognizedSpeedLimitKmh.value = null
                    _slaOnOffState.value = MbCanBinaryState.Unavailable(deniedReason)
                    clearSlaSignUiState()
                }
                MbCanSignal.SpeedLimiter -> {
                    _speedLimiterState.value = MbCanBinaryState.Unavailable(deniedReason)
                }
                MbCanSignal.AccCruise -> {
                    _accCruiseMode.value = null
                    _accCruiseVSetDisKmh.value = null
                    _accFrmFeedbackAvailable.value = false
                    _ccsCruiseStatus.value = null
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
                MbCanSignal.WiperMaintenance -> stateEngine.applyWiperMaintenanceCandidate(MbCanBinaryState.Unavailable(reason))
                MbCanSignal.ParkingRadar -> stateEngine.applyParkingRadarCandidate(MbCanBinaryState.Unavailable(reason))
                MbCanSignal.RearFogLight -> stateEngine.applyRearFogCandidate(MbCanBinaryState.Unavailable(reason))
                MbCanSignal.AutoLock, MbCanSignal.AutoUnlock, MbCanSignal.FollowMeHome,
                MbCanSignal.DriverUnlockMode, MbCanSignal.RemoteLockFeedback, MbCanSignal.WiperSensitivity,
                MbCanSignal.RearWiper, MbCanSignal.LowBeamHeight, MbCanSignal.TurnFlashCount -> clearCertifiedCarSettings()
                MbCanSignal.AvhSwitch -> stateEngine.applyAvhCandidate(MbCanBinaryState.Unavailable(reason))
                MbCanSignal.HdcSwitch -> stateEngine.applyHdcCandidate(MbCanBinaryState.Unavailable(reason))
                MbCanSignal.EspOffSwitch -> stateEngine.applyEspOffCandidate(MbCanBinaryState.Unavailable(reason))
                MbCanSignal.LasModeSelection -> _lasModeRaw.value = null
                MbCanSignal.LightControl -> _headlightModeRaw.value = null
                MbCanSignal.TjaIca -> stateEngine.applyTjaIcaCandidate(MbCanBinaryState.Unavailable(reason))
                MbCanSignal.HmaSwitch -> stateEngine.applyHmaCandidate(MbCanBinaryState.Unavailable(reason))
                MbCanSignal.Bsd -> _bsdState.value = MbCanBinaryState.Unavailable(reason)
                MbCanSignal.Dow -> _dowState.value = MbCanBinaryState.Unavailable(reason)
                MbCanSignal.Fcw -> _fcwState.value = MbCanBinaryState.Unavailable(reason)
                MbCanSignal.FcwSensitivity -> _fcwSensitivity.value = null
                MbCanSignal.LdwSensitivity -> _ldwSensitivity.value = null
                MbCanSignal.HvacCustomMode -> HvacClimateCanRepository.applyCustomModeVhal(-1)
                MbCanSignal.HvacAcMax -> stateEngine.applyHvacAcMaxCandidate(MbCanBinaryState.Unavailable(reason))
                MbCanSignal.FrontWindscreenHeat -> stateEngine.applyWindshieldHeatCandidate(MbCanBinaryState.Unavailable(reason))
                MbCanSignal.HvacDefroster -> stateEngine.applyHvacDefrosterCandidate(MbCanBinaryState.Unavailable(reason))
                MbCanSignal.HvacAirRecirculation -> stateEngine.applyHvacAirRecirculationCandidate(MbCanBinaryState.Unavailable(reason))
                MbCanSignal.HvacAcPower -> stateEngine.applyHvacAcPowerCandidate(MbCanBinaryState.Unavailable(reason))
                MbCanSignal.HvacAcCleanWhenLocked -> stateEngine.applyHvacAcCleanWhenLockedCandidate(MbCanBinaryState.Unavailable(reason))
                MbCanSignal.HvacAutoState -> stateEngine.applyHvacAutoStateCandidate(MbCanBinaryState.Unavailable(reason))
                MbCanSignal.FirstBlowing -> _firstBlowingState.value = MbCanBinaryState.Unavailable(reason)
                MbCanSignal.BtReduceFan -> _btReduceFanState.value = MbCanBinaryState.Unavailable(reason)
                MbCanSignal.AutoVentilation -> _autoVentilationState.value = MbCanBinaryState.Unavailable(reason)
                MbCanSignal.HvacDefrosterFront -> stateEngine.applyHvacDefrosterFrontCandidate(MbCanBinaryState.Unavailable(reason))
                MbCanSignal.HvacFrontOff -> HvacClimateCanRepository.applyFrontOffVhal(0)
                MbCanSignal.HvacTempLeft -> HvacClimateCanRepository.applyTempLeftVhal(-1)
                MbCanSignal.HvacTempRight -> HvacClimateCanRepository.applyTempRightVhal(-1)
                MbCanSignal.HvacFanSpeed -> HvacClimateCanRepository.applyFanSpeed(-1)
                MbCanSignal.HvacSync -> HvacClimateCanRepository.applySyncVhal(-1)
                MbCanSignal.HvacBlowMode -> HvacClimateCanRepository.applyBlowModeVhal(-1)
                MbCanSignal.HudSwitch -> _hudSwitchState.value = MbCanBinaryState.Unavailable(reason)
                MbCanSignal.HudHeight -> _hudHeight.value = null
                MbCanSignal.HudBrightness -> _hudBrightness.value = null
                MbCanSignal.HudDisplayMode -> _hudDisplayMode.value = null
                MbCanSignal.HudAutoBrightness -> _hudAutoBrightnessState.value = MbCanBinaryState.Unavailable(reason)
                MbCanSignal.OverspeedAlarm -> _overspeedAlarmKmh.value = null
                MbCanSignal.TrunkDoor -> TrunkDoorRepository.clear()
                MbCanSignal.AudioVolumeSpeed -> {
                    _audioVolumeSpeedModeState.value = null
                    stateEngine.applyVolumeSpeedCandidate(MbCanBinaryState.Unavailable(reason))
                }
                MbCanSignal.FrontLeftSeatMode ->
                    stateEngine.applySeatCandidate(MbCanSeatSlot.FrontLeft, MbCanSeatModeState.Unavailable(reason))
                MbCanSignal.FrontRightSeatMode ->
                    stateEngine.applySeatCandidate(MbCanSeatSlot.FrontRight, MbCanSeatModeState.Unavailable(reason))
                MbCanSignal.RearLeftSeatMode ->
                    stateEngine.applySeatCandidate(MbCanSeatSlot.RearLeft, MbCanSeatModeState.Unavailable(reason))
                MbCanSignal.RearRightSeatMode ->
                    stateEngine.applySeatCandidate(MbCanSeatSlot.RearRight, MbCanSeatModeState.Unavailable(reason))
                MbCanSignal.AudioVolume -> _audioVolumeState.value = null
                MbCanSignal.EngineRpm -> _engineRpmState.value = null
                MbCanSignal.EngineTemperature -> _engineTemperatureState.value = null
                MbCanSignal.CarSpeed -> clearCarSpeedCacheAndState()
                MbCanSignal.VehicleGear -> _gearBoxModeState.value = null
                MbCanSignal.ReverseGearSwitch -> _reverseGearSwitchState.value = null
                MbCanSignal.FuelLevel -> _fuelLevelPercentState.value = null
                MbCanSignal.TotalOdometer -> _odometerKmState.value = null
                MbCanSignal.OutsideTemperature -> _outsideTemperatureState.value = null
                MbCanSignal.VehicleTires -> {
                    _wheelsPressureState.value = Wheels()
                    _wheelsTemperatureState.value = Wheels()
                }
                MbCanSignal.CurrentFuelConsumption -> _currentFuelConsumptionState.value = null
                MbCanSignal.DistanceToNextMaintenance -> _distanceToNextMaintenanceKmState.value = null
                MbCanSignal.DistanceToFuelEmpty -> _distanceToFuelEmptyKmState.value = null
                MbCanSignal.Pm25AirQuality -> {
                    _insideAirQualityState.value = null
                    _outsideAirQualityState.value = null
                }
                MbCanSignal.SteeringAngle -> {
                    _steerAngleState.value = null
                    _steerSpeedState.value = null
                }
                MbCanSignal.CarSettingsVehicleParams -> {
                    _carSettingsEpsMode.value = null
                    _carSettingsDriveMode.value = null
                    _carSettingsDriveMode6dctWet.value = null
                }
                MbCanSignal.SlaSpeedLimit -> {
                    _slaRecognizedSpeedLimitKmh.value = null
                    _slaOnOffState.value = MbCanBinaryState.Unavailable(reason)
                    clearSlaSignUiState()
                }
                MbCanSignal.SpeedLimiter -> {
                    _speedLimiterState.value = MbCanBinaryState.Unavailable(reason)
                }
                MbCanSignal.AccCruise -> {
                    _accCruiseMode.value = null
                    _accCruiseVSetDisKmh.value = null
                    _accFrmFeedbackAvailable.value = false
                    _ccsCruiseStatus.value = null
                }
                MbCanSignal.WirelessChargingSwitch -> Unit
            }
            return
        }

        when (signal) {
            MbCanSignal.AutoLock, MbCanSignal.AutoUnlock, MbCanSignal.FollowMeHome,
            MbCanSignal.DriverUnlockMode, MbCanSignal.RemoteLockFeedback, MbCanSignal.WiperSensitivity,
            MbCanSignal.RearWiper, MbCanSignal.LowBeamHeight, MbCanSignal.TurnFlashCount -> refreshCertifiedCarSettings(signal)
            MbCanSignal.SteeringWheelHeat -> {
                val propertyId = FirmwareVehicleJsonMapper
                    .resolveReadPropertyId(MbCanKnownVehiclePropertyId.STEERING_WHEEL_HEAT_SWITCH)
                    ?: MbCanKnownVehiclePropertyId.STEERING_WHEEL_HEAT_SWITCH
                val raw = bridge?.getIntProperty(propertyId)
                stateEngine.applySteeringCandidate(
                    raw?.let(::decodeVhalBinaryOneIsOn) ?: MbCanBinaryState.Unknown
                )
            }
            MbCanSignal.WiperMaintenance -> {
                val propertyId = FirmwareVehicleJsonMapper
                    .resolveReadPropertyId(MbCanKnownVehiclePropertyId.WIPER_MAINTENANCE_SWITCH)
                    ?: MbCanKnownVehiclePropertyId.WIPER_MAINTENANCE_SWITCH
                val raw = bridge?.getIntProperty(propertyId)
                stateEngine.applyWiperMaintenanceCandidate(
                    raw?.let(::decodeVhalBinaryOneIsOn) ?: MbCanBinaryState.Unknown
                )
            }
            MbCanSignal.ParkingRadar -> {
                val propertyId = FirmwareVehicleJsonMapper
                    .resolveReadPropertyId(MbCanKnownVehiclePropertyId.PARKING_RADAR_SWITCH)
                    ?: MbCanKnownVehiclePropertyId.PARKING_RADAR_SWITCH
                val raw = bridge?.getIntProperty(propertyId)
                stateEngine.applyParkingRadarCandidate(
                    raw?.let(::decodeVhalBinaryOneIsOn) ?: MbCanBinaryState.Unknown
                )
            }
            MbCanSignal.RearFogLight -> {
                val propertyId = FirmwareVehicleJsonMapper
                    .resolveReadPropertyId(MbCanKnownVehiclePropertyId.REAR_FOG_LIGHT)
                    ?: MbCanKnownVehiclePropertyId.REAR_FOG_LIGHT
                val raw = bridge?.getIntProperty(propertyId)
                stateEngine.applyRearFogCandidate(
                    raw?.let(::decodeVhalBinaryOneIsOn) ?: MbCanBinaryState.Unknown
                )
            }
            MbCanSignal.AvhSwitch -> {
                val propertyId = FirmwareVehicleJsonMapper
                    .resolveReadPropertyId(MbCanKnownVehiclePropertyId.AVH_SWITCH)
                    ?: MbCanKnownVehiclePropertyId.AVH_SWITCH
                val raw = bridge?.getIntProperty(propertyId)
                stateEngine.applyAvhCandidate(
                    raw?.let(MbCanSignalStateEngine::decodeAvhHdcStatusRaw) ?: MbCanBinaryState.Unknown
                )
            }
            MbCanSignal.HdcSwitch -> {
                val propertyId = FirmwareVehicleJsonMapper
                    .resolveReadPropertyId(MbCanKnownVehiclePropertyId.HDC_SWITCH)
                    ?: MbCanKnownVehiclePropertyId.HDC_SWITCH
                val raw = bridge?.getIntProperty(propertyId)
                stateEngine.applyHdcCandidate(
                    raw?.let(MbCanSignalStateEngine::decodeAvhHdcStatusRaw) ?: MbCanBinaryState.Unknown
                )
            }
            MbCanSignal.EspOffSwitch -> {
                val propertyId = FirmwareVehicleJsonMapper
                    .resolveReadPropertyId(MbCanKnownVehiclePropertyId.ESP_OFF_SWITCH)
                    ?: MbCanKnownVehiclePropertyId.ESP_OFF_SWITCH
                val raw = bridge?.getIntProperty(propertyId)
                stateEngine.applyEspOffCandidate(
                    raw?.let(MbCanSignalStateEngine::decodeEspOffStatusRaw) ?: MbCanBinaryState.Unknown
                )
            }
            MbCanSignal.LasModeSelection -> {
                val raw = readMappedIntProperty(MbCanKnownVehiclePropertyId.LAS_MODE_SELECTION)
                _lasModeRaw.value = raw?.let { MbCanSignalStateEngine.decodeLasModeRaw(it) }
            }
            MbCanSignal.LightControl -> {
                val raw = readMappedIntProperty(MbCanKnownVehiclePropertyId.LIGHTCONTROL)
                _headlightModeRaw.value = raw?.let { MbCanSignalStateEngine.decodeLightControlRaw(it) }
            }
            MbCanSignal.TjaIca -> {
                val raw = readMappedIntProperty(MbCanKnownVehiclePropertyId.TJA_ICA_SWITCH)
                stateEngine.applyTjaIcaCandidate(
                    raw?.let(::decodeVhalBinaryOneIsOn) ?: MbCanBinaryState.Unknown
                )
            }
            MbCanSignal.HmaSwitch -> {
                val raw = readMappedIntProperty(MbCanKnownVehiclePropertyId.HMA_SWITCH)
                stateEngine.applyHmaCandidate(
                    raw?.let(::decodeVhalBinaryOneIsOn) ?: MbCanBinaryState.Unknown
                )
            }
            MbCanSignal.Bsd -> _bsdState.value = readMappedIntProperty(
                MbCanKnownVehiclePropertyId.BLIND_AREA_DETECTION
            )?.let(::decodeVhalBinaryOneIsOn) ?: MbCanBinaryState.Unknown
            MbCanSignal.Dow -> _dowState.value = readMappedIntProperty(
                MbCanKnownVehiclePropertyId.DOOR_OPEN_WARNING
            )?.let(::decodeVhalBinaryOneIsOn) ?: MbCanBinaryState.Unknown
            MbCanSignal.Fcw -> _fcwState.value = readMappedIntProperty(
                MbCanKnownVehiclePropertyId.FCW_SWITCH
            )?.let(::decodeVhalBinaryOneIsOn) ?: MbCanBinaryState.Unknown
            MbCanSignal.FcwSensitivity -> _fcwSensitivity.value = readMappedIntProperty(
                MbCanKnownVehiclePropertyId.FCW_SENSITIVITY
            )?.let(CarSettingsAdasDomain::decodeFcwSensitivityVhal)
            MbCanSignal.LdwSensitivity -> _ldwSensitivity.value = readMappedIntProperty(
                MbCanKnownVehiclePropertyId.LAS_SENSITIVITY_LEVEL
            )?.let(CarSettingsAdasDomain::decodeLdwSensitivityVhal)
            MbCanSignal.HvacCustomMode -> {
                val raw = readMappedIntProperty(MbCanKnownVehiclePropertyId.HVAC_CUSTOM)
                if (raw == null) HvacClimateCanRepository.applyCustomModeVhal(-1)
                else HvacClimateCanRepository.applyCustomModeVhal(raw)
            }
            MbCanSignal.HvacAcMax -> {
                val raw = readMappedIntProperty(MbCanKnownVehiclePropertyId.HVAC_AC_MAX)
                stateEngine.applyHvacAcMaxCandidate(
                    raw?.let(MbCanSignalStateEngine::decodeHvacAcMaxVhalRaw) ?: MbCanBinaryState.Unknown
                )
            }
            MbCanSignal.FrontWindscreenHeat -> {
                val propertyId = FirmwareVehicleJsonMapper
                    .resolveReadPropertyId(MbCanKnownVehiclePropertyId.FRONT_WINDSCREEN_HEAT_SWITCH)
                    ?: MbCanKnownVehiclePropertyId.FRONT_WINDSCREEN_HEAT_SWITCH
                val raw = bridge?.getIntProperty(propertyId)
                stateEngine.applyWindshieldHeatCandidate(
                    raw?.let(::decodeVhalBinaryOneIsOn) ?: MbCanBinaryState.Unknown
                )
            }
            MbCanSignal.HvacDefroster -> {
                val propertyId = FirmwareVehicleJsonMapper
                    .resolveReadPropertyId(MbCanKnownVehiclePropertyId.HVAC_DEFROSTER_SWITCH)
                    ?: MbCanKnownVehiclePropertyId.HVAC_DEFROSTER_SWITCH
                val raw = bridge?.getIntProperty(propertyId)
                stateEngine.applyHvacDefrosterCandidate(
                    raw?.let(::decodeVhalBinaryOneIsOn) ?: MbCanBinaryState.Unknown
                )
            }
            MbCanSignal.HvacAirRecirculation -> {
                val propertyId = FirmwareVehicleJsonMapper
                    .resolveReadPropertyId(MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION)
                    ?: MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION
                val raw = bridge?.getIntProperty(propertyId)
                stateEngine.applyHvacAirRecirculationCandidate(
                    raw?.let(::decodeVhalBinaryOneIsOn) ?: MbCanBinaryState.Unknown
                )
            }
            MbCanSignal.HvacAcPower -> {
                val propertyId = FirmwareVehicleJsonMapper
                    .resolveReadPropertyId(MbCanKnownVehiclePropertyId.HVAC_POWER)
                    ?: MbCanKnownVehiclePropertyId.HVAC_POWER
                val raw = bridge?.getIntProperty(propertyId)
                stateEngine.applyHvacAcPowerCandidate(
                    raw?.let(::decodeVhalBinaryOneIsOn) ?: MbCanBinaryState.Unknown
                )
            }
            MbCanSignal.HvacAcCleanWhenLocked -> {
                val propertyId = FirmwareVehicleJsonMapper
                    .resolveReadPropertyId(MbCanKnownVehiclePropertyId.HVAC_BLOWER_DELAY)
                    ?: MbCanKnownVehiclePropertyId.HVAC_BLOWER_DELAY
                val raw = bridge?.getIntProperty(propertyId)
                stateEngine.applyHvacAcCleanWhenLockedCandidate(
                    raw?.let(::decodeVhalBinaryOneIsOn) ?: MbCanBinaryState.Unknown
                )
            }
            MbCanSignal.HvacAutoState -> {
                val propertyId = FirmwareVehicleJsonMapper
                    .resolveReadPropertyId(MbCanKnownVehiclePropertyId.HVAC_AUTO_STATE)
                    ?: MbCanKnownVehiclePropertyId.HVAC_AUTO_STATE
                val raw = bridge?.getIntProperty(propertyId)
                stateEngine.applyHvacAutoStateCandidate(
                    raw?.let(::decodeVhalBinaryOneIsOn) ?: MbCanBinaryState.Unknown
                )
            }
            MbCanSignal.FirstBlowing ->
                _firstBlowingState.value = readMappedIntProperty(MbCanKnownVehiclePropertyId.POWER_FIRST_BREATH)
                    ?.let(::decodeVhalBinaryOneIsOn) ?: MbCanBinaryState.Unknown
            MbCanSignal.BtReduceFan ->
                _btReduceFanState.value = readMappedIntProperty(MbCanKnownVehiclePropertyId.BT_REDUCED_WIND_SPEED)
                    ?.let(::decodeVhalBinaryOneIsOn) ?: MbCanBinaryState.Unknown
            MbCanSignal.AutoVentilation ->
                _autoVentilationState.value = readMappedIntProperty(MbCanKnownVehiclePropertyId.HVAC_VENTILATION_AUTO_SWITCH)
                    ?.let(::decodeVhalBinaryOneIsOn) ?: MbCanBinaryState.Unknown
            MbCanSignal.HvacDefrosterFront -> {
                val raw = readMappedIntProperty(MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION)
                stateEngine.applyHvacDefrosterFrontCandidate(
                    raw?.let(MbCanSignalStateEngine::decodeHvacFrontDefrostVhalRaw) ?: MbCanBinaryState.Unknown
                )
                raw?.let { HvacClimateCanRepository.applyBlowModeVhal(it) }
            }
            MbCanSignal.HvacFrontOff -> {
                readMappedIntProperty(MbCanKnownVehiclePropertyId.HVAC_FRONT_OFF)
                    ?.let { HvacClimateCanRepository.applyFrontOffVhal(it) }
            }
            MbCanSignal.HvacTempLeft -> {
                readMappedIntProperty(MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_LEFT)
                    ?.let { HvacClimateCanRepository.applyTempLeftVhal(it) }
            }
            MbCanSignal.HvacTempRight -> {
                readMappedIntProperty(MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_RIGHT)
                    ?.let { HvacClimateCanRepository.applyTempRightVhal(it) }
            }
            MbCanSignal.HvacFanSpeed -> {
                readMappedIntProperty(MbCanKnownVehiclePropertyId.HVAC_FAN_SPEED)
                    ?.let { HvacClimateCanRepository.applyFanSpeed(it) }
            }
            MbCanSignal.HvacSync -> {
                readMappedIntProperty(MbCanKnownVehiclePropertyId.HVAC_SYNC_SWITCH)
                    ?.let { HvacClimateCanRepository.applySyncVhal(it) }
            }
            MbCanSignal.HvacBlowMode -> {
                readMappedIntProperty(MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION)
                    ?.let { HvacClimateCanRepository.applyBlowModeVhal(it) }
            }
            MbCanSignal.HudSwitch ->
                _hudSwitchState.value = readMappedIntProperty(MbCanKnownVehiclePropertyId.HUD_SWITCH)
                    ?.let(::decodeVhalBinaryOneIsOn) ?: MbCanBinaryState.Unknown
            MbCanSignal.HudHeight ->
                _hudHeight.value = readMappedIntProperty(MbCanKnownVehiclePropertyId.HUD_HEIGHT)?.takeIf { it in 1..10 }
            MbCanSignal.HudBrightness ->
                _hudBrightness.value = readMappedIntProperty(MbCanKnownVehiclePropertyId.HUD_BRIGHTNESS)?.takeIf { it in 1..10 }
            MbCanSignal.HudDisplayMode ->
                _hudDisplayMode.value = readMappedIntProperty(MbCanKnownVehiclePropertyId.HUD_DISPLAY_MODE)?.takeIf { it in 1..2 }
            MbCanSignal.HudAutoBrightness ->
                _hudAutoBrightnessState.value = readMappedIntProperty(MbCanKnownVehiclePropertyId.HUD_AUTO_BRIGHTNESS)
                    ?.let(::decodeVhalBinaryOneIsOn) ?: MbCanBinaryState.Unknown
            MbCanSignal.OverspeedAlarm ->
                _overspeedAlarmKmh.value = readMappedIntProperty(MbCanKnownVehiclePropertyId.OVERSPEED_ALARM_SET)
                    ?.let(CarSettingsHudDomain::decodeOverspeedKmh)
            MbCanSignal.TrunkDoor -> {
                readMappedIntProperty(MbCanKnownVehiclePropertyId.TRUNK_STATUS)
                    ?.let { TrunkDoorRepository.applyVhalOpenRaw(it) }
                readMappedIntProperty(MbCanKnownVehiclePropertyId.TRUNK_REAR_DOOR_MOVE_DIR)
                    ?.let { TrunkDoorRepository.applyMoveDirRaw(it) }
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
                _audioVolumeSpeedModeState.value = raw?.let(::decodeAudioVolumeSpeedMode)
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
                _carSettingsEpsMode.value = decodeCarSettingsIntZeroToSix(bridge?.getIntProperty(epsId))
                _carSettingsDriveMode.value = decodeCarSettingsIntZeroToSix(bridge?.getIntProperty(driveId))
                _carSettingsDriveMode6dctWet.value = decodeCarSettingsIntZeroToSix(bridge?.getIntProperty(driveWetId))
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
            MbCanSignal.EngineRpm -> {
                _engineRpmState.value = decodeEngineRpm(readNumericProperty(VHAL_ENGINE_RPM_PROPERTY_ID))
            }
            MbCanSignal.EngineTemperature -> {
                _engineTemperatureState.value =
                    decodeEngineTemperature(readNumericProperty(VHAL_ENGINE_TEMPERATURE_PROPERTY_ID))
            }
            MbCanSignal.CarSpeed -> {
                lastVsoSpeedRaw = asSpeedRawFloat(readNumericProperty(VHAL_CAR_SPEED_VSO_SIG_PROPERTY_ID))
                lastDisplaySpeedRaw = asSpeedRawFloat(readNumericProperty(VHAL_CAR_SPEED_DISPLAY_PROPERTY_ID))
                publishCarSpeedFromCachedRaws()
            }
            MbCanSignal.VehicleGear -> {
                val raw = bridge?.getIntProperty(VHAL_GEAR_SELECTION_PROPERTY_ID)
                    ?: bridge?.getIntProperty(VHAL_CURRENT_GEAR_PROPERTY_ID)
                _gearBoxModeState.value = decodeVehicleGear(raw)
            }
            MbCanSignal.ReverseGearSwitch -> {
                _reverseGearSwitchState.value =
                    decodeReverseGearSwitch(bridge?.getIntProperty(VHAL_REVERSE_GEAR_SWITCH_PROPERTY_ID))
            }
            MbCanSignal.FuelLevel -> {
                _fuelLevelPercentState.value =
                    decodeFuelLevelPercent(bridge?.getIntProperty(VHAL_FUEL_LEVEL_PROPERTY_ID))
            }
            MbCanSignal.TotalOdometer -> {
                val raw = bridge?.getIntProperty(VHAL_TOTAL_ODOMETER_KM_PROPERTY_ID)
                    ?: bridge?.getFloatProperty(VHAL_TOTAL_ODOMETER_KM_PROPERTY_ID)
                _odometerKmState.value = decodeOdometerKm(raw)
            }
            MbCanSignal.OutsideTemperature -> {
                _outsideTemperatureState.value = decodeOutsideTemperature(
                    bridge?.getIntProperty(VHAL_EXTERNAL_TEMPERATURE_RAW_PROPERTY_ID)
                )
            }
            MbCanSignal.VehicleTires -> {
                applyVhalTirePressureCorner(0, decodeVhalTirePressure(bridge?.getIntProperty(VHAL_LF_TYRE_PRESSURE)))
                applyVhalTirePressureCorner(1, decodeVhalTirePressure(bridge?.getIntProperty(VHAL_RF_TYRE_PRESSURE)))
                applyVhalTirePressureCorner(2, decodeVhalTirePressure(bridge?.getIntProperty(VHAL_LR_TYRE_PRESSURE)))
                applyVhalTirePressureCorner(3, decodeVhalTirePressure(bridge?.getIntProperty(VHAL_RR_TYRE_PRESSURE)))
                applyVhalTireTemperatureCorner(
                    0,
                    decodeVhalTireTemperature(bridge?.getIntProperty(VHAL_LF_TYRE_TEMPERATURE)),
                )
                applyVhalTireTemperatureCorner(
                    1,
                    decodeVhalTireTemperature(bridge?.getIntProperty(VHAL_RF_TYRE_TEMPERATURE)),
                )
                applyVhalTireTemperatureCorner(
                    2,
                    decodeVhalTireTemperature(bridge?.getIntProperty(VHAL_LR_TYRE_TEMPERATURE)),
                )
                applyVhalTireTemperatureCorner(
                    3,
                    decodeVhalTireTemperature(bridge?.getIntProperty(VHAL_RR_TYRE_TEMPERATURE)),
                )
            }
            MbCanSignal.CurrentFuelConsumption -> {
                val raw = bridge?.getIntProperty(VHAL_FUEL_ROLLING_COUNTER_PROPERTY_ID)
                _currentFuelConsumptionState.value =
                    raw?.let { InstantFuelConsumptionDomain.decodeRawCounter(it) }
            }
            MbCanSignal.DistanceToNextMaintenance -> {
                val raw = bridge?.getIntProperty(VHAL_MAINTENANCE_TIPS_PROPERTY_ID)
                _distanceToNextMaintenanceKmState.value =
                    raw?.let { MaintenanceTipsDomain.decodeKm(it) }
            }
            MbCanSignal.DistanceToFuelEmpty -> {
                val raw = bridge?.getIntProperty(VHAL_DISTANCE_TO_EMPTY_KM_PROPERTY_ID)
                _distanceToFuelEmptyKmState.value =
                    raw?.let { DistanceToEmptyDomain.decodeKm(it) }
            }
            MbCanSignal.Pm25AirQuality -> {
                _insideAirQualityState.value = Pm25AirQualityDomain.decodeDensity(
                    bridge?.getIntProperty(VHAL_PM25_INDENSITY_PROPERTY_ID) ?: -1
                )
                _outsideAirQualityState.value = Pm25AirQualityDomain.decodeDensity(
                    bridge?.getIntProperty(VHAL_PM25_OUTDENSITY_PROPERTY_ID) ?: -1
                )
            }
            MbCanSignal.SteeringAngle -> {
                _steerAngleState.value = null
                _steerSpeedState.value = null
            }
            MbCanSignal.SlaSpeedLimit -> {
                val limitRaw = bridge?.getIntProperty(FirmwareVehicleJsonMapper.VHAL_SLA_SPEED_LIMIT_RAW)
                slaLkaLimitRaw = limitRaw
                _slaRecognizedSpeedLimitKmh.value = limitRaw?.let(SlaSpeedLimitDomain::decodeRecognizedSpeedKmh)
                val onOffRaw = bridge?.getIntProperty(FirmwareVehicleJsonMapper.VHAL_SLA_ON_OFF_STATUS)
                slaLkaOnOffRaw = onOffRaw
                _slaOnOffState.value = onOffRaw?.let(SlaSpeedLimitDomain::decodeSlaOnOffVhalRaw) ?: MbCanBinaryState.Unknown
                slaLkaStateRaw = bridge?.getIntProperty(FirmwareVehicleJsonMapper.VHAL_SLA_STATE)
                publishSlaSignUiState()
            }
            MbCanSignal.SpeedLimiter -> {
                val switchId = FirmwareVehicleJsonMapper
                    .resolveReadPropertyId(MbCanKnownVehiclePropertyId.VEHICLE_SPEEDLIMIT_SWITCH)
                    ?: MbCanKnownVehiclePropertyId.VEHICLE_SPEEDLIMIT_SWITCH
                val raw = bridge?.getIntProperty(switchId)
                _speedLimiterState.value = raw?.let(SlaSpeedLimitDomain::decodeSpeedLimiterSwitchVhalRaw)
                    ?: MbCanBinaryState.Unknown
            }
            MbCanSignal.AccCruise -> {
                _accCruiseMode.value = bridge?.getIntProperty(FirmwareVehicleJsonMapper.VHAL_FRM_ACC_MODE)
                val vSetRaw = bridge?.getIntProperty(FirmwareVehicleJsonMapper.VHAL_FRM_V_SET_DIS)
                _accCruiseVSetDisKmh.value = vSetRaw?.let(AccCruiseDomain::decodeVhalVSetDisKmh)
                if (_accCruiseMode.value != null || vSetRaw != null) {
                    _accFrmFeedbackAvailable.value = true
                }
                val ccsRaw = bridge?.getIntProperty(FirmwareVehicleJsonMapper.VHAL_EMS_CRUISE_CONTROL_STATUS)
                _ccsCruiseStatus.value = ccsRaw?.let(AccCruiseDomain::decodeMbCanCruiseControlStatus)
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
            is MbCanCommand.SetFcwEnabled -> {
                val value = if (command.enabled) 2 else 1
                val ids = listOf(
                    MbCanKnownVehiclePropertyId.FCW_SWITCH,
                    MbCanKnownVehiclePropertyId.ACC_AUTOBRAKE_SWITCH,
                    MbCanKnownVehiclePropertyId.SAFE_DISTANCE_WARNING,
                )
                val ok = ids.all { propertyId ->
                    val effectiveId = FirmwareVehicleJsonMapper.resolveWritePropertyId(propertyId) ?: propertyId
                    bridge?.setIntProperty(effectiveId, value) == true
                }
                if (ok) requestBurstPolling()
                refreshSignal(MbCanSignal.Fcw)
                MbCanCommandResult(ok, "FCW/AEB/distance warning updated")
            }
            is MbCanCommand.ToggleProperty -> {
                val spec = MbCanCommandRegistry.get(command.propertyId)
                    ?: return MbCanCommandResult(false, "No command policy for propertyId=${command.propertyId}")
                val effectivePropertyId = FirmwareVehicleJsonMapper.resolveWritePropertyId(command.propertyId)
                    ?: command.propertyId
                permissionDeniedReasonForProperty(effectivePropertyId)?.let {
                    return MbCanCommandResult(false, it)
                }
                logDebug("ToggleProperty request=${command.propertyId} effective=$effectivePropertyId")
                val target = when (spec.policy) {
                    is MbCanCommandPolicy.ToggleHvacFrontDefrost -> {
                        val readPropertyId = FirmwareVehicleJsonMapper.resolveReadPropertyId(command.propertyId)
                            ?: command.propertyId
                        val currentRaw = bridge?.getIntProperty(readPropertyId) ?: -1
                        MbCanSignalStateEngine.resolveHvacFrontDefrostVhalToggleTarget(currentRaw)
                    }
                    is MbCanCommandPolicy.ToggleBinary -> {
                        if (!isVhalBinaryToggleProperty(command.propertyId)) {
                            return MbCanCommandResult(
                                false,
                                "No VHAL binary toggle mapping for propertyId=${command.propertyId}",
                            )
                        }
                        val readPropertyId = FirmwareVehicleJsonMapper.resolveReadPropertyId(command.propertyId)
                            ?: command.propertyId
                        val currentRaw = bridge?.getIntProperty(readPropertyId)
                        val currentState = currentRaw
                            ?.let { decodeVhalBinaryReadState(command.propertyId, it) }
                            ?: latestBinaryState(command.propertyId)
                        val targetOn = when (currentState) {
                            MbCanBinaryState.On -> false
                            MbCanBinaryState.Off -> true
                            else -> true
                        }
                        encodeVhalBinaryWriteValue(command.propertyId, targetOn)
                            ?: return MbCanCommandResult(
                                false,
                                "No VHAL write mapping for propertyId=${command.propertyId}",
                            )
                    }
                    else -> return MbCanCommandResult(false, "Toggle unsupported for propertyId=${command.propertyId}")
                }
                val ok = bridge?.setIntProperty(effectivePropertyId, target) == true
                logDebug("ToggleProperty result=$ok propertyId=$effectivePropertyId target=$target")
                if (ok) requestBurstPolling()
                spec.refreshSignal?.let { refreshSignal(it) }
                MbCanCommandResult(ok, if (ok) "Set ok" else "Set failed")
            }
            is MbCanCommand.SetProperty -> {
                val spec = MbCanCommandRegistry.get(command.propertyId)
                    ?: return MbCanCommandResult(false, "No command policy for propertyId=${command.propertyId}")
                val allowed = when (val policy = spec.policy) {
                    is MbCanCommandPolicy.SetExact -> policy.allowedValues
                    is MbCanCommandPolicy.ToggleHvacFrontDefrost -> setOf(
                        MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FACE,
                        MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FOOT,
                        MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FACE_FOOT,
                        MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_DEFROST,
                        MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_DEFROST_FOOT,
                    )
                    else -> return MbCanCommandResult(false, "Set unsupported for propertyId=${command.propertyId}")
                }
                if (!allowed.contains(command.value)) {
                    return MbCanCommandResult(false, "Value ${command.value} is not allowed")
                }
                val effectivePropertyId = FirmwareVehicleJsonMapper.resolveWritePropertyId(command.propertyId)
                    ?: command.propertyId
                permissionDeniedReasonForProperty(effectivePropertyId)?.let {
                    return MbCanCommandResult(false, it)
                }
                val encodedValue = encodeVhalSetValue(command.propertyId, command.value)
                    ?: return MbCanCommandResult(false, "Value ${command.value} cannot be encoded for VHAL")
                logDebug(
                    "SetProperty request=${command.propertyId} effective=$effectivePropertyId " +
                        "value=${command.value} encoded=$encodedValue"
                )
                val ok = bridge?.setIntProperty(effectivePropertyId, encodedValue) == true
                logDebug("SetProperty result=$ok propertyId=$effectivePropertyId encoded=$encodedValue")
                if (ok) requestBurstPolling()
                spec.refreshSignal?.let { refreshSignal(it) }
                MbCanCommandResult(ok, if (ok) "Set ok" else "Set failed")
            }
            is MbCanCommand.TrunkPulse -> {
                if (command.value !in setOf(1, 2)) {
                    return MbCanCommandResult(false, "Trunk pulse value ${command.value} is not allowed")
                }
                val effectivePropertyId = FirmwareVehicleJsonMapper.resolveWritePropertyId(
                    MbCanKnownVehiclePropertyId.TRUNK_PLG_CONTROL
                ) ?: MbCanKnownVehiclePropertyId.TRUNK_PLG_CONTROL
                permissionDeniedReasonForProperty(effectivePropertyId)?.let {
                    return MbCanCommandResult(false, it)
                }
                val firstOk = bridge?.setIntProperty(effectivePropertyId, command.value) == true
                if (!firstOk) {
                    return MbCanCommandResult(false, "Trunk pulse failed")
                }
                delay(HvacClimateDomain.TRUNK_PULSE_RESET_MS)
                bridge?.setIntProperty(effectivePropertyId, 0)
                requestBurstPolling()
                refreshSignal(MbCanSignal.TrunkDoor)
                MbCanCommandResult(true, "Trunk pulse sent")
            }
            is MbCanCommand.ToggleAudioProperty -> {
                val spec = MbCanAudioCommandRegistry.get(command.propertyId)
                    ?: return MbCanCommandResult(false, "No audio command policy for propertyId=${command.propertyId}")
                val policy = spec.policy as? MbCanCommandPolicy.ToggleBinary
                    ?: return MbCanCommandResult(false, "Toggle unsupported for audio propertyId=${command.propertyId}")
                val effectivePropertyId = FirmwareVehicleJsonMapper.resolveWritePropertyId(command.propertyId)
                    ?: command.propertyId
                permissionDeniedReasonForProperty(effectivePropertyId)?.let {
                    return MbCanCommandResult(false, it)
                }
                logDebug("ToggleAudioProperty request=${command.propertyId} effective=$effectivePropertyId")
                val current = bridge?.getIntProperty(effectivePropertyId)
                    ?: return MbCanCommandResult(false, "Audio property read failed")
                val target = when (current) {
                    policy.onValue -> policy.offValue
                    policy.offValue -> policy.onValue
                    else -> policy.unknownFallbackValue
                }
                val ok = bridge?.setIntProperty(effectivePropertyId, target) == true
                logDebug("ToggleAudioProperty result=$ok propertyId=$effectivePropertyId current=$current target=$target")
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
                permissionDeniedReasonForProperty(effectivePropertyId)?.let {
                    return MbCanCommandResult(false, it)
                }
                logDebug(
                    "SetAudioProperty request=${command.propertyId} effective=$effectivePropertyId " +
                        "value=${command.value}"
                )
                val ok = bridge?.setIntProperty(effectivePropertyId, command.value) == true
                logDebug("SetAudioProperty result=$ok propertyId=$effectivePropertyId value=${command.value}")
                if (ok) requestBurstPolling()
                spec.refreshSignal?.let { refreshSignal(it) }
                MbCanCommandResult(ok, if (ok) "Set ok" else "Set failed")
            }
            is MbCanCommand.RefreshSignal -> {
                logDebug("RefreshSignal ${command.signal}")
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
        permissionDeniedReasonForProperty(effectiveVolumeId)?.let {
            return MbCanCommandResult(false, it)
        }
        logDebug("setAudioVolume request=$value effectivePropertyId=$effectiveVolumeId")
        val ok = bridge?.setIntProperty(effectiveVolumeId, target) == true
        logDebug("setAudioVolume result=$ok value=$target propertyId=$effectiveVolumeId")
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

    private fun decodeAudioVolumeSpeedMode(raw: Int): Int? = raw.takeIf { it in 1..4 }

    private fun cancelDebouncedClearSource(sourceId: String) {
        pendingDebouncedClearJobs.remove(sourceId)?.cancel()
    }
}
