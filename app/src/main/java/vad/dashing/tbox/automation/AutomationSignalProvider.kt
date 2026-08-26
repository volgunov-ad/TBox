package vad.dashing.tbox.automation

import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import vad.dashing.tbox.CanDataRepository
import vad.dashing.tbox.TboxRepository
import vad.dashing.tbox.Wheels
import vad.dashing.tbox.mbcan.HvacClimateCanRepository
import vad.dashing.tbox.mbcan.MbCanAvailability
import vad.dashing.tbox.mbcan.MbCanBinaryState
import vad.dashing.tbox.mbcan.MbCanSeatModeState
import vad.dashing.tbox.mbcan.MbCanSignal
import vad.dashing.tbox.mbcan.UniversalCanRepository

class AutomationSignalProvider(
    private val scope: CoroutineScope,
    private val onSample: suspend (AutomationSignalSample) -> Unit,
) {
    private val jobs = mutableListOf<Job>()
    private var activeKeys: Set<AutomationSignalKey> = emptySet()

    suspend fun replaceInterests(keys: Set<AutomationSignalKey>) {
        if (keys == activeKeys) return
        jobs.forEach(Job::cancel)
        jobs.clear()
        activeKeys = keys

        val huSignals = keys
            .asSequence()
            .filter { it.source == AutomationSignalSource.HEAD_UNIT }
            .mapNotNull { huInterestFor(it.signal) }
            .toSet()
        if (huSignals.isEmpty()) {
            UniversalCanRepository.enqueueClearSource(SOURCE_ID)
        } else {
            UniversalCanRepository.setSourceSignals(SOURCE_ID, huSignals)
        }

        keys.forEach { key ->
            val flow = flowFor(key) ?: return@forEach
            jobs += scope.launch {
                try {
                    flow.collect { value ->
                        val sample = AutomationSignalSample(
                            key = key,
                            value = value,
                            observedAtElapsedMillis = SystemClock.elapsedRealtime(),
                        )
                        onSample(sample)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    TboxRepository.addLog(
                        "ERROR",
                        "Automation",
                        "Signal ${key.signal.storageKey}/${key.source.storageKey}: " +
                            (error.message ?: error.javaClass.simpleName),
                    )
                }
            }
        }
    }

    fun stop() {
        jobs.forEach(Job::cancel)
        jobs.clear()
        activeKeys = emptySet()
        UniversalCanRepository.enqueueClearSource(SOURCE_ID)
    }

    private fun flowFor(key: AutomationSignalKey): Flow<AutomationSignalValue>? =
        when (key.source) {
            AutomationSignalSource.TBOX -> tboxFlow(key.signal)?.withAvailability(
                TboxRepository.tboxConnected,
            )

            AutomationSignalSource.HEAD_UNIT -> headUnitFlow(key.signal)?.withAvailability(
                UniversalCanRepository.availability.map { it is MbCanAvailability.Available },
            )
        }

    private fun tboxFlow(signal: AutomationSignalId): Flow<AutomationSignalValue>? = when (signal) {
        AutomationSignalId.ENGINE_RPM -> CanDataRepository.engineRPM.numberFlow()
        AutomationSignalId.CAR_SPEED -> CanDataRepository.carSpeed.numberFlow()
        AutomationSignalId.ENGINE_TEMPERATURE -> CanDataRepository.engineTemperature.numberFlow()
        AutomationSignalId.OUTSIDE_TEMPERATURE -> CanDataRepository.outsideTemperature.numberFlow()
        AutomationSignalId.INSIDE_TEMPERATURE -> CanDataRepository.insideTemperature.numberFlow()
        AutomationSignalId.FUEL_LEVEL_PERCENT -> CanDataRepository.fuelLevelPercentage.uintNumberFlow()
        AutomationSignalId.ODOMETER_KM -> CanDataRepository.odometer.uintNumberFlow()
        AutomationSignalId.CURRENT_FUEL_CONSUMPTION ->
            CanDataRepository.currentFuelConsumption.numberFlow()

        AutomationSignalId.DISTANCE_TO_EMPTY_KM -> CanDataRepository.distanceToFuelEmpty.uintNumberFlow()
        AutomationSignalId.DISTANCE_TO_MAINTENANCE_KM ->
            CanDataRepository.distanceToNextMaintenance.uintNumberFlow()

        AutomationSignalId.VOLTAGE -> CanDataRepository.voltage.numberFlow()
        AutomationSignalId.STEERING_ANGLE -> CanDataRepository.steerAngle.numberFlow()
        AutomationSignalId.STEERING_SPEED -> CanDataRepository.steerSpeed.numberFlow()
        AutomationSignalId.CRUISE_SET_SPEED -> CanDataRepository.cruiseSetSpeed.uintNumberFlow()
        AutomationSignalId.GEAR_MODE -> CanDataRepository.gearBoxMode.map { value ->
            value.trim().takeIf(String::isNotEmpty)?.let(AutomationSignalValue::State)
                ?: AutomationSignalValue.Unavailable
        }

        AutomationSignalId.CURRENT_GEAR -> CanDataRepository.gearBoxCurrentGear.numberFlow()
        AutomationSignalId.FRONT_LEFT_WHEEL_PRESSURE ->
            CanDataRepository.wheelsPressure.wheelNumberFlow(Wheels::wheel1)

        AutomationSignalId.FRONT_RIGHT_WHEEL_PRESSURE ->
            CanDataRepository.wheelsPressure.wheelNumberFlow(Wheels::wheel2)

        AutomationSignalId.REAR_LEFT_WHEEL_PRESSURE ->
            CanDataRepository.wheelsPressure.wheelNumberFlow(Wheels::wheel3)

        AutomationSignalId.REAR_RIGHT_WHEEL_PRESSURE ->
            CanDataRepository.wheelsPressure.wheelNumberFlow(Wheels::wheel4)

        AutomationSignalId.FRONT_LEFT_WHEEL_TEMPERATURE ->
            CanDataRepository.wheelsTemperature.wheelNumberFlow(Wheels::wheel1)

        AutomationSignalId.FRONT_RIGHT_WHEEL_TEMPERATURE ->
            CanDataRepository.wheelsTemperature.wheelNumberFlow(Wheels::wheel2)

        AutomationSignalId.REAR_LEFT_WHEEL_TEMPERATURE ->
            CanDataRepository.wheelsTemperature.wheelNumberFlow(Wheels::wheel3)

        AutomationSignalId.REAR_RIGHT_WHEEL_TEMPERATURE ->
            CanDataRepository.wheelsTemperature.wheelNumberFlow(Wheels::wheel4)

        AutomationSignalId.INSIDE_AIR_QUALITY -> CanDataRepository.insideAirQuality.uintNumberFlow()
        AutomationSignalId.OUTSIDE_AIR_QUALITY -> CanDataRepository.outsideAirQuality.uintNumberFlow()
        else -> null
    }

    private fun headUnitFlow(signal: AutomationSignalId): Flow<AutomationSignalValue>? = when (signal) {
        AutomationSignalId.ENGINE_RPM -> UniversalCanRepository.engineRpmState.numberFlow()
        AutomationSignalId.CAR_SPEED -> UniversalCanRepository.carSpeedState.numberFlow()
        AutomationSignalId.ENGINE_TEMPERATURE ->
            UniversalCanRepository.engineTemperatureState.numberFlow()

        AutomationSignalId.OUTSIDE_TEMPERATURE ->
            UniversalCanRepository.outsideTemperatureState.numberFlow()

        AutomationSignalId.FUEL_LEVEL_PERCENT ->
            UniversalCanRepository.fuelLevelPercentState.uintNumberFlow()

        AutomationSignalId.ODOMETER_KM -> UniversalCanRepository.odometerKmState.uintNumberFlow()
        AutomationSignalId.CURRENT_FUEL_CONSUMPTION ->
            UniversalCanRepository.currentFuelConsumptionState.numberFlow()

        AutomationSignalId.DISTANCE_TO_EMPTY_KM ->
            UniversalCanRepository.distanceToFuelEmptyKmState.uintNumberFlow()

        AutomationSignalId.DISTANCE_TO_MAINTENANCE_KM ->
            UniversalCanRepository.distanceToNextMaintenanceKmState.uintNumberFlow()

        AutomationSignalId.STEERING_ANGLE -> UniversalCanRepository.steerAngleState.numberFlow()
        AutomationSignalId.STEERING_SPEED -> UniversalCanRepository.steerSpeedState.numberFlow()
        AutomationSignalId.CRUISE_SET_SPEED -> UniversalCanRepository.accCruiseVSetDisKmh.numberFlow()
        AutomationSignalId.GEAR_MODE -> UniversalCanRepository.gearBoxModeState.map { value ->
            value?.trim()?.takeIf(String::isNotEmpty)?.let(AutomationSignalValue::State)
                ?: AutomationSignalValue.Unavailable
        }

        AutomationSignalId.FRONT_LEFT_WHEEL_PRESSURE ->
            UniversalCanRepository.wheelsPressureState.wheelNumberFlow(Wheels::wheel1)

        AutomationSignalId.FRONT_RIGHT_WHEEL_PRESSURE ->
            UniversalCanRepository.wheelsPressureState.wheelNumberFlow(Wheels::wheel2)

        AutomationSignalId.REAR_LEFT_WHEEL_PRESSURE ->
            UniversalCanRepository.wheelsPressureState.wheelNumberFlow(Wheels::wheel3)

        AutomationSignalId.REAR_RIGHT_WHEEL_PRESSURE ->
            UniversalCanRepository.wheelsPressureState.wheelNumberFlow(Wheels::wheel4)

        AutomationSignalId.FRONT_LEFT_WHEEL_TEMPERATURE ->
            UniversalCanRepository.wheelsTemperatureState.wheelNumberFlow(Wheels::wheel1)

        AutomationSignalId.FRONT_RIGHT_WHEEL_TEMPERATURE ->
            UniversalCanRepository.wheelsTemperatureState.wheelNumberFlow(Wheels::wheel2)

        AutomationSignalId.REAR_LEFT_WHEEL_TEMPERATURE ->
            UniversalCanRepository.wheelsTemperatureState.wheelNumberFlow(Wheels::wheel3)

        AutomationSignalId.REAR_RIGHT_WHEEL_TEMPERATURE ->
            UniversalCanRepository.wheelsTemperatureState.wheelNumberFlow(Wheels::wheel4)

        AutomationSignalId.INSIDE_AIR_QUALITY ->
            UniversalCanRepository.insideAirQualityState.uintNumberFlow()

        AutomationSignalId.OUTSIDE_AIR_QUALITY ->
            UniversalCanRepository.outsideAirQualityState.uintNumberFlow()

        AutomationSignalId.STEERING_WHEEL_HEAT ->
            UniversalCanRepository.steeringWheelHeatState.binaryFlow()

        AutomationSignalId.WIPER_MAINTENANCE ->
            UniversalCanRepository.wiperMaintenanceState.binaryFlow()

        AutomationSignalId.PARKING_RADAR ->
            UniversalCanRepository.parkingRadarState.binaryFlow()

        AutomationSignalId.REAR_FOG -> UniversalCanRepository.rearFogState.binaryFlow()
        AutomationSignalId.AVH -> UniversalCanRepository.avhState.binaryFlow()
        AutomationSignalId.HDC -> UniversalCanRepository.hdcState.binaryFlow()
        AutomationSignalId.ESP_OFF -> UniversalCanRepository.espOffState.binaryFlow()
        AutomationSignalId.TJA_ICA -> UniversalCanRepository.tjaIcaState.binaryFlow()
        AutomationSignalId.HMA -> UniversalCanRepository.hmaState.binaryFlow()
        AutomationSignalId.HVAC_AC_MAX -> UniversalCanRepository.hvacAcMaxState.binaryFlow()
        AutomationSignalId.HVAC_POWER -> UniversalCanRepository.hvacAcPowerState.binaryFlow()
        AutomationSignalId.HVAC_AUTO -> UniversalCanRepository.hvacAutoState.binaryFlow()
        AutomationSignalId.HVAC_RECIRCULATION ->
            UniversalCanRepository.hvacAirRecirculationState.binaryFlow()

        AutomationSignalId.HVAC_SYNC -> HvacClimateCanRepository.hvacSyncState.binaryFlow()
        AutomationSignalId.DRIVE_MODE -> UniversalCanRepository.carSettingsDriveMode.numberFlow()
        AutomationSignalId.HEADLIGHT_MODE -> UniversalCanRepository.headlightModeRaw.numberFlow()
        AutomationSignalId.REVERSE_GEAR -> UniversalCanRepository.reverseGearSwitchState.map {
            it?.let { engaged -> AutomationSignalValue.State(if (engaged) "on" else "off") }
                ?: AutomationSignalValue.Unavailable
        }

        AutomationSignalId.FRONT_LEFT_SEAT_MODE ->
            UniversalCanRepository.frontLeftSeatModeState.seatModeFlow()

        AutomationSignalId.FRONT_RIGHT_SEAT_MODE ->
            UniversalCanRepository.frontRightSeatModeState.seatModeFlow()

        AutomationSignalId.REAR_LEFT_SEAT_MODE ->
            UniversalCanRepository.rearLeftSeatModeState.seatModeFlow()

        AutomationSignalId.REAR_RIGHT_SEAT_MODE ->
            UniversalCanRepository.rearRightSeatModeState.seatModeFlow()

        else -> null
    }

    private fun huInterestFor(signal: AutomationSignalId): MbCanSignal? = when (signal) {
        AutomationSignalId.ENGINE_RPM -> MbCanSignal.EngineRpm
        AutomationSignalId.CAR_SPEED -> MbCanSignal.CarSpeed
        AutomationSignalId.ENGINE_TEMPERATURE -> MbCanSignal.EngineTemperature
        AutomationSignalId.OUTSIDE_TEMPERATURE -> MbCanSignal.OutsideTemperature
        AutomationSignalId.FUEL_LEVEL_PERCENT -> MbCanSignal.FuelLevel
        AutomationSignalId.ODOMETER_KM -> MbCanSignal.TotalOdometer
        AutomationSignalId.CURRENT_FUEL_CONSUMPTION -> MbCanSignal.CurrentFuelConsumption
        AutomationSignalId.DISTANCE_TO_EMPTY_KM -> MbCanSignal.DistanceToFuelEmpty
        AutomationSignalId.DISTANCE_TO_MAINTENANCE_KM -> MbCanSignal.DistanceToNextMaintenance
        AutomationSignalId.STEERING_ANGLE,
        AutomationSignalId.STEERING_SPEED,
        -> MbCanSignal.SteeringAngle

        AutomationSignalId.CRUISE_SET_SPEED -> MbCanSignal.AccCruise
        AutomationSignalId.GEAR_MODE -> MbCanSignal.VehicleGear
        AutomationSignalId.FRONT_LEFT_WHEEL_PRESSURE,
        AutomationSignalId.FRONT_RIGHT_WHEEL_PRESSURE,
        AutomationSignalId.REAR_LEFT_WHEEL_PRESSURE,
        AutomationSignalId.REAR_RIGHT_WHEEL_PRESSURE,
        AutomationSignalId.FRONT_LEFT_WHEEL_TEMPERATURE,
        AutomationSignalId.FRONT_RIGHT_WHEEL_TEMPERATURE,
        AutomationSignalId.REAR_LEFT_WHEEL_TEMPERATURE,
        AutomationSignalId.REAR_RIGHT_WHEEL_TEMPERATURE,
        -> MbCanSignal.VehicleTires

        AutomationSignalId.INSIDE_AIR_QUALITY,
        AutomationSignalId.OUTSIDE_AIR_QUALITY,
        -> MbCanSignal.Pm25AirQuality

        AutomationSignalId.STEERING_WHEEL_HEAT -> MbCanSignal.SteeringWheelHeat
        AutomationSignalId.WIPER_MAINTENANCE -> MbCanSignal.WiperMaintenance
        AutomationSignalId.PARKING_RADAR -> MbCanSignal.ParkingRadar
        AutomationSignalId.REAR_FOG -> MbCanSignal.RearFogLight
        AutomationSignalId.AVH -> MbCanSignal.AvhSwitch
        AutomationSignalId.HDC -> MbCanSignal.HdcSwitch
        AutomationSignalId.ESP_OFF -> MbCanSignal.EspOffSwitch
        AutomationSignalId.TJA_ICA -> MbCanSignal.TjaIca
        AutomationSignalId.HMA -> MbCanSignal.HmaSwitch
        AutomationSignalId.HVAC_AC_MAX -> MbCanSignal.HvacAcMax
        AutomationSignalId.HVAC_POWER -> MbCanSignal.HvacAcPower
        AutomationSignalId.HVAC_AUTO -> MbCanSignal.HvacAutoState
        AutomationSignalId.HVAC_RECIRCULATION -> MbCanSignal.HvacAirRecirculation
        AutomationSignalId.HVAC_SYNC -> MbCanSignal.HvacSync
        AutomationSignalId.DRIVE_MODE -> MbCanSignal.CarSettingsVehicleParams
        AutomationSignalId.HEADLIGHT_MODE -> MbCanSignal.LightControl
        AutomationSignalId.REVERSE_GEAR -> MbCanSignal.ReverseGearSwitch
        AutomationSignalId.FRONT_LEFT_SEAT_MODE -> MbCanSignal.FrontLeftSeatMode
        AutomationSignalId.FRONT_RIGHT_SEAT_MODE -> MbCanSignal.FrontRightSeatMode
        AutomationSignalId.REAR_LEFT_SEAT_MODE -> MbCanSignal.RearLeftSeatMode
        AutomationSignalId.REAR_RIGHT_SEAT_MODE -> MbCanSignal.RearRightSeatMode
        else -> null
    }

    companion object {
        const val SOURCE_ID = "user-automations"
    }
}

private fun <T : Number> Flow<T?>.numberFlow(): Flow<AutomationSignalValue> =
    map { value ->
        value?.toDouble()?.takeIf(Double::isFinite)?.let(AutomationSignalValue::Number)
            ?: AutomationSignalValue.Unavailable
    }

private fun Flow<UInt?>.uintNumberFlow(): Flow<AutomationSignalValue> =
    map { value ->
        value?.toDouble()?.let(AutomationSignalValue::Number) ?: AutomationSignalValue.Unavailable
    }

private fun Flow<Wheels>.wheelNumberFlow(
    selector: (Wheels) -> Float?,
): Flow<AutomationSignalValue> =
    map { wheels ->
        selector(wheels)?.toDouble()?.takeIf(Double::isFinite)?.let(AutomationSignalValue::Number)
            ?: AutomationSignalValue.Unavailable
    }

private fun Flow<MbCanBinaryState>.binaryFlow(): Flow<AutomationSignalValue> =
    map { state ->
        when (state) {
            MbCanBinaryState.Off -> AutomationSignalValue.State("off")
            MbCanBinaryState.On -> AutomationSignalValue.State("on")
            is MbCanBinaryState.Unavailable,
            MbCanBinaryState.Unknown,
            -> AutomationSignalValue.Unavailable
        }
    }

private fun Flow<MbCanSeatModeState>.seatModeFlow(): Flow<AutomationSignalValue> =
    map { state ->
        val value = when (state) {
            MbCanSeatModeState.Off -> "off"
            is MbCanSeatModeState.Heat -> "heat_${state.level}"
            is MbCanSeatModeState.Vent -> "vent_${state.level}"
            is MbCanSeatModeState.Unavailable,
            MbCanSeatModeState.Unknown,
            -> null
        }
        value?.let(AutomationSignalValue::State) ?: AutomationSignalValue.Unavailable
    }

private fun Flow<AutomationSignalValue>.withAvailability(
    availability: Flow<Boolean>,
): Flow<AutomationSignalValue> =
    combine(availability) { value, available ->
        if (available) value else AutomationSignalValue.Unavailable
    }.distinctUntilChanged()
