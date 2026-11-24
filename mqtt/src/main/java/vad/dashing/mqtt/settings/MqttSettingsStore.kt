package vad.dashing.mqtt.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import java.security.KeyStore
import java.util.UUID

class MqttSettingsStore private constructor(
    context: Context,
    private val prefs: SharedPreferences,
    val storageError: String?,
    val storageNotice: String?,
) {
    private val appContext = context.applicationContext

    @Volatile
    private var snapshot: MqttSettings? = null

    fun load(): MqttSettings {
        snapshot?.let { return it }
        synchronized(this) {
            snapshot?.let { return it }
            val loaded = read().normalized()
            snapshot = loaded
            return loaded
        }
    }

    fun save(normalized: MqttSettings) {
        if (storageError != null) return
        val current = snapshot
        val token = current?.accessToken ?: prefs.getString(KEY_TOKEN, "").orEmpty()
        val port = current?.apiPort ?: prefs.getInt(KEY_API_PORT, normalized.apiPort)
        val suffix = normalized.clientSuffix.ifBlank {
            current?.clientSuffix?.ifBlank { null }
                ?: prefs.getString(KEY_CLIENT_SUFFIX, "").orEmpty()
        }
        prefs.edit()
            .putBoolean(KEY_BROKER_ENABLED, normalized.brokerEnabled)
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
            .putString(KEY_CLIENT_SUFFIX, suffix)
            .putBoolean(KEY_DISCOVERY, normalized.discoveryEnabled)
            .putBoolean(KEY_COMMANDS, normalized.acceptCommands)
            .putBoolean(KEY_AUTOSTART, normalized.autostart)
            .putInt(KEY_POLL, normalized.pollSeconds)
            .putInt(KEY_REPEAT, normalized.repeatMinutes)
            .putInt(KEY_FAST, normalized.fastPublishSeconds)
            .putString(KEY_SELECTED, encodeIds(normalized.selectedObjectIds))
            .putBoolean(KEY_WG_ENABLED, normalized.wireguardEnabled)
            .putString(KEY_WG_CONF, normalized.wireguardConf)
            .putString(KEY_WG_FILE, normalized.wireguardFileName)
            .putString(KEY_WQTT_TOKEN, normalized.wqttApiToken)
            .putString(KEY_WQTT_ROOM, normalized.wqttRoom)
            .putString(KEY_WQTT_DEVICE_IDS, encodeIntIds(normalized.wqttDeviceIds))
            .apply()
        snapshot = normalized.copy(accessToken = token.trim(), apiPort = port, clientSuffix = suffix)
    }

    /**
     * Token and port from Monitor's local provider. Written on their own: the bridge service
     * renews the token, and a full [save] from a stale copy would bring the old one back.
     */
    fun saveMonitorAccess(accessToken: String? = null, apiPort: Int? = null) {
        if (storageError != null) return
        val editor = prefs.edit()
        if (accessToken != null) editor.putString(KEY_TOKEN, accessToken.trim())
        if (apiPort != null) editor.putInt(KEY_API_PORT, apiPort)
        editor.apply()
        val base = snapshot ?: read().normalized()
        snapshot = base.copy(
            accessToken = accessToken?.trim() ?: base.accessToken,
            apiPort = apiPort ?: base.apiPort,
        )
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
        if (storageError != null) return
        prefs.edit().putString(KEY_PUBLISHED, encodeIds(topics)).apply()
    }

    private fun read(): MqttSettings = MqttSettings(
        apiPort = prefs.getInt(KEY_API_PORT, 8765),
        brokerEnabled = prefs.getBoolean(KEY_BROKER_ENABLED, true),
        brokerHost = prefs.getString(KEY_HOST, "").orEmpty(),
        brokerPort = prefs.getInt(KEY_PORT, 1883),
        username = prefs.getString(KEY_USER, "").orEmpty(),
        password = prefs.getString(KEY_PASSWORD, "").orEmpty(),
        tlsEnabled = prefs.getBoolean(KEY_TLS, false),
        caPem = prefs.getString(KEY_CA, "").orEmpty(),
        topicPrefix = prefs.getString(KEY_TOPIC_PREFIX, TopicsDefault.TOPIC).orEmpty(),
        discoveryPrefix = prefs.getString(KEY_DISCOVERY_PREFIX, TopicsDefault.DISCOVERY).orEmpty(),
        deviceId = loadDeviceId(),
        deviceName = prefs.getString(KEY_DEVICE_NAME, TopicsDefault.NAME).orEmpty(),
        mqttClientId = prefs.getString(KEY_CLIENT_ID, "").orEmpty(),
        clientSuffix = loadClientSuffix(),
        discoveryEnabled = prefs.getBoolean(KEY_DISCOVERY, true),
        acceptCommands = prefs.getBoolean(KEY_COMMANDS, true),
        autostart = prefs.getBoolean(KEY_AUTOSTART, true),
        pollSeconds = prefs.getInt(KEY_POLL, 3),
        repeatMinutes = prefs.getInt(KEY_REPEAT, 5),
        fastPublishSeconds = loadFastPublishSeconds(),
        accessToken = prefs.getString(KEY_TOKEN, "").orEmpty(),
        selectedObjectIds = decodeIds(prefs.getString(KEY_SELECTED, "[]").orEmpty()),
        wireguardEnabled = prefs.getBoolean(KEY_WG_ENABLED, false),
        wireguardConf = prefs.getString(KEY_WG_CONF, "").orEmpty(),
        wireguardFileName = prefs.getString(KEY_WG_FILE, "").orEmpty(),
        wqttApiToken = prefs.getString(KEY_WQTT_TOKEN, "").orEmpty(),
        wqttRoom = prefs.getString(KEY_WQTT_ROOM, DEFAULT_WQTT_ROOM).orEmpty(),
        wqttDeviceIds = decodeIntIds(prefs.getString(KEY_WQTT_DEVICE_IDS, "[]").orEmpty()),
    )

    /** A saved id stays. A new install gets `dashing_` plus 4 hex digits from ANDROID_ID. */
    private fun loadDeviceId(): String {
        if (prefs.contains(KEY_DEVICE_ID)) {
            return prefs.getString(KEY_DEVICE_ID, TopicsDefault.DEVICE).orEmpty()
        }
        val suffix = androidIdSuffix(appContext)
        val generated = "dashing_$suffix"
        prefs.edit().putString(KEY_DEVICE_ID, generated).apply()
        return generated
    }

    /** Empty client id is suffixed so two cars do not share `tbox-mqtt-dashing`. */
    private fun loadClientSuffix(): String {
        val existing = prefs.getString(KEY_CLIENT_SUFFIX, null)
        if (!existing.isNullOrBlank()) return existing
        val created = UUID.randomUUID().toString().replace("-", "").take(8)
        prefs.edit().putString(KEY_CLIENT_SUFFIX, created).apply()
        return created
    }

    /** Old default 5 becomes 10 once. A 5 saved after that stays 5. */
    private fun loadFastPublishSeconds(): Int {
        val stored = if (prefs.contains(KEY_FAST)) {
            prefs.getInt(KEY_FAST, FAST_PUBLISH_DEFAULT_SECONDS)
        } else {
            null
        }
        val (seconds, write) = fastPublishSecondsOnLoad(
            stored,
            prefs.getBoolean(KEY_FAST_MIGRATED, false),
        )
        if (write) {
            prefs.edit()
                .putInt(KEY_FAST, seconds)
                .putBoolean(KEY_FAST_MIGRATED, true)
                .apply()
        }
        return seconds
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
        internal const val PLAIN_FILE = "mqtt_settings_plain"
        private const val UNAVAILABLE_FILE = "mqtt_settings_unavailable"
        private const val HEALTH_FILE = "mqtt_keystore_health"
        private const val KEY_CORRUPT_AT = "corrupt_at_elapsed"
        private const val KEY_CORRUPT_PID = "corrupt_pid"
        private const val RETRY_TIMES = 3
        private const val RETRY_BACKOFF_MS = 500L
        private const val ERROR_COOLDOWN_MS = 5_000L

        const val STORAGE_UNAVAILABLE = "Хранилище ключей недоступно"
        const val STORAGE_RECOVERED =
            "Хранилище настроек было повреждено и создано заново. Введите данные брокера ещё раз."
        const val STORAGE_MIGRATED = "Настройки перенесены в защищённое хранилище."

        @Volatile
        private var instance: MqttSettingsStore? = null

        @Volatile
        private var errorInstance: MqttSettingsStore? = null

        @Volatile
        private var errorAtMs: Long = 0L

        /**
         * A successful encrypted open is cached. A failed open is not: the next call retries
         * after a short cooldown, so an early-boot keystore miss does not stick for the process.
         */
        fun get(context: Context): MqttSettingsStore {
            instance?.let { return it }
            synchronized(this) {
                instance?.let { return it }
                val now = SystemClock.elapsedRealtime()
                val recentError = errorInstance
                if (recentError != null && now - errorAtMs < ERROR_COOLDOWN_MS) return recentError
                val app = context.applicationContext
                val opened = openEncrypted(app)
                val store = MqttSettingsStore(app, opened.prefs, opened.error, opened.notice)
                if (opened.error == null) {
                    instance = store
                    errorInstance = null
                } else {
                    errorInstance = store
                    errorAtMs = now
                }
                return store
            }
        }

        /** Unit tests. Skips the keystore. */
        internal fun forTest(context: Context, prefs: SharedPreferences): MqttSettingsStore =
            MqttSettingsStore(context, prefs, storageError = null, storageNotice = null)

        internal fun resetForTest() {
            synchronized(this) {
                instance = null
                errorInstance = null
                errorAtMs = 0L
            }
        }

        private const val KEY_API_PORT = "api_port"
        private const val KEY_BROKER_ENABLED = "broker_enabled"
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
        private const val KEY_CLIENT_SUFFIX = "client_suffix"
        private const val KEY_DISCOVERY = "discovery"
        private const val KEY_COMMANDS = "accept_commands"
        private const val KEY_AUTOSTART = "autostart"
        private const val KEY_POLL = "poll_seconds"
        private const val KEY_REPEAT = "repeat_minutes"
        private const val KEY_FAST = "fast_publish_seconds"
        private const val KEY_FAST_MIGRATED = "fast_publish_default_10"
        private const val KEY_TOKEN = "access_token"
        private const val KEY_SELECTED = "selected_ids"
        private const val KEY_PAIR_ID = "pair_client_id"
        private const val KEY_PUBLISHED = "published_configs"
        private const val KEY_WG_ENABLED = "wg_enabled"
        private const val KEY_WG_CONF = "wg_conf"
        private const val KEY_WG_FILE = "wg_file"
        private const val KEY_WQTT_TOKEN = "wqtt_api_token"
        private const val KEY_WQTT_ROOM = "wqtt_room"
        private const val KEY_WQTT_DEVICE_IDS = "wqtt_device_ids"

        private data class Opened(
            val prefs: SharedPreferences,
            val error: String?,
            val notice: String?,
        )

        private fun openEncrypted(context: Context): Opened {
            val retry = retryKeystore(RETRY_TIMES, RETRY_BACKOFF_MS, { Thread.sleep(it) }) {
                openEncryptedOnce(context)
            }
            if (retry.value != null) {
                clearCorruptStamp(context)
                val notice = if (migratePlaintextInto(context, retry.value)) STORAGE_MIGRATED else null
                return Opened(retry.value, null, notice)
            }
            val failure = retry.error
            if (
                failure != null &&
                classifyKeystoreFailure(failure) == KeystoreFailure.CORRUPT &&
                corruptStampRepeats(context)
            ) {
                wipeEncrypted(context)
                val again = runCatching { openEncryptedOnce(context) }
                if (again.isSuccess) {
                    clearCorruptStamp(context)
                    return Opened(again.getOrThrow(), null, STORAGE_RECOVERED)
                }
            }
            return Opened(
                context.getSharedPreferences(UNAVAILABLE_FILE, Context.MODE_PRIVATE),
                STORAGE_UNAVAILABLE,
                null,
            )
        }

        private fun openEncryptedOnce(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }

        /** Records the first corrupt open. Returns true when a later open may wipe. */
        private fun corruptStampRepeats(context: Context): Boolean {
            val prefs = context.getSharedPreferences(HEALTH_FILE, Context.MODE_PRIVATE)
            val now = SystemClock.elapsedRealtime()
            val pid = Process.myPid()
            val previousAt = prefs.getLong(KEY_CORRUPT_AT, 0L)
            val previousPid = prefs.getInt(KEY_CORRUPT_PID, 0)
            val wipe = corruptRepeats(previousAt, previousPid, now, pid)
            if (previousAt <= 0L || previousPid != pid) {
                prefs.edit().putLong(KEY_CORRUPT_AT, now).putInt(KEY_CORRUPT_PID, pid).commit()
            }
            return wipe
        }

        private fun clearCorruptStamp(context: Context) {
            val prefs = context.getSharedPreferences(HEALTH_FILE, Context.MODE_PRIVATE)
            if (!prefs.contains(KEY_CORRUPT_AT)) return
            prefs.edit().remove(KEY_CORRUPT_AT).remove(KEY_CORRUPT_PID).commit()
        }

        private fun wipeEncrypted(context: Context) {
            context.deleteSharedPreferences(FILE)
            runCatching {
                val keyStore = KeyStore.getInstance("AndroidKeyStore")
                keyStore.load(null)
                val alias = MasterKey.DEFAULT_MASTER_KEY_ALIAS
                if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
            }
        }

        /** @return true when plaintext values were copied into [encrypted]. */
        internal fun migratePlaintextInto(context: Context, encrypted: SharedPreferences): Boolean {
            val plain = context.getSharedPreferences(PLAIN_FILE, Context.MODE_PRIVATE)
            if (plain.all.isEmpty()) return false
            val encryptedHasData = encrypted.contains(KEY_HOST) ||
                encrypted.contains(KEY_TOKEN) ||
                encrypted.contains(KEY_PASSWORD)
            if (encryptedHasData) {
                context.deleteSharedPreferences(PLAIN_FILE)
                return false
            }
            val editor = encrypted.edit()
            copyAll(plain, editor)
            editor.commit()
            context.deleteSharedPreferences(PLAIN_FILE)
            return true
        }

        private fun copyAll(from: SharedPreferences, editor: SharedPreferences.Editor) {
            from.all.forEach { (key, value) ->
                when (value) {
                    is String -> editor.putString(key, value)
                    is Int -> editor.putInt(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                }
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

        private fun encodeIntIds(ids: Set<Int>): String {
            val array = JSONArray()
            ids.filter { it > 0 }.sorted().forEach { array.put(it) }
            return array.toString()
        }

        private fun decodeIntIds(raw: String): Set<Int> {
            return runCatching {
                val array = JSONArray(raw)
                buildSet {
                    for (i in 0 until array.length()) {
                        val value = array.optInt(i)
                        if (value > 0) add(value)
                    }
                }
            }.getOrDefault(emptySet())
        }
    }
}

internal fun androidIdSuffix(context: Context): String {
    val raw = runCatching {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }.getOrNull().orEmpty()
    val hex = raw.lowercase().filter { it in '0'..'9' || it in 'a'..'f' }
    if (hex.length >= 4) return hex.takeLast(4)
    val random = UUID.randomUUID().toString().replace("-", "")
    return (hex + random).take(4)
}
