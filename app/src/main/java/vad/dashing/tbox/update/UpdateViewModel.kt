package vad.dashing.tbox.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import vad.dashing.tbox.SettingsManager

class UpdateViewModel(
    application: Application,
    private val settingsManager: SettingsManager,
) : AndroidViewModel(application) {
    private val repository = UpdateRepository(application, settingsManager)

    val uiState = repository.uiState
    val updateChannel = settingsManager.updateChannelFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UpdateChannel.RELEASE)

    fun checkForUpdate(force: Boolean = false) {
        viewModelScope.launch {
            repository.checkForUpdate(force = force)
        }
    }

    fun downloadAndVerify() {
        viewModelScope.launch {
            repository.downloadAndVerify()
        }
    }

    fun installPreparedApk() {
        repository.installPreparedApk()
    }

    fun canInstallPackages(): Boolean = repository.canInstallPackages()

    fun shouldShowMenuEntry(): Boolean = repository.shouldShowMenuEntry()

    fun saveUpdateChannel(channel: UpdateChannel) {
        viewModelScope.launch {
            settingsManager.saveUpdateChannel(channel)
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
