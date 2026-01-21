package vad.dashing.tbox

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object CycleDataRepository {

    private val _odometer = MutableStateFlow<UInt?>(null)
    val odometer: StateFlow<UInt?> = _odometer.asStateFlow()

    private val _carSpeed = MutableStateFlow<Float?>(null)
    val carSpeed: StateFlow<Float?> = _carSpeed.asStateFlow()

    private val _voltage = MutableStateFlow<Float?>(null)
    val voltage: StateFlow<Float?> = _voltage.asStateFlow()

    private val _longitudinalAcceleration = MutableStateFlow<Float?>(null)
    val longitudinalAcceleration: StateFlow<Float?> = _longitudinalAcceleration.asStateFlow()

    private val _lateralAcceleration = MutableStateFlow<Float?>(null)
    val lateralAcceleration: StateFlow<Float?> = _lateralAcceleration.asStateFlow()

    private val _yawRate = MutableStateFlow<Float?>(null)
    val yawRate: StateFlow<Float?> = _yawRate.asStateFlow()

    private val _pressure1 = MutableStateFlow<Float?>(null)
    val pressure1: StateFlow<Float?> = _pressure1.asStateFlow()

    private val _pressure2 = MutableStateFlow<Float?>(null)
    val pressure2: StateFlow<Float?> = _pressure2.asStateFlow()

    private val _pressure3 = MutableStateFlow<Float?>(null)
    val pressure3: StateFlow<Float?> = _pressure3.asStateFlow()

    private val _pressure4 = MutableStateFlow<Float?>(null)
    val pressure4: StateFlow<Float?> = _pressure4.asStateFlow()

    private val _temperature1 = MutableStateFlow<Float?>(null)
    val temperature1: StateFlow<Float?> = _temperature1.asStateFlow()

    private val _temperature2 = MutableStateFlow<Float?>(null)
    val temperature2: StateFlow<Float?> = _temperature2.asStateFlow()

    private val _temperature3 = MutableStateFlow<Float?>(null)
    val temperature3: StateFlow<Float?> = _temperature3.asStateFlow()

    private val _temperature4 = MutableStateFlow<Float?>(null)
    val temperature4: StateFlow<Float?> = _temperature4.asStateFlow()

    private val _engineRPM = MutableStateFlow<Float?>(null)
    val engineRPM: StateFlow<Float?> = _engineRPM.asStateFlow()

    fun updateVoltage(newValue: Float) {
        _voltage.value = newValue
    }

    fun updateCarSpeed(newValue: Float) {
        _carSpeed.value = newValue
    }

    fun updateOdometer(newValue: UInt) {
        _odometer.value = newValue
    }

    fun updateLongitudinalAcceleration(newValue: Float) {
        _longitudinalAcceleration.value = newValue
    }

    fun updateLateralAcceleration(newValue: Float) {
        _lateralAcceleration.value = newValue
    }

    fun updateYawRate(newValue: Float) {
        _yawRate.value = newValue
    }

    fun updatePressure1(newValue: Float) {
        _pressure1.value = newValue
    }

    fun updatePressure2(newValue: Float) {
        _pressure2.value = newValue
    }

    fun updatePressure3(newValue: Float) {
        _pressure3.value = newValue
    }

    fun updatePressure4(newValue: Float) {
        _pressure4.value = newValue
    }

    fun updateTemperature1(newValue: Float) {
        _temperature1.value = newValue
    }

    fun updateTemperature2(newValue: Float) {
        _temperature2.value = newValue
    }

    fun updateTemperature3(newValue: Float) {
        _temperature3.value = newValue
    }

    fun updateTemperature4(newValue: Float) {
        _temperature4.value = newValue
    }

    fun updateEngineRPM(newValue: Float) {
        _engineRPM.value = newValue
    }
}
