package vad.dashing.tbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import vad.dashing.tbox.fuel.FuelTypeOption
import vad.dashing.tbox.fuel.RefuelRepository
import vad.dashing.tbox.fuel.refuelsListToJson
import vad.dashing.tbox.trip.TripRepository
import vad.dashing.tbox.trip.favoritesSetToJson
import vad.dashing.tbox.trip.tripsListToJson

class AppDataViewModel(
    private val appDataManager: AppDataManager,
    private val settingsManager: SettingsManager,
) : ViewModel() {
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

    fun updateRefuelPriceSourceName(id: String, sourceName: String?) {
        viewModelScope.launch {
            synchronized(RefuelRepository.lock) {
                RefuelRepository.updatePriceSourceName(id, sourceName)
            }
            persistRefuelsIfNeeded()
        }
    }

    fun updateRefuelAmbientTemp(id: String, degreesC: Float) {
        viewModelScope.launch {
            val t = degreesC.coerceIn(-60f, 60f)
            synchronized(RefuelRepository.lock) {
                RefuelRepository.updateAmbientTempAtRefuel(id, t)
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

    /**
     * Смена объёма бака: сброс калибровки в настройках, пересчёт оценочных литров в заправках, сброс флагов обучения.
     */
    fun applyFuelTankChangeWithCalibrationReset(liters: Int) {
        viewModelScope.launch {
            settingsManager.saveFuelTankLitersAndClearFuelCalibration(liters)
            synchronized(RefuelRepository.lock) {
                RefuelRepository.recalculateEstimatedLitersForNominalTank(liters)
                RefuelRepository.clearFuelCalibrationUsageFlags()
            }
            persistRefuelsIfNeeded()
        }
    }

    /**
     * Порог зрелости зон (л датчика на зону для полной уверенности в локальном K).
     * JSON калибровки и флаги заправок не сбрасываются.
     */
    fun applyFuelCalibrationMaturityThreshold(thresholdLiters: Int) {
        viewModelScope.launch {
            settingsManager.saveFuelCalibrationMaturityThreshold(thresholdLiters)
        }
    }

    /** Смена числа зон: сброс калибровки и пересчёт заправок при текущем объёме бака. */
    fun applyFuelCalibrationZoneCountWithReset(zoneCount: Int) {
        viewModelScope.launch {
            settingsManager.saveFuelCalibrationZoneCountAndClearCalibration(zoneCount)
            val tank = settingsManager.fuelTankLitersFlow.first()
            synchronized(RefuelRepository.lock) {
                RefuelRepository.recalculateEstimatedLitersForNominalTank(tank)
                RefuelRepository.clearFuelCalibrationUsageFlags()
            }
            persistRefuelsIfNeeded()
        }
    }

    /** Только очистка JSON калибровки и флагов заправок (кнопка «сброс»). */
    fun clearFuelCalibrationOnly() {
        viewModelScope.launch {
            settingsManager.clearFuelCalibrationJson()
            synchronized(RefuelRepository.lock) {
                RefuelRepository.clearFuelCalibrationUsageFlags()
            }
            persistRefuelsIfNeeded()
        }
    }
}

class AppDataViewModelFactory(
    private val appDataManager: AppDataManager,
    private val settingsManager: SettingsManager,
) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppDataViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AppDataViewModel(appDataManager, settingsManager) as T
        }
        throw IllegalArgumentException("Unknown AppData ViewModel class")
    }
}