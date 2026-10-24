package vad.dashing.tbox.phone

import android.content.Context
import android.util.Base64
import vad.dashing.tbox.phoneble.PhoneBleCodec
import java.security.SecureRandom

class PhoneStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("phone_companion", Context.MODE_PRIVATE)
    private val counterLock = Any()
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
     * block instead of reusing a counter the ESP or HU already rejected as a replay.
     * Block size is 512 so 10 Hz YAW + 1 Hz LOC/MAG only commits to flash every ~45 s.
     */
    private var reserved: Long = prefs.getLong(KEY_COUNTER, 0L)
    private var counter: Long = reserved

    fun nextCounter(): Long = synchronized(counterLock) {
        val next = counter + 1L
        if (next > reserved) {
            reserved = next + COUNTER_BLOCK
            prefs.edit().putLong(KEY_COUNTER, reserved).commit()
        }
        counter = next
        next
    }

    /** Set once the companion or the head unit answered with a sealed snapshot, i.e. it knows our key. */
    var paired: Boolean
        get() = prefs.getBoolean(KEY_PAIRED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_PAIRED, value).apply()
        }

    /** Last page shown, by name; null before the first choice. */
    var page: String?
        get() = prefs.getString(KEY_PAGE, null)
        set(value) {
            prefs.edit().putString(KEY_PAGE, value).apply()
        }

    var path: PhonePath
        get() = PhonePath.entries.firstOrNull { it.name == prefs.getString(KEY_PATH, null) } ?: PhonePath.AUTO
        set(value) {
            prefs.edit().putString(KEY_PATH, value.name).apply()
        }

    /** Head unit that last answered over the direct link; tried first next time. */
    var directAddress: String?
        get() = prefs.getString(KEY_DIRECT_ADDRESS, null)
        set(value) {
            prefs.edit().putString(KEY_DIRECT_ADDRESS, value).apply()
        }

    var shareLocation: Boolean
        get() = prefs.getBoolean(KEY_SHARE_LOCATION, false)
        set(value) {
            prefs.edit().putBoolean(KEY_SHARE_LOCATION, value).apply()
        }

    var shareGyro: Boolean
        get() = prefs.getBoolean(KEY_SHARE_GYRO, false)
        set(value) {
            prefs.edit().putBoolean(KEY_SHARE_GYRO, value).apply()
        }

    var shareCompass: Boolean
        get() = prefs.getBoolean(KEY_SHARE_COMPASS, false)
        set(value) {
            prefs.edit().putBoolean(KEY_SHARE_COMPASS, value).apply()
        }

    var anyTelemetryEnabled: Boolean
        get() = path != PhonePath.COMPANION && (shareLocation || shareGyro || shareCompass)
        private set(_) = Unit

    val hasAnyTelemetryToggleChecked: Boolean
        get() = shareLocation || shareGyro || shareCompass

    val directPaired: Boolean
        get() = !directAddress.isNullOrBlank()

    companion object {
        const val KEY_ID = "id"
        const val KEY_SECRET = "key"
        const val KEY_COUNTER = "counter"
        const val KEY_PAIRED = "paired"
        const val KEY_PAGE = "page"
        const val KEY_PATH = "path"
        const val KEY_DIRECT_ADDRESS = "direct_address"
        const val KEY_SHARE_LOCATION = "share_location"
        const val KEY_SHARE_GYRO = "share_gyro"
        const val KEY_SHARE_COMPASS = "share_compass"
        const val COUNTER_BLOCK = 512L
    }
}
