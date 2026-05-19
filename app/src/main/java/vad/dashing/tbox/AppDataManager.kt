package vad.dashing.tbox

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private const val APP_DATA_NAME = "vad.dashing.tbox.app_data"

internal val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = APP_DATA_NAME)

class AppDataManager(private val context: Context) {

    internal val preferencesDataStore: DataStore<Preferences>
        get() = context.appDataStore

    companion object {
        private const val KEY_PREFIX = "vad.dashing.tbox.data."

        /** Full preference name for [TRIPS_JSON_KEY] (backup export matches on this). */
        internal const val TRIPS_JSON_PREFERENCE_NAME = "${KEY_PREFIX}trips_json"

        internal const val TRIP_FAVORITES_JSON_PREFERENCE_NAME = "${KEY_PREFIX}trip_favorites_json"
        internal const val REFUELS_JSON_PREFERENCE_NAME = "${KEY_PREFIX}refuels_json"

        private val MOTOR_HOURS_KEY = floatPreferencesKey("${KEY_PREFIX}motor_hours")
        private val TRIPS_JSON_KEY = stringPreferencesKey(TRIPS_JSON_PREFERENCE_NAME)
        private val TRIP_FAVORITES_JSON_KEY =
            stringPreferencesKey(TRIP_FAVORITES_JSON_PREFERENCE_NAME)
        private val REFUELS_JSON_KEY = stringPreferencesKey(REFUELS_JSON_PREFERENCE_NAME)

        private val WHEEL1_PRESSURE_LAST_KEY = floatPreferencesKey("${KEY_PREFIX}wheel1_pressure_last")
        private val WHEEL2_PRESSURE_LAST_KEY = floatPreferencesKey("${KEY_PREFIX}wheel2_pressure_last")
        private val WHEEL3_PRESSURE_LAST_KEY = floatPreferencesKey("${KEY_PREFIX}wheel3_pressure_last")
        private val WHEEL4_PRESSURE_LAST_KEY = floatPreferencesKey("${KEY_PREFIX}wheel4_pressure_last")

        /** Last stable filtered fuel % from CAN (0…100), saved on engine stop like wheel pressures. */
        private val FUEL_LAST_FILTERED_PERCENT_KEY = intPreferencesKey("${KEY_PREFIX}fuel_last_filtered_percent")

        /** Last standard calibrated tank liters matching [FUEL_LAST_FILTERED_PERCENT_KEY]. */
        private val FUEL_LAST_CALIBRATED_STD_LITERS_KEY =
            floatPreferencesKey("${KEY_PREFIX}fuel_last_calibrated_std_liters")
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

    val refuelsJsonFlow: Flow<String> = context.appDataStore.data
        .map { preferences -> preferences[REFUELS_JSON_KEY] ?: "" }
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

    suspend fun saveRefuelsJson(json: String) {
        context.appDataStore.edit { preferences ->
            preferences[REFUELS_JSON_KEY] = json
        }
    }

    /**
     * Сохраняет только положительные (не нулевые) давления по колёсам.
     * Для колеса с null или неположительным значением существующий ключ в DataStore не меняется.
     */
    suspend fun saveLastKnownNonZeroWheelPressuresPartial(wheels: Wheels) {
        context.appDataStore.edit { preferences ->
            fun MutablePreferences.maybePut(v: Float?, key: Preferences.Key<Float>) {
                val p = v ?: return
                if (p > 0f) {
                    this[key] = p
                }
            }
            preferences.maybePut(wheels.wheel1, WHEEL1_PRESSURE_LAST_KEY)
            preferences.maybePut(wheels.wheel2, WHEEL2_PRESSURE_LAST_KEY)
            preferences.maybePut(wheels.wheel3, WHEEL3_PRESSURE_LAST_KEY)
            preferences.maybePut(wheels.wheel4, WHEEL4_PRESSURE_LAST_KEY)
        }
    }

    suspend fun loadLastKnownWheelPressures(): Wheels {
        val preferences = context.appDataStore.data.first()
        fun read(key: Preferences.Key<Float>): Float? =
            preferences[key]?.takeIf { it > 0f }
        return Wheels(
            wheel1 = read(WHEEL1_PRESSURE_LAST_KEY),
            wheel2 = read(WHEEL2_PRESSURE_LAST_KEY),
            wheel3 = read(WHEEL3_PRESSURE_LAST_KEY),
            wheel4 = read(WHEEL4_PRESSURE_LAST_KEY),
        )
    }

    /**
     * Сохраняет последний известный отфильтрованный % и калиброванные стандартные литры с шины
     * (на переходе RPM > 0 → 0). Ключ не перезаписывается, если соответствующее значение недоступно.
     */
    suspend fun saveLastKnownFuelLevelPartial(
        percentFiltered: UInt?,
        calibratedStandardLiters: Float?,
    ) {
        context.appDataStore.edit { preferences ->
            if (percentFiltered != null) {
                preferences[FUEL_LAST_FILTERED_PERCENT_KEY] =
                    percentFiltered.toInt().coerceIn(0, 100)
            }
            val liters = calibratedStandardLiters
            if (liters != null && liters.isFinite() && liters >= 0f) {
                preferences[FUEL_LAST_CALIBRATED_STD_LITERS_KEY] = liters
            }
        }
    }

    /** Пара: отфильтрованный % (если был сохранён), стандартные калиброванные литры (если были). */
    suspend fun loadLastKnownFuelLevel(): Pair<UInt?, Float?> {
        val preferences = context.appDataStore.data.first()
        val pct = preferences[FUEL_LAST_FILTERED_PERCENT_KEY]
            ?.coerceIn(0, 100)
            ?.toUInt()
        val liters = preferences[FUEL_LAST_CALIBRATED_STD_LITERS_KEY]?.takeIf { it.isFinite() && it >= 0f }
        return Pair(pct, liters)
    }
}