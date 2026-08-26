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
                        AutomationStoreSnapshot(document)
                    } else {
                        AutomationStoreSnapshot(
                            document = document,
                            loadError = issues.joinToString("; ") { "${it.path}: ${it.message}" },
                        )
                    }
                },
                onFailure = { error ->
                    AutomationStoreSnapshot(
                        document = AutomationDocument(),
                        loadError = error.message ?: error.javaClass.simpleName,
                    )
                },
            )
        }
        .distinctUntilChanged()

    suspend fun save(document: AutomationDocument): Result<Unit> = runCatching {
        val normalized = document.copy(formatVersion = AUTOMATION_FORMAT_VERSION)
        val issues = AutomationValidator.validate(normalized)
        require(issues.isEmpty()) {
            issues.joinToString("; ") { "${it.path}: ${it.message}" }
        }
        writeMutex.withLock {
            appContext.settingsDataStore.edit { preferences ->
                preferences[AUTOMATIONS_JSON_KEY] = AutomationCodec.encode(normalized)
            }
        }
    }

    suspend fun upsert(
        definition: AutomationDefinition,
        current: AutomationDocument,
    ): Result<Unit> {
        val definitions = current.automations.toMutableList()
        val index = definitions.indexOfFirst { it.id == definition.id }
        if (index >= 0) {
            definitions[index] = definition
        } else {
            definitions += definition
        }
        return save(current.copy(automations = definitions))
    }

    suspend fun delete(
        automationId: String,
        current: AutomationDocument,
    ): Result<Unit> = save(
        current.copy(automations = current.automations.filterNot { it.id == automationId }),
    )

    suspend fun setEnabled(
        automationId: String,
        enabled: Boolean,
        current: AutomationDocument,
    ): Result<Unit> {
        val definitions = current.automations.map {
            if (it.id == automationId) it.copy(enabled = enabled) else it
        }
        return save(current.copy(automations = definitions))
    }

    companion object {
        private val AUTOMATIONS_JSON_KEY =
            stringPreferencesKey("vad.dashing.tbox.automations_json")
    }
}
