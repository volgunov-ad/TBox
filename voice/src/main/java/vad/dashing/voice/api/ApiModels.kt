package vad.dashing.voice.api

data class HealthStatus(
    val ok: Boolean,
    val apiVersion: Int,
    val catalogVersion: Int,
    val serverEnabled: Boolean,
    val pairingActive: Boolean,
    val appVersion: String,
)

data class CatalogSignal(
    val id: String,
    val label: String,
    val unit: String,
    val valueType: String,
    val sources: List<String>,
    val voiceAliasesRu: List<String>,
    val namedValues: Map<String, String> = emptyMap(),
)

data class CatalogAction(
    val type: String,
    val actionType: String? = null,
    val bus: String? = null,
    val propertyId: Int? = null,
    val label: String? = null,
    val safety: String? = null,
    val voiceAliasesRu: List<String>,
)

data class ApiCatalog(
    val catalogVersion: Int,
    val signals: List<CatalogSignal>,
    val actionTypes: List<CatalogAction>,
)

data class AutomationSummary(
    val id: String,
    val name: String,
    val enabled: Boolean,
)

data class SignalReading(
    val id: String,
    val available: Boolean,
    val valueType: String,
    val value: Any?,
)

data class SignalsSnapshot(
    val signals: List<SignalReading>,
)
