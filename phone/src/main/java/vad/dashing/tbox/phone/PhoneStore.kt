package vad.dashing.tbox.phone

import android.content.Context
import android.util.Base64
import vad.dashing.tbox.phoneble.PhoneBleCodec
import java.security.SecureRandom

class PhoneStore(context: Context) {
    private val prefs = context.getSharedPreferences("phone_companion", Context.MODE_PRIVATE)
    val id: ByteArray
    val key: ByteArray

    init {
        val existingId = prefs.getString(KEY_ID, null)?.let(PhoneBleCodec::parseIdHex)
        val existingKey = prefs.getString(KEY_SECRET, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
        if (existingId != null && existingKey != null && existingKey.size == PhoneBleCodec.KEY_LEN) {
            id = existingId
            key = existingKey
        } else {
            val random = SecureRandom()
            id = ByteArray(PhoneBleCodec.ID_LEN).also(random::nextBytes)
            key = ByteArray(PhoneBleCodec.KEY_LEN).also(random::nextBytes)
            prefs.edit()
                .putString(KEY_ID, PhoneBleCodec.idHex(id))
                .putString(KEY_SECRET, Base64.encodeToString(key, Base64.NO_WRAP))
                .putLong(KEY_COUNTER, 0L)
                .apply()
        }
    }

    /*
     * The stored value is a reserved upper bound, written with commit() before any
     * counter below it goes on air. A killed process then skips the unused rest of the
     * block instead of reusing a counter the ESP already rejected as a replay.
     */
    private var reserved: Long = prefs.getLong(KEY_COUNTER, 0L)
    private var counter: Long = reserved

    fun nextCounter(): Long {
        val next = counter + 1L
        if (next > reserved) {
            reserved = next + COUNTER_BLOCK
            prefs.edit().putLong(KEY_COUNTER, reserved).commit()
        }
        counter = next
        return next
    }

    /** Set once the companion answered with a sealed snapshot, i.e. it knows our key. */
    var paired: Boolean
        get() = prefs.getBoolean(KEY_PAIRED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_PAIRED, value).apply()
        }

    private companion object {
        const val KEY_ID = "id"
        const val KEY_SECRET = "key"
        const val KEY_COUNTER = "counter"
        const val KEY_PAIRED = "paired"
        const val COUNTER_BLOCK = 64L
    }
}
