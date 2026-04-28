package vad.dashing.tbox.mbcan

import android.os.Handler
import android.os.Looper
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
    SteeringWheelHeat(setOf("eMBCAN_CFG_VEHICLE")),
    FrontLeftSeatMode(setOf("eMBCAN_CFG_VEHICLE")),
    FrontRightSeatMode(setOf("eMBCAN_CFG_VEHICLE"))
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
    private const val SIGNAL_STEERING_WIDGET_KEY = "steeringWheelHeatWidget"
    private const val SIGNAL_FRONT_LEFT_SEAT_WIDGET_KEY = "frontLeftSeatHeatVentWidget"
    private const val SIGNAL_FRONT_RIGHT_SEAT_WIDGET_KEY = "frontRightSeatHeatVentWidget"
    private const val INTERESTS_DEBOUNCE_MS = 350L
    private const val POST_COMMAND_VERIFY_DELAY_MS = 500L
    private const val VEHICLE_CFG_MODULAR = 2
    /** Emit Unknown only after first observation + 2 repeats. */
    private const val UNKNOWN_CONFIRMATION_COUNT = 3
    /** Coalesce rapid [eMBCAN_CFG_VEHICLE] pushes before updating [StateFlow]s (50–150 ms band). */
    private const val CFG_VEHICLE_PUSH_COALESCE_MS = 100L

    private val cfgPushHandler = Handler(Looper.getMainLooper())
    private val pendingCfgPushes = mutableMapOf<Int, Int>()
    private val flushCfgPushesRunnable = Runnable { flushPendingCfgPushes() }

    private fun decodeSteeringWheelHeatRaw(raw: Int): MbCanBinaryState {
        return when (raw) {
            2 -> MbCanBinaryState.On
            1 -> MbCanBinaryState.Off
            else -> MbCanBinaryState.Unknown
        }
    }

    private fun decodeSeatModeRaw(raw: Int): MbCanSeatModeState {
        return when (raw) {
            1 -> MbCanSeatModeState.Off
            2 -> MbCanSeatModeState.Heat(1)
            3 -> MbCanSeatModeState.Heat(2)
            4 -> MbCanSeatModeState.Heat(3)
            5 -> MbCanSeatModeState.Vent(1)
            6 -> MbCanSeatModeState.Vent(2)
            7 -> MbCanSeatModeState.Vent(3)
            else -> MbCanSeatModeState.Unknown
        }
    }

    private fun MbCanBinaryState.isProblemState(): Boolean =
        this is MbCanBinaryState.Unknown || this is MbCanBinaryState.Unavailable

    private fun MbCanSeatModeState.isProblemState(): Boolean =
        this is MbCanSeatModeState.Unknown || this is MbCanSeatModeState.Unavailable

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
    private var steeringUnknownStreak = 0
    private var steeringUnavailableStreak = 0
    private var frontLeftSeatUnknownStreak = 0
    private var frontLeftSeatUnavailableStreak = 0
    private var frontRightSeatUnknownStreak = 0
    private var frontRightSeatUnavailableStreak = 0

    suspend fun bind(scope: CoroutineScope) {
        try {
            boundScope = scope
            _availability.value = MbCanEngineFacade.probeAvailability()
            TboxRepository.addLog("DEBUG", "MBCAN_TMP", "bind() availability=${_availability.value}")
            MbCanJobManager.attach(scope)
            scheduleReapplyAllInterests()
        } catch (e: Exception) {
            TboxRepository.addLog("ERROR", "MBCAN_TMP", "bind() failed: ${e.message}")
        }
    }

    suspend fun unbind() {
        try {
            TboxRepository.addLog("DEBUG", "MBCAN_TMP", "unbind()")
            cfgPushHandler.removeCallbacks(flushCfgPushesRunnable)
            synchronized(pendingCfgPushes) { pendingCfgPushes.clear() }
            MbCanEngineFacade.syncVehicleCfgCmdListener(false)
            reapplyJob?.cancel()
            reapplyJob = null
            boundScope = null
            MbCanJobManager.detach()
        } catch (e: Exception) {
            TboxRepository.addLog("ERROR", "MBCAN_TMP", "unbind() failed: ${e.message}")
        }
    }

    /**
     * Called from [MbCanEngineFacade] [IMBCmdListener.onCmdChanged] (vendor CAN thread).
     * Updates are coalesced on the main thread.
     */
    fun scheduleVehicleCfgPush(modular: Int, item: Int, value: Int) {
        if (modular != VEHICLE_CFG_MODULAR) return
        when (item) {
            MbCanKnownVehiclePropertyId.STEERING_WHEEL_HEAT_SWITCH,
            MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH,
            MbCanKnownVehiclePropertyId.FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH -> Unit
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
        for ((item, raw) in snapshot) {
            when (item) {
                MbCanKnownVehiclePropertyId.STEERING_WHEEL_HEAT_SWITCH ->
                    updateSteeringStateWithUnknownConfirmation(decodeSteeringWheelHeatRaw(raw))
                MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH ->
                    updateSeatStateWithUnknownConfirmation(
                        state = decodeSeatModeRaw(raw),
                        currentUnknownStreak = frontLeftSeatUnknownStreak,
                        currentUnavailableStreak = frontLeftSeatUnavailableStreak,
                        setUnknownStreak = { frontLeftSeatUnknownStreak = it },
                        setUnavailableStreak = { frontLeftSeatUnavailableStreak = it },
                        update = { _frontLeftSeatModeState.value = it }
                    )
                MbCanKnownVehiclePropertyId.FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH ->
                    updateSeatStateWithUnknownConfirmation(
                        state = decodeSeatModeRaw(raw),
                        currentUnknownStreak = frontRightSeatUnknownStreak,
                        currentUnavailableStreak = frontRightSeatUnavailableStreak,
                        setUnknownStreak = { frontRightSeatUnknownStreak = it },
                        setUnavailableStreak = { frontRightSeatUnavailableStreak = it },
                        update = { _frontRightSeatModeState.value = it }
                    )
            }
        }
    }

    private fun updateSteeringStateWithUnknownConfirmation(state: MbCanBinaryState) {
        if (state is MbCanBinaryState.Unknown) {
            steeringUnknownStreak += 1
            steeringUnavailableStreak = 0
            if (steeringUnknownStreak >= UNKNOWN_CONFIRMATION_COUNT) {
                _steeringWheelHeatState.value = MbCanBinaryState.Unknown
            }
            return
        }
        if (state is MbCanBinaryState.Unavailable) {
            steeringUnavailableStreak += 1
            steeringUnknownStreak = 0
            if (steeringUnavailableStreak >= UNKNOWN_CONFIRMATION_COUNT) {
                _steeringWheelHeatState.value = state
            }
            return
        }
        steeringUnknownStreak = 0
        steeringUnavailableStreak = 0
        _steeringWheelHeatState.value = state
    }

    private fun updateSeatStateWithUnknownConfirmation(
        state: MbCanSeatModeState,
        currentUnknownStreak: Int,
        currentUnavailableStreak: Int,
        setUnknownStreak: (Int) -> Unit,
        setUnavailableStreak: (Int) -> Unit,
        update: (MbCanSeatModeState) -> Unit
    ) {
        if (state is MbCanSeatModeState.Unknown) {
            val next = currentUnknownStreak + 1
            setUnknownStreak(next)
            setUnavailableStreak(0)
            if (next >= UNKNOWN_CONFIRMATION_COUNT) {
                update(MbCanSeatModeState.Unknown)
            }
            return
        }
        if (state is MbCanSeatModeState.Unavailable) {
            val next = currentUnavailableStreak + 1
            setUnavailableStreak(next)
            setUnknownStreak(0)
            if (next >= UNKNOWN_CONFIRMATION_COUNT) {
                update(state)
            }
            return
        }
        setUnknownStreak(0)
        setUnavailableStreak(0)
        update(state)
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
            MbCanBinaryState.Off -> 2
            MbCanBinaryState.Unknown -> 2
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
            MbCanSignal.FrontLeftSeatMode -> refreshFrontLeftSeatMode()
            MbCanSignal.FrontRightSeatMode -> refreshFrontRightSeatMode()
        }
    }

    private suspend fun refreshSteeringWheelHeat() {
        if (!MbCanEngineFacade.isInitialized()) {
            _availability.value = MbCanEngineFacade.probeAvailability()
            updateSteeringStateWithUnknownConfirmation(MbCanBinaryState.Unknown)
            return
        }

        val availability = MbCanEngineFacade.availability
        _availability.value = availability
        if (availability !is MbCanAvailability.Available) {
            TboxRepository.addLog("WARN", "MBCAN_TMP", "refreshSteeringWheelHeat unavailable=$availability")
            updateSteeringStateWithUnknownConfirmation(
                MbCanBinaryState.Unavailable(
                reason = (availability as? MbCanAvailability.Unavailable)?.reason ?: "Unavailable"
            )
            )
            return
        }
        val raw = MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.STEERING_WHEEL_HEAT_SWITCH)
        val state = if (raw == null) {
            MbCanBinaryState.Unknown
        } else {
            decodeSteeringWheelHeatRaw(raw)
        }
        if (state.isProblemState() && !_steeringWheelHeatState.value.isProblemState()) {
            MbCanJobManager.requestBurst(MbCanSignal.SteeringWheelHeat)
        }
        updateSteeringStateWithUnknownConfirmation(state)
        TboxRepository.addLog(
            "DEBUG",
            "MBCAN_TMP",
            "refreshSteeringWheelHeat raw=$raw state=${_steeringWheelHeatState.value}"
        )
    }

    private suspend fun refreshFrontLeftSeatMode() {
        refreshSeatMode(
            propertyId = MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH,
            update = { _frontLeftSeatModeState.value = it },
            tag = "frontLeftSeat"
        )
    }

    private suspend fun refreshFrontRightSeatMode() {
        refreshSeatMode(
            propertyId = MbCanKnownVehiclePropertyId.FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH,
            update = { _frontRightSeatModeState.value = it },
            tag = "frontRightSeat"
        )
    }

    private suspend fun refreshSeatMode(
        propertyId: Int,
        update: (MbCanSeatModeState) -> Unit,
        tag: String
    ) {
        if (!MbCanEngineFacade.isInitialized()) {
            _availability.value = MbCanEngineFacade.probeAvailability()
            updateSeatStateWithUnknownConfirmation(
                state = MbCanSeatModeState.Unknown,
                currentUnknownStreak = if (propertyId == MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH) {
                    frontLeftSeatUnknownStreak
                } else {
                    frontRightSeatUnknownStreak
                },
                currentUnavailableStreak = if (propertyId == MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH) {
                    frontLeftSeatUnavailableStreak
                } else {
                    frontRightSeatUnavailableStreak
                },
                setUnknownStreak = {
                    if (propertyId == MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH) {
                        frontLeftSeatUnknownStreak = it
                    } else {
                        frontRightSeatUnknownStreak = it
                    }
                },
                setUnavailableStreak = {
                    if (propertyId == MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH) {
                        frontLeftSeatUnavailableStreak = it
                    } else {
                        frontRightSeatUnavailableStreak = it
                    }
                },
                update = update
            )
            return
        }

        val availability = MbCanEngineFacade.availability
        _availability.value = availability
        if (availability !is MbCanAvailability.Available) {
            updateSeatStateWithUnknownConfirmation(
                state = MbCanSeatModeState.Unavailable(
                    reason = (availability as? MbCanAvailability.Unavailable)?.reason ?: "Unavailable"
                ),
                currentUnknownStreak = if (propertyId == MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH) {
                    frontLeftSeatUnknownStreak
                } else {
                    frontRightSeatUnknownStreak
                },
                currentUnavailableStreak = if (propertyId == MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH) {
                    frontLeftSeatUnavailableStreak
                } else {
                    frontRightSeatUnavailableStreak
                },
                setUnknownStreak = {
                    if (propertyId == MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH) {
                        frontLeftSeatUnknownStreak = it
                    } else {
                        frontRightSeatUnknownStreak = it
                    }
                },
                setUnavailableStreak = {
                    if (propertyId == MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH) {
                        frontLeftSeatUnavailableStreak = it
                    } else {
                        frontRightSeatUnavailableStreak = it
                    }
                },
                update = update
            )
            return
        }
        val raw = MbCanEngineFacade.canGetVehicleParam(propertyId)
        val state = if (raw == null) MbCanSeatModeState.Unknown else decodeSeatModeRaw(raw)
        val currentState = if (propertyId == MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH) {
            _frontLeftSeatModeState.value
        } else {
            _frontRightSeatModeState.value
        }
        if (state.isProblemState() && !currentState.isProblemState()) {
            val signal = if (propertyId == MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH) {
                MbCanSignal.FrontLeftSeatMode
            } else {
                MbCanSignal.FrontRightSeatMode
            }
            MbCanJobManager.requestBurst(signal)
        }
        updateSeatStateWithUnknownConfirmation(
            state = state,
            currentUnknownStreak = if (propertyId == MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH) {
                frontLeftSeatUnknownStreak
            } else {
                frontRightSeatUnknownStreak
            },
            currentUnavailableStreak = if (propertyId == MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH) {
                frontLeftSeatUnavailableStreak
            } else {
                frontRightSeatUnavailableStreak
            },
            setUnknownStreak = {
                if (propertyId == MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH) {
                    frontLeftSeatUnknownStreak = it
                } else {
                    frontRightSeatUnknownStreak = it
                }
            },
            setUnavailableStreak = {
                if (propertyId == MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH) {
                    frontLeftSeatUnavailableStreak = it
                } else {
                    frontRightSeatUnavailableStreak = it
                }
            },
            update = update
        )
        TboxRepository.addLog("DEBUG", "MBCAN_TMP", "refreshSeatMode tag=$tag raw=$raw state=$state")
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
        MbCanEngineFacade.syncVehicleCfgCmdListener(mergedSignals.isNotEmpty())
    }

    private fun widgetKeyToSignal(widgetKey: String): MbCanSignal? {
        return when (widgetKey) {
            SIGNAL_STEERING_WIDGET_KEY -> MbCanSignal.SteeringWheelHeat
            SIGNAL_FRONT_LEFT_SEAT_WIDGET_KEY -> MbCanSignal.FrontLeftSeatMode
            SIGNAL_FRONT_RIGHT_SEAT_WIDGET_KEY -> MbCanSignal.FrontRightSeatMode
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

