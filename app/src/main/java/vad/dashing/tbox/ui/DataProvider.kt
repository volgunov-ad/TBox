package vad.dashing.tbox.ui

import android.content.Context
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import vad.dashing.tbox.AppDataViewModel
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.TboxViewModel
import vad.dashing.tbox.createDateTimeWidgetDateFormat
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.seatModeToString
import vad.dashing.tbox.utils.GEARBOX_MODE_CURRENT_GEAR_DATA_KEY
import vad.dashing.tbox.utils.formatGearBoxModeWithCurrentGear
import vad.dashing.tbox.valueToString
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

interface DataProvider {
    fun getValueFlow(
        key: String,
        accuracy: Int? = null,
        dateTimeFormat: String = "",
    ): StateFlow<String>
}

/** Keys for composite tiles only — same CAN data as public keys, different default decimal places. */
object DashboardCompositeTileFlowKeys {
    const val ENGINE_TEMP_VOLTAGE_ENGINE = "engineTemperature_voltageEngineTile"
    const val GEARBOX_OIL_TEMP_GEAR_TILE = "gearBoxOilTemperature_gearBoxTile"
    const val WHEEL1_PRESSURE_WHEELS_TILE = "wheel1Pressure_wheelsTile"
    const val WHEEL2_PRESSURE_WHEELS_TILE = "wheel2Pressure_wheelsTile"
    const val WHEEL3_PRESSURE_WHEELS_TILE = "wheel3Pressure_wheelsTile"
    const val WHEEL4_PRESSURE_WHEELS_TILE = "wheel4Pressure_wheelsTile"
}

const val ENGINE_RPM_CAN_FLOW_KEY = "engineRPM_can"
const val ENGINE_TEMPERATURE_CAN_FLOW_KEY = "engineTemperature_can"
const val CAR_SPEED_CAN_FLOW_KEY = "carSpeed_can"
const val ODOMETER_CAN_FLOW_KEY = "odometer_can"
const val FUEL_LEVEL_PERCENTAGE_CAN_FLOW_KEY = "fuelLevelPercentage_can"
const val OUTSIDE_TEMPERATURE_CAN_FLOW_KEY = "outsideTemperature_can"

private data class ValueFlowCacheKey(
    val key: String,
    val accuracy: Int?,
    val dateTimeFormat: String,
)

