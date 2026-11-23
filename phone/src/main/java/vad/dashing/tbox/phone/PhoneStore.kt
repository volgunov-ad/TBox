package vad.dashing.tbox.phone

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import vad.dashing.tbox.phoneble.PhoneBleCodec
import java.security.SecureRandom

class PhoneStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("phone_companion", Context.MODE_PRIVATE)
    val id: ByteArray
    val key: ByteArray

    init {
        val identity = loadIdentity(prefs)
        id = identity.first
        key = identity.second
    }

    /** Clears the remembered head unit. The id and key stay so a new pair can reuse them. */
    fun forgetPairing() {
        paired = false
        directAddress = null
    }

    fun nextCounter(): Long = synchronized(counterLock) {
        val shared = counterPrefs ?: prefs.also { counterPrefs = it }
        if (reserved < 0L) {
            reserved = shared.getLong(KEY_COUNTER, 0L)
            counter = reserved
        }
        val next = counter + 1L
        if (next > reserved) {
            reserved = next + COUNTER_BLOCK
            shared.edit().putLong(KEY_COUNTER, reserved).commit()
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

    val anyTelemetryEnabled: Boolean
        get() = path != PhonePath.COMPANION && (shareLocation || shareGyro || shareCompass)

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

        private val identityLock = Any()

        /**
         * Two stores created together (activity and a boot receiver) must not each invent
         * an id. The second caller re-reads after the first has committed.
         */
        private fun loadIdentity(prefs: SharedPreferences): Pair<ByteArray, ByteArray> =
            synchronized(identityLock) {
                fun read(): Pair<ByteArray, ByteArray>? {
                    val existingId = prefs.getString(KEY_ID, null)?.let(PhoneBleCodec::parseIdHex)
                    val existingKey = prefs.getString(KEY_SECRET, null)
                        ?.let { runCatching { Base64.decode(it, Base64.NO_WRAP) }.getOrNull() }
                    if (existingId != null && existingKey != null && existingKey.size == PhoneBleCodec.KEY_LEN) {
                        return existingId to existingKey
                    }
                    return null
                }
                read() ?: run {
                    val random = SecureRandom()
                    val newId = ByteArray(PhoneBleCodec.ID_LEN).also(random::nextBytes)
                    val newKey = ByteArray(PhoneBleCodec.KEY_LEN).also(random::nextBytes)
                    prefs.edit()
                        .putString(KEY_ID, PhoneBleCodec.idHex(newId))
                        .putString(KEY_SECRET, Base64.encodeToString(newKey, Base64.NO_WRAP))
                        .putLong(KEY_COUNTER, 0L)
                        .commit()
                    read() ?: (newId to newKey)
                }
            }

        /*
         * The stored value is a reserved upper bound, written with commit() before any
         * counter below it goes on air. A killed process then skips the unused rest of the
         * block instead of reusing a counter the ESP or HU already rejected as a replay.
         * Block size is 512 so 10 Hz YAW + 1 Hz LOC/MAG only commits to flash every ~45 s.
         */
        private val counterLock = Any()
        private var counterPrefs: SharedPreferences? = null
        private var reserved: Long = -1L
        private var counter: Long = -1L
    }
}
