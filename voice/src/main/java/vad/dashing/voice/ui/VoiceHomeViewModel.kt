package vad.dashing.voice.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vad.dashing.voice.api.ExternalApiClient
import vad.dashing.voice.settings.VoiceConnectionSettings
import vad.dashing.voice.settings.VoiceSettingsRepository

data class VoiceHomeUiState(
    val host: String = VoiceConnectionSettings.DEFAULT_HOST,
    val portText: String = VoiceConnectionSettings.DEFAULT_PORT.toString(),
    val token: String = "",
    val statusMessage: String = "",
    val busy: Boolean = false,
    val lastHealthOk: Boolean? = null,
)

class VoiceHomeViewModel(
    application: Application,
    private val settingsRepository: VoiceSettingsRepository = VoiceSettingsRepository(application),
    private val apiClient: ExternalApiClient = ExternalApiClient(),
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(VoiceHomeUiState())
    val uiState: StateFlow<VoiceHomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update {
                    it.copy(
                        host = settings.host,
                        portText = settings.port.toString(),
                        token = settings.accessToken,
                    )
                }
            }
        }
    }

    fun onHostChange(value: String) {
        _uiState.update { it.copy(host = value) }
    }

    fun onPortChange(value: String) {
        _uiState.update { it.copy(portText = value.filter { ch -> ch.isDigit() }.take(5)) }
    }

    fun onTokenChange(value: String) {
        _uiState.update { it.copy(token = value) }
    }

    fun saveSettings() {
        val state = _uiState.value
        val port = state.portText.toIntOrNull() ?: VoiceConnectionSettings.DEFAULT_PORT
        viewModelScope.launch {
            settingsRepository.save(state.host, port, state.token)
            _uiState.update { it.copy(statusMessage = "Сохранено") }
        }
    }

    fun checkHealth() {
        val state = _uiState.value
        val port = state.portText.toIntOrNull() ?: VoiceConnectionSettings.DEFAULT_PORT
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, statusMessage = "Проверка…") }
            val result = withContext(Dispatchers.IO) {
                apiClient.health(state.host, port)
            }
            result.fold(
                onSuccess = { health ->
                    if (!health.ok) {
                        _uiState.update {
                            it.copy(
                                busy = false,
                                lastHealthOk = false,
                                statusMessage = "Ответ без ok=true",
                            )
                        }
                        return@fold
                    }
                    _uiState.update {
                        it.copy(
                            busy = false,
                            lastHealthOk = true,
                            statusMessage = "OK — API v${health.apiVersion}, " +
                                "каталог v${health.catalogVersion}, app ${health.appVersion}" +
                                if (!health.serverEnabled) " (serverEnabled=false)" else "",
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            busy = false,
                            lastHealthOk = false,
                            statusMessage = "Нет связи: ${error.message ?: error.javaClass.simpleName}",
                        )
                    }
                },
            )
        }
    }
}

class VoiceHomeViewModelFactory(
    private val application: Application,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VoiceHomeViewModel::class.java)) {
            return VoiceHomeViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel ${modelClass.name}")
    }
}
