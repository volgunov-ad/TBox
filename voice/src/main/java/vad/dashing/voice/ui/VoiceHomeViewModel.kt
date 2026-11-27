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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vad.dashing.voice.api.ApiCatalog
import vad.dashing.voice.api.AutomationSummary
import vad.dashing.tbox.localaccess.LocalAccessClient
import vad.dashing.tbox.localaccess.LocalAccessContract
import vad.dashing.tbox.localaccess.LocalAccessResult
import vad.dashing.voice.api.ExternalApiClient
import vad.dashing.voice.api.ExternalApiHttpException
import vad.dashing.voice.api.InvokeActionPayloadBuilder
import vad.dashing.voice.nlu.AliasNluMatcher
import vad.dashing.voice.nlu.LocalIntentClassifier
import vad.dashing.voice.nlu.NgramLocalIntentClassifier
import vad.dashing.voice.nlu.SignalAnswerFormatter
import vad.dashing.voice.nlu.VoiceIntent
import vad.dashing.voice.llm.ClarifySession
import vad.dashing.voice.llm.CloudAgentOutcome
import vad.dashing.voice.llm.CloudBaseUrlNormalizer
import vad.dashing.voice.llm.CloudLlmPreset
import vad.dashing.voice.llm.CloudLlmSettings
import vad.dashing.voice.llm.OpenAiCompatibleClient
import vad.dashing.voice.llm.VoiceCloudAgent
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
    val cloudEnabled: Boolean = false,
    val cloudPrefer: Boolean = true,
    val cloudAllowPhrases: Boolean = false,
    val cloudAllowFreeform: Boolean = false,
    val cloudPreset: CloudLlmPreset = CloudLlmPreset.AITUNNEL,
    val cloudBaseUrl: String = CloudLlmPreset.AITUNNEL.defaultBaseUrl,
    val cloudApiKey: String = "",
    val cloudModel: String = "",
    val cloudModelIds: List<String> = emptyList(),
    val cloudStatusMessage: String = "",
    val cloudLastOk: Boolean? = null,
    val phrase: String = "сколько градусов на улице",
    val statusMessage: String = "",
    val answerMessage: String = "",
    val catalogSummary: String = "",
    val busy: Boolean = false,
    val speaking: Boolean = false,
    val listening: Boolean = false,
    val lastHealthOk: Boolean? = null,
    /** Waiting for one clarifying reply after a cloud clarify question. */
    val awaitingClarify: Boolean = false,
    val localClassifierEnabled: Boolean = true,
) {
    fun cloudSettings(): CloudLlmSettings =
        CloudLlmSettings(
            enabled = cloudEnabled,
            preferCloud = cloudPrefer,
            allowSendPhrases = cloudAllowPhrases,
            allowFreeformSpeak = cloudAllowFreeform,
            preset = cloudPreset,
            baseUrl = cloudBaseUrl,
            apiKey = cloudApiKey,
            model = cloudModel,
        )
}

