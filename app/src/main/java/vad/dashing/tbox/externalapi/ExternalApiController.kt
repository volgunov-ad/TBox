package vad.dashing.tbox.externalapi

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import vad.dashing.tbox.AppDataManager
import vad.dashing.tbox.BuildConfig
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsManager
import vad.dashing.tbox.TboxRepository
import vad.dashing.tbox.automation.AutomationAction
import vad.dashing.tbox.automation.AutomationActionExecutor
import vad.dashing.tbox.automation.AutomationActionResult
import vad.dashing.tbox.automation.AutomationDefinition
import vad.dashing.tbox.automation.AutomationRunNow
import vad.dashing.tbox.automation.AutomationServiceActions
import vad.dashing.tbox.automation.AutomationStore
import vad.dashing.tbox.automation.AutomationSignalSource
import vad.dashing.tbox.automation.AutomationTriggerContext
import vad.dashing.tbox.phoneble.PhoneBleCodec
import vad.dashing.tbox.phoneble.PhoneCompanionHost
import java.net.Inet4Address
import java.net.NetworkInterface

data class ExternalApiStatus(
    val enabled: Boolean = false,
    val running: Boolean = false,
    val boundPort: Int? = null,
    val lastError: String? = null,
    val pairingActive: Boolean = false,
    val pairingExpiresAtElapsed: Long? = null,
    val apiVersion: Int = ExternalApiConstants.API_VERSION,
    val catalogVersion: Int = ExternalApiConstants.CATALOG_VERSION,
    val dangerousEnabled: Boolean = false,
    val clients: List<ExternalApiPairedClient> = emptyList(),
)

data class ExternalApiManualToken(
    val clientId: String,
    val clientName: String,
    val accessToken: String,
)

