package vad.dashing.tbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppDataViewModel(private val appDataManager: AppDataManager) : ViewModel() {
    val motorHours = CarDataRepository.motorHours
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CarDataRepository.motorHours.value
        )

    val trips = TripRepository.trips
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TripRepository.trips.value
        )

    val favoriteTripIds = TripRepository.favoriteIds
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TripRepository.favoriteIds.value
        )

    val activeTrip = TripRepository.activeTrip
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TripRepository.activeTrip.value
        )

    fun setMotorHours(value: Float) {
        viewModelScope.launch {
            CarDataRepository.setMotorHours(value)
            appDataManager.saveMotorHours(value)
            CarDataRepository.markPersisted(value)
        }
    }

    fun updateTripName(id: String, name: String) {
        viewModelScope.launch {
            synchronized(TripRepository.lock) {
                val t = TripRepository.trips.value.firstOrNull { it.id == id } ?: return@launch
                TripRepository.replaceTrip(t.copy(name = name))
            }
            persistTripsIfNeeded()
        }
    }

    fun deleteTrip(id: String) {
        viewModelScope.launch {
            TripRepository.removeTrip(id)
            persistTripsIfNeeded()
        }
    }

    fun setTripFavorite(id: String, favorite: Boolean) {
        viewModelScope.launch {
            TripRepository.setFavorite(id, favorite)
            persistTripsIfNeeded()
        }
    }

    private suspend fun persistTripsIfNeeded() {
        if (!TripRepository.needsPersistence()) return
        val tripsJson = tripsListToJson(TripRepository.trips.value)
        val favJson = favoritesSetToJson(TripRepository.favoriteIds.value)
        appDataManager.saveTripsJson(tripsJson)
        appDataManager.saveTripFavoritesJson(favJson)
        TripRepository.markPersisted(tripsJson, favJson)
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