class VoiceHomeViewModel(
    application: Application,
    private val settingsRepository: VoiceSettingsRepository = VoiceSettingsRepository(application),
    private val apiClient: ExternalApiClient = ExternalApiClient(),
    private val cloudClient: OpenAiCompatibleClient = OpenAiCompatibleClient(),
    private val cloudAgent: VoiceCloudAgent = VoiceCloudAgent(cloudClient),
    private val localClassifier: LocalIntentClassifier = NgramLocalIntentClassifier(),
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

    /** Old → new token, so later calls of one phrase skip the token Monitor already replaced. */
    @Volatile
    private var renewal: Pair<String, String>? = null

    @Volatile
    private var pendingClarify: ClarifySession? = null

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                val cloud = settings.cloud
                _uiState.update {
                    it.copy(
                        host = settings.connection.host,
                        portText = settings.connection.port.toString(),
                        token = settings.connection.accessToken,
                        cloudEnabled = cloud.enabled,
                        cloudPrefer = cloud.preferCloud,
                        cloudAllowPhrases = cloud.allowSendPhrases,
                        cloudAllowFreeform = cloud.allowFreeformSpeak,
                        cloudPreset = cloud.preset,
                        cloudBaseUrl = cloud.baseUrl,
                        cloudApiKey = cloud.apiKey,
                        cloudModel = cloud.model,
                        localClassifierEnabled = settings.localClassifierEnabled,
                    )
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { tts.ensureReady() }
            runCatching { stt.ensureReady() }
        }
        viewModelScope.launch {
            val saved = settingsRepository.settings.first()
            if (saved.connection.accessToken.isBlank() &&
                isLocalMonitor(saved.connection.host)
            ) {
                requestMonitorAccess()
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

    fun cancelClarify() {
        pendingClarify = null
        _uiState.update {
            it.copy(
                awaitingClarify = false,
                statusMessage = "Уточнение отменено",
            )
        }
    }

    fun onCloudEnabledChange(value: Boolean) {
        _uiState.update { it.copy(cloudEnabled = value) }
    }

    fun onCloudPreferChange(value: Boolean) {
        _uiState.update { it.copy(cloudPrefer = value) }
    }

    fun onCloudAllowPhrasesChange(value: Boolean) {
        _uiState.update { it.copy(cloudAllowPhrases = value) }
    }

    fun onCloudAllowFreeformChange(value: Boolean) {
        _uiState.update { it.copy(cloudAllowFreeform = value) }
    }

    fun onLocalClassifierEnabledChange(value: Boolean) {
        _uiState.update { it.copy(localClassifierEnabled = value) }
    }

    fun onCloudPresetChange(preset: CloudLlmPreset) {
        _uiState.update {
            val url = if (preset == CloudLlmPreset.CUSTOM) {
                it.cloudBaseUrl
            } else {
                preset.defaultBaseUrl
            }
            it.copy(cloudPreset = preset, cloudBaseUrl = url)
        }
    }

    fun onCloudBaseUrlChange(value: String) {
        _uiState.update { it.copy(cloudBaseUrl = value, cloudPreset = CloudLlmPreset.CUSTOM) }
    }

    fun onCloudApiKeyChange(value: String) {
        _uiState.update { it.copy(cloudApiKey = value) }
    }

    fun onCloudModelChange(value: String) {
        _uiState.update { it.copy(cloudModel = value) }
    }

    fun selectNextCloudModel() {
        val state = _uiState.value
        val ids = state.cloudModelIds
        if (ids.isEmpty()) return
        val index = ids.indexOf(state.cloudModel).let { if (it < 0) 0 else (it + 1) % ids.size }
        _uiState.update { it.copy(cloudModel = ids[index]) }
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
        val cloud = state.cloudSettings().copy(
            apiKey = AccessTokenNormalizer.normalize(state.cloudApiKey),
            baseUrl = CloudBaseUrlNormalizer.normalize(state.cloudBaseUrl),
            model = state.cloudModel.trim(),
        )
        viewModelScope.launch {
            settingsRepository.save(
                host = state.host,
                port = port,
                accessToken = token,
                cloud = cloud,
                localClassifierEnabled = state.localClassifierEnabled,
            )
            _uiState.update {
                it.copy(
                    token = token,
                    cloudApiKey = cloud.apiKey,
                    cloudBaseUrl = cloud.baseUrl,
                    cloudModel = cloud.model,
                    statusMessage = "Сохранено",
                )
            }
        }
    }

    fun checkCloudModels() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update {
                it.copy(busy = true, cloudStatusMessage = "Загрузка моделей…", cloudLastOk = null)
            }
            val result = withContext(Dispatchers.IO) {
                cloudClient.listModels(state.cloudBaseUrl, state.cloudApiKey)
            }
            result.fold(
                onSuccess = { models ->
                    val ids = models.modelIds
                    val selected = when {
                        state.cloudModel.isNotBlank() && state.cloudModel in ids -> state.cloudModel
                        ids.isNotEmpty() -> ids.first()
                        else -> state.cloudModel
                    }
                    _uiState.update {
                        it.copy(
                            busy = false,
                            cloudModelIds = ids,
                            cloudModel = selected,
                            cloudLastOk = true,
                            cloudStatusMessage = if (ids.isEmpty()) {
                                "Связь OK, но список моделей пуст"
                            } else {
                                "Облако OK — моделей ${ids.size}"
                            },
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            busy = false,
                            cloudLastOk = false,
                            cloudStatusMessage = "Облако: ${error.message ?: error.javaClass.simpleName}",
                        )
                    }
                },
            )
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
                withFreshToken(state, port) { token -> apiClient.catalog(state.host, port, token) }
            }
            val autosResult = withContext(Dispatchers.IO) {
                withFreshToken(state, port) { token -> apiClient.automations(state.host, port, token) }
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
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, answerMessage = "", statusMessage = "Разбор фразы…") }
            val catalog = cachedCatalog ?: run {
                val loaded = withContext(Dispatchers.IO) {
                    withFreshToken(state, port) { token -> apiClient.catalog(state.host, port, token) }
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
                    withFreshToken(state, port) { token -> apiClient.automations(state.host, port, token) }
                }.getOrDefault(emptyList())
                loaded
            }

            val cloudSettings = state.cloudSettings()
            val clarify = pendingClarify
            if (cloudSettings.looksConfigured() && cloudSettings.preferCloud) {
                _uiState.update {
                    it.copy(
                        statusMessage = if (clarify != null) {
                            "Уточнение…"
                        } else {
                            "Облачный агент…"
                        },
                    )
                }
                val outcome = withContext(Dispatchers.IO) {
                    if (clarify != null) {
                        cloudAgent.continueClarify(
                            reply = state.phrase,
                            session = clarify,
                            settings = cloudSettings,
                            catalog = catalog,
                            automations = cachedAutomations,
                        )
                    } else {
                        cloudAgent.resolve(
                            phrase = state.phrase,
                            settings = cloudSettings,
                            catalog = catalog,
                            automations = cachedAutomations,
                        )
                    }
                }
                when (outcome) {
                    is CloudAgentOutcome.Intent -> {
                        pendingClarify = null
                        _uiState.update { it.copy(awaitingClarify = false) }
                        handleIntent(
                            outcome.intent,
                            state,
                            port,
                            catalog,
                            sourceTag = if (clarify != null) "cloud-clarify" else "cloud",
                        )
                        return@launch
                    }
                    is CloudAgentOutcome.Clarify -> {
                        pendingClarify = outcome.session
                        _uiState.update {
                            it.copy(
                                busy = false,
                                awaitingClarify = true,
                                lastHealthOk = true,
                                answerMessage = outcome.question,
                                phrase = "",
                                statusMessage = "intent=cloud-clarify — ответьте фразой или «Слушать»",
                            )
                        }
                        speakAnswer(outcome.question)
                        return@launch
                    }
                    is CloudAgentOutcome.Speak -> {
                        pendingClarify = null
                        _uiState.update {
                            it.copy(
                                busy = false,
                                awaitingClarify = false,
                                lastHealthOk = true,
                                answerMessage = outcome.text,
                                statusMessage = "intent=cloud-speak",
                            )
                        }
                        speakAnswer(outcome.text)
                        return@launch
                    }
                    CloudAgentOutcome.Fallback -> {
                        pendingClarify = null
                        _uiState.update {
                            it.copy(
                                awaitingClarify = false,
                                statusMessage = "Локальный NLU…",
                            )
                        }
                    }
                }
            } else {
                pendingClarify = null
            }

            if (state.localClassifierEnabled) {
                _uiState.update { it.copy(statusMessage = "Локальный классификатор…") }
                val localIntent = withContext(Dispatchers.Default) {
                    localClassifier.classify(state.phrase, catalog, cachedAutomations)
                }
                if (localIntent !is VoiceIntent.Unknown) {
                    handleIntent(localIntent, state, port, catalog, sourceTag = "local")
                    return@launch
                }
            }

            _uiState.update { it.copy(statusMessage = "Aliases…") }
            val intent = nlu.match(state.phrase, catalog, cachedAutomations)
            handleIntent(intent, state, port, catalog, sourceTag = "alias")
        }
    }

    private suspend fun handleIntent(
        intent: VoiceIntent,
        state: VoiceHomeUiState,
        port: Int,
        catalog: ApiCatalog,
        sourceTag: String,
    ) {
        when (intent) {
            is VoiceIntent.QuerySignal -> {
                val source = SignalAnswerFormatter.preferredSource(intent.signal)
                if (source == null) {
                    val msg = "У сигнала ${intent.signal.id} нет источников"
                    _uiState.update {
                        it.copy(
                            busy = false,
                            answerMessage = msg,
                            statusMessage = "intent=query/$sourceTag alias=${intent.matchedAlias}",
                        )
                    }
                    speakAnswer(msg)
                    return
                }
                val snapshot = withContext(Dispatchers.IO) {
                    withFreshToken(state, port) { token ->
                        apiClient.signals(
                            host = state.host,
                            port = port,
                            token = token,
                            ids = listOf(intent.signal.id),
                            source = source,
                        )
                    }
                }.getOrElse { error ->
                    _uiState.update {
                        it.copy(
                            busy = false,
                            lastHealthOk = false,
                            statusMessage = "Signals: ${error.message ?: error.javaClass.simpleName}",
                        )
                    }
                    return
                }
                val reading = snapshot.signals.firstOrNull { it.id == intent.signal.id }
                val answer = SignalAnswerFormatter.format(intent.signal, reading)
                _uiState.update {
                    it.copy(
                        busy = false,
                        lastHealthOk = true,
                        answerMessage = answer,
                        statusMessage = "query/$sourceTag ${intent.signal.id}@$source " +
                            "(alias «${intent.matchedAlias}»)",
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
                            statusMessage = "intent=invoke/$sourceTag alias=${intent.matchedAlias}",
                        )
                    }
                    speakAnswer(msg)
                    return
                }
                val body = InvokeActionPayloadBuilder.wrapActions(payload)
                val result = withContext(Dispatchers.IO) {
                    withFreshToken(state, port) { token ->
                        apiClient.invokeActions(state.host, port, token, body)
                    }
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
                    return
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
                        statusMessage = "intent=invoke/$sourceTag alias=${intent.matchedAlias} ok=$ok",
                    )
                }
                speakAnswer(msg)
            }

            is VoiceIntent.RunAutomation -> {
                val result = withContext(Dispatchers.IO) {
                    withFreshToken(state, port) { token ->
                        apiClient.runAutomation(state.host, port, token, intent.automation.id)
                    }
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
                    return
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
                        statusMessage = "intent=run/$sourceTag id=${intent.automation.id} " +
                            "accepted=${result.accepted}",
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
                        statusMessage = "intent=unknown/$sourceTag",
                    )
                }
                speakAnswer(msg)
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

    /** Asks TBox Monitor on this head unit for access; Monitor shows its dialog once. */
    fun requestMonitorAccess() {
        val state = _uiState.value
        if (state.busy) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, statusMessage = "Запрос доступа у TBox Monitor…") }
            val result = withContext(Dispatchers.IO) {
                LocalAccessClient.awaitAccess(getApplication()) {
                    _uiState.update { it.copy(statusMessage = "Подтвердите доступ в окне TBox Monitor") }
                }
            }
            if (result.granted) {
                saveMonitorAccess(state, result)
                _uiState.update {
                    it.copy(
                        busy = false,
                        lastHealthOk = true,
                        statusMessage = if (result.serverEnabled == false) {
                            "Доступ разрешён. Включите API в TBox Monitor: Настройки → API"
                        } else {
                            "Доступ разрешён"
                        },
                    )
                }
                return@launch
            }
            val message = when (result.status) {
                LocalAccessContract.STATUS_DENIED -> "Доступ отклонён в TBox Monitor"
                LocalAccessContract.STATUS_PENDING -> "Время ожидания вышло"
                LocalAccessContract.STATUS_CONSENT_REQUIRED -> "Окно TBox Monitor закрыто без ответа"
                LocalAccessContract.STATUS_UNAVAILABLE -> "TBox Monitor не запущен"
                LocalAccessContract.STATUS_NOT_SUPPORTED ->
                    "TBox Monitor не найден или старой версии. Вставьте токен вручную"
                else -> "TBox Monitor ответил: ${result.status}"
            }
            _uiState.update { it.copy(busy = false, lastHealthOk = false, statusMessage = message) }
        }
    }

    /**
     * Runs [call] with the current token. On the local Monitor a missing or rejected token is
     * replaced once without asking the user, as long as Monitor still trusts this app.
     */
    private suspend fun <T> withFreshToken(
        state: VoiceHomeUiState,
        port: Int,
        call: (String) -> Result<T>,
    ): Result<T> {
        val stateToken = AccessTokenNormalizer.normalize(state.token)
        val token = renewal?.takeIf { it.first == stateToken }?.second ?: stateToken
        val first = if (token.isBlank()) null else call(token)
        val rejected = first == null ||
            (first.exceptionOrNull() as? ExternalApiHttpException)?.httpStatus == 401
        if (!rejected || !isLocalMonitor(state.host)) return first ?: call(token)
        val renewed = LocalAccessClient.getAccess(getApplication(), interactive = false)
        if (!renewed.granted) return first ?: call(token)
        val fresh = renewed.accessToken.orEmpty()
        renewal = stateToken to fresh
        saveMonitorAccess(state, renewed, fallbackPort = port)
        return call(fresh)
    }

    private suspend fun saveMonitorAccess(
        state: VoiceHomeUiState,
        result: LocalAccessResult,
        fallbackPort: Int = portOrDefault(state),
    ) {
        val token = result.accessToken.orEmpty()
        val port = result.port ?: fallbackPort
        _uiState.update { it.copy(token = token, portText = port.toString()) }
        settingsRepository.saveConnection(state.host, port, token)
    }

    private fun isLocalMonitor(host: String): Boolean =
        host.trim().lowercase() in setOf("", "127.0.0.1", "localhost")
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
