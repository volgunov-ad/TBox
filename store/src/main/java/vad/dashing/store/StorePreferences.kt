package vad.dashing.store

import android.content.Context
import vad.dashing.store.disk.UpdateChannel

class StorePreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var channel: UpdateChannel
        get() = UpdateChannel.fromStorageValue(prefs.getString(KEY_CHANNEL, null))
        set(value) {
            prefs.edit().putString(KEY_CHANNEL, value.storageValue).apply()
        }

    companion object {
        private const val PREFS_NAME = "tbox_store"
        private const val KEY_CHANNEL = "channel"
    }
}
