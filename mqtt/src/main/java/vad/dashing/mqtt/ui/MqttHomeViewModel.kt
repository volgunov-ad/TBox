package vad.dashing.mqtt.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vad.dashing.mqtt.api.ApiCallException
import vad.dashing.mqtt.api.MonitorApi
import vad.dashing.mqtt.ha.AutomationRow
import vad.dashing.mqtt.ha.CatalogEntity
import vad.dashing.mqtt.ha.buildEntities
import vad.dashing.mqtt.mqttclient.HiveMqttSession
import vad.dashing.mqtt.wireguard.parseWgConf
import vad.dashing.mqtt.service.BridgeStatus
import vad.dashing.mqtt.service.MqttBridgeService
import vad.dashing.mqtt.service.BridgeStatusStore
import vad.dashing.mqtt.settings.BrokerWarning
import vad.dashing.mqtt.settings.MqttSettings
import vad.dashing.mqtt.settings.MqttSettingsStore
import vad.dashing.mqtt.settings.connectionChanged
import vad.dashing.tbox.localaccess.LocalAccessClient
import vad.dashing.tbox.localaccess.LocalAccessContract

data class MqttHomeState(
    val tab: Int = 0,
    val settings: MqttSettings = MqttSettings(),
    val pairMessage: String = "",
    val pairBusy: Boolean = false,
    val brokerMessage: String = "",
    val brokerBusy: Boolean = false,
    val entities: List<CatalogEntity> = emptyList(),
    val entitiesMessage: String = "",
    val advancedOpen: Boolean = false,
    val manualTokenOpen: Boolean = false,
    val manualToken: String = "",
    val connectionDraft: MqttSettings = MqttSettings(),
    val connectionMessage: String = "",
    val bridge: BridgeStatus = BridgeStatus(),
)

class MqttHomeViewModel(app: Application) : AndroidViewModel(app) {
    private val store = MqttSettingsStore.get(app)
    private val api = MonitorApi()
    private val _state = MutableStateFlow(MqttHomeState())
    val state: StateFlow<MqttHomeState> = _state
    private var pairJob: Job? = null

    init {
        val settings = store.load()
        val startTab = if (settings.ready) 1 else 0
        _state.value = _state.value.copy(
            settings = settings,
            connectionDraft = settings,
            tab = startTab,
        )
        viewModelScope.launch {
            BridgeStatusStore.state.collect { status ->
                _state.update { current ->
                    val rejected = status.monitorError == TOKEN_REJECTED &&
                        current.bridge.monitorError != TOKEN_REJECTED
                    current.copy(bridge = status, tab = if (rejected) 0 else current.tab)
                }
            }
        }
        if (settings.accessToken.isNotBlank()) {
            refreshEntities()
        } else {
            requestAccess(httpFallback = false)
        }
    }

    fun selectTab(index: Int) {
        _state.value = _state.value.copy(tab = index)
        if (index == 2) refreshEntities()
    }

    fun update(transform: (MqttSettings) -> MqttSettings) {
        val current = _state.value
        val next = transform(withStoredAccess(current.settings))
        store.save(next)
        _state.value = current.copy(
            settings = next,
            connectionDraft = current.connectionDraft.copy(
                apiPort = next.apiPort,
                brokerEnabled = next.brokerEnabled,
                autostart = next.autostart,
                accessToken = next.accessToken,
                deviceName = next.deviceName,
                discoveryEnabled = next.discoveryEnabled,
                acceptCommands = next.acceptCommands,
                selectedObjectIds = next.selectedObjectIds,
            ),
        )
    }

    /** Applies at once, unlike the broker fields: the switch is the quick way to pause the bridge. */
    fun setBrokerEnabled(enabled: Boolean) {
        update { it.copy(brokerEnabled = enabled) }
        if (enabled) {
            startBridgeIfReady()
        } else {
            MqttBridgeService.stop(getApplication())
        }
    }

    fun reportConnection(message: String) {
        _state.value = _state.value.copy(connectionMessage = message)
    }

    fun setWireguardEnabled(enabled: Boolean) {
        if (enabled && _state.value.connectionDraft.wireguardConf.isBlank()) {
            reportConnection("Сначала выберите файл .conf")
            return
        }
        editConnection { it.copy(wireguardEnabled = enabled) }
    }

    fun importWireguard(fileName: String, text: String) {
        val failure = parseWgConf(text).exceptionOrNull()
        if (failure != null) {
            reportConnection(failure.message ?: "Файл WireGuard не разобран")
            return
        }
        editConnection {
            it.copy(
                wireguardConf = text,
                wireguardFileName = fileName.trim().ifBlank { "wireguard.conf" },
                wireguardEnabled = true,
            )
        }
        reportConnection("Файл прочитан. Нажмите «Сохранить».")
    }

