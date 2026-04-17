package vad.dashing.tbox

import com.mengbo.mbCan.MBCanEngine
import com.mengbo.mbCan.MbCanEngineCallbackRelay
import com.mengbo.mbCan.defines.MBCanDataType
import com.mengbo.mbCan.entity.MBCanTotalOdometer
import com.mengbo.mbCan.entity.MBCanVehicleConsumption
import com.mengbo.mbCan.entity.MBCanVehicleEngine
import com.mengbo.mbCan.entity.MBCanVehicleFuelLevel
import com.mengbo.mbCan.entity.MBCanVehicleSpeed
import com.mengbo.mbCan.entity.MBCanVehicleSteeringAngle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Head-unit MB-CAN readings **in parallel** with TBox UDP → [CanDataRepository].
 * Does not touch [CanDataRepository] or CAN frame timestamps; UI or debug code can collect these flows.
 */
object MbCanParallelRepository {

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _vehicleSpeed = MutableStateFlow<Float?>(null)
    val vehicleSpeed: StateFlow<Float?> = _vehicleSpeed.asStateFlow()

    private val _gear = MutableStateFlow<Int?>(null)
    val gear: StateFlow<Int?> = _gear.asStateFlow()

    private val _steerAngleDeg = MutableStateFlow<Float?>(null)
    val steerAngleDeg: StateFlow<Float?> = _steerAngleDeg.asStateFlow()

    private val _steerRateDegS = MutableStateFlow<Int?>(null)
    val steerRateDegS: StateFlow<Int?> = _steerRateDegS.asStateFlow()

    private val _engineRpm = MutableStateFlow<Float?>(null)
    val engineRpm: StateFlow<Float?> = _engineRpm.asStateFlow()

    private val _engineCoolantC = MutableStateFlow<Float?>(null)
    val engineCoolantC: StateFlow<Float?> = _engineCoolantC.asStateFlow()

    private val _odometerKm = MutableStateFlow<Float?>(null)
    val odometerKm: StateFlow<Float?> = _odometerKm.asStateFlow()

    private val _fuelLevelPercent = MutableStateFlow<Int?>(null)
    val fuelLevelPercent: StateFlow<Int?> = _fuelLevelPercent.asStateFlow()

    private val _distanceToEmptyKm = MutableStateFlow<Float?>(null)
    val distanceToEmptyKm: StateFlow<Float?> = _distanceToEmptyKm.asStateFlow()

    private val _avgFuelConsumption = MutableStateFlow<Float?>(null)
    val avgFuelConsumption: StateFlow<Float?> = _avgFuelConsumption.asStateFlow()

    private val _lastNativeType = MutableStateFlow<Int?>(null)
    val lastNativeType: StateFlow<Int?> = _lastNativeType.asStateFlow()

    fun setEnabled(on: Boolean) {
        _enabled.value = on
        if (!on) {
            MbCanEngineCallbackRelay.listener = null
            clearValues()
        }
    }

    fun attachNativeRelay() {
        MbCanEngineCallbackRelay.listener = cb@{ type, data ->
            if (!_enabled.value) return@cb
            if (data != null) {
                _lastNativeType.value = type
                applyTyped(type, data)
            }
        }
    }

    fun applyPoll(engine: MBCanEngine) {
        if (!_enabled.value) return
        engine.getMbCanData(MBCanDataType.eMBCAN_VEHICLE_SPEED.value, MBCanVehicleSpeed::class.java)
            ?.let { applyVehicleSpeed(it) }
        engine.getMbCanData(MBCanDataType.eMBCAN_VEHICLE_GEAR.value, MBCanVehicleSpeed::class.java)
            ?.let { applyGear(it) }
        engine.getMbCanData(MBCanDataType.eMBCAN_VEHICLE_STEERING_ANGLE.value, MBCanVehicleSteeringAngle::class.java)
            ?.let { applySteering(it) }
        engine.getMbCanData(MBCanDataType.eMBCAN_VEHICLE_ENGINE.value, MBCanVehicleEngine::class.java)
            ?.let { applyEngine(it) }
        engine.getMbCanData(MBCanDataType.eMBCAN_VEHICLE_ENGINE_GEAR.value, MBCanVehicleEngine::class.java)
            ?.let { applyEngine(it) }
        engine.getMbCanData(MBCanDataType.eMBCAN_VEHICLE_TOTALODOMETER.value, MBCanTotalOdometer::class.java)
            ?.let { applyOdometer(it) }
        engine.getMbCanData(MBCanDataType.eMBCAN_VEHICLE_FUELLEVEL.value, MBCanVehicleFuelLevel::class.java)
            ?.let { applyFuel(it) }
        engine.getMbCanData(MBCanDataType.eMBCAN_VEHICLE_CONSUMPTION.value, MBCanVehicleConsumption::class.java)
            ?.let { applyConsumption(it) }
    }

