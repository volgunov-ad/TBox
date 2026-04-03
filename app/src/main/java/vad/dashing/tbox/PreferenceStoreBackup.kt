package vad.dashing.tbox

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

/**
 * Serializes one [DataStore] [Preferences] snapshot to/from JSON arrays of entries
 * `{ "n": key name, "t": type, "v": value }`.
 */
object PreferenceStoreBackup {

    internal const val K_NAME = "n"
    internal const val K_TYPE = "t"
    internal const val K_VALUE = "v"

    internal const val T_BOOL = "boolean"
    internal const val T_INT = "int"
    internal const val T_LONG = "long"
    internal const val T_FLOAT = "float"
    internal const val T_STRING = "string"
    internal const val T_STRING_SET = "string_set"

    suspend fun exportEntries(dataStore: DataStore<Preferences>): JSONArray {
        val prefs = dataStore.data.first()
        val arr = JSONArray()
        for ((key, value) in prefs.asMap()) {
            val o = JSONObject()
            o.put(K_NAME, key.name)
            when (value) {
                is Boolean -> {
                    o.put(K_TYPE, T_BOOL)
                    o.put(K_VALUE, value)
                }
                is Int -> {
                    o.put(K_TYPE, T_INT)
                    o.put(K_VALUE, value)
                }
                is Long -> {
                    o.put(K_TYPE, T_LONG)
                    o.put(K_VALUE, value)
                }
                is Float -> {
                    o.put(K_TYPE, T_FLOAT)
                    o.put(K_VALUE, value.toDouble())
                }
                is String -> {
                    o.put(K_TYPE, T_STRING)
                    o.put(K_VALUE, value)
                }
                is Set<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    val strSet = value as? Set<String> ?: continue
                    o.put(K_TYPE, T_STRING_SET)
                    val ja = JSONArray()
                    strSet.sorted().forEach { ja.put(it) }
                    o.put(K_VALUE, ja)
                }
                else -> continue
            }
            arr.put(o)
        }
        return arr
    }

    suspend fun importEntries(dataStore: DataStore<Preferences>, arr: JSONArray): Result<Unit> {
        return runCatching {
            dataStore.edit { mutable ->
                mutable.clear()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val name = o.optString(K_NAME)
                    if (name.isEmpty()) continue
                    when (o.optString(K_TYPE)) {
                        T_BOOL -> {
                            if (o.has(K_VALUE) && !o.isNull(K_VALUE)) {
                                mutable[booleanPreferencesKey(name)] = o.optBoolean(K_VALUE)
                            }
                        }
                        T_INT -> {
                            mutable[intPreferencesKey(name)] = o.optInt(K_VALUE)
                        }
                        T_LONG -> {
                            mutable[longPreferencesKey(name)] = o.optLong(K_VALUE)
                        }
                        T_FLOAT -> {
                            val d = o.optDouble(K_VALUE, Double.NaN)
                            if (!d.isNaN()) {
                                mutable[floatPreferencesKey(name)] = d.toFloat()
                            }
                        }
                        T_STRING -> {
                            mutable[stringPreferencesKey(name)] = o.optString(K_VALUE)
                        }
                        T_STRING_SET -> {
                            val ja = o.optJSONArray(K_VALUE) ?: JSONArray()
                            val set = buildSet {
                                for (j in 0 until ja.length()) {
                                    add(ja.optString(j))
                                }
                            }
                            mutable[stringSetPreferencesKey(name)] = set
                        }
                    }
                }
            }
        }
    }
}

object SettingsBackupCoordinator {

    const val FORMAT_VERSION = 1
    private const val KEY_FORMAT = "formatVersion"
    private const val KEY_PACKAGE = "packageName"
    private const val KEY_EXPORTED_AT = "exportedAtMillis"
    private const val KEY_SETTINGS = "settings"
    private const val KEY_APP_DATA = "app_data"