    fun clearWireguard() {
        editConnection { it.copy(wireguardEnabled = false, wireguardConf = "", wireguardFileName = "") }
    }

    fun editConnection(transform: (MqttSettings) -> MqttSettings) {
        val current = _state.value
        _state.value = current.copy(
            connectionDraft = transform(current.connectionDraft),
            connectionMessage = "",
        )
    }

    fun saveConnection() {
        val current = _state.value
        val merged = withStoredAccess(current.settings).applyingConnection(current.connectionDraft).normalized()
        store.save(merged)
        _state.value = current.copy(
            settings = merged,
            connectionDraft = merged,
            connectionMessage = "Сохранено",
        )
        startBridgeIfReady()
    }

    fun toggleAdvanced() {
        _state.value = _state.value.copy(advancedOpen = !_state.value.advancedOpen)
    }

    fun toggleManualToken() {
        _state.value = _state.value.copy(manualTokenOpen = !_state.value.manualTokenOpen)
    }

    fun setManualToken(value: String) {
        _state.value = _state.value.copy(manualToken = value)
    }

    fun saveManualToken() {
        val token = normalizeToken(_state.value.manualToken)
        if (token.isBlank()) {
            _state.value = _state.value.copy(pairMessage = "Вставьте токен")
            return
        }
        update { it.copy(accessToken = token) }
        _state.value = _state.value.copy(manualToken = "", manualTokenOpen = false, pairMessage = "Токен сохранён")
        startBridgeIfReady()
        refreshEntities()
    }

    fun sendPairRequest() = requestAccess(httpFallback = true)

    /**
     * Monitor confirms this app in its own dialog once and then hands out tokens by itself.
     * The HTTP pairing stays for a Monitor without the local provider.
     */
    private fun requestAccess(httpFallback: Boolean) {
        if (_state.value.pairBusy) return
        pairJob?.cancel()
        pairJob = viewModelScope.launch(Dispatchers.IO) {
            updateUi { it.copy(pairBusy = true, pairMessage = "Запрос доступа у TBox Monitor") }
            val result = LocalAccessClient.awaitAccess(getApplication()) {
                updateUi { it.copy(pairMessage = "Подтвердите доступ в окне TBox Monitor") }
            }
            if (result.granted) {
                withContext(Dispatchers.Main) {
                    store.saveMonitorAccess(accessToken = result.accessToken, apiPort = result.port)
                    reloadMonitorAccess()
                    val message = if (result.serverEnabled == false) {
                        "Доступ разрешён. Включите API в TBox Monitor: Настройки → API"
                    } else {
                        "Доступ разрешён"
                    }
                    updateUi { it.copy(pairBusy = false, pairMessage = message) }
                    startBridgeIfReady()
                    refreshEntities()
                }
                return@launch
            }
            if (httpFallback && result.status == LocalAccessContract.STATUS_NOT_SUPPORTED) {
                pairOverHttp()
                return@launch
            }
            updateUi { it.copy(pairBusy = false, pairMessage = localAccessMessage(result.status)) }
        }
    }

    private suspend fun pairOverHttp() {
        val settings = _state.value.settings
        updateUi { it.copy(pairMessage = "Отправка запроса") }
        try {
            val request = api.pairRequest(
                port = settings.apiPort,
                clientId = store.pairClientId(),
                clientName = "TBox MQTT",
            )
            updateUi { it.copy(pairMessage = "Ожидание подтверждения в Monitor") }
            val deadline = System.currentTimeMillis() + 120_000L
            while (System.currentTimeMillis() < deadline) {
                delay(1_000L)
                val status = api.pairStatus(settings.apiPort, request.requestId)
                when (status.status) {
                    "approved" -> {
                        val token = status.accessToken.orEmpty()
                        if (token.isBlank()) {
                            updateUi { it.copy(pairBusy = false, pairMessage = "Monitor не вернул токен") }
                        } else {
                            withContext(Dispatchers.Main) {
                                update { it.copy(accessToken = token) }
                                updateUi { it.copy(pairBusy = false, pairMessage = "Доступ разрешён") }
                                startBridgeIfReady()
                                refreshEntities()
                            }
                        }
                        return
                    }
                    "denied" -> {
                        updateUi { it.copy(pairBusy = false, pairMessage = "Запрос отклонён") }
                        return
                    }
                }
            }
            updateUi { it.copy(pairBusy = false, pairMessage = "Время ожидания вышло") }
        } catch (error: ApiCallException) {
            val message = when {
                error.code == "pairing_inactive" || error.httpStatus == 403 ->
                    "Сначала нажмите «Подключить приложение» в Monitor"
                error.httpStatus == 404 -> "Запрос пропал"
                else -> error.message ?: "Не удалось отправить запрос"
            }
            updateUi { it.copy(pairBusy = false, pairMessage = message) }
        } catch (error: Exception) {
            updateUi { it.copy(pairBusy = false, pairMessage = error.message ?: "Не удалось отправить запрос") }
        }
    }

