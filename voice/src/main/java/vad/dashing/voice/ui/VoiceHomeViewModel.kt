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
import vad.dashing.voice.api.ApiCatalog
import vad.dashing.voice.api.AutomationSummary
import vad.dashing.voice.api.ExternalApiClient
import vad.dashing.voice.nlu.AliasNluMatcher
import vad.dashing.voice.nlu.SignalAnswerFormatter
import vad.dashing.voice.nlu.VoiceIntent
import vad.dashing.voice.settings.VoiceConnectionSettings
import vad.dashing.voice.settings.VoiceSettingsRepository

data class VoiceHomeUiState(
    val host: String = VoiceConnectionSettings.DEFAULT_HOST,
    val portText: String = VoiceConnectionSettings.DEFAULT_PORT.toString(),
    val token: String = "",
    val phrase: String = "сколько градусов на улице",
    val statusMessage: String = "",
    val answerMessage: String = "",
    val catalogSummary: String = "",
    val busy: Boolean = false,
    val lastHealthOk: Boolean? = null,
)

class VoiceHomeViewModel(
    application: Application,
    private val settingsRepository: VoiceSettingsRepository = VoiceSettingsRepository(application),
    private val apiClient: ExternalApiClient = ExternalApiClient(),
    private val nlu: AliasNluMatcher = AliasNluMatcher(),
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(VoiceHomeUiState())
    val uiState: StateFlow<VoiceHomeUiState> = _uiState.asStateFlow()

    private var cachedCatalog: ApiCatalog? = null
    private var cachedAutomations: List<AutomationSummary> = emptyList()

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

    fun onPhraseChange(value: String) {
        _uiState.update { it.copy(phrase = value) }
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
        val port = portOrDefault(state)
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, statusMessage = "Проверка…") }
            val result = withContext(Dispatchers.IO) {
                apiClient.health(state.host, port)
            }
            result.fold(
                onSuccess = { health ->
                    if (!health.ok) {
                        _uiState.update {
                            it.copy(busy = false, lastHealthOk = false, statusMessage = "Ответ без ok=true")
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

    fun refreshCatalog() {
        val state = _uiState.value
        val port = portOrDefault(state)
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, statusMessage = "Загрузка каталога…") }
            val catalogResult = withContext(Dispatchers.IO) {
                apiClient.catalog(state.host, port, state.token)
            }
            val autosResult = withContext(Dispatchers.IO) {
                apiClient.automations(state.host, port, state.token)
            }
            catalogResult.fold(
                onSuccess = { catalog ->
                    cachedCatalog = catalog
                    cachedAutomations = autosResult.getOrDefault(emptyList())
                    _uiState.update {
                        it.copy(
                            busy = false,
                            lastHealthOk = true,
                            catalogSummary = "Каталог v${catalog.catalogVersion}: " +
                                "сигналов ${catalog.signals.size}, действий ${catalog.actionTypes.size}, " +
                                "автоматизаций ${cachedAutomations.size}",
                            statusMessage = "Каталог обновлён",
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            busy = false,
                            lastHealthOk = false,
                            statusMessage = "Каталог: ${error.message ?: error.javaClass.simpleName}",
                        )
                    }
                },
            )
        }
    }

    fun runPhrase() {
        val state = _uiState.value
        val port = portOrDefault(state)
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, answerMessage = "", statusMessage = "Разбор фразы…") }
            val catalog = cachedCatalog ?: run {
                val loaded = withContext(Dispatchers.IO) {
                    apiClient.catalog(state.host, port, state.token)
                }.getOrElse { error ->
                    _uiState.update {
                        it.copy(
                            busy = false,
                            lastHealthOk = false,
                            statusMessage = "Каталог: ${error.message ?: error.javaClass.simpleName}",
                        )
                    }
                    return@launch
                }
                cachedCatalog = loaded
                cachedAutomations = withContext(Dispatchers.IO) {
                    apiClient.automations(state.host, port, state.token)
                }.getOrDefault(emptyList())
                loaded
            }

            when (val intent = nlu.match(state.phrase, catalog, cachedAutomations)) {
                is VoiceIntent.QuerySignal -> {
                    val source = SignalAnswerFormatter.preferredSource(intent.signal)
                    if (source == null) {
                        _uiState.update {
                            it.copy(
                                busy = false,
                                answerMessage = "У сигнала ${intent.signal.id} нет источников",
                                statusMessage = "intent=query alias=${intent.matchedAlias}",
                            )
                        }
                        return@launch
                    }
                    val snapshot = withContext(Dispatchers.IO) {
                        apiClient.signals(
                            host = state.host,
                            port = port,
                            token = state.token,
                            ids = listOf(intent.signal.id),
                            source = source,
                        )
                    }.getOrElse { error ->
                        _uiState.update {
                            it.copy(
                                busy = false,
                                lastHealthOk = false,
                                statusMessage = "Signals: ${error.message ?: error.javaClass.simpleName}",
                            )
                        }
                        return@launch
                    }
                    val reading = snapshot.signals.firstOrNull { it.id == intent.signal.id }
                    val answer = SignalAnswerFormatter.format(intent.signal, reading)
                    _uiState.update {
                        it.copy(
                            busy = false,
                            lastHealthOk = true,
                            answerMessage = answer,
                            statusMessage = "query ${intent.signal.id}@$source (alias «${intent.matchedAlias}»)",
                            catalogSummary = it.catalogSummary.ifBlank {
                                "Каталог v${catalog.catalogVersion}: сигналов ${catalog.signals.size}"
                            },
                        )
                    }
                }

                is VoiceIntent.InvokeAction -> {
                    val name = intent.action.label
                        ?: intent.action.actionType
                        ?: intent.action.type
                    _uiState.update {
                        it.copy(
                            busy = false,
                            answerMessage = "Распознано действие «$name». Invoke — на следующем этапе.",
                            statusMessage = "intent=invoke alias=${intent.matchedAlias}",
                        )
                    }
                }

                is VoiceIntent.RunAutomation -> {
                    _uiState.update {
                        it.copy(
                            busy = false,
                            answerMessage = "Распознано правило «${intent.automation.name}». RunNow — на следующем этапе.",
                            statusMessage = "intent=run id=${intent.automation.id}",
                        )
                    }
                }

                VoiceIntent.Unknown -> {
                    _uiState.update {
                        it.copy(
                            busy = false,
                            answerMessage = "Не понял фразу",
                            statusMessage = "intent=unknown",
                        )
                    }
                }
            }
        }
    }

    private fun portOrDefault(state: VoiceHomeUiState): Int =
        state.portText.toIntOrNull() ?: VoiceConnectionSettings.DEFAULT_PORT
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
