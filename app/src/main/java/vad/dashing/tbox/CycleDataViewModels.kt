package vad.dashing.tbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlin.Boolean
import kotlin.collections.List

class CycleDataViewModel : ViewModel() {
    val odometer: StateFlow<UInt?> = CycleDataRepository.odometer
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val carSpeed: StateFlow<Float?> = CycleDataRepository.carSpeed
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val voltage: StateFlow<Float?> = CycleDataRepository.voltage
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val longitudinalAcceleration: StateFlow<Float?> = CycleDataRepository.longitudinalAcceleration
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val lateralAcceleration: StateFlow<Float?> = CycleDataRepository.lateralAcceleration
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val yawRate: StateFlow<Float?> = CycleDataRepository.yawRate
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val pressure1: StateFlow<Float?> = CycleDataRepository.pressure1
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val pressure2: StateFlow<Float?> = CycleDataRepository.pressure2
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val pressure3: StateFlow<Float?> = CycleDataRepository.pressure3
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val pressure4: StateFlow<Float?> = CycleDataRepository.pressure4
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val temperature1: StateFlow<Float?> = CycleDataRepository.temperature1
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val temperature2: StateFlow<Float?> = CycleDataRepository.temperature2
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val temperature3: StateFlow<Float?> = CycleDataRepository.temperature3
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val temperature4: StateFlow<Float?> = CycleDataRepository.temperature4
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val engineRPM: StateFlow<Float?> = CycleDataRepository.engineRPM
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
}