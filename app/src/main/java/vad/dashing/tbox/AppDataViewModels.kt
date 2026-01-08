package vad.dashing.tbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppDataViewModel(private val appDataManager: AppDataManager) : ViewModel() {
    val motorHours = appDataManager.motorHoursFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0f
        )

    fun setMotorHours(value: Float) {
        viewModelScope.launch {
            appDataManager.saveMotorHours(value)
        }
    }
}

class AppDataViewModelFactory(private val appDataManager: AppDataManager) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppDataViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AppDataViewModel(appDataManager) as T
        }
        throw IllegalArgumentException("Unknown AppData ViewModel class")
    }
}