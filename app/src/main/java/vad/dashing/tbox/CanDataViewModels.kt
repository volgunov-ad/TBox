package vad.dashing.tbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlin.Boolean
import kotlin.collections.List

class CanDataViewModel : ViewModel() {
    val odometer: StateFlow<UInt?> = CanDataRepository.odometer
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val distanceToNextMaintenance: StateFlow<UInt?> = CanDataRepository.distanceToNextMaintenance
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val distanceToFuelEmpty: StateFlow<UInt?> = CanDataRepository.distanceToFuelEmpty
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val breakingForce: StateFlow<UInt?> = CanDataRepository.breakingForce
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val engineRPM: StateFlow<Float?> = CanDataRepository.engineRPM
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val param1: StateFlow<Float?> = CanDataRepository.param1
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val param2: StateFlow<Float?> = CanDataRepository.param2
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val param3: StateFlow<Float?> = CanDataRepository.param3
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val param4: StateFlow<Float?> = CanDataRepository.param4
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val carSpeed: StateFlow<Float?> = CanDataRepository.carSpeed
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val carSpeedAccurate: StateFlow<Float?> = CanDataRepository.carSpeedAccurate
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val voltage: StateFlow<Float?> = CanDataRepository.voltage
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val fuelLevelPercentage: StateFlow<UInt?> = CanDataRepository.fuelLevelPercentage
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val fuelLevelPercentageFiltered: StateFlow<UInt?> = CanDataRepository.fuelLevelPercentageFiltered
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val cruiseSetSpeed: StateFlow<UInt?> = CanDataRepository.cruiseSetSpeed
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val wheelsSpeed: StateFlow<Wheels> = CanDataRepository.wheelsSpeed
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = Wheels()
        )

    val wheelsPressure: StateFlow<Wheels> = CanDataRepository.wheelsPressure
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = Wheels()
        )

    val wheelsTemperature: StateFlow<Wheels> = CanDataRepository.wheelsTemperature
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = Wheels()
        )

    val steerAngle: StateFlow<Float?> = CanDataRepository.steerAngle
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val steerSpeed: StateFlow<Int?> = CanDataRepository.steerSpeed
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val climateSetTemperature1: StateFlow<Float?> = CanDataRepository.climateSetTemperature1
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val engineTemperature: StateFlow<Float?> = CanDataRepository.engineTemperature
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )


    val gearBoxMode: StateFlow<String> = CanDataRepository.gearBoxMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ""
        )

    val gearBoxCurrentGear: StateFlow<Int?> = CanDataRepository.gearBoxCurrentGear
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val gearBoxChangeGear: StateFlow<Boolean?> = CanDataRepository.gearBoxChangeGear
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val gearBoxPreparedGear: StateFlow<Int?> = CanDataRepository.gearBoxPreparedGear
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val gearBoxOilTemperature: StateFlow<Int?> = CanDataRepository.gearBoxOilTemperature
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val gearBoxDriveMode: StateFlow<String> = CanDataRepository.gearBoxDriveMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ""
        )

    val gearBoxWork: StateFlow<String> = CanDataRepository.gearBoxWork
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ""
        )

    val frontLeftSeatMode: StateFlow<UInt?> = CanDataRepository.frontLeftSeatMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val frontRightSeatMode: StateFlow<UInt?> = CanDataRepository.frontRightSeatMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val outsideTemperature: StateFlow<Float?> = CanDataRepository.outsideTemperature
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val insideTemperature: StateFlow<Float?> = CanDataRepository.insideTemperature
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val isWindowsBlocked: StateFlow<Boolean?> = CanDataRepository.isWindowsBlocked
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val canFramesStructured: StateFlow<Map<String, List<CanFrame>>> = CanDataRepository.canFramesStructured
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    val motorHoursTrip: StateFlow<Float?> = CanDataRepository.motorHoursTrip
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
}

fun seatModeToString(seatMode: UInt?): String {
    if (seatMode == null) return ""
    return when (seatMode) {
        1u -> {
            "выключено"
        }
        2u -> {
            "обогрев 1"
        }
        3u -> {
            "обогрев 2"
        }
        4u -> {
            "обогрев 3"
        }
        5u -> {
            "вентиляция 1"
        }
        6u -> {
            "вентиляция 2"
        }
        7u -> {
            "вентиляция 3"
        }
        else -> {
            seatMode.toString()
        }
    }
}