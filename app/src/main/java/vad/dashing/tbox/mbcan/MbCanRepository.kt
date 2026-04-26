package vad.dashing.tbox.mbcan

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import vad.dashing.tbox.TboxRepository

enum class MbCanSignal(val subscribeDataTypes: Set<String>) {
    SteeringWheelHeat(setOf("eMBCAN_CFG_VEHICLE")),

    /**
     * Service-wide stream pack: push via [IMBCanSettingsCallback] where wired; union is periodically
     * resubscribed (unsubscribe → subscribe) by [MbCanJobManager]. No per-minute property poll job.
     */
    ServiceDebugTelemetry(
        setOf(
            "eMBCAN_VEHICLE_SPEED",
            "eMBCAN_VEHICLE_ENGINE",
            "eMBCAN_VEHICLE_ENGINE_GEAR",
            "eMBCAN_VEHICLE_LKA_STATUS",
            "eMBCAN_VEHICLE_FRM_INFO",
            "eMBCAN_SEAT_STATUS",
            "eMBCAN_CFG_VEHICLE"
        )
    )
}

sealed class MbCanBinaryState {
    data object Unknown : MbCanBinaryState()
    data object Off : MbCanBinaryState()
    data object On : MbCanBinaryState()
    data class Unavailable(val reason: String) : MbCanBinaryState()
}

data class MbCanCommandResult(
    val success: Boolean,
    val message: String
)

sealed class MbCanCommand {
    data class ToggleProperty(val propertyId: Int) : MbCanCommand()
    data class SetProperty(val propertyId: Int, val value: Int) : MbCanCommand()
    data class RefreshSignal(val signal: MbCanSignal) : MbCanCommand()
}

object MbCanRepository {
    private const val SIGNAL_STEERING_WIDGET_KEY = "steeringWheelHeatWidget"
    private const val INTERESTS_DEBOUNCE_MS = 350L
    private const val POST_COMMAND_VERIFY_DELAY_MS = 500L

    private val serviceWideSignals: Set<MbCanSignal> = setOf(MbCanSignal.ServiceDebugTelemetry)

    /**
     * [MBVehicleProperty.eVEHICLE_SET_MFS_HEAT_SWITCH] (188): `canGetVehicleParam` uses an
     * active-low style encoding on Jetour Dashing — 1 while heat is physically OFF, 0 when ON.
     * (Naive `raw > 0` would invert UI and send the wrong toggle command.)
     */
    private fun decodeSteeringWheelHeatRaw(raw: Int): MbCanBinaryState {
        return when (raw) {
            0 -> MbCanBinaryState.On
            1 -> MbCanBinaryState.Off
            else -> if (raw < 0) MbCanBinaryState.Unknown else MbCanBinaryState.On
        }
    }

    private val sourceSignals = mutableMapOf<String, Set<MbCanSignal>>()
    private val sourceMutex = Mutex()
    private var boundScope: CoroutineScope? = null
    private var reapplyJob: Job? = null

    private val _availability = MutableStateFlow<MbCanAvailability>(MbCanAvailability.Unknown)
    val availability: StateFlow<MbCanAvailability> = _availability.asStateFlow()

    private val _steeringWheelHeatState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val steeringWheelHeatState: StateFlow<MbCanBinaryState> = _steeringWheelHeatState.asStateFlow()

    private val _lastVehicleSpeedPush = MutableStateFlow<String?>(null)
    val lastVehicleSpeedPush: StateFlow<String?> = _lastVehicleSpeedPush.asStateFlow()

    private val _lastVehicleEnginePush = MutableStateFlow<String?>(null)
    val lastVehicleEnginePush: StateFlow<String?> = _lastVehicleEnginePush.asStateFlow()

    suspend fun bind(scope: CoroutineScope) {
        boundScope = scope
        _availability.value = MbCanEngineFacade.probeAvailability()
        TboxRepository.addLog("DEBUG", "MBCAN_TMP", "bind() availability=${_availability.value}")
        MbCanJobManager.attach(scope)
        MbCanEngineFacade.registerSettingsTelemetryBridge()
        scheduleReapplyAllInterests()
    }