class TboxDataProvider(
    private val viewModel: TboxViewModel,
    private val canViewModel: CanDataViewModel,
    private val appDataViewModel: AppDataViewModel,
    private val settingsViewModel: SettingsViewModel,
    private val context: Context,
) : DataProvider {
    private val flowCache = mutableMapOf<ValueFlowCacheKey, StateFlow<String>>()
    private val yesLabel = context.getString(R.string.value_yes)
    private val noLabel = context.getString(R.string.value_no)
    private val switchingLabel = context.getString(R.string.value_switching)
    private val blockedLabel = context.getString(R.string.value_blocked)
    private val unblockedLabel = context.getString(R.string.value_unblocked)
    private val restartFlow = MutableStateFlow(context.getString(R.string.tbox_short)).asStateFlow()
    private val emptyFlow = MutableStateFlow("").asStateFlow()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val localTimeFlow = flow {
        val formatter = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())
        while (true) {
            emit(formatter.format(Date()))
            delay(1000)
        }
    }.distinctUntilChanged().stateIn(
        scope = viewModel.viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()).format(Date())
    )
    private val localDateFlow = flow {
        val formatter = DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault())
        while (true) {
            emit(formatter.format(Date()))
            delay(1000)
        }
    }.distinctUntilChanged().stateIn(
        scope = viewModel.viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault()).format(Date())
    )

    override fun getValueFlow(
        key: String,
        accuracy: Int?,
        dateTimeFormat: String,
    ): StateFlow<String> {
        val cacheKey = ValueFlowCacheKey(key, accuracy, dateTimeFormat.trim())
        return flowCache.getOrPut(cacheKey) {
            createFlowForKey(key, accuracy, dateTimeFormat)
        }
    }

    private fun effectiveDecimalPlaces(defaultDigits: Int, accuracy: Int?): Int =
        if (accuracy != null && accuracy >= 0) accuracy else defaultDigits

    private fun createFlowForKey(key: String, accuracy: Int?, dateTimeFormat: String): StateFlow<String> {
        fun eff(defaultDigits: Int) = effectiveDecimalPlaces(defaultDigits, accuracy)
        return when (key) {
            "voltage" -> canViewModel.voltage.mapState { valueToString(it, eff(1)) }
            "steerAngle" -> canViewModel.steerAngle.mapState { valueToString(it, eff(1)) }
            "steerSpeed" -> canViewModel.steerSpeed.mapState { valueToString(it, eff(1)) }
            "engineRPM" -> canViewModel.engineRPM.mapState { valueToString(it, eff(1)) }
            ENGINE_RPM_CAN_FLOW_KEY -> UniversalCanRepository.engineRpmState.mapState { valueToString(it, eff(1)) }
            "param1" -> canViewModel.param1.mapState { valueToString(it, eff(1)) }
            "param2" -> canViewModel.param2.mapState { valueToString(it, eff(1)) }
            "param3" -> canViewModel.param3.mapState { valueToString(it, eff(1)) }
            "param4" -> canViewModel.param4.mapState { valueToString(it, eff(1)) }
            "param5" -> canViewModel.param5.mapState { valueToString(it, eff(1)) }
            "carSpeed" -> canViewModel.carSpeed.mapState { valueToString(it, eff(1)) }
            CAR_SPEED_CAN_FLOW_KEY -> UniversalCanRepository.carSpeedState.mapState { valueToString(it, eff(1)) }
            "carSpeedAccurate" -> canViewModel.carSpeedAccurate.mapState { valueToString(it, eff(1)) }
            "wheel1Speed" -> canViewModel.wheelsSpeed.mapState { valueToString(it.wheel1, eff(1)) }
            "wheel2Speed" -> canViewModel.wheelsSpeed.mapState { valueToString(it.wheel2, eff(1)) }
            "wheel3Speed" -> canViewModel.wheelsSpeed.mapState { valueToString(it.wheel3, eff(1)) }
            "wheel4Speed" -> canViewModel.wheelsSpeed.mapState { valueToString(it.wheel4, eff(1)) }
            "wheel1Pressure" -> canViewModel.wheelsPressure.mapState { valueToString(it.wheel1, eff(2)) }
            "wheel2Pressure" -> canViewModel.wheelsPressure.mapState { valueToString(it.wheel2, eff(2)) }
            "wheel3Pressure" -> canViewModel.wheelsPressure.mapState { valueToString(it.wheel3, eff(2)) }
            "wheel4Pressure" -> canViewModel.wheelsPressure.mapState { valueToString(it.wheel4, eff(2)) }
            // Composite wheels tiles historically formatted pressure with 1 decimal (not 2 like solo keys).
            DashboardCompositeTileFlowKeys.WHEEL1_PRESSURE_WHEELS_TILE -> canViewModel.wheelsPressure.mapState {
                valueToString(it.wheel1, eff(1))
            }
            DashboardCompositeTileFlowKeys.WHEEL2_PRESSURE_WHEELS_TILE -> canViewModel.wheelsPressure.mapState {
                valueToString(it.wheel2, eff(1))
            }
            DashboardCompositeTileFlowKeys.WHEEL3_PRESSURE_WHEELS_TILE -> canViewModel.wheelsPressure.mapState {
                valueToString(it.wheel3, eff(1))
            }
            DashboardCompositeTileFlowKeys.WHEEL4_PRESSURE_WHEELS_TILE -> canViewModel.wheelsPressure.mapState {
                valueToString(it.wheel4, eff(1))
            }
            "wheel1Temperature" -> canViewModel.wheelsTemperature.mapState {
                valueToString(it.wheel1, eff(0))
            }
            "wheel2Temperature" -> canViewModel.wheelsTemperature.mapState {
                valueToString(it.wheel2, eff(0))
            }
            "wheel3Temperature" -> canViewModel.wheelsTemperature.mapState {
                valueToString(it.wheel3, eff(0))
            }
            "wheel4Temperature" -> canViewModel.wheelsTemperature.mapState {
                valueToString(it.wheel4, eff(0))
            }
            "cruiseSetSpeed" -> canViewModel.cruiseSetSpeed.mapState { valueToString(it, eff(1)) }
            "odometer" -> canViewModel.odometer.mapState { valueToString(it, eff(1)) }
            ODOMETER_CAN_FLOW_KEY -> UniversalCanRepository.odometerKmState.mapState { valueToString(it, eff(1)) }
            "distanceToNextMaintenance" -> canViewModel.distanceToNextMaintenance.mapState {
                valueToString(it, eff(1))
            }
            "distanceToFuelEmpty" -> canViewModel.distanceToFuelEmpty.mapState { valueToString(it, eff(1)) }
            "breakingForce" -> canViewModel.breakingForce.mapState { valueToString(it, eff(1)) }
            "fuelLevelPercentage" -> canViewModel.fuelLevelPercentage.mapState { valueToString(it, eff(1)) }
            FUEL_LEVEL_PERCENTAGE_CAN_FLOW_KEY ->
                UniversalCanRepository.fuelLevelPercentState.mapState { valueToString(it, eff(1)) }
            "fuelLevelPercentageFiltered" -> canViewModel.fuelLevelPercentageFiltered.mapState {
                valueToString(it, eff(1))
            }
            "fuelLevelLiters" -> canViewModel.fuelLevelCalibratedLiters.mapState { valueToString(it, eff(1)) }
            "currentFuelConsumption" -> canViewModel.currentFuelConsumption.mapState {
                valueToString(it, eff(1))
            }
            "engineTemperature" -> canViewModel.engineTemperature.mapState { valueToString(it, eff(1)) }
            ENGINE_TEMPERATURE_CAN_FLOW_KEY -> UniversalCanRepository.engineTemperatureState.mapState {
                valueToString(it, eff(1))
            }
            // Voltage+engine composite used integer °C for engine line when accuracy is default.
            DashboardCompositeTileFlowKeys.ENGINE_TEMP_VOLTAGE_ENGINE -> canViewModel.engineTemperature.mapState {
                valueToString(it, eff(0))
            }
            "gearBoxOilTemperature" -> canViewModel.gearBoxOilTemperature.mapState {
                valueToString(it, eff(1))
            }
            // Gearbox composite used integer °C for oil temperature when accuracy is default.
            DashboardCompositeTileFlowKeys.GEARBOX_OIL_TEMP_GEAR_TILE -> canViewModel.gearBoxOilTemperature.mapState {
                valueToString(it, eff(0))
            }
            "gearBoxCurrentGear" -> canViewModel.gearBoxCurrentGear.mapState { valueToString(it, eff(1)) }
            "gearBoxPreparedGear" -> canViewModel.gearBoxPreparedGear.mapState { valueToString(it, eff(1)) }
            "gearBoxChangeGear" -> canViewModel.gearBoxChangeGear.mapState {
                valueToString(it, booleanTrue = switchingLabel, booleanFalse = noLabel)
            }
            "gearBoxMode" -> canViewModel.gearBoxMode
            GEARBOX_MODE_CURRENT_GEAR_DATA_KEY -> combine(
                canViewModel.gearBoxMode,
                canViewModel.gearBoxCurrentGear,
            ) { mode, gear ->
                formatGearBoxModeWithCurrentGear(mode, gear)
            }.let { flow ->
                flow.distinctUntilChanged().stateIn(
                    scope = viewModel.viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = "",
                )
            }
            "gearBoxDriveMode" -> canViewModel.gearBoxDriveMode
            "gearBoxWork" -> canViewModel.gearBoxWork
            "frontRightSeatMode" -> canViewModel.frontRightSeatMode.mapState { seatModeToString(context, it) }
            "frontLeftSeatMode" -> canViewModel.frontLeftSeatMode.mapState { seatModeToString(context, it) }
            "signalLevel" -> viewModel.netState.mapState { valueToString(it.signalLevel, eff(1)) }
            "netStatus" -> viewModel.netState.mapState { it.netStatus }
            "regStatus" -> viewModel.netState.mapState { it.regStatus }
            "simStatus" -> viewModel.netState.mapState { it.simStatus }
            "locateStatus" -> viewModel.locValues.mapState {
                valueToString(it.locateStatus, booleanTrue = yesLabel, booleanFalse = noLabel)
            }
            "isLocValuesTrue" -> viewModel.isLocValuesTrue.mapState {
                valueToString(it, booleanTrue = yesLabel, booleanFalse = noLabel)
            }
            "gnssSpeed" -> viewModel.locValues.mapState { valueToString(it.speed, eff(1)) }
            "longitude" -> viewModel.locValues.mapState { valueToString(it.longitude, eff(6)) }
            "latitude" -> viewModel.locValues.mapState { valueToString(it.latitude, eff(6)) }
            "altitude" -> viewModel.locValues.mapState { valueToString(it.altitude, eff(2)) }
            "visibleSatellites" -> viewModel.locValues.mapState { valueToString(it.visibleSatellites, eff(1)) }
            "trueDirection" -> viewModel.locValues.mapState { valueToString(it.trueDirection, eff(1)) }
            "locationUpdateTime" -> viewModel.locValues.mapState {
                it.updateTime?.let { updateTime -> timeFormat.format(updateTime) } ?: ""
            }
            "locationRefreshTime" -> viewModel.locationUpdateTime.mapState {
                it?.let { locationUpdateTime -> timeFormat.format(locationUpdateTime) } ?: ""
            }
            "outsideTemperature" -> canViewModel.outsideTemperature.mapState { valueToString(it, eff(1)) }
            OUTSIDE_TEMPERATURE_CAN_FLOW_KEY ->
                UniversalCanRepository.outsideTemperatureState.mapState { valueToString(it, eff(1)) }
            "insideTemperature" -> canViewModel.insideTemperature.mapState { valueToString(it, eff(1)) }
            "outsideAirQuality" -> canViewModel.outsideAirQuality.mapState { valueToString(it, eff(1)) }
            "insideAirQuality" -> canViewModel.insideAirQuality.mapState { valueToString(it, eff(1)) }
            "isWindowsBlocked" -> canViewModel.isWindowsBlocked.mapState {
                valueToString(it, booleanTrue = blockedLabel, booleanFalse = unblockedLabel)
            }
            "motorHours" -> appDataViewModel.motorHours.mapState { valueToString(it, eff(1)) }
            "motorHoursTrip" -> canViewModel.motorHoursTrip.mapState { valueToString(it, eff(1)) }
            "timeWidget" -> if (dateTimeFormat.isBlank()) {
                localTimeFlow
            } else {
                createDateTimeFlow(key, dateTimeFormat)
            }
            "dateWidget" -> if (dateTimeFormat.isBlank()) {
                localDateFlow
            } else {
                createDateTimeFlow(key, dateTimeFormat)
            }
            "restartTbox" -> restartFlow
            "espConnected" -> vad.dashing.tbox.esp.EspCompanionRepository.connected.mapState {
                valueToString(it, booleanTrue = yesLabel, booleanFalse = noLabel)
            }
            "espGpioMask" -> vad.dashing.tbox.esp.EspCompanionRepository.gpioMask.mapState {
                Integer.toBinaryString(it).padStart(8, '0')
            }
            "espRelayMask" -> vad.dashing.tbox.esp.EspCompanionRepository.relayMask.mapState {
                Integer.toBinaryString(it).padStart(4, '0')
            }
            "espGpioIn0" -> vad.dashing.tbox.esp.EspCompanionRepository.gpioMask.mapState {
                valueToString((it and 1) != 0, booleanTrue = yesLabel, booleanFalse = noLabel)
            }
            "espGpioIn1" -> vad.dashing.tbox.esp.EspCompanionRepository.gpioMask.mapState {
                valueToString((it and 2) != 0, booleanTrue = yesLabel, booleanFalse = noLabel)
            }
            "espGpioIn2" -> vad.dashing.tbox.esp.EspCompanionRepository.gpioMask.mapState {
                valueToString((it and 4) != 0, booleanTrue = yesLabel, booleanFalse = noLabel)
            }
            "espGpioIn3" -> vad.dashing.tbox.esp.EspCompanionRepository.gpioMask.mapState {
                valueToString((it and 8) != 0, booleanTrue = yesLabel, booleanFalse = noLabel)
            }
            "espRelay0" -> vad.dashing.tbox.esp.EspCompanionRepository.relayMask.mapState {
                valueToString((it and 1) != 0, booleanTrue = yesLabel, booleanFalse = noLabel)
            }
            "espRelay1" -> vad.dashing.tbox.esp.EspCompanionRepository.relayMask.mapState {
                valueToString((it and 2) != 0, booleanTrue = yesLabel, booleanFalse = noLabel)
            }
            else -> emptyFlow
        }
    }

    private fun createDateTimeFlow(key: String, dateTimeFormat: String): StateFlow<String> {
        val formatter = createDateTimeWidgetDateFormat(key, dateTimeFormat, Locale.getDefault())
        return flow {
            while (true) {
                emit(formatter.format(Date()))
                delay(1000)
            }
        }.distinctUntilChanged().stateIn(
            scope = viewModel.viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = formatter.format(Date()),
        )
    }

    private fun <T> Flow<T>.mapState(transform: (T) -> String): StateFlow<String> {
        return this.map { transform(it) }
            .distinctUntilChanged()
            .stateIn(
                scope = viewModel.viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = ""
            )
    }
}
