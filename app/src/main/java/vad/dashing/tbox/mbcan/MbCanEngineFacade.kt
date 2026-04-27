package vad.dashing.tbox.mbcan

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicReference

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
    private var subscribeMethod: Method? = null
    private var unSubscribeMethod: Method? = null
    private var registerCarSettingsListenerMethod: Method? = null
    private var unregisterCarSettingsListenerMethod: Method? = null
    private var settingsTelemetryProxy: Any? = null
    private var registCmdListenerMethod: Method? = null
    private var unRegistCmdListenerMethod: Method? = null
    private var cfgVehicleDataType: Any? = null
    private var vehicleCfgCmdListenerProxy: Any? = null
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
            val dataTypeClass = Class.forName(DATA_TYPE_CLASS) as Class<out Enum<*>>
            cfgVehicleDataType = java.lang.Enum.valueOf(dataTypeClass, "eMBCAN_CFG_VEHICLE")
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
     * Single [com.mengbo.mbCan.interfaces.IMBCanSettingsCallback] on [MBCanEngine] — forwards speed/engine
     * pushes into [MbCanRepository]. Safe to call once after [ensureInitialized]; no-op if already registered.
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
                "onCanVehicleSpeed",
                "onVehicleEngineStatusChange" -> Unit
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
                val item = (args[2] as Number).toInt() and 0xFFFF
                val value = (args[3] as Number).toInt()
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
}

