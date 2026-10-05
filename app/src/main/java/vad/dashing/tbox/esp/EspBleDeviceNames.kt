package vad.dashing.tbox.esp

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import vad.dashing.tbox.settingsDataStore
import java.util.Locale

/** Normalize companion BLE MAC for maps, automations, and DataStore keys. */
fun normalizeEspBleMac(raw: String): String =
    raw.trim().lowercase(Locale.US)

/**
 * Local display names for allowlisted Shelly Blu remotes (`mac → name`).
 * Stored in settings DataStore; not written to companion NVS.
 */
object EspBleDeviceNamesCodec {
    const val PREF_KEY_NAME = "vad.dashing.tbox.esp_ble_device_names_json"
    val PREF_KEY = stringPreferencesKey(PREF_KEY_NAME)

    fun decode(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            buildMap {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val mac = normalizeEspBleMac(key)
                    if (mac.isEmpty()) continue
                    val name = obj.optString(key, "").trim()
                    if (name.isNotEmpty()) put(mac, name)
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun encode(names: Map<String, String>): String {
        if (names.isEmpty()) return ""
        val obj = JSONObject()
        names.forEach { (macRaw, nameRaw) ->
            val mac = normalizeEspBleMac(macRaw)
            val name = nameRaw.trim()
            if (mac.isNotEmpty() && name.isNotEmpty()) {
                obj.put(mac, name)
            }
        }
        return if (obj.length() == 0) "" else obj.toString()
    }

    /** Label for dropdowns: `Name (aa:bb:…)` or bare MAC. */
    fun label(macRaw: String, names: Map<String, String>): String {
        val mac = normalizeEspBleMac(macRaw)
        if (mac.isEmpty()) return ""
        val name = names[mac]?.trim().orEmpty()
        return if (name.isEmpty()) mac else "$name ($mac)"
    }

    fun flow(context: Context): Flow<Map<String, String>> =
        context.settingsDataStore.data
            .map { preferences -> decode(preferences[PREF_KEY].orEmpty()) }
            .distinctUntilChanged()
}
