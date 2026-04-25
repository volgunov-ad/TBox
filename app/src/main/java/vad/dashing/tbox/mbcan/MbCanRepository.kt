package vad.dashing.tbox.mbcan

import kotlinx.coroutines.CoroutineScope
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
    SteeringWheelHeat(setOf("eMBCAN_CFG_VEHICLE"));
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

    private val sourceSignals = mutableMapOf<String, Set<MbCanSignal>>()
    private val sourceMutex = Mutex()
    private var boundScope: CoroutineScope? = null
    private var reapplyJob: Job? = null

    private val _availability = MutableStateFlow<MbCanAvailability>(MbCanAvailability.Unknown)
    val availability: StateFlow<MbCanAvailability> = _availability.asStateFlow()

    private val _steeringWheelHeatState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val steeringWheelHeatState: StateFlow<MbCanBinaryState> = _steeringWheelHeatState.asStateFlow()

    suspend fun bind(scope: CoroutineScope) {
        boundScope = scope
        _availability.value = MbCanEngineFacade.probeAvailability()
        TboxRepository.addLog("DEBUG", "MBCAN_TMP", "bind() availability=${_availability.value}")
        MbCanJobManager.attach(scope)
        scheduleReapplyAllInterests()
    }

    suspend fun unbind() {
        TboxRepository.addLog("DEBUG", "MBCAN_TMP", "unbind()")
        reapplyJob?.cancel()
        reapplyJob = null
        boundScope = null
        MbCanJobManager.detach()
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
            MbCanBinaryState.On -> 0
            MbCanBinaryState.Off -> 1
            MbCanBinaryState.Unknown -> 1
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
        MbCanJobManager.requestBurst(MbCanSignal.SteeringWheelHeat)
        refreshSteeringWheelHeat()
        TboxRepository.addLog("DEBUG", "MBCAN_TMP", "steering state after refresh=${steeringWheelHeatState.value}")
        return MbCanCommandResult(setResult >= 0, "Set result: $setResult")
    }

    suspend fun refreshSignal(signal: MbCanSignal) {
        when (signal) {
            MbCanSignal.SteeringWheelHeat -> refreshSteeringWheelHeat()
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
        _steeringWheelHeatState.value = when {
            raw == null -> MbCanBinaryState.Unknown
            raw > 0 -> MbCanBinaryState.On
            else -> MbCanBinaryState.Off
        }
        TboxRepository.addLog("DEBUG", "MBCAN_TMP", "refreshSteeringWheelHeat raw=$raw state=${_steeringWheelHeatState.value}")
    }

    private suspend fun ensureMbCanReadyIfNeeded() {
        if (MbCanEngineFacade.isInitialized()) return
        val availability = MbCanEngineFacade.ensureInitialized()
        _availability.value = availability
        TboxRepository.addLog("DEBUG", "MBCAN_TMP", "ensureMbCanReadyIfNeeded availability=$availability")
        if (availability is MbCanAvailability.Available) {
            MbCanJobManager.onEngineInitialized()
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
        val mergedSignals = sourceMutex.withLock { sourceSignals.values.flatten().toSet() }
        MbCanJobManager.replaceSignals(mergedSignals)
    }

    private fun widgetKeyToSignal(widgetKey: String): MbCanSignal? {
        return when (widgetKey) {
            SIGNAL_STEERING_WIDGET_KEY -> MbCanSignal.SteeringWheelHeat
            else -> null
        }
    }
}

