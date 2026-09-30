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
import vad.dashing.tbox.AppDataManager
import vad.dashing.tbox.BuildConfig
import vad.dashing.tbox.SettingsManager
import vad.dashing.tbox.TboxRepository
import vad.dashing.tbox.automation.AutomationAction
import vad.dashing.tbox.automation.AutomationActionExecutor
import vad.dashing.tbox.automation.AutomationActionResult
import vad.dashing.tbox.automation.AutomationDefinition
import vad.dashing.tbox.automation.AutomationRunNow
import vad.dashing.tbox.automation.AutomationServiceActions
import vad.dashing.tbox.automation.AutomationStore
import vad.dashing.tbox.automation.AutomationTriggerContext
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

    private var pairedClients: List<ExternalApiPairedClient> = emptyList()
    private var enabled: Boolean = false
    private var port: Int = ExternalApiConstants.DEFAULT_PORT
    private var dangerousEnabled: Boolean = false
    private var automations: List<AutomationDefinition> = emptyList()
    private var pairingTimeoutJob: Job? = null
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
    )

    fun start() {
        if (observeJob != null) return
        observeJob = scope.launch {
            combine(
                settingsManager.externalApiEnabledFlow,
                settingsManager.externalApiPortFlow,
                settingsManager.externalApiDangerousEnabledFlow,
                settingsManager.externalApiClientsJsonFlow,
            ) { apiEnabled, apiPort, apiDangerous, clientsJson ->
                Quad(apiEnabled, apiPort, apiDangerous, clientsJson)
            }.collectLatest { (apiEnabled, apiPort, apiDangerous, clientsJson) ->
                enabled = apiEnabled
                port = apiPort.coerceIn(ExternalApiConstants.MIN_PORT, ExternalApiConstants.MAX_PORT)
                dangerousEnabled = apiDangerous
                pairedClients = ExternalApiPairedClient.decodeList(clientsJson)
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
        pairingSession.stopPairing()
        httpServer.stop()
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
        pairedClients = ExternalApiAuth.registerApprovedClient(
            clients = pairedClients,
            clientId = request.clientId,
            clientName = request.clientName,
            accessToken = token,
            createdAtEpochMs = System.currentTimeMillis(),
        )
        scope.launch {
            settingsManager.saveExternalApiClientsJson(
                ExternalApiPairedClient.encodeList(pairedClients),
            )
        }
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
        pairedClients = ExternalApiAuth.revokeClient(pairedClients, clientId)
        scope.launch {
            settingsManager.saveExternalApiClientsJson(
                ExternalApiPairedClient.encodeList(pairedClients),
            )
        }
        publishStatus()
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

    private fun syncServer() {
        if (!enabled) {
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

    private data class Quad<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
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
