package vad.dashing.voice.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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

class VoiceSettingsRepository(private val context: Context) {
    private val hostKey = stringPreferencesKey("host")
    private val portKey = intPreferencesKey("port")
    private val tokenKey = stringPreferencesKey("access_token")

    val settings: Flow<VoiceConnectionSettings> = context.voiceDataStore.data.map { prefs ->
        VoiceConnectionSettings(
            host = prefs[hostKey] ?: VoiceConnectionSettings.DEFAULT_HOST,
            port = prefs[portKey] ?: VoiceConnectionSettings.DEFAULT_PORT,
            accessToken = AccessTokenNormalizer.normalize(prefs[tokenKey].orEmpty()),
        )
    }

    suspend fun save(host: String, port: Int, accessToken: String) {
        context.voiceDataStore.edit { prefs ->
            prefs[hostKey] = host.trim().ifEmpty { VoiceConnectionSettings.DEFAULT_HOST }
            prefs[portKey] = port.coerceIn(1024, 65535)
            prefs[tokenKey] = AccessTokenNormalizer.normalize(accessToken)
        }
    }
}
