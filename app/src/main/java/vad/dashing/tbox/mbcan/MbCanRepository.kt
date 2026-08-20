package vad.dashing.tbox.mbcan

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.job
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import vad.dashing.tbox.ACC_CRUISE_WIDGET_DATA_KEY
import vad.dashing.tbox.CRUISE_STATUS_WIDGET_DATA_KEY
import vad.dashing.tbox.DRIVE_MODE_WIDGET_DATA_KEY
import vad.dashing.tbox.DRIVE_MODE_CYCLE_WIDGET_DATA_KEY
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
import vad.dashing.tbox.Wheels
import vad.dashing.tbox.TRUNK_DOOR_WIDGET_DATA_KEY
import vad.dashing.tbox.AVH_WIDGET_DATA_KEY
import vad.dashing.tbox.ESP_OFF_WIDGET_DATA_KEY
import vad.dashing.tbox.LDW_WIDGET_DATA_KEY
import vad.dashing.tbox.LKA_WIDGET_DATA_KEY
import vad.dashing.tbox.TJA_ICA_WIDGET_DATA_KEY
import vad.dashing.tbox.HMA_WIDGET_DATA_KEY
import vad.dashing.tbox.HVAC_AC_MAX_WIDGET_DATA_KEY
import vad.dashing.tbox.HVAC_CUSTOM_MODE_CYCLE_WIDGET_DATA_KEY
import vad.dashing.tbox.FRONT_LEFT_SEAT_HEAT_VENT_SINGLE_WIDGET_DATA_KEY
import vad.dashing.tbox.FRONT_RIGHT_SEAT_HEAT_VENT_SINGLE_WIDGET_DATA_KEY
import vad.dashing.tbox.HDC_WIDGET_DATA_KEY
import vad.dashing.tbox.PARKING_RADAR_WIDGET_DATA_KEY
import vad.dashing.tbox.HEADLIGHT_MODE_CYCLE_WIDGET_DATA_KEY
import vad.dashing.tbox.REAR_FOG_WIDGET_DATA_KEY
import vad.dashing.tbox.REAR_LEFT_SEAT_HEAT_WIDGET_DATA_KEY
import vad.dashing.tbox.REAR_RIGHT_SEAT_HEAT_WIDGET_DATA_KEY
import vad.dashing.tbox.SLA_SPEED_LIMIT_WIDGET_DATA_KEY
import vad.dashing.tbox.SPEED_LIMITER_WIDGET_DATA_KEY
import vad.dashing.tbox.WIPER_MAINTENANCE_WIDGET_DATA_KEY

enum class MbCanSignal(val subscribeDataTypes: Set<String>) {
    SteeringWheelHeat(setOf("eMBCAN_CFG_VEHICLE")),
    WiperMaintenance(setOf("eMBCAN_CFG_VEHICLE")),
    ParkingRadar(setOf("eMBCAN_CFG_VEHICLE")),
    RearFogLight(setOf("eMBCAN_CFG_VEHICLE")),
    AutoLock(setOf("eMBCAN_CFG_VEHICLE")),
    AutoUnlock(setOf("eMBCAN_CFG_VEHICLE")),
    FollowMeHome(setOf("eMBCAN_CFG_VEHICLE")),
    DriverUnlockMode(setOf("eMBCAN_CFG_VEHICLE")),
    RemoteLockFeedback(setOf("eMBCAN_CFG_VEHICLE")),
    WiperSensitivity(setOf("eMBCAN_CFG_VEHICLE")),
    RearWiper(setOf("eMBCAN_CFG_VEHICLE")),
    MirrorAutoFold(setOf("eMBCAN_CFG_VEHICLE")),
    LowBeamHeight(setOf("eMBCAN_CFG_VEHICLE")),
    TurnFlashCount(setOf("eMBCAN_CFG_VEHICLE")),
    AvhSwitch(setOf("eMBCAN_CFG_VEHICLE")),
    HdcSwitch(setOf("eMBCAN_CFG_VEHICLE")),
    EspOffSwitch(setOf("eMBCAN_CFG_VEHICLE")),
    LightControl(setOf("eMBCAN_CFG_VEHICLE")),
    LasModeSelection(setOf("eMBCAN_CFG_VEHICLE")),
    TjaIca(setOf("eMBCAN_CFG_VEHICLE")),
    HmaSwitch(setOf("eMBCAN_CFG_VEHICLE")),
    Bsd(setOf("eMBCAN_CFG_VEHICLE")),
    Dow(setOf("eMBCAN_CFG_VEHICLE")),
    Fcw(setOf("eMBCAN_CFG_VEHICLE")),
    FcwSensitivity(setOf("eMBCAN_CFG_VEHICLE")),
    LdwSensitivity(setOf("eMBCAN_CFG_VEHICLE")),
    HvacCustomMode(setOf("eMBCAN_CFG_VEHICLE")),
    HvacAcMax(setOf("eMBCAN_CFG_VEHICLE")),
    FrontWindscreenHeat(setOf("eMBCAN_CFG_VEHICLE")),
    HvacDefroster(setOf("eMBCAN_CFG_VEHICLE")),
    HvacAirRecirculation(setOf("eMBCAN_CFG_VEHICLE")),
    HvacAcPower(setOf("eMBCAN_CFG_VEHICLE")),
    HvacAcCleanWhenLocked(setOf("eMBCAN_CFG_VEHICLE")),
    HvacAutoState(setOf("eMBCAN_CFG_VEHICLE")),
    HvacAnionPurify(setOf("eMBCAN_CFG_VEHICLE")),
    FragranceSwitch(setOf("eMBCAN_CFG_VEHICLE")),
    FragranceSmell(setOf("eMBCAN_CFG_VEHICLE")),
    FragranceConcentration(setOf("eMBCAN_CFG_VEHICLE")),
    FirstBlowing(setOf("eMBCAN_CFG_VEHICLE")),
    BtReduceFan(setOf("eMBCAN_CFG_VEHICLE")),
    AutoVentilation(setOf("eMBCAN_CFG_VEHICLE")),
    HvacDefrosterFront(setOf("eMBCAN_CFG_VEHICLE")),
    HvacFrontOff(setOf("eMBCAN_CFG_VEHICLE")),
    HvacTempLeft(setOf("eMBCAN_CFG_VEHICLE")),
    HvacTempRight(setOf("eMBCAN_CFG_VEHICLE")),
    HvacFanSpeed(setOf("eMBCAN_CFG_VEHICLE")),
    HvacSync(setOf("eMBCAN_CFG_VEHICLE")),
    HvacBlowMode(setOf("eMBCAN_CFG_VEHICLE")),
    HudSwitch(setOf("eMBCAN_CFG_VEHICLE")),
    HudHeight(setOf("eMBCAN_CFG_VEHICLE")),
    HudBrightness(setOf("eMBCAN_CFG_VEHICLE")),
    HudDisplayMode(setOf("eMBCAN_CFG_VEHICLE")),
    HudAutoBrightness(setOf("eMBCAN_CFG_VEHICLE")),
    IcmBrightnessMode(setOf("eMBCAN_CFG_VEHICLE")),
    IcmManualBrightness(setOf("eMBCAN_CFG_VEHICLE")),
    OverspeedAlarm(setOf("eMBCAN_CFG_VEHICLE")),
    TrunkDoor(emptySet()),
    WirelessChargingSwitch(setOf("eMBCAN_CFG_VEHICLE")),
    /** Vehicle cfg params shown on [vad.dashing.tbox.ui.CarSettingsTab] (poll + push). */
    CarSettingsVehicleParams(setOf("eMBCAN_CFG_VEHICLE")),
    FrontLeftSeatMode(setOf("eMBCAN_CFG_VEHICLE")),
    FrontRightSeatMode(setOf("eMBCAN_CFG_VEHICLE")),
    RearLeftSeatMode(setOf("eMBCAN_CFG_VEHICLE")),
    RearRightSeatMode(setOf("eMBCAN_CFG_VEHICLE")),
    AudioVolume(setOf("eMBCAN_CFG_AUDIO")),
    AudioVolumeSpeed(setOf("eMBCAN_CFG_AUDIO")),
    AudioKeyToneVolume(setOf("eMBCAN_CFG_AUDIO")),
    AudioRadarAlarmVolume(setOf("eMBCAN_CFG_AUDIO")),
    AudioEqMode(setOf("eMBCAN_CFG_AUDIO")),
    AudioEqBass(setOf("eMBCAN_CFG_AUDIO")),
    AudioEqMiddle(setOf("eMBCAN_CFG_AUDIO")),
    AudioEqTreble(setOf("eMBCAN_CFG_AUDIO")),
    AudioBalance(setOf("eMBCAN_CFG_AUDIO")),
    AudioFader(setOf("eMBCAN_CFG_AUDIO")),
    EngineRpm(setOf("eMBCAN_VEHICLE_ENGINE")),
    EngineTemperature(setOf("eMBCAN_VEHICLE_ENGINE")),
    CarSpeed(setOf("eMBCAN_VEHICLE_SPEED")),
    /** PRND from `MBCanVehicleSpeed.getGear()` (`eMBCAN_VEHICLE_GEAR`). */
    VehicleGear(setOf("eMBCAN_VEHICLE_GEAR")),
    /**
     * CEM reverse gear switch from BCM (`eMBCAN_VEHICLE_BCM_STATUS`).
     * Also kept alive via settings telemetry bridge (same as [TrunkDoor]).
     */
    ReverseGearSwitch(setOf("eMBCAN_VEHICLE_BCM_STATUS")),
    /** Fuel tank level percent (`eMBCAN_VEHICLE_FUELLEVEL`). */
    FuelLevel(setOf("eMBCAN_VEHICLE_FUELLEVEL")),
    /** Total odometer km (`eMBCAN_VEHICLE_TOTALODOMETER`). */
    TotalOdometer(setOf("eMBCAN_VEHICLE_TOTALODOMETER")),
    /** ESP wheel pulse counters (`eMBCAN_VEHICLE_WHEEL`). */
    WheelPulse(setOf("eMBCAN_VEHICLE_WHEEL")),
    /** Outside ambient temperature (`eMBCAN_VEHICLE_EXTERNAL_TEMP_RAW`). */
    OutsideTemperature(setOf("eMBCAN_VEHICLE_EXTERNAL_TEMP_RAW")),
    /** FCM SLA / recognized speed-limit sign (`eMBCAN_VEHICLE_LKA_STATUS`). */
    SlaSpeedLimit(setOf("eMBCAN_VEHICLE_LKA_STATUS")),
    /**
     * Vehicle speed limiter switch and target (`eMBCAN_CFG_VEHICLE`).
     * Unsupported on Jetour Dashing — kept for API/widget wiring only.
     */
    SpeedLimiter(setOf("eMBCAN_CFG_VEHICLE")),
    /**
     * ACC FRM mode/set speed + conventional CCS Gasped status.
     * No JobManager subscribe types: OEM subscribe is owned by
     * [MbCanEngineFacade.syncFrmDectInfoListener] / [MbCanEngineFacade.syncGaspedStatusListener].
     * Sharing those types with [MbCanJobManager] caused extra unSubscribe races on A9.
     */
    AccCruise(emptySet()),
    /** TPMS: tire pressure + temperature (`eMBCAN_VEHICLE_TIRE`). */
    VehicleTires(setOf("eMBCAN_VEHICLE_TIRE")),
    /** Instant fuel L/100km from engine FuelRollingCounter (`eMBCAN_VEHICLE_ENGINE`). */
    CurrentFuelConsumption(setOf("eMBCAN_VEHICLE_ENGINE")),
    /** Distance to next maintenance km (`eMBCAN_ICM_TRIP_INFO`). */
    DistanceToNextMaintenance(setOf("eMBCAN_ICM_TRIP_INFO")),
    /** Distance to empty km (`eMBCAN_VEHICLE_FUELLEVEL`). */
    DistanceToFuelEmpty(setOf("eMBCAN_VEHICLE_FUELLEVEL")),
    /** PM2.5 inside/outside density (`eMBCAN_PM25INFO`). */
    Pm25AirQuality(setOf("eMBCAN_PM25INFO")),
    /** Steering wheel angle + rate (`eMBCAN_VEHICLE_STEERING_ANGLE`); A10: MCU angle only. */
    SteeringAngle(setOf("eMBCAN_VEHICLE_STEERING_ANGLE")),
    /**
     * Left/right turn + hazard together (`eMBCAN_VEHICLE_TURNLIGHT`).
     * A10: DirectionInd L/R + HazardLightSW (+ TurnlightSts as lamp blink fallback).
     */
    TurnSignals(setOf("eMBCAN_VEHICLE_TURNLIGHT")),
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
    /** Power liftgate pulse: set [value] then 0 after [HvacClimateDomain.TRUNK_PULSE_RESET_MS]. */
    data class TrunkPulse(val value: Int) : MbCanCommand()
    data class ToggleAudioProperty(val propertyId: Int) : MbCanCommand()
    data class SetAudioProperty(val propertyId: Int, val value: Int) : MbCanCommand()
    /** Stock FCW switch always updates FCW, AEB and safe-distance warning together. */
    data class SetFcwEnabled(val enabled: Boolean) : MbCanCommand()
    data class RefreshSignal(val signal: MbCanSignal) : MbCanCommand()
}

object MbCanRepository {
    /**
     * Runs [clearSource] after a delay when UI leaves composition ([DisposableEffect] onDispose).
     * A child of [rememberCoroutineScope] launched from onDispose is cancelled with the composition
     * before work runs; this scope is independent of that lifecycle. Debounced so brief navigation
     * does not churn push subscription; [setSourceWidgetKeys] / [setSourceSignals] cancel the timer.
     */
    private const val CLEAR_SOURCE_PUSH_DEBOUNCE_MS = 3 * 60_000L

    private val debouncedClearSourceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val pendingDebouncedClearJobs = ConcurrentHashMap<String, Job>()

    fun enqueueClearSource(sourceId: String) {
        pendingDebouncedClearJobs.remove(sourceId)?.cancel()
        val job = debouncedClearSourceScope.launch {
            delay(CLEAR_SOURCE_PUSH_DEBOUNCE_MS)
            if (pendingDebouncedClearJobs.remove(sourceId, coroutineContext.job)) {
                clearSource(sourceId)
            }
        }
        pendingDebouncedClearJobs[sourceId] = job
    }

    private fun cancelDebouncedClearSource(sourceId: String) {
        pendingDebouncedClearJobs.remove(sourceId)?.cancel()
    }

    private data class WidgetSignalBinding(
        val widgetKey: String,
        val signal: MbCanSignal
    )

    private val widgetSignalRegistry = listOf(
        WidgetSignalBinding("steeringWheelHeatWidget", MbCanSignal.SteeringWheelHeat),
        WidgetSignalBinding(WIPER_MAINTENANCE_WIDGET_DATA_KEY, MbCanSignal.WiperMaintenance),
        WidgetSignalBinding(PARKING_RADAR_WIDGET_DATA_KEY, MbCanSignal.ParkingRadar),
        WidgetSignalBinding(REAR_FOG_WIDGET_DATA_KEY, MbCanSignal.RearFogLight),
        WidgetSignalBinding(HEADLIGHT_MODE_CYCLE_WIDGET_DATA_KEY, MbCanSignal.LightControl),
        WidgetSignalBinding(AVH_WIDGET_DATA_KEY, MbCanSignal.AvhSwitch),
        WidgetSignalBinding(HDC_WIDGET_DATA_KEY, MbCanSignal.HdcSwitch),
        WidgetSignalBinding(ESP_OFF_WIDGET_DATA_KEY, MbCanSignal.EspOffSwitch),
        WidgetSignalBinding(LDW_WIDGET_DATA_KEY, MbCanSignal.LasModeSelection),
        WidgetSignalBinding(LKA_WIDGET_DATA_KEY, MbCanSignal.LasModeSelection),
        WidgetSignalBinding(TJA_ICA_WIDGET_DATA_KEY, MbCanSignal.TjaIca),
        WidgetSignalBinding(HMA_WIDGET_DATA_KEY, MbCanSignal.HmaSwitch),
        WidgetSignalBinding(HVAC_CUSTOM_MODE_CYCLE_WIDGET_DATA_KEY, MbCanSignal.HvacCustomMode),
        WidgetSignalBinding(HVAC_AC_MAX_WIDGET_DATA_KEY, MbCanSignal.HvacAcMax),
        WidgetSignalBinding("frontWindscreenHeatWidget", MbCanSignal.FrontWindscreenHeat),
        WidgetSignalBinding("rearWindowMirrorsDefrostWidget", MbCanSignal.HvacDefroster),
        WidgetSignalBinding("hvacAirRecirculationWidget", MbCanSignal.HvacAirRecirculation),
        WidgetSignalBinding("hvacAcWidget", MbCanSignal.HvacAcPower),
        WidgetSignalBinding("hvacAcCleanWhenLockedWidget", MbCanSignal.HvacAcCleanWhenLocked),
        WidgetSignalBinding("hvacAutoWidget", MbCanSignal.HvacAutoState),
        WidgetSignalBinding("hvacDefrosterFrontWidget", MbCanSignal.HvacDefrosterFront),
        WidgetSignalBinding(HVAC_SYNC_WIDGET_DATA_KEY, MbCanSignal.HvacSync),
        WidgetSignalBinding(HVAC_FAN_WIDGET_HORIZONTAL_DATA_KEY, MbCanSignal.HvacFanSpeed),
        WidgetSignalBinding(HVAC_FAN_WIDGET_VERTICAL_DATA_KEY, MbCanSignal.HvacFanSpeed),
        WidgetSignalBinding(HVAC_TEMP_LEFT_WIDGET_HORIZONTAL_DATA_KEY, MbCanSignal.HvacTempLeft),
        WidgetSignalBinding(HVAC_TEMP_LEFT_WIDGET_VERTICAL_DATA_KEY, MbCanSignal.HvacTempLeft),
        WidgetSignalBinding(HVAC_TEMP_RIGHT_WIDGET_HORIZONTAL_DATA_KEY, MbCanSignal.HvacTempRight),
        WidgetSignalBinding(HVAC_TEMP_RIGHT_WIDGET_VERTICAL_DATA_KEY, MbCanSignal.HvacTempRight),
        WidgetSignalBinding(HVAC_BLOW_MODE_CYCLE_WIDGET_DATA_KEY, MbCanSignal.HvacBlowMode),
        WidgetSignalBinding(HVAC_BLOW_MODE_PANEL_WIDGET_HORIZONTAL_DATA_KEY, MbCanSignal.HvacBlowMode),
        WidgetSignalBinding(HVAC_BLOW_MODE_PANEL_WIDGET_VERTICAL_DATA_KEY, MbCanSignal.HvacBlowMode),
        WidgetSignalBinding(TRUNK_DOOR_WIDGET_DATA_KEY, MbCanSignal.TrunkDoor),
        WidgetSignalBinding(DRIVE_MODE_WIDGET_DATA_KEY, MbCanSignal.CarSettingsVehicleParams),
        WidgetSignalBinding(DRIVE_MODE_CYCLE_WIDGET_DATA_KEY, MbCanSignal.CarSettingsVehicleParams),
        WidgetSignalBinding("frontLeftSeatHeatVentWidget", MbCanSignal.FrontLeftSeatMode),
        WidgetSignalBinding("frontRightSeatHeatVentWidget", MbCanSignal.FrontRightSeatMode),
        WidgetSignalBinding(FRONT_LEFT_SEAT_HEAT_VENT_SINGLE_WIDGET_DATA_KEY, MbCanSignal.FrontLeftSeatMode),
        WidgetSignalBinding(FRONT_RIGHT_SEAT_HEAT_VENT_SINGLE_WIDGET_DATA_KEY, MbCanSignal.FrontRightSeatMode),
        WidgetSignalBinding(REAR_LEFT_SEAT_HEAT_WIDGET_DATA_KEY, MbCanSignal.RearLeftSeatMode),
        WidgetSignalBinding(REAR_RIGHT_SEAT_HEAT_WIDGET_DATA_KEY, MbCanSignal.RearRightSeatMode),
        WidgetSignalBinding(SLA_SPEED_LIMIT_WIDGET_DATA_KEY, MbCanSignal.SlaSpeedLimit),
        WidgetSignalBinding(SPEED_LIMITER_WIDGET_DATA_KEY, MbCanSignal.SpeedLimiter),
        WidgetSignalBinding(ACC_CRUISE_WIDGET_DATA_KEY, MbCanSignal.AccCruise),
        WidgetSignalBinding(CRUISE_STATUS_WIDGET_DATA_KEY, MbCanSignal.AccCruise),
    )