    private fun applyTyped(type: Int, data: Any) {
        when (type) {
            MBCanDataType.eMBCAN_VEHICLE_SPEED.value ->
                if (data is MBCanVehicleSpeed) applyVehicleSpeed(data)
            MBCanDataType.eMBCAN_VEHICLE_GEAR.value ->
                if (data is MBCanVehicleSpeed) applyGear(data)
            MBCanDataType.eMBCAN_VEHICLE_STEERING_ANGLE.value ->
                if (data is MBCanVehicleSteeringAngle) applySteering(data)
            MBCanDataType.eMBCAN_VEHICLE_ENGINE.value,
            MBCanDataType.eMBCAN_VEHICLE_ENGINE_GEAR.value,
            -> if (data is MBCanVehicleEngine) applyEngine(data)
            MBCanDataType.eMBCAN_VEHICLE_TOTALODOMETER.value ->
                if (data is MBCanTotalOdometer) applyOdometer(data)
            MBCanDataType.eMBCAN_VEHICLE_FUELLEVEL.value ->
                if (data is MBCanVehicleFuelLevel) applyFuel(data)
            MBCanDataType.eMBCAN_VEHICLE_CONSUMPTION.value ->
                if (data is MBCanVehicleConsumption) applyConsumption(data)
            else -> Unit
        }
    }

    private fun applyVehicleSpeed(v: MBCanVehicleSpeed) {
        val spd = v.speed
        if (spd.isFinite() && spd >= 0f) _vehicleSpeed.value = spd
    }

    private fun applyGear(v: MBCanVehicleSpeed) {
        val g = v.gear.toInt() and 0xFF
        if (g != 0) _gear.value = g
    }

    private fun applySteering(s: MBCanVehicleSteeringAngle) {
        val ang = s.steeringAngle
        if (ang.isFinite()) _steerAngleDeg.value = ang
        val rate = s.steeringAngleSpeed
        if (rate.isFinite()) _steerRateDegS.value = rate.roundToInt()
    }

    private fun applyEngine(e: MBCanVehicleEngine) {
        val rpmCandidate = e.getfSpeed()
        val rpm = when {
            rpmCandidate > 20000f -> rpmCandidate / 4f
            rpmCandidate > 0f -> rpmCandidate
            else -> 0f
        }
        if (rpmCandidate.isFinite()) _engineRpm.value = rpm
        val coolant = e.getfTemperture()
        if (coolant.isFinite()) _engineCoolantC.value = coolant
    }

    private fun applyOdometer(o: MBCanTotalOdometer) {
        val km = o.getOdometer()
        if (km.isFinite() && km >= 0f) _odometerKm.value = km
    }

    private fun applyFuel(f: MBCanVehicleFuelLevel) {
        val pct = f.getFuelLevel().toInt() and 0xFF
        if (pct in 0..100) _fuelLevelPercent.value = pct
        val dte = f.getDistenceToEmpty()
        if (dte.isFinite() && dte > 0f) _distanceToEmptyKm.value = dte
    }

    private fun applyConsumption(c: MBCanVehicleConsumption) {
        val v = c.getfAvgFuCns()
        if (v.isFinite() && abs(v) < 1000f) _avgFuelConsumption.value = v
    }

    private fun clearValues() {
        _vehicleSpeed.value = null
        _gear.value = null
        _steerAngleDeg.value = null
        _steerRateDegS.value = null
        _engineRpm.value = null
        _engineCoolantC.value = null
        _odometerKm.value = null
        _fuelLevelPercent.value = null
        _distanceToEmptyKm.value = null
        _avgFuelConsumption.value = null
        _lastNativeType.value = null
    }
}
