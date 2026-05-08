package vad.dashing.tbox.can

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import vad.dashing.tbox.mbcan.MbCanCommand
import vad.dashing.tbox.mbcan.MbCanCommandPolicy
import vad.dashing.tbox.mbcan.MbCanCommandRegistry
import vad.dashing.tbox.mbcan.MbCanCommandResult
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId
import vad.dashing.tbox.mbcan.MbCanRepository

/**
 * Reflection-based VHAL bridge (no compile-time android.car dependency).
 * Reads/writes int properties and mirrors values into MbCanRepository flows so existing UI remains intact.
 */
object VhalControlBackend : CanControlBackend {
    private const val TAG = "VhalControlBackend"
    private const val DEFAULT_AREA_ID = 0
    private const val POLL_INTERVAL_MS = 800L

    private val trackedPropertyIds = listOf(
        MbCanKnownVehiclePropertyId.STEERING_WHEEL_HEAT_SWITCH,
        MbCanKnownVehiclePropertyId.FRONT_WINDSCREEN_HEAT_SWITCH,
        MbCanKnownVehiclePropertyId.HVAC_DEFROSTER_SWITCH,
        MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH,
        MbCanKnownVehiclePropertyId.FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH,
        MbCanKnownVehiclePropertyId.REAR_LEFT_SEAT_HEAT_SWITCH,
        MbCanKnownVehiclePropertyId.REAR_RIGHT_SEAT_HEAT_SWITCH
    )

    private var carInstance: Any? = null
    private var carPropertyManager: Any? = null
    private var pollJob: Job? = null

    override suspend fun bind(context: Context, scope: CoroutineScope) {
        val manager = connectCarPropertyManager(context)
        if (manager == null) {
            MbCanRepository.applyExternalAvailability(available = false, reason = "VHAL unavailable")
            return
        }
        carPropertyManager = manager
        MbCanRepository.applyExternalAvailability(available = true, reason = null)
        pollJob?.cancel()
        pollJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                for (propertyId in trackedPropertyIds) {
                    val raw = readIntProperty(propertyId)
                    MbCanRepository.applyExternalVehicleProperty(propertyId, raw)
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    override suspend fun unbind() {
        pollJob?.cancel()
        pollJob = null
        carPropertyManager = null
        disconnectCar()
    }

    override suspend fun execute(command: MbCanCommand): MbCanCommandResult {
        return when (command) {
            is MbCanCommand.RefreshSignal -> {
                for (propertyId in trackedPropertyIds) {
                    val raw = readIntProperty(propertyId)
                    if (raw != null) {
                        MbCanRepository.applyExternalVehicleProperty(propertyId, raw)
                    }
                }
                MbCanCommandResult(true, "Refresh requested")
            }
            is MbCanCommand.SetProperty -> setProperty(command.propertyId, command.value)
            is MbCanCommand.ToggleProperty -> toggleProperty(command.propertyId)
        }
    }

    private suspend fun toggleProperty(propertyId: Int): MbCanCommandResult {
        val spec = MbCanCommandRegistry.get(propertyId)
            ?: return MbCanCommandResult(false, "No command policy for propertyId=$propertyId")
        val policy = spec.policy as? MbCanCommandPolicy.ToggleBinary
            ?: return MbCanCommandResult(false, "Toggle unsupported by policy for propertyId=$propertyId")
        val current = readIntProperty(propertyId)
            ?: return MbCanCommandResult(false, "Read before toggle failed")
        val target = when (current) {
            policy.onValue -> policy.offValue
            policy.offValue -> policy.onValue
            else -> policy.unknownFallbackValue
        }
        return setProperty(propertyId, target)
    }

    private suspend fun setProperty(propertyId: Int, value: Int): MbCanCommandResult {
        val manager = carPropertyManager ?: return MbCanCommandResult(false, "VHAL backend not connected")
        return try {
            val method = manager.javaClass.getMethod(
                "setIntProperty",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            method.invoke(manager, mapToVhalPropertyId(propertyId), DEFAULT_AREA_ID, value)
            MbCanRepository.applyExternalVehicleProperty(propertyId, value)
            MbCanCommandResult(true, "Set result: 0")
        } catch (t: Throwable) {
            MbCanCommandResult(false, "Set failed: ${t.javaClass.simpleName}")
        }
    }

    private fun readIntProperty(propertyId: Int): Int? {
        val manager = carPropertyManager ?: return null
        return try {
            val method = manager.javaClass.getMethod(
                "getIntProperty",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            method.invoke(manager, mapToVhalPropertyId(propertyId), DEFAULT_AREA_ID) as? Int
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Default mapping is identity (mbCAN property id == VHAL property id).
     * If a specific HU uses different IDs, this mapper is the single place to override.
     */
    private fun mapToVhalPropertyId(mbCanPropertyId: Int): Int = mbCanPropertyId

    private fun connectCarPropertyManager(context: Context): Any? {
        return try {
            val carClass = Class.forName("android.car.Car")
            val createCar = carClass.getMethod("createCar", Context::class.java)
            val car = createCar.invoke(null, context) ?: return null
            carInstance = car
            try {
                carClass.getMethod("connect").invoke(car)
            } catch (_: Throwable) {
            }
            val propertyService = try {
                carClass.getField("PROPERTY_SERVICE").get(null) as String
            } catch (_: Throwable) {
                "property"
            }
            val getCarManager = carClass.getMethod("getCarManager", String::class.java)
            getCarManager.invoke(car, propertyService)
        } catch (t: Throwable) {
            Log.w(TAG, "connectCarPropertyManager failed: ${t.javaClass.simpleName}: ${t.message}")
            null
        }
    }

    private fun disconnectCar() {
        val car = carInstance ?: return
        try {
            car.javaClass.getMethod("disconnect").invoke(car)
        } catch (_: Throwable) {
        } finally {
            carInstance = null
        }
    }
}
