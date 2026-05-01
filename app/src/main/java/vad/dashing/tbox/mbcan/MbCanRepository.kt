package vad.dashing.tbox.mbcan

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import vad.dashing.tbox.FRONT_LEFT_SEAT_HEAT_VENT_SINGLE_WIDGET_DATA_KEY
import vad.dashing.tbox.FRONT_RIGHT_SEAT_HEAT_VENT_SINGLE_WIDGET_DATA_KEY
import vad.dashing.tbox.REAR_LEFT_SEAT_HEAT_WIDGET_DATA_KEY
import vad.dashing.tbox.REAR_RIGHT_SEAT_HEAT_WIDGET_DATA_KEY

enum class MbCanSignal(val subscribeDataTypes: Set<String>) {
    SteeringWheelHeat(setOf("eMBCAN_CFG_VEHICLE")),
    FrontLeftSeatMode(setOf("eMBCAN_CFG_VEHICLE")),
    FrontRightSeatMode(setOf("eMBCAN_CFG_VEHICLE")),
    RearLeftSeatMode(setOf("eMBCAN_CFG_VEHICLE")),
    RearRightSeatMode(setOf("eMBCAN_CFG_VEHICLE"))
}

sealed class MbCanBinaryState {
    data object Unknown : MbCanBinaryState()
    data object Off : MbCanBinaryState()
    data object On : MbCanBinaryState()
    data class Unavailable(val reason: String) : MbCanBinaryState()
}

sealed class MbCanSeatModeState {
    data object Unknown : MbCanSeatModeState()
    data object Off : MbCanSeatModeState()
    data class Heat(val level: Int) : MbCanSeatModeState()
    data class Vent(val level: Int) : MbCanSeatModeState()
    data class Unavailable(val reason: String) : MbCanSeatModeState()
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
    private data class WidgetSignalBinding(
        val widgetKey: String,
        val signal: MbCanSignal
    )

    private val widgetSignalRegistry = listOf(
        WidgetSignalBinding("steeringWheelHeatWidget", MbCanSignal.SteeringWheelHeat),
        WidgetSignalBinding("frontLeftSeatHeatVentWidget", MbCanSignal.FrontLeftSeatMode),
        WidgetSignalBinding("frontRightSeatHeatVentWidget", MbCanSignal.FrontRightSeatMode),
        WidgetSignalBinding(FRONT_LEFT_SEAT_HEAT_VENT_SINGLE_WIDGET_DATA_KEY, MbCanSignal.FrontLeftSeatMode),
        WidgetSignalBinding(FRONT_RIGHT_SEAT_HEAT_VENT_SINGLE_WIDGET_DATA_KEY, MbCanSignal.FrontRightSeatMode),
        WidgetSignalBinding(REAR_LEFT_SEAT_HEAT_WIDGET_DATA_KEY, MbCanSignal.RearLeftSeatMode),
        WidgetSignalBinding(REAR_RIGHT_SEAT_HEAT_WIDGET_DATA_KEY, MbCanSignal.RearRightSeatMode)
    )

    private val signalByWidgetKey: Map<String, MbCanSignal> = widgetSignalRegistry
        .associate { it.widgetKey to it.signal }
    private const val INTERESTS_DEBOUNCE_MS = 350L
    private const val POST_COMMAND_VERIFY_DELAY_MS = 500L
    private const val VEHICLE_CFG_MODULAR = 2
    private const val CFG_VEHICLE_DATA_TYPE = "eMBCAN_CFG_VEHICLE"
    /** Coalesce rapid [eMBCAN_CFG_VEHICLE] pushes before updating [StateFlow]s (50–150 ms band). */
    private const val CFG_VEHICLE_PUSH_COALESCE_MS = 100L

