package vad.dashing.tbox.mbcan

import android.content.Context
import android.content.ServiceConnection
import android.os.Handler
import android.os.Looper
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
import vad.dashing.tbox.DRIVE_MODE_WIDGET_DATA_KEY
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
import vad.dashing.tbox.PARKING_RADAR_WIDGET_DATA_KEY
import vad.dashing.tbox.REAR_LEFT_SEAT_HEAT_WIDGET_DATA_KEY
import vad.dashing.tbox.REAR_RIGHT_SEAT_HEAT_WIDGET_DATA_KEY
import vad.dashing.tbox.SLA_SPEED_LIMIT_WIDGET_DATA_KEY
import vad.dashing.tbox.SPEED_LIMITER_WIDGET_DATA_KEY
import vad.dashing.tbox.WIPER_MAINTENANCE_WIDGET_DATA_KEY

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
    private val VHAL_CAR_SPEED_PROPERTY_ID = FirmwareVehicleJsonMapper.VHAL_CAR_SPEED_PROPERTY_ID
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
    private val _wiperMaintenanceState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val wiperMaintenanceState: StateFlow<MbCanBinaryState> = _wiperMaintenanceState.asStateFlow()
    private val _parkingRadarState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val parkingRadarState: StateFlow<MbCanBinaryState> = _parkingRadarState.asStateFlow()
    private val _frontWindscreenHeatState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val frontWindscreenHeatState: StateFlow<MbCanBinaryState> = _frontWindscreenHeatState.asStateFlow()
    private val _hvacDefrosterState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val hvacDefrosterState: StateFlow<MbCanBinaryState> = _hvacDefrosterState.asStateFlow()
    private val _hvacAirRecirculationState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val hvacAirRecirculationState: StateFlow<MbCanBinaryState> = _hvacAirRecirculationState.asStateFlow()
    private val _hvacAcPowerState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val hvacAcPowerState: StateFlow<MbCanBinaryState> = _hvacAcPowerState.asStateFlow()
    private val _hvacAutoState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val hvacAutoState: StateFlow<MbCanBinaryState> = _hvacAutoState.asStateFlow()
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

    private val stateEngine = MbCanSignalStateEngine(
        steeringFlow = _steeringWheelHeatState,
        wiperMaintenanceFlow = _wiperMaintenanceState,
        parkingRadarFlow = _parkingRadarState,
        windshieldHeatFlow = _frontWindscreenHeatState,
        hvacDefrosterFlow = _hvacDefrosterState,
        hvacAirRecirculationFlow = _hvacAirRecirculationState,
        hvacAcPowerFlow = _hvacAcPowerState,
        hvacAutoStateFlow = _hvacAutoState,
        hvacDefrosterFrontFlow = _hvacDefrosterFrontState,
        wirelessChargingFlow = MutableStateFlow(MbCanBinaryState.Unknown),
        volumeSpeedFlow = _audioVolumeSpeedState,
        frontLeftSeatFlow = _frontLeftSeatModeState,
        frontRightSeatFlow = _frontRightSeatModeState,
        rearLeftSeatFlow = _rearLeftSeatModeState,
        rearRightSeatFlow = _rearRightSeatModeState
    )

    private data class PushDebugBucket(
        val count: Int,
        val areaId: Int,
        val value: Any?,
    )

    private fun currentUnavailableReason(): String =
        (availability.value as? MbCanAvailability.Unavailable)?.reason ?: "VHAL unavailable"

    private fun permissionDeniedReasonOrNull(): String? {
        return if (carInfoPermissionDenied) {
            "Missing VHAL permission"
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
            VHAL_CAR_SPEED_PROPERTY_ID,
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
            carInfoPermissionDenied = true
            _availability.value = MbCanAvailability.Unavailable("Missing VHAL permission: ${root.message}")
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
            carInfoPermissionDenied = true
            _availability.value = MbCanAvailability.Unavailable("Missing VHAL permission: ${root.message}")
        }
        MbCanDiagnostics.log(
            level = "WARN",
            tag = LOG_TAG,
            message = "$prefix propertyId=$propertyId areaId=$areaId value=$value " +
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
        logDebug("bind()")
        carInfoPermissionDenied = false
        ensureConnected()
        restartPolling()
    }

    suspend fun unbind() {
        logDebug("unbind()")
        pollJob?.cancel()
        pollJob = null
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

    suspend fun warmUpAvailabilityForUi() {
        logDebug("warmUpAvailabilityForUi()")
        ensureConnected()
    }

    suspend fun setSourceWidgetKeys(sourceId: String, widgetKeys: Set<String>) {
        cancelDebouncedClearSource(sourceId)
        val signals = widgetKeys.mapNotNull { key ->
            when (UniversalCanRepository.normalizeWidgetDataKey(key)) {
                "steeringWheelHeatWidget" -> MbCanSignal.SteeringWheelHeat
                WIPER_MAINTENANCE_WIDGET_DATA_KEY -> MbCanSignal.WiperMaintenance
                PARKING_RADAR_WIDGET_DATA_KEY -> MbCanSignal.ParkingRadar
                "frontWindscreenHeatWidget" -> MbCanSignal.FrontWindscreenHeat
                "rearWindowMirrorsDefrostWidget" -> MbCanSignal.HvacDefroster
                "hvacAirRecirculationWidget" -> MbCanSignal.HvacAirRecirculation
                "hvacAcWidget" -> MbCanSignal.HvacAcPower
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
                SLA_SPEED_LIMIT_WIDGET_DATA_KEY -> MbCanSignal.SlaSpeedLimit
                SPEED_LIMITER_WIDGET_DATA_KEY -> MbCanSignal.SpeedLimiter
                "frontLeftSeatHeatVentWidget" -> MbCanSignal.FrontLeftSeatMode
                "frontRightSeatHeatVentWidget" -> MbCanSignal.FrontRightSeatMode
                FRONT_LEFT_SEAT_HEAT_VENT_SINGLE_WIDGET_DATA_KEY -> MbCanSignal.FrontLeftSeatMode
                FRONT_RIGHT_SEAT_HEAT_VENT_SINGLE_WIDGET_DATA_KEY -> MbCanSignal.FrontRightSeatMode
                REAR_LEFT_SEAT_HEAT_WIDGET_DATA_KEY -> MbCanSignal.RearLeftSeatMode
                REAR_RIGHT_SEAT_HEAT_WIDGET_DATA_KEY -> MbCanSignal.RearRightSeatMode
                else -> null
            }
        }.toSet()
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
                "frontWindscreenHeatWidget",
                "rearWindowMirrorsDefrostWidget",
                "hvacAirRecirculationWidget",
                "hvacAcWidget",
                "hvacAutoWidget",
                "hvacDefrosterFrontWidget",
                DRIVE_MODE_WIDGET_DATA_KEY,
                SLA_SPEED_LIMIT_WIDGET_DATA_KEY,
                SPEED_LIMITER_WIDGET_DATA_KEY,
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
            MbCanSignal.FrontWindscreenHeat -> setOf(resolved(MbCanKnownVehiclePropertyId.FRONT_WINDSCREEN_HEAT_SWITCH))
            MbCanSignal.HvacDefroster -> setOf(resolved(MbCanKnownVehiclePropertyId.HVAC_DEFROSTER_SWITCH))
            MbCanSignal.HvacAirRecirculation -> setOf(resolved(MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION))
            MbCanSignal.HvacAcPower -> setOf(resolved(MbCanKnownVehiclePropertyId.HVAC_POWER))
            MbCanSignal.HvacAutoState -> setOf(resolved(MbCanKnownVehiclePropertyId.HVAC_AUTO_STATE))
            MbCanSignal.HvacDefrosterFront -> setOf(resolved(MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION))
            MbCanSignal.HvacFrontOff -> setOf(resolved(MbCanKnownVehiclePropertyId.HVAC_FRONT_OFF))
            MbCanSignal.HvacTempLeft -> setOf(resolved(MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_LEFT))
            MbCanSignal.HvacTempRight -> setOf(resolved(MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_RIGHT))
            MbCanSignal.HvacFanSpeed -> setOf(resolved(MbCanKnownVehiclePropertyId.HVAC_FAN_SPEED))
            MbCanSignal.HvacSync -> setOf(resolved(MbCanKnownVehiclePropertyId.HVAC_SYNC_SWITCH))
            MbCanSignal.HvacBlowMode -> setOf(resolved(MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION))
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
            MbCanSignal.FrontLeftSeatMode -> setOf(resolved(MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH))
            MbCanSignal.FrontRightSeatMode -> setOf(resolved(MbCanKnownVehiclePropertyId.FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH))
            MbCanSignal.RearLeftSeatMode -> setOf(resolved(MbCanKnownVehiclePropertyId.REAR_LEFT_SEAT_HEAT_SWITCH))
            MbCanSignal.RearRightSeatMode -> setOf(resolved(MbCanKnownVehiclePropertyId.REAR_RIGHT_SEAT_HEAT_SWITCH))
            MbCanSignal.WirelessChargingSwitch -> emptySet()
            MbCanSignal.EngineRpm -> setOf(VHAL_ENGINE_RPM_PROPERTY_ID)
            MbCanSignal.EngineTemperature -> setOf(VHAL_ENGINE_TEMPERATURE_PROPERTY_ID)
            MbCanSignal.CarSpeed -> setOf(VHAL_CAR_SPEED_PROPERTY_ID)
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

    private fun decodeCarSpeed(raw: Any?): Float? {
        return (raw as? Number)?.toFloat()?.coerceAtLeast(0f)
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
    // Keep this decoder local to Android10VhalRepository to avoid affecting mbCAN behavior.
    /** Stock VHAL binary ON/OFF read: selected when raw == 1, otherwise off. */
    private fun decodeVhalBinaryOneIsOn(raw: Int): MbCanBinaryState =
        if (raw == 1) MbCanBinaryState.On else MbCanBinaryState.Off

    private fun isVhalBinaryToggleProperty(propertyId: Int): Boolean = when (propertyId) {
        MbCanKnownVehiclePropertyId.STEERING_WHEEL_HEAT_SWITCH,
        MbCanKnownVehiclePropertyId.WIPER_MAINTENANCE_SWITCH,
        MbCanKnownVehiclePropertyId.PARKING_RADAR_SWITCH,
        MbCanKnownVehiclePropertyId.FRONT_WINDSCREEN_HEAT_SWITCH,
        MbCanKnownVehiclePropertyId.HVAC_DEFROSTER_SWITCH,
        MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION,
        MbCanKnownVehiclePropertyId.HVAC_POWER,
        MbCanKnownVehiclePropertyId.HVAC_AUTO_STATE -> true
        MbCanKnownVehiclePropertyId.HVAC_SYNC_SWITCH,
        MbCanKnownVehiclePropertyId.HVAC_FRONT_OFF -> true
        else -> false
    }

    private fun decodeVhalBinaryReadState(propertyId: Int, raw: Int): MbCanBinaryState = when (propertyId) {
        MbCanKnownVehiclePropertyId.STEERING_WHEEL_HEAT_SWITCH,
        MbCanKnownVehiclePropertyId.WIPER_MAINTENANCE_SWITCH,
        MbCanKnownVehiclePropertyId.PARKING_RADAR_SWITCH,
        MbCanKnownVehiclePropertyId.FRONT_WINDSCREEN_HEAT_SWITCH,
        MbCanKnownVehiclePropertyId.HVAC_DEFROSTER_SWITCH,
        MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION,
        MbCanKnownVehiclePropertyId.HVAC_POWER,
        MbCanKnownVehiclePropertyId.HVAC_AUTO_STATE ->
            decodeVhalBinaryOneIsOn(raw)
        MbCanKnownVehiclePropertyId.HVAC_SYNC_SWITCH ->
            HvacClimateDomain.decodeHvacSyncVhalRaw(raw)
        MbCanKnownVehiclePropertyId.HVAC_FRONT_OFF ->
            HvacClimateDomain.decodeHvacFrontOffVhalRaw(raw)
        MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION ->
            MbCanSignalStateEngine.decodeHvacFrontDefrostVhalRaw(raw)
        else -> MbCanBinaryState.Unknown
    }

    private fun encodeVhalBinaryWriteValue(propertyId: Int, targetOn: Boolean): Int? = when (propertyId) {
        // Stock CarSettings/HVAC: T_0401_SET_MFS_Heat and T_0401_SET_Wiper_Maintenance use 1=on, 2=off.
        MbCanKnownVehiclePropertyId.STEERING_WHEEL_HEAT_SWITCH,
        MbCanKnownVehiclePropertyId.WIPER_MAINTENANCE_SWITCH,
        // Stock HVAC: T_0201_IHU_5_FrontOFF_Req — selected (climate off) writes 1, else 2.
        MbCanKnownVehiclePropertyId.HVAC_FRONT_OFF ->
            if (targetOn) 1 else 2
        // Stock: these writes use 2=on, 1=off.
        MbCanKnownVehiclePropertyId.PARKING_RADAR_SWITCH,
        MbCanKnownVehiclePropertyId.FRONT_WINDSCREEN_HEAT_SWITCH,
        MbCanKnownVehiclePropertyId.HVAC_DEFROSTER_SWITCH,
        MbCanKnownVehiclePropertyId.HVAC_POWER,
        MbCanKnownVehiclePropertyId.HVAC_AUTO_STATE ->
            if (targetOn) 2 else 1
        // Recirculation: 1=inside(recirc on), 2=outside(recirc off).
        MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION ->
            if (targetOn) MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION_VALUE_ON
            else MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION_VALUE_OFF
        MbCanKnownVehiclePropertyId.HVAC_SYNC_SWITCH ->
            HvacClimateDomain.encodeHvacSyncVhalWrite(targetOn)
        else -> null
    }

    private fun latestBinaryState(propertyId: Int): MbCanBinaryState = when (propertyId) {
        MbCanKnownVehiclePropertyId.STEERING_WHEEL_HEAT_SWITCH -> _steeringWheelHeatState.value
        MbCanKnownVehiclePropertyId.WIPER_MAINTENANCE_SWITCH -> _wiperMaintenanceState.value
        MbCanKnownVehiclePropertyId.PARKING_RADAR_SWITCH -> _parkingRadarState.value
        MbCanKnownVehiclePropertyId.FRONT_WINDSCREEN_HEAT_SWITCH -> _frontWindscreenHeatState.value
        MbCanKnownVehiclePropertyId.HVAC_DEFROSTER_SWITCH -> _hvacDefrosterState.value
        MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION -> _hvacAirRecirculationState.value
        MbCanKnownVehiclePropertyId.HVAC_POWER -> _hvacAcPowerState.value
        MbCanKnownVehiclePropertyId.HVAC_AUTO_STATE -> _hvacAutoState.value
        MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION -> _hvacDefrosterFrontState.value
        else -> MbCanBinaryState.Unknown
    }

    private fun readNumericProperty(propertyId: Int): Float? {
        val asInt = bridge?.getIntProperty(propertyId)
        if (asInt != null) return asInt.toFloat()
        return bridge?.getFloatProperty(propertyId)
    }

    private fun encodeVhalSetValue(propertyId: Int, mbCanValue: Int): Int? = when (propertyId) {
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
            resolved(MbCanKnownVehiclePropertyId.HVAC_AUTO_STATE) ->
                raw?.let {
                    stateEngine.applyHvacAutoStateCandidate(decodeVhalBinaryOneIsOn(it))
                }
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
            VHAL_CAR_SPEED_PROPERTY_ID ->
                _carSpeedState.value = decodeCarSpeed(rawValue)
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
        permissionDeniedReasonOrNull()?.let { deniedReason ->
            when (signal) {
                MbCanSignal.SteeringWheelHeat -> stateEngine.applySteeringCandidate(MbCanBinaryState.Unavailable(deniedReason))
                MbCanSignal.WiperMaintenance -> stateEngine.applyWiperMaintenanceCandidate(MbCanBinaryState.Unavailable(deniedReason))
                MbCanSignal.ParkingRadar -> stateEngine.applyParkingRadarCandidate(MbCanBinaryState.Unavailable(deniedReason))
                MbCanSignal.FrontWindscreenHeat -> stateEngine.applyWindshieldHeatCandidate(MbCanBinaryState.Unavailable(deniedReason))
                MbCanSignal.HvacDefroster -> stateEngine.applyHvacDefrosterCandidate(MbCanBinaryState.Unavailable(deniedReason))
                MbCanSignal.HvacAirRecirculation -> stateEngine.applyHvacAirRecirculationCandidate(MbCanBinaryState.Unavailable(deniedReason))
                MbCanSignal.HvacAcPower -> stateEngine.applyHvacAcPowerCandidate(MbCanBinaryState.Unavailable(deniedReason))
                MbCanSignal.HvacAutoState -> stateEngine.applyHvacAutoStateCandidate(MbCanBinaryState.Unavailable(deniedReason))
                MbCanSignal.HvacDefrosterFront -> stateEngine.applyHvacDefrosterFrontCandidate(MbCanBinaryState.Unavailable(deniedReason))
                MbCanSignal.HvacFrontOff -> HvacClimateCanRepository.applyFrontOffVhal(0)
                MbCanSignal.HvacTempLeft -> HvacClimateCanRepository.applyTempLeftVhal(-1)
                MbCanSignal.HvacTempRight -> HvacClimateCanRepository.applyTempRightVhal(-1)
                MbCanSignal.HvacFanSpeed -> HvacClimateCanRepository.applyFanSpeed(-1)
                MbCanSignal.HvacSync -> HvacClimateCanRepository.applySyncVhal(-1)
                MbCanSignal.HvacBlowMode -> HvacClimateCanRepository.applyBlowModeVhal(-1)
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
                MbCanSignal.CarSpeed -> _carSpeedState.value = null
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
                MbCanSignal.FrontWindscreenHeat -> stateEngine.applyWindshieldHeatCandidate(MbCanBinaryState.Unavailable(reason))
                MbCanSignal.HvacDefroster -> stateEngine.applyHvacDefrosterCandidate(MbCanBinaryState.Unavailable(reason))
                MbCanSignal.HvacAirRecirculation -> stateEngine.applyHvacAirRecirculationCandidate(MbCanBinaryState.Unavailable(reason))
                MbCanSignal.HvacAcPower -> stateEngine.applyHvacAcPowerCandidate(MbCanBinaryState.Unavailable(reason))
                MbCanSignal.HvacAutoState -> stateEngine.applyHvacAutoStateCandidate(MbCanBinaryState.Unavailable(reason))
                MbCanSignal.HvacDefrosterFront -> stateEngine.applyHvacDefrosterFrontCandidate(MbCanBinaryState.Unavailable(reason))
                MbCanSignal.HvacFrontOff -> HvacClimateCanRepository.applyFrontOffVhal(0)
                MbCanSignal.HvacTempLeft -> HvacClimateCanRepository.applyTempLeftVhal(-1)
                MbCanSignal.HvacTempRight -> HvacClimateCanRepository.applyTempRightVhal(-1)
                MbCanSignal.HvacFanSpeed -> HvacClimateCanRepository.applyFanSpeed(-1)
                MbCanSignal.HvacSync -> HvacClimateCanRepository.applySyncVhal(-1)
                MbCanSignal.HvacBlowMode -> HvacClimateCanRepository.applyBlowModeVhal(-1)
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
                MbCanSignal.CarSpeed -> _carSpeedState.value = null
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
            MbCanSignal.HvacAutoState -> {
                val propertyId = FirmwareVehicleJsonMapper
                    .resolveReadPropertyId(MbCanKnownVehiclePropertyId.HVAC_AUTO_STATE)
                    ?: MbCanKnownVehiclePropertyId.HVAC_AUTO_STATE
                val raw = bridge?.getIntProperty(propertyId)
                stateEngine.applyHvacAutoStateCandidate(
                    raw?.let(::decodeVhalBinaryOneIsOn) ?: MbCanBinaryState.Unknown
                )
            }
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
                _carSpeedState.value = readNumericProperty(VHAL_CAR_SPEED_PROPERTY_ID)?.coerceAtLeast(0f)
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
                val effectivePropertyId = FirmwareVehicleJsonMapper.resolveWritePropertyId(command.propertyId)
                    ?: command.propertyId
                logDebug("ToggleProperty request=${command.propertyId} effective=$effectivePropertyId")
                val target = when (spec.policy) {
                    is MbCanCommandPolicy.ToggleHvacFrontDefrost -> {
                        val readPropertyId = FirmwareVehicleJsonMapper.resolveReadPropertyId(command.propertyId)
                            ?: command.propertyId
                        val currentRaw = bridge?.getIntProperty(readPropertyId) ?: -1
                        MbCanSignalStateEngine.resolveHvacFrontDefrostVhalToggleTarget(currentRaw)
                    }
                    is MbCanCommandPolicy.ToggleBinary -> if (isVhalBinaryToggleProperty(command.propertyId)) {
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
                            ?: return MbCanCommandResult(false, "No VHAL write mapping for propertyId=${command.propertyId}")
                    } else {
                        val current = bridge?.getIntProperty(effectivePropertyId)
                            ?: return MbCanCommandResult(false, "Property read failed")
                        when (current) {
                            spec.policy.onValue -> spec.policy.offValue
                            spec.policy.offValue -> spec.policy.onValue
                            else -> spec.policy.unknownFallbackValue
                        }
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
