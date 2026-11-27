package vad.dashing.voice.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores the cloud LLM API key outside plain DataStore prefs.
 * Falls back to a private SharedPreferences file if encrypted prefs cannot be created
 * (rare OEM / test environments).
 */
class CloudApiKeyStore(context: Context) {
    private val prefs: SharedPreferences = createPrefs(context.applicationContext)

    fun read(): String =
        AccessTokenNormalizer.normalize(prefs.getString(KEY, "").orEmpty())

    fun write(apiKey: String) {
        prefs.edit()
            .putString(KEY, AccessTokenNormalizer.normalize(apiKey))
            .apply()
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    companion object {
        private const val FILE_NAME = "vad_voice_cloud_secrets"
        private const val KEY = "cloud_api_key"

        private fun createPrefs(context: Context): SharedPreferences =
            runCatching {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    FILE_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            }.getOrElse {
                context.getSharedPreferences(FILE_NAME + "_plain", Context.MODE_PRIVATE)
            }
    }
}
