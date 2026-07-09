package vad.dashing.tbox.mbcan

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared HVAC climate + trunk state updated by the active CAN backend
 * ([MbCanRepository] on Android 9, [Android10VhalRepository] on Android 10).
 */
object HvacClimateCanRepository {
    private val _hvacFrontOffState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val hvacFrontOffState: StateFlow<MbCanBinaryState> = _hvacFrontOffState.asStateFlow()

    private val _hvacTempLeftCelsius = MutableStateFlow<Float?>(null)
    val hvacTempLeftCelsius: StateFlow<Float?> = _hvacTempLeftCelsius.asStateFlow()

    private val _hvacTempRightCelsius = MutableStateFlow<Float?>(null)
    val hvacTempRightCelsius: StateFlow<Float?> = _hvacTempRightCelsius.asStateFlow()

    private val _hvacFanSpeed = MutableStateFlow<Int?>(null)
    val hvacFanSpeed: StateFlow<Int?> = _hvacFanSpeed.asStateFlow()

    private val _hvacSyncState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val hvacSyncState: StateFlow<MbCanBinaryState> = _hvacSyncState.asStateFlow()

    private val _hvacBlowMode = MutableStateFlow<HvacBlowMode?>(null)
    val hvacBlowMode: StateFlow<HvacBlowMode?> = _hvacBlowMode.asStateFlow()

    private val _trunkDoorState = MutableStateFlow<TrunkDoorState>(TrunkDoorState.Unknown)
    val trunkDoorState: StateFlow<TrunkDoorState> = _trunkDoorState.asStateFlow()

    private var trunkMoveDir: Int? = null
    private var trunkStatus: Int? = null
    private var trunkReadPlatform: TrunkDoorReadPlatform = TrunkDoorReadPlatform.Vhal

    fun clearAll() {
        _hvacFrontOffState.value = MbCanBinaryState.Unknown
        _hvacTempLeftCelsius.value = null
        _hvacTempRightCelsius.value = null
        _hvacFanSpeed.value = null
        _hvacSyncState.value = MbCanBinaryState.Unknown
        _hvacBlowMode.value = null
        trunkMoveDir = null
        trunkStatus = null
        _trunkDoorState.value = TrunkDoorState.Unknown
    }

    fun applyFrontOffMbCan(raw: Int) {
        _hvacFrontOffState.value = HvacClimateDomain.decodeHvacFrontOffMbCanRaw(raw)
    }

    fun applyFrontOffVhal(raw: Int) {
        _hvacFrontOffState.value = HvacClimateDomain.decodeHvacFrontOffVhalRaw(raw)
    }

    fun applyTempLeftMbCan(raw: Int) {
        _hvacTempLeftCelsius.value = HvacClimateDomain.mbCanTempRawToCelsius(raw)
    }

    fun applyTempLeftVhal(raw: Int) {
        _hvacTempLeftCelsius.value = HvacClimateDomain.vhalTempRawToCelsius(raw)
    }

    fun applyTempRightMbCan(raw: Int) {
        _hvacTempRightCelsius.value = HvacClimateDomain.mbCanTempRawToCelsius(raw)
    }

    fun applyTempRightVhal(raw: Int) {
        _hvacTempRightCelsius.value = HvacClimateDomain.vhalTempRawToCelsius(raw)
    }

    fun applyFanSpeed(raw: Int) {
        _hvacFanSpeed.value = raw.takeIf { it in HvacClimateDomain.FAN_SPEED_MIN..HvacClimateDomain.FAN_SPEED_MAX }
    }

    fun applySyncMbCan(raw: Int) {
        _hvacSyncState.value = HvacClimateDomain.decodeHvacSyncMbCanRaw(raw)
    }

    fun applySyncVhal(raw: Int) {
        _hvacSyncState.value = HvacClimateDomain.decodeHvacSyncVhalRaw(raw)
    }

    fun applyBlowModeMbCan(raw: Int) {
        _hvacBlowMode.value = HvacBlowMode.fromMbCanRaw(raw)
    }

    fun applyBlowModeVhal(raw: Int) {
        _hvacBlowMode.value = HvacBlowMode.fromVhalRaw(raw)
    }

    fun applyTrunkMoveDirMbCan(raw: Int) {
        trunkReadPlatform = TrunkDoorReadPlatform.MbCanBcm
        trunkMoveDir = raw
        publishTrunkState()
    }

    fun applyTrunkMoveDirVhal(raw: Int) {
        trunkReadPlatform = TrunkDoorReadPlatform.Vhal
        trunkMoveDir = raw
        publishTrunkState()
    }

    fun applyTrunkStatusVhal(raw: Int) {
        trunkReadPlatform = TrunkDoorReadPlatform.Vhal
        trunkStatus = raw
        publishTrunkState()
    }

    fun clearTrunkState() {
        trunkMoveDir = null
        trunkStatus = null
        _trunkDoorState.value = TrunkDoorState.Unknown
    }

    private fun publishTrunkState() {
        _trunkDoorState.value = HvacClimateDomain.resolveTrunkDoorState(
            moveDir = trunkMoveDir,
            status = trunkStatus,
            platform = trunkReadPlatform,
        )
    }
}
