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
import vad.dashing.voice.api.InvokeActionPayloadBuilder
import vad.dashing.voice.nlu.AliasNluMatcher
import vad.dashing.voice.nlu.SignalAnswerFormatter
import vad.dashing.voice.nlu.VoiceIntent
import vad.dashing.voice.settings.AccessTokenNormalizer
import vad.dashing.voice.settings.VoiceConnectionSettings
import vad.dashing.voice.settings.VoiceSettingsRepository
import vad.dashing.voice.stt.ListenEndReason
import vad.dashing.voice.stt.VoiceStt
import vad.dashing.voice.stt.VoskVoiceStt
import vad.dashing.voice.tts.PiperVoiceTts
import vad.dashing.voice.tts.VoiceTts

data class VoiceHomeUiState(
    val host: String = VoiceConnectionSettings.DEFAULT_HOST,
    val portText: String = VoiceConnectionSettings.DEFAULT_PORT.toString(),
    val token: String = "",
    val phrase: String = "сколько градусов на улице",
    val statusMessage: String = "",
    val answerMessage: String = "",
    val catalogSummary: String = "",
    val busy: Boolean = false,
    val speaking: Boolean = false,
    val listening: Boolean = false,
    val lastHealthOk: Boolean? = null,
)

class VoiceHomeViewModel(
    application: Application,
    private val settingsRepository: VoiceSettingsRepository = VoiceSettingsRepository(application),
    private val apiClient: ExternalApiClient = ExternalApiClient(),
    private val nlu: AliasNluMatcher = AliasNluMatcher(),
    private val tts: VoiceTts = PiperVoiceTts.createOrNoOp(application),
    private val stt: VoiceStt = VoskVoiceStt.createOrNoOp(application),
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(VoiceHomeUiState())
    val uiState: StateFlow<VoiceHomeUiState> = _uiState.asStateFlow()

    private var cachedCatalog: ApiCatalog? = null
    private var cachedAutomations: List<AutomationSummary> = emptyList()

    @Volatile
    private var lastHeardText: String = ""

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
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { tts.ensureReady() }
            runCatching { stt.ensureReady() }
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

    fun onMicPermissionDenied() {
        _uiState.update {
            it.copy(
                statusMessage = "Нужен доступ к микрофону",
                lastHealthOk = false,
            )
        }
    }

    fun saveSettings() {
        val state = _uiState.value
        val port = state.portText.toIntOrNull() ?: VoiceConnectionSettings.DEFAULT_PORT
        val token = AccessTokenNormalizer.normalize(state.token)
        viewModelScope.launch {
            settingsRepository.save(state.host, port, token)
            _uiState.update {
                it.copy(
                    token = token,
                    statusMessage = "Сохранено",
                )
            }
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
        val token = AccessTokenNormalizer.normalize(state.token)
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, statusMessage = "Загрузка каталога…") }
            val catalogResult = withContext(Dispatchers.IO) {
                apiClient.catalog(state.host, port, token)
            }
            val autosResult = withContext(Dispatchers.IO) {
                apiClient.automations(state.host, port, token)
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

    /** Toggle PTT listening. Call only after RECORD_AUDIO is granted. */
    fun toggleListen(source: String = "ui") {
        if (_uiState.value.listening) {
            _uiState.update { it.copy(statusMessage = "Остановка…") }
            // SpeechService.stop() joins the recorder thread — never call it on Main.
            viewModelScope.launch(Dispatchers.IO) {
                stt.stopListening()
            }
            return
        }
        startListen(source)
    }

    fun startListen(source: String = "ui") {
        if (_uiState.value.listening) return
        viewModelScope.launch(Dispatchers.IO) {
            tts.stop()
            stt.ensureReady()
            if (!stt.isReady) {
                _uiState.update {
                    it.copy(
                        statusMessage = "STT: ${stt.lastError ?: "модель не готова"}",
                        lastHealthOk = false,
                    )
                }
                return@launch
            }
            lastHeardText = ""
            _uiState.update {
                it.copy(
                    listening = true,
                    speaking = false,
                    answerMessage = "",
                    statusMessage = "Слушаю ($source)…",
                    phrase = "",
                )
            }
            stt.startListening(
                onPartial = { text ->
                    lastHeardText = text
                    _uiState.update {
                        it.copy(phrase = text, statusMessage = "Слышу: $text")
                    }
                },
                onFinal = { text ->
                    if (text.isNotBlank()) {
                        lastHeardText = text
                        _uiState.update { it.copy(phrase = text) }
                    }
                },
                onEnded = { reason ->
                    _uiState.update { it.copy(listening = false) }
                    val heard = lastHeardText.trim()
                    when {
                        heard.isNotBlank() -> {
                            _uiState.update {
                                it.copy(
                                    phrase = heard,
                                    statusMessage = "Распознано ($reason): $heard",
                                )
                            }
                            runPhrase()
                        }
                        reason == ListenEndReason.ERROR -> {
                            _uiState.update {
                                it.copy(
                                    statusMessage = "STT ошибка: ${stt.lastError ?: "unknown"}",
                                    lastHealthOk = false,
                                )
                            }
                        }
                        else -> {
                            _uiState.update {
                                it.copy(statusMessage = "Ничего не услышал ($reason)")
                            }
                        }
                    }
                },
            )
        }
    }

    fun runPhrase() {
        val state = _uiState.value
        val port = portOrDefault(state)
        val token = AccessTokenNormalizer.normalize(state.token)
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, answerMessage = "", statusMessage = "Разбор фразы…") }
            val catalog = cachedCatalog ?: run {
                val loaded = withContext(Dispatchers.IO) {
                    apiClient.catalog(state.host, port, token)
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
                    apiClient.automations(state.host, port, token)
                }.getOrDefault(emptyList())
                loaded
            }

            when (val intent = nlu.match(state.phrase, catalog, cachedAutomations)) {
                is VoiceIntent.QuerySignal -> {
                    val source = SignalAnswerFormatter.preferredSource(intent.signal)
                    if (source == null) {
                        val msg = "У сигнала ${intent.signal.id} нет источников"
                        _uiState.update {
                            it.copy(
                                busy = false,
                                answerMessage = msg,
                                statusMessage = "intent=query alias=${intent.matchedAlias}",
                            )
                        }
                        speakAnswer(msg)
                        return@launch
                    }
                    val snapshot = withContext(Dispatchers.IO) {
                        apiClient.signals(
                            host = state.host,
                            port = port,
                            token = token,
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
                    speakAnswer(answer)
                }

                is VoiceIntent.InvokeAction -> {
                    val name = intent.action.label
                        ?: intent.action.actionType
                        ?: intent.action.type
                    val payload = InvokeActionPayloadBuilder.build(intent.action).getOrElse { error ->
                        val msg = error.message ?: "Нельзя вызвать действие"
                        _uiState.update {
                            it.copy(
                                busy = false,
                                answerMessage = msg,
                                statusMessage = "intent=invoke alias=${intent.matchedAlias}",
                            )
                        }
                        speakAnswer(msg)
                        return@launch
                    }
                    val body = InvokeActionPayloadBuilder.wrapActions(payload)
                    val result = withContext(Dispatchers.IO) {
                        apiClient.invokeActions(state.host, port, token, body)
                    }.getOrElse { error ->
                        val msg = "Действие не выполнено: ${error.message ?: error.javaClass.simpleName}"
                        _uiState.update {
                            it.copy(
                                busy = false,
                                lastHealthOk = false,
                                answerMessage = msg,
                                statusMessage = "invoke failed",
                            )
                        }
                        speakAnswer(msg)
                        return@launch
                    }
                    val first = result.results.firstOrNull()
                    val ok = first?.success == true
                    val detail = first?.message?.takeIf { it.isNotBlank() }
                    val msg = when {
                        ok && detail != null -> "Готово: $detail"
                        ok -> "Сделано: $name"
                        detail != null -> "Ошибка: $detail"
                        else -> "Действие «$name» не выполнено"
                    }
                    _uiState.update {
                        it.copy(
                            busy = false,
                            lastHealthOk = ok,
                            answerMessage = msg,
                            statusMessage = "intent=invoke alias=${intent.matchedAlias} ok=$ok",
                        )
                    }
                    speakAnswer(msg)
                }

                is VoiceIntent.RunAutomation -> {
                    val result = withContext(Dispatchers.IO) {
                        apiClient.runAutomation(state.host, port, token, intent.automation.id)
                    }.getOrElse { error ->
                        val msg = "Правило не запущено: ${error.message ?: error.javaClass.simpleName}"
                        _uiState.update {
                            it.copy(
                                busy = false,
                                lastHealthOk = false,
                                answerMessage = msg,
                                statusMessage = "run failed",
                            )
                        }
                        speakAnswer(msg)
                        return@launch
                    }
                    val msg = if (result.accepted) {
                        "Запускаю «${intent.automation.name}»"
                    } else {
                        result.message.ifBlank { "Правило «${intent.automation.name}» отклонено" }
                    }
                    _uiState.update {
                        it.copy(
                            busy = false,
                            lastHealthOk = result.accepted,
                            answerMessage = msg,
                            statusMessage = "intent=run id=${intent.automation.id} accepted=${result.accepted}",
                        )
                    }
                    speakAnswer(msg)
                }

                VoiceIntent.Unknown -> {
                    val msg = "Не понял фразу"
                    _uiState.update {
                        it.copy(
                            busy = false,
                            answerMessage = msg,
                            statusMessage = "intent=unknown",
                        )
                    }
                    speakAnswer(msg)
                }
            }
        }
    }

    fun stopSpeaking() {
        viewModelScope.launch(Dispatchers.IO) {
            tts.stop()
            _uiState.update { it.copy(speaking = false) }
        }
    }

    override fun onCleared() {
        stt.stopListening()
        stt.release()
        tts.stop()
        tts.release()
        super.onCleared()
    }

    private fun speakAnswer(text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(speaking = true) }
            try {
                tts.speak(text)
            } finally {
                _uiState.update { it.copy(speaking = false) }
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
