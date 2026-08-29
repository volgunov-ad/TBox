package vad.dashing.tbox.mbcan

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

suspend fun UniversalCanRepository.setHvacTempLeftCelsius(celsius: Float): MbCanCommandResult {
    val mbCanRaw = HvacClimateDomain.celsiusToMbCanTempRaw(celsius)
    return execute(MbCanCommand.SetProperty(MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_LEFT, mbCanRaw))
}

suspend fun UniversalCanRepository.setHvacTempRightCelsius(celsius: Float): MbCanCommandResult {
    val mbCanRaw = HvacClimateDomain.celsiusToMbCanTempRaw(celsius)
    return execute(MbCanCommand.SetProperty(MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_RIGHT, mbCanRaw))
}

suspend fun UniversalCanRepository.setHvacFanSpeed(level: Int): MbCanCommandResult {
    val clamped = level.coerceIn(HvacClimateDomain.FAN_SPEED_MIN, HvacClimateDomain.FAN_SPEED_MAX)
    return execute(MbCanCommand.SetProperty(MbCanKnownVehiclePropertyId.HVAC_FAN_SPEED, clamped))
}

suspend fun UniversalCanRepository.toggleHvacFrontOff(): MbCanCommandResult =
    execute(MbCanCommand.ToggleProperty(MbCanKnownVehiclePropertyId.HVAC_FRONT_OFF))

suspend fun UniversalCanRepository.toggleHvacSync(): MbCanCommandResult =
    execute(MbCanCommand.ToggleProperty(MbCanKnownVehiclePropertyId.HVAC_SYNC_SWITCH))

suspend fun UniversalCanRepository.setHvacBlowMode(mode: HvacBlowMode): MbCanCommandResult =
    execute(MbCanCommand.SetProperty(MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION, mode.mbCanValue))

suspend fun UniversalCanRepository.cycleHvacBlowMode(): MbCanCommandResult {
    val next = HvacBlowMode.nextInCycle(HvacClimateCanRepository.hvacBlowMode.value)
    return setHvacBlowMode(next)
}

suspend fun UniversalCanRepository.setHvacBlowModeDefrost(): MbCanCommandResult =
    setHvacBlowMode(HvacBlowMode.Defrost)

suspend fun UniversalCanRepository.setHvacCustomMode(mode: HvacCustomMode): MbCanCommandResult =
    execute(MbCanCommand.SetProperty(MbCanKnownVehiclePropertyId.HVAC_CUSTOM, mode.mbCanValue))

suspend fun UniversalCanRepository.cycleHvacCustomMode(): MbCanCommandResult {
    val next = HvacCustomMode.nextInCycle(HvacClimateCanRepository.hvacCustomMode.value)
    return setHvacCustomMode(next)
}

private val hvacClimateCommandMutex = Mutex()

fun UniversalCanRepository.launchHvacClimateCommand(
    scope: CoroutineScope,
    block: suspend UniversalCanRepository.() -> MbCanCommandResult,
) {
    scope.launch {
        hvacClimateCommandMutex.withLock {
            block(UniversalCanRepository)
        }
    }
}

fun UniversalCanRepository.launchAdjustHvacFanSpeed(scope: CoroutineScope, increase: Boolean) {
    val next = HvacClimateCanRepository.advanceFanSpeed(increase)
    launchHvacClimateCommand(scope) { setHvacFanSpeed(next) }
}

fun UniversalCanRepository.launchAdjustHvacTempLeft(
    scope: CoroutineScope,
    increase: Boolean,
    stepTenths: Int = HvacClimateDomain.TEMP_MB_CAN_STEP,
) {
    val next = HvacClimateCanRepository.advanceTempLeft(increase, stepTenths)
    launchHvacClimateCommand(scope) { setHvacTempLeftCelsius(next) }
}

fun UniversalCanRepository.launchAdjustHvacTempRight(
    scope: CoroutineScope,
    increase: Boolean,
    stepTenths: Int = HvacClimateDomain.TEMP_MB_CAN_STEP,
) {
    val next = HvacClimateCanRepository.advanceTempRight(increase, stepTenths)
    launchHvacClimateCommand(scope) { setHvacTempRightCelsius(next) }
}
