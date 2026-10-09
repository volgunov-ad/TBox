package vad.dashing.tbox.mbcan

import java.lang.reflect.Constructor
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import android.os.Looper
import vad.dashing.tbox.Wheels

sealed class MbCanAvailability {
    data object Unknown : MbCanAvailability()
    data object Available : MbCanAvailability()
    data class Unavailable(val reason: String) : MbCanAvailability()
}

/**
 * Reflection-only bridge to vendor mbCAN classes.
 * Keeps app build/runtime safe when vendor library is absent.
 */
object MbCanEngineFacade {
    private const val TAG = "MbCanEngineFacade"

    /** OEM/JNI callbacks must never throw back into native. */
    private inline fun oemSafe(label: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "OEM callback $label failed", t)
        }
    }

    /** Deep mode: every OEM push payload goes field-by-field into [DeepCanDiagnostics]. */
    private fun mirrorDeepCallback(methodName: String, args: Array<out Any?>?) {
        if (!MbCanDiagnostics.deepEnabled.value) return
        runCatching {
            DeepCanDiagnostics.recordMbCanObjectFields(
                DeepDiagnosticsCatalog.mbcanCallbackDataType(methodName),
                MbCanObjectDump.flattenArgs(args),
            )
        }.onFailure { android.util.Log.w(TAG, "deep mirror $methodName failed", it) }
    }

    private const val ENGINE_CLASS = "com.mengbo.mbCan.MBCanEngine"
    private const val DATA_TYPE_CLASS = "com.mengbo.mbCan.defines.MBCanDataType"
    private const val WINDOW_CLASS = "com.mengbo.mbCan.entity.MBCanVehicleWindow"

    /**
     * OEM JNI is not thread-safe: concurrent [canGetAudioParam] (UI) and vehicle parse
     * ([mbcan-state-apply]) aborted the process with stack-protector SIGABRT.
     */
    private val nativeCallLock = ReentrantLock()

    private val availabilityRef = AtomicReference<MbCanAvailability>(MbCanAvailability.Unknown)
    private var engineInstance: Any? = null
    private var canGetVehicleParamMethod: Method? = null
    private var canSetVehicleParamMethod: Method? = null
    private var canSetWindowStatusMethod: Method? = null
    private var windowStatusConstructor: Constructor<*>? = null
    private var canGetAudioParamMethod: Method? = null
    private var canSetAudioParamMethod: Method? = null
    private var subscribeMethod: Method? = null
    private var unSubscribeMethod: Method? = null
    private var registerCarSettingsListenerMethod: Method? = null
    private var unregisterCarSettingsListenerMethod: Method? = null
    private var settingsTelemetryProxy: Any? = null
    private var registCmdListenerMethod: Method? = null
    private var unRegistCmdListenerMethod: Method? = null
    private var unRegistCmdListenerListenerMethod: Method? = null
    private var registerLkaSlaListenerMethod: Method? = null
    private var unregisterLkaSlaListenerMethod: Method? = null
    private var registerFrmDectInfoListenerMethod: Method? = null
    private var unregisterFrmDectInfoListenerMethod: Method? = null
    private var registerGaspedStatusListenerMethod: Method? = null
    private var unregisterGaspedStatusListenerMethod: Method? = null
    private var cfgVehicleDataType: Any? = null
    private var cfgAudioDataType: Any? = null
    private var vehicleCfgCmdListenerProxy: Any? = null
    private var audioCfgCmdListenerProxy: Any? = null
    /** Deep diagnostics: raw [IMBCmdListener] proxies per non-CFG data type. */
    private val deepCmdListenerProxies = mutableMapOf<String, Any>()
    /** Deep diagnostics fan-out from the production CFG listeners (OEM unRegister clears a whole type). */
    @Volatile
    private var onCfgCmdDeepDiagnosticEvent: ((source: String, modular: Int, rev: Int, item: Int, value: Int) -> Unit)? = null
    private var lkaSlaStatusListenerProxy: Any? = null
    private var frmDectInfoListenerProxy: Any? = null
    private var gaspedStatusListenerProxy: Any? = null
    /** Shared OEM hardkey proxy; fans out to production + diagnostic listeners. */
    private var hardKeyListenerProxy: Any? = null
    private val hardKeyListenersLock = Any()
    private val hardKeyListeners = mutableListOf<(keyCode: Int, keyStatus: Int, keyType: Int) -> Unit>()
    @Volatile private var onHardKeyDiagnosticEvent: ((keyCode: Int, keyStatus: Int, keyType: Int) -> Unit)? = null
    /** [IMBVehicleListener] for steer + turn-light push; field set without OEM unSubscribe side-effects. */
    @Volatile private var vehicleListenerWantSteer = false
    @Volatile private var vehicleListenerWantTurnLights = false
    @Volatile private var vehicleListenerWantWheelPulse = false
    private var imbVehicleListenerProxy: Any? = null
    private var initialized = false

    val availability: MbCanAvailability
        get() = availabilityRef.get()

    fun isInitialized(): Boolean = initialized

    @Synchronized
    fun probeAvailability(): MbCanAvailability {
        if (availabilityRef.get() is MbCanAvailability.Available && initialized) {
            return MbCanAvailability.Available
        }
        return try {
            Class.forName(ENGINE_CLASS, false, MbCanEngineFacade::class.java.classLoader)
            MbCanAvailability.Unknown
        } catch (t: Throwable) {
            MbCanAvailability.Unavailable("${t.javaClass.simpleName}: ${t.message ?: "unknown"}")
        }.also { availabilityRef.set(it) }
    }

    @Synchronized
    fun ensureInitialized(): MbCanAvailability {
        if (availabilityRef.get() is MbCanAvailability.Available) return MbCanAvailability.Available
        try {
            val engineClass = Class.forName(ENGINE_CLASS)
            val getInstance = engineClass.getMethod("getInstance")
            val instance = getInstance.invoke(null) ?: run {
                val unavailable = MbCanAvailability.Unavailable("MBCanEngine.getInstance() returned null")
                availabilityRef.set(unavailable)
                return unavailable
            }
            engineInstance = instance
            canGetVehicleParamMethod = engineClass.getMethod("canGetVehicleParam", Int::class.javaPrimitiveType)
            canSetVehicleParamMethod =
                engineClass.getMethod("canSetVehicleParam", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            val windowClass = runCatching { Class.forName(WINDOW_CLASS) }.getOrNull()
            if (windowClass != null) {
                windowStatusConstructor = runCatching {
                    windowClass.getConstructor(
                        Byte::class.javaPrimitiveType,
                        Byte::class.javaPrimitiveType,
                        Byte::class.javaPrimitiveType,
                        Byte::class.javaPrimitiveType,
                    )
                }.getOrNull()
                canSetWindowStatusMethod = runCatching {
                    engineClass.getMethod("canSetWindowStatus", windowClass)
                }.getOrNull()
            }
            canGetAudioParamMethod =
                engineClass.getMethod("canGetAudioParam", Int::class.javaPrimitiveType)
            canSetAudioParamMethod =
                engineClass.getMethod("canSetAudioParam", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            subscribeMethod = engineClass.getMethod("subscribeCanDataWithList", ArrayList::class.java)
            unSubscribeMethod = engineClass.getMethod("unSubscribeCanDataWithList", ArrayList::class.java)
            registerCarSettingsListenerMethod =
                engineClass.getMethod("registIMBCarSettingsListener", Class.forName("com.mengbo.mbCan.interfaces.IMBCanSettingsCallback"))
            unregisterCarSettingsListenerMethod = engineClass.getMethod("unregistIMBCarSettingsListener")
            registCmdListenerMethod = engineClass.getMethod(
                "registCMDListener",
                Class.forName(DATA_TYPE_CLASS),
                Class.forName("com.mengbo.mbCan.interfaces.IMBCmdListener")
            )
            unRegistCmdListenerMethod = engineClass.getMethod("unRegistCMDListener", Class.forName(DATA_TYPE_CLASS))
            unRegistCmdListenerListenerMethod = runCatching {
                engineClass.getMethod(
                    "unRegistCMDListener",
                    Class.forName(DATA_TYPE_CLASS),
                    Class.forName("com.mengbo.mbCan.interfaces.IMBCmdListener")
                )
            }.getOrNull()
            registerLkaSlaListenerMethod = runCatching {
                engineClass.getMethod(
                    "registIMBCanVehicleLkaSlaStatusListener",
                    Class.forName("com.mengbo.mbCan.interfaces.IMBCanVehicleLkaSlaStatusCallback")
                )
            }.getOrNull()
            unregisterLkaSlaListenerMethod = runCatching {
                engineClass.getMethod("unRegistIMBCanVehicleLkaSlaStatusListener")
            }.getOrNull()
            registerFrmDectInfoListenerMethod = runCatching {
                engineClass.getMethod(
                    "registIMBVehicleFrmDectInfoListener",
                    Class.forName("com.mengbo.mbCan.interfaces.IMBCanVehicleFrmDectInfoCallback")
                )
            }.getOrNull()
            unregisterFrmDectInfoListenerMethod = runCatching {
                engineClass.getMethod("unRegistIMBVehicleFrmDectInfoListener")
            }.getOrNull()
            registerGaspedStatusListenerMethod = runCatching {
                engineClass.getMethod(
                    "registIMBCanVehicleGaspedStatusListener",
                    Class.forName("com.mengbo.mbCan.interfaces.IMBCanVehicleGaspedStatusCallback")
                )
            }.getOrNull()
            unregisterGaspedStatusListenerMethod = runCatching {
                engineClass.getMethod("unRegistIMBCanVehicleGaspedStatusListener")
            }.getOrNull()
            val dataTypeClass = Class.forName(DATA_TYPE_CLASS) as Class<out Enum<*>>
            cfgVehicleDataType = java.lang.Enum.valueOf(dataTypeClass, "eMBCAN_CFG_VEHICLE")
            cfgAudioDataType = java.lang.Enum.valueOf(dataTypeClass, "eMBCAN_CFG_AUDIO")
            initialized = true
            availabilityRef.set(MbCanAvailability.Available)
        } catch (t: Throwable) {
            initialized = false
            availabilityRef.set(MbCanAvailability.Unavailable("${t.javaClass.simpleName}: ${t.message ?: "unknown"}"))
        }
        return availabilityRef.get()
    }

    fun canGetVehicleParam(propertyId: Int): Int? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        return invokeNativeGet(canGetVehicleParamMethod, propertyId)
    }

    /** [com.mengbo.mbCan.MBCanEngine.canGetAudioParam] — [com.mengbo.mbCan.defines.MBAudioProperty] ordinal ids. */
    fun canGetAudioParam(propertyId: Int): Int? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        return invokeNativeGet(canGetAudioParamMethod, propertyId)
    }

    /** [com.mengbo.mbCan.MBCanEngine.canSetAudioParam] — [com.mengbo.mbCan.defines.MBAudioProperty] value ids. */
    fun canSetAudioParam(propertyId: Int, value: Int): Int? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        return invokeNativeSet(canSetAudioParamMethod, propertyId, value)
    }

    fun canSetVehicleParam(propertyId: Int, value: Int): Int? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        return invokeNativeSet(canSetVehicleParamMethod, propertyId, value)
    }

    /**
     * [com.mengbo.mbCan.MBCanEngine.canSetWindowStatus] — constructor order FR, FL, RR, RL.
     * Stock voice uses **−1** for a pane that should not move.
     */
    fun canSetWindowStatus(fr: Int, fl: Int, rr: Int, rl: Int): Int? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        val engine = engineInstance ?: return null
        val ctor = windowStatusConstructor ?: return null
        val method = canSetWindowStatusMethod ?: return null
        warnIfNativeCallOnMain("setWindow", MbCanKnownVehiclePropertyId.WINDOW_POS)
        return nativeCallLock.withLock {
            try {
                val window = ctor.newInstance(fr.toByte(), fl.toByte(), rr.toByte(), rl.toByte())
                method.invoke(engine, window) as? Int
            } catch (_: Throwable) {
                null
            }
        }
    }

    private fun invokeNativeGet(method: Method?, propertyId: Int): Int? {
        val engine = engineInstance ?: return null
        warnIfNativeCallOnMain("get", propertyId)
        return nativeCallLock.withLock {
            try {
                method?.invoke(engine, propertyId) as? Int
            } catch (_: Throwable) {
                null
            }
        }
    }

    private fun invokeNativeSet(method: Method?, propertyId: Int, value: Int): Int? {
        val engine = engineInstance ?: return null
        warnIfNativeCallOnMain("set", propertyId)
        return nativeCallLock.withLock {
            try {
                method?.invoke(engine, propertyId, value) as? Int
            } catch (_: Throwable) {
                null
            }
        }
    }

    /**
     * OEM keeps listeners in collections: [Object] methods must not reach [handler], whose
     * `null` result would break `hashCode()` unboxing and identity-based `remove`.
     */
    private fun newOemProxy(loader: ClassLoader?, iface: Class<*>, handler: InvocationHandler): Any =
        Proxy.newProxyInstance(loader, arrayOf(iface)) { proxyObj, method, args ->
            if (method.declaringClass == Any::class.java) {
                when (method.name) {
                    "hashCode" -> System.identityHashCode(proxyObj)
                    "equals" -> proxyObj === args?.getOrNull(0)
                    "toString" -> "${iface.simpleName}Proxy@" + Integer.toHexString(System.identityHashCode(proxyObj))
                    else -> null
                }
            } else {
                handler.invoke(proxyObj, method, args)
            }
        }

    private fun nativeGetMbCanData(getMbCanData: Method, inst: Any, dataType: Int, cls: Class<*>): Any? =
        nativeCallLock.withLock { getMbCanData.invoke(inst, dataType, cls) }

    private fun <T> nativeCall(block: () -> T): T = nativeCallLock.withLock(block)

    private fun warnIfNativeCallOnMain(op: String, propertyId: Int) {
        if (Looper.getMainLooper().isCurrentThread) {
            android.util.Log.w(TAG, "OEM $op on main thread propertyId=$propertyId")
        }
    }

    fun subscribe(dataTypeNames: Set<String>): Int? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        if (dataTypeNames.isEmpty()) return 0
        warnIfNativeCallOnMain("subscribe", -1)
        return nativeCallLock.withLock {
            try {
                val dataTypeClass = Class.forName(DATA_TYPE_CLASS)
                val enumClass = dataTypeClass as Class<out Enum<*>>
                val list = ArrayList<Any>(dataTypeNames.size)
                dataTypeNames.forEach { name ->
                    val enumValue = java.lang.Enum.valueOf(enumClass, name)
                    list.add(enumValue)
                }
                subscribeMethod?.invoke(engineInstance, list) as? Int
            } catch (_: Throwable) {
                null
            }
        }
    }

    fun unSubscribe(dataTypeNames: Set<String>): Int? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        if (dataTypeNames.isEmpty()) return 0
        warnIfNativeCallOnMain("unSubscribe", -1)
        return nativeCallLock.withLock {
            try {
                val dataTypeClass = Class.forName(DATA_TYPE_CLASS)
                val enumClass = dataTypeClass as Class<out Enum<*>>
                val list = ArrayList<Any>(dataTypeNames.size)
                dataTypeNames.forEach { name ->
                    val enumValue = java.lang.Enum.valueOf(enumClass, name)
                    list.add(enumValue)
                }
                unSubscribeMethod?.invoke(engineInstance, list) as? Int
            } catch (_: Throwable) {
                null
            }
        }
    }

    /**
     * Single [com.mengbo.mbCan.interfaces.IMBCanSettingsCallback] on [MBCanEngine] — forwards speed/engine/
     * fuel/odometer/outside-temp/tires/BCM/AccStatus pushes into [MbCanRepository]. Safe to call once after [ensureInitialized];
     * no-op if already registered.
     *
     * Callbacks must only parse the push payload. Never call `getMbCanData` / `read*` here: on A9 a re-entrant
     * binder read when decode yields “no data” (idle IFC=0, DTE≤0, temp sentinel, …) can stall OEM push/CFG.
     * Fresh values when push fields are absent come from [MbCanJobManager] poll (`refreshSignal`).
     */
    @Synchronized
    fun registerSettingsTelemetryBridge() {
        if (settingsTelemetryProxy != null) return
        if (ensureInitialized() !is MbCanAvailability.Available) return
        val inst = engineInstance ?: return
        val iface = try {
            Class.forName("com.mengbo.mbCan.interfaces.IMBCanSettingsCallback")
        } catch (_: Throwable) {
            return
        }
        val loader = iface.classLoader ?: return
        val handler = InvocationHandler { _: Any?, method: Method, args: Array<out Any?>? ->
            oemSafe(method.name) {
                mirrorDeepCallback(method.name, args)
                when (method.name) {
                    "onCanVehicleSpeed" -> {
                        val fromArgs = runCatching {
                            val raw = args?.getOrNull(0)
                            when (raw) {
                                is Number -> raw.toFloat()
                                else -> {
                                    val getter = raw?.javaClass?.methods?.firstOrNull { it.name == "getSpeed" && it.parameterCount == 0 }
                                    (getter?.invoke(raw) as? Number)?.toFloat()
                                }
                            }
                        }.getOrNull()
                        if (fromArgs != null) {
                            MbCanRepository.scheduleCarSpeedPush(fromArgs)
                        }
                        val gearRaw = runCatching {
                            val raw = args?.getOrNull(0) ?: return@runCatching null
                            val getter = raw.javaClass.methods.firstOrNull { it.name == "getGear" && it.parameterCount == 0 }
                            (getter?.invoke(raw) as? Number)?.toInt()
                        }.getOrNull()
                        if (gearRaw != null) {
                            MbCanRepository.scheduleVehicleGearPush(gearRaw)
                        }
                    }
                    "onVehicleEngineStatusChange" -> {
                        val engine = args?.getOrNull(0)
                        val rpm = runCatching {
                            val getter = engine?.javaClass?.getMethod("getfSpeed")
                            (getter?.invoke(engine) as? Number)?.toFloat()
                        }.getOrNull()
                        val temperature = runCatching {
                            val getter = engine?.javaClass?.getMethod("getfTemperture")
                            (getter?.invoke(engine) as? Number)?.toFloat()
                        }.getOrNull()
                        val fuelRollingRaw = runCatching {
                            engine?.javaClass?.getMethod("getFuelRollingCounter")?.invoke(engine)
                        }.getOrNull()
                        MbCanRepository.scheduleEngineRpmPush(rpm)
                        MbCanRepository.scheduleEngineTemperaturePush(temperature)
                        // Idle/parked counter is often 0 → decode null; do not re-enter getMbCanData.
                        val litersPer100Km = when (fuelRollingRaw) {
                            is Short -> InstantFuelConsumptionDomain.decodeRawCounter(fuelRollingRaw)
                            is Number -> InstantFuelConsumptionDomain.decodeRawCounter(fuelRollingRaw.toInt())
                            else -> null
                        }
                        if (fuelRollingRaw is Number) {
                            MbCanRepository.scheduleCurrentFuelConsumptionPush(litersPer100Km)
                        }
                    }
                    "onCanVehicleFuelLevel" -> {
                        val fuel = args?.getOrNull(0)
                        val pct = runCatching {
                            val getter = fuel?.javaClass?.getMethod("getFuelLevel")
                            (getter?.invoke(fuel) as? Number)?.toInt()
                        }.getOrNull()
                        val validated = pct?.takeIf { it in 0..100 }?.toUInt()
                        val dteKm = runCatching {
                            val getter = fuel?.javaClass?.getMethod("getDistenceToEmpty")
                            val km = (getter?.invoke(fuel) as? Number)?.toFloat() ?: return@runCatching null
                            DistanceToEmptyDomain.decodeKm(km)?.toInt()?.toUInt()
                        }.getOrNull()
                        if (validated != null || dteKm != null) {
                            MbCanRepository.scheduleFuelLevelPush(validated, dteKm)
                        }
                    }
                    "onCanVehicleExternalTemp" -> {
                        val tempObj = args?.getOrNull(0)
                        val celsius = runCatching {
                            val getter = tempObj?.javaClass?.getMethod("getExternalTemperatureRaw")
                            val raw = (getter?.invoke(tempObj) as? Number)?.toInt() ?: return@runCatching null
                            OutsideTemperatureDomain.decodeMbCanCelsiusRaw(raw)
                        }.getOrNull()
                        if (celsius != null) {
                            MbCanRepository.scheduleOutsideTemperaturePush(celsius)
                        }
                    }
                    "onCanVehicleTires" -> {
                        val tiresObj = args?.getOrNull(0) ?: return@InvocationHandler null
                        val snapshot = decodeVehicleTiresObject(tiresObj) ?: return@InvocationHandler null
                        MbCanRepository.scheduleVehicleTiresPush(snapshot.pressure, snapshot.temperature)
                    }
                    "onVehicleTotalOdoMeterChange" -> {
                        val odo = args?.getOrNull(0)
                        val km = runCatching {
                            when (odo) {
                                is Number -> odo.toFloat()
                                else -> {
                                    val getter = odo?.javaClass?.methods?.firstOrNull {
                                        it.name == "getOdometer" && it.parameterCount == 0
                                    }
                                    (getter?.invoke(odo) as? Number)?.toFloat()
                                }
                            }
                        }.getOrNull()
                        val asUInt = km?.takeIf { it.isFinite() && it >= 0f }?.toInt()?.toUInt()
                        if (asUInt != null) {
                            MbCanRepository.scheduleTotalOdometerPush(asUInt)
                        }
                    }
                    "onVehicleBcmStatusChange" -> {
                        val bcm = args?.getOrNull(0) ?: return@InvocationHandler null
                        val moveDir = runCatching {
                            val getter = bcm.javaClass.getMethod("getRearDoorMoveDir")
                            (getter.invoke(bcm) as? Number)?.toInt()
                        }.getOrNull()
                        val doorSnapshot = runCatching {
                            val doorGetter = bcm.javaClass.getMethod("getDoorStatus")
                            val door = doorGetter.invoke(bcm) ?: return@runCatching null
                            BcmDoorDomain.fromDoorObject(door)
                        }.getOrNull()
                        val trunkSts = doorSnapshot?.trunk
                        if (moveDir != null || trunkSts != null) {
                            MbCanRepository.scheduleTrunkBcmPush(moveDir, trunkSts)
                        }
                        if (doorSnapshot != null) {
                            MbCanRepository.scheduleDoorsBcmPush(doorSnapshot)
                        }
                        val reverseRaw = runCatching {
                            val getter = bcm.javaClass.getMethod("getReverseGearSwitch")
                            (getter.invoke(bcm) as? Number)?.toInt()
                        }.getOrNull()
                        if (reverseRaw != null) {
                            MbCanRepository.scheduleReverseGearSwitchPush(reverseRaw)
                        }
                        val brakeRaw = runCatching {
                            val getter = bcm.javaClass.getMethod("getBrakePedalSts")
                            (getter.invoke(bcm) as? Number)?.toInt()
                        }.getOrNull()
                        if (brakeRaw != null) {
                            MbCanRepository.scheduleBrakePedalPush(brakeRaw)
                        }
                        val wiperStsRaw = runCatching {
                            val getter = bcm.javaClass.getMethod("getWiperSts")
                            (getter.invoke(bcm) as? Number)?.toInt()
                        }.getOrNull()
                        if (wiperStsRaw != null) {
                            MbCanRepository.scheduleWiperStsPush(wiperStsRaw)
                        }
                        val rainRaw = runCatching {
                            val getter = bcm.javaClass.getMethod("getRainDetectedSts")
                            (getter.invoke(bcm) as? Number)?.toInt()
                        }.getOrNull()
                        if (rainRaw != null) {
                            MbCanRepository.scheduleRainDetectedPush(rainRaw)
                        }
                        val highBeamRaw = runCatching {
                            val light = bcm.javaClass.getMethod("getLightStatus").invoke(bcm)
                                ?: return@runCatching null
                            val highBeamGetter = light.javaClass.getMethod("getHighBeamSts")
                            (highBeamGetter.invoke(light) as? Number)?.toInt()
                        }.getOrNull()
                        if (highBeamRaw != null) {
                            MbCanRepository.scheduleHighBeamPush(highBeamRaw)
                        }
                        val epbParkLampRaw = runCatching {
                            val getter = bcm.javaClass.getMethod("getEPBParkLampSts")
                            (getter.invoke(bcm) as? Number)?.toInt()
                        }.getOrNull()
                        if (epbParkLampRaw != null) {
                            MbCanRepository.scheduleEpbParkLampPush(epbParkLampRaw)
                        }
                        val gearShiftPosRaw = runCatching {
                            val getter = bcm.javaClass.getMethod("getGSM_GearShiftPos")
                            (getter.invoke(bcm) as? Number)?.toInt()
                        }.getOrNull()
                        if (gearShiftPosRaw != null) {
                            MbCanRepository.scheduleCurrentGearNumberPush(gearShiftPosRaw)
                        }
                        val bodyComfort = runCatching { parseBcmBodyComfort(bcm) }.getOrNull()
                        if (bodyComfort != null) {
                            MbCanRepository.scheduleBodyComfortBcmPush(bodyComfort)
                        }
                    }
                    "onVehicleAccStatusChange" -> {
                        val accObj = args?.getOrNull(0) ?: return@InvocationHandler null
                        val raw = runCatching {
                            (accObj.javaClass.getMethod("getAccStatus").invoke(accObj) as? Number)?.toInt()
                        }.getOrNull()
                        if (raw != null) {
                            MbCanRepository.scheduleAccStatusPush(raw)
                        }
                    }
                }
            }
            null
        }
        val proxy = newOemProxy(loader, iface, handler)
        settingsTelemetryProxy = proxy
        try {
            nativeCall { registerCarSettingsListenerMethod?.invoke(inst, proxy) }
        } catch (_: Throwable) {
            settingsTelemetryProxy = null
        }
    }

    @Synchronized
    fun unregisterSettingsTelemetryBridge() {
        val inst = engineInstance
        if (inst != null && settingsTelemetryProxy != null) {
            try {
                nativeCall { unregisterCarSettingsListenerMethod?.invoke(inst) }
            } catch (_: Throwable) {
            }
        }
        settingsTelemetryProxy = null
    }

    /**
     * Registers a single [com.mengbo.mbCan.interfaces.IMBCmdListener] for [eMBCAN_CFG_VEHICLE] when [active],
     * unregisters when inactive. OEM [unRegistCMDListener] clears all listeners for that data type.
     */
    @Synchronized
    fun syncVehicleCfgCmdListener(active: Boolean) {
        if (!active) {
            unregisterVehicleCfgCmdListener()
            return
        }
        if (vehicleCfgCmdListenerProxy != null) return
        if (ensureInitialized() !is MbCanAvailability.Available) return
        val inst = engineInstance ?: return
        val dt = cfgVehicleDataType ?: return
        val iface = try {
            Class.forName("com.mengbo.mbCan.interfaces.IMBCmdListener")
        } catch (_: Throwable) {
            return
        }
        val loader = iface.classLoader ?: return
        val handler = InvocationHandler { _: Any?, method: Method, args: Array<out Any?>? ->
            oemSafe(method.name) {

                if (method.name == "onCmdChanged" && args != null && args.size >= 4) {
                    val modular = (args[0] as Number).toInt() and 0xFF
                    val rev = (args[1] as Number).toInt() and 0xFF
                    val item = (args[2] as Number).toInt() and 0xFFFF
                    val value = (args[3] as Number).toInt()
                    MbCanDiagnostics.log(
                        "DEBUG",
                        "cfgVehiclePush modular=$modular rev=$rev item=$item value=$value"
                    )
                    onCfgCmdDeepDiagnosticEvent?.invoke("eMBCAN_CFG_VEHICLE", modular, rev, item, value)
                    MbCanRepository.scheduleVehicleCfgPush(modular, item, value)
                }
            }
            null
        }
        val proxy = newOemProxy(loader, iface, handler)
        vehicleCfgCmdListenerProxy = proxy
        try {
            nativeCall { registCmdListenerMethod?.invoke(inst, dt, proxy) }
        } catch (_: Throwable) {
            vehicleCfgCmdListenerProxy = null
        }
    }

    @Synchronized
    private fun unregisterVehicleCfgCmdListener() {
        val inst = engineInstance
        val dt = cfgVehicleDataType
        if (inst != null && vehicleCfgCmdListenerProxy != null && dt != null) {
            try {
                nativeCall { unRegistCmdListenerMethod?.invoke(inst, dt) }
            } catch (_: Throwable) {
            }
        }
        vehicleCfgCmdListenerProxy = null
    }

    /**
     * Registers [IMBCmdListener] for [eMBCAN_CFG_AUDIO] (same [onCmdChanged] shape as vehicle cfg).
     * Independent from [syncVehicleCfgCmdListener]; OEM clears listeners per data type on unregister.
     */
    @Synchronized
    fun syncAudioCfgCmdListener(active: Boolean) {
        if (!active) {
            unregisterAudioCfgCmdListener()
            return
        }
        if (audioCfgCmdListenerProxy != null) return
        if (ensureInitialized() !is MbCanAvailability.Available) return
        val inst = engineInstance ?: return
        val dt = cfgAudioDataType ?: return
        val iface = try {
            Class.forName("com.mengbo.mbCan.interfaces.IMBCmdListener")
        } catch (_: Throwable) {
            return
        }
        val loader = iface.classLoader ?: return
        val handler = InvocationHandler { _: Any?, method: Method, args: Array<out Any?>? ->
            oemSafe(method.name) {

                if (method.name == "onCmdChanged" && args != null && args.size >= 4) {
                    val modular = (args[0] as Number).toInt() and 0xFF
                    val rev = (args[1] as Number).toInt() and 0xFF
                    val item = (args[2] as Number).toInt() and 0xFFFF
                    val value = (args[3] as Number).toInt()
                    MbCanDiagnostics.log(
                        "DEBUG",
                        "cfgAudioPush modular=$modular rev=$rev item=$item value=$value"
                    )
                    onCfgCmdDeepDiagnosticEvent?.invoke("eMBCAN_CFG_AUDIO", modular, rev, item, value)
                    MbCanRepository.scheduleAudioCfgPush(modular, item, value)
                }
            }
            null
        }
        val proxy = newOemProxy(loader, iface, handler)
        audioCfgCmdListenerProxy = proxy
        try {
            nativeCall { registCmdListenerMethod?.invoke(inst, dt, proxy) }
        } catch (_: Throwable) {
            audioCfgCmdListenerProxy = null
        }
    }

    /**
     * Deep diagnostics: resolves candidate [MBCanDataType] enum names against the OEM
     * build. [subscribe] resolves the whole list at once and fails wholesale on an
     * unknown name, so unknown candidates must be filtered out first.
     */
    fun resolveDataTypeNames(candidateNames: Collection<String>): List<String> {
        if (candidateNames.isEmpty()) return emptyList()
        return runCatching {
            val enumClass = Class.forName(DATA_TYPE_CLASS) as Class<out Enum<*>>
            candidateNames.filter { name ->
                try {
                    java.lang.Enum.valueOf(enumClass, name)
                    true
                } catch (_: IllegalArgumentException) {
                    false
                }
            }
        }.getOrDefault(emptyList())
    }

    /** Deep diagnostics: raw CFG push sink (vehicle + audio), independent of UI interests. */
    fun setCfgCmdDeepDiagnosticListener(
        listener: ((source: String, modular: Int, rev: Int, item: Int, value: Int) -> Unit)?
    ) {
        onCfgCmdDeepDiagnosticEvent = listener
    }

    /**
     * Deep diagnostics: registers one [com.mengbo.mbCan.interfaces.IMBCmdListener] per
     * non-CFG data type and forwards raw `onCmdChanged` into [DeepCanDiagnostics].
     *
     * `eMBCAN_CFG_VEHICLE` / `eMBCAN_CFG_AUDIO` are skipped: OEM `unRegistCMDListener(type)`
     * clears **all** listeners of a type, so production CFG listeners must stay sole owners;
     * their raw events are mirrored via [setCfgCmdDeepDiagnosticListener] instead.
     *
     * `eMBCAN_VEHICLE_DOOR` / `eMBCAN_SEAT_BELT_STATUS` are **not** CMD-shaped on this OEM:
     * [startDeepTypedObjectListeners] wires door callback + seat-belt poll instead.
     * OEM `registCMDListener` only stores listeners for CFG_* types anyway.
     */
    @Synchronized
    fun startDeepCmdListeners(dataTypeNames: Set<String>): List<Pair<String, Boolean>> {
        if (ensureInitialized() !is MbCanAvailability.Available || engineInstance == null) {
            return dataTypeNames.map { it to false }
        }
        val inst = engineInstance!!
        val iface = try {
            Class.forName("com.mengbo.mbCan.interfaces.IMBCmdListener")
        } catch (_: Throwable) {
            return dataTypeNames.map { it to false }
        }
        val loader = iface.classLoader
        val skipCmdListener = setOf(
            "eMBCAN_CFG_VEHICLE",
            "eMBCAN_CFG_AUDIO",
            "eMBCAN_VEHICLE_DOOR",
            "eMBCAN_SEAT_BELT_STATUS",
        )
        return dataTypeNames.map { name ->
            when {
                name in skipCmdListener -> name to false
                deepCmdListenerProxies.containsKey(name) -> name to true
                else -> {
                    val dtEnum = resolveDataTypeEnum(name)
                        ?: return@map name to false
                    val handler = InvocationHandler { _, method, args ->
                        oemSafe(method.name) {
                            if (method.name == "onCmdChanged" && args != null && args.size >= 4) {
                                val modular = (args[0] as Number).toInt() and 0xFF
                                val rev = (args[1] as Number).toInt() and 0xFF
                                val item = (args[2] as Number).toInt() and 0xFFFF
                                val value = (args[3] as Number).toInt()
                                DeepCanDiagnostics.recordMbCanCmdChanged(name, modular, rev, item, value)
                            }
                        }
                        null
                    }
                    val proxy = newOemProxy(loader, iface, handler)
                    val registered = runCatching {
                        nativeCall { registCmdListenerMethod?.invoke(inst, dtEnum, proxy) }
                        true
                    }.getOrDefault(false)
                    if (registered) deepCmdListenerProxies[name] = proxy
                    name to registered
                }
            }
        }
    }

    /**
     * Deep-mode typed listeners for OEM object types that never reach [IMBCmdListener].
     * Door: [registCarDorListener]. Seat belt: OEM push Runnable is empty — poll via
     * [readSeatBeltWarningRaw] from [MbCanRepository] while deep is on.
     */
    @Synchronized
    fun startDeepTypedObjectListeners(): List<Pair<String, Boolean>> {
        val doorOk = registerDeepDoorListener()
        return listOf(
            "eMBCAN_VEHICLE_DOOR" to doorOk,
            "eMBCAN_SEAT_BELT_STATUS" to true, // poll-owned by MbCanRepository while deep
        )
    }

    @Synchronized
    fun stopDeepTypedObjectListeners() {
        unregisterDeepDoorListener()
    }

    private var deepDoorListenerProxy: Any? = null

    @Synchronized
    private fun registerDeepDoorListener(): Boolean {
        if (deepDoorListenerProxy != null) return true
        if (ensureInitialized() !is MbCanAvailability.Available) return false
        val inst = engineInstance ?: return false
        val iface = try {
            Class.forName("com.mengbo.mbCan.interfaces.IMbCanVehicleDoorCallback")
        } catch (_: Throwable) {
            return false
        }
        val loader = iface.classLoader ?: return false
        val handler = InvocationHandler { _, method, args ->
            oemSafe(method.name) {
                mirrorDeepCallback(method.name, args)
                if (method.name == "onVehicleDoorChange") {
                    val door = args?.getOrNull(0) ?: return@oemSafe
                    val snapshot = BcmDoorDomain.fromDoorObject(door) ?: return@oemSafe
                    DeepCanDiagnostics.recordMbCanObjectSnapshot(
                        "eMBCAN_VEHICLE_DOOR",
                        snapshot.journalSample(),
                    )
                    MbCanRepository.scheduleDoorsBcmPush(snapshot)
                    MbCanRepository.scheduleTrunkBcmPush(moveDir = null, trunkSts = snapshot.trunk)
                }
            }
            null
        }
        val proxy = newOemProxy(loader, iface, handler)
        val ok = runCatching {
            val register = inst.javaClass.getMethod(
                "registCarDorListener",
                Class.forName("com.mengbo.mbCan.interfaces.IMbCanVehicleDoorCallback"),
            )
            nativeCall { register.invoke(inst, proxy) }
            true
        }.getOrDefault(false)
        if (ok) deepDoorListenerProxy = proxy
        return ok
    }

    @Synchronized
    private fun unregisterDeepDoorListener() {
        val inst = engineInstance
        if (inst != null && deepDoorListenerProxy != null) {
            runCatching {
                val unregister = inst.javaClass.getMethod("unregistCarDorListener")
                nativeCall { unregister.invoke(inst) }
            }
        }
        deepDoorListenerProxy = null
    }

    /**
     * Expert raw window: whatever object OEM holds for [dataType]. `getMbCanData`
     * only casts, so `Object` accepts every entity class. Call off the main thread.
     */
    fun readMbCanDataObject(dataType: Int): Any? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        val inst = engineInstance ?: return null
        warnIfNativeCallOnMain("getMbCanData", dataType)
        return runCatching {
            val getMbCanData = Class.forName(ENGINE_CLASS)
                .getMethod("getMbCanData", Int::class.javaPrimitiveType, Class::class.java)
            nativeGetMbCanData(getMbCanData, inst, dataType, Any::class.java)
        }.getOrNull()
    }

    /**
     * [com.mengbo.mbCan.MBCanEngine.canGetVehicleValue]: raw bytes behind a vehicle
     * property, which can be wider than the int from [canGetVehicleParam].
     */
    fun canGetVehicleValueBytes(propertyId: Int): ByteArray? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        val engine = engineInstance ?: return null
        warnIfNativeCallOnMain("getValue", propertyId)
        return nativeCallLock.withLock {
            runCatching {
                val method = canGetVehicleValueMethod ?: engine.javaClass
                    .getMethod("canGetVehicleValue", Int::class.javaPrimitiveType)
                    .also { canGetVehicleValueMethod = it }
                method.invoke(engine, propertyId) as? ByteArray
            }.getOrNull()
        }
    }

    private var canGetVehicleValueMethod: Method? = null

    /**
     * Seat-belt warning raw via [getMbCanData] type **15** (`eMBCAN_SEAT_BELT_STATUS`).
     * OEM push callback for this type is empty — poll only.
     */
    fun readSeatBeltWarningRaw(): Pair<Int?, Int?>? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        val inst = engineInstance ?: return null
        return runCatching {
            val engineClass = Class.forName(ENGINE_CLASS)
            val getMbCanData = engineClass.getMethod("getMbCanData", Int::class.javaPrimitiveType, Class::class.java)
            val cls = Class.forName("com.mengbo.mbCan.entity.MBCanSeatBeltWarning")
            val obj = nativeGetMbCanData(getMbCanData, inst, 15, cls) ?: return null
            val driver = (cls.getMethod("getDriverWarning").invoke(obj) as? Number)?.toInt()
            val passenger = (cls.getMethod("getPassengerWarning").invoke(obj) as? Number)?.toInt()
            driver to passenger
        }.getOrNull()
    }

    @Synchronized
    fun stopDeepCmdListeners() {
        val inst = engineInstance
        deepCmdListenerProxies.forEach { (name, proxy) ->
            val dtEnum = resolveDataTypeEnum(name) ?: return@forEach
            runCatching {
                nativeCall {
                    unRegistCmdListenerListenerMethod?.invoke(inst, dtEnum, proxy)
                        ?: unRegistCmdListenerMethod?.invoke(inst, dtEnum)
                }
            }
        }
        deepCmdListenerProxies.clear()
        stopDeepTypedObjectListeners()
        stopDeepExtraListeners()
    }

    /**
     * Deep-only OEM callbacks that production never registers.
     *
     * Listeners whose unregister also unsubscribes shared types (ACC unsubscribes BCM,
     * air-purge unsubscribes BCM) are attached by setting the engine field, so stopping
     * deep mode does not tear down production subscriptions. The rest use the public
     * register/unregister pair: each one only touches its own data type.
     */
    @Synchronized
    fun startDeepExtraListeners(): List<Pair<String, Boolean>> {
        if (ensureInitialized() !is MbCanAvailability.Available || engineInstance == null) {
            return emptyList()
        }
        val attached = mutableListOf<Pair<String, Boolean>>()
        attached += "acc" to attachDeepFieldListener(
            "mbCanVehicleAccStatusCallback",
            "com.mengbo.mbCan.interfaces.IMbCanVehicleAccStatusCallback",
        )
        attached += "airPurge" to attachDeepFieldListener(
            "mbAirPurgeListener",
            "com.mengbo.mbCan.interfaces.IMBAirPurgeListener",
        )
        deepExtraListenerSpecs.forEach { spec ->
            attached += spec.label to registerDeepExtraListener(spec)
        }
        return attached
    }

    @Synchronized
    fun stopDeepExtraListeners() {
        detachDeepFieldListeners()
        val inst = engineInstance
        deepExtraListenerProxies.keys.toList().forEach { label ->
            val spec = deepExtraListenerSpecs.firstOrNull { it.label == label } ?: return@forEach
            if (inst != null) {
                runCatching {
                    nativeCall { inst.javaClass.getMethod(spec.unregister).invoke(inst) }
                }
            }
        }
        deepExtraListenerProxies.clear()
    }

    private data class DeepExtraListener(val label: String, val iface: String, val register: String, val unregister: String)

    private val deepExtraListenerProxies = HashMap<String, Any>()
    private val deepFieldListenerProxies = HashMap<String, Any>()

    private fun registerDeepExtraListener(spec: DeepExtraListener): Boolean {
        if (deepExtraListenerProxies.containsKey(spec.label)) return true
        val inst = engineInstance ?: return false
        val iface = runCatching { Class.forName(spec.iface) }.getOrNull() ?: return false
        val proxy = newOemProxy(iface.classLoader, iface) { _, method, args ->
            oemSafe(method.name) { mirrorDeepCallback(method.name, args) }
            null
        }
        val ok = runCatching {
            nativeCall { inst.javaClass.getMethod(spec.register, iface).invoke(inst, proxy) }
            true
        }.getOrDefault(false)
        if (ok) deepExtraListenerProxies[spec.label] = proxy
        return ok
    }

    /** Sets an engine listener field without the OEM register method (no subscribe side effect). */
    private fun attachDeepFieldListener(fieldName: String, ifaceName: String): Boolean {
        if (deepFieldListenerProxies.containsKey(fieldName)) return true
        val inst = engineInstance ?: return false
        val iface = runCatching { Class.forName(ifaceName) }.getOrNull() ?: return false
        val field = runCatching {
            inst.javaClass.getDeclaredField(fieldName).apply { isAccessible = true }
        }.getOrNull() ?: return false
        val current = runCatching { field.get(inst) }.getOrNull()
        if (current != null) return false
        val proxy = newOemProxy(iface.classLoader, iface) { _, method, args ->
            oemSafe(method.name) { mirrorDeepCallback(method.name, args) }
            null
        }
        val ok = runCatching { field.set(inst, proxy); true }.getOrDefault(false)
        if (ok) deepFieldListenerProxies[fieldName] = proxy
        return ok
    }

    private fun detachDeepFieldListeners() {
        val inst = engineInstance
        deepFieldListenerProxies.forEach { (fieldName, proxy) ->
            if (inst == null) return@forEach
            runCatching {
                val field = inst.javaClass.getDeclaredField(fieldName).apply { isAccessible = true }
                if (field.get(inst) === proxy) field.set(inst, null)
            }
        }
        deepFieldListenerProxies.clear()
    }

    private val deepExtraListenerSpecs = listOf(
            DeepExtraListener("avm", "com.mengbo.mbCan.interfaces.IMbCanAvmStatusCallback", "registMBCanAvmStatusCallback", "unRegistMBCanAvmStatusCallback"),
            DeepExtraListener("bsd", "com.mengbo.mbCan.interfaces.IMbCanBsdAlarmCallback", "registIMBBsdAlarmListener", "unRegistIMBBsdAlarmListener"),
            DeepExtraListener("dow", "com.mengbo.mbCan.interfaces.IMbCanDowAlarmCallback", "registIMBDowAlarmListener", "unRegistIMBDowAlarmListener"),
            DeepExtraListener("rcta", "com.mengbo.mbCan.interfaces.IMbCanRCTAAlarmCallback", "registIMBRCTAAlarmListener", "unRegistIMBRCTAAlarmListener"),
            DeepExtraListener("radar", "com.mengbo.mbCan.interfaces.IMbCanRadarSensorCallback", "registRadarSensorListener", "unregistRadarSensorListener"),
            DeepExtraListener("chime", "com.mengbo.mbCan.interfaces.IMBCanChimeStatusCallback", "registIMBChimeStatusListener", "unRegistIMBChimeStatusListener"),
            DeepExtraListener("icmAlarm", "com.mengbo.mbCan.interfaces.IMbCanICMAlarmInfoCallback", "registerICMAlarmInfoListener", "unregisterICMAlarmInfoListener"),
            DeepExtraListener("dvrParam", "com.mengbo.mbCan.interfaces.IMbCanDVRParamCallback", "registerCanDVRParamInfoCallback", "unregisterCanDVRParamInfoCallback"),
        )

    private fun resolveDataTypeEnum(name: String): Any? = runCatching {
        val enumClass = Class.forName(DATA_TYPE_CLASS) as Class<out Enum<*>>
        java.lang.Enum.valueOf(enumClass, name)
    }.getOrNull()

    /**
     * Reads trunk movement and full cabin door ajar from cached BCM snapshot
     * ([com.mengbo.mbCan.defines.MBCanDataType.eMBCAN_VEHICLE_BCM_STATUS]).
     *
     * [BcmTrunkSnapshot.doors] seeds [MbCanRepository.bcmDoorsState] on pull so
     * automations are not stuck Unavailable until the next BCM push.
     */
    data class BcmTrunkSnapshot(
        val moveDir: Int?,
        val trunkSts: Int?,
        val doors: BcmDoorSnapshot? = null,
    )

    fun readVehicleBcmTrunkSnapshot(): BcmTrunkSnapshot? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        val inst = engineInstance ?: return null
        return runCatching {
            val engineClass = Class.forName(ENGINE_CLASS)
            val getMbCanData = engineClass.getMethod("getMbCanData", Int::class.javaPrimitiveType, Class::class.java)
            val bcmCls = Class.forName("com.mengbo.mbCan.entity.MBCanVehicleBcmStatus")
            val bcmObj = nativeGetMbCanData(getMbCanData, inst, 21, bcmCls) ?: return null
            val moveDir = bcmCls.getMethod("getRearDoorMoveDir").invoke(bcmObj)?.let { (it as Number).toInt() }
            val doors = runCatching {
                val door = bcmCls.getMethod("getDoorStatus").invoke(bcmObj) ?: return@runCatching null
                BcmDoorDomain.fromDoorObject(door)
            }.getOrNull()
            BcmTrunkSnapshot(moveDir = moveDir, trunkSts = doors?.trunk, doors = doors)
        }.getOrNull()
    }

    /**
     * Reads RPM from [com.mengbo.mbCan.entity.MBCanVehicleEngine#getfSpeed()] via
     * [com.mengbo.mbCan.MBCanEngine.getMbCanData] data type 22 (eMBCAN_VEHICLE_ENGINE).
     */
    fun readVehicleEngineRpm(): Float? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        val inst = engineInstance ?: return null
        return runCatching {
            val engineClass = Class.forName(ENGINE_CLASS)
            val getMbCanData = engineClass.getMethod("getMbCanData", Int::class.javaPrimitiveType, Class::class.java)
            val engCls = Class.forName("com.mengbo.mbCan.entity.MBCanVehicleEngine")
            val engObj = nativeGetMbCanData(getMbCanData, inst, 22, engCls) ?: return null
            val fs = engCls.getMethod("getfSpeed").invoke(engObj) as? Number
            fs?.toFloat()
        }.getOrNull()
    }

    /**
     * Reads coolant temperature from [com.mengbo.mbCan.entity.MBCanVehicleEngine#getfTemperture()].
     *
     * Observed on Android 9 (mbCAN): push/pull always report `0.0` even with live RPM.
     * Android 10 (VHAL) coolant decode appears fine — prefer VHAL / TBox for real °C.
     */
    fun readVehicleEngineTemperature(): Float? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        val inst = engineInstance ?: return null
        return runCatching {
            val engineClass = Class.forName(ENGINE_CLASS)
            val getMbCanData = engineClass.getMethod("getMbCanData", Int::class.javaPrimitiveType, Class::class.java)
            val engCls = Class.forName("com.mengbo.mbCan.entity.MBCanVehicleEngine")
            val engObj = nativeGetMbCanData(getMbCanData, inst, 22, engCls) ?: return null
            val temp = engCls.getMethod("getfTemperture").invoke(engObj) as? Number
            temp?.toFloat()
        }.getOrNull()
    }

    /**
     * Reads speed from [com.mengbo.mbCan.entity.MBCanVehicleSpeed#getSpeed()] via
     * [com.mengbo.mbCan.MBCanEngine.getMbCanData] data type 1 (eMBCAN_VEHICLE_SPEED).
     */
    fun readVehicleSpeed(): Float? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        val inst = engineInstance ?: return null
        return runCatching {
            val engineClass = Class.forName(ENGINE_CLASS)
            val getMbCanData = engineClass.getMethod("getMbCanData", Int::class.javaPrimitiveType, Class::class.java)
            val speedCls = Class.forName("com.mengbo.mbCan.entity.MBCanVehicleSpeed")
            val speedObj = nativeGetMbCanData(getMbCanData, inst, 1, speedCls) ?: return null
            val speed = speedCls.getMethod("getSpeed").invoke(speedObj) as? Number
            speed?.toFloat()
        }.getOrNull()
    }

    /**
     * PRND letter from [com.mengbo.mbCan.entity.MBCanVehicleSpeed#getGear] via
     * getMbCanData data type **20** (`eMBCAN_VEHICLE_GEAR`); falls back to type **1**
     * (speed entity also carries `nGear`).
     */
    fun readVehicleGearMode(): String? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        val inst = engineInstance ?: return null
        return runCatching {
            val engineClass = Class.forName(ENGINE_CLASS)
            val getMbCanData = engineClass.getMethod("getMbCanData", Int::class.javaPrimitiveType, Class::class.java)
            val speedCls = Class.forName("com.mengbo.mbCan.entity.MBCanVehicleSpeed")
            val speedObj = nativeGetMbCanData(getMbCanData, inst, 20, speedCls)
                ?: nativeGetMbCanData(getMbCanData, inst, 1, speedCls)
                ?: return null
            val gear = (speedCls.getMethod("getGear").invoke(speedObj) as? Number)?.toInt() ?: return null
            VehicleGearDomain.decodePrndBitmask(gear)
        }.getOrNull()
    }

    /**
     * Reverse gear switch from [MBCanVehicleBcmStatus.getReverseGearSwitch].
     * Data type **21** (`eMBCAN_VEHICLE_BCM_STATUS`).
     */
    fun readReverseGearSwitch(): Boolean? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        val inst = engineInstance ?: return null
        return runCatching {
            val engineClass = Class.forName(ENGINE_CLASS)
            val getMbCanData = engineClass.getMethod("getMbCanData", Int::class.javaPrimitiveType, Class::class.java)
            val bcmCls = Class.forName("com.mengbo.mbCan.entity.MBCanVehicleBcmStatus")
            val bcmObj = nativeGetMbCanData(getMbCanData, inst, 21, bcmCls) ?: return null
            val raw = (bcmCls.getMethod("getReverseGearSwitch").invoke(bcmObj) as? Number)?.toInt() ?: return null
            VehicleGearDomain.decodeReverseGearSwitch(raw)
        }.getOrNull()
    }

    /**
     * AccStatus from [MBCanVehicleAccStatus.getAccStatus].
     * Data type **6** (`eMBCAN_VEHICLE_ACCSTATUS`).
     */
    fun readAccStatus(): String? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        val inst = engineInstance ?: return null
        return runCatching {
            val engineClass = Class.forName(ENGINE_CLASS)
            val getMbCanData = engineClass.getMethod("getMbCanData", Int::class.javaPrimitiveType, Class::class.java)
            val accCls = Class.forName("com.mengbo.mbCan.entity.MBCanVehicleAccStatus")
            val accObj = nativeGetMbCanData(getMbCanData, inst, 6, accCls) ?: return null
            val raw = (accCls.getMethod("getAccStatus").invoke(accObj) as? Number)?.toInt() ?: return null
            AccStatusDomain.decodeMbCan(raw)
        }.getOrNull()
    }

    /**
     * Accelerator pedal percent from [MBCanVehicleGaspedStatus].
     * Data type **36** (`eMBCAN_VEHICLE_GASPED_STATUS`).
     */
    fun readGasPedalPercent(): Float? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        val inst = engineInstance ?: return null
        return runCatching {
            val engineClass = Class.forName(ENGINE_CLASS)
            val getMbCanData = engineClass.getMethod("getMbCanData", Int::class.javaPrimitiveType, Class::class.java)
            val gaspedCls = Class.forName("com.mengbo.mbCan.entity.MBCanVehicleGaspedStatus")
            val gaspedObj = nativeGetMbCanData(getMbCanData, inst, 36, gaspedCls) ?: return null
            val position = (gaspedCls.getMethod("getfGasPedalPosition").invoke(gaspedObj) as? Number)?.toFloat()
            val invalid = (gaspedCls.getMethod("getnGasPedalPositionInvalidData").invoke(gaspedObj) as? Number)?.toInt()
            PedalDomain.decodeGasPedalPercent(position, invalid)
        }.getOrNull()
    }

    /**
     * Brake pedal from [MBCanVehicleBcmStatus.getBrakePedalSts].
     * Data type **21** (`eMBCAN_VEHICLE_BCM_STATUS`).
     */
    fun readBrakePedalPressed(): Boolean? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        val inst = engineInstance ?: return null
        return runCatching {
            val engineClass = Class.forName(ENGINE_CLASS)
            val getMbCanData = engineClass.getMethod("getMbCanData", Int::class.javaPrimitiveType, Class::class.java)
            val bcmCls = Class.forName("com.mengbo.mbCan.entity.MBCanVehicleBcmStatus")
            val bcmObj = nativeGetMbCanData(getMbCanData, inst, 21, bcmCls) ?: return null
            val raw = (bcmCls.getMethod("getBrakePedalSts").invoke(bcmObj) as? Number)?.toInt() ?: return null
            PedalDomain.decodeBrakePressed(raw)
        }.getOrNull()
    }

    /**
     * Front wiper mode from [MBCanVehicleBcmStatus.getWiperSts].
     * Data type **21** (`eMBCAN_VEHICLE_BCM_STATUS`).
     */
    fun readWiperOperatingMode(): WiperOperatingMode? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        val inst = engineInstance ?: return null
        return runCatching {
            val engineClass = Class.forName(ENGINE_CLASS)
            val getMbCanData = engineClass.getMethod("getMbCanData", Int::class.javaPrimitiveType, Class::class.java)
            val bcmCls = Class.forName("com.mengbo.mbCan.entity.MBCanVehicleBcmStatus")
            val bcmObj = nativeGetMbCanData(getMbCanData, inst, 21, bcmCls) ?: return null
            val raw = (bcmCls.getMethod("getWiperSts").invoke(bcmObj) as? Number)?.toInt() ?: return null
            WiperStsDomain.decode(raw)
        }.getOrNull()
    }

    /**
     * Rain detected from [MBCanVehicleBcmStatus.getRainDetectedSts].
     * Data type **21** (`eMBCAN_VEHICLE_BCM_STATUS`).
     */
    fun readRainDetected(): Boolean? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        val inst = engineInstance ?: return null
        return runCatching {
            val engineClass = Class.forName(ENGINE_CLASS)
            val getMbCanData = engineClass.getMethod("getMbCanData", Int::class.javaPrimitiveType, Class::class.java)
            val bcmCls = Class.forName("com.mengbo.mbCan.entity.MBCanVehicleBcmStatus")
            val bcmObj = nativeGetMbCanData(getMbCanData, inst, 21, bcmCls) ?: return null
            val raw = (bcmCls.getMethod("getRainDetectedSts").invoke(bcmObj) as? Number)?.toInt() ?: return null
            RainDetectedDomain.decodeDetected(raw)
        }.getOrNull()
    }

    /**
     * High beam on from [com.mengbo.mbCan.entity.MBCanLightStatus.getHighBeamSts]
     * of BCM status. Data type **21** (`eMBCAN_VEHICLE_BCM_STATUS`).
     */
    fun readHighBeamOn(): Boolean? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        val inst = engineInstance ?: return null
        return runCatching {
            val engineClass = Class.forName(ENGINE_CLASS)
            val getMbCanData = engineClass.getMethod("getMbCanData", Int::class.javaPrimitiveType, Class::class.java)
            val bcmCls = Class.forName("com.mengbo.mbCan.entity.MBCanVehicleBcmStatus")
            val bcmObj = nativeGetMbCanData(getMbCanData, inst, 21, bcmCls) ?: return null
            val light = bcmCls.getMethod("getLightStatus").invoke(bcmObj) ?: return null
            val raw = (light.javaClass.getMethod("getHighBeamSts").invoke(light) as? Number)?.toInt() ?: return null
            HighBeamDomain.decodeOn(raw)
        }.getOrNull()
    }

    /**
     * EPB park lamp from [MBCanVehicleBcmStatus.getEPBParkLampSts].
     * Data type **21** (`eMBCAN_VEHICLE_BCM_STATUS`).
     */
    fun readEpbParkLampOn(): Boolean? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        val inst = engineInstance ?: return null
        return runCatching {
            val engineClass = Class.forName(ENGINE_CLASS)
            val getMbCanData = engineClass.getMethod("getMbCanData", Int::class.javaPrimitiveType, Class::class.java)
            val bcmCls = Class.forName("com.mengbo.mbCan.entity.MBCanVehicleBcmStatus")
            val bcmObj = nativeGetMbCanData(getMbCanData, inst, 21, bcmCls) ?: return null
            val raw = (bcmCls.getMethod("getEPBParkLampSts").invoke(bcmObj) as? Number)?.toInt() ?: return null
            EpbParkLampDomain.decodeOn(raw)
        }.getOrNull()
    }

    data class IcmDriverWarningLamps(
        val engineOilWarning: Boolean?,
        val brakeFluidWarning: Boolean?,
    )

    /**
     * ICM engine-oil / brake-fluid warning lamps from [MBCanVehicleIcmDriverInfo].
     * Data type **44** (`eMBCAN_VEHICLE_ICM_DRIVE_INFO`). OEM settings dispatch is empty —
     * pull / JobManager poll only.
     */
    fun readIcmDriverWarningLamps(): IcmDriverWarningLamps? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        val inst = engineInstance ?: return null
        return runCatching {
            val engineClass = Class.forName(ENGINE_CLASS)
            val getMbCanData = engineClass.getMethod("getMbCanData", Int::class.javaPrimitiveType, Class::class.java)
            val icmCls = Class.forName("com.mengbo.mbCan.entity.MBCanVehicleIcmDriverInfo")
            val icmObj = nativeGetMbCanData(getMbCanData, inst, 44, icmCls) ?: return null
            val oilRaw = (icmCls.getMethod("getICM_EngineOil").invoke(icmObj) as? Number)?.toInt()
            val brakeRaw = (icmCls.getMethod("getICM_Brakefluid").invoke(icmObj) as? Number)?.toInt()
            IcmDriverWarningLamps(
                engineOilWarning = oilRaw?.let(IcmWarningLampDomain::decodeWarningActive),
                brakeFluidWarning = brakeRaw?.let(IcmWarningLampDomain::decodeWarningActive),
            )
        }.getOrNull()
    }

    /**
     * Current gear number from [MBCanVehicleBcmStatus.getGSM_GearShiftPos].
     * Data type **21** (`eMBCAN_VEHICLE_BCM_STATUS`).
     */
    fun readCurrentGearNumber(): Int? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        val inst = engineInstance ?: return null
        return runCatching {
            val engineClass = Class.forName(ENGINE_CLASS)
            val getMbCanData = engineClass.getMethod("getMbCanData", Int::class.javaPrimitiveType, Class::class.java)
            val bcmCls = Class.forName("com.mengbo.mbCan.entity.MBCanVehicleBcmStatus")
            val bcmObj = nativeGetMbCanData(getMbCanData, inst, 21, bcmCls) ?: return null
            val raw = (bcmCls.getMethod("getGSM_GearShiftPos").invoke(bcmObj) as? Number)?.toInt() ?: return null
            GearNumberDomain.decode(raw)
        }.getOrNull()
    }

    fun readSunshadeRaw(): Int? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        return BodyComfortDomain.sanitizeStatusRaw(
            canGetVehicleParam(MbCanKnownVehiclePropertyId.SUNSHADE_POS),
        )
    }

    fun readSunroofRaw(): Int? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        return BodyComfortDomain.sanitizeStatusRaw(
            canGetVehicleParam(MbCanKnownVehiclePropertyId.SUNROOF_CONTROL),
        )
    }

    fun readBcmBodyComfort(): BodyComfortBcmRaw? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        warnIfNativeCallOnMain("get", 21)
        return nativeCallLock.withLock {
            try {
                val engine = engineInstance ?: return@withLock null
                val engineClass = engine.javaClass
                val getMbCanData = engineClass.getMethod("getMbCanData", Int::class.javaPrimitiveType, Class::class.java)
                val bcmCls = Class.forName("com.mengbo.mbCan.entity.MBCanVehicleBcmStatus")
                val bcmObj = getMbCanData.invoke(engine, 21, bcmCls) ?: return@withLock null
                parseBcmBodyComfort(bcmObj)
            } catch (_: Throwable) {
                null
            }
        }
    }

    private fun parseBcmBodyComfort(bcm: Any): BodyComfortBcmRaw {
        val sunRoof = runCatching {
            (bcm.javaClass.getMethod("getSunRoof").invoke(bcm) as? Number)?.toInt()
        }.getOrNull()
        val window = runCatching {
            bcm.javaClass.getMethod("getVehicleWindow").invoke(bcm)
        }.getOrNull()
        fun windowByte(name: String): Int? = runCatching {
            (window?.javaClass?.getMethod(name)?.invoke(window) as? Number)?.toInt()
        }.getOrNull()
        return BodyComfortBcmRaw(
            sunRoof = sunRoof,
            windowFl = windowByte("getFLWindow"),
            windowFr = windowByte("getFRWindow"),
            windowRl = windowByte("getRLWindow"),
            windowRr = windowByte("getRRWindow"),
        )
    }

    /** Fuel % from [MBCanVehicleFuelLevel.getFuelLevel]; valid range 0…100. Data type 12. */
    fun readVehicleFuelLevelPercent(): UInt? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        val inst = engineInstance ?: return null
        return runCatching {
            val engineClass = Class.forName(ENGINE_CLASS)
            val getMbCanData = engineClass.getMethod("getMbCanData", Int::class.javaPrimitiveType, Class::class.java)
            val fuelCls = Class.forName("com.mengbo.mbCan.entity.MBCanVehicleFuelLevel")
            val fuelObj = nativeGetMbCanData(getMbCanData, inst, 12, fuelCls) ?: return null
            val level = (fuelCls.getMethod("getFuelLevel").invoke(fuelObj) as? Number)?.toInt() ?: return null
            if (level in 0..100) level.toUInt() else null
        }.getOrNull()
    }

    /** Distance-to-empty km from [MBCanVehicleFuelLevel.getDistenceToEmpty]. Data type 12. */
    fun readDistanceToFuelEmptyKm(): UInt? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        val inst = engineInstance ?: return null
        return runCatching {
            val engineClass = Class.forName(ENGINE_CLASS)
            val getMbCanData = engineClass.getMethod("getMbCanData", Int::class.javaPrimitiveType, Class::class.java)
            val fuelCls = Class.forName("com.mengbo.mbCan.entity.MBCanVehicleFuelLevel")
            val fuelObj = nativeGetMbCanData(getMbCanData, inst, 12, fuelCls) ?: return null
            val km = (fuelCls.getMethod("getDistenceToEmpty").invoke(fuelObj) as? Number)?.toFloat() ?: return null
            DistanceToEmptyDomain.decodeKm(km)?.toInt()?.coerceAtLeast(0)?.toUInt()
        }.getOrNull()
    }

    /**
     * Instant fuel L/100km from [MBCanVehicleEngine.getFuelRollingCounter] / 10. Data type 22.
     */
    fun readCurrentFuelConsumptionLPer100Km(): Float? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        val inst = engineInstance ?: return null
        return runCatching {
            val engineClass = Class.forName(ENGINE_CLASS)
            val getMbCanData = engineClass.getMethod("getMbCanData", Int::class.javaPrimitiveType, Class::class.java)
            val engCls = Class.forName("com.mengbo.mbCan.entity.MBCanVehicleEngine")
            val engObj = nativeGetMbCanData(getMbCanData, inst, 22, engCls) ?: return null
            val raw = (engCls.getMethod("getFuelRollingCounter").invoke(engObj) as? Number)?.toInt() ?: return null
            InstantFuelConsumptionDomain.decodeRawCounter(raw)
        }.getOrNull()
    }

    /** Average fuel L/100km from [MBCanVehicleIcmInfo.getICM_4_AverageFuelConsume]. Data type 42. */
    fun readAverageFuelConsumptionLPer100Km(): Float? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        val inst = engineInstance ?: return null
        return runCatching {
            val engineClass = Class.forName(ENGINE_CLASS)
            val getMbCanData = engineClass.getMethod("getMbCanData", Int::class.javaPrimitiveType, Class::class.java)
            val icmCls = Class.forName("com.mengbo.mbCan.entity.MBCanVehicleIcmInfo")
            val icmObj = nativeGetMbCanData(getMbCanData, inst, 42, icmCls) ?: return null
            val raw = (icmCls.getMethod("getICM_4_AverageFuelConsume").invoke(icmObj) as? Number)
                ?.toFloat() ?: return null
            AverageFuelConsumptionDomain.decodeMbCanLitersPer100Km(raw)
        }.getOrNull()
    }

    /** Maintenance tips km from [MBCanVehicleIcmTripInfo.getICM_6_Maintenance_tips]. Data type 48. */
    fun readDistanceToNextMaintenanceKm(): UInt? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        val inst = engineInstance ?: return null
        return runCatching {
            val engineClass = Class.forName(ENGINE_CLASS)
            val getMbCanData = engineClass.getMethod("getMbCanData", Int::class.javaPrimitiveType, Class::class.java)
            val tripCls = Class.forName("com.mengbo.mbCan.entity.MBCanVehicleIcmTripInfo")
            val tripObj = nativeGetMbCanData(getMbCanData, inst, 48, tripCls) ?: return null
            val raw = (tripCls.getMethod("getICM_6_Maintenance_tips").invoke(tripObj) as? Number)?.toInt()
                ?: return null
            MaintenanceTipsDomain.decodeKm(raw)
        }.getOrNull()
    }

    data class Pm25AirQualitySnapshot(val inside: UInt?, val outside: UInt?)

    /** PM2.5 densities from [MBCanPM25]. Data type 28. */
    fun readPm25AirQuality(): Pm25AirQualitySnapshot? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        val inst = engineInstance ?: return null
        return runCatching {
            val engineClass = Class.forName(ENGINE_CLASS)
            val getMbCanData = engineClass.getMethod("getMbCanData", Int::class.javaPrimitiveType, Class::class.java)
            val pmCls = Class.forName("com.mengbo.mbCan.entity.MBCanPM25")
            val pmObj = nativeGetMbCanData(getMbCanData, inst, 28, pmCls) ?: return null
            val insideRaw = (pmCls.getMethod("getPM25Indensity").invoke(pmObj) as? Number)?.toInt()
            val outsideRaw = (pmCls.getMethod("getPM25outdensity").invoke(pmObj) as? Number)?.toInt()
            Pm25AirQualitySnapshot(
                inside = insideRaw?.let { Pm25AirQualityDomain.decodeDensity(it) },
                outside = outsideRaw?.let { Pm25AirQualityDomain.decodeDensity(it) },
            )
        }.getOrNull()
    }

    data class SteeringAngleSnapshot(val angleDeg: Float?, val angleSpeed: Float?)

    /** Steering angle from [MBCanVehicleSteeringAngle]. Data type 3. */
    fun readSteeringAngle(): SteeringAngleSnapshot? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        val inst = engineInstance ?: return null
        return runCatching {
            val engineClass = Class.forName(ENGINE_CLASS)
            val getMbCanData = engineClass.getMethod("getMbCanData", Int::class.javaPrimitiveType, Class::class.java)
            val steerCls = Class.forName("com.mengbo.mbCan.entity.MBCanVehicleSteeringAngle")
            val steerObj = nativeGetMbCanData(getMbCanData, inst, 3, steerCls) ?: return null
            val angle = (steerCls.getMethod("getSteeringAngle").invoke(steerObj) as? Number)?.toFloat()
            val speed = (steerCls.getMethod("getSteeringAngleSpeed").invoke(steerObj) as? Number)?.toFloat()
            SteeringAngleSnapshot(
                angleDeg = angle?.takeIf { it.isFinite() },
                angleSpeed = speed?.takeIf { it.isFinite() },
            )
        }.getOrNull()
    }

    /** Turn L/R (+ hazard from pair) from [MBCanVehicleTurnLight]. Data type 2. */
    fun readTurnSignals(): TurnSignalsState? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        val inst = engineInstance ?: return null
        return runCatching {
            val engineClass = Class.forName(ENGINE_CLASS)
            val getMbCanData = engineClass.getMethod("getMbCanData", Int::class.javaPrimitiveType, Class::class.java)
            val turnCls = Class.forName("com.mengbo.mbCan.entity.MBCanVehicleTurnLight")
            val turnObj = nativeGetMbCanData(getMbCanData, inst, 2, turnCls) ?: return null
            val left = (turnCls.getMethod("getLeftLightState").invoke(turnObj) as? Number)?.toInt()
                ?: return null
            val right = (turnCls.getMethod("getRightLightState").invoke(turnObj) as? Number)?.toInt()
                ?: return null
            TurnSignalsDomain.fromMbCanTurnLightRaw(left, right)
        }.getOrNull()
    }

    /** Total odometer km from [MBCanTotalOdometer.getOdometer]. Data type 16. */
    fun readTotalOdometerKm(): UInt? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        val inst = engineInstance ?: return null
        return runCatching {
            val engineClass = Class.forName(ENGINE_CLASS)
            val getMbCanData = engineClass.getMethod("getMbCanData", Int::class.javaPrimitiveType, Class::class.java)
            val odoCls = Class.forName("com.mengbo.mbCan.entity.MBCanTotalOdometer")
            val odoObj = nativeGetMbCanData(getMbCanData, inst, 16, odoCls) ?: return null
            val km = (odoCls.getMethod("getOdometer").invoke(odoObj) as? Number)?.toFloat() ?: return null
            if (!km.isFinite() || km < 0f) null else km.toInt().coerceAtLeast(0).toUInt()
        }.getOrNull()
    }

    /** Wheel pulse counters from [MBCanVehicleWheel]. Data type 4 (`eMBCAN_VEHICLE_WHEEL`). */
    fun readVehicleWheelPulseCounters(): vad.dashing.tbox.vehicle.WheelCounters? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        val inst = engineInstance ?: return null
        return runCatching {
            val engineClass = Class.forName(ENGINE_CLASS)
            val getMbCanData = engineClass.getMethod("getMbCanData", Int::class.javaPrimitiveType, Class::class.java)
            val wheelCls = Class.forName("com.mengbo.mbCan.entity.MBCanVehicleWheel")
            val wheelObj = nativeGetMbCanData(getMbCanData, inst, 4, wheelCls) ?: return null
            val mask = (1 shl vad.dashing.tbox.vehicle.WheelPulseOdometer.COUNTER_BITS) - 1
            fun counter(name: String): Int =
                ((wheelCls.getMethod(name).invoke(wheelObj) as? Number)?.toInt() ?: 0)
                    .coerceAtLeast(0) and mask
            vad.dashing.tbox.vehicle.WheelCounters(
                lhf = counter("getLHFPulseCounter"),
                rhf = counter("getRHFPulseCounter"),
                lhr = counter("getLHRPulseCounter"),
                rhr = counter("getRHRPulseCounter"),
                updatedElapsedMs = android.os.SystemClock.elapsedRealtime(),
            )
        }.getOrNull()
    }

    /**
     * Outside temp °C from [MBCanVehicleExternalTemp.getExternalTemperatureRaw].
     * Raw byte is already °C; sentinel 87 = invalid. Data type 38.
     * (VHAL uses a different raw encoding — see [OutsideTemperatureDomain.decodeVhalRaw].)
     */
    fun readOutsideTemperatureC(): Float? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        val inst = engineInstance ?: return null
        return runCatching {
            val engineClass = Class.forName(ENGINE_CLASS)
            val getMbCanData = engineClass.getMethod("getMbCanData", Int::class.javaPrimitiveType, Class::class.java)
            val tempCls = Class.forName("com.mengbo.mbCan.entity.MBCanVehicleExternalTemp")
            val tempObj = nativeGetMbCanData(getMbCanData, inst, 38, tempCls) ?: return null
            val raw = (tempCls.getMethod("getExternalTemperatureRaw").invoke(tempObj) as? Number)?.toInt()
                ?: return null
            OutsideTemperatureDomain.decodeMbCanCelsiusRaw(raw)
        }.getOrNull()
    }

    data class VehicleTiresSnapshot(val pressure: Wheels, val temperature: Wheels)

    /**
     * TPMS from [MBCanVehicleTires] via getMbCanData data type 34 (`eMBCAN_VEHICLE_TIRE`).
     * Order LF/RF/LR/RR → wheel1…wheel4.
     */
    fun readVehicleTires(): VehicleTiresSnapshot? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        val inst = engineInstance ?: return null
        return runCatching {
            val engineClass = Class.forName(ENGINE_CLASS)
            val getMbCanData = engineClass.getMethod("getMbCanData", Int::class.javaPrimitiveType, Class::class.java)
            val tiresCls = Class.forName("com.mengbo.mbCan.entity.MBCanVehicleTires")
            val tiresObj = nativeGetMbCanData(getMbCanData, inst, 34, tiresCls) ?: return null
            decodeVehicleTiresObject(tiresObj)
        }.getOrNull()
    }

    fun decodeVehicleTiresObject(tiresObj: Any): VehicleTiresSnapshot? {
        return runCatching {
            val tiresCls = tiresObj.javaClass
            val arr = tiresCls.getMethod("getVstTire").invoke(tiresObj) as? Array<*> ?: return null
            fun pressureAt(index: Int): Float? {
                val tire = arr.getOrNull(index) ?: return null
                val p = (tire.javaClass.getMethod("getPressure").invoke(tire) as? Number)?.toFloat() ?: return null
                return TirePressureDomain.decodeMbCanPressureBar(p)
            }
            fun temperatureAt(index: Int): Float? {
                val tire = arr.getOrNull(index) ?: return null
                val t = (tire.javaClass.getMethod("getTemperature").invoke(tire) as? Number)?.toInt() ?: return null
                return TirePressureDomain.decodeMbCanTemperatureC(t)
            }
            VehicleTiresSnapshot(
                pressure = Wheels(
                    wheel1 = pressureAt(0),
                    wheel2 = pressureAt(1),
                    wheel3 = pressureAt(2),
                    wheel4 = pressureAt(3),
                ),
                temperature = Wheels(
                    wheel1 = temperatureAt(0),
                    wheel2 = temperatureAt(1),
                    wheel3 = temperatureAt(2),
                    wheel4 = temperatureAt(3),
                ),
            )
        }.getOrNull()
    }

    @Synchronized
    private fun unregisterAudioCfgCmdListener() {
        val inst = engineInstance
        val dt = cfgAudioDataType
        if (inst != null && audioCfgCmdListenerProxy != null && dt != null) {
            try {
                nativeCall { unRegistCmdListenerMethod?.invoke(inst, dt) }
            } catch (_: Throwable) {
            }
        }
        audioCfgCmdListenerProxy = null
    }

    /**
     * Debug snapshot from [com.mengbo.mbCan.MBCanEngine.getMbCanData] (native cache only; no CycleData).
     * 1 = [com.mengbo.mbCan.defines.MBCanDataType.eMBCAN_VEHICLE_SPEED],
     * 22 / 29 = [com.mengbo.mbCan.defines.MBCanDataType.eMBCAN_VEHICLE_ENGINE] /
     * [com.mengbo.mbCan.defines.MBCanDataType.eMBCAN_VEHICLE_ENGINE_GEAR] ([MBCanVehicleEngine]; `fs` is vendor field name, may correlate to RPM on HU).
     */
    fun peekMbCanMotionDebugLine(): String {
        if (availabilityRef.get() !is MbCanAvailability.Available) return "mbCAN_motion=na"
        val inst = engineInstance ?: return "mbCAN_motion=no_inst"
        return try {
            val engineClass = Class.forName(ENGINE_CLASS)
            val getMbCanData = engineClass.getMethod("getMbCanData", Int::class.javaPrimitiveType, Class::class.java)
            val spdCls = Class.forName("com.mengbo.mbCan.entity.MBCanVehicleSpeed")
            val spdObj = nativeGetMbCanData(getMbCanData, inst, 1, spdCls)
            val speedStr =
                if (spdObj != null) {
                    val s = spdCls.getMethod("getSpeed").invoke(spdObj) as Float
                    val ok = spdCls.getMethod("getSpeedValidSts").invoke(spdObj) as Byte
                    "mbCAN_dt1_spd=$s ok=$ok"
                } else {
                    "mbCAN_dt1_spd=null"
                }
            val engCls = Class.forName("com.mengbo.mbCan.entity.MBCanVehicleEngine")
            fun fmtEng(prefix: String, dataType: Int): String {
                val engObj = nativeGetMbCanData(getMbCanData, inst, dataType, engCls)
                return if (engObj != null) {
                    val fs = engCls.getMethod("getfSpeed").invoke(engObj) as Float
                    val tmp = engCls.getMethod("getfTemperture").invoke(engObj) as Float
                    val st = engCls.getMethod("getStatus").invoke(engObj) as Byte
                    val dsp = engCls.getMethod("getnDisplayVehiceSpeed").invoke(engObj) as Short
                    "${prefix}fs=$fs tmp=$tmp st=$st dsp=$dsp"
                } else {
                    "${prefix}null"
                }
            }
            val eng22 = fmtEng("mbCAN_dt22_eng ", 22)
            val eng29 = fmtEng("mbCAN_dt29_eg ", 29)
            listOf(speedStr, eng22, eng29).joinToString(" | ")
        } catch (t: Throwable) {
            "mbCAN_motion_err=${t.javaClass.simpleName}:${t.message}"
        }
    }

    /**
     * Forwards [IMBVehicleListener.onSteeringWheel] / [IMBVehicleListener.onVehicleTurnLightChange] /
     * [IMBVehicleListener.onPull] (wheel pulse) into [MbCanRepository] push schedulers.
     *
     * Sets OEM `mVehicletener` directly instead of [MBCanEngine.registVehicleListener] /
     * [MBCanEngine.unRegistVehicleListener]: those also subscribe/unsubscribe SPEED/TURNLIGHT/WHEEL
     * and would race with [MbCanJobManager] / settings telemetry refcounts.
     * Subscription for `eMBCAN_VEHICLE_STEERING_ANGLE` / `eMBCAN_VEHICLE_TURNLIGHT` /
     * `eMBCAN_VEHICLE_WHEEL` stays owned by [MbCanJobManager]
     * ([MbCanJobManager.ensureOemSubscriptions] after interest reapply).
     *
     * One shared listener field: steer, turn lights, and wheel pulse share `mVehicletener`.
     */
    @Synchronized
    fun syncImbVehicleListener(
        needSteer: Boolean,
        needTurnLights: Boolean,
        needWheelPulse: Boolean = false,
        keepForDeepDiagnostics: Boolean = false,
    ) {
        vehicleListenerWantSteer = needSteer
        vehicleListenerWantTurnLights = needTurnLights
        vehicleListenerWantWheelPulse = needWheelPulse
        if (!needSteer && !needTurnLights && !needWheelPulse && !keepForDeepDiagnostics) {
            clearImbVehicleListener()
            return
        }
        if (imbVehicleListenerProxy != null) return
        if (ensureInitialized() !is MbCanAvailability.Available) return
        val inst = engineInstance ?: return
        val iface = try {
            Class.forName("com.mengbo.mbCan.interfaces.IMBVehicleListener")
        } catch (_: Throwable) {
            return
        }
        val loader = iface.classLoader ?: return
        val handler = InvocationHandler { _: Any?, method: Method, args: Array<out Any?>? ->
            oemSafe(method.name) {
                mirrorDeepCallback(method.name, args)
                when (method.name) {
                    "onSteeringWheel" -> {
                        if (vehicleListenerWantSteer) {
                            val angle = (args?.getOrNull(0) as? Number)?.toFloat()?.takeIf { it.isFinite() }
                            val speed = (args?.getOrNull(1) as? Number)?.toFloat()?.takeIf { it.isFinite() }
                            MbCanRepository.scheduleSteeringAnglePush(angleDeg = angle, angleSpeed = speed)
                        }
                    }
                    "onVehicleTurnLightChange" -> {
                        if (vehicleListenerWantTurnLights) {
                            val left = (args?.getOrNull(0) as? Number)?.toInt()
                            val right = (args?.getOrNull(1) as? Number)?.toInt()
                            if (left != null && right != null) {
                                MbCanRepository.scheduleTurnSignalsPush(left, right)
                            }
                        }
                    }
                    "onPull" -> {
                        if (vehicleListenerWantWheelPulse) {
                            val lhf = (args?.getOrNull(0) as? Number)?.toInt()
                            val rhf = (args?.getOrNull(1) as? Number)?.toInt()
                            val lhr = (args?.getOrNull(2) as? Number)?.toInt()
                            val rhr = (args?.getOrNull(3) as? Number)?.toInt()
                            if (lhf != null && rhf != null && lhr != null && rhr != null) {
                                MbCanRepository.scheduleWheelPulsePush(lhf, rhf, lhr, rhr)
                            }
                        }
                    }
                }
            }
            null
        }
        val proxy = newOemProxy(loader, iface, handler)
        if (!setVehicleListenerField(inst, proxy)) {
            return
        }
        imbVehicleListenerProxy = proxy
    }

    @Synchronized
    private fun clearImbVehicleListener() {
        val inst = engineInstance
        val proxy = imbVehicleListenerProxy
        imbVehicleListenerProxy = null
        vehicleListenerWantSteer = false
        vehicleListenerWantTurnLights = false
        vehicleListenerWantWheelPulse = false
        if (inst == null || proxy == null) return
        runCatching {
            val field = Class.forName(ENGINE_CLASS).getDeclaredField("mVehicletener")
            field.isAccessible = true
            if (field.get(inst) === proxy) {
                field.set(inst, null)
            }
        }
    }

    private fun setVehicleListenerField(inst: Any, listener: Any?): Boolean {
        return runCatching {
            val field = Class.forName(ENGINE_CLASS).getDeclaredField("mVehicletener")
            field.isAccessible = true
            field.set(inst, listener)
            true
        }.getOrDefault(false)
    }

    /**
     * Register a production hardkey listener (A9 `IMBHardKeyListener`).
     * Shares one OEM subscription with diagnostics; safe to call repeatedly.
     */
    @Synchronized
    fun addHardKeyListener(
        listener: (keyCode: Int, keyStatus: Int, keyType: Int) -> Unit,
    ): Result<Unit> {
        synchronized(hardKeyListenersLock) {
            if (hardKeyListeners.none { it === listener }) {
                hardKeyListeners.add(listener)
            }
        }
        return ensureHardKeyOemRegistered()
    }

    /** Remove a production hardkey listener; OEM unregisters only when nobody remains. */
    @Synchronized
    fun removeHardKeyListener(
        listener: (keyCode: Int, keyStatus: Int, keyType: Int) -> Unit,
    ): Result<Unit> {
        synchronized(hardKeyListenersLock) {
            hardKeyListeners.removeAll { it === listener }
        }
        return maybeUnregisterHardKeyOem()
    }

    @Synchronized
    fun startHardKeyDiagnostics(
        onEvent: (keyCode: Int, keyStatus: Int, keyType: Int) -> Unit,
    ): Result<Unit> {
        onHardKeyDiagnosticEvent = onEvent
        return ensureHardKeyOemRegistered()
    }

    @Synchronized
    fun stopHardKeyDiagnostics(): Result<Unit> {
        onHardKeyDiagnosticEvent = null
        return maybeUnregisterHardKeyOem()
    }

    private fun hardKeyHasConsumers(): Boolean {
        if (onHardKeyDiagnosticEvent != null) return true
        synchronized(hardKeyListenersLock) {
            return hardKeyListeners.isNotEmpty()
        }
    }

    private fun dispatchHardKey(keyCode: Int, keyStatus: Int, keyType: Int) {
        val snapshot: List<(Int, Int, Int) -> Unit>
        synchronized(hardKeyListenersLock) {
            snapshot = hardKeyListeners.toList()
        }
        for (listener in snapshot) {
            oemSafe("hardKeyListener") { listener(keyCode, keyStatus, keyType) }
        }
        oemSafe("hardKeyDiagnostic") {
            onHardKeyDiagnosticEvent?.invoke(keyCode, keyStatus, keyType)
        }
    }

    private fun ensureHardKeyOemRegistered(): Result<Unit> {
        if (hardKeyListenerProxy != null) return Result.success(Unit)
        val availability = ensureInitialized()
        if (availability !is MbCanAvailability.Available) {
            return Result.failure(
                IllegalStateException(
                    (availability as? MbCanAvailability.Unavailable)?.reason ?: "mbCAN unavailable",
                ),
            )
        }
        val inst = engineInstance
            ?: return Result.failure(IllegalStateException("MBCanEngine instance is null"))
        return runCatching {
            val iface = Class.forName("com.mengbo.mbCan.interfaces.IMBHardKeyListener")
            val proxy = Proxy.newProxyInstance(
                iface.classLoader,
                arrayOf(iface),
            ) { proxyObj, method, args ->
                when {
                    method.declaringClass == Any::class.java && method.name == "hashCode" ->
                        System.identityHashCode(proxyObj)
                    method.declaringClass == Any::class.java && method.name == "equals" ->
                        proxyObj === args?.getOrNull(0)
                    method.declaringClass == Any::class.java && method.name == "toString" ->
                        "IMBHardKeyListenerProxy@" + Integer.toHexString(System.identityHashCode(proxyObj))
                    method.name == "onHardKey" -> {
                        oemSafe(method.name) {
                            val keyCode = (args?.getOrNull(0) as? Number)?.toInt() ?: return@oemSafe
                            val keyStatus = (args.getOrNull(1) as? Number)?.toInt() ?: return@oemSafe
                            val keyType = (args.getOrNull(2) as? Number)?.toInt() ?: return@oemSafe
                            dispatchHardKey(keyCode, keyStatus, keyType)
                        }
                        null
                    }
                    else -> null
                }
            }
            val register = inst.javaClass.getMethod("registHardKeyListener", iface)
            nativeCallLock.withLock { register.invoke(inst, proxy) }
            hardKeyListenerProxy = proxy
        }.onFailure {
            hardKeyListenerProxy = null
        }
    }

    private fun maybeUnregisterHardKeyOem(): Result<Unit> {
        if (hardKeyHasConsumers()) return Result.success(Unit)
        val inst = engineInstance
        val proxy = hardKeyListenerProxy
        hardKeyListenerProxy = null
        if (inst == null || proxy == null) return Result.success(Unit)
        return runCatching {
            nativeCallLock.withLock {
                inst.javaClass.getMethod("unRegistHardKeyListener").invoke(inst)
            }
        }
    }

    @Synchronized
    fun syncLkaSlaStatusListener(active: Boolean) {
        if (!active) {
            unregisterLkaSlaStatusListener()
            return
        }
        if (lkaSlaStatusListenerProxy != null) return
        if (ensureInitialized() !is MbCanAvailability.Available) return
        val inst = engineInstance ?: return
        val register = registerLkaSlaListenerMethod ?: return
        val iface = try {
            Class.forName("com.mengbo.mbCan.interfaces.IMBCanVehicleLkaSlaStatusCallback")
        } catch (_: Throwable) {
            return
        }
        val loader = iface.classLoader ?: return
        val handler = InvocationHandler { _: Any?, method: Method, args: Array<out Any?>? ->
            oemSafe(method.name) {
                mirrorDeepCallback(method.name, args)
                if (method.name == "onVehicleLkaSlaStatus") {
                    val status = args?.getOrNull(0) ?: return@InvocationHandler null
                    val slaOnOff = runCatching {
                        status.javaClass.getMethod("getFCM_2_SLAOnOffsts").invoke(status) as? Number
                    }.getOrNull()?.toInt()
                    val slaState = runCatching {
                        status.javaClass.getMethod("getFCM_2_SLAState").invoke(status) as? Number
                    }.getOrNull()?.toInt()
                    val slaLimit = runCatching {
                        status.javaClass.getMethod("getFCM_2_SLASpdlimit").invoke(status) as? Number
                    }.getOrNull()?.toInt()
                    MbCanRepository.scheduleLkaSlaPush(
                        slaOnOffRaw = slaOnOff,
                        slaStateRaw = slaState,
                        slaLimitRaw = slaLimit,
                    )
                }
            }
            null
        }
        val proxy = newOemProxy(loader, iface, handler)
        lkaSlaStatusListenerProxy = proxy
        try {
            nativeCall { register.invoke(inst, proxy) }
        } catch (_: Throwable) {
            lkaSlaStatusListenerProxy = null
        }
    }

    @Synchronized
    private fun unregisterLkaSlaStatusListener() {
        val inst = engineInstance
        val unregister = unregisterLkaSlaListenerMethod
        if (inst != null && lkaSlaStatusListenerProxy != null && unregister != null) {
            try {
                nativeCall { unregister.invoke(inst) }
            } catch (_: Throwable) {
            }
        }
        lkaSlaStatusListenerProxy = null
    }

    @Synchronized
    fun syncFrmDectInfoListener(active: Boolean) {
        if (!active) {
            unregisterFrmDectInfoListener()
            return
        }
        if (frmDectInfoListenerProxy != null) return
        if (ensureInitialized() !is MbCanAvailability.Available) return
        val inst = engineInstance ?: return
        val register = registerFrmDectInfoListenerMethod ?: return
        val iface = try {
            Class.forName("com.mengbo.mbCan.interfaces.IMBCanVehicleFrmDectInfoCallback")
        } catch (_: Throwable) {
            return
        }
        val loader = iface.classLoader ?: return
        val handler = InvocationHandler { _: Any?, method: Method, args: Array<out Any?>? ->
            oemSafe(method.name) {
                mirrorDeepCallback(method.name, args)
                if (method.name == "onCanVehicleFrmInfo") {
                    val info = args?.getOrNull(0) ?: return@InvocationHandler null
                    val accMode = runCatching {
                        info.javaClass.getMethod("getFRM_3_ACCMode").invoke(info) as? Number
                    }.getOrNull()?.toInt()
                    val vSetDis = runCatching {
                        info.javaClass.getMethod("getFRM_3_VSetDis").invoke(info) as? Number
                    }.getOrNull()?.toInt()
                    MbCanRepository.scheduleFrmAccPush(accModeRaw = accMode, vSetDisRaw = vSetDis)
                    val dxTarObj = runCatching {
                        info.javaClass.getMethod("getFRM_3_DxTarObj").invoke(info) as? Number
                    }.getOrNull()?.toInt()
                    val objValid = runCatching {
                        info.javaClass.getMethod("getFRM_3_ObjValid").invoke(info) as? Number
                    }.getOrNull()?.toInt()
                    MbCanRepository.scheduleFrmDxTarObjPush(dxRaw = dxTarObj, objValidRaw = objValid)
                    val timeGapIcm = runCatching {
                        info.javaClass.getMethod("getFRM_3_TimeGapSet_ICM").invoke(info) as? Number
                    }.getOrNull()?.toInt()
                    if (timeGapIcm != null) {
                        MbCanRepository.scheduleFrmTimeGapIcmPush(timeGapIcm)
                    }
                }
            }
            null
        }
        val proxy = newOemProxy(loader, iface, handler)
        frmDectInfoListenerProxy = proxy
        try {
            nativeCall { register.invoke(inst, proxy) }
        } catch (_: Throwable) {
            frmDectInfoListenerProxy = null
        }
    }

    @Synchronized
    private fun unregisterFrmDectInfoListener() {
        val inst = engineInstance
        val unregister = unregisterFrmDectInfoListenerMethod
        if (inst != null && frmDectInfoListenerProxy != null && unregister != null) {
            try {
                nativeCall { unregister.invoke(inst) }
            } catch (_: Throwable) {
            }
        }
        frmDectInfoListenerProxy = null
    }

    @Synchronized
    fun syncGaspedStatusListener(active: Boolean) {
        if (!active) {
            unregisterGaspedStatusListener()
            return
        }
        if (gaspedStatusListenerProxy != null) return
        if (ensureInitialized() !is MbCanAvailability.Available) return
        val inst = engineInstance ?: return
        val register = registerGaspedStatusListenerMethod ?: return
        val iface = try {
            Class.forName("com.mengbo.mbCan.interfaces.IMBCanVehicleGaspedStatusCallback")
        } catch (_: Throwable) {
            return
        }
        val loader = iface.classLoader ?: return
        val handler = InvocationHandler { _: Any?, method: Method, args: Array<out Any?>? ->
            oemSafe(method.name) {
                mirrorDeepCallback(method.name, args)
                if (method.name == "onVehicleGaspedStatus") {
                    val info = args?.getOrNull(0) ?: return@InvocationHandler null
                    val cruiseStatus = runCatching {
                        info.javaClass.getMethod("getnCruiseControlStatus").invoke(info) as? Number
                    }.getOrNull()?.toInt()
                    MbCanRepository.scheduleGaspedCcsPush(cruiseControlStatusRaw = cruiseStatus)
                    val position = runCatching {
                        (info.javaClass.getMethod("getfGasPedalPosition").invoke(info) as? Number)?.toFloat()
                    }.getOrNull()
                    val invalid = runCatching {
                        (info.javaClass.getMethod("getnGasPedalPositionInvalidData").invoke(info) as? Number)?.toInt()
                    }.getOrNull()
                    MbCanRepository.scheduleGasPedalPush(position, invalid)
                }
            }
            null
        }
        val proxy = newOemProxy(loader, iface, handler)
        gaspedStatusListenerProxy = proxy
        try {
            nativeCall { register.invoke(inst, proxy) }
        } catch (_: Throwable) {
            gaspedStatusListenerProxy = null
        }
    }

    @Synchronized
    private fun unregisterGaspedStatusListener() {
        val inst = engineInstance
        val unregister = unregisterGaspedStatusListenerMethod
        if (inst != null && gaspedStatusListenerProxy != null && unregister != null) {
            try {
                nativeCall { unregister.invoke(inst) }
            } catch (_: Throwable) {
            }
        }
        gaspedStatusListenerProxy = null
    }
}

