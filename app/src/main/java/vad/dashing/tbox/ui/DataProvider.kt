package vad.dashing.tbox.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import vad.dashing.tbox.TboxViewModel
import vad.dashing.tbox.seatModeToString
import vad.dashing.tbox.valueToString
import java.text.SimpleDateFormat
import java.util.Locale

interface DataProvider {
    fun getValueFlow(key: String): StateFlow<String>
}

class TboxDataProvider(private val viewModel: TboxViewModel) : DataProvider {
    override fun getValueFlow(key: String): StateFlow<String> = when (key) {
        "voltage" -> viewModel.voltage.mapState { valueToString(it, 1) }
        "steerAngle" -> viewModel.steerAngle.mapState { valueToString(it, 1) }
        "steerSpeed" -> viewModel.steerSpeed.mapState { valueToString(it) }
        "engineRPM" -> viewModel.engineRPM.mapState { valueToString(it, 1) }
        "carSpeed" -> viewModel.carSpeed.mapState { valueToString(it, 1) }
        "carSpeedAccurate" -> viewModel.carSpeedAccurate.mapState { valueToString(it, 1) }
        "wheel1Speed" -> viewModel.wheelsSpeed.mapState { valueToString(it.wheel1, 1) }
        "wheel2Speed" -> viewModel.wheelsSpeed.mapState { valueToString(it.wheel2, 1) }
        "wheel3Speed" -> viewModel.wheelsSpeed.mapState { valueToString(it.wheel3, 1) }
        "wheel4Speed" -> viewModel.wheelsSpeed.mapState { valueToString(it.wheel4, 1) }
        "wheel1Pressure" -> viewModel.wheelsPressure.mapState { valueToString(it.wheel1, 2) }
        "wheel2Pressure" -> viewModel.wheelsPressure.mapState { valueToString(it.wheel2, 2) }
        "wheel3Pressure" -> viewModel.wheelsPressure.mapState { valueToString(it.wheel3, 2) }
        "wheel4Pressure" -> viewModel.wheelsPressure.mapState { valueToString(it.wheel4, 2) }
        "cruiseSetSpeed" -> viewModel.cruiseSetSpeed.mapState { valueToString(it) }
        "odometer" -> viewModel.odometer.mapState { valueToString(it) }
        "distanceToNextMaintenance" -> viewModel.distanceToNextMaintenance.mapState { valueToString(it) }
        "distanceToFuelEmpty" -> viewModel.distanceToFuelEmpty.mapState { valueToString(it) }
        "breakingForce" -> viewModel.breakingForce.mapState { valueToString(it) }
        "fuelLevelPercentage" -> viewModel.fuelLevelPercentage.mapState { valueToString(it) }
        "fuelLevelPercentageFiltered" -> viewModel.fuelLevelPercentageFiltered.mapState { valueToString(it) }
        "engineTemperature" -> viewModel.engineTemperature.mapState { valueToString(it, 1) }
        "gearBoxOilTemperature" -> viewModel.gearBoxOilTemperature.mapState { valueToString(it) }
        "gearBoxCurrentGear" -> viewModel.gearBoxCurrentGear.mapState { valueToString(it) }
        "gearBoxPreparedGear" -> viewModel.gearBoxPreparedGear.mapState { valueToString(it) }
        "gearBoxChangeGear" -> viewModel.gearBoxChangeGear.mapState {
            valueToString(it, booleanTrue = "переключение", booleanFalse = "нет")
        }
        "gearBoxMode" -> viewModel.gearBoxMode
        "gearBoxDriveMode" -> viewModel.gearBoxDriveMode
        "gearBoxWork" -> viewModel.gearBoxWork
        "frontRightSeatMode" -> viewModel.frontRightSeatMode.mapState { seatModeToString(it) }
        "frontLeftSeatMode" -> viewModel.frontLeftSeatMode.mapState { seatModeToString(it) }
        "signalLevel" -> viewModel.netState.mapState { valueToString(it.signalLevel) }
        "netStatus" -> viewModel.netState.mapState { it.netStatus }
        "regStatus" -> viewModel.netState.mapState { it.regStatus }
        "simStatus" -> viewModel.netState.mapState { it.simStatus }
        "locateStatus" -> viewModel.locValues.mapState { valueToString(it.locateStatus) }
        "isLocValuesTrue" -> viewModel.isLocValuesTrue.mapState { valueToString(it) }
        "gnssSpeed" -> viewModel.locValues.mapState { valueToString(it.speed, 1) }
        "longitude" -> viewModel.locValues.mapState { valueToString(it.longitude, 6) }
        "latitude" -> viewModel.locValues.mapState { valueToString(it.latitude, 6) }
        "altitude" -> viewModel.locValues.mapState { valueToString(it.altitude, 2) }
        "visibleSatellites" -> viewModel.locValues.mapState { valueToString(it.visibleSatellites) }
        "trueDirection" -> viewModel.locValues.mapState { valueToString(it.trueDirection, 1) }
        "locationUpdateTime" -> viewModel.locValues.mapState {
            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            it.updateTime?.let { updateTime -> timeFormat.format(updateTime) } ?: ""
        }
        "locationRefreshTime" -> viewModel.locationUpdateTime.mapState {
            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            it?.let { locationUpdateTime -> timeFormat.format(locationUpdateTime) } ?: ""
        }
        else -> MutableStateFlow("").asStateFlow()
    }

    private fun <T> Flow<T>.mapState(transform: (T) -> String): StateFlow<String> {
        return this.map { transform(it) }
            .stateIn(
                scope = CoroutineScope(Dispatchers.Main),
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = ""
            )
    }
}