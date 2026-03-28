package vad.dashing.tbox

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

private const val APP_DATA_NAME = "vad.dashing.tbox.app_data"

private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = APP_DATA_NAME)

class AppDataManager(private val context: Context) {

    companion object {
        private const val KEY_PREFIX = "vad.dashing.tbox.data."

        private val MOTOR_HOURS_KEY = floatPreferencesKey("${KEY_PREFIX}motor_hours")
        private val TRIPS_JSON_KEY = stringPreferencesKey("${KEY_PREFIX}trips_json")
        private val TRIP_FAVORITES_JSON_KEY = stringPreferencesKey("${KEY_PREFIX}trip_favorites_json")
    }

    // Flow для моторных часов
    val motorHoursFlow: Flow<Float> = context.appDataStore.data
        .map { preferences -> preferences[MOTOR_HOURS_KEY] ?: 0f }
        .distinctUntilChanged()

    val tripsJsonFlow: Flow<String> = context.appDataStore.data
        .map { preferences -> preferences[TRIPS_JSON_KEY] ?: "" }
        .distinctUntilChanged()

    val tripFavoritesJsonFlow: Flow<String> = context.appDataStore.data
        .map { preferences -> preferences[TRIP_FAVORITES_JSON_KEY] ?: "" }
        .distinctUntilChanged()

    // Suspend функции для сохранения данных
    suspend fun saveMotorHours(value: Float) {
        context.appDataStore.edit { preferences ->
            preferences[MOTOR_HOURS_KEY] = value
        }
    }

    suspend fun saveTripsJson(json: String) {
        context.appDataStore.edit { preferences ->
            preferences[TRIPS_JSON_KEY] = json
        }
    }

    suspend fun saveTripFavoritesJson(json: String) {
        context.appDataStore.edit { preferences ->
            preferences[TRIP_FAVORITES_JSON_KEY] = json
        }
    }
}