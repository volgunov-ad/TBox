package vad.dashing.tbox.ui

import android.content.Context
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import vad.dashing.tbox.AppDataViewModel
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.R
import vad.dashing.tbox.TboxViewModel
import vad.dashing.tbox.seatModeToString
import vad.dashing.tbox.valueToString
import java.text.SimpleDateFormat
import java.util.Locale

interface DataProvider {
    fun getValueFlow(key: String): StateFlow<String>
}

class TboxDataProvider(
    private val viewModel: TboxViewModel,
    private val canViewModel: CanDataViewModel,
    private val appDataViewModel: AppDataViewModel,
    private val context: Context,
) : DataProvider {
    private val flowCache = mutableMapOf<String, StateFlow<String>>()
    private val yesLabel = context.getString(R.string.value_yes)
    private val noLabel = context.getString(R.string.value_no)
    private val switchingLabel = context.getString(R.string.value_switching)
    private val blockedLabel = context.getString(R.string.value_blocked)
    private val unblockedLabel = context.getString(R.string.value_unblocked)
    private val restartFlow = MutableStateFlow(context.getString(R.string.tbox_short)).asStateFlow()
    private val emptyFlow = MutableStateFlow("").asStateFlow()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun getValueFlow(key: String): StateFlow<String> {
        return flowCache.getOrPut(key) {
            createFlowForKey(key)
        }
    }

    private fun createFlowForKey(key: String): StateFlow<String> = when (key) {
        "voltage" -> canViewModel.voltage.mapState { valueToString(it, 1) }
        "steerAngle" -> canViewModel.steerAngle.mapState { valueToString(it, 1) }
        "steerSpeed" -> canViewModel.steerSpeed.mapState { valueToString(it) }
        "engineRPM" -> canViewModel.engineRPM.mapState { valueToString(it, 1) }
        "param1" -> canViewModel.param1.mapState { valueToString(it, 1) }
        "param2" -> canViewModel.param2.mapState { valueToString(it, 1) }
        "param3" -> canViewModel.param3.mapState { valueToString(it, 1) }
        "param4" -> canViewModel.param4.mapState { valueToString(it, 1) }
        "carSpeed" -> canViewModel.carSpeed.mapState { valueToString(it, 1) }
        "carSpeedAccurate" -> canViewModel.carSpeedAccurate.mapState { valueToString(it, 1) }
        "wheel1Speed" -> canViewModel.wheelsSpeed.mapState { valueToString(it.wheel1, 1) }
        "wheel2Speed" -> canViewModel.wheelsSpeed.mapState { valueToString(it.wheel2, 1) }
        "wheel3Speed" -> canViewModel.wheelsSpeed.mapState { valueToString(it.wheel3, 1) }
        "wheel4Speed" -> canViewModel.wheelsSpeed.mapState { valueToString(it.wheel4, 1) }
        "wheel1Pressure" -> canViewModel.wheelsPressure.mapState { valueToString(it.wheel1, 2) }
        "wheel2Pressure" -> canViewModel.wheelsPressure.mapState { valueToString(it.wheel2, 2) }
        "wheel3Pressure" -> canViewModel.wheelsPressure.mapState { valueToString(it.wheel3, 2) }
        "wheel4Pressure" -> canViewModel.wheelsPressure.mapState { valueToString(it.wheel4, 2) }
        "wheel1Temperature" -> canViewModel.wheelsTemperature.mapState { valueToString(it.wheel1, 0) }
        "wheel2Temperature" -> canViewModel.wheelsTemperature.mapState { valueToString(it.wheel2, 0) }
        "wheel3Temperature" -> canViewModel.wheelsTemperature.mapState { valueToString(it.wheel3, 0) }
        "wheel4Temperature" -> canViewModel.wheelsTemperature.mapState { valueToString(it.wheel4, 0) }
        "cruiseSetSpeed" -> canViewModel.cruiseSetSpeed.mapState { valueToString(it) }
        "odometer" -> canViewModel.odometer.mapState { valueToString(it) }
        "distanceToNextMaintenance" -> canViewModel.distanceToNextMaintenance.mapState { valueToString(it) }
        "distanceToFuelEmpty" -> canViewModel.distanceToFuelEmpty.mapState { valueToString(it) }
        "breakingForce" -> canViewModel.breakingForce.mapState { valueToString(it) }
        "fuelLevelPercentage" -> canViewModel.fuelLevelPercentage.mapState { valueToString(it) }
        "fuelLevelPercentageFiltered" -> canViewModel.fuelLevelPercentageFiltered.mapState { valueToString(it) }
        "engineTemperature" -> canViewModel.engineTemperature.mapState { valueToString(it, 1) }
        "gearBoxOilTemperature" -> canViewModel.gearBoxOilTemperature.mapState { valueToString(it) }
        "gearBoxCurrentGear" -> canViewModel.gearBoxCurrentGear.mapState { valueToString(it) }
        "gearBoxPreparedGear" -> canViewModel.gearBoxPreparedGear.mapState { valueToString(it) }
        "gearBoxChangeGear" -> canViewModel.gearBoxChangeGear.mapState {
            valueToString(it, booleanTrue = switchingLabel, booleanFalse = noLabel)
        }
        "gearBoxMode" -> canViewModel.gearBoxMode
        "gearBoxDriveMode" -> canViewModel.gearBoxDriveMode
        "gearBoxWork" -> canViewModel.gearBoxWork
        "frontRightSeatMode" -> canViewModel.frontRightSeatMode.mapState { seatModeToString(context, it) }
        "frontLeftSeatMode" -> canViewModel.frontLeftSeatMode.mapState { seatModeToString(context, it) }
        "signalLevel" -> viewModel.netState.mapState { valueToString(it.signalLevel) }
        "netStatus" -> viewModel.netState.mapState { it.netStatus }
        "regStatus" -> viewModel.netState.mapState { it.regStatus }
        "simStatus" -> viewModel.netState.mapState { it.simStatus }
        "locateStatus" -> viewModel.locValues.mapState {
            valueToString(it.locateStatus, booleanTrue = yesLabel, booleanFalse = noLabel)
        }
        "isLocValuesTrue" -> viewModel.isLocValuesTrue.mapState {
            valueToString(it, booleanTrue = yesLabel, booleanFalse = noLabel)
        }
        "gnssSpeed" -> viewModel.locValues.mapState { valueToString(it.speed, 1) }
        "longitude" -> viewModel.locValues.mapState { valueToString(it.longitude, 6) }
        "latitude" -> viewModel.locValues.mapState { valueToString(it.latitude, 6) }
        "altitude" -> viewModel.locValues.mapState { valueToString(it.altitude, 2) }
        "visibleSatellites" -> viewModel.locValues.mapState { valueToString(it.visibleSatellites) }
        "trueDirection" -> viewModel.locValues.mapState { valueToString(it.trueDirection, 1) }
        "locationUpdateTime" -> viewModel.locValues.mapState {
            it.updateTime?.let { updateTime -> timeFormat.format(updateTime) } ?: ""
        }
        "locationRefreshTime" -> viewModel.locationUpdateTime.mapState {
            it?.let { locationUpdateTime -> timeFormat.format(locationUpdateTime) } ?: ""
        }
        "outsideTemperature" -> canViewModel.outsideTemperature.mapState { valueToString(it, 1) }
        "insideTemperature" -> canViewModel.insideTemperature.mapState { valueToString(it, 1) }
        "outsideAirQuality" -> canViewModel.outsideAirQuality.mapState { valueToString(it) }
        "insideAirQuality" -> canViewModel.insideAirQuality.mapState { valueToString(it) }
        "isWindowsBlocked" -> canViewModel.isWindowsBlocked.mapState {
            valueToString(it, booleanTrue = blockedLabel, booleanFalse = unblockedLabel)
        }
        "motorHours" -> appDataViewModel.motorHours.mapState { valueToString(it, 1) }
        "motorHoursTrip" -> canViewModel.motorHoursTrip.mapState { valueToString(it, 1) }
        "restartTbox" -> restartFlow
        else -> emptyFlow
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