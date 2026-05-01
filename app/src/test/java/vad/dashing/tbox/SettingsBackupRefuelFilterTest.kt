package vad.dashing.tbox

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SettingsBackupRefuelFilterTest {

    @Test
    fun filterOutTripAndRefuelListPreferences_removesRefuelsToo() {
        val entries = JSONArray()
            .put(entry(AppDataManager.TRIPS_JSON_PREFERENCE_NAME))
            .put(entry(AppDataManager.TRIP_FAVORITES_JSON_PREFERENCE_NAME))
            .put(entry(AppDataManager.REFUELS_JSON_PREFERENCE_NAME))
            .put(entry("vad.dashing.tbox.data.motor_hours"))

        val filtered = SettingsBackupCoordinator.filterOutTripAndRefuelListPreferences(entries)
        val names = buildSet {
            for (i in 0 until filtered.length()) {
                add(filtered.getJSONObject(i).getString(PreferenceStoreBackup.K_NAME))
            }
        }

        assertFalse(names.contains(AppDataManager.TRIPS_JSON_PREFERENCE_NAME))
        assertFalse(names.contains(AppDataManager.TRIP_FAVORITES_JSON_PREFERENCE_NAME))
        assertFalse(names.contains(AppDataManager.REFUELS_JSON_PREFERENCE_NAME))
        assertTrue(names.contains("vad.dashing.tbox.data.motor_hours"))
    }

    private fun entry(name: String): JSONObject = JSONObject()
        .put(PreferenceStoreBackup.K_NAME, name)
        .put(PreferenceStoreBackup.K_TYPE, PreferenceStoreBackup.T_STRING)
        .put(PreferenceStoreBackup.K_VALUE, "[]")
}