    private val signalByWidgetKey: Map<String, MbCanSignal> = widgetSignalRegistry
        .associate { it.widgetKey to it.signal }
    private const val INTERESTS_DEBOUNCE_MS = 350L
    private const val POST_COMMAND_VERIFY_DELAY_MS = 500L
    /**
     * MFS cruise pulses (210/212/213/214) are write-only (bus resets); the usual
     * 500 ms verify + canGet is useless and dwarfs widget step intervals. Short settle only.
     */
    private const val POST_MFS_CRUISE_PULSE_DELAY_MS = 50L
    private val mfsCruisePulsePropertyIds: Set<Int> = setOf(
        MbCanKnownVehiclePropertyId.MFS_CRUISE_CONTROL,
        MbCanKnownVehiclePropertyId.MFS_CANCEL,
        MbCanKnownVehiclePropertyId.MFS_RES_PLUS,
        MbCanKnownVehiclePropertyId.MFS_SET_MINUS,
    )
    private const val VEHICLE_CFG_MODULAR = 2
    private const val CFG_VEHICLE_DATA_TYPE = "eMBCAN_CFG_VEHICLE"
    private const val CFG_AUDIO_DATA_TYPE = "eMBCAN_CFG_AUDIO"
    /** Coalesce rapid push updates before applying to [StateFlow]s. */
    private const val PUSH_STATE_COALESCE_MS = 200L
    /** Coalesce debug push logs so runtime logs stay readable. */
    private const val PUSH_DEBUG_LOG_COALESCE_MS = 1_000L

