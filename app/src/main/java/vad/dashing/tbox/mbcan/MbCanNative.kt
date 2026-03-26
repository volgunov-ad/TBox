package vad.dashing.tbox.mbcan

import android.util.Log

/**
 * Loads the same native libraries as *Dashing Electric Heat* ({@code MBCanEngine} static initializer).
 */
object MbCanNative {

    val librariesLoaded: Boolean

    init {
        librariesLoaded = runCatching {
            System.loadLibrary("mbcanclient")
            System.loadLibrary("mbCan")
            true
        }.onFailure { e ->
            Log.w(TAG, "MB-CAN libraries not loaded (expected on non-arm64 or missing deps)", e)
        }.getOrDefault(false)
    }

    private const val TAG = "MbCanNative"
}
