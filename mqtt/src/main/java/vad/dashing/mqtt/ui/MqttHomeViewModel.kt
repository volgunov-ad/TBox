package vad.dashing.mqtt.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import vad.dashing.mqtt.api.ApiCallException
import vad.dashing.mqtt.api.MonitorApi
import vad.dashing.mqtt.ha.CatalogEntity
import vad.dashing.mqtt.ha.buildEntities
import vad.dashing.mqtt.mqttclient.HiveMqttSession
import vad.dashing.mqtt.service.BridgeStatus
import vad.dashing.mqtt.service.BridgeStatusStore
import vad.dashing.mqtt.settings.BrokerWarning
import vad.dashing.mqtt.settings.MqttSettings
import vad.dashing.mqtt.settings.MqttSettingsStore

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
    private val store = MqttSettingsStore(app)
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
                val current = _state.value
                val tab = if (status.lastError == "Monitor не принимает токен") 0 else current.tab
                _state.value = current.copy(bridge = status, tab = tab)
            }
        }
        if (settings.accessToken.isNotBlank()) refreshEntities()
    }

    fun selectTab(index: Int) {
        _state.value = _state.value.copy(tab = index)
        if (index == 2) refreshEntities()
    }

    fun update(transform: (MqttSettings) -> MqttSettings) {
        val current = _state.value
        val next = transform(current.settings)
        store.save(next)
        _state.value = current.copy(
            settings = next,
            connectionDraft = current.connectionDraft.copy(
                apiPort = next.apiPort,
                accessToken = next.accessToken,
                deviceName = next.deviceName,
                discoveryEnabled = next.discoveryEnabled,
                acceptCommands = next.acceptCommands,
                selectedObjectIds = next.selectedObjectIds,
            ),
        )
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
        val merged = current.settings.applyingConnection(current.connectionDraft).normalized()
        store.save(merged)
        _state.value = current.copy(
            settings = merged,
            connectionDraft = merged,
            connectionMessage = "Сохранено",
        )
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
        refreshEntities()
    }

    fun sendPairRequest() {
        if (_state.value.pairBusy) return
        pairJob?.cancel()
        pairJob = viewModelScope.launch(Dispatchers.IO) {
            val settings = _state.value.settings
            updateUi { it.copy(pairBusy = true, pairMessage = "Отправка запроса") }
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
                            val saved = _state.value.settings.copy(accessToken = token).normalized()
                            store.save(saved)
                            updateUi {
                                it.copy(
                                    settings = saved,
                                    connectionDraft = it.connectionDraft.copy(accessToken = token),
                                    pairBusy = false,
                                    pairMessage = "Доступ разрешён",
                                )
                            }
                                refreshEntities()
                            }
                            return@launch
                        }
                        "denied" -> {
                            updateUi { it.copy(pairBusy = false, pairMessage = "Запрос отклонён") }
                            return@launch
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

    fun refreshEntities() {
        val settings = _state.value.settings
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
                    .map { vad.dashing.mqtt.ha.AutomationRow(it.id, it.name) }
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

    private fun updateUi(transform: (MqttHomeState) -> MqttHomeState) {
        _state.value = transform(_state.value)
    }

    private fun normalizeToken(raw: String): String =
        raw.trim().replace(Regex("^Bearer\\s+", RegexOption.IGNORE_CASE), "").trim()
}
