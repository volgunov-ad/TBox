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
import vad.dashing.tbox.ForegroundAppMonitor
import vad.dashing.tbox.TboxRepository
import vad.dashing.tbox.Wheels
import vad.dashing.tbox.esp.EspCompanionRepository
import vad.dashing.tbox.location.GeoDisplayRepository
import vad.dashing.tbox.location.LocIndicatorState
import vad.dashing.tbox.mbcan.BodyComfortDomain
import vad.dashing.tbox.mbcan.MbCanAvailability
import vad.dashing.tbox.mbcan.MbCanBinaryState
import vad.dashing.tbox.mbcan.MbCanSeatModeState
import vad.dashing.tbox.mbcan.MbCanSignal
import vad.dashing.tbox.mbcan.ShadeRoofPosition
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.mbcan.WindowPanePosition

class AutomationSignalProvider(
    private val scope: CoroutineScope,
    private val onSample: suspend (AutomationSignalSample) -> Unit,
) {
    private val jobs = mutableListOf<Job>()
    private var activeKeys: Set<AutomationSignalKey> = emptySet()

    suspend fun replaceInterests(keys: Set<AutomationSignalKey>) {
        ForegroundAppMonitor.setAutomationWatching(
            keys.any { it.signal == AutomationSignalId.FOREGROUND_APP },
        )
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
            UniversalCanRepository.clearSourceNow(SOURCE_ID)
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
        ForegroundAppMonitor.setAutomationWatching(false)
        UniversalCanRepository.clearSourceNow(SOURCE_ID)
    }

    private fun flowFor(key: AutomationSignalKey): Flow<AutomationSignalValue>? =
        when (key.source) {
            AutomationSignalSource.TBOX -> tboxFlow(key.signal)?.withAvailability(
                TboxRepository.tboxConnected,
            )

            AutomationSignalSource.HEAD_UNIT -> headUnitFlow(key.signal)?.withAvailability(
                UniversalCanRepository.availability.map { it is MbCanAvailability.Available },
            )

            AutomationSignalSource.APP -> when (key.signal) {
                AutomationSignalId.GEO_POSITION -> geoDisplayFlow()
                AutomationSignalId.ESP_GPIO_IN_0 -> espMaskBitFlow(EspCompanionRepository.gpioMask, 0)
                AutomationSignalId.ESP_GPIO_IN_1 -> espMaskBitFlow(EspCompanionRepository.gpioMask, 1)
                AutomationSignalId.ESP_GPIO_IN_2 -> espMaskBitFlow(EspCompanionRepository.gpioMask, 2)
                AutomationSignalId.ESP_GPIO_IN_3 -> espMaskBitFlow(EspCompanionRepository.gpioMask, 3)
                AutomationSignalId.ESP_RELAY_0 -> espMaskBitFlow(EspCompanionRepository.relayMask, 0)
                AutomationSignalId.ESP_RELAY_1 -> espMaskBitFlow(EspCompanionRepository.relayMask, 1)
                AutomationSignalId.FOREGROUND_APP -> foregroundAppFlow()
                else -> null
            }
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

    private fun headUnitFlow(signal: AutomationSignalId): Flow<AutomationSignalValue>? =
        headUnitFlowFor(signal)

    private fun huInterestFor(signal: AutomationSignalId): MbCanSignal? =
        huInterestForSignal(signal)

    companion object {
        const val SOURCE_ID = "user-automations"
    }
}

private fun espMaskBitFlow(mask: Flow<Int>, bit: Int): Flow<AutomationSignalValue> =
    mask.map { value ->
        AutomationSignalValue.State(if ((value and (1 shl bit)) != 0) "on" else "off")
    }.withAvailability(EspCompanionRepository.connected)

private fun foregroundAppFlow(): Flow<AutomationSignalValue> =
    ForegroundAppMonitor.packageName
        .map { pkg ->
            val name = pkg?.trim().orEmpty()
            if (name.isEmpty()) {
                AutomationSignalValue.Unavailable
            } else {
                AutomationSignalValue.State(name)
            }
        }
        .distinctUntilChanged()

private fun geoDisplayFlow(): Flow<AutomationSignalValue> =
    GeoDisplayRepository.state
        .map { state ->
            val lat = state.latitude
            val lon = state.longitude
            if (
                state.indicator == LocIndicatorState.NONE ||
                state.indicator == LocIndicatorState.LOST ||
                !lat.isFinite() ||
                !lon.isFinite()
            ) {
                AutomationSignalValue.Unavailable
            } else {
                AutomationSignalValue.Position(lat, lon)
            }
        }
        .distinctUntilChanged()

internal fun <T : Number> Flow<T?>.numberFlow(): Flow<AutomationSignalValue> =
    map { value ->
        value?.toDouble()?.takeIf(Double::isFinite)?.let(AutomationSignalValue::Number)
            ?: AutomationSignalValue.Unavailable
    }

internal fun Flow<UInt?>.uintNumberFlow(): Flow<AutomationSignalValue> =
    map { value ->
        value?.toDouble()?.let(AutomationSignalValue::Number) ?: AutomationSignalValue.Unavailable
    }

internal fun Flow<Wheels>.wheelNumberFlow(
    selector: (Wheels) -> Float?,
): Flow<AutomationSignalValue> =
    map { wheels ->
        selector(wheels)?.toDouble()?.takeIf(Double::isFinite)?.let(AutomationSignalValue::Number)
            ?: AutomationSignalValue.Unavailable
    }

internal fun Flow<MbCanBinaryState>.binaryFlow(): Flow<AutomationSignalValue> =
    map { state ->
        when (state) {
            MbCanBinaryState.Off -> AutomationSignalValue.State("off")
            MbCanBinaryState.On -> AutomationSignalValue.State("on")
            is MbCanBinaryState.Unavailable,
            MbCanBinaryState.Unknown,
            -> AutomationSignalValue.Unavailable
        }
    }

internal fun Flow<ShadeRoofPosition?>.shadeRoofFlow(): Flow<AutomationSignalValue> =
    map { position ->
        position?.let { AutomationSignalValue.State(BodyComfortDomain.toAutomationState(it)) }
            ?: AutomationSignalValue.Unavailable
    }

internal fun Flow<WindowPanePosition?>.windowPaneFlow(): Flow<AutomationSignalValue> =
    map { position ->
        position?.let { AutomationSignalValue.State(BodyComfortDomain.toAutomationState(it)) }
            ?: AutomationSignalValue.Unavailable
    }

internal fun Flow<MbCanSeatModeState>.seatModeFlow(): Flow<AutomationSignalValue> =
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
