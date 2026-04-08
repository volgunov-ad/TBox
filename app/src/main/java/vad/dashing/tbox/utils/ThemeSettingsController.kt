package vad.dashing.tbox.utils

import android.content.ContentResolver
import android.content.Context
import android.provider.Settings
import android.util.Log

/**
 * Reads and writes Jetour/MB head unit theme settings observed by [ThemeObserver].
 */
object ThemeSettingsController {

    const val NIGHT_MODE_AUTO_KEY = "com.mb.provider.night_mode_auto"
    const val DAY_NIGHT_STATUS_KEY = "DAY_NIGHT_STATUS"

    /** OEM default when the key is missing (matches [ThemeObserver]). */
    const val AUTO_MODE_FOLLOW_SYSTEM = 1
    const val AUTO_MODE_LIGHT_FIXED = 0
    const val AUTO_MODE_DARK_FIXED = 2

    fun readAutoMode(cr: ContentResolver): Int {
        return try {
            Settings.Global.getInt(cr, NIGHT_MODE_AUTO_KEY, AUTO_MODE_FOLLOW_SYSTEM)
        } catch (e: SecurityException) {
            Log.w("ThemeSettings", "readAutoMode SecurityException", e)
            AUTO_MODE_FOLLOW_SYSTEM
        } catch (e: Exception) {
            Log.e("ThemeSettings", "readAutoMode failed", e)
            AUTO_MODE_FOLLOW_SYSTEM
        }
    }

    fun isFollowSystem(autoMode: Int): Boolean = autoMode == AUTO_MODE_FOLLOW_SYSTEM

    fun applyLightFixed(context: Context): Boolean {
        return try {
            Settings.Global.putInt(
                context.contentResolver,
                NIGHT_MODE_AUTO_KEY,
                AUTO_MODE_LIGHT_FIXED
            )
            Settings.System.putInt(
                context.contentResolver,
                DAY_NIGHT_STATUS_KEY,
                1
            )
            true
        } catch (e: SecurityException) {
            Log.w("ThemeSettings", "applyLightFixed denied", e)
            false
        } catch (e: Exception) {
            Log.e("ThemeSettings", "applyLightFixed failed", e)
            false
        }
    }

    fun applyDarkFixed(context: Context): Boolean {
        return try {
            Settings.Global.putInt(
                context.contentResolver,
                NIGHT_MODE_AUTO_KEY,
                AUTO_MODE_DARK_FIXED
            )
            Settings.System.putInt(
                context.contentResolver,
                DAY_NIGHT_STATUS_KEY,
                2
            )
            true
        } catch (e: SecurityException) {
            Log.w("ThemeSettings", "applyDarkFixed denied", e)
            false
        } catch (e: Exception) {
            Log.e("ThemeSettings", "applyDarkFixed failed", e)
            false
        }
    }

    fun applyFollowSystem(context: Context): Boolean {
        return try {
            Settings.Global.putInt(
                context.contentResolver,
                NIGHT_MODE_AUTO_KEY,
                AUTO_MODE_FOLLOW_SYSTEM
            )
            true
        } catch (e: SecurityException) {
            Log.w("ThemeSettings", "applyFollowSystem denied", e)
            false
        } catch (e: Exception) {
            Log.e("ThemeSettings", "applyFollowSystem failed", e)
            false
        }
    }
}