    /**
     * Single-thread dispatcher for streak counters, burst decisions, and [StateFlow] writes so push
     * (Handler → launch) and poll ([MbCanJobManager] IO) never interleave.
     */
    private val stateApplyDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "mbcan-state-apply").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    private val cfgPushHandler = Handler(Looper.getMainLooper())
    private val pendingCfgPushes = mutableMapOf<Int, Int>()
    private val flushCfgPushesRunnable = Runnable { flushPendingCfgPushes() }

    private val sourceSignals = mutableMapOf<String, Set<MbCanSignal>>()
    private val sourceMutex = Mutex()
    private var boundScope: CoroutineScope? = null
    private var reapplyJob: Job? = null

    private val _availability = MutableStateFlow<MbCanAvailability>(MbCanAvailability.Unknown)
    val availability: StateFlow<MbCanAvailability> = _availability.asStateFlow()

    private val _steeringWheelHeatState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val steeringWheelHeatState: StateFlow<MbCanBinaryState> = _steeringWheelHeatState.asStateFlow()
    private val _frontLeftSeatModeState = MutableStateFlow<MbCanSeatModeState>(MbCanSeatModeState.Unknown)
    val frontLeftSeatModeState: StateFlow<MbCanSeatModeState> = _frontLeftSeatModeState.asStateFlow()
    private val _frontRightSeatModeState = MutableStateFlow<MbCanSeatModeState>(MbCanSeatModeState.Unknown)
    val frontRightSeatModeState: StateFlow<MbCanSeatModeState> = _frontRightSeatModeState.asStateFlow()
    private val _rearLeftSeatModeState = MutableStateFlow<MbCanSeatModeState>(MbCanSeatModeState.Unknown)
    val rearLeftSeatModeState: StateFlow<MbCanSeatModeState> = _rearLeftSeatModeState.asStateFlow()
    private val _rearRightSeatModeState = MutableStateFlow<MbCanSeatModeState>(MbCanSeatModeState.Unknown)
    val rearRightSeatModeState: StateFlow<MbCanSeatModeState> = _rearRightSeatModeState.asStateFlow()

    private val stateEngine = MbCanSignalStateEngine(
        steeringFlow = _steeringWheelHeatState,
        frontLeftSeatFlow = _frontLeftSeatModeState,
        frontRightSeatFlow = _frontRightSeatModeState,
        rearLeftSeatFlow = _rearLeftSeatModeState,
        rearRightSeatFlow = _rearRightSeatModeState
    )

    suspend fun bind(scope: CoroutineScope) {
        try {
            boundScope = scope
            _availability.value = MbCanEngineFacade.probeAvailability()
            MbCanDiagnostics.log("DEBUG", "bind() availability=${_availability.value}")
            MbCanJobManager.attach(scope)
            scheduleReapplyAllInterests()
        } catch (e: Exception) {
            MbCanDiagnostics.log("ERROR", "bind() failed: ${e.message}")
        }
    }

    suspend fun unbind() {
        try {
            MbCanDiagnostics.log("DEBUG", "unbind()")
            cfgPushHandler.removeCallbacks(flushCfgPushesRunnable)
            synchronized(pendingCfgPushes) { pendingCfgPushes.clear() }
            MbCanEngineFacade.syncVehicleCfgCmdListener(false)
            reapplyJob?.cancel()
            reapplyJob = null
            boundScope = null
            MbCanJobManager.detach()
        } catch (e: Exception) {
            MbCanDiagnostics.log("ERROR", "unbind() failed: ${e.message}")
        }
    }

    /**
     * Called from [MbCanEngineFacade] [IMBCmdListener.onCmdChanged] (vendor CAN thread).
     * Updates are coalesced on the main thread, then applied on [stateApplyDispatcher].
     */
    fun scheduleVehicleCfgPush(modular: Int, item: Int, value: Int) {
        if (modular != VEHICLE_CFG_MODULAR) return
        when (item) {
            MbCanKnownVehiclePropertyId.STEERING_WHEEL_HEAT_SWITCH,
            MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH,
            MbCanKnownVehiclePropertyId.FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH,
            MbCanKnownVehiclePropertyId.REAR_LEFT_SEAT_HEAT_SWITCH,
            MbCanKnownVehiclePropertyId.REAR_RIGHT_SEAT_HEAT_SWITCH -> Unit
            else -> return
        }
        synchronized(pendingCfgPushes) {
            pendingCfgPushes[item] = value
        }
        cfgPushHandler.removeCallbacks(flushCfgPushesRunnable)
        cfgPushHandler.postDelayed(flushCfgPushesRunnable, CFG_VEHICLE_PUSH_COALESCE_MS)
    }

    private fun flushPendingCfgPushes() {
        val snapshot = synchronized(pendingCfgPushes) {
            if (pendingCfgPushes.isEmpty()) return
            pendingCfgPushes.toMap().also { pendingCfgPushes.clear() }
        }
        val scope = boundScope ?: return
        scope.launch(stateApplyDispatcher) {
            for ((item, raw) in snapshot) {
                when (item) {
                    MbCanKnownVehiclePropertyId.STEERING_WHEEL_HEAT_SWITCH ->
                        stateEngine.applySteeringCandidate(
                            MbCanSignalStateEngine.decodeSteeringWheelHeatRaw(raw)
                        )
                    MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH ->
                        stateEngine.applySeatCandidate(
                            MbCanSeatSlot.FrontLeft,
                            MbCanSignalStateEngine.decodeSeatModeRaw(raw)
                        )
                    MbCanKnownVehiclePropertyId.FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH ->
                        stateEngine.applySeatCandidate(
                            MbCanSeatSlot.FrontRight,
                            MbCanSignalStateEngine.decodeSeatModeRaw(raw)
                        )
                    MbCanKnownVehiclePropertyId.REAR_LEFT_SEAT_HEAT_SWITCH ->
                        stateEngine.applySeatCandidate(
                            MbCanSeatSlot.RearLeft,
                            MbCanSignalStateEngine.decodeRearSeatHeatRaw(raw)
                        )
                    MbCanKnownVehiclePropertyId.REAR_RIGHT_SEAT_HEAT_SWITCH ->
                        stateEngine.applySeatCandidate(
                            MbCanSeatSlot.RearRight,
                            MbCanSignalStateEngine.decodeRearSeatHeatRaw(raw)
                        )
                }
            }
        }
    }

    suspend fun setSourceWidgetKeys(sourceId: String, widgetKeys: Set<String>) {
        val signals = widgetKeys.mapNotNull { widgetKeyToSignal(it) }.toSet()
        MbCanDiagnostics.log(
            "DEBUG",
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
        MbCanDiagnostics.log("DEBUG", "clearSource source=$sourceId")
        sourceMutex.withLock { sourceSignals.remove(sourceId) }
        scheduleReapplyAllInterests()
    }

    suspend fun execute(command: MbCanCommand): MbCanCommandResult {
        MbCanDiagnostics.log("DEBUG", "execute command=$command")
        ensureMbCanReadyIfNeeded()
        return when (command) {
            is MbCanCommand.ToggleProperty -> executeToggleViaRegistry(command.propertyId)
            is MbCanCommand.SetProperty -> executeSetViaRegistry(command.propertyId, command.value)
            is MbCanCommand.RefreshSignal -> {
                refreshSignal(command.signal)
                MbCanCommandResult(true, "Refresh requested")
            }
        }
    }

    private suspend fun executeToggleViaRegistry(propertyId: Int): MbCanCommandResult {
        MbCanDiagnostics.log("DEBUG", "executeToggleProperty propertyId=$propertyId")
        val spec = MbCanCommandRegistry.get(propertyId)
            ?: return MbCanCommandResult(false, "No command policy for propertyId=$propertyId")
        val policy = spec.policy as? MbCanCommandPolicy.ToggleBinary
            ?: return MbCanCommandResult(false, "Toggle unsupported by policy for propertyId=$propertyId")
        if (availability.value !is MbCanAvailability.Available) {
            return MbCanCommandResult(false, "mbCAN unavailable")
        }
        val current = MbCanEngineFacade.canGetVehicleParam(propertyId)
            ?: return MbCanCommandResult(false, "Pre-read failed")
                .also {
                    MbCanDiagnostics.log("ERROR", "toggle pre-read failed propertyId=$propertyId")
                }
        val target = when (current) {
            policy.onValue -> policy.offValue
            policy.offValue -> policy.onValue
            else -> policy.unknownFallbackValue
        }
        MbCanDiagnostics.log("DEBUG", "toggle pre-read current=$current target=$target propertyId=$propertyId")
        return applySetAndVerify(spec, target)
    }

    private suspend fun executeSetViaRegistry(propertyId: Int, value: Int): MbCanCommandResult {
        MbCanDiagnostics.log("DEBUG", "executeSetProperty propertyId=$propertyId value=$value")
        val spec = MbCanCommandRegistry.get(propertyId)
            ?: return MbCanCommandResult(false, "No command policy for propertyId=$propertyId")
        val policy = spec.policy as? MbCanCommandPolicy.SetExact
            ?: return MbCanCommandResult(false, "Set unsupported by policy for propertyId=$propertyId")
        if (!policy.allowedValues.contains(value)) {
            return MbCanCommandResult(false, "Value $value is not allowed for propertyId=$propertyId")
        }
        if (availability.value !is MbCanAvailability.Available) {
            return MbCanCommandResult(false, "mbCAN unavailable")
        }
        return applySetAndVerify(spec, value)
    }

    private suspend fun applySetAndVerify(spec: MbCanCommandSpec, targetValue: Int): MbCanCommandResult {
        val propertyId = spec.propertyId
        val setResult = MbCanEngineFacade.canSetVehicleParam(propertyId, targetValue)
            ?: return MbCanCommandResult(false, "Set command failed")
                .also {
                    MbCanDiagnostics.log("ERROR", "set failed propertyId=$propertyId value=$targetValue")
                }
        MbCanDiagnostics.log("DEBUG", "set result=$setResult propertyId=$propertyId value=$targetValue")
        if (setResult >= 0) {
            spec.refreshSignal?.let { MbCanJobManager.requestBurst(it) }
            delay(POST_COMMAND_VERIFY_DELAY_MS)
            val after = MbCanEngineFacade.canGetVehicleParam(propertyId)
            MbCanDiagnostics.log("DEBUG", "set verify propertyId=$propertyId after=$after")
            spec.refreshSignal?.let { refreshSignal(it) }
        }
        return MbCanCommandResult(setResult >= 0, "Set result: $setResult")
    }

    suspend fun refreshSignal(signal: MbCanSignal) {
        when (signal) {
            MbCanSignal.SteeringWheelHeat -> refreshSteeringWheelHeat()
            MbCanSignal.FrontLeftSeatMode -> refreshSeatSlot(MbCanSeatSlot.FrontLeft)
            MbCanSignal.FrontRightSeatMode -> refreshSeatSlot(MbCanSeatSlot.FrontRight)
            MbCanSignal.RearLeftSeatMode -> refreshSeatSlot(MbCanSeatSlot.RearLeft)
            MbCanSignal.RearRightSeatMode -> refreshSeatSlot(MbCanSeatSlot.RearRight)
        }
    }

    private suspend fun refreshSteeringWheelHeat() {
        withContext(stateApplyDispatcher) {
            if (!MbCanEngineFacade.isInitialized()) {
                _availability.value = MbCanEngineFacade.probeAvailability()
                stateEngine.applySteeringCandidate(MbCanBinaryState.Unknown)
                return@withContext
            }

            val availability = MbCanEngineFacade.availability
            _availability.value = availability
            if (availability !is MbCanAvailability.Available) {
                MbCanDiagnostics.log("WARN", "refreshSteeringWheelHeat unavailable=$availability")
                stateEngine.applySteeringCandidate(
                    MbCanBinaryState.Unavailable(
                        reason = (availability as? MbCanAvailability.Unavailable)?.reason ?: "Unavailable"
                    )
                )
                return@withContext
            }
            val raw = MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.STEERING_WHEEL_HEAT_SWITCH)
            val decoded = if (raw == null) {
                MbCanBinaryState.Unknown
            } else {
                MbCanSignalStateEngine.decodeSteeringWheelHeatRaw(raw)
            }
            stateEngine.applySteeringCandidate(decoded)
            MbCanDiagnostics.log(
                "DEBUG",
                "refreshSteeringWheelHeat raw=$raw state=${_steeringWheelHeatState.value}"
            )
        }
    }

    private suspend fun refreshSeatSlot(slot: MbCanSeatSlot) {
        withContext(stateApplyDispatcher) {
            val propertyId = slot.propertyId
            if (!MbCanEngineFacade.isInitialized()) {
                _availability.value = MbCanEngineFacade.probeAvailability()
                stateEngine.applySeatCandidate(slot, MbCanSeatModeState.Unknown)
                return@withContext
            }

            val availability = MbCanEngineFacade.availability
            _availability.value = availability
            if (availability !is MbCanAvailability.Available) {
                stateEngine.applySeatCandidate(
                    slot,
                    MbCanSeatModeState.Unavailable(
                        reason = (availability as? MbCanAvailability.Unavailable)?.reason ?: "Unavailable"
                    )
                )
                return@withContext
            }
            val raw = MbCanEngineFacade.canGetVehicleParam(propertyId)
            val decoded = if (raw == null) {
                MbCanSeatModeState.Unknown
            } else {
                when (slot) {
                    MbCanSeatSlot.FrontLeft, MbCanSeatSlot.FrontRight ->
                        MbCanSignalStateEngine.decodeSeatModeRaw(raw)
                    MbCanSeatSlot.RearLeft, MbCanSeatSlot.RearRight ->
                        MbCanSignalStateEngine.decodeRearSeatHeatRaw(raw)
                }
            }
            stateEngine.applySeatCandidate(slot, decoded)
            MbCanDiagnostics.log("DEBUG", "refreshSeatMode tag=${slot.name} raw=$raw state=$decoded")
        }
    }

    private suspend fun ensureMbCanReadyIfNeeded() {
        if (MbCanEngineFacade.isInitialized()) return
        val availability = MbCanEngineFacade.ensureInitialized()
        _availability.value = availability
        MbCanDiagnostics.log("DEBUG", "ensureMbCanReadyIfNeeded availability=$availability")
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
        val needsCfgVehicleListener = mergedSignals.any { signal ->
            signal.subscribeDataTypes.contains(CFG_VEHICLE_DATA_TYPE)
        }
        MbCanEngineFacade.syncVehicleCfgCmdListener(needsCfgVehicleListener)
    }

    private fun widgetKeyToSignal(widgetKey: String): MbCanSignal? {
        return signalByWidgetKey[widgetKey]
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