    suspend fun exportFullJson(
        packageName: String,
        settingsStore: DataStore<Preferences>,
        appDataStore: DataStore<Preferences>,
        excludeTripLists: Boolean = false,
    ): String {
        val exportedAt = System.currentTimeMillis()
        val root = JSONObject()
        root.put(KEY_FORMAT, FORMAT_VERSION)
        root.put(KEY_PACKAGE, packageName)
        root.put(KEY_EXPORTED_AT, exportedAt)
        root.put(KEY_SETTINGS, PreferenceStoreBackup.exportEntries(settingsStore))
        var appDataArr = PreferenceStoreBackup.exportEntries(appDataStore)
        if (excludeTripLists) {
            appDataArr = filterOutTripListPreferences(appDataArr)
        } else {
            patchTripsJsonInExportedAppData(appDataArr, exportedAt)
        }
        root.put(KEY_APP_DATA, appDataArr)
        return root.toString(2)
    }

    private fun filterOutTripListPreferences(entries: JSONArray): JSONArray {
        val out = JSONArray()
        for (i in 0 until entries.length()) {
            val o = entries.optJSONObject(i) ?: continue
            val name = o.optString(PreferenceStoreBackup.K_NAME)
            if (name == AppDataManager.TRIPS_JSON_PREFERENCE_NAME ||
                name == AppDataManager.TRIP_FAVORITES_JSON_PREFERENCE_NAME
            ) {
                continue
            }
            out.put(o)
        }
        return out
    }

    /**
     * For backup file only: close the active trip in serialized [trips_json] so importers see a
     * completed trip; runtime DataStore and [TripRepository] are unchanged.
     */
    private fun patchTripsJsonInExportedAppData(entries: JSONArray, endTimeEpochMs: Long) {
        for (i in 0 until entries.length()) {
            val o = entries.optJSONObject(i) ?: continue
            if (o.optString(PreferenceStoreBackup.K_NAME) != AppDataManager.TRIPS_JSON_PREFERENCE_NAME) {
                continue
            }
            if (o.optString(PreferenceStoreBackup.K_TYPE) != PreferenceStoreBackup.T_STRING) {
                continue
            }
            val raw = o.optString(PreferenceStoreBackup.K_VALUE)
            o.put(PreferenceStoreBackup.K_VALUE, tripsJsonForBackupExport(raw, endTimeEpochMs))
        }
    }

    /**
     * Replaces both preference stores and reapplies app_data into in-memory [TripRepository] / [CarDataRepository].
     */
    suspend fun importFullJson(
        appDataManager: AppDataManager,
        settingsStore: DataStore<Preferences>,
        appDataStore: DataStore<Preferences>,
        json: String,
    ): Result<Unit> {
        val root = runCatching { JSONObject(json) }.getOrElse {
            return Result.failure(IllegalArgumentException("invalid_json"))
        }
        if (root.optInt(KEY_FORMAT) != FORMAT_VERSION) {
            return Result.failure(IllegalArgumentException("unsupported_format"))
        }
        val settingsArr = root.optJSONArray(KEY_SETTINGS)
            ?: return Result.failure(IllegalArgumentException("missing_settings"))
        val appDataArr = root.optJSONArray(KEY_APP_DATA)
            ?: return Result.failure(IllegalArgumentException("missing_app_data"))

        PreferenceStoreBackup.importEntries(settingsStore, settingsArr).getOrElse {
            return Result.failure(it)
        }
        PreferenceStoreBackup.importEntries(appDataStore, appDataArr).getOrElse {
            return Result.failure(it)
        }
        applyImportedAppDataToRuntime(appDataManager)
        return Result.success(Unit)
    }

    private suspend fun applyImportedAppDataToRuntime(appDataManager: AppDataManager) {
        val motor = appDataManager.motorHoursFlow.first()
        CarDataRepository.setMotorHours(motor)
        CarDataRepository.markPersisted(motor)
        val tripsJson = appDataManager.tripsJsonFlow.first()
        val favJson = appDataManager.tripFavoritesJsonFlow.first()
        TripRepository.setTripsFromStore(
            tripsListFromJson(tripsJson),
            favoritesSetFromJson(favJson)
        )
    }
}
