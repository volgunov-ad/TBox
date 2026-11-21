package vad.dashing.store.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import vad.dashing.store.StoreAppRow
import vad.dashing.store.StoreRepository
import vad.dashing.store.disk.UpdateChannel

class StoreViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StoreRepository(application)

    val listState = repository.listState
    val actionState = repository.actionState
    val channel = repository.channel
    val hu = repository.hu

    private val _selectedAppId = MutableStateFlow<String?>(null)
    val selectedAppId: StateFlow<String?> = _selectedAppId.asStateFlow()

    private var downloadJob: Job? = null

    fun refresh() {
        viewModelScope.launch { repository.refreshCatalog() }
    }

    fun setChannel(channel: UpdateChannel) {
        repository.setChannel(channel)
        refresh()
    }

    fun selectApp(appId: String?) {
        _selectedAppId.value = appId
    }

    fun selectedRow(): StoreAppRow? {
        val id = _selectedAppId.value ?: return null
        val ready = listState.value as? vad.dashing.store.StoreListState.Ready ?: return null
        return ready.rows.firstOrNull { it.entry.id == id }
    }

    fun download(appId: String) {
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            repository.downloadAndVerify(appId)
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        repository.cancelDownload()
    }

    fun install() {
        repository.installPreparedApk()
    }

    fun openApp(packageName: String): Boolean = repository.openInstalledApp(packageName)

    fun canInstallPackages(): Boolean = repository.canInstallPackages()

    fun onResume() {
        repository.refreshInstalledStatuses()
    }
}
