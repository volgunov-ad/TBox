package vad.dashing.tbox.mbcan

import java.lang.reflect.Method
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
}

