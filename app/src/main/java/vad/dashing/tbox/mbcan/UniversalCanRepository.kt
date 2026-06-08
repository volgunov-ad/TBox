package vad.dashing.tbox.mbcan

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import vad.dashing.tbox.HeadUnitCanMode

/**
 * One entry point for car-control/CAN behavior across HU platforms.
 *
 * Android 9 mode delegates to [MbCanRepository].
 * Android 10 mode delegates to [Android10VhalRepository] (VHAL via CarPropertyManager reflection).
 */
@OptIn(ExperimentalCoroutinesApi::class)
object UniversalCanRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var boundScope: CoroutineScope? = null

    private val _mode = MutableStateFlow(HeadUnitCanMode.Android9MbCan)
    val mode: StateFlow<HeadUnitCanMode> = _mode.asStateFlow()

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

    val engineRpmState: StateFlow<Float?> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.engineRpmState
            } else {
                Android10VhalRepository.engineRpmState
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    fun setMode(mode: HeadUnitCanMode) {
        if (_mode.value == mode) return
        _mode.value = mode
        boundScope?.let { scopeToRebind ->
            scope.launch {
                when (mode) {
                    HeadUnitCanMode.Android9MbCan -> {
                        Android10VhalRepository.unbind()
                        MbCanRepository.bind(scopeToRebind)
                    }
                    HeadUnitCanMode.Android10Vhal -> {
                        MbCanRepository.unbind()
                        Android10VhalRepository.bind(scopeToRebind)
                    }
                }
            }
        }
    }

    suspend fun bind(scope: CoroutineScope) {
        boundScope = scope
        if (_mode.value == HeadUnitCanMode.Android9MbCan) {
            MbCanRepository.bind(scope)
        } else {
            Android10VhalRepository.bind(scope)
        }
    }

    suspend fun unbind() {
        boundScope = null
        MbCanRepository.unbind()
        Android10VhalRepository.unbind()
    }

    suspend fun warmUpAvailabilityForUi() {
        if (_mode.value == HeadUnitCanMode.Android9MbCan) {
            MbCanRepository.warmUpAvailabilityForUi()
        } else {
            Android10VhalRepository.warmUpAvailabilityForUi()
        }
    }

    suspend fun setSourceWidgetKeys(sourceId: String, widgetKeys: Set<String>) {
        if (_mode.value == HeadUnitCanMode.Android9MbCan) {
            MbCanRepository.setSourceWidgetKeys(sourceId, widgetKeys)
        } else {
            Android10VhalRepository.setSourceWidgetKeys(sourceId, widgetKeys)
        }
    }

    suspend fun setSourceSignals(sourceId: String, signals: Set<MbCanSignal>) {
        if (_mode.value == HeadUnitCanMode.Android9MbCan) {
            MbCanRepository.setSourceSignals(sourceId, signals)
        } else {
            Android10VhalRepository.setSourceSignals(sourceId, signals)
        }
    }

    fun enqueueClearSource(sourceId: String) {
        if (_mode.value == HeadUnitCanMode.Android9MbCan) {
            MbCanRepository.enqueueClearSource(sourceId)
        } else {
            Android10VhalRepository.enqueueClearSource(sourceId)
        }
    }

    fun widgetConfigsNeedMbCan(dataKeys: Iterable<String>): Boolean {
        return if (_mode.value == HeadUnitCanMode.Android9MbCan) {
            MbCanRepository.widgetConfigsNeedMbCan(dataKeys)
        } else {
            Android10VhalRepository.widgetConfigsNeedMbCan(dataKeys)
        }
    }

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
}
