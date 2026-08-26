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
                    val issues = AutomationValidator.validate(document)
                    if (issues.isEmpty()) {
                        AutomationStoreSnapshot(document, rawJson = raw)
                    } else {
                        AutomationStoreSnapshot(
                            document = document,
                            loadError = issues.joinToString("; ") { "${it.path}: ${it.message}" },
                            rawJson = raw,
                        )
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
        validateForSave(normalized)
        writeMutex.withLock {
            appContext.settingsDataStore.edit { preferences ->
                preferences[AUTOMATIONS_JSON_KEY] = AutomationCodec.encode(normalized)
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
                val current = AutomationCodec.decode(raw).getOrThrow()
                validateForSave(current)
                val next = transform(current).copy(formatVersion = AUTOMATION_FORMAT_VERSION)
                validateForSave(next)
                preferences[AUTOMATIONS_JSON_KEY] = AutomationCodec.encode(next)
            }
        }
    }

    private fun validateForSave(document: AutomationDocument) {
        val issues = AutomationValidator.validate(document)
        require(issues.isEmpty()) {
            issues.joinToString("; ") { "${it.path}: ${it.message}" }
        }
    }

    companion object {
        private val AUTOMATIONS_JSON_KEY =
            stringPreferencesKey("vad.dashing.tbox.automations_json")
    }
}
