package vad.dashing.mqtt.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import java.util.UUID

class MqttSettingsStore private constructor(context: Context) {
    private val prefs: SharedPreferences = openPrefs(context)

    fun load(): MqttSettings = MqttSettings(
        apiPort = prefs.getInt(KEY_API_PORT, 8765),
        brokerHost = prefs.getString(KEY_HOST, "").orEmpty(),
        brokerPort = prefs.getInt(KEY_PORT, 1883),
        username = prefs.getString(KEY_USER, "").orEmpty(),
        password = prefs.getString(KEY_PASSWORD, "").orEmpty(),
        tlsEnabled = prefs.getBoolean(KEY_TLS, false),
        caPem = prefs.getString(KEY_CA, "").orEmpty(),
        topicPrefix = prefs.getString(KEY_TOPIC_PREFIX, TopicsDefault.TOPIC).orEmpty(),
        discoveryPrefix = prefs.getString(KEY_DISCOVERY_PREFIX, TopicsDefault.DISCOVERY).orEmpty(),
        deviceId = prefs.getString(KEY_DEVICE_ID, TopicsDefault.DEVICE).orEmpty(),
        deviceName = prefs.getString(KEY_DEVICE_NAME, TopicsDefault.NAME).orEmpty(),
        mqttClientId = prefs.getString(KEY_CLIENT_ID, "").orEmpty(),
        discoveryEnabled = prefs.getBoolean(KEY_DISCOVERY, true),
        acceptCommands = prefs.getBoolean(KEY_COMMANDS, true),
        autostart = prefs.getBoolean(KEY_AUTOSTART, true),
        pollSeconds = prefs.getInt(KEY_POLL, 3),
        repeatMinutes = prefs.getInt(KEY_REPEAT, 5),
        fastPublishSeconds = prefs.getInt(KEY_FAST, 5),
        accessToken = prefs.getString(KEY_TOKEN, "").orEmpty(),
        selectedObjectIds = decodeIds(prefs.getString(KEY_SELECTED, "[]").orEmpty()),
        wireguardEnabled = prefs.getBoolean(KEY_WG_ENABLED, false),
        wireguardConf = prefs.getString(KEY_WG_CONF, "").orEmpty(),
        wireguardFileName = prefs.getString(KEY_WG_FILE, "").orEmpty(),
    ).normalized()

    fun save(normalized: MqttSettings) {
        prefs.edit()
            .putInt(KEY_API_PORT, normalized.apiPort)
            .putString(KEY_HOST, normalized.brokerHost)
            .putInt(KEY_PORT, normalized.brokerPort)
            .putString(KEY_USER, normalized.username)
            .putString(KEY_PASSWORD, normalized.password)
            .putBoolean(KEY_TLS, normalized.tlsEnabled)
            .putString(KEY_CA, normalized.caPem)
            .putString(KEY_TOPIC_PREFIX, normalized.topicPrefix)
            .putString(KEY_DISCOVERY_PREFIX, normalized.discoveryPrefix)
            .putString(KEY_DEVICE_ID, normalized.deviceId)
            .putString(KEY_DEVICE_NAME, normalized.deviceName)
            .putString(KEY_CLIENT_ID, normalized.mqttClientId)
            .putBoolean(KEY_DISCOVERY, normalized.discoveryEnabled)
            .putBoolean(KEY_COMMANDS, normalized.acceptCommands)
            .putBoolean(KEY_AUTOSTART, normalized.autostart)
            .putInt(KEY_POLL, normalized.pollSeconds)
            .putInt(KEY_REPEAT, normalized.repeatMinutes)
            .putInt(KEY_FAST, normalized.fastPublishSeconds)
            .putString(KEY_TOKEN, normalized.accessToken.trim())
            .putString(KEY_SELECTED, encodeIds(normalized.selectedObjectIds))
            .putBoolean(KEY_WG_ENABLED, normalized.wireguardEnabled)
            .putString(KEY_WG_CONF, normalized.wireguardConf)
            .putString(KEY_WG_FILE, normalized.wireguardFileName)
            .apply()
    }

    fun pairClientId(): String {
        val existing = prefs.getString(KEY_PAIR_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_PAIR_ID, created).apply()
        return created
    }

    fun loadPublishedTopics(): Set<String> =
        decodeIds(prefs.getString(KEY_PUBLISHED, "[]").orEmpty())

    fun savePublishedTopics(topics: Set<String>) {
        prefs.edit().putString(KEY_PUBLISHED, encodeIds(topics)).apply()
    }

    private object TopicsDefault {
        const val TOPIC = "tbox"
        const val DISCOVERY = "homeassistant"
        const val DEVICE = "dashing"
        const val NAME = "Jetour Dashing"
    }

    companion object {
        private const val FILE = "mqtt_settings"
        // Never reuse FILE: plain entries mixed into the encrypted file break it on the next open.
        private const val PLAIN_FILE = "mqtt_settings_plain"

        @Volatile
        private var instance: MqttSettingsStore? = null

        /** One instance per process: opening the keystore-backed file is slow and done on the main thread. */
        fun get(context: Context): MqttSettingsStore =
            instance ?: synchronized(this) {
                instance ?: MqttSettingsStore(context.applicationContext).also { instance = it }
            }
        private const val KEY_API_PORT = "api_port"
        private const val KEY_HOST = "broker_host"
        private const val KEY_PORT = "broker_port"
        private const val KEY_USER = "broker_user"
        private const val KEY_PASSWORD = "broker_password"
        private const val KEY_TLS = "tls"
        private const val KEY_CA = "ca_pem"
        private const val KEY_TOPIC_PREFIX = "topic_prefix"
        private const val KEY_DISCOVERY_PREFIX = "discovery_prefix"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DEVICE_NAME = "device_name"
        private const val KEY_CLIENT_ID = "mqtt_client_id"
        private const val KEY_DISCOVERY = "discovery"
        private const val KEY_COMMANDS = "accept_commands"
        private const val KEY_AUTOSTART = "autostart"
        private const val KEY_POLL = "poll_seconds"
        private const val KEY_REPEAT = "repeat_minutes"
        private const val KEY_FAST = "fast_publish_seconds"
        private const val KEY_TOKEN = "access_token"
        private const val KEY_SELECTED = "selected_ids"
        private const val KEY_PAIR_ID = "pair_client_id"
        private const val KEY_PUBLISHED = "published_configs"
        private const val KEY_WG_ENABLED = "wg_enabled"
        private const val KEY_WG_CONF = "wg_conf"
        private const val KEY_WG_FILE = "wg_file"

        private fun openPrefs(context: Context): SharedPreferences {
            return try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            } catch (_: Exception) {
                context.getSharedPreferences(PLAIN_FILE, Context.MODE_PRIVATE)
            }
        }

        private fun encodeIds(ids: Set<String>): String {
            val array = JSONArray()
            ids.sorted().forEach { array.put(it) }
            return array.toString()
        }

        private fun decodeIds(raw: String): Set<String> {
            return runCatching {
                val array = JSONArray(raw)
                buildSet {
                    for (i in 0 until array.length()) {
                        val value = array.optString(i)
                        if (value.isNotBlank()) add(value)
                    }
                }
            }.getOrDefault(emptySet())
        }
    }
}
