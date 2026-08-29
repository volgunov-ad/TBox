package vad.dashing.tbox.mbcan

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import vad.dashing.tbox.HeadUnitCanMode
import vad.dashing.tbox.SettingsManager

/**
 * One entry point for car-control/CAN behavior across HU platforms.
 *
 * Android 9 mode delegates to [MbCanRepository].
 * Android 10 mode delegates to [Android10VhalRepository] (VHAL via CarPropertyManager reflection).
 */
@OptIn(ExperimentalCoroutinesApi::class)
object UniversalCanRepository {
    private const val AUTO_BIND_ATTEMPTS_PER_MODE = 3
    private const val AUTO_BIND_ATTEMPT_TIMEOUT_MS = 3_500L
    private const val AUTO_BIND_ATTEMPT_PAUSE_MS = 1_200L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var boundScope: CoroutineScope? = null
    private val modeSwitchMutex = Mutex()
    private val sourceWidgetKeys = ConcurrentHashMap<String, Set<String>>()
    private val sourceSignals = ConcurrentHashMap<String, Set<MbCanSignal>>()

    private val _mode = MutableStateFlow(HeadUnitCanMode.Android9MbCan)
    val mode: StateFlow<HeadUnitCanMode> = _mode.asStateFlow()

    /**
     * Null-debounce for HU (mbCAN/VHAL) tire pressure — same durations as TBox
     * ([TirePressureDomain.DEFAULT_PRESSURE_NULL_DEBOUNCE_MS] /
     * [TirePressureDomain.PERSIST_PRESSURE_NULL_DEBOUNCE_MS]).
     * Updated from [vad.dashing.tbox.BackgroundService] when
     * `wheelPressurePersistAcrossStops` changes.
     */
    @Volatile
    var wheelPressureNullDebounceMs: Long = TirePressureDomain.DEFAULT_PRESSURE_NULL_DEBOUNCE_MS
        private set

    fun setWheelPressureNullDebounceMs(ms: Long) {
        wheelPressureNullDebounceMs = ms.coerceAtLeast(0L)
    }

    /** Disk restore for HU tire pressure (HU-only AppData keys `wheel*_pressure_last_hu`). */
    fun restoreWheelsPressureFromSaved(saved: vad.dashing.tbox.Wheels) {
        MbCanRepository.restoreWheelsPressureFromSaved(saved)
        Android10VhalRepository.restoreWheelsPressureFromSaved(saved)
    }