    /**
     * Single-thread dispatcher for streak counters, burst decisions, and [StateFlow] writes so push
     * (Handler → launch) and poll ([MbCanJobManager] IO) never interleave.
     */
    private val stateApplyDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "mbcan-state-apply").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    private val cfgPushHandler = Handler(Looper.getMainLooper())
    private val pendingCfgPushes = mutableMapOf<Int, Int>()
    private val cfgPushScheduleLock = Any()
    private var cfgPushFlushScheduled = false
    private val flushCfgPushesRunnable = Runnable { flushPendingCfgPushes() }
    private val pendingAudioPushes = mutableMapOf<Int, Int>()
    private val audioPushScheduleLock = Any()
    private var audioPushFlushScheduled = false
    private val flushAudioCfgPushesRunnable = Runnable { flushPendingAudioPushes() }
    private val telemetryPushLock = Any()
    private val pendingTelemetryPushes = mutableMapOf<MbCanSignal, Float?>()
    @Volatile private var pendingSteerSpeedPush: Float? = null
    private var pendingSteerSpeedIncluded = false
    private val pendingFuelLevelPush = Any()
    @Volatile private var pendingFuelLevelPercent: UInt? = null
    @Volatile private var pendingDistanceToFuelEmptyKm: UInt? = null
    private var pendingFuelLevelFlushScheduled = false
    private val pendingOdometerPush = Any()
    @Volatile private var pendingOdometerKm: UInt? = null
    private var pendingOdometerFlushScheduled = false
    private var telemetryPushFlushScheduled = false
    private val flushTelemetryPushesRunnable = Runnable { flushPendingTelemetryPushes() }
    private val flushFuelLevelPushRunnable = Runnable { flushPendingFuelLevelPush() }
    private val flushOdometerPushRunnable = Runnable { flushPendingOdometerPush() }
    private val pendingGearPush = Any()
    @Volatile private var pendingGearBoxMode: String? = null
    private var pendingGearBoxModeFlushScheduled = false
    private val flushGearBoxModePushRunnable = Runnable { flushPendingGearBoxModePush() }
    private val pendingReverseGearPush = Any()
    @Volatile private var pendingReverseGearSwitch: Boolean? = null
    private var pendingReverseGearFlushScheduled = false
    private val flushReverseGearPushRunnable = Runnable { flushPendingReverseGearPush() }
    private val pendingTurnSignalsPush = Any()
    @Volatile private var pendingTurnSignals: TurnSignalsState? = null
    private var pendingTurnSignalsFlushScheduled = false
    private val flushTurnSignalsPushRunnable = Runnable { flushPendingTurnSignalsPush() }
    private val pendingWheelPulsePush = Any()
    @Volatile private var pendingWheelPulse: vad.dashing.tbox.vehicle.WheelCounters? = null
    private var pendingWheelPulseFlushScheduled = false
    private val flushWheelPulsePushRunnable = Runnable { flushPendingWheelPulsePush() }
    private val tirePushLock = Any()
    @Volatile private var pendingTirePressure: Wheels? = null
    @Volatile private var pendingTireTemperature: Wheels? = null
    private var tirePushFlushScheduled = false
    private val flushTirePushRunnable = Runnable { flushPendingTirePush() }
    private val trunkPushLock = Any()
    private var pendingTrunkMoveDir: Int? = null
    private var pendingTrunkSts: Int? = null
    private var trunkPushFlushScheduled = false
    private val flushTrunkPushRunnable = Runnable { flushPendingTrunkPush() }
    private val pendingPushDebugByKey = mutableMapOf<String, Pair<Int, String>>()
    private var pushDebugFlushScheduled = false
    private val flushPushDebugRunnable = Runnable { flushPendingPushDebugLogs() }

    private val sourceSignals = mutableMapOf<String, Set<MbCanSignal>>()
    private val sourceMutex = Mutex()
    private var boundScope: CoroutineScope? = null
    private var reapplyJob: Job? = null

    private val _availability = MutableStateFlow<MbCanAvailability>(MbCanAvailability.Unknown)
    val availability: StateFlow<MbCanAvailability> = _availability.asStateFlow()

    private val _steeringWheelHeatState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val steeringWheelHeatState: StateFlow<MbCanBinaryState> = _steeringWheelHeatState.asStateFlow()
    private val _wiperMaintenanceState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val wiperMaintenanceState: StateFlow<MbCanBinaryState> = _wiperMaintenanceState.asStateFlow()
    private val _parkingRadarState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val parkingRadarState: StateFlow<MbCanBinaryState> = _parkingRadarState.asStateFlow()
    private val _rearFogState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val rearFogState: StateFlow<MbCanBinaryState> = _rearFogState.asStateFlow()
    private val _autoLockState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val autoLockState: StateFlow<MbCanBinaryState> = _autoLockState.asStateFlow()
    private val _autoUnlockState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val autoUnlockState: StateFlow<MbCanBinaryState> = _autoUnlockState.asStateFlow()
    private val _rearWiperState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val rearWiperState: StateFlow<MbCanBinaryState> = _rearWiperState.asStateFlow()
    private val _mirrorAutoFoldState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val mirrorAutoFoldState: StateFlow<MbCanBinaryState> = _mirrorAutoFoldState.asStateFlow()
    private val _followMeHomeMode = MutableStateFlow<FollowMeHomeMode?>(null)
    val followMeHomeMode: StateFlow<FollowMeHomeMode?> = _followMeHomeMode.asStateFlow()
    private val _driverUnlockMode = MutableStateFlow<Int?>(null)
    val driverUnlockMode: StateFlow<Int?> = _driverUnlockMode.asStateFlow()
    private val _remoteLockFeedback = MutableStateFlow<Int?>(null)
    val remoteLockFeedback: StateFlow<Int?> = _remoteLockFeedback.asStateFlow()
    private val _wiperSensitivity = MutableStateFlow<Int?>(null)
    val wiperSensitivity: StateFlow<Int?> = _wiperSensitivity.asStateFlow()
    private val _lowBeamHeight = MutableStateFlow<Int?>(null)
    val lowBeamHeight: StateFlow<Int?> = _lowBeamHeight.asStateFlow()
    private val _turnFlashCount = MutableStateFlow<Int?>(null)
    val turnFlashCount: StateFlow<Int?> = _turnFlashCount.asStateFlow()
    private val _avhState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val avhState: StateFlow<MbCanBinaryState> = _avhState.asStateFlow()
    private val _hdcState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val hdcState: StateFlow<MbCanBinaryState> = _hdcState.asStateFlow()
    private val _espOffState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val espOffState: StateFlow<MbCanBinaryState> = _espOffState.asStateFlow()
    private val _lasModeRaw = MutableStateFlow<Int?>(null)
    val lasModeRaw: StateFlow<Int?> = _lasModeRaw.asStateFlow()
    private val _headlightModeRaw = MutableStateFlow<Int?>(null)
    val headlightModeRaw: StateFlow<Int?> = _headlightModeRaw.asStateFlow()
    private val _tjaIcaState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val tjaIcaState: StateFlow<MbCanBinaryState> = _tjaIcaState.asStateFlow()
    private val _hmaState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val hmaState: StateFlow<MbCanBinaryState> = _hmaState.asStateFlow()
    private val _bsdState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val bsdState: StateFlow<MbCanBinaryState> = _bsdState.asStateFlow()
    private val _dowState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val dowState: StateFlow<MbCanBinaryState> = _dowState.asStateFlow()
    private val _fcwState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val fcwState: StateFlow<MbCanBinaryState> = _fcwState.asStateFlow()
    private val _fcwSensitivity = MutableStateFlow<FcwSensitivity?>(null)
    val fcwSensitivity: StateFlow<FcwSensitivity?> = _fcwSensitivity.asStateFlow()
    private val _ldwSensitivity = MutableStateFlow<LdwSensitivity?>(null)
    val ldwSensitivity: StateFlow<LdwSensitivity?> = _ldwSensitivity.asStateFlow()
    private val _hvacAcMaxState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val hvacAcMaxState: StateFlow<MbCanBinaryState> = _hvacAcMaxState.asStateFlow()
    private val _frontWindscreenHeatState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val frontWindscreenHeatState: StateFlow<MbCanBinaryState> = _frontWindscreenHeatState.asStateFlow()
    private val _hvacDefrosterState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val hvacDefrosterState: StateFlow<MbCanBinaryState> = _hvacDefrosterState.asStateFlow()
    private val _hvacAirRecirculationState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val hvacAirRecirculationState: StateFlow<MbCanBinaryState> = _hvacAirRecirculationState.asStateFlow()
    private val _hvacAcPowerState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val hvacAcPowerState: StateFlow<MbCanBinaryState> = _hvacAcPowerState.asStateFlow()
    private val _hvacAcCleanWhenLockedState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val hvacAcCleanWhenLockedState: StateFlow<MbCanBinaryState> = _hvacAcCleanWhenLockedState.asStateFlow()
    private val _hvacAutoState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val hvacAutoState: StateFlow<MbCanBinaryState> = _hvacAutoState.asStateFlow()
    private val _hvacAnionPurifyState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val hvacAnionPurifyState: StateFlow<MbCanBinaryState> = _hvacAnionPurifyState.asStateFlow()
    private val _fragranceSwitchState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val fragranceSwitchState: StateFlow<MbCanBinaryState> = _fragranceSwitchState.asStateFlow()
    private val _fragranceSmell = MutableStateFlow<Int?>(null)
    val fragranceSmell: StateFlow<Int?> = _fragranceSmell.asStateFlow()
    private val _fragranceConcentration = MutableStateFlow<Int?>(null)
    val fragranceConcentration: StateFlow<Int?> = _fragranceConcentration.asStateFlow()
    private val _firstBlowingState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val firstBlowingState: StateFlow<MbCanBinaryState> = _firstBlowingState.asStateFlow()
    private val _btReduceFanState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val btReduceFanState: StateFlow<MbCanBinaryState> = _btReduceFanState.asStateFlow()
    private val _autoVentilationState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val autoVentilationState: StateFlow<MbCanBinaryState> = _autoVentilationState.asStateFlow()
    private val _hvacDefrosterFrontState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val hvacDefrosterFrontState: StateFlow<MbCanBinaryState> = _hvacDefrosterFrontState.asStateFlow()
    private val _wirelessChargingState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val wirelessChargingState: StateFlow<MbCanBinaryState> = _wirelessChargingState.asStateFlow()
    private val _frontLeftSeatModeState = MutableStateFlow<MbCanSeatModeState>(MbCanSeatModeState.Unknown)
    val frontLeftSeatModeState: StateFlow<MbCanSeatModeState> = _frontLeftSeatModeState.asStateFlow()
    private val _frontRightSeatModeState = MutableStateFlow<MbCanSeatModeState>(MbCanSeatModeState.Unknown)
    val frontRightSeatModeState: StateFlow<MbCanSeatModeState> = _frontRightSeatModeState.asStateFlow()
    private val _rearLeftSeatModeState = MutableStateFlow<MbCanSeatModeState>(MbCanSeatModeState.Unknown)
    val rearLeftSeatModeState: StateFlow<MbCanSeatModeState> = _rearLeftSeatModeState.asStateFlow()
    private val _rearRightSeatModeState = MutableStateFlow<MbCanSeatModeState>(MbCanSeatModeState.Unknown)
    val rearRightSeatModeState: StateFlow<MbCanSeatModeState> = _rearRightSeatModeState.asStateFlow()
    private val _audioVolumeSpeedState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val audioVolumeSpeedState: StateFlow<MbCanBinaryState> = _audioVolumeSpeedState.asStateFlow()
    private val _audioVolumeSpeedModeState = MutableStateFlow<Int?>(null)
    val audioVolumeSpeedModeState: StateFlow<Int?> = _audioVolumeSpeedModeState.asStateFlow()
    private val _audioKeyToneVolume = MutableStateFlow<Int?>(null)
    val audioKeyToneVolume: StateFlow<Int?> = _audioKeyToneVolume.asStateFlow()
    private val _audioRadarAlarmVolume = MutableStateFlow<Int?>(null)
    val audioRadarAlarmVolume: StateFlow<Int?> = _audioRadarAlarmVolume.asStateFlow()
    private val _audioVolumeState = MutableStateFlow<Int?>(null)
    val audioVolumeState: StateFlow<Int?> = _audioVolumeState.asStateFlow()
    private val _audioEqMode = MutableStateFlow<Int?>(null)
    val audioEqMode: StateFlow<Int?> = _audioEqMode.asStateFlow()
    private val _audioEqBass = MutableStateFlow<Int?>(null)
    val audioEqBass: StateFlow<Int?> = _audioEqBass.asStateFlow()
    private val _audioEqMiddle = MutableStateFlow<Int?>(null)
    val audioEqMiddle: StateFlow<Int?> = _audioEqMiddle.asStateFlow()
    private val _audioEqTreble = MutableStateFlow<Int?>(null)
    val audioEqTreble: StateFlow<Int?> = _audioEqTreble.asStateFlow()
    private val _audioBalance = MutableStateFlow<Int?>(null)
    val audioBalance: StateFlow<Int?> = _audioBalance.asStateFlow()
    private val _audioFader = MutableStateFlow<Int?>(null)
    val audioFader: StateFlow<Int?> = _audioFader.asStateFlow()
    private val _audioVolumeLastNonZeroInSession = MutableStateFlow<Int?>(null)
    val audioVolumeLastNonZeroInSession: StateFlow<Int?> = _audioVolumeLastNonZeroInSession.asStateFlow()
    private val _engineRpmState = MutableStateFlow<Float?>(null)
    val engineRpmState: StateFlow<Float?> = _engineRpmState.asStateFlow()
    private val _engineTemperatureState = MutableStateFlow<Float?>(null)
    val engineTemperatureState: StateFlow<Float?> = _engineTemperatureState.asStateFlow()
    private val _carSpeedState = MutableStateFlow<Float?>(null)
    val carSpeedState: StateFlow<Float?> = _carSpeedState.asStateFlow()
    private val _gearBoxModeState = MutableStateFlow<String?>(null)
    val gearBoxModeState: StateFlow<String?> = _gearBoxModeState.asStateFlow()
    private val _reverseGearSwitchState = MutableStateFlow<Boolean?>(null)
    val reverseGearSwitchState: StateFlow<Boolean?> = _reverseGearSwitchState.asStateFlow()
    private val _fuelLevelPercentState = MutableStateFlow<UInt?>(null)
    val fuelLevelPercentState: StateFlow<UInt?> = _fuelLevelPercentState.asStateFlow()
    private val _odometerKmState = MutableStateFlow<UInt?>(null)
    val odometerKmState: StateFlow<UInt?> = _odometerKmState.asStateFlow()
    private val _wheelPulseState = MutableStateFlow<vad.dashing.tbox.vehicle.WheelCounters?>(null)
    val wheelPulseState: StateFlow<vad.dashing.tbox.vehicle.WheelCounters?> = _wheelPulseState.asStateFlow()
    private val _outsideTemperatureState = MutableStateFlow<Float?>(null)
    val outsideTemperatureState: StateFlow<Float?> = _outsideTemperatureState.asStateFlow()
    private val _wheelsPressureState = MutableStateFlow(Wheels())
    val wheelsPressureState: StateFlow<Wheels> = _wheelsPressureState.asStateFlow()
    private val _wheelsTemperatureState = MutableStateFlow(Wheels())
    val wheelsTemperatureState: StateFlow<Wheels> = _wheelsTemperatureState.asStateFlow()
    private val _currentFuelConsumptionState = MutableStateFlow<Float?>(null)
    val currentFuelConsumptionState: StateFlow<Float?> = _currentFuelConsumptionState.asStateFlow()
    private val _distanceToNextMaintenanceKmState = MutableStateFlow<UInt?>(null)
    val distanceToNextMaintenanceKmState: StateFlow<UInt?> = _distanceToNextMaintenanceKmState.asStateFlow()
    private val _distanceToFuelEmptyKmState = MutableStateFlow<UInt?>(null)
    val distanceToFuelEmptyKmState: StateFlow<UInt?> = _distanceToFuelEmptyKmState.asStateFlow()
    private val _insideAirQualityState = MutableStateFlow<UInt?>(null)
    val insideAirQualityState: StateFlow<UInt?> = _insideAirQualityState.asStateFlow()
    private val _outsideAirQualityState = MutableStateFlow<UInt?>(null)
    val outsideAirQualityState: StateFlow<UInt?> = _outsideAirQualityState.asStateFlow()
    private val _steerAngleState = MutableStateFlow<Float?>(null)
    val steerAngleState: StateFlow<Float?> = _steerAngleState.asStateFlow()
    private val _steerSpeedState = MutableStateFlow<Float?>(null)
    val steerSpeedState: StateFlow<Float?> = _steerSpeedState.asStateFlow()
    private val _turnSignalsState = MutableStateFlow(TurnSignalsState())
    val turnSignalsState: StateFlow<TurnSignalsState> = _turnSignalsState.asStateFlow()

    private val _carSettingsEpsMode = MutableStateFlow<Int?>(null)
    val carSettingsEpsMode: StateFlow<Int?> = _carSettingsEpsMode.asStateFlow()
    private val _carSettingsDriveMode = MutableStateFlow<Int?>(null)
    val carSettingsDriveMode: StateFlow<Int?> = _carSettingsDriveMode.asStateFlow()
    private val _carSettingsDriveMode6dctWet = MutableStateFlow<Int?>(null)
    val carSettingsDriveMode6dctWet: StateFlow<Int?> = _carSettingsDriveMode6dctWet.asStateFlow()
    private val _hudSwitchState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val hudSwitchState: StateFlow<MbCanBinaryState> = _hudSwitchState.asStateFlow()
    private val _hudHeight = MutableStateFlow<Int?>(null)
    val hudHeight: StateFlow<Int?> = _hudHeight.asStateFlow()
    private val _hudBrightness = MutableStateFlow<Int?>(null)
    val hudBrightness: StateFlow<Int?> = _hudBrightness.asStateFlow()
    private val _hudDisplayMode = MutableStateFlow<Int?>(null)
    val hudDisplayMode: StateFlow<Int?> = _hudDisplayMode.asStateFlow()
    private val _hudAutoBrightnessState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val hudAutoBrightnessState: StateFlow<MbCanBinaryState> = _hudAutoBrightnessState.asStateFlow()
    private val _icmBrightnessMode = MutableStateFlow<Int?>(null)
    val icmBrightnessMode: StateFlow<Int?> = _icmBrightnessMode.asStateFlow()
    private val _icmManualBrightness = MutableStateFlow<Int?>(null)
    val icmManualBrightness: StateFlow<Int?> = _icmManualBrightness.asStateFlow()
    private val _overspeedAlarmKmh = MutableStateFlow<Int?>(null)
    val overspeedAlarmKmh: StateFlow<Int?> = _overspeedAlarmKmh.asStateFlow()
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
    private val _speedLimiterSwitchRaw = MutableStateFlow<Int?>(null)
    val speedLimiterSwitchRaw: StateFlow<Int?> = _speedLimiterSwitchRaw.asStateFlow()
    private val _speedLimiterValueSetRaw = MutableStateFlow<Int?>(null)
    val speedLimiterValueSetRaw: StateFlow<Int?> = _speedLimiterValueSetRaw.asStateFlow()
    private val _accCruiseMode = MutableStateFlow<Int?>(null)
    val accCruiseMode: StateFlow<Int?> = _accCruiseMode.asStateFlow()
    private val _accCruiseVSetDisKmh = MutableStateFlow<Int?>(null)
    val accCruiseVSetDisKmh: StateFlow<Int?> = _accCruiseVSetDisKmh.asStateFlow()
    private val _accFrmFeedbackAvailable = MutableStateFlow(false)
    val accFrmFeedbackAvailable: StateFlow<Boolean> = _accFrmFeedbackAvailable.asStateFlow()
    /** Sticky: true after any non-zero ACCMode this bind session (AUTO cruise path). */
    private val _accModeEverNonZero = MutableStateFlow(false)
    val accModeEverNonZero: StateFlow<Boolean> = _accModeEverNonZero.asStateFlow()
    private val _ccsCruiseStatus = MutableStateFlow<Int?>(null)
    val ccsCruiseStatus: StateFlow<Int?> = _ccsCruiseStatus.asStateFlow()

    private val carSettingsCfgVehicleIds: Set<Int> = setOf(
        MbCanKnownVehiclePropertyId.VEHICLE_PROPERTY_EPS_MODE,
        MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE,
        MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE_6DCT_WET,
        MbCanKnownVehiclePropertyId.DOOR_AUTO_LOCK,
        MbCanKnownVehiclePropertyId.DOOR_IGNOFF_UNLOCK,
        MbCanKnownVehiclePropertyId.HEADLIGHTS_HOMELIGHT_DELAY,
        MbCanKnownVehiclePropertyId.DRIVER_UNLOCK_MODE,
        MbCanKnownVehiclePropertyId.DEFENCES_PROMPT,
        MbCanKnownVehiclePropertyId.WIPER_SENSITIVITY,
        MbCanKnownVehiclePropertyId.REAR_WIPER,
        MbCanKnownVehiclePropertyId.MIRROR_AUTOFOLD_SW,
        MbCanKnownVehiclePropertyId.HIGHBEAM_ADJUST,
        MbCanKnownVehiclePropertyId.TURN_FLASH_COUNT,
    )

    private val stateEngine = MbCanSignalStateEngine(
        steeringFlow = _steeringWheelHeatState,
        wiperMaintenanceFlow = _wiperMaintenanceState,
        parkingRadarFlow = _parkingRadarState,
        rearFogFlow = _rearFogState,
        avhFlow = _avhState,
        hdcFlow = _hdcState,
        espOffFlow = _espOffState,
        tjaIcaFlow = _tjaIcaState,
        hmaFlow = _hmaState,
        hvacAcMaxFlow = _hvacAcMaxState,
        windshieldHeatFlow = _frontWindscreenHeatState,
        hvacDefrosterFlow = _hvacDefrosterState,
        hvacAirRecirculationFlow = _hvacAirRecirculationState,
        hvacAcPowerFlow = _hvacAcPowerState,
        hvacAcCleanWhenLockedFlow = _hvacAcCleanWhenLockedState,
        hvacAutoStateFlow = _hvacAutoState,
        hvacDefrosterFrontFlow = _hvacDefrosterFrontState,
        wirelessChargingFlow = _wirelessChargingState,
        volumeSpeedFlow = _audioVolumeSpeedState,
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

    /**
     * Ensures mbCAN is initialized and republishes [availability] without any widget subscriptions.
     * Used so Settings (e.g. "Reboot HU") can enable controls when no mbCAN dashboard widget was shown yet.
     */
    suspend fun warmUpAvailabilityForUi() = withContext(Dispatchers.Default) {
        try {
            if (MbCanEngineFacade.isInitialized()) {
                _availability.value = MbCanEngineFacade.availability
                // Engine may have been initialized by a listener/read path without
                // replaying JobManager OEM subscribes — heal that, then reapply.
                MbCanJobManager.onEngineInitialized()
                reapplyAllInterests()
                return@withContext
            }
            val availability = MbCanEngineFacade.ensureInitialized()
            _availability.value = availability
            MbCanDiagnostics.log("DEBUG", "warmUpAvailabilityForUi availability=$availability")
            if (availability is MbCanAvailability.Available) {
                MbCanJobManager.onEngineInitialized()
                reapplyAllInterests()
            }
        } catch (e: Exception) {
            MbCanDiagnostics.log("ERROR", "warmUpAvailabilityForUi ${e.message}")
        }
    }

    suspend fun unbind() {
        try {
            MbCanDiagnostics.log("DEBUG", "unbind()")
            cfgPushHandler.removeCallbacks(flushCfgPushesRunnable)
            cfgPushHandler.removeCallbacks(flushAudioCfgPushesRunnable)
            cfgPushHandler.removeCallbacks(flushTelemetryPushesRunnable)
            cfgPushHandler.removeCallbacks(flushTirePushRunnable)
            cfgPushHandler.removeCallbacks(flushTrunkPushRunnable)
            cfgPushHandler.removeCallbacks(flushPushDebugRunnable)
            synchronized(pendingCfgPushes) { pendingCfgPushes.clear() }
            synchronized(pendingAudioPushes) { pendingAudioPushes.clear() }
            synchronized(cfgPushScheduleLock) { cfgPushFlushScheduled = false }
            synchronized(audioPushScheduleLock) { audioPushFlushScheduled = false }
            synchronized(telemetryPushLock) {
                pendingTelemetryPushes.clear()
                telemetryPushFlushScheduled = false
            }
            synchronized(tirePushLock) {
                pendingTirePressure = null
                pendingTireTemperature = null
                tirePushFlushScheduled = false
            }
            synchronized(trunkPushLock) {
                pendingTrunkMoveDir = null
                pendingTrunkSts = null
                trunkPushFlushScheduled = false
            }
            synchronized(pendingGearPush) {
                pendingGearBoxMode = null
                pendingGearBoxModeFlushScheduled = false
            }
            synchronized(pendingReverseGearPush) {
                pendingReverseGearSwitch = null
                pendingReverseGearFlushScheduled = false
            }
            synchronized(pendingTurnSignalsPush) {
                pendingTurnSignals = null
                pendingTurnSignalsFlushScheduled = false
            }
            synchronized(pendingWheelPulsePush) {
                pendingWheelPulse = null
                pendingWheelPulseFlushScheduled = false
            }
            synchronized(pendingPushDebugByKey) {
                pendingPushDebugByKey.clear()
                pushDebugFlushScheduled = false
            }
            MbCanEngineFacade.syncVehicleCfgCmdListener(false)
            MbCanEngineFacade.syncAudioCfgCmdListener(false)
            MbCanEngineFacade.unregisterSettingsTelemetryBridge()
            MbCanEngineFacade.syncLkaSlaStatusListener(false)
            MbCanEngineFacade.syncImbVehicleListener(
                needSteer = false,
                needTurnLights = false,
                needWheelPulse = false,
            )
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
            MbCanKnownVehiclePropertyId.WIPER_MAINTENANCE_SWITCH,
            MbCanKnownVehiclePropertyId.PARKING_RADAR_SWITCH,
            MbCanKnownVehiclePropertyId.REAR_FOG_LIGHT,
            MbCanKnownVehiclePropertyId.AVH_SWITCH,
            MbCanKnownVehiclePropertyId.HDC_SWITCH,
            MbCanKnownVehiclePropertyId.ESP_OFF_SWITCH,
            MbCanKnownVehiclePropertyId.LIGHTCONTROL,
            MbCanKnownVehiclePropertyId.LAS_MODE_SELECTION,
            MbCanKnownVehiclePropertyId.TJA_ICA_SWITCH,
            MbCanKnownVehiclePropertyId.HMA_SWITCH,
            MbCanKnownVehiclePropertyId.HVAC_CUSTOM,
            MbCanKnownVehiclePropertyId.HVAC_AC_MAX,
            MbCanKnownVehiclePropertyId.FRONT_WINDSCREEN_HEAT_SWITCH,
            MbCanKnownVehiclePropertyId.HVAC_DEFROSTER_SWITCH,
            MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION,
            MbCanKnownVehiclePropertyId.HVAC_POWER,
            MbCanKnownVehiclePropertyId.HVAC_BLOWER_DELAY,
            MbCanKnownVehiclePropertyId.HVAC_AUTO_STATE,
            MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION,
            MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_LEFT,
            MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_RIGHT,
            MbCanKnownVehiclePropertyId.HVAC_FAN_SPEED,
            MbCanKnownVehiclePropertyId.HVAC_FRONT_OFF,
            MbCanKnownVehiclePropertyId.HVAC_SYNC_SWITCH,
            MbCanKnownVehiclePropertyId.CHG_WIRELESS_SWITCH,
            in carSettingsCfgVehicleIds,
            MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH,
            MbCanKnownVehiclePropertyId.FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH,
            MbCanKnownVehiclePropertyId.REAR_LEFT_SEAT_HEAT_SWITCH,
            MbCanKnownVehiclePropertyId.REAR_RIGHT_SEAT_HEAT_SWITCH,
            MbCanKnownVehiclePropertyId.VEHICLE_TSR_SWITCH,
            MbCanKnownVehiclePropertyId.VEHICLE_SPEEDLIMIT_SWITCH,
            MbCanKnownVehiclePropertyId.VEHICLE_SPEEDLIMIT_VALUESET -> Unit
            else -> return
        }
        synchronized(pendingCfgPushes) {
            pendingCfgPushes[item] = value
        }
        recordPushDebugEvent("cfg_vehicle/$item", "raw=$value")
        synchronized(cfgPushScheduleLock) {
            if (cfgPushFlushScheduled) return
            cfgPushFlushScheduled = true
        }
        cfgPushHandler.postDelayed(flushCfgPushesRunnable, PUSH_STATE_COALESCE_MS)
    }

    private fun flushPendingCfgPushes() {
        synchronized(cfgPushScheduleLock) {
            cfgPushFlushScheduled = false
        }
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
                    MbCanKnownVehiclePropertyId.WIPER_MAINTENANCE_SWITCH ->
                        stateEngine.applyWiperMaintenanceCandidate(
                            MbCanSignalStateEngine.decodeSteeringWheelHeatRaw(raw)
                        )
                    MbCanKnownVehiclePropertyId.PARKING_RADAR_SWITCH ->
                        stateEngine.applyParkingRadarCandidate(
                            MbCanSignalStateEngine.decodeSteeringWheelHeatRaw(raw)
                        )
                    MbCanKnownVehiclePropertyId.REAR_FOG_LIGHT ->
                        stateEngine.applyRearFogCandidate(
                            MbCanSignalStateEngine.decodeRearFogMbCanRaw(raw)
                        )
                    MbCanKnownVehiclePropertyId.AVH_SWITCH ->
                        stateEngine.applyAvhCandidate(
                            MbCanSignalStateEngine.decodeAvhHdcStatusRaw(raw)
                        )
                    MbCanKnownVehiclePropertyId.HDC_SWITCH ->
                        stateEngine.applyHdcCandidate(
                            MbCanSignalStateEngine.decodeAvhHdcStatusRaw(raw)
                        )
                    MbCanKnownVehiclePropertyId.ESP_OFF_SWITCH ->
                        stateEngine.applyEspOffCandidate(
                            MbCanSignalStateEngine.decodeEspOffStatusRaw(raw)
                        )
                    MbCanKnownVehiclePropertyId.LIGHTCONTROL ->
                        _headlightModeRaw.value = MbCanSignalStateEngine.decodeLightControlRaw(raw)
                    MbCanKnownVehiclePropertyId.LAS_MODE_SELECTION ->
                        _lasModeRaw.value = MbCanSignalStateEngine.decodeLasModeRaw(raw)
                    MbCanKnownVehiclePropertyId.TJA_ICA_SWITCH ->
                        stateEngine.applyTjaIcaCandidate(
                            MbCanSignalStateEngine.decodeSteeringWheelHeatRaw(raw)
                        )
                    MbCanKnownVehiclePropertyId.HMA_SWITCH ->
                        stateEngine.applyHmaCandidate(
                            MbCanSignalStateEngine.decodeSteeringWheelHeatRaw(raw)
                        )
                    MbCanKnownVehiclePropertyId.HVAC_CUSTOM ->
                        HvacClimateCanRepository.applyCustomModeMbCan(raw)
                    MbCanKnownVehiclePropertyId.HVAC_AC_MAX ->
                        stateEngine.applyHvacAcMaxCandidate(
                            MbCanSignalStateEngine.decodeHvacAcMaxMbCanRaw(raw)
                        )
                    MbCanKnownVehiclePropertyId.FRONT_WINDSCREEN_HEAT_SWITCH ->
                        stateEngine.applyWindshieldHeatCandidate(
                            MbCanSignalStateEngine.decodeFrontWindscreenHeatRaw(raw)
                        )
                    MbCanKnownVehiclePropertyId.HVAC_DEFROSTER_SWITCH ->
                        stateEngine.applyHvacDefrosterCandidate(
                            MbCanSignalStateEngine.decodeHvacDefrosterRaw(raw)
                        )
                    MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION ->
                        stateEngine.applyHvacAirRecirculationCandidate(
                            MbCanSignalStateEngine.decodeHvacAirRecirculationRaw(raw)
                        )
                    MbCanKnownVehiclePropertyId.HVAC_POWER ->
                        stateEngine.applyHvacAcPowerCandidate(
                            MbCanSignalStateEngine.decodeHvacAcPowerRaw(raw)
                        )
                    MbCanKnownVehiclePropertyId.HVAC_BLOWER_DELAY ->
                        stateEngine.applyHvacAcCleanWhenLockedCandidate(
                            MbCanSignalStateEngine.decodeHvacBlowerDelayMbCanRaw(raw)
                        )
                    MbCanKnownVehiclePropertyId.HVAC_AUTO_STATE ->
                        stateEngine.applyHvacAutoStateCandidate(
                            MbCanSignalStateEngine.decodeHvacAutoStateRaw(raw)
                        )
                    MbCanKnownVehiclePropertyId.HVAC_AQS ->
                        _hvacAnionPurifyState.value = if (raw == 2) MbCanBinaryState.On else MbCanBinaryState.Off
                    MbCanKnownVehiclePropertyId.FRAGRANCE_SWITCH ->
                        _fragranceSwitchState.value = if (raw == 2) MbCanBinaryState.On else MbCanBinaryState.Off
                    MbCanKnownVehiclePropertyId.FRAGRANCE_SMELL ->
                        _fragranceSmell.value = raw.takeIf { it in 1..3 }
                    MbCanKnownVehiclePropertyId.FRAGRANCE_CONCENTRATION ->
                        _fragranceConcentration.value = raw.takeIf { it in 1..3 }
                    MbCanKnownVehiclePropertyId.POWER_FIRST_BREATH ->
                        _firstBlowingState.value = if (raw == 2) MbCanBinaryState.On else MbCanBinaryState.Off
                    MbCanKnownVehiclePropertyId.BT_REDUCED_WIND_SPEED ->
                        _btReduceFanState.value = if (raw == 2) MbCanBinaryState.On else MbCanBinaryState.Off
                    MbCanKnownVehiclePropertyId.HVAC_VENTILATION_AUTO_SWITCH ->
                        _autoVentilationState.value = if (raw == 2) MbCanBinaryState.On else MbCanBinaryState.Off
                    MbCanKnownVehiclePropertyId.HUD_SWITCH ->
                        _hudSwitchState.value = if (raw == 2) MbCanBinaryState.On else MbCanBinaryState.Off
                    MbCanKnownVehiclePropertyId.HUD_HEIGHT -> _hudHeight.value = raw.takeIf { it in 1..10 }
                    MbCanKnownVehiclePropertyId.HUD_BRIGHTNESS -> _hudBrightness.value = raw.takeIf { it in 1..10 }
                    MbCanKnownVehiclePropertyId.HUD_DISPLAY_MODE -> _hudDisplayMode.value = raw.takeIf { it in 1..2 }
                    MbCanKnownVehiclePropertyId.HUD_AUTO_BRIGHTNESS ->
                        _hudAutoBrightnessState.value = if (raw == 2) MbCanBinaryState.On else MbCanBinaryState.Off
                    MbCanKnownVehiclePropertyId.ICM_BRIGHTNESS_MODE ->
                        _icmBrightnessMode.value = raw.takeIf { it in 0..1 }
                    MbCanKnownVehiclePropertyId.ICM_BRIGHTNESS_MANUAL ->
                        _icmManualBrightness.value = raw.takeIf { it in 1..10 }
                    MbCanKnownVehiclePropertyId.OVERSPEED_ALARM_SET ->
                        _overspeedAlarmKmh.value = CarSettingsHudDomain.decodeOverspeedKmh(raw)
                    MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION -> {
                        stateEngine.applyHvacDefrosterFrontCandidate(
                            MbCanSignalStateEngine.decodeHvacFrontDefrostMbCanRaw(raw)
                        )
                        HvacClimateCanRepository.applyBlowModeMbCan(raw)
                    }
                    MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_LEFT ->
                        HvacClimateCanRepository.applyTempLeftMbCan(raw)
                    MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_RIGHT ->
                        HvacClimateCanRepository.applyTempRightMbCan(raw)
                    MbCanKnownVehiclePropertyId.HVAC_FAN_SPEED ->
                        HvacClimateCanRepository.applyFanSpeed(raw)
                    MbCanKnownVehiclePropertyId.HVAC_FRONT_OFF ->
                        HvacClimateCanRepository.applyFrontOffMbCan(raw)
                    MbCanKnownVehiclePropertyId.HVAC_SYNC_SWITCH ->
                        HvacClimateCanRepository.applySyncMbCan(raw)
                    MbCanKnownVehiclePropertyId.CHG_WIRELESS_SWITCH ->
                        stateEngine.applyWirelessChargingCandidate(
                            MbCanSignalStateEngine.decodeWirelessChargingRaw(raw)
                        )
                    in carSettingsCfgVehicleIds -> applyCarSettingsVehicleCfgPush(item, raw)
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
                    MbCanKnownVehiclePropertyId.VEHICLE_TSR_SWITCH ->
                        _slaOnOffState.value = SlaSpeedLimitDomain.decodeSlaOnOffRaw(raw)
                    MbCanKnownVehiclePropertyId.VEHICLE_SPEEDLIMIT_SWITCH ->
                        applySpeedLimiterSwitchRaw(raw)
                    MbCanKnownVehiclePropertyId.VEHICLE_SPEEDLIMIT_VALUESET ->
                        _speedLimiterValueSetRaw.value = raw
                }
            }
        }
    }

    fun scheduleLkaSlaPush(slaOnOffRaw: Int?, slaStateRaw: Int?, slaLimitRaw: Int?) {
        if (slaOnOffRaw == null && slaStateRaw == null && slaLimitRaw == null) return
        recordPushDebugEvent(
            "lka_sla",
            "onOff=$slaOnOffRaw state=$slaStateRaw limit=$slaLimitRaw",
        )
        val scope = boundScope ?: return
        scope.launch(stateApplyDispatcher) {
            // Settings toggle reads TSR_SPEED_LIMIT_SIGN (18) only; FCM OnOff/State drive sign UI.
            if (slaOnOffRaw != null) slaLkaOnOffRaw = slaOnOffRaw
            if (slaStateRaw != null) slaLkaStateRaw = slaStateRaw
            if (slaLimitRaw != null) {
                slaLkaLimitRaw = slaLimitRaw
                _slaRecognizedSpeedLimitKmh.value = SlaSpeedLimitDomain.decodeRecognizedSpeedKmh(slaLimitRaw)
            }
            publishSlaSignUiState()
        }
    }

    fun scheduleFrmAccPush(accModeRaw: Int?, vSetDisRaw: Int?) {
        if (accModeRaw == null && vSetDisRaw == null) return
        recordPushDebugEvent(
            "frm_acc",
            "accMode=$accModeRaw vSetDis=$vSetDisRaw",
        )
        val scope = boundScope ?: return
        scope.launch(stateApplyDispatcher) {
            _accFrmFeedbackAvailable.value = true
            if (accModeRaw != null) {
                val mode = AccCruiseDomain.decodeMbCanAccMode(accModeRaw)
                _accCruiseMode.value = mode
                if (AccCruiseDomain.isAccModeNonZero(mode)) {
                    _accModeEverNonZero.value = true
                }
            }
            if (vSetDisRaw != null) {
                _accCruiseVSetDisKmh.value = AccCruiseDomain.decodeMbCanVSetDisKmh(vSetDisRaw)
            }
        }
    }

    fun scheduleGaspedCcsPush(cruiseControlStatusRaw: Int?) {
        if (cruiseControlStatusRaw == null) return
        recordPushDebugEvent("gasped_ccs", "cruiseStatus=$cruiseControlStatusRaw")
        val scope = boundScope ?: return
        scope.launch(stateApplyDispatcher) {
            _ccsCruiseStatus.value =
                AccCruiseDomain.decodeMbCanCruiseControlStatus(cruiseControlStatusRaw)
        }
    }

    private fun publishSlaSignUiState() {
        _slaSignUiState.value = SlaSpeedLimitDomain.resolveSlaSignUiState(
            slaOnOffRaw = slaLkaOnOffRaw,
            slaStateRaw = slaLkaStateRaw,
            slaLimitRaw = slaLkaLimitRaw,
        )
    }

    /**
     * [eMBCAN_CFG_AUDIO] push ([IMBCmdListener.onCmdChanged]); [item] is [com.mengbo.mbCan.defines.MBAudioProperty] id.
     */
    fun scheduleAudioCfgPush(_modular: Int, item: Int, value: Int) {
        when (item) {
            MbCanKnownAudioPropertyId.VOLUME,
            MbCanKnownAudioPropertyId.VOLUME_SPEED,
            MbCanKnownAudioPropertyId.VOLUME_KEY,
            MbCanKnownAudioPropertyId.VOLUME_RADAR -> Unit
            else -> return
        }
        synchronized(pendingAudioPushes) {
            pendingAudioPushes[item] = value
        }
        recordPushDebugEvent("cfg_audio/$item", "raw=$value")
        synchronized(audioPushScheduleLock) {
            if (audioPushFlushScheduled) return
            audioPushFlushScheduled = true
        }
        cfgPushHandler.postDelayed(flushAudioCfgPushesRunnable, PUSH_STATE_COALESCE_MS)
    }

    /**
     * Called from [MbCanEngineFacade.registerSettingsTelemetryBridge] push callback.
     */
    fun scheduleEngineRpmPush(rpm: Float?) {
        synchronized(telemetryPushLock) {
            pendingTelemetryPushes[MbCanSignal.EngineRpm] = rpm?.coerceAtLeast(0f)
            if (!telemetryPushFlushScheduled) {
                telemetryPushFlushScheduled = true
                cfgPushHandler.postDelayed(flushTelemetryPushesRunnable, PUSH_STATE_COALESCE_MS)
            }
        }
        recordPushDebugEvent("telemetry/engine_rpm", "raw=$rpm")
    }

    /**
     * Called from [MbCanEngineFacade.registerSettingsTelemetryBridge] push callback.
     */
    fun scheduleEngineTemperaturePush(temperature: Float?) {
        synchronized(telemetryPushLock) {
            pendingTelemetryPushes[MbCanSignal.EngineTemperature] = temperature
            if (!telemetryPushFlushScheduled) {
                telemetryPushFlushScheduled = true
                cfgPushHandler.postDelayed(flushTelemetryPushesRunnable, PUSH_STATE_COALESCE_MS)
            }
        }
        recordPushDebugEvent("telemetry/engine_temp", "raw=$temperature")
    }

    /**
     * Called from [MbCanEngineFacade.registerSettingsTelemetryBridge] push callback.
     */
    fun scheduleCarSpeedPush(speed: Float?) {
        synchronized(telemetryPushLock) {
            pendingTelemetryPushes[MbCanSignal.CarSpeed] = speed?.coerceAtLeast(0f)
            if (!telemetryPushFlushScheduled) {
                telemetryPushFlushScheduled = true
                cfgPushHandler.postDelayed(flushTelemetryPushesRunnable, PUSH_STATE_COALESCE_MS)
            }
        }
        recordPushDebugEvent("telemetry/car_speed", "raw=$speed")
    }

    /**
     * Called from [MbCanEngineFacade.registerSettingsTelemetryBridge] push callback
     * (`onCanVehicleSpeed` also fires for `eMBCAN_VEHICLE_GEAR`).
     */
    fun scheduleVehicleGearPush(rawGear: Int?) {
        val mode = rawGear?.let(VehicleGearDomain::decodePrndBitmask)
        synchronized(pendingGearPush) {
            pendingGearBoxMode = mode
            if (!pendingGearBoxModeFlushScheduled) {
                pendingGearBoxModeFlushScheduled = true
                cfgPushHandler.postDelayed(flushGearBoxModePushRunnable, PUSH_STATE_COALESCE_MS)
            }
        }
        recordPushDebugEvent("telemetry/vehicle_gear", "raw=$rawGear mode=$mode")
    }

    /**
     * Called from [MbCanEngineFacade.registerSettingsTelemetryBridge] BCM callback
     * (`getReverseGearSwitch`).
     */
    fun scheduleReverseGearSwitchPush(raw: Int?) {
        val engaged = raw?.let(VehicleGearDomain::decodeReverseGearSwitch)
        synchronized(pendingReverseGearPush) {
            pendingReverseGearSwitch = engaged
            if (!pendingReverseGearFlushScheduled) {
                pendingReverseGearFlushScheduled = true
                cfgPushHandler.postDelayed(flushReverseGearPushRunnable, PUSH_STATE_COALESCE_MS)
            }
        }
        recordPushDebugEvent("telemetry/reverse_gear_switch", "raw=$raw engaged=$engaged")
    }

    /**
     * Called from [MbCanEngineFacade] [IMBVehicleListener.onVehicleTurnLightChange]
     * (left/right raw bytes from `eMBCAN_VEHICLE_TURNLIGHT`).
     */
    fun scheduleTurnSignalsPush(leftRaw: Int, rightRaw: Int) {
        val state = TurnSignalsDomain.fromMbCanTurnLightRaw(leftRaw, rightRaw)
        synchronized(pendingTurnSignalsPush) {
            pendingTurnSignals = state
            if (!pendingTurnSignalsFlushScheduled) {
                pendingTurnSignalsFlushScheduled = true
                cfgPushHandler.postDelayed(flushTurnSignalsPushRunnable, PUSH_STATE_COALESCE_MS)
            }
        }
        recordPushDebugEvent(
            "telemetry/turn_signals",
            "L=$leftRaw R=$rightRaw left=${state.leftActive} right=${state.rightActive} hazard=${state.hazardActive}",
        )
    }

    /**
     * Called from [MbCanEngineFacade] [IMBVehicleListener.onPull]
     * (LHF/RHF/LHR/RHR from `eMBCAN_VEHICLE_WHEEL` — full atomic frame).
     */
    fun scheduleWheelPulsePush(lhf: Int, rhf: Int, lhr: Int, rhr: Int) {
        val counters = vad.dashing.tbox.vehicle.WheelCounters(
            lhf = lhf.coerceAtLeast(0),
            rhf = rhf.coerceAtLeast(0),
            lhr = lhr.coerceAtLeast(0),
            rhr = rhr.coerceAtLeast(0),
            updatedElapsedMs = SystemClock.elapsedRealtime(),
        )
        synchronized(pendingWheelPulsePush) {
            pendingWheelPulse = counters
            if (!pendingWheelPulseFlushScheduled) {
                pendingWheelPulseFlushScheduled = true
                cfgPushHandler.postDelayed(flushWheelPulsePushRunnable, PUSH_STATE_COALESCE_MS)
            }
        }
        recordPushDebugEvent(
            "telemetry/wheel_pulse",
            "LHF=$lhf RHF=$rhf LHR=$lhr RHR=$rhr",
        )
    }

    fun scheduleFuelLevelPush(percent: UInt?, distanceToEmptyKm: UInt? = null) {
        if (percent == null && distanceToEmptyKm == null) return
        synchronized(pendingFuelLevelPush) {
            // Do not clear a coalesced % when only DTE arrived (or the reverse).
            if (percent != null) {
                pendingFuelLevelPercent = percent
            }
            if (distanceToEmptyKm != null) {
                pendingDistanceToFuelEmptyKm = distanceToEmptyKm
            }
            if (!pendingFuelLevelFlushScheduled) {
                pendingFuelLevelFlushScheduled = true
                cfgPushHandler.postDelayed(flushFuelLevelPushRunnable, PUSH_STATE_COALESCE_MS)
            }
        }
        recordPushDebugEvent("telemetry/fuel_level", "raw=$percent dte=$distanceToEmptyKm")
    }

    fun scheduleCurrentFuelConsumptionPush(litersPer100Km: Float?) {
        synchronized(telemetryPushLock) {
            pendingTelemetryPushes[MbCanSignal.CurrentFuelConsumption] = litersPer100Km
            if (!telemetryPushFlushScheduled) {
                telemetryPushFlushScheduled = true
                cfgPushHandler.postDelayed(flushTelemetryPushesRunnable, PUSH_STATE_COALESCE_MS)
            }
        }
        recordPushDebugEvent("telemetry/current_fuel", "raw=$litersPer100Km")
    }

    fun scheduleSteeringAnglePush(angleDeg: Float?, angleSpeed: Float?) {
        synchronized(telemetryPushLock) {
            pendingTelemetryPushes[MbCanSignal.SteeringAngle] = angleDeg
            pendingSteerSpeedPush = angleSpeed
            pendingSteerSpeedIncluded = true
            if (!telemetryPushFlushScheduled) {
                telemetryPushFlushScheduled = true
                cfgPushHandler.postDelayed(flushTelemetryPushesRunnable, PUSH_STATE_COALESCE_MS)
            }
        }
        recordPushDebugEvent("telemetry/steer", "angle=$angleDeg speed=$angleSpeed")
    }

    fun scheduleTotalOdometerPush(km: UInt?) {
        synchronized(pendingOdometerPush) {
            pendingOdometerKm = km
            if (!pendingOdometerFlushScheduled) {
                pendingOdometerFlushScheduled = true
                cfgPushHandler.postDelayed(flushOdometerPushRunnable, PUSH_STATE_COALESCE_MS)
            }
        }
        recordPushDebugEvent("telemetry/odometer", "raw=$km")
    }

    fun scheduleOutsideTemperaturePush(celsius: Float?) {
        synchronized(telemetryPushLock) {
            pendingTelemetryPushes[MbCanSignal.OutsideTemperature] = celsius
            if (!telemetryPushFlushScheduled) {
                telemetryPushFlushScheduled = true
                cfgPushHandler.postDelayed(flushTelemetryPushesRunnable, PUSH_STATE_COALESCE_MS)
            }
        }
        recordPushDebugEvent("telemetry/outside_temp", "raw=$celsius")
    }

    fun scheduleVehicleTiresPush(pressure: Wheels, temperature: Wheels) {
        synchronized(tirePushLock) {
            pendingTirePressure = pressure
            pendingTireTemperature = temperature
            if (!tirePushFlushScheduled) {
                tirePushFlushScheduled = true
                cfgPushHandler.postDelayed(flushTirePushRunnable, PUSH_STATE_COALESCE_MS)
            }
        }
        recordPushDebugEvent(
            "telemetry/tires",
            "p=${pressure.wheel1}/${pressure.wheel2}/${pressure.wheel3}/${pressure.wheel4}",
        )
    }

    private fun flushPendingTirePush() {
        val snapshot = synchronized(tirePushLock) {
            tirePushFlushScheduled = false
            val p = pendingTirePressure
            val t = pendingTireTemperature
            pendingTirePressure = null
            pendingTireTemperature = null
            p to t
        }
        val (pressure, temperature) = snapshot
        if (pressure == null && temperature == null) return
        val scope = boundScope ?: return
        scope.launch(stateApplyDispatcher) {
            pressure?.let { applyWheelsPressureWithDebounce(it) }
            temperature?.let { _wheelsTemperatureState.value = it }
        }
    }

    /** Disk restore for HU tire pressure (same AppData keys as TBox). */
    fun restoreWheelsPressureFromSaved(saved: Wheels) {
        val now = SystemClock.elapsedRealtime()
        val cur = _wheelsPressureState.value
        val merged = TirePressureDomain.restoreMissingPressures(cur, saved, now)
        if (merged != cur) {
            _wheelsPressureState.value = merged
        }
    }

    private fun applyWheelsPressureWithDebounce(incoming: Wheels) {
        val now = SystemClock.elapsedRealtime()
        _wheelsPressureState.value = TirePressureDomain.mergeWheelsPressure(
            current = _wheelsPressureState.value,
            incoming = incoming,
            now = now,
            debounceMs = UniversalCanRepository.wheelPressureNullDebounceMs,
        )
    }

    /**
     * Called from [MbCanEngineFacade.registerSettingsTelemetryBridge] BCM push callback.
     */
    fun scheduleTrunkBcmPush(moveDir: Int?, trunkSts: Int?) {
        synchronized(trunkPushLock) {
            moveDir?.let { pendingTrunkMoveDir = it }
            trunkSts?.let { pendingTrunkSts = it }
            if (!trunkPushFlushScheduled) {
                trunkPushFlushScheduled = true
                cfgPushHandler.postDelayed(flushTrunkPushRunnable, PUSH_STATE_COALESCE_MS)
            }
        }
        recordPushDebugEvent(
            "telemetry/trunk_bcm",
            "moveDir=$moveDir trunkSts=$trunkSts",
        )
    }

    private fun flushPendingTrunkPush() {
        val snapshot = synchronized(trunkPushLock) {
            trunkPushFlushScheduled = false
            pendingTrunkMoveDir to pendingTrunkSts
        }
        val (moveDir, trunkSts) = snapshot
        if (moveDir == null && trunkSts == null) return
        synchronized(trunkPushLock) {
            pendingTrunkMoveDir = null
            pendingTrunkSts = null
        }
        val scope = boundScope ?: return
        scope.launch(stateApplyDispatcher) {
            TrunkDoorRepository.applyBcmPush(moveDir, trunkSts)
        }
    }

    private fun flushPendingAudioPushes() {
        synchronized(audioPushScheduleLock) {
            audioPushFlushScheduled = false
        }
        val snapshot = synchronized(pendingAudioPushes) {
            if (pendingAudioPushes.isEmpty()) return
            pendingAudioPushes.toMap().also { pendingAudioPushes.clear() }
        }
        val scope = boundScope ?: return
        scope.launch(stateApplyDispatcher) {
            for ((item, raw) in snapshot) {
                when (item) {
                    MbCanKnownAudioPropertyId.VOLUME -> applyAudioVolumeRaw(raw)
                    MbCanKnownAudioPropertyId.VOLUME_SPEED -> {
                        _audioVolumeSpeedModeState.value = CarSettingsAudioDomain.decodeVolumeSpeedMbCan(raw)
                        stateEngine.applyVolumeSpeedCandidate(
                            MbCanSignalStateEngine.decodeVolumeSpeedMbCanRaw(raw)
                        )
                    }
                    MbCanKnownAudioPropertyId.VOLUME_KEY ->
                        _audioKeyToneVolume.value = raw.takeIf { it in 0..3 }
                    MbCanKnownAudioPropertyId.VOLUME_RADAR ->
                        _audioRadarAlarmVolume.value = raw.takeIf { it in 1..3 }
                    MbCanKnownAudioPropertyId.EQ_MODE -> _audioEqMode.value = CarSettingsAudioDomain.decodeEqMode(raw)
                    MbCanKnownAudioPropertyId.EQ_BAND_BASS -> _audioEqBass.value = CarSettingsAudioDomain.decodeEqBand(raw)
                    MbCanKnownAudioPropertyId.EQ_BAND_MIDDLE -> _audioEqMiddle.value = CarSettingsAudioDomain.decodeEqBand(raw)
                    MbCanKnownAudioPropertyId.EQ_BAND_TREBLE -> _audioEqTreble.value = CarSettingsAudioDomain.decodeEqBand(raw)
                    MbCanKnownAudioPropertyId.BALANCE -> _audioBalance.value = CarSettingsAudioDomain.decodeBalanceFader(raw)
                    MbCanKnownAudioPropertyId.FADER -> _audioFader.value = CarSettingsAudioDomain.decodeBalanceFader(raw)
                }
            }
        }
    }

    private fun flushPendingTelemetryPushes() {
        val snapshot = synchronized(telemetryPushLock) {
            telemetryPushFlushScheduled = false
            if (pendingTelemetryPushes.isEmpty() && !pendingSteerSpeedIncluded) return
            val map = pendingTelemetryPushes.toMap().also { pendingTelemetryPushes.clear() }
            val steerSpeed = if (pendingSteerSpeedIncluded) pendingSteerSpeedPush else null
            val includeSteerSpeed = pendingSteerSpeedIncluded
            pendingSteerSpeedPush = null
            pendingSteerSpeedIncluded = false
            Triple(map, steerSpeed, includeSteerSpeed)
        }
        val scope = boundScope ?: return
        scope.launch(stateApplyDispatcher) {
            snapshot.first.forEach { (signal, value) ->
                when (signal) {
                    MbCanSignal.EngineRpm -> _engineRpmState.value = value
                    MbCanSignal.EngineTemperature -> _engineTemperatureState.value = value
                    MbCanSignal.CarSpeed -> _carSpeedState.value = value
                    MbCanSignal.OutsideTemperature -> _outsideTemperatureState.value = value
                    MbCanSignal.CurrentFuelConsumption -> _currentFuelConsumptionState.value = value
                    MbCanSignal.SteeringAngle -> _steerAngleState.value = value
                    else -> Unit
                }
            }
            if (snapshot.third) {
                _steerSpeedState.value = snapshot.second
            }
        }
    }

    private fun flushPendingFuelLevelPush() {
        val (pct, dte) = synchronized(pendingFuelLevelPush) {
            pendingFuelLevelFlushScheduled = false
            val percent = pendingFuelLevelPercent.also { pendingFuelLevelPercent = null }
            val distance = pendingDistanceToFuelEmptyKm.also { pendingDistanceToFuelEmptyKm = null }
            percent to distance
        }
        if (pct == null && dte == null) return
        val scope = boundScope ?: return
        scope.launch(stateApplyDispatcher) {
            pct?.let { _fuelLevelPercentState.value = it }
            dte?.let { _distanceToFuelEmptyKmState.value = it }
        }
    }

    private fun flushPendingOdometerPush() {
        val km = synchronized(pendingOdometerPush) {
            pendingOdometerFlushScheduled = false
            pendingOdometerKm.also { pendingOdometerKm = null }
        } ?: return
        val scope = boundScope ?: return
        scope.launch(stateApplyDispatcher) {
            _odometerKmState.value = km
        }
    }

    private fun flushPendingGearBoxModePush() {
        val mode = synchronized(pendingGearPush) {
            pendingGearBoxModeFlushScheduled = false
            pendingGearBoxMode.also { pendingGearBoxMode = null }
        }
        val scope = boundScope ?: return
        scope.launch(stateApplyDispatcher) {
            _gearBoxModeState.value = mode
        }
    }

    private fun flushPendingReverseGearPush() {
        val engaged = synchronized(pendingReverseGearPush) {
            pendingReverseGearFlushScheduled = false
            pendingReverseGearSwitch.also { pendingReverseGearSwitch = null }
        }
        val scope = boundScope ?: return
        scope.launch(stateApplyDispatcher) {
            _reverseGearSwitchState.value = engaged
        }
    }

    private fun flushPendingTurnSignalsPush() {
        val state = synchronized(pendingTurnSignalsPush) {
            pendingTurnSignalsFlushScheduled = false
            pendingTurnSignals.also { pendingTurnSignals = null }
        } ?: return
        val scope = boundScope ?: return
        scope.launch(stateApplyDispatcher) {
            _turnSignalsState.value = state
        }
    }

    private fun flushPendingWheelPulsePush() {
        val counters = synchronized(pendingWheelPulsePush) {
            pendingWheelPulseFlushScheduled = false
            pendingWheelPulse.also { pendingWheelPulse = null }
        } ?: return
        val scope = boundScope ?: return
        scope.launch(stateApplyDispatcher) {
            _wheelPulseState.value = counters
        }
    }

    private fun recordPushDebugEvent(key: String, sample: String) {
        synchronized(pendingPushDebugByKey) {
            val prev = pendingPushDebugByKey[key]
            val nextCount = (prev?.first ?: 0) + 1
            pendingPushDebugByKey[key] = nextCount to sample
            if (pushDebugFlushScheduled) return
            pushDebugFlushScheduled = true
        }
        cfgPushHandler.postDelayed(flushPushDebugRunnable, PUSH_DEBUG_LOG_COALESCE_MS)
    }

    private fun flushPendingPushDebugLogs() {
        val snapshot = synchronized(pendingPushDebugByKey) {
            pushDebugFlushScheduled = false
            if (pendingPushDebugByKey.isEmpty()) return
            pendingPushDebugByKey.toMap().also { pendingPushDebugByKey.clear() }
        }
        val body = snapshot.entries.joinToString("; ") { (key, payload) ->
            "$key count=${payload.first} last=${payload.second}"
        }
        MbCanDiagnostics.log("DEBUG", "push_coalesced[$body]")
    }

    suspend fun setSourceWidgetKeys(sourceId: String, widgetKeys: Set<String>) {
        cancelDebouncedClearSource(sourceId)
        val normalizedKeys = widgetKeys.map { UniversalCanRepository.normalizeWidgetDataKey(it) }
        val signals = normalizedKeys.mapNotNull { key -> widgetKeyToSignal(key) }.toMutableSet()
        // A9: Front OFF piggybacks on eMBCAN_CFG_VEHICLE with other climate params.
        // Keep HvacFrontOff in the interest set so poll + push stay aligned with climate panels.
        if (normalizedKeys.any { it in HVAC_CLIMATE_WIDGET_DATA_KEYS }) {
            signals.add(MbCanSignal.HvacFrontOff)
        }
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

    /**
     * Registers mbCAN interest for a UI surface by explicit [MbCanSignal]s (no widget-key indirection).
     * Merged with widget-derived interests in [reapplyAllInterests].
     */
    suspend fun setSourceSignals(sourceId: String, signals: Set<MbCanSignal>) {
        cancelDebouncedClearSource(sourceId)
        MbCanDiagnostics.log(
            "DEBUG",
            "setSourceSignals source=$sourceId signals=${signals.joinToString()}"
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
            is MbCanCommand.TrunkPulse -> executeTrunkPulse(command.value)
            is MbCanCommand.ToggleAudioProperty -> executeToggleAudioViaRegistry(command.propertyId)
            is MbCanCommand.SetAudioProperty -> executeSetAudioViaRegistry(command.propertyId, command.value)
            is MbCanCommand.SetFcwEnabled -> executeSetFcwEnabled(command.enabled)
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
        if (availability.value !is MbCanAvailability.Available) {
            return MbCanCommandResult(false, "mbCAN unavailable")
        }
        val target = when (val policy = spec.policy) {
            is MbCanCommandPolicy.ToggleHvacFrontDefrost -> {
                val current = MbCanEngineFacade.canGetVehicleParam(propertyId)
                    ?: return MbCanCommandResult(false, "Pre-read failed")
                        .also {
                            MbCanDiagnostics.log("ERROR", "toggle pre-read failed propertyId=$propertyId")
                        }
                MbCanSignalStateEngine.resolveHvacFrontDefrostMbCanToggleTarget(current)
            }
            is MbCanCommandPolicy.ToggleBinary -> {
                val current = MbCanEngineFacade.canGetVehicleParam(propertyId)
                    ?: return MbCanCommandResult(false, "Pre-read failed")
                        .also {
                            MbCanDiagnostics.log("ERROR", "toggle pre-read failed propertyId=$propertyId")
                        }
                when (current) {
                    policy.onValue -> policy.offValue
                    policy.offValue -> policy.onValue
                    else -> policy.unknownFallbackValue
                }
            }
            else -> return MbCanCommandResult(false, "Toggle unsupported by policy for propertyId=$propertyId")
        }
        MbCanDiagnostics.log("DEBUG", "toggle target=$target propertyId=$propertyId")
        return applySetAndVerify(spec, target)
    }

    private suspend fun executeSetViaRegistry(propertyId: Int, value: Int): MbCanCommandResult {
        MbCanDiagnostics.log("DEBUG", "executeSetProperty propertyId=$propertyId value=$value")
        val spec = MbCanCommandRegistry.get(propertyId)
            ?: return MbCanCommandResult(false, "No command policy for propertyId=$propertyId")
        when (val policy = spec.policy) {
            is MbCanCommandPolicy.SetAnyInt -> Unit
            is MbCanCommandPolicy.SetExact -> {
                if (value !in policy.allowedValues) {
                    return MbCanCommandResult(false, "Value $value is not allowed for propertyId=$propertyId")
                }
            }
            is MbCanCommandPolicy.SetRange -> {
                if (value !in policy.allowedValues) {
                    return MbCanCommandResult(false, "Value $value is not allowed for propertyId=$propertyId")
                }
            }
            // Explicit on/off writes (e.g. SetFcwEnabled triple-write) share ToggleBinary specs.
            is MbCanCommandPolicy.ToggleBinary -> {
                if (value != policy.offValue && value != policy.onValue) {
                    return MbCanCommandResult(false, "Value $value is not allowed for propertyId=$propertyId")
                }
            }
            is MbCanCommandPolicy.ToggleHvacFrontDefrost -> {
                val allowed = setOf(
                    MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FACE,
                    MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FOOT,
                    MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FACE_FOOT,
                    MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_DEFROST,
                    MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_DEFROST_FOOT,
                )
                if (value !in allowed) {
                    return MbCanCommandResult(false, "Value $value is not allowed for propertyId=$propertyId")
                }
            }
            else -> return MbCanCommandResult(false, "Set unsupported by policy for propertyId=$propertyId")
        }
        if (availability.value !is MbCanAvailability.Available) {
            return MbCanCommandResult(false, "mbCAN unavailable")
        }
        return applySetAndVerify(spec, value)
    }

    private suspend fun executeTrunkPulse(value: Int): MbCanCommandResult {
        val allowed = setOf(1, 2)
        if (value !in allowed) {
            return MbCanCommandResult(false, "Trunk pulse value $value is not allowed")
        }
        val spec = MbCanCommandRegistry.get(MbCanKnownVehiclePropertyId.TRUNK_PLG_CONTROL)
            ?: return MbCanCommandResult(false, "No trunk command policy")
        if (availability.value !is MbCanAvailability.Available) {
            return MbCanCommandResult(false, "mbCAN unavailable")
        }
        val first = applySetAndVerify(spec, value)
        if (!first.success) return first
        delay(HvacClimateDomain.TRUNK_PULSE_RESET_MS)
        MbCanEngineFacade.canSetVehicleParam(MbCanKnownVehiclePropertyId.TRUNK_PLG_CONTROL, 0)
        spec.refreshSignal?.let { refreshSignal(it) }
        return MbCanCommandResult(true, "Trunk pulse sent")
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
            if (propertyId in mfsCruisePulsePropertyIds) {
                // Pulse resets on the bus — skip canGet verify; short settle only.
                delay(POST_MFS_CRUISE_PULSE_DELAY_MS)
            } else {
                delay(POST_COMMAND_VERIFY_DELAY_MS)
                val after = MbCanEngineFacade.canGetVehicleParam(propertyId)
                MbCanDiagnostics.log("DEBUG", "set verify propertyId=$propertyId after=$after")
                spec.refreshSignal?.let { refreshSignal(it) }
            }
        }
        return MbCanCommandResult(setResult >= 0, "Set result: $setResult")
    }

    private suspend fun executeToggleAudioViaRegistry(propertyId: Int): MbCanCommandResult {
        MbCanDiagnostics.log("DEBUG", "executeToggleAudioProperty propertyId=$propertyId")
        val spec = MbCanAudioCommandRegistry.get(propertyId)
            ?: return MbCanCommandResult(false, "No audio command policy for propertyId=$propertyId")
        val policy = spec.policy as? MbCanCommandPolicy.ToggleBinary
            ?: return MbCanCommandResult(false, "Toggle unsupported for audio propertyId=$propertyId")
        if (availability.value !is MbCanAvailability.Available) {
            return MbCanCommandResult(false, "mbCAN unavailable")
        }
        val current = MbCanEngineFacade.canGetAudioParam(propertyId)
            ?: return MbCanCommandResult(false, "Pre-read failed")
                .also { MbCanDiagnostics.log("ERROR", "audio toggle pre-read failed propertyId=$propertyId") }
        val target = when (current) {
            policy.onValue -> policy.offValue
            policy.offValue -> policy.onValue
            else -> policy.unknownFallbackValue
        }
        MbCanDiagnostics.log("DEBUG", "audio toggle pre-read current=$current target=$target propertyId=$propertyId")
        return applyAudioSetAndVerify(spec, target)
    }

    private suspend fun executeSetAudioViaRegistry(propertyId: Int, value: Int): MbCanCommandResult {
        MbCanDiagnostics.log("DEBUG", "executeSetAudioProperty propertyId=$propertyId value=$value")
        val spec = MbCanAudioCommandRegistry.get(propertyId)
            ?: return MbCanCommandResult(false, "No audio command policy for propertyId=$propertyId")
        val isAllowed = when (val policy = spec.policy) {
            is MbCanCommandPolicy.SetExact -> value in policy.allowedValues
            is MbCanCommandPolicy.SetRange -> value in policy.allowedValues
            else -> return MbCanCommandResult(false, "Set unsupported for audio propertyId=$propertyId")
        }
        if (!isAllowed) {
            return MbCanCommandResult(false, "Value $value is not allowed for audio propertyId=$propertyId")
        }
        if (availability.value !is MbCanAvailability.Available) {
            return MbCanCommandResult(false, "mbCAN unavailable")
        }
        val encodedValue = when (propertyId) {
            MbCanKnownAudioPropertyId.VOLUME_SPEED -> CarSettingsAudioDomain.encodeVolumeSpeedMbCan(value)
            MbCanKnownAudioPropertyId.EQ_BAND_BASS,
            MbCanKnownAudioPropertyId.EQ_BAND_MIDDLE,
            MbCanKnownAudioPropertyId.EQ_BAND_TREBLE -> CarSettingsAudioDomain.encodeEqBand(value)
            MbCanKnownAudioPropertyId.BALANCE,
            MbCanKnownAudioPropertyId.FADER -> CarSettingsAudioDomain.encodeBalanceFader(value)
            else -> value
        }
            ?: return MbCanCommandResult(false, "Value $value cannot be encoded for audio propertyId=$propertyId")
        return applyAudioSetAndVerify(spec, encodedValue)
    }

    private suspend fun applyAudioSetAndVerify(spec: MbCanAudioCommandSpec, targetValue: Int): MbCanCommandResult {
        val propertyId = spec.propertyId
        val setResult = MbCanEngineFacade.canSetAudioParam(propertyId, targetValue)
            ?: return MbCanCommandResult(false, "Set audio command failed")
                .also { MbCanDiagnostics.log("ERROR", "audio set failed propertyId=$propertyId value=$targetValue") }
        MbCanDiagnostics.log("DEBUG", "audio set result=$setResult propertyId=$propertyId value=$targetValue")
        if (setResult >= 0) {
            spec.refreshSignal?.let { MbCanJobManager.requestBurst(it) }
            delay(POST_COMMAND_VERIFY_DELAY_MS)
            val after = MbCanEngineFacade.canGetAudioParam(propertyId)
            MbCanDiagnostics.log("DEBUG", "audio set verify propertyId=$propertyId after=$after")
            spec.refreshSignal?.let { refreshSignal(it) }
        }
        return MbCanCommandResult(setResult >= 0, "Set result: $setResult")
    }

    suspend fun refreshSignal(signal: MbCanSignal) {
        when (signal) {
            MbCanSignal.SteeringWheelHeat -> refreshSteeringWheelHeat()
            MbCanSignal.WiperMaintenance -> refreshWiperMaintenance()
            MbCanSignal.ParkingRadar -> refreshParkingRadar()
            MbCanSignal.RearFogLight -> refreshRearFog()
            MbCanSignal.AutoLock,
            MbCanSignal.AutoUnlock,
            MbCanSignal.FollowMeHome,
            MbCanSignal.DriverUnlockMode,
            MbCanSignal.RemoteLockFeedback,
            MbCanSignal.WiperSensitivity,
            MbCanSignal.RearWiper,
            MbCanSignal.MirrorAutoFold,
            MbCanSignal.LowBeamHeight,
            MbCanSignal.TurnFlashCount -> refreshCertifiedCarSettingsSignal(signal)
            MbCanSignal.AvhSwitch -> refreshAvh()
            MbCanSignal.HdcSwitch -> refreshHdc()
            MbCanSignal.EspOffSwitch -> refreshEspOff()
            MbCanSignal.LightControl -> refreshLightControl()
            MbCanSignal.LasModeSelection -> refreshLasMode()
            MbCanSignal.TjaIca -> refreshTjaIca()
            MbCanSignal.HmaSwitch -> refreshHma()
            MbCanSignal.Bsd -> refreshAdasBinary(MbCanKnownVehiclePropertyId.BLIND_AREA_DETECTION, _bsdState)
            MbCanSignal.Dow -> refreshAdasBinary(MbCanKnownVehiclePropertyId.DOOR_OPEN_WARNING, _dowState)
            MbCanSignal.Fcw -> refreshAdasBinary(MbCanKnownVehiclePropertyId.FCW_SWITCH, _fcwState)
            MbCanSignal.FcwSensitivity -> _fcwSensitivity.value =
                MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.FCW_SENSITIVITY)?.let(CarSettingsAdasDomain::decodeFcwSensitivityMbCan)
            MbCanSignal.LdwSensitivity -> _ldwSensitivity.value =
                MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.LAS_SENSITIVITY_LEVEL)?.let(CarSettingsAdasDomain::decodeLdwSensitivityMbCan)
            MbCanSignal.HvacCustomMode -> refreshHvacCustomMode()
            MbCanSignal.HvacAcMax -> refreshHvacAcMax()
            MbCanSignal.FrontWindscreenHeat -> refreshFrontWindscreenHeat()
            MbCanSignal.HvacDefroster -> refreshHvacDefroster()
            MbCanSignal.HvacAirRecirculation -> refreshHvacAirRecirculation()
            MbCanSignal.HvacAcPower -> refreshHvacAcPower()
            MbCanSignal.HvacAcCleanWhenLocked -> refreshHvacAcCleanWhenLocked()
            MbCanSignal.HvacAutoState -> refreshHvacAutoState()
            MbCanSignal.HvacAnionPurify -> refreshSimpleBinary(MbCanKnownVehiclePropertyId.HVAC_AQS, _hvacAnionPurifyState)
            MbCanSignal.FragranceSwitch -> refreshSimpleBinary(MbCanKnownVehiclePropertyId.FRAGRANCE_SWITCH, _fragranceSwitchState)
            MbCanSignal.FragranceSmell -> _fragranceSmell.value =
                MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.FRAGRANCE_SMELL)?.takeIf { it in 1..3 }
            MbCanSignal.FragranceConcentration -> _fragranceConcentration.value =
                MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.FRAGRANCE_CONCENTRATION)?.takeIf { it in 1..3 }
            MbCanSignal.FirstBlowing -> refreshSimpleBinary(MbCanKnownVehiclePropertyId.POWER_FIRST_BREATH, _firstBlowingState)
            MbCanSignal.BtReduceFan -> refreshSimpleBinary(MbCanKnownVehiclePropertyId.BT_REDUCED_WIND_SPEED, _btReduceFanState)
            MbCanSignal.AutoVentilation -> refreshSimpleBinary(MbCanKnownVehiclePropertyId.HVAC_VENTILATION_AUTO_SWITCH, _autoVentilationState)
            MbCanSignal.HvacDefrosterFront -> refreshHvacDefrosterFront()
            MbCanSignal.HvacFrontOff -> refreshHvacFrontOff()
            MbCanSignal.HvacTempLeft -> refreshHvacTempLeft()
            MbCanSignal.HvacTempRight -> refreshHvacTempRight()
            MbCanSignal.HvacFanSpeed -> refreshHvacFanSpeed()
            MbCanSignal.HvacSync -> refreshHvacSync()
            MbCanSignal.HvacBlowMode -> refreshHvacBlowMode()
            MbCanSignal.HudSwitch -> refreshSimpleBinary(MbCanKnownVehiclePropertyId.HUD_SWITCH, _hudSwitchState)
            MbCanSignal.HudHeight -> _hudHeight.value = MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.HUD_HEIGHT)?.takeIf { it in 1..10 }
            MbCanSignal.HudBrightness -> _hudBrightness.value = MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.HUD_BRIGHTNESS)?.takeIf { it in 1..10 }
            MbCanSignal.HudDisplayMode -> _hudDisplayMode.value = MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.HUD_DISPLAY_MODE)?.takeIf { it in 1..2 }
            MbCanSignal.HudAutoBrightness -> refreshSimpleBinary(MbCanKnownVehiclePropertyId.HUD_AUTO_BRIGHTNESS, _hudAutoBrightnessState)
            MbCanSignal.IcmBrightnessMode -> _icmBrightnessMode.value =
                MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.ICM_BRIGHTNESS_MODE)?.takeIf { it in 0..1 }
            MbCanSignal.IcmManualBrightness -> _icmManualBrightness.value =
                MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.ICM_BRIGHTNESS_MANUAL)?.takeIf { it in 1..10 }
            MbCanSignal.OverspeedAlarm -> _overspeedAlarmKmh.value = MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.OVERSPEED_ALARM_SET)?.let(CarSettingsHudDomain::decodeOverspeedKmh)
            MbCanSignal.TrunkDoor -> refreshTrunkDoor()
            MbCanSignal.WirelessChargingSwitch -> refreshWirelessCharging()
            MbCanSignal.CarSettingsVehicleParams -> refreshCarSettingsVehicleParams()
            MbCanSignal.AudioVolume -> refreshAudioVolume()
            MbCanSignal.AudioVolumeSpeed -> refreshAudioVolumeSpeed()
            MbCanSignal.AudioKeyToneVolume,
            MbCanSignal.AudioRadarAlarmVolume -> refreshAudioAlertVolumes()
            MbCanSignal.AudioEqMode,
            MbCanSignal.AudioEqBass,
            MbCanSignal.AudioEqMiddle,
            MbCanSignal.AudioEqTreble,
            MbCanSignal.AudioBalance,
            MbCanSignal.AudioFader -> refreshAudioEqAndBalance()
            MbCanSignal.FrontLeftSeatMode -> refreshSeatSlot(MbCanSeatSlot.FrontLeft)
            MbCanSignal.FrontRightSeatMode -> refreshSeatSlot(MbCanSeatSlot.FrontRight)
            MbCanSignal.RearLeftSeatMode -> refreshSeatSlot(MbCanSeatSlot.RearLeft)
            MbCanSignal.RearRightSeatMode -> refreshSeatSlot(MbCanSeatSlot.RearRight)
            MbCanSignal.EngineRpm -> refreshEngineRpm()
            MbCanSignal.EngineTemperature -> refreshEngineTemperature()
            MbCanSignal.CarSpeed -> refreshCarSpeed()
            MbCanSignal.VehicleGear -> refreshVehicleGear()
            MbCanSignal.ReverseGearSwitch -> refreshReverseGearSwitch()
            MbCanSignal.FuelLevel -> refreshFuelLevel()
            MbCanSignal.TotalOdometer -> refreshTotalOdometer()
            MbCanSignal.WheelPulse -> refreshWheelPulse()
            MbCanSignal.OutsideTemperature -> refreshOutsideTemperature()
            MbCanSignal.VehicleTires -> refreshVehicleTires()
            MbCanSignal.CurrentFuelConsumption -> refreshCurrentFuelConsumption()
            MbCanSignal.DistanceToNextMaintenance -> refreshDistanceToNextMaintenance()
            MbCanSignal.DistanceToFuelEmpty -> refreshDistanceToFuelEmpty()
            MbCanSignal.Pm25AirQuality -> refreshPm25AirQuality()
            MbCanSignal.SteeringAngle -> refreshSteeringAngle()
            MbCanSignal.TurnSignals -> refreshTurnSignals()
            MbCanSignal.SlaSpeedLimit -> refreshSlaSpeedLimit()
            MbCanSignal.SpeedLimiter -> refreshSpeedLimiter()
            MbCanSignal.AccCruise -> refreshAccCruise()
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

    private suspend fun refreshWiperMaintenance() {
        withContext(stateApplyDispatcher) {
            if (!MbCanEngineFacade.isInitialized()) {
                _availability.value = MbCanEngineFacade.probeAvailability()
                stateEngine.applyWiperMaintenanceCandidate(MbCanBinaryState.Unknown)
                return@withContext
            }

            val availability = MbCanEngineFacade.availability
            _availability.value = availability
            if (availability !is MbCanAvailability.Available) {
                MbCanDiagnostics.log("WARN", "refreshWiperMaintenance unavailable=$availability")
                stateEngine.applyWiperMaintenanceCandidate(
                    MbCanBinaryState.Unavailable(
                        reason = (availability as? MbCanAvailability.Unavailable)?.reason ?: "Unavailable"
                    )
                )
                return@withContext
            }
            val raw = MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.WIPER_MAINTENANCE_SWITCH)
            val decoded = if (raw == null) {
                MbCanBinaryState.Unknown
            } else {
                MbCanSignalStateEngine.decodeSteeringWheelHeatRaw(raw)
            }
            stateEngine.applyWiperMaintenanceCandidate(decoded)
            MbCanDiagnostics.log(
                "DEBUG",
                "refreshWiperMaintenance raw=$raw state=${_wiperMaintenanceState.value}"
            )
        }
    }

    private suspend fun refreshParkingRadar() {
        withContext(stateApplyDispatcher) {
            if (!MbCanEngineFacade.isInitialized()) {
                _availability.value = MbCanEngineFacade.probeAvailability()
                stateEngine.applyParkingRadarCandidate(MbCanBinaryState.Unknown)
                return@withContext
            }

            val availability = MbCanEngineFacade.availability
            _availability.value = availability
            if (availability !is MbCanAvailability.Available) {
                MbCanDiagnostics.log("WARN", "refreshParkingRadar unavailable=$availability")
                stateEngine.applyParkingRadarCandidate(
                    MbCanBinaryState.Unavailable(
                        reason = (availability as? MbCanAvailability.Unavailable)?.reason ?: "Unavailable"
                    )
                )
                return@withContext
            }
            val raw = MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.PARKING_RADAR_SWITCH)
            val decoded = if (raw == null) {
                MbCanBinaryState.Unknown
            } else {
                MbCanSignalStateEngine.decodeSteeringWheelHeatRaw(raw)
            }
            stateEngine.applyParkingRadarCandidate(decoded)
            MbCanDiagnostics.log(
                "DEBUG",
                "refreshParkingRadar raw=$raw state=${_parkingRadarState.value}"
            )
        }
    }

    private suspend fun refreshRearFog() {
        withContext(stateApplyDispatcher) {
            if (!MbCanEngineFacade.isInitialized()) {
                _availability.value = MbCanEngineFacade.probeAvailability()
                stateEngine.applyRearFogCandidate(MbCanBinaryState.Unknown)
                return@withContext
            }

            val availability = MbCanEngineFacade.availability
            _availability.value = availability
            if (availability !is MbCanAvailability.Available) {
                MbCanDiagnostics.log("WARN", "refreshRearFog unavailable=$availability")
                stateEngine.applyRearFogCandidate(
                    MbCanBinaryState.Unavailable(
                        reason = (availability as? MbCanAvailability.Unavailable)?.reason ?: "Unavailable"
                    )
                )
                return@withContext
            }
            val raw = MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.REAR_FOG_LIGHT)
            val decoded = if (raw == null) {
                MbCanBinaryState.Unknown
            } else {
                MbCanSignalStateEngine.decodeRearFogMbCanRaw(raw)
            }
            stateEngine.applyRearFogCandidate(decoded)
            MbCanDiagnostics.log(
                "DEBUG",
                "refreshRearFog raw=$raw state=${_rearFogState.value}"
            )
        }
    }

    private suspend fun refreshAvh() {
        withContext(stateApplyDispatcher) {
            if (!MbCanEngineFacade.isInitialized()) {
                _availability.value = MbCanEngineFacade.probeAvailability()
                stateEngine.applyAvhCandidate(MbCanBinaryState.Unknown)
                return@withContext
            }

            val availability = MbCanEngineFacade.availability
            _availability.value = availability
            if (availability !is MbCanAvailability.Available) {
                MbCanDiagnostics.log("WARN", "refreshAvh unavailable=$availability")
                stateEngine.applyAvhCandidate(
                    MbCanBinaryState.Unavailable(
                        reason = (availability as? MbCanAvailability.Unavailable)?.reason ?: "Unavailable"
                    )
                )
                return@withContext
            }
            val raw = MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.AVH_SWITCH)
            val decoded = if (raw == null) {
                MbCanBinaryState.Unknown
            } else {
                MbCanSignalStateEngine.decodeAvhHdcStatusRaw(raw)
            }
            stateEngine.applyAvhCandidate(decoded)
            MbCanDiagnostics.log(
                "DEBUG",
                "refreshAvh raw=$raw state=${_avhState.value}"
            )
        }
    }

    private suspend fun refreshHdc() {
        withContext(stateApplyDispatcher) {
            if (!MbCanEngineFacade.isInitialized()) {
                _availability.value = MbCanEngineFacade.probeAvailability()
                stateEngine.applyHdcCandidate(MbCanBinaryState.Unknown)
                return@withContext
            }

            val availability = MbCanEngineFacade.availability
            _availability.value = availability
            if (availability !is MbCanAvailability.Available) {
                MbCanDiagnostics.log("WARN", "refreshHdc unavailable=$availability")
                stateEngine.applyHdcCandidate(
                    MbCanBinaryState.Unavailable(
                        reason = (availability as? MbCanAvailability.Unavailable)?.reason ?: "Unavailable"
                    )
                )
                return@withContext
            }
            val raw = MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.HDC_SWITCH)
            val decoded = if (raw == null) {
                MbCanBinaryState.Unknown
            } else {
                MbCanSignalStateEngine.decodeAvhHdcStatusRaw(raw)
            }
            stateEngine.applyHdcCandidate(decoded)
            MbCanDiagnostics.log(
                "DEBUG",
                "refreshHdc raw=$raw state=${_hdcState.value}"
            )
        }
    }

    private suspend fun refreshEspOff() {
        withContext(stateApplyDispatcher) {
            if (!MbCanEngineFacade.isInitialized()) {
                _availability.value = MbCanEngineFacade.probeAvailability()
                stateEngine.applyEspOffCandidate(MbCanBinaryState.Unknown)
                return@withContext
            }

            val availability = MbCanEngineFacade.availability
            _availability.value = availability
            if (availability !is MbCanAvailability.Available) {
                MbCanDiagnostics.log("WARN", "refreshEspOff unavailable=$availability")
                stateEngine.applyEspOffCandidate(
                    MbCanBinaryState.Unavailable(
                        reason = (availability as? MbCanAvailability.Unavailable)?.reason ?: "Unavailable"
                    )
                )
                return@withContext
            }
            val raw = MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.ESP_OFF_SWITCH)
            val decoded = if (raw == null) {
                MbCanBinaryState.Unknown
            } else {
                MbCanSignalStateEngine.decodeEspOffStatusRaw(raw)
            }
            stateEngine.applyEspOffCandidate(decoded)
            MbCanDiagnostics.log(
                "DEBUG",
                "refreshEspOff raw=$raw state=${_espOffState.value}"
            )
        }
    }

    private suspend fun refreshLasMode() {
        withContext(stateApplyDispatcher) {
            if (!MbCanEngineFacade.isInitialized()) {
                _availability.value = MbCanEngineFacade.probeAvailability()
                _lasModeRaw.value = null
                return@withContext
            }
            val availability = MbCanEngineFacade.availability
            _availability.value = availability
            if (availability !is MbCanAvailability.Available) {
                _lasModeRaw.value = null
                return@withContext
            }
            val raw = MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.LAS_MODE_SELECTION)
            _lasModeRaw.value = raw?.let { MbCanSignalStateEngine.decodeLasModeRaw(it) }
            MbCanDiagnostics.log("DEBUG", "refreshLasMode raw=$raw state=${_lasModeRaw.value}")
        }
    }

    private suspend fun refreshLightControl() {
        withContext(stateApplyDispatcher) {
            if (!MbCanEngineFacade.isInitialized()) {
                _availability.value = MbCanEngineFacade.probeAvailability()
                _headlightModeRaw.value = null
                return@withContext
            }
            val availability = MbCanEngineFacade.availability
            _availability.value = availability
            if (availability !is MbCanAvailability.Available) {
                _headlightModeRaw.value = null
                return@withContext
            }
            val raw = MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.LIGHTCONTROL)
            _headlightModeRaw.value = raw?.let { MbCanSignalStateEngine.decodeLightControlRaw(it) }
            MbCanDiagnostics.log("DEBUG", "refreshLightControl raw=$raw state=${_headlightModeRaw.value}")
        }
    }

    private suspend fun refreshTjaIca() {
        withContext(stateApplyDispatcher) {
            if (!MbCanEngineFacade.isInitialized()) {
                _availability.value = MbCanEngineFacade.probeAvailability()
                stateEngine.applyTjaIcaCandidate(MbCanBinaryState.Unknown)
                return@withContext
            }
            val availability = MbCanEngineFacade.availability
            _availability.value = availability
            if (availability !is MbCanAvailability.Available) {
                stateEngine.applyTjaIcaCandidate(
                    MbCanBinaryState.Unavailable(
                        reason = (availability as? MbCanAvailability.Unavailable)?.reason ?: "Unavailable"
                    )
                )
                return@withContext
            }
            val raw = MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.TJA_ICA_SWITCH)
            val decoded = if (raw == null) MbCanBinaryState.Unknown
            else MbCanSignalStateEngine.decodeSteeringWheelHeatRaw(raw)
            stateEngine.applyTjaIcaCandidate(decoded)
            MbCanDiagnostics.log("DEBUG", "refreshTjaIca raw=$raw state=${_tjaIcaState.value}")
        }
    }

    private suspend fun refreshAdasBinary(
        propertyId: Int,
        target: MutableStateFlow<MbCanBinaryState>,
    ) {
        withContext(stateApplyDispatcher) {
            val raw = MbCanEngineFacade.canGetVehicleParam(propertyId)
            target.value = when (raw) {
                2 -> MbCanBinaryState.On
                1 -> MbCanBinaryState.Off
                null -> MbCanBinaryState.Unknown
                else -> MbCanBinaryState.Unknown
            }
        }
    }

    private suspend fun executeSetFcwEnabled(enabled: Boolean): MbCanCommandResult {
        val value = if (enabled) 2 else 1
        val ids = listOf(
            MbCanKnownVehiclePropertyId.FCW_SWITCH,
            MbCanKnownVehiclePropertyId.ACC_AUTOBRAKE_SWITCH,
            MbCanKnownVehiclePropertyId.SAFE_DISTANCE_WARNING,
        )
        val results = ids.map { executeSetViaRegistry(it, value) }
        refreshSignal(MbCanSignal.Fcw)
        return MbCanCommandResult(results.all { it.success }, "FCW/AEB/distance warning updated")
    }

    private suspend fun refreshHma() {
        withContext(stateApplyDispatcher) {
            if (!MbCanEngineFacade.isInitialized()) {
                _availability.value = MbCanEngineFacade.probeAvailability()
                stateEngine.applyHmaCandidate(MbCanBinaryState.Unknown)
                return@withContext
            }
            val availability = MbCanEngineFacade.availability
            _availability.value = availability
            if (availability !is MbCanAvailability.Available) {
                stateEngine.applyHmaCandidate(
                    MbCanBinaryState.Unavailable(
                        reason = (availability as? MbCanAvailability.Unavailable)?.reason ?: "Unavailable"
                    )
                )
                return@withContext
            }
            val raw = MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.HMA_SWITCH)
            val decoded = if (raw == null) MbCanBinaryState.Unknown
            else MbCanSignalStateEngine.decodeSteeringWheelHeatRaw(raw)
            stateEngine.applyHmaCandidate(decoded)
            MbCanDiagnostics.log("DEBUG", "refreshHma raw=$raw state=${_hmaState.value}")
        }
    }

    private suspend fun refreshHvacCustomMode() {
        withContext(stateApplyDispatcher) {
            if (!MbCanEngineFacade.isInitialized()) {
                _availability.value = MbCanEngineFacade.probeAvailability()
                HvacClimateCanRepository.applyCustomModeMbCan(-1)
                return@withContext
            }
            val availability = MbCanEngineFacade.availability
            _availability.value = availability
            if (availability !is MbCanAvailability.Available) {
                HvacClimateCanRepository.applyCustomModeMbCan(-1)
                return@withContext
            }
            val raw = MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.HVAC_CUSTOM)
            if (raw == null) HvacClimateCanRepository.applyCustomModeMbCan(-1)
            else HvacClimateCanRepository.applyCustomModeMbCan(raw)
            MbCanDiagnostics.log(
                "DEBUG",
                "refreshHvacCustomMode raw=$raw state=${HvacClimateCanRepository.hvacCustomMode.value}"
            )
        }
    }

    private suspend fun refreshHvacAcMax() {
        withContext(stateApplyDispatcher) {
            if (!MbCanEngineFacade.isInitialized()) {
                _availability.value = MbCanEngineFacade.probeAvailability()
                stateEngine.applyHvacAcMaxCandidate(MbCanBinaryState.Unknown)
                return@withContext
            }
            val availability = MbCanEngineFacade.availability
            _availability.value = availability
            if (availability !is MbCanAvailability.Available) {
                stateEngine.applyHvacAcMaxCandidate(
                    MbCanBinaryState.Unavailable(
                        reason = (availability as? MbCanAvailability.Unavailable)?.reason ?: "Unavailable"
                    )
                )
                return@withContext
            }
            val raw = MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.HVAC_AC_MAX)
            val decoded = if (raw == null) MbCanBinaryState.Unknown
            else MbCanSignalStateEngine.decodeHvacAcMaxMbCanRaw(raw)
            stateEngine.applyHvacAcMaxCandidate(decoded)
            MbCanDiagnostics.log("DEBUG", "refreshHvacAcMax raw=$raw state=${_hvacAcMaxState.value}")
        }
    }

    private suspend fun refreshFrontWindscreenHeat() {
        withContext(stateApplyDispatcher) {
            if (!MbCanEngineFacade.isInitialized()) {
                _availability.value = MbCanEngineFacade.probeAvailability()
                stateEngine.applyWindshieldHeatCandidate(MbCanBinaryState.Unknown)
                return@withContext
            }

            val availability = MbCanEngineFacade.availability
            _availability.value = availability
            if (availability !is MbCanAvailability.Available) {
                MbCanDiagnostics.log("WARN", "refreshFrontWindscreenHeat unavailable=$availability")
                stateEngine.applyWindshieldHeatCandidate(
                    MbCanBinaryState.Unavailable(
                        reason = (availability as? MbCanAvailability.Unavailable)?.reason ?: "Unavailable"
                    )
                )
                return@withContext
            }
            val raw = MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.FRONT_WINDSCREEN_HEAT_SWITCH)
            val decoded = if (raw == null) {
                MbCanBinaryState.Unknown
            } else {
                MbCanSignalStateEngine.decodeFrontWindscreenHeatRaw(raw)
            }
            stateEngine.applyWindshieldHeatCandidate(decoded)
            MbCanDiagnostics.log(
                "DEBUG",
                "refreshFrontWindscreenHeat raw=$raw state=${_frontWindscreenHeatState.value}"
            )
        }
    }

    private suspend fun refreshHvacDefroster() {
        withContext(stateApplyDispatcher) {
            if (!MbCanEngineFacade.isInitialized()) {
                _availability.value = MbCanEngineFacade.probeAvailability()
                stateEngine.applyHvacDefrosterCandidate(MbCanBinaryState.Unknown)
                return@withContext
            }

            val availability = MbCanEngineFacade.availability
            _availability.value = availability
            if (availability !is MbCanAvailability.Available) {
                MbCanDiagnostics.log("WARN", "refreshHvacDefroster unavailable=$availability")
                stateEngine.applyHvacDefrosterCandidate(
                    MbCanBinaryState.Unavailable(
                        reason = (availability as? MbCanAvailability.Unavailable)?.reason ?: "Unavailable"
                    )
                )
                return@withContext
            }
            val raw = MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.HVAC_DEFROSTER_SWITCH)
            val decoded = if (raw == null) {
                MbCanBinaryState.Unknown
            } else {
                MbCanSignalStateEngine.decodeHvacDefrosterRaw(raw)
            }
            stateEngine.applyHvacDefrosterCandidate(decoded)
            MbCanDiagnostics.log(
                "DEBUG",
                "refreshHvacDefroster raw=$raw state=${_hvacDefrosterState.value}"
            )
        }
    }

    private suspend fun refreshHvacAirRecirculation() {
        withContext(stateApplyDispatcher) {
            if (!MbCanEngineFacade.isInitialized()) {
                _availability.value = MbCanEngineFacade.probeAvailability()
                stateEngine.applyHvacAirRecirculationCandidate(MbCanBinaryState.Unknown)
                return@withContext
            }

            val availability = MbCanEngineFacade.availability
            _availability.value = availability
            if (availability !is MbCanAvailability.Available) {
                MbCanDiagnostics.log("WARN", "refreshHvacAirRecirculation unavailable=$availability")
                stateEngine.applyHvacAirRecirculationCandidate(
                    MbCanBinaryState.Unavailable(
                        reason = (availability as? MbCanAvailability.Unavailable)?.reason ?: "Unavailable"
                    )
                )
                return@withContext
            }
            val raw = MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION)
            val decoded = if (raw == null) {
                MbCanBinaryState.Unknown
            } else {
                MbCanSignalStateEngine.decodeHvacAirRecirculationRaw(raw)
            }
            stateEngine.applyHvacAirRecirculationCandidate(decoded)
            MbCanDiagnostics.log(
                "DEBUG",
                "refreshHvacAirRecirculation raw=$raw state=${_hvacAirRecirculationState.value}"
            )
        }
    }

    private suspend fun refreshHvacAcPower() {
        withContext(stateApplyDispatcher) {
            if (!MbCanEngineFacade.isInitialized()) {
                _availability.value = MbCanEngineFacade.probeAvailability()
                stateEngine.applyHvacAcPowerCandidate(MbCanBinaryState.Unknown)
                return@withContext
            }

            val availability = MbCanEngineFacade.availability
            _availability.value = availability
            if (availability !is MbCanAvailability.Available) {
                MbCanDiagnostics.log("WARN", "refreshHvacAcPower unavailable=$availability")
                stateEngine.applyHvacAcPowerCandidate(
                    MbCanBinaryState.Unavailable(
                        reason = (availability as? MbCanAvailability.Unavailable)?.reason ?: "Unavailable"
                    )
                )
                return@withContext
            }
            val raw = MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.HVAC_POWER)
            val decoded = if (raw == null) {
                MbCanBinaryState.Unknown
            } else {
                MbCanSignalStateEngine.decodeHvacAcPowerRaw(raw)
            }
            stateEngine.applyHvacAcPowerCandidate(decoded)
            MbCanDiagnostics.log(
                "DEBUG",
                "refreshHvacAcPower raw=$raw state=${_hvacAcPowerState.value}"
            )
        }
    }

    private suspend fun refreshHvacAcCleanWhenLocked() {
        withContext(stateApplyDispatcher) {
            if (!MbCanEngineFacade.isInitialized()) {
                _availability.value = MbCanEngineFacade.probeAvailability()
                stateEngine.applyHvacAcCleanWhenLockedCandidate(MbCanBinaryState.Unknown)
                return@withContext
            }

            val availability = MbCanEngineFacade.availability
            _availability.value = availability
            if (availability !is MbCanAvailability.Available) {
                MbCanDiagnostics.log("WARN", "refreshHvacAcCleanWhenLocked unavailable=$availability")
                stateEngine.applyHvacAcCleanWhenLockedCandidate(
                    MbCanBinaryState.Unavailable(
                        reason = (availability as? MbCanAvailability.Unavailable)?.reason ?: "Unavailable"
                    )
                )
                return@withContext
            }
            val raw = MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.HVAC_BLOWER_DELAY)
            val decoded = if (raw == null) {
                MbCanBinaryState.Unknown
            } else {
                MbCanSignalStateEngine.decodeHvacBlowerDelayMbCanRaw(raw)
            }
            stateEngine.applyHvacAcCleanWhenLockedCandidate(decoded)
            MbCanDiagnostics.log(
                "DEBUG",
                "refreshHvacAcCleanWhenLocked raw=$raw state=${_hvacAcCleanWhenLockedState.value}"
            )
        }
    }

    private suspend fun refreshSimpleBinary(
        propertyId: Int,
        target: MutableStateFlow<MbCanBinaryState>,
    ) {
        val raw = MbCanEngineFacade.canGetVehicleParam(propertyId)
        target.value = when (raw) {
            2 -> MbCanBinaryState.On
            1 -> MbCanBinaryState.Off
            else -> MbCanBinaryState.Unknown
        }
    }

    private suspend fun refreshHvacAutoState() {
        withContext(stateApplyDispatcher) {
            if (!MbCanEngineFacade.isInitialized()) {
                _availability.value = MbCanEngineFacade.probeAvailability()
                stateEngine.applyHvacAutoStateCandidate(MbCanBinaryState.Unknown)
                return@withContext
            }

            val availability = MbCanEngineFacade.availability
            _availability.value = availability
            if (availability !is MbCanAvailability.Available) {
                MbCanDiagnostics.log("WARN", "refreshHvacAutoState unavailable=$availability")
                stateEngine.applyHvacAutoStateCandidate(
                    MbCanBinaryState.Unavailable(
                        reason = (availability as? MbCanAvailability.Unavailable)?.reason ?: "Unavailable"
                    )
                )
                return@withContext
            }
            val raw = MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.HVAC_AUTO_STATE)
            val decoded = if (raw == null) {
                MbCanBinaryState.Unknown
            } else {
                MbCanSignalStateEngine.decodeHvacAutoStateRaw(raw)
            }
            stateEngine.applyHvacAutoStateCandidate(decoded)
            MbCanDiagnostics.log(
                "DEBUG",
                "refreshHvacAutoState raw=$raw state=${_hvacAutoState.value}"
            )
        }
    }

    private suspend fun refreshHvacDefrosterFront() {
        withContext(stateApplyDispatcher) {
            if (!MbCanEngineFacade.isInitialized()) {
                _availability.value = MbCanEngineFacade.probeAvailability()
                stateEngine.applyHvacDefrosterFrontCandidate(MbCanBinaryState.Unknown)
                return@withContext
            }

            val availability = MbCanEngineFacade.availability
            _availability.value = availability
            if (availability !is MbCanAvailability.Available) {
                MbCanDiagnostics.log("WARN", "refreshHvacDefrosterFront unavailable=$availability")
                stateEngine.applyHvacDefrosterFrontCandidate(
                    MbCanBinaryState.Unavailable(
                        reason = (availability as? MbCanAvailability.Unavailable)?.reason ?: "Unavailable"
                    )
                )
                return@withContext
            }
            val raw = MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION)
            val decoded = if (raw == null) {
                MbCanBinaryState.Unknown
            } else {
                MbCanSignalStateEngine.decodeHvacFrontDefrostMbCanRaw(raw)
            }
            stateEngine.applyHvacDefrosterFrontCandidate(decoded)
            MbCanDiagnostics.log(
                "DEBUG",
                "refreshHvacDefrosterFront raw=$raw state=${_hvacDefrosterFrontState.value}"
            )
        }
    }

    private suspend fun refreshHvacClimateIntParam(
        propertyId: Int,
        onUnavailable: () -> Unit,
        onRaw: (Int) -> Unit,
    ) {
        withContext(stateApplyDispatcher) {
            if (!MbCanEngineFacade.isInitialized()) {
                _availability.value = MbCanEngineFacade.probeAvailability()
                onUnavailable()
                return@withContext
            }
            val availability = MbCanEngineFacade.availability
            _availability.value = availability
            if (availability !is MbCanAvailability.Available) {
                onUnavailable()
                return@withContext
            }
            val raw = MbCanEngineFacade.canGetVehicleParam(propertyId) ?: return@withContext
            onRaw(raw)
        }
    }

    private suspend fun refreshHvacFrontOff() {
        refreshHvacClimateIntParam(
            propertyId = MbCanKnownVehiclePropertyId.HVAC_FRONT_OFF,
            onUnavailable = { HvacClimateCanRepository.applyFrontOffMbCan(0) },
            onRaw = { HvacClimateCanRepository.applyFrontOffMbCan(it) },
        )
    }

    private suspend fun refreshHvacTempLeft() {
        refreshHvacClimateIntParam(
            propertyId = MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_LEFT,
            onUnavailable = { HvacClimateCanRepository.applyTempLeftMbCan(-1) },
            onRaw = { HvacClimateCanRepository.applyTempLeftMbCan(it) },
        )
    }

    private suspend fun refreshHvacTempRight() {
        refreshHvacClimateIntParam(
            propertyId = MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_RIGHT,
            onUnavailable = { HvacClimateCanRepository.applyTempRightMbCan(-1) },
            onRaw = { HvacClimateCanRepository.applyTempRightMbCan(it) },
        )
    }

    private suspend fun refreshHvacFanSpeed() {
        refreshHvacClimateIntParam(
            propertyId = MbCanKnownVehiclePropertyId.HVAC_FAN_SPEED,
            onUnavailable = { HvacClimateCanRepository.applyFanSpeed(-1) },
            onRaw = { HvacClimateCanRepository.applyFanSpeed(it) },
        )
    }

    private suspend fun refreshHvacSync() {
        refreshHvacClimateIntParam(
            propertyId = MbCanKnownVehiclePropertyId.HVAC_SYNC_SWITCH,
            onUnavailable = { HvacClimateCanRepository.applySyncMbCan(-1) },
            onRaw = { HvacClimateCanRepository.applySyncMbCan(it) },
        )
    }

    private suspend fun refreshHvacBlowMode() {
        refreshHvacClimateIntParam(
            propertyId = MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION,
            onUnavailable = { HvacClimateCanRepository.applyBlowModeMbCan(-1) },
            onRaw = { HvacClimateCanRepository.applyBlowModeMbCan(it) },
        )
    }

    private suspend fun refreshTrunkDoor() {
        withContext(stateApplyDispatcher) {
            if (!MbCanEngineFacade.isInitialized()) {
                _availability.value = MbCanEngineFacade.probeAvailability()
                TrunkDoorRepository.clear()
                return@withContext
            }
            val availability = MbCanEngineFacade.availability
            _availability.value = availability
            if (availability !is MbCanAvailability.Available) {
                TrunkDoorRepository.clear()
                return@withContext
            }
            val snapshot = MbCanEngineFacade.readVehicleBcmTrunkSnapshot()
            if (snapshot != null) {
                TrunkDoorRepository.applyBcmPush(snapshot.moveDir, snapshot.trunkSts)
            }
        }
    }

    private suspend fun refreshWirelessCharging() {
        withContext(stateApplyDispatcher) {
            if (!MbCanEngineFacade.isInitialized()) {
                _availability.value = MbCanEngineFacade.probeAvailability()
                stateEngine.applyWirelessChargingCandidate(MbCanBinaryState.Unknown)
                return@withContext
            }

            val availability = MbCanEngineFacade.availability
            _availability.value = availability
            if (availability !is MbCanAvailability.Available) {
                MbCanDiagnostics.log("WARN", "refreshWirelessCharging unavailable=$availability")
                stateEngine.applyWirelessChargingCandidate(
                    MbCanBinaryState.Unavailable(
                        reason = (availability as? MbCanAvailability.Unavailable)?.reason ?: "Unavailable"
                    )
                )
                return@withContext
            }
            val raw = MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.CHG_WIRELESS_SWITCH)
            val decoded = if (raw == null) {
                MbCanBinaryState.Unknown
            } else {
                MbCanSignalStateEngine.decodeWirelessChargingRaw(raw)
            }
            stateEngine.applyWirelessChargingCandidate(decoded)
            MbCanDiagnostics.log(
                "DEBUG",
                "refreshWirelessCharging raw=$raw state=${_wirelessChargingState.value}"
            )
        }
    }

    private suspend fun refreshAudioVolumeSpeed() {
        withContext(stateApplyDispatcher) {
            if (!MbCanEngineFacade.isInitialized()) {
                _availability.value = MbCanEngineFacade.probeAvailability()
                _audioVolumeSpeedModeState.value = null
                stateEngine.applyVolumeSpeedCandidate(MbCanBinaryState.Unknown)
                return@withContext
            }

            val availability = MbCanEngineFacade.availability
            _availability.value = availability
            if (availability !is MbCanAvailability.Available) {
                MbCanDiagnostics.log("WARN", "refreshAudioVolumeSpeed unavailable=$availability")
                _audioVolumeSpeedModeState.value = null
                stateEngine.applyVolumeSpeedCandidate(
                    MbCanBinaryState.Unavailable(
                        reason = (availability as? MbCanAvailability.Unavailable)?.reason ?: "Unavailable"
                    )
                )
                return@withContext
            }
            val raw = MbCanEngineFacade.canGetAudioParam(MbCanKnownAudioPropertyId.VOLUME_SPEED)
            val decoded = if (raw == null) {
                MbCanBinaryState.Unknown
            } else {
                MbCanSignalStateEngine.decodeVolumeSpeedMbCanRaw(raw)
            }
            _audioVolumeSpeedModeState.value = raw?.let(CarSettingsAudioDomain::decodeVolumeSpeedMbCan)
            stateEngine.applyVolumeSpeedCandidate(decoded)
            MbCanDiagnostics.log(
                "DEBUG",
                "refreshAudioVolumeSpeed raw=$raw mode=${_audioVolumeSpeedModeState.value} " +
                    "state=${_audioVolumeSpeedState.value}"
            )
        }
    }

    private suspend fun refreshAudioVolume() {
        withContext(stateApplyDispatcher) {
            if (!MbCanEngineFacade.isInitialized()) {
                _availability.value = MbCanEngineFacade.probeAvailability()
                _audioVolumeState.value = null
                return@withContext
            }

            val availability = MbCanEngineFacade.availability
            _availability.value = availability
            if (availability !is MbCanAvailability.Available) {
                MbCanDiagnostics.log("WARN", "refreshAudioVolume unavailable=$availability")
                _audioVolumeState.value = null
                return@withContext
            }
            val raw = MbCanEngineFacade.canGetAudioParam(MbCanKnownAudioPropertyId.VOLUME)
            applyAudioVolumeRaw(raw)
            MbCanDiagnostics.log(
                "DEBUG",
                "refreshAudioVolume raw=$raw state=${_audioVolumeState.value}"
            )
        }
    }

    private suspend fun refreshAudioAlertVolumes() {
        withContext(stateApplyDispatcher) {
            if (!MbCanEngineFacade.isInitialized() || MbCanEngineFacade.availability !is MbCanAvailability.Available) {
                _audioKeyToneVolume.value = null
                _audioRadarAlarmVolume.value = null
                return@withContext
            }
            _audioKeyToneVolume.value =
                MbCanEngineFacade.canGetAudioParam(MbCanKnownAudioPropertyId.VOLUME_KEY)?.takeIf { it in 0..3 }
            _audioRadarAlarmVolume.value =
                MbCanEngineFacade.canGetAudioParam(MbCanKnownAudioPropertyId.VOLUME_RADAR)?.takeIf { it in 1..3 }
        }
    }

    private suspend fun refreshAudioEqAndBalance() {
        withContext(stateApplyDispatcher) {
            if (!MbCanEngineFacade.isInitialized() || MbCanEngineFacade.availability !is MbCanAvailability.Available) {
                _audioEqMode.value = null
                _audioEqBass.value = null
                _audioEqMiddle.value = null
                _audioEqTreble.value = null
                _audioBalance.value = null
                _audioFader.value = null
                return@withContext
            }
            _audioEqMode.value = MbCanEngineFacade.canGetAudioParam(MbCanKnownAudioPropertyId.EQ_MODE)?.let(CarSettingsAudioDomain::decodeEqMode)
            _audioEqBass.value = MbCanEngineFacade.canGetAudioParam(MbCanKnownAudioPropertyId.EQ_BAND_BASS)?.let(CarSettingsAudioDomain::decodeEqBand)
            _audioEqMiddle.value = MbCanEngineFacade.canGetAudioParam(MbCanKnownAudioPropertyId.EQ_BAND_MIDDLE)?.let(CarSettingsAudioDomain::decodeEqBand)
            _audioEqTreble.value = MbCanEngineFacade.canGetAudioParam(MbCanKnownAudioPropertyId.EQ_BAND_TREBLE)?.let(CarSettingsAudioDomain::decodeEqBand)
            _audioBalance.value = MbCanEngineFacade.canGetAudioParam(MbCanKnownAudioPropertyId.BALANCE)?.let(CarSettingsAudioDomain::decodeBalanceFader)
            _audioFader.value = MbCanEngineFacade.canGetAudioParam(MbCanKnownAudioPropertyId.FADER)?.let(CarSettingsAudioDomain::decodeBalanceFader)
        }
    }

    private suspend fun refreshEngineRpm() {
        withContext(stateApplyDispatcher) {
            if (!MbCanEngineFacade.isInitialized()) {
                _availability.value = MbCanEngineFacade.probeAvailability()
                _engineRpmState.value = null
                return@withContext
            }
            val availability = MbCanEngineFacade.availability
            _availability.value = availability
            if (availability !is MbCanAvailability.Available) {
                _engineRpmState.value = null
                return@withContext
            }
            _engineRpmState.value = MbCanEngineFacade.readVehicleEngineRpm()?.coerceAtLeast(0f)
        }
    }

    private suspend fun refreshEngineTemperature() {
        withContext(stateApplyDispatcher) {
            if (!MbCanEngineFacade.isInitialized()) {
                _availability.value = MbCanEngineFacade.probeAvailability()
                _engineTemperatureState.value = null
                return@withContext
            }
            val availability = MbCanEngineFacade.availability
            _availability.value = availability
            if (availability !is MbCanAvailability.Available) {
                _engineTemperatureState.value = null
                return@withContext
            }
            _engineTemperatureState.value = MbCanEngineFacade.readVehicleEngineTemperature()
        }
    }

    private suspend fun refreshCarSpeed() {
        withContext(stateApplyDispatcher) {
            if (!MbCanEngineFacade.isInitialized()) {
                _availability.value = MbCanEngineFacade.probeAvailability()
                _carSpeedState.value = null
                return@withContext
            }
            val availability = MbCanEngineFacade.availability
            _availability.value = availability
            if (availability !is MbCanAvailability.Available) {
                _carSpeedState.value = null
                return@withContext
            }
            _carSpeedState.value = MbCanEngineFacade.readVehicleSpeed()?.coerceAtLeast(0f)
        }
    }

    private suspend fun refreshVehicleGear() {
        withContext(stateApplyDispatcher) {
            if (!MbCanEngineFacade.isInitialized()) {
                _availability.value = MbCanEngineFacade.probeAvailability()
                _gearBoxModeState.value = null
                return@withContext
            }
            val availability = MbCanEngineFacade.availability
            _availability.value = availability
            if (availability !is MbCanAvailability.Available) {
                _gearBoxModeState.value = null
                return@withContext
            }
            _gearBoxModeState.value = MbCanEngineFacade.readVehicleGearMode()
        }
    }

    private suspend fun refreshReverseGearSwitch() {
        withContext(stateApplyDispatcher) {
            if (!MbCanEngineFacade.isInitialized()) {
                _availability.value = MbCanEngineFacade.probeAvailability()
                _reverseGearSwitchState.value = null
                return@withContext
            }
            val availability = MbCanEngineFacade.availability
            _availability.value = availability
            if (availability !is MbCanAvailability.Available) {
                _reverseGearSwitchState.value = null
                return@withContext
            }
            _reverseGearSwitchState.value = MbCanEngineFacade.readReverseGearSwitch()
        }
    }

    private suspend fun refreshFuelLevel() {
        withContext(stateApplyDispatcher) {
            if (!MbCanEngineFacade.isInitialized()) {
                _availability.value = MbCanEngineFacade.probeAvailability()
                _fuelLevelPercentState.value = null
                return@withContext
            }
            val availability = MbCanEngineFacade.availability
            _availability.value = availability
            if (availability !is MbCanAvailability.Available) {
                _fuelLevelPercentState.value = null
                return@withContext
            }
            _fuelLevelPercentState.value = MbCanEngineFacade.readVehicleFuelLevelPercent()
            // Same FUELLEVEL entity also carries DTE; keep HU DTE fresh when % is polled.
            _distanceToFuelEmptyKmState.value = MbCanEngineFacade.readDistanceToFuelEmptyKm()
        }
    }

    private suspend fun refreshCurrentFuelConsumption() {
        withContext(stateApplyDispatcher) {
            if (!MbCanEngineFacade.isInitialized()) {
                _availability.value = MbCanEngineFacade.probeAvailability()
                _currentFuelConsumptionState.value = null
                return@withContext
            }
            val availability = MbCanEngineFacade.availability
            _availability.value = availability
            if (availability !is MbCanAvailability.Available) {
                _currentFuelConsumptionState.value = null
                return@withContext
            }
            _currentFuelConsumptionState.value = MbCanEngineFacade.readCurrentFuelConsumptionLPer100Km()
        }
    }

    private suspend fun refreshDistanceToNextMaintenance() {
        withContext(stateApplyDispatcher) {
            if (!MbCanEngineFacade.isInitialized()) {
                _availability.value = MbCanEngineFacade.probeAvailability()
                _distanceToNextMaintenanceKmState.value = null
                return@withContext
            }
            val availability = MbCanEngineFacade.availability
            _availability.value = availability
            if (availability !is MbCanAvailability.Available) {
                _distanceToNextMaintenanceKmState.value = null
                return@withContext
            }
            _distanceToNextMaintenanceKmState.value = MbCanEngineFacade.readDistanceToNextMaintenanceKm()
        }
    }

    private suspend fun refreshDistanceToFuelEmpty() {
        withContext(stateApplyDispatcher) {
            if (!MbCanEngineFacade.isInitialized()) {
                _availability.value = MbCanEngineFacade.probeAvailability()
                _distanceToFuelEmptyKmState.value = null
                return@withContext
            }
            val availability = MbCanEngineFacade.availability
            _availability.value = availability
            if (availability !is MbCanAvailability.Available) {
                _distanceToFuelEmptyKmState.value = null
                return@withContext
            }
            _distanceToFuelEmptyKmState.value = MbCanEngineFacade.readDistanceToFuelEmptyKm()
        }
    }

    private suspend fun refreshPm25AirQuality() {
        withContext(stateApplyDispatcher) {
            if (!MbCanEngineFacade.isInitialized()) {
                _availability.value = MbCanEngineFacade.probeAvailability()
                _insideAirQualityState.value = null
                _outsideAirQualityState.value = null
                return@withContext
            }
            val availability = MbCanEngineFacade.availability
            _availability.value = availability
            if (availability !is MbCanAvailability.Available) {
                _insideAirQualityState.value = null
                _outsideAirQualityState.value = null
                return@withContext
            }
            val snapshot = MbCanEngineFacade.readPm25AirQuality()
            _insideAirQualityState.value = snapshot?.inside
            _outsideAirQualityState.value = snapshot?.outside
        }
    }

    private suspend fun refreshSteeringAngle() {
        withContext(stateApplyDispatcher) {
            if (!MbCanEngineFacade.isInitialized()) {
                _availability.value = MbCanEngineFacade.probeAvailability()
                _steerAngleState.value = null
                _steerSpeedState.value = null
                return@withContext
            }
            val availability = MbCanEngineFacade.availability
            _availability.value = availability
            if (availability !is MbCanAvailability.Available) {
                _steerAngleState.value = null
                _steerSpeedState.value = null
                return@withContext
            }
            val snapshot = MbCanEngineFacade.readSteeringAngle()
            _steerAngleState.value = snapshot?.angleDeg
            _steerSpeedState.value = snapshot?.angleSpeed
        }
    }

    private suspend fun refreshTurnSignals() {
        withContext(stateApplyDispatcher) {
            if (!MbCanEngineFacade.isInitialized()) {
                _availability.value = MbCanEngineFacade.probeAvailability()
                _turnSignalsState.value = TurnSignalsState()
                return@withContext
            }
            val availability = MbCanEngineFacade.availability
            _availability.value = availability
            if (availability !is MbCanAvailability.Available) {
                _turnSignalsState.value = TurnSignalsState()
                return@withContext
            }
            _turnSignalsState.value =
                MbCanEngineFacade.readTurnSignals() ?: TurnSignalsState()
        }
    }

    private suspend fun refreshTotalOdometer() {
        withContext(stateApplyDispatcher) {
            if (!MbCanEngineFacade.isInitialized()) {
                _availability.value = MbCanEngineFacade.probeAvailability()
                _odometerKmState.value = null
                return@withContext
            }
            val availability = MbCanEngineFacade.availability
            _availability.value = availability
            if (availability !is MbCanAvailability.Available) {
                _odometerKmState.value = null
                return@withContext
            }
            _odometerKmState.value = MbCanEngineFacade.readTotalOdometerKm()
        }
    }

    private suspend fun refreshWheelPulse() {
        withContext(stateApplyDispatcher) {
            if (!MbCanEngineFacade.isInitialized()) {
                _availability.value = MbCanEngineFacade.probeAvailability()
                _wheelPulseState.value = null
                return@withContext
            }
            val availability = MbCanEngineFacade.availability
            _availability.value = availability
            if (availability !is MbCanAvailability.Available) {
                _wheelPulseState.value = null
                return@withContext
            }
            _wheelPulseState.value = MbCanEngineFacade.readVehicleWheelPulseCounters()
        }
    }

    private suspend fun refreshOutsideTemperature() {
        withContext(stateApplyDispatcher) {
            if (!MbCanEngineFacade.isInitialized()) {
                _availability.value = MbCanEngineFacade.probeAvailability()
                _outsideTemperatureState.value = null
                return@withContext
            }
            val availability = MbCanEngineFacade.availability
            _availability.value = availability
            if (availability !is MbCanAvailability.Available) {
                _outsideTemperatureState.value = null
                return@withContext
            }
            _outsideTemperatureState.value = MbCanEngineFacade.readOutsideTemperatureC()
        }
    }

    private suspend fun refreshVehicleTires() {
        withContext(stateApplyDispatcher) {
            if (!MbCanEngineFacade.isInitialized()) {
                _availability.value = MbCanEngineFacade.probeAvailability()
                _wheelsPressureState.value = Wheels()
                _wheelsTemperatureState.value = Wheels()
                return@withContext
            }
            val availability = MbCanEngineFacade.availability
            _availability.value = availability
            if (availability !is MbCanAvailability.Available) {
                _wheelsPressureState.value = Wheels()
                _wheelsTemperatureState.value = Wheels()
                return@withContext
            }
            val snapshot = MbCanEngineFacade.readVehicleTires()
            if (snapshot != null) {
                applyWheelsPressureWithDebounce(snapshot.pressure)
                _wheelsTemperatureState.value = snapshot.temperature
            }
        }
    }

    private fun applyAudioVolumeRaw(raw: Int?) {
        val safeValue = raw?.coerceAtLeast(0)
        val previous = _audioVolumeState.value
        if (safeValue != null && safeValue > 0) {
            _audioVolumeLastNonZeroInSession.value = safeValue
        } else if (safeValue == 0 && (previous ?: 0) > 0) {
            _audioVolumeLastNonZeroInSession.value = previous
        }
        _audioVolumeState.value = safeValue
    }

    fun rememberAudioVolumeLastNonZeroInSession(value: Int) {
        if (value > 0) {
            _audioVolumeLastNonZeroInSession.value = value
        }
    }

    fun audioVolumeRestoreCandidate(defaultValue: Int = 10): Int {
        return (_audioVolumeLastNonZeroInSession.value ?: defaultValue).coerceAtLeast(1)
    }

    suspend fun setAudioVolume(value: Int): MbCanCommandResult {
        ensureMbCanReadyIfNeeded()
        if (availability.value !is MbCanAvailability.Available) {
            return MbCanCommandResult(false, "mbCAN unavailable")
        }
        val target = value.coerceAtLeast(0)
        val before = _audioVolumeState.value ?: MbCanEngineFacade.canGetAudioParam(MbCanKnownAudioPropertyId.VOLUME)
        if (target == 0 && (before ?: 0) > 0) {
            _audioVolumeLastNonZeroInSession.value = before
        } else if (target > 0) {
            _audioVolumeLastNonZeroInSession.value = target
        }
        val setResult = MbCanEngineFacade.canSetAudioParam(MbCanKnownAudioPropertyId.VOLUME, target)
            ?: return MbCanCommandResult(false, "Set audio command failed")
        if (setResult >= 0) {
            applyAudioVolumeRaw(target)
            MbCanJobManager.requestBurst(MbCanSignal.AudioVolume)
        }
        return MbCanCommandResult(setResult >= 0, "Set result: $setResult")
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
        if (MbCanEngineFacade.isInitialized()) {
            // Heal orphaned JobManager types if init happened outside this path.
            MbCanJobManager.ensureOemSubscriptions()
            return
        }
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
        val needsCfgAudioListener = mergedSignals.any { signal ->
            signal.subscribeDataTypes.contains(CFG_AUDIO_DATA_TYPE)
        }
        val needsSettingsTelemetry = mergedSignals.contains(MbCanSignal.EngineRpm) ||
            mergedSignals.contains(MbCanSignal.EngineTemperature) ||
            mergedSignals.contains(MbCanSignal.CarSpeed) ||
            mergedSignals.contains(MbCanSignal.VehicleGear) ||
            mergedSignals.contains(MbCanSignal.ReverseGearSwitch) ||
            mergedSignals.contains(MbCanSignal.FuelLevel) ||
            mergedSignals.contains(MbCanSignal.TotalOdometer) ||
            mergedSignals.contains(MbCanSignal.OutsideTemperature) ||
            mergedSignals.contains(MbCanSignal.VehicleTires) ||
            mergedSignals.contains(MbCanSignal.CurrentFuelConsumption) ||
            mergedSignals.contains(MbCanSignal.DistanceToFuelEmpty) ||
            mergedSignals.contains(MbCanSignal.TrunkDoor)
        MbCanEngineFacade.syncVehicleCfgCmdListener(needsCfgVehicleListener)
        MbCanEngineFacade.syncAudioCfgCmdListener(needsCfgAudioListener)
        if (needsSettingsTelemetry) {
            MbCanEngineFacade.registerSettingsTelemetryBridge()
        } else {
            MbCanEngineFacade.unregisterSettingsTelemetryBridge()
        }
        val needsLkaSlaListener = mergedSignals.contains(MbCanSignal.SlaSpeedLimit)
        MbCanEngineFacade.syncLkaSlaStatusListener(needsLkaSlaListener)
        val needsFrmAccListener = mergedSignals.contains(MbCanSignal.AccCruise)
        MbCanEngineFacade.syncFrmDectInfoListener(needsFrmAccListener)
        val needsGaspedCcsListener = mergedSignals.contains(MbCanSignal.AccCruise)
        MbCanEngineFacade.syncGaspedStatusListener(needsGaspedCcsListener)
        val needsSteeringListener = mergedSignals.contains(MbCanSignal.SteeringAngle)
        val needsTurnSignalsListener = mergedSignals.contains(MbCanSignal.TurnSignals)
        val needsWheelPulseListener = mergedSignals.contains(MbCanSignal.WheelPulse)
        MbCanEngineFacade.syncImbVehicleListener(
            needSteer = needsSteeringListener,
            needTurnLights = needsTurnSignalsListener,
            needWheelPulse = needsWheelPulseListener,
        )
        // Listener bridges above may ensureInitialized() as a side effect; make sure
        // JobManager types (incl. STEERING_ANGLE / TURNLIGHT for A9 push) are actually subscribed.
        MbCanJobManager.ensureOemSubscriptions()
    }

    private fun widgetKeyToSignal(widgetKey: String): MbCanSignal? {
        return signalByWidgetKey[widgetKey]
    }

    /**
     * Whether any widget [dataKey] on a panel needs mbCAN (subscribe/refresh). Used so panels without
     * such widgets never call [setSourceWidgetKeys]/[enqueueClearSource].
     */
    fun widgetConfigsNeedMbCan(dataKeys: Iterable<String>): Boolean {
        return dataKeys.any { raw ->
            UniversalCanRepository.isMeaningfulWidgetDataKey(raw) &&
                widgetKeyToSignal(UniversalCanRepository.normalizeWidgetDataKey(raw)) != null
        }
    }

    private val carSettingsZeroToSixRange = 0..6

    private fun decodeCarSettingsIntZeroToSix(raw: Int): Int? =
        if (raw in carSettingsZeroToSixRange) raw else null

    private fun decodeAudioVolumeSpeedMode(raw: Int): Int? =
        CarSettingsAudioDomain.decodeVolumeSpeedMbCan(raw)

    private fun applyCarSettingsVehicleCfgPush(item: Int, raw: Int) {
        when (item) {
            MbCanKnownVehiclePropertyId.VEHICLE_PROPERTY_EPS_MODE ->
                _carSettingsEpsMode.value = decodeCarSettingsIntZeroToSix(raw)
            MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE ->
                _carSettingsDriveMode.value = decodeCarSettingsIntZeroToSix(raw)
            MbCanKnownVehiclePropertyId.DOOR_AUTO_LOCK -> _autoLockState.value =
                MbCanSignalStateEngine.decodeSteeringWheelHeatRaw(raw)
            MbCanKnownVehiclePropertyId.DOOR_IGNOFF_UNLOCK -> _autoUnlockState.value =
                MbCanSignalStateEngine.decodeSteeringWheelHeatRaw(raw)
            MbCanKnownVehiclePropertyId.HEADLIGHTS_HOMELIGHT_DELAY ->
                _followMeHomeMode.value = FollowMeHomeMode.fromMbCanRaw(raw)
            MbCanKnownVehiclePropertyId.DRIVER_UNLOCK_MODE -> _driverUnlockMode.value = raw.takeIf { it in 1..2 }
            MbCanKnownVehiclePropertyId.DEFENCES_PROMPT -> _remoteLockFeedback.value = raw.takeIf { it in 1..3 }
            MbCanKnownVehiclePropertyId.WIPER_SENSITIVITY -> _wiperSensitivity.value = raw.takeIf { it in 1..4 }
            MbCanKnownVehiclePropertyId.REAR_WIPER -> _rearWiperState.value =
                MbCanSignalStateEngine.decodeSteeringWheelHeatRaw(raw)
            MbCanKnownVehiclePropertyId.HIGHBEAM_ADJUST -> _lowBeamHeight.value = raw.takeIf { it in 1..4 }
            MbCanKnownVehiclePropertyId.TURN_FLASH_COUNT -> _turnFlashCount.value = raw.takeIf { it in 1..3 }
            MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE_6DCT_WET ->
                _carSettingsDriveMode6dctWet.value = decodeCarSettingsIntZeroToSix(raw)
        }
    }

    private fun clearCarSettingsIntParamFlows() {
        _carSettingsEpsMode.value = null
        _carSettingsDriveMode.value = null
        _carSettingsDriveMode6dctWet.value = null
    }

    private suspend fun refreshCarSettingsVehicleParams() {
        withContext(stateApplyDispatcher) {
            if (!MbCanEngineFacade.isInitialized()) {
                _availability.value = MbCanEngineFacade.probeAvailability()
                clearCarSettingsIntParamFlows()
                return@withContext
            }

            val availability = MbCanEngineFacade.availability
            _availability.value = availability
            if (availability !is MbCanAvailability.Available) {
                MbCanDiagnostics.log("WARN", "refreshCarSettingsVehicleParams unavailable=$availability")
                clearCarSettingsIntParamFlows()
                return@withContext
            }

            fun readInt(id: Int): Int? =
                MbCanEngineFacade.canGetVehicleParam(id)?.let { decodeCarSettingsIntZeroToSix(it) }

            _carSettingsEpsMode.value = readInt(MbCanKnownVehiclePropertyId.VEHICLE_PROPERTY_EPS_MODE)
            _carSettingsDriveMode.value = readInt(MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE)
            _carSettingsDriveMode6dctWet.value = readInt(MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE_6DCT_WET)
            MbCanDiagnostics.log("DEBUG", "refreshCarSettingsVehicleParams refreshed")
        }
    }

    private suspend fun refreshCertifiedCarSettingsSignal(signal: MbCanSignal) {
        withContext(stateApplyDispatcher) {
            if (!MbCanEngineFacade.isInitialized() || MbCanEngineFacade.availability !is MbCanAvailability.Available) {
                applyCertifiedCarSettingsRaw(signal, null)
                return@withContext
            }
            val propertyId = when (signal) {
                MbCanSignal.AutoLock -> MbCanKnownVehiclePropertyId.DOOR_AUTO_LOCK
                MbCanSignal.AutoUnlock -> MbCanKnownVehiclePropertyId.DOOR_IGNOFF_UNLOCK
                MbCanSignal.FollowMeHome -> MbCanKnownVehiclePropertyId.HEADLIGHTS_HOMELIGHT_DELAY
                MbCanSignal.DriverUnlockMode -> MbCanKnownVehiclePropertyId.DRIVER_UNLOCK_MODE
                MbCanSignal.RemoteLockFeedback -> MbCanKnownVehiclePropertyId.DEFENCES_PROMPT
                MbCanSignal.WiperSensitivity -> MbCanKnownVehiclePropertyId.WIPER_SENSITIVITY
                MbCanSignal.RearWiper -> MbCanKnownVehiclePropertyId.REAR_WIPER
            MbCanSignal.MirrorAutoFold -> MbCanKnownVehiclePropertyId.MIRROR_AUTOFOLD_SW
                MbCanSignal.LowBeamHeight -> MbCanKnownVehiclePropertyId.HIGHBEAM_ADJUST
                MbCanSignal.TurnFlashCount -> MbCanKnownVehiclePropertyId.TURN_FLASH_COUNT
                else -> return@withContext
            }
            applyCertifiedCarSettingsRaw(signal, MbCanEngineFacade.canGetVehicleParam(propertyId))
        }
    }

    private fun applyCertifiedCarSettingsRaw(signal: MbCanSignal, raw: Int?) {
        when (signal) {
            MbCanSignal.AutoLock -> _autoLockState.value = raw?.let(MbCanSignalStateEngine::decodeSteeringWheelHeatRaw) ?: MbCanBinaryState.Unknown
            MbCanSignal.AutoUnlock -> _autoUnlockState.value = raw?.let(MbCanSignalStateEngine::decodeSteeringWheelHeatRaw) ?: MbCanBinaryState.Unknown
            MbCanSignal.FollowMeHome -> _followMeHomeMode.value = raw?.let(FollowMeHomeMode::fromMbCanRaw)
            MbCanSignal.DriverUnlockMode -> _driverUnlockMode.value = raw?.takeIf { it in 1..2 }
            MbCanSignal.RemoteLockFeedback -> _remoteLockFeedback.value = raw?.takeIf { it in 1..3 }
            MbCanSignal.WiperSensitivity -> _wiperSensitivity.value = raw?.takeIf { it in 1..4 }
            MbCanSignal.RearWiper -> _rearWiperState.value = raw?.let(MbCanSignalStateEngine::decodeSteeringWheelHeatRaw) ?: MbCanBinaryState.Unknown
            MbCanSignal.MirrorAutoFold -> _mirrorAutoFoldState.value =
                raw?.let(MbCanSignalStateEngine::decodeSteeringWheelHeatRaw) ?: MbCanBinaryState.Unknown
            MbCanSignal.LowBeamHeight -> _lowBeamHeight.value = raw?.takeIf { it in 1..4 }
            MbCanSignal.TurnFlashCount -> _turnFlashCount.value = raw?.takeIf { it in 1..3 }
            else -> Unit
        }
    }

    private suspend fun refreshSlaSpeedLimit() {
        withContext(stateApplyDispatcher) {
            if (!MbCanEngineFacade.isInitialized()) {
                _availability.value = MbCanEngineFacade.probeAvailability()
                _slaRecognizedSpeedLimitKmh.value = null
                _slaOnOffState.value = MbCanBinaryState.Unknown
                slaLkaOnOffRaw = null
                slaLkaStateRaw = null
                slaLkaLimitRaw = null
                publishSlaSignUiState()
                return@withContext
            }
            val availability = MbCanEngineFacade.availability
            _availability.value = availability
            if (availability !is MbCanAvailability.Available) {
                _slaRecognizedSpeedLimitKmh.value = null
                _slaOnOffState.value = MbCanBinaryState.Unavailable(
                    (availability as? MbCanAvailability.Unavailable)?.reason ?: "Unavailable"
                )
                slaLkaOnOffRaw = null
                slaLkaStateRaw = null
                slaLkaLimitRaw = null
                publishSlaSignUiState()
                return@withContext
            }
            val onOffRaw = MbCanEngineFacade.canGetVehicleParam(MbCanKnownVehiclePropertyId.VEHICLE_TSR_SWITCH)
            _slaOnOffState.value = onOffRaw?.let(SlaSpeedLimitDomain::decodeSlaOnOffRaw) ?: MbCanBinaryState.Unknown
            // Sign UI (OnOff/State/Spdlimit) comes from LKA push only.
        }
    }

    private fun applySpeedLimiterSwitchRaw(raw: Int?) {
        _speedLimiterSwitchRaw.value = raw
        _speedLimiterState.value = raw?.let(SlaSpeedLimitDomain::decodeSpeedLimiterSwitchRaw)
            ?: MbCanBinaryState.Unknown
    }

    private fun clearSpeedLimiterFlows(state: MbCanBinaryState = MbCanBinaryState.Unknown) {
        _speedLimiterState.value = state
        _speedLimiterSwitchRaw.value = null
        _speedLimiterValueSetRaw.value = null
    }

    private suspend fun refreshSpeedLimiter() {
        withContext(stateApplyDispatcher) {
            if (!MbCanEngineFacade.isInitialized()) {
                _availability.value = MbCanEngineFacade.probeAvailability()
                clearSpeedLimiterFlows(MbCanBinaryState.Unknown)
                return@withContext
            }
            val availability = MbCanEngineFacade.availability
            _availability.value = availability
            if (availability !is MbCanAvailability.Available) {
                clearSpeedLimiterFlows(
                    MbCanBinaryState.Unavailable(
                        (availability as? MbCanAvailability.Unavailable)?.reason ?: "Unavailable"
                    )
                )
                return@withContext
            }
            val switchRaw = MbCanEngineFacade.canGetVehicleParam(
                MbCanKnownVehiclePropertyId.VEHICLE_SPEEDLIMIT_SWITCH,
            )
            applySpeedLimiterSwitchRaw(switchRaw)
            _speedLimiterValueSetRaw.value = MbCanEngineFacade.canGetVehicleParam(
                MbCanKnownVehiclePropertyId.VEHICLE_SPEEDLIMIT_VALUESET,
            )
        }
    }

    private suspend fun refreshAccCruise() {
        withContext(stateApplyDispatcher) {
            if (!MbCanEngineFacade.isInitialized()) {
                _availability.value = MbCanEngineFacade.probeAvailability()
                _accCruiseMode.value = null
                _accCruiseVSetDisKmh.value = null
                _accFrmFeedbackAvailable.value = false
                _accModeEverNonZero.value = false
                _ccsCruiseStatus.value = null
                return@withContext
            }
            val availability = MbCanEngineFacade.availability
            _availability.value = availability
            if (availability !is MbCanAvailability.Available) {
                _accCruiseMode.value = null
                _accCruiseVSetDisKmh.value = null
                _accFrmFeedbackAvailable.value = false
                _accModeEverNonZero.value = false
                _ccsCruiseStatus.value = null
                return@withContext
            }
            // FRM ACCMode / VSetDis: push-only via registIMBVehicleFrmDectInfoListener.
            // CCS status: push-only via registIMBCanVehicleGaspedStatusListener.
        }
    }
}
