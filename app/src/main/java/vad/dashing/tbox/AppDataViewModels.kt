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

    val refuels = RefuelRepository.refuels
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = RefuelRepository.refuels.value
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
            synchronized(TripRepository.lock) {
                TripRepository.removeTrip(id)
            }
            persistTripsIfNeeded()
        }
    }

    fun setTripFavorite(id: String, favorite: Boolean) {
        viewModelScope.launch {
            synchronized(TripRepository.lock) {
                TripRepository.setFavorite(id, favorite)
            }
            persistTripsIfNeeded()
        }
    }

    fun deleteRefuel(id: String) {
        viewModelScope.launch {
            synchronized(RefuelRepository.lock) {
                RefuelRepository.removeRefuel(id)
            }
            persistRefuelsIfNeeded()
        }
    }

    fun updateRefuelActualLiters(id: String, liters: Float) {
        viewModelScope.launch {
            synchronized(RefuelRepository.lock) {
                RefuelRepository.updateActualLiters(id, liters)
            }
            persistRefuelsIfNeeded()
        }
    }

    fun updateRefuelPricePerLiter(id: String, pricePerLiterRub: Float?) {
        viewModelScope.launch {
            synchronized(RefuelRepository.lock) {
                RefuelRepository.updatePricePerLiter(id, pricePerLiterRub)
            }
            persistRefuelsIfNeeded()
        }
    }

    fun updateRefuelFuelType(id: String, fuelType: FuelTypeOption) {
        viewModelScope.launch {
            synchronized(RefuelRepository.lock) {
                RefuelRepository.updateFuelType(id, fuelType)
            }
            persistRefuelsIfNeeded()
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

    private suspend fun persistRefuelsIfNeeded() {
        if (!RefuelRepository.needsPersistence()) return
        val refuelsJson = refuelsListToJson(RefuelRepository.refuels.value)
        appDataManager.saveRefuelsJson(refuelsJson)
        RefuelRepository.markPersisted(refuelsJson)
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