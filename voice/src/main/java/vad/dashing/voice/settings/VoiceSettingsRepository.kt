package vad.dashing.voice.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import vad.dashing.voice.llm.CloudBaseUrlNormalizer
import vad.dashing.voice.llm.CloudLlmPreset
import vad.dashing.voice.llm.CloudLlmSettings

private val Context.voiceDataStore by preferencesDataStore(name = "vad_voice_settings")

data class VoiceConnectionSettings(
    val host: String = DEFAULT_HOST,
    val port: Int = DEFAULT_PORT,
    val accessToken: String = "",
) {
    companion object {
        const val DEFAULT_HOST = "127.0.0.1"
        const val DEFAULT_PORT = 8765
    }
}

data class VoiceStoredSettings(
    val connection: VoiceConnectionSettings = VoiceConnectionSettings(),
    val cloud: CloudLlmSettings = CloudLlmSettings(),
    /** Offline n-gram / future ONNX classifier between cloud and aliases (stage E). */
    val localClassifierEnabled: Boolean = true,
)

class VoiceSettingsRepository(private val context: Context) {
    private val hostKey = stringPreferencesKey("host")
    private val portKey = intPreferencesKey("port")
    private val tokenKey = stringPreferencesKey("access_token")

    private val cloudEnabledKey = booleanPreferencesKey("cloud_enabled")
    private val cloudPreferKey = booleanPreferencesKey("cloud_prefer")
    private val cloudAllowPhrasesKey = booleanPreferencesKey("cloud_allow_phrases")
    private val cloudAllowFreeformKey = booleanPreferencesKey("cloud_allow_freeform")
    private val cloudPresetKey = stringPreferencesKey("cloud_preset")
    private val cloudBaseUrlKey = stringPreferencesKey("cloud_base_url")
    /** Legacy plaintext key — migrated into [CloudApiKeyStore] then cleared. */
    private val cloudApiKeyKey = stringPreferencesKey("cloud_api_key")
    private val cloudModelKey = stringPreferencesKey("cloud_model")
    private val localClassifierKey = booleanPreferencesKey("local_classifier_enabled")

    private val apiKeyStore = CloudApiKeyStore(context)

    val settings: Flow<VoiceStoredSettings> = context.voiceDataStore.data.map { prefs ->
        val preset = CloudLlmPreset.fromStorageKey(prefs[cloudPresetKey])
        val storedUrl = prefs[cloudBaseUrlKey]
        val baseUrl = when {
            !storedUrl.isNullOrBlank() -> CloudBaseUrlNormalizer.normalize(storedUrl)
            preset != CloudLlmPreset.CUSTOM -> preset.defaultBaseUrl
            else -> ""
        }
        val legacyKey = AccessTokenNormalizer.normalize(prefs[cloudApiKeyKey].orEmpty())
        val secureKey = apiKeyStore.read()
        val apiKey = when {
            secureKey.isNotBlank() -> secureKey
            legacyKey.isNotBlank() -> {
                apiKeyStore.write(legacyKey)
                legacyKey
            }
            else -> ""
        }
        VoiceStoredSettings(
            connection = VoiceConnectionSettings(
                host = prefs[hostKey] ?: VoiceConnectionSettings.DEFAULT_HOST,
                port = prefs[portKey] ?: VoiceConnectionSettings.DEFAULT_PORT,
                accessToken = AccessTokenNormalizer.normalize(prefs[tokenKey].orEmpty()),
            ),
            cloud = CloudLlmSettings(
                enabled = prefs[cloudEnabledKey] ?: false,
                preferCloud = prefs[cloudPreferKey] ?: true,
                allowSendPhrases = prefs[cloudAllowPhrasesKey] ?: false,
                allowFreeformSpeak = prefs[cloudAllowFreeformKey] ?: false,
                preset = preset,
                baseUrl = baseUrl,
                apiKey = apiKey,
                model = prefs[cloudModelKey].orEmpty().trim(),
            ),
            localClassifierEnabled = prefs[localClassifierKey] ?: true,
        )
    }

    suspend fun save(
        host: String,
        port: Int,
        accessToken: String,
        cloud: CloudLlmSettings,
        localClassifierEnabled: Boolean = true,
    ) {
        apiKeyStore.write(cloud.apiKey)
        context.voiceDataStore.edit { prefs ->
            prefs[hostKey] = host.trim().ifEmpty { VoiceConnectionSettings.DEFAULT_HOST }
            prefs[portKey] = port.coerceIn(1024, 65535)
            prefs[tokenKey] = AccessTokenNormalizer.normalize(accessToken)

            prefs[cloudEnabledKey] = cloud.enabled
            prefs[cloudPreferKey] = cloud.preferCloud
            prefs[cloudAllowPhrasesKey] = cloud.allowSendPhrases
            prefs[cloudAllowFreeformKey] = cloud.allowFreeformSpeak
            prefs[cloudPresetKey] = cloud.preset.storageKey
            prefs[cloudBaseUrlKey] = CloudBaseUrlNormalizer.normalize(cloud.baseUrl)
            // Drop legacy plaintext copy after moving to EncryptedSharedPreferences.
            prefs.remove(cloudApiKeyKey)
            prefs[cloudModelKey] = cloud.model.trim()
            prefs[localClassifierKey] = localClassifierEnabled
        }
    }

    /** Monitor-only save kept for call sites that only refresh the token. */
    suspend fun saveConnection(host: String, port: Int, accessToken: String) {
        context.voiceDataStore.edit { prefs ->
            prefs[hostKey] = host.trim().ifEmpty { VoiceConnectionSettings.DEFAULT_HOST }
            prefs[portKey] = port.coerceIn(1024, 65535)
            prefs[tokenKey] = AccessTokenNormalizer.normalize(accessToken)
        }
    }
}
