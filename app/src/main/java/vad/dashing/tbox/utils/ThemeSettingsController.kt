package vad.dashing.tbox.utils

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log

/**
 * Reads and writes Jetour/MB head unit theme settings observed by [ThemeObserver].
 *
 * - [Settings.System] `DAY_NIGHT_STATUS` may require [android.Manifest.permission.WRITE_SETTINGS]
 *   (user can allow via system screen).
 * - [Settings.Global] `com.mb.provider.night_mode_auto` typically requires
 *   [android.Manifest.permission.WRITE_SECURE_SETTINGS] (often granted only via `adb pm grant` on
 *   non-root head units).
 */
object ThemeSettingsController {

    const val NIGHT_MODE_AUTO_KEY = "com.mb.provider.night_mode_auto"
    const val DAY_NIGHT_STATUS_KEY = "DAY_NIGHT_STATUS"

    /** OEM default when the key is missing (matches [ThemeObserver]). */
    const val AUTO_MODE_FOLLOW_SYSTEM = 1
    const val AUTO_MODE_LIGHT_FIXED = 0
    const val AUTO_MODE_DARK_FIXED = 2

    enum class ApplyOutcome {
        Success,
        /** Global OEM key not written; system key may or may not have been applied. */
        GlobalDenied,
        /** DAY_NIGHT_STATUS not written; global may have been rolled back. */
        SystemDenied,
        /** Neither global nor system could be written. */
        BothDenied,
    }

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

    fun readDayNightRaw(cr: ContentResolver): Int {
        return try {
            Settings.System.getInt(cr, DAY_NIGHT_STATUS_KEY, 1)
        } catch (e: SecurityException) {
            Log.w("ThemeSettings", "readDayNightRaw SecurityException", e)
            1
        } catch (e: Exception) {
            Log.e("ThemeSettings", "readDayNightRaw failed", e)
            1
        }
    }

    fun isFollowSystem(autoMode: Int): Boolean = autoMode == AUTO_MODE_FOLLOW_SYSTEM

    fun applyLightFixed(context: Context): ApplyOutcome {
        val cr = context.contentResolver
        val prevGlobal = readAutoMode(cr)
        if (putGlobalInt(cr, AUTO_MODE_LIGHT_FIXED)) {
            if (putSystemInt(cr, 1)) return ApplyOutcome.Success
            putGlobalInt(cr, prevGlobal)
            return ApplyOutcome.SystemDenied
        }
        return if (putSystemInt(cr, 1)) ApplyOutcome.GlobalDenied else ApplyOutcome.BothDenied
    }

    fun applyDarkFixed(context: Context): ApplyOutcome {
        val cr = context.contentResolver
        val prevGlobal = readAutoMode(cr)
        if (putGlobalInt(cr, AUTO_MODE_DARK_FIXED)) {
            if (putSystemInt(cr, 2)) return ApplyOutcome.Success
            putGlobalInt(cr, prevGlobal)
            return ApplyOutcome.SystemDenied
        }
        return if (putSystemInt(cr, 2)) ApplyOutcome.GlobalDenied else ApplyOutcome.BothDenied
    }

    fun applyFollowSystem(context: Context): ApplyOutcome {
        val cr = context.contentResolver
        return if (putGlobalInt(cr, AUTO_MODE_FOLLOW_SYSTEM)) {
            ApplyOutcome.Success
        } else {
            ApplyOutcome.GlobalDenied
        }
    }

    private fun putGlobalInt(cr: ContentResolver, value: Int): Boolean {
        return try {
            Settings.Global.putInt(cr, NIGHT_MODE_AUTO_KEY, value)
            true
        } catch (e: SecurityException) {
            Log.w("ThemeSettings", "putGlobalInt denied ($value)", e)
            false
        } catch (e: Exception) {
            Log.e("ThemeSettings", "putGlobalInt failed ($value)", e)
            false
        }
    }

    private fun putSystemInt(cr: ContentResolver, value: Int): Boolean {
        return try {
            Settings.System.putInt(cr, DAY_NIGHT_STATUS_KEY, value)
            true
        } catch (e: SecurityException) {
            Log.w("ThemeSettings", "putSystemInt denied ($value)", e)
            false
        } catch (e: Exception) {
            Log.e("ThemeSettings", "putSystemInt failed ($value)", e)
            false
        }
    }

    fun canWriteSystemSettings(context: Context): Boolean =
        Settings.System.canWrite(context.applicationContext)

    /** Opens the screen where the user can allow [android.Manifest.permission.WRITE_SETTINGS]. */
    fun openManageWriteSettingsScreen(context: Context) {
        runCatching {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure { Log.e("ThemeSettings", "openManageWriteSettingsScreen failed", it) }
    }

    fun adbGrantWriteSecureSettingsCommand(packageName: String): String =
        "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS"
}
