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
            statusText = "MB-CAN: библиотеки подгружаются только по кнопке «Подключить»"
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
        viewModelScope.launch {
            _uiState.update { it.copy(statusText = "Загрузка библиотек…", lastError = null) }
            if (!MbCanNative.ensureLoaded()) {
                _uiState.update {
                    it.copy(
                        statusText = "MB-CAN недоступен на этом устройстве",
                        lastError = "Не удалось загрузить libmbcanclient/libmbCan (см. logcat: MbCanNative)",
                    )
                }
                return@launch
            }
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
            } catch (t: Throwable) {
                Log.e(TAG, "MB-CAN connect failed", t)
                _uiState.update {
                    it.copy(
                        statusText = "Ошибка MB-CAN",
                        lastError = t.message ?: t.toString(),
                    )
                }
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch(Dispatchers.Default) {
            if (!MbCanNative.isLoaded()) {
                _uiState.update {
                    it.copy(
                        statusText = "Отключено",
                        subscribeReturnCode = null,
                        speedKmh = null,
                        gear = null,
                    )
                }
                return@launch
            }
            try {
                MBCanClient.nativeCanUnInit()
            } catch (t: Throwable) {
                Log.e(TAG, "nativeCanUnInit", t)
            }
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
        if (!MbCanNative.isLoaded()) return
        try {
            MBCanClient.nativeCanUnInit()
        } catch (t: Throwable) {
            Log.e(TAG, "nativeCanUnInit in onCleared", t)
        }
    }

    companion object {
        private const val TAG = "MbCanViewModel"
    }
}