    suspend fun unbind() {
        TboxRepository.addLog("DEBUG", "MBCAN_TMP", "unbind()")
        MbCanEngineFacade.unregisterSettingsTelemetryBridge()
        reapplyJob?.cancel()
        reapplyJob = null
        boundScope = null
        MbCanJobManager.detach()
    }

    /**
     * Vendor [com.mengbo.mbCan.entity.MBCanVehicleSpeed] instance from callback — formatted via reflection
     * so this module does not require a direct Kotlin dependency on those Java types.
     */
    fun onPushVehicleSpeed(speed: Any?) {
        val line = formatMbCanVehicleSpeedLine(speed) ?: return
        val scope = boundScope ?: return
        scope.launch(Dispatchers.Default) {
            _lastVehicleSpeedPush.value = line
            TboxRepository.addLog("DEBUG", "MBCAN_TMP", "mbCAN cb onCanVehicleSpeed $line")
        }
    }

    /** Vendor [com.mengbo.mbCan.entity.MBCanVehicleEngine] instance from callback (reflection). */
    fun onPushVehicleEngine(engine: Any?) {
        val line = formatMbCanVehicleEngineLine(engine) ?: return
        val scope = boundScope ?: return
        scope.launch(Dispatchers.Default) {
            _lastVehicleEnginePush.value = line
            TboxRepository.addLog("DEBUG", "MBCAN_TMP", "mbCAN cb onVehicleEngineStatusChange $line")
        }
    }

    private fun formatMbCanVehicleSpeedLine(obj: Any?): String? {
        if (obj == null) return null
        return try {
            val cls = obj.javaClass
            val speed = cls.getMethod("getSpeed").invoke(obj) as Float
            val gear = (cls.getMethod("getGear").invoke(obj) as Byte).toInt() and 0xFF
            val spdValid = (cls.getMethod("getSpeedValidSts").invoke(obj) as Byte).toInt() and 0xFF
            val gearValid = (cls.getMethod("getGearValidSts").invoke(obj) as Byte).toInt() and 0xFF
            val pwrRdy = (cls.getMethod("getPowerReadySts").invoke(obj) as Byte).toInt() and 0xFF
            "push speed=$speed gear=$gear spdValid=$spdValid gearValid=$gearValid pwrRdy=$pwrRdy"
        } catch (_: Throwable) {
            null
        }
    }

