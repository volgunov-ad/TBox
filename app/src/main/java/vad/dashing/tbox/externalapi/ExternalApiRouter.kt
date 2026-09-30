package vad.dashing.tbox.externalapi

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import vad.dashing.tbox.automation.AutomationAction
import vad.dashing.tbox.automation.AutomationActionResult
import vad.dashing.tbox.automation.AutomationBuiltinActionType
import vad.dashing.tbox.automation.AutomationCanCatalog
import vad.dashing.tbox.automation.AutomationCodec
import vad.dashing.tbox.automation.AutomationDefinition
import vad.dashing.tbox.automation.AutomationRunNow
import vad.dashing.tbox.automation.AutomationSignalCatalog
import vad.dashing.tbox.automation.AutomationSignalSource
import vad.dashing.tbox.automation.AutomationSignalValueType
import vad.dashing.tbox.automation.AutomationTriggerContext

class ExternalApiRouter(
    private val appVersion: String,
    private val serverEnabled: () -> Boolean,
    private val pairingSession: ExternalApiPairingSession,
    private val pairedClients: () -> List<ExternalApiPairedClient>,
    private val dangerousEnabled: () -> Boolean,
    private val signalReader: ExternalApiSignalReader,
    private val automationsProvider: () -> List<AutomationDefinition>,
    private val executeActions: suspend (List<AutomationAction>) -> List<AutomationActionResult>,
    private val runAutomationNow: (String) -> String?,
) {
    fun handle(
        method: String,
        path: String,
        query: Map<String, String>,
        headers: Map<String, String>,
        body: String,
    ): ExternalApiHttpResponse {
        return when {
            path == ExternalApiConstants.PATH_HEALTH && method == "GET" ->
                handleHealth()

            path == ExternalApiConstants.PATH_PAIR_REQUEST && method == "POST" ->
                handlePairRequest(body)

            path == ExternalApiConstants.PATH_PAIR_STATUS && method == "GET" ->
                handlePairStatus(query)

            path == ExternalApiConstants.PATH_CATALOG && method == "GET" ->
                requireAuth(headers) { handleCatalog() }

            path == ExternalApiConstants.PATH_SIGNALS && method == "GET" ->
                requireAuth(headers) { handleSignals(query) }

            path == ExternalApiConstants.PATH_ACTIONS_INVOKE && method == "POST" ->
                requireAuth(headers) { handleActionsInvoke(body) }

            path == ExternalApiConstants.PATH_AUTOMATIONS && method == "GET" ->
                requireAuth(headers) { handleAutomationsList() }

            path.startsWith(ExternalApiConstants.PATH_AUTOMATIONS_RUN_PREFIX) &&
                path.endsWith("/run") &&
                method == "POST" ->
                requireAuth(headers) {
                    val id = path
                        .removePrefix(ExternalApiConstants.PATH_AUTOMATIONS_RUN_PREFIX)
                        .removeSuffix("/run")
                        .trim('/')
                    handleAutomationRun(id)
                }

            else -> errorResponse(404, "not_found", "Unknown route")
        }
    }

    private fun handleHealth(): ExternalApiHttpResponse =
        jsonResponse(
            200,
            JSONObject()
                .put("ok", true)
                .put("apiVersion", ExternalApiConstants.API_VERSION)
                .put("catalogVersion", ExternalApiConstants.CATALOG_VERSION)
                .put("serverEnabled", serverEnabled())
                .put("pairingActive", pairingSession.isPairingActive())
                .put("appVersion", appVersion),
        )

    private fun handlePairRequest(body: String): ExternalApiHttpResponse {
        if (!pairingSession.isPairingActive()) {
            return errorResponse(403, "pairing_inactive", "Pairing mode is not active")
        }
        return try {
            val json = JSONObject(body.ifBlank { "{}" })
            val clientId = json.optString("clientId").trim()
            val clientName = json.optString("clientName").trim()
            if (clientId.isEmpty() || clientName.isEmpty()) {
                return errorResponse(400, "invalid_request", "clientId and clientName are required")
            }
            val clientKind = json.optString("clientKind").trim().takeIf { it.isNotEmpty() }
            val request = pairingSession.submitPairRequest(clientId, clientName, clientKind)
            jsonResponse(
                202,
                JSONObject()
                    .put("status", "pending")
                    .put("requestId", request.requestId),
            )
        } catch (error: Exception) {
            errorResponse(400, "invalid_request", error.message ?: "Invalid JSON")
        }
    }

    private fun handlePairStatus(query: Map<String, String>): ExternalApiHttpResponse {
        val requestId = query["requestId"]?.trim().orEmpty()
        if (requestId.isEmpty()) {
            return errorResponse(400, "invalid_request", "requestId is required")
        }
        val request = pairingSession.getRequest(requestId)
            ?: return errorResponse(404, "not_found", "Pair request not found")
        val json = JSONObject().put("status", request.status.name.lowercase())
        when (request.status) {
            ExternalApiPairRequestStatus.APPROVED -> {
                json.put("accessToken", request.accessToken)
                json.put("clientId", request.clientId)
                json.put("expiresAt", JSONObject.NULL)
            }
            ExternalApiPairRequestStatus.DENIED -> Unit
            ExternalApiPairRequestStatus.PENDING -> Unit
        }
        return jsonResponse(200, json)
    }

    private fun handleCatalog(): ExternalApiHttpResponse {
        val signals = JSONArray()
        AutomationSignalCatalog.entries.forEach { descriptor ->
            val namedValues = JSONArray()
            descriptor.namedValues.forEach { named ->
                namedValues.put(
                    JSONObject()
                        .put("value", named.value)
                        .put("label", named.label),
                )
            }
            val stateOptions = JSONArray()
            descriptor.stateOptions.forEach { stateOptions.put(it) }
            val sources = JSONArray()
            descriptor.sources.forEach { sources.put(it.storageKey) }
            signals.put(
                JSONObject()
                    .put("id", descriptor.id.storageKey)
                    .put("label", descriptor.label)
                    .put("unit", descriptor.unit)
                    .put("valueType", descriptor.id.valueType.name.lowercase())
                    .put("sources", sources)
                    .put("stateOptions", stateOptions)
                    .put("namedValues", namedValues)
                    .put("typicalRange", descriptor.typicalRange)
                    .put(
                        "voiceAliasesRu",
                        jsonStringArray(
                            ExternalApiVoiceAliasesRu.forSignal(descriptor.id, descriptor.label),
                        ),
                    ),
            )
        }

        val builtins = JSONArray()
        AutomationBuiltinActionType.entries.forEach { type ->
            builtins.put(
                JSONObject()
                    .put("type", "builtin")
                    .put("actionType", type.storageKey)
                    .put("safety", ExternalApiActionSafetyRules.builtinSafety(type).storageKey)
                    .put(
                        "voiceAliasesRu",
                        jsonStringArray(ExternalApiVoiceAliasesRu.forBuiltin(type)),
                    ),
            )
        }

        val canCommands = JSONArray()
        AutomationCanCatalog.entries.forEach { entry ->
            canCommands.put(
                JSONObject()
                    .put("type", "can_command")
                    .put("bus", entry.bus.storageKey)
                    .put("propertyId", entry.propertyId)
                    .put("label", entry.label)
                    .put("safety", ExternalApiActionSafety.CONFIRM.storageKey)
                    .put(
                        "voiceAliasesRu",
                        jsonStringArray(
                            ExternalApiVoiceAliasesRu.forCanCommand(
                                entry.bus,
                                entry.propertyId,
                                entry.label,
                            ),
                        ),
                    ),
            )
        }

        val actionTypes = JSONArray()
        for (index in 0 until builtins.length()) {
            actionTypes.put(builtins.get(index))
        }
        for (index in 0 until canCommands.length()) {
            actionTypes.put(canCommands.get(index))
        }
        actionTypes.put(genericActionType("launch_application", "confirm"))
        actionTypes.put(genericActionType("open_main_screen", "safe"))
        actionTypes.put(genericActionType("http_request", "confirm"))
        actionTypes.put(genericActionType("delay", "safe"))

        return jsonResponse(
            200,
            JSONObject()
                .put("catalogVersion", ExternalApiConstants.CATALOG_VERSION)
                .put("signals", signals)
                .put("actionTypes", actionTypes),
        )
    }

    private fun handleSignals(query: Map<String, String>): ExternalApiHttpResponse {
        val rawIds = query["ids"]?.trim().orEmpty()
        val sourceRaw = query["source"]?.trim().orEmpty()
        if (rawIds.isEmpty() || sourceRaw.isEmpty()) {
            return errorResponse(400, "invalid_request", "ids and source are required")
        }
        val source = AutomationSignalSource.fromStorageKey(sourceRaw)
            ?: return errorResponse(400, "invalid_request", "Unknown source: $sourceRaw")
        val ids = rawIds.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(ExternalApiConstants.MAX_SIGNAL_IDS_PER_REQUEST)
        if (ids.isEmpty()) {
            return errorResponse(400, "invalid_request", "ids must not be empty")
        }
        val payload = runBlocking {
            signalReader.readSnapshot(ids, source)
        }
        return jsonResponse(200, payload)
    }

    private fun handleActionsInvoke(body: String): ExternalApiHttpResponse {
        val decoded = AutomationCodec.decodeActionsPayload(body)
            .getOrElse { error ->
                return errorResponse(400, "invalid_request", error.message ?: "Invalid actions payload")
            }
        if (decoded.isEmpty()) {
            return errorResponse(400, "invalid_request", "actions must not be empty")
        }
        if (decoded.size > ExternalApiConstants.MAX_ACTIONS_PER_REQUEST) {
            return errorResponse(400, "invalid_request", "Too many actions")
        }
        if (!dangerousEnabled() && decoded.any(ExternalApiActionSafetyRules::isDangerous)) {
            return errorResponse(403, "dangerous_disabled", "Dangerous commands are disabled")
        }
        val results = runBlocking {
            executeActions(decoded)
        }
        val array = JSONArray()
        results.forEach { result ->
            array.put(
                JSONObject()
                    .put("success", result.success)
                    .put("message", result.message),
            )
        }
        return jsonResponse(200, JSONObject().put("results", array))
    }

    private fun handleAutomationsList(): ExternalApiHttpResponse {
        val array = JSONArray()
        automationsProvider().forEach { definition ->
            array.put(
                JSONObject()
                    .put("id", definition.id)
                    .put("name", definition.name)
                    .put("enabled", definition.enabled),
            )
        }
        return jsonResponse(200, JSONObject().put("automations", array))
    }

    private fun handleAutomationRun(id: String): ExternalApiHttpResponse {
        if (id.isBlank()) {
            return errorResponse(400, "invalid_request", "Automation id is required")
        }
        val definition = automationsProvider().firstOrNull { it.id == id }
        val rejection = AutomationRunNow.rejection(definition)
        if (rejection != null) {
            return jsonResponse(
                400,
                JSONObject()
                    .put("accepted", false)
                    .put("message", rejection),
            )
        }
        val runError = runAutomationNow(id)
        if (runError != null) {
            return jsonResponse(
                400,
                JSONObject()
                    .put("accepted", false)
                    .put("message", runError),
            )
        }
        return jsonResponse(
            202,
            JSONObject()
                .put("accepted", true)
                .put("message", ""),
        )
    }

    private inline fun requireAuth(
        headers: Map<String, String>,
        block: () -> ExternalApiHttpResponse,
    ): ExternalApiHttpResponse {
        val token = bearerToken(headers) ?: return errorResponse(401, "unauthorized", "Bearer token required")
        val client = ExternalApiAuth.findClientByToken(pairedClients(), token)
            ?: return errorResponse(401, "unauthorized", "Invalid token")
        if (client.clientId.isBlank()) {
            return errorResponse(401, "unauthorized", "Invalid token")
        }
        return block()
    }

    private fun genericActionType(type: String, safety: String): JSONObject =
        JSONObject()
            .put("type", type)
            .put("safety", safety)
            .put(
                "voiceAliasesRu",
                jsonStringArray(ExternalApiVoiceAliasesRu.forGenericAction(type)),
            )

    private fun jsonStringArray(values: List<String>): JSONArray =
        JSONArray().also { array -> values.forEach { array.put(it) } }

    private fun bearerToken(headers: Map<String, String>): String? {
        val auth = headers["authorization"] ?: return null
        val prefix = "Bearer "
        if (!auth.startsWith(prefix, ignoreCase = true)) return null
        return auth.substring(prefix.length).trim().takeIf { it.isNotEmpty() }
    }

    private fun jsonResponse(status: Int, json: JSONObject): ExternalApiHttpResponse =
        ExternalApiHttpResponse(status = status, body = json.toString())

    private fun errorResponse(status: Int, code: String, message: String): ExternalApiHttpResponse =
        jsonResponse(
            status,
            JSONObject().put(
                "error",
                JSONObject()
                    .put("code", code)
                    .put("message", message),
            ),
        )
}