    private fun localAccessMessage(status: String): String = when (status) {
        LocalAccessContract.STATUS_DENIED -> "Доступ отклонён в TBox Monitor"
        LocalAccessContract.STATUS_PENDING -> "Время ожидания вышло"
        LocalAccessContract.STATUS_CONSENT_REQUIRED -> "Окно TBox Monitor закрыто без ответа"
        LocalAccessContract.STATUS_UNAVAILABLE -> "TBox Monitor не запущен"
        LocalAccessContract.STATUS_UNKNOWN_CLIENT -> "TBox Monitor не узнал приложение"
        LocalAccessContract.STATUS_NOT_SUPPORTED ->
            "TBox Monitor не найден или старой версии. Подключитесь кнопкой «Отправить запрос»"
        else -> "TBox Monitor ответил: $status"
    }

    /** The bridge service may have renewed the token or port since this screen loaded. */
    private fun withStoredAccess(settings: MqttSettings): MqttSettings {
        val stored = store.load()
        return settings.copy(accessToken = stored.accessToken, apiPort = stored.apiPort)
    }

    private fun reloadMonitorAccess() {
        _state.update { current ->
            val settings = withStoredAccess(current.settings)
            current.copy(
                settings = settings,
                connectionDraft = current.connectionDraft.copy(
                    accessToken = settings.accessToken,
                    apiPort = settings.apiPort,
                ),
            )
        }
    }

    fun checkBroker() {
        if (_state.value.brokerBusy) return
        viewModelScope.launch(Dispatchers.IO) {
            updateUi { it.copy(brokerBusy = true, brokerMessage = "Проверка") }
            val message = HiveMqttSession.probe(_state.value.connectionDraft)
            updateUi {
                it.copy(
                    brokerBusy = false,
                    brokerMessage = message ?: "Брокер отвечает",
                )
            }
        }
    }

    fun toggleEntity(objectId: String, enabled: Boolean) {
        update { settings ->
            val next = settings.selectedObjectIds.toMutableSet()
            if (enabled) next += objectId else next -= objectId
            settings.copy(selectedObjectIds = next)
        }
    }

    fun setEntities(objectIds: Collection<String>, enabled: Boolean) {
        update { settings ->
            val next = settings.selectedObjectIds.toMutableSet()
            if (enabled) next += objectIds else next -= objectIds.toSet()
            settings.copy(selectedObjectIds = next)
        }
    }

    fun connectionDirty(): Boolean = connectionChanged(_state.value.settings, _state.value.connectionDraft)

    fun refreshEntities() {
        val settings = withStoredAccess(_state.value.settings)
        if (settings.accessToken.isBlank()) {
            _state.value = _state.value.copy(
                entities = emptyList(),
                entitiesMessage = "Сначала подключитесь к Monitor",
            )
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val catalog = api.catalog(settings.apiPort, settings.accessToken)
                val automations = api.automations(settings.apiPort, settings.accessToken)
                    .map { AutomationRow(it.id, it.name) }
                val rows = buildEntities(catalog, automations)
                updateUi { it.copy(entities = rows, entitiesMessage = "") }
            } catch (error: Exception) {
                updateUi { it.copy(entitiesMessage = error.message ?: "Каталог недоступен") }
            }
        }
    }

    fun brokerWarning(): String? {
        val settings = _state.value.connectionDraft
        return if (BrokerWarning.shouldWarn(settings.username, settings.tlsEnabled, settings.brokerHost)) {
            BrokerWarning.TEXT
        } else {
            null
        }
    }

    private fun startBridgeIfReady() {
        if (_state.value.settings.normalized().active) MqttBridgeService.start(getApplication())
    }

    private fun updateUi(transform: (MqttHomeState) -> MqttHomeState) {
        _state.update(transform)
    }

    private fun normalizeToken(raw: String): String =
        raw.trim().replace(Regex("^Bearer\\s+", RegexOption.IGNORE_CASE), "").trim()

    private companion object {
        const val TOKEN_REJECTED = "Monitor не принимает токен"
    }
}