    private fun formatMbCanVehicleEngineLine(obj: Any?): String? {
        if (obj == null) return null
        return try {
            val cls = obj.javaClass
            val fSpeed = cls.getMethod("getfSpeed").invoke(obj) as Float
            val temp = cls.getMethod("getfTemperture").invoke(obj) as Float
            val st = (cls.getMethod("getStatus").invoke(obj) as Byte).toInt() and 0xFF
            val gear = (cls.getMethod("getGear").invoke(obj) as Byte).toInt() and 0xFF
            val dsp = cls.getMethod("getnDisplayVehiceSpeed").invoke(obj) as Short
            "push engine fSpeed=$fSpeed temp=$temp st=$st gear=$gear dspSpd=$dsp"
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun setSourceWidgetKeys(sourceId: String, widgetKeys: Set<String>) {
        val signals = widgetKeys.mapNotNull { widgetKeyToSignal(it) }.toSet()
        TboxRepository.addLog(
            "DEBUG",
            "MBCAN_TMP",
            "setSourceWidgetKeys source=$sourceId widgetKeys=${widgetKeys.joinToString()} signals=${signals.joinToString()}"
        )
        sourceMutex.withLock {
            if (signals.isEmpty()) {
                sourceSignals.remove(sourceId)
            } else {
                sourceSignals[sourceId] = signals
            }
        }
        scheduleReapplyAllInterests()
    }

    suspend fun clearSource(sourceId: String) {
        TboxRepository.addLog("DEBUG", "MBCAN_TMP", "clearSource source=$sourceId")
        sourceMutex.withLock { sourceSignals.remove(sourceId) }
        scheduleReapplyAllInterests()
    }

    suspend fun execute(command: MbCanCommand): MbCanCommandResult {
        TboxRepository.addLog("DEBUG", "MBCAN_TMP", "execute command=$command")
        ensureMbCanReadyIfNeeded()
        return when (command) {
            is MbCanCommand.ToggleProperty -> executeToggleProperty(command.propertyId)
            is MbCanCommand.SetProperty -> executeSetProperty(command.propertyId, command.value)
            is MbCanCommand.RefreshSignal -> {
                refreshSignal(command.signal)
                MbCanCommandResult(true, "Refresh requested")
            }
        }
    }

    private suspend fun executeToggleProperty(propertyId: Int): MbCanCommandResult {
        TboxRepository.addLog("DEBUG", "MBCAN_TMP", "executeToggleProperty propertyId=$propertyId")
        if (propertyId == MbCanKnownVehiclePropertyId.STEERING_WHEEL_HEAT_SWITCH) {
            return toggleSteeringWheelHeat()
        }
        if (availability.value !is MbCanAvailability.Available) {
            return MbCanCommandResult(false, "mbCAN unavailable")
        }
        val current = MbCanEngineFacade.canGetVehicleParam(propertyId)
            ?: return MbCanCommandResult(false, "Pre-read failed")
                .also {
                    TboxRepository.addLog("ERROR", "MBCAN_TMP", "toggle pre-read failed propertyId=$propertyId")
                }
        val target = if (current > 0) 0 else 1
        TboxRepository.addLog("DEBUG", "MBCAN_TMP", "toggle pre-read current=$current target=$target propertyId=$propertyId")
        val setResult = MbCanEngineFacade.canSetVehicleParam(propertyId, target)
            ?: return MbCanCommandResult(false, "Set command failed")
                .also {
                    TboxRepository.addLog("ERROR", "MBCAN_TMP", "toggle set failed propertyId=$propertyId target=$target")
                }
        TboxRepository.addLog("DEBUG", "MBCAN_TMP", "toggle set result=$setResult propertyId=$propertyId target=$target")
        if (setResult >= 0) {
            delay(POST_COMMAND_VERIFY_DELAY_MS)
            val after = MbCanEngineFacade.canGetVehicleParam(propertyId)
            TboxRepository.addLog("DEBUG", "MBCAN_TMP", "toggle verify propertyId=$propertyId after=$after")
        }
        return MbCanCommandResult(setResult >= 0, "Set result: $setResult")
    }

    private suspend fun executeSetProperty(propertyId: Int, value: Int): MbCanCommandResult {
        TboxRepository.addLog("DEBUG", "MBCAN_TMP", "executeSetProperty propertyId=$propertyId value=$value")
        if (availability.value !is MbCanAvailability.Available) {
            return MbCanCommandResult(false, "mbCAN unavailable")
        }
        val setResult = MbCanEngineFacade.canSetVehicleParam(propertyId, value)
            ?: return MbCanCommandResult(false, "Set command failed")
                .also {
                    TboxRepository.addLog("ERROR", "MBCAN_TMP", "set failed propertyId=$propertyId value=$value")
                }
        TboxRepository.addLog("DEBUG", "MBCAN_TMP", "set result=$setResult propertyId=$propertyId value=$value")
        if (setResult >= 0) {
            delay(POST_COMMAND_VERIFY_DELAY_MS)
            val after = MbCanEngineFacade.canGetVehicleParam(propertyId)
            TboxRepository.addLog("DEBUG", "MBCAN_TMP", "set verify propertyId=$propertyId after=$after")
        }
        return MbCanCommandResult(setResult >= 0, "Set result: $setResult")
    }

    private suspend fun toggleSteeringWheelHeat(): MbCanCommandResult {
        TboxRepository.addLog("DEBUG", "MBCAN_TMP", "toggleSteeringWheelHeat() begin")
        refreshSteeringWheelHeat()
        if (availability.value !is MbCanAvailability.Available) {
            return MbCanCommandResult(false, "mbCAN unavailable")
        }
        val current = steeringWheelHeatState.value
        val target = when (current) {
            MbCanBinaryState.On -> 1
            MbCanBinaryState.Off -> 0
            MbCanBinaryState.Unknown -> 0
            is MbCanBinaryState.Unavailable -> return MbCanCommandResult(false, "mbCAN unavailable")
        }
        val setResult = MbCanEngineFacade.canSetVehicleParam(
            MbCanKnownVehiclePropertyId.STEERING_WHEEL_HEAT_SWITCH,
            target
        ) ?: return MbCanCommandResult(false, "Set command failed")
            .also {
                TboxRepository.addLog("ERROR", "MBCAN_TMP", "steering set failed target=$target")
            }
        TboxRepository.addLog("DEBUG", "MBCAN_TMP", "steering set result=$setResult target=$target current=$current")
        if (setResult >= 0) {
            delay(POST_COMMAND_VERIFY_DELAY_MS)
            refreshSteeringWheelHeat()
            TboxRepository.addLog("DEBUG", "MBCAN_TMP", "steering verify after delay state=${steeringWheelHeatState.value}")
        }
        MbCanJobManager.requestBurst(MbCanSignal.SteeringWheelHeat)
        refreshSteeringWheelHeat()
        TboxRepository.addLog("DEBUG", "MBCAN_TMP", "steering state after burst refresh=${steeringWheelHeatState.value}")
        return MbCanCommandResult(setResult >= 0, "Set result: $setResult")
    }

    suspend fun refreshSignal(signal: MbCanSignal) {
        when (signal) {
            MbCanSignal.SteeringWheelHeat -> refreshSteeringWheelHeat()
            MbCanSignal.ServiceDebugTelemetry -> Unit
        }
    }

    private suspend fun refreshSteeringWheelHeat() {
        if (!MbCanEngineFacade.isInitialized()) {
            _availability.value = MbCanEngineFacade.probeAvailability()
            _steeringWheelHeatState.value = MbCanBinaryState.Unknown
            return
        }

        val availability = MbCanEngineFacade.availability
        _availability.value = availability
        if (availability !is MbCanAvailability.Available) {
            TboxRepository.addLog("WARN", "MBCAN_TMP", "refreshSteeringWheelHeat unavailable=$availability")
            _steeringWheelHeatState.value = MbCanBinaryState.Unavailable(
                reason = (availability as? MbCanAvailability.Unavailable)?.reason ?: "Unavailable"
            )
            return
        }
        val raw = MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.STEERING_WHEEL_HEAT_SWITCH)
        _steeringWheelHeatState.value = if (raw == null) {
            MbCanBinaryState.Unknown
        } else {
            decodeSteeringWheelHeatRaw(raw)
        }
        TboxRepository.addLog(
            "DEBUG",
            "MBCAN_TMP",
            "refreshSteeringWheelHeat raw=$raw state=${_steeringWheelHeatState.value}"
        )
    }

    private suspend fun ensureMbCanReadyIfNeeded() {
        if (MbCanEngineFacade.isInitialized()) return
        val availability = MbCanEngineFacade.ensureInitialized()
        _availability.value = availability
        TboxRepository.addLog("DEBUG", "MBCAN_TMP", "ensureMbCanReadyIfNeeded availability=$availability")
        if (availability is MbCanAvailability.Available) {
            MbCanJobManager.onEngineInitialized()
            MbCanEngineFacade.registerSettingsTelemetryBridge()
            reapplyAllInterests()
        }
    }

    private fun scheduleReapplyAllInterests() {
        val scope = boundScope ?: return
        reapplyJob?.cancel()
        reapplyJob = scope.launch {
            delay(INTERESTS_DEBOUNCE_MS)
            reapplyAllInterests()
        }
    }

    private suspend fun reapplyAllInterests() {
        val mergedSignals = sourceMutex.withLock { sourceSignals.values.flatten().toSet() } + serviceWideSignals
        MbCanJobManager.replaceSignals(mergedSignals)
    }

    private fun widgetKeyToSignal(widgetKey: String): MbCanSignal? {
        return when (widgetKey) {
            SIGNAL_STEERING_WIDGET_KEY -> MbCanSignal.SteeringWheelHeat
            else -> null
        }
    }

    /**
     * Whether any widget [dataKey] on a panel needs mbCAN (subscribe/refresh). Used so panels without
     * such widgets never call [setSourceWidgetKeys]/[clearSource].
     */
    fun widgetConfigsNeedMbCan(dataKeys: Iterable<String>): Boolean {
        return dataKeys.any { raw ->
            val key = raw.trim()
            key.isNotBlank() && key != "null" && widgetKeyToSignal(key) != null
        }
    }
}

