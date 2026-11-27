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

data class CatalogWriteOption(
    val state: String,
    /** Portable key (String) or raw CAN int (Number) for invoke `value`. */
    val value: Any,
)

data class CatalogWriteSchema(
    val kind: String,
    val options: List<CatalogWriteOption> = emptyList(),
    val min: Double? = null,
    val max: Double? = null,
    val step: Double? = null,
    val unit: String? = null,
) {
    companion object {
        const val KIND_BINARY = "binary"
        const val KIND_OPTIONS = "options"
        const val KIND_NUMBER = "number"
        const val KIND_PULSE = "pulse"
    }
}

data class CatalogAction(
    val type: String,
    val actionType: String? = null,
    val bus: String? = null,
    val propertyId: Int? = null,
    val label: String? = null,
    val safety: String? = null,
    val voiceAliasesRu: List<String>,
    val operations: List<String> = emptyList(),
    val signalId: String? = null,
    val write: CatalogWriteSchema? = null,
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

data class InvokeResultItem(
    val success: Boolean,
    val message: String,
)

data class InvokeResults(
    val results: List<InvokeResultItem>,
)

data class RunAutomationResult(
    val accepted: Boolean,
    val message: String,
)
