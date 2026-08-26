package vad.dashing.tbox.automation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AutomationViewModel(application: Application) : AndroidViewModel(application) {
    private val store = AutomationStore(application)

    val storeSnapshot: StateFlow<AutomationStoreSnapshot> = store.snapshots.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = AutomationStoreSnapshot(AutomationDocument()),
    )

    val runtimeStatuses: StateFlow<Map<String, AutomationRuntimeStatus>> =
        AutomationRuntimeState.statuses

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _editorDraft = MutableStateFlow<AutomationDefinition?>(null)
    val editorDraft: StateFlow<AutomationDefinition?> = _editorDraft.asStateFlow()

    fun edit(definition: AutomationDefinition) {
        _editorDraft.value = definition
    }

    fun updateDraft(definition: AutomationDefinition) {
        _editorDraft.value = definition
    }

    fun closeEditor() {
        _editorDraft.value = null
    }

    fun clearError() {
        _lastError.value = null
    }

    fun save(definition: AutomationDefinition, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val result = store.upsert(definition)
            result.onSuccess {
                _lastError.value = null
                _editorDraft.value = null
                onSuccess()
            }.onFailure {
                _lastError.value = it.message ?: it.javaClass.simpleName
            }
        }
    }

    fun setEnabled(automationId: String, enabled: Boolean) {
        viewModelScope.launch {
            store.setEnabled(automationId, enabled)
                .onFailure { _lastError.value = it.message ?: it.javaClass.simpleName }
        }
    }

    fun delete(automationId: String) {
        viewModelScope.launch {
            store.delete(automationId)
                .onFailure { _lastError.value = it.message ?: it.javaClass.simpleName }
        }
    }

    fun resetInvalidConfiguration() {
        viewModelScope.launch {
            store.reset()
                .onSuccess { _lastError.value = null }
                .onFailure { _lastError.value = it.message ?: it.javaClass.simpleName }
        }
    }
}
