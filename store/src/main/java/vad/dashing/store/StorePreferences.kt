package vad.dashing.store

import android.content.Context
import vad.dashing.store.disk.ApkLanguage
import vad.dashing.store.disk.CloudSource
import vad.dashing.store.disk.UpdateChannel

class StorePreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var channel: UpdateChannel
        get() = UpdateChannel.fromStorageValue(prefs.getString(KEY_CHANNEL, null))
        set(value) {
            prefs.edit().putString(KEY_CHANNEL, value.storageValue).apply()
        }

    var cloudSource: CloudSource
        get() = CloudSource.fromStorageValue(prefs.getString(KEY_CLOUD, null))
        set(value) {
            prefs.edit().putString(KEY_CLOUD, value.storageValue).apply()
        }

    /**
     * Global preferred APK language when a catalog app has both `ru` and `en` releases.
     * Defaults to the Store APK's own build flavor.
     */
    var apkLanguage: ApkLanguage
        get() {
            val raw = prefs.getString(KEY_APK_LANG, null)
            return if (raw.isNullOrBlank()) {
                ApkLanguage.fromStoreFlavor(BuildConfig.FLAVOR)
            } else {
                ApkLanguage.fromStorageValue(raw)
            }
        }
        set(value) {
            prefs.edit().putString(KEY_APK_LANG, value.storageValue).apply()
        }

    companion object {
        private const val PREFS_NAME = "tbox_store"
        private const val KEY_CHANNEL = "channel"
        private const val KEY_CLOUD = "cloud_source"
        private const val KEY_APK_LANG = "apk_language"
    }
}
