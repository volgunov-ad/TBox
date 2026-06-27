package vad.dashing.tbox.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import vad.dashing.tbox.SettingsManager

class UpdateViewModel(
    application: Application,
    private val settingsManager: SettingsManager,
) : AndroidViewModel(application) {
    private val repository = UpdateRepository(application, settingsManager)
    private var downloadJob: Job? = null

    val uiState = repository.uiState
    val updateChannel = settingsManager.updateChannelFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UpdateChannel.RELEASE)
    val updateCheckEnabled = settingsManager.updateCheckEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun checkForUpdate(force: Boolean = false) {
        viewModelScope.launch {
            repository.checkForUpdate(force = force)
        }
    }

    fun checkForUpdateOnStartupIfEnabled() {
        viewModelScope.launch {
            if (!settingsManager.updateCheckEnabledFlow.first()) return@launch
            if (UpdateSessionGate.tryBeginSessionCheck()) {
                repository.checkForUpdate(force = false)
            }
        }
    }

    fun downloadAndVerify() {
        if (downloadJob?.isActive == true) return
        downloadJob = viewModelScope.launch {
            try {
                repository.downloadAndVerify()
            } finally {
                downloadJob = null
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        repository.cancelDownload()
    }

    fun installPreparedApk() {
        repository.installPreparedApk()
    }

    fun canInstallPackages(): Boolean = repository.canInstallPackages()

    fun shouldShowMenuEntry(): Boolean = repository.shouldShowMenuEntry()

    fun peekUpdateInfo(): UpdateReleaseInfo? = repository.peekUpdateInfo()

    fun saveUpdateChannel(channel: UpdateChannel) {
        viewModelScope.launch {
            val current = settingsManager.updateChannelFlow.first()
            if (current == channel) return@launch
            settingsManager.saveUpdateChannel(channel)
            repository.resetAfterChannelChange()
            repository.checkForUpdate(force = true)
        }
    }
}

class UpdateViewModelFactory(
    private val application: Application,
    private val settingsManager: SettingsManager,
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UpdateViewModel::class.java)) {
            return UpdateViewModel(application, settingsManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