    val availability: StateFlow<MbCanAvailability> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.availability
            } else {
                Android10VhalRepository.availability
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, MbCanAvailability.Unknown)

    val steeringWheelHeatState: StateFlow<MbCanBinaryState> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.steeringWheelHeatState
            } else {
                Android10VhalRepository.steeringWheelHeatState
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, MbCanBinaryState.Unknown)

    val wiperMaintenanceState: StateFlow<MbCanBinaryState> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.wiperMaintenanceState
            } else {
                Android10VhalRepository.wiperMaintenanceState
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, MbCanBinaryState.Unknown)

    val parkingRadarState: StateFlow<MbCanBinaryState> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.parkingRadarState
            } else {
                Android10VhalRepository.parkingRadarState
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, MbCanBinaryState.Unknown)

    val rearFogState: StateFlow<MbCanBinaryState> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.rearFogState
            } else {
                Android10VhalRepository.rearFogState
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, MbCanBinaryState.Unknown)

    val autoLockState: StateFlow<MbCanBinaryState> = mode.flatMapLatest {
        if (it == HeadUnitCanMode.Android9MbCan) MbCanRepository.autoLockState else Android10VhalRepository.autoLockState
    }.stateIn(scope, SharingStarted.Eagerly, MbCanBinaryState.Unknown)
    val autoUnlockState: StateFlow<MbCanBinaryState> = mode.flatMapLatest {
        if (it == HeadUnitCanMode.Android9MbCan) MbCanRepository.autoUnlockState else Android10VhalRepository.autoUnlockState
    }.stateIn(scope, SharingStarted.Eagerly, MbCanBinaryState.Unknown)
    val rearWiperState: StateFlow<MbCanBinaryState> = mode.flatMapLatest {
        if (it == HeadUnitCanMode.Android9MbCan) MbCanRepository.rearWiperState else Android10VhalRepository.rearWiperState
    }.stateIn(scope, SharingStarted.Eagerly, MbCanBinaryState.Unknown)
    val mirrorAutoFoldState: StateFlow<MbCanBinaryState> = mode.flatMapLatest {
        if (it == HeadUnitCanMode.Android9MbCan) MbCanRepository.mirrorAutoFoldState else Android10VhalRepository.mirrorAutoFoldState
    }.stateIn(scope, SharingStarted.Eagerly, MbCanBinaryState.Unknown)
    val followMeHomeMode: StateFlow<FollowMeHomeMode?> = mode.flatMapLatest {
        if (it == HeadUnitCanMode.Android9MbCan) MbCanRepository.followMeHomeMode else Android10VhalRepository.followMeHomeMode
    }.stateIn(scope, SharingStarted.Eagerly, null)
    val driverUnlockMode: StateFlow<Int?> = mode.flatMapLatest {
        if (it == HeadUnitCanMode.Android9MbCan) MbCanRepository.driverUnlockMode else Android10VhalRepository.driverUnlockMode
    }.stateIn(scope, SharingStarted.Eagerly, null)
    val remoteLockFeedback: StateFlow<Int?> = mode.flatMapLatest {
        if (it == HeadUnitCanMode.Android9MbCan) MbCanRepository.remoteLockFeedback else Android10VhalRepository.remoteLockFeedback
    }.stateIn(scope, SharingStarted.Eagerly, null)
    val wiperSensitivity: StateFlow<Int?> = mode.flatMapLatest {
        if (it == HeadUnitCanMode.Android9MbCan) MbCanRepository.wiperSensitivity else Android10VhalRepository.wiperSensitivity
    }.stateIn(scope, SharingStarted.Eagerly, null)
    val lowBeamHeight: StateFlow<Int?> = mode.flatMapLatest {
        if (it == HeadUnitCanMode.Android9MbCan) MbCanRepository.lowBeamHeight else Android10VhalRepository.lowBeamHeight
    }.stateIn(scope, SharingStarted.Eagerly, null)
    val turnFlashCount: StateFlow<Int?> = mode.flatMapLatest {
        if (it == HeadUnitCanMode.Android9MbCan) MbCanRepository.turnFlashCount else Android10VhalRepository.turnFlashCount
    }.stateIn(scope, SharingStarted.Eagerly, null)

    val avhState: StateFlow<MbCanBinaryState> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.avhState
            } else {
                Android10VhalRepository.avhState
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, MbCanBinaryState.Unknown)

    val hdcState: StateFlow<MbCanBinaryState> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.hdcState
            } else {
                Android10VhalRepository.hdcState
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, MbCanBinaryState.Unknown)

    val espOffState: StateFlow<MbCanBinaryState> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.espOffState
            } else {
                Android10VhalRepository.espOffState
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, MbCanBinaryState.Unknown)

    val lasModeRaw: StateFlow<Int?> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.lasModeRaw
            } else {
                Android10VhalRepository.lasModeRaw
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val headlightModeRaw: StateFlow<Int?> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.headlightModeRaw
            } else {
                Android10VhalRepository.headlightModeRaw
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val tjaIcaState: StateFlow<MbCanBinaryState> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.tjaIcaState
            } else {
                Android10VhalRepository.tjaIcaState
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, MbCanBinaryState.Unknown)

    val hmaState: StateFlow<MbCanBinaryState> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.hmaState
            } else {
                Android10VhalRepository.hmaState
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, MbCanBinaryState.Unknown)
    val bsdState: StateFlow<MbCanBinaryState> = mode.flatMapLatest {
        if (it == HeadUnitCanMode.Android9MbCan) MbCanRepository.bsdState else Android10VhalRepository.bsdState
    }.stateIn(scope, SharingStarted.Eagerly, MbCanBinaryState.Unknown)
    val dowState: StateFlow<MbCanBinaryState> = mode.flatMapLatest {
        if (it == HeadUnitCanMode.Android9MbCan) MbCanRepository.dowState else Android10VhalRepository.dowState
    }.stateIn(scope, SharingStarted.Eagerly, MbCanBinaryState.Unknown)
    val fcwState: StateFlow<MbCanBinaryState> = mode.flatMapLatest {
        if (it == HeadUnitCanMode.Android9MbCan) MbCanRepository.fcwState else Android10VhalRepository.fcwState
    }.stateIn(scope, SharingStarted.Eagerly, MbCanBinaryState.Unknown)
    val fcwSensitivity: StateFlow<FcwSensitivity?> = mode.flatMapLatest {
        if (it == HeadUnitCanMode.Android9MbCan) MbCanRepository.fcwSensitivity else Android10VhalRepository.fcwSensitivity
    }.stateIn(scope, SharingStarted.Eagerly, null)
    val ldwSensitivity: StateFlow<LdwSensitivity?> = mode.flatMapLatest {
        if (it == HeadUnitCanMode.Android9MbCan) MbCanRepository.ldwSensitivity else Android10VhalRepository.ldwSensitivity
    }.stateIn(scope, SharingStarted.Eagerly, null)

    val hvacAcMaxState: StateFlow<MbCanBinaryState> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.hvacAcMaxState
            } else {
                Android10VhalRepository.hvacAcMaxState
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, MbCanBinaryState.Unknown)

    val frontWindscreenHeatState: StateFlow<MbCanBinaryState> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.frontWindscreenHeatState
            } else {
                Android10VhalRepository.frontWindscreenHeatState
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, MbCanBinaryState.Unknown)

    val hvacDefrosterState: StateFlow<MbCanBinaryState> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.hvacDefrosterState
            } else {
                Android10VhalRepository.hvacDefrosterState
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, MbCanBinaryState.Unknown)

    val hvacAirRecirculationState: StateFlow<MbCanBinaryState> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.hvacAirRecirculationState
            } else {
                Android10VhalRepository.hvacAirRecirculationState
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, MbCanBinaryState.Unknown)

    val hvacAcPowerState: StateFlow<MbCanBinaryState> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.hvacAcPowerState
            } else {
                Android10VhalRepository.hvacAcPowerState
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, MbCanBinaryState.Unknown)

    val hvacAcCleanWhenLockedState: StateFlow<MbCanBinaryState> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.hvacAcCleanWhenLockedState
            } else {
                Android10VhalRepository.hvacAcCleanWhenLockedState
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, MbCanBinaryState.Unknown)

    val hvacAutoState: StateFlow<MbCanBinaryState> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.hvacAutoState
            } else {
                Android10VhalRepository.hvacAutoState
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, MbCanBinaryState.Unknown)
    val hvacAnionPurifyState: StateFlow<MbCanBinaryState> = mode.flatMapLatest {
        if (it == HeadUnitCanMode.Android9MbCan) MbCanRepository.hvacAnionPurifyState else Android10VhalRepository.hvacAnionPurifyState
    }.stateIn(scope, SharingStarted.Eagerly, MbCanBinaryState.Unknown)
    val fragranceSwitchState: StateFlow<MbCanBinaryState> = mode.flatMapLatest {
        if (it == HeadUnitCanMode.Android9MbCan) MbCanRepository.fragranceSwitchState
        else flowOf(MbCanBinaryState.Unavailable("Fragrance is available on Android 9 mbCAN only"))
    }.stateIn(scope, SharingStarted.Eagerly, MbCanBinaryState.Unknown)
    val fragranceSmell: StateFlow<Int?> = mode.flatMapLatest {
        if (it == HeadUnitCanMode.Android9MbCan) MbCanRepository.fragranceSmell else flowOf(null)
    }.stateIn(scope, SharingStarted.Eagerly, null)
    val fragranceConcentration: StateFlow<Int?> = mode.flatMapLatest {
        if (it == HeadUnitCanMode.Android9MbCan) MbCanRepository.fragranceConcentration else flowOf(null)
    }.stateIn(scope, SharingStarted.Eagerly, null)
    val firstBlowingState: StateFlow<MbCanBinaryState> = mode.flatMapLatest {
        if (it == HeadUnitCanMode.Android9MbCan) MbCanRepository.firstBlowingState else Android10VhalRepository.firstBlowingState
    }.stateIn(scope, SharingStarted.Eagerly, MbCanBinaryState.Unknown)
    val btReduceFanState: StateFlow<MbCanBinaryState> = mode.flatMapLatest {
        if (it == HeadUnitCanMode.Android9MbCan) MbCanRepository.btReduceFanState else Android10VhalRepository.btReduceFanState
    }.stateIn(scope, SharingStarted.Eagerly, MbCanBinaryState.Unknown)
    val autoVentilationState: StateFlow<MbCanBinaryState> = mode.flatMapLatest {
        if (it == HeadUnitCanMode.Android9MbCan) MbCanRepository.autoVentilationState else Android10VhalRepository.autoVentilationState
    }.stateIn(scope, SharingStarted.Eagerly, MbCanBinaryState.Unknown)

    val hvacDefrosterFrontState: StateFlow<MbCanBinaryState> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.hvacDefrosterFrontState
            } else {
                Android10VhalRepository.hvacDefrosterFrontState
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, MbCanBinaryState.Unknown)

    val frontLeftSeatModeState: StateFlow<MbCanSeatModeState> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.frontLeftSeatModeState
            } else {
                Android10VhalRepository.frontLeftSeatModeState
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, MbCanSeatModeState.Unknown)

    val frontRightSeatModeState: StateFlow<MbCanSeatModeState> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.frontRightSeatModeState
            } else {
                Android10VhalRepository.frontRightSeatModeState
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, MbCanSeatModeState.Unknown)

    val rearLeftSeatModeState: StateFlow<MbCanSeatModeState> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.rearLeftSeatModeState
            } else {
                Android10VhalRepository.rearLeftSeatModeState
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, MbCanSeatModeState.Unknown)

    val rearRightSeatModeState: StateFlow<MbCanSeatModeState> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.rearRightSeatModeState
            } else {
                Android10VhalRepository.rearRightSeatModeState
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, MbCanSeatModeState.Unknown)

    val audioVolumeState: StateFlow<Int?> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.audioVolumeState
            } else {
                Android10VhalRepository.audioVolumeState
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val audioVolumeSpeedState: StateFlow<MbCanBinaryState> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.audioVolumeSpeedState
            } else {
                Android10VhalRepository.audioVolumeSpeedState
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, MbCanBinaryState.Unknown)

    val audioVolumeSpeedModeState: StateFlow<Int?> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.audioVolumeSpeedModeState
            } else {
                Android10VhalRepository.audioVolumeSpeedModeState
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** Key tone, radar alarm, EQ, and sound-field controls are verified for Android 9 mbCAN only. */
    val audioKeyToneVolume: StateFlow<Int?> = mode.flatMapLatest {
        if (it == HeadUnitCanMode.Android9MbCan) MbCanRepository.audioKeyToneVolume else flowOf(null)
    }.stateIn(scope, SharingStarted.Eagerly, null)
    val audioRadarAlarmVolume: StateFlow<Int?> = mode.flatMapLatest {
        if (it == HeadUnitCanMode.Android9MbCan) MbCanRepository.audioRadarAlarmVolume else flowOf(null)
    }.stateIn(scope, SharingStarted.Eagerly, null)
    val audioEqMode: StateFlow<Int?> = mode.flatMapLatest {
        if (it == HeadUnitCanMode.Android9MbCan) MbCanRepository.audioEqMode else flowOf(null)
    }.stateIn(scope, SharingStarted.Eagerly, null)
    val audioEqBass: StateFlow<Int?> = mode.flatMapLatest {
        if (it == HeadUnitCanMode.Android9MbCan) MbCanRepository.audioEqBass else flowOf(null)
    }.stateIn(scope, SharingStarted.Eagerly, null)
    val audioEqMiddle: StateFlow<Int?> = mode.flatMapLatest {
        if (it == HeadUnitCanMode.Android9MbCan) MbCanRepository.audioEqMiddle else flowOf(null)
    }.stateIn(scope, SharingStarted.Eagerly, null)
    val audioEqTreble: StateFlow<Int?> = mode.flatMapLatest {
        if (it == HeadUnitCanMode.Android9MbCan) MbCanRepository.audioEqTreble else flowOf(null)
    }.stateIn(scope, SharingStarted.Eagerly, null)
    val audioBalance: StateFlow<Int?> = mode.flatMapLatest {
        if (it == HeadUnitCanMode.Android9MbCan) MbCanRepository.audioBalance else flowOf(null)
    }.stateIn(scope, SharingStarted.Eagerly, null)
    val audioFader: StateFlow<Int?> = mode.flatMapLatest {
        if (it == HeadUnitCanMode.Android9MbCan) MbCanRepository.audioFader else flowOf(null)
    }.stateIn(scope, SharingStarted.Eagerly, null)

    val carSettingsEpsMode: StateFlow<Int?> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.carSettingsEpsMode
            } else {
                Android10VhalRepository.carSettingsEpsMode
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val carSettingsDriveMode: StateFlow<Int?> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.carSettingsDriveMode
            } else {
                Android10VhalRepository.carSettingsDriveMode
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val carSettingsDriveMode6dctWet: StateFlow<Int?> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.carSettingsDriveMode6dctWet
            } else {
                Android10VhalRepository.carSettingsDriveMode6dctWet
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)
    val hudSwitchState: StateFlow<MbCanBinaryState> = mode.flatMapLatest {
        if (it == HeadUnitCanMode.Android9MbCan) MbCanRepository.hudSwitchState else Android10VhalRepository.hudSwitchState
    }.stateIn(scope, SharingStarted.Eagerly, MbCanBinaryState.Unknown)
    val hudHeight: StateFlow<Int?> = mode.flatMapLatest {
        if (it == HeadUnitCanMode.Android9MbCan) MbCanRepository.hudHeight else Android10VhalRepository.hudHeight
    }.stateIn(scope, SharingStarted.Eagerly, null)
    val hudBrightness: StateFlow<Int?> = mode.flatMapLatest {
        if (it == HeadUnitCanMode.Android9MbCan) MbCanRepository.hudBrightness else Android10VhalRepository.hudBrightness
    }.stateIn(scope, SharingStarted.Eagerly, null)
    val hudDisplayMode: StateFlow<Int?> = mode.flatMapLatest {
        if (it == HeadUnitCanMode.Android9MbCan) MbCanRepository.hudDisplayMode else Android10VhalRepository.hudDisplayMode
    }.stateIn(scope, SharingStarted.Eagerly, null)
    val hudAutoBrightnessState: StateFlow<MbCanBinaryState> = mode.flatMapLatest {
        if (it == HeadUnitCanMode.Android9MbCan) MbCanRepository.hudAutoBrightnessState else Android10VhalRepository.hudAutoBrightnessState
    }.stateIn(scope, SharingStarted.Eagerly, MbCanBinaryState.Unknown)
    val icmBrightnessMode: StateFlow<Int?> = mode.flatMapLatest {
        if (it == HeadUnitCanMode.Android9MbCan) MbCanRepository.icmBrightnessMode else Android10VhalRepository.icmBrightnessMode
    }.stateIn(scope, SharingStarted.Eagerly, null)
    val icmManualBrightness: StateFlow<Int?> = mode.flatMapLatest {
        if (it == HeadUnitCanMode.Android9MbCan) MbCanRepository.icmManualBrightness else Android10VhalRepository.icmManualBrightness
    }.stateIn(scope, SharingStarted.Eagerly, null)
    val overspeedAlarmKmh: StateFlow<Int?> = mode.flatMapLatest {
        if (it == HeadUnitCanMode.Android9MbCan) MbCanRepository.overspeedAlarmKmh else Android10VhalRepository.overspeedAlarmKmh
    }.stateIn(scope, SharingStarted.Eagerly, null)

    val slaRecognizedSpeedLimitKmh: StateFlow<Int?> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.slaRecognizedSpeedLimitKmh
            } else {
                Android10VhalRepository.slaRecognizedSpeedLimitKmh
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val slaSignUiState: StateFlow<SlaSignUiState> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.slaSignUiState
            } else {
                Android10VhalRepository.slaSignUiState
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, SlaSignUiState.Inactive)

    val slaOnOffState: StateFlow<MbCanBinaryState> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.slaOnOffState
            } else {
                Android10VhalRepository.slaOnOffState
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, MbCanBinaryState.Unknown)

    val speedLimiterState: StateFlow<MbCanBinaryState> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.speedLimiterState
            } else {
                Android10VhalRepository.speedLimiterState
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, MbCanBinaryState.Unknown)

    val speedLimiterSwitchRaw: StateFlow<Int?> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.speedLimiterSwitchRaw
            } else {
                Android10VhalRepository.speedLimiterSwitchRaw
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val speedLimiterValueSetRaw: StateFlow<Int?> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.speedLimiterValueSetRaw
            } else {
                Android10VhalRepository.speedLimiterValueSetRaw
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val accCruiseMode: StateFlow<Int?> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.accCruiseMode
            } else {
                Android10VhalRepository.accCruiseMode
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val accCruiseVSetDisKmh: StateFlow<Int?> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.accCruiseVSetDisKmh
            } else {
                Android10VhalRepository.accCruiseVSetDisKmh
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** True once FRM ACC push was observed (A9) or AccCruise VHAL pull/push ran (A10). */
    val accFrmFeedbackAvailable: StateFlow<Boolean> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.accFrmFeedbackAvailable
            } else {
                Android10VhalRepository.accFrmFeedbackAvailable
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /** Sticky: non-zero ACCMode seen this bind session (AUTO prefers ACC after first ACC use). */
    val accModeEverNonZero: StateFlow<Boolean> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.accModeEverNonZero
            } else {
                Android10VhalRepository.accModeEverNonZero
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /** Conventional CCS: Gasped `nCruiseControlStatus` (A9) / EMS CruiseControlStatus (A10). */
    val ccsCruiseStatus: StateFlow<Int?> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.ccsCruiseStatus
            } else {
                Android10VhalRepository.ccsCruiseStatus
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val engineRpmState: StateFlow<Float?> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.engineRpmState
            } else {
                Android10VhalRepository.engineRpmState
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val engineTemperatureState: StateFlow<Float?> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.engineTemperatureState
            } else {
                Android10VhalRepository.engineTemperatureState
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val carSpeedState: StateFlow<Float?> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.carSpeedState
            } else {
                Android10VhalRepository.carSpeedState
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val gearBoxModeState: StateFlow<String?> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.gearBoxModeState
            } else {
                Android10VhalRepository.gearBoxModeState
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val accStatusState: StateFlow<String?> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.accStatusState
            } else {
                Android10VhalRepository.accStatusState
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val gasPedalPercentState: StateFlow<Float?> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.gasPedalPercentState
            } else {
                Android10VhalRepository.gasPedalPercentState
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val brakePedalPressedState: StateFlow<Boolean?> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.brakePedalPressedState
            } else {
                Android10VhalRepository.brakePedalPressedState
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val wiperOperatingModeState: StateFlow<WiperOperatingMode?> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.wiperOperatingModeState
            } else {
                Android10VhalRepository.wiperOperatingModeState
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** CEM reverse gear switch; for mock-location / DR consumers. */
    val reverseGearSwitchState: StateFlow<Boolean?> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.reverseGearSwitchState
            } else {
                Android10VhalRepository.reverseGearSwitchState
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val fuelLevelPercentState: StateFlow<UInt?> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.fuelLevelPercentState
            } else {
                Android10VhalRepository.fuelLevelPercentState
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val odometerKmState: StateFlow<UInt?> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.odometerKmState
            } else {
                Android10VhalRepository.odometerKmState
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val wheelPulseState: StateFlow<vad.dashing.tbox.vehicle.WheelCounters?> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.wheelPulseState
            } else {
                Android10VhalRepository.wheelPulseState
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val outsideTemperatureState: StateFlow<Float?> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.outsideTemperatureState
            } else {
                Android10VhalRepository.outsideTemperatureState
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val wheelsPressureState: StateFlow<vad.dashing.tbox.Wheels> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.wheelsPressureState
            } else {
                Android10VhalRepository.wheelsPressureState
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, vad.dashing.tbox.Wheels())

    val wheelsTemperatureState: StateFlow<vad.dashing.tbox.Wheels> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.wheelsTemperatureState
            } else {
                Android10VhalRepository.wheelsTemperatureState
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, vad.dashing.tbox.Wheels())

    val currentFuelConsumptionState: StateFlow<Float?> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.currentFuelConsumptionState
            } else {
                Android10VhalRepository.currentFuelConsumptionState
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val distanceToNextMaintenanceKmState: StateFlow<UInt?> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.distanceToNextMaintenanceKmState
            } else {
                Android10VhalRepository.distanceToNextMaintenanceKmState
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val distanceToFuelEmptyKmState: StateFlow<UInt?> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.distanceToFuelEmptyKmState
            } else {
                Android10VhalRepository.distanceToFuelEmptyKmState
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val insideAirQualityState: StateFlow<UInt?> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.insideAirQualityState
            } else {
                Android10VhalRepository.insideAirQualityState
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val outsideAirQualityState: StateFlow<UInt?> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.outsideAirQualityState
            } else {
                Android10VhalRepository.outsideAirQualityState
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val steerAngleState: StateFlow<Float?> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.steerAngleState
            } else {
                Android10VhalRepository.steerAngleState
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val steerSpeedState: StateFlow<Float?> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.steerSpeedState
            } else {
                Android10VhalRepository.steerSpeedState
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** Left/right turn + hazard; raw HU sample (A9 blinks, A10 stalk). */
    val turnSignalsState: StateFlow<TurnSignalsState> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.turnSignalsState
            } else {
                Android10VhalRepository.turnSignalsState
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, TurnSignalsState())

    private val turnSignalsLatchRuntime = TurnSignalsLatchRuntime(
        elapsedRealtimeMs = { android.os.SystemClock.elapsedRealtime() },
    )

    /**
     * Latched L/R for any consumer (matcher, widgets, UI). Never hazard.
     * Hold 2.5 s after the last flash / stalk sample; opposite side or hazard
     * clears the other latch. Raw [turnSignalsState] stays unlatched.
     */
    val turnSignalsLatchedSide: StateFlow<TurnSignalSide?> = turnSignalsLatchRuntime.side

    /**
     * Comfort 3-blink vs intentional (≥4 flashes / held stalk). Outside the
     * matcher so Ordinary↔Rails resets do not clear it.
     */
    val turnSignalsIntent: StateFlow<TurnSignalIntentTracker.Snapshot> =
        turnSignalsLatchRuntime.intent

    /** Snapshot at the current elapsedRealtime; prefer this when polling. */
    fun latchedTurnSignalSide(): TurnSignalSide? {
        turnSignalsLatchRuntime.poll()
        return turnSignalsLatchRuntime.side.value
    }

    fun turnSignalIntentSnapshot(): TurnSignalIntentTracker.Snapshot {
        turnSignalsLatchRuntime.poll()
        return turnSignalsLatchRuntime.intent.value
    }

    /** Live thresholds from road-match tuning (latch hold / intentional stalk). */
    fun configureTurnSignalLatch(
        holdMs: Long,
        minFlashesForIntent: Int,
        continuousStalkMs: Long,
    ) {
        turnSignalsLatchRuntime.configure(
            holdMs = holdMs,
            minFlashesForIntent = minFlashesForIntent,
            continuousStalkMs = continuousStalkMs,
        )
    }

    init {
        scope.launch {
            var lastMode: HeadUnitCanMode? = null
            combine(mode, turnSignalsState) { activeMode, state -> activeMode to state }
                .collect { (activeMode, state) ->
                    if (lastMode != null && activeMode != lastMode) {
                        turnSignalsLatchRuntime.reset()
                    }
                    lastMode = activeMode
                    turnSignalsLatchRuntime.ingest(state)
                }
        }
        scope.launch {
            while (isActive) {
                delay(TurnSignalsLatchRuntime.POLL_MS)
                turnSignalsLatchRuntime.poll()
            }
        }
    }

    suspend fun setMode(mode: HeadUnitCanMode) {
        modeSwitchMutex.withLock {
            setModeLocked(mode, rebindIfBound = true)
        }
    }

    suspend fun bind(scope: CoroutineScope) {
        modeSwitchMutex.withLock {
            bindLocked(scope)
        }
    }

    suspend fun unbind() {
        modeSwitchMutex.withLock {
            unbindLocked()
        }
    }

    suspend fun warmUpAvailabilityForUi() {
        modeSwitchMutex.withLock {
            warmUpAvailabilityForUiLocked()
        }
    }

    suspend fun setSourceWidgetKeys(sourceId: String, widgetKeys: Set<String>) {
        modeSwitchMutex.withLock {
            if (widgetKeys.isEmpty()) sourceWidgetKeys.remove(sourceId)
            else sourceWidgetKeys[sourceId] = widgetKeys.toSet()
            if (_mode.value == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.setSourceWidgetKeys(sourceId, widgetKeys)
            } else {
                Android10VhalRepository.setSourceWidgetKeys(sourceId, widgetKeys)
            }
        }
    }

    suspend fun setSourceSignals(sourceId: String, signals: Set<MbCanSignal>) {
        modeSwitchMutex.withLock {
            if (signals.isEmpty()) sourceSignals.remove(sourceId)
            else sourceSignals[sourceId] = signals.toSet()
            if (_mode.value == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.setSourceSignals(sourceId, signals)
            } else {
                Android10VhalRepository.setSourceSignals(sourceId, signals)
            }
        }
    }

    fun enqueueClearSource(sourceId: String) {
        sourceWidgetKeys.remove(sourceId)
        sourceSignals.remove(sourceId)
        // Clear both: the source may have been registered before a runtime backend switch.
        run {
            MbCanRepository.enqueueClearSource(sourceId)
            Android10VhalRepository.enqueueClearSource(sourceId)
        }
    }

    fun widgetConfigsNeedMbCan(dataKeys: Iterable<String>): Boolean =
        MbCanWidgetSignalMap.panelNeedsCan(dataKeys)

    suspend fun execute(command: MbCanCommand): MbCanCommandResult {
        return if (_mode.value == HeadUnitCanMode.Android9MbCan) {
            MbCanRepository.execute(command)
        } else {
            Android10VhalRepository.execute(command)
        }
    }

    suspend fun setAudioVolume(value: Int): MbCanCommandResult {
        return if (_mode.value == HeadUnitCanMode.Android9MbCan) {
            MbCanRepository.setAudioVolume(value)
        } else {
            Android10VhalRepository.setAudioVolume(value)
        }
    }

    fun rememberAudioVolumeLastNonZeroInSession(value: Int) {
        if (_mode.value == HeadUnitCanMode.Android9MbCan) {
            MbCanRepository.rememberAudioVolumeLastNonZeroInSession(value)
        } else {
            Android10VhalRepository.rememberAudioVolumeLastNonZeroInSession(value)
        }
    }

    fun audioVolumeRestoreCandidate(defaultValue: Int = 10): Int {
        return if (_mode.value == HeadUnitCanMode.Android9MbCan) {
            MbCanRepository.audioVolumeRestoreCandidate(defaultValue)
        } else {
            Android10VhalRepository.audioVolumeRestoreCandidate(defaultValue)
        }
    }

    suspend fun setSlaRecognitionEnabled(on: Boolean): MbCanCommandResult {
        return execute(
            MbCanCommand.SetProperty(
                MbCanKnownVehiclePropertyId.VEHICLE_TSR_SWITCH,
                SlaSpeedLimitDomain.encodeSlaSwitchOn(on),
            )
        )
    }

    /**
     * Writes limiter target km/h (clamped). Prefer live CAN VALUESET for display;
     * DataStore mirror may still be updated by the widget for a future fallback.
     * @see MbCanKnownVehiclePropertyId.VEHICLE_SPEEDLIMIT_VALUESET
     */
    suspend fun setSpeedLimiterTargetKmh(kmh: Int): MbCanCommandResult {
        val clamped = SlaSpeedLimitDomain.clampLimiterTargetKmh(kmh)
        return execute(
            MbCanCommand.SetProperty(
                MbCanKnownVehiclePropertyId.VEHICLE_SPEEDLIMIT_VALUESET,
                clamped,
            )
        )
    }

    /**
     * Enables/disables vehicle speed limiter.
     * @see MbCanKnownVehiclePropertyId.VEHICLE_SPEEDLIMIT_SWITCH
     */
    suspend fun setSpeedLimiterEnabled(on: Boolean): MbCanCommandResult {
        return execute(
            MbCanCommand.SetProperty(
                MbCanKnownVehiclePropertyId.VEHICLE_SPEEDLIMIT_SWITCH,
                SlaSpeedLimitDomain.encodeSpeedLimiterSwitchOn(on),
            )
        )
    }

    /** Writes [targetKmh] (or bootstrap when null) then enables the limiter switch. */
    suspend fun enableSpeedLimiter(targetKmh: Int?): MbCanCommandResult {
        val resolved = SlaSpeedLimitDomain.resolveLimiterTargetOrBootstrap(targetKmh)
        setSpeedLimiterTargetKmh(resolved)
        return setSpeedLimiterEnabled(true)
    }

    suspend fun autoResolveModeOnStartup(
        settingsManager: SettingsManager,
        scope: CoroutineScope,
    ) {
        modeSwitchMutex.withLock {
            if (!settingsManager.canAutoBindEnabledFlow.first()) {
                MbCanDiagnostics.log("INFO", "AUTO_CAN startup skipped: disabled")
                return
            }
            if (settingsManager.canAutoBindLockedFlow.first()) {
                MbCanDiagnostics.log("INFO", "AUTO_CAN startup skipped: locked")
                return
            }
            val primaryMode = settingsManager.headUnitCanModeFlow.first()
            val alternativeMode = primaryMode.otherMode()
            settingsManager.saveCanAutoBindLastPrimaryMode(primaryMode)
            MbCanDiagnostics.log(
                "INFO",
                "AUTO_CAN startup begin primary=${primaryMode.storageValue} alternative=${alternativeMode.storageValue}"
            )

            val primaryResult = bindModeWithRetries(
                mode = primaryMode,
                scope = scope,
                attemptLabel = "primary"
            )
            if (primaryResult.success) {
                settingsManager.saveCanAutoBindLastResult(
                    "primary_ok:${primaryMode.storageValue}:attempt=${primaryResult.attempt}"
                )
                MbCanDiagnostics.log(
                    "INFO",
                    "AUTO_CAN primary success mode=${primaryMode.storageValue} attempt=${primaryResult.attempt}"
                )
                return
            }
            MbCanDiagnostics.log(
                "WARN",
                "AUTO_CAN primary failed mode=${primaryMode.storageValue} reason=${primaryResult.reason}"
            )

            setMode(alternativeMode)
            settingsManager.saveHeadUnitCanMode(alternativeMode)
            val alternativeResult = bindModeWithRetries(
                mode = alternativeMode,
                scope = scope,
                attemptLabel = "alternative"
            )
            if (alternativeResult.success) {
                settingsManager.saveCanAutoBindLastResult(
                    "alternative_ok:${alternativeMode.storageValue}:attempt=${alternativeResult.attempt}"
                )
                MbCanDiagnostics.log(
                    "INFO",
                    "AUTO_CAN alternative success mode=${alternativeMode.storageValue} attempt=${alternativeResult.attempt}"
                )
                return
            }
            MbCanDiagnostics.log(
                "WARN",
                "AUTO_CAN alternative failed mode=${alternativeMode.storageValue} reason=${alternativeResult.reason}"
            )

            setModeLocked(primaryMode, rebindIfBound = false)
            settingsManager.saveHeadUnitCanMode(primaryMode)
            settingsManager.saveCanAutoBindLocked(true)
            settingsManager.saveCanAutoBindLastResult(
                "locked_after_fail:${primaryMode.storageValue}|${alternativeMode.storageValue}"
            )
            bindLocked(scope)
            MbCanDiagnostics.log(
                "WARN",
                "AUTO_CAN locked after failed retries; reverted to ${primaryMode.storageValue}"
            )
        }
    }

    private suspend fun bindModeWithRetries(
        mode: HeadUnitCanMode,
        scope: CoroutineScope,
        attemptLabel: String,
    ): AutoBindAttemptResult {
        repeat(AUTO_BIND_ATTEMPTS_PER_MODE) { index ->
            val attempt = index + 1
            setModeLocked(mode, rebindIfBound = false)
            // First attempt: bind without tearing down; retries unbind then rebind.
            if (index > 0) {
                unbindLocked()
            }
            bindLocked(scope)
            val attemptResult = waitForAvailability(
                timeoutMs = AUTO_BIND_ATTEMPT_TIMEOUT_MS,
                onTimeoutProbe = { warmUpAvailabilityForUiLocked() }
            )
            MbCanDiagnostics.log(
                "INFO",
                "AUTO_CAN $attemptLabel attempt=$attempt/${AUTO_BIND_ATTEMPTS_PER_MODE} " +
                    "mode=${mode.storageValue} result=${attemptResult.summary}"
            )
            if (attemptResult.success) {
                return AutoBindAttemptResult(success = true, attempt = attempt, reason = null)
            }
            if (attempt < AUTO_BIND_ATTEMPTS_PER_MODE) {
                delay(AUTO_BIND_ATTEMPT_PAUSE_MS)
            }
        }
        return AutoBindAttemptResult(
            success = false,
            attempt = AUTO_BIND_ATTEMPTS_PER_MODE,
            reason = (availability.value as? MbCanAvailability.Unavailable)?.reason
                ?: "timeout_unknown"
        )
    }

    private suspend fun waitForAvailability(
        timeoutMs: Long,
        onTimeoutProbe: suspend () -> Unit,
    ): AvailabilityAttemptResult {
        val startedAt = System.currentTimeMillis()
        while ((System.currentTimeMillis() - startedAt) < timeoutMs) {
            when (val current = availability.value) {
                MbCanAvailability.Available -> return AvailabilityAttemptResult(
                    success = true,
                    summary = "available"
                )
                is MbCanAvailability.Unavailable -> return AvailabilityAttemptResult(
                    success = false,
                    summary = "unavailable:${current.reason}"
                )
                MbCanAvailability.Unknown -> {
                    // Continue polling for a definitive backend state within timeout window.
                }
            }
            delay(120L)
        }
        onTimeoutProbe()
        return when (val current = availability.value) {
            MbCanAvailability.Available -> AvailabilityAttemptResult(
                success = true,
                summary = "available_after_timeout_probe"
            )
            is MbCanAvailability.Unavailable -> AvailabilityAttemptResult(
                success = false,
                summary = "unavailable_reason:${current.reason}"
            )
            MbCanAvailability.Unknown -> AvailabilityAttemptResult(
                success = false,
                summary = "timeout_unknown"
            )
        }
    }

    private fun HeadUnitCanMode.otherMode(): HeadUnitCanMode {
        return if (this == HeadUnitCanMode.Android9MbCan) {
            HeadUnitCanMode.Android10Vhal
        } else {
            HeadUnitCanMode.Android9MbCan
        }
    }

    private data class AvailabilityAttemptResult(
        val success: Boolean,
        val summary: String,
    )

    private data class AutoBindAttemptResult(
        val success: Boolean,
        val attempt: Int,
        val reason: String?,
    )

    internal fun normalizeWidgetDataKey(raw: String): String = raw.trim()

    internal fun isMeaningfulWidgetDataKey(raw: String): Boolean {
        val key = normalizeWidgetDataKey(raw)
        return key.isNotBlank() && key != "null"
    }

    private suspend fun setModeLocked(mode: HeadUnitCanMode, rebindIfBound: Boolean) {
        if (_mode.value == mode) return
        _mode.value = mode
        if (!rebindIfBound) return
        val scopeToRebind = boundScope ?: return
        when (mode) {
            HeadUnitCanMode.Android9MbCan -> {
                Android10VhalRepository.unbind()
                MbCanRepository.bind(scopeToRebind)
                applyAllInterestsLocked(HeadUnitCanMode.Android9MbCan)
            }
            HeadUnitCanMode.Android10Vhal -> {
                MbCanRepository.unbind()
                Android10VhalRepository.bind(scopeToRebind)
                applyAllInterestsLocked(HeadUnitCanMode.Android10Vhal)
            }
        }
    }

    private suspend fun bindLocked(scope: CoroutineScope) {
        boundScope = scope
        if (_mode.value == HeadUnitCanMode.Android9MbCan) {
            MbCanRepository.bind(scope)
        } else {
            Android10VhalRepository.bind(scope)
        }
        applyAllInterestsLocked(_mode.value)
    }

    private suspend fun unbindLocked() {
        boundScope = null
        MbCanRepository.unbind()
        Android10VhalRepository.unbind()
    }

    private suspend fun applyAllInterestsLocked(mode: HeadUnitCanMode) {
        sourceWidgetKeys.forEach { (sourceId, keys) ->
            if (mode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.setSourceWidgetKeys(sourceId, keys)
            } else {
                Android10VhalRepository.setSourceWidgetKeys(sourceId, keys)
            }
        }
        sourceSignals.forEach { (sourceId, signals) ->
            if (mode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.setSourceSignals(sourceId, signals)
            } else {
                Android10VhalRepository.setSourceSignals(sourceId, signals)
            }
        }
    }

    private suspend fun warmUpAvailabilityForUiLocked() {
        if (_mode.value == HeadUnitCanMode.Android9MbCan) {
            MbCanRepository.warmUpAvailabilityForUi()
        } else {
            Android10VhalRepository.warmUpAvailabilityForUi()
        }
    }
}
