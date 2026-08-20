package vad.dashing.tbox.mbcan

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicReference
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
    private const val ENGINE_CLASS = "com.mengbo.mbCan.MBCanEngine"
    private const val DATA_TYPE_CLASS = "com.mengbo.mbCan.defines.MBCanDataType"

    private val availabilityRef = AtomicReference<MbCanAvailability>(MbCanAvailability.Unknown)
    private var engineInstance: Any? = null
    private var canGetVehicleParamMethod: Method? = null
    private var canSetVehicleParamMethod: Method? = null
    private var canGetAudioParamMethod: Method? = null
    private var canSetAudioParamMethod: Method? = null
    private var subscribeMethod: Method? = null
    private var unSubscribeMethod: Method? = null
    private var registerCarSettingsListenerMethod: Method? = null
    private var unregisterCarSettingsListenerMethod: Method? = null
    private var settingsTelemetryProxy: Any? = null
    private var registCmdListenerMethod: Method? = null
    private var unRegistCmdListenerMethod: Method? = null
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
    private var lkaSlaStatusListenerProxy: Any? = null
    private var frmDectInfoListenerProxy: Any? = null
    private var gaspedStatusListenerProxy: Any? = null
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
        return try {
            (canGetVehicleParamMethod?.invoke(engineInstance, propertyId) as? Int)
        } catch (_: Throwable) {
            null
        }
    }

    /** [com.mengbo.mbCan.MBCanEngine.canGetAudioParam] — [com.mengbo.mbCan.defines.MBAudioProperty] ordinal ids. */
    fun canGetAudioParam(propertyId: Int): Int? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        return try {
            (canGetAudioParamMethod?.invoke(engineInstance, propertyId) as? Int)
        } catch (_: Throwable) {
            null
        }
    }

    /** [com.mengbo.mbCan.MBCanEngine.canSetAudioParam] — [com.mengbo.mbCan.defines.MBAudioProperty] value ids. */
    fun canSetAudioParam(propertyId: Int, value: Int): Int? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        return try {
            (canSetAudioParamMethod?.invoke(engineInstance, propertyId, value) as? Int)
        } catch (_: Throwable) {
            null
        }
    }

    fun canSetVehicleParam(propertyId: Int, value: Int): Int? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        return try {
            (canSetVehicleParamMethod?.invoke(engineInstance, propertyId, value) as? Int)
        } catch (_: Throwable) {
            null
        }
    }

    fun subscribe(dataTypeNames: Set<String>): Int? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        if (dataTypeNames.isEmpty()) return 0
        return try {
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

    fun unSubscribe(dataTypeNames: Set<String>): Int? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        if (dataTypeNames.isEmpty()) return 0
        return try {
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

    /**
     * Single [com.mengbo.mbCan.interfaces.IMBCanSettingsCallback] on [MBCanEngine] — forwards speed/engine/
     * fuel/odometer/outside-temp/tires/BCM pushes into [MbCanRepository]. Safe to call once after [ensureInitialized];
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
                    val trunkSts = runCatching {
                        val doorGetter = bcm.javaClass.getMethod("getDoorStatus")
                        val door = doorGetter.invoke(bcm) ?: return@runCatching null
                        val trunkGetter = door.javaClass.getMethod("getTrunkSts")
                        (trunkGetter.invoke(door) as? Number)?.toInt()
                    }.getOrNull()
                    if (moveDir != null || trunkSts != null) {
                        MbCanRepository.scheduleTrunkBcmPush(moveDir, trunkSts)
                    }
                    val reverseRaw = runCatching {
                        val getter = bcm.javaClass.getMethod("getReverseGearSwitch")
                        (getter.invoke(bcm) as? Number)?.toInt()
                    }.getOrNull()
                    if (reverseRaw != null) {
                        MbCanRepository.scheduleReverseGearSwitchPush(reverseRaw)
                    }
                }
            }
            null
        }
        val proxy = Proxy.newProxyInstance(loader, arrayOf(iface), handler)
        settingsTelemetryProxy = proxy
        try {
            registerCarSettingsListenerMethod?.invoke(inst, proxy)
        } catch (_: Throwable) {
            settingsTelemetryProxy = null
        }
    }

    @Synchronized
    fun unregisterSettingsTelemetryBridge() {
        val inst = engineInstance
        if (inst != null && settingsTelemetryProxy != null) {
            try {
                unregisterCarSettingsListenerMethod?.invoke(inst)
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
            if (method.name == "onCmdChanged" && args != null && args.size >= 4) {
                val modular = (args[0] as Number).toInt() and 0xFF
                val rev = (args[1] as Number).toInt() and 0xFF
                val item = (args[2] as Number).toInt() and 0xFFFF
                val value = (args[3] as Number).toInt()
                MbCanDiagnostics.log(
                    "DEBUG",
                    "cfgVehiclePush modular=$modular rev=$rev item=$item value=$value"
                )
                MbCanRepository.scheduleVehicleCfgPush(modular, item, value)
            }
            null
        }
        val proxy = Proxy.newProxyInstance(loader, arrayOf(iface), handler)
        vehicleCfgCmdListenerProxy = proxy
        try {
            registCmdListenerMethod?.invoke(inst, dt, proxy)
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
                unRegistCmdListenerMethod?.invoke(inst, dt)
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
            if (method.name == "onCmdChanged" && args != null && args.size >= 4) {
                val modular = (args[0] as Number).toInt() and 0xFF
                val rev = (args[1] as Number).toInt() and 0xFF
                val item = (args[2] as Number).toInt() and 0xFFFF
                val value = (args[3] as Number).toInt()
                MbCanDiagnostics.log(
                    "DEBUG",
                    "cfgAudioPush modular=$modular rev=$rev item=$item value=$value"
                )
                MbCanRepository.scheduleAudioCfgPush(modular, item, value)
            }
            null
        }
        val proxy = Proxy.newProxyInstance(loader, arrayOf(iface), handler)
        audioCfgCmdListenerProxy = proxy
        try {
            registCmdListenerMethod?.invoke(inst, dt, proxy)
        } catch (_: Throwable) {
            audioCfgCmdListenerProxy = null
        }
    }

    /**
     * Reads trunk movement and door status from cached BCM snapshot
     * ([com.mengbo.mbCan.defines.MBCanDataType.eMBCAN_VEHICLE_BCM_STATUS]).
     */
    data class BcmTrunkSnapshot(val moveDir: Int?, val trunkSts: Int?)

    fun readVehicleBcmTrunkSnapshot(): BcmTrunkSnapshot? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        val inst = engineInstance ?: return null
        return runCatching {
            val engineClass = Class.forName(ENGINE_CLASS)
            val getMbCanData = engineClass.getMethod("getMbCanData", Int::class.javaPrimitiveType, Class::class.java)
            val bcmCls = Class.forName("com.mengbo.mbCan.entity.MBCanVehicleBcmStatus")
            val bcmObj = getMbCanData.invoke(inst, 21, bcmCls) ?: return null
            val moveDir = bcmCls.getMethod("getRearDoorMoveDir").invoke(bcmObj)?.let { (it as Number).toInt() }
            val trunkSts = runCatching {
                val door = bcmCls.getMethod("getDoorStatus").invoke(bcmObj) ?: return@runCatching null
                val trunkGetter = door.javaClass.getMethod("getTrunkSts")
                trunkGetter.invoke(door)?.let { (it as Number).toInt() }
            }.getOrNull()
            BcmTrunkSnapshot(moveDir = moveDir, trunkSts = trunkSts)
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
            val engObj = getMbCanData.invoke(inst, 22, engCls) ?: return null
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
            val engObj = getMbCanData.invoke(inst, 22, engCls) ?: return null
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
            val speedObj = getMbCanData.invoke(inst, 1, speedCls) ?: return null
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
            val speedObj = getMbCanData.invoke(inst, 20, speedCls)
                ?: getMbCanData.invoke(inst, 1, speedCls)
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
            val bcmObj = getMbCanData.invoke(inst, 21, bcmCls) ?: return null
            val raw = (bcmCls.getMethod("getReverseGearSwitch").invoke(bcmObj) as? Number)?.toInt() ?: return null
            VehicleGearDomain.decodeReverseGearSwitch(raw)
        }.getOrNull()
    }

    /** Fuel % from [MBCanVehicleFuelLevel.getFuelLevel]; valid range 0…100. Data type 12. */
    fun readVehicleFuelLevelPercent(): UInt? {
        if (ensureInitialized() !is MbCanAvailability.Available) return null
        val inst = engineInstance ?: return null
        return runCatching {
            val engineClass = Class.forName(ENGINE_CLASS)
            val getMbCanData = engineClass.getMethod("getMbCanData", Int::class.javaPrimitiveType, Class::class.java)
            val fuelCls = Class.forName("com.mengbo.mbCan.entity.MBCanVehicleFuelLevel")
            val fuelObj = getMbCanData.invoke(inst, 12, fuelCls) ?: return null
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
            val fuelObj = getMbCanData.invoke(inst, 12, fuelCls) ?: return null
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
            val engObj = getMbCanData.invoke(inst, 22, engCls) ?: return null
            val raw = (engCls.getMethod("getFuelRollingCounter").invoke(engObj) as? Number)?.toInt() ?: return null
            InstantFuelConsumptionDomain.decodeRawCounter(raw)
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
            val tripObj = getMbCanData.invoke(inst, 48, tripCls) ?: return null
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
            val pmObj = getMbCanData.invoke(inst, 28, pmCls) ?: return null
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
            val steerObj = getMbCanData.invoke(inst, 3, steerCls) ?: return null
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
            val turnObj = getMbCanData.invoke(inst, 2, turnCls) ?: return null
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
            val odoObj = getMbCanData.invoke(inst, 16, odoCls) ?: return null
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
            val wheelObj = getMbCanData.invoke(inst, 4, wheelCls) ?: return null
            fun counter(name: String): Int =
                (wheelCls.getMethod(name).invoke(wheelObj) as? Number)?.toInt() ?: 0
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
            val tempObj = getMbCanData.invoke(inst, 38, tempCls) ?: return null
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
            val tiresObj = getMbCanData.invoke(inst, 34, tiresCls) ?: return null
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
                unRegistCmdListenerMethod?.invoke(inst, dt)
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
            val spdObj = getMbCanData.invoke(inst, 1, spdCls)
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
                val engObj = getMbCanData.invoke(inst, dataType, engCls)
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
    ) {
        vehicleListenerWantSteer = needSteer
        vehicleListenerWantTurnLights = needTurnLights
        vehicleListenerWantWheelPulse = needWheelPulse
        if (!needSteer && !needTurnLights && !needWheelPulse) {
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
            null
        }
        val proxy = Proxy.newProxyInstance(loader, arrayOf(iface), handler)
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
            null
        }
        val proxy = Proxy.newProxyInstance(loader, arrayOf(iface), handler)
        lkaSlaStatusListenerProxy = proxy
        try {
            register.invoke(inst, proxy)
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
                unregister.invoke(inst)
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
            if (method.name == "onCanVehicleFrmInfo") {
                val info = args?.getOrNull(0) ?: return@InvocationHandler null
                val accMode = runCatching {
                    info.javaClass.getMethod("getFRM_3_ACCMode").invoke(info) as? Number
                }.getOrNull()?.toInt()
                val vSetDis = runCatching {
                    info.javaClass.getMethod("getFRM_3_VSetDis").invoke(info) as? Number
                }.getOrNull()?.toInt()
                MbCanRepository.scheduleFrmAccPush(accModeRaw = accMode, vSetDisRaw = vSetDis)
            }
            null
        }
        val proxy = Proxy.newProxyInstance(loader, arrayOf(iface), handler)
        frmDectInfoListenerProxy = proxy
        try {
            register.invoke(inst, proxy)
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
                unregister.invoke(inst)
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
            if (method.name == "onVehicleGaspedStatus") {
                val info = args?.getOrNull(0) ?: return@InvocationHandler null
                val cruiseStatus = runCatching {
                    info.javaClass.getMethod("getnCruiseControlStatus").invoke(info) as? Number
                }.getOrNull()?.toInt()
                MbCanRepository.scheduleGaspedCcsPush(cruiseControlStatusRaw = cruiseStatus)
            }
            null
        }
        val proxy = Proxy.newProxyInstance(loader, arrayOf(iface), handler)
        gaspedStatusListenerProxy = proxy
        try {
            register.invoke(inst, proxy)
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
                unregister.invoke(inst)
            } catch (_: Throwable) {
            }
        }
        gaspedStatusListenerProxy = null
    }
}

