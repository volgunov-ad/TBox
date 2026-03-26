package vad.dashing.tbox.mbcan

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mengbo.mbCan.MBCanClient
import com.mengbo.mbCan.defines.MBCanDataType
import com.mengbo.mbCan.entity.MBCanSubscribeBase
import com.mengbo.mbCan.entity.MBCanVehicleSpeed
import com.mengbo.mbCan.interfaces.ICanBaseCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MbCanUiState(
    val statusText: String,
    val initReturnCode: Int? = null,
    val subscribeReturnCode: Int? = null,
    val speedKmh: Float? = null,
    val gear: Int? = null,
    val lastDataType: Int? = null,
    val lastError: String? = null,
)

/**
 * MB-CAN client (Mengbo stack) — same JNI entry points as in *Dashing Electric Heat* reference APK.
 * Requires OEM services + [vad.dashing.tbox.TboxApplication] to load {@code libmbcanclient} / {@code libmbCan}.
 */
class MbCanViewModel(application: Application) : AndroidViewModel(application) {

    private val client = MBCanClient()

    private val _uiState = MutableStateFlow(
        MbCanUiState(
            statusText = if (MbCanNative.librariesLoaded) {
                "Библиотеки загружены"
            } else {
                "Нативные библиотеки MB-CAN недоступны на этой сборке"
            }
        )
    )
    val uiState: StateFlow<MbCanUiState> = _uiState.asStateFlow()

    private val callback = ICanBaseCallback { dataType, data ->
        if (data is MBCanVehicleSpeed) {
            _uiState.update {
                it.copy(
                    lastDataType = dataType,
                    speedKmh = data.speed,
                    gear = data.gear.toInt(),
                    lastError = null,
                )
            }
        } else {
            _uiState.update { it.copy(lastDataType = dataType) }
        }
    }

    fun connect(subscribeIntervalMs: Int) {
        if (!MbCanNative.librariesLoaded) {
            _uiState.update { it.copy(lastError = "Нет arm64-v8a .so в APK") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(statusText = "Подключение…", lastError = null) }
            try {
                val initRc: Int
                val subRc: Int
                withContext(Dispatchers.Default) {
                    initRc = client.nativeCanInit(callback)
                    val subs = listOf(
                        MBCanSubscribeBase(
                            MBCanDataType.eMBCAN_VEHICLE_SPEED.ordinal,
                            subscribeIntervalMs,
                            callback
                        )
                    )
                    subRc = client.nativeCanSubscribe(subs)
                }
                _uiState.update {
                    it.copy(
                        statusText = "Подписка активна (скорость)",
                        initReturnCode = initRc,
                        subscribeReturnCode = subRc,
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "MB-CAN connect failed", e)
                _uiState.update {
                    it.copy(
                        statusText = "Ошибка",
                        lastError = e.message ?: e.toString(),
                    )
                }
            }
        }
    }

    fun disconnect() {
        if (!MbCanNative.librariesLoaded) return
        viewModelScope.launch(Dispatchers.Default) {
            runCatching {
                MBCanClient.nativeCanUnInit()
            }.onFailure { Log.e(TAG, "nativeCanUnInit", it) }
            _uiState.update {
                it.copy(
                    statusText = "Отключено",
                    subscribeReturnCode = null,
                    speedKmh = null,
                    gear = null,
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (MbCanNative.librariesLoaded) {
            runCatching { MBCanClient.nativeCanUnInit() }
                .onFailure { Log.e(TAG, "nativeCanUnInit in onCleared", it) }
        }
    }

    companion object {
        private const val TAG = "MbCanViewModel"
    }
}
