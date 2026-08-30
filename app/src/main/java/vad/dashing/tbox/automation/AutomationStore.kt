package vad.dashing.tbox.automation

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import vad.dashing.tbox.settingsDataStore

data class AutomationStoreSnapshot(
    val document: AutomationDocument,
    val loadError: String? = null,
    val rawJson: String = "",
)

class AutomationStore(context: Context) {
    private val appContext = context.applicationContext
    private val writeMutex = Mutex()

    val snapshots: Flow<AutomationStoreSnapshot> = appContext.settingsDataStore.data
        .map { preferences ->
            val raw = preferences[AUTOMATIONS_JSON_KEY].orEmpty()
            AutomationCodec.decode(raw).fold(
                onSuccess = { document ->
                    val integrity = AutomationValidator.integrityIssues(document)
                    if (integrity.isNotEmpty()) {
                        AutomationStoreSnapshot(
                            document = document,
                            loadError = integrity.joinToString("; ") { "${it.path}: ${it.message}" },
                            rawJson = raw,
                        )
                    } else {
                        val (normalized, _) = AutomationValidator.withInvalidDisabled(document)
                        AutomationStoreSnapshot(normalized, rawJson = raw)
                    }
                },
                onFailure = { error ->
                    AutomationStoreSnapshot(
                        document = AutomationDocument(),
                        loadError = error.message ?: error.javaClass.simpleName,
                        rawJson = raw,
                    )
                },
            )
        }
        .distinctUntilChanged()

    suspend fun save(document: AutomationDocument): Result<Unit> = runCatching {
        val normalized = document.copy(formatVersion = AUTOMATION_FORMAT_VERSION)
        val (safe, _) = AutomationValidator.withInvalidDisabled(normalized)
        validateForSave(safe)
        writeMutex.withLock {
            appContext.settingsDataStore.edit { preferences ->
                preferences[AUTOMATIONS_JSON_KEY] = AutomationCodec.encode(safe)
            }
        }
    }

    suspend fun upsert(
        definition: AutomationDefinition,
    ): Result<Unit> = mutate { current ->
        val definitions = current.automations.toMutableList()
        val index = definitions.indexOfFirst { it.id == definition.id }
        if (index >= 0) definitions[index] = definition else definitions += definition
        current.copy(automations = definitions)
    }

    suspend fun duplicate(
        source: AutomationDefinition,
    ): Result<Unit> = mutate { current ->
        val copy = source.duplicated()
        val definitions = current.automations.toMutableList()
        val index = definitions.indexOfFirst { it.id == source.id }
        if (index >= 0) {
            definitions.add(index + 1, copy)
        } else {
            definitions += copy
        }
        current.copy(automations = definitions)
    }

    suspend fun delete(
        automationId: String,
    ): Result<Unit> = mutate { current ->
        current.copy(automations = current.automations.filterNot { it.id == automationId })
    }

    suspend fun setEnabled(
        automationId: String,
        enabled: Boolean,
    ): Result<Unit> = mutate { current ->
        current.copy(
            automations = current.automations.map {
                if (it.id == automationId) it.copy(enabled = enabled) else it
            },
        )
    }

    suspend fun importAll(
        incoming: List<AutomationDefinition>,
    ): Result<Int> = mutate { current ->
        val imported = AutomationExport.withUniqueIds(
            incoming = incoming,
            existingIds = current.automations.map { it.id }.toSet(),
        )
        require(imported.isNotEmpty()) { "В файле нет автоматизаций" }
        val (safeImported, _) = AutomationValidator.withInvalidDisabled(
            AutomationDocument(automations = imported),
        )
        current.copy(automations = current.automations + safeImported.automations)
    }.map { incoming.size }

    suspend fun disableInvalidEnabled(): Result<Int> {
        var disabledCount = 0
        return mutate { current ->
            val (next, disabledIds) = AutomationValidator.withInvalidDisabled(current)
            disabledCount = disabledIds.size
            next
        }.map { disabledCount }
    }

    suspend fun reset(): Result<Unit> = runCatching {
        writeMutex.withLock {
            appContext.settingsDataStore.edit { preferences ->
                preferences.remove(AUTOMATIONS_JSON_KEY)
            }
        }
    }

    private suspend fun mutate(
        transform: (AutomationDocument) -> AutomationDocument,
    ): Result<Unit> = runCatching {
        writeMutex.withLock {
            appContext.settingsDataStore.edit { preferences ->
                val raw = preferences[AUTOMATIONS_JSON_KEY].orEmpty()
                val decoded = AutomationCodec.decode(raw).getOrThrow()
                require(AutomationValidator.integrityIssues(decoded).isEmpty()) {
                    AutomationValidator.integrityIssues(decoded)
                        .joinToString("; ") { "${it.path}: ${it.message}" }
                }
                val (current, _) = AutomationValidator.withInvalidDisabled(decoded)
                val next = transform(current).copy(formatVersion = AUTOMATION_FORMAT_VERSION)
                validateForSave(next)
                if (next != decoded) {
                    preferences[AUTOMATIONS_JSON_KEY] = AutomationCodec.encode(next)
                }
            }
        }
    }

    private fun validateForSave(document: AutomationDocument) {
        val integrity = AutomationValidator.integrityIssues(document)
        require(integrity.isEmpty()) {
            integrity.joinToString("; ") { "${it.path}: ${it.message}" }
        }
        document.automations.forEach { definition ->
            if (!definition.enabled) return@forEach
            val issues = AutomationValidator.validate(definition)
            require(issues.isEmpty()) {
                issues.joinToString("; ") { "${it.path}: ${it.message}" }
            }
        }
    }

    companion object {
        private val AUTOMATIONS_JSON_KEY =
            stringPreferencesKey("vad.dashing.tbox.automations_json")
    }
}
