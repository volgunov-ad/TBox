package vad.dashing.tbox

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Dashboard / floating panel widget data key for Mengbo steering wheel heat. */
const val STEERING_WHEEL_HEAT_WIDGET_DATA_KEY = "steeringWheelHeatWidget"

/**
 * Head-unit vehicle properties via Mengbo MB-CAN stack (native libs from [mbcan/]).
 * JNI ordinals match [com.mengbo.mbCan.defines.MBVehicleProperty] extracted from MB_FactoryMode.apk.
 */
object MbVehicleRepository {
    private val _clientAvailable = MutableStateFlow(false)
    val clientAvailable: StateFlow<Boolean> = _clientAvailable.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    /** Raw int from [com.mengbo.mbCan.MBCanEngine.canGetVehicleParam]. */
    private val _mfsHeatParam = MutableStateFlow<Int?>(null)
    val mfsHeatParam: StateFlow<Int?> = _mfsHeatParam.asStateFlow()

    /** Optional raw payload from [com.mengbo.mbCan.MBCanEngine.canGetVehicleValue]. */
    private val _mfsHeatValueBytes = MutableStateFlow<ByteArray?>(null)
    val mfsHeatValueBytes: StateFlow<ByteArray?> = _mfsHeatValueBytes.asStateFlow()

    /** Best-effort on/off derived for UI. */
    private val _steeringWheelHeatOn = MutableStateFlow<Boolean?>(null)
    val steeringWheelHeatOn: StateFlow<Boolean?> = _steeringWheelHeatOn.asStateFlow()

    fun setClientAvailable(available: Boolean) {
        _clientAvailable.value = available
        if (!available) {
            _mfsHeatParam.value = null
            _mfsHeatValueBytes.value = null
            _steeringWheelHeatOn.value = null
            _lastError.value = null
        }
    }

    fun setLastError(message: String?) {
        _lastError.value = message
    }

    fun updateMfsHeatRead(param: Int?, valueBytes: ByteArray?) {
        _mfsHeatParam.value = param
        _mfsHeatValueBytes.value = valueBytes?.copyOf()
        _steeringWheelHeatOn.value = deriveOnFromRead(param, valueBytes)
    }

    private fun deriveOnFromRead(param: Int?, valueBytes: ByteArray?): Boolean? {
        valueBytes?.let { b ->
            when (b.size) {
                1 -> return b[0].toInt() and 0xFF != 0
                4 -> {
                    val v = (b[0].toInt() and 0xFF) or
                        ((b[1].toInt() and 0xFF) shl 8) or
                        ((b[2].toInt() and 0xFF) shl 16) or
                        ((b[3].toInt() and 0xFF) shl 24)
                    return v != 0
                }
                else -> if (b.isNotEmpty()) return b.any { it.toInt() != 0 }
            }
        }
        param?.let { p ->
            if (p == 0 || p == 1) return p == 1
            if (p > 1) return true
        }
        return null
    }

    fun applyOptimisticSteeringHeat(on: Boolean) {
        _steeringWheelHeatOn.value = on
    }
}
