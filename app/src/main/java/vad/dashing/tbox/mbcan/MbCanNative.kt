package vad.dashing.tbox.mbcan

import android.util.Log

/**
 * Loads the same native libraries as *Dashing Electric Heat* (`mbcanclient` then `mbCan`).
 *
 * Loading is **lazy** (only when [ensureLoaded] runs — e.g. user taps Connect on the MB-CAN tab)
 * so a broken or incompatible `.so` on a device cannot crash app startup or the rest of the app.
 */
object MbCanNative {

    @Volatile
    private var librariesLoaded: Boolean = false

    @Volatile
    private var loadFailedPermanently: Boolean = false

    /**
     * Returns true if libraries were loaded successfully. Safe to call from any thread.
     * After the first failed attempt, returns false without retrying (avoids repeated crashes/logs).
     */
    @Synchronized
    fun ensureLoaded(): Boolean {
        if (librariesLoaded) return true
        if (loadFailedPermanently) return false
        return try {
            System.loadLibrary("mbcanclient")
            System.loadLibrary("mbCan")
            librariesLoaded = true
            true
        } catch (t: Throwable) {
            // UnsatisfiedLinkError, JNI_OnLoad failure, etc. — must not take down the process.
            Log.w(TAG, "MB-CAN libraries failed to load; MB-CAN tab disabled", t)
            loadFailedPermanently = true
            false
        }
    }

    /** True only after a successful [ensureLoaded]. */
    fun isLoaded(): Boolean = librariesLoaded

    private const val TAG = "MbCanNative"
}