class ExternalApiController(
    context: Context,
    private val scope: CoroutineScope,
    private val settingsManager: SettingsManager,
    appDataManager: AppDataManager,
    private val automationStore: AutomationStore,
    private val serviceActions: AutomationServiceActions,
    private val runAutomationNowCallback: (String) -> String?,
) {
    private val appContext = context.applicationContext
    private val pairingSession = ExternalApiPairingSession()
    private val signalReader = ExternalApiSignalReader()
    private val actionExecutor = AutomationActionExecutor(
        appContext,
        settingsManager,
        appDataManager,
        serviceActions,
    )

    private val _status = MutableStateFlow(ExternalApiStatus())
    val status: StateFlow<ExternalApiStatus> = _status.asStateFlow()

    private val _pendingPairRequests = MutableStateFlow<List<ExternalApiPairRequest>>(emptyList())
    val pendingPairRequests: StateFlow<List<ExternalApiPairRequest>> = _pendingPairRequests.asStateFlow()

    private val clientsLock = Any()
    private val persistMutex = Mutex()
    private val lastPersistedUseAt = HashMap<String, Long>()

    @Volatile
    private var pairedClients: List<ExternalApiPairedClient> = emptyList()
    private var enabled: Boolean = false
    private var port: Int = ExternalApiConstants.DEFAULT_PORT
    private var dangerousEnabled: Boolean = false
    private var climatePanelEnabled: Boolean = false
    private var automations: List<AutomationDefinition> = emptyList()
    private var pairingTimeoutJob: Job? = null
    private var tokenExpiryJob: Job? = null
    private var observeJob: Job? = null

    private val httpServer = ExternalApiHttpServer { method, path, query, headers, body ->
        router.handle(method, path, query, headers, body)
    }

    private val router = ExternalApiRouter(
        appVersion = BuildConfig.VERSION_NAME,
        serverEnabled = { enabled },
        pairingSession = pairingSession,
        pairedClients = { pairedClients },
        dangerousEnabled = { dangerousEnabled },
        signalReader = signalReader,
        automationsProvider = { automations },
        executeActions = { actions -> executeActions(actions) },
        runAutomationNow = { id ->
            val rejection = AutomationRunNow.rejection(automations.firstOrNull { it.id == id })
            if (rejection != null) {
                rejection
            } else {
                runAutomationNowCallback(id)
                null
            }
        },
        webPanelEnabled = { climatePanelEnabled },
        pageLanguage = { appContext.getString(R.string.web_panel_language) },
        onAuthenticated = ::noteTokenUsed,
    )

    fun start() {
        if (observeJob != null) return
        observeJob = scope.launch {
            combine(
                settingsManager.externalApiEnabledFlow,
                settingsManager.externalApiPortFlow,
                settingsManager.externalApiDangerousEnabledFlow,
                settingsManager.externalApiClientsJsonFlow,
                settingsManager.externalApiWebPanelEnabledFlow,
            ) { apiEnabled, apiPort, apiDangerous, clientsJson, webPanel ->
                ApiRuntimeSettings(apiEnabled, apiPort, apiDangerous, clientsJson, webPanel)
            }.collectLatest { settings ->
                enabled = settings.enabled
                port = settings.port.coerceIn(ExternalApiConstants.MIN_PORT, ExternalApiConstants.MAX_PORT)
                dangerousEnabled = settings.dangerousEnabled
                climatePanelEnabled = settings.webPanelEnabled
                synchronized(clientsLock) {
                    pairedClients = ExternalApiAuth.mergeNewerUsage(
                        incoming = ExternalApiPairedClient.decodeList(settings.clientsJson),
                        current = pairedClients,
                    )
                }
                syncServer()
                publishStatus()
            }
        }
        scope.launch {
            automationStore.snapshots.collectLatest { snapshot ->
                automations = snapshot.document.automations
            }
        }
        scope.launch {
            while (true) {
                _pendingPairRequests.value = pairingSession.pendingRequestsSnapshot()
                delay(250)
            }
        }
    }

    fun stop() {
        observeJob?.cancel()
        observeJob = null
        pairingTimeoutJob?.cancel()
        pairingTimeoutJob = null
        tokenExpiryJob?.cancel()
        tokenExpiryJob = null
        pairingSession.stopPairing()
        httpServer.stop()
        persistClients()
        publishStatus()
    }

    fun startPairing() {
        pairingSession.startPairing()
        pairingTimeoutJob?.cancel()
        pairingTimeoutJob = scope.launch {
            delay(ExternalApiConstants.PAIRING_TIMEOUT_MS)
            if (pairingSession.isPairingActive()) {
                pairingSession.stopPairing()
                publishStatus()
            }
        }
        publishStatus()
        _pendingPairRequests.value = pairingSession.pendingRequestsSnapshot()
    }

    fun stopPairing() {
        pairingTimeoutJob?.cancel()
        pairingTimeoutJob = null
        pairingSession.stopPairing()
        publishStatus()
        _pendingPairRequests.value = emptyList()
    }

    fun approvePair(requestId: String) {
        val request = pairingSession.getRequest(requestId) ?: return
        val token = ExternalApiAuth.generateToken()
        pairingSession.approveRequest(requestId, token)
        val createdAt = System.currentTimeMillis()
        synchronized(clientsLock) {
            pairedClients = ExternalApiAuth.registerApprovedClient(
                clients = pairedClients,
                clientId = request.clientId,
                clientName = request.clientName,
                accessToken = token,
                createdAtEpochMs = createdAt,
            )
            lastPersistedUseAt[request.clientId] = createdAt
        }
        persistClients()
        TboxRepository.addLog("INFO", "ExternalApi", "Pair approved: ${request.clientName}")
        stopPairing()
        _pendingPairRequests.value = pairingSession.pendingRequestsSnapshot()
    }

    fun denyPair(requestId: String) {
        pairingSession.denyRequest(requestId)
        TboxRepository.addLog("INFO", "ExternalApi", "Pair denied: $requestId")
        _pendingPairRequests.value = pairingSession.pendingRequestsSnapshot()
    }

    fun revokeClient(clientId: String) {
        synchronized(clientsLock) {
            pairedClients = ExternalApiAuth.revokeClient(pairedClients, clientId)
            lastPersistedUseAt.remove(clientId.trim())
        }
        persistClients()
        publishStatus()
    }

    /**
     * Creates a trusted client and returns the plaintext token once.
     * Used from Settings → API for phone/Tasker/curl without pairing UI.
     */
    fun createManualToken(clientName: String): ExternalApiManualToken {
        val name = clientName.trim().ifEmpty { "Manual token" }
        val clientId = "manual-${java.util.UUID.randomUUID()}"
        val token = ExternalApiAuth.generateToken()
        val createdAt = System.currentTimeMillis()
        synchronized(clientsLock) {
            pairedClients = ExternalApiAuth.registerApprovedClient(
                clients = pairedClients,
                clientId = clientId,
                clientName = name,
                accessToken = token,
                createdAtEpochMs = createdAt,
            )
            lastPersistedUseAt[clientId] = createdAt
        }
        persistClients()
        TboxRepository.addLog("INFO", "ExternalApi", "Manual token created: $name")
        publishStatus()
        return ExternalApiManualToken(
            clientId = clientId,
            clientName = name,
            accessToken = token,
        )
    }

    fun lanAddresses(): List<String> {
        val addresses = linkedSetOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (!networkInterface.isUp || networkInterface.isLoopback) continue
                val addressEnum = networkInterface.inetAddresses
                while (addressEnum.hasMoreElements()) {
                    val address = addressEnum.nextElement()
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        address.hostAddress?.let(addresses::add)
                    }
                }
            }
        } catch (_: Exception) {
        }
        return addresses.toList()
    }

    suspend fun readPhoneSnapshot(): PhoneBleCodec.Snapshot {
        val headUnit = signalReader.readSnapshot(
            PhoneCompanionHost.headUnitSignalIds,
            AutomationSignalSource.HEAD_UNIT,
        )
        val volume = signalReader.readSnapshot(
            listOf("hu_media_volume"),
            AutomationSignalSource.APP,
        )
        return PhoneCompanionHost.snapshotFromSignals(headUnit, volume)
    }

    suspend fun executePhoneCommand(op: Int, seat: Int, arg: Int) {
        val action = PhoneCompanionHost.toAction(op, seat, arg) ?: return
        val results = executeActions(listOf(action))
        val failed = results.firstOrNull { !it.success }
        if (failed != null) {
            TboxRepository.addLog(
                "INFO",
                "Phone companion",
                failed.message.ifBlank { "phone command failed" },
            )
        }
    }

    private suspend fun executeActions(actions: List<AutomationAction>): List<AutomationActionResult> {
        val context = AutomationTriggerContext(
            automationId = "external-api",
            triggerId = "external-api",
            firedAtEpochMillis = System.currentTimeMillis(),
        )
        return actions.map { action ->
            actionExecutor.execute(
                actions = listOf(action),
                context = context,
                signalSnapshot = { emptyMap() },
            )
        }
    }

    private fun noteTokenUsed(client: ExternalApiPairedClient) {
        val now = System.currentTimeMillis()
        val persist = synchronized(clientsLock) {
            var shouldPersist = false
            pairedClients = pairedClients.map { existing ->
                if (existing.clientId != client.clientId || existing.tokenHash != client.tokenHash) {
                    existing
                } else {
                    val persistedAt = lastPersistedUseAt[existing.clientId] ?: 0L
                    if (now - persistedAt >= ExternalApiConstants.TOKEN_USAGE_PERSIST_INTERVAL_MS) {
                        lastPersistedUseAt[existing.clientId] = now
                        shouldPersist = true
                    }
                    existing.copy(lastUsedAtEpochMs = now)
                }
            }
            shouldPersist
        }
        if (persist) {
            persistClients()
            publishStatus()
        }
    }

    private fun sweepIdleTokens() {
        val now = System.currentTimeMillis()
        val removed = synchronized(clientsLock) {
            val before = pairedClients
            val kept = ExternalApiAuth.retainRecentlyUsed(before, now)
            pairedClients = kept
            val removedIds = before.map { it.clientId }.toSet() - kept.map { it.clientId }.toSet()
            lastPersistedUseAt.keys.retainAll(kept.map { it.clientId }.toSet())
            kept.forEach { lastPersistedUseAt[it.clientId] = now }
            before.filter { it.clientId in removedIds }
        }
        removed.forEach { client ->
            TboxRepository.addLog(
                "INFO",
                "ExternalApi",
                "Token idle > ${ExternalApiConstants.TOKEN_IDLE_MONTHS} months, removed: ${client.clientName}",
            )
        }
        persistClients()
        publishStatus()
    }

    private fun persistClients() {
        scope.launch {
            persistMutex.withLock {
                val snapshot = synchronized(clientsLock) { pairedClients }
                settingsManager.saveExternalApiClientsJson(
                    ExternalApiPairedClient.encodeList(snapshot),
                )
            }
        }
    }

    private fun scheduleTokenExpirySweep() {
        tokenExpiryJob?.cancel()
        tokenExpiryJob = scope.launch {
            delay(ExternalApiConstants.TOKEN_EXPIRY_CHECK_DELAY_MS)
            sweepIdleTokens()
        }
    }

    private fun syncServer() {
        if (!enabled) {
            tokenExpiryJob?.cancel()
            tokenExpiryJob = null
            httpServer.stop()
            publishStatus()
            return
        }
        if (httpServer.isRunning && httpServer.boundPort == port) {
            publishStatus()
            return
        }
        try {
            httpServer.start(port)
            if (httpServer.isRunning) {
                scheduleTokenExpirySweep()
            }
        } catch (error: Exception) {
            val message = error.message ?: error.javaClass.simpleName
            TboxRepository.addLog("ERROR", "ExternalApi", "Bind failed: $message")
        }
        publishStatus()
    }

    private fun publishStatus() {
        _status.value = ExternalApiStatus(
            enabled = enabled,
            running = httpServer.isRunning,
            boundPort = httpServer.boundPort,
            lastError = httpServer.lastError,
            pairingActive = pairingSession.isPairingActive(),
            pairingExpiresAtElapsed = pairingSession.pairingExpiresAtElapsed(),
            dangerousEnabled = dangerousEnabled,
            clients = pairedClients,
        )
    }

    private data class ApiRuntimeSettings(
        val enabled: Boolean,
        val port: Int,
        val dangerousEnabled: Boolean,
        val clientsJson: String,
        val webPanelEnabled: Boolean,
    )
}

object ExternalApiControllerHolder {
    private val _instance = MutableStateFlow<ExternalApiController?>(null)
    val instance: StateFlow<ExternalApiController?> = _instance.asStateFlow()

    fun register(controller: ExternalApiController) {
        _instance.value = controller
    }

    fun unregister() {
        _instance.value = null
    }

    fun get(): ExternalApiController? = _instance.value
}